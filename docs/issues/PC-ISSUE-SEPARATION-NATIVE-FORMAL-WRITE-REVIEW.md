# Native formal-write review presentation boundary

Date: 2026-10-30

Status: **CODE COMPLETE; focused and build gates TESTED; full JVM gate FAIL; Android device NOT TESTED.**

## Finding and change

`PowerCalApp` still constructed the formal-write review title, snapshot summary, change rows, and action buttons inline. This display remained in the Host source even though the Native renderer is owned by `:native-skin`.

Commit `c7b7567cf1f89783978be4a400399c135f49a1ac` moves that presentation to `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeFormalWriteReview.kt` and adds value-only display models to `skin-api`. The Host still projects `WebFormalWriteReview`, owns the review token and snapshot, performs version and authorization checks, and supplies the resolve callbacks. No Core, persistence, Bridge authorization, skin selection, default, fallback, or Clear source/resource behavior changed.

The Native component renders the same review title, target and recurrence summaries, explanation, complete before/after or deletion rows, and resolving-disabled controls. Its Host-provided dialog slot preserves the existing overlay routing.

## Verification

- `NativeFormalWriteReviewComposeTest`: **2/2 PASS**. Covered displayed Host-projected snapshot fields, approval callback routing, and disabled actions while resolving.
- `:app:verifyNativeSkinBoundary` and `:app:verifySkinSourceBoundary`: **PASS**.
- `:app:compileDebugAndroidTestKotlin`: **PASS**, including the two pre-existing unstaged WebSkin instrumentation edits; those files were not staged or changed by this issue.
- Debug APK assembly and `:app:lintDebug`: **PASS**; 152 actionable Gradle tasks completed in the isolated build run.
- Full `:app:testDebugUnitTest`: **FAIL**, 782 tests completed, 1 failure. `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` timed out after 60 seconds with `memoCount=0`. The same test was rerun alone and **PASS 1/1**. The full-suite result remains FAIL; the isolated pass does not replace it. No Clear/Memo/Bridge code was changed.
- ADB reported no online devices. Android instrumentation execution and physical/OEM UI behavior are **NOT TESTED**.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 66,516,562 bytes, SHA-256 `6154B14BCAF66C2EE6EAD6F636700EB4DB0628B8F75BDF9E1BC04FC5995C3230`.

Logs under `.local/android-test/reports/native-formal-write-review-20261030/`:

- Focused test and boundary checks: `focused-sdk-configured.log`, SHA-256 `11DAED52137B8013776197439F8E0576604A44D36B4026DA52068972A753B651`.
- Full JVM gate: `full-gate.log`, SHA-256 `3A1469D58B55EAE987CD988AB9D2CE66454B9A8FBBE38A06D47C11DE351BB606`.
- Isolated replay and boundary/AndroidTest compile/APK/lint gates: `isolated-and-build.log`, SHA-256 `C13BE2234D6FF41D920C2451710B3E098F44C6785F4752B1E578036A68EABCF4`.

## Boundary and remaining work

This closes only the Native formal-write review presentation slice. It does not close SEP-16, complete Native extraction, establish Native/Skin functional parity, or authorize a skin cutover. The full-suite Memo test failure and device verification remain open. Clear is known broken and was intentionally left untouched as directed.

Implementation commit `c7b7567cf1f89783978be4a400399c135f49a1ac` was pushed to `origin/feat/skin-first-separation`.
