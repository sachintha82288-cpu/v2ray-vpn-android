package com.v2rayvpn.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2rayvpn.app.core.util.Formatters
import com.v2rayvpn.app.domain.model.ConnectionState
import com.v2rayvpn.app.ui.AppViewModel
import com.v2rayvpn.app.ui.components.GlassCard
import com.v2rayvpn.app.ui.components.InfoBanner
import com.v2rayvpn.app.ui.components.OutlineButton
import com.v2rayvpn.app.ui.components.Pill
import com.v2rayvpn.app.ui.components.ScreenBackground
import com.v2rayvpn.app.ui.components.StatusDot
import com.v2rayvpn.app.ui.theme.VpnColors
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onNavigate: (String) -> Unit
) {
    val state by viewModel.connectionState.collectAsState()
    val traffic by viewModel.traffic.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val publicIp by viewModel.publicIp.collectAsState()
    val activeId by viewModel.activeProfileId.collectAsState()

    val selectedProfile = remember(profiles, settings.selectedProfileId, activeId) {
        profiles.firstOrNull { it.id == (activeId ?: settings.selectedProfileId) }
            ?: profiles.firstOrNull()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.connect(selectedProfile?.id)
        } else {
            viewModel.postMessage("VPN permission was not granted")
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "V2RAY VPN",
                        style = MaterialTheme.typography.titleLarge,
                        color = VpnColors.TextPrimary
                    )
                    Text(
                        text = "Free · no ads · no accounts",
                        style = MaterialTheme.typography.bodySmall,
                        color = VpnColors.TextTertiary
                    )
                }
                IconButton(onClick = { onNavigate("settings") }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = VpnColors.TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            val currentState = state
            ConnectPanel(
                state = currentState,
                durationMillis = if (currentState is ConnectionState.Connected) now - currentState.since else 0,
                reducedMotion = settings.reducedMotion,
                onClick = {
                    when (currentState) {
                        is ConnectionState.Connected, is ConnectionState.Connecting, is ConnectionState.Preparing ->
                            viewModel.disconnect()
                        else -> {
                            if (selectedProfile == null) {
                                viewModel.postMessage("Import a configuration first")
                                onNavigate("import")
                            } else {
                                val intent = viewModel.prepareVpnIntent()
                                if (intent != null) permissionLauncher.launch(intent)
                                else viewModel.connect(selectedProfile.id)
                            }
                        }
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            when (val s = state) {
                is ConnectionState.Error -> InfoBanner(
                    text = buildString {
                        append(s.message)
                        s.detail?.let { append(" — ").append(it) }
                    },
                    tone = VpnColors.Red
                )

                is ConnectionState.Connected -> if (!s.healthCheckOk) {
                    InfoBanner(text = s.healthCheckMessage ?: "Health check did not pass")
                }

                else -> Unit
            }

            Spacer(Modifier.height(16.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SESSION TRAFFIC",
                    style = MaterialTheme.typography.labelSmall,
                    color = VpnColors.TextTertiary
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TrafficStat("Download", Formatters.rate(traffic.downloadRate), Formatters.bytes(traffic.downloadBytes), Modifier.weight(1f))
                    TrafficStat("Upload", Formatters.rate(traffic.uploadRate), Formatters.bytes(traffic.uploadBytes), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (state is ConnectionState.Connected) {
                        "Counters come from the Xray engine (proxy outbound). They reset when the tunnel stops."
                    } else {
                        "No active session. Counters start when a tunnel is running."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = VpnColors.TextTertiary
                )
            }

            Spacer(Modifier.height(12.dp))

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("servers") }
            ) {
                Text(
                    text = "SERVER",
                    style = MaterialTheme.typography.labelSmall,
                    color = VpnColors.TextTertiary
                )
                Spacer(Modifier.height(8.dp))
                if (selectedProfile == null) {
                    Text(
                        text = "No configuration imported yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = VpnColors.TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tap to import a share link, JSON configuration, QR code or subscription.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VpnColors.TextSecondary
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = selectedProfile.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = VpnColors.TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = "${selectedProfile.protocol.label} · ${selectedProfile.endpoint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = VpnColors.TextSecondary
                            )
                        }
                        Pill(
                            text = Formatters.latency(selectedProfile.lastLatencyMs),
                            color = VpnColors.Cyan
                        )
                    }
                }
            }

            if (settings.publicIpLookupEnabled) {
                Spacer(Modifier.height(12.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "PUBLIC IP (via api.ipify.org)",
                                style = MaterialTheme.typography.labelSmall,
                                color = VpnColors.TextTertiary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = publicIp ?: "Not available yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = VpnColors.TextPrimary
                            )
                        }
                        IconButton(onClick = { viewModel.refreshPublicIp() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh public IP",
                                tint = VpnColors.TextSecondary
                            )
                        }
                    }
                    Text(
                        text = "This asks an external service what address it sees. It is off by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VpnColors.TextTertiary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(text = "Import", modifier = Modifier.weight(1f)) { onNavigate("import") }
                OutlineButton(text = "Subscriptions", modifier = Modifier.weight(1f)) { onNavigate("subscriptions") }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(text = "Statistics", modifier = Modifier.weight(1f)) { onNavigate("statistics") }
                OutlineButton(text = "Logs", modifier = Modifier.weight(1f)) { onNavigate("logs") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TrafficStat(label: String, rate: String, total: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = VpnColors.TextTertiary
        )
        Text(
            text = rate,
            style = MaterialTheme.typography.titleLarge,
            color = VpnColors.TextPrimary
        )
        Text(
            text = total,
            style = MaterialTheme.typography.bodySmall,
            color = VpnColors.TextSecondary
        )
    }
}

@Composable
private fun ConnectPanel(
    state: ConnectionState,
    durationMillis: Long,
    reducedMotion: Boolean,
    onClick: () -> Unit
) {
    val accent = when (state) {
        is ConnectionState.Connected -> VpnColors.Green
        is ConnectionState.Connecting, is ConnectionState.Preparing -> VpnColors.Amber
        is ConnectionState.Error -> VpnColors.Red
        else -> VpnColors.Purple
    }

    val pulse = if (reducedMotion) {
        0.35f
    } else {
        val transition = rememberInfiniteTransition(label = "pulse")
        val value by transition.animateFloat(
            initialValue = 0.18f,
            targetValue = 0.42f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        value
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = pulse), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(196.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.28f),
                                Color(0x14FFFFFF),
                                Color(0x08FFFFFF)
                            )
                        )
                    )
                    .border(1.5.dp, accent.copy(alpha = 0.55f), CircleShape)
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics {
                        contentDescription = when (state) {
                            is ConnectionState.Connected -> "Disconnect VPN"
                            is ConnectionState.Connecting, is ConnectionState.Preparing -> "Cancel connection"
                            else -> "Connect VPN"
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when (state) {
                            is ConnectionState.Connected -> "DISCONNECT"
                            is ConnectionState.Connecting -> "CANCEL"
                            is ConnectionState.Preparing -> "CANCEL"
                            is ConnectionState.Disconnecting -> "STOPPING"
                            else -> "CONNECT"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = VpnColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(accent)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = state.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = VpnColors.TextSecondary
                        )
                    }
                    if (state is ConnectionState.Connected) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = Formatters.duration(durationMillis),
                            style = MaterialTheme.typography.bodyMedium,
                            color = VpnColors.TextPrimary
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = when (val s = state) {
                    is ConnectionState.Connected -> s.profileName
                    is ConnectionState.Connecting -> s.message
                    is ConnectionState.Preparing -> s.message
                    is ConnectionState.Error -> s.message
                    else -> "Tap to connect with the selected server"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = VpnColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
