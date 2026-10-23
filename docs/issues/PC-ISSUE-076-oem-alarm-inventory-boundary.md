# PC-ISSUE-076 — OEM alarm inventory boundary

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-122`)

Status: **CORE ADAPTER PASS / API 35, PHYSICAL DEVICE, AND OEM CLOCK BEHAVIOR NOT TESTED / OVERALL FREEZE HOLD**

## Finding

The product baseline says public Android APIs do not generally expose existing system alarm inventory and forbids claiming automatic retrieval from arbitrary OEM clocks. The core adapter is consistent with that boundary: `SystemCalendar.readUpcoming()` reads Android Calendar `Instances`; `showSystemAlarms()` creates an `AlarmClock.ACTION_SHOW_ALARMS` intent; and `setSystemAlarm()` creates an `AlarmClock.ACTION_SET_ALARM` intent with `EXTRA_SKIP_UI=false`. The adapter has no OEM alarm inventory query.

The prior candidate had no issue-level execution evidence. No production defect was found; this closes the core-adapter evidence gap only.

## Change and evidence

Added `SystemCalendarTest` with two Robolectric API 34 checks:

- The system-alarm viewer helper returns only the platform `ACTION_SHOW_ALARMS` intent, with no data, explicit component, or extras.
- The set-alarm helper preserves the requested hour, minute, and message and does not request skipping the platform interaction.

This verifies the adapter's intent boundary. It does not establish that a particular device resolves or displays either intent, that an OEM clock exposes an inventory, or that an actual alarm handoff succeeds.

## Verification

- Focused `:app:testDebugUnitTest --tests dev.powerfulcalendar.interop.SystemCalendarTest --offline --rerun-tasks --console=plain`: **2/2 PASS**. Log `.local/android-test/reports/freeze-20261024/pc-issue-076-system-calendar-targeted-20261024.log`, SHA-256 `7A7681E8C46B2AD043ED28E6B004FA99759F13357AAF1BCC3EEE00132C57FDEB`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-076-targeted-SystemCalendarTest.xml`, SHA-256 `FD587FE8C180A1B457C85B7BFE58F6B60545459CD9C9C510E5CEE5F7E00893E2`.
- Full `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **428/428 tests across 56 suites; zero failures, errors, or skips; APK build successful; lint 0 errors / 58 warnings**. Log `.local/android-test/reports/freeze-20261024/pc-issue-076-full-gradle-20261024.log`, SHA-256 `5D145A9B243EE744B12D50C12D8BD6D818F1A9B6764C94B3970C71B9A22C8B04`. Full `SystemCalendarTest` XML SHA-256 `1057998CFD9906C85BB87225DA296E138B0600CA73FA524EFF4F275C04E91172`; lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256: `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`.

## Git

- Test-only commit `539174c8036924119ed4b8efecba85644e4aa2c3` (`test(互操作): 验证系统闹钟交接意图`) is pushed to `origin/main`.

`REC-P0-122` is signed PASS for the tested core adapter boundary. API 35 resolver/device behavior, physical devices, OEM alarm inventory, and end-to-end handoff remain untested; overall freeze remains **HOLD**.
