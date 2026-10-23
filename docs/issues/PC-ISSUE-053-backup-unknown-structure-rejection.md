# PC-ISSUE-053 — Reject unknown backup fields and future versions

Date: 2026-10-23

Severity: P0 candidate

Status: **BACKUP PARSER REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

The native backup reader must fail closed when it encounters unknown top-level data or a format/schema version it cannot safely interpret. The existing `BackupEnvelopeTest` regressions directly cover these boundaries; all three were rerun on the current code/test baseline. No implementation change was needed.

## Candidate mapping

- `REC-P0-089`: `legacyImportRejectsUnknownFieldsButPreservesItsNextOrder` adds `futureField` to a legacy v2 backup and requires rejection. The same isolated target subsequently accepts the valid legacy input and preserves its `nextOrder`, which also demonstrates the rejected input did not occupy the target.
- `REC-P0-090`: `unknownFutureBackupFormatVersionIsRejected` rejects `backupFormatVersion=99`; `backupFromANewerSchemaIsRejectedInsteadOfGuessed` rejects a `databaseSchemaVersion` newer than the installed schema. Both assert the target has no imported identities.

The two candidates remain `UNMAPPED` to the unavailable original 209-case checklist.

## Verification evidence

Environment: Robolectric API 34, isolated PowerStore SQLite fixtures; no Android device.

- Targeted command: `:app:testDebugUnitTest --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.legacyImportRejectsUnknownFieldsButPreservesItsNextOrder" --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.unknownFutureBackupFormatVersionIsRejected" --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.backupFromANewerSchemaIsRejectedInsteadOfGuessed" --offline --rerun-tasks --console=plain` — **3/3 PASS**, zero failures/errors/skips.
- Log `.local/android-test/reports/freeze-20261023/pc-issue-053-backup-unknown-structure-targeted-20261023.log`, SHA-256 `029F8416FCDB443748A36E737559632B8F8C90BB598D6732C5C6B0275AFDA423`.
- Targeted JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-053-targeted-junit/TEST-dev.powerfulcalendar.core.BackupEnvelopeTest.xml`, SHA-256 `B117D99E56EFE2C8FE4612DE6994D3417E1C5635E955F6B343E45C1CA2BA4946`; 3 tests, 0 failures/errors/skips.
- All three methods are also present and pass in the current full Gradle baseline's 22-test `BackupEnvelopeTest` XML, SHA-256 `DA216CDE3DE32BAC4A3D460E9E9F7684B7078AD389165E7FE78BCDC92D4EE500`.
- Current code/test baseline `c2f2ad5efb5b84b887fb38b4ee03916c0c23906e` has a full Gradle result of **409/409 PASS** across 55 suites, lint 0 errors / 55 warnings. Full log SHA-256 `EACE03207765FED33CD83882FAD41F9CC477BB48BD26EA5CF14D1B6FD1A07FEE`; Debug APK SHA-256 `80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`.
- The structural-only ledger validation includes this sign-off: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-053.json`, SHA-256 `F89EB3716E146E5FFB58AE0820070A2B72D92B07A73480708BCAEC81D3130E70`; ledger SHA-256 `E2E45D187C2BFEDC95187DB73075B72B6AE0BADD749154A626298CB76CDD02EC`. It confirms 209 unique candidate rows and 100/0/9/93/7 status counts; structural validation does not test app behavior or recover historical mapping.

No product source, test source, UI, or user data changed for this sign-off. No physical-device restore flow is claimed.

## Candidate sign-off and limits

`REC-P0-089` and `REC-P0-090` are PASS in the reconstructed ledger for the tested legacy unknown-field and future version rejection paths. Overall core freeze remains **HOLD**.

## Git

This sign-off changes only the independent report, acceptance ledger, and synchronized freeze documentation. It is committed separately with a Chinese conventional commit and pushed to `origin/main`.
