package com.screen.remote.android.infrastructure.scrcpy.client

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.CompatibilityCaptureSettings
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import com.screen.remote.android.infrastructure.adb.helper.DadbHelperAsset
import dadb.helper.RemoteScreenshotStream
import dadb.helper.RemoteScreenshotCaptureBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

internal class CompatibilityModeController(
    private val context: Context,
    private val adbConnectionManager: AdbConnectionManager,
    private val getDeviceId: () -> String?,
    private val getCaptureSettings: () -> CompatibilityCaptureSettings?,
    private val onFrame: (Bitmap?) -> Unit,
    private val onResolution: (Int, Int) -> Unit,
    private val onCaptureFailure: (String) -> Unit,
) {
    private val helperJar by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DadbHelperAsset.extract(context)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    @Volatile
    private var activeCaptureStream: RemoteScreenshotStream? = null
    private val touchLock = Any()
    private val inputMutex = Mutex()
    private var activePointerId: Long? = null
    private var touchStart: TouchSample? = null
    @Volatile
    private var isStarted = false
    @Volatile
    private var remoteWidth = 0
    @Volatile
    private var remoteHeight = 0

    suspend fun start() {
        activeCaptureStream?.close()
        activeCaptureStream = null
        captureJob?.cancelAndJoin()
        captureJob = null
        resetTouchState()
        remoteWidth = 0
        remoteHeight = 0
        onFrame(null)
        isStarted = true
        wakeUpScreen()
            .onSuccess {
                LogManager.i(
                    LogTags.SCRCPY_CLIENT,
                    "Compatibility mode wake-up command sent",
                )
            }.onFailure { error ->
                LogManager.w(
                    LogTags.SCRCPY_CLIENT,
                    "Compatibility mode wake-up command failed: ${error.message}",
                )
            }
        captureJob =
            scope.launch {
                var loggedCaptureBackend: RemoteScreenshotCaptureBackend? = null
                val settings = getCaptureSettings() ?: DEFAULT_CAPTURE_SETTINGS
                var consecutiveFailures = 0
                while (isActive) {
                    val openResult = openHelperStream(settings)
                    val helperStream = openResult.getOrNull()
                    if (helperStream == null) {
                        consecutiveFailures++
                        val error = openResult.exceptionOrNull()
                        if (!retryCaptureOrReportFailure(consecutiveFailures, error)) {
                            return@launch
                        }
                        continue
                    }

                    activeCaptureStream = helperStream
                    LogManager.i(
                        LogTags.SCRCPY_CLIENT,
                        "Compatibility capture started: maxSize=${settings.maxSize} jpegQuality=${settings.jpegQuality}",
                    )
                    var firstFrameReceived = false
                    val firstFrameDeadline = SystemClock.elapsedRealtime() + FIRST_FRAME_TIMEOUT_MS
                    try {
                        while (isActive) {
                            val capturedFrame = captureHelperFrame(helperStream)
                            if (capturedFrame == null) {
                                if (!firstFrameReceived && SystemClock.elapsedRealtime() >= firstFrameDeadline) {
                                    throw IllegalStateException(
                                        "Compatibility screenshot helper did not produce an initial frame within ${FIRST_FRAME_TIMEOUT_MS}ms",
                                    )
                                }
                                continue
                            }
                            firstFrameReceived = true
                            consecutiveFailures = 0
                            remoteWidth = capturedFrame.sourceWidth
                            remoteHeight = capturedFrame.sourceHeight
                            onResolution(capturedFrame.sourceWidth, capturedFrame.sourceHeight)
                            onFrame(capturedFrame.bitmap)
                            if (capturedFrame.captureBackend != loggedCaptureBackend) {
                                loggedCaptureBackend = capturedFrame.captureBackend
                                LogManager.i(
                                    LogTags.SCRCPY_CLIENT,
                                    "Compatibility capture backend selected: " +
                                        "backend=${capturedFrame.captureBackend} " +
                                        "captureMs=${capturedFrame.captureDurationMillis} " +
                                        "payloadBytes=${capturedFrame.payloadBytes}",
                                )
                            }
                        }
                    } catch (error: Exception) {
                        if (isActive) {
                            consecutiveFailures++
                            LogManager.w(
                                LogTags.SCRCPY_CLIENT,
                                "Compatibility display capture interrupted: ${error.message}",
                            )
                            if (!retryCaptureOrReportFailure(consecutiveFailures, error)) {
                                return@launch
                            }
                        }
                    } finally {
                        helperStream.close()
                        if (activeCaptureStream === helperStream) {
                            activeCaptureStream = null
                        }
                    }
                }
            }
    }

    suspend fun stop() {
        isStarted = false
        activeCaptureStream?.close()
        activeCaptureStream = null
        captureJob?.cancelAndJoin()
        captureJob = null
        resetTouchState()
        remoteWidth = 0
        remoteHeight = 0
        onFrame(null)
        LogManager.i(LogTags.SCRCPY_CLIENT, "Compatibility capture stopped")
    }

    fun sendTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
    ): Result<Boolean> {
        if (!isStarted) {
            return Result.failure(IllegalStateException("Compatibility mode is not active"))
        }
        if (currentConnection() == null) {
            return Result.failure(IllegalStateException("ADB connection is unavailable"))
        }

        val sample = TouchSample(clampX(x), clampY(y), System.currentTimeMillis())
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val accepted =
                    synchronized(touchLock) {
                        if (activePointerId == null) {
                            activePointerId = pointerId
                            touchStart = sample
                            true
                        } else {
                            activePointerId == pointerId
                        }
                    }
                return Result.success(accepted)
            }

            MotionEvent.ACTION_MOVE -> Unit

            MotionEvent.ACTION_UP -> {
                val gesture =
                    synchronized(touchLock) {
                        if (activePointerId != pointerId) {
                            null
                        } else {
                            val start = touchStart
                            activePointerId = null
                            touchStart = null
                            start?.let { it to sample }
                        }
                    }
                gesture?.let { (start, end) ->
                    scope.launch {
                        val command =
                            if (abs(end.x - start.x) <= TAP_SLOP_PX && abs(end.y - start.y) <= TAP_SLOP_PX) {
                                "input tap ${end.x} ${end.y}"
                            } else {
                                val duration = (end.timeMs - start.timeMs).coerceIn(MIN_SWIPE_DURATION_MS, MAX_SWIPE_DURATION_MS)
                                "input swipe ${start.x} ${start.y} ${end.x} ${end.y} $duration"
                            }
                        executeInput(command)
                            .onFailure { error ->
                                LogManager.w(
                                    LogTags.SCRCPY_CLIENT,
                                    "Compatibility touch command failed: ${error.message}",
                                )
                            }
                    }
                }
                return Result.success(gesture != null)
            }

            MotionEvent.ACTION_CANCEL ->
                synchronized(touchLock) {
                    if (activePointerId == pointerId) {
                        activePointerId = null
                        touchStart = null
                    }
                }

            else -> return Result.success(false)
        }
        return Result.success(true)
    }

    suspend fun sendKeyEvent(
        keyCode: Int,
        action: Int,
    ): Result<Boolean> {
        if (action == KeyEvent.ACTION_UP) {
            return Result.success(true)
        }
        return executeInput("input keyevent $keyCode")
    }

    suspend fun sendText(text: String): Result<Boolean> {
        if (text.isEmpty()) {
            return Result.success(true)
        }
        if (text.any { it.code !in ASCII_PRINTABLE_RANGE }) {
            if (!isStarted) {
                return Result.failure(IllegalStateException("Compatibility mode is not active"))
            }
            val connection =
                currentConnection()
                    ?: return Result.failure(IllegalStateException("ADB connection is unavailable"))
            return inputMutex.withLock {
                connection.injectRemoteText(
                    localHelperJar = helperJar,
                    text = text,
                )
            }
        }
        val encoded = text.replace("%", "\\%").replace(" ", "%s")
        return executeInput("input text ${encoded.shellQuoted()}")
    }

    suspend fun wakeUpScreen(): Result<Boolean> = executeInput("input keyevent ${KeyEvent.KEYCODE_WAKEUP}")

    private fun currentConnection(): AdbConnection? =
        getDeviceId()?.let(adbConnectionManager::getConnection)

    private suspend fun openHelperStream(settings: CompatibilityCaptureSettings): Result<RemoteScreenshotStream> {
        val connection =
            currentConnection()
                ?: return Result.failure(IllegalStateException("ADB connection is unavailable"))
        return runCatching {
            connection
                .openRemoteScreenshotStream(
                    localHelperJar = helperJar,
                    maxSize = settings.maxSize,
                    jpegQuality = settings.jpegQuality,
                ).getOrThrow()
        }.onFailure { error ->
            LogManager.w(
                LogTags.SCRCPY_CLIENT,
                "Compatibility screenshot helper is unavailable: ${error.message}",
            )
        }
    }

    private fun captureHelperFrame(stream: RemoteScreenshotStream): CapturedFrame? {
        val frame = stream.requestFrame() ?: return null
        val bitmap =
            BitmapFactory.decodeByteArray(frame.jpegBytes, 0, frame.jpegBytes.size)
                ?: throw IllegalStateException("Unable to decode compatibility helper JPEG")
        if (bitmap.width != frame.imageWidth || bitmap.height != frame.imageHeight) {
            bitmap.recycle()
            throw IllegalStateException(
                "Compatibility helper JPEG dimensions do not match the frame header: " +
                    "header=${frame.imageWidth}x${frame.imageHeight}",
            )
        }
        return CapturedFrame(
            bitmap = bitmap,
            sourceWidth = frame.sourceWidth,
            sourceHeight = frame.sourceHeight,
            captureBackend = frame.captureBackend,
            captureDurationMillis = frame.captureDurationMillis,
            payloadBytes = frame.jpegBytes.size,
        )
    }

    private suspend fun executeInput(command: String): Result<Boolean> {
        if (!isStarted) {
            return Result.failure(IllegalStateException("Compatibility mode is not active"))
        }
        return inputMutex.withLock {
            val connection =
                currentConnection()
                    ?: return@withLock Result.failure(IllegalStateException("ADB connection is unavailable"))
            val checkedCommand = "{ $command; }; status=\$?; echo \"$INPUT_EXIT_MARKER\$status\""
            connection
                .executeShell(checkedCommand, retryOnFailure = false)
                .mapCatching { output ->
                    val exitCode =
                        output
                            .lineSequence()
                            .map(String::trim)
                            .firstOrNull { it.startsWith(INPUT_EXIT_MARKER) }
                            ?.removePrefix(INPUT_EXIT_MARKER)
                            ?.toIntOrNull()
                            ?: throw IllegalStateException(
                                "Compatibility input command did not report an exit status",
                            )
                    check(exitCode == 0) {
                        "Compatibility input command failed with exit code $exitCode"
                    }
                    true
                }
        }
    }

    private suspend fun retryCaptureOrReportFailure(
        consecutiveFailures: Int,
        error: Throwable?,
    ): Boolean {
        if (consecutiveFailures >= MAX_CONSECUTIVE_CAPTURE_FAILURES) {
            val message =
                "Compatibility display capture stopped after $consecutiveFailures consecutive failures: " +
                    (error?.message ?: "unknown error")
            LogManager.e(LogTags.SCRCPY_CLIENT, message, error)
            isStarted = false
            onFrame(null)
            onCaptureFailure(message)
            return false
        }
        val retryDelayMs = (CAPTURE_RETRY_DELAY_MS * consecutiveFailures).coerceAtMost(MAX_CAPTURE_RETRY_DELAY_MS)
        LogManager.i(
            LogTags.SCRCPY_CLIENT,
            "Compatibility display capture will retry in ${retryDelayMs}ms " +
                "after failure $consecutiveFailures/$MAX_CONSECUTIVE_CAPTURE_FAILURES",
        )
        delay(retryDelayMs)
        return true
    }

    private fun resetTouchState() {
        synchronized(touchLock) {
            activePointerId = null
            touchStart = null
        }
    }

    private fun clampX(x: Int): Int = remoteWidth.takeIf { it > 0 }?.let { x.coerceIn(0, it - 1) } ?: x.coerceAtLeast(0)

    private fun clampY(y: Int): Int = remoteHeight.takeIf { it > 0 }?.let { y.coerceIn(0, it - 1) } ?: y.coerceAtLeast(0)

    private fun String.shellQuoted(): String = "'" + replace("'", "'\\''") + "'"

    private data class TouchSample(
        val x: Int,
        val y: Int,
        val timeMs: Long,
    )

    private data class CapturedFrame(
        val bitmap: Bitmap,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val captureBackend: RemoteScreenshotCaptureBackend,
        val captureDurationMillis: Int = 0,
        val payloadBytes: Int = 0,
    )

    private companion object {
        const val TAP_SLOP_PX = 12
        const val MIN_SWIPE_DURATION_MS = 80L
        const val MAX_SWIPE_DURATION_MS = 1_000L
        const val FIRST_FRAME_TIMEOUT_MS = 3_000L
        val ASCII_PRINTABLE_RANGE = 0x20..0x7e
        const val MAX_CONSECUTIVE_CAPTURE_FAILURES = 3
        const val CAPTURE_RETRY_DELAY_MS = 500L
        const val MAX_CAPTURE_RETRY_DELAY_MS = 2_000L
        const val INPUT_EXIT_MARKER = "__SCREEN_REMOTE_INPUT_EXIT__="
        val DEFAULT_CAPTURE_SETTINGS = CompatibilityCaptureSettings(maxSize = 720, jpegQuality = 55)
    }
}
