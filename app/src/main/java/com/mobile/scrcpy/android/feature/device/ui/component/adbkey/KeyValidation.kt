/**
 * ADB 密钥验证逻辑
 * 
 * 包含密钥生成确认和导入提示对话框
 */
package com.mobile.scrcpy.android.feature.device.ui.component.adbkey

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts

/**
 * 生成密钥对确认对话框
 */
@Composable
fun GenerateKeyPairConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val txtTitle = AdbTexts.ADB_KEY_GENERATE_CONFIRM_TITLE.get()
    val txtDestructiveOp = AdbTexts.ADB_KEY_DESTRUCTIVE_OP.get()
    val txtCurrentKeysDeleted = AdbTexts.ADB_KEY_CURRENT_KEYS_DELETED.get()
    val txtDevicesLoseAuth = AdbTexts.ADB_KEY_DEVICES_LOSE_AUTH.get()
    val txtNeedReauth = AdbTexts.ADB_KEY_NEED_REAUTH.get()
    val txtCannotUndo = AdbTexts.ADB_KEY_CANNOT_UNDO.get()
    val txtConfirmGenerate = AdbTexts.ADB_KEY_CONFIRM_GENERATE.get()
    val txtConfirm = CommonTexts.BUTTON_CONFIRM.get()
    val txtCancel = CommonTexts.BUTTON_CANCEL.get()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )
        },
        title = {
            Text(txtTitle)
        },
        text = {
            Column {
                Text(
                    txtDestructiveOp,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                Text("• $txtCurrentKeysDeleted")
                Text("• $txtDevicesLoseAuth")
                Spacer(Modifier.height(12.dp))
                Text("• $txtNeedReauth")
                Text("• $txtCannotUndo")
                Spacer(Modifier.height(16.dp))
                Text(
                    txtConfirmGenerate,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(txtConfirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(txtCancel)
            }
        },
    )
}

/**
 * 导入密钥提示对话框
 */
@Composable
fun ImportKeysHintDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val txtTitle = AdbTexts.BUTTON_IMPORT_KEYS.get()
    val txtHint1 = AdbTexts.ADB_KEY_IMPORT_HINT.get()
    val txtHint2 = AdbTexts.ADB_KEY_IMPORT_HINT_MULTISELECT.get()
    val txtHint3 = AdbTexts.ADB_KEY_IMPORT_HINT_BOTH_FILES.get()
    val txtConfirm = CommonTexts.BUTTON_CONFIRM.get()
    val txtCancel = CommonTexts.BUTTON_CANCEL.get()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
        },
        title = {
            Text(txtTitle)
        },
        text = {
            Column {
                Text(
                    txtHint1,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text("• $txtHint2")
                Spacer(Modifier.height(8.dp))
                Text("• $txtHint3")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(txtConfirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(txtCancel)
            }
        },
    )
}
