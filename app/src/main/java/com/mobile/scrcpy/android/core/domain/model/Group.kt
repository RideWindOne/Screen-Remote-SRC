package com.mobile.scrcpy.android.core.domain.model

/**
 * 分组类型
 */
enum class GroupType {
    SESSION, // 会话分组
    AUTOMATION, // 自动化分组
}

/**
 * 设备分组
 */
data class DeviceGroup(
    val id: String,
    val name: String,
    val type: GroupType = GroupType.SESSION,
    val path: String = "/",
    val parentPath: String = "/",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    /**
     * 获取路径深度（层级）
     */
    fun getDepth(): Int = if (path == "/") 0 else path.count { it == '/' }

    /**
     * 检查是否为根分组
     */
    fun isRoot(): Boolean = path == "/"

    /**
     * 检查是否为某个路径的子分组
     */
    fun isChildOf(parentPath: String): Boolean = this.parentPath == parentPath

    /**
     * 检查是否为某个路径的后代分组（包括子、孙等）
     */
    fun isDescendantOf(ancestorPath: String): Boolean = path.startsWith("$ancestorPath/")
}

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
