package com.screen.remote.android.app.deeplink

import com.screen.remote.android.core.data.repository.parseBitRate
import com.screen.remote.android.core.domain.model.ScrcpyConfig
import com.screen.remote.android.core.domain.model.ScrcpyTunnelMode

internal val SCRCPY_URL_PARAMETER_NAMES =
    setOf(
        "maxSize",
        "videoBitRate",
        "maxFps",
        "videoEncoder",
        "videoDecoder",
        "audio",
        "enableAudio",
        "audioBitRate",
        "audioEncoder",
        "audioDecoder",
        "gameMode",
        "compatibilityMode",
        "fullScreen",
        "useFullScreen",
        "floatingBall",
        "showFloatingBall",
        "hardwareDecoding",
        "enableHardwareDecoding",
        "followOrientation",
        "followRemoteOrientation",
        "clipboard",
        "clipboardSync",
        "turnScreenOff",
        "powerOffOnClose",
        "cleanupOnDisconnect",
        "stayAwake",
        "keepDeviceAwake",
        "showTouches",
        "ignoreVideoEncoderConstraints",
        "displayId",
        "newDisplayEnabled",
        "newDisplay",
        "virtualDisplaySystemDecorations",
        "preserveVirtualDisplayContent",
        "startApp",
        "codecOptions",
        "tunnelMode",
    )

internal fun ScrcpyConfig.withUrlParameters(parameters: Map<String, String>): Result<ScrcpyConfig> =
    runCatching {
        normalizeScrcpyUrlParameters(parameters)
            .getOrThrow()
            .entries
            .fold(this) { config, (key, value) ->
                when (key) {
                    "maxSize" -> config.copy(maxSize = value.requireNonNegativeInt(key))
                    "videoBitRate" -> config.copy(videoBitRate = value.requireBitRate(key))
                    "maxFps" -> config.copy(maxFps = value.requirePositiveInt(key))
                    "videoEncoder" -> config.copy(userVideoEncoder = value)
                    "videoDecoder" -> config.copy(userVideoDecoder = value)
                    "audio", "enableAudio" -> config.copy(enableAudio = value.requireBoolean(key))
                    "audioBitRate" -> config.copy(audioBitRate = value.requireBitRate(key))
                    "audioEncoder" -> config.copy(userAudioEncoder = value)
                    "audioDecoder" -> config.copy(userAudioDecoder = value)
                    "gameMode" -> config.copy(gameMode = value.requireBoolean(key))
                    "compatibilityMode" -> config.copy(compatibilityMode = value.requireBoolean(key))
                    "fullScreen", "useFullScreen" -> config.copy(useFullScreen = value.requireBoolean(key))
                    "floatingBall", "showFloatingBall" -> config.copy(showFloatingBall = value.requireBoolean(key))
                    "hardwareDecoding", "enableHardwareDecoding" ->
                        config.copy(enableHardwareDecoding = value.requireBoolean(key))
                    "followOrientation", "followRemoteOrientation" ->
                        config.copy(followRemoteOrientation = value.requireBoolean(key))
                    "clipboard", "clipboardSync" -> config.copy(clipboardSync = value.requireBoolean(key))
                    "turnScreenOff" -> config.copy(turnScreenOff = value.requireBoolean(key))
                    "powerOffOnClose" -> config.copy(powerOffOnClose = value.requireBoolean(key))
                    "cleanupOnDisconnect" -> config.copy(cleanupOnDisconnect = value.requireBoolean(key))
                    "stayAwake" -> config.copy(stayAwake = value.requireBoolean(key))
                    "keepDeviceAwake" -> config.copy(keepDeviceAwake = value.requireBoolean(key))
                    "showTouches" -> config.copy(showTouches = value.requireBoolean(key))
                    "ignoreVideoEncoderConstraints" ->
                        config.copy(ignoreVideoEncoderConstraints = value.requireBoolean(key))
                    "displayId" -> config.copy(displayId = value.requireNonNegativeInt(key))
                    "newDisplayEnabled" -> config.copy(newDisplayEnabled = value.requireBoolean(key))
                    "newDisplay" -> config.copy(newDisplayEnabled = true, newDisplay = value)
                    "virtualDisplaySystemDecorations" ->
                        config.copy(virtualDisplaySystemDecorations = value.requireBoolean(key))
                    "preserveVirtualDisplayContent" ->
                        config.copy(preserveVirtualDisplayContent = value.requireBoolean(key))
                    "startApp" -> config.copy(startApp = value)
                    "codecOptions" -> config.copy(codecOptions = value)
                    "tunnelMode" ->
                        config.copy(
                            tunnelMode =
                                when (value) {
                                    "direct", "direct_adb" -> ScrcpyTunnelMode.DIRECT_ADB
                                    "forward", "adb_forward" -> ScrcpyTunnelMode.ADB_FORWARD
                                    else -> error("Invalid tunnelMode: $value")
                                },
                        )
                    else -> error("Unsupported scrcpy URL parameter: $key")
                }
            }
            .let { config ->
                if (config.compatibilityMode) {
                    config.copy(
                        enableAudio = false,
                        clipboardSync = false,
                    )
                } else {
                    config
                }
            }
    }

internal fun normalizeScrcpyUrlParameters(parameters: Map<String, String>): Result<Map<String, String>> =
    runCatching {
        buildMap {
            parameters.forEach { (key, value) ->
                val canonicalKey =
                    SCRCPY_URL_PARAMETER_NAMES.firstOrNull { it.equals(key, ignoreCase = true) }
                        ?: error("Unsupported scrcpy URL parameter: $key")
                require(canonicalKey !in this) {
                    "Duplicate scrcpy URL parameter: $canonicalKey"
                }
                put(canonicalKey, value)
            }
        }
    }

internal fun String.requireBoolean(key: String): Boolean =
    when (this) {
        "on", "true", "1", "yes" -> true
        "off", "false", "0", "no" -> false
        else -> error("Invalid boolean value for $key: $this")
    }

private fun String.requireBitRate(key: String): Int =
    parseBitRate(this)?.takeIf { it > 0 } ?: error("Invalid bitrate for $key: $this")

private fun String.requirePositiveInt(key: String): Int =
    toIntOrNull()?.takeIf { it > 0 } ?: error("Invalid positive integer for $key: $this")

private fun String.requireNonNegativeInt(key: String): Int =
    toIntOrNull()?.takeIf { it >= 0 } ?: error("Invalid non-negative integer for $key: $this")
