# PC-ISSUE-059 — Enforce a strict backup input byte limit

Date: 2026-10-23

Severity: P0 candidate

Status: **BOUNDED INPUT REGRESSION PASS / ANDROID SAF PROVIDER NOT TESTED**

## Scope and finding

`REC-P0-108` requires rejecting an oversized backup before parsing its JSON. The app already had a 64 MiB guard, but its 64 KiB stream reads appended a whole chunk before checking the limit. That allowed the in-memory input buffer to exceed the configured limit by as much as one read chunk, and the behavior had no direct boundary regression.

`BackupEnvelope.readUtf8Bounded` now limits each read to the remaining allowance plus one byte. An exact-limit input is accepted; the first byte beyond the limit causes an exception before the method returns text for JSON parsing. The SAF input path delegates to this bounded core reader. The production default remains `BackupEnvelope.MAX_BYTES` (64 MiB).

This changes backup input handling only. It does not change event data, backup format, or UI layout/interaction.

## Candidate mapping

- `REC-P0-108`: `BackupEnvelopeTest.boundedBackupReaderAcceptsLimitAndStopsOversizedInputAtFirstExcessByte` verifies exact-limit acceptance and rejection after consuming only `maxBytes + 1` bytes. The reconstructed candidate remains `UNMAPPED` to the original checklist.

## Verification evidence

Environment: JVM/Robolectric unit test with bounded in-memory `InputStream`; no Android device or OS SAF provider.

- Targeted command: `:app:testDebugUnitTest --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.boundedBackupReaderAcceptsLimitAndStopsOversizedInputAtFirstExcessByte" --offline --rerun-tasks --console=plain` — **1/1 PASS**, zero failures/errors/skips.
- Targeted log `.local/android-test/reports/freeze-20261023/pc-issue-059-backup-size-cap-targeted-20261023.log`, SHA-256 `D5DCCE52E1E0B6FA32314F2EAF1FFBFC65F834F76860F27EB05A79C299E6C860`.
- Preserved targeted JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-059-targeted-junit/TEST-dev.powerfulcalendar.core.BackupEnvelopeTest.xml`, SHA-256 `A7D77CDDC5302324161B2E61E7A493E86986EF483DAAE6929F9891C9E3B26248`; one test, zero failures/errors/skips.
- Full command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 412/412 tests across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. This includes the protected untracked `DraftDeckFeedbackTest.kt` (411 tracked + 1 protected untracked).
- Full log `.local/android-test/reports/freeze-20261023/pc-issue-059-full-gradle-20261023.log`, SHA-256 `719C3C4D568F800BB8F48363592049F627216D1D416EF589E9656217D0802A1F`.
- Preserved full JUnit `BackupEnvelopeTest` XML `.local/android-test/reports/freeze-20261023/pc-issue-059-full-junit/TEST-dev.powerfulcalendar.core.BackupEnvelopeTest.xml`, SHA-256 `4E67C31DD333B9B110BB8D347782D3D14C12FA41068179A79FDD5D4A2AF79530`.
- Debug APK SHA-256 `1E8332E7A18D71A1516D3E6B3197244738418E7703071D2EBCEF1363938B4BEF`.

## Candidate sign-off and limits

`REC-P0-108` is PASS for the bounded stream-reader behavior and its app call site. The OS SAF provider path, physical devices, OEM behavior, and historical checklist mapping remain unverified. Overall core freeze remains **HOLD**.

## Git

- Code/test commit: `a03ada74e2413303b29d58325d24d5eb7081af3f`
- Message: `fix(备份): 限制超大文件读取`
- Status: pushed to `origin/main`.
- The test and code commit contains only `BackupEnvelope.kt`, `PowerCalApp.kt` backup stream wiring, and `BackupEnvelopeTest.kt`; protected untracked assets were not staged.
- This independent report and synchronized freeze documentation are committed separately.
