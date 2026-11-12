# V2RAY VPN

A free, ad-free Android client for **your own** V2Ray/Xray servers. Xray-core is the engine;
Android's `VpnService` provides the TUN interface; every configuration, subscription URL and
credential stays on the device, encrypted with an Android Keystore key.

This repository contains the complete Android Studio project, the unit tests, the release APKs and
the documents required to audit the build.

---

## What this app is

| | |
|---|---|
| Product | V2RAY VPN — Android VPN **client** |
| Engine | Xray-core (via the `libv2ray` gomobile binding, `AndroidLibXrayLite` v26.11.9) |
| Protocols | VMess, VLESS, Trojan, Shadowsocks (AEAD + 2022), SOCKS, HTTP |
| Transports | TCP, WebSocket, gRPC, HTTP/2, XHTTP, QUIC |
| Security | TLS, REALITY (VLESS/Trojan), none |
| UI | Jetpack Compose, pure AMOLED black + glass-inspired surfaces, purple/blue accents |
| Business model | Free forever — no ads, no subscriptions, no accounts, no analytics |
| Servers | **None are provided.** You import your own configuration or subscription. |

## Features

**Connection**
- Real `VpnService` + TUN lifecycle; the descriptor is handed to Xray-core with
  `CoreController.startLoop(config, tunFd)`.
- The UI only shows **Connected** after the TUN interface is up, the engine has started *and* a
  health check has round-tripped through the tunnel. Otherwise it says so.
- Foreground service with a status notification and a disconnect action.
- Revoke handling, network-change reconnect (configurable), clean teardown of the descriptor,
  engine and coroutines.

**Configuration**
- Import from share link (`vmess://`, `vless://`, `trojan://`, `ss://`, `socks://`, `http://`),
  single-outbound Xray JSON, QR image, clipboard or file.
- Manual paste editor with validation; unsupported fields are rejected with a reason, never
  silently dropped.
- Search, sort (name / latency / recent), favourites, rename, duplicate, delete, share link, QR
  export, JSON export.
- De-duplication by endpoint fingerprint.

**Subscriptions**
- Multiple URLs, manual refresh, configurable automatic refresh.
- Parses base64 link lists, plain link lists and JSON arrays; reads `subscription-userinfo` and
  `profile-title` headers when the server sends them.
- A failed refresh **keeps** the previously working configurations and shows the error.
- URLs are stored encrypted, masked in the UI and never logged.

**Network controls**
- Routing: global, or bypass-LAN (private ranges excluded at the Android route level).
- DNS: Xray DNS module with port-53 hijack, or plain DNS forwarded through the proxy.
- IPv6 on by default (no silent IPv6 leak), optional block-ads/trackers rule (`geosite:category-ads-all`).
- Per-app VPN: all apps, allow-list or bypass-list, applied through `VpnService.Builder`.
- MTU selection, log level.

**Diagnostics**
- Session counters and rates read from the engine's outbound statistics once per second.
- Latency tests that state exactly what they measure (proxy round-trip, not throughput).
- Redacted in-memory log with copy/share/clear.
- Optional public-IP lookup — **off by default**, discloses that it contacts `api.ipify.org`.

## Screens

Onboarding · Home dashboard · Servers · Import · Subscriptions · Statistics · Logs & diagnostics ·
DNS · Routing · Split tunnelling · Settings · About/privacy/licenses.

## Build

Requirements: JDK 17, Android SDK with platform 34 and build-tools 34.0.0, Gradle 8.7 (wrapper
compatible), ~2 GB free disk for the AAR and build outputs.

```bash
# 1. Fetch the pinned engine AAR (60 MB, attached to the release) and verify it
./scripts/fetch_engine.sh

# 2. Point Gradle at your SDK
echo "sdk.dir=/path/to/android-sdk" > local.properties

# 3. Build
./gradlew :app:assembleDebug      # debug APKs (per-ABI + universal)
./gradlew :app:testDebugUnitTest  # 43 JVM unit tests
./gradlew :app:assembleRelease    # release APKs; signed when keystore.properties exists
```

Release signing uses an untracked `keystore.properties` at the repository root:

```properties
storeFile=release.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

Without that file the release APKs are produced unsigned. See `docs/BUILDING.md` for the exact
toolchain versions used for the published APKs and for the constrained-host notes.

## Install

1. Download the APK for your CPU (`arm64-v8a` for almost all phones) or the universal APK.
2. Allow installation from unknown sources for your browser/file manager.
3. Open the app, complete onboarding, import a configuration.
4. Tap **CONNECT** and accept Android's VPN permission dialog.
5. Verify `SHA-256` against `CHECKSUMS.txt` before installing if you downloaded from elsewhere.

## Project layout

```
app/src/main/java/com/v2rayvpn/app/
├── core/          Keystore crypto, redaction, formatting, QR, container (manual DI)
├── domain/model/  Profile, Subscription, AppSettings, ConnectionState, TrafficSnapshot
├── data/          Encrypted store + mappers, repositories, parsers, subscription fetcher
├── vpn/           VpnService, Xray engine wrapper, latency tester, state holder
└── ui/            Compose theme, components, single ViewModel, 12 screens
app/src/test/      JVM unit tests for parsers, config builder, redaction, formatting
docs/              Build notes, test report, project status, decisions, spec pack
licenses/          LGPL-3.0 (AndroidLibXrayLite) and MPL-2.0 (Xray-core) texts
libs/              Engine AAR location + provenance (binary is a release asset)
```

## Security and privacy summary

- No analytics, crash reporting, ads or accounts.
- No browsing history is stored. The log holds connection events and engine messages only, and
  every line is redacted (UUIDs, passwords, keys, share links, tokens) before it is kept.
- Profiles, subscriptions and settings live in one AES-256-GCM blob keyed by a non-exportable
  Android Keystore key (`app/src/main/java/com/v2rayvpn/app/core/crypto/SecretBox.kt`).
- `allowBackup=false` and cloud backup excluded, so secrets are not copied off-device.
- Imported JSON, QR data and subscription bodies are bounded in size, parsed as data, never
  executed, and validated before use.
- Cleartext HTTP is permitted **only** because a user may import an `http://` subscription URL;
  TLS certificate validation is never disabled by this app.
- "Delete all local data" removes every stored profile and subscription.

## Known limitations

- Client only. No servers, no accounts, no server marketplace.
- Latency numbers are proxy round-trips, not throughput; the app performs no speed tests.
- Kill-switch depends on Android's *always-on VPN* system setting; the app cannot enforce it alone.
- Automatic failover between servers is not implemented (manual selection and latency sorting only).
- Shadowsocks plugins (obfs/v2ray-plugin), WireGuard and Hysteria outbounds are rejected.
- R8/ProGuard shrinking is disabled in the published release build (see `docs/IMPLEMENTATION_DECISIONS.md`).
- No instrumented (on-device) test run was possible in the build environment; see
  `docs/TEST_REPORT.md` for what is verified and what is blocked.

## Licenses

Xray-core is MPL-2.0. AndroidLibXrayLite (the `libv2ray` AAR) is LGPL-3.0 and is used as an
unmodified prebuilt shared library (`libgojni.so`); its complete source is available upstream.
AndroidX/Compose, Kotlin, OkHttp and ZXing are Apache-2.0. Full texts: `licenses/`, in-app About
screen.
