# PC-ISSUE-133 — AI chat-to-pending-draft local pipeline

Date: 2026-10-24

Priority: P1 follow-up to PC-ISSUE-002

Status: **PASS for the narrowed local client-to-SQLite pipeline; PC-ISSUE-002 remains PARTIAL / BLOCKED**

## Finding and scope

PC-ISSUE-121 verified that a chat marker cannot replace the current user's raw text with a model-generated source. PC-ISSUE-118 verified structured CREATE/REPLACE parsing and pending-draft creation. Those checks were separate; no single regression connected both client stages to SQLite persistence and reopen.

Added one Robolectric API 34 loopback test joining those stages. The fake chat response carries a deliberately fabricated source containing an instruction to delete calendar events. The client exposes only the original synthetic user input as proposalSource. The same client then sends that source through the structured-proposal stage; AiDraftService persists the resulting proposal as one pending draft.

The test confirms the draft remains PENDING with stable draft ID and queue order after closing and reopening SQLite. There is no formal event, commit history, or schedule intent before or after reopen. The test uses a uniquely named disposable database and deletes only that database in finally.

This is local loopback evidence only. It does not test live-model intent classification, provider availability, UI card presentation or user actions. No external provider, DeepSeek, physical device, or UI was used.

## Verification evidence

Focused command:

    .\gradlew.bat :app:testDebugUnitTest --tests dev.powerfulcalendar.ai.AiChatDraftPipelineTest --offline --rerun-tasks --console=plain

Result: **1/1 PASS**. Test log: .local/android-test/reports/freeze-20261024/pc-issue-133-targeted.log, SHA-256 1B343F9AB19B66F524C58C9ACEF1C95E761E5DEA2783A23B1A673A7CE2E5C1E6. JUnit XML: .local/android-test/reports/freeze-20261024/pc-issue-133-artifacts/TEST-dev.powerfulcalendar.ai.AiChatDraftPipelineTest.xml, SHA-256 78CD8691A2ABA35A8EB9CAF50A5CCE8C4FE0C046292ADE9B41DFB442DAE9CAEF.

Full command:

    .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain

Result: **BUILD SUCCESSFUL**, 469/469 JVM tests across 58 suites, zero failures/errors/skips. Source boundary, Debug APK assembly, Android test APK assembly, and lint passed; lint reported 0 errors and 58 warnings. The total includes one pre-existing protected untracked test, which was not modified or staged.

- Full Gradle log: .local/android-test/reports/freeze-20261024/pc-issue-133-full-gradle.log, SHA-256 862CCBC38283B8D8D8776FDB98EC246ED1F2C1E11A5337C580D0A1D5F9364F62.
- Lint XML: .local/android-test/reports/freeze-20261024/pc-issue-133-artifacts/lint-results-debug.xml, SHA-256 2BEABC23C862180FE0301AC2F3A3FABDBC432D106CFDDFBC0E1188879BCD1B47.
- Debug APK: app/build/outputs/apk/debug/app-debug.apk, SHA-256 35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4.
- Android test APK: app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk, SHA-256 93B762B8B0DB9F9574EA51BDB7A5E6C6378E6EE5E8E7BD9C019FA321AA9F6A28.

## Freeze decision

This report signs only the deterministic local chat → original-source handoff → structured parser → PENDING SQLite draft → reopen path. REC-P1-047 remains BLOCKED: the real free provider returned HTTP 429 during the structured-proposal stage, and this test cannot establish live-provider availability or real model behavior. PC-ISSUE-002 remains PARTIAL / BLOCKED; overall freeze remains HOLD.

Test commit b4f5179efa689015bb5ec73a2987a5e76f2c6b82 — test(ai): 验证聊天到待审草稿核心链路 — is pushed. This report and the ledger/handoff updates are committed separately.
Structural-only ledger validation: .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-133.json, SHA-256 CA6AD79C2254613BE21C06FCD23A6B49756227190F32CA0C672A7CF507D3002C; ledger SHA-256 F2B851D069C7322AAFA636E7406C3B3FA9A9C94B608BD1E3DD999F6CD3966820. Structure and counts only; product_behavior_tested=false.
