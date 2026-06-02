package com.screen.remote.android.feature.remote.widget.floating

import android.content.Context
import android.graphics.PixelFormat
import android.os.Vibrator
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import com.screen.remote.android.R
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.ApiCompatHelper
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
            ballA = ballA,
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
        val params = positionController.createInitialLayoutParams(menu)
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

        val oldHeight = measureMenuHeight(menu)
        val newVisible = !overlayState.isMoreActionsVisible
        moreActionsRow.visibility = if (newVisible) View.VISIBLE else View.GONE
        val newHeight = measureMenuHeight(menu)
        val heightDelta = newHeight - oldHeight

        params.y = (params.y - heightDelta).coerceAtLeast(0)
        overlayState.isMoreActionsVisible = newVisible

        runCatching { windowManager.updateViewLayout(menu, params) }
    }

    fun updateMenuPosition(
        deltaX: Int,
        deltaY: Int,
    ) {
        positionController.updateMenuPosition(
            windowManager = windowManager,
            deltaX = deltaX,
            deltaY = deltaY,
        )
    }

    fun centerMenuHorizontally() {
        positionController.centerMenuHorizontally(windowManager)
    }

    fun animateMenuWithSnap(
        startMenuX: Int,
        startMenuY: Int,
        deltaX: Int,
        deltaY: Int,
        fraction: Float,
    ) {
        positionController.animateMenuWithSnap(
            windowManager = windowManager,
            startMenuX = startMenuX,
            startMenuY = startMenuY,
            deltaX = deltaX,
            deltaY = deltaY,
            fraction = fraction,
        )
    }

    fun constrainMovementWithMenu(
        deltaY: Int,
        paramsA: WindowManager.LayoutParams,
        ballA: View,
    ): Int = positionController.constrainMovementWithMenu(deltaY, paramsA, ballA)

    fun getMenuX(): Int = overlayState.menuParams?.x ?: 0

    fun getMenuY(): Int = overlayState.menuParams?.y ?: 0

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
        flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }

internal class FloatingMenuMenuPositionController(
    context: Context,
    private val paramsA: WindowManager.LayoutParams,
    private val ballA: View,
    private val state: FloatingMenuGestureState,
    private val overlayState: FloatingMenuOverlayState,
) {
    companion object {
        private const val MENU_GAP_DP = 16f
    }

    private val density = context.resources.displayMetrics.density
    private val displayMetrics = context.resources.displayMetrics

    fun createInitialLayoutParams(menu: View): WindowManager.LayoutParams {
        val menuWidth = currentMenuWidth(menu)
        val menuHeight = currentMenuHeight(menu)
        val x = (displayMetrics.widthPixels - menuWidth) / 2
        val y = (paramsA.y - menuHeight - MENU_GAP_DP * density).toInt().coerceAtLeast(0)
        return createFloatingMenuLayoutParams(x = x, y = y)
    }

    fun updateMenuPosition(
        windowManager: WindowManager,
        deltaX: Int,
        deltaY: Int,
    ) {
        if (!isMenuVisible()) return

        overlayState.menuParams?.let { params ->
            params.y += deltaY
            params.x = (displayMetrics.widthPixels - currentMenuWidth()) / 2
            updateLayout(windowManager, params)
        }
    }

    fun centerMenuHorizontally(windowManager: WindowManager) {
        if (!isMenuVisible()) return

        overlayState.menuParams?.let { params ->
            params.x = (displayMetrics.widthPixels - currentMenuWidth()) / 2
            updateLayout(windowManager, params)
            FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER_MSG, "📍 菜单居中对齐")
        }
    }

    fun animateMenuWithSnap(
        windowManager: WindowManager,
        startMenuX: Int,
        startMenuY: Int,
        deltaX: Int,
        deltaY: Int,
        fraction: Float,
    ) {
        if (!isMenuVisible()) return

        overlayState.menuParams?.let { params ->
            params.x = (startMenuX + deltaX * fraction).toInt()
            params.y = (startMenuY + deltaY * fraction).toInt()
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

        val menuParams = overlayState.menuParams ?: return deltaY
        val menuHeight = currentMenuHeight()
        val menuAtTop = menuParams.y <= 0
        val ballAtBottomEdge = paramsA.y + ballA.height >= displayMetrics.heightPixels
        val menuBottom = menuParams.y + menuHeight
        val menuAtBottom = menuBottom >= displayMetrics.heightPixels

        var finalDeltaY = deltaY
        var yMovementLocked = false

        if (menuAtTop && deltaY < 0) {
            finalDeltaY = 0
            yMovementLocked = true
        }

        if ((ballAtBottomEdge || menuAtBottom) && deltaY > 0) {
            finalDeltaY = 0
            yMovementLocked = true
        }

        if (!yMovementLocked) {
            val newMenuY = menuParams.y + deltaY
            if (newMenuY < 0) {
                finalDeltaY = -menuParams.y
            } else if (newMenuY + menuHeight > displayMetrics.heightPixels) {
                finalDeltaY = displayMetrics.heightPixels - menuHeight - menuParams.y
            }
        }

        return finalDeltaY
    }

    private fun isMenuVisible(): Boolean =
        state.isMenuShown && overlayState.menuView != null && overlayState.menuParams != null

    private fun currentMenuWidth(menu: View? = overlayState.menuView): Int {
        val measuredWidth = menu?.measuredWidth ?: 0
        return if (measuredWidth > 0) measuredWidth else (240 * density).toInt()
    }

    private fun currentMenuHeight(menu: View? = overlayState.menuView): Int {
        val measuredHeight = menu?.measuredHeight ?: 0
        return if (measuredHeight > 0) measuredHeight else (48 * density).toInt()
    }

    private fun updateLayout(
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
    ) {
        val menuView = overlayState.menuView ?: return
        try {
            windowManager.updateViewLayout(menuView, params)
        } catch (e: Exception) {
            LogManager.e(LogTags.FLOATING_CONTROLLER, "更新菜单位置失败: ${e.message}")
        }
    }
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
        bindBackKey(menu)
    }

    private fun bindBackKey(menu: View) {
        menu.isFocusable = true
        menu.isFocusableInTouchMode = true
        menu.requestFocus()
        menu.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                scope.launch {
                    val result = actions.controlViewModel.sendKeyEvent(4)
                    if (result.isFailure) {
                        LogManager.e(LogTags.FLOATING_CONTROLLER, "发送返回键失败: ${result.exceptionOrNull()?.message}")
                    } else {
                        FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER, "返回键已发送到远程设备")
                    }
                }
                true
            } else {
                false
            }
        }
    }

    private fun bindButtons(
        menu: View,
        onHideMenu: () -> Unit,
    ) {
        bindActionButton(menu, R.id.btn_back, "⬅️ 返回按钮", 4, "发送返回键失败", onHideMenu)
        bindActionButton(menu, R.id.btn_home, "🏠 主页按钮", 3, "发送主页键失败", onHideMenu)
        bindActionButton(menu, R.id.btn_recent, "📋 最近任务按钮", 187, "发送最近任务键失败", onHideMenu)

        menu.findViewById<ImageButton>(R.id.btn_keyboard)?.let { button ->
            bindSimpleButton(button, onHideMenu) {
                FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER_MSG, "⌨️ 键盘按钮")
                actions.showKeyboardInput()
            }
        }

        menu.findViewById<ImageButton>(R.id.btn_upload)?.let { button ->
            bindSimpleButton(button, onHideMenu) {
                FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER_MSG, "📤 上传按钮")
                actions.requestUploadFilePicker()
            }
        }

        menu.findViewById<ImageButton>(R.id.btn_layout_inspector)?.let { button ->
            bindSimpleButton(button, onHideMenu) {
                FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER_MSG, "🧩 布局分析按钮")
                actions.requestLayoutInspectorRender()
            }
        }

        menu.findViewById<ImageButton>(R.id.btn_menu)?.let { button ->
            button.setOnClickListener {
                if (hapticEnabled) {
                    performHapticFeedbackCompat(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER_MSG, "📱 更多菜单按钮")
                onToggleMoreActionsRow()
            }
        }

        menu.findViewById<ImageButton>(R.id.btn_close)?.setOnClickListener {
            if (hapticEnabled) {
                performHapticFeedbackCompat(ApiCompatHelper.getHapticFeedbackConstant("reject"))
            }
            FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER_MSG, "❌ 断开连接")

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
        logMessage: String,
        keyCode: Int,
        failureLog: String,
        onHideMenu: () -> Unit,
    ) {
        menu.findViewById<ImageButton>(buttonId)?.let { button ->
            bindSimpleButton(button, onHideMenu) {
                FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER_MSG, logMessage)
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
            LogManager.e(LogTags.FLOATING_CONTROLLER, "移除球体失败: ${e.message}")
        }
    }
}

internal object HapticHelper {
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = ApiCompatHelper.getVibratorCompat(context)

        if (vibrator?.hasVibrator() == true) {
            FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER, "Vibrator 初始化成功")
        } else {
            LogManager.w(LogTags.FLOATING_CONTROLLER, "设备不支持触感")
        }
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
