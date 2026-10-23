# PC-ISSUE-027 — Rejected structured import does not consume an event sequence

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

The structured import path must validate an incoming event before allocating its permanent `eventSeq`. A rejected record must leave the identity allocator unchanged, so a corrected retry using the same `(source, externalId)` can import exactly once without creating a sequence gap.

Code review of `PowerStore.importStructured` confirmed that candidate validation precedes `allocateIdentity`. This batch found no production defect. It closes a case-specific regression evidence gap with a test that checks rejection, same-identity retry, and persistence after reopening SQLite. It does not establish a mapping to any missing historical acceptance ID.

## Change

Added `PowerStoreTest.rejectedStructuredImportDoesNotConsumeSequenceBeforeValidRetry` in the test-only commit `5c8d6c50f8d4c710b65c4a6c26da6b6be4b22221` (`test(冻结): 验证拒绝导入不消耗事件序号`). The test:

1. Imports an Activity without an end time and expects `Rejected`.
2. Verifies identity count remains zero, next sequence remains 1, and sequence 1 has no record.
3. Repairs the record and retries the same source and external ID; the import succeeds as sequence 1, with one history entry.
4. Reopens the same SQLite database and verifies event kind, exact interval, identity count, sequence watermark, and history.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database. No emulator or physical device was used.

- Post-commit targeted Gradle test: **1/1 PASS**, zero failures/errors/skips. Log: `.local/android-test/reports/freeze-20261023/pc-issue-027-rejected-import-sequence-targeted-postcommit-20261023.log`, SHA-256 `E78E9DA3B7485D19D0092C527FE5B292FA1A4EDD0A3B5EF24A8EB98E1BB48660`.
- Targeted JUnit XML: `.local/android-test/reports/freeze-20261023/pc-issue-027-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `95058B1CFD08B90F1DCE6EE1D12ED403DA179907F26D9E09E162BED807079227`.
- Full command: `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 390/390 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This working-tree total includes the protected untracked test `DraftDeckFeedbackTest.kt` (389 tracked tests + 1 protected untracked test); that file was not modified or staged.
- Full Gradle log: `.local/android-test/reports/freeze-20261023/pc-issue-027-rejected-import-sequence-full-gradle-20261023.log`, SHA-256 `EA57B13C40C36C8749168E400C6D92FFEDADEEA211D3DA798DA87A144ACE8570`.
- Full `PowerStoreTest` XML: `.local/android-test/reports/freeze-20261023/pc-issue-027-full-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `F585260CC73AC81659227AEFF5DA805EE8E28ADDB9BDC1446920F97EA57DB3EB`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-028` is marked **PASS** in the reconstructed candidate ledger. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-027.json` has SHA-256 `99D2A6F8A74F935E14D5D303148B045F93875AE2556BE85526DBD08B0CB209BA`; it checks the ledger structure and explicitly does not test product behavior. The candidate remains `UNMAPPED` to the original 209-case checklist. This is JVM/SQLite evidence only; external importer interoperability, Android device behavior, and historical case mapping are not covered.

Overall core freeze remains **HOLD** because the original 209 case IDs are unavailable, other candidates and platform gates remain open, and device-level acceptance is incomplete. No UI work is included in this issue.

## Commit and push

- Test commit: `5c8d6c50f8d4c710b65c4a6c26da6b6be4b22221` — `test(冻结): 验证拒绝导入不消耗事件序号`.
- The independent report and ledger/verdict synchronization are committed and pushed separately after review of the documentation diff.
