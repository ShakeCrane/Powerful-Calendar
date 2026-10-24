# PC-ISSUE-124 — deterministic timeline lane allocation (core sign-off)

Date: 2026-10-24

Status: **PASS for the narrowed core layout-model contract; overall freeze remains HOLD**

Cases: `REC-P1-016`, `REC-P1-017` (reconstructed, still unmapped to historical IDs)

## Scope and result

The acceptance ledger had no one-to-one evidence link for deterministic lane assignment or reuse. Read-only review found the existing `TimelineLayout` core model sorts events by their true start time and stable `eventSeq`, assigns the first lane whose prior half-open interval has ended, and reuses that lane. The native default is unbounded; this sign-off tests the layout model only.

No product defect was found in the narrowed model behavior. No production code or test source was changed. No app UI, canvas rendering, horizontal scrolling, forced orientation, emulator, or physical device was exercised.

## Verification

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.powerfulcalendar.core.TemporalProtocolTest --offline --rerun-tasks --console=plain
```

Result: **38/38 PASS**, zero failures/errors/skips. Five relevant cases directly cover the ledger rows:

- `earlierStartingOverlapStaysNearestTheAxis`
- `tenOverlappingEventsRemainIndependentWithoutOverflowByDefault`
- `tenIdenticalAlarmTimestampsUseTenIndependentLeftLanes`
- `freedLaneIsReusedByALaterEvent`
- `laneAssignmentIsIndependentOfInputOrder`

The tests verify overlap ordering, stable ordering for identical event timestamps, more than three independent lanes, input-order independence, and reuse after an interval ends. The implementation uses `priorEnd <= nextStart` for lane reuse. They do not establish rendered geometry or platform behavior.

- Targeted log: `.local/android-test/reports/freeze-20261024/pc-issue-124-timeline-lanes-targeted.log`, SHA-256 `88772FC37E170E12B4D118852B4BFEB7251B18606A4AA716771914B79EC71321`.
- JUnit XML: `app/build/test-results/testDebugUnitTest/TEST-dev.powerfulcalendar.core.TemporalProtocolTest.xml`, SHA-256 `F51185351FE75B645F0DB01F7D5B41FBB6CBB2CB33D9BE5BD000FE650B838E34`.
- Test source baseline: `d4584faac2a78758c4e31676ab79658d209c6057`.
- APK reference: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `C5F20B47128753D1130E87D0B15885E5CDF9C6F6C755ACBCE50A6AB72BAFA05D`; this is the binary from the latest full Gradle regression, not a newly assembled APK for this test-only run.

## Sign-off boundary

`REC-P1-016` passes for stable lane ordering in the core layout model. `REC-P1-017` passes for reuse of an available lane after the prior interval ends. The cases remain `RECONSTRUCTED` and `UNMAPPED`; these results do not map to or close any missing historical 209-case ID. Screen rendering and device acceptance remain outside this narrowed result. Core freeze stays **HOLD**.
