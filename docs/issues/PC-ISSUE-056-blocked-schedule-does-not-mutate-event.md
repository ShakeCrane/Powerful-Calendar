# PC-ISSUE-056 — A blocked schedule must not complete or delete its event

Date: 2026-10-23

Severity: P0 candidate

Status: **POWERSTORE STATE-SEPARATION REGRESSION PASS / OS PERMISSION FLOW NOT TESTED**

## Scope and finding

`REC-P0-018` requires a scheduling intent in `BLOCKED` state to remain independent from the formal event's existence and completion state. The prior gap was lack of a direct persistence regression. The new SQLite test marks an existing committed Alarm intent `BLOCKED`, verifies the event remains present with unchanged formal snapshot and no completed occurrence keys, then closes and reopens the database and verifies those states remain separate. The test passed. No production-code defect was observed, so only a regression test was added.

The test writes the durable blocked scheduling state through `PowerStore.markIntent`; it does not exercise Android's exact-alarm permission denial, `ReminderScheduler.reconcile`, notification guidance, or a physical device.

## Candidate mapping

- `REC-P0-018`: `PowerStoreTest.blockedScheduleStateDoesNotCompleteOrDeleteItsFormalEventAcrossRestart` verifies `DesiredSchedule.BLOCKED` and its permission hint persist while the formal event remains at its committed revision, remains queryable, stays incomplete, and retains its commit history across SQLite reopen. The candidate remains `UNMAPPED` to the original checklist.

## Verification evidence

Environment: Robolectric API 34, isolated PowerStore SQLite database; no Android device.

- Targeted command: `:app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerStoreTest.blockedScheduleStateDoesNotCompleteOrDeleteItsFormalEventAcrossRestart" --offline --rerun-tasks --console=plain` — **1/1 PASS**, zero failures/errors/skips.
- Targeted log `.local/android-test/reports/freeze-20261023/pc-issue-056-blocked-schedule-state-targeted-20261023.log`, SHA-256 `CCE3127D5D083C4B3BA564D24BFF4E9E42A34EA85C7EBCDA33A7FC5539F6160F`.
- Targeted JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-056-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `BA4E34678FC7B0E4B63F03A6567BC9AD994EB1ED785F79FE63C43E582BF9E1F5`; 1 test, 0 failures/errors/skips.
- Full Gradle command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 410/410 across 55 JUnit suites, 0 failures/errors/skips, lint 0 errors / 55 warnings. This includes protected untracked `DraftDeckFeedbackTest.kt` (409 tracked tests + 1 protected untracked).
- Full log `.local/android-test/reports/freeze-20261023/pc-issue-056-full-gradle-20261023.log`, SHA-256 `92E006A3038FDD32D22A806CC2D990CE6255875450256D3DAB51A23B8EE66191`.
- Full `PowerStoreTest` XML SHA-256 `A0214D48AA90000EF2BA455836350FFCF952E00D110ECF0E9A45DAD19CC8E1FF`; 62 tests, 0 failures. Debug APK SHA-256 `80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`.
- Test-only source commit `92dd9016d7907b1e57cd4a27920332fa72e572fc` is pushed to `origin/main`; no production code or user data changed.
- Structural-only ledger validation: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-056.json`, SHA-256 `A83278D62871564CD5CD6431965E0E6EA7CC0687348062ABA5C93677CA786A4D`; ledger SHA-256 `A20D6F4718540835DE9BEF7D47E6E850A9A97B6793E27E4E91D000BA6E386DAA`. It confirms 209 unique candidate IDs and 104/0/9/89/7 status counts. Structural validation does not test product behavior or prove historical mapping.

## Candidate sign-off and limits

`REC-P0-018` is PASS for the tested PowerStore separation between durable schedule intent and formal event state. Actual OS permission denial/regrant, notification behavior, and historical checklist mapping remain unverified. Overall core freeze remains **HOLD**.

## Git

The test-only regression is committed separately as `92dd9016d7907b1e57cd4a27920332fa72e572fc`. This independent report, acceptance-ledger update, and synchronized freeze documentation are committed separately using a Chinese conventional commit and pushed to `origin/main`.
