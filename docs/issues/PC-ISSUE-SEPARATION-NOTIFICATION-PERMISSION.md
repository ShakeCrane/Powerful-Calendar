# PC-ISSUE-SEPARATION-NOTIFICATION-PERMISSION

Date: 2026-10-30
Area: Skin-First separation / reconstructed `S0-ST-18`; related Settings route slice `S0-ST-01`
State: **IMPLEMENTED; local Host, JVM, Chrome, and Studio evidence PASS; Android WebView/OS dialog NOT TESTED; parity PARTIAL / HOLD**

## Expected behavior and prior result

The Native Tools page already owned notification permission status and the Android permission launcher. Clear's `settings` action went directly to the protected Native Tools page, however, so Clear had neither a Settings surface nor a typed notification permission action/state projection. A Clear user could only leave the skin to reach the existing Host control.

Expected: Clear can show the Host's notification permission state and request permission through the Android Host, while denied notifications continue to be recorded as `BLOCKED`. The Native Tools recovery page remains reachable.

## Root cause

- `Page` had no Clear Settings state; the Web `settings` route assigned `Page.TOOLS`, which forced the full Native surface.
- `WebSkinAction`/the closed Bridge decoder had no notification permission command or status projection.
- Clear's source had no Settings page. Skin Studio's Preview Mock also rejected the route as unknown.

## Changes

- Added a Clear Settings page for reminder permission status, a Host-routed request button, and an explicit route to Native Tools/recovery.
- Added closed-schema `requestNotificationPermission`. The Host accepts it only on the Settings page; API 33+ uses the existing `POST_NOTIFICATIONS` Activity Result launcher, and older Android versions open the existing app notification settings screen.
- Projected only `required` and `allowed` on the Settings page, using `ReminderScheduler.notificationsAllowed`. The permission result refreshes the Web projection. Reminder scheduling, delivery, and `BLOCKED` semantics were not changed.
- Added a Settings Back route to Timeline. Native Tools remains the protected Native fallback/recovery surface.
- Updated the Skin Studio Preview Mock to display a simulated denied state and log permission/native recovery actions without launching Android UI or changing real permission state.
- Rebuilt the single canonical Clear package from source.

## Verification evidence

### Android Host and JVM

Targeted command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.powerfulcalendar.ui.v2.WebSkinBridgeTest --tests dev.powerfulcalendar.ui.v2.WebSkinNotificationPermissionIntegrationTest --offline --rerun-tasks --no-daemon --max-workers=1 --console=plain
```

Result: **5/5 tests PASS**. The tests cover exact Bridge payload validation, API 32 versus API 33+ request routing, required/allowed projection, the actual `PowerCalApp` Settings-to-Native-Tools route, the real Robolectric `POST_NOTIFICATIONS` permission request, and unchanged Store events, pending count, identity count, and sequence watermark.

- Final targeted log: `.local/android-test/reports/separation-s0-timeline-drag/notification-permission-cdp/gradle-targeted-final3.log`
- Targeted log SHA-256: `376D5544CF785349ED8477653E332C823E001BC08DB2BB9B42F2F1912CB0B354`

Full offline gate:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --max-workers=1 --console=plain
```

Result: **BUILD SUCCESSFUL; 661/661 JVM tests across 87 suites, zero failures/errors/skips; 88/88 tasks passed; lint 0 errors / 52 warnings.** The AndroidTest APK was built but instrumentation was not executed.

- Full log: `.local/android-test/reports/separation-s0-timeline-drag/notification-permission-cdp/gradle-full-final3.log`
- Full log SHA-256: `B7B9418ACDDAD5F48D4E8AD5F86D9954027B1F6F5E004438AB5433A7C5D7C895`
- Lint XML SHA-256: `1231376985EC594B291F0238A65E1D468E947908D9923F361782223397E3492B`
- Debug APK: 66,334,225 bytes; SHA-256 `D6B8CC95C8F01FCBA95E4F6FF37B0E7882232F934297440295149C54553738C7`.
- AndroidTest APK: 1,337,236 bytes; SHA-256 `C23375D347A00880431B9A2807E0BFAB005973D690A8FF8BECC4DF94B6122931`.

### Clear and Skin Studio browser behavior

- Canonical Clear source in Chrome 156 Headless/CDP: Settings route, permission request action, and Native recovery route all matched the Bridge contract. The denied, granted, and pre-33 status text rendered as expected. Result: `PASS`.
- Screenshot: `.local/android-test/reports/separation-s0-timeline-drag/notification-permission-cdp/settings-2026-10-29T16-44-33-028Z.png`; SHA-256: `C26234A6E54D47DDCE83964E6D2D7CA0DC819D34ABAF532F761D12C8F7F70728`.
- Structured browser result: `.local/android-test/reports/separation-s0-timeline-drag/notification-permission-cdp/result-2026-10-29T16-44-33-028Z.json`; SHA-256: `B3E67737E7D6BB2B3629A36BBEAE1573F2421B10905B12230DF17DCA43552C6B`.
- Chrome/CDP log SHA-256: `C63B5693D837F7B5F5C4C0C90AD60D6E96E00F3585AA25BC097EA0491F7F4040`.
- Chrome/CDP Skin Studio Preview loaded the canonical 9-file package; the Settings page rendered a denied state; permission and Native recovery requests were logged as simulations and did not invoke Android or change system state. Result: `PASS`; JSON: `.local/android-test/reports/separation-s0-timeline-drag/notification-permission-cdp/studio-mock-2026-10-29T16-45-36-479Z.json`.
- Studio Mock E2E log SHA-256: `3C704A15F91BEF9C761F5F95B315F88B9E37CD1706111C0515E7C464D71286B1`; result JSON SHA-256: `33A2205E76FDC3D6ADB747670A2E314DB349666BF286D65B2E196470D0CB685D`.
- Skin Studio Python suite: **61/61 PASS**; final log: `.local/android-test/reports/separation-s0-timeline-drag/notification-permission-cdp/skin-studio-unittest-final2.log`; SHA-256: `840CA5D41F8D053AC14CC0834EB32C37DD44FC7DE650E9ACB82884E41065EEBB`.
- `node --check` passed for Clear `app.js`, Studio `v3-studio.js`, and both new CDP scripts. `git diff --check` passed.
- `build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk` passed. Canonical package: 9 files / 392,170 bytes; source, checked-in package, and APK asset SHA-256: `af640cbca89a870c2d1ea733eb562ed35e6082b75b08fe7aa37afa9d4ff6f98a`.

Initial harness attempts stopped before product assertions because a fresh shell lacked `ANDROID_HOME`, a Robolectric fixture used the wrong `shadowOf` type, and Chrome's `DevToolsActivePort` was briefly locked during startup. These setup issues were corrected; final targeted, full Gradle, and browser runs above all passed. The first targeted log and retry artifacts remain under the same ignored report directory.

### Android WebView and OS permission prompt

`E:\android-sdk\platform-tools\adb.exe devices -l` returned an empty device list, and `E:\android-sdk\emulator\emulator.exe` is absent. Therefore Android WebView interaction and the Android runtime permission dialog/settings screen were **NOT TESTED**. The `assembleDebugAndroidTest` task only proves the test APK builds; it is not an instrumentation result. Physical/OEM behavior remains for the user to test.

## Matrix sign-off

- `S0-ST-18`: Host state/action and local behavior **PASS**; Android WebView/OS dialog/device parity **NOT TESTED**; overall row **PARTIAL**.
- `S0-ST-01`: Clear Settings and explicit Native Tools recovery routes are implemented and exercised in Host/Preview; complete Native open/back/navigation parity **NOT TESTED**; overall row **PARTIAL**.
- Other `S0-ST-*` rows remain unchanged. This issue does not close S4, S0, or separation, and does not authorize skin cutover.

## Git delivery

Implementation, regression tests, matrix entry, and this Issue report were committed and pushed as `68257eb97ebd1064c4176a3742d5dc91ecf73eeb` (`feat(皮肤分离): 增加Clear通知权限入口`) to `origin/codex/separation-s0-timeline-drag`.
