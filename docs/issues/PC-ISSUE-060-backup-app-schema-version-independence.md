# PC-ISSUE-060 — Keep app and database backup versions independent

Date: 2026-10-23

Severity: P0 candidate

Status: **SUPPORTED-SCHEMA RESTORE REGRESSION PASS / ANDROID PROVIDER NOT TESTED**

## Scope and finding

`REC-P0-084` requires an app upgrade to use the backup database schema version as the compatibility contract, rather than treating the producer's app version as the schema version. The current implementation already keeps those fields separate: `BackupEnvelope.verify` checks the format version and supported database schema, and retains `appVersionCode` as metadata. The missing evidence was a restore test using an app version higher than the running build.

The new regression raises the exported envelope's `appVersionCode` above `BuildConfig.VERSION_CODE` while retaining the current supported database schema. Verification succeeds, and restore into an isolated SQLite store preserves the event and regenerates its desired alarm intent. No production defect was observed; this issue adds a test only.

## Candidate mapping

- `REC-P0-084`: `BackupEnvelopeTest.backupFromNewerAppVersionRestoresWhenDatabaseSchemaIsSupported` verifies the version axes and the restore path. The reconstructed candidate remains `UNMAPPED` to the original checklist.

## Verification evidence

Environment: Robolectric API 34 with isolated source and target SQLite databases; no Android device or document provider.

- Targeted command: `:app:testDebugUnitTest --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.backupFromNewerAppVersionRestoresWhenDatabaseSchemaIsSupported" --offline --rerun-tasks --console=plain` — **1/1 PASS**, zero failures/errors/skips.
- Targeted log `.local/android-test/reports/freeze-20261023/pc-issue-060-app-version-schema-targeted-20261023.log`, SHA-256 `99DBB5932D3D8244A5AE73074A6F6409A00484E7C47B0EFF9C8F0167275F8C4A`.
- Preserved targeted JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-060-targeted-junit/TEST-dev.powerfulcalendar.core.BackupEnvelopeTest.xml`, SHA-256 `92CEF9655ADEF7FFAA8111B30506F2C10C02A7D75ABE3D8D57DC8F5E2F1FA6CA`; one test, zero failures/errors/skips.
- Full command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 413/413 tests across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. This includes the protected untracked `DraftDeckFeedbackTest.kt` (412 tracked + 1 protected untracked).
- Full log `.local/android-test/reports/freeze-20261023/pc-issue-060-full-gradle-20261023.log`, SHA-256 `01DBFA3453683AE661611AD0F60C912C47670E71387C52FC94E95DD49C0B545D`.
- Preserved full JUnit `BackupEnvelopeTest` XML `.local/android-test/reports/freeze-20261023/pc-issue-060-full-junit/TEST-dev.powerfulcalendar.core.BackupEnvelopeTest.xml`, SHA-256 `6940193A90D0B7C441A96F048B0B0D0F900E5663C0E8AFF10A1F344A65F36154`.
- Debug APK SHA-256 `1E8332E7A18D71A1516D3E6B3197244738418E7703071D2EBCEF1363938B4BEF`.

## Candidate sign-off and limits

`REC-P0-084` is PASS for backup verification and SQLite restoration when the producing app version is higher than the running app and the database schema is supported. Android SAF provider behavior, physical devices, and historical checklist mapping remain unverified. Overall core freeze remains **HOLD**.

## Git

- Test-only commit: `ae0335479df24414c9d9b92696edafbc1efd6d24`
- Message: `test(备份): 区分应用与数据库版本`
- Status: pushed to `origin/main`.
- No production code, UI, user database, or protected untracked asset was changed or staged.
- This independent report and synchronized freeze documentation are committed separately.
