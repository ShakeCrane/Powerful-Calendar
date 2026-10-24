# PC-ISSUE-142 — Timeline initial camera core sign-off

Date: 2026-10-24 | Risk: P1 | Reconstructed acceptance key: `REC-P1-001` | Status: **PASS within the initial-camera model**

This sign-off covers the result returned by `TimelineCoordinate.initialCamera()`. It does not sign what is visibly rendered on the first screen, saved-state restoration, orientation behavior, or device interaction.

## Requirement and test

The requirement derives from `PRODUCT_BASELINE_G1-G11.md` §5: the initial camera uses the current local date, noon at the vertical center, and a 24-hour span. `PowerCalApp.kt` initializes its camera state through `TimelineCoordinate.initialCamera(System.currentTimeMillis(), zone)`; this call site was inspected, but no UI was launched.

The existing JVM regression `TemporalProtocolTest.initialCameraCentresNoonButTodayJumpsToNow` supplies 2026-10-20 15:42 in Asia/Shanghai and asserts that the returned anchor is 2026-10-20 12:00 with `hours=24.0`. The separate Today interaction is not part of this sign-off. The test source was introduced in commit `85d2cda4743ea598222855ca492242c4f6a467f4`; PC-ISSUE-142 adds no product or test source changes.

## Results

- Focused test: **1/1 passed**, zero failures/errors/skips.
- Full offline Gradle regression: **474/474 JVM tests** across 59 suites, zero failures/errors/skips. Source-boundary verification, Debug APK assembly, AndroidTest APK assembly, and lint passed; lint reported 0 errors / 58 warnings.
- No Compose UI, emulator interaction, Android instrumentation, or physical device was used. The first-screen presentation and save/restore behavior remain unverified.
- The full regression included the protected pre-existing untracked `DraftDeckFeedbackTest.kt`; it was not modified or staged for this Issue.

## Evidence

- Focused log `.local/android-test/reports/freeze-20261024/pc-issue-142-targeted.log`; SHA-256 `9FC4340B447B048B20ACE5ADF96F0AC8B691C4B3D15C6283832EC74DB503636D`.
- Focused JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-142-artifacts/TemporalProtocolTest-targeted.xml`; SHA-256 `6F0B82444CEA1C8EE090C1CB741C50704F0C82092BA72F40AFF0AFF8BA51195A`.
- Full Gradle log `.local/android-test/reports/freeze-20261024/pc-issue-142-full-gradle.log`; SHA-256 `897FDB560E7B4200238E1B3E621C0C5FC53FA5AB32263AF56292390C72555B1E`.
- Preserved full-run JUnit XML: `.local/android-test/reports/freeze-20261024/pc-issue-142-artifacts/full-results/` (59 suites); summary JSON SHA-256 `0B0FEF6CA67BB90C7AA85346973D37CD74AFF6057A376F3F7CB0752D8CA56C49`.
- Lint XML SHA-256 `2BEABC23C862180FE0301AC2F3A3FABDBC432D106CFDDFBC0E1188879BCD1B47`.
- Debug APK SHA-256 `35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4`; AndroidTest APK SHA-256 `D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A`.
- Current verification checkout: `bb21120bc955f39520bbe8ece702842649bad3a4`; no source changes were made for PC-ISSUE-142.
- Current structural-only ledger artifact and digest are recorded in `CORE_FREEZE_VERDICT.md`; it does not establish product behavior.

Overall core freeze remains **HOLD**.
