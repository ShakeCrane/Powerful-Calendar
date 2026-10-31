# Native Timeline renderer module boundary

**Status:** CODE COMPLETE; full automated JVM/build gate and selected API 35 Native Compose device slices TESTED; SEP-01 remains PARTIAL / HOLD. This closes only the Native Timeline module boundary, not full timeline parity.

## Root cause and change

The Native Timeline renderer was in `:app` and directly consumed `TemporalRecord`, recurrence projection objects, Core timeline coordinates, and app-owned appearance services. Moving the composable alone would have either pulled Core into the Native renderer or duplicated host business logic.

Added display-only timeline state/event/action/appearance contracts and a `NativeTimelineGeometry` interface in `:skin-api`. Moved `NativeTimelineScreen` and its layout/gesture/title helpers into `:native-skin`. The renderer now consumes prepared values, opaque row keys, geometry callbacks, and Host-injected Native visual/dialog/image slots; it has no Core or Store references.

The `:app` Host adapter preserves the existing `TimelineScreen` signature and current route. The Host projection retains recurrence expansion, true-time lane allocation, event owner/revision/original occurrence mapping, summaries, and date-only deadline semantics. The geometry adapter delegates to the existing `TimelineCoordinate` implementation. Native renderer IDs, selection, event time geometry, title folding, hit targets, accessibility labels, camera gestures, independent horizontal lane scrolling, and axis reset behavior remain unchanged. Clear, frozen Core semantics, persistence, skin selection, and Native fallback availability were not changed.

Per-instance recurrence dragging remains refused by the existing Host callback until the separate editor capability exists; this extraction preserves that behavior and does not convert an instance gesture into a series write.

## Verification

The focused set passed **51/51 tests across 10 XML suites**, covering projection and owner/original-slot mapping, lane geometry, date-only deadline precision/event zone, memo exclusion, title folding and click behavior, zoom/pinch/reset, scrolling, accessibility, font scale, and forced-landscape layout. The last source edit after this focused run removed three unused imports; the full suite below compiled and tested the resulting source.

Focused command:

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:testDebugUnitTest `
  --tests '*NativeTimelineProjectionTest' `
  --tests '*TimelineTitleComposeTest' `
  --tests '*GlassComposeTest' `
  --tests '*TimelineWingScrollComposeTest' `
  --tests '*TimelineTickLabelsTest' `
  --tests '*TimeAxisAccessibilityTest' `
  --tests '*ActivityGeometryTest' `
  --tests '*GreedyTitleFoldingTest' `
  --tests '*TimelineFontScaleComposeTest' `
  --tests '*TimelineForcedLandscapeComposeTest' `
  --offline --rerun-tasks --console=plain
```

Focused log: `.local/android-test/reports/separation-native-timeline-module-20261030/focused-final.log`; SHA-256 `A20003BBAA020245FCF47DDEEB9B3A75A7059745831B6585ED34041C1F3AF95E`.

The first full-suite attempt reported 748/749 tests passing because `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` timed out waiting for Memo Host state after 60 seconds. The same test passed alone in 10 seconds; the final complete gate also passed. This is a Web/Memo integration path outside the Native Timeline renderer. Prior Native AI module progress records this same isolated timeout followed by isolated and full-suite passes; the cause of the intermittent timeout is not established.

```text
Full gate command: `:app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`
Full gate result: **BUILD SUCCESSFUL; 749/749 JVM tests across 109 XML suites, 0 failures, 0 errors, 0 skipped; 152 actionable tasks.** Native/skin source-boundary checks, AndroidTest Kotlin compilation, Debug APK assembly, and lint all PASS.
APK: `app/build/outputs/apk/debug/app-debug.apk`, 66,451,026 bytes; SHA-256 `D055E80721C44A09D53F7011CC8E9603D2469796C6E67547250F539DB7BF6884`.
Full-gate log: `.local/android-test/reports/separation-native-timeline-module-20261030/full-gate-rerun.log`; SHA-256 `D37CC388D0F61884492DB2399F602D2AF41C5DC66860D7F9E64A9568888993AD`.
```

`adb devices -l` and `emulator -list-avds` returned no device/AVD during the audit. Android instrumentation and physical-device UI interaction are NOT TESTED.

## Native-only API 35 device follow-up (2026-10-31)

Using the same production Native Timeline source at commit `2f55ec672421125ec6fe7a25847a9baffc36c526`, four Native Compose instrumentation cases passed on Android 15 / API 35 `emulator-5570`; these tests do not launch Clear or modify Clear production code:

- `TimelineSmallScreenInstrumentedTest` **1/1 PASS**: portrait small viewport, sub-pixel event hit target vs true visual geometry, full-title accessibility, and both title/event taps.
- `TimelineDateOnlyDeadlineAccessibilityInstrumentedTest` **1/1 PASS**: date-only DEADLINE remains date-precise and accessible at 4h, 24h and 48h zoom; opening preserves the date and null exact-time value.
- `TimelineSystemFontScaleInstrumentedTest` **1/1 PASS** with actual system font scale **2.0**: timeline actions remain within the viewport and invoke their callbacks.
- `TimelineForcedLandscapeInstrumentedTest` **1/1 PASS** at a temporary logical display size of **2400×1080**: greedy visible title leads do not overlap; hidden titles remain absent while hidden event hit targets and accessible titles remain available. The test asserts screenshot generation. This verifies a landscape-sized layout, not sensor rotation or OEM behavior.

Font scale, accelerometer/user rotation settings and display size were restored after the runs (`1.0`, auto-rotation enabled / user rotation 0, physical `1080×2400`). The final four-case result is **4/4 PASS**. Logs and SHA-256 values are recorded in `.local/android-test/reports/sep08-deck-lifecycle-parity-20261031/native-timeline-*.log`; manifest `native-timeline-device-manifest-2f55ec6.json`, SHA-256 `1FBB183EE27D700A82AB0A784BE7796F80A9BF4E017F85CEC4DCA752B8D8804C`.

This adds device evidence for the tested Native slices only. Matched Native/Skin gestures, all accessibility paths, real system/OEM orientation handling and physical-device behavior remain unverified; SEP-01 remains **PARTIAL / HOLD**. No Clear production files or runtime were changed or exercised by this follow-up.

## Git

Implementation source commit `09a1fe27964bbeb5950706cdcdbdc99919616ef2` (`refactor(皮肤分离): 将原生时间轴渲染迁入独立模块`) is pushed to `origin/feat/skin-first-separation`. The contract update and this report are committed separately after the implementation. The two pre-existing dirty WebSkin instrumentation files are excluded from staging and remain untouched.
