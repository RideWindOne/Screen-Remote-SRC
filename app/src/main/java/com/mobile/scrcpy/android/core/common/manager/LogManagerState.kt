package com.mobile.scrcpy.android.core.common.manager

import android.annotation.SuppressLint
import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale

internal class LogManagerState {
    var context: Context? = null
    var isEnabled = true
    var enableAudioStreamLog = false
    var enableVideoStreamLog = false
    var enableControlStreamLog = false
    var enableShellStreamLog = false
    var enableManagementLog = false
    var logFile: File? = null
    var fileWriter: FileWriter? = null

    @SuppressLint("ConstantLocale")
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @SuppressLint("ConstantLocale")
    val fileNameFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
}
