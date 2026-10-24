# PC-ISSUE-140 — Timeline coordinate continuity across midnight

Date: 2026-10-24 | Risk: P1 | Reconstructed acceptance key: `REC-P1-004` | Status: **PASS within pure timeline geometry**

This Issue signs the core instant-to-Y and Y-to-instant mapping across a local date boundary. It does not sign a user scroll gesture, Compose recomposition, date-label rendering, DST transitions, or device behavior.

## Requirement and regression

The reconstructed requirement derives from `CORE_CONTRACT_FREEZE_2026-10-21.md` invariants 5–6 and the timeline coordinate model in `TemporalModels.kt`. `REC-P1-004` remains explicitly `RECONSTRUCTED` and `UNMAPPED` to the unavailable historical 209-item list.

Added `TemporalProtocolTest.timelineCoordinateRemainsContinuousAcrossMidnight`. It maps fixed Asia/Shanghai instants at 23:59 and the following day at 00:01 into Y coordinates using a 24-hour, 2400-pixel viewport. It checks monotonic increase, verifies that the Y delta equals the elapsed two-minute span within 0.01 px, and checks that inverse mapping retains the elapsed difference within 2 ms. This is a core geometry check; it does not simulate scrolling.

## Results

- Corrected focused Robolectric/JVM test: **1/1 passed**, zero failures/errors/skips.
- The first compile attempt failed before executing assertions because the new assertion mixed `Double` and `Float`. The test expression was corrected; no product assertion failed, and the initial log is retained.
- Full offline Gradle regression: **472/472 JVM tests** across 59 suites, zero failures/errors/skips. Source-boundary verification, Debug APK assembly, AndroidTest APK assembly, and lint passed; lint reported 0 errors / 58 warnings.
- No UI, emulator interaction, Android instrumentation, or physical device was used. Actual scroll handling, date labels and DST visuals remain unverified.
- The full regression included the protected pre-existing untracked `DraftDeckFeedbackTest.kt`; it was not modified or staged for this Issue.

## Evidence

- Initial compile-only log: `.local/android-test/reports/freeze-20261024/pc-issue-140-targeted.log`; SHA-256 `43E3B7C9B4648A4559C5D242C19095D31E58A5B52552AC1981B5A05AD8675B27`.
- Corrected focused log: `.local/android-test/reports/freeze-20261024/pc-issue-140-targeted-r2.log`; SHA-256 `B5CAF52DE4C8CD0E715E09E7201BA28336795A2F36970FEC2006B4E2658DA061`.
- Focused JUnit XML: `.local/android-test/reports/freeze-20261024/pc-issue-140-artifacts/TemporalProtocolTest-targeted.xml`; SHA-256 `8E3C24937F48A06B0411D418D09280AB4FDB5094D187B9878C06FD6A5BBBE3E9`.
- Full Gradle log: `.local/android-test/reports/freeze-20261024/pc-issue-140-full-gradle.log`; SHA-256 `076D85C120B2E9C56C4810F3663690201CD6822407CE0264A66BEE2701EDC2EF`.
- Preserved full-run JUnit XML: `.local/android-test/reports/freeze-20261024/pc-issue-140-artifacts/full-results/` (59 suites); summary JSON SHA-256 `DD37DDA4EBEF82F03B1BE26DA9A0A84C425A37B3065601669DC54D29DABC47E5`.
- Lint XML SHA-256 `2BEABC23C862180FE0301AC2F3A3FABDBC432D106CFDDFBC0E1188879BCD1B47`.
- Debug APK SHA-256 `35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4`; AndroidTest APK SHA-256 `D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A`.
- Test source commit `b23341fa216b718c3e1decba6db4cbe12e311182` (`test(核心冻结): 覆盖跨午夜时间映射`), pushed to `origin/main`.
- Current structural-only ledger artifact and digest are recorded in `CORE_FREEZE_VERDICT.md`; it does not establish product behavior.

Overall core freeze remains **HOLD**.
