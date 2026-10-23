# PC-ISSUE-067 — AI proposal appends without stealing current focus

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-039`)

Status: **AI SERVICE / SQLITE / CONTROLLER MODEL PASS / NETWORK AND UI NOT TESTED / OVERALL FREEZE HOLD**

## Finding

The reconstructed ledger had no single regression tying a validated AI interpretation to persisted draft ordering and the controller's current-card selection. Existing queue storage and controller focus checks were isolated. No product defect was reproduced; the gap was integration-level evidence for the core path.

## Change

- Added `PowerStoreTest.aiProposalAppendsAtQueueTailAndRefreshPreservesCurrentFocus`.
- The test creates two existing user drafts, feeds one already-parsed AI CREATE proposal through `AiDraftService`, and verifies the SQLite queue places it last by `queueOrder`.
- After `DeckController.refresh()`, the previously selected second draft remains current while the new AI draft appears at the end of the state list.
- No network request, UI, production source, schema, user data, or design asset changed. This is not an end-to-end model/API or screen-rendering test.

## Verification

Environment: Robolectric API 34, isolated PowerStore SQLite, real `AiDraftService`, `StoreDeckPersistence`, and `DeckController`; no device or network.

- Focused test `PowerStoreTest.aiProposalAppendsAtQueueTailAndRefreshPreservesCurrentFocus`: **1/1 PASS**, zero failures/errors. Log `.local/android-test/reports/freeze-20261024/pc-issue-067-ai-proposal-queue-targeted-20261024.log`, SHA-256 `4BFB76CD598A1BD6234106A77DF603F44452D464BB39EFE475862F1199BD5B27`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-067-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `D9DCB773E0C69D7966332EC38B46D0E02612DE9088A012C6531634EF5DED7B72`.
- Full command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **BUILD SUCCESSFUL**, **419/419** tests across 55 suites, zero failures/errors/skips, lint 0 errors / 55 warnings. Log `.local/android-test/reports/freeze-20261024/pc-issue-067-full-gradle-20261024.log`, SHA-256 `CEEC58B22D9233BFE379795280FC470898FD893E480350D5EE5D77923A3F7E75`; full `PowerStoreTest` XML `.local/android-test/reports/freeze-20261024/pc-issue-067-full-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `B34C2B98B2992E2C6FC8D0C028C316B6FCA55FBB97A2351230F1389D29379E63`.
- Debug APK SHA-256: `DB944538964299348D31E8981D6A2F701F420FE74E60B508B8C53B253EC0517D`.

`REC-P0-039` is PASS for the parsed-proposal → SQLite queue → controller state contract. Real model/network behavior, visible UI ordering, and device behavior remain untested. The candidate stays `UNMAPPED` to the absent historical checklist, and the overall freeze remains **HOLD**.

## Git

- Test-only commit `feb3f2546d2130bce5a94f16a5734ec6dd576379` (`test(AI): 验证提案追加队尾并保留焦点`) is pushed to `origin/main`.
- This report and the synchronized ledger/verdict updates are committed separately.
