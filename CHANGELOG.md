# Changelog

All notable changes to V2RAY VPN. The app ships as signed APKs on the
[releases page](https://github.com/sachintha82288-cpu/v2ray-vpn-android/releases).

## 1.1.0 — 2026-11-18

### New features
- **Quick Settings tile** (`VpnTileService`): connect or disconnect from the notification shade.
  If the VPN has never been authorised it opens the app instead of pretending to connect.
- **Live counters in the ongoing notification**: download/upload rate and session total, refreshed
  every five seconds from the engine's own statistics.
- **Reconnect action** on the home screen, and an internal restart path that tears the tunnel down
  before bringing it back up.
- **Theme controls**: five accent palettes (Violet, Azure, Cyan, Emerald, Rose) and two surface
  styles (AMOLED pure black, Midnight charcoal), persisted with the rest of the settings.

### Bug fixes
- Session traffic and the active-profile marker are now cleared when a tunnel stops, so the home
  screen no longer shows a previous session's totals after disconnecting.
- Deleting the profile that is carrying the tunnel now disconnects first instead of leaving an
  orphaned VPN service running.
- Per-app "only selected" mode with an empty selection silently routed *every* app; it now behaves
  like "all apps" explicitly and logs a warning, matching what the label says.
- VMess links that rely on TCP header obfuscation are rejected with a clear message instead of
  being imported as a configuration that cannot connect.
- The installed-app list for split tunnelling is queried off the main thread.
- The settings store is no longer loaded twice at startup.
- Removed two JVM crash dumps (`hs_err_pid*.log`) that had been committed by mistake.
- Statistics screen duration now ticks every second instead of only when traffic arrives.

### Build
- Kotlin compilation moved to the Kotlin daemon with C1-only tiered compilation, which cut a cold
  build on a 2 GB / 2 vCPU host from "does not finish" to ~2 minutes.
- `versionCode` 2, `versionName` 1.1.0.

### Tests
- 47 JVM unit tests (was 43): settings/theme round-trip, theme fallback for unknown values, MTU
  clamping, and VMess header-obfuscation rejection.

## 1.0.0 — 2026-11-12

First public release: Xray-core engine over Android `VpnService`, VMess/VLESS/Trojan/Shadowsocks/
SOCKS/HTTP, subscriptions, routing, DNS, split tunnelling, AMOLED Compose UI, 43 unit tests.
