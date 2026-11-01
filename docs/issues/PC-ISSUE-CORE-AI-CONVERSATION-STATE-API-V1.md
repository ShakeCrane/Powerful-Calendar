# PC-ISSUE-CORE-AI-CONVERSATION-STATE-API-V1 — Core-owned AI transcript state

- **Date:** 2026-11-02 (Asia/Shanghai)
- **Priority:** P1 — PHASE 1 Core Feature API
- **Status:** JVM/build/source-boundary **PASS**; Android device and packaged WebView **NOT TESTED**
- **Code commit:** `f3c2bd6c5fb889f598e52070a29fbe5a3308c3a7` (`feat(分离): 接入 Core AI 对话状态 API`)
- **Base:** `6687cfdf33a365516231ae5d8b5508d62250124f`
- **Phase:** PHASE 1 remains open. This does not sign Native/Clear parity or cutover.

## Objective and outcome

The previous chat API accepted caller-supplied history while `PowerCalApp` owned transcript state, persistence scheduling, clear, and rewind. Web mutations also used a Host-side reducer. This slice moves canonical transcript state and its mutations behind versioned `AiFeatureApiV1` commands while retaining the existing Android file format and Web revision checks.

## Implementation

- Added versioned conversation snapshot, mutation status/receipt, rewind mode, persistence status, and `AiConversationHistoryPortV1` DTOs/interfaces to the pure Kotlin `:core-api` contract.
- `StoreAiFeatureApiV1` now owns the canonical transcript and process-local revision. Query returns detached snapshots. Append, clear, and rewind require the expected revision; rewind resolves a stable message ID and retains the prior user/assistant behavior, including edit-and-resend prefill. The in-memory transcript is capped at 200 messages.
- `AiChatRequestV1` no longer accepts a history list. It carries the expected conversation revision and exact `userMessageId`; Core checks both against the current transcript and builds the bounded provider history itself. A mismatch is rejected before a provider call. The model adapter receives a distinct `AiChatModelRequestV1`.
- `persistConversation(expectedRevision)` rejects stale snapshots and serializes writes. `AndroidAiConversationHistoryPortV1` delegates to the existing app-private `powercal-chat-history.json` `AtomicFile`, keeps all six existing JSON fields, and writes on `Dispatchers.IO`.
- `PowerCalApp` now uses Core commands for user/assistant appends, Web clear/rewind, and Native clear/rewind. Web actions retain their exact history revision. Native display indices are resolved to Core message IDs in the Host. Compose state remains a projection of Core snapshots; temporary AI-cache cleanup remains an Android capability.
- No database schema, backup format, event semantics, proposal authorization, provider credentials, or Native fallback behavior changed. Attachments, OCR/file intake and general tool orchestration remain outside this API slice.

## Expected behavior and observed evidence

- **Expected:** Native and Web operate on one canonical transcript; stale revisions and mismatched chat-turn IDs cannot mutate history or send a different turn; clear and rewind preserve their current behavior; Android persistence remains backward-compatible.
- **Observed:** API tests verify revision fencing, exact-turn binding, edit/retract/clear behavior, missing/ambiguous targets, model-history sourcing, and persistence results. The Android adapter test round-trips `role`, `content`, `proposalSource`, `messageId`, `createdAtMillis`, and `zoneId` through the existing JSON file shape. A Robolectric `PowerCalApp` test verifies Web edit-and-resend, stale clear rejection, persistence, no-op clear, and unchanged Store counters.

## Verification

Environment: Windows PowerShell, Gradle Wrapper 8.14.3, `ANDROID_HOME` and `ANDROID_SDK_ROOT` set for the Gradle process to `E:\android-sdk`, offline dependencies, one worker.

| Check | Result | Evidence |
| --- | --- | --- |
| Focused API/persistence/Web/Native/Bridge regression set | **42/42 PASS**, 0 failures/errors/skips | `.local/android-test/reports/core-ai-conversation-state-20261102/focused-final.log`; SHA-256 `E1B979A0A3C0B4963B4EFB7A99416B606D90C398D1037E1074FC81BD1939D378` |
| Forced serial offline full gate | **BUILD SUCCESSFUL**; **849/849 JVM tests**, 135 XML suites, 0 failures/errors/skips; 154 tasks | `.local/android-test/reports/core-ai-conversation-state-20261102/full-gate-final.log`; SHA-256 `71DC3A1F0225833B08BD7D821612C6171A77E73105B51082F1D97531D49AB203` |
| Source boundaries | `verifySkinSourceBoundary` and `verifyNativeSkinBoundary` **PASS** | Full-gate log above |
| Debug APK | **66,863,726 bytes**; SHA-256 `162AED21E5939D2B015B2A0D6EBAF0A99C9FEE42557850B0B73A530CE42F9C6F` | `app/build/outputs/apk/debug/app-debug.apk` |
| Lint | **0 errors / 53 warnings** | `app/build/reports/lint-results-debug.xml` |
| AndroidTest | Kotlin sources **compiled**; tests were not executed | Full-gate log above |
| `git diff --check` | **PASS** | Final source/doc check |
| Android device / emulator | **NOT TESTED**; `adb devices` listed no devices and `emulator -list-avds` was empty | Runtime UI and packaged WebView are not covered by this gate |

Full command:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
```

The first Gradle attempt stopped before task configuration because the isolated worktree had no SDK path. The rerun used the repository-local SDK through process-level environment variables; no `local.properties` or SDK files were added. An early rewind test fixture used the same ID for both ambiguous-target and edit-role assertions; the fixture was corrected, and the final focused and full runs passed. A full run before adding the exact `userMessageId` fence passed 848/848; the final 849/849 run above includes that change.

## Limits and next work

- Transcript data persists in the existing file, but its revision and chat receipt cache are process-local; the revision restarts from zero when the API instance is recreated. No cross-process mutation receipt was added.
- This is JVM/Robolectric/build evidence. Native and Clear device routes, packaged Android WebView, real provider behavior, external/OEM storage, TalkBack, and physical-device behavior remain **NOT TESTED**.
- The API does not absorb Android URI permissions, document pickers, local OCR, file extraction, user consent, provider credentials, or general AI tool orchestration. Those remain Host/platform responsibilities pending their own versioned intake boundary.
- No acceptance-ledger row is marked PASS by this report. PHASE 1 and overall runtime/skin parity remain open.
- **唯一 NEXT_ACTION:** define a versioned Core AI intake boundary for reviewed image/OCR/ICS/file evidence while keeping URI grants, bounded reads, pickers, local OCR, and user consent in Host adapters.
