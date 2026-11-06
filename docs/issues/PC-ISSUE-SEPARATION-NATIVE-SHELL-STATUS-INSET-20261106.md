# PC Issue: Native 0.9.5 shell top inset parity

Date: 2026-11-06
Status: RESOLVED — narrow shell geometry PASS; full page parity remains open
Reference source: `b1f7629f0c52812e1887443872f43aa6c49908c4`
Reference APK SHA-256: `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`
Product Cutover: HOLD

## Observed difference and cause

The Web v3 brand/date header sat about 24dp above the frozen Native 0.9.5 header. On API 35, the active window reported a 128px status-bar inset, while the static `status_bar_height_default` dimension was 63px. `WebSkinRoot` preferred the resource value, so its WebView began at y=63px instead of matching Compose's current `statusBarsPadding()` inset.

## Change

`WebSkinRoot` now prefers the current `rootWindowInsets` status-bar inset and uses the Android dimension only when no positive window inset is available. This keeps platform inset handling in Host and follows the active device/window geometry rather than a static resource. No CSS offset or device-specific constant was added.

## Reproduction and result

Before the fix, the actual WebView top was y=63px. After the fix on the same isolated API 35 environment, it was y=128px. In the paired Android Compose/WebView instrumentation run, the Native title layout top was y=153px and the Web title screen top was y=162.39px, a 3.58dp delta under the test's 4dp limit.

Direct screenshot comparison against the frozen raw APK showed the brand title ink beginning at y=168px in both images; Native ended at y=198px and Web at y=199px. Date subtitle ink began at y=227px in Native and y=226px in Web. These main-activity screenshots were taken on two separate isolated AVDs and had different event data, so only the title/subtitle shell geometry is signed off. The same-fixture instrumented screenshot pair independently checks the Native Compose/WebView layout coordinates.

## Verification

- `TimelineDragWebViewInstrumentedTest#nativeAndClearMatchTheEmptyDateOnlyDeadlineBand`: **1/1 PASS**, including the header-position regression assertion.
- Full serial Gradle gate: PASS; 977 JVM tests, 0 failures/errors/skips; source boundaries, assemble, lint and AndroidTest compile/package PASS.
- Installed debug APK SHA-256: `254d2ff1a8e4da4ebb3af4cbbffd68f9a2f3c13ff92fdf57cbd2ac3f509683de`; pulled device `base.apk` has the same SHA-256.
- Frozen APK: `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`.
- Raw and Web screenshots, package verification and Gradle log: `.local/android-test/reports/native095-empty-date-band-20261106/`.

## Limits

This closes only the measured status-bar/header vertical offset. It does not claim full-screen pixel parity or sign off the Timeline, toolbar controls, events, navigation, Month, editor, or other Native screens. Native recovery and single-default package identity are unchanged. Product Cutover remains HOLD.
