# Native skin catalog review boundary

**Status:** CODE COMPLETE; focused Compose/Native settings tests and independent build checks TESTED; full JVM gate FAIL due one out-of-scope Memo navigation timeout; Android/device UI NOT TESTED.

**Scope:** Native skin renderer separation only. SEP-13 overall remains PARTIAL.

## Root cause

The Native Settings screen itself was rendered in `:native-skin`, but its installed-skin activation and deletion confirmation copy/buttons were still constructed inline in `PowerCalApp.kt`. The Host also owns consequential operations that must not move with that presentation: checking for an active edit before activation, selecting/removing the installed skin, refreshing state and reporting results.

## Change

- Added `NativeSkinCatalogReview` in `:native-skin` and a display-only `NativeSkinCatalogDialog` slot in `:skin-api` for activation and deletion confirmation.
- Moved only the review text, recovery instructions, labels and buttons. The confirmation callback still enters the existing Host operation; dismissal remains a no-op on the installed skin.
- Preserved the existing selection/deletion services, the edit-safety check, skin-aware dialog shell, user-visible copy and button labels. Import still never auto-enables a skin; Native fallback/default selection is unchanged.
- Added Compose regressions for activation warning/recovery copy, Host confirmation callback, and deletion copy/callback. Existing Native settings protected-slot and skin-package export tests remain green.
- No Clear source, frozen Core, persistent data, default skin or Native fallback changed. Two pre-existing dirty WebSkin AndroidTest files were not staged or committed.

## Verification

All current-source checks ran against implementation commit `843a5a721fd80f34e24f82d84fb61bcf1e723e7f`, pushed to `origin/feat/skin-first-separation`.

Focused command:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest --tests '*NativeSkinCatalogReviewComposeTest' --tests '*NativeToolsPageProtectedSlotsComposeTest' --tests '*SkinPackageExportFlowTest' --offline --rerun-tasks --max-workers=2 --console=plain
```

Result: **6/6 PASS** — 2 new review tests, 3 protected-slot tests and 1 skin-package export test. Final log `.local/android-test/reports/native-skin-catalog-review-20261030/focused-retry.log`; SHA-256 `0EC83DD6A9A95D819A15977B419D46FC063B42ADADE4F1D4F336F675D51058CE`. The first attempt used an exact text matcher against multiline review copy and failed one assertion; the matcher was corrected to test the visible substring and the full focused set then passed.

The full JVM/build command was:

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=1 --console=plain
```

The **full JVM gate failed** at **766 tests with 1 failure**, in the existing `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` test: after a 60-second wait, Host state still showed `memoCount=0`. This Web/Memo path does not exercise skin catalog review. The isolated retry passed **1/1**. Logs: full run `.local/android-test/reports/native-skin-catalog-review-20261030/full-gate.log`, SHA-256 `257F47743AE8BB63464CBF62A760CBAF4D596DA83A6A2C24DC97E73D80D89EE5`; isolated case `.local/android-test/reports/native-skin-catalog-review-20261030/memo-paging-isolated.log`, SHA-256 `4E83DFB5E79F5F042274F338BBB8D3042564B50AE2ABCC203816CACC072A76E6`.

To complete the independent Native build checks, this command omitted the JVM test task:

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=1 --console=plain
```

Result: **BUILD SUCCESSFUL**, 144 actionable tasks; both source boundaries, AndroidTest Kotlin compilation, Debug APK and lint passed. Log `.local/android-test/reports/native-skin-catalog-review-20261030/build-gate.log`; SHA-256 `0E8D341202188570BFE7FE2A40E9A20C0C031921B974315F9D85A3CC285BDEA0`.

Debug APK `app/build/outputs/apk/debug/app-debug.apk`: 66,500,178 bytes; SHA-256 `26263D7964977E91CAD07A3F7E10F813E8E0FDDBF8F7C3AD7F6138C06FC0B18C`.

ADB had no online devices. Android instrumentation execution and physical/OEM skin management behavior are **NOT TESTED**.

## Remaining scope

This closes only Native skin activation/deletion review presentation. SEP-13 remains PARTIAL; runtime package and device validation remain open. No Clear repair or skin cutover is implied.

## Git

- Implementation: `843a5a721fd80f34e24f82d84fb61bcf1e723e7f` — `refactor(皮肤分离): 将原生皮肤管理确认迁入模块`.
- Pushed as a normal fast-forward to `origin/feat/skin-first-separation`.
