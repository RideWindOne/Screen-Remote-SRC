package com.screen.remote.android.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.screen.remote.android.core.common.IosDesignTokens

@Composable
fun IOSStyledDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopCenter,
    shadowElevation: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) {
        return
    }
    val density = LocalDensity.current

    Popup(
        alignment = alignment,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
        offset =
            with(density) {
                androidx.compose.ui.unit.IntOffset(offset.x.roundToPx(), offset.y.roundToPx())
            },
    ) {
        Surface(
            modifier =
                modifier
                    .widthIn(min = 30.dp, max = 150.dp)
                    .wrapContentWidth()
                    .widthIn(min = 30.dp, max = 160.dp)
                    .wrapContentSize(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(IosDesignTokens.cardCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = shadowElevation,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(vertical = 4.dp)
                        .wrapContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        }
    }
}

@Composable
fun IOSStyledDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
) {
    Box(
        modifier =
            modifier
                .wrapContentSize()
                .clickable(onClick = onClick)
                .padding(
                    horizontal = IosDesignTokens.standardHorizontalPadding,
                    vertical = IosDesignTokens.compactSpacing
                ),
        contentAlignment = Alignment.Center,
    ) {
        val style =
            if (fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) {
                MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize)
            } else {
                MaterialTheme.typography.bodyMedium
            }
        Text(
            text = text,
            color = textColor,
            textAlign = TextAlign.Center,
            style = style,
        )
    }
}

/**
 * 带开关的下拉菜单项
 * 文字在上，开关在下，适应窄菜单宽度
 */
@Composable
fun IOSStyledDropdownMenuSwitchItem(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier =
            modifier
                .wrapContentSize()
                .clickable { onCheckedChange(!checked) }
                .padding(
                    horizontal = IosDesignTokens.standardHorizontalPadding,
                    vertical = IosDesignTokens.compactSpacing,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = text,
            color = textColor,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                ),
        )
    }
}

