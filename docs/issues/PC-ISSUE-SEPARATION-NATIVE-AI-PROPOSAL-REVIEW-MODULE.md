# Native AI proposal review renderer module

**Status:** CODE COMPLETE; focused and full JVM/build checks TESTED; Android device UI NOT TESTED.

**Scope:** Native UI module separation only. SEP-10 as a whole remains PARTIAL.

## Root cause

The Native AI proposal review card was rendered in `:app` and directly consumed `AiInterpretation`, `AiProposal`, `DraftForm`, and Core operation types. Moving that composable into `:native-skin` unchanged would have introduced an app/AI/Core dependency into the Native presentation module. Keeping it in the Host also left this Native surface outside the module boundary.

## Change

- Added display-only proposal review DTOs to `:skin-api`. Rows carry their original Host list position, a display group/caption, title and time strings, blocked reason, and Host-computed publishability. They do not contain AI parser objects, Core forms, or Store identities.
- Added `NativeAiProposalReview` to `:native-skin`. It renders the existing grouped review card, selection state, busy/blocked states, and actions. Apply, discard, and supplement remain callbacks; the renderer performs no authorization or persistence.
- Reduced the `:app` composable to a projection and callback adapter. Host-side projection computes labels and publishability with the existing Core rules; existing proposal selection and action semantics remain in the Host flow.
- Added a projection regression that verifies operation grouping, preserved row positions, blocked/publishable state, display text, and the warning limit. Existing Compose review tests now exercise the Host adapter backed by the Native renderer.
- No Clear, frozen Core, persistence, default skin, or Native fallback code changed. Two pre-existing dirty WebSkin AndroidTest files were not staged or committed.

## Verification

All checks below ran against the exact implementation tree committed as `629b8d0fc2dd0c7f51e1cfd47e0f99a68648b408` and pushed to `origin/feat/skin-first-separation`.

Focused regression:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.ui.v2.AiProposalReviewComposeTest' --tests 'dev.powerfulcalendar.ui.v2.AiReviewComposeTest' --tests 'dev.powerfulcalendar.ui.v2.NativeAiProjectionTest' --tests 'dev.powerfulcalendar.ui.v2.NativeAiChatScreenComposeTest' --offline --rerun-tasks --max-workers=2 --console=plain
```

Result: **11/11 PASS** across the four selected suites. Log: `.local/android-test/reports/native-ai-proposal-review-20261030/focused-final.log`; SHA-256 `4D2689D59802E5E36B89813A2BDD972C60A9CB7CC4D4C80CAD86F9D2BD6105CD`.

Full build and regression gate:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=2 --console=plain
```

Result: **BUILD SUCCESSFUL**, 152 actionable tasks; Native and skin source boundaries PASS; AndroidTest Kotlin compilation PASS; Debug APK assembly PASS; lint PASS; **759/759 JVM tests PASS, 114 XML suites, 0 failures, 0 errors, 0 skipped**. Full log: `.local/android-test/reports/native-ai-proposal-review-20261030/full-gate.log`; SHA-256 `24D8099505EAEE8F5E77C43F4D7214151756FED567E04E1188FB7692C0178E6D`.

Debug APK at `app/build/outputs/apk/debug/app-debug.apk`: 66,483,794 bytes; SHA-256 `A246BA5CDCC7A31D23AE29563553334B7AE4862AD32C75E0180F498C5483AC44`.

`adb devices -l` returned no online devices. Android instrumentation execution and physical-device interaction are **NOT TESTED**; AndroidTest source compilation is not represented as device evidence.

## Remaining scope

This closes the Native renderer module slice, not SEP-10 parity. The AI batch authorization dialog and remaining AI workflows still require separate Native separation and parity evidence. No cutover or default-skin change is implied.

## Git

- Implementation: `629b8d0fc2dd0c7f51e1cfd47e0f99a68648b408` — `refactor(皮肤分离): 将原生 AI 提案审核卡迁入模块`.
- Pushed as a normal fast-forward to `origin/feat/skin-first-separation`.
