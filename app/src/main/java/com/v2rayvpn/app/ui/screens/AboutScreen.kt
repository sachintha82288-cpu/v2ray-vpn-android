package com.v2rayvpn.app.ui.screens

import androidx.compose.foundation.layout.Column
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
import com.v2rayvpn.app.BuildConfig
import com.v2rayvpn.app.ui.AppViewModel
import com.v2rayvpn.app.ui.components.GlassCard
import com.v2rayvpn.app.ui.components.ScreenBackground
import com.v2rayvpn.app.ui.components.ScreenHeader
import com.v2rayvpn.app.ui.components.SectionTitle
import com.v2rayvpn.app.ui.theme.VpnColors

@Composable
fun AboutScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val xrayVersion by viewModel.xrayVersion.collectAsState()

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            ScreenHeader(title = "About", subtitle = "Version, privacy and licenses", onBack = onBack)
            Spacer(Modifier.height(14.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("V2RAY VPN", style = MaterialTheme.typography.titleLarge, color = VpnColors.TextPrimary)
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = VpnColors.TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Engine: $xrayVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = VpnColors.TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "A free, ad-free Android client for configurations you supply. No accounts, no telemetry, no bundled servers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VpnColors.TextSecondary
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Privacy")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "• No analytics, crash reporting, advertising SDKs or accounts.\n" +
                        "• No browsing history is recorded. The log only contains connection events and engine messages, redacted.\n" +
                        "• Profiles, credentials and subscription URLs are encrypted at rest with a non-exportable Android Keystore key.\n" +
                        "• Backups are disabled for this app (allowBackup=false, cloud backup excluded) so secrets are not copied off-device.\n" +
                        "• Network requests: subscription downloads you configure, and the optional public-IP lookup (off by default).\n" +
                        "• \"Delete all local data\" in Settings removes every stored profile and subscription.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VpnColors.TextSecondary
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Open-source licenses")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                License("Xray-core", "MPL-2.0", "https://github.com/XTLS/Xray-core")
                License("AndroidLibXrayLite (libv2ray AAR)", "LGPL-3.0, unmodified prebuilt shared library", "https://github.com/2dust/AndroidLibXrayLite")
                License("AndroidX / Jetpack Compose", "Apache-2.0", "https://developer.android.com/jetpack")
                License("Kotlin & kotlinx.coroutines", "Apache-2.0", "https://kotlinlang.org")
                License("OkHttp", "Apache-2.0", "https://square.github.io/okhttp/")
                License("ZXing core", "Apache-2.0", "https://github.com/zxing/zxing")
                License("geoip.dat / geosite.dat", "Data distributed with Xray-core", "https://github.com/v2fly/geoip")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Full license texts are in the licenses/ directory of the source repository. " +
                        "The LGPL-3.0 engine library is used as an unmodified shared object (libgojni.so) built from the " +
                        "upstream project; its complete corresponding source is available at the link above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VpnColors.TextTertiary
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Limitations")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "• The app is a client only: it cannot provide servers, and no configuration is included.\n" +
                        "• Latency figures are proxy round-trip measurements, not throughput.\n" +
                        "• Kill-switch relies on Android's always-on VPN setting, which you enable in system settings; the app cannot enforce it alone.\n" +
                        "• Shadowsocks plugins, WireGuard and Hysteria outbounds are not supported.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VpnColors.TextSecondary
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun License(name: String, license: String, url: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = VpnColors.TextPrimary)
        Text(license, style = MaterialTheme.typography.bodySmall, color = VpnColors.TextSecondary)
        Text(url, style = MaterialTheme.typography.bodySmall, color = VpnColors.Cyan)
    }
}
