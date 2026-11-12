# Implementation decisions

Short records of the choices that shaped the code, with the reasoning and the trade-off. Anything
that differs from the instruction pack is called out explicitly.

## 1. Engine integration: prebuilt `libv2ray.aar`, pinned by release

* **Decision:** use `2dust/AndroidLibXrayLite` v26.11.9 (`libs/libv2ray.aar`), pinned by release
  tag and SHA-256, instead of building Xray-core with gomobile in this repository.
* **Why:** the binding is the maintained Android integration of Xray-core (the same one used by
  v2rayNG), it ships all four ABIs, and it exposes a real TUN file-descriptor handoff
  (`CoreController.startLoop(config, tunFd)`). Building Xray-core with gomobile requires Go, the
  NDK and hours of cgo compilation per ABI — not possible on the 2 GB build host, and it would
  make the build far less reproducible.
* **Verified before coding:** the Java API surface (generated sources in the AAR), the Go side of
  `StartLoop` (env var `xray.tun.fd`, fd duplication, non-blocking), and the `tun` inbound JSON
  schema from the exact pinned Xray-core commit (`676eaf66f8f0`).
* **Trade-off:** LGPL-3.0 dependency (see `licenses/`), and the engine version moves only when the
  AAR is replaced.

## 2. One `:app` module instead of the suggested module list

* **Decision:** single Gradle module, package-level separation (`core`, `domain`, `data`, `vpn`,
  `ui`).
* **Why:** the pack itself says "do not create needless modules"; twelve modules for ~40 files
  would add build time and configuration surface without adding a boundary the compiler enforces
  here. The package layout mirrors the suggested modules one-to-one.
* **Trade-off:** no compile-time enforcement of layer boundaries.

## 3. Manual DI instead of Hilt

* **Decision:** `AppContainer` singleton wired in `V2RayApp.onCreate`.
* **Why:** one graph, three dependencies. Hilt adds an annotation processor and generated code for
  no functional gain, and KSP/kapt is the single largest memory consumer in this build
  environment.
* **Trade-off:** no assisted injection or scoping framework; acceptable at this size.

## 4. Encrypted JSON document instead of Room + DataStore

* **Decision:** profiles, subscriptions and settings live in one AES-256-GCM blob
  (`files/store.enc`) keyed by an Android Keystore key, with hand-written mappers.
* **Why:** every field in the store is a credential or a preference that must be protected at rest.
  One encryption boundary is easy to audit; Room would need annotation processing, a separate
  encryption layer for SQLite, and would leave secrets in WAL/journal files unless carefully
  handled.
* **Trade-off:** the whole document is rewritten on each change. Fine for hundreds of profiles;
  would need Room if the dataset grew into the thousands.
* **Failure behaviour:** if the Keystore key is lost, the blob is moved aside as
  `store.enc.unreadable` and the app starts empty instead of crashing or silently overwriting.

## 5. Traffic counters from the engine, not from Android

* **Decision:** read `CoreController.queryAllOutboundTrafficStats()` once per second and accumulate
  the deltas for the `proxy` outbound.
* **Why:** it measures what actually went through the tunnel, and the counters reset on read so
  deltas are exact. `TrafficStats` would mix in traffic that never entered the tunnel.
* **Trade-off:** counters are per process/session; they are not persisted across restarts.

## 6. Latency tests are labelled as proxy round-trips

* **Decision:** `Libv2ray.measureOutboundDelay(config, url)` for a profile, `measureDelay(url)` for
  the live tunnel; the UI says "proxy round-trip" and shows the timestamp.
* **Why:** a TCP handshake is not a proxy test, and a proxy round-trip is not throughput. The pack
  forbids presenting one as the other.
* **Trade-off:** each test builds a throw-away core instance, so "test all" is sequential and
  slower than a ping sweep.

## 7. The app's own traffic is excluded from the VPN

* **Decision:** `VpnService.Builder.addDisallowedApplication(packageName)` (and in allow-list mode,
  by not listing the app at all).
* **Why:** the engine's sockets then leave through the physical network instead of looping back
  into the TUN. This is the mechanism the upstream client uses; `autoOutboundsInterface` is not
  needed and is unsupported ("automatic outbound interface selection is not supported on this
  platform") when `autoSystemRoutingTable` is absent.
* **Trade-off:** while connected, the app's own UI requests (subscription refresh, optional IP
  lookup) also bypass the tunnel. This is stated in the UI where relevant.

## 8. IPv6 is enabled by default

* **Decision:** `ipv6Enabled = true`; the builder adds an IPv6 address and `::/0` (or the non-LAN
  IPv6 ranges) whenever it is on.
* **Why:** a VPN that routes only IPv4 leaks IPv6 traffic on IPv6-capable networks. The toggle is
  kept for users whose server cannot carry IPv6, with an explicit warning about the leak.

## 9. Bypass-LAN is implemented at the Android route level

* **Decision:** in bypass mode the builder adds the complement of the private ranges instead of
  `0.0.0.0/0`; a `geoip:private → direct` rule is kept as defence in depth.
* **Why:** "bypass" must mean LAN packets never enter the tunnel — otherwise a printer or router
  page would be proxied. Verified against the same route list used by the upstream client.

## 10. R8 is disabled in the published release build

* **Decision:** `isMinifyEnabled = false`, `isShrinkResources = false` for release.
* **Why:** the build host has ~2 GB of RAM; the shrinker was killed mid-run. Shipping a signed,
  tested, slightly larger APK is better than shipping nothing or shipping an unverified obfuscated
  build.
* **Trade-off:** larger APK, readable bytecode. `proguard-rules.pro` is present and correct, so
  re-enabling both flags on a normal machine is a two-line change (`docs/BUILDING.md`).

## 11. Cleartext HTTP is allowed at the manifest level

* **Decision:** `android:usesCleartextTraffic="true"`.
* **Why:** a user may import an `http://` subscription URL; blocking cleartext would make those
  subscriptions unusable with a confusing error. The app never downgrades its own HTTPS requests
  and never disables certificate validation.
* **Trade-off:** an `http://` subscription is readable by the network. The UI warns before adding
  one and masks the URL afterwards.

## 12. Single ViewModel

* **Decision:** one `AppViewModel` for all screens.
* **Why:** the screens share one repository and one connection state; per-screen ViewModels would
  duplicate the same plumbing twelve times. Logic lives in the repository, parsers and VPN
  service, so this class only orchestrates.
* **Trade-off:** a 400-line ViewModel; splitting it is mechanical if the app grows.
