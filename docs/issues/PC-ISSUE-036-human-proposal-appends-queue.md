# PC-ISSUE-036 — Human proposal appends to the draft queue tail

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

The core contract under test is that creating a human proposal allocates a later `queueOrder` than proposals already in the pending draft queue. The order and proposal identities must survive closing and reopening the same SQLite database.

The existing `PowerStore.newDraft(..., actor="user")` path satisfied this behavior. The missing evidence was a focused test that starts with queued drafts, appends a human proposal, and checks persisted order after reopening. This report signs off only that core storage behavior; it does not claim editor UI or Android device coverage.

## Change

Added `PowerStoreTest.humanProposalAppendsAfterExistingQueueAcrossRestart` in test commit `f930b8714c9b94c62d8a4d294940b36a5322ea35` (`test(冻结): 验证人工提案追加队尾`). It creates two queued user drafts, appends a human proposal, verifies strictly increasing `queueOrder` values and pending order, then closes and reopens the isolated database and checks that draft IDs, order, and titles remain unchanged.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database.

- Pre-commit targeted run: **2/2 PASS**, including the new test and existing queue/event sequence independence test.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-036-human-proposal-appends-queue-targeted-precommit-20261023.log`, SHA-256 `2B2F1657DB15F9874550E3FCA0B879FDFED1B29E4CD9F65A68E708751A79B23F`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-036-targeted-precommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `DBE06030AD59C35735B9A71F114C609E5BE0CB4FD3C11FDCD8AE1A587BA9C3E3`.
- Post-commit targeted run: **2/2 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-036-human-proposal-appends-queue-targeted-postcommit-20261023.log`, SHA-256 `8CF359ED5B02675EF95C78DC74B5A7A12A453F7BB676F4E7BC60AAAF362972E2`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-036-targeted-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `0B87A0A54FB96F6D63B992FCCC028927911DD91E3858973ED8DCA0CE27CCAE4E`.
- Full command: `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 399/399 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes 398 tracked tests and the protected untracked `DraftDeckFeedbackTest.kt`; that protected file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-036-human-proposal-appends-queue-full-gradle-20261023.log`, SHA-256 `B7CBB97050DEC49C3AE880A5C9C080B7CF47676A108D343211A243634FEAECD9`.
- Full `PowerStoreTest` XML: 52 tests, zero failures/errors/skips; SHA-256 `6D6E249FF2C27651FA8EC53316B257E61EBE972C35A30C01A8384889212978ED`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-038` is marked **PASS** in the reconstructed candidate ledger. It remains `UNMAPPED` to the original 209-case checklist. The test proves queue-tail allocation and persistence in PowerStore; it does not prove an Android editor interaction or physical-device behavior. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-036.json` is structural-only and does not add product behavior evidence.

After this candidate, the ledger has 82 PASS, 0 FAIL, 9 BLOCKED, 112 NOT TESTED, and 6 SPEC UNRESOLVED rows; 57 reconstructed candidates have individual evidence. The overall core freeze remains **HOLD**.

## Commit and push

- Test commit: `f930b8714c9b94c62d8a4d294940b36a5322ea35` — `test(冻结): 验证人工提案追加队尾` (pushed to `origin/main`).
- This report and the synchronized ledger/verdict updates are committed and pushed separately after documentation review.
