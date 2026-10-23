# PC-ISSUE-078 — Do not approximate an unavailable exact Alarm

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-114`)

Status: **CORE SCHEDULER PASS / API 35 AND PHYSICAL-DEVICE PERMISSION BEHAVIOR NOT TESTED / OVERALL FREEZE HOLD**

## Finding

The contract requires an exact Alarm to remain explicitly blocked when exact scheduling is unavailable. It must not be silently rescheduled as approximate. The scheduler's denied-permission branch already marks the durable intent `BLOCKED` and returns before calling either AlarmManager scheduling method. This candidate lacked issue-level evidence of the fail-closed behavior; no production defect was found.

## Change and evidence

Added `ReminderValidationTest.exactAlarmPermissionDeniedBlocksWithoutApproximateFallback`. The test:

- starts with an Alarm fixture and explicitly selects `ReminderMethod.ALARM`, producing an `EXACT` trigger requirement;
- configures Robolectric `ShadowAlarmManager` to deny exact scheduling;
- invokes `ReminderScheduler.reconcile()`;
- verifies the durable desired state is `BLOCKED`, the stored hint identifies the exact-alarm permission, and the fake AlarmManager has no scheduled task.

The fixture explicitly chooses the reminder method because an event's `ALARM` kind defaults to the `MESSAGE` reminder method. The test-fixture corrections and their observed outputs are recorded in `.local/android-test/reports/freeze-20261024/pc-issue-078-test-fixture-triage-20261024.md`.

This is a Robolectric API 34 scheduler-boundary test with isolated SQLite. It does not verify the Android API 35 permission screen, system broadcasts after a permission change, physical-device exact-alarm behavior, or OEM scheduling.

## Verification

- Focused `:app:testDebugUnitTest --tests dev.powerfulcalendar.reminders.ReminderValidationTest.exactAlarmPermissionDeniedBlocksWithoutApproximateFallback --offline --rerun-tasks --console=plain`: **1/1 PASS**. Log `.local/android-test/reports/freeze-20261024/pc-issue-078-exact-alarm-denied-targeted-20261024.log`, SHA-256 `562D7C28338E9F935F0B72C915CB810EADEEC2D6841B8BF89A648A7082D083F4`; focused JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-078-targeted-ReminderValidationTest.xml`, SHA-256 `EDA0EB71B6C3F309792A45F9690C3915EE87BBE96E9BB81B06EF54A87133A567`.
- Full `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **430/430 tests across 56 suites; zero failures, errors, or skips; APK build successful; lint 0 errors / 58 warnings**. Log `.local/android-test/reports/freeze-20261024/pc-issue-078-full-gradle-20261024.log`, SHA-256 `D0426B8657887CF08A3DD5FB896CFBCEB9EA358A93410EB283D01B6E154C71F1`. Full `ReminderValidationTest` XML SHA-256 `049886363CFB9CAC1D6A1B7CEF046888318C92A457A4CC06E08AF436654E86A6`; lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256: `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`.

## Git

- Test-only commit `c2310688f92ef432721a93652753dc13fca9012d` (`test(提醒): 验证精确闹钟受阻时不降级`) is pushed to `origin/main`.

`REC-P0-114` is signed PASS for the tested core scheduler boundary. Android API 35 permission changes, real AlarmManager delivery, and physical/OEM behavior remain untested; overall freeze remains **HOLD**.
