package com.mobile.scrcpy.android.infrastructure.adb.connection

internal object AdbEncoderOutputParser {
    fun parse(output: String): EncoderDetectionResult =
        EncoderDetectionResult(
            videoEncoders = parseVideoEncoderList(output),
            audioEncoders = parseAudioEncoderList(output),
        )

    private fun parseVideoEncoderList(output: String): List<EncoderInfo.Video> {
        val encoders = mutableListOf<EncoderInfo.Video>()
        val section =
            if (output.contains("List of video encoders:")) {
                val start = output.indexOf("List of video encoders:")
                val end =
                    if (output.contains("List of audio encoders:")) {
                        output.indexOf("List of audio encoders:")
                    } else {
                        output.length
                    }
                output.substring(start, end)
            } else {
                output
            }

        section.lines().forEach { line ->
            val trimmed = line.trim()
            val codecMatch = Regex("--video-codec=([^\\s]+)").find(trimmed)
            val encoderMatch = Regex("--video-encoder='?([^'\\s]+)'?").find(trimmed)

            if (codecMatch != null && encoderMatch != null) {
                val mimeType = codecToMimeType(codecMatch.groupValues[1], isVideo = true)
                if (mimeType != null) {
                    encoders.add(
                        EncoderInfo.Video(
                            name = encoderMatch.groupValues[1].trim('\''),
                            mimeType = mimeType,
                        ),
                    )
                }
            }
        }

        return encoders
    }

    private fun parseAudioEncoderList(output: String): List<EncoderInfo.Audio> {
        val encoders = mutableListOf<EncoderInfo.Audio>()
        val section =
            if (output.contains("List of audio encoders:")) {
                val start = output.indexOf("List of audio encoders:")
                output.substring(start)
            } else {
                return encoders
            }

        section.lines().forEach { line ->
            val trimmed = line.trim()
            val codecMatch = Regex("--audio-codec=([^\\s]+)").find(trimmed)
            val encoderMatch = Regex("--audio-encoder='?([^'\\s]+)'?").find(trimmed)

            if (codecMatch != null && encoderMatch != null) {
                val mimeType = codecToMimeType(codecMatch.groupValues[1], isVideo = false)
                if (mimeType != null) {
                    encoders.add(
                        EncoderInfo.Audio(
                            name = encoderMatch.groupValues[1].trim('\''),
                            mimeType = mimeType,
                        ),
                    )
                }
            }
        }

        return encoders
    }

    private fun codecToMimeType(
        codec: String,
        isVideo: Boolean,
    ): String? =
        if (isVideo) {
            when (codec.lowercase()) {
                "h264" -> "video/avc"
                "h265", "hevc" -> "video/hevc"
                "av1" -> "video/av01"
                "vp8" -> "video/x-vnd.on2.vp8"
                "vp9" -> "video/x-vnd.on2.vp9"
                else -> null
            }
        } else {
            when (codec.lowercase()) {
                "opus" -> "audio/opus"
                "aac" -> "audio/mp4a-latm"
                "flac" -> "audio/flac"
                "raw" -> "audio/raw"
                else -> null
            }
        }
}
