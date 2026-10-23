# PC-ISSUE-001 — ICS recurrence export repair

Date: 2026-10-23 | Severity: P1
Status: **FIXED IN CODE + UNIT VERIFIED**; independent calendar-client compatibility remains unverified.

## Root cause

Activity export projected `TemporalRecord` to legacy `CalendarEvent` and silently lost `recurrence`. `IcsCodec.export()` therefore wrote one VEVENT without RRULE, flattening daily series to a single instance.

## Repair

- New `IcsCodec.exportActivities(records)` preserves the authoritative recurring Activity model, including RRULE frequency/interval/count/until, weekly BYDAY, EXDATE, and timezone-aware local start/end.
- The SAF path now validates/serializes **before** opening the output stream, catches unsupported cases, and reports clear errors.
- Unsupported effective ranges, moved/overridden occurrences, unrepresentable monthly dates, and contradictory rules fail closed instead of silently losing events.
- Only Activity export is claimed. Native Envelope remains required to retain alarms, deadlines, memos, progress, and full PowerCal metadata.

## Evidence

Prior sample `.local/android-test/reports/regression-20261023/PowerfulCalendar-activities.ics`: two VEVENTs, zero RRULEs although a daily series was present.

New `IcsExportRecurrenceTest`: **6/6 PASS** (daily interval, weekly count/BYDAY/EXDATE, UTC UNTIL, DST-zone start, unsupported overrides/active range, invalid monthly dates/kinds).
Full Gradle `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`: BUILD SUCCESSFUL.
Logs: `.local/android-test/reports/freeze-20261023/issue001-targeted.log` and `issue001-full.log`.

## Scope limit

The new APK still needs Android SAF real-file export and independent external calendar import acceptance. Those are NOT VERIFIED; do not call 209-case release gate passed.
