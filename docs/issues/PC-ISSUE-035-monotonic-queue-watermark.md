# PC-ISSUE-035 — Queue-order high-water mark advances across restart

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

Each newly enqueued draft receives a `queueOrder` greater than every previously allocated position. Committed and discarded drafts retain their historical positions; a database restart must not lower the allocator or reuse them. The store already persisted the `next_order` high-water mark. No production defect was found.

The evidence gap was a case-specific regression that allocates positions to both committed and discarded drafts, reopens SQLite, then proves the next draft advances beyond both old positions.

## Change

Added `PowerStoreTest.nextDraftQueueOrderAdvancesPastCommittedAndDiscardedDraftsAfterRestart` in test commit `1a74b8b336d0351e1ceeed0b1742943c1bc6f697` (`test(冻结): 验证队列高水位跨重启递增`). The test commits one draft, discards a later draft, closes and reopens the same isolated database, confirms the discarded status persisted, then enqueues another draft and verifies its `queueOrder` is greater than both earlier allocations.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database. No UI, emulator, or physical device was tested.

- Pre-commit targeted run: **2/2 PASS**, covering the new restart regression and existing queue/event independence test.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-035-monotonic-queue-watermark-targeted-precommit-20261023.log`, SHA-256 `2B2F1657DB15F9874550E3FCA0B879FDFED1B29E4CD9F65A68E708751A79B23F`.
  - JUnit XML SHA-256 `203CD45301DB58DA708501A582A2E936F84A64D1D0753A6406E1060508FF3784`.
- Post-commit targeted run: **2/2 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-035-monotonic-queue-watermark-targeted-postcommit-20261023.log`, SHA-256 `DF28959E2EF61401DEC8B987114261D23711F088394B186CFAA64BA84F3C406A`.
  - JUnit XML SHA-256 `2B1B1FA9741A9A16C6F7CEFC1CF1636C280BF9FE01FAF4980A198FEA35F9AA5E`.
- Full command: `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 398/398 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes protected untracked `DraftDeckFeedbackTest.kt` (397 tracked tests + 1 protected untracked test); that file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-035-monotonic-queue-watermark-full-gradle-20261023.log`, SHA-256 `CF006C08916F2BFA332F40E00D52D1B6A2742C2DAD3B872AEE2A4E7BBBBCF3B6`.
- Full `PowerStoreTest` XML: 51 tests, zero failures/errors/skips; SHA-256 `F691A0E980CDA4C129D6A4C80020E1FA757B11E4ABA71A5545509F703EC3DAD6`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-036` is marked **PASS** in the reconstructed candidate ledger. It remains `UNMAPPED` to the original 209-case checklist. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-035.json` has SHA-256 `41258CF994F31ACC8428A495C4A64F84CC1380D827DF4195217ED86C2A79B039`; it validates ledger structure, not product behavior. The updated CSV SHA-256 is `CA5CEF342DB393D57384802A0E8C412AACDD1EB0548AD6753C1477F1352609D7`.

The ledger now has 81 PASS, 0 FAIL, 9 BLOCKED, 113 NOT TESTED, and 6 SPEC UNRESOLVED rows; 56 reconstructed candidates have individual evidence. The candidate ledger is not the missing historical case list, so the overall core freeze remains **HOLD**.

## Commit and push

- Test commit: `1a74b8b336d0351e1ceeed0b1742943c1bc6f697` — `test(冻结): 验证队列高水位跨重启递增`.
- This report and the synchronized ledger/verdict updates are committed and pushed separately after documentation review.
