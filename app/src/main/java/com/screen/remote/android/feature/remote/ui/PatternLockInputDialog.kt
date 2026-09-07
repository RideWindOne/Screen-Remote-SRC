package com.screen.remote.android.feature.remote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 图案密码输入对话框
 * 用户在 3x3 点阵上绘制图案，完成后回调选中的点索引序列（0-8）
 */
@Composable
fun PatternLockInputDialog(
    onDismiss: () -> Unit,
    onPatternComplete: (List<Int>) -> Unit,
    onClearCache: (() -> Unit)? = null,
) {
    // 选中的点索引序列（0-8，按行优先排列）
    val selectedPoints = remember { mutableStateListOf<Int>() }
    var currentDragPosition by remember { mutableStateOf<Offset?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    // 9个点的中心位置（在 Canvas 中的相对位置，0-1）
    val pointPositions = remember {
        listOf(
            Offset(1f/6f, 1f/6f), Offset(3f/6f, 1f/6f), Offset(5f/6f, 1f/6f),
            Offset(1f/6f, 3f/6f), Offset(3f/6f, 3f/6f), Offset(5f/6f, 3f/6f),
            Offset(1f/6f, 5f/6f), Offset(3f/6f, 5f/6f), Offset(5f/6f, 5f/6f),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("绘制图案密码") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "在下方点阵上绘制解锁图案",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 图案绘制区域
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    selectedPoints.clear()
                                    currentDragPosition = offset
                                    // 检查是否点中了某个点
                                    checkPointHit(offset, pointPositions, size.width, size.height)?.let {
                                        selectedPoints.add(it)
                                    }
                                },
                                onDrag = { change, _ ->
                                    currentDragPosition = change.position
                                    // 检查是否经过了某个点
                                    checkPointHit(change.position, pointPositions, size.width, size.height)?.let {
                                        if (it !in selectedPoints) {
                                            // 检查是否需要添加中间点
                                            addPointWithIntermediate(it, selectedPoints, pointPositions)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    currentDragPosition = null
                                },
                                onDragCancel = {
                                    isDragging = false
                                    currentDragPosition = null
                                },
                            )
                        },
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val pointRadius = canvasWidth / 12f
                        val hitRadius = canvasWidth / 8f

                        // 绘制连接线
                        if (selectedPoints.size >= 2) {
                            val path = Path()
                            val firstPoint = pointPositions[selectedPoints[0]]
                            path.moveTo(firstPoint.x * canvasWidth, firstPoint.y * canvasHeight)
                            for (i in 1 until selectedPoints.size) {
                                val point = pointPositions[selectedPoints[i]]
                                path.lineTo(point.x * canvasWidth, point.y * canvasHeight)
                            }
                            // 绘制到当前拖拽位置
                            if (isDragging && currentDragPosition != null) {
                                path.lineTo(currentDragPosition!!.x, currentDragPosition!!.y)
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFF4CAF50),
                                style = Stroke(width = 8f),
                            )
                        }

                        // 绘制9个点
                        pointPositions.forEachIndexed { index, pos ->
                            val cx = pos.x * canvasWidth
                            val cy = pos.y * canvasHeight
                            val isSelected = index in selectedPoints

                            // 外圈
                            drawCircle(
                                color = if (isSelected) Color(0xFF4CAF50) else Color.Gray,
                                radius = pointRadius,
                                center = Offset(cx, cy),
                                style = Stroke(width = 3f),
                            )
                            // 内点
                            drawCircle(
                                color = if (isSelected) Color(0xFF4CAF50) else Color.Gray,
                                radius = pointRadius / 3f,
                                center = Offset(cx, cy),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "已选择 ${selectedPoints.size} 个点",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onClearCache != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onClearCache,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("清除位置缓存")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedPoints.size >= 4) {
                        onPatternComplete(selectedPoints.toList())
                    }
                },
                enabled = selectedPoints.size >= 4,
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * 检查触摸位置是否命中某个点
 */
private fun checkPointHit(
    position: Offset,
    pointPositions: List<Offset>,
    canvasWidth: Int,
    canvasHeight: Int,
): Int? {
    val hitRadius = canvasWidth / 8f
    pointPositions.forEachIndexed { index, pos ->
        val cx = pos.x * canvasWidth
        val cy = pos.y * canvasHeight
        val distance = sqrt((position.x - cx) * (position.x - cx) + (position.y - cy) * (position.y - cy))
        if (distance < hitRadius) {
            return index
        }
    }
    return null
}

/**
 * 添加点时检查是否需要添加中间点
 * 例如从点0直接到点2，应该自动添加点1
 */
private fun addPointWithIntermediate(
    newPoint: Int,
    selectedPoints: MutableList<Int>,
    pointPositions: List<Offset>,
) {
    if (selectedPoints.isEmpty()) {
        selectedPoints.add(newPoint)
        return
    }

    val lastPoint = selectedPoints.last()

    // 检查是否在同一行、同一列或同一对角线上，且中间有点
    val lastRow = lastPoint / 3
    val lastCol = lastPoint % 3
    val newRow = newPoint / 3
    val newCol = newPoint % 3

    val rowDiff = newRow - lastRow
    val colDiff = newCol - lastCol

    // 如果是水平、垂直或对角线移动，且距离为2，中间有一个点
    if (abs(rowDiff) <= 1 && abs(colDiff) <= 1) {
        // 相邻点，直接添加
        selectedPoints.add(newPoint)
        return
    }

    // 检查是否跳过了中间点
    if (rowDiff == 0 && abs(colDiff) == 2) {
        // 水平跳过
        val midPoint = lastPoint + colDiff / 2
        if (midPoint !in selectedPoints) {
            selectedPoints.add(midPoint)
        }
    } else if (colDiff == 0 && abs(rowDiff) == 2) {
        // 垂直跳过
        val midPoint = lastPoint + rowDiff / 2 * 3
        if (midPoint !in selectedPoints) {
            selectedPoints.add(midPoint)
        }
    } else if (abs(rowDiff) == 2 && abs(colDiff) == 2) {
        // 对角线跳过
        val midPoint = lastPoint + rowDiff / 2 * 3 + colDiff / 2
        if (midPoint !in selectedPoints) {
            selectedPoints.add(midPoint)
        }
    }

    selectedPoints.add(newPoint)
}
