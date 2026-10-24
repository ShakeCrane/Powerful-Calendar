# PC-ISSUE-168 — System reduced-motion behavior on API 35 AVD

Date: 2026-10-24

Status: **API 35 AVD subcase PASS / REC-P1-075 remains BLOCKED for physical-device acceptance / core freeze HOLD**

## Scope

`PRODUCT_BASELINE_G1-G11.md` requires the product to respect reduced-motion settings. This test verifies the actual Android `Settings.Global.ANIMATOR_DURATION_SCALE=0` setting reaches the existing `rememberSystemGlassEffects()` observer and selects the `glassMotion()` reduced-motion path.

The test runs on `emulator-5556` (API 35) and changes only that emulator's animator-duration setting during the focused run. The setting was initially absent (`null`, Android default behavior), temporarily set to 0, and deleted in a `finally` block after the run; a post-run read confirmed it was restored to `null`. No production code, app preferences, PowerStore data, or calendar records changed. No other emulator or physical device was used.

## Verification

- API 35 `emulator-5556`, AndroidJUnitRunner: **1/1 PASS** for `SystemReducedMotionInstrumentedTest.systemZeroAnimatorScaleDisablesGlassMotionAndBlur`.
- With the system setting at 0, the production observer reported `reduceMotion=true` and `blur=false`. The production `glassMotion()` animation probe reached target `1.0` after advancing the Compose test clock by one 100 ms interval. The test did not measure intermediate rendered animation frames.
- The final run verified restoration of the original unset setting (`null`).
- Full forced-offline Gradle command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain`: **BUILD SUCCESSFUL**, 496/496 JVM tests across 67 suites, 0 failures/errors/skips; all 88 tasks executed.

Evidence SHA-256:

- Final focused AVD runner log: `D4FABB8F940B07680590E38E5D84B27F37D8FF29D23F9451A1866E2F53FBD59F`.
- Final state trace: `9B3F4D97AE2F95CAA1FA3A9DE01F861479FB1D4B5D273C48E1CC86A9E9AFE94E`.
- Full Gradle log: `E71C621C06FDBFF38B17611CC7DB26D9E836295BC1E8BD327880323DEBF03479`.
- Debug APK: `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1`; AndroidTest APK: `2BD1C17F32722F588B1E6DE70AEAF98E174B73861C08BE93F6007AA656AEDE82`.
- Test source SHA-256: `49D7CD805794981C42171289D9E3CDD610C554AED8D9C0708DB56530A681EC0D`.

The first compile attempt omitted the Material3 `Text` import and was corrected before the final build (compile-r1 SHA-256 `23B063AC40E173FFF3942CF78BB4362B05DCE4D2CEFBBC31E6B8BB664A9F1428`; final compile-r3 SHA-256 `E17520FD50662998991D880705145F14E1237491F169EF562ADD6CF35182CB30`). An initial instrumentation attempt also read the animation probe before advancing the Compose test clock; the `reduceMotion` and blur assertions had already passed, and the emulator setting was restored. The harness then advanced the test clock and the final run passed. Initial-run log SHA-256 `F86BF7E61E35C7A92BD72C023E0AA9D6734506804751A9C1D72AC3946A916A61`.

Detailed artifacts are under `.local/android-test/reports/freeze-20261024/pc-issue-168-reduced-motion-avd/`.

## Remaining gate

This is an API 35 emulator result only. It does not establish physical-device or OEM behavior, so `REC-P1-075` remains **BLOCKED** pending the user-owned device check. No GO is inferred.

Structural-only ledger validation r37 retains 211 rows / 210 active reconstructed candidates: 198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED; historical IDs mapped=0 and `product_behavior_tested=false`. Artifact SHA-256 `3BCC9875D23E0591AC52760E05DA71EE830DA8208E850B0DF1C4C33C681EE075`; ledger SHA-256 `F458A02ED1C04980E90A8EE6A221D6DDA5B06A7E15CBDE100707D997E4CDD1C2`. This structural check does not clear the physical-device gate.

## Commit

Test-only commit `4dfaff99fb27c47f98b01125f94fe0629dd15df7` (`test(无障碍): 验证系统减少动态效果设置`) is pushed to `origin/main`.
