# Native formal-write review presentation boundary

Date: 2026-10-30

Status: **CODE COMPLETE; focused/build gates TESTED; final integrated JVM suite PASS; Android device NOT TESTED.**

## Finding and change

`PowerCalApp` still constructed the formal-write review title, snapshot summary, change rows, and action buttons inline. This display remained in the Host source even though the Native renderer is owned by `:native-skin`.

Commit `c7b7567cf1f89783978be4a400399c135f49a1ac` moves that presentation to `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeFormalWriteReview.kt` and adds value-only display models to `skin-api`. The Host still projects `WebFormalWriteReview`, owns the review token and snapshot, performs version and authorization checks, and supplies the resolve callbacks. No Core, persistence, Bridge authorization, skin selection, default, fallback, or Clear source/resource behavior changed.

The Native component renders the same review title, target and recurrence summaries, explanation, complete before/after or deletion rows, and resolving-disabled controls. Its Host-provided dialog slot preserves the existing overlay routing.

## Verification

- `NativeFormalWriteReviewComposeTest`: **2/2 PASS**. Covered displayed Host-projected snapshot fields, approval callback routing, and disabled actions while resolving.
- `:app:verifyNativeSkinBoundary` and `:app:verifySkinSourceBoundary`: **PASS**.
- `:app:compileDebugAndroidTestKotlin`: **PASS**, including the two pre-existing unstaged WebSkin instrumentation edits; those files were not staged or changed by this issue.
- Debug APK assembly and `:app:lintDebug`: **PASS**; 152 actionable Gradle tasks completed in the isolated build run.
- An earlier full `:app:testDebugUnitTest` run **FAILED** with 782 tests completed and 1 failure: `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` timed out after 60 seconds with `memoCount=0`; its isolated rerun passed 1/1. The final serial integrated run at SHA `c93ace28a038f42a5f539a92ae0ec575495a3326` passed **784/784 tests, 125 suites, 0 failures/errors/skips**. The earlier failure was not reproduced; its cause remains unknown. No Clear/Memo/Bridge code was changed.
- ADB reported no online devices. Android instrumentation execution and physical/OEM UI behavior are **NOT TESTED**.

Final integrated Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 66,516,562 bytes, SHA-256 `83B78E837330F9D224A4003681F9858004E16DE530E12DE0BC97AE8846C09BE0`.

Logs under `.local/android-test/reports/native-formal-write-review-20261030/`:

- Focused test and boundary checks: `focused-sdk-configured.log`, SHA-256 `11DAED52137B8013776197439F8E0576604A44D36B4026DA52068972A753B651`.
- Full JVM gate: `full-gate.log`, SHA-256 `3A1469D58B55EAE987CD988AB9D2CE66454B9A8FBBE38A06D47C11DE351BB606`.
- Isolated replay and boundary/AndroidTest compile/APK/lint gates: `isolated-and-build.log`, SHA-256 `C13BE2234D6FF41D920C2451710B3E098F44C6785F4752B1E578036A68EABCF4`.

Final integrated JVM and Android build logs are under `.local/android-test/reports/native-skin-separation-final-20261030/`: `full-jvm.log` SHA-256 `3533C0415A71600FFDE277B38B8C63E6EAE50B2D53A55A2F64803768C5BE4766`; `android-build.log` SHA-256 `27C1BE88CCB18FBA7DE3221943FB48F39854A617208D457A2C7F606C592135E0`.

## Boundary and remaining work

This closes only the Native formal-write review presentation slice. It does not close SEP-16, establish Clear/Studio functional parity, or authorize a skin cutover. Device verification remains open. Clear is known broken and was intentionally left untouched as directed.

Implementation commit `c7b7567cf1f89783978be4a400399c135f49a1ac` was pushed to `origin/feat/skin-first-separation`.
