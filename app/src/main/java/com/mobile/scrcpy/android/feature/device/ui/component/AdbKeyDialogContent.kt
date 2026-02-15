package com.mobile.scrcpy.android.feature.device.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.feature.device.ui.component.adbkey.KeyActionItem
import com.mobile.scrcpy.android.feature.device.ui.component.adbkey.KeyEditItem
import com.mobile.scrcpy.android.feature.device.ui.component.adbkey.KeyInfoItem

@Composable
internal fun AdbKeyManagementDialogContent(
    state: AdbKeyManagementDialogState,
    texts: AdbKeyDialogTexts,
    privateKeyFocusRequester: FocusRequester,
    publicKeyFocusRequester: FocusRequester,
    onSaveKeys: () -> Unit,
    onImportKeys: () -> Unit,
    onExportKeys: () -> Unit,
    onGenerateKeys: () -> Unit,
) {
    AdbKeyInfoSection(
        state = state,
        texts = texts,
        privateKeyFocusRequester = privateKeyFocusRequester,
        publicKeyFocusRequester = publicKeyFocusRequester,
    )

    Spacer(modifier = Modifier.height(10.dp))

    AdbKeyActionsSection(
        texts = texts,
        onSaveKeys = onSaveKeys,
        onImportKeys = onImportKeys,
        onExportKeys = onExportKeys,
        onGenerateKeys = onGenerateKeys,
    )

    Spacer(modifier = Modifier.height(10.dp))

    AdbKeyStatusSection(
        status = state.keysLoadStatus,
        isSuccess = state.isKeysLoadSuccessful,
        title = texts.status,
    )
}

@Composable
private fun AdbKeyInfoSection(
    state: AdbKeyManagementDialogState,
    texts: AdbKeyDialogTexts,
    privateKeyFocusRequester: FocusRequester,
    publicKeyFocusRequester: FocusRequester,
) {
    AdbKeySectionCard(title = texts.keyInfo) {
        KeyInfoItem(
            label = texts.keyDir,
            value = state.adbKeysDir,
        )
        AppDivider()
        KeyEditItem(
            label = texts.privateKey,
            value = state.privateKeyEditable,
            onValueChange = { state.privateKeyEditable = it },
            isVisible = state.privateKeyVisible,
            onVisibilityToggle = { state.privateKeyVisible = !state.privateKeyVisible },
            focusRequester = privateKeyFocusRequester,
            txtHide = texts.hide,
            txtShow = texts.show,
        )
        AppDivider()
        KeyEditItem(
            label = texts.publicKey,
            value = state.publicKeyEditable,
            onValueChange = { state.publicKeyEditable = it },
            isVisible = state.publicKeyVisible,
            onVisibilityToggle = { state.publicKeyVisible = !state.publicKeyVisible },
            focusRequester = publicKeyFocusRequester,
            txtHide = texts.hide,
            txtShow = texts.show,
        )
    }
}

@Composable
private fun AdbKeyActionsSection(
    texts: AdbKeyDialogTexts,
    onSaveKeys: () -> Unit,
    onImportKeys: () -> Unit,
    onExportKeys: () -> Unit,
    onGenerateKeys: () -> Unit,
) {
    AdbKeySectionCard(title = texts.keyOperations) {
        KeyActionItem(
            icon = Icons.Default.Save,
            title = texts.saveKeys,
            onClick = onSaveKeys,
        )
        AppDivider()
        KeyActionItem(
            icon = Icons.Default.Download,
            title = texts.importKeys,
            onClick = onImportKeys,
        )
        AppDivider()
        KeyActionItem(
            icon = Icons.Default.Upload,
            title = texts.exportKeys,
            onClick = onExportKeys,
        )
        AppDivider()
        KeyActionItem(
            icon = Icons.Default.Key,
            title = texts.generateKeys,
            onClick = onGenerateKeys,
        )
    }
}

@Composable
private fun AdbKeyStatusSection(
    status: String,
    isSuccess: Boolean,
    title: String,
) {
    AdbKeySectionCard(title = title) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppDimens.listItemHeight)
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (isSuccess) {
                        Color(0xFF34C759)
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
        }
    }
}

@Composable
private fun AdbKeySectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}
