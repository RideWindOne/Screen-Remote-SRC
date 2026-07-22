package com.screen.remote.android.core.designsystem.component.tree

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.domain.model.GroupTreeNode

/**
 * 树形结构展开状态管理
 *
 * 用于管理树形结构中节点的展开/折叠状态。
 */
class TreeExpandState(
    initialExpandedPaths: Set<String> = emptySet(),
) {
    var expandedPaths by mutableStateOf(initialExpandedPaths)
        private set

    /**
     * 切换路径的展开状态
     */
    fun togglePath(path: String) {
        expandedPaths =
            if (expandedPaths.contains(path)) {
                expandedPaths - path
            } else {
                expandedPaths + path
            }
    }

    /**
     * 展开指定路径
     */
    fun expandPath(path: String) {
        if (!expandedPaths.contains(path)) {
            expandedPaths = expandedPaths + path
        }
    }

    /**
     * 折叠指定路径
     */
    fun collapsePath(path: String) {
        if (expandedPaths.contains(path)) {
            expandedPaths = expandedPaths - path
        }
    }

    /**
     * 展开多个路径
     */
    fun expandPaths(paths: Set<String>) {
        expandedPaths = expandedPaths + paths
    }

    /**
     * 折叠所有路径
     */
    fun collapseAll() {
        expandedPaths = emptySet()
    }

    /**
     * 展开所有路径
     */
    fun expandAll(allPaths: Set<String>) {
        expandedPaths = allPaths
    }

    /**
     * 检查路径是否已展开
     */
    fun isExpanded(path: String): Boolean = expandedPaths.contains(path)
}

/**
 * 记住树形展开状态
 */
@Composable
fun rememberTreeExpandState(initialExpandedPaths: Set<String> = emptySet()): TreeExpandState =
    remember {
        TreeExpandState(initialExpandedPaths)
    }

/**
 * 树形选择状态管理
 *
 * 用于管理树形结构中节点的选择状态（单选）。
 */
class TreeSelectionState<T>(
    initialSelection: T? = null,
) {
    var selectedItem by mutableStateOf(initialSelection)
        private set

    /**
     * 选择项
     */
    fun select(item: T) {
        selectedItem = item
    }

    /**
     * 切换选择（如果已选中则取消选择）
     */
    fun toggle(item: T) {
        selectedItem = if (selectedItem == item) null else item
    }

    /**
     * 清除选择
     */
    fun clear() {
        selectedItem = null
    }

    /**
     * 检查是否选中
     */
    fun isSelected(item: T): Boolean = selectedItem == item
}

/**
 * 记住树形选择状态
 */
@Composable
fun <T> rememberTreeSelectionState(initialSelection: T? = null): TreeSelectionState<T> =
    remember {
        TreeSelectionState(initialSelection)
    }

/**
 * 树形多选状态管理
 *
 * 用于管理树形结构中节点的多选状态。
 */
class TreeMultiSelectionState<T>(
    initialSelection: Set<T> = emptySet(),
) {
    var selectedItems by mutableStateOf(initialSelection)
        private set

    /**
     * 添加选择项
     */
    fun add(item: T) {
        if (!selectedItems.contains(item)) {
            selectedItems = selectedItems + item
        }
    }

    /**
     * 移除选择项
     */
    fun remove(item: T) {
        if (selectedItems.contains(item)) {
            selectedItems = selectedItems - item
        }
    }

    /**
     * 切换选择
     */
    fun toggle(item: T) {
        selectedItems =
            if (selectedItems.contains(item)) {
                selectedItems - item
            } else {
                selectedItems + item
            }
    }

    /**
     * 清除所有选择
     */
    fun clear() {
        selectedItems = emptySet()
    }

    /**
     * 设置选择项
     */
    fun setSelection(items: Set<T>) {
        selectedItems = items
    }

    /**
     * 检查是否选中
     */
    fun isSelected(item: T): Boolean = selectedItems.contains(item)

    /**
     * 获取选择数量
     */
    fun count(): Int = selectedItems.size
}

/**
 * 记住树形多选状态
 */
@Composable
fun <T> rememberTreeMultiSelectionState(initialSelection: Set<T> = emptySet()): TreeMultiSelectionState<T> =
    remember {
        TreeMultiSelectionState(initialSelection)
    }

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

    fun findNodeByPath(
        nodes: List<GroupTreeNode>,
        path: String,
    ): GroupTreeNode? {
        for (node in nodes) {
            if (node.group.path == path) {
                return node
            }
            val found = findNodeByPath(node.children, path)
            if (found != null) {
                return found
            }
        }
        return null
    }

    fun getAllPaths(nodes: List<GroupTreeNode>): List<String> {
        val paths = mutableListOf<String>()

        fun collectPaths(nodeList: List<GroupTreeNode>) {
            for (node in nodeList) {
                paths.add(node.group.path)
                collectPaths(node.children)
            }
        }
        collectPaths(nodes)
        return paths
    }

    fun pathExists(
        nodes: List<GroupTreeNode>,
        path: String,
    ): Boolean = findNodeByPath(nodes, path) != null

    fun getDescendantPaths(node: GroupTreeNode): List<String> {
        val paths = mutableListOf(node.group.path)

        fun collectDescendants(nodeList: List<GroupTreeNode>) {
            for (child in nodeList) {
                paths.add(child.group.path)
                collectDescendants(child.children)
            }
        }
        collectDescendants(node.children)
        return paths
    }

    fun canMoveTo(
        sourcePath: String,
        targetPath: String,
        nodes: List<GroupTreeNode>,
    ): Boolean {
        if (sourcePath == targetPath) return false

        val sourceNode = findNodeByPath(nodes, sourcePath) ?: return false
        val descendantPaths = getDescendantPaths(sourceNode)
        return targetPath !in descendantPaths
    }
}
