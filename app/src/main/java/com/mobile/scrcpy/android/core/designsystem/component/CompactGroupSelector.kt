package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.manager.LanguageManager.isChinese
import com.mobile.scrcpy.android.core.domain.model.DefaultGroups
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.i18n.SessionTexts

/**
 * 紧凑分组选择器（显示在 Tab 栏右侧）
 * 只显示上一级和当前级别
 */
@Composable
fun CompactGroupSelector(
    groups: List<DeviceGroup>,
    selectedGroupPath: String,
    onGroupSelected: (String) -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }
    var clickedLevel by remember { mutableStateOf<String?>(null) } // 记录点击的是哪一级
    val isDarkTheme = isSystemInDarkTheme()

    // 解析当前路径
    val pathParts =
        if (selectedGroupPath == DefaultGroups.ALL_DEVICES || selectedGroupPath == DefaultGroups.UNGROUPED) {
            emptyList()
        } else {
            selectedGroupPath.split("/").filter { it.isNotEmpty() }
        }

    // 获取父级路径和名称
    val (parentPath, parentName) =
        when {
            pathParts.isEmpty() -> {
                null to null
            }

            pathParts.size == 1 -> {
                DefaultGroups.ALL_DEVICES to SessionTexts.GROUP_ALL.get()
            }

            else -> {
                val path = "/" + pathParts.dropLast(1).joinToString("/")
                val name = pathParts[pathParts.size - 2] // 倒数第二级的名称
                path to (if (name.length > 10) name.take(10) + "..." else name)
            }
        }

    // 获取当前级别名称（最多10个字符）
    val currentLevelName =
        if (pathParts.isEmpty()) {
            SessionTexts.GROUP_ALL.get()
        } else {
            val name = pathParts.last()
            if (name.length > 10) name.take(10) + "..." else name
        }

    // 外层容器：添加圆角背景
    Row(
        modifier =
            Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 根目录时显示"全部"，否则显示上一级
        if (pathParts.isEmpty()) {
            // 根目录：只显示"全部"
            // 检查是否有一级分组
            val hasFirstLevelGroups = groups.filter { it.parentPath == "/" }.isNotEmpty()

            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (isDarkTheme) {
                                AppColors.darkIOSSelectedBackground
                            } else {
                                AppColors.iOSSelectedBackground
                            },
                        ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .then(
                                if (hasFirstLevelGroups) {
                                    Modifier.clickable {
                                        clickedLevel = "parent"
                                        showDropdown = true
                                    }
                                } else {
                                    Modifier // 没有分组时不可点击
                                },
                            ).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = SessionTexts.GROUP_ALL.get(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // 下拉菜单 - 只有存在一级分组时才显示
                if (hasFirstLevelGroups) {
                    IOSStyledDropdownMenu(
                        expanded = showDropdown && clickedLevel == "parent",
                        offset = DpOffset(0.dp, 98.dp),
                        onDismissRequest = {
                            showDropdown = false
                            clickedLevel = null
                        },
                    ) {
                        val childGroups = groups.filter { it.parentPath == "/" }

                        childGroups.forEach { group ->
                            val displayName =
                                if (group.name.length > 10) {
                                    group.name.take(10) + "..."
                                } else {
                                    group.name
                                }
                            IOSStyledDropdownMenuItem(
                                text = displayName,
                                onClick = {
                                    onGroupSelected(group.path)
                                    showDropdown = false
                                    clickedLevel = null
                                },
                            )
                        }
                    }
                }
            }
        } else {
            // 非根目录：显示上一级（可点击，显示下拉菜单）
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (isDarkTheme) {
                                AppColors.darkIOSSelectedBackground
                            } else {
                                AppColors.iOSSelectedBackground
                            },
                        ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .clickable {
                                clickedLevel = "parent"
                                showDropdown = true
                            }.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = parentName ?: SessionTexts.GROUP_ALL.get(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // 下拉菜单 - 显示父级的兄弟目录
                IOSStyledDropdownMenu(
                    offset = DpOffset(0.dp, 98.dp),
                    expanded = showDropdown && clickedLevel == "parent",
                    onDismissRequest = {
                        showDropdown = false
                        clickedLevel = null
                    },
                ) {
                    // 如果父级是根目录，显示全部设备 + 一级分组
                    if (parentPath == DefaultGroups.ALL_DEVICES) {
                        IOSStyledDropdownMenuItem(
                            text = SessionTexts.GROUP_ALL.get(),
                            onClick = {
                                onGroupSelected(DefaultGroups.ALL_DEVICES)
                                showDropdown = false
                                clickedLevel = null
                            },
                        )

                        val childGroups = groups.filter { it.parentPath == "/" }
                        childGroups.forEach { group ->
                            val displayName =
                                if (group.name.length > 10) {
                                    group.name.take(10) + "..."
                                } else {
                                    group.name
                                }
                            IOSStyledDropdownMenuItem(
                                text = displayName,
                                onClick = {
                                    onGroupSelected(group.path)
                                    showDropdown = false
                                    clickedLevel = null
                                },
                            )
                        }
                    } else {
                        // 第一项：返回到父级（添加箭头）
                        IOSStyledDropdownMenuItem(
                            text = (if (isChinese()) "返回" else "Back"),
                            onClick = {
                                onGroupSelected(parentPath ?: DefaultGroups.ALL_DEVICES)
                                showDropdown = false
                                clickedLevel = null
                            },
                        )

                        // 显示父级的兄弟目录（排除父级本身，因为点击它等价于"返回"）
                        val grandparentPathForSiblings =
                            if (pathParts.size == 2) {
                                "/"
                            } else {
                                "/" + pathParts.dropLast(2).joinToString("/")
                            }

                        val siblingGroups =
                            groups.filter {
                                it.parentPath == grandparentPathForSiblings && it.path != parentPath
                            }
                        siblingGroups.forEach { group ->
                            val displayName =
                                if (group.name.length > 10) {
                                    group.name.take(10) + "..."
                                } else {
                                    group.name
                                }
                            IOSStyledDropdownMenuItem(
                                text = displayName,
                                onClick = {
                                    onGroupSelected(group.path)
                                    showDropdown = false
                                    clickedLevel = null
                                },
                            )
                        }
                    }
                }
            }
        }

        // 如果有当前级别（不是根目录）
        if (pathParts.isNotEmpty()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )

            // 当前级别（可点击，显示其子目录）
            // 先检查是否有子目录
            val currentChildGroups =
                remember(selectedGroupPath, groups) {
                    groups.filter { it.parentPath == selectedGroupPath }
                }

            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (isDarkTheme) {
                                AppColors.darkIOSSelectedBackground
                            } else {
                                AppColors.iOSSelectedBackground
                            },
                        ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .then(
                                if (currentChildGroups.isNotEmpty()) {
                                    Modifier.clickable {
                                        clickedLevel = "current"
                                        showDropdown = true
                                    }
                                } else {
                                    Modifier // 没有子目录时不可点击
                                },
                            ).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currentLevelName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // 下拉菜单 - 只有存在子目录时才显示
                if (currentChildGroups.isNotEmpty()) {
                    IOSStyledDropdownMenu(
                        offset = DpOffset(0.dp, 98.dp),
                        expanded = showDropdown && clickedLevel == "current",
                        onDismissRequest = {
                            showDropdown = false
                            clickedLevel = null
                        },
                    ) {
                        // 第一项：返回上一级
                        if (pathParts.size > 1) {
                            IOSStyledDropdownMenuItem(
                                text = (if (isChinese()) "返回" else "Back"),
                                onClick = {
                                    onGroupSelected(parentPath ?: DefaultGroups.ALL_DEVICES)
                                    showDropdown = false
                                    clickedLevel = null
                                },
                            )
                        }

                        // 子目录列表
                        currentChildGroups.forEach { group ->
                            val displayName =
                                if (group.name.length > 10) {
                                    group.name.take(10) + "..."
                                } else {
                                    group.name
                                }
                            IOSStyledDropdownMenuItem(
                                text = displayName,
                                onClick = {
                                    onGroupSelected(group.path)
                                    showDropdown = false
                                    clickedLevel = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
