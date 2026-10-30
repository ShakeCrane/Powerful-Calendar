# Native AI renderer module boundary

**Status:** CODE COMPLETE; automated JVM/build checks TESTED; Android device interaction NOT TESTED. This closes the Native AI chat module slice only; overall Native separation remains in progress.

## Root cause and change

`NativeAiChatScreen` lived in `:app` and directly consumed `AiChatMessage`, `AiInterpretation`, `AiBatchRunner.Mode`, Android clipboard services, skin painter APIs and the Host proposal review composable. Moving the screen unchanged would have made `:native-skin` depend on the app/AI layer and would have allowed the presentation module to reach Android and Host services directly.

Moved `NativeAiChatScreen` into `:native-skin`. Added `NativeAiMessage` and `NativeAiBatchMode` display/action DTOs in `:skin-api`. The Host now projects provider messages, maps batch actions to the existing `AiBatchRunner`, owns `ClipboardManager`, and injects proposal review and avatar rendering through composable slots. AI interpretation, proposal authorization, commit handling and attachment picking remain in the Host. A message or proposal-source string remains display/input data; it does not authorize a write.

Updated existing conversation anchoring, IME and suggestion Android instrumentation sources to use the new DTO/callback interface. Added Native Compose callback/slot coverage and pure projection/mode mapping tests. No Clear, frozen Core, persistence, AI provider or authorization behavior was changed.

## Verification

All results below were produced from the same source tree that was committed as `cbec81a6500300ef0a16ded1af999c83de10ded3` and pushed to `origin/feat/skin-first-separation`.

Focused command:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifyNativeSkinBoundary :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --tests '*NativeAiChatScreenComposeTest' --tests '*NativeAiProjectionTest' --offline --console=plain
```

Result: **BUILD SUCCESSFUL**. Native boundary verification and AndroidTest Kotlin compilation passed; the two new JVM test classes passed (4 tests total).

Build and regression commands:

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests '*WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites' --offline --rerun-tasks --console=plain
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug --offline --rerun-tasks --console=plain
```

The first full attempt passed both source boundaries, AndroidTest Kotlin compilation and Debug APK assembly, but the JVM suite reported **740 tests, one failure**: `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` timed out after 60 seconds while waiting for the Memo Host state after navigation. The test was then run alone and passed. A subsequent full suite plus lint completed successfully: **740/740 JVM tests passed, 0 failures, 0 errors, 0 skipped; lint passed**. The initial timeout did not recur, but its cause was not established; preserve it as an intermittent test risk rather than treating it as proof of a product defect or hiding the failed attempt.

Evidence in the ignored local report directory:

- `.local/android-test/reports/separation-native-ai-module-20261030/full-gate.log` — first full build; contains the transient test failure. SHA-256 `C89F01EC77FACDB11888228A0D7AD7B7B7779C734D5F1246668141D2359024C3`.
- `.local/android-test/reports/separation-native-ai-module-20261030/memo-paging-isolated.log` — isolated Memo paging retry. SHA-256 `243772DC413DF2D63BAA926AE05DCD225A8C64B4287AA7509D36D2E8EAC9ECD4`.
- `.local/android-test/reports/separation-native-ai-module-20261030/full-suite-retry.log` — successful full suite and lint retry. SHA-256 `55A7635B15ADFE39442332B42FD2AB0EBB877DA6FA2E6AAA2E5AB5B70CFA7F17`.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 66,418,258 bytes; SHA-256 `96A1B3AF4F96DEA1B050D0A82F7CB084F38BAF90A61A8A8E891A6A50A5F0C623`.
- `git diff --cached --check` passed before commit. `adb devices -l` returned no devices; Android instrumentation and physical-device behavior are **NOT TESTED**.

## Remaining work

Native Month and Timeline, Draft Deck, renderer adapters and other shared Host projections remain in `:app`. Next independent page slice is Month: project display-only values in the Host and return opaque action keys so Core occurrence identity and business semantics remain Host-owned. Clear remains unchanged.

## Git

Implementation commit `cbec81a6500300ef0a16ded1af999c83de10ded3` (`refactor(皮肤分离): 将原生 AI 聊天页迁入独立模块`) is pushed to `origin/feat/skin-first-separation`. The two pre-existing dirty WebSkin instrumentation files were not staged or committed.
