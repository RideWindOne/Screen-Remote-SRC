package com.mobile.scrcpy.android.core.designsystem.component

import com.mobile.scrcpy.android.core.designsystem.component.tree.TreeActions
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupTreeNode

/**
 * 分组树形结构工具类
 *
 * 统一管理树形结构构建逻辑，避免重复代码。
 * 用于将扁平的分组列表转换为层级树形结构，便于 UI 展示。
 *
 * @deprecated 使用 tree.TreeActions 代替
 */
@Deprecated(
    "Use tree.TreeActions instead",
    ReplaceWith(
        "TreeActions",
        "com.mobile.scrcpy.android.core.designsystem.component.tree.TreeActions",
    ),
)
object GroupTreeUtils {
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
    fun buildGroupTree(groups: List<DeviceGroup>): List<GroupTreeNode> = TreeActions.buildGroupTree(groups)
}
