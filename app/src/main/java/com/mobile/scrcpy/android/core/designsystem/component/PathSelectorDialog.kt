package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.designsystem.component.tree.TreeNodeItem
import com.mobile.scrcpy.android.core.designsystem.component.tree.TreeRootItem
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts

/**
 * 路径选择对话框（树形展示）
 *
 * 用于选择分组路径，支持树形结构展示和展开/折叠。
 * 适用场景：
 * - 创建/编辑分组时选择父路径
 * - 其他需要选择分组路径的场景
 *
 * @param groups 可选的分组列表
 * @param selectedPath 当前选中的路径
 * @param onPathSelected 路径选择回调
 * @param onDismiss 关闭对话框回调
 */
@Composable
fun PathSelectorDialog(
    groups: List<DeviceGroup>,
    selectedPath: String,
    onPathSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentSelection by remember { mutableStateOf(selectedPath) }
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }

    // 构建树形结构
    val treeNodes =
        remember(groups) {
            GroupTreeUtils.buildGroupTree(groups)
        }

    DialogPage(
        title = SessionTexts.GROUP_SELECT_PATH.get(),
        onDismiss = onDismiss,
        showBackButton = false,
        leftButtonText = CommonTexts.BUTTON_CANCEL.get(),
        rightButtonText = CommonTexts.BUTTON_CONFIRM.get(),
        onRightButtonClick = {
            onPathSelected(currentSelection)
        },
        enableScroll = false,
        horizontalPadding = 10.dp,
    ) {
        // 整个列表用一个 Card 包裹（参考 SettingsCard 样式）
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 10.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            LazyColumn {
                // 根目录
                item {
                    TreeRootItem(
                        isSelected = currentSelection == "/",
                        hasChildren = treeNodes.isNotEmpty(),
                        isExpanded = expandedPaths.contains("/"),
                        onToggleExpand = {
                            expandedPaths =
                                if (expandedPaths.contains("/")) {
                                    expandedPaths - "/"
                                } else {
                                    expandedPaths + "/"
                                }
                        },
                        onClick = { currentSelection = "/" },
                    )
                }

                // 树形节点（只在根目录展开时显示）
                if (expandedPaths.contains("/")) {
                    items(treeNodes.size) { index ->
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )

                        TreeNodeItem(
                            node = treeNodes[index],
                            selectedPath = currentSelection,
                            expandedPaths = expandedPaths,
                            onToggleExpand = { path ->
                                expandedPaths =
                                    if (expandedPaths.contains(path)) {
                                        expandedPaths - path
                                    } else {
                                        expandedPaths + path
                                    }
                            },
                            onSelect = { currentSelection = it },
                        )
                    }
                }
            }
        }
    }
}
