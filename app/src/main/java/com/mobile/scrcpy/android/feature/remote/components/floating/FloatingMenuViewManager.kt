package com.mobile.scrcpy.android.feature.remote.components.floating

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.mobile.scrcpy.android.R
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper
import com.mobile.scrcpy.android.feature.session.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 菜单视图管理器
 * 负责菜单的显示、隐藏、位置更新
 */
internal class FloatingMenuViewManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val paramsA: WindowManager.LayoutParams,
    private val ballA: View,
    private val ballB: View,
    private val viewModel: MainViewModel,
    private val scope: CoroutineScope,
    private val state: FloatingMenuGestureState,
    private val hapticEnabled: Boolean,
) {
    private val density = context.resources.displayMetrics.density
    private val displayMetrics = context.resources.displayMetrics

    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null

    /**
     * 显示菜单
     */
    fun showMenu() {
        val parent = android.widget.FrameLayout(context)
        val menu = LayoutInflater.from(context).inflate(R.layout.floating_menu, parent, false)

        // 强制测量菜单尺寸
        menu.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        val menuWidth = if (menu.measuredWidth > 0) menu.measuredWidth else (240 * density).toInt()
        val menuHeight = if (menu.measuredHeight > 0) menu.measuredHeight else (48 * density).toInt()

        val params =
            WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION
                format = PixelFormat.TRANSLUCENT
                // 可触摸，不设置 FLAG_NOT_FOCUSABLE，让菜单能接收返回键
                flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.START

                // 垂直位置：菜单在小球上方，距离小球顶部 35dp
                y = (paramsA.y - menuHeight - 35 * density).toInt()

                // 水平位置：菜单水平居中对齐屏幕
                x = (displayMetrics.widthPixels - menuWidth) / 2

                // 限制菜单不超出屏幕顶部
                if (y < 0) y = 0
            }

        windowManager.addView(menu, params)
        menuView = menu
        menuParams = params
        state.isMenuShown = true

        setupMenuButtons(menu)
    }

    /**
     * 隐藏菜单
     */
    fun hideMenu() {
        menuView?.let { menu ->
            try {
                windowManager.removeView(menu)
            } catch (e: Exception) {
                Log.e(LogTags.FLOATING_CONTROLLER, "移除菜单失败: ${e.message}")
            }
        }
        menuView = null
        menuParams = null
        state.isMenuShown = false
    }

    /**
     * 更新菜单位置（跟随小球移动）
     */
    fun updateMenuPosition(
        deltaX: Int,
        deltaY: Int,
    ) {
        if (!state.isMenuShown || menuView == null || menuParams == null) return

        menuParams?.let { params ->
            // 菜单Y方向跟随小球移动
            params.y += deltaY

            // 菜单X方向保持在屏幕中央
            val menuWidth =
                if (menuView!!.measuredWidth > 0) {
                    menuView!!.measuredWidth
                } else {
                    (240 * density).toInt()
                }
            params.x = (displayMetrics.widthPixels - menuWidth) / 2

            try {
                windowManager.updateViewLayout(menuView, params)
            } catch (e: Exception) {
                Log.e(LogTags.FLOATING_CONTROLLER, "更新菜单位置失败: ${e.message}")
            }
        }
    }

    /**
     * 菜单居中对齐
     */
    fun centerMenuHorizontally() {
        if (!state.isMenuShown || menuView == null || menuParams == null) return

        val menuWidth =
            if (menuView!!.measuredWidth > 0) {
                menuView!!.measuredWidth
            } else {
                (240 * density).toInt()
            }
        menuParams!!.x = (displayMetrics.widthPixels - menuWidth) / 2

        try {
            windowManager.updateViewLayout(menuView, menuParams)
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "📍 菜单居中对齐")
        } catch (e: Exception) {
            Log.e(LogTags.FLOATING_CONTROLLER, "菜单居中失败: ${e.message}")
        }
    }

    /**
     * 贴边动画时更新菜单位置
     */
    fun animateMenuWithSnap(
        startMenuX: Int,
        startMenuY: Int,
        deltaX: Int,
        deltaY: Int,
        fraction: Float,
    ) {
        if (!state.isMenuShown || menuView == null || menuParams == null) return

        menuParams?.let { params ->
            params.x = (startMenuX + deltaX * fraction).toInt()
            params.y = (startMenuY + deltaY * fraction).toInt()
            windowManager.updateViewLayout(menuView, params)
        }
    }

    /**
     * 约束移动（考虑菜单边界）
     */
    fun constrainMovementWithMenu(
        deltaY: Int,
        paramsA: WindowManager.LayoutParams,
        ballA: View,
    ): Int {
        if (!state.isMenuShown || menuView == null || menuParams == null) {
            return deltaY
        }

        val menuHeight =
            if (menuView!!.measuredHeight > 0) {
                menuView!!.measuredHeight
            } else {
                (48 * density).toInt()
            }

        val menuAtTop = menuParams!!.y <= 0
        val ballAtBottomEdge = paramsA.y + ballA.height >= displayMetrics.heightPixels
        val menuBottom = menuParams!!.y + menuHeight
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
            val newMenuY = menuParams!!.y + deltaY
            if (newMenuY < 0) {
                finalDeltaY = -menuParams!!.y
            } else if (newMenuY + menuHeight > displayMetrics.heightPixels) {
                finalDeltaY = displayMetrics.heightPixels - menuHeight - menuParams!!.y
            }
        }

        return finalDeltaY
    }

    /**
     * 获取菜单X坐标
     */
    fun getMenuX(): Int = menuParams?.x ?: 0

    /**
     * 获取菜单Y坐标
     */
    fun getMenuY(): Int = menuParams?.y ?: 0

    /**
     * 设置菜单按钮
     */
    private fun setupMenuButtons(menu: View) {
        val hapticClickListener: (View, () -> Unit) -> Unit = { view, action ->
            view.setOnClickListener {
                if (hapticEnabled) {
                    performHapticFeedbackCompat(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                action()
            }
        }

        // 返回键
        menu.findViewById<android.widget.ImageButton>(R.id.btn_back)?.let { btn ->
            hapticClickListener(btn) {
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "⬅️ 返回按钮")
                scope.launch {
                    val result = viewModel.sendKeyEvent(4) // KEYCODE_BACK
                    if (result.isFailure) {
                        Log.e(
                            LogTags.FLOATING_CONTROLLER_MSG,
                            "发送返回键失败: ${result.exceptionOrNull()?.message}",
                        )
                    }
                }
                hideMenu()
            }
        }

        // 主页键
        menu.findViewById<android.widget.ImageButton>(R.id.btn_home)?.let { btn ->
            hapticClickListener(btn) {
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🏠 主页按钮")
                scope.launch {
                    val result = viewModel.sendKeyEvent(3) // KEYCODE_HOME
                    if (result.isFailure) {
                        Log.e(
                            LogTags.FLOATING_CONTROLLER_MSG,
                            "发送主页键失败: ${result.exceptionOrNull()?.message}",
                        )
                    }
                }
                hideMenu()
            }
        }

        // 最近任务
        menu.findViewById<android.widget.ImageButton>(R.id.btn_recent)?.let { btn ->
            hapticClickListener(btn) {
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "📋 最近任务按钮")
                scope.launch {
                    val result = viewModel.sendKeyEvent(187) // KEYCODE_APP_SWITCH
                    if (result.isFailure) {
                        Log.e(
                            LogTags.FLOATING_CONTROLLER_MSG,
                            "发送最近任务键失败: ${result.exceptionOrNull()?.message}",
                        )
                    }
                }
                hideMenu()
            }
        }

        // 键盘按钮
        menu.findViewById<android.widget.ImageButton>(R.id.btn_keyboard)?.let { btn ->
            hapticClickListener(btn) {
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "⌨️ 键盘按钮")
                // TODO: 实现键盘功能
                hideMenu()
            }
        }

        // 更多菜单按钮
        menu.findViewById<android.widget.ImageButton>(R.id.btn_menu)?.let { btn ->
            hapticClickListener(btn) {
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "📱 更多菜单按钮")
                // TODO: 实现更多菜单功能
                hideMenu()
            }
        }

        // 断开连接按钮
        menu.findViewById<android.widget.ImageButton>(R.id.btn_close)?.let { btn ->
            btn.setOnClickListener {
                if (hapticEnabled) {
                    performHapticFeedbackCompat(ApiCompatHelper.getHapticFeedbackConstant("reject"))
                }
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "❌ 断开连接")

                scope.launch {
                    hideMenu()
                    try {
                        if (ballA.isAttachedToWindow) {
                            windowManager.removeView(ballA)
                        }
                        if (ballB.isAttachedToWindow) {
                            windowManager.removeView(ballB)
                        }
                    } catch (e: Exception) {
                        Log.e(LogTags.FLOATING_CONTROLLER, "移除球体失败: ${e.message}")
                    }

                    viewModel.clearConnectStatus()
                    viewModel.disconnectFromDevice()
                }
            }
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        if (state.isMenuShown && menuView != null) {
            try {
                windowManager.removeView(menuView)
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "菜单已移除")
            } catch (e: Exception) {
                Log.e(LogTags.FLOATING_CONTROLLER, "❌ 移除菜单失败: ${e.message}")
            }
        }
        menuView = null
        menuParams = null
        state.isMenuShown = false
    }
}
