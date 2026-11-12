package com.v2rayvpn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.v2rayvpn.app.ui.AppViewModel
import com.v2rayvpn.app.ui.components.ConfirmDialog
import com.v2rayvpn.app.ui.components.GlassCard
import com.v2rayvpn.app.ui.components.InfoBanner
import com.v2rayvpn.app.ui.components.OptionChip
import com.v2rayvpn.app.ui.components.OutlineButton
import com.v2rayvpn.app.ui.components.ScreenBackground
import com.v2rayvpn.app.ui.components.ScreenHeader
import com.v2rayvpn.app.ui.components.SectionTitle
import com.v2rayvpn.app.ui.components.SettingRow
import com.v2rayvpn.app.ui.components.ToggleRow
import com.v2rayvpn.app.ui.theme.VpnColors

@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            ScreenHeader(title = "Settings", subtitle = "Everything is stored on this device", onBack = onBack)
            Spacer(Modifier.height(14.dp))

            SectionTitle("Network")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SettingRow(title = "Routing", description = "Global or bypass local network", value = settings.routingMode.name, onClick = { onNavigate("routing") })
                SettingRow(title = "DNS", description = "Resolver used while connected", value = settings.dnsMode.name, onClick = { onNavigate("dns") })
                SettingRow(title = "Split tunnelling", description = "Per-app VPN rules", value = settings.perAppMode.name, onClick = { onNavigate("split-tunnel") })
                ToggleRow(
                    title = "Reconnect when the network changes",
                    description = "Re-establishes the tunnel after a Wi-Fi or mobile data switch",
                    checked = settings.reconnectOnNetworkChange
                ) { value -> viewModel.updateSettings { it.copy(reconnectOnNetworkChange = value) } }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Subscriptions")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "Automatic refresh",
                    description = "Checks subscriptions in the background while the app is open",
                    value = if (settings.subscriptionAutoRefreshHours == 0) "Off" else "${settings.subscriptionAutoRefreshHours}h",
                    onClick = {
                        val next = when (settings.subscriptionAutoRefreshHours) {
                            0 -> 6
                            6 -> 12
                            12 -> 24
                            else -> 0
                        }
                        viewModel.updateSettings { it.copy(subscriptionAutoRefreshHours = next) }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Diagnostics")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("warning", "info", "debug").forEach { level ->
                        OptionChip(level, settings.logLevel == level) {
                            viewModel.updateSettings { it.copy(logLevel = level) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Engine log level. Debug is verbose and is not recommended for daily use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VpnColors.TextSecondary
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Privacy")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                ToggleRow(
                    title = "Public IP lookup",
                    description = "Off by default. When on, the app asks api.ipify.org what address it sees, once per connection.",
                    checked = settings.publicIpLookupEnabled
                ) { value -> viewModel.updateSettings { it.copy(publicIpLookupEnabled = value) } }
                ToggleRow(
                    title = "Reduce motion",
                    description = "Turns off the pulsing connect animation",
                    checked = settings.reducedMotion
                ) { value -> viewModel.updateSettings { it.copy(reducedMotion = value) } }
            }
            if (settings.publicIpLookupEnabled) {
                Spacer(Modifier.height(10.dp))
                InfoBanner(
                    text = "Public IP lookup contacts api.ipify.org over HTTPS. It reveals your egress address to that service; " +
                        "turn it off to keep the app fully local apart from subscription downloads.",
                    tone = VpnColors.Amber
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Data")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "Stored configurations",
                    description = "Encrypted with an Android Keystore key",
                    value = "${profiles.size}"
                )
                SettingRow(
                    title = "Export configurations",
                    description = "Share a JSON file of share links. It contains credentials — treat it as a secret.",
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "V2RAY VPN profiles")
                            putExtra(Intent.EXTRA_TEXT, viewModel.exportProfilesJson())
                        }
                        context.startActivity(Intent.createChooser(intent, "Export configurations"))
                    }
                )
                OutlineButton(
                    text = "Delete all local data",
                    modifier = Modifier.fillMaxWidth(),
                    tint = VpnColors.Red
                ) { confirmDelete = true }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("About")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SettingRow(title = "About, privacy and licenses", onClick = { onNavigate("about") })
                SettingRow(title = "Diagnostic logs", onClick = { onNavigate("logs") })
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete all local data?",
            body = "Profiles, subscriptions and settings will be permanently removed from this device. Exported files are not affected.",
            confirmLabel = "Delete everything",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = { viewModel.clearAllData() }
        )
    }
}
