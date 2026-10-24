# PC-ISSUE-143 — Today action centers the current instant

Date: 2026-10-24 | Risk: P1 | Reconstructed acceptance key: `REC-P1-002` | Status: **PASS for the wired camera-state transition**

## Root cause and fix

`PRODUCT_BASELINE_G1-G11.md` §4 requires the initial timeline to open at noon but the Today action to jump to the current time. The existing `PowerCalApp` Today callback incorrectly reused `TimelineCoordinate.initialCamera()`, which returns local noon and resets the view to 24 hours. That made Today behave like first-open initialization.

Added `TimelineCoordinate.todayCamera(nowMillis, currentHours)`, which anchors to the supplied current instant and preserves the current zoom within the supported range. Wired the Today callback to this transition. The visual layout and styling were not changed.

## Verification

- `TemporalProtocolTest.todayCameraJumpsToNowAndPreservesTheCurrentZoom` passed **1/1**. It verifies the exact current millisecond anchor and retention of a 9-hour zoom.
- Full offline Gradle regression passed **475/475 JVM tests** across 59 suites, zero failures/errors/skips. Source-boundary verification, Debug APK assembly, AndroidTest APK assembly, and lint passed; lint reported 0 errors / 58 warnings.
- No Compose UI, emulator interaction, Android instrumentation, or physical device was used. The callback is compile-verified and wired to the tested model transition; visible viewport behavior remains unverified.
- The full regression included the protected pre-existing untracked `DraftDeckFeedbackTest.kt`; it was not modified or staged for this Issue.

## Evidence

- Focused log `.local/android-test/reports/freeze-20261024/pc-issue-143-targeted.log`; SHA-256 `9C1D7B879E9F69010767CAEB287E4205E18156F29723D32493ECF79206A5E34C`.
- Focused JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-143-artifacts/TemporalProtocolTest-targeted.xml`; SHA-256 `FFE54F78504E45A9F6FA04A246BF800676BB34560AC4DA412CE4A25250DB3956`.
- Full Gradle log `.local/android-test/reports/freeze-20261024/pc-issue-143-full-gradle.log`; SHA-256 `4F1264E171DE8355F15DC7A2AB194149DF97055AD542D5A1A7D2CC0CA5A51A6A`.
- Preserved full-run JUnit XML: `.local/android-test/reports/freeze-20261024/pc-issue-143-artifacts/full-results/` (59 suites); summary JSON SHA-256 `06FEB9705597447BF9BCA5BC477B9A3EBFF8CEB1A89AC488DF6349310450524D`.
- Lint XML SHA-256 `243E7F92EE5B3B46D2286C877371D19A83A8053C634405E64E7BE8873BC46A95`.
- Debug APK SHA-256 `22BB40B68F9F40C174B234AF99ACA1622B512706A74EF325B4E08BC433701A5B`; AndroidTest APK SHA-256 `D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A`.
- Fix/test commit `fc98e29c02c8f1bb1670e57adf2872cc8e3f6a91` (`fix(时间轴): Today 跳转到当前时刻`), pushed to `origin/main`.
- Current structural-only ledger artifact and digest are recorded in `CORE_FREEZE_VERDICT.md`; structural validation does not establish product behavior.

Overall core freeze remains **HOLD**.
