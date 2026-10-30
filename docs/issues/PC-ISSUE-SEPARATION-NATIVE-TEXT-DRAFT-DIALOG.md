# Native text-to-draft prompt presentation boundary

Date: 2026-10-30

Status: **CODE COMPLETE; focused/build gates TESTED; final integrated JVM suite PASS; Android device NOT TESTED.**

## Finding and change

The Native “用文字创建草稿” form still lived in `PowerCalApp.kt`. Its text field and explanatory copy were part of the Native presentation, while parsing and draft creation already belonged to the Host.

Commit `c006049edbd562725f533c5b21c82c4f3c7566da` moves the form to `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeTextDraftDialog.kt` and adds a slot-only dialog interface in `skin-api`. The module owns only transient input presentation and forwards the submitted text through the Host callback. `TextParser`, current-time/zone selection, and `createDraftFromText` are unchanged. Dismissal still closes the prompt without submitting.

## Verification

- `NativeTextDraftDialogComposeTest`: **2/2 PASS**, covering disabled submit on blank input, exact text forwarded to the Host callback, and dismiss without submit.
- `:app:verifyNativeSkinBoundary` and `:app:verifySkinSourceBoundary`: **PASS**.
- `:app:compileDebugAndroidTestKotlin`: **PASS**.
- `:app:assembleDebug` and `:app:lintDebug`: **PASS**, 152 actionable Gradle tasks.
- Final serial full JVM regression at integrated SHA `c93ace28a038f42a5f539a92ae0ec575495a3326`: **784/784 PASS, 125 suites, 0 failures/errors/skips**. An earlier full run at `c7b7567` had 781/782 pass and the Memo integration timeout; the isolated rerun passed 1/1, and the failure was not reproduced in the final full run. The cause remains unknown.
- ADB had no online devices. Android instrumentation and physical/OEM UI behavior are **NOT TESTED**.

Final integrated Debug APK: 66,516,562 bytes, SHA-256 `83B78E837330F9D224A4003681F9858004E16DE530E12DE0BC97AE8846C09BE0`.

Build and test log: `.local/android-test/reports/native-text-draft-dialog-20261030/focused-build.log`, SHA-256 `F9FF3B264E2E2344FB29D7B533D4AE7910A11CEAD5C61E85B4D264209E22C30F`.

Final integrated full JVM and Android build logs are under `.local/android-test/reports/native-skin-separation-final-20261030/`: `full-jvm.log` SHA-256 `3533C0415A71600FFDE277B38B8C63E6EAE50B2D53A55A2F64803768C5BE4766`; `android-build.log` SHA-256 `27C1BE88CCB18FBA7DE3221943FB48F39854A617208D457A2C7F606C592135E0`. The integrated build also passed both source boundaries, AndroidTest Kotlin compilation, APK assembly, and lint.

## Boundary and remaining work

This closes only the Native text-draft prompt presentation slice. It does not change default-time semantics or establish Clear/Studio parity or a skin cutover. Device behavior remains untested. Clear was not changed.

Implementation commit `c006049edbd562725f533c5b21c82c4f3c7566da` was pushed to `origin/feat/skin-first-separation`.
