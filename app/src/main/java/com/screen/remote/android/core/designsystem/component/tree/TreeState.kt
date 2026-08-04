package com.screen.remote.android.core.designsystem.component.tree

import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.domain.model.GroupTreeNode

object TreeActions {
    fun buildGroupTree(groups: List<DeviceGroup>): List<GroupTreeNode> {
        val groupMap = groups.associateBy { it.path }
        val rootNodes = mutableListOf<GroupTreeNode>()

        fun buildNode(
            path: String,
            level: Int,
        ): GroupTreeNode? {
            val group = groupMap[path] ?: return null
            val children =
                groups
                    .filter { it.parentPath == path }
                    .sortedBy { it.name }
                    .mapNotNull { buildNode(it.path, level + 1) }
            return GroupTreeNode(group, children, false, level)
        }

        groups
            .filter { it.parentPath == "/" }
            .sortedBy { it.name }
            .forEach { group ->
                buildNode(group.path, 0)?.let { rootNodes.add(it) }
            }

        return rootNodes
    }

}
