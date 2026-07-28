package com.screen.remote.android.feature.settings.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.screen.remote.android.core.common.AppConstants
import com.screen.remote.android.feature.session.viewmodel.MainViewModel
import com.screen.remote.android.feature.settings.ui.internal.SettingsScreenContent
import com.screen.remote.android.feature.settings.ui.internal.SettingsScreenDialogs
import com.screen.remote.android.feature.settings.ui.internal.rememberSettingsScreenRouteState
import com.screen.remote.android.feature.settings.ui.internal.rememberSettingsScreenTexts

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToAdbKeys: () -> Unit = {},
    onNavigateToLogManagement: () -> Unit = {},
    onNavigateToGroupManagement: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToCustomCommands: () -> Unit = {},
    openDevicePairingOnEntry: Boolean = false,
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val routeState = rememberSettingsScreenRouteState()
    val texts = rememberSettingsScreenTexts()

    LaunchedEffect(openDevicePairingOnEntry) {
        if (openDevicePairingOnEntry) {
            routeState.openDevicePairingDialog()
        }
    }

    SettingsScreenContent(
        settings = settings,
        texts = texts,
        routeState = routeState,
        onBack = onBack,
        onNavigateToAbout = onNavigateToAbout,
        onNavigateToAppearance = onNavigateToAppearance,
        onNavigateToLanguage = onNavigateToLanguage,
        onNavigateToAdbKeys = onNavigateToAdbKeys,
        onNavigateToLogManagement = onNavigateToLogManagement,
        onNavigateToGroupManagement = onNavigateToGroupManagement,
        onNavigateToBackupRestore = onNavigateToBackupRestore,
        onNavigateToCustomCommands = onNavigateToCustomCommands,
        onUpdateSettings = viewModel::updateSettings,
        onOpenIssueTracker = { launchExternalLink(context, AppConstants.GITHUB_ISSUES) },
        onOpenUserGuide = { launchExternalLink(context, AppConstants.GITHUB_USER_GUIDE) },
    )

    SettingsScreenDialogs(
        routeState = routeState,
        texts = texts,
    )
}

@SuppressLint("QueryPermissionsNeeded")
private fun launchExternalLink(
    context: Context,
    url: String,
) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    context.startActivity(intent)
}
