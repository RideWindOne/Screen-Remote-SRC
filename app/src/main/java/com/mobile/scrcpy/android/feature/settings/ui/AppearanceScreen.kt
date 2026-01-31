package com.mobile.scrcpy.android.feature.settings.ui

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.domain.model.ThemeMode
import com.mobile.scrcpy.android.core.i18n.SettingsTexts
import com.mobile.scrcpy.android.feature.session.viewmodel.MainViewModel

@Composable
fun AppearanceScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()

    val txtTitle = rememberText(SettingsTexts.APPEARANCE_TITLE)
    val txtSectionTitle = rememberText(SettingsTexts.THEME_SECTION_TITLE)
    val txtSystem = rememberText(SettingsTexts.THEME_SYSTEM)
    val txtDark = rememberText(SettingsTexts.THEME_DARK)
    val txtLight = rememberText(SettingsTexts.THEME_LIGHT)

    DialogPage(
        title = txtTitle,
        onDismiss = onBack,
    ) {
        // 主题选项卡片
        SectionTitle(txtSectionTitle)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDimens.cardCornerRadius),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ThemeOption(
                    title = txtSystem,
                    isSelected = settings.themeMode == ThemeMode.SYSTEM,
                    onClick = {
                        Log.d(
                            LogTags.APP,
                            "点击跟随系统，当前主题: ${settings.themeMode}",
                        )
                        viewModel.updateSettings(settings.copy(themeMode = ThemeMode.SYSTEM))
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = AppColors.divider.copy(alpha = 0.3f),
                )

                ThemeOption(
                    title = txtLight,
                    isSelected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = {
                        Log.d(
                            LogTags.APP,
                            "点击浅色模式，当前主题: ${settings.themeMode}",
                        )
                        viewModel.updateSettings(settings.copy(themeMode = ThemeMode.LIGHT))
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = AppColors.divider.copy(alpha = 0.3f),
                )

                ThemeOption(
                    title = txtDark,
                    isSelected = settings.themeMode == ThemeMode.DARK,
                    onClick = {
                        Log.d(
                            LogTags.APP,
                            "点击深色模式，当前主题: ${settings.themeMode}",
                        )
                        viewModel.updateSettings(settings.copy(themeMode = ThemeMode.DARK))
                    },
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(AppDimens.themeOptionHeight)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选择",
                tint = AppColors.iOSBlue,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
