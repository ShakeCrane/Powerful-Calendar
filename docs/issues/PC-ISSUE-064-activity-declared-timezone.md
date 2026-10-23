# PC-ISSUE-064 — Timezone-bound Activity recurrence

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-077`)

Status: **CORE RECURRENCE CONTRACT PASS / ANDROID DEVICE PATH NOT EXERCISED / OVERALL FREEZE HOLD**

## Contract and observation

A recurring Activity with an explicit timezone must keep its declared wall-clock schedule when the device timezone changes. The daily Alarm behavior signed off in PC-ISSUE-063 is intentionally different: only daily Alarms follow the current local timezone.

The regression fixes the JVM default timezone to Asia/Tokyo while the Activity remains anchored to America/New_York at 09:00 for 90 minutes. Across New York's 2026 spring DST transition, projection returns the three stable local slots `2026-03-07T09:00`, `2026-03-08T09:00`, and `2026-03-09T09:00`. Each remains 09:00 New York time; the elapsed interval across the DST boundary is 23 hours. `nextOccurrence` and `occurrenceForKey`, even when explicitly passed Tokyo as the current local zone, agree with the projection.

No defect reproduced on the current baseline. This closes the reconstructed model-level candidate with a regression guard; it does not claim that Android delivered a timezone-change broadcast or exercised device scheduling.

## Change

- Added `TemporalProtocolTest.timezoneBoundActivityKeepsItsDeclaredZoneAcrossDeviceZoneChangeAndDst`.
- Production source, UI, database schema/data, design assets, and device state were not changed.

## Verification

Environment: deterministic JVM timezone switch to Asia/Tokyo; event zone America/New_York; recurrence crosses the 2026 spring DST transition. No emulator or physical device was used.

- Targeted test: **1/1 PASS**, zero failures/errors. Log `.local/android-test/reports/freeze-20261024/pc-issue-064-activity-timezone-targeted-20261024.log`, SHA-256 `EE7DEEEBB65254C6D753562384D600FAC6429CA858CCDF64F1AA67BEBE625C5D`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-064-targeted-junit/TEST-dev.powerfulcalendar.core.TemporalProtocolTest.xml`, SHA-256 `C63E671C6A0A390939A0D7E940D9389244C8C73C1C2D3C32C4E712DAAC47F2A8`.
- Full command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, **418/418** tests across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. This includes 417 tracked tests plus the protected untracked `DraftDeckFeedbackTest.kt`, which was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261024/pc-issue-064-full-gradle-20261024.log`, SHA-256 `B8C47A004349B2991582682DFB7A994604119DD511E57765EAFFB6A80B4D164B`; full `TemporalProtocolTest` XML `.local/android-test/reports/freeze-20261024/pc-issue-064-full-junit/TEST-dev.powerfulcalendar.core.TemporalProtocolTest.xml`, SHA-256 `2FD5CE5C4FE92E53D05526977C235B43C7F2F890DBBD41E6C640D403D79ED7A0`.
- Debug APK SHA-256 `DB944538964299348D31E8981D6A2F701F420FE74E60B508B8C53B253EC0517D`.

`REC-P0-077` is PASS for the recurrence-model contract and remains UNMAPPED to the unavailable historical checklist. Android timezone broadcasts, AlarmManager behavior, OEM differences, and physical-device behavior are outside this JVM test. The overall freeze remains **HOLD**.

## Git

- Test commit: `f497330abdd5851752f65f0e53112709a382b2b9`
- Message: `test(重复): 签核活动声明时区`
- Status: pushed to `origin/main`.
- This independent report and synchronized freeze ledger are committed separately.