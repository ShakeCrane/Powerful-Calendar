# PC-ISSUE-031 — Staging a draft edit preserves its identity

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

Editing an existing proposal updates that draft in place. The documented V4 editing contract requires the same `draftId` and target `eventSeq` to survive a staged content edit. The reconstructed candidate row had described this imprecisely as “Change draftId”; it has been normalized to the documented edit semantics. The original 209-case IDs and row mapping remain unavailable.

The store already retained both identities while staging an edit. The missing evidence was a case-specific assertion that the draft, target event, and allocator state remain stable through staging and SQLite reopen. No production defect was found.

## Change

Added `PowerStoreTest.stagingDraftEditKeepsDraftAndEventIdentitiesAcrossRestart` in test commit `1d373f55876336a2153064d3013d09cbcfdd7f84` (`test(冻结): 验证暂存草稿保留事件身份`). The regression stages revised content on an existing Activity draft, verifies the original `draftId` and target `eventSeq`, checks that `draftVersion` increments while the identity count and next-sequence preview remain unchanged, then closes and reopens the same isolated SQLite database and verifies the revised draft and pending queue persist.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database. No UI, emulator, or physical device was tested.

- Pre-commit targeted run: **2/2 PASS**, including the new staged-edit regression and existing draft-reopen coverage.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-031-staged-draft-identity-targeted-precommit-20261023.log`, SHA-256 `D28C3D464FFF7D5ECDD5E03FB0D2E9E05A84EABD6F2A3A8FEDEE9CD072C51224`.
  - JUnit XML SHA-256 `2F4A4B68E6469A5E893A4E66C318696D455C9BB4C1CBA6B02742F74BF09DB08D`.
- Post-commit targeted run: **2/2 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-031-staged-draft-identity-targeted-postcommit-20261023.log`, SHA-256 `A6D078A431A9870A8E89C49529A5E1F692355BB15E73A9E305C913A6325643B6`.
  - JUnit XML SHA-256 `FD7B64CD57D79E4E8E70A80E3D6BB355F6B5F3C62E5C419C60906BC5ED96A927`.
- Full command: `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 394/394 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes protected untracked `DraftDeckFeedbackTest.kt` (393 tracked tests + 1 protected untracked test); that file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-031-staged-draft-identity-full-gradle-20261023.log`, SHA-256 `0837BE595F54D803DE16963BD3F4F32354586E8D1027AF5333E1DE97FC2D775B`.
- Full `PowerStoreTest` XML: 47 tests, zero failures/errors/skips; SHA-256 `9BA04D5BB571D74C44A1683FBB4A1059A2185C339CE1796B5D94085640E773F7`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-032` is marked **PASS** in the reconstructed candidate ledger after correcting its action and expected result to match the documented V4 edit contract. It remains `UNMAPPED` to the original 209-case checklist. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-031.json` has SHA-256 `EB1A4840986445879300BBF915AF18BC617BA690204C809C04BBE34837FEC3B3`; it validates ledger structure, not product behavior. The updated CSV SHA-256 is `BF06DF82897330A45B9ED941CF7A1AF18E6E830281A1609EF1482E73E9A1D1EA`.

The ledger now has 77 PASS, 0 FAIL, 9 BLOCKED, 117 NOT TESTED, and 6 SPEC UNRESOLVED rows; 52 reconstructed candidates have individual evidence. The candidate ledger is not the missing historical case list, so the overall core freeze remains **HOLD**.

## Commit and push

- Test commit: `1d373f55876336a2153064d3013d09cbcfdd7f84` — `test(冻结): 验证暂存草稿保留事件身份`.
- This report and the synchronized ledger/verdict updates are committed and pushed separately after documentation review.
