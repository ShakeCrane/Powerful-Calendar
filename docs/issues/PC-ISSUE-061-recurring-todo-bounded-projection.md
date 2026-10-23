# PC-ISSUE-061 — Keep recurring todo storage bounded

Date: 2026-10-23

Severity: P0 candidate

Status: **POWERSTORE/SQLITE AND WINDOWED PROJECTION REGRESSION PASS / DEVICE NOT TESTED**

## Scope and finding

`REC-P0-064` requires an unbounded recurring todo to remain a semantic recurrence rule and to project only a bounded requested window. The implementation already stores one formal record and computes occurrences from the rule. The missing evidence was a PowerStore regression combining an unbounded recurring MEMO todo, a distant query window, and a database reopen.

The test creates a daily MEMO series anchored in 2024, commits it to SQLite, and queries only 2026-10-20 through 2026-10-23. The projection returns exactly three original-slot keys. Storage still contains one identity and one formal record; reopening SQLite preserves the one recurrence rule and does not create occurrence rows. No production defect was observed; this issue adds a test only.

## Candidate mapping

- `REC-P0-064`: `PowerStoreTest.unboundedRecurringMemoStoresOneSeriesAndProjectsOnlyTheRequestedWindow` verifies bounded projection and one-series persistence before and after reopen. The reconstructed candidate remains `UNMAPPED` to the original checklist.

## Verification evidence

Environment: Robolectric API 34 with isolated PowerStore SQLite; no Android device.

- Targeted command: `:app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerStoreTest.unboundedRecurringMemoStoresOneSeriesAndProjectsOnlyTheRequestedWindow" --offline --rerun-tasks --console=plain` — **1/1 PASS**, zero failures/errors/skips.
- Targeted log `.local/android-test/reports/freeze-20261023/pc-issue-061-recurring-todo-targeted-20261023.log`, SHA-256 `846866B86EB8B369291003E9FA2FF7139A0AFB37618669E5F9C6D24675A1D81C`.
- Preserved targeted JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-061-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `8C789D16C7CC3B2252314460DF71942BE4DBAEAEB790D3568AFC932A731CC360`; one test, zero failures/errors/skips.
- Full command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 414/414 tests across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. This includes the protected untracked `DraftDeckFeedbackTest.kt` (413 tracked + 1 protected untracked).
- Full log `.local/android-test/reports/freeze-20261023/pc-issue-061-full-gradle-20261023.log`, SHA-256 `C4BC8F0E5EEAA0F66E707435A1AB5187275EDCA2928372EAA4F7EE2470FF13D6`.
- Preserved full JUnit `PowerStoreTest` XML `.local/android-test/reports/freeze-20261023/pc-issue-061-full-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `A4A22E45E176C36A5CDEEFEB7739DB744754D06FF40F239BFA0D3F6288845BC6`; 64 tests, zero failures.
- Debug APK SHA-256 `1E8332E7A18D71A1516D3E6B3197244738418E7703071D2EBCEF1363938B4BEF`.

## Candidate sign-off and limits

`REC-P0-064` is PASS for bounded recurring-MEMO projection and single-series persistence. UI todo presentation, Android device behavior, and historical checklist mapping remain unverified. Overall core freeze remains **HOLD**.

## Git

- Test-only commit: `58ac0c8489d8a1363388c8a66f8a397c7d7610c6`
- Message: `test(待办): 验证重复事项按窗口投影`
- Status: pushed to `origin/main`.
- No production code, UI, user database, or protected untracked asset was changed or staged.
- This independent report and synchronized freeze documentation are committed separately.
