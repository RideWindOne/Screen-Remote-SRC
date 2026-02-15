package com.mobile.scrcpy.android.feature.remote.ui.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.scrcpy.android.R
import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutLabelSource
import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutNode
import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutNodeKind
import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutSnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val OverlayToolbarBackgroundColor = Color(0xEA2C2C2E)
private val OverlayLabelExtraBorrow = 3.dp
private val OverlayLabelMinWidth = 16.dp
private val OverlayLabelHorizontalPadding = 2.dp
private val OverlayLabelVerticalPadding = 1.dp
private val OverlayLabelMinFont = 7.sp
private val OverlayLabelMaxFont = 9.sp

@Composable
internal fun RemoteLayoutInspectorOverlay(
    snapshot: RemoteUiLayoutSnapshot?,
    nodes: List<RemoteUiLayoutNode>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    if (nodes.isEmpty()) {
        return
    }

    val viewportBounds = snapshot?.viewportBounds ?: inferViewportBounds(nodes)
    val viewportWidth = max(viewportBounds.width, 1)
    val viewportHeight = max(viewportBounds.height, 1)
    val density = LocalDensity.current
    var toolbarOffset by remember { mutableStateOf(IntOffset.Zero) }
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val overlayWidthPx = with(density) { maxWidth.toPx() }
        val overlayHeightPx = with(density) { maxHeight.toPx() }
        val scaleX = overlayWidthPx / viewportWidth.toFloat()
        val scaleY = overlayHeightPx / viewportHeight.toFloat()

        nodes.forEach { node ->
            val leftPx = (node.bounds.left - viewportBounds.left) * scaleX
            val topPx = (node.bounds.top - viewportBounds.top) * scaleY
            val widthPx = max((node.bounds.width * scaleX).roundToInt(), 1)
            val heightPx = max((node.bounds.height * scaleY).roundToInt(), 1)
            val nodeColor = overlayNodeColor(node)
            val nodeWidthDp = with(density) { widthPx.toDp() }
            val nodeHeightDp = with(density) { heightPx.toDp() }
            val indicatorPlacement = overlayIndicatorPlacement(node, nodeWidthDp, nodeHeightDp)

            Box(
                modifier =
                    Modifier
                        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                        .width(nodeWidthDp)
                        .height(nodeHeightDp)
                        .border(
                            width = if (node.focused) 2.dp else 1.dp,
                            color = nodeColor,
                            shape = RoundedCornerShape(6.dp),
                        ).background(
                            color = nodeColor.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(6.dp),
                        ),
            ) {
                if (shouldRenderToggleIndicator(node)) {
                    ToggleIndicator(
                        node = node,
                        placement = indicatorPlacement,
                    )
                } else if (shouldRenderArrowIndicator(node)) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Dropdown indicator",
                        tint = nodeColor,
                        modifier = Modifier.align(Alignment.Center).size(16.dp),
                    )
                }
            }

            val shouldShowNodeLabel = node.label.isNotBlank() && !shouldSuppressNodeLabel(node, nodes)
            if (shouldShowNodeLabel) {
                val remainingWidthDp =
                    with(density) {
                        (overlayWidthPx - leftPx).coerceAtLeast(widthPx.toFloat()).toDp()
                    }
                val baseLabelWidthDp =
                    if (nodeWidthDp > OverlayLabelMinWidth) {
                        nodeWidthDp
                    } else {
                        OverlayLabelMinWidth
                    }
                val baseTextWidthPx =
                    with(density) {
                        (baseLabelWidthDp - OverlayLabelHorizontalPadding * 2).roundToPx().coerceAtLeast(1)
                    }
                val contentTextWidthPx =
                    textMeasurer
                        .measure(
                            text = AnnotatedString(node.label),
                            style = overlayLabelStyle(initialFontSize = OverlayLabelMaxFont),
                            maxLines = 1,
                            constraints = Constraints(),
                        ).size.width
                val labelHeightDp = with(density) { heightPx.toDp() }
                val initialFontSize =
                    when {
                        labelHeightDp <= 18.dp -> OverlayLabelMinFont
                        labelHeightDp <= 28.dp -> 8.sp
                        else -> OverlayLabelMaxFont
                    }
                var finalFontSize = initialFontSize
                var finalLayoutResult =
                    textMeasurer.measure(
                        text = AnnotatedString(node.label),
                        style = overlayLabelStyle(initialFontSize),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 3,
                        constraints = Constraints(maxWidth = baseTextWidthPx),
                    )
                if (finalLayoutResult.hasVisualOverflow && initialFontSize > OverlayLabelMinFont) {
                    finalFontSize = OverlayLabelMinFont
                    finalLayoutResult =
                        textMeasurer.measure(
                            text = AnnotatedString(node.label),
                            style = overlayLabelStyle(finalFontSize),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 3,
                            constraints = Constraints(maxWidth = baseTextWidthPx),
                        )
                }
                val canBorrowLabelWidth = node.text.isNotBlank()
                val needsBorrow =
                    canBorrowLabelWidth &&
                        (finalLayoutResult.lineCount > 1 || finalLayoutResult.hasVisualOverflow)
                val preferredLabelWidthDp =
                    if (needsBorrow) {
                        baseLabelWidthDp + OverlayLabelExtraBorrow
                    } else {
                        baseLabelWidthDp
                    }
                val compactLabelWidthDp =
                    with(density) {
                        (contentTextWidthPx + (OverlayLabelHorizontalPadding * 2).roundToPx()).toDp()
                    }.coerceAtLeast(OverlayLabelMinWidth)
                val targetLabelWidthDp =
                    if (compactLabelWidthDp < preferredLabelWidthDp) {
                        compactLabelWidthDp
                    } else {
                        preferredLabelWidthDp
                    }
                val labelWidthDp =
                    if (remainingWidthDp < targetLabelWidthDp) {
                        remainingWidthDp
                    } else {
                        targetLabelWidthDp
                    }

                Text(
                    text = node.label,
                    modifier =
                        Modifier
                            .offset { IntOffset(leftPx.roundToInt() + 2.dp.roundToPx(), topPx.roundToInt() + 2.dp.roundToPx()) }
                            .background(
                                color = nodeColor.copy(alpha = 0.88f),
                                shape = RoundedCornerShape(4.dp),
                            ).width(labelWidthDp)
                            .padding(horizontal = OverlayLabelHorizontalPadding, vertical = OverlayLabelVerticalPadding),
                    style = overlayLabelStyle(finalFontSize),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Surface(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .offset { toolbarOffset }
                    .pointerInput(isLoading) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            toolbarOffset =
                                IntOffset(
                                    x = toolbarOffset.x + dragAmount.x.roundToInt(),
                                    y = toolbarOffset.y + dragAmount.y.roundToInt(),
                                )
                        }
                    },
            color = OverlayToolbarBackgroundColor,
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isLoading,
                        modifier = Modifier.size(30.dp),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_toolbar_refresh),
                                contentDescription = "Refresh layout overlay",
                                modifier = Modifier.size(16.dp),
                                tint = Color.Unspecified,
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_toolbar_close),
                            contentDescription = "Close layout overlay",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Unspecified,
                        )
                    }
                }
            }
        }
    }
}

private fun overlayNodeColor(node: RemoteUiLayoutNode): Color =
    when (node.kind) {
        RemoteUiLayoutNodeKind.INPUT -> Color(0xFF4CAF50)
        RemoteUiLayoutNodeKind.BUTTON -> Color(0xFF64B5F6)
        RemoteUiLayoutNodeKind.TEXT -> Color(0xFFFFD54F)
        RemoteUiLayoutNodeKind.TOGGLE -> Color(0xFFFF8A65)
        RemoteUiLayoutNodeKind.IMAGE -> Color(0xFFBA68C8)
        RemoteUiLayoutNodeKind.CONTAINER -> Color(0xFF90A4AE)
        RemoteUiLayoutNodeKind.OTHER -> Color(0xFFE0E0E0)
    }

private fun overlayLabelStyle(initialFontSize: androidx.compose.ui.unit.TextUnit): TextStyle =
    TextStyle(
        fontSize = initialFontSize,
        lineHeight = (initialFontSize.value + 1f).sp,
        color = Color.Black,
    )

private fun inferViewportBounds(nodes: List<RemoteUiLayoutNode>) =
    if (nodes.isEmpty()) {
        com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutBounds(0, 0, 1, 1)
    } else {
        com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutBounds(
            left = nodes.minOf { it.bounds.left },
            top = nodes.minOf { it.bounds.top },
            right = nodes.maxOf { it.bounds.right },
            bottom = nodes.maxOf { it.bounds.bottom },
        )
    }

private fun shouldRenderArrowIndicator(node: RemoteUiLayoutNode): Boolean {
    val loweredResource = node.resourceId.lowercase()
    return node.label.isBlank() &&
        node.kind in setOf(RemoteUiLayoutNodeKind.IMAGE, RemoteUiLayoutNodeKind.OTHER, RemoteUiLayoutNodeKind.BUTTON) &&
        (
            loweredResource.contains("arrow") ||
                loweredResource.contains("dropdown") ||
                loweredResource.contains("expand") ||
                loweredResource.contains("chevron") ||
                loweredResource.contains("caret") ||
                loweredResource.contains("spinner") ||
                loweredResource.contains("region_image") ||
                loweredResource.contains("regionimage")
        )
}

private fun shouldRenderToggleIndicator(node: RemoteUiLayoutNode): Boolean =
    node.kind == RemoteUiLayoutNodeKind.TOGGLE || looksLikeCustomCheckContainer(node)

private data class OverlayIndicatorPlacement(
    val alignment: Alignment,
    val offsetX: Dp = 0.dp,
)

private enum class ToggleIndicatorStyle {
    CHECKBOX,
    RADIO,
    SWITCH,
}

@Composable
private fun BoxScope.ToggleIndicator(
    node: RemoteUiLayoutNode,
    placement: OverlayIndicatorPlacement,
) {
    when (toggleIndicatorStyle(node)) {
        ToggleIndicatorStyle.CHECKBOX -> CheckboxIndicator(node, placement)
        ToggleIndicatorStyle.RADIO -> RadioIndicator(node, placement)
        ToggleIndicatorStyle.SWITCH -> SwitchIndicator(node, placement)
    }
}

@Composable
private fun BoxScope.CheckboxIndicator(
    node: RemoteUiLayoutNode,
    placement: OverlayIndicatorPlacement,
) {
    val backgroundColor =
        if (node.checked) {
            Color(0xFF22C55E).copy(alpha = 0.92f)
        } else {
            Color(0xFF6B7280).copy(alpha = 0.72f)
        }
    val borderColor =
        if (node.checked) {
            Color(0xFF22C55E)
        } else {
            Color(0xFFD1D5DB).copy(alpha = 0.9f)
        }
    val iconTint =
        if (node.checked) {
            Color.White
        } else {
            Color(0xFFE5E7EB).copy(alpha = 0.82f)
        }

    Box(
        modifier =
            Modifier
                .align(placement.alignment)
                .offset(x = placement.offsetX)
                .size(16.dp)
                .background(backgroundColor, RoundedCornerShape(4.dp))
                .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(4.dp)),
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = if (node.checked) "Checked" else "Unchecked checkbox",
            tint = iconTint,
            modifier = Modifier.align(Alignment.Center).size(11.dp),
        )
    }
}

@Composable
private fun BoxScope.RadioIndicator(
    node: RemoteUiLayoutNode,
    placement: OverlayIndicatorPlacement,
) {
    val borderColor =
        if (node.checked) {
            Color(0xFF22C55E)
        } else {
            Color(0xFFD1D5DB).copy(alpha = 0.9f)
        }
    val outerBackground =
        if (node.checked) {
            Color(0xFF22C55E).copy(alpha = 0.18f)
        } else {
            Color(0xFF6B7280).copy(alpha = 0.12f)
        }
    val dotColor =
        if (node.checked) {
            Color(0xFF22C55E)
        } else {
            Color(0xFFD1D5DB).copy(alpha = 0.68f)
        }
    val dotSize = if (node.checked) 6.dp else 4.dp

    Box(
        modifier =
            Modifier
                .align(placement.alignment)
                .offset(x = placement.offsetX)
                .size(16.dp)
                .background(outerBackground, CircleShape)
                .border(width = 1.5.dp, color = borderColor, shape = CircleShape),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(dotSize)
                    .background(dotColor, CircleShape),
        )
    }
}

@Composable
private fun BoxScope.SwitchIndicator(
    node: RemoteUiLayoutNode,
    placement: OverlayIndicatorPlacement,
) {
    val trackColor =
        if (node.checked) {
            Color(0xFF22C55E).copy(alpha = 0.9f)
        } else {
            Color(0xFF6B7280).copy(alpha = 0.78f)
        }
    val trackBorder =
        if (node.checked) {
            Color(0xFF22C55E)
        } else {
            Color(0xFFD1D5DB).copy(alpha = 0.82f)
        }
    val thumbColor =
        if (node.checked) {
            Color.White
        } else {
            Color(0xFFE5E7EB)
        }

    Box(
        modifier =
            Modifier
                .align(placement.alignment)
                .offset(x = placement.offsetX)
                .width(26.dp)
                .height(16.dp)
                .background(trackColor, RoundedCornerShape(999.dp))
                .border(width = 1.dp, color = trackBorder, shape = RoundedCornerShape(999.dp)),
    ) {
        Box(
            modifier =
                Modifier
                    .align(if (node.checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .offset(x = if (node.checked) (-2).dp else 2.dp)
                    .size(10.dp)
                    .background(thumbColor, CircleShape),
        )
    }
}

private fun toggleIndicatorStyle(node: RemoteUiLayoutNode): ToggleIndicatorStyle {
    val loweredClass = node.shortClassName.lowercase()
    val loweredResource = node.resourceId.lowercase()

    return when {
        loweredClass.contains("radio") ||
            loweredResource.contains(":id/rb_") ||
            loweredResource.contains("/rb_") -> ToggleIndicatorStyle.RADIO

        loweredClass.contains("switch") ||
            (loweredClass.contains("compoundbutton") &&
                !loweredClass.contains("radio") &&
                !loweredClass.contains("check")) ||
            loweredResource.contains(":id/sb_") ||
            loweredResource.contains("/sb_") -> ToggleIndicatorStyle.SWITCH

        else -> ToggleIndicatorStyle.CHECKBOX
    }
}

private fun overlayIndicatorPlacement(
    node: RemoteUiLayoutNode,
    nodeWidth: Dp,
    nodeHeight: Dp,
): OverlayIndicatorPlacement {
    if (!shouldAnchorIndicatorToLeadingEdge(node)) {
        return OverlayIndicatorPlacement(alignment = Alignment.Center)
    }

    val preferredInset = nodeHeight * 0.12f
    val maxInsetCandidate = nodeWidth * 0.08f
    val maxInset = if (maxInsetCandidate < 10.dp) maxInsetCandidate else 10.dp
    val insetCandidate = if (preferredInset < maxInset) preferredInset else maxInset
    val inset = if (insetCandidate > 2.dp) insetCandidate else 2.dp
    return OverlayIndicatorPlacement(
        alignment = Alignment.CenterStart,
        offsetX = inset,
    )
}

private fun shouldAnchorIndicatorToLeadingEdge(node: RemoteUiLayoutNode): Boolean {
    val hasInlineLabel =
        node.text.isNotBlank() ||
            node.label.isNotBlank() ||
            node.contentDescription.isNotBlank()

    if (!node.checkable || !hasInlineLabel) {
        return false
    }

    return node.bounds.width > node.bounds.height
}

private fun looksLikeCustomCheckContainer(node: RemoteUiLayoutNode): Boolean {
    val lowered = node.resourceId.lowercase()
    return node.kind == RemoteUiLayoutNodeKind.CONTAINER &&
        node.clickable &&
        (
            lowered.contains("check") ||
                lowered.contains("checkbox") ||
                lowered.contains("confirm") ||
                lowered.contains("agree") ||
                lowered.contains("accept") ||
                lowered.contains("consent") ||
                lowered.contains("policy")
        )
}

internal fun shouldSuppressNodeLabel(
    node: RemoteUiLayoutNode,
    allNodes: List<RemoteUiLayoutNode>,
): Boolean {
    if (node.kind in setOf(RemoteUiLayoutNodeKind.TEXT, RemoteUiLayoutNodeKind.INPUT)) {
        return false
    }

    if (node.labelSource == RemoteUiLayoutLabelSource.TEXT) {
        return false
    }

    val sameComponentHasPrimaryLabel =
        node.componentKey != null &&
            allNodes.any { other ->
                other !== node &&
                    other.componentKey == node.componentKey &&
                    other.label.isNotBlank() &&
                    other.labelSource != RemoteUiLayoutLabelSource.RESOURCE_ID
            }

    if (sameComponentHasPrimaryLabel) {
        return true
    }

    val hasNearbyPrimaryTextLabel =
        node.kind == RemoteUiLayoutNodeKind.IMAGE &&
            node.labelSource == RemoteUiLayoutLabelSource.RESOURCE_ID &&
            allNodes.any { other ->
                other !== node &&
                    other.kind == RemoteUiLayoutNodeKind.TEXT &&
                    other.label.isNotBlank() &&
                    other.labelSource != RemoteUiLayoutLabelSource.RESOURCE_ID &&
                    other.packageName == node.packageName &&
                    (
                        (
                            horizontalOverlapRatio(node, other) >= 0.4f &&
                                verticalGap(node, other) in 0..80 &&
                                isLikelyNodeSpecificStackedTextLabel(node, other)
                        ) ||
                            (verticalOverlapRatio(node, other) >= 0.4f && horizontalGap(node, other) in 0..80)
                    )
            }

    if (hasNearbyPrimaryTextLabel) {
        return true
    }

    val hasLargerResourceNamedSiblingInSameComponent =
        node.componentKey != null &&
            node.labelSource == RemoteUiLayoutLabelSource.RESOURCE_ID &&
            allNodes.any { other ->
                other !== node &&
                    other.componentKey == node.componentKey &&
                    other.label.isNotBlank() &&
                    other.labelSource == RemoteUiLayoutLabelSource.RESOURCE_ID &&
                    (
                        other.bounds.area() > node.bounds.area() ||
                            (
                                other.bounds.area() == node.bounds.area() &&
                                    other.bounds.width > node.bounds.width
                            ) ||
                            (
                                other.bounds.area() == node.bounds.area() &&
                                    other.bounds.width == node.bounds.width &&
                                    other.resourceId < node.resourceId
                            )
                    )
            }

    if (hasLargerResourceNamedSiblingInSameComponent) {
        return true
    }

    val isGroupContainerLabel =
        node.labelSource == RemoteUiLayoutLabelSource.RESOURCE_ID &&
            node.kind == RemoteUiLayoutNodeKind.CONTAINER &&
            (
                node.resourceId.lowercase().contains("layout_") ||
                    node.resourceId.lowercase().contains("_group") ||
                    node.resourceId.lowercase().contains("container")
            ) &&
            allNodes.any { other ->
                other !== node &&
                    other.packageName == node.packageName &&
                    other.kind == RemoteUiLayoutNodeKind.TEXT &&
                    other.label.isNotBlank() &&
                    containsBounds(node, other)
            }

    if (isGroupContainerLabel) {
        return true
    }

    if (node.kind == RemoteUiLayoutNodeKind.IMAGE && node.labelSource == RemoteUiLayoutLabelSource.RESOURCE_ID) {
        val overlappedLabeledNode =
            allNodes.any { other ->
                other !== node &&
                    other.label.isNotBlank() &&
                    other.labelSource != RemoteUiLayoutLabelSource.RESOURCE_ID &&
                    other.packageName == node.packageName &&
                    other.bounds.area() >= node.bounds.area() &&
                    overlapOverSmallerArea(node, other) >= 0.6f
            }
        if (overlappedLabeledNode) {
            return true
        }
    }

    val hasAdjacentTextLabel = allNodes.any { other ->
        other !== node &&
            other.kind == RemoteUiLayoutNodeKind.TEXT &&
            other.label.isNotBlank() &&
            other.packageName == node.packageName &&
            verticalOverlapRatio(node, other) >= 0.6f &&
            horizontalGap(node, other) in 0..36
    }

    if (hasAdjacentTextLabel) {
        return true
    }

    val hasStackedTextLabel = allNodes.any { other ->
        other !== node &&
            other.kind == RemoteUiLayoutNodeKind.TEXT &&
            other.label.isNotBlank() &&
            other.labelSource != RemoteUiLayoutLabelSource.RESOURCE_ID &&
            other.packageName == node.packageName &&
            horizontalOverlapRatio(node, other) >= 0.6f &&
            verticalGap(node, other) in 0..48 &&
            isLikelyNodeSpecificStackedTextLabel(node, other)
    }

    if (hasStackedTextLabel) {
        return true
    }

    val hasLabeledAncestor = allNodes.any { other ->
        other !== node &&
            other.label.isNotBlank() &&
            other.labelSource != RemoteUiLayoutLabelSource.RESOURCE_ID &&
            other.packageName == node.packageName &&
            other.bounds.area() >= node.bounds.area() &&
            containsBounds(other, node)
    }

    if (hasLabeledAncestor) {
        return true
    }

    val hasLabeledOverlap = allNodes.any { other ->
        other !== node &&
            other.label.isNotBlank() &&
            other.labelSource != RemoteUiLayoutLabelSource.RESOURCE_ID &&
            other.packageName == node.packageName &&
            other.bounds.area() >= node.bounds.area() &&
            overlapOverSmallerArea(node, other) >= 0.88f
    }

    return hasLabeledOverlap
}

private fun isLikelyNodeSpecificStackedTextLabel(
    node: RemoteUiLayoutNode,
    textNode: RemoteUiLayoutNode,
): Boolean {
    val maxTextWidth = max(node.bounds.width, 1) * 1.5f
    if (textNode.bounds.width > maxTextWidth.roundToInt()) {
        return false
    }

    val nodeCenterX = (node.bounds.left + node.bounds.right) / 2f
    val textCenterX = (textNode.bounds.left + textNode.bounds.right) / 2f
    val maxCenterOffset = max(node.bounds.width, 1) * 0.25f
    return abs(nodeCenterX - textCenterX) <= maxCenterOffset
}

private fun verticalOverlapRatio(
    a: RemoteUiLayoutNode,
    b: RemoteUiLayoutNode,
): Float {
    val intersectionTop = maxOf(a.bounds.top, b.bounds.top)
    val intersectionBottom = minOf(a.bounds.bottom, b.bounds.bottom)
    if (intersectionBottom <= intersectionTop) {
        return 0f
    }
    val intersectionHeight = intersectionBottom - intersectionTop
    val smallerHeight = minOf(a.bounds.height, b.bounds.height).coerceAtLeast(1)
    return intersectionHeight.toFloat() / smallerHeight.toFloat()
}

private fun horizontalGap(
    a: RemoteUiLayoutNode,
    b: RemoteUiLayoutNode,
): Int =
    when {
        b.bounds.left >= a.bounds.right -> b.bounds.left - a.bounds.right
        a.bounds.left >= b.bounds.right -> a.bounds.left - b.bounds.right
        else -> 0
    }

private fun verticalGap(
    a: RemoteUiLayoutNode,
    b: RemoteUiLayoutNode,
): Int =
    when {
        b.bounds.top >= a.bounds.bottom -> b.bounds.top - a.bounds.bottom
        a.bounds.top >= b.bounds.bottom -> a.bounds.top - b.bounds.bottom
        else -> 0
    }

private fun horizontalOverlapRatio(
    a: RemoteUiLayoutNode,
    b: RemoteUiLayoutNode,
): Float {
    val intersectionLeft = maxOf(a.bounds.left, b.bounds.left)
    val intersectionRight = minOf(a.bounds.right, b.bounds.right)
    if (intersectionRight <= intersectionLeft) {
        return 0f
    }
    val intersectionWidth = intersectionRight - intersectionLeft
    val smallerWidth = minOf(a.bounds.width, b.bounds.width).coerceAtLeast(1)
    return intersectionWidth.toFloat() / smallerWidth.toFloat()
}

private fun containsBounds(
    outer: RemoteUiLayoutNode,
    inner: RemoteUiLayoutNode,
): Boolean =
    outer.bounds.left <= inner.bounds.left &&
        outer.bounds.top <= inner.bounds.top &&
        outer.bounds.right >= inner.bounds.right &&
        outer.bounds.bottom >= inner.bounds.bottom

private fun overlapOverSmallerArea(
    a: RemoteUiLayoutNode,
    b: RemoteUiLayoutNode,
): Float {
    val intersectionLeft = maxOf(a.bounds.left, b.bounds.left)
    val intersectionTop = maxOf(a.bounds.top, b.bounds.top)
    val intersectionRight = minOf(a.bounds.right, b.bounds.right)
    val intersectionBottom = minOf(a.bounds.bottom, b.bounds.bottom)
    if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
        return 0f
    }
    val intersectionArea = (intersectionRight - intersectionLeft).toLong() * (intersectionBottom - intersectionTop).toLong()
    val smallerArea = minOf(a.bounds.area(), b.bounds.area()).coerceAtLeast(1L)
    return intersectionArea.toFloat() / smallerArea.toFloat()
}
