# Native Memo module boundary

**Status:** CODE COMPLETE; automated JVM/build checks TESTED; Android device interaction NOT TESTED. Native Memo extraction is complete as a module slice; the overall Native skin separation remains in progress.

## Root cause and implementation

`NativeMemoCenterScreen` was still compiled in `:app` and accepted `TodoEntry`, whose owner is a Core `TemporalRecord`. It read recurrence, event kind, date, zone, and sequence data directly and returned Core-backed objects from click callbacks. Moving the composable as-is would have made `:native-skin` depend on Host/Core types.

Added the display-only `NativeMemoRow` DTO to `:skin-api` and moved `NativeMemoCenterScreen` to `:native-skin`. The Host's existing `TodoProjection` still owns recurrence-window projection and `TodoEntry` identities. Host maps each entry to text and checkbox values, then resolves the DTO's opaque `rowKey` back to the current `TodoEntry` before invoking the original toggle/open callbacks. Opening a repeated item still passes its original occurrence key; toggle still uses the original event sequence, revision, and completion key through the existing Store path.

Dates are formatted in each owning record's zone, preserving the former screen's date and cross-midnight behavior. Undated Memo rows remain undated. No persistence, recurrence, event identity, or authorization logic moved into the Native module.

## Verification

Command, using the repository Android SDK at `E:\android-sdk`:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

Result: **BUILD SUCCESSFUL**, 152 actionable tasks. Full JVM XML totals: **736 tests; 0 failures, 0 errors, 0 skipped** across 104 XML files. Both module boundary checks, AndroidTest Kotlin compilation, Debug APK assembly, and `lintDebug` passed. `git diff --check` passed.

The new tests cover:

- `NativeMemoRowProjectionTest`: owner-zone date formatting across UTC date boundaries, repeating rows and stable row keys, and no-date Memo semantics.
- `NativeMemoCenterScreenComposeTest`: completed/pending filtering, row open/toggle actions, and Host resolution of the opaque key back to the original `TodoEntry`.
- Existing `DirectEventEditorComposeTest.memoOccurrenceOpenPassesTheOriginalSlotKeyToTheEditorHost` continues to cover the recurrence occurrence key reaching the editor Host.

Evidence:

- Gate log: `.local/android-test/reports/separation-native-memo-module-20261030/full-gate.log`
- Gate log SHA-256: `B3C8AF885CC97A6D874980B61A6C3677C812DB0F812B36AF6DC0026C8C7B2051`
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 66,418,258 bytes
- APK SHA-256: `FF6A8D207B9658B1647BBE163EDB506A61912DA87A9EAF3E2DED02ECF6795907`
- Android runtime interaction: NOT TESTED; `adb devices -l` had no online device and no AVD was configured.

No Clear source, assets, package, or behavior was edited. The two pre-existing dirty WebSkin instrumentation test files in the worktree remained unstaged and untouched.

## Remaining work

Native Timeline, Month, AI chat, Draft Deck, Native renderer adapters, and remaining Host projections still compile in `:app`. This slice does not claim complete Native separation, Studio equivalence, Clear repair, or physical-device parity.

## Git

Implementation commit `e9c619a6f8b1ac3cc6a0ffa68e6aa173649d0f4f` (`refactor(皮肤分离): 将原生 Memo 页迁入独立模块`) was pushed to `origin/feat/skin-first-separation`.
