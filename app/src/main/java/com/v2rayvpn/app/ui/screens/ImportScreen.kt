package com.v2rayvpn.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.v2rayvpn.app.ui.AppViewModel
import com.v2rayvpn.app.ui.components.GlassCard
import com.v2rayvpn.app.ui.components.GradientButton
import com.v2rayvpn.app.ui.components.InfoBanner
import com.v2rayvpn.app.ui.components.OutlineButton
import com.v2rayvpn.app.ui.components.ScreenBackground
import com.v2rayvpn.app.ui.components.ScreenHeader
import com.v2rayvpn.app.ui.components.SectionTitle
import com.v2rayvpn.app.ui.theme.VpnColors

@Composable
fun ImportScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val busy by viewModel.busy.collectAsState()
    var text by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFile(it) } }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importQr(it) } }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            ScreenHeader(
                title = "Import",
                subtitle = "Share link, JSON configuration, QR code or file",
                onBack = onBack
            )
            Spacer(Modifier.height(14.dp))

            InfoBanner(
                text = "Only import configurations from sources you trust. A configuration decides which server your traffic is sent to. Nothing is executed: links and JSON are parsed as data and validated."
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Paste a link or JSON configuration") },
                placeholder = { Text("vmess://… vless://… trojan://… ss://… or an Xray JSON object") },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(text = "Paste", modifier = Modifier.weight(1f)) {
                    val clip = clipboard.getText()
                    if (clip.isNullOrBlank()) viewModel.postMessage("Clipboard is empty")
                    else text = clip.text
                }
                OutlineButton(text = "File", modifier = Modifier.weight(1f)) {
                    filePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
                OutlineButton(text = "QR image", modifier = Modifier.weight(1f)) {
                    imagePicker.launch("image/*")
                }
            }

            Spacer(Modifier.height(14.dp))
            if (busy) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(color = VpnColors.Purple)
                }
                Spacer(Modifier.height(12.dp))
            }
            GradientButton(
                text = "Validate and import",
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && text.isNotBlank(),
                onClick = {
                    viewModel.importText(text)
                    text = ""
                }
            )

            Spacer(Modifier.height(20.dp))
            SectionTitle("What is supported")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Protocols: VMess, VLESS, Trojan, Shadowsocks (AEAD and 2022 ciphers), SOCKS, HTTP.\n" +
                        "Transports: TCP, WebSocket, gRPC, HTTP/2, XHTTP, QUIC.\n" +
                        "Security: TLS, REALITY (VLESS/Trojan), none.\n" +
                        "Formats: share links, single-outbound Xray JSON, QR codes, subscription URLs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VpnColors.TextSecondary
                )
            }
            Spacer(Modifier.height(12.dp))
            SectionTitle("Not supported")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Shadowsocks plugins (obfs, v2ray-plugin), WireGuard and Hysteria outbounds, " +
                        "configurations with multiple outbounds or balancers, and any configuration that needs a field " +
                        "this client does not map. These are rejected with a message instead of being silently altered.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VpnColors.TextSecondary
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
