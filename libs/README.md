# Engine artifact

> The 60 MB `libv2ray.aar` is **not** stored in git. It is attached to the
> [v1.0.0 release](https://github.com/sachintha82288-cpu/v2ray-vpn-android/releases/tag/v1.0.0)
> as `libv2ray.aar`. Fetch and verify it with `scripts/fetch_engine.sh` before building.


`libv2ray.aar` is the prebuilt Android binding of **Xray-core**, taken unmodified from:

- Project: `2dust/AndroidLibXrayLite`
- Release: `v26.11.9` (https://github.com/2dust/AndroidLibXrayLite/releases/tag/v26.11.9)
- Asset: `libv2ray.aar`
- Size: 60,437,926 bytes
- SHA-256: see `CHECKSUMS.txt` (`libv2ray.aar`)

Contents used by this app:

| Part | Purpose |
|---|---|
| `classes.jar` → `libv2ray.Libv2ray`, `libv2ray.CoreController`, `go.Seq` | gomobile bindings |
| `jni/arm64-v8a/libgojni.so`, `jni/armeabi-v7a/`, `jni/x86/`, `jni/x86_64/` | Xray-core engine per ABI |
| `assets/geoip.dat`, `assets/geosite.dat`, `assets/geoip-only-cn-private.dat` | routing data used by `geoip:private` and `geosite:category-ads-all` |

Engine identity inside this AAR (from the project's `go.mod` at that tag):

- Library version: `Lib v40`
- Xray-core: `v1.260327.1-0.20261109052709-676eaf66f8f0` (commit `676eaf66f8f0`)

APIs used (all verified against the binding's generated Java sources):

```java
Libv2ray.initCoreEnv(String envPath, String key);
Libv2ray.checkVersionX();
Libv2ray.measureOutboundDelay(String configJson, String url);
Libv2ray.newCoreController(CoreCallbackHandler handler);
CoreController.startLoop(String configContent, int tunFd);
CoreController.stopLoop();
CoreController.measureDelay(String url);
CoreController.queryAllOutboundTrafficStats();
```

The Android TUN descriptor is passed to `startLoop`; Xray-core reads it from the
`xray.tun.fd` environment variable, duplicates it (`F_DUPFD_CLOEXEC`) and sets it non-blocking, so
the VPN service keeps ownership of its own descriptor.

License: LGPL-3.0 (`../licenses/LGPL-3.0-AndroidLibXrayLite.txt`). The AAR is redistributed
unmodified; its corresponding source is the upstream repository at the tag above.
