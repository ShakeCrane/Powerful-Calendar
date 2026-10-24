# PC-ISSUE-167 — Android IME current-build replay

Date: 2026-10-24

Status: **API 35 AVD replay PASS / REC-P1-070 remains BLOCKED for full device acceptance / core freeze HOLD**

## Scope

This replays the existing `AiChatImeInstrumentedTest.openImeKeepsComposerAndSendVisibleAndResizesConversation` against the current API 35 build after the preceding core-freeze work. The test runs the production `SkinAppShell` and `NativeAiChatScreen` in an isolated `ComponentActivity`, uses the emulator's real system IME, and supplies only synthetic chat text. It does not launch `MainActivity`, use `PowerStore`, or call AI.

No production or test source changed for this replay. It uses `emulator-5556` in portrait; `emulator-5554` and physical devices were not touched.

## Result and measured geometry

AndroidJUnitRunner passed **1/1**. The active input method was `com.google.android.inputmethod.latin/...LatinIME`. On the 1080×2400 API 35 emulator, the IME inset was 820px and its top edge was y=1580. The conversation list shrank from 1858px to 1101px; the synthetic message stayed visible; the composer remained above the keyboard; and the enabled send control ended at y=1559, 21px above the IME.

| Measured element | Bounds in px `[left, top, right, bottom]` | Result |
| --- | --- | --- |
| Compose root | `[0, 0, 1080, 2400]` | Portrait viewport |
| IME top | `y=1580` | Bottom inset 820px |
| Message list before IME | `[32, 293, 1048, 2151]` | Height 1858px |
| Message list with IME | `[32, 293, 1048, 1394]` | Height 1101px |
| Synthetic message | `[32, 293, 491, 436]` | Remains visible |
| Composer | `[169, 1412, 885, 1559]` | Above IME |
| Enabled send control | `[896, 1422, 1048, 1559]` | 21px above IME |

## Evidence

- Focused API 35 runner log SHA-256: `DF6DBF8F3D96A3ECF0FE8C5F7410F9FDFE1CCD396195252222278A12C763202B`.
- Geometry trace SHA-256: `0FD953F7CC33DC62E171BFB53060FDC1B7802967BB0FE7E52AEA0CE20CFA81CC`.
- Current-build Debug APK SHA-256: `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1`; AndroidTest APK SHA-256: `9C8C887B1AC86236415889A1A665E25497D3E121C318C2BAD5A3474E9A64EE4B`.
- The full forced-offline Gradle gate on the same source snapshot passed 496/496 JVM tests across 67 suites, with zero failures/errors/skips; all 88 tasks executed, including source-boundary verification, both APK builds, and lint. Log SHA-256: `6CB27E33765748096F388520C9AECAF65DDEAC03179B897C19D8B82F4D640835`.
- Source snapshot: `d081a7ebb16dd7889c7386770c3bb1e63571bfbb`; the IME instrumentation itself is from previously pushed commit `caa01096a20bddc2b02116a7c7d5931d329c4097`.

Detailed replay artifacts are under `.local/android-test/reports/freeze-20261024/pc-issue-167-ime-current-build/`. Earlier IME evidence and the test's original scope are documented in `docs/issues/PC-ISSUE-155-ai-ime-avoidance.md`.

## Remaining gate

This confirms only the isolated API 35 emulator behavior. Full `MainActivity` route integration, physical devices, and OEM keyboards remain unverified. `REC-P1-070` therefore remains **BLOCKED**; no device-level GO is inferred. The physical-device test remains user-owned.

Structural-only ledger validation r36 retains 211 rows / 210 active reconstructed candidates: 198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED; historical IDs mapped=0 and `product_behavior_tested=false`. Artifact SHA-256 `911254CE0245ED17A69150E7DEA4D87198BD08171994EB4E7C1B0D5B7BF46E4D`; ledger SHA-256 `48A6960B0CF4845FDF71D47CCC9FC32379AD2F8161C94F2D79C3D92375A8063E`. This structure-only check does not remove the physical/OEM gate.
