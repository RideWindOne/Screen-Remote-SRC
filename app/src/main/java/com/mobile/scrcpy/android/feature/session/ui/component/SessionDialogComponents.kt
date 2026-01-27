package com.mobile.scrcpy.android.feature.session.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.scrcpy.android.core.common.AppDimens

/**
 * 紧凑型文本输入框
 */
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .padding(horizontal = 10.dp),
        textStyle = TextStyle(
            fontSize = 15.sp,
            lineHeight = 15.sp,
            color = if (isError) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurface,
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) Color(0xFFFF3B30).copy(alpha = 0.6f) else Color(0xFF959595),
                    )
                }
                innerTextField()
            }
        }
    )
}

/**
 * 带标签的文本输入框
 */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            modifier = Modifier.wrapContentWidth()
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 10.dp),
            textStyle = TextStyle(
                fontSize = 15.sp,
                lineHeight = 15.sp,
                color = if (isError) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.wrapContentWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError) Color(0xFFFF3B30).copy(alpha = 0.6f) else Color(0xFF959595),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * 紧凑型开关行
 */
@Composable
fun CompactSwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 紧凑型可点击行
 */
@Composable
fun CompactClickableRow(
    text: String,
    trailingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArrow: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        )
        Row(
            modifier = Modifier.clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showArrow) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFE5E5EA)
                )
            }
        }
    }
}
