# PC-ISSUE-003 — Nonempty restore feedback

Date: 2026-10-23 | Severity P2
Status: **FIXED IN CODE + REGRESSION VERIFIED**; SAF visual re-check pending.

## Root cause
Nonempty-backup restore refusal depended on parsing/localizing generic `IllegalArgumentException` text and the Compose front-end sometimes rendered `IllegalArgumentException` instead of a cause with actionable advice.

## Change
- The authoritative `PowerStore.restoreBackup()` now raises `NonEmptyRestoreTargetException` at **both** the preflight check and the transaction-local recheck. Neither check is removed; existing data is still never overwritten.
- `RestoreFailureExplanation` recognizes typed errors through wrapped cause chains. It tells users to export existing data first and restore only to a known-empty app database.
- Known invalid JSON, failed hash check, and SAF security permission issues have specific safe explanations. Unknown errors no longer disclose provider/file exception internals.
- Both normal restore and consented old-format restore entry points use the same explanation.

## Tests
- `RestoreFailureExplanationTest`: 5/5 PASS, covering typed & wrapped nonempty refusal, corrupt JSON, hash, security, and secret-leak prevention.
- `BackupEnvelopeTest.restoreIsRefusedWhenTheTargetAlreadyContainsUserData` tightened to assert the typed exception and unchanged identity count; passes.
- Full Android build, skin boundary, JVM tests and Lint: **BUILD SUCCESSFUL**.
- Full JVM suite: **336/336 PASS**, 0 failures/errors.
- Evidence: `.local/android-test/reports/freeze-20261023/issue003-targeted.log` and `issue003-full.log`.

## Limits
The repaired UI display has not been visually replayed via Android SAF on the emulator; that specific device-level check is NOT VERIFIED. No real-user database was cleared.
