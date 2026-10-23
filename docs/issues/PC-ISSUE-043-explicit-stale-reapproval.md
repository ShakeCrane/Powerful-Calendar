# PC-ISSUE-043 — Stale replacement requires explicit reapproval

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

A full-replacement draft based on an old revision must not overwrite newer formal data by default. The PowerStore API must return a conflict and preserve the current snapshot until the caller supplies explicit stale approval. This report covers the durable store gate; it does not sign off any confirmation presentation.

The existing same-process test covered the approval flag. This regression extends the contract across SQLite reopen, where the conflict state and newer formal revision must remain intact before explicit reapproval.

## Change

Added `PowerStoreTest.staleDraftRequiresExplicitReapprovalAfterRestart` in test commit `07c7a30919fea42d090dcd7d646ea37322eb9fbd` (`test(冻结): 验证过期草稿显式重审`). The test stages a replacement at revision 1, changes the formal completion state to advance it to revision 2, and closes/reopens SQLite. A default commit returns `CommitOutcome.Conflict`; title, revision and completion key remain unchanged and the draft stays `CONFLICT`. Only a later call with `approveStale=true` and actor `human-review` commits the proposed full snapshot as revision 3. The completion-key change occurs with that explicit replacement.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database.

- Pre-commit targeted run: **1/1 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-043-explicit-stale-reapproval-targeted-precommit-20261023.log`, SHA-256 `27FCBBB172186ED037984407591F653CA64BC8588B1EBC5BD2599F20E85F9A78`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-043-targeted-precommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `152DB5F4203021BDACD20ED1E45F8C50AD113DE47B79C00EBDEAC4D738184CB1`.
- Post-commit targeted run: **1/1 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-043-explicit-stale-reapproval-targeted-postcommit-20261023.log`, SHA-256 `A6D078A431A9870A8E89C49529A5E1F692355BB15E73A9E305C913A6325643B6`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-043-targeted-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `7A015A456728DFFA6A4046127BEE6B154D9649F6209528EBA056DBF46432190A`.
- Full command: ` .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 406/406 tests, zero failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes 405 tracked tests and the protected untracked `DraftDeckFeedbackTest.kt`; that file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-043-explicit-stale-reapproval-full-gradle-20261023.log`, SHA-256 `EEFE6AE0ECBFAE4561FFE0EEDB8B31F7A6FAAF8BF846DEDB85C27377331775AA`.
- Full `PowerStoreTest` XML: 59 tests, zero failures/errors/skips; SHA-256 `63EEBDFDC3469FD5B072A142D20248EBA01F7F940AC97AEB689C470682D8CD23`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-050` is marked **PASS** in the reconstructed candidate ledger for the explicit stale-approval gate. It remains `UNMAPPED` to the original 209-case checklist. The overall core freeze remains **HOLD**.

The linked structural-only ledger validation is `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-043.json`, SHA-256 `4D6CCA9829F52A7A785F63CA6134451C36BDA6ED4DADD7145689E2E85A28FD79`; it validates row structure only, not product behavior.

## Commit and push

- Test commit: `07c7a30919fea42d090dcd7d646ea37322eb9fbd` — `test(冻结): 验证过期草稿显式重审` (pushed to `origin/main`).
- This report and the synchronized ledger/verdict updates are committed and pushed separately.
