# PC-ISSUE-CORE-AI-FEATURE-API-V1 — Structured AI generation and batch feature API

- **Date:** 2026-11-01 (Asia/Shanghai)
- **Priority:** P1 — PHASE 1 Core Feature API
- **Status:** PASS for the verified JVM/build scope; Android instrumentation and packaged WebView behavior NOT TESTED
- **Scope:** Structured proposal generation, batch candidate selection, reviewed batch execution, state, cancellation, recovery, and stuck-session release
- **Code commits:** 19eaf2c248cfae009904da26c6e68ee5896c448f (implementation) and 98fda6ef08c53e1fd6fd1c25f3b0d9a17af3f2e3 (API recovery regression), both fast-forward pushed to origin/feat/skin-first-separation
- **Phase status:** PHASE 1 remains OPEN. This slice does not establish full Core extraction, Native/Skin parity, or cutover readiness.

## Expected behavior

Structured AI generation and batch commands need a stable, versioned Core Feature API path shared by Native and Web Host workflows. Model output must remain untrusted: generation may return reviewable proposals only; model-selected batch IDs must resolve against the exact local candidate snapshot; writes must require an explicit Host review bound to the exact mode, IDs, versions, and operations. The existing Store remains responsible for transactional version checks, fencing, cancellation, recovery, and commits.

## Implementation

- Added pure Kotlin `AiFeatureApiV1` DTOs and commands to `CoreFeatureApiV1.ai`.
- Added `StoreAiFeatureApiV1` over the existing `PowerStore`, `AiBatchRunner`, and `StoreEventFeatureApiV1`; no second store, scheduler, or transaction path was introduced.
- Added the Host-owned `AndroidAiModelPortV1`. It loads existing protected AI settings and credentials and invokes `AiClient`; credentials and Android context are not part of the `:core-api` contract.
- Routed manual structured generation, automatic conversation-task interpretation, and file/image-derived structured proposals through `generateProposal`. Results remain detached candidates; `AiProposalFeatureApiV1` continues to own staging and reviewed application.
- Routed batch selection through the API. The model returns draft IDs only; the API resolves them against exact candidate snapshots and rejects duplicate, excessive, or out-of-scope IDs. Host-set explicit “all” selection does not call the model.
- Routed batch state, cancellation, interrupted recovery, reviewed execution, and stuck-session release through the API. A one-use Host marker binds execution to the exact review mode and candidate snapshot. The API regression rejects a forged release marker, requires the stale cancellation threshold, consumes a valid Host approval once, and returns an unknown-outcome terminal DTO without raw report details. The existing Store transaction rechecks versions and operations, and `AiBatchRunner` retains per-item fencing and cancellation behavior.
- Adapted Native and Web presentation projections to stable session DTOs and sanitized terminal reports. Skin projections omit internal session IDs, lock epochs, draft scope IDs, and stored exception details.
- No database schema, backup format, frozen Core behavior, or default skin selection changed.

## Verification evidence

Environment: Windows PowerShell, repository-local Android SDK at `E:\android-sdk`, Gradle 8.14.3, offline build. The SDK ADB listed no connected device, and no AVD was configured.

| Check | Result | Evidence |
| --- | --- | --- |
| Focused AI API, proposal, batch-selection, task pipeline, Host adapter, and Web projection suites | **55/55 PASS**, 10 XML suites, 0 failures/errors/skips | `.local/android-test/reports/core-ai-feature-api-20261101/focused-api-tests-final.log`, SHA-256 `A9AE42EC7DC8AE285EDD69EC4A728948EC6A4E05EF6429DB13E6CB36B07BADFA` |
| Forced serial offline gate: both source boundaries, complete JVM suite, Debug APK, lint, AndroidTest Kotlin compilation | **BUILD SUCCESSFUL**; **843/843 JVM tests**, 134 XML suites, 0 failures/errors/skips; lint 0 errors / 53 warnings | `.local/android-test/reports/core-ai-feature-api-20261101/full-gate-final.log`, SHA-256 `1C2F57B2DC50310C4631B16300E3E8566D2827EE57DEC05F1297CB0F6E4E496F` |
| `git diff --check` | **PASS** | Final source/document diff check |
| Debug APK | **66,830,958 bytes** | `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `98E719B76A055A9B7045086193653F62DE424BC81BADC79A3F8C2B3EA8A3CE18` |
| Android instrumentation / actual Native UI / packaged Android WebView / physical or OEM device behavior | **NOT TESTED** | No ADB device or configured AVD was available; AndroidTest Kotlin compilation alone is not runtime evidence |

The forced full-gate command was:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
```

The focused command selected `AiFeatureApiV1Test`, `AiProposalFeatureApiV1Test`, `CoreApiAdapterTest`, `AiBatchSelectionTest`, `AiChatDraftPipelineTest`, `AiTaskPipelineTest`, `AiPartialProposalLoopbackTest`, `AiPartialProposalFallbackTest`, `WebAiBatchProjectionTest`, and `WebAiProposalReviewIntegrationTest`.

## Limits and next work

- Ordinary AI chat replies, image/OCR transport, file selection/consent, provider orchestration, and the general tool registry remain Host work.
- The proposal-generation request has no durable replay receipt. Existing `AiProposalFeatureApiV1` replay limitations remain as documented there.
- The API and Host/JVM integration tests do not prove behavior with a live model provider, installed Web skin, Android WebView, or physical/OEM device.
- Native fallback remains available. This report does not authorize a skin cutover or mark any broader acceptance row PASS.
- The Memo visibility precheck and runtime Memo projection have since moved to `MemoFeatureApiV1`; see the [Memo API report](PC-ISSUE-CORE-MEMO-API-V1.md). The current PHASE 1 action is to remove the remaining Web event-open and AI proposal target Store re-reads through the Event API while preserving revision, occurrence, and editor-availability checks.

## Source paths

- `core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/AiFeatureApiV1.kt`
- `core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/StoreAiFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/AndroidAiModelPortV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebAiBatchProjection.kt`
- `app/src/test/java/dev/powerfulcalendar/core/api/v1/AiFeatureApiV1Test.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebAiBatchProjectionTest.kt`
