# PC-ISSUE-057 — Reminder firing must not mutate event lifecycle

Date: 2026-10-23

Severity: P0 candidate

Status: **RECEIVER/SQLITE STATE-SEPARATION REGRESSION PASS / SYSTEM DELIVERY NOT TESTED**

## Scope and finding

`REC-P0-019` requires reminder execution state to remain separate from the formal event lifecycle. The current `ReminderReceiver` path already records a fired execution without completing or revising the event. The gap was direct current-baseline evidence mapped to this reconstructed candidate; no production-code defect was observed, so this sign-off makes no source or test change.

The test dispatches a synthetic `ACTION_FIRE` to `ReminderReceiver.handle` with an isolated SQLite-backed `PowerStore`. It verifies the execution is recorded as `FIRED`, the one-shot schedule intent is removed, and the same formal event remains present with its revision unchanged and `completedKeys` empty. It does not verify Android `AlarmManager` delivery, audible output, lock-screen behavior, notification interactions, or a physical device.

## Candidate mapping

- `REC-P0-019`: `ReminderCompletionIsolationTest.firedAlarmRecordsExecutionWithoutCompletingTrackedEvent` verifies the receiver's reminder execution state does not complete, delete, or revise the formal tracked event. The reconstructed candidate remains `UNMAPPED` to the original checklist.

## Verification evidence

Environment: Robolectric API 34, isolated SQLite database; no Android device.

- Targeted command: `:app:testDebugUnitTest --tests "dev.powerfulcalendar.reminders.ReminderCompletionIsolationTest.firedAlarmRecordsExecutionWithoutCompletingTrackedEvent" --offline --rerun-tasks --console=plain` — **1/1 PASS**, zero failures/errors/skips.
- Targeted log `.local/android-test/reports/freeze-20261023/pc-issue-057-ringing-lifecycle-targeted-20261023.log`, SHA-256 `70E056E358A19C4A0553BE8C5A8F9EB1931FC579D893AE37D4D68AC2F9F1575D`.
- Preserved JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-057-targeted-junit/TEST-dev.powerfulcalendar.reminders.ReminderCompletionIsolationTest.xml`, SHA-256 `48B0A577EDF868BE040C3B132B9AAE2AFBA679DEA61DE0B4293A1932C690B429`; one test, zero failures/errors/skips.
- Full Gradle regression on the same code/test baseline `92dd9016d7907b1e57cd4a27920332fa72e572fc`: **410/410** across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. This full run is the Issue 056 run because no source or test files changed afterward. Log SHA-256 `92E006A3038FDD32D22A806CC2D990CE6255875450256D3DAB51A23B8EE66191`.
- Debug APK SHA-256 `80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`.

## Candidate sign-off and limits

`REC-P0-019` is PASS for the tested receiver/SQLite state separation. Actual Android alarm delivery and ringing, notification behavior, and historical checklist mapping remain unverified. Overall core freeze remains **HOLD**.

## Git

This sign-off changes only the candidate ledger and freeze documentation; production and test sources are unchanged. The evidence references code/test baseline `92dd9016d7907b1e57cd4a27920332fa72e572fc`. Documentation will be committed and pushed separately using a Chinese conventional commit.
