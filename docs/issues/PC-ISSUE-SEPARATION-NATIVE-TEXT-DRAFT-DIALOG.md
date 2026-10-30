# Native text-to-draft prompt presentation boundary

Date: 2026-10-30

Status: **CODE COMPLETE; focused and build gates TESTED; full JVM suite NOT RUN at this commit; Android device NOT TESTED.**

## Finding and change

The Native “用文字创建草稿” form still lived in `PowerCalApp.kt`. Its text field and explanatory copy were part of the Native presentation, while parsing and draft creation already belonged to the Host.

Commit `c006049edbd562725f533c5b21c82c4f3c7566da` moves the form to `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeTextDraftDialog.kt` and adds a slot-only dialog interface in `skin-api`. The module owns only transient input presentation and forwards the submitted text through the Host callback. `TextParser`, current-time/zone selection, and `createDraftFromText` are unchanged. Dismissal still closes the prompt without submitting.

## Verification

- `NativeTextDraftDialogComposeTest`: **2/2 PASS**, covering disabled submit on blank input, exact text forwarded to the Host callback, and dismiss without submit.
- `:app:verifyNativeSkinBoundary` and `:app:verifySkinSourceBoundary`: **PASS**.
- `:app:compileDebugAndroidTestKotlin`: **PASS**.
- `:app:assembleDebug` and `:app:lintDebug`: **PASS**, 152 actionable Gradle tasks.
- Full JVM suite was **NOT RUN at this commit**. The previous full run at `c7b7567` had 781/782 pass and the known Memo integration timeout; that failure was independently rerun 1/1 PASS. A final full suite is still required on the integrated baseline.
- ADB had no online devices. Android instrumentation and physical/OEM UI behavior are **NOT TESTED**.

Debug APK from the focused/build gate: 66,516,562 bytes, SHA-256 `EDE00390FD36283694DC309BA798EC5EC7E972C5DFE54B9C0751CF337DAEAC59`.

Build and test log: `.local/android-test/reports/native-text-draft-dialog-20261030/focused-build.log`, SHA-256 `F9FF3B264E2E2344FB29D7B533D4AE7910A11CEAD5C61E85B4D264209E22C30F`.

## Boundary and remaining work

This closes only the Native text-draft prompt presentation slice. It does not change default-time semantics or close Phase 1, SEP-03, SEP-15, or overall Native separation. The full JVM gate and device behavior remain outstanding. Clear was not changed.

Implementation commit `c006049edbd562725f533c5b21c82c4f3c7566da` was pushed to `origin/feat/skin-first-separation`.
