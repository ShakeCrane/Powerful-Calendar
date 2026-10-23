# PC-ISSUE-080 — Submit and reconcile a pending reminder intent

Date: 2026-10-24

Candidate: `REC-P0-111` (reconstructed P0; historical mapping remains `UNMAPPED`)

Status: **PARTIAL — scheduler reconciliation PASS / asynchronous Android receiver path NOT TESTED / overall freeze HOLD**

## Finding

The candidate requires a persisted desired schedule to be reconciled to Android asynchronously. The scheduler's state transition and idempotency now have direct regression evidence. The Robolectric experiment did not establish that the Android `BootReceiver` asynchronous entry completes, so the candidate remains `NOT TESTED` in the acceptance ledger.

## Change and evidence

Added `ReminderValidationTest.pendingExactIntentIsSubmittedAndMarkedScheduledIdempotently`. The test explicitly configures `ReminderMethod.ALARM`, verifies an EXACT intent starts in `PENDING`, calls `ReminderScheduler.reconcile()`, and verifies the intent becomes `SCHEDULED` with one `ShadowAlarmManager` task. A second reconcile leaves the intent `SCHEDULED` and the task count at one.

This is Robolectric API 34 with isolated SQLite and `ShadowAlarmManager`. It verifies the scheduler operation directly. It does not verify the Android broadcast wrapper, `BootReceiver.goAsync()` completion, API 35, or delivery on a physical/OEM device. No production code changed.

## Verification

- Focused `:app:testDebugUnitTest --tests dev.powerfulcalendar.reminders.ReminderValidationTest.pendingExactIntentIsSubmittedAndMarkedScheduledIdempotently --offline --rerun-tasks --console=plain`: **1/1 PASS**. Log `.local/android-test/reports/freeze-20261024/pc-issue-080-schedule-reconcile-targeted-20261024.log`, SHA-256 `562D7C28338E9F935F0B72C915CB810EADEEC2D6841B8BF89A648A7082D083F4`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-080-targeted-ReminderValidationTest.xml`, SHA-256 `37C6158F5079C3AFD33B2D018C1DE250CBAEBAC655323FC3C21ED3AD0D9F7917`.
- Full `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **432/432 tests across 56 suites; zero failures, errors, or skips; APK build successful; lint 0 errors / 58 warnings**. Log `.local/android-test/reports/freeze-20261024/pc-issue-080-final-full-gradle-20261024.log`, SHA-256 `C688F166B7B9081E61FFCAEA5249C4558762E4FDC039763181C3C8F92BB02C3A`. Full `ReminderValidationTest` XML SHA-256 `C304D9C020C179223668EDADB3B08CE03B3F81B8E0E8E0C6C1CC2207CCAC7A58`; lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256: `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`.

## Asynchronous-path test attempt

The experiment tried explicit manifest broadcast delivery and dynamic receiver registration. The explicit manifest broadcast had no matching receiver wrapper in the Robolectric test environment. Dynamic registration reached `goAsync()`, but no scheduled task was observed within the test window. A direct `onReceive()` call returned no `PendingResult` and is not valid evidence for the Android framework path. The experimental source, final failing XML, and retained logs are under `.local/android-test/reports/freeze-20261024/pc-issue-080-*bootreceiver*` and `.local/android-test/reports/freeze-20261024/pc-issue-080-BootReceiverReconcileTest-experiment.kt`. During retries, the first explicit-broadcast log path was reused for the later compile attempt, so that first raw log was overwritten; the observed `Matching wrappers: []` and pending-state failure were captured in the run output. These attempts are not counted as passing tests and do not establish a production defect.

## Git and disposition

- Test-only commit `d4e989cb9233d9be11ad3380234e3374613c1226` (`test(提醒): 验证待调度意图重协调幂等`) is pushed to `origin/main`.
- `REC-P0-111` remains `NOT TESTED`: the direct scheduler behavior passes, but the candidate explicitly requires asynchronous Android reconciliation. A device/instrumentation or reliable framework-level receiver test is still needed.

Overall core freeze remains **HOLD**.
