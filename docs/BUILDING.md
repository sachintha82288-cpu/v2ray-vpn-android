# Building V2RAY VPN

## Toolchain used for the published APKs

| Component | Version |
|---|---|
| JDK | Temurin 17.0.12+7 |
| Gradle | 8.7 (`--no-daemon`) |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 2.0.21 (Compose compiler plugin 2.0.21) |
| Compose BOM | 2024.09.02 |
| compileSdk / targetSdk | 34 |
| minSdk | 26 (Android 8.0) |
| Build tools | 34.0.0 |
| Engine AAR | AndroidLibXrayLite v26.11.9 (`libs/libv2ray.aar`) |

`gradle.properties` in this repository is tuned for a **2 GB / 2 vCPU** build host
(`-Xmx512m`, serial GC, in-process Kotlin compilation, no parallel execution). On a normal
workstation you can raise `org.gradle.jvmargs` to `-Xmx2g` and enable `org.gradle.parallel=true`.

## Steps

```bash
# 0. Fetch the pinned engine AAR (not stored in git) and verify its SHA-256
./scripts/fetch_engine.sh

export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew :app:testDebugUnitTest    # 43 JVM tests
./gradlew :app:assembleDebug        # debug: per-ABI + universal
./gradlew :app:assembleRelease      # release: per-ABI + universal
```

Outputs land in `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/`.
ABI splits are configured in `app/build.gradle.kts` (`splits { abi { … } }`), so each build
produces `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` and a universal APK.

## Signing

Create a keystore and an untracked `keystore.properties` at the repository root:

```bash
keytool -genkeypair -v -keystore release.keystore -alias v2rayvpn \
        -keyalg RSA -keysize 2048 -validity 10950
```

```properties
# keystore.properties  (never commit this file)
storeFile=release.keystore
storePassword=…
keyAlias=v2rayvpn
keyPassword=…
```

`app/build.gradle.kts` reads it if present and signs the release variant; otherwise the release
APKs are produced unsigned.

Verify a signature:

```bash
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --print-certs app-universal-release.apk
```

## Enabling R8

The published release build has `isMinifyEnabled = false` because the 2 GB build host could not
complete the shrinker. On a larger machine set both flags back to `true` in the `release` block;
`app/proguard-rules.pro` already keeps the gomobile classes (`go.**`, `libv2ray.**`).

## Replacing the engine

Drop a new `libv2ray.aar` into `libs/` (or point `scripts/fetch_engine.sh` at it), update `libs/README.md` and `CHECKSUMS.txt`, then re-run
the unit tests and a release build. No Java API in this app is used beyond the list in
`libs/README.md`; if an upstream release renames those methods the build fails loudly rather than
silently degrading.

## Notes for constrained hosts

* Each Gradle invocation forks a single-use daemon. Dexing transforms are cached per JAR in
  `~/.gradle/caches/transforms-*`, so an interrupted first build still makes progress when rerun.
* Packaging the 150 MB universal APK needs headroom for ~36 MB native buffers; if `packageRelease`
  fails with "Unable to allocate", lower `org.gradle.jvmargs` rather than raising it.
