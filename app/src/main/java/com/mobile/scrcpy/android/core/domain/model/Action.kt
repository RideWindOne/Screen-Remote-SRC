package com.mobile.scrcpy.android.core.domain.model

/**
 * Scrcpy 动作
 */
data class ScrcpyAction(
    val id: String,
    val name: String,
    val type: ActionType,
    val commands: List<String>,
)

/**
 * 动作类型
 */
enum class ActionType {
    CONVERSATION,
    AUTOMATION,
}
