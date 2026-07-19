package com.screen.remote.android.feature.remote.widget.video

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.feature.remote.presentation.VideoDecoderManager
import com.screen.remote.android.infrastructure.media.video.VideoPerformanceSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

internal data class RemotePerformanceSample(
    val decodedFps: Double = 0.0,
    val renderedFps: Double = 0.0,
    val videoBitsPerSecond: Double = 0.0,
    val networkTxBitsPerSecond: Double? = null,
    val networkRxBitsPerSecond: Double? = null,
)

@Composable
internal fun RemotePerformanceStatsOverlay(
    videoDecoderManager: VideoDecoderManager,
    isNetworkSession: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    var sample by remember(videoDecoderManager) { mutableStateOf(RemotePerformanceSample()) }
    var wifiLinkRates by remember { mutableStateOf(WifiLinkRates()) }

    LaunchedEffect(videoDecoderManager, isNetworkSession) {
        var previousVideo = videoDecoderManager.performanceSnapshot()
        var previousTxBytes = readTrafficBytes(tx = true)
        var previousRxBytes = readTrafficBytes(tx = false)
        var previousTimeNanos = SystemClock.elapsedRealtimeNanos()

        while (isActive) {
            delay(STATS_SAMPLE_INTERVAL_MS)
            val nowNanos = SystemClock.elapsedRealtimeNanos()
            val elapsedSeconds = (nowNanos - previousTimeNanos) / 1_000_000_000.0
            val currentVideo = videoDecoderManager.performanceSnapshot()
            val currentTxBytes = readTrafficBytes(tx = true)
            val currentRxBytes = readTrafficBytes(tx = false)

            sample =
                calculateRemotePerformanceSample(
                    previousVideo = previousVideo,
                    currentVideo = currentVideo,
                    previousTxBytes = previousTxBytes,
                    currentTxBytes = currentTxBytes,
                    previousRxBytes = previousRxBytes,
                    currentRxBytes = currentRxBytes,
                    elapsedSeconds = elapsedSeconds,
                )
            wifiLinkRates = if (isNetworkSession) readWifiLinkRates(context) else WifiLinkRates()

            previousVideo = currentVideo
            previousTxBytes = currentTxBytes
            previousRxBytes = currentRxBytes
            previousTimeNanos = nowNanos
        }
    }

    Column(
        modifier =
            modifier
                .background(Color(0xB8000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        StatsText(
            "${RemoteTexts.REMOTE_STATS_RENDER.get()} ${formatRate(sample.renderedFps)} FPS  ·  " +
                "${RemoteTexts.REMOTE_STATS_DECODE.get()} ${formatRate(sample.decodedFps)} FPS",
        )
        StatsText("${RemoteTexts.REMOTE_STATS_VIDEO.get()} ${formatBitRate(sample.videoBitsPerSecond)}")
        StatsText(
            "${RemoteTexts.REMOTE_STATS_NETWORK_ACTUAL.get()} " +
                "TX ${formatBitRate(sample.networkTxBitsPerSecond)}  ·  " +
                "RX ${formatBitRate(sample.networkRxBitsPerSecond)}",
        )
        if (isNetworkSession) {
            StatsText(
                "${RemoteTexts.REMOTE_STATS_WIFI_LINK.get()} " +
                    "TX ${formatLinkRate(wifiLinkRates.txMbps)}  ·  " +
                    "RX ${formatLinkRate(wifiLinkRates.rxMbps)}",
            )
        }
    }
}

@Composable
private fun StatsText(text: String) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
}

internal fun calculateRemotePerformanceSample(
    previousVideo: VideoPerformanceSnapshot,
    currentVideo: VideoPerformanceSnapshot,
    previousTxBytes: Long?,
    currentTxBytes: Long?,
    previousRxBytes: Long?,
    currentRxBytes: Long?,
    elapsedSeconds: Double,
): RemotePerformanceSample {
    if (elapsedSeconds <= 0.0) return RemotePerformanceSample()

    return RemotePerformanceSample(
        decodedFps = nonNegativeDelta(currentVideo.decodedFrames, previousVideo.decodedFrames) / elapsedSeconds,
        renderedFps = nonNegativeDelta(currentVideo.renderedFrames, previousVideo.renderedFrames) / elapsedSeconds,
        videoBitsPerSecond =
            nonNegativeDelta(currentVideo.receivedBytes, previousVideo.receivedBytes) * BITS_PER_BYTE / elapsedSeconds,
        networkTxBitsPerSecond = byteRate(previousTxBytes, currentTxBytes, elapsedSeconds),
        networkRxBitsPerSecond = byteRate(previousRxBytes, currentRxBytes, elapsedSeconds),
    )
}

private fun readTrafficBytes(tx: Boolean): Long? {
    val value =
        if (tx) {
            TrafficStats.getUidTxBytes(Process.myUid())
        } else {
            TrafficStats.getUidRxBytes(Process.myUid())
        }
    return value.takeIf { it != TrafficStats.UNSUPPORTED.toLong() && it >= 0L }
}

private data class WifiLinkRates(
    val txMbps: Int? = null,
    val rxMbps: Int? = null,
)

@SuppressLint("MissingPermission", "Deprecated")
@Suppress("DEPRECATION")
private fun readWifiLinkRates(context: Context): WifiLinkRates {
    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return WifiLinkRates()
    val wifiInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork
            val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
                return WifiLinkRates()
            }
            capabilities.transportInfo as? WifiInfo ?: wifiManager.connectionInfo
        } else {
            wifiManager.connectionInfo
        }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        WifiLinkRates(
            txMbps = wifiInfo.txLinkSpeedMbps.validLinkSpeed(),
            rxMbps = wifiInfo.rxLinkSpeedMbps.validLinkSpeed(),
        )
    } else {
        WifiLinkRates(txMbps = wifiInfo.linkSpeed.validLinkSpeed())
    }
}

private fun Int.validLinkSpeed(): Int? = takeIf { it > 0 && it != WifiInfo.LINK_SPEED_UNKNOWN }

private fun byteRate(
    previousBytes: Long?,
    currentBytes: Long?,
    elapsedSeconds: Double,
): Double? {
    if (previousBytes == null || currentBytes == null) return null
    return nonNegativeDelta(currentBytes, previousBytes) * BITS_PER_BYTE / elapsedSeconds
}

private fun nonNegativeDelta(
    current: Long,
    previous: Long,
): Double = (current - previous).coerceAtLeast(0L).toDouble()

private fun formatRate(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun formatLinkRate(mbps: Int?): String = mbps?.let { "$it Mb/s" } ?: "--"

private fun formatBitRate(bitsPerSecond: Double?): String {
    if (bitsPerSecond == null) return "--"
    return when {
        bitsPerSecond >= 1_000_000.0 -> String.format(Locale.US, "%.2f Mb/s", bitsPerSecond / 1_000_000.0)
        bitsPerSecond >= 1_000.0 -> String.format(Locale.US, "%.1f Kb/s", bitsPerSecond / 1_000.0)
        else -> String.format(Locale.US, "%.0f b/s", bitsPerSecond)
    }
}

private const val STATS_SAMPLE_INTERVAL_MS = 1_000L
private const val BITS_PER_BYTE = 8.0
