# PC-ISSUE-144 — Timeline DST gap and fold tick labels

Date: 2026-10-24 | Risk: P1 | Reconstructed acceptance keys: `REC-P1-008/009` | Status: **PASS for deterministic tick-label generation**

## Requirement and scope

`PRODUCT_BASELINE_G1-G11.md` §4 requires the elapsed-time axis to remain continuous through DST and calendar labels to identify missing and duplicated local hours. `POWERCAL_GLASS_DESIGN.md` §中央时间轴 specifies that a repeated fall-back hour carries its UTC offset.

This sign-off covers the deterministic labels supplied to the ruler and to current-time badge collision avoidance. It does not set the still-unresolved recurrence gap/fold policy in `REC-P0-074/075`, and it does not claim that a new AVD or physical-device screenshot was captured.

## Finding and change

The Canvas generated the ruler labels inline and separately regenerated the same ticks for badge collision avoidance. The two paths duplicated DST resolution and offset formatting, while no focused test protected the timeline label contract.

Extracted `timelineTickLabels(fromMillis, toMillis, tickMinutes, zone)` and wired both consumers to the same ordered label list. Tick positions still come from elapsed instants; a repeated local time includes its actual UTC offset. No visual style, event geometry, orientation behavior, or recurrence policy changed.

## Regression evidence

- Spring-forward fixture (`America/New_York`, 2026-03-08): the first hourly labels are `00:00`, `01:00`, `03:00`, `04:00`; there is no false `02:00` tick. The 01:00 and 03:00 instants remain one elapsed hour apart.
- Fall-back fixture (`America/New_York`, 2026-11-01): the labels include `01:00\n-04:00` and `01:00\n-05:00` at distinct instants, followed by 02:00 and 03:00.
- Targeted `TimelineTickLabelsTest`: **2/2 passed**, zero failures/errors/skips.
- Full offline Gradle command included source-boundary verification, all JVM/Robolectric tests, Debug and AndroidTest APK assembly, and lint: **477/477 tests across 60 suites**, zero failures/errors/skips; all build tasks passed; lint 0 errors / 58 warnings.
- An initial targeted compile exposed a `Double` to `Long` argument mismatch at the helper call site. The call was corrected using the same millisecond conversion as the prior tick code; the targeted and full runs above passed afterward.
- This change was verified on the JVM test runner. No new AVD visualization or physical-device test was run; visible label placement remains outside this narrowed sign-off.

## Artifacts

| Evidence | Path | SHA-256 |
| --- | --- | --- |
| Targeted Gradle log | `.local/android-test/reports/freeze-20261024/pc-issue-144-targeted.log` | `03B4810FF4459E69FE78DB67DC9E9CCEDBF8F8F94D398B1FC3BC1B358886987D` |
| Targeted JUnit XML | `app/build/test-results/testDebugUnitTest/TEST-dev.powerfulcalendar.ui.v2.TimelineTickLabelsTest.xml` | `F42D0002E2019712DA96AAFBD23E139ED4C13812987C2A74A37B7AD1F335F620` |
| Full Gradle log | `.local/android-test/reports/freeze-20261024/pc-issue-144-full-gradle.log` | `C5C62FB0BF5AE379E17E1BC1358A89B4257648CEBB45E70F2D504FD72EF79741` |
| Lint XML | `app/build/reports/lint-results-debug.xml` | `243E7F92EE5B3B46D2286C877371D19A83A8053C634405E64E7BE8873BC46A95` |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | `39EECD5DA9AD60ECE73FC29D64EBDDAB01AED9F97C5B55F5C6C434321186C600` |
| AndroidTest APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A` |

Code/test commit `13e7e73a4b15865277289c43c87651460c008461` (`test(时间轴): 验证夏令时刻度标签`) is pushed to `origin/main`.

Overall core freeze remains **HOLD**; the narrowed sign-off does not cover screen rendering on AVD/device or unresolved recurrence DST semantics.
