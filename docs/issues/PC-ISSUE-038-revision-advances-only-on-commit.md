# PC-ISSUE-038 — Formal revision advances only on commit

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

A valid staged mutation must not change the revision of the currently published event. The new revision is assigned only when the proposal is committed. This must remain true after closing and reopening the SQLite database.

The PowerStore implementation already creates or updates the formal snapshot inside the commit transaction. The evidence gap was a focused regression proving that proposal creation and staging do not publish a new revision, while commit advances it exactly once.

## Change

Added `PowerStoreTest.stagedMutationAdvancesFormalRevisionOnlyWhenCommittedAcrossRestart` in test commit `a515c11478af21412fbd79ea955e1976a2fd3dba` (`test(冻结): 验证修订号仅在提交时递增`). The test commits an initial event at revision 1, creates and edits a replacement proposal, verifies the formal snapshot remains revision 1, then closes and reopens SQLite and verifies the same published snapshot and staged proposal. Committing the staged proposal advances the formal revision to 2 and publishes its final title.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database.

- Pre-commit targeted run: **2/2 PASS**, including the new revision regression and existing multiple-commit snapshot test.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-038-revision-advances-only-on-commit-targeted-precommit-20261023.log`, SHA-256 `2B2F1657DB15F9874550E3FCA0B879FDFED1B29E4CD9F65A68E708751A79B23F`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-038-targeted-precommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `B876943BBAFC139922C72F36CED2718FC0D197F9AFDD82BC398FACF8F2B0B80F`.
- Post-commit targeted run: **2/2 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-038-revision-advances-only-on-commit-targeted-postcommit-20261023.log`, SHA-256 `E78E9DA3B7485D19D0092C527FE5B292FA1A4EDD0A3B5EF24A8EB98E1BB48660`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-038-targeted-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `EACA0BD159D62660B2B6476E22722D9776E28F3F2199071C2D5CB1DE83D0927B`.
- Full command: `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 401/401 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes 400 tracked tests and the protected untracked `DraftDeckFeedbackTest.kt`; that protected file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-038-revision-advances-only-on-commit-full-gradle-20261023.log`, SHA-256 `09984A1F258A9E7E9E1D20C1912957A6554A8F874AEEA707DFA1BB9487AB5782`.
- Full `PowerStoreTest` XML: 54 tests, zero failures/errors/skips; SHA-256 `6A84DCF7F987F89DDF8DE923410341D18A46A906CF6A05418E49F593C894E880`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-044` is marked **PASS** in the reconstructed candidate ledger. It remains `UNMAPPED` to the original 209-case checklist. Evidence covers formal revision persistence and commit behavior in PowerStore only; it does not claim UI or physical-device behavior. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-038.json` is structural-only and does not add product behavior evidence.

After this candidate, the ledger has 84 PASS, 0 FAIL, 9 BLOCKED, 110 NOT TESTED, and 6 SPEC UNRESOLVED rows; 59 reconstructed candidates have individual evidence. The overall core freeze remains **HOLD**.

## Commit and push

- Test commit: `a515c11478af21412fbd79ea955e1976a2fd3dba` — `test(冻结): 验证修订号仅在提交时递增` (pushed to `origin/main`).
- This report and the synchronized ledger/verdict updates are committed and pushed separately after documentation review.
