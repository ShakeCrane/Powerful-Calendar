# PC-ISSUE-001 — ICS recurrence export repair

Date: 2026-10-23 | Severity: P1
Status: **PARTIAL / HOLD** — code repair, real Android SAF export, DST-bearing SAF fixture, independent parser, and semantic serialize/parse round-trip pass. Import into a separate calendar client and physical-device acceptance remain unverified.

## Root cause

The original Activity export projected `TemporalRecord` to legacy `CalendarEvent` and silently lost `recurrence`, producing a single VEVENT with no RRULE. Follow-up RFC review found that the repaired recurring export emitted `TZID` values without corresponding `VTIMEZONE` definitions, leaving external clients without the rules needed to interpret local recurring times and DST.

## Repair

- `IcsCodec.exportActivities(records)` preserves the Activity recurrence model: frequency, interval, COUNT or UTC UNTIL, weekly BYDAY, EXDATE, and timezone-aware local start/end.
- Recurring TZID values now have matching VTIMEZONE components. Fixed offsets get an initial offset definition; historic transitions and representable annual DST rules are emitted. A 400-year Gregorian-cycle check rejects transition rules that cannot be represented without changing their dates or times.
- The SAF path validates and serializes before opening the output stream. Unsupported effective ranges, moved/overridden instances, invalid monthly dates, and unsupported timezone rules fail closed.
- ICS carries Activity data only. Native Envelope remains necessary for alarms, deadlines, memos, progress, and full PowerCal metadata.
- RFC basis: [RFC 5545 §3.2.19](https://www.rfc-editor.org/rfc/rfc5545.html#section-3.2.19) and [§3.6.5](https://www.rfc-editor.org/rfc/rfc5545.html#section-3.6.5) require a VTIMEZONE definition for each referenced TZID.

## Evidence

- Code and regression tests were committed and pushed as `50a83a0e4787503d2bf2c225d4d1091659c8d21f` (`fix(ics): 为重复事件导出时区定义`).
- `IcsExportRecurrenceTest`: **6/6 PASS**, including DAILY interval, WEEKLY COUNT/BYDAY/EXDATE, UTC UNTIL, DST-zone local anchor, and fail-closed cases.
- Full Gradle verification: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 345 tests, 0 failures/errors/skips; lint 0 errors / 56 warnings. Log: `.local/android-test/reports/freeze-20261023/issue001-vtimezone-full-final.log` (SHA-256 `E5F5001B73851E752FB46C33C7B197495027F0E6DD951CB2D84D23561611C6F5`).
- APK `905 / 0.9.5-rc.1`, SHA-256 `A2CED9A71952716D2DA7870D7963F5085167EE586497DD6E2959CE671B8DA0D4`, installed on Android 15 / API 35 emulator with `adb install -r`. The database remained intact: 8 active objects, including 5 Activities.
- Real SAF wrote a new, uniquely named Downloads file; existing ICS files were not overwritten. Export message confirmed Activity ICS success. Evidence: `.local/android-test/reports/freeze-20261023/issue001-saf-export-vtimezone.ics`, 1,228 bytes, SHA-256 `63EBE8EA8A1342C38B60242910D621B9DBAB9A0520E86DDDB8DAA84F658CF528`.
- Independent `icalendar 7.3.0` parse found 5 VEVENTs, 3 recurring series (DAILY/WEEKLY/MONTHLY), one VTIMEZONE for the referenced TZID, timezone-aware DTSTART/DTEND, unique UIDs, and zero parser warnings. Serialize → parse preserved all event start/TZID/RRULE/EXDATE semantics. Summary: `.local/android-test/reports/freeze-20261023/issue001-independent-parse.json` (SHA-256 `8F9F57A3A028F05527E914A5EB481E737FCB2711DFBEF2AEDF45C02F488B2BEC`).
- The existing emulator fixture uses fixed `GMT` and has no COUNT, UNTIL, BYDAY, EXDATE, or DST-bearing series. Those recurrence features have unit coverage, but are **not** claimed as real-SAF device evidence.
- A second, isolated Android 15 / API 35 emulator (`emulator-5556`) provided real DST-bearing SAF evidence. A synthetic weekly Activity `PC_QA_ISSUE001_DST_WEEKLY` in `America/New_York` (2026-10-26 14:00–15:00 local) was exported through the Android Create Document flow to the unique Downloads file `.local/android-test/reports/freeze-20261023/issue001-saf-dst-emulator5556.ics` (1,728 bytes; SHA-256 `9F9E973963C6AA3ADA592E9CA3C1661F87FE3F8A1AD33227ED7C628673353E68`). No existing file was overwritten.
- Independent `icalendar 7.3.0` parsing found 6 VEVENTs, 2 VTIMEZONEs, and 0 parser errors. The weekly event retains `DTSTART;TZID=America/New_York:20261026T140000` and `RRULE:FREQ=WEEKLY;INTERVAL=1`; the embedded timezone defines the March second-Sunday EDT transition and November first-Sunday EST transition. Expansion preserves 14:00 local across the fallback: Oct 26 is 18:00Z (UTC−04:00), Nov 2 is 19:00Z (UTC−05:00). Serialize/parse preserved DTSTART, TZID, and RRULE. Details: `.local/android-test/reports/freeze-20261023/issue001-dst-independent-parse-emulator5556.json` (SHA-256 `A2F5B5B4CDEB4126CC3F796E62617AAF6868E35E3DF7A5F9FD21A2EECAA8121F`).
- After the probe, emulator-5556 was restored to `persist.sys.timezone=GMT`, `auto_time_zone=1`, and unset global `time_zone`; a read-only database query confirmed both synthetic QA series (event_seq 5 and 6) remain. Postcheck: `.local/android-test/reports/freeze-20261023/issue001-emulator5556-postcheck.json` (SHA-256 `93EC383C832163F21915AECAD2F77446489E14D6FCE10B0123893CC513DBA669`).
- Fresh complete working-tree regression on the current source: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 364 tests, 0 failures/errors/skips; lint 0 errors / 55 warnings. The count includes 1 test from the protected, untracked `app/src/test/java/dev/powerfulcalendar/ui/v2/DraftDeckFeedbackTest.kt`; thus this run is 363 tracked tests plus 1 untracked test, and is not a clean committed-tree test count. That file was not staged or modified. Log: `.local/android-test/reports/freeze-20261023/issue001-dst-full-gradle-20261023.log` (SHA-256 `02A3FB2D275B2BD225BE102960DAD1D7B02AD2829028C10E312FBD8DC0F74DA5`). Built APK SHA-256 `B0AB218FF70D696E25B570F0151BEC0F58B07FE545A045EE976EBD4F1173CAE1`.

## Remaining acceptance gap

The emulator has Google Calendar installed, but Android reported no activity for `ACTION_VIEW` with MIME type `text/calendar`; there was no separate calendar importer available to complete a real client import. No physical Android device was available in this run. Therefore PC-ISSUE-001 remains **PARTIAL / HOLD**, and the 209-case release gate is not signed off. Next acceptance needs import into a compatible external calendar with recurrence/timezone semantics checked, plus physical-device coverage when hardware is available.
