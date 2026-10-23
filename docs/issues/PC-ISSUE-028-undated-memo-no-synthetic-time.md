# PC-ISSUE-028 — Undated Memo has no synthetic time anchor

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE DATA MODEL PASS / OVERALL FREEZE HOLD**

## Scope and finding

The approved contract allows a Memo without a date and says it must not be projected to midnight or “today” (`CORE_CONTRACT_FREEZE_2026-10-21.md`, Invariant 3). Inspection found the temporal model already represents an undated Memo without a date, instant, or axis anchor. The existing pure model test checked the no-fake-time rule, but the persistence coverage did not separately assert that all date/time fields and derived anchors remain empty after reopening SQLite.

No production defect was found. This issue adds a focused persistence regression so the stored core record cannot silently acquire a date or a midnight/“today” timestamp. It does not assert any UI presentation behavior.

## Change

Added `PowerStoreTest.undatedMemoKeepsDateAndTimeFieldsAbsentAcrossRestart` in test commit `3f9426606dfa26b40f63ca11aceecc1c1e93bd34` (`test(冻结): 验证无日期备忘不生成时间点`). It commits an undated Memo, verifies all date/time/deadline fields plus `anchorMillis()` and `spanEndMillis()` are null, reopens the same SQLite database, and repeats those checks. It also verifies the stable summary `无日期 · 待办` before and after reopening.

The test-only change did not modify production code, UI, business data, or protected untracked assets.

## Verification evidence

Environment: pure JVM temporal-model test and Robolectric API 34 with an isolated PowerStore SQLite database. No Android UI, emulator, or physical device was tested.

- Pre-commit targeted run: **2/2 PASS**, comprising the new persistence regression and `PowerCalMemoContractTest.undatedMemoNeedsOnlyTitleAndHasNoFakeTime`.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-028-undated-memo-targeted-precommit-20261023.log`, SHA-256 `2010C351D82318FC0E3D1154BE91AD64FECD4CC8B1025117870DC8C5D2D2A673`.
  - `PowerStoreTest` XML SHA-256 `96056FD224F3F26D7DA7F27D548BDDF9FFFA345034644349167E38CCC12E50D`; model test XML SHA-256 `C517D5A8FCF063C0CDE23E978C3F07F340E785ACF88BB46BF02B6751CFA964F8`.
- Post-commit targeted run: **2/2 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-028-undated-memo-targeted-postcommit-20261023.log`, SHA-256 `DF28959E2EF61401DEC8B987114261D23711F088394B186CFAA64BA84F3C406A`.
  - `PowerStoreTest` XML SHA-256 `E8900B92CA66E54BCBC6A5ED425B4EBEBE2A57C3812C82521902E516F037C22C`; model test XML SHA-256 `8B3C72EC91AF851EF1694D438B142CF246F03FF43B8E95812FD34A15721FAE34`.
- Full command: `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 391/391 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. The working-tree total includes protected untracked `DraftDeckFeedbackTest.kt` (390 tracked tests + 1 protected untracked test); that file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-028-undated-memo-full-gradle-20261023.log`, SHA-256 `754C84377941BA778AD5AF2728C0E4C7CCD10B4275586409744803EB1A22DD45`.
- Full `PowerStoreTest` XML: 44 tests, zero failures/errors/skips; SHA-256 `3D7E713E9D754F012F4B4F1BB540714F117AC0390E3B342675322F408B32F390`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-006` is marked **PASS** for its core data-model invariant in the reconstructed ledger. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-028.json` has SHA-256 `1421ED2DF17153F6FC50A7F02D4474C5310B27AE9092385A74C46E847D771B99`; this validation checks the ledger only, not product behavior. The regression proves that no synthetic instant or interval is present in the persisted record; it does not sign off visual behavior. The candidate remains `UNMAPPED` to the original 209-case checklist, and the overall core freeze remains **HOLD**.

## Commit and push

- Test commit: `3f9426606dfa26b40f63ca11aceecc1c1e93bd34` — `test(冻结): 验证无日期备忘不生成时间点`.
- The independent report and ledger/verdict synchronization are committed and pushed separately after review of the documentation diff.
