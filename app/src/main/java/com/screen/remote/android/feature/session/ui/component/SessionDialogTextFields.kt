package com.screen.remote.android.feature.session.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.common.IosDesignTokens
import com.screen.remote.android.core.designsystem.component.HelpIcon

private val DialogFieldLabelMaxWidth = IosDesignTokens.dialogLabelMaxWidth
private val DialogFieldLabelTextMaxWidth = IosDesignTokens.dialogLabelTextMaxWidth
private val DialogFieldSpacing = IosDesignTokens.compactSpacing
private val DialogFieldTextEndPadding = IosDesignTokens.fieldContentEndPadding
private const val DialogFieldCursorGap = " "

@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = IosDesignTokens.compactHorizontalPadding),
        textStyle = dialogFieldTextStyle(isError = isError),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                DialogFieldPlaceholder(
                    visible = value.isEmpty(),
                    placeholder = placeholder,
                    isError = isError,
                )
                innerTextField()
            }
        },
    )
}

@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    helpText: String? = null,
) {
    DialogFieldRow(
        label = label,
        modifier = modifier,
        helpText = helpText,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier.fillMaxWidth(),
            textStyle =
                dialogFieldTextStyle(
                    isError = isError,
                    textAlign = TextAlign.End,
                ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = dialogFieldCursorGapTransformation(),
            decorationBox = { innerTextField ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = IosDesignTokens.fieldContentStartPadding, end = DialogFieldTextEndPadding),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    DialogFieldPlaceholder(
                        visible = value.isEmpty(),
                        placeholder = placeholder,
                        isError = isError,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun DialogFieldRow(
    label: String,
    modifier: Modifier,
    helpText: String?,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = IosDesignTokens.compactHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.widthIn(max = DialogFieldLabelMaxWidth),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier =
                    Modifier.widthIn(
                        max =
                            if (helpText != null) {
                                DialogFieldLabelTextMaxWidth
                            } else {
                                DialogFieldLabelMaxWidth
                            },
                    ),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (helpText != null) {
                HelpIcon(
                    helpText = helpText,
                    modifier = Modifier.padding(start = IosDesignTokens.compactInlineSpacing),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = DialogFieldSpacing),
            contentAlignment = Alignment.CenterEnd,
        ) {
            content()
        }
    }
}

@Composable
private fun DialogFieldPlaceholder(
    visible: Boolean,
    placeholder: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    if (!visible) {
        return
    }

    Text(
        text = placeholder,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color =
            if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun dialogFieldTextStyle(
    isError: Boolean,
    textAlign: TextAlign = TextAlign.Start,
): TextStyle =
    TextStyle(
        fontSize = 15.sp,
        lineHeight = 15.sp,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        textAlign = textAlign,
    )

private fun dialogFieldCursorGapTransformation(): VisualTransformation =
    VisualTransformation { text ->
        if (text.isEmpty()) {
            return@VisualTransformation TransformedText(text, OffsetMapping.Identity)
        }

        val transformedText = AnnotatedString(text.text + DialogFieldCursorGap)
        val originalLength = text.length

        TransformedText(
            text = transformedText,
            offsetMapping =
                object : OffsetMapping {
                    override fun originalToTransformed(offset: Int): Int =
                        if (offset == originalLength) {
                            originalLength + DialogFieldCursorGap.length
                        } else {
                            offset
                        }

                    override fun transformedToOriginal(offset: Int): Int =
                        offset.coerceAtMost(originalLength)
                },
        )
    }
