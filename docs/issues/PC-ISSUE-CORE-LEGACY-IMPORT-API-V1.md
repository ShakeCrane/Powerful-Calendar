# PC-ISSUE-CORE-LEGACY-IMPORT-API-V1 — route legacy database writes through Import/Export API

Date: 2026-11-02 (China Standard Time)
Status: **IMPLEMENTED; JVM / source-boundary / build verification PASS; device NOT TESTED; PHASE 1 OPEN**

## Problem and result

After the user confirmed the legacy database migration, `PowerCalApp` called
`LegacyImporter.migrate(appContext, store)`. The importer opened the v0.1 SQLite database read-only,
but also began a migration batch and wrote every event directly through `PowerStore.importStructured`.
That bypassed `ImportExportFeatureApiV1` and its shared Event change notifications.

`PowerCalApp` now passes the existing `importExportApi`. `LegacyImporter` retains only Android
database access and v0.1 row mapping; batch creation, event import receipts, finalization, Store
transactions, and change notifications use the existing structured-import API. No new API method,
Store schema, backup format, confirmation step, or migration rule was added. The old source remains
read-only. Source-link retries still skip duplicates without emitting another Event change.

`StructuredCalendarEventV1` now accepts an explicit `enabled` value. Its default remains
`reminderMinutes >= 0`, preserving existing System Calendar import behavior. The legacy migration
passes `enabled = true`, preserving the old importer behavior even when the legacy reminder offset
is negative. The Event API adapter still applies the existing `PowerStore.importStructured`
transaction, including its existing import-time `createdAtMillis` policy.

## Verification

Focused command:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'dev.powerfulcalendar.core.api.v1.ImportExportFeatureApiV1Test' `
  --tests 'dev.powerfulcalendar.data.LegacyMigrationTest' `
  --tests 'dev.powerfulcalendar.ui.v2.LegacyHostMigrationTest' `
  --tests 'dev.powerfulcalendar.ui.v2.SystemCalendarImportTest' `
  --tests 'dev.powerfulcalendar.ui.v2.CoreApiAdapterTest' `
  --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
```

Result: **16/16 PASS** across 5 XML suites, 0 failures/errors/skips. This covers Import/Export
DTO enabled-state passthrough, new/repeated source links and change notifications, legacy migration
retries, missing source and missing-table handling, the Native Host confirmation/cancel flow, and
System Calendar structured import. Focused log SHA-256
`5F201A26440ACF2B1C4CD0B7F4FD612F5692F92B52288BB4CC68233817A3090D`; focused JUnit manifest SHA-256
`7273381722F92B67185C0CB38B1B7197224A20FF1570345367A10390B6202D36`.

Forced serial offline gate:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary `
  :app:testDebugUnitTest :app:assembleDebug :app:lintDebug `
  :app:compileDebugAndroidTestKotlin --offline --rerun-tasks `
  --max-workers=1 --console=plain --no-daemon
```

Result: `BUILD SUCCESSFUL`; **922/922 JVM tests across 139 XML suites**, 0 failures/errors/skips;
154 tasks. Both source boundaries, Debug APK assembly, lint (**0 errors / 54 warnings**),
AndroidTest Kotlin compilation, and `git diff --check` passed. The migration implementation was also
searched directly: `LegacyImporter` has no `PowerStore` dependency or direct Store batch/write calls,
and `PowerCalApp` passes the Import/Export API.

- Debug APK: 67,060,334 bytes, SHA-256
  `5A2B55DCF6208E0C4CC7165AA8F738DD1A936D179606B1AA9B87B10CC09382CC`.
- Full gate log SHA-256
  `41DBE7ACFCA8026894745B23BA68E6739356C8CAC6AA9548387010530A9179F3`.
- Full JUnit manifest SHA-256
  `87E32EDF3FA1108785009893B1BBED771F30E1E071F71CE829772AC39681BC7B`.
- Evidence is under `.local/android-test/reports/core-legacy-import-20261102/` (Git-ignored).
- AndroidTest was compiled, not run. `adb devices -l` listed no connected devices. Production
  WebView/Android migration behavior remains **NOT TESTED**.

## Failed attempt and correction

The first focused run had **2 failed test assertions**. A candidate API field and regression assertion
assumed that `PowerStore.importStructured` preserves a caller-supplied creation time; actual values
were the current import time. The same policy already applied to the old direct migration path.
Inspection of the Store transaction confirmed this behavior, so the unused creation-time field and
assumption were removed. No creation-time product behavior or Store transaction changed. The
corrected focused suite and full gate passed above.

- Initial failed focused log SHA-256
  `63F2F4211766D74469EA51674F84E944760320F6B571292745FE6D7B929D3D98`.
- Initial failed JUnit manifest SHA-256
  `646CF28368F6BA5D40091E8C8DC3E424AAB0FA58FDFE7C9B7B68DC8B55733F36`.
- Both are retained under
  `.local/android-test/reports/core-legacy-import-20261102/initial-failed-created-at-policy/`.

## Limits and delivery

The v0.1 schema interpretation and user confirmation remain in their existing migration/data
adapter. This change routes formal Store mutations through the shared capability; it does not
relocate old-schema parsing into a new Core runtime module or close Phase 1. Device/OEM migration
and old-alarm handoff remain unverified. The Native fallback and selected Skin are unchanged.

Code commit: `1635f90` (`分离：通过ImportExport API执行旧库迁移`). PHASE 1 remains open.

2026-11-02 compatibility follow-ups are tracked independently: [provider identity preservation](PC-ISSUE-CORE-LEGACY-PROVIDER-IDENTITY-V1.md)
and [priority-loss warning](PC-ISSUE-CORE-LEGACY-PRIORITY-LOSS-WARNING.md). They add links through
the same structured-import operation and disclose values that the current Core model cannot keep;
neither changes the Store schema or backup format.
