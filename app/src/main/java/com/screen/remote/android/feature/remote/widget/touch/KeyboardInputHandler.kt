package com.screen.remote.android.feature.remote.widget.touch

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.feature.remote.presentation.ControlViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 键盘输入处理组件
 * 隐藏的 TextField 用于接收键盘输入并转发到远程设备
 *
 * @param controlViewModel 控制 ViewModel
 * @param keyboardController 键盘控制器
 * @param onDismiss 关闭回调
 */
@Composable
fun KeyboardInputHandler(
    controlViewModel: ControlViewModel,
    keyboardController: SoftwareKeyboardController?,
    requestToken: Int,
    onDismiss: () -> Unit,
) {
    val hostView = LocalView.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var keyboardText by remember { mutableStateOf(TextFieldValue("")) }
    var lastTextLength by remember { mutableIntStateOf(0) }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .size(1.dp)
                .alpha(0.01f),
    ) {
        BasicTextField(
            value = keyboardText,
            onValueChange = { newValue ->
                val oldText = keyboardText.text
                val newText = newValue.text
                val oldLength = lastTextLength

                // 检测删除操作 - 只在实际删除一个字符时发送
                if (newText.length < oldText.length && newText.length == oldLength - 1) {
                    scope.launch {
                        controlViewModel.sendKeyEvent(67) // KEYCODE_DEL
                    }
                }
                // 检测新输入的字符（包括粘贴）
                else if (newText.length > oldText.length) {
                    // 获取所有新增的字符
                    val newChars = newText.substring(oldText.length)
                    scope.launch {
                        if (shouldUseClipboardPaste(newChars)) {
                            controlViewModel.setClipboardAndPaste(newChars)
                        } else {
                            controlViewModel.sendText(newChars)
                        }
                    }
                }

                lastTextLength = newText.length
                keyboardText = newValue
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .onKeyEvent { keyEvent ->
                        // 监听快捷键
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.isCtrlPressed) {
                            when (keyEvent.key) {
                                Key.A -> {
                                    // Ctrl+A: 全选
                                    scope.launch {
                                        controlViewModel.sendKeyEvent(
                                            keyCode = 29, // KEYCODE_A
                                            action = 0, // ACTION_DOWN
                                            metaState = 4096, // CTRL
                                        )
                                        delay(10)
                                        controlViewModel.sendKeyEvent(
                                            keyCode = 29,
                                            action = 1, // ACTION_UP
                                            metaState = 4096,
                                        )
                                    }
                                    true
                                }

                                Key.C -> {
                                    // Ctrl+C: 复制
                                    scope.launch {
                                        controlViewModel.sendKeyEvent(
                                            keyCode = 31, // KEYCODE_C
                                            action = 0,
                                            metaState = 4096,
                                        )
                                        delay(10)
                                        controlViewModel.sendKeyEvent(
                                            keyCode = 31,
                                            action = 1,
                                            metaState = 4096,
                                        )
                                    }
                                    true
                                }

                                Key.X -> {
                                    // Ctrl+X: 剪切
                                    scope.launch {
                                        controlViewModel.sendKeyEvent(
                                            keyCode = 52, // KEYCODE_X
                                            action = 0,
                                            metaState = 4096,
                                        )
                                        delay(10)
                                        controlViewModel.sendKeyEvent(
                                            keyCode = 52,
                                            action = 1,
                                            metaState = 4096,
                                        )
                                    }
                                    true
                                }

                                Key.V -> {
                                    // Sync the local clipboard through scrcpy before pasting. Sending
                                    // Ctrl+V alone would paste stale content from the remote device.
                                    scope.launch {
                                        val clipboard =
                                            hostView.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val text =
                                            clipboard
                                                ?.primaryClip
                                                ?.takeIf { clip -> clip.itemCount > 0 }
                                                ?.getItemAt(0)
                                                ?.coerceToText(hostView.context)
                                                ?.toString()
                                        if (text != null) {
                                            controlViewModel.setClipboardAndPaste(text)
                                        }
                                    }
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        } else {
                            false
                        }
                    },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        onDismiss()
                        keyboardController?.hide()
                        keyboardText = TextFieldValue("") // 清空输入
                        lastTextLength = 0
                    },
                ),
        )
    }

    // 自动请求焦点并显示键盘
    LaunchedEffect(requestToken) {
        LogManager.d(
            LogTags.CONTROL_HANDLER,
            "KeyboardInputHandler requesting local keyboard, token=$requestToken",
        )
        delay(120)
        try {
            focusRequester.requestFocus()
            hostView.requestFocus()
            delay(80)
            ApiCompatHelper.showSoftInput(hostView)
            keyboardController?.show()
        } catch (e: Exception) {
            LogManager.e(LogTags.CONTROL_HANDLER, "${RemoteTexts.REMOTE_FOCUS_REQUEST_FAILED.get()}: ${e.message}")
        }
    }
}

internal fun shouldUseClipboardPaste(text: String): Boolean =
    text.length > 1 ||
        text.toByteArray(Charsets.UTF_8).size > 300 ||
        text.any { character -> character.code > 0x7f || character == '\n' || character == '\r' || character == '\t' }
