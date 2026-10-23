# PC-ISSUE-037 — Draft timestamps do not reorder the queue

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

Queue position is defined by `queueOrder`; edits may update a draft's `updatedAtMillis` without changing that position. This regression deliberately makes an older queue item newer by timestamp than the item behind it and checks the order before and after SQLite reopen.

The store implementation already selects actionable drafts with `ORDER BY queue_order ASC`, and `stageDraft` updates `updated_ms` without assigning another queue position. No production defect was found. The evidence gap was a deterministic test proving the timestamp and queue order can diverge while order remains stable.

## Change

Added `PowerStoreTest.updatingOlderDraftTimestampDoesNotChangeQueueOrderAcrossRestart` in test commit `0b19f017cae097232398166fb736b0b5cf0fe5a7` (`test(冻结): 验证时间戳不改变队列顺序`). It creates two drafts in order, waits for the second timestamp to advance, edits the first draft, and asserts the first draft now has the newer timestamp but still appears first. It then closes and reopens the isolated SQLite database and checks the same queue order, `queueOrder` values, and timestamps.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database.

- Pre-commit targeted run: **2/2 PASS**, including the new timestamp regression and existing queue-order persistence coverage.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-037-timestamp-does-not-reorder-queue-targeted-precommit-20261023.log`, SHA-256 `2010C351D82318FC0E3D1154BE91AD64FECD4CC8B1025117870DC8C5D2D2A673`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-037-targeted-precommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `6387BE9B527708765CB12052EA0EE406145EE0955799D81B5EEA912EFD4CE915`.
- Post-commit targeted run: **2/2 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-037-timestamp-does-not-reorder-queue-targeted-postcommit-20261023.log`, SHA-256 `63A3E07DF3E465791660753E0F9058FD6E84443A8C99B066852B31FA10E0C8FA`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-037-targeted-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `6F83AAF85D83065E32CAD4B6FBFF4889771D37C437727D91C6972530D16ED261`.
- Full command: `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 400/400 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes 399 tracked tests and the protected untracked `DraftDeckFeedbackTest.kt`; that protected file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-037-timestamp-does-not-reorder-queue-full-gradle-20261023.log`, SHA-256 `7FA263167BB49A5740AF1ABE46634C9E0EF4DC4AF8E6431B3A6AC9397BB931E8`.
- Full `PowerStoreTest` XML: 53 tests, zero failures/errors/skips; SHA-256 `872BE34030F89B7A5471EF19081D946E883A21CF6CBA25AC89509A2B3ACFB613`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-042` is marked **PASS** in the reconstructed candidate ledger. It remains `UNMAPPED` to the original 209-case checklist. Evidence covers queue ordering and timestamp persistence in PowerStore only; it does not claim UI or physical-device behavior. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-037.json` is structural-only and does not add product behavior evidence.

After this candidate, the ledger has 83 PASS, 0 FAIL, 9 BLOCKED, 111 NOT TESTED, and 6 SPEC UNRESOLVED rows; 58 reconstructed candidates have individual evidence. The overall core freeze remains **HOLD**.

## Commit and push

- Test commit: `0b19f017cae097232398166fb736b0b5cf0fe5a7` — `test(冻结): 验证时间戳不改变队列顺序` (pushed to `origin/main`).
- This report and the synchronized ledger/verdict updates are committed and pushed separately after documentation review.
