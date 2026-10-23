# PC-ISSUE-042 — Conflicted draft remains available after restart

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

When an authorized AI batch encounters a stale full-replacement proposal, it must leave that proposal available for later human resolution. This report signs off the durable PowerStore contract only: the conflicted draft remains queryable with its content and queue identity after SQLite reopen. It makes no claim about presentation or interaction outside the store.

Before this regression, the AI runner test showed that it kept a conflicted card during the current process, but did not prove the retained proposal survived a subsequent database close and reopen.

## Change

Added `PowerStoreTest.aiConflictDraftRemainsAvailableForHumanResolutionAcrossRestart` in test commit `06c0534a9fc43c7cda243dbe431384c72aa3b161` (`test(冻结): 验证冲突草稿重启后保留`). The test creates a formal event, stages an AI replacement at revision 1, advances the formal event to revision 2, and runs the authorized batch. The batch publishes zero and keeps one card. After closing and reopening SQLite, the test verifies that the conflict query returns the same draft ID, event ID, queue order, operation, base revision and complete proposal form; the card also remains in the actionable draft collection. The formal title and completion key remain intact, and the AI lock is released.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database.

- Pre-commit targeted run: **1/1 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-042-conflict-draft-retention-targeted-precommit-20261023.log`, SHA-256 `27FCBBB172186ED037984407591F653CA64BC8588B1EBC5BD2599F20E85F9A78`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-042-targeted-precommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `83BDF3038928C44684A3FB2D9CAD6972A95F97365EF3BC1D8ACED5DC4245EE45`.
- Post-commit targeted run: **1/1 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-042-conflict-draft-retention-targeted-postcommit-20261023.log`, SHA-256 `AA47B7E69FFD12D50B2EA20E01414CFBD8F0C9BB7C66AB44884B583FDEF3D269`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-042-targeted-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `4EA1C0CC758E977FBB570CD556CD4D5669813C06B4062DBE11BA19EC0641619B`.
- Full command: ` .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 405/405 tests, zero failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes 404 tracked tests and the protected untracked `DraftDeckFeedbackTest.kt`; that file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-042-conflict-draft-retention-full-gradle-20261023.log`, SHA-256 `09984A1F258A9E7E9E1D20C1912957A6554A8F874AEEA707DFA1BB9487AB5782`.
- Full `PowerStoreTest` XML: 58 tests, zero failures/errors/skips; SHA-256 `07B3B69FF89CFD48BFDE74F2084F22A20464650A0E63DC2A1D969FB385ECAD3A`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-048` is marked **PASS** in the reconstructed candidate ledger for the durable PowerStore behavior. It remains `UNMAPPED` to the original 209-case checklist. The test does not establish UI behavior or physical-device behavior. Overall core freeze remains **HOLD**.

The linked structural-only ledger validation is `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-042.json`, SHA-256 `41D784376C9A0D9251188EAE9B78A0DE8F207F265D157306B7E950D552F84AFA`; it validates row structure only, not product behavior.

## Commit and push

- Test commit: `06c0534a9fc43c7cda243dbe431384c72aa3b161` — `test(冻结): 验证冲突草稿重启后保留` (pushed to `origin/main`).
- This report and the synchronized ledger/verdict updates are committed and pushed separately.
