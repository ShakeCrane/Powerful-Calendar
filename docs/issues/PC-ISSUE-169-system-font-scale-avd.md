# PC-ISSUE-169 — System font scale 2× on API 35 AVD

Date: 2026-10-24

Status: **API 35 AVD subcase PASS / REC-P1-069 remains BLOCKED for physical/OEM acceptance / core freeze HOLD**

## Scope

`REC-P1-069` requires the timeline toolbar and essential controls to remain usable at a 2× system font scale. The earlier PC-ISSUE-154 Robolectric check exercised an explicit Compose `LocalDensity`; this follow-up changes Android's actual `Settings.System.FONT_SCALE` on the dedicated API 35 emulator and verifies that the production `TimelineScreen` receives the resulting system configuration.

The test was run on `emulator-5556` (API 35, portrait, 1080×2400 px). The original system font scale was `1.0`; it was temporarily set to `2.0` for the instrumented test and restored in `finally`. A post-run read confirmed `1.0`. The test hosted the production timeline composable in a test `ComponentActivity`; it did not exercise `MainActivity`, physical devices, or OEM behavior. No production code, app preferences, PowerStore data, or calendar records changed.

## Verification

- AndroidJUnitRunner final focused run: **1/1 PASS** for `TimelineSystemFontScaleInstrumentedTest.actualSystemFontScaleTwoKeepsTimelineActionsReachable`.
- The test confirmed both `Resources.configuration.fontScale` and Compose `LocalDensity.fontScale` were `2.0`.
- Today reset, Memo, pending deck, New, zoom-out, and zoom-in controls all exposed click actions and remained inside the 1080×2400 px viewport. Every action succeeded, including selecting Activity from the New menu.
- Captured semantics bounds (px): Today `Rect(600,107,716,223)`; Memo `Rect(716,107,832,223)`; pending deck `Rect(832,107,948,223)`; New `Rect(948,107,1064,223)`; zoom-out `Rect(32,382,137,487)`; zoom-in `Rect(514,382,619,487)`. All are within `Rect(0,0,1080,2400)`.
- Full forced-offline Gradle command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain`: **BUILD SUCCESSFUL**, 496/496 JVM tests across 67 suites, 0 failures/errors/skips; all 88 tasks executed. Lint completed with 0 errors and 58 warnings.

Evidence SHA-256:

- Final focused API 35 runner log: `70CD58CA2FADA2476D34ABD8D7C7272783F5CE17BD97F249C7FE202EA5D88169`.
- Final geometry/state trace: `87483817A4AFDF40BE0973C8F0DA8278A3E25512B09E6E8D27E1EE9654126947`.
- Full Gradle log: `CD68BC0C2AD4ED2D882AE0E31051C9CFF75C604E0477972DD2AC935904FD7EEF`.
- Debug APK: `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1`; AndroidTest APK: `0865B074F300681BAA2285029EC191BC5F57D9D21DDFC9A6C3CCD3B703E9A2B5`.
- Lint XML: `44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F`.
- Instrumented test source: `180EEF26C81FD06DAD53D69EB49EB52DA66D4296B5F0867E82F6691EEDAC5CEA`.

The first test compile exposed a missing `Modifier.testTag` import; that harness issue was corrected before the successful compile (initial log SHA-256 `880DB577CD9B0E51F9710D73F32837EA3A67616719A233E0C77B4882688479AF`; final compile log SHA-256 `E17520FD50662998991D880705145F14E1237491F169EF562ADD6CF35182CB30`). An initial runner wrapper could not resolve `adb` from `PATH` and stopped before querying or changing the emulator; the final run used the SDK's explicit `adb.exe`. The emulator setting was restored and verified after each successful run.

Detailed artifacts are under `.local/android-test/reports/freeze-20261024/pc-issue-169-system-font-scale-avd/`.

## Remaining gate

This is an isolated API 35 AVD result. It does not establish `MainActivity`, physical-device, or OEM font-scaling behavior, so `REC-P1-069` remains **BLOCKED** pending those checks. The API 35 subcase supplements PC-ISSUE-154 and does not justify a GO verdict.

Structural-only ledger validation r38 retains 211 rows / 210 active reconstructed candidates: 198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED; historical IDs mapped=0 and `product_behavior_tested=false`. Artifact SHA-256 `4B3A90A6F20F85A111568D4E19139D06074B1402C65D957102B41E6238D3BF16`; ledger SHA-256 `0EFEB757A68C14D614B111F53E8C59E6E2174F22E7269BC57B0C6FF627156E16`. Structural validation does not clear the device gate.

## Commit

Test-only commit `97bfead75f0c8b2d2c3de21be8d05495b751c7cc` (`test(时间轴): 验证系统字体二倍缩放`) is pushed to `origin/main`.
