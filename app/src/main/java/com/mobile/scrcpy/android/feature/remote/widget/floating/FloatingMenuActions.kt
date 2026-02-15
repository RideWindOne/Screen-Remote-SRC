package com.mobile.scrcpy.android.feature.remote.widget.floating

import com.mobile.scrcpy.android.feature.remote.presentation.ControlViewModel

data class FloatingMenuActions(
    val controlViewModel: ControlViewModel,
    val captureTargetDeviceScreenshot: suspend () -> Result<String>,
    val disconnect: suspend () -> Unit,
    val showKeyboardInput: () -> Unit,
    val requestUploadFilePicker: () -> Unit,
    val requestLayoutInspectorRender: () -> Unit,
    val hapticEnabled: Boolean,
)
