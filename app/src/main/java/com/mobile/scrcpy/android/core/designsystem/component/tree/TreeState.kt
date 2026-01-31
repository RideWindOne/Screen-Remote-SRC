package com.mobile.scrcpy.android.core.designsystem.component.tree

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
