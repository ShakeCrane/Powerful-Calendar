# PC-ISSUE-030 — Independent create proposals keep unique draft identities

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

Each persisted create proposal needs a unique `draftId` associated with its own allocated `eventSeq`. The SQLite v4 editing contract also permits at most one actionable draft per event, so this check creates two separate events and verifies their separate proposal identities. It does not assert that the numeric values of `draftId` and `eventSeq` must differ.

The store already used an auto-increment primary key for `draftId`. The missing evidence was a case-specific check that two simultaneous create proposals retain unique IDs and correct target mappings after SQLite reopen. No production defect was found.

## Change

Added `PowerStoreTest.independentCreateDraftsKeepUniqueDraftIdsAcrossRestart` in test commit `9cfd0ee4ff71c13103dcbb7feab9d8bc6b11e6a3` (`test(冻结): 验证并行提案草稿身份独立`). The test creates two Activity proposal drafts, verifies distinct `draftId` and `eventSeq` values and correct draft-to-event mappings, then reopens the same database and verifies both draft IDs, mappings, and pending queue entries persist. It runs alongside the existing `oneEventReusesOneActionableCardAndItsQueueSlot` regression to retain the current same-event editing rule.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database. No Android UI, emulator, or physical device was tested.

- Pre-commit targeted run: **2/2 PASS**, covering the new independent-create test and the existing same-event actionable-draft test.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-030-independent-draft-targeted-precommit-20261023.log`, SHA-256 `2010C351D82318FC0E3D1154BE91AD64FECD4CC8B1025117870DC8C5D2D2A673`.
  - JUnit XML SHA-256 `4049D93DFBFD953D4C967B8F983A8F23FFB2B25F22B8DA3D360C1876130616ED`.
- Post-commit targeted run: **2/2 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-030-independent-draft-targeted-postcommit-20261023.log`, SHA-256 `8CF359ED5B02675EF95C78DC74B5A7A12A453F7BB676F4E7BC60AAAF362972E2`.
  - JUnit XML SHA-256 `34B222F57CC7F2693E20472ED3BA934209DF5C83A3788B417B59DE15A63D17D2`.
- Full command: `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 393/393 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes protected untracked `DraftDeckFeedbackTest.kt` (392 tracked tests + 1 protected untracked test); that file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-030-independent-draft-full-gradle-20261023.log`, SHA-256 `54E0983E3F4020CBB5453265B10815C728960E146EB60637B9DA13F57E1418CF`.
- Full `PowerStoreTest` XML: 46 tests, zero failures/errors/skips; SHA-256 `922B3FA40F5B3DF2FB8AA8BB6178FBC8EF8AD1072BEA38BA63A279C6D1233B19`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-031` is marked **PASS** in the reconstructed candidate ledger. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-030.json` has SHA-256 `ACFF4AAC80FA1DD81DD1BF29818E920F0E65B714E744182EFB0D71BD81E02CB0`; it validates ledger structure, not product behavior. This signs off persisted unique draft IDs and their event targets in the core store; it does not sign off proposal-card UI or device behavior. The candidate remains `UNMAPPED` to the original 209-case checklist. Overall core freeze remains **HOLD**.

## Commit and push

- Test commit: `9cfd0ee4ff71c13103dcbb7feab9d8bc6b11e6a3` — `test(冻结): 验证并行提案草稿身份独立`.
- The independent report and ledger/verdict synchronization are committed and pushed separately after review of the documentation diff.
