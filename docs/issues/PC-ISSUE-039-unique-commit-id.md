# PC-ISSUE-039 — Separate commit operations receive unique commit IDs

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

Each accepted commit operation must have a non-empty, unique `commitId`. The identifiers must remain available in commit history after SQLite reopen. This candidate covers separate operations; retries of the same operation are handled by the adjacent idempotency contract.

PowerStore already allocates a UUID for a commit without a caller-supplied idempotency key and stores it under the `commits.commit_id` primary key. The evidence gap was a focused test proving distinct operations do not share an ID and that both IDs persist.

## Change

Added `PowerStoreTest.separateCommitOperationsReceiveUniqueCommitIdsAcrossRestart` in test commit `fb0d03bd74f3d3a0de81e1e99b1f4d9652af6d79` (`test(冻结): 验证独立提交使用唯一标识`). It commits an event and a later replacement, checks both returned IDs are non-empty and different, verifies both appear exactly once in history, then reopens SQLite and confirms the same two unique IDs remain. The existing `repeatedCommitReturnsTheOriginalOutcome` test also passes and confirms a retry of the same draft returns its original `commitId`.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database.

- Pre-commit targeted run: **2/2 PASS**, covering separate-commit ID uniqueness and same-operation retry identity.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-039-unique-commit-id-targeted-precommit-20261023.log`, SHA-256 `43DEDF1BD40270113C64531416CE1800058F09AE820B99A8C1DF33B40CC99D30`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-039-targeted-precommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `FA1351B65F1E543AF675051A24311E4AC711D6B53A7F909B00CEAD15909CB775`.
- Post-commit targeted run: **2/2 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-039-unique-commit-id-targeted-postcommit-20261023.log`, SHA-256 `8CF359ED5B02675EF95C78DC74B5A7A12A453F7BB676F4E7BC60AAAF362972E2`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-039-targeted-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `28430D5E11BB6DF2B5E0C91200D41AD441B0601100C0FA90F308081640E99D02`.
- Full command: `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 402/402 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes 401 tracked tests and the protected untracked `DraftDeckFeedbackTest.kt`; that protected file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-039-unique-commit-id-full-gradle-20261023.log`, SHA-256 `1E82BC51B97AB7FE9EC9F296C349A41E4312B25BAF2F5C872CD31BC2E1D3D769`.
- Full `PowerStoreTest` XML: 55 tests, zero failures/errors/skips; SHA-256 `5C8E233AA749FAEDB902579D484B958BB6847F1B867E77FFBC9EA3F00AC1256B`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-045` is marked **PASS** in the reconstructed candidate ledger. It remains `UNMAPPED` to the original 209-case checklist. Evidence covers commit ID uniqueness and persisted history in PowerStore only; it does not claim UI or physical-device behavior. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-039.json` is structural-only and does not add product behavior evidence.

After this candidate, the ledger has 85 PASS, 0 FAIL, 9 BLOCKED, 109 NOT TESTED, and 6 SPEC UNRESOLVED rows; 60 reconstructed candidates have individual evidence. The overall core freeze remains **HOLD**.

## Commit and push

- Test commit: `fb0d03bd74f3d3a0de81e1e99b1f4d9652af6d79` — `test(冻结): 验证独立提交使用唯一标识` (pushed to `origin/main`).
- This report and the synchronized ledger/verdict updates are committed and pushed separately after documentation review.
