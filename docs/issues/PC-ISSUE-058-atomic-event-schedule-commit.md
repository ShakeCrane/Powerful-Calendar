# PC-ISSUE-058 — Formal event, commit history, and desired schedule are atomic

Date: 2026-10-23

Severity: P0 candidate

Status: **SQLITE ATOMICITY AND RETRY REGRESSION PASS / ANDROID SCHEDULER DELIVERY NOT TESTED**

## Scope and finding

`REC-P0-110` requires a formal commit to persist its new revision, commit history, and desired schedule as one transaction. The implementation already performs these writes inside one SQLite transaction. The missing evidence was a fault injected specifically between formal commit work and the desired-schedule write. No production defect was observed; this issue adds a regression test only.

The test installs a SQLite trigger that aborts insertion into `schedule_intents`. It confirms the failed commit leaves no formal event, commit history, or desired intent, and the draft remains `PENDING`. After removing the trigger, retry succeeds at revision 1; the formal snapshot, commit record, and matching `PENDING` intent remain consistent after closing and reopening SQLite. This validates PowerStore transaction behavior, not Android `AlarmManager` delivery.

## Candidate mapping

- `REC-P0-110`: `PowerStoreTest.commitRollsBackFormalEventHistoryAndScheduleWhenDesiredIntentWriteFailsThenRecovers` verifies injected rollback and successful retry/reopen persistence. The reconstructed candidate remains `UNMAPPED` to the original checklist.

## Verification evidence

Environment: Robolectric API 34, isolated PowerStore SQLite database; no Android device.

- Targeted command: `:app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerStoreTest.commitRollsBackFormalEventHistoryAndScheduleWhenDesiredIntentWriteFailsThenRecovers" --offline --rerun-tasks --console=plain` — **1/1 PASS**, zero failures/errors/skips.
- Targeted log `.local/android-test/reports/freeze-20261023/pc-issue-058-atomic-commit-targeted-20261023.log`, SHA-256 `B2D3FA1EC7E25E718EE17AD2F655D264611B59CD5C1CD1BC2B251F361BD473E2`.
- Preserved targeted JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-058-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `F8518DBBA895E460463763A0F3287D419F0464446D74EBE630A5D1914699A72D`; one test, zero failures/errors/skips.
- Full Gradle command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 411/411 tests across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. It includes the protected untracked `DraftDeckFeedbackTest.kt` (410 tracked + 1 protected untracked).
- Full log `.local/android-test/reports/freeze-20261023/pc-issue-058-full-gradle-20261023.log`, SHA-256 `57A54B40941585805737E5A48FAFAC16E052CCCEAD3EF3C1538BC67DACABB0E5`.
- Preserved full JUnit directory `.local/android-test/reports/freeze-20261023/pc-issue-058-full-junit/`; `PowerStoreTest` XML SHA-256 `7BDCA77CE8F0BC11083B81D7C6188F0F278C8088C2F2711DFD2395C05C10FBC4`; 63 tests, zero failures.
- Debug APK SHA-256 `80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`.

## Candidate sign-off and limits

`REC-P0-110` is PASS for the tested PowerStore transaction: formal revision, commit history, and desired schedule roll back together on an injected schedule-write failure and persist together after a successful retry. Android scheduler reconciliation/delivery, physical-device behavior, and historical checklist mapping remain unverified. Overall core freeze remains **HOLD**.

## Git

- Test-only commit: `4906601d221bba0298068a3568a1bac2ad47c382`
- Message: `test(存储): 验证事件提交与调度原子性`
- Status: pushed to `origin/main`.
- No production code, user data, UI, or protected untracked files were changed.
- This independent report and synchronized freeze documentation will be committed separately with a Chinese conventional commit.
