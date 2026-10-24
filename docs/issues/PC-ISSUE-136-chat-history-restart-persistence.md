# PC-ISSUE-136 — AI chat history survives process and system restarts

Date: 2026-10-24 | Priority: P1 | Status: **PASS within the tested local-persistence scope**

## Requirement and reconstructed acceptance row

Core contract v0.9.4, invariant 10, requires chat history to persist locally across restart and sensitive credentials never to enter chat history. The freeze handoff also calls for restart and recovery evidence. This report signs the newly reconstructed `REC-P1-083`; it does not map to any unavailable historical acceptance ID.

The test exercises `AiChatHistoryStore` directly through Android instrumentation. It does not verify normal Activity/UI restoration, user-visible clearing controls, live model conversations, provider behavior, physical-device/OEM behavior, or recovery from low-memory/process crashes.

## Test method

- Added `AiChatHistoryProcessRestartTest`, using two synthetic messages in the app-private `powercal-chat-history.json` file. On cycle 0, the test first asserts that this file does not exist and that `AiChatHistoryStore.load()` is empty; it refuses to overwrite an existing history.
- On API 35 AVD `emulator-5558`, AndroidJUnitRunner ran 13 invocations. Cycle 0 created the synthetic conversation. The host then issued one Android VM reboot (`boot_count` 3→4); cycles 1–12 each ran in a newly started process. Eleven host force-stop rounds stopped the target and instrumentation packages between the remaining runs.
- Every invocation compared the loaded messages to the same two synthetic messages and checked that the serialized history file existed and was non-empty. The final cycle removed only that file, after the assertions passed; a corrected metadata-only `ls` check confirmed it was absent afterward.
- No provider was called, no credential was read, and no other app data was read or changed. The AVD was stopped after the run; other running emulators were left untouched.

## Result

**PASS: 13/13 instrumentation invocations, 13 distinct process PIDs, 1 Android VM reboot, and 11 host force-stop rounds.** Both synthetic messages were recovered unchanged after each process start and after the VM reboot. The test-owned history file was absent after the final successful assertion and cleanup.

Two host-runner attempts were retained but excluded from the pass count: the first stopped after cycles 0 and 1 because the PowerShell runner assigned to its reserved `$PID` variable; the second stopped before invoking AndroidJUnitRunner because its `adb shell` metadata existence probe was incorrectly quoted. Neither produced a failed app assertion. The corrected third runner continued cycles 2–12 and completed all final assertions. Logs for all three attempts are retained below.

## Regression and evidence

Full offline Gradle command:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

Result: **469/469 JVM tests across 58 suites**, zero failures, errors, or skips; source-boundary verification, Debug APK assembly, AndroidTest APK assembly, and lint passed. Lint reported 0 errors and 58 warnings. The working-tree run included the pre-existing protected, untracked `DraftDeckFeedbackTest.kt`; that file remained unchanged and unstaged. This is build/JVM regression evidence; Android behavior is separately covered by the instrumentation run above.

- Corrected continuation log: `.local/android-test/reports/freeze-20261024/pc-issue-136-chat-history-r3.log`, SHA-256 `EA9A2F55CA6ABA4E4391AECEC1703F49EDBEC603A005B1103AB914E7A7E5C0C5`.
- Combined run summary: `.local/android-test/reports/freeze-20261024/pc-issue-136-chat-history-r3.json`, SHA-256 `BF218E95037B8159C5E867429EE1F973CEF43A15B0E9A03B26B2F2063DA52F7B`.
- Initial runner log (cycles 0 and 1 passed; host orchestration then stopped): SHA-256 `9F58B03876ABACF5A69F169952D5039B13DFF4A822268CC9488EDDF61417C3DE`.
- Second runner log (stopped at precondition before instrumentation): SHA-256 `FA3CA3FD5F66FFD1AD222DEFA358E1F92CE2CA6A774616408DC50C739AF27C31`.
- Full Gradle log: SHA-256 `52148CECAC914B46117498F4D7236D312D3254635FB404527B1FF777AAC59C59`.
- Debug APK: SHA-256 `35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4`.
- AndroidTest APK: SHA-256 `D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A`.
- Lint XML: SHA-256 `2BEABC23C862180FE0301AC2F3A3FABDBC432D106CFDDFBC0E1188879BCD1B47`.

Tested code baseline before the report/test-source commit: `47452a05d25a4686a77b72e4ac7c614efd33f732`. No production code, event data, design assets, or UI behavior changed.

## Remaining scope

This is a local store and Android VM lifecycle sign-off. It does not claim live session restoration, UI presentation, explicit clear-history controls, credential storage/exclusion behavior, low-memory recovery, or physical-device/OEM coverage. No credential was read or written during this test. Those requirements remain independently tracked in the reconstructed matrix; the overall core freeze remains **HOLD**.
