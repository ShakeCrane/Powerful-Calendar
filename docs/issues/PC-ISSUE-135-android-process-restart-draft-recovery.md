# PC-ISSUE-135 — Android process restart draft persistence

Date: 2026-10-24 | Priority: P2 | Status: **PASS within the tested persistence scope**

## Requirement source and scope

Reconstructed from the core freeze handoff's process monitoring and recovery requirement and the existing AI pending-draft persistence contract. This is a newly assigned `REC-P2-001` case; it is not claimed to be one of the unavailable historical 209 IDs. It verifies that a pending AI-origin draft survives repeated Android app-process death and restart without becoming a formal event.

The run uses a synthetic draft with `source="ai"`. It does not call an AI provider and does not establish recovery of a live chat transcript, Activity state, crash/low-memory behavior, or physical-device/OEM lifecycle behavior. No UI or physical device was used.

## Test method

- Added `PowerStoreProcessRestartTest`, invoked once per process start with a test-only uniquely named database.
- On API 35 emulator `emulator-5558`, the host invoked AndroidJUnitRunner 13 times, force-stopping both the target and instrumentation packages between runs (12 stops).
- Each invocation checked that the same single pending CREATE draft remained publishable and retained its draft ID, event sequence and queue order. It also checked that there were no formal events, commit-history rows, or reminder intents.
- The last invocation removed only the uniquely generated synthetic test database after all assertions passed; on failure, the test preserves it for diagnosis.
- In a second fresh API 35 AVD on C:, the same test ran cycle 0, issued `adb reboot`, waited for `sys.boot_completed`, and verified Android's boot count advanced from 1 to 2. Cycle 1 then reopened the same synthetic database and checked the original draft identity. Cycle 12 performed the final assertions and removed only that synthetic database.

## Result

**PASS: 13/13 process-restart invocations**, with 13 distinct process IDs. Every cycle reported `PENDING`; the stable identity was draft ID 1, event sequence 1, queue order 1. Formal events: 0; commit-history rows: 0; reminder intent: none. The synthetic database was absent after the successful final cycle.

**PASS: one Android VM system reboot.** The second run observed boot count 1 before and 2 after `adb reboot`; PowerCal remained installed, and the pending draft reopened with the same ID, event sequence, queue order and status. Its three instrumentation invocations were the pre-reboot write, post-reboot verification, and final cleanup assertion. This is an AVD reboot result, not physical-device/OEM reboot coverage.

The emulator initially had no PowerCal package, and the run used its newly initialized user-data image. A post-run, read-only filename/size inventory observed `powercal-v2.db` and a zero-byte journal in the app sandbox. The instrumentation test opened only its explicitly named synthetic database; database content was not read, and the default-named files were left in place. Inventory evidence is recorded separately below.

An earlier exploratory attempt on an already-running emulator was excluded from signoff after a default-named database was observed there. That database was not read or removed, and no further test was run on that emulator. Only the two fresh API 35 AVD runs above support this result.

## Regression and evidence

Full offline Gradle command:

```text
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

Result: **469/469 JVM tests across 58 suites; 0 failures, 0 errors, 0 skipped.** Source-boundary verification, Debug APK assembly, AndroidTest APK assembly, and lint succeeded. Lint reported 0 errors and 58 warnings. This Gradle run compiles/packages the Android instrumentation test; the 13 emulator invocations are reported separately.

| Evidence | SHA-256 |
|---|---|
| Instrumentation run log `.local/android-test/reports/freeze-20261024/pc-issue-135-process-restarts-isolated-r5.log` | `67507A6BB830D72B4A4930A8B4A9F5F4F5C77138D39BEC3E36CEF11B85F3C6DE` |
| Instrumentation summary `.local/android-test/reports/freeze-20261024/pc-issue-135-process-restarts-isolated-r5.json` | `2C7771DA82228515788C8ED28EE513FA49E7503A8559A422DC05B6F58C5D839D` |
| Read-only database metadata `.local/android-test/reports/freeze-20261024/pc-issue-135-isolated-r5-database-metadata.json` | `4BFBBC3B9CE6F6FF5E8A4F5231A75FB59F8E179ED6BA4B473CC1912C604CFE0A` |
| System reboot run `.local/android-test/reports/freeze-20261024/pc-issue-135-system-reboot-r2.log` | `AA86B2BEDF5E1936A852F1967B32B8B8EAAA25B9F1A2D2540674D88A13E076B9` |
| System reboot summary `.local/android-test/reports/freeze-20261024/pc-issue-135-system-reboot-r2.json` | `A72CDB86C4F46F0213BD9D0B6C37385F8A2C86D5BEA80E604DBB159F43549E14` |
| Post-reboot metadata-only inventory `.local/android-test/reports/freeze-20261024/pc-issue-135-system-reboot-r2-database-metadata-v2.json` | `066D3B0F21854B5C72A4F6B804D184D126EFC2E9D18E80600F5F1B1DC326F757` |
| Full Gradle log `.local/android-test/reports/freeze-20261024/pc-issue-135-full-gradle-r1.log` | `A516C5683B8151546A57ABE82E2B70A4FFFB4E45F03F09F32E6BFF08B29F48B4` |
| Lint XML | `2BEABC23C862180FE0301AC2F3A3FABDBC432D106CFDDFBC0E1188879BCD1B47` |
| Debug APK | `35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4` |
| AndroidTest APK | `70933105F35F5486F316148DFDA1C86A6FA7AE93CFC5DFFE78BEF5EFF9BCC92D` |

The build/test baseline before this addition was Git SHA `2b583e03aa622901b61f4a3e6f81abdedb100e18`. The test source is `app/src/androidTest/java/dev/powerfulcalendar/core/PowerStoreProcessRestartTest.kt`; its host runner is `.local/android-test/reports/freeze-20261024/run-pc-issue-135-process-restarts.ps1`.

## Acceptance boundary

This result signs persistence of one synthetic AI-origin pending draft across 12 host-driven process stops and one Android VM reboot on API 35 AVDs. It does not sign the real-device lifecycle matrix, crash/low-memory recovery, or live AI/chat-session recovery. The overall core freeze remains **HOLD**. The acceptance CSV still needs the user-directed risk-based P0/P1 reclassification; its historical row count and old 126/82 split are not treated as a target or as recovered historical IDs.
