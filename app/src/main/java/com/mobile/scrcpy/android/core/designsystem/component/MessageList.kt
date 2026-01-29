package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 消息项数据类
 */
data class MessageItem(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String = "",
    val error: String? = null,
)

/**
 * 消息列表状态管理类
 */
class MessageListState {
    private val _messages = mutableStateListOf<MessageItem>()
    val messages: List<MessageItem> get() = _messages

    /**
     * 添加消息
     */
    fun addMessage(message: MessageItem) {
        _messages.add(message)
    }

    /**
     * 更新消息（根据 id）
     */
    fun updateMessage(
        id: String,
        update: (MessageItem) -> MessageItem,
    ) {
        val index = _messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            _messages[index] = update(_messages[index])
        }
    }

    /**
     * 清空所有消息
     */
    fun clear() {
        _messages.clear()
    }
}

/**
 * 记住消息列表状态
 */
@Composable
fun rememberMessageListState(): MessageListState = remember { MessageListState() }

/**
 * 消息列表组件
 *
 * @param state 消息列表状态
 * @param title 标题文字
 * @param modifier 修饰符
 */
@Composable
fun MessageList(
    state: MessageListState,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 始终显示标题
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )

        // 显示消息列表
        state.messages.forEach { message ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message.icon,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column {
                    Text(
                        text = message.title,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (message.subtitle.isNotEmpty()) {
                        Text(
                            text = message.subtitle,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (message.error != null) {
                        Text(
                            text = message.error,
                            color = Color.Red.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
