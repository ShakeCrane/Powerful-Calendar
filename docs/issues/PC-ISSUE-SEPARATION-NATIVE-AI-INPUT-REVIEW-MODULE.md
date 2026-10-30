# Native AI image consent and OCR review module

**Status:** CODE COMPLETE; focused Compose/Host tests and build checks TESTED; full JVM gate BLOCKED by a repeatable out-of-scope Memo navigation failure; Android/device UI NOT TESTED.

**Scope:** Native skin module separation only. SEP-09 overall remains PARTIAL.

## Root cause

The Native AI image-consent and OCR text-review dialogs were rendered inline in `PowerCalApp.kt`. The same Host composable also owns the selected `Uri`, file reads, local OCR, AI client calls, chat state, and submission callbacks. Moving that implementation intact into `:native-skin` would have passed Android/AI services into presentation code.

## Change

- Added display-only dialog slot DTOs to `:skin-api` and moved `NativeAiImageConsentReview` / `NativeAiOcrTextReview` into `:native-skin`.
- The Native module receives only the busy flag, editable OCR text, and callbacks. It does not receive a URI, file resolver, OCR recognizer, AI client, Core object, or persistence service.
- `PowerCalApp.kt` remains the Host adapter. The Host still owns original-image consent, URI validation and byte bounds, the provider request, local OCR, reviewed text state, submit/cancel handling, and `SkinAlertDialog` selection. Existing actions and text remain unchanged.
- Added focused Compose coverage for explicit image consent/local OCR and OCR submission callbacks. Existing Host attachment integration coverage continues to exercise file review, edit/redaction, provider handoff, image-consent choice, and OCR routing.
- No Clear source, frozen Core, data semantics, AI transport, Native fallback, or skin default changed. Two pre-existing dirty WebSkin AndroidTest files were not staged or committed.

## Verification

All current-code checks below ran against implementation commit `1c502b728f49165daa268563419346cea4ce1dd9`, pushed to `origin/feat/skin-first-separation`.

Focused Native and Host attachment regression:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifyNativeSkinBoundary :app:testDebugUnitTest --tests '*NativeAiInputReviewComposeTest' --tests '*WebAiAttachmentFlowIntegrationTest' --offline --rerun-tasks --max-workers=2 --console=plain
```

Result: **8/8 PASS** (2 Native Compose tests and 6 existing Host attachment integration tests). Log: `.local/android-test/reports/native-ai-input-review-20261030/focused-final.log`; SHA-256 `2CCD71F236961B630DB191D6737031339C4F0E812F46CD2BCAD7ED216054E7DD`.

The complete JVM suite was attempted twice with `:app:verifyNativeSkinBoundary`, `:app:verifySkinSourceBoundary`, `:app:testDebugUnitTest`, AndroidTest compilation, APK assembly and lint. Both full runs completed **762 tests with 1 failure** in `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites`: after navigation the Host projection remained at `memoCount=0` until its 60-second timeout. This Web/Memo test does not exercise the Native AI input dialogs and was not modified. A separate isolated rerun passed **1/1**:

- First full log: `.local/android-test/reports/native-ai-input-review-20261030/full-gate.log`; SHA-256 `1ABA3AFB5BB54AE9D7F91852DD2B3EB03F715E49F2CD689C7DD1DCB8F09279EA`.
- Serial full retry (`--max-workers=1`): `.local/android-test/reports/native-ai-input-review-20261030/full-gate-retry.log`; SHA-256 `E2F2AE596E5C6FFAC7288E164C207FABB2B1FD30C6AD7811503DC02050B169F4`.
- Isolated Memo test: `.local/android-test/reports/native-ai-input-review-20261030/memo-paging-isolated.log`; SHA-256 `8FBA97A925A810308F55253598BF191A17B8BB4ADD0A18C6676C7C143D8A90B5`.

Because the full unit-test gate failed twice, it is **not recorded as PASS**. To finish the independent Native build checks, this command was run without the JVM test task:

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=1 --console=plain
```

Result: **BUILD SUCCESSFUL**, 144 actionable tasks; both source boundaries, AndroidTest Kotlin compilation, Debug APK assembly and lint PASS. Log SHA-256 `517BCDC25DE922C57CB453B2B65D36758E9CC1E5EBB1B3CD3A2740C44352EBC2`.

Debug APK at `app/build/outputs/apk/debug/app-debug.apk`: 66,500,178 bytes; SHA-256 `007CEF18C872EF977F6A6840304A5692698E1F96B6D786BD7B969E265B5E8A90`.

Two earlier attempts at the new UI test are preserved in the same report directory: the first lacked the Robolectric runner annotation; the next hit a Robolectric `ShadowTrace` heap failure while querying OCR text through a real dialog window. The final test uses a test-provided dialog slot for the OCR field, while the Native image dialog and existing Host integration still exercise `SkinAlertDialog`. Final focused results are green. ADB reported no online devices, so Android instrumentation execution and physical-device behavior are **NOT TESTED**.

## Remaining scope

This closes the Native input-dialog renderer slice only. SEP-09 remains PARTIAL; native/device parity and the full suite’s unrelated Memo navigation failure remain open. No Clear implementation work or cutover is implied.

## Git

- Implementation: `1c502b728f49165daa268563419346cea4ce1dd9` — `refactor(皮肤分离): 将原生AI图片与OCR审核迁入模块`.
- Pushed as a normal fast-forward to `origin/feat/skin-first-separation`.
