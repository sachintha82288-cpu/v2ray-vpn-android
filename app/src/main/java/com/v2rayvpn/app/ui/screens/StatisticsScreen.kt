package com.v2rayvpn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.v2rayvpn.app.core.util.Formatters
import com.v2rayvpn.app.domain.model.ConnectionState
import com.v2rayvpn.app.ui.AppViewModel
import com.v2rayvpn.app.ui.components.GlassCard
import com.v2rayvpn.app.ui.components.InfoBanner
import com.v2rayvpn.app.ui.components.ScreenBackground
import com.v2rayvpn.app.ui.components.ScreenHeader
import com.v2rayvpn.app.ui.components.SectionTitle
import com.v2rayvpn.app.ui.theme.VpnColors

@Composable
fun StatisticsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val traffic by viewModel.traffic.collectAsState()
    val state by viewModel.connectionState.collectAsState()
    val profiles by viewModel.profiles.collectAsState()

    val tested = profiles.filter { it.lastTestAt != null }.sortedBy { it.lastLatencyMs ?: Long.MAX_VALUE }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            ScreenHeader(
                title = "Statistics",
                subtitle = "Measured values only — nothing here is estimated",
                onBack = onBack
            )
            Spacer(Modifier.height(14.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "CURRENT SESSION",
                            style = MaterialTheme.typography.labelSmall,
                            color = VpnColors.TextTertiary
                        )
                        Spacer(Modifier.height(10.dp))
                        val connected = state as? ConnectionState.Connected
                        if (connected != null) {
                            StatLine("Duration", Formatters.duration(System.currentTimeMillis() - connected.since))
                            StatLine("Downloaded", Formatters.bytes(traffic.downloadBytes))
                            StatLine("Uploaded", Formatters.bytes(traffic.uploadBytes))
                            StatLine("Download rate", Formatters.rate(traffic.downloadRate))
                            StatLine("Upload rate", Formatters.rate(traffic.uploadRate))
                            StatLine("Server", connected.profileName)
                        } else {
                            Text(
                                text = "No active session.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VpnColors.TextSecondary
                            )
                        }
                    }
                }

                item {
                    InfoBanner(
                        text = "Counters are read from the Xray engine's outbound statistics once per second. " +
                            "Throughput is not benchmarked: this app does not run speed tests, and no number here is simulated.",
                        tone = VpnColors.Blue
                    )
                }

                item { SectionTitle("Latency measurements") }

                if (tested.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No server has been tested yet. Use \"Test all\" on the Servers screen.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VpnColors.TextSecondary
                            )
                        }
                    }
                } else {
                    items(tested, key = { it.id }) { profile ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = VpnColors.TextPrimary
                                    )
                                    Text(
                                        text = "${profile.lastTestType ?: "test"} · ${Formatters.time(profile.lastTestAt ?: 0)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VpnColors.TextSecondary
                                    )
                                }
                                Text(
                                    text = Formatters.latency(profile.lastLatencyMs),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = VpnColors.Green
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = VpnColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = VpnColors.TextPrimary
        )
    }
}
