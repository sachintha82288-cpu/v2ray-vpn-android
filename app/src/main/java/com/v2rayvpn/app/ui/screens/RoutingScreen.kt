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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.v2rayvpn.app.domain.model.RoutingMode
import com.v2rayvpn.app.ui.AppViewModel
import com.v2rayvpn.app.ui.components.GlassCard
import com.v2rayvpn.app.ui.components.InfoBanner
import com.v2rayvpn.app.ui.components.OptionChip
import com.v2rayvpn.app.ui.components.OutlineButton
import com.v2rayvpn.app.ui.components.ScreenBackground
import com.v2rayvpn.app.ui.components.ScreenHeader
import com.v2rayvpn.app.ui.components.SectionTitle
import com.v2rayvpn.app.ui.components.ToggleRow
import com.v2rayvpn.app.ui.theme.VpnColors

@Composable
fun RoutingScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            ScreenHeader(title = "Routing", subtitle = "What goes through the tunnel", onBack = onBack)
            Spacer(Modifier.height(14.dp))

            SectionTitle("Mode")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionChip("Global", settings.routingMode == RoutingMode.GLOBAL) {
                    viewModel.updateSettings { it.copy(routingMode = RoutingMode.GLOBAL) }
                }
                OptionChip("Bypass LAN", settings.routingMode == RoutingMode.BYPASS_LAN) {
                    viewModel.updateSettings { it.copy(routingMode = RoutingMode.BYPASS_LAN) }
                }
            }
            Spacer(Modifier.height(10.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = when (settings.routingMode) {
                        RoutingMode.GLOBAL ->
                            "0.0.0.0/0 is routed into the tunnel, so every destination including your local network goes through the proxy."
                        RoutingMode.BYPASS_LAN ->
                            "Private ranges (10/8, 172.16/12, 192.168/16, 127/8, 169.254/16, multicast) are excluded at the " +
                                "Android route level, so printers, NAS boxes and router pages are reached directly and never enter the tunnel."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = VpnColors.TextSecondary
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Options")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                ToggleRow(
                    title = "Block ads and trackers",
                    description = "Routes geosite:category-ads-all to a blackhole outbound",
                    checked = settings.blockAdsAndTrackers
                ) { value -> viewModel.updateSettings { it.copy(blockAdsAndTrackers = value) } }
                ToggleRow(
                    title = "IPv6 through the tunnel",
                    description = if (settings.ipv6Enabled) {
                        "::/0 is routed into the tunnel"
                    } else {
                        "Off: IPv6 traffic from apps is not captured by the tunnel on IPv6-capable networks"
                    },
                    checked = settings.ipv6Enabled
                ) { value -> viewModel.updateSettings { it.copy(ipv6Enabled = value) } }
            }

            if (!settings.ipv6Enabled) {
                Spacer(Modifier.height(10.dp))
                InfoBanner(
                    text = "With IPv6 disabled the VPN has no IPv6 route, so apps that use IPv6 will bypass the tunnel. " +
                        "Enable it unless your server cannot carry IPv6."
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Interface")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "MTU",
                            style = MaterialTheme.typography.titleMedium,
                            color = VpnColors.TextPrimary
                        )
                        Text(
                            text = "${settings.mtu} bytes",
                            style = MaterialTheme.typography.bodySmall,
                            color = VpnColors.TextSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OptionChip("1280", settings.mtu == 1280) {
                            viewModel.updateSettings { it.copy(mtu = 1280) }
                        }
                        OptionChip("1500", settings.mtu == 1500) {
                            viewModel.updateSettings { it.copy(mtu = 1500) }
                        }
                        OptionChip("9000", settings.mtu == 9000) {
                            viewModel.updateSettings { it.copy(mtu = 9000) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlineButton(text = "Reconnect to apply changes", modifier = Modifier.fillMaxWidth()) {
                viewModel.disconnect()
                viewModel.postMessage("Disconnected. Connect again to apply the new routing.")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
