# PC-ISSUE-071 — Single-use scoped AI batch authorization

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-126`)

Status: **CORE AUTHORIZATION BOUNDARY PASS / USER CONFIRMATION AND LIVE MODEL FLOW NOT TESTED / OVERALL FREEZE HOLD**

## Finding

The candidate had no one-to-one execution evidence for whether an AI batch authorization stays limited to the reviewed drafts and operations and becomes unusable after completion. Read-only inspection found that `PowerStore.beginAiBatch` freezes reviewed draft versions and allowed operations; authorization checks the active session, process owner, lock epoch, draft scope, reviewed version, and operation. `finishAiBatch` terminates the session and advances the epoch. No production defect was found, so no production code change was needed.

## Change

- Added `AiAuthorizationTest.completedBatchApprovalCannotBeReplayed`.
- The test finishes an approved batch, verifies a second finish is rejected, then confirms the old authorization cannot commit and leaves the formal event absent and draft pending.
- It opens a fresh batch for the same reviewed snapshot, verifies a distinct session and higher epoch, rejects replay of the first authorization while the new batch is active, and confirms the fresh authorization can commit the reviewed draft.
- Existing adjacent tests continue to cover changed review content, discarded drafts, unauthorized operations, and scope growth. This issue adds the missing completion/replay regression only.
- No production, UI, user-data, or design-asset files changed.

## Verification

Environment: local Gradle unit tests with Robolectric API 34/PowerStore SQLite fixtures. No physical Android device, live AI provider, or user-confirmation UI was exercised.

- Focused test `AiAuthorizationTest.completedBatchApprovalCannotBeReplayed`: **1/1 PASS**, zero failures/errors/skips. Log `.local/android-test/reports/freeze-20261024/pc-issue-071-ai-approval-single-use-targeted-final-20261024.log`, SHA-256 `F64F2CF0BD4AF7D0AC028F060814B587F93D1F8BE6060DBC0FE05B522A56C299`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-071-targeted-junit/TEST-dev.powerfulcalendar.core.AiAuthorizationTest.xml`, SHA-256 `DBADF23DB7829559F08E8BEB378DF5CC7E7F29A4A1AC4E07D85BE00B7D6EBB0C`.
- Full command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **BUILD SUCCESSFUL**, **423/423** tests across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. This includes 422 tracked tests and one protected untracked test; the protected file was not modified or staged. Log `.local/android-test/reports/freeze-20261024/pc-issue-071-full-gradle-20261024.log`, SHA-256 `FBC84B23CDA78D3FA92C48B787FE4A37CBAE8773DE9171789A2F41B2F7ECC646`; full `AiAuthorizationTest` XML `.local/android-test/reports/freeze-20261024/pc-issue-071-full-junit/TEST-dev.powerfulcalendar.core.AiAuthorizationTest.xml`, SHA-256 `E41AE0447F2112833056D18B9C69363DDA736876930E5C87D1939A203550C81A`.
- Debug APK SHA-256: `9C56BAEFD17D0EBF1D43BFE25BF02592C3EF20C108E92CD117EE03A6FD0BC134`.

`REC-P0-126` is PASS for the PowerStore scoped-authorization and single-use session boundary. This does not establish that a user confirmation screen is wired to this API, that a live model follows the approved scope, or that the full network-to-approval flow works. The reconstructed candidate remains `UNMAPPED` to the absent historical checklist, and overall freeze remains **HOLD**.

## Git

- Test commit `e8eb48c96f4132c0fe9af63aac8933e0d8435257` (`test(AI): 验证批量授权不可重放`) is pushed to `origin/main`.
- This report and the synchronized ledger/verdict updates are committed separately.
