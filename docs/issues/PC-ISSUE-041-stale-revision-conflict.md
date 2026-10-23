# PC-ISSUE-041 — Stale base revision returns conflict without overwriting

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

When a replacement proposal's `baseRevision` is stale, PowerStore must return a conflict and leave the current formal snapshot unchanged. The UI presentation of that conflict is outside this candidate's scoped evidence.

The existing commit path checks the current formal revision before applying a stale proposal. The evidence gap was a focused test across SQLite reopen, where the newer formal snapshot includes a persisted completion mutation and the old proposal must not overwrite either its content or completion state.

## Change

Added `PowerStoreTest.staleBaseRevisionReturnsConflictWithoutOverwritingAcrossRestart` in test commit `e8ee3e5aad3c8a8ef353d84781d3cff40f2d4127` (`test(冻结): 验证过期修订冲突不覆盖`). It commits an event at revision 1, stages a replacement against that revision, advances the formal record to revision 2 through completion tracking, then closes and reopens SQLite. Committing the stale proposal returns `CommitOutcome.Conflict`; the revision 2 title and completion key remain, and commit history does not grow. The existing stale-approval regression also passes.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database.

- Pre-commit targeted run: **2/2 PASS**, covering stale conflict across restart and the existing explicit-approval path.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-041-stale-revision-conflict-targeted-precommit-20261023.log`, SHA-256 `27FCBBB172186ED037984407591F653CA64BC8588B1EBC5BD2599F20E85F9A78`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-041-targeted-precommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `147D615FCB0B779AD719CCD8E87C6447C63F204A13FFB704DAD43821B4388A4B`.
- Post-commit targeted run: **2/2 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-041-stale-revision-conflict-targeted-postcommit-20261023.log`, SHA-256 `DF28959E2EF61401DEC8B987114261D23711F088394B186CFAA64BA84F3C406A`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-041-targeted-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `19EFF9C3BD504873B74436ED3A3A48B2C206F563F5B10959C9C8D9FE26201E92`.
- Full command: ` .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 404/404 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes 403 tracked tests and the protected untracked `DraftDeckFeedbackTest.kt`; that protected file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-041-stale-revision-conflict-full-gradle-20261023.log`, SHA-256 `02E42FD3984B649B0AC2DC6DEF8B12855A7632DA075B9E56DEEE2CF80B713AB8`.
- Full `PowerStoreTest` XML: 57 tests, zero failures/errors/skips; SHA-256 `14273D8082573684B443FCA1D0F0A741CA0BF4EE5B3869774ED9FAF73ECD7CD3`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-047` is marked **PASS** in the reconstructed candidate ledger with its expected result narrowed to the core `CommitOutcome.Conflict` and preservation contract. It remains `UNMAPPED` to the original 209-case checklist. Evidence covers PowerStore storage only; showing conflict details in the editor and physical-device behavior are not signed off. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-041.json` is structural-only and does not add product behavior evidence.

After this candidate, the ledger has 87 PASS, 0 FAIL, 9 BLOCKED, 107 NOT TESTED, and 6 SPEC UNRESOLVED rows; 62 reconstructed candidates have individual evidence. The overall core freeze remains **HOLD**.

## Commit and push

- Test commit: `e8ee3e5aad3c8a8ef353d84781d3cff40f2d4127` — `test(冻结): 验证过期修订冲突不覆盖` (pushed to `origin/main`).
- This report and the synchronized ledger/verdict updates are committed and pushed separately after documentation review.
