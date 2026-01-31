package com.mobile.scrcpy.android.core.designsystem.component.tree

import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupTreeNode

/**
 * 树形结构操作工具类
 *
 * 提供树形结构的通用操作方法，包括构建、查找、验证等。
 */
object TreeActions {
    /**
     * 构建分组树形结构
     *
     * 将扁平的分组列表转换为树形结构，根据 parentPath 建立父子关系。
     * 树形结构从根路径 "/" 开始，递归构建所有子节点。
     *
     * @param groups 扁平的分组列表，每个分组包含 path 和 parentPath 信息
     * @return 根节点列表（parentPath 为 "/" 的所有分组），按名称排序
     *
     * @see DeviceGroup 分组数据模型
     * @see GroupTreeNode 树形节点模型
     *
     * 示例：
     * ```
     * 输入: [
     *   DeviceGroup(path="/A", parentPath="/"),
     *   DeviceGroup(path="/A/B", parentPath="/A"),
     *   DeviceGroup(path="/C", parentPath="/")
     * ]
     *
     * 输出: [
     *   GroupTreeNode(group=A, children=[
     *     GroupTreeNode(group=B, children=[], level=1)
     *   ], level=0),
     *   GroupTreeNode(group=C, children=[], level=0)
     * ]
     * ```
     */
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

        // 构建根节点的子节点
        groups
            .filter { it.parentPath == "/" }
            .sortedBy { it.name }
            .forEach { group ->
                buildNode(group.path, 0)?.let { rootNodes.add(it) }
            }

        return rootNodes
    }

    /**
     * 查找指定路径的节点
     *
     * @param nodes 树形节点列表
     * @param path 要查找的路径
     * @return 找到的节点，如果不存在则返回 null
     */
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

    /**
     * 获取所有节点的路径列表
     *
     * @param nodes 树形节点列表
     * @return 所有节点的路径列表
     */
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

    /**
     * 检查路径是否存在
     *
     * @param nodes 树形节点列表
     * @param path 要检查的路径
     * @return 路径是否存在
     */
    fun pathExists(
        nodes: List<GroupTreeNode>,
        path: String,
    ): Boolean = findNodeByPath(nodes, path) != null

    /**
     * 获取节点的所有子孙节点路径
     *
     * @param node 起始节点
     * @return 所有子孙节点的路径列表（包括起始节点自身）
     */
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

    /**
     * 检查是否可以将节点移动到目标路径
     *
     * @param sourcePath 源节点路径
     * @param targetPath 目标父节点路径
     * @param nodes 树形节点列表
     * @return 是否可以移动
     */
    fun canMoveTo(
        sourcePath: String,
        targetPath: String,
        nodes: List<GroupTreeNode>,
    ): Boolean {
        // 不能移动到自己
        if (sourcePath == targetPath) return false

        // 不能移动到自己的子孙节点
        val sourceNode = findNodeByPath(nodes, sourcePath) ?: return false
        val descendantPaths = getDescendantPaths(sourceNode)
        if (targetPath in descendantPaths) return false

        return true
    }
}
