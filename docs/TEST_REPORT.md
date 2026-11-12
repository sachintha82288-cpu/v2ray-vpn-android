# Test report — V2RAY VPN 1.0.0 (versionCode 1)

Date: 2026-11-12
Build: `assembleDebug`, `assembleRelease`, `testDebugUnitTest` on JDK 17.0.12, Gradle 8.7,
AGP 8.5.2, Kotlin 2.0.21, compileSdk 34, minSdk 26.

Status vocabulary: **PASS** = executed and verified in this build · **BLOCKED / NOT VERIFIED** =
could not be executed in the build environment · **FAIL** = executed and failed.

---

## 1. Automated tests — PASS

`./gradlew :app:testDebugUnitTest` → **43 tests, 0 failures, 0 skipped** (JVM, `app/build/test-results/testDebugUnitTest/*.xml`).

| Suite | Tests | Covers |
|---|---|---|
| `UriParserTest` | 14 | VLESS (WS/TLS, REALITY), VMess base64, Trojan, Shadowsocks (SIP002 + legacy), SOCKS auth, IPv6 literal; rejects invalid UUID, out-of-range port, REALITY without `pbk`, SS plugins, unsupported cipher |
| `XrayConfigBuilderTest` | 7 | TUN inbound contract, VLESS user mapping, stats/policy enabled, DNS-outbound + hijack rule only in Xray-DNS mode, bypass-LAN rule presence, Shadowsocks method/password, no local proxy port opened |
| `SubscriptionParserTest` | 9 | base64 list, plain list, JSON array, de-duplication, partial-parse skipping, empty/garbage bodies rejected, `subscription-userinfo` parsing (and absence) |
| `XrayJsonImportTest` | 5 | VMess JSON import (transport/security/TLS fields), rejects balancers, unsupported protocol, malformed JSON, oversized input |
| `ShareUriBuilderTest` | 3 | VLESS (REALITY/gRPC) and Shadowsocks round-trip, export scheme |
| `RedactorTest` | 5 | UUIDs, share links, key/value secrets, bearer tokens removed; byte/duration/latency formatting |

First run had 1 failure (`RedactorTest.removesUuids`) caused by redaction ordering: the
`<uuid:redacted>` marker itself matched the `uuid:` key/value rule. Fixed by redacting key/value
pairs first and using the marker `[uuid]`; suite re-run green.

## 2. Build verification — PASS

| Check | Result | Evidence |
|---|---|---|
| Clean debug build | PASS | `BUILD SUCCESSFUL`, 5 APKs (4 ABI + universal) |
| Release build | PASS | `BUILD SUCCESSFUL`, 5 APKs |
| Release signing | PASS | `apksigner verify --print-certs` → `CN=V2RAY VPN, OU=Android, O=V2RAY VPN, L=Colombo, C=LK`, SHA-256 `c0f22619…b626312e` on all 5 APKs |
| ABI contents | PASS | `unzip -l` shows exactly one `lib/<abi>/libgojni.so` per split; universal APK contains all four |
| Manifest | PASS | `aapt2 dump badging`: `com.v2rayvpn.app`, versionCode 1 / 1.0.0, minSdk 26, targetSdk 34, launcher `MainActivity` |
| VPN service declared | PASS | manifest tree: `V2RayVpnService`, `android:permission="android.permission.BIND_VPN_SERVICE"`, `exported=false`, intent-filter `android.net.VpnService` |
| Permission set | PASS | INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS, QUERY_ALL_PACKAGES (per-app list only) — no camera, contacts, location, storage |
| No analytics/ads SDKs | PASS | dependency list contains only AndroidX/Compose, Kotlin/coroutines, OkHttp, ZXing, libv2ray |

## 3. On-device behaviour — BLOCKED / NOT VERIFIED

No Android device, and no hardware-accelerated emulator, was available in the build environment
(`/dev/kvm` absent, no ADB target). The following gates from the instruction pack are therefore
**not** claimed as passing:

| Gate | Status | Notes |
|---|---|---|
| APK installs on a supported device | BLOCKED | Signature and manifest validated statically only |
| Real Xray tunnel with a valid configuration | BLOCKED | Requires a device plus a user-supplied server; no endpoint was provided and none was invented |
| Connect / disconnect lifecycle, TUN fd handoff at runtime | BLOCKED | Code path follows the verified upstream contract (`startLoop(config, tunFd)`, `xray.tun.fd`) but was not executed on hardware |
| Health-check gating of the "Connected" state | BLOCKED | Implementation reviewed; runtime behaviour unverified |
| No crash on malformed imports | PARTIAL — parser level PASS (unit tests), UI level BLOCKED | Every parser rejects bad input in tests; the Compose error paths are not instrumented |
| Secret redaction | PASS at unit level | Redaction tests; log export uses the same function |
| Network interruption / reconnect | BLOCKED | `ConnectivityManager` callback implemented, untested on device |
| Protocol tests against real endpoints | BLOCKED | No endpoints available |
| Always-on / kill-switch behaviour | BLOCKED | Depends on OS setting; not exercised |
| Per-app VPN allow/disallow lists | BLOCKED | `VpnService.Builder` calls implemented, untested on device |
| Battery / performance | BLOCKED | Not measurable here |

**How to close these gates:** install `app-arm64-v8a-release.apk` on an Android 8.0+ device, import
a configuration you control, connect, confirm the notification, the health-check state, counters
moving under traffic, then toggle airplane mode and revoke the VPN permission from system settings.

## 4. Reproducibility notes

* Engine artifact pinned by release tag and SHA-256 (`CHECKSUMS.txt`).
* All Gradle dependencies pinned to exact versions (no `+`), repositories limited to
  `google()` and `mavenCentral()`.
* `libs/libv2ray.aar` is vendored in the repository, so the engine does not depend on a remote
  resolution step.
* R8 is disabled in the release build (documented in `IMPLEMENTATION_DECISIONS.md` §10), so the
  release APK contains unobfuscated bytecode.
