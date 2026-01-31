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
import com.mobile.scrcpy.android.core.common.manager.LanguageManager
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.domain.model.AppLanguage
import com.mobile.scrcpy.android.core.i18n.SettingsTexts
import com.mobile.scrcpy.android.feature.session.viewmodel.MainViewModel

@Composable
fun LanguageScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()

    val txtTitle = rememberText(SettingsTexts.LANGUAGE_TITLE)
    val txtSectionTitle = rememberText(SettingsTexts.LANGUAGE_SECTION_TITLE)
    val txtAuto = rememberText(SettingsTexts.LANGUAGE_AUTO)
    val txtChinese = rememberText(SettingsTexts.LANGUAGE_CHINESE)
    val txtEnglish = rememberText(SettingsTexts.LANGUAGE_ENGLISH)

    DialogPage(
        title = txtTitle,
        onDismiss = onBack,
    ) {
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
                LanguageOption(
                    title = txtAuto,
                    isSelected = settings.language == AppLanguage.AUTO,
                    onClick = {
                        Log.d(LogTags.APP, "切换语言: AUTO")
                        viewModel.updateSettings(settings.copy(language = AppLanguage.AUTO))
                        LanguageManager.setLanguage(AppLanguage.AUTO)
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = AppColors.divider.copy(alpha = 0.3f),
                )

                LanguageOption(
                    title = txtChinese,
                    isSelected = settings.language == AppLanguage.CHINESE,
                    onClick = {
                        Log.d(LogTags.APP, "切换语言: CHINESE")
                        viewModel.updateSettings(settings.copy(language = AppLanguage.CHINESE))
                        LanguageManager.setLanguage(AppLanguage.CHINESE)
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = AppColors.divider.copy(alpha = 0.3f),
                )

                LanguageOption(
                    title = txtEnglish,
                    isSelected = settings.language == AppLanguage.ENGLISH,
                    onClick = {
                        Log.d(LogTags.APP, "切换语言: ENGLISH")
                        viewModel.updateSettings(settings.copy(language = AppLanguage.ENGLISH))
                        LanguageManager.setLanguage(AppLanguage.ENGLISH)
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(43.dp)
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
                contentDescription = "Selected",
                tint = AppColors.iOSBlue,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
