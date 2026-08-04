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
import dadb.helper.RemoteScreenshotCaptureBackend
import dadb.helper.RemoteScreenshotStream
import dadb.helper.RemoteTouchStream
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
import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

internal class CompatibilityModeController(
    private val context: Context,
    private val adbConnectionManager: AdbConnectionManager,
    private val getDeviceId: () -> String?,
    private val getCaptureSettings: () -> CompatibilityCaptureSettings?,
    private val onFrame: (Bitmap?) -> Unit,
    private val onResolution: (Int, Int) -> Unit,
    private val onConnectionLost: (String) -> Unit,
    private val onCaptureFailure: (String) -> Unit,
) {
    private val helperJar by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DadbHelperAsset.extract(context)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var touchDispatchJob: Job? = null

    @Volatile
    private var activeCaptureStream: RemoteScreenshotStream? = null

    @Volatile
    private var activeTouchStream: RemoteTouchStream? = null
    private val touchLock = Any()
    private val inputMutex = Mutex()
    private var activePointerId: Long? = null
    private val liveTouchQueue = CompatibilityLiveTouchQueue()
    private var liveTouchDispatchRunning = false

    @Volatile
    private var isStarted = false

    @Volatile
    private var remoteWidth = 0

    @Volatile
    private var remoteHeight = 0
    private val connectionLostHandled = AtomicBoolean(false)

    suspend fun start(): Result<Boolean> {
        isStarted = false
        safeCloseScreenshotStream(activeCaptureStream)
        activeCaptureStream = null
        captureJob?.cancelAndJoin()
        captureJob = null
        safeCloseTouchStream(activeTouchStream)
        activeTouchStream = null
        touchDispatchJob?.cancelAndJoin()
        touchDispatchJob = null
        resetTouchState()
        remoteWidth = 0
        remoteHeight = 0
        onFrame(null)
        isStarted = true
        connectionLostHandled.set(false)
        wakeUpScreen().onSuccess {
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
        val touchStreamResult = openTouchHelperStream()
        val touchStream = touchStreamResult.getOrNull()
        if (touchStream == null) {
            val error = touchStreamResult.exceptionOrNull()
                ?: IllegalStateException("Compatibility live touch stream failed without an error")
            if (handleConnectionLostForClosedError("compatibility live touch", error)) {
                return Result.success(true)
            }
            LogManager.w(
                LogTags.SCRCPY_CLIENT,
                "Compatibility live touch stream failed to start and will retry: ${error.message}",
            )
            touchDispatchJob = scope.launch {
                recoverTouchStream(error)
            }
        } else {
            activeTouchStream = touchStream
            LogManager.i(
                LogTags.SCRCPY_CLIENT,
                "Compatibility live touch stream started",
            )
        }
        captureJob = scope.launch {
            var loggedCaptureBackend: RemoteScreenshotCaptureBackend? = null
            var consecutiveFailures = 0
            while (isActive) {
                val settings = getCaptureSettings() ?: DEFAULT_CAPTURE_SETTINGS
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
                logCaptureSettings("capture start", settings)
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
                                "Compatibility capture backend selected: " + "backend=${capturedFrame.captureBackend} " + "captureMs=${capturedFrame.captureDurationMillis} " + "payloadBytes=${capturedFrame.payloadBytes}",
                            )
                        }
                    }
                } catch (error: Exception) {
                    if (handleConnectionLostForClosedError("compatibility live capture", error)) {
                        return@launch
                    }
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
                    safeCloseScreenshotStream(helperStream)
                    if (activeCaptureStream === helperStream) {
                        activeCaptureStream = null
                    }
                }
            }
        }
        return Result.success(true)
    }

    suspend fun stop() {
        isStarted = false
        safeCloseTouchStream(activeTouchStream)
        activeTouchStream = null
        touchDispatchJob?.cancelAndJoin()
        touchDispatchJob = null
        safeCloseScreenshotStream(activeCaptureStream)
        activeCaptureStream = null
        captureJob?.cancelAndJoin()
        captureJob = null
        connectionLostHandled.set(false)
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
        if (activeTouchStream == null) {
            return Result.failure(IllegalStateException("Compatibility live touch stream is unavailable"))
        }

        val sample = TouchSample(clampX(x), clampY(y))
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                var shouldLaunchDispatcher = false
                val accepted = synchronized(touchLock) {
                    if (activePointerId == null) {
                        activePointerId = pointerId
                        liveTouchQueue.offer(event = liveTouchEvent(action, pointerId, sample))
                        if (!liveTouchDispatchRunning) {
                            liveTouchDispatchRunning = true
                            shouldLaunchDispatcher = true
                        }
                        true
                    } else {
                        activePointerId == pointerId
                    }
                }
                if (shouldLaunchDispatcher) {
                    launchLiveTouchDispatcher()
                }
                return Result.success(accepted)
            }

            MotionEvent.ACTION_MOVE -> {
                var shouldLaunchDispatcher = false
                val accepted = synchronized(touchLock) {
                    if (activePointerId != pointerId) {
                        false
                    } else {
                        liveTouchQueue.offer(event = liveTouchEvent(action, pointerId, sample))
                        if (!liveTouchDispatchRunning) {
                            liveTouchDispatchRunning = true
                            shouldLaunchDispatcher = true
                        }
                        true
                    }
                }
                if (shouldLaunchDispatcher) {
                    launchLiveTouchDispatcher()
                }
                return Result.success(accepted)
            }

            MotionEvent.ACTION_UP -> {
                var shouldLaunchDispatcher = false
                val accepted = synchronized(touchLock) {
                    if (activePointerId != pointerId) {
                        false
                    } else {
                        liveTouchQueue.offer(event = liveTouchEvent(action, pointerId, sample))
                        if (!liveTouchDispatchRunning) {
                            liveTouchDispatchRunning = true
                            shouldLaunchDispatcher = true
                        }
                        true
                    }
                }
                if (shouldLaunchDispatcher) {
                    launchLiveTouchDispatcher()
                }
                return Result.success(accepted)
            }

            MotionEvent.ACTION_CANCEL -> {
                var shouldLaunchDispatcher = false
                val accepted = synchronized(touchLock) {
                    if (activePointerId != pointerId) {
                        false
                    } else {
                        liveTouchQueue.offer(event = liveTouchEvent(action, pointerId, sample))
                        if (!liveTouchDispatchRunning) {
                            liveTouchDispatchRunning = true
                            shouldLaunchDispatcher = true
                        }
                        true
                    }
                }
                if (shouldLaunchDispatcher) {
                    launchLiveTouchDispatcher()
                }
                return Result.success(accepted)
            }

            else -> return Result.success(false)
        }
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
                currentConnection() ?: return Result.failure(IllegalStateException("ADB connection is unavailable"))
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

    suspend fun startApp(name: String): Result<Boolean> {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            return Result.success(true)
        }
        val launchCommand = "monkey -p ${normalizedName.shellQuoted()} -c android.intent.category.LAUNCHER 1"
        return executeInput(launchCommand, allowShellPasswordFallback = true)
    }

    suspend fun wakeUpScreen(): Result<Boolean> = executeInput("input keyevent ${KeyEvent.KEYCODE_WAKEUP}")

    private fun currentConnection(): AdbConnection? = getDeviceId()?.let(adbConnectionManager::getConnection)

    private suspend fun openHelperStream(settings: CompatibilityCaptureSettings): Result<RemoteScreenshotStream> {
        val connection =
            currentConnection() ?: return Result.failure(IllegalStateException("ADB connection is unavailable"))
        return runCatching {
            connection.openRemoteScreenshotStream(
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

    private suspend fun openTouchHelperStream(): Result<RemoteTouchStream> {
        val connection =
            currentConnection() ?: return Result.failure(IllegalStateException("ADB connection is unavailable"))
        return connection.openRemoteTouchStream(localHelperJar = helperJar)
    }

    private fun launchLiveTouchDispatcher() {
        touchDispatchJob = scope.launch {
            while (isActive) {
                val event = synchronized(touchLock) {
                    liveTouchQueue.poll() ?: run {
                        liveTouchDispatchRunning = false
                        null
                    }
                } ?: return@launch
                val stream = activeTouchStream ?: run {
                    synchronized(touchLock) {
                        liveTouchDispatchRunning = false
                    }
                    return@launch
                }
                val result = runCatching {
                    stream.sendTouch(
                        action = event.action,
                        x = event.x,
                        y = event.y,
                    )
                }
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    safeCloseTouchStream(stream)
                    if (activeTouchStream === stream) {
                        activeTouchStream = null
                    }
                    resetTouchState()
                    if (handleConnectionLostForClosedError("compatibility live touch", error)) {
                        return@launch
                    }
                    if (isStarted) {
                        LogManager.w(
                            LogTags.SCRCPY_CLIENT,
                            "Compatibility live touch stream was interrupted and will retry: ${error?.message}",
                        )
                        recoverTouchStream(error)
                    }
                    return@launch
                }
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    synchronized(touchLock) {
                        if (activePointerId == event.pointerId) {
                            activePointerId = null
                        }
                    }
                }
            }
        }
    }

    private suspend fun recoverTouchStream(initialError: Throwable?): Boolean {
        var latestError =
            initialError ?: IllegalStateException("Compatibility live touch stream failed without an error")
        if (handleConnectionLostForClosedError("compatibility live touch", latestError)) {
            return false
        }
        repeat(MAX_TOUCH_RECOVERY_ATTEMPTS) { attempt ->
            if (!isStarted) return false
            val retryDelayMs = (TOUCH_RETRY_DELAY_MS * (attempt + 1)).coerceAtMost(MAX_TOUCH_RETRY_DELAY_MS)
            delay(retryDelayMs.milliseconds)
            if (!isStarted) return false

            val result = openTouchHelperStream()
            val stream = result.getOrNull()
            if (stream != null) {
                activeTouchStream = stream
                if (!isStarted) {
                    if (activeTouchStream === stream) {
                        activeTouchStream = null
                    }
                    safeCloseTouchStream(stream)
                    return false
                }
                LogManager.i(
                    LogTags.SCRCPY_CLIENT,
                    "Compatibility live touch stream recovered after attempt ${attempt + 1}",
                )
                return true
            }
            latestError = result.exceptionOrNull()
                ?: IllegalStateException("Compatibility live touch stream retry failed without an error")
            if (handleConnectionLostForClosedError("compatibility live touch", latestError)) {
                return false
            }
        }

        LogManager.w(
            LogTags.SCRCPY_CLIENT,
            "Compatibility live touch is unavailable after $MAX_TOUCH_RECOVERY_ATTEMPTS recovery attempts: " + latestError.message,
        )
        return false
    }

    private fun captureHelperFrame(stream: RemoteScreenshotStream): CapturedFrame? {
        val frame = stream.requestFrame() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(frame.jpegBytes, 0, frame.jpegBytes.size)
            ?: throw IllegalStateException("Unable to decode compatibility helper JPEG")
        if (bitmap.width != frame.imageWidth || bitmap.height != frame.imageHeight) {
            bitmap.recycle()
            throw IllegalStateException(
                "Compatibility helper JPEG dimensions do not match the frame header: " + "header=${frame.imageWidth}x${frame.imageHeight}",
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

    private fun logCaptureSettings(
        context: String,
        settings: CompatibilityCaptureSettings,
    ) {
        LogManager.i(
            LogTags.SCRCPY_CLIENT,
            "Compatibility Capture Settings\n" + "Context: $context\n" + "maxSize=${settings.maxSize}\n" + "jpegQuality=${settings.jpegQuality}",
        )
    }

    private suspend fun executeInput(
        command: String,
        allowShellPasswordFallback: Boolean = true,
    ): Result<Boolean> {
        if (!isStarted) {
            return Result.failure(IllegalStateException("Compatibility mode is not active"))
        }
        return inputMutex.withLock {
            val connection = currentConnection()
                ?: return@withLock Result.failure(IllegalStateException("ADB connection is unavailable"))
            val checkedCommand = $$"{ $$command; }; status=$?; echo \"$$INPUT_EXIT_MARKER$status\""
            connection.executeShell(
                checkedCommand,
                retryOnFailure = false,
                allowShellPasswordFallback = allowShellPasswordFallback,
            ).mapCatching { output ->
                val exitCode = output.lineSequence().map(String::trim).firstOrNull { it.startsWith(INPUT_EXIT_MARKER) }
                    ?.removePrefix(INPUT_EXIT_MARKER)?.toIntOrNull() ?: throw IllegalStateException(
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
        if (handleConnectionLostForClosedError("compatibility live capture", error)) {
            return false
        }
        if (consecutiveFailures >= MAX_CONSECUTIVE_CAPTURE_FAILURES) {
            val message =
                "Compatibility display capture stopped after $consecutiveFailures consecutive failures: " + (error?.message
                    ?: "unknown error")
            LogManager.e(LogTags.SCRCPY_CLIENT, message, error)
            isStarted = false
            onFrame(null)
            onCaptureFailure(message)
            return false
        }
        val retryDelayMs = (CAPTURE_RETRY_DELAY_MS * consecutiveFailures).coerceAtMost(MAX_CAPTURE_RETRY_DELAY_MS)
        LogManager.i(
            LogTags.SCRCPY_CLIENT,
            "Compatibility display capture will retry in ${retryDelayMs}ms " + "after failure $consecutiveFailures/$MAX_CONSECUTIVE_CAPTURE_FAILURES",
        )
        delay(retryDelayMs.milliseconds)
        return true
    }

    private fun handleConnectionLostForClosedError(
        source: String,
        error: Throwable?,
    ): Boolean {
        if (!isStarted) {
            return false
        }
        if (!isRecoverableClosedError(error)) {
            return false
        }
        requestReconnection(source, error)
        return true
    }

    private fun requestReconnection(
        source: String,
        error: Throwable?,
    ) {
        if (!connectionLostHandled.compareAndSet(false, true)) {
            return
        }
        isStarted = false
        captureJob?.cancel()
        touchDispatchJob?.cancel()
        safeCloseScreenshotStream(activeCaptureStream)
        safeCloseTouchStream(activeTouchStream)
        activeCaptureStream = null
        activeTouchStream = null
        resetTouchState()
        onConnectionLost(
            "$source stream encountered recoverable closed state: ${error?.message ?: "closed"}",
        )
    }

    private fun isRecoverableClosedError(error: Throwable?): Boolean {
        var cursor = error
        while (cursor != null) {
            if (cursor is IOException && cursor.message?.contains("closed", ignoreCase = true) == true) {
                return true
            }
            when (cursor) {
                is EOFException,
                is SocketException,
                is SocketTimeoutException -> {
                    return true
                }
            }
            cursor = cursor.cause
        }
        return false
    }

    private fun safeCloseScreenshotStream(stream: RemoteScreenshotStream?) {
        runCatching {
            stream?.close()
        }.onFailure { error ->
            LogManager.w(
                LogTags.SCRCPY_CLIENT,
                "Failed to close compatibility screenshot stream: ${error.message}",
                error,
            )
        }
    }

    private fun safeCloseTouchStream(stream: RemoteTouchStream?) {
        runCatching {
            stream?.close()
        }.onFailure { error ->
            LogManager.w(
                LogTags.SCRCPY_CLIENT,
                "Failed to close compatibility touch stream: ${error.message}",
                error,
            )
        }
    }

    private fun resetTouchState() {
        synchronized(touchLock) {
            activePointerId = null
            liveTouchQueue.clear()
            liveTouchDispatchRunning = false
        }
    }

    private fun clampX(x: Int): Int = remoteWidth.takeIf { it > 0 }?.let { x.coerceIn(0, it - 1) } ?: x.coerceAtLeast(0)

    private fun clampY(y: Int): Int =
        remoteHeight.takeIf { it > 0 }?.let { y.coerceIn(0, it - 1) } ?: y.coerceAtLeast(0)

    private fun String.shellQuoted(): String = "'" + replace("'", "'\\''") + "'"

    private data class TouchSample(
        val x: Int,
        val y: Int,
    )

    private fun liveTouchEvent(
        action: Int,
        pointerId: Long,
        sample: TouchSample,
    ) = CompatibilityLiveTouchEvent(
        action = action,
        pointerId = pointerId,
        x = sample.x,
        y = sample.y,
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
        const val FIRST_FRAME_TIMEOUT_MS = 3_000L
        val ASCII_PRINTABLE_RANGE = 0x20..0x7e
        const val MAX_CONSECUTIVE_CAPTURE_FAILURES = 3
        const val CAPTURE_RETRY_DELAY_MS = 500L
        const val MAX_CAPTURE_RETRY_DELAY_MS = 2_000L
        const val MAX_TOUCH_RECOVERY_ATTEMPTS = 2
        const val TOUCH_RETRY_DELAY_MS = 300L
        const val MAX_TOUCH_RETRY_DELAY_MS = 1_000L
        const val INPUT_EXIT_MARKER = "__SCREEN_REMOTE_INPUT_EXIT__="
        val DEFAULT_CAPTURE_SETTINGS = CompatibilityCaptureSettings(maxSize = 720, jpegQuality = 55)
    }
}
