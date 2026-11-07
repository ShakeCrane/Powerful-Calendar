# PowerCal Functional Preview — 2026-11-07

## Artifact

- APK: PowerCal-Functional-Preview-2026-11-07-742ffbbd.apk
- SHA-256: 1DA53F916AE924C1583A2472CB10820754A7E4BD21E6EEBE433642505940D54A
- Size: 67,984,965 bytes
- Build checkout: 742ffbbdcd98e3763fd59c1e917088bae4f718e6
- App-code commit: 5a267814ca83a201f4d2c3a1c3df357c1d35c1a0 (the later commit only adds documentation)
- Application ID: dev.powerfulcalendar
- Version: 0.9.5-rc.1 (versionCode 905), Debug

The package contains the current single default Skin, powercal-clear-default.pcskin. It is an interim Functional Preview; its appearance is not signed off as the final Native 0.9.5 visual migration. Product Cutover remains HOLD.

## Package verification

- Gradle command: .\gradlew.bat :app:assembleDebug --offline --no-daemon --max-workers=1 --no-parallel --console=plain — PASS (UP-TO-DATE on this documentation-only checkout).
- Official builder verification: python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk — PASS.
- Exactly one .pcskin is present in the APK.
- Default package ID: powercal-clear-default; 10 entries / 422210 bytes.
- Source and APK asset SHA-256: 13a6efc6af0e68a8d91c7786ac3bd0264dea630fe4880feefaa39251be869ffa.

## Verification status

The app-code commit 5a267814ca83a201f4d2c3a1c3df357c1d35c1a0 passed the serial full Gradle gate: 989 JVM tests, zero failures/errors/skips, both source boundaries, APK assembly, lint and AndroidTest Kotlin compilation. Skin Studio Python suite passed 90/90. The current APK hash was used by the focused API 35 WebView Draft Deck empty/AI-locked test (1/1); the frozen Native reference test also passed 1/1. Full main-flow smoke was not repeated for this refreshed package. Browser E2E, TalkBack traversal, physical-device/OEM behavior, and live AI provider connectivity are not covered here.

## Install

~~~powershell
adb -s <isolated-device-serial> install -r .\PowerCal-Functional-Preview-2026-11-07-742ffbbd.apk
adb -s <isolated-device-serial> shell monkey -p dev.powerfulcalendar 1
~~~

This is a Debug-signed internal preview, not a public release. Android may refuse in-place installation over an app signed by another key; do not clear a user's app data to work around that restriction.
