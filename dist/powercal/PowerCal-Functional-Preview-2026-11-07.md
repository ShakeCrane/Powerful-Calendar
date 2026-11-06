# PowerCal Functional Preview — 2026-11-07

**Status: DELIVERED for isolated functional preview.** This is an installable internal Debug APK. It is not a public release and does not claim full Native 0.9.5 visual parity.

## Artifact

- APK: `dist/powercal/PowerCal-Functional-Preview-2026-11-07-05514a12.apk`
- SHA-256: `CF310827CA4C3A7989DB79A5D35F6F2755F2AD01E7601D28B40CA271C74492CB`
- Size: `67,983,743` bytes
- Build type: Debug
- Build code commit: `05514a12c3cf6c0b62bfe2daad04fbbdf433097f`
- Application ID: `dev.powerfulcalendar`
- Version: `0.9.5-rc.1`, version code `905`
- Factory Skin: `powercal-clear-default.pcskin`, ID `powercal-clear-default`
- Factory Skin SHA-256: `78C13F92BB2878A559CCD57FE64FEA7F22D2137460620139C69155C7CBF5BB7E`
- Official builder verification: PASS; ten package files; source package and embedded APK asset bytes match; APK contains one `.pcskin`.

## Installation

Install only on an isolated QA device. This Debug APK uses the project debug signing identity and the normal application ID; Android may reject an update if a different signing identity is already installed.

```powershell
adb -s <isolated-device-serial> install -r .\dist\powercal\PowerCal-Functional-Preview-2026-11-07-05514a12.apk
adb -s <isolated-device-serial> shell monkey -p dev.powerfulcalendar 1
```

The verified device for this build was `PowerCal_Wave2_QA35_20261104` / `emulator-5556`, Android API 35, 1080×2400 at 420 dpi, System WebView `124.0.6367.219`. Installation used `adb install -r`; no app data or AVD was cleared.

## Verified on this build

- Serial Gradle gate: `BUILD SUCCESSFUL`; 987 JVM tests, zero failures/errors/skips; Skin and Native source boundaries, Debug assemble, Lint, and AndroidTest Kotlin compile passed.
- Skin Studio Python suite: 90/90; binding catalog tests: 9/9; related Node syntax and Preview Mock browser E2E passed.
- The installed APK launched into the default Web Timeline. Real device taps opened Month, selected a date, opened Settings, and returned to Timeline.
- Android instrumentation against the packaged Clear Web Runtime and isolated test Stores: all-four-kind create flow 1/1; all-four-kind edit flow 1/1; recurrence scopes 1/1 on the final run; 251-item Memo paging/completion flow 1/1.
- Process recovery: prepare and restore phases each passed 1/1 after an explicit force-stop. The isolated fixture verified a formal Memo/completion snapshot and recovered a pending dirty editor draft. Its UUID-scoped database, marker and temporary preference changes were cleaned/restored by the test.
- Force-stop followed by a fresh MainActivity launch returned to the Timeline. The existing QA application data was preserved.
- Device screenshots and individual logs are under `.local/android-test/reports/functional-preview-20261107/`.

## Limits and unresolved checks

- AI page entry was observed in the installed application. The deterministic chat-send instrumentation failed twice at its `#chatSend` trusted-pointerdown assertion; no chat-send PASS is claimed for this smoke. No live provider or credential was used.
- The first recurrence instrumentation attempt also missed a trusted tap on the already-selected month date; after relaunch and a successful manual Month/date touch, one bounded rerun passed the complete scope test. Both attempt logs are retained.
- Import/export, permission changes, OEM reminder delivery, TalkBack traversal and physical-device behavior were not retested in this preview run.
- The delivered skin is still the current Clear-based Web v3 Skin with verified Native-aligned slices. The Owner’s frozen Native 0.9.5-rc.1 interface remains the visual/interaction target; full-page parity is incomplete.
- Product Cutover: `HOLD`.

The Studio project remains `tools/skin-studio/default-clear/`; launch from the repository root with `python tools/skin-studio/skin_studio.py serve --port 8765`.
