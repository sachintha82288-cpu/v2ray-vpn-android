package com.v2rayvpn.app.domain.model

/** Accent palettes. The app is dark-only by design; the accent is the variable. */
enum class ThemeAccent(
    val label: String,
    val primary: Long,
    val deep: Long,
    val secondary: Long
) {
    VIOLET("Violet", 0xFFA855F7, 0xFF7C3AED, 0xFF3B82F6),
    AZURE("Azure", 0xFF3B82F6, 0xFF1D4ED8, 0xFF22D3EE),
    CYAN("Cyan", 0xFF22D3EE, 0xFF0891B2, 0xFF818CF8),
    EMERALD("Emerald", 0xFF34D399, 0xFF059669, 0xFF22D3EE),
    ROSE("Rose", 0xFFFB7185, 0xFFE11D48, 0xFFA855F7)
}

/** Pure-black AMOLED surfaces, or slightly lifted charcoal for readability in bright light. */
enum class SurfaceStyle(val label: String) {
    AMOLED("AMOLED black"),
    MIDNIGHT("Midnight charcoal")
}

enum class RoutingMode(val label: String) {
    GLOBAL("Global — all traffic through the proxy"),
    BYPASS_LAN("Bypass local network — LAN stays direct")
}

enum class DnsMode(val label: String) {
    VPN("Xray DNS module (port 53 hijacked into the tunnel)"),
    SYSTEM("Plain DNS forwarded through the proxy")
}

enum class PerAppMode(val label: String) {
    OFF("All apps use the VPN"),
    ALLOW("Only selected apps use the VPN"),
    DISALLOW("Selected apps bypass the VPN")
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    val themeAccent: ThemeAccent = ThemeAccent.VIOLET,
    val surfaceStyle: SurfaceStyle = SurfaceStyle.AMOLED,
    val selectedProfileId: String? = null,
    val routingMode: RoutingMode = RoutingMode.BYPASS_LAN,
    val blockAdsAndTrackers: Boolean = false,
    val ipv6Enabled: Boolean = true,
    val dnsMode: DnsMode = DnsMode.VPN,
    val remoteDns: String = "1.1.1.1",
    val localDns: String = "1.1.1.1",
    val mtu: Int = 1500,
    val perAppMode: PerAppMode = PerAppMode.OFF,
    val perAppPackages: Set<String> = emptySet(),
    val reconnectOnNetworkChange: Boolean = true,
    val logLevel: String = "warning",
    val reducedMotion: Boolean = false,
    val publicIpLookupEnabled: Boolean = false,
    val subscriptionAutoRefreshHours: Int = 0,
    val healthCheckUrl: String = "https://www.google.com/generate_204",
    val latencyTestUrl: String = "https://www.google.com/generate_204"
) {
    val tunIpv4Address: String get() = "10.10.14.1"
    val tunIpv6Address: String get() = "fc00::10:10:14:1"
}
