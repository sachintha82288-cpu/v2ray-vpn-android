# V2RAY VPN 1.0.0

First public release of the Android client. Free forever: no ads, no accounts, no analytics, no
bundled servers.

## Download

| File | For | Size |
|---|---|---|
| `app-arm64-v8a-release.apk` | Almost all modern phones and tablets (64-bit ARM) | ~47 MB |
| `app-armeabi-v7a-release.apk` | Older 32-bit ARM devices | ~46 MB |
| `app-x86_64-release.apk` | 64-bit x86 emulators / Chromebooks | ~49 MB |
| `app-x86-release.apk` | 32-bit x86 emulators | ~46 MB |
| `app-universal-release.apk` | Any device, contains all four ABIs | ~144 MB |
| `app-universal-debug.apk` | Debug build, `applicationId` ends in `.debug` | ~149 MB |

Verify before installing: `sha256sum -c CHECKSUMS.txt`

Signed with a 2048-bit RSA key, certificate `CN=V2RAY VPN, OU=Android, O=V2RAY VPN, L=Colombo, C=LK`,
SHA-256 `c0f226197f6b50fe9525544f2895845eb5b24a59145588ba95d188cdb626312e`.

## What works

- Xray-core engine (AndroidLibXrayLite v26.11.9, lib v40) over a real Android `VpnService` TUN.
- VMess, VLESS, Trojan, Shadowsocks (AEAD + 2022), SOCKS, HTTP.
- TCP, WebSocket, gRPC, HTTP/2, XHTTP, QUIC; TLS and REALITY.
- Import by share link, Xray JSON, QR image, clipboard or file; validation with explicit errors.
- Subscriptions with manual/automatic refresh that keeps your working list when a refresh fails.
- Routing (global / bypass LAN), Xray DNS or plain DNS, per-app allow/bypass lists, MTU, IPv6.
- Real session counters from the engine, redacted logs, latency tests labelled as proxy round-trips.
- AMOLED glass UI in Compose, 12 screens, accessibility labels, reduced-motion option.

## Honest limitations

- No servers are included — you must import your own configuration.
- Not verified on a physical device by the build pipeline: install, tunnel, reconnect and per-app
  behaviour are documented as untested in `docs/TEST_REPORT.md`. Please report what you see.
- Kill-switch depends on Android's always-on VPN setting; the app cannot enforce it alone.
- No automatic failover; latency sorting and "use fastest" only.
- Shadowsocks plugins, WireGuard and Hysteria outbounds are rejected.
- R8 shrinking is disabled in this build (2 GB build host), so the APK is larger than it could be.

## Install

1. Download the APK for your device.
2. Allow installs from unknown sources when prompted.
3. Open the app, finish onboarding, import a configuration.
4. Tap CONNECT and accept Android's VPN permission request.

## Source and licenses

Full source in this repository. Xray-core is MPL-2.0; AndroidLibXrayLite is LGPL-3.0 and is used as
an unmodified prebuilt shared library — its source is available upstream. Other components are
Apache-2.0. See `licenses/` and the in-app About screen.
