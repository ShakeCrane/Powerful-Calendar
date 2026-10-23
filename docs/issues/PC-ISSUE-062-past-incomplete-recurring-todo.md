# PC-ISSUE-062 — Retain past incomplete recurring todo occurrences

Date: 2026-10-23

Severity: P0 candidate

Status: **CORE MODEL REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

`REC-P0-065` requires a past, incomplete occurrence of an unbounded recurring todo to remain recoverable. The current core model stores the recurrence rule and completion keys on one formal MEMO record; there is no occurrence-pruning write path in this contract. The missing evidence was a historical-window query around an incomplete occurrence across SQLite reopen.

The test stores one daily MEMO series anchored in 2024, leaves all occurrence keys incomplete, and queries the historical 2025-03-14 window. The original occurrence key is returned before and after database reopen, while `completedKeys` remains empty and only one formal event exists. No production defect was observed; this issue adds a test only.

## Candidate mapping

- `REC-P0-065`: `PowerStoreTest.unboundedRecurringMemoKeepsPastIncompleteOccurrenceAcrossRestart` verifies that a past occurrence remains queryable and incomplete after SQLite reopen. The reconstructed candidate remains `UNMAPPED` to the original checklist.

## Verification evidence

Environment: Robolectric API 34 with isolated PowerStore SQLite and a historical recurrence window; no Android device.

- Targeted command: `:app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerStoreTest.unboundedRecurringMemoKeepsPastIncompleteOccurrenceAcrossRestart" --offline --rerun-tasks --console=plain` — **1/1 PASS**, zero failures/errors/skips.
- Targeted log `.local/android-test/reports/freeze-20261023/pc-issue-062-overdue-todo-targeted-20261023.log`, SHA-256 `2EAD5EFBAF553AA6B5CC8DA815466A2E4D4F58CC19212BA32D8D119DA91A3E46`.
- Preserved targeted JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-062-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `E5510FE9F9C8ECC3C2F81E29B39CF4897530220972EC4E064A932C241BD400C7`; one test, zero failures/errors/skips.
- Full command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 415/415 tests across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. This includes the protected untracked `DraftDeckFeedbackTest.kt` (414 tracked + 1 protected untracked).
- Full log `.local/android-test/reports/freeze-20261023/pc-issue-062-full-gradle-20261023.log`, SHA-256 `AFFA844FBDCD8AB615E491CFB4D9ADE044C5B8CE9FE87C025A74C1E926FC4E90`.
- Preserved full JUnit `PowerStoreTest` XML `.local/android-test/reports/freeze-20261023/pc-issue-062-full-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `DF0A260D32C7BF064D274C68E72422AE8925A6416D06526790904E73C008363B`; 65 tests, zero failures.
- Debug APK SHA-256 `1E8332E7A18D71A1516D3E6B3197244738418E7703071D2EBCEF1363938B4BEF`.

## Candidate sign-off and limits

`REC-P0-065` is PASS for core historical-window projection and SQLite retention of an incomplete recurring MEMO occurrence. The test provides no Android device evidence, and the reconstructed candidate remains unmapped to the missing historical checklist. Overall core freeze remains **HOLD**.

## Git

- Test-only commit: `74f3782a972fdddbf8b4d030e00f164152c1b75d`
- Message: `test(待办): 保留过期未完成重复项`
- Status: pushed to `origin/main`.
- No production code, UI, user database, or protected untracked asset was changed or staged.
- This independent report and synchronized freeze documentation are committed separately.
