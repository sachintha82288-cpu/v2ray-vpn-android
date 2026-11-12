# Project status — V2RAY VPN 1.0.0

Legend: **Implemented** = code exists and is covered by unit tests or static verification ·
**Partial** = implemented with a documented gap · **Blocked** = written but not executable in this
environment · **Not implemented** = deliberately out of scope for 1.0.0.

## Screens

| Screen | Status | Notes |
|---|---|---|
| Onboarding / first-run explanation | Implemented | Explains VpnService, no bundled servers, encryption, no trackers |
| Home dashboard | Implemented | Connect control, state, duration, rates/totals, selected server, warnings, quick actions |
| Servers | Implemented | Search, sort (name/latency/recent), favourites, per-server and bulk latency test, "use fastest", rename/duplicate/share/QR/delete |
| Import | Implemented | Link, JSON, clipboard, file, QR image; validation errors surfaced |
| Subscription manager | Implemented | Multiple URLs, manual + automatic refresh, metadata when present, keeps old data on failure, masked URLs |
| Statistics | Implemented | Session counters/rates from engine, latency table with test type + timestamp |
| Logs & diagnostics | Implemented | Level filter, copy, share, clear, redacted export |
| DNS settings | Implemented | Xray-DNS vs plain-DNS mode, remote/local resolver, IP validation |
| Routing settings | Implemented | Global / bypass-LAN, ad-block rule, IPv6, MTU |
| Split tunnelling | Implemented | All / allow-list / bypass-list with app picker |
| General settings | Implemented | Reconnect, auto-refresh, log level, public-IP opt-in, reduced motion, export, delete all |
| About / privacy / licenses | Implemented | Versions, privacy statement, license list, limitations |

## Feature groups

| Area | Status | Notes |
|---|---|---|
| VMess / VLESS / Trojan / Shadowsocks | Implemented | Config builder covered by tests |
| SOCKS / HTTP outbounds | Implemented | With optional username/password |
| Transports TCP/WS/gRPC/HTTP2/XHTTP/QUIC | Implemented | Mapped to the pinned engine's field names |
| TLS / REALITY | Implemented | REALITY requires `pbk`; rejected otherwise |
| Shadowsocks plugins, WireGuard, Hysteria | Not implemented | Explicitly rejected with a message |
| Multi-outbound / balancer configs | Not implemented | Rejected on import |
| TUN lifecycle, foreground service, notification | Implemented (runtime Blocked) | Descriptor handoff per upstream contract |
| Connect/disconnect state machine + health check | Implemented (runtime Blocked) | "Connected" only after engine start + round-trip |
| Traffic statistics | Implemented | Engine outbound counters, 1 s poll |
| Latency testing | Implemented | Proxy round-trip via `measureOutboundDelay` |
| Subscription import/refresh/auto-refresh | Implemented | Keeps previous data on failure |
| QR import / export | Implemented | ZXing decode from image; encode for sharing |
| Export / backup / restore | Partial | JSON share + share-link/QR export; restore via Import (no encrypted backup file format) |
| Search / sort / filter / favourites / rename / duplicate | Implemented | |
| Fastest-server selection | Implemented | Uses last measured latency only |
| Automatic failover | Not implemented | Manual selection only; documented |
| DNS configuration | Implemented | Two modes, numeric resolvers |
| Routing rules | Implemented | Global, bypass-LAN, ad-block |
| Per-app VPN | Implemented (runtime Blocked) | OS-level allow/disallow lists |
| Local network bypass | Implemented | Route-level exclusion of private ranges |
| Reconnect policy | Implemented | On network change, toggleable |
| Always-on / lockdown compatibility | Partial | Compatible by design (no extra state assumed); kill-switch depends on the OS setting, documented in-app |
| IPv4/IPv6 handling | Implemented | IPv6 default on; leak warning when off |
| Secret protection | Implemented | Keystore AES-GCM store, redacted logs, masked URLs |
| Local data deletion | Implemented | Settings → Delete all local data |
| Optional public IP lookup | Implemented | Off by default, discloses api.ipify.org |
| Ads / analytics / accounts | Not implemented | By design |
| Release APKs (4 ABI + universal), signed | Implemented | See `CHECKSUMS.txt` |
| Universal + ABI-specific APKs | Implemented | `splits.abi` |
| SHA-256 checksums | Implemented | `CHECKSUMS.txt` |
| License notices | Implemented | `licenses/`, in-app About |
| Instrumented/device test run | Blocked | No device or emulator in the build environment |
