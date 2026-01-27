package com.mobile.scrcpy.android.feature.remote.components.touch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.feature.remote.viewmodel.ControlViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.mobile.scrcpy.android.core.i18n.RemoteTexts
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
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var keyboardText by remember { mutableStateOf(TextFieldValue("")) }
    var lastTextLength by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .size(1.dp)
            .offset(x = (-1000).dp, y = (-1000).dp) // 移到屏幕外
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
                        // 使用 INJECT_TEXT，配合 keyboard=uhid 支持所有语言
                        controlViewModel.sendText(newChars)
                    }
                }

                lastTextLength = newText.length
                keyboardText = newValue
            },
            modifier = Modifier
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
                                        metaState = 4096 // CTRL
                                    )
                                    delay(10)
                                    controlViewModel.sendKeyEvent(
                                        keyCode = 29,
                                        action = 1, // ACTION_UP
                                        metaState = 4096
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
                                        metaState = 4096
                                    )
                                    delay(10)
                                    controlViewModel.sendKeyEvent(
                                        keyCode = 31,
                                        action = 1,
                                        metaState = 4096
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
                                        metaState = 4096
                                    )
                                    delay(10)
                                    controlViewModel.sendKeyEvent(
                                        keyCode = 52,
                                        action = 1,
                                        metaState = 4096
                                    )
                                }
                                true
                            }

                            Key.V -> {
                                // Ctrl+V: 粘贴
                                scope.launch {
                                    controlViewModel.sendKeyEvent(
                                        keyCode = 50, // KEYCODE_V
                                        action = 0,
                                        metaState = 4096
                                    )
                                    delay(10)
                                    controlViewModel.sendKeyEvent(
                                        keyCode = 50,
                                        action = 1,
                                        metaState = 4096
                                    )
                                }
                                true
                            }

                            else -> false
                        }
                    } else {
                        false
                    }
                },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    onDismiss()
                    keyboardController?.hide()
                    keyboardText = TextFieldValue("") // 清空输入
                    lastTextLength = 0
                }
            )
        )
    }

    // 自动请求焦点并显示键盘
    LaunchedEffect(Unit) {
        delay(200) // 增加延迟，确保 TextField 已渲染
        try {
            focusRequester.requestFocus()
            delay(100)
            keyboardController?.show()
        } catch (e: Exception) {
            LogManager.e(LogTags.CONTROL_HANDLER, "${RemoteTexts.REMOTE_FOCUS_REQUEST_FAILED.get()}: ${e.message}")
        }
    }
}
