# PC-ISSUE-CORE-CALENDAR-API-V1 — bounded timeline/month queries

- **Status:** PASS for the API and Host integration slice; PHASE 1 remains open.
- **Date:** 2026-11-01.
- **Code commit:** `313562e386a14334314b7b2dc0a405e4e0465ad7`, fast-forward pushed to `origin/feat/skin-first-separation` from fetched baseline `c17275e7a615f6d66d1424695f8211ce5f55d884`.
- **Contract:** `:core-api` `CalendarFeatureApiV1`; app adapter `SnapshotCalendarFeatureApiV1`.

## Problem and expected behavior

Native and Web timeline/month Host projections consumed the full `TemporalRecord` list. Recurrence expansion and visible-window filtering happened in each projection. Native month navigation kept the records supplied at initial composition even when the user browsed to another month. The expected behavior is a shared versioned Core query for each visible calendar window, with matching event owners and existing recurrence, date precision, zone, override, and interval semantics preserved.

## Change

- Added `CalendarWindowQueryV1` with a non-empty half-open instant range and validated IANA `zoneId`. `CalendarFeatureApiV1.queryWindow` filters an immutable `EventQuerySnapshotV1`, returns each matching owner once, and preserves its `featureRevision`.
- The adapter delegates effective occurrence matching to the existing `RecurrenceEngine`. Dated non-repeating MEMOs use inclusive date-range overlap; undated MEMOs are omitted. Date-only deadlines are evaluated against visible calendar dates without converting them into timed deadlines.
- Native and Web timeline/month projections use the same Host-created `PowerCalCoreFeatureApiV1.calendar` service. Timeline queries retain the existing 17-day horizon; month queries use the existing Monday-first 42-day grid. Native month navigation asks for the window of each navigated month. A synchronous Web Host refresh queries its live timeline/month window if Compose still holds the previous remembered window.
- Existing projection geometry, occurrence display, and renderer presentation rules remain in use. No Store writes, schema, backup format, recurrence policy, or frozen Core behavior changed.

The Calendar query is snapshot-backed, not database paging. `PowerCalApp.reload()` still loads the full formal-event snapshot into the Host. Calendar projection callers receive the window-filtered owners, but other runtime projection boundaries and conversion from DTOs to existing Core records remain open PHASE 1 work.

## Verification

Focused command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*CalendarFeatureApiV1Test' --tests '*EventFeatureApiV1Test' --tests '*NativeTimelineProjectionTest' --tests '*NativeMonthProjectionTest' --tests '*CoreApiAdapterTest' --offline --rerun-tasks --max-workers=1 --console=plain
```

The selected run passed **16/16 tests** across five XML suites: Calendar API 3, Event API 5, Native timeline 4, Native month 3, and the shared Core API adapter integration 1. Test IDs: `windowIncludesEffectiveOccurrences`, `dateOnlyDeadlinesAndDateRangeMemosUseTheVisibleCalendarDates`, `calendarWindowRejectsEmptyOrInvalidZoneRequests`, `oneFeatureApiSupportsQueryCreateEditDeleteAndIdempotentReplay`, `timelineReplacementChecksRevisionAndReplaysOneDraftReceipt`, `formalSearchPreservesMatchingOrderAndReturnsBoundedDisplayHits`, `occurrenceCompletionUsesExpectedRevisionStateAndOneChangeReceipt`, `recurringCompletionUsesOriginalSlotKeysAndCanReopenOneSlot`, `recurringRowsKeepDisplayOverridesAndResolveToOriginalOwnerSlot`, `laneProjectionRetainsTrueIntervalsAndDateOnlyDeadlinePrecision`, `memoIsNotGivenMinuteScaleLaneAndEmptyStateReflectsFormalRecords`, `dateOnlyDeadlineWindowUsesTheEventZoneWhenTheCameraZoneDiffers`, `projectionPreservesCrossDayRowsOverrideTextAndOriginalOccurrenceKey`, `openTargetsResolveOwnerAndOriginalSlotAndClearWhenMonthChanges`, `nonRepeatingOpenTargetDoesNotInventOccurrenceScope`, and `nativeAndWebShareSameCoreApi`. Final focused log: `.local/android-test/reports/calendar-feature-api-20261101/focused-final.log`, SHA-256 `D3F2B1E0CFBFB7A9DC5C78C56673E96E9D4201602111B267A1B752C56BB6240D`.

Forced serial gate:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain
```

Result: **BUILD SUCCESSFUL**, **831/831 JVM tests**, 131 XML reports, 0 failures/errors/skips, 154/154 actionable tasks executed. Both source boundaries, Debug APK assembly, `lintDebug` (**0 errors / 54 warnings**), AndroidTest Kotlin compilation, and `git diff --check` passed.

Environment: Windows PowerShell, Android SDK at `E:\android-sdk`, offline Gradle, Robolectric SDK 34 for Store-backed fixtures. This run did not use an emulator or physical device.

- Full gate log: `.local/android-test/reports/calendar-feature-api-20261101/full-gate.log`, SHA-256 `D7C41F4B4D3CA6436B299E46B537A231F3986658C63FB314D08C2C6F5C1B1C1A`.
- Debug APK: `66,765,422` bytes, SHA-256 `8AA317888327AD461AAB648A4860C833235A7C10E9BA48F9D7BC8897A3B9014E`.
- The first focused run is retained at `focused.log`, SHA-256 `2AD58E86E0E1CF320B870E58219635DB05D0E295E9263FA235483FD32C88AF3B`. It exposed a missing non-repeating Memo date-range predicate and a Robolectric SQLite fixture path that was too long for Windows. The date overlap check and short test/database names fixed both; `focused-rerun.log` and `focused-final.log` passed. The final run also covers per-month Native queries and live Web window refresh integration.

All logs and XML remain under ignored `.local/android-test/reports/calendar-feature-api-20261101/`. No ADB/device or user database was used. Android instrumentation, packaged WebView behavior, physical/OEM behavior, runtime projection extraction, and Native/Skin parity are **NOT TESTED** by this slice. This report does not change the Release QA HOLD or authorize a skin cutover.
