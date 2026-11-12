package com.v2rayvpn.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2rayvpn.app.domain.model.PerAppMode
import com.v2rayvpn.app.ui.AppViewModel
import com.v2rayvpn.app.ui.InstalledApp
import com.v2rayvpn.app.ui.components.InfoBanner
import com.v2rayvpn.app.ui.components.OptionChip
import com.v2rayvpn.app.ui.components.ScreenBackground
import com.v2rayvpn.app.ui.components.ScreenHeader
import com.v2rayvpn.app.ui.theme.VpnColors

@Composable
fun SplitTunnelScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { apps = viewModel.loadInstalledApps() }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            ScreenHeader(
                title = "Split tunnelling",
                subtitle = "Per-app VPN rules enforced by Android",
                onBack = onBack
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionChip("All apps", settings.perAppMode == PerAppMode.OFF) {
                    viewModel.updateSettings { it.copy(perAppMode = PerAppMode.OFF) }
                }
                OptionChip("Only selected", settings.perAppMode == PerAppMode.ALLOW) {
                    viewModel.updateSettings { it.copy(perAppMode = PerAppMode.ALLOW) }
                }
                OptionChip("Bypass selected", settings.perAppMode == PerAppMode.DISALLOW) {
                    viewModel.updateSettings { it.copy(perAppMode = PerAppMode.DISALLOW) }
                }
            }
            Spacer(Modifier.height(10.dp))
            InfoBanner(
                text = "These rules are applied by Android's VpnService when the tunnel is created, so apps outside " +
                    "the selection never see the VPN interface. This app itself always stays outside the tunnel to avoid a loop. " +
                    "Reconnect after changing the list.",
                tone = VpnColors.Blue
            )
            Spacer(Modifier.height(10.dp))

            if (settings.perAppMode != PerAppMode.OFF) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps (${settings.perAppPackages.size} selected)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = settings.perAppPackages.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = if (checked) {
                                        settings.perAppPackages - app.packageName
                                    } else {
                                        settings.perAppPackages + app.packageName
                                    }
                                    viewModel.updateSettings { it.copy(perAppPackages = updated) }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.padding(4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = VpnColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VpnColors.TextTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            } else {
                Text(
                    text = "Every app on the device uses the VPN. Choose \"Only selected\" or \"Bypass selected\" to change that.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VpnColors.TextSecondary,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
