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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.v2rayvpn.app.domain.model.DnsMode
import com.v2rayvpn.app.ui.AppViewModel
import com.v2rayvpn.app.ui.components.GlassCard
import com.v2rayvpn.app.ui.components.InfoBanner
import com.v2rayvpn.app.ui.components.OptionChip
import com.v2rayvpn.app.ui.components.OutlineButton
import com.v2rayvpn.app.ui.components.ScreenBackground
import com.v2rayvpn.app.ui.components.ScreenHeader
import com.v2rayvpn.app.ui.components.SectionTitle
import com.v2rayvpn.app.ui.theme.VpnColors

@Composable
fun DnsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    var remote by remember(settings.remoteDns) { mutableStateOf(settings.remoteDns) }
    var local by remember(settings.localDns) { mutableStateOf(settings.localDns) }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            ScreenHeader(title = "DNS", subtitle = "How names are resolved while connected", onBack = onBack)
            Spacer(Modifier.height(14.dp))

            SectionTitle("Mode")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DnsMode.entries.forEach { mode ->
                    OptionChip(
                        label = if (mode == DnsMode.VPN) "Xray DNS" else "Plain DNS",
                        selected = settings.dnsMode == mode
                    ) {
                        viewModel.updateSettings { it.copy(dnsMode = mode) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = when (settings.dnsMode) {
                        DnsMode.VPN ->
                            "Queries sent to the DNS address below are captured on port 53 by the Xray DNS module " +
                                "and resolved through your proxy. Answers cannot be read by the local network."
                        DnsMode.SYSTEM ->
                            "The address below is handed to Android as the VPN DNS server. Queries travel through the " +
                                "proxy as ordinary DNS packets, without the Xray DNS module."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = VpnColors.TextSecondary
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Servers")
            OutlinedTextField(
                value = remote,
                onValueChange = { remote = it.trim() },
                label = { Text("Remote DNS (IP address)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = local,
                onValueChange = { local = it.trim() },
                label = { Text("Local DNS for private domains (IP address)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlineButton(
                text = "Save DNS settings",
                modifier = Modifier.fillMaxWidth(),
                enabled = isIp(remote) && isIp(local)
            ) {
                viewModel.updateSettings { it.copy(remoteDns = remote, localDns = local) }
                viewModel.postMessage("DNS settings saved — reconnect to apply")
            }
            Spacer(Modifier.height(12.dp))
            InfoBanner(
                text = "Only numeric IP addresses are accepted. DNS-over-HTTPS and DNS-over-TLS endpoints are not configured by this screen; " +
                    "a DoH/DoT upstream can be added by importing a full Xray JSON configuration that contains a dns section.",
                tone = VpnColors.Blue
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun isIp(value: String): Boolean {
    val v = value.trim()
    val ipv4 = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
    if (ipv4.matches(v)) return v.split('.').all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
    return v.contains(':') && Regex("^[0-9a-fA-F:]{3,39}$").matches(v)
}
