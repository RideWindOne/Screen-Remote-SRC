package com.screen.remote.android.feature.remote.widget.floating

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.screen.remote.android.R
import kotlinx.coroutines.CoroutineScope

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
