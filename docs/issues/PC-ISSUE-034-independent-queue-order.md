# PC-ISSUE-034 — Queue order is independent and survives restart

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

Draft `queueOrder` represents enqueue order independently of `eventSeq`. Replacing an older event later can therefore place its proposal behind a newer event's proposal. The store already allocated queue order from its own high-water mark. No production defect was found.

The evidence gap was a case-specific persistence check that two active drafts retain distinct queue positions and correct event mappings after reopening SQLite, including when queue order runs opposite to event sequence.

## Change

Added `PowerStoreTest.independentQueueOrdersPersistAcrossRestartAndDoNotFollowEventNumbers` in test commit `500889acc6a5968f984caa7e8b063b9823255983` (`test(冻结): 验证队列序号独立持久化`). The test commits two events, enqueues a proposal for the later event before a proposal for the earlier event, and verifies unique positive `queueOrder` values, reversed order relative to `eventSeq`, and stable queue ordering and event mappings after reopening the same isolated SQLite database.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database. No UI, emulator, or physical device was tested.

- Pre-commit targeted run: **2/2 PASS**, covering the new persistence regression and existing queue/event independence test.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-034-independent-queue-order-targeted-precommit-20261023.log`, SHA-256 `2010C351D82318FC0E3D1154BE91AD64FECD4CC8B1025117870DC8C5D2D2A673`.
  - JUnit XML SHA-256 `95474E3662FA7550A833049B9998F62565266D61CF242CF13EB7C6CB49388AFA`.
- Post-commit targeted run: **2/2 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-034-independent-queue-order-targeted-postcommit-20261023.log`, SHA-256 `DF28959E2EF61401DEC8B987114261D23711F088394B186CFAA64BA84F3C406A`.
  - JUnit XML SHA-256 `D237A71F8EB75BC26101D5C07D33A7ED4CB7E29FEB742426CB2CEAE64350CD79`.
- Full command: `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 397/397 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes protected untracked `DraftDeckFeedbackTest.kt` (396 tracked tests + 1 protected untracked test); that file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-034-independent-queue-order-full-gradle-20261023.log`, SHA-256 `4FFF6FDA19716F8F9CD2576C20D068190CA6D2D65356961AEAAD2C63AF0DB665`.
- Full `PowerStoreTest` XML: 50 tests, zero failures/errors/skips; SHA-256 `C9FCA167A5600024DF55C819C01E6F1563B604C8CD4C9E0DE4AD08D6C4278CDE`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-035` is marked **PASS** in the reconstructed candidate ledger. It remains `UNMAPPED` to the original 209-case checklist. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-034.json` has SHA-256 `42E830802A5811093CB6F478463C6FC8C63849CFCB01DCA497826A6856DD6AA0`; it validates ledger structure, not product behavior. The updated CSV SHA-256 is `D4D29507F3D528C62A00F2DF3FE034C32062C82215580B4311781DD0A49328D2`.

The ledger now has 80 PASS, 0 FAIL, 9 BLOCKED, 114 NOT TESTED, and 6 SPEC UNRESOLVED rows; 55 reconstructed candidates have individual evidence. The candidate ledger is not the missing historical case list, so the overall core freeze remains **HOLD**.

## Commit and push

- Test commit: `500889acc6a5968f984caa7e8b063b9823255983` — `test(冻结): 验证队列序号独立持久化`.
- This report and the synchronized ledger/verdict updates are committed and pushed separately after documentation review.
