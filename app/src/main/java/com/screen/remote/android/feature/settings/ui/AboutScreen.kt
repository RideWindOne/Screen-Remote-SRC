package com.screen.remote.android.feature.settings.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.screen.remote.android.R
import com.screen.remote.android.core.common.AppConstants
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.common.IosDesignTokens
import com.screen.remote.android.core.designsystem.component.IOSSwitch
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.data.datastore.PreferencesManager
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SettingsTexts
import com.screen.remote.android.core.telemetry.TelemetryPreferences
import com.screen.remote.android.core.telemetry.TelemetryJournal
import com.screen.remote.android.core.telemetry.TelemetryManager
import com.screen.remote.android.core.update.GitHubReleaseInfo
import com.screen.remote.android.core.update.GitHubReleaseUpdateChecker
import com.screen.remote.android.core.update.AppUpdateDownloader
import com.screen.remote.android.core.update.DownloadedUpdate
import com.screen.remote.android.core.update.selectApkAsset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val RELEASES_URL = "https://github.com/XRSec/Screen-Remote/releases"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateChecker = remember { GitHubReleaseUpdateChecker() }
    val preferencesManager = remember(context) { PreferencesManager(context.applicationContext) }
    val telemetryPreferences = remember(context) { TelemetryPreferences(context.applicationContext) }
    var showWechatGroupDialog by remember { mutableStateOf(false) }
    var showDonateDialog by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<GitHubReleaseInfo?>(null) }
    var autoCheckUpdates by remember { mutableStateOf<Boolean?>(null) }
    var telemetryEnabled by remember { mutableStateOf<Boolean?>(null) }
    var uploadingTelemetry by remember { mutableStateOf(false) }
    var showTelemetryConsentDialog by remember { mutableStateOf(false) }

    fun checkForUpdate() {
        if (checkingUpdate) return
        scope.launch {
            val now = System.currentTimeMillis()
            val settings = preferencesManager.settingsFlow.first()
            checkingUpdate = true
            updateChecker
                .check(
                    channel = settings.updateChannel,
                ).onSuccess { release ->
                    preferencesManager.recordUpdateCheck(now, release)
                    if (release == null) {
                        Toast.makeText(context, SettingsTexts.ABOUT_UPDATE_LATEST.get(), Toast.LENGTH_SHORT).show()
                    } else {
                        availableUpdate = release
                    }
                }.onFailure {
                    Toast.makeText(context, SettingsTexts.ABOUT_UPDATE_FAILED.get(), Toast.LENGTH_SHORT).show()
                }
            checkingUpdate = false
        }
    }

    LaunchedEffect(preferencesManager) {
        val settings = preferencesManager.settingsFlow.first()
        autoCheckUpdates = settings.autoCheckUpdates
        telemetryEnabled = telemetryPreferences.stateFlow.first().enabled
    }

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

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDimens.cardCornerRadius),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(AppDimens.listItemHeight)
                        .padding(horizontal = IosDesignTokens.standardHorizontalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = SettingsTexts.ABOUT_AUTO_CHECK_UPDATE.get(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(IosDesignTokens.compactInlineSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { checkForUpdate() },
                        enabled = !checkingUpdate,
                        modifier = Modifier.size(AppDimens.listItemHeight),
                    ) {
                        if (checkingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(IosDesignTokens.externalIconSize),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = SettingsTexts.ABOUT_CHECK_UPDATE.get(),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(IosDesignTokens.externalIconSize),
                            )
                        }
                    }
                    IOSSwitch(
                        checked = autoCheckUpdates == true,
                        enabled = autoCheckUpdates != null,
                        onCheckedChange = { enabled ->
                            autoCheckUpdates = enabled
                            scope.launch {
                                val settings = preferencesManager.settingsFlow.first()
                                preferencesManager.updateSettings(settings.copy(autoCheckUpdates = enabled))
                            }
                        },
                    )
                }
            }

            SettingsDivider()

            SettingsSwitch(
                title = SettingsTexts.ABOUT_TELEMETRY.get(),
                checked = telemetryEnabled == true,
                enabled = telemetryEnabled != null,
                helpText = SettingsTexts.ABOUT_TELEMETRY_HELP.get(),
                trailingAction = {
                    IconButton(
                        onClick = {
                            if (uploadingTelemetry) return@IconButton
                            uploadingTelemetry = true
                            scope.launch {
                                try {
                                    val result = TelemetryManager.uploadTodayAndYesterday(context.applicationContext)
                                    val message =
                                        when {
                                            result.failedCount > 0 -> SettingsTexts.ABOUT_TELEMETRY_UPLOAD_PARTIAL.get()
                                            result.uploadedCount > 0 -> SettingsTexts.ABOUT_TELEMETRY_UPLOAD_SUCCESS.get()
                                            else -> SettingsTexts.ABOUT_TELEMETRY_UPLOAD_EMPTY.get()
                                        }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        SettingsTexts.ABOUT_TELEMETRY_UPLOAD_PARTIAL.get(),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } finally {
                                    uploadingTelemetry = false
                                }
                            }
                        },
                        enabled = telemetryEnabled == true && !uploadingTelemetry,
                        modifier = Modifier.size(AppDimens.listItemHeight),
                    ) {
                        if (uploadingTelemetry) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(IosDesignTokens.externalIconSize),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = SettingsTexts.ABOUT_TELEMETRY_UPLOAD.get(),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(IosDesignTokens.externalIconSize),
                            )
                        }
                    }
                },
                onCheckedChange = { enabled ->
                    if (enabled) {
                        showTelemetryConsentDialog = true
                    } else {
                        telemetryEnabled = false
                        TelemetryJournal.setEnabled(false)
                        scope.launch { telemetryPreferences.setEnabled(false) }
                    }
                },
            )
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

        if (showTelemetryConsentDialog) {
            AlertDialog(
                onDismissRequest = { showTelemetryConsentDialog = false },
                title = {
                    Text(SettingsTexts.ABOUT_TELEMETRY_CONSENT_TITLE.get())
                },
                text = {
                    Text(SettingsTexts.ABOUT_TELEMETRY_CONSENT.get())
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showTelemetryConsentDialog = false
                            telemetryEnabled = true
                            TelemetryJournal.setEnabled(true)
                            scope.launch { telemetryPreferences.setEnabled(true) }
                        },
                    ) {
                        Text(SettingsTexts.ABOUT_TELEMETRY_ENABLE.get())
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTelemetryConsentDialog = false }) {
                        Text(SettingsTexts.ABOUT_TELEMETRY_CANCEL.get())
                    }
                },
            )
        }

        availableUpdate?.let { release ->
            UpdateAvailableDialog(
                release = release,
                onDismiss = { availableUpdate = null },
            )
        }
    }
}

@Composable
internal fun UpdateAvailableDialog(
    release: GitHubReleaseInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloader = remember(context) { AppUpdateDownloader(context.applicationContext) }
    val asset = remember(release) { selectApkAsset(release, Build.SUPPORTED_ABIS.toList()) }
    var downloadProgress by remember { mutableStateOf<Int?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var pendingUpdate by remember { mutableStateOf<DownloadedUpdate?>(null) }
    val installPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val update = pendingUpdate ?: return@rememberLauncherForActivityResult
            if (AppUpdateDownloader.canInstallPackages(context)) {
                AppUpdateDownloader.install(context, update)
                pendingUpdate = null
                onDismiss()
            } else {
                Toast.makeText(context, SettingsTexts.ABOUT_UPDATE_INSTALL_PERMISSION.get(), Toast.LENGTH_LONG).show()
            }
        }

    fun installOrRequestPermission(update: DownloadedUpdate) {
        if (AppUpdateDownloader.canInstallPackages(context)) {
            AppUpdateDownloader.install(context, update)
            onDismiss()
        } else {
            pendingUpdate = update
            installPermissionLauncher.launch(AppUpdateDownloader.unknownSourcesSettingsIntent(context))
        }
    }

    fun startDownload() {
        if (downloadJob != null) return
        if (asset == null) {
            Toast.makeText(context, SettingsTexts.ABOUT_UPDATE_NO_APK.get(), Toast.LENGTH_LONG).show()
            return
        }
        downloadJob =
            scope.launch {
                try {
                    val update = downloader.download(asset) { downloadProgress = it }
                    installOrRequestPermission(update)
                } catch (_: CancellationException) {
                    // The user explicitly cancelled the download.
                } catch (_: Exception) {
                    Toast.makeText(context, SettingsTexts.ABOUT_UPDATE_DOWNLOAD_FAILED.get(), Toast.LENGTH_LONG).show()
                } finally {
                    downloadJob = null
                    downloadProgress = null
                }
            }
    }

    AlertDialog(
        onDismissRequest = {
            downloadJob?.cancel()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(AppDimens.WINDOW_WIDTH_RATIO),
        title = {
            Text(
                text = SettingsTexts.ABOUT_UPDATE_AVAILABLE.get(),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
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
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    UpdateVersionRow(
                        label = SettingsTexts.ABOUT_UPDATE_CURRENT_VERSION.format(""),
                        version = AppConstants.APP_VERSION,
                        highlight = false,
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    UpdateVersionRow(
                        label = SettingsTexts.ABOUT_UPDATE_LATEST_VERSION.format(""),
                        version = release.tagName,
                        highlight = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                enabled = downloadJob == null,
                onClick = ::startDownload,
            ) {
                Text(
                    text =
                        if (downloadJob != null) {
                            SettingsTexts.ABOUT_UPDATE_DOWNLOADING.format(
                                downloadProgress?.let { "$it%" } ?: "…",
                            )
                        } else {
                            SettingsTexts.ABOUT_UPDATE_DOWNLOAD_INSTALL.get()
                        },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                onClick = {
                    if (downloadJob != null) {
                        downloadJob?.cancel()
                    } else {
                        val url = release.htmlUrl.ifBlank { RELEASES_URL }
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        onDismiss()
                    }
                },
            ) {
                Text(
                    text =
                        if (downloadJob != null) {
                            SettingsTexts.ABOUT_UPDATE_CANCEL_DOWNLOAD.get()
                        } else {
                            SettingsTexts.ABOUT_UPDATE_OPEN_RELEASES.get()
                        },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun UpdateVersionRow(
    label: String,
    version: String,
    highlight: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.trimEnd('：', ':', ' '),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = version,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DonateDialog(onDismiss: () -> Unit) {
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

private fun saveWechatQrToGallery(context: Context) {
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
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                error("write gallery file failed")
            }
        } ?: error("open output stream failed")
    }.onSuccess {
        Toast.makeText(context, SettingsTexts.ABOUT_WECHAT_SAVED.get(), Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, SettingsTexts.ABOUT_WECHAT_SAVE_F.get(), Toast.LENGTH_SHORT).show()
    }
}
