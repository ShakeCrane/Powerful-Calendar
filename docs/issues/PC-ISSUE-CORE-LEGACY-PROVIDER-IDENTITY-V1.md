# PC-ISSUE-CORE-LEGACY-PROVIDER-IDENTITY-V1 — Preserve old Calendar provider identity

- **Date:** 2026-11-02 (Asia/Shanghai)
- **Priority:** P1 — PHASE 1 data integrity
- **Status:** JVM/API verification PASS; Android device migration NOT TESTED
- **Owner decision:** Preserve the old Android Calendar provider identity on the migrated event.
- **Code commit:** `ce22edde011d860d268ba5fe33318590bfba285b`
- **Phase status:** PHASE 1 remains OPEN.

## Problem

The v0.1 migration created a stable `legacy-v01/v01:<row-id>` link but discarded an old row's
`source=android-calendar` and `external_id` identity. A later import of the same provider event
could therefore create a duplicate.

## Change

`LegacyImporter` now passes both the stable legacy identity and the old Android Calendar provider
identity to the existing `ImportExportFeatureApiV1.importStructuredEvent` operation. The API
adapter passes these identities to the existing Store transaction. The transaction creates the
event and links all identities atomically; if all known identities resolve to one existing event,
it fills in missing links and returns that event without replacing its data. If the IDs point to
different events, the import is rejected without writes. No second importer/store, database schema
change, or backup-format change was introduced.

## Verification

- `LegacyMigrationTest.legacyAndroidCalendarIdentityIsLinkedToTheMigratedEvent` verifies both IDs
  target the same event and a subsequent Android Calendar import is `ALREADY_IMPORTED` without a
  duplicate.
- `LegacyMigrationTest.legacyProviderIdentityJoinsAnAlreadyImportedCalendarEvent` verifies that a
  provider event imported first is retained unchanged while the migration adds its legacy ID.
- Focused `LegacyMigrationTest`: **6/6 PASS**, 0 failures/errors/skips; log SHA-256
  `0A3F54ACAE3FC0C7D30B36A7DAFD9F04095015E44735E05164D5AC0BEC7443A9`.
- Full forced offline gate: **934/934 JVM tests / 139 XML suites**, 0 failures/errors/skips; both
  source boundaries, Debug APK, lint (0 errors / 54 warnings), AndroidTest Kotlin compilation, and
  `git diff --check` passed. Full log SHA-256
  `8B6475049A2AED96C6920381347874C94AD3EE728D9AAE767AEBF0BDFDFF7092`; JUnit manifest SHA-256
  `F4E4295DE6002326475F2D510FB149B1E9230B2E47FA585A72E1FE585069A5B0`.
- Evidence directory: `.local/android-test/reports/phase1-core-feature-api-20261102/` (Git-ignored).
- No ADB device or AVD was available. Real Android migration and WebView execution remain
  **NOT TESTED**.

## Limits

This preserves the provider key for deduplication; it does not reconcile edits made independently
in the system Calendar after migration. The old database remains read-only. No old user data or
acceptance ledger was modified.
