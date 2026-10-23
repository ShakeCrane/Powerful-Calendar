# PC-ISSUE-090 — ICS TZID values include matching VTIMEZONE definitions

Date: 2026-10-24

Candidate: `REC-P1-078` (reconstructed P1; historical mapping remains `UNMAPPED`)

Status: **CORE ICS TIMEZONE SERIALIZATION PASS / overall freeze HOLD**

## Finding

An ICS datetime that refers to a `TZID` must include the matching `VTIMEZONE` definition. The existing codec regression suite directly checks this output contract for a fixed-offset zone and a daylight-saving zone, including independent parse-back of the series.

## Evidence

- `IcsExportRecurrenceTest.dailySeriesExportsRruleAndImportsAsOneSemanticSeries` verifies that `VTIMEZONE` appears before `VEVENT`, the `Asia/Shanghai` TZID and UTC offset are present, and the recurrence imports back with its zone intact and no warnings.
- `IcsExportRecurrenceTest.dstZoneKeepsWallClockAnchor` verifies that a New York export includes both DST observances and offsets, the yearly transition rules, the event TZID, and the unchanged wall-clock anchor after parse-back.
- Focused Robolectric API 34 regression: **6/6 PASS**. Log SHA-256 `67CB928029CBB1759776544081D710802D7A47E24E60F15733D6C7DE3B569676`; JUnit XML SHA-256 `B48271D52E3B178E494F252BFAE05A6796A333FA7D1F327DBF8522F089D9C2A6`.
- Full Gradle on the same source/test baseline `13e720bce54d0b31a179e68e9fa1a86eec518c61`: **444/444 tests across 56 suites; zero failures, errors, or skips; BUILD SUCCESSFUL; lint 0 errors / 58 warnings**. This run was recorded for PC-ISSUE-089 and includes the ICS exporter suite. Full log SHA-256 `EE9DA7BC1A810D3C706DAA44F7CBE0D3EED6C5557572F5690F50C45621BFE44D`; APK SHA-256 `86D48004E3CE00E94A459A4CCAA929A9B55F8816DDBE7E10D6E9937D0C11ED94`.
- No production or test source changes were needed; this report maps the existing, directly matching codec tests to the reconstructed candidate.

## Scope and disposition

- `REC-P1-078` is **PASS** for the ICS codec requirement to emit a matching `VTIMEZONE` for exported TZID datetimes.
- The tests exercise codec output and parse-back. They do not claim interoperability with every third-party calendar consumer or a physical device.
- The historical 209-case checklist remains unavailable and unmapped. Overall core freeze remains **HOLD**.

## Git

- Evidence baseline: `13e720bce54d0b31a179e68e9fa1a86eec518c61` (pushed).
- This independent report and its ledger sign-off are committed separately.
