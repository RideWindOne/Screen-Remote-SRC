package com.mobile.scrcpy.android.feature.remote.widget.floating

import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import com.mobile.scrcpy.android.R
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
