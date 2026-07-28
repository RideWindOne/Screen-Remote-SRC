package com.screen.remote.android.feature.remote.widget.floating

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.os.Build
import android.os.Vibrator
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import com.screen.remote.android.R
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.i18n.RemoteTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 菜单视图管理器
 * 对外保留统一门面，内部再拆为覆盖层状态、位置控制和交互绑定。
 */
internal class FloatingMenuViewManager(
    private val context: Context,
    private val windowManager: WindowManager,
    paramsA: WindowManager.LayoutParams,
    paramsB: WindowManager.LayoutParams,
    ballA: View,
    ballB: View,
    actions: FloatingMenuActions,
    scope: CoroutineScope,
    private val state: FloatingMenuGestureState,
    hapticEnabled: Boolean,
) {
    private val overlayState = FloatingMenuOverlayState()
    private val positionController =
        FloatingMenuMenuPositionController(
            context = context,
            paramsA = paramsA,
            paramsB = paramsB,
            ballA = ballA,
            ballB = ballB,
            state = state,
            overlayState = overlayState,
        )
    private val interactionBinder =
        FloatingMenuMenuInteractionBinder(
            windowManager = windowManager,
            ballA = ballA,
            ballB = ballB,
            actions = actions,
            scope = scope,
            hapticEnabled = hapticEnabled,
            onToggleMoreActionsRow = ::toggleMoreActionsRow,
        )

    fun showMenu() {
        val menu = createMenuView(context)
        val params = positionController.createInitialLayoutParams(menu, windowManager)
        windowManager.addView(menu, params)

        overlayState.menuView = menu
        overlayState.menuParams = params
        overlayState.isMoreActionsVisible = false
        state.isMenuShown = true

        interactionBinder.bind(
            menu = menu,
            onHideMenu = ::hideMenu,
        )
    }

    fun hideMenu() {
        overlayState.menuView?.let { menu ->
            runCatching { windowManager.removeView(menu) }
        }
        overlayState.menuView = null
        overlayState.menuParams = null
        overlayState.isMoreActionsVisible = false
        state.isMenuShown = false
    }

    fun toggleMoreActionsRow() {
        val menu = overlayState.menuView ?: return
        val params = overlayState.menuParams ?: return
        val moreActionsRow = menu.findViewById<View>(R.id.layout_more_actions) ?: return

        val newVisible = !overlayState.isMoreActionsVisible
        moreActionsRow.visibility = if (newVisible) View.VISIBLE else View.GONE
        measureMenuHeight(menu)
        overlayState.isMoreActionsVisible = newVisible
        positionController.repositionForCurrentMenuSize(windowManager, params)
    }

    fun syncMenuToBall() = positionController.syncMenuToBall(windowManager)

    fun centerMenuHorizontally() {
        positionController.centerMenuHorizontally(windowManager)
    }

    fun constrainMovementWithMenu(
        deltaY: Int,
        paramsA: WindowManager.LayoutParams,
        ballA: View,
    ): Int = positionController.constrainMovementWithMenu(deltaY, paramsA, ballA)

    fun cleanup() {
        hideMenu()
    }

    private fun createMenuView(context: Context): View {
        val parent = android.widget.FrameLayout(context)
        val menu = LayoutInflater.from(context).inflate(R.layout.floating_menu, parent, false)
        menu.findViewById<View>(R.id.layout_more_actions)?.visibility = View.GONE
        measureMenuHeight(menu)
        return menu
    }

    private fun measureMenuHeight(menu: View): Int {
        menu.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return menu.measuredHeight
    }
}

internal class FloatingMenuOverlayState {
    var menuView: View? = null
    var menuParams: WindowManager.LayoutParams? = null
    var isMoreActionsVisible: Boolean = false
}

internal fun createFloatingMenuLayoutParams(
    x: Int,
    y: Int,
): WindowManager.LayoutParams =
    WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION
        format = PixelFormat.TRANSLUCENT
        flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
        useWholeWindowCoordinateSpace()
    }

internal fun WindowManager.LayoutParams.useWholeWindowCoordinateSpace() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        setFitInsetsTypes(0)
    }
}

internal class FloatingMenuMenuPositionController(
    context: Context,
    private val paramsA: WindowManager.LayoutParams,
    private val paramsB: WindowManager.LayoutParams,
    private val ballA: View,
    private val ballB: View,
    private val state: FloatingMenuGestureState,
    private val overlayState: FloatingMenuOverlayState,
) {
    companion object {
        private const val MENU_GAP_DP = 16f
    }

    private val density = context.resources.displayMetrics.density
    private val windowBoundsProvider = FloatingMenuWindowBoundsProvider(context)

    fun createInitialLayoutParams(
        menu: View,
        windowManager: WindowManager,
    ): WindowManager.LayoutParams {
        val windowBounds = windowBoundsProvider.current()
        val menuWidth = currentMenuWidth(menu)
        val menuHeight = currentMenuHeight(menu)
        val menuTop = currentMenuTop(windowBounds)
        makeRoomAboveBall(menuHeight, menuTop, windowBounds, windowManager)
        val x = windowBounds.left + (windowBounds.width - menuWidth) / 2
        val y =
            calculateFloatingMenuWindowY(
                ballY = paramsA.y,
                menuHeight = menuHeight,
                gap = (MENU_GAP_DP * density).toInt(),
                boundsTop = menuTop,
            )
        return createFloatingMenuLayoutParams(x = x, y = y)
    }

    private fun makeRoomAboveBall(
        menuHeight: Int,
        menuTop: Int,
        windowBounds: FloatingMenuWindowBounds,
        windowManager: WindowManager,
    ) {
        val gapPx = (MENU_GAP_DP * density).toInt()
        val placement =
            calculateFloatingMenuVerticalPlacement(
                currentBallY = paramsA.y,
                ballHeight = ballA.height,
                menuHeight = menuHeight,
                gap = gapPx,
                boundsTop = menuTop,
                boundsBottom = windowBounds.bottom,
            )
        val movement = placement.ballY - paramsA.y
        if (movement == 0) return

        paramsA.y += movement
        paramsB.y += movement
        windowManager.updateViewLayout(ballA, paramsA)
        windowManager.updateViewLayout(ballB, paramsB)
        state.ballBCenterX = paramsB.x + ballB.width / 2f
        state.ballBCenterY = paramsB.y + ballB.height / 2f
    }

    fun repositionForCurrentMenuSize(
        windowManager: WindowManager,
        menuParams: WindowManager.LayoutParams,
    ) {
        val windowBounds = windowBoundsProvider.current()
        val menuHeight = currentMenuHeight()
        val menuTop = currentMenuTop(windowBounds)
        makeRoomAboveBall(menuHeight, menuTop, windowBounds, windowManager)
        menuParams.x = windowBounds.left + (windowBounds.width - currentMenuWidth()) / 2
        menuParams.y = calculateMenuWindowY(menuTop, menuHeight)
        updateLayout(windowManager, menuParams)
    }

    fun syncMenuToBall(windowManager: WindowManager) {
        if (!isMenuVisible()) return
        val windowBounds = windowBoundsProvider.current()
        val menuHeight = currentMenuHeight()
        val gapPx = (MENU_GAP_DP * density).toInt()
        val menuTop = currentMenuTop(windowBounds)

        overlayState.menuParams?.let { params ->
            params.x = windowBounds.left + (windowBounds.width - currentMenuWidth()) / 2
            params.y = calculateMenuWindowY(menuTop, menuHeight, gapPx)
            updateLayout(windowManager, params)
        }
    }

    fun centerMenuHorizontally(windowManager: WindowManager) {
        if (!isMenuVisible()) return
        val windowBounds = windowBoundsProvider.current()

        overlayState.menuParams?.let { params ->
            params.x = windowBounds.left + (windowBounds.width - currentMenuWidth()) / 2
            updateLayout(windowManager, params)
        }
    }

    fun constrainMovementWithMenu(
        deltaY: Int,
        paramsA: WindowManager.LayoutParams,
        ballA: View,
    ): Int {
        if (!isMenuVisible()) {
            return deltaY
        }

        val windowBounds = windowBoundsProvider.current()
        overlayState.menuParams ?: return deltaY
        val menuHeight = currentMenuHeight()
        val gapPx = (MENU_GAP_DP * density).toInt()
        val menuTop = currentMenuTop(windowBounds)
        val placement =
            calculateFloatingMenuVerticalPlacement(
                currentBallY = paramsA.y + deltaY,
                ballHeight = ballA.height,
                menuHeight = menuHeight,
                gap = gapPx,
                boundsTop = menuTop,
                boundsBottom = windowBounds.bottom,
            )
        return placement.ballY - paramsA.y
    }

    private fun isMenuVisible(): Boolean =
        state.isMenuShown && overlayState.menuView != null && overlayState.menuParams != null

    private fun currentMenuWidth(menu: View? = overlayState.menuView): Int {
        val laidOutWidth = menu?.width ?: 0
        if (laidOutWidth > 0 && menu?.isLayoutRequested == false) return laidOutWidth
        val measuredWidth = menu?.measuredWidth ?: 0
        return if (measuredWidth > 0) measuredWidth else (240 * density).toInt()
    }

    private fun currentMenuHeight(menu: View? = overlayState.menuView): Int {
        val laidOutHeight = menu?.height ?: 0
        if (laidOutHeight > 0 && menu?.isLayoutRequested == false) return laidOutHeight
        val measuredHeight = menu?.measuredHeight ?: 0
        return if (measuredHeight > 0) measuredHeight else (48 * density).toInt()
    }

    private fun calculateMenuWindowY(
        menuTop: Int,
        menuHeight: Int,
        gapPx: Int = (MENU_GAP_DP * density).toInt(),
    ): Int {
        return calculateFloatingMenuWindowY(
            ballY = paramsA.y,
            menuHeight = menuHeight,
            gap = gapPx,
            boundsTop = menuTop,
        )
    }

    private fun currentMenuTop(windowBounds: FloatingMenuWindowBounds): Int =
        calculateFloatingMenuTop(
            windowBounds = windowBounds,
            applicationWindowTop = windowBoundsProvider.currentApplicationWindowTop(),
        )

    private fun updateLayout(
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
    ) {
        val menuView = overlayState.menuView ?: return
        try {
            windowManager.updateViewLayout(menuView, params)
        } catch (e: Exception) {
            LogManager.e(LogTags.FLOATING_CONTROLLER, "Failed to update menu location: ${e.message}")
        }
    }
}

internal data class FloatingMenuVerticalPlacement(
    val ballY: Int,
    val menuY: Int,
)

internal fun calculateFloatingMenuVerticalPlacement(
    currentBallY: Int,
    ballHeight: Int,
    menuHeight: Int,
    gap: Int,
    boundsTop: Int,
    boundsBottom: Int,
): FloatingMenuVerticalPlacement {
    val minimumBallY = boundsTop + menuHeight + gap
    val maximumBallY = (boundsBottom - ballHeight).coerceAtLeast(boundsTop)
    val ballY = currentBallY.coerceAtLeast(minimumBallY).coerceAtMost(maximumBallY)
    val menuY = calculateFloatingMenuWindowY(ballY, menuHeight, gap, boundsTop)
    return FloatingMenuVerticalPlacement(ballY = ballY, menuY = menuY)
}

internal fun calculateFloatingMenuWindowY(
    ballY: Int,
    menuHeight: Int,
    gap: Int,
    boundsTop: Int,
): Int = (ballY - menuHeight - gap).coerceAtLeast(boundsTop)

internal fun calculateFloatingMenuTop(
    windowBounds: FloatingMenuWindowBounds,
    applicationWindowTop: Int,
): Int =
    if (windowBounds.width > windowBounds.height) {
        windowBounds.top
    } else {
        applicationWindowTop.coerceIn(windowBounds.top, windowBounds.bottom)
    }

internal class FloatingMenuMenuInteractionBinder(
    private val windowManager: WindowManager,
    private val ballA: View,
    private val ballB: View,
    private val actions: FloatingMenuActions,
    private val scope: CoroutineScope,
    private val hapticEnabled: Boolean,
    private val onToggleMoreActionsRow: () -> Unit,
) {
    fun bind(
        menu: View,
        onHideMenu: () -> Unit,
    ) {
        bindButtons(menu, onHideMenu)
    }

    private fun bindButtons(
        menu: View,
        onHideMenu: () -> Unit,
    ) {
        bindActionButton(menu, R.id.btn_back, 4, "Failed to send the Back key", onHideMenu)
        bindActionButton(menu, R.id.btn_home, 3, "Failed to send the Home key", onHideMenu)
        bindActionButton(menu, R.id.btn_recent, 187, "Failed to send the Recents key", onHideMenu)

        menu.findViewById<ImageButton>(R.id.btn_rotate_target)?.let { button ->
            bindSimpleButton(button, onHideMenu) {
                scope.launch {
                    actions.rotateTargetDevice().onFailure { error ->
                        LogManager.e(
                            LogTags.FLOATING_CONTROLLER_MSG,
                            "Failed to rotate target device: ${error.message}",
                            error,
                        )
                    }
                }
            }
        }

        menu.findViewById<ImageButton>(R.id.btn_keyboard)?.let { button ->
            bindSimpleButton(button, onHideMenu) {
                actions.showKeyboardInput()
            }
        }

        menu.findViewById<ImageButton>(R.id.btn_upload)?.let { button ->
            bindSimpleButton(button, onHideMenu) {
                actions.requestUploadFilePicker()
            }
        }

        menu.findViewById<ImageButton>(R.id.btn_layout_inspector)?.let { button ->
            bindSimpleButton(button, onHideMenu) {
                actions.requestLayoutInspectorRender()
            }
        }

        menu.findViewById<ImageButton>(R.id.btn_adapt_resolution)?.let { button ->
            val isAdapted = actions.isDeviceResolutionAdapted()
            button.contentDescription =
                if (isAdapted) {
                    RemoteTexts.REMOTE_RESTORE_DEVICE_RESOLUTION.get()
                } else {
                    RemoteTexts.REMOTE_ADAPT_DEVICE_RESOLUTION.get()
                }
            button.imageTintList =
                ColorStateList.valueOf(
                    if (isAdapted) ADAPTED_RESOLUTION_TINT else DEFAULT_MENU_ICON_TINT,
                )
            bindSimpleButton(button, onHideMenu) {
                scope.launch {
                    actions.toggleDeviceResolutionAdaptation()
                        .onFailure { error ->
                            LogManager.e(
                                LogTags.FLOATING_CONTROLLER_MSG,
                                "Failed to switch target device resolution: ${error.message}",
                                error,
                            )
                        }
                }
            }
        }

        menu.findViewById<ImageButton>(R.id.btn_menu)?.let { button ->
            button.setOnClickListener {
                if (hapticEnabled) {
                    performHapticFeedbackCompat(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                onToggleMoreActionsRow()
            }
        }

        menu.findViewById<ImageButton>(R.id.btn_close)?.setOnClickListener {
            if (hapticEnabled) {
                performHapticFeedbackCompat(ApiCompatHelper.getHapticFeedbackConstant("reject"))
            }
            scope.launch {
                onHideMenu()
                removeBallViews()
                actions.disconnect()
            }
        }
    }

    private fun bindActionButton(
        menu: View,
        buttonId: Int,
        keyCode: Int,
        failureLog: String,
        onHideMenu: () -> Unit,
    ) {
        menu.findViewById<ImageButton>(buttonId)?.let { button ->
            bindSimpleButton(button, onHideMenu) {
                scope.launch {
                    val result = actions.controlViewModel.sendKeyEvent(keyCode)
                    if (result.isFailure) {
                        LogManager.e(LogTags.FLOATING_CONTROLLER_MSG, "$failureLog: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        }
    }

    private fun bindSimpleButton(
        button: View,
        onHideMenu: () -> Unit,
        action: () -> Unit,
    ) {
        button.setOnClickListener {
            if (hapticEnabled) {
                performHapticFeedbackCompat(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            action()
            onHideMenu()
        }
    }

    private fun removeBallViews() {
        try {
            if (ballA.isAttachedToWindow) {
                windowManager.removeView(ballA)
            }
            if (ballB.isAttachedToWindow) {
                windowManager.removeView(ballB)
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.FLOATING_CONTROLLER, "Failed to remove sphere: ${e.message}")
        }
    }

    private companion object {
        const val ADAPTED_RESOLUTION_TINT = 0xFF0A84FF.toInt()
        const val DEFAULT_MENU_ICON_TINT = 0xFFFFFFFF.toInt()
    }
}

internal object HapticHelper {
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = ApiCompatHelper.getVibratorCompat(context)

    }

    fun vibrate(type: String = "tick") {
        ApiCompatHelper.vibrateCompat(vibrator, type)
    }
}

internal fun performHapticFeedbackCompat(feedbackConstant: Int) {
    val rejectConstant = ApiCompatHelper.getHapticFeedbackConstant("reject")
    val type =
        when (feedbackConstant) {
            HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.VIRTUAL_KEY,
            -> "tick"

            HapticFeedbackConstants.CONTEXT_CLICK -> "click"

            HapticFeedbackConstants.LONG_PRESS,
            rejectConstant,
            -> "heavy"

            else -> "tick"
        }
    HapticHelper.vibrate(type)
}
