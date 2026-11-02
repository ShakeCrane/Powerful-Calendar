# PC-ISSUE-CORE-LEGACY-PRIORITY-LOSS-WARNING — Explain legacy priority loss

- **Date:** 2026-11-02 (Asia/Shanghai)
- **Priority:** P1 — PHASE 1 migration compatibility
- **Status:** JVM/API verification PASS; Android device migration NOT TESTED
- **Owner decision:** Do not add a priority field; explicitly report old values that are not kept.
- **Code commit:** `ce22edde011d860d268ba5fe33318590bfba285b`
- **Phase status:** PHASE 1 remains OPEN.

## Problem

The v0.1 database has a `priority` column, while the current four-kind event contract has no
priority field. The importer previously discarded that information without telling the user.

## Change

The read-only legacy importer now aggregates every nonblank old priority value, including `NORMAL`,
and adds a migration warning with the value and count. `PowerCalApp` includes the first migration
warning in its completion feedback. The priority is not copied into a new field or inferred into
another event property; the current event model remains authoritative.

## Verification

- `LegacyMigrationTest.legacyRowsKeepTheirOwnMetadata` checks that `HIGH×1` is reported.
- `LegacyMigrationTest.legacyAndroidCalendarIdentityIsLinkedToTheMigratedEvent` checks that the
  default `NORMAL×1` value is reported too.
- Focused `LegacyMigrationTest`: **6/6 PASS**, 0 failures/errors/skips; log SHA-256
  `0A3F54ACAE3FC0C7D30B36A7DAFD9F04095015E44735E05164D5AC0BEC7443A9`.
- Full forced offline gate: **934/934 JVM tests / 139 XML suites**, 0 failures/errors/skips; both
  source boundaries, Debug APK, lint (0 errors / 54 warnings), AndroidTest Kotlin compilation, and
  `git diff --check` passed. Full log SHA-256
  `8B6475049A2AED96C6920381347874C94AD3EE728D9AAE767AEBF0BDFDFF7092`; JUnit manifest SHA-256
  `F4E4295DE6002326475F2D510FB149B1E9230B2E47FA585A72E1FE585069A5B0`.
- Evidence directory: `.local/android-test/reports/phase1-core-feature-api-20261102/` (Git-ignored).
- No ADB device or AVD was available. Real Android migration feedback remains **NOT TESTED**.

## Limits

Values are grouped for the migration warning and are not retained in the Core event model. This is
the owner-approved behavior; migration does not change the old read-only database or backup format.
