package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.IosDesignTokens

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppDimens.sectionTitleHeight)
                .padding(horizontal = IosDesignTokens.dialogHeaderHorizontalPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = IosDesignTokens.dividerAlpha),
    )
}

@Composable
fun DialogHeaderSpacer() {
    Spacer(modifier = Modifier.height(IosDesignTokens.dialogHeaderSpacerHeight))
}

@Composable
fun DialogBottomSpacer() {
    Spacer(modifier = Modifier.height(IosDesignTokens.dialogBottomSpacerHeight))
}
