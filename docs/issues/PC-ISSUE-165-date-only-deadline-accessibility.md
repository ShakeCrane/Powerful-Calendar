# PC-ISSUE-165 — Date-only Deadline accessibility semantics

Date: 2026-10-24

Status: **API 35 emulator subcase PASS / REC-P1-068 SPEC UNRESOLVED / core freeze HOLD**

## Scope

`REC-P1-068` expects a date-only Deadline to announce its date precision without inventing a time. The approved baseline requires a date-band/day-level anchor, while the exact position within that band and accessibility behavior across timeline zoom remain listed as unresolved in `PRODUCT_BASELINE_G1-G11.md` §9. This report signs only the demonstrable API 35 date-band semantics subcase; it does not resolve the row's remaining specification or device gates.

No production or visual code changed. The new Android instrumentation test composes the existing production `TimelineScreen` with a synthetic DATE Deadline and checks its date-band semantics and action.

## Verification

- API 35 `emulator-5556`, AndroidJUnitRunner: **1/1 PASS** for `TimelineDateOnlyDeadlineAccessibilityInstrumentedTest.dateOnlyDeadlineKeepsDatePrecisionInAccessibilityAcrossZoomAndOpen`.
- At 4h, 24h, and 48h camera zoom, the merged semantics text contained the complete title and `2026-10-24 · 截止，无具体时刻`; no `HH:mm` value appeared. The clickable node bounds stayed identical at `Rect.fromLTRB(42.0, 268.0, 657.0, 405.0)` for all three zooms.
- Tapping the date band returned event sequence 520 with the original complete title, `deadlineDate=2026-10-24`, and `deadlineMillis=null`.
- Full forced-offline Gradle command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain`: **BUILD SUCCESSFUL**, 496/496 JVM tests across 67 suites, 0 failures/errors/skips; all 88 tasks executed. Python skin-studio regression: **37/37 PASS**.

Evidence SHA-256:

- Focused API 35 runner log: `12A2285C6F2F18DEFD6A26F7AA679565CF867178498FC7CDCD339E4DC37E02D`.
- Extracted semantic text and bounds: `B4CDBC723D1FDCB1F31DD807C2FD86C47FE544D67D375C9E17BE5390F5FB10D8`.
- Full Gradle log: `9827E96E155A152B6BA6FD4947F6BBC54749A700E6FD0FCD7E9BE0278C97DFED`.
- JVM `TemporalProtocolTest` XML: `2394EF9D0A9D1E3F223BC6329966D5B3C81BFFEFF5AE57C220101465A14B30F5`.
- Skin-studio Python log: `E216E15F8807E81DB3D67D2679F46D2BDFDA521E36E40EE3B608FD2E9F91A513`.
- Debug APK: `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1`; AndroidTest APK: `13CF8D486194FB778D0FFC68804966BD545A47F52F2E872425FDF9DEA987F12E`.
- Test source SHA-256: `EAF43235EE9D1D7531909030B0F7AA3EC89BCCD4DEE5E7311274664A62382ECE`.

The first exploratory instrumentation attempt queried a `timeline-event-520-...` node, which date-only Deadlines do not render because they use the date-band chip. That test-harness targeting attempt failed before asserting product behavior; its log is retained at `api35-emulator-5556-targeted-r1.log` (SHA-256 `56E5FAFC1BFD98348F86F0DB7CB832779DAAD3EBB51DDF70F2B99F0D24E6574B`). A subsequent test compilation attempt used unsupported optional semantics access and also failed; after correcting the harness, the final source compiled and passed the full gates above. No product defect was inferred from either harness failure.

Detailed artifacts are under `.local/android-test/reports/freeze-20261024/pc-issue-165-date-only-deadline/`.

## Remaining limits

This was a Compose semantics-tree and click-callback check on an API 35 emulator. It did not invoke TalkBack or verify spoken output, OEM accessibility services, a physical device, or the still-unspecified exact visual anchor within the date band. `REC-P1-068` therefore remains **SPEC UNRESOLVED** and the freeze remains **HOLD**. This is evidence for the date-only timeline subcase, not a full accessibility or product-row sign-off.

Structural-only ledger validation r34 retains 211 rows / 210 active reconstructed candidates: 198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED; historical IDs mapped=0 and `product_behavior_tested=false`. Artifact SHA-256 `1D91A98FFD4B8E99C31DAADF13538DC60624CCC45F17CFC276412B47EA5B29C1`; ledger SHA-256 `D3CBB371BC0546CB757A93BAB2B02E29A4E181A22ED02CB4A7AA19C5B96BD2A6`. Structural validation does not sign off product behavior; the freeze remains HOLD.

## Commit

Test-only commit `2844236dd00f4b87db50bffcc031cf4e7b74911e` (`test(时间轴): 验证日期截止条无障碍语义`) is pushed to `origin/main`. The tested production-source baseline was `2b8cee9dd596f6a0a45c4a3b354b8ac9c01eae80`.
