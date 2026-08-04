package com.screen.remote.android.core.domain.model

/**
 * 设备分组
 */
data class DeviceGroup(
    val id: String,
    val name: String,
    val path: String = "/",
    val parentPath: String = "/",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * 树形节点（用于 UI 展示）
 */
data class GroupTreeNode(
    val group: DeviceGroup,
    val children: List<GroupTreeNode> = emptyList(),
    val isExpanded: Boolean = false,
    val level: Int = 0,
)

/**
 * 默认分组
 */
object DefaultGroups {
    const val ALL_DEVICES = "all_devices"
    const val UNGROUPED = "ungrouped"
}
