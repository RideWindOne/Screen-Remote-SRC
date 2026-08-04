package com.screen.remote.android.feature.session.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.feature.session.viewmodel.ConnectionLatencyEndpointState
import com.screen.remote.android.feature.session.viewmodel.ConnectionLatencyTestViewModel
import com.screen.remote.android.feature.session.viewmodel.allTestsCompleted
import com.screen.remote.android.feature.session.viewmodel.copyText
import com.screen.remote.android.feature.session.viewmodel.median
import com.screen.remote.android.feature.session.viewmodel.oneDecimal

@Composable
fun ConnectionLatencyTestPage(
    sessionData: SessionData,
    onBack: () -> Unit,
    viewModel: ConnectionLatencyTestViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(sessionData) {
        viewModel.start(sessionData)
    }
    DisposableEffect(viewModel) {
        onDispose(viewModel::stop)
    }

    DialogPage(
        title = SessionTexts.LATENCY_TEST_ENTRY.get(),
        onDismiss = onBack,
        leftButtonText = "返回",
        trailingContent = {
            TextButton(
                onClick = {
                    if (state.running) viewModel.stop() else viewModel.start(sessionData)
                },
            ) {
                Text(if (state.running) "停止" else "重新测试")
            }
        },
        enableScroll = true,
        verticalSpacing = 10.dp,
    ) {
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.endpoints.values.forEach { endpoint ->
            ConnectionLatencyEndpointCard(endpoint)
        }

        if (state.allTestsCompleted()) {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "Screen Remote 连接延迟测试",
                            state.copyText(sessionData),
                        ),
                    )
                    Toast.makeText(
                        context,
                        SessionTexts.LATENCY_TEST_COPIED.get(),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(SessionTexts.LATENCY_TEST_COPY_ALL.get())
            }
        }
    }
}

@Composable
private fun ConnectionLatencyEndpointCard(endpoint: ConnectionLatencyEndpointState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = endpoint.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            LinearProgressIndicator(
                progress = { endpoint.completedRounds / 10f },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("进度 ${endpoint.completedRounds}/10")
                Text("成功 ${endpoint.connectSamples.size}｜失败 ${endpoint.failures}")
            }

            endpoint.activeRound?.let { round ->
                Text(
                    text = "正在测试第 $round 轮…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (endpoint.connectSamples.isNotEmpty()) {
                Text(
                    text =
                        "连接：中位 ${median(endpoint.connectSamples).oneDecimal()} ms，" +
                            "平均 ${endpoint.connectSamples.average().oneDecimal()} ms，" +
                            "范围 ${
                                endpoint.connectSamples.minOrNull()!!.oneDecimal()
                            }–${endpoint.connectSamples.maxOrNull()!!.oneDecimal()} ms",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (endpoint.shellSamples.isNotEmpty()) {
                Text(
                    text =
                        "连接后 RTT：中位 ${median(endpoint.shellSamples).oneDecimal()} ms，" +
                            "平均 ${endpoint.shellSamples.average().oneDecimal()} ms，" +
                            "范围 ${
                                endpoint.shellSamples.minOrNull()!!.oneDecimal()
                            }–${endpoint.shellSamples.maxOrNull()!!.oneDecimal()} ms，" +
                            "样本 ${endpoint.shellSamples.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (endpoint.resolvedEndpoint.isNotBlank()) {
                Text(
                    text = "实际地址：${endpoint.resolvedEndpoint}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                endpoint.roundLogs.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color =
                            if ("失败" in line) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}
