# PC-ISSUE-145 — Independent horizontal scrolling for both timeline wings

Date: 2026-10-24 | Risk: P1 | Reconstructed acceptance keys: `REC-P1-018/019/020` | Status: **PASS for Robolectric Compose wing scrolling and viewport bounds**

## Requirement and root cause

The accepted timeline contract requires each wing to scroll and clip independently, without changing the central time ruler or discarding events. The existing right wing had a horizontal scroll container. The left wing instead compressed all lanes into its half-width and disabled horizontal scrolling, so the outer left lanes did not have the required independent browse path.

## Fix

Both wings now use stable lane widths (up to 84dp) and 4dp spacing, and each has its own horizontal scroll state. The left wing initializes at its content's right edge so the lane nearest the central ruler is visible first. The existing layout has no three-lane cap; all lane events remain composed and clipped by the wing viewport.

## Regression evidence

- `TimelineWingScrollComposeTest.leftAndRightWingsScrollIndependentlyAndKeepTheirViewportBounds` passed **1/1** on Robolectric API 35.
- The fixture contains eight simultaneous left alarms and eight overlapping right activities. It verifies the center-proximal left lane is initially visible, outer lanes can be reached by scrolling that wing, the other wing remains in its prior state, and scrolling the right wing does not reset the left wing. Both wing viewport bounds remain fixed and all 16 event nodes remain present.
- Full offline Gradle regression passed **478/478 JVM/Robolectric tests across 61 suites**, zero failures/errors/skips. Source-boundary verification, Debug/AndroidTest APK assembly, and lint passed; lint 0 errors / 58 warnings.
- During test development, the initial test compile had an incorrect Compose test import, and the first gesture attempt did not travel far enough to reach the outer lane. Both test-harness issues were corrected; the forced targeted run and final full run passed. No product assertion failure remains.
- The central ruler Canvas remains outside each wing's horizontal scroll container; the Compose test confirms each viewport's bounds stay fixed. No new AVD screenshot or physical-device run was captured, so visual rendering/OEM behavior is not claimed as verified.

## Artifacts

| Evidence | Path | SHA-256 |
| --- | --- | --- |
| Forced targeted Gradle log | `.local/android-test/reports/freeze-20261024/pc-issue-145-targeted.log` | `6A9C6E17B5442135B5A4B227A895EDD04ED9C21A79511BC984AB6F0734CC896D` |
| Targeted JUnit XML | `app/build/test-results/testDebugUnitTest/TEST-dev.powerfulcalendar.ui.v2.TimelineWingScrollComposeTest.xml` | `6BE5323F93E5D2F02B64B768D2BE4A2C9D25B2CA19FCAACEA9C6C71A42BD0950` |
| Final full Gradle log | `.local/android-test/reports/freeze-20261024/pc-issue-145-full-gradle-final.log` | `E01CBA2964475E82851D9B25028C20FE6BC45082EA128C1F1153C3A76329615D` |
| Full-run summary JSON | `.local/android-test/reports/freeze-20261024/pc-issue-145-full-run-summary.json` | `12EE19AB97F5319768256374A5799204F668583ADDAE92AB88AF33A21F6136BB` |
| Lint XML | `app/build/reports/lint-results-debug.xml` | `243E7F92EE5B3B46D2286C877371D19A83A8053C634405E64E7BE8873BC46A95` |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | `60D2363EF4CA8A4A5CE15EE928ABF72D02186FDE8369C9CAAA40A77BAA387CF0` |
| AndroidTest APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A` |

Code/test commit `b0c9d5e6f7445ed7b1ad748eba62b565add4e9f0` (`fix(时间轴): 修复左翼独立滚动`) is pushed to `origin/main`.

Overall core freeze remains **HOLD**; this narrowed Compose sign-off does not replace AVD screenshot or physical-device/OEM verification.
