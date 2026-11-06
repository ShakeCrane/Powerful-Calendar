# PC Issue: Native 0.9.5 Timeline outside-title and greedy-folding parity

Date: 2026-11-06
Status: RESOLVED — narrow slice PASS; Timeline overall remains PARTIAL
Reference source: `b1f7629f0c52812e1887443872f43aa6c49908c4`
Reference APK SHA-256: `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`
Product Cutover: HOLD

## Observed difference

The Web v3 timeline selected `inside` placement for an ACTIVITY title when its event frame was tall enough. Frozen Native keeps ACTIVITY titles above the event frame, including a long 08:00–16:00 event. The Web title letter spacing also produced a measurable title rectangle difference.

## Change

- `tools/skin-studio/default-clear/source/web/app.js`: retain the frozen `start-anchor` title placement for ACTIVITY entries instead of testing title/frame height and moving the title into a tall frame.
- `tools/skin-studio/default-clear/source/web/native-095.css`: set ACTIVITY title letter spacing to `0.43px` to match the measured frozen Native title bounds.
- `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/TimelineGreedyFoldingWebViewInstrumentedTest.kt`: add an 8-hour fixture, title placement/style/geometry evidence, measured reference inset, and matching assertions. Existing measured-rectangle greedy fold decisions and tap targets remain under test.
- Rebuilt only through `tools/skin-studio/build_default_clear.py`; did not modify Core, Host behavior, Native recovery, package identity, or calendar data.

## Reproduction and result

On isolated API 35 AVD `PowerCal_Wave2_QA35_20261104` (`emulator-5556`, 1080×2400, 420 dpi, WebView 124.0.6367.219), the same five-event fixture produced the same visible title leads in Native Compose and actual WebView: `LongActivity`, `Fold_A`, `Fold_C`. `Fold_B` and `Fold_D` folded based on measured title rectangles. All five event targets remained clickable and kept their full accessible labels. Tapping folded `Fold_B` opened its own editor and left the isolated Store snapshot and commit count unchanged.

Title rectangle edges matched within the instrumentation's 2.5 physical-pixel bound. The noon geometry differed by 0.67px. The “today” action returned Host camera to the 24-hour local-noon view; zoom and reset were exercised.

## Verification

- Full serial Gradle gate: PASS; 976 JVM tests, 0 failures/errors/skips; source boundaries, assembleDebug, lintDebug, AndroidTest compile/package PASS.
- `TimelineGreedyFoldingWebViewInstrumentedTest`: 2/2 PASS on the verified isolated AVD.
- Greedy folding unit tests: 10/10 PASS.
- Single-default distribution tests: 2/2 PASS.
- Node syntax and `build_default_clear.py --verify --apk`: PASS.
- APK SHA-256: `E18730AB26D010EDA290989FB0C9FC8B79DF55A55102AA0EA2FB81D4F5A328C1`.
- Default package: 410655 bytes; one APK `.pcskin`; ID `powercal-clear-default`; source/APK SHA-256 `3B1BBCD210D24458DF40F3AEBC5A4090F7B059F9406E964EAAB81661078BBA0E`.
- Full device, package, image, and command evidence: `.local/android-test/reports/native095-timeline-title-20261106/manifest.md`.

## Limits

This closes only outside-title placement, title bounds, measured greedy folding and folded-event opening for the tested ACTIVITY fixture. It does not sign off the left ALARM wing, other event types, dark mode, large font, recurrence, or the entire Timeline. Original-APK home and current MainActivity screenshots used different data states; only shell title/separator measurements were compared between those captures. Product Cutover remains HOLD.
