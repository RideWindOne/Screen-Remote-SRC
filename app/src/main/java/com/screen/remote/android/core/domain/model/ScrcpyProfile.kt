package com.screen.remote.android.core.domain.model

import com.screen.remote.android.core.common.ScrcpyConstants

data class ScrcpyProfile(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val maxSize: Int = 1920,
    val videoBitRate: Int = ScrcpyConstants.DEFAULT_VIDEO_BITRATE_INT,
    val maxFps: Int = 60,
    val displayId: Int = 0,
    val newDisplayEnabled: Boolean = false,
    val newDisplay: String = "",
    val showTouches: Boolean = false,
    val enableClipboardSync: Boolean = true,
    val stayAwake: Boolean = false,
    val codecOptions: String = ScrcpyConstants.DEFAULT_CODEC_OPTIONS,
    val powerOffOnClose: Boolean = false,
    val cleanupOnDisconnect: Boolean = true,
    val ignoreVideoEncoderConstraints: Boolean = false,
    val enableAudio: Boolean = false,
    val audioBitRate: Int = 128000,
    val turnScreenOff: Boolean = true,
    val keepDeviceAwake: Boolean = false,
    val enableHardwareDecoding: Boolean = true,
    val followRemoteOrientation: Boolean = false,
    val preferredVideoCodec: String = ScrcpyConstants.DEFAULT_VIDEO_CODEC,
    val preferredAudioCodec: String = ScrcpyConstants.DEFAULT_AUDIO_CODEC,
    val userVideoEncoder: String = "",
    val userAudioEncoder: String = "",
    val userVideoDecoder: String = "",
    val userAudioDecoder: String = "",
) {
    companion object {
        const val DEFAULT_ID = "default"

        fun default(): ScrcpyProfile =
            ScrcpyProfile(
                id = DEFAULT_ID,
                name = "Default",
            )
    }
}

fun ScrcpyOptions.withProfile(profile: ScrcpyProfile): ScrcpyOptions =
    copy(
        maxSize = profile.maxSize,
        videoBitRate = profile.videoBitRate,
        maxFps = profile.maxFps,
        displayId = profile.displayId,
        newDisplayEnabled = profile.newDisplayEnabled,
        newDisplay = profile.newDisplay,
        showTouches = profile.showTouches,
        enableClipboardSync = profile.enableClipboardSync,
        stayAwake = profile.stayAwake,
        codecOptions = profile.codecOptions,
        powerOffOnClose = profile.powerOffOnClose,
        cleanupOnDisconnect = profile.cleanupOnDisconnect,
        ignoreVideoEncoderConstraints = profile.ignoreVideoEncoderConstraints,
        enableAudio = profile.enableAudio,
        audioBitRate = profile.audioBitRate,
        turnScreenOff = profile.turnScreenOff,
        keepDeviceAwake = profile.keepDeviceAwake,
        enableHardwareDecoding = profile.enableHardwareDecoding,
        followRemoteOrientation = profile.followRemoteOrientation,
        preferredVideoCodec = profile.preferredVideoCodec,
        preferredAudioCodec = profile.preferredAudioCodec,
        userVideoEncoder = profile.userVideoEncoder,
        userAudioEncoder = profile.userAudioEncoder,
        userVideoDecoder = profile.userVideoDecoder,
        userAudioDecoder = profile.userAudioDecoder,
    )
