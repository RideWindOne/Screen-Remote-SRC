package com.screen.remote.android.core.designsystem.component

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.screen.remote.android.core.common.AppColors
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.common.IosDesignTokens

private val IOSAlertDialogCornerRadius = 22.dp
private val IOSAlertDialogContentPadding = 18.dp
private val IOSAlertDialogActionPadding = 12.dp

@Composable
fun DialogHeader(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    leftButtonText: String? = null,
    rightButtonText: String? = null,
    onRightButtonClick: (() -> Unit)? = null,
    rightButtonEnabled: Boolean = true,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IosDesignTokens.dialogHeaderHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = IosDesignTokens.dialogHeaderBackgroundAlpha))
                    .padding(horizontal = IosDesignTokens.dialogHeaderHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leftButtonText != null) {
                TextButton(onClick = onDismiss) {
                    Text(
                        leftButtonText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.iOSBlue,
                    )
                }
            } else if (showBackButton) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = AppColors.iOSBlue,
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(IosDesignTokens.dialogActionSlotWidth))
            }

            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )

            if (trailingContent != null) {
                trailingContent()
            } else if (rightButtonText != null && onRightButtonClick != null) {
                TextButton(
                    onClick = onRightButtonClick,
                    enabled = rightButtonEnabled,
                ) {
                    Text(
                        rightButtonText,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (rightButtonEnabled) {
                                AppColors.iOSBlue
                            } else {
                                AppColors.iOSBlue.copy(alpha = IosDesignTokens.disabledActionAlpha)
                            },
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(IosDesignTokens.dialogActionSlotWidth))
            }
        }

        AppDivider()
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun DialogContainer(
    modifier: Modifier = Modifier,
    widthRatio: Float = AppDimens.WINDOW_WIDTH_RATIO,
    maxHeightRatio: Float = AppDimens.WINDOW_MAX_HEIGHT_RATIO,
    cornerRadius: Dp = AppDimens.windowCornerRadius,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable ColumnScope.() -> Unit,
) {
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * maxHeightRatio).dp

    Surface(
        modifier =
            modifier
                .fillMaxWidth(widthRatio)
                .wrapContentHeight()
                .wrapContentWidth()
                .heightIn(max = maxHeight),
        shape = RoundedCornerShape(cornerRadius),
        color = backgroundColor,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun DialogPage(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    widthRatio: Float = AppDimens.WINDOW_WIDTH_RATIO,
    maxHeightRatio: Float = AppDimens.WINDOW_MAX_HEIGHT_RATIO,
    showBackButton: Boolean = true,
    leftButtonText: String? = null,
    rightButtonText: String? = null,
    onRightButtonClick: (() -> Unit)? = null,
    rightButtonEnabled: Boolean = true,
    trailingContent: @Composable (() -> Unit)? = null,
    enableScroll: Boolean = false,
    horizontalPadding: Dp = AppDimens.paddingStandard,
    verticalSpacing: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
    ) {
        DialogContainer(
            modifier = modifier,
            widthRatio = widthRatio,
            maxHeightRatio = maxHeightRatio,
        ) {
            DialogHeader(
                title = title,
                onDismiss = onDismiss,
                showBackButton = showBackButton,
                leftButtonText = leftButtonText,
                rightButtonText = rightButtonText,
                onRightButtonClick = onRightButtonClick,
                rightButtonEnabled = rightButtonEnabled,
                trailingContent = trailingContent,
            )

            DialogHeaderSpacer()

            val contentModifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = horizontalPadding)
                    .then(if (enableScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)

            Column(
                modifier = contentModifier,
                verticalArrangement =
                    if (verticalSpacing != null) {
                        Arrangement.spacedBy(verticalSpacing)
                    } else {
                        Arrangement.Top
                    },
            ) {
                content()
                if (enableScroll) {
                    DialogBottomSpacer()
                }
            }

            if (!enableScroll) {
                DialogBottomSpacer()
            }
        }
    }
}

@Composable
fun IOSAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = iosAlertDialogContainerColor(),
    widthRatio: Float = 0.84f,
    maxHeightRatio: Float = 0.78f,
    showActionBar: Boolean = true,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
) {
    val effectiveContainerColor =
        when (containerColor) {
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.background,
            Color.Unspecified,
            Color.Transparent,
            -> iosAlertDialogContainerColor()

            else -> containerColor
        }
    val titleColor = iosAlertDialogTitleColor()
    val textColor = iosAlertDialogTextColor()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = dismissOnBackPress,
                dismissOnClickOutside = dismissOnClickOutside,
            ),
    ) {
        DialogContainer(
            modifier = modifier,
            widthRatio = widthRatio,
            maxHeightRatio = maxHeightRatio,
            cornerRadius = IOSAlertDialogCornerRadius,
            backgroundColor = effectiveContainerColor,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = IOSAlertDialogContentPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        icon()
                    }
                }

                if (title != null) {
                    CompositionLocalProvider(
                        LocalContentColor provides titleColor,
                        LocalTextStyle provides
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    ) {
                        title()
                    }
                }

                if (text != null) {
                    CompositionLocalProvider(
                        LocalContentColor provides textColor,
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp)
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            text()
                        }
                    }
                }
            }

            if (showActionBar) {
                AppDivider()
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = IOSAlertDialogActionPadding,
                                vertical = 10.dp,
                            ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
private fun iosAlertDialogContainerColor(): Color =
    if (isAppDarkTheme()) {
        AppColors.darkDialogBackground
    } else {
        AppColors.white
    }

@Composable
private fun iosAlertDialogTitleColor(): Color =
    if (isAppDarkTheme()) {
        MaterialTheme.colorScheme.onSurface
    } else {
        Color(0xFF2D2D31)
    }

@Composable
private fun iosAlertDialogTextColor(): Color =
    if (isAppDarkTheme()) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color(0xFF6E6E73)
    }

@Composable
private fun isAppDarkTheme(): Boolean =
    MaterialTheme.colorScheme.surface.let { color ->
        0.299f * color.red + 0.587f * color.green + 0.114f * color.blue < 0.5f
    }
