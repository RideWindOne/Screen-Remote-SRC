package com.mobile.scrcpy.android.core.common.manager

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.mobile.scrcpy.android.core.common.AppConstants
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.i18n.LogTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("StaticFieldLeak") // 使用 applicationContext，不会造成内存泄漏
object LogManager {
    private const val LOG_DIR = "logs"
    private const val MAX_LOG_SIZE = 10 * 1024 * 1024 // 10MB
    
    private var context: Context? = null
    private var isEnabled = true
    private var logFile: File? = null
    private var fileWriter: FileWriter? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    fun init(context: Context, enabled: Boolean = true) {
        // 使用 applicationContext 避免内存泄漏
        this.context = context.applicationContext
        this.isEnabled = enabled
        if (enabled) {
            initLogFile()
        }
    }
    
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (enabled) {
            initLogFile()
        } else {
            closeLogFile()
        }
    }
    
    private fun initLogFile() {
        try {
            val ctx = context ?: return
            val logDir = File(ctx.filesDir, LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            val version = AppConstants.APP_VERSION
            val date = fileNameFormat.format(Date())
            val fileName = "Scrcpy_Remote_${version}_${date}.log"
            logFile = File(logDir, fileName)
            
            // 检查文件大小，如果超过限制则创建新文件
            if (logFile?.exists() == true && (logFile?.length() ?: 0) > MAX_LOG_SIZE) {
                val timestamp = System.currentTimeMillis()
                val newFileName = "Scrcpy_Remote_${version}_${date}_$timestamp.log"
                logFile = File(logDir, newFileName)
            }
            
            fileWriter = FileWriter(logFile, true)
            i(LogTags.LOG_MANAGER, "${LogTexts.LOG_SYSTEM_INIT_SUCCESS.get()}: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(LogTags.LOG_MANAGER, LogTexts.LOG_INIT_FILE_FAILED.get(), e)
        }
    }
    
    private fun closeLogFile() {
        try {
            fileWriter?.close()
            fileWriter = null
        } catch (e: Exception) {
            Log.e(LogTags.LOG_MANAGER, LogTexts.LOG_CLOSE_FILE_FAILED.get(), e)
        }
    }
    
    private fun writeLog(level: String, tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timestamp = dateFormat.format(Date())
                val logMessage = buildString {
                    append("[$timestamp] ")
                    append("$level/$tag: ")
                    append(message)
                    if (throwable != null) {
                        append("\n")
                        append(throwable.stackTraceToString())
                    }
                    append("\n")
                }
                
                fileWriter?.apply {
                    write(logMessage)
                    flush()
                }
                
                // 检查文件大小
                if ((logFile?.length() ?: 0) > MAX_LOG_SIZE) {
                    closeLogFile()
                    initLogFile()
                }
            } catch (e: Exception) {
                Log.e(LogTags.LOG_MANAGER, LogTexts.LOG_WRITE_FAILED.get(), e)
            }
        }
    }
    
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        Log.v(tag, message, throwable)
        writeLog("V", tag, message, throwable)
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        Log.d(tag, message, throwable)
        writeLog("D", tag, message, throwable)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
        writeLog("I", tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        writeLog("W", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        writeLog("E", tag, message, throwable)
    }
    
    fun getLogFiles(): List<File> {
        val ctx = context ?: return emptyList()
        val logDir = File(ctx.filesDir, LOG_DIR)
        if (!logDir.exists()) return emptyList()
        
        return logDir.listFiles()?.filter { it.extension == "log" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    fun getTotalLogSize(): Long {
        return getLogFiles().sumOf { it.length() }
    }
    
    fun clearAllLogs() {
        closeLogFile()
        val ctx = context ?: return
        val logDir = File(ctx.filesDir, LOG_DIR)
        if (logDir.exists()) {
            logDir.listFiles()?.forEach { it.delete() }
        }
        if (isEnabled) {
            initLogFile()
        }
    }
    
    fun clearOldLogs() {
        val ctx = context ?: return
        val logDir = File(ctx.filesDir, LOG_DIR)
        if (!logDir.exists()) return
        
        val currentLogFile = logFile
        logDir.listFiles()?.forEach { file ->
            if (file != currentLogFile && file.extension == "log") {
                try {
                    file.delete()
                    i(LogTags.LOG_MANAGER, "${LogTexts.LOG_DELETE_FILE_SUCCESS.get()}: ${file.name}")
                } catch (e: Exception) {
                    e(LogTags.LOG_MANAGER, "${LogTexts.LOG_DELETE_FILE_FAILED.get()}: ${file.name}", e)
                }
            }
        }
    }
    
    fun deleteLogFile(file: File): Boolean {
        return try {
            if (file == logFile) {
                closeLogFile()
                val result = file.delete()
                if (isEnabled) {
                    initLogFile()
                }
                result
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(LogTags.LOG_MANAGER, LogTexts.LOG_DELETE_FILE_FAILED.get(), e)
            false
        }
    }
    
    fun readLogFile(file: File): String {
        return try {
            file.readText()
        } catch (e: Exception) {
            Log.e(LogTags.LOG_MANAGER, LogTexts.LOG_READ_FILE_FAILED.get(), e)
            "${LogTexts.LOG_READ_FILE_ERROR.get()}: ${e.message}"
        }
    }
    
    /**
     * 直接写入原始日志（不通过 Android Log）
     * 用于捕获 Native 代码、scrcpy-server 等外部日志
     * @param level 日志级别 (V/D/I/W/E)
     * @param tag 日志标签
     * @param message 日志消息
     */
    fun writeRawLog(level: String, tag: String, message: String) {
        if (!isEnabled) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timestamp = dateFormat.format(Date())
                val logMessage = buildString {
                    append("[$timestamp] ")
                    append("$level/$tag: ")
                    append(message)
                    if (!message.endsWith("\n")) {
                        append("\n")
                    }
                }
                
                fileWriter?.apply {
                    write(logMessage)
                    flush()
                }
                
                // 检查文件大小
                if ((logFile?.length() ?: 0) > MAX_LOG_SIZE) {
                    closeLogFile()
                    initLogFile()
                }
            } catch (e: Exception) {
                // 如果写入失败，至少输出到 Android Log
                Log.e(LogTags.LOG_MANAGER, LogTexts.LOG_WRITE_RAW_FAILED.get(), e)
            }
        }
    }
    
    /**
     * 写入原始日志（JNI 调用）
     * @param level 日志级别
     * @param tag 日志标签
     * @param message 日志消息
     */
    @JvmStatic
    fun writeRawLogJNI(level: String, tag: String, message: String) {
        writeRawLog(level, tag, message)
    }
}
