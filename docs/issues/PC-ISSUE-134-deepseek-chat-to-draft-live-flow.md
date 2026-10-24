# PC-ISSUE-134 — DeepSeek chat-to-pending-draft live flow

Date: 2026-10-24

Priority: P1 follow-up to PC-ISSUE-002

Status: **PASS for the authorized DeepSeek V4.1 Flash two-stage emulator path; PC-ISSUE-002 remains PARTIAL / BLOCKED**

## Scope and setup

PC-ISSUE-133 verified the complete local client-to-SQLite pipeline with a loopback stub. This follow-up tested whether the authorized live model supplies the chat proposal marker and whether its structured result reaches a reviewable pending draft without publishing.

Ran one instrumentation test on Android 15/API 35 emulator emulator-5554. It loaded the already configured HTTPS endpoint and credential without displaying or recording credential contents, overrode the model in memory to deepseek/deepseek-v4.1-flash, and disabled calendar-context sharing. The prompt contained only a synthetic ACTIVITY; it did not include real calendar data.

The test did not open an Activity or UI. It wrote only to a uniquely named temporary SQLite database and deleted that database in finally. The app's default calendar database and persisted AI settings were not modified.

## Result

- Chat request: HTTP 200 in 4.693 seconds; output budget 540; proposal intent marker present; visible reply length 70 characters.
- Structured proposal request: HTTP 200 in 2.769 seconds; output budget 1800; one ACTIVITY proposal; zero warnings.
- The chat client handed off only the exact current synthetic user text as proposalSource.
- The proposal became one PENDING CREATE draft. SQLite close/reopen preserved draft ID, queue order, and PENDING status.
- Formal events, commit history, and schedule intent remained empty before and after reopen.
- The filtered diagnostic log contains exactly one chat completion and one structured-proposal completion; no automatic retry occurred.

This signs the live DeepSeek two-stage client-to-draft path only. It does not sign the free provider, UI/card presentation, user approval/publish flow, physical devices, or OEM behavior.

## Verification evidence

Instrumentation result: **1/1 PASS**, zero failures/errors/skips. Test XML: .local/android-test/reports/freeze-20261024/pc-issue-134-artifacts/TEST-PowerCal_API35(AVD) - 15-_app-.xml, SHA-256 F5C6970C5B1B2FA64DC62243261A1E71499A61A01CFD8589E0F35EE58EED2575.

- Targeted AndroidTest Gradle log: .local/android-test/reports/freeze-20261024/pc-issue-134-deepseek-chat-draft-probe-r3.log, SHA-256 3412A5ECA121F388D0673D23AD0DE3C5DDA178EA8670154658EA393539503728.
- Filtered PowerCalAiPerf log: .local/android-test/reports/freeze-20261024/pc-issue-134-deepseek-perf.log, SHA-256 B292B8C79D8373A505034503681D9B27512F52C1358F58CA9ACE60279118A7E2. It records stage, status, elapsed time, budget, proposal intent/count, and warning count only.
- Full regression command:

      .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain

  Result: **BUILD SUCCESSFUL**, 469/469 JVM tests across 58 suites, zero failures/errors/skips; source boundary, Debug APK assembly, Android test APK assembly, and lint passed. Lint reported 0 errors / 58 warnings.
- Full Gradle log: .local/android-test/reports/freeze-20261024/pc-issue-134-full-gradle.log, SHA-256 935FC910ACB6663693564E5B5D323A018FC49A41B800BB4EA6DDA9C025CE726B.
- Lint XML: .local/android-test/reports/freeze-20261024/pc-issue-134-artifacts/lint-results-debug.xml, SHA-256 2BEABC23C862180FE0301AC2F3A3FABDBC432D106CFDDFBC0E1188879BCD1B47.
- Debug APK: app/build/outputs/apk/debug/app-debug.apk, SHA-256 35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4.
- Android test APK: app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk, SHA-256 9B07836BF9E2ED66D7D2FD3F3BCB794183AE771AA6FE2C4D525A30EEE5FA7627.

Two earlier launch attempts were harness failures and did not contact the model: an unquoted Gradle runner-filter argument was parsed as a task (log SHA-256 F8A675C48EE3DF356476DC0687EF7CBB9C4330BAD72D1F9443103B059CBB8992), and the corrected offline invocation lacked the cached Android test host-output plugin (log SHA-256 263003BA1E4430905E47E57F0F6E73859C65B4DA3F46AB546B2744DE62D29283). The final online dependency resolution completed and the selected test passed.

## Freeze decision

PC-ISSUE-134 signs the authorized live DeepSeek path from chat marker through structured proposal to a durable pending draft without formal publication. REC-P1-047 remains BLOCKED because its free-provider acceptance still fails at the structured HTTP 429 stage; the DeepSeek result does not replace that requirement. PC-ISSUE-002 remains PARTIAL / BLOCKED, and the overall core freeze remains HOLD.

Test commit 477ddf44f1199e3316c5981e0a640c41efcdd416 — test(ai): 验证DeepSeek两阶段待审草稿链路 — is pushed. The issue report and ledger/handoff updates are committed separately.

Structural-only ledger validation: `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-134.json`, SHA-256 `FB25654B9A34DFD676713F580D1C955B24E6BCDBBE504ECE8D16150A8582465A`; ledger SHA-256 `CD6275A893058EB678FBD9B3BEA04EAEAB6BFEEE26BFEFDC18366074852C8E58`. It reports 209 unique rows, 164 PASS / 0 FAIL / 8 BLOCKED / 30 NOT TESTED / 7 SPEC UNRESOLVED, and `product_behavior_tested=false`; this is a structural check, not product evidence.

### Follow-up probe safety

After this successful run, the paid and free live instrumentation probes were made explicit opt-in via `powercalLiveProbe=deepseek|free`; without that argument, they do not load settings or make network calls. Test-only commit `c33eb36a9308066983667cbf0091b8c451a27806` is pushed. Its full offline regression compiled AndroidTest and passed 469/469 JVM tests, but did not execute instrumentation. A later free-provider retest was not sent because `emulator-5554` no longer had the app package installed. The original 1/1 DeepSeek result above remains the live-flow evidence; the free-provider acceptance remains blocked under PC-ISSUE-002.
