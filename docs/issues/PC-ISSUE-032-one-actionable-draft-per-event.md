# PC-ISSUE-032 — One actionable draft per event survives restart

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

SQLite v4 specifies at most one actionable draft (`PENDING`, `CONFLICT`, or `INVALID`) for each `event_seq`. Successive proposals for the same existing event update that active draft in place and retain its `draftId` and `queueOrder`. The reconstructed `REC-P0-033` row had instead asked to preserve multiple actionable cards for one event, which conflicts with the approved storage contract. Its action and expected result have been normalized to the V4 rule; the original 209-case IDs and row mapping remain unavailable.

The store already enforced the single active slot and reused its identity. The evidence gap was a dedicated persistence regression showing the latest proposal remains the only actionable draft after SQLite reopen, and a discarded prior draft cannot be undone into a second active slot. No production defect was found.

## Change

Added `PowerStoreTest.repeatedSameEventProposalReusesOneActionableDraftAcrossRestart` in test commit `c455351dc7268147fbc00238111315a4a43a55c1` (`test(冻结): 验证同事件草稿单槽持久化`). The test stages two successive `REPLACE` proposals for one committed event, checks they share `draftId` and `queueOrder` while `draftVersion` advances, reopens the same isolated SQLite database, and verifies the latest content remains the sole actionable draft. It then discards that draft, creates a replacement, and verifies undoing the prior discarded draft is refused.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database. No UI, emulator, or physical device was tested.

- Pre-commit targeted run: **2/2 PASS**, covering the new restart regression and existing same-event reuse regression.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-032-one-actionable-draft-targeted-precommit-20261023.log`, SHA-256 `FF1AA6BE1243F698E52F85B624C32932D38252BE2ACE4196CEC5851EE7149591`.
  - JUnit XML SHA-256 `CD8281705D88C92E7AE5953595FDC899347499586E7D3DD646BC3E4171AA8D33`.
- Post-commit targeted run: **2/2 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-032-one-actionable-draft-targeted-postcommit-20261023.log`, SHA-256 `E78E9DA3B7485D19D0092C527FE5B292FA1A4EDD0A3B5EF24A8EB98E1BB48660`.
  - JUnit XML SHA-256 `2DFAB6A91651C8099C320CFF3239B64FA7B511488CA9ECF83C37F3FD715CA06D`.
- Full command: `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 395/395 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes protected untracked `DraftDeckFeedbackTest.kt` (394 tracked tests + 1 protected untracked test); that file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-032-one-actionable-draft-full-gradle-20261023.log`, SHA-256 `50E2F0FD9BE0D495AE3DD6339AAE2F7983B899C9A75DFF6E7E138DF5644685F6`.
- Full `PowerStoreTest` XML: 48 tests, zero failures/errors/skips; SHA-256 `53A61631F305C17B6F581A9BFF37225F0A1584714675D438A7347560E675C327`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-033` is marked **PASS** in the reconstructed candidate ledger against the corrected V4 meaning. It remains `UNMAPPED` to the original 209-case checklist. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-032.json` has SHA-256 `0B58C608A65BB30EFE3C579B8205804C9101C2D7D036380E253979F078BCEF55`; it validates ledger structure, not product behavior. The updated CSV SHA-256 is `EC07C74A4BC13F1D61D58B2DCEF093346424B64310ED5E455DC4507AECBEA86F`.

The ledger now has 78 PASS, 0 FAIL, 9 BLOCKED, 116 NOT TESTED, and 6 SPEC UNRESOLVED rows; 53 reconstructed candidates have individual evidence. The candidate ledger is not the missing historical case list, so the overall core freeze remains **HOLD**.

## Commit and push

- Test commit: `c455351dc7268147fbc00238111315a4a43a55c1` — `test(冻结): 验证同事件草稿单槽持久化`.
- This report and the synchronized ledger/verdict updates are committed and pushed separately after documentation review.
