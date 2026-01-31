package com.mobile.scrcpy.android.core.designsystem.component

/**
 * 分组树形组件
 *
 * 本文件已重构，组件已拆分到以下文件：
 * - tree/TreeNode.kt - 树形节点 UI 组件
 * - tree/TreeRootItems.kt - 树形根节点 UI 组件
 *
 * 为保持向后兼容，本文件重新导出所有公开组件。
 * 所有组件现在都可以从 tree 包中直接导入。
 */

// 重新导出树形节点组件（使用 import 别名）
import com.mobile.scrcpy.android.core.designsystem.component.tree.TreeRootItemForManagement
import com.mobile.scrcpy.android.core.designsystem.component.tree.TreeNodeItemForManagement
import com.mobile.scrcpy.android.core.designsystem.component.tree.TreeRootItemForSelector
import com.mobile.scrcpy.android.core.designsystem.component.tree.TreeNodeItemForSelector
import com.mobile.scrcpy.android.core.designsystem.component.tree.TreeRootItem
import com.mobile.scrcpy.android.core.designsystem.component.tree.TreeNodeItem
