package com.v2rayvpn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2rayvpn.app.ui.components.GlassCard
import com.v2rayvpn.app.ui.components.GradientButton
import com.v2rayvpn.app.ui.components.ScreenBackground
import com.v2rayvpn.app.ui.theme.VpnColors

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "V2RAY VPN",
                style = MaterialTheme.typography.displaySmall,
                color = VpnColors.TextPrimary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "A free, ad-free client for your own V2Ray/Xray servers. Xray-core is the engine; your configurations stay on this device.",
                style = MaterialTheme.typography.bodyLarge,
                color = VpnColors.TextSecondary
            )
            Spacer(Modifier.height(28.dp))

            OnboardingPoint(
                title = "No servers are included",
                body = "This app never provides servers. Import a share link, a JSON configuration, a QR code or a subscription URL that you already have."
            )
            Spacer(Modifier.height(12.dp))
            OnboardingPoint(
                title = "Android will ask for VPN permission",
                body = "Android routes device traffic through a local TUN interface so Xray can carry it to your server. The app cannot read your traffic; it is not a proxy for anyone else."
            )
            Spacer(Modifier.height(12.dp))
            OnboardingPoint(
                title = "Credentials stay encrypted",
                body = "Profiles, UUIDs, passwords and subscription URLs are encrypted with a key held in the Android Keystore. Logs are redacted before they are kept."
            )
            Spacer(Modifier.height(12.dp))
            OnboardingPoint(
                title = "Free forever, no trackers",
                body = "No ads, no accounts, no analytics, no browsing history. The only optional external request is the public-IP lookup, which you must turn on yourself."
            )

            Spacer(Modifier.height(32.dp))
            GradientButton(
                text = "Continue",
                modifier = Modifier.fillMaxWidth(),
                onClick = onContinue
            )
        }
    }
}

@Composable
private fun OnboardingPoint(title: String, body: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "•",
                style = MaterialTheme.typography.titleLarge,
                color = VpnColors.Purple
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VpnColors.TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VpnColors.TextSecondary
                )
            }
        }
    }
}
