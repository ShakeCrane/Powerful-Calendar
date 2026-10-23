# PC-ISSUE-040 — Explicit commit retry is idempotent across restart

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

Retrying a committed draft with the same explicit `idempotencyKey` must return the original commit result and must not add history, increment the event revision, inflate the published counter, or create another durable schedule intent. The guarantee must survive SQLite reopen.

PowerStore checks the draft and key against the commit ledger before applying the mutation. The evidence gap was a case-specific restart regression for the caller-supplied key. This test covers durable PowerStore effects; it does not test Android AlarmManager or OS-level delivery.

## Change

Added `PowerStoreTest.explicitCommitIdRetryAfterRestartDoesNotRepeatSideEffects` in test commit `2f7b96bcf621e606c2f6470f7d81ee839b3a8909` (`test(冻结): 验证提交重试幂等性`). It commits an enabled Alarm with a fixed idempotency key, confirms one history row, one published count, one pending schedule intent and no pending draft, then closes and reopens SQLite. Retrying the same draft with the same key returns the original `commitId`, revision and event identity with `duplicate=true`; every checked durable count and intent remains unchanged. The existing normal retry test also passes.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database.

- Pre-commit targeted run: **2/2 PASS**, covering explicit-key retry and ordinary same-draft retry.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-040-idempotent-commit-retry-targeted-precommit-20261023.log`, SHA-256 `2B2F1657DB15F9874550E3FCA0B879FDFED1B29E4CD9F65A68E708751A79B23F`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-040-targeted-precommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `281117ED9FB967583900D9E1D8DCCAC3D91F2CC6951385EBAE72371D2428E801`.
- Post-commit targeted run: **2/2 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-040-idempotent-commit-retry-targeted-postcommit-20261023.log`, SHA-256 `AA47B7E69FFD12D50B2EA20E01414CFBD8F0C9BB7C66AB44884B583FDEF3D269`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-040-targeted-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `478E47D32A0BE4E032FC1D8C0F8F90E7E681536B74662582CA1D235FD2EF9090`.
- Full command: `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 403/403 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes 402 tracked tests and the protected untracked `DraftDeckFeedbackTest.kt`; that protected file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-040-idempotent-commit-retry-full-gradle-20261023.log`, SHA-256 `51D83DBE2B6E9129931CFD91ACDCB3FB9DB84D87F6E6237F29597987DC7CC36B`.
- Full `PowerStoreTest` XML: 56 tests, zero failures/errors/skips; SHA-256 `4B419D62DFC8267929986986710907EF00A717CBECFA5FE3A5E8A8C3E09D1B7C`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-046` is marked **PASS** in the reconstructed candidate ledger. It remains `UNMAPPED` to the original 209-case checklist. Evidence covers durable PowerStore retry effects only; actual Android scheduling/delivery and physical-device behavior remain separate. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-040.json` is structural-only and does not add product behavior evidence.

After this candidate, the ledger has 86 PASS, 0 FAIL, 9 BLOCKED, 108 NOT TESTED, and 6 SPEC UNRESOLVED rows; 61 reconstructed candidates have individual evidence. The overall core freeze remains **HOLD**.

## Commit and push

- Test commit: `2f7b96bcf621e606c2f6470f7d81ee839b3a8909` — `test(冻结): 验证提交重试幂等性` (pushed to `origin/main`).
- This report and the synchronized ledger/verdict updates are committed and pushed separately after documentation review.
