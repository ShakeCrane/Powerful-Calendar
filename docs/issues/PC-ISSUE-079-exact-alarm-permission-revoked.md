# PC-ISSUE-079 — Reconcile an exact Alarm after permission revocation

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-113`)

Status: **CORE RECONCILIATION PASS / ANDROID PERMISSION BROADCAST, API 35, AND PHYSICAL DEVICE NOT TESTED / OVERALL FREEZE HOLD**

## Finding

The product requires a previously scheduled exact reminder to become visibly blocked when exact-alarm access is revoked and the app next reconciles its desired schedule. `PowerStore.dueIntents()` includes `SCHEDULED` entries, and `ReminderScheduler.reconcile()` rechecks exact permission before resubmitting them. This path lacked issue-level evidence; no production defect was found.

## Change and evidence

Added `ReminderValidationTest.revokingExactAlarmPermissionBlocksScheduledIntentOnNextReconcile`. The test creates an event with `ReminderMethod.ALARM`, enables exact scheduling in Robolectric, and reconciles once. It verifies the intent becomes `SCHEDULED` and one task is submitted. It then disables exact scheduling and reconciles again, verifying the durable intent becomes `BLOCKED` with an exact-permission hint.

The test uses Robolectric API 34, an isolated SQLite database, and `ShadowAlarmManager`. It does not send Android's permission-state broadcast, exercise `BootReceiver.onReceive()`/`goAsync()`, verify OS cancellation of previously set alarms, or test physical/OEM delivery.

## Verification

- Focused `:app:testDebugUnitTest --tests dev.powerfulcalendar.reminders.ReminderValidationTest.revokingExactAlarmPermissionBlocksScheduledIntentOnNextReconcile --offline --rerun-tasks --console=plain`: **1/1 PASS**. Log `.local/android-test/reports/freeze-20261024/pc-issue-079-exact-permission-revoked-targeted-20261024.log`, SHA-256 `F64F2CF0BD4AF7D0AC028F060814B587F93D1F8BE6060DBC0FE05B522A56C299`; focused JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-079-targeted-ReminderValidationTest.xml`, SHA-256 `1D4D516A48ACD1BBEF05C16B63DAEFA328FB892E33B8FC850E307180AD0930F1`.
- Full `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **431/431 tests across 56 suites; zero failures, errors, or skips; APK build successful; lint 0 errors / 58 warnings**. Log `.local/android-test/reports/freeze-20261024/pc-issue-079-full-gradle-20261024.log`, SHA-256 `EF9B3C190BEC5C7D0D1ED94B422893F4E44DB456E6A0CF4936FAF13ED5CC6A91`. Full `ReminderValidationTest` XML SHA-256 `7C27195F2F978B973401F3A4D7DC63376BE19336F7DA84B9542A385EB675B12D`; lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256: `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`.

## Git

- Test-only commit `a45188fd107b05cec1467851acf647422c8230f6` (`test(提醒): 覆盖撤销精确闹钟权限后的重协调`) is pushed to `origin/main`.

`REC-P0-113` is signed PASS for the tested core reconciliation state transition. Android permission-state broadcasts, `BootReceiver` framework lifecycle, OS cancellation, API 35 behavior, and physical/OEM scheduling remain untested; overall freeze remains **HOLD**.
