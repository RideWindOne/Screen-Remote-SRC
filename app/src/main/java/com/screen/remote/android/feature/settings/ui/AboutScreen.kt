package com.screen.remote.android.feature.settings.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.screen.remote.android.R
import com.screen.remote.android.core.common.AppConstants
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SettingsTexts

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showWechatGroupDialog by remember { mutableStateOf(false) }
    var showDonateDialog by remember { mutableStateOf(false) }

    DialogPage(
        title = SettingsTexts.SETTINGS_ABOUT.get(),
        onDismiss = onBack,
        enableScroll = true,
    ) {
        // 版本信息
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Screen Remote ${AppConstants.APP_VERSION}",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${SettingsTexts.ABOUT_BASED_ON.get()} ${AppConstants.SCRCPY_VERSION}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 应用说明卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = SettingsTexts.ABOUT_DESCRIPTION.get(),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = SettingsTexts.ABOUT_CONNECTION_TIP.get(),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.error,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = SettingsTexts.ABOUT_HELP_TEXT.get(),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 帮助与支持卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(AppDimens.listItemHeight)
                            .clickable { showWechatGroupDialog = true }
                            .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = SettingsTexts.ABOUT_WECHAT_BUTTON.get(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )

                // Telegram 频道
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(AppDimens.listItemHeight)
                            // 临时禁用 Telegram 频道功能
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, AppConstants.TELEGRAM_CHANNEL.toUri())
                                context.startActivity(intent)
                            }.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = SettingsTexts.ABOUT_TELEGRAM_BUTTON.get(),
                        style = MaterialTheme.typography.bodyLarge,
                        // color = MaterialTheme.colorScheme.onSurfaceVariant,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        // tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(AppDimens.listItemHeight)
                            .clickable { showDonateDialog = true }
                            .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = SettingsTexts.ABOUT_DONATE_BUTTON.get(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )

                // 链接
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(AppDimens.listItemHeight)
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, AppConstants.GITHUB_REPO.toUri())
                                context.startActivity(intent)
                            }.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = SettingsTexts.ABOUT_PORTING_BUTTON.get(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (showWechatGroupDialog) {
            WechatGroupDialog(
                onDismiss = { showWechatGroupDialog = false },
            )
        }

        if (showDonateDialog) {
            DonateDialog(
                onDismiss = { showDonateDialog = false },
            )
        }
    }
}

@Composable
private fun DonateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val trc20Address = "TMrGbcfyXT4cf49EULAoBfB5mfYNeAyxLj"
    val gateInviteCode = "ZKDRFFFF"

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(SettingsTexts.ABOUT_DONATE_TITLE.get())
        },
        modifier = Modifier.fillMaxWidth(AppDimens.WINDOW_WIDTH_RATIO),
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DonateInfoCard(
                    label = SettingsTexts.ABOUT_DONATE_USDT_LABEL.get(),
                    value = trc20Address,
                    copiedToast = SettingsTexts.ABOUT_DONATE_ADDRESS_COPIED.get(),
                )

                DonateInfoCard(
                    label = SettingsTexts.ABOUT_DONATE_GATE_LABEL.get(),
                    value = gateInviteCode,
                    copiedToast = SettingsTexts.ABOUT_DONATE_INVITE_COPIED.get(),
                )

                Text(
                    text = SettingsTexts.ABOUT_DONATE_GATE_INFO.get(),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(CommonTexts.BUTTON_CLOSE.get())
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun DonateInfoCard(
    label: String,
    value: String,
    copiedToast: String,
) {
    val context = LocalContext.current
    val isLongValue = value.length > 16

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            ),
        border =
            BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = SettingsTexts.ABOUT_DONATE_COPY_HINT.get(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = value,
                modifier =
                    Modifier.clickable {
                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, value))
                        Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                    },
                style = MaterialTheme.typography.bodyMedium,
                fontSize = if (isLongValue) 12.sp else 15.sp,
                lineHeight = if (isLongValue) 16.sp else 20.sp,
                fontFamily = if (isLongValue) FontFamily.Monospace else FontFamily.Default,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@SuppressLint("LocalContextResourcesRead")
@Composable
private fun WechatGroupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap =
        remember {
            BitmapFactory.decodeResource(context.resources, R.drawable.wechat_qr)?.asImageBitmap()
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(AppDimens.WINDOW_WIDTH_RATIO),
        title = {
            Text(SettingsTexts.ABOUT_WECHAT_BUTTON.get())
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = SettingsTexts.ABOUT_WECHAT_QR.get(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                bitmap?.let {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = it,
                            contentDescription = SettingsTexts.ABOUT_WECHAT_QR.get(),
                            modifier =
                                Modifier
                                    .size(280.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            saveWechatQrToGallery(context)
                                        },
                                    ),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                Text(
                    text = SettingsTexts.ABOUT_WECHAT_SAVE.get(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = SettingsTexts.ABOUT_WECHAT_ID.get(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(CommonTexts.BUTTON_CLOSE.get())
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

private fun saveWechatQrToGallery(context: android.content.Context) {
    runCatching {
        val bitmap =
            BitmapFactory.decodeResource(context.resources, R.drawable.wechat_qr)
                ?: error("decode qr failed")
        val fileName = "scrcpy_remote_wechat_group_${System.currentTimeMillis()}.png"
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScrcpyMobile")
                }
            }

        val resolver = context.contentResolver
        val uri =
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("create gallery file failed")

        resolver.openOutputStream(uri)?.use { output ->
            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                error("write gallery file failed")
            }
        } ?: error("open output stream failed")
    }.onSuccess {
        Toast.makeText(context, SettingsTexts.ABOUT_WECHAT_SAVED.get(), Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, SettingsTexts.ABOUT_WECHAT_SAVE_F.get(), Toast.LENGTH_SHORT).show()
    }
}
