package com.mobile.scrcpy.android.feature.remote.widget.floating

import android.content.Context
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.mobile.scrcpy.android.core.common.LogTags

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
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "📍 菜单居中对齐")
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
            Log.e(LogTags.FLOATING_CONTROLLER, "更新菜单位置失败: ${e.message}")
        }
    }
}
