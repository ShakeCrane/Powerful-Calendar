# PC-ISSUE-033 — One current formal snapshot per event

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

Each `eventSeq` has one current formal snapshot. Later commits replace that snapshot as a whole and advance its revision; earlier commit records remain in history. The SQLite `objects.event_seq` primary key already enforces one current row, and commit handling updates the existing row. No production defect was found.

The evidence gap was a case-specific check that querying formal records returns exactly one current snapshot after multiple commits and still does so after SQLite reopen, while prior commits remain in the separate history table.

## Change

Added `PowerStoreTest.multipleCommitsKeepOneCurrentFormalSnapshotAcrossRestart` in test commit `c0c0a2912fa849013a20ba105036299b4e66d741` (`test(冻结): 验证事件仅保留当前正式快照`). It commits three successive whole snapshots for one event, checks that `all()` returns one row at revision 3 with the latest title before and after reopening the same isolated SQLite database, and confirms the three commit-history entries remain available.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database. No UI, emulator, or physical device was tested.

- Pre-commit targeted run: **2/2 PASS**, covering the new persistence regression and existing whole-snapshot commit test.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-033-formal-snapshot-targeted-precommit-20261023.log`, SHA-256 `2010C351D82318FC0E3D1154BE91AD64FECD4CC8B1025117870DC8C5D2D2A673`.
  - JUnit XML SHA-256 `74FC59E21FE2C4874DA6A8B8B5E0E03578B88462ED83ED8AB2A97368D1CDCADC`.
- Post-commit targeted run: **2/2 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-033-formal-snapshot-targeted-postcommit-20261023.log`, SHA-256 `E78E9DA3B7485D19D0092C527FE5B292FA1A4EDD0A3B5EF24A8EB98E1BB48660`.
  - JUnit XML SHA-256 `F3BA930D37E566E770D6D1C16B199C65858FC1DAE7DAE0C21BF423627D63D246`.
- Full command: `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 396/396 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes protected untracked `DraftDeckFeedbackTest.kt` (395 tracked tests + 1 protected untracked test); that file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-033-formal-snapshot-full-gradle-20261023.log`, SHA-256 `C309FEF4E25520E13AB07864D1ACFA32EEFCD647D7AC62081633C89FB779ADF4`.
- Full `PowerStoreTest` XML: 49 tests, zero failures/errors/skips; SHA-256 `A09075019FDFA8FFA46D3663DAA24307BCCA2AD23EC40E9C86DA53BA9B5F942A`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-034` is marked **PASS** in the reconstructed candidate ledger. It remains `UNMAPPED` to the original 209-case checklist. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-033.json` has SHA-256 `7196F96BDACF9774507EC0827B2928D58FC69B322BA7D515CBE47AA09B23C76D`; it validates ledger structure, not product behavior. The updated CSV SHA-256 is `D67CD686E2600D004622844C845B4567A4D773662B3A953B8B892C898D8C495F`.

The ledger now has 79 PASS, 0 FAIL, 9 BLOCKED, 115 NOT TESTED, and 6 SPEC UNRESOLVED rows; 54 reconstructed candidates have individual evidence. The candidate ledger is not the missing historical case list, so the overall core freeze remains **HOLD**.

## Commit and push

- Test commit: `c0c0a2912fa849013a20ba105036299b4e66d741` — `test(冻结): 验证事件仅保留当前正式快照`.
- This report and the synchronized ledger/verdict updates are committed and pushed separately after documentation review.
