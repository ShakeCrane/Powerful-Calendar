# PC-ISSUE-055 — Keep the current runtime event snapshot in SQLite

Date: 2026-10-23

Severity: P0 candidate

Status: **POWERSTORE / SQLITE PATH PASS / OTHER PROJECTIONS NOT TESTED**

## Scope and finding

`REC-P0-081` requires a persisted runtime event to remain represented by the durable SQLite store as the authoritative current snapshot. The existing PowerStore regression creates and commits three successive revisions, closes and reopens the database, then verifies exactly one current formal snapshot at revision 3 and all three commit-history records. It passed on the current code/test baseline. No source or test change was needed; this closes a specific storage-evidence gap, not a reproduced defect.

The test exercises the PowerStore/SQLite path only. It does not audit UI, AI, ICS, or Android AlarmManager projections for independent writes; those paths are not signed by this issue.

## Candidate mapping

- `REC-P0-081`: `PowerStoreTest.multipleCommitsKeepOneCurrentFormalSnapshotAcrossRestart` verifies one current snapshot before and after SQLite reopen, with the latest title/revision and the full three-entry history retained. The candidate remains `UNMAPPED` to the original checklist.

## Verification evidence

Environment: Robolectric API 34, isolated PowerStore SQLite fixture; no Android device.

- Targeted command: `:app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerStoreTest.multipleCommitsKeepOneCurrentFormalSnapshotAcrossRestart" --offline --rerun-tasks --console=plain` — **1/1 PASS**, zero failures/errors/skips.
- Log `.local/android-test/reports/freeze-20261023/pc-issue-055-runtime-event-sqlite-targeted-20261023.log`, SHA-256 `0204D79B2E0FA988A6C2BD97E810C52335D59256CBD910B38582B9208422B2B1`.
- JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-055-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `DA3564895E0445B68F38D9B393C95BA8867F2DD939BB3F133F0A8DD507AFDAA9`; 1 test, 0 failures/errors/skips.
- Tested code/test baseline: `c2f2ad5efb5b84b887fb38b4ee03916c0c23906e`; latest full Gradle on that baseline passed **409/409** across 55 suites, lint 0 errors / 55 warnings. Full log SHA-256 `EACE03207765FED33CD83882FAD41F9CC477BB48BD26EA5CF14D1B6FD1A07FEE`; `PowerStoreTest` XML SHA-256 `1B4C2D53312F5D81119384965BF64410D32401256EFFDC6BBDDFE5992C62A0EE`; Debug APK SHA-256 `80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`.
- Structural-only ledger validation: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-055.json`, SHA-256 `CC388AA2BC2725817CB093C21289F6D6B3E850C54D3E9A8AB653798BBD24C3AE`; ledger SHA-256 `B7E704E9A4BCC2B1093C44577E558527C3A69917E2A2F9FBA2F1118C5BF41CD6`. It confirms 209 unique candidate IDs and 103/0/9/90/7 status counts. Structural validation does not test product behavior or prove historical mapping.

## Candidate sign-off and limits

`REC-P0-081` is PASS for the tested formal PowerStore/SQLite current-snapshot and history path. Projection writers, physical-device behavior, and the missing historical mapping remain outside this evidence. Overall core freeze remains **HOLD**.

## Git

This sign-off updates the independent report, acceptance ledger, and synchronized freeze documentation; it is committed separately using a Chinese conventional commit and pushed to `origin/main`.
