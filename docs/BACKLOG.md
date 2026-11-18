# Backlog

Ordered work queue for the daily maintenance run. One item per day, smallest first; an item is
only moved to "done" when it is implemented, unit-tested and shipped in a release.

Rules for every daily run:
1. Build and test first. A red build is the day's work — fix it before anything else.
2. Never claim a device-level behaviour works; there is no device in the build pipeline.
3. Bump the patch version, update `CHANGELOG.md` and `CHECKSUMS.txt`, publish only if tests pass.
4. Keep `docs/TEST_REPORT.md` honest: new code that cannot be executed on hardware stays BLOCKED.

## Ready

1. **Encrypted backup / restore file.** Export profiles + subscriptions to a single
   passphrase-encrypted file (PBKDF2 → AES-GCM) and import it back. Closes the last "Partial" row
   in `docs/PROJECT_STATUS.md`.
2. **Automatic failover.** After N consecutive health-check failures, move to the next profile by
   last measured latency, with a hard retry cap and a visible log line.
3. **Notification actions.** Add "Reconnect" and "Copy last error" to the ongoing notification.
4. **DNS presets.** One-tap `1.1.1.1`, `8.8.8.8`, `9.9.9.9`, plus a DoH/DoT upstream field for the
   Xray DNS module (`https://dns.google/dns-query`).
5. **Route presets.** Toggleable `geosite` categories (ads, private, streaming) instead of a single
   ad-block switch, with the exact tag shown in the UI.
6. **Accessibility pass.** TalkBack labels on the connect control and every icon button, contrast
   check of the glass surfaces, and a font-scale test at 200 %.
7. **Baseline profile + R8.** Re-enable shrinking and ship a baseline profile once a host with
   ≥ 4 GB RAM is available; verify `apksigner` and startup afterwards.
8. **Import error catalogue.** Turn the most common parse failures into actionable messages with
   the offending field name.

## Later

9. **Per-UID routing** via `CoreController.registerProcessFinder` for apps that must use the proxy
   without the OS-level VPN interface.
10. **Camera QR scan** (CameraX) as an alternative to picking a QR image, behind an explicit
    permission prompt.
11. **Home screen widget** for connect/disconnect.
12. **Instrumented test suite** to run on a real device or a KVM-capable CI runner — the only way
    to close the BLOCKED gates in `docs/TEST_REPORT.md`.
