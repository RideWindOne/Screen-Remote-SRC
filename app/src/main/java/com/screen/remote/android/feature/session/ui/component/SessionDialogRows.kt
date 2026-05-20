package com.screen.remote.android.feature.session.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.common.IosDesignTokens
import com.screen.remote.android.core.designsystem.component.HelpIcon
import com.screen.remote.android.core.designsystem.component.IOSSwitch

private val DialogRowLabelMaxWidth = IosDesignTokens.dialogLabelMaxWidth
private val DialogRowSpacing = IosDesignTokens.compactSpacing
private val DialogTrailingActionWidth = IosDesignTokens.dialogTrailingActionWidth
private val DialogTrailingActionHorizontalPadding = IosDesignTokens.dialogHeaderHorizontalPadding

@Composable
fun CompactSwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    helpText: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = IosDesignTokens.compactHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DialogRowLabel(
            label = text,
            helpText = helpText,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = DialogRowSpacing),
        )
        IOSSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
fun CompactClickableRow(
    text: String,
    trailingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArrow: Boolean = false,
    helpText: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = IosDesignTokens.compactHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DialogRowLabel(
            label = text,
            helpText = helpText,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = DialogRowSpacing),
        )
        DialogTrailingAction(
            text = trailingText,
            onClick = onClick,
            showArrow = showArrow,
        )
    }
}

@Composable
fun LabeledRow(
    label: String,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier.padding(start = IosDesignTokens.compactHorizontalPadding),
    helpText: String? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = IosDesignTokens.compactHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DialogRowLabel(
            label = label,
            helpText = helpText,
            modifier = Modifier.widthIn(max = DialogRowLabelMaxWidth),
        )
        Box(
            modifier = contentModifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            content()
        }
    }
}

@Composable
fun LabeledSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    helpText: String? = null,
) {
    LabeledRow(
        label = label,
        modifier = modifier,
        helpText = helpText,
    ) {
        IOSSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
fun LabeledClickableRow(
    label: String,
    trailingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArrow: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
    helpText: String? = null,
) {
    LabeledRow(
        label = label,
        modifier = modifier,
        helpText = helpText,
    ) {
        DialogTrailingAction(
            text = trailingText,
            onClick = onClick,
            showArrow = showArrow,
            leadingIcon = leadingIcon,
            leadingIconTint = leadingIconTint,
        )
    }
}

@Composable
fun LabeledDropdownRow(
    label: String,
    trailingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    helpText: String? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = IosDesignTokens.compactHorizontalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DialogRowLabel(
            label = label,
            helpText = helpText,
            modifier =
                Modifier
                    .widthIn(max = DialogRowLabelMaxWidth)
                    .padding(end = DialogRowSpacing),
        )
        Box(
            modifier = Modifier.width(DialogTrailingActionWidth),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .fillMaxHeight()
                        .padding(
                            start = DialogTrailingActionHorizontalPadding,
                            end = DialogTrailingActionHorizontalPadding,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = trailingText,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            content()
        }
    }
}

@Composable
private fun DialogTrailingAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArrow: Boolean = false,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
) {
    Row(
            modifier =
                modifier
                    .width(DialogTrailingActionWidth)
                    .fillMaxHeight()
                    .clickable(onClick = onClick)
                    .padding(
                        start = DialogTrailingActionHorizontalPadding,
                        end = DialogTrailingActionHorizontalPadding,
                    ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = leadingIconTint ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IosDesignTokens.externalIconSize),
            )
            Spacer(modifier = Modifier.width(IosDesignTokens.compactInlineSpacing))
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showArrow) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFE5E5EA),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun DialogRowLabel(
    label: String,
    modifier: Modifier = Modifier,
    helpText: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IosDesignTokens.compactInlineSpacing),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (helpText != null) {
            HelpIcon(helpText = helpText)
        }
    }
}
