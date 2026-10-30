# Native Settings and Navigation module boundary

**Status:** CODE COMPLETE; automated JVM/build checks TESTED; Android device interaction NOT TESTED. This closes only the Settings and Navigation extraction slice. Native skin separation remains in progress.

## Scope and root cause

The Native Settings and bottom navigation implementations were grouped under `nativeui/`, but still compiled inside `:app`. Their renderer context also lived in the Host module. That package organization did not provide a Gradle dependency boundary, and the Native composables reached Host-owned visual helpers directly.

This slice creates `:skin-api` and `:native-skin`, then moves the Settings/Navigation renderer DTOs and two Native composables across that boundary. The direction is `:app → :native-skin → :skin-api`, with `:app → :skin-api` for Host adapters. The Native library has Compose dependencies only; it does not depend on the Host app module. Host supplies Settings shapes, the settings icon, protected profile/credential content, values, and actions through parameters or slots.

A new `:app:verifyNativeSkinBoundary` task rejects the app module as a direct dependency and scans Native module sources for known Core, Store, scheduler, AI, and Host visual service APIs. A Compose regression verifies Navigation selection, callbacks, and the injected Settings icon. The existing Settings Compose coverage continues to exercise Host-protected profile and credential slots.

## Changes

- Added Android library modules `:skin-api` and `:native-skin`.
- Moved `SettingsVisualContext.kt` into `:skin-api`; it now also owns `VisualTab` and `NavigationVisualContext`.
- Moved `NativeToolsPage.kt` and `NativePowerNavigation.kt` into `:native-skin`.
- Replaced direct Native reads of `GlassShapes`, skin drawing helpers, and Host settings icon handling with injected shapes and composable slots.
- Wired the Host runtime to provide those shapes, icon, and protected slots.
- Added `NativePowerNavigationComposeTest` and an automatic Native module boundary check.
- Added ignore entries for build output under the two new modules.

No Clear source, assets, package, or behavior was edited. No frozen Core behavior, persisted records, default skin, or Native fallback selection was changed.

## Evidence

Environment: Windows PowerShell, Android SDK `E:\android-sdk`, Gradle online dependency resolution followed by a successful full gate.

Command:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --rerun-tasks --console=plain
```

Result: **BUILD SUCCESSFUL**, 152 actionable tasks. JUnit XML: **732 tests, 0 failures, 0 errors, 0 skipped** across 102 XML files. Source boundary checks, AndroidTest Kotlin compilation, Debug APK assembly, and `lintDebug` passed. `git diff --check` passed.

- Gate log: `.local/android-test/reports/separation-native-module-settings-navigation-20261030/full-gate-boundary-settings-navigation-online.log`
- Gate log SHA-256: `8819DFFE47F8C87AEDEBC089E865C08F1F82822CD648F93E7E08CD8A9F25702B`
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 66,418,258 bytes
- APK SHA-256: `8982F085F3953339456A38317A7BC7F9EFBAE80ADFCDEE108D114910D1C078FC`
- Focused Navigation and Settings tests: 4/4 passed after correcting the new test's merged-semantics query.
- `adb devices -l`: no online devices. `emulator -list-avds`: no configured AVDs. Runtime/device interaction therefore remains NOT TESTED.

The initial offline full-gate attempt could not resolve several AndroidX AARs not present in the cache. The online gate above resolved them and completed successfully; this was an environment cache issue, not a code test failure.

## Remaining Native extraction

Timeline, Month, Memo, AI chat, Draft Deck, Native renderer adapters, and other Host projections remain in `:app`. This slice does not claim full Native separation or Native/Skin behavioral parity. Next low-coupling page candidate is Memo: move only a value-only row model and Compose renderer, leaving occurrence projection and record resolution in the Host/Core.

## Git

Implementation commit `48f30fe07b6fa1deb99ed017bcf8e9329ac62fa8` (`refactor(皮肤分离): 独立原生设置与导航模块`) was pushed to `origin/feat/skin-first-separation`.

The two pre-existing dirty WebSkin instrumentation files in the worktree were left untouched and unstaged.
