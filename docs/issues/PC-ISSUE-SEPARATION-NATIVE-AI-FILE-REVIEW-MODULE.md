# Native AI extracted-file review module

**Status:** CODE COMPLETE; focused and full JVM tests, source-boundary checks, AndroidTest Kotlin compilation, APK assembly and lint TESTED; Android/device UI NOT TESTED.

**Scope:** Native renderer separation only. SEP-09 overall remains PARTIAL.

## Root cause

The extracted-file review surface was composed inline in `PowerCalApp.kt`. It mixed Native presentation (review title, privacy copy, editable extracted text, optional consent checkbox and ICS preflight summary) with Host decisions (ICS parsing and direct-import eligibility, persistent consent, import and AI submission callbacks). That left this Native input surface outside `:native-skin`.

## Change

- Added `NativeAiFileReview` in `:native-skin`, reusing the existing display-only `NativeAiInputReviewDialog` slot. It displays the file name, extraction/privacy notices, optional ICS summary, editable text, optional consent control, busy state and Host-selected submit label.
- `PowerCalApp` remains authoritative for the source URI, file reader, parsed ICS result, direct-import predicate, consent storage and all actions. It continues to supply the existing skin-aware `SkinAlertDialog` slot.
- The Native renderer receives only strings, booleans and callbacks. No URI, file reader, parser, provider client, Core model, store or persistence object crosses the boundary.
- Added Compose regressions for text redaction/edit callback, explicit consent callback, ICS summary and deterministic-import button selection.
- No Clear source, frozen Core, persistent data semantics, AI transport, skin default or Native fallback changed.

## Verification

All checks below ran against implementation commit `80859e3b0a8e9d456a16011314f3afb992930960`, pushed to `origin/feat/skin-first-separation`.

Focused command:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest --tests '*NativeAiInputReviewComposeTest' --tests '*WebAiAttachmentFlowIntegrationTest' --offline --rerun-tasks --max-workers=2 --console=plain
```

Result: **10/10 PASS** — 4 Native input-review Compose tests and 6 existing Host attachment integration tests. Log `.local/android-test/reports/native-ai-file-review-20261030/focused-retry.log`; SHA-256 `ED04B2069C710F4155664F693B82A5D0AF911968A51C9E23F1D0F13A6DC896BB`.

Full gate:

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=1 --console=plain
```

Result: **BUILD SUCCESSFUL**, 152 actionable tasks; **764/764 JVM tests passed in 115 suites**; both source-boundary checks, AndroidTest Kotlin compilation, Debug APK assembly and lint passed. Full log `.local/android-test/reports/native-ai-file-review-20261030/full-gate.log`; SHA-256 `06E337649348C4DD394B4B100A5608FE91561B1B0C87A73ED53F83CADD059B53`.

Debug APK `app/build/outputs/apk/debug/app-debug.apk`: 66,500,178 bytes; SHA-256 `E979D74E3B2FDC325053F782E4108C7C480F51065AACB93A60564CBA9C1C542D`.

ADB had no online devices during the check. Android instrumentation execution, device Picker/OCR flow and physical/OEM behavior are **NOT TESTED**.

## Remaining scope

This closes only the Native extracted-file review renderer slice. SEP-09 remains PARTIAL; device verification and remaining AI flow coverage continue to be open. No Clear repair or skin cutover is implied.

## Git

- Implementation: `80859e3b0a8e9d456a16011314f3afb992930960` — `refactor(皮肤分离): 将原生AI文件核对视图迁入模块`.
- Pushed as a normal fast-forward to `origin/feat/skin-first-separation`.
