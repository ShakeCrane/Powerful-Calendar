# Native Month renderer module boundary

**Status:** CODE COMPLETE; automated JVM/build checks TESTED; Android device interaction NOT TESTED. This closes the Native Month module slice only; overall Native separation remains in progress.

## Root cause and change

`MonthScreen` lived in `:app` and received `TemporalRecord` values directly. Moving it unchanged into `:native-skin` would have made the presentation module depend on Core models and recurrence identity. The screen also used app-owned glass components, skin dialogs, and colors directly.

Moved the renderer into `:native-skin` as `NativeMonthScreen`. Added `NativeMonthRow`, a closed kind enum, and primitive visual tokens/Host composable slots in `:skin-api`. The Host projects each visible month with the existing `displayOccurrences` and `overlapsDayWindow` helpers, preserving date-only deadline visibility, cross-midnight events, recurrence overrides, event summaries, and the original occurrence key. It maps the renderer's opaque row key back to the owner record and original recurrence key before invoking the existing `onOpen` action. The current month's action map replaces the previous map when the month changes.

The renderer retains the existing month grid, date selection, month/year pickers, month swipe, create/open callbacks, AI-lock indication, and dialog behavior. Background, panel, colors, and dialog presentation are injected by the Host. Clear, frozen Core behavior, persistence, renderer selection, and Native fallback availability were not changed.

## Verification

Results are from source SHA `7488c89820b7ff8be2d4cb24e0c68dfd26f1ab3c`, committed and pushed to `origin/feat/skin-first-separation`.

Focused command:

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:testDebugUnitTest --tests '*NativeMonthProjectionTest' --tests '*NativeMonthScreenComposeTest' --tests '*GlassComposeTest' --tests '*DirectEventEditorComposeTest' --offline --rerun-tasks --console=plain
```

Result: **30/30 tests passed**, including all five new projection and Native renderer tests. Coverage verifies recurrence owner/original-slot resolution, date-only deadlines, cross-midnight visibility, stale row-key clearing after month change, month grid/date selection, open/create/timeline callbacks, and AI-lock/dialog slot behavior.

Full gate:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

Result: **BUILD SUCCESSFUL**, 152 actionable tasks. The full JVM suite passed **745/745 tests across 108 XML suites, 0 failures, 0 errors, 0 skipped**. Both source boundary tasks, AndroidTest Kotlin compilation, Debug APK assembly, and lint passed. An earlier full attempt stopped once at `:app:packageDebug`; the isolated package retry and complete gate rerun both succeeded. The initial splitter failure's underlying cause was not established and is retained as an intermittent build risk.

Evidence:

- `.local/android-test/reports/separation-native-month-module-20261030/full-gate-rerun.log` — full gate output; SHA-256 `82CB58731DF8A723D65ABAC20D672178551327B6106C702E771035045D37D9D8`.
- `.local/android-test/reports/separation-native-month-module-20261030/package-debug-retry.log` — isolated successful APK packaging retry.
- `app/build/outputs/apk/debug/app-debug.apk` — 66,434,642 bytes; SHA-256 `D8EB6FBE87D9B63EED0A5596ABF8C020135D3E7FC482B74FA7DD903D5B72CDB2`.
- `git diff --cached --check` passed before commit. `adb devices -l` returned no devices and `emulator -list-avds` returned no AVDs; Android interaction is **NOT TESTED**.

## Remaining work

Timeline and Draft Deck renderer extraction plus shared Host adapters/projections remain. Timeline drag-to-Bridge and reset-button touch routing are prioritized next. Native separation, Studio parity, and skin cutover are not complete or signed off. Clear remains unchanged.

## Git

Implementation commit `7488c89820b7ff8be2d4cb24e0c68dfd26f1ab3c` (`refactor(皮肤分离): 将原生月历渲染迁入独立模块`) is pushed to `origin/feat/skin-first-separation`. Two pre-existing dirty WebSkin instrumentation files were not staged or committed.
