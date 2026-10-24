# PC-ISSUE-141 — Timeline zoom model boundaries

Date: 2026-10-24 | Risk: P1 | Reconstructed acceptance keys: `REC-P1-006` / `REC-P1-007` | Status: **PASS within pure zoom-model boundaries**

This Issue verifies zoom clamping and focal-time preservation in `TimelineCoordinate`. It does not test zoom buttons, pinch gestures, Compose state updates, or device behavior.

## Requirement and tests

The acceptance source is `CORE_CONTRACT_FREEZE_2026-10-21.md` invariants 5–6 and the timeline zoom model in `TemporalModels.kt`. Both rows remain `RECONSTRUCTED` and `UNMAPPED` to the unavailable historical 209-item list.

- `REC-P1-007`: `timelineZoomClampsToTheContractBounds` checks requests below and above the limits and verifies exact endpoints. `clampHours(-1.0)` returns 0.25; `clampHours(49.0)` returns 48.0; the exact limits remain unchanged.
- `REC-P1-006`: `timelineZoomPreservesFocusedInstantAtBothContractBounds` requests zoom beyond both limits, clamps to 0.25 and 48 hours, then checks that `anchorPreservingFocus` preserves the instant under a fixed Y coordinate within 1 ms at each limit.

## Results

- Focused Robolectric/JVM tests: **2/2 passed**, zero failures/errors/skips.
- Full offline Gradle regression: **474/474 JVM tests** across 59 suites, zero failures/errors/skips. Source-boundary verification, Debug APK assembly, AndroidTest APK assembly, and lint passed; lint reported 0 errors / 58 warnings.
- No UI or device interaction was run. The sign-off covers the pure clamp and anchor math; whether UI actions invoke it correctly remains unverified.
- The full regression included the protected pre-existing untracked `DraftDeckFeedbackTest.kt`; it was not modified or staged for this Issue.

## Evidence

- Focused log `.local/android-test/reports/freeze-20261024/pc-issue-141-targeted.log`; SHA-256 `7CF13719EE900F581521D50756018CE3B744BC2D6072229725F77C191FCE5D8E`.
- Focused JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-141-artifacts/TemporalProtocolTest-targeted.xml`; SHA-256 `82BC2403E23F6A3032B8461AD10737E07D2B7A2545F0293E2B8C2F0C78046264`.
- Full Gradle log `.local/android-test/reports/freeze-20261024/pc-issue-141-full-gradle.log`; SHA-256 `7B9425F20B9D56CCB23F8AC6280E60F89A8E57A2E1783B5A68D6A6AB496748E2`.
- Preserved full-run JUnit XML: `.local/android-test/reports/freeze-20261024/pc-issue-141-artifacts/full-results/` (59 suites); summary JSON SHA-256 `0B0FEF6CA67BB90C7AA85346973D37CD74AFF6057A376F3F7CB0752D8CA56C49`.
- Lint XML SHA-256 `2BEABC23C862180FE0301AC2F3A3FABDBC432D106CFDDFBC0E1188879BCD1B47`.
- Debug APK SHA-256 `35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4`; AndroidTest APK SHA-256 `D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A`.
- Test source commit `02ec6f4f313315faf2cd3d0c23e16aaf1e898810` (`test(核心冻结): 验证时间轴缩放边界`), pushed to `origin/main`.
- Current structural-only ledger artifact and digest are recorded in `CORE_FREEZE_VERDICT.md`; it does not establish product behavior.

Overall core freeze remains **HOLD**.
