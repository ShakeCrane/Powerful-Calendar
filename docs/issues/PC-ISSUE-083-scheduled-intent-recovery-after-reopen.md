# PC-ISSUE-083 — Re-arm a durable scheduled intent after store reopen

Date: 2026-10-24

Candidate: `REC-P0-117` (reconstructed P0; historical mapping remains `UNMAPPED`)

Status: **CORE REARM PASS / REC-P0-117 NOT TESTED / overall freeze HOLD**

## Finding

The durable schedule table keeps intents in `SCHEDULED` state so the scheduler can reconstruct Android alarm registrations after process or device state loss. Before this issue there was no focused regression proving that a `SCHEDULED` row survives SQLite reopen and is re-armed when its AlarmManager registration is gone.

## Change and evidence

Added `ReminderValidationTest.scheduledIntentIsRearmedAfterStoreReopen`. The test commits an exact Alarm reminder, reconciles it to `SCHEDULED`, closes the SQLite store, removes the ShadowAlarmManager registration to represent system loss, reopens the same database, and calls reconcile again. It asserts that the durable intent is unchanged and exactly one alarm registration is restored.

This is Robolectric API 34 core evidence. Production code did not change. The test calls `ReminderScheduler.reconcile()` directly; it does not exercise the asynchronous `BootReceiver.goAsync()` path or a real Android reboot.

Two initial test-fixture attempts failed because `scheduledAlarms` returns a snapshot list and `ShadowAlarmManager.reset()` does not clear an AlarmManager instance's queue. These were harness setup failures, not product failures. Their logs/XML are retained as r1 and r2 evidence below. The passing r3 uses `AlarmManager.cancel()` on each scheduled PendingIntent before reopening SQLite.

## Verification

- Focused `:app:testDebugUnitTest --tests dev.powerfulcalendar.reminders.ReminderValidationTest.scheduledIntentIsRearmedAfterStoreReopen --offline --rerun-tasks --console=plain`: **1/1 PASS**. Log SHA-256 `8FDDE014DB59FE27D8C3DC60A5B4EF5321BC67DC57D3C96F48D3D62715922387`; JUnit XML SHA-256 `04D92B477C0D0AF0989344555F1784B193A7444FCA0F03EF62E6B7AB31A1212D`.
- Initial test-fixture attempts: r1 log/XML SHA-256 `5B46565B2AA008BE410013B50B802DB040D4AD8ABCEB92DE28F791590E77D87A` / `FE439B8D0D440259D5FCF4C10337DB27A90D547B264524377FFEC53D322D96A1`; r2 log/XML SHA-256 `7E06E10DB5786A1AE60C17E6A4781443F9D41222315D6A2BAFFC6C4A3505E736` / `5C953C63A6100EE539CBC6DD1F41DA4302CB9282010CC499F941C2391D68D478`. Both failed at the incorrect simulated-alarm-loss setup and are not counted as product failures or passes.
- Full `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **435/435 tests across 56 suites; zero failures, errors, or skips; APK build successful; lint 0 errors / 58 warnings**. Full log SHA-256 `2C7C594AF569CBEA175A562A7DBA11859CA30AB3C9DBA81ADBE25854594F2AC0`; ReminderValidationTest XML SHA-256 `EE15933C7B8B2567C364DA78917EA56AE93A5D379F1788E48BB23A0562AD982F`; lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256: `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`.

## Git and disposition

- Test-only commit `1f82667830fd038d7b2ae37e277516e639edc296` (`test(提醒): 验证重开后重建已调度闹钟`) is pushed to `origin/main`.
- The core durable-rearm subpath passes. `REC-P0-117` remains **NOT TESTED** because the system receiver trigger, asynchronous completion, app-upgrade/permission/timezone broadcasts, and actual API 35 or physical-device reboot recovery were not exercised. No claim is made for OEM behavior; the overall core freeze remains **HOLD**.
