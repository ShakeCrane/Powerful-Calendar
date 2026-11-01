# PC Issue — Core AI proposal tool dispatch v1

- **Date:** 2026-11-02
- **Phase:** PHASE 1 — Establish Core Feature API
- **Status:** **PASS for the bounded proposal-tool slice; PHASE 1 remains open**
- **Code commit:** `a3d3c94973a2698264acf53e26ff2275a81b356b`
- **Base:** `2f1aca194cd0f0de6193055a00c61e40f5a0cb0d`

## Gap and result

Ordinary chat had no provider Function Calling transport or Core-owned dispatch path. This slice adds one Core-registered function, `calendar.propose.v1` (`calendar_propose_v1`), and routes an accepted call through the existing `AiFeatureApiV1.generateProposal` command.

The tool only returns a detached proposal for the existing review surface. It cannot stage drafts or publish formal events. Native and Web Host callers still enter through the same `CoreFeatureApiV1.ai` service. No frozen event semantics, Store schema, backup format, default skin, or cutover state changed.

## Contract and checks

- `:core-api` defines the registry entry, schema version, input/output bounds, context permission scope, read/write class, idempotency policy, untrusted call DTO, and typed execution receipt.
- The Android Host serializes only the exact Core registry entry and parses provider `tool_calls` as untrusted data. If a tool-enabled request returns HTTP 400/422, it makes one plain-chat fallback and caches that endpoint/model as unsupported for 12 hours, with a 64-entry process-local cap. The cache key is a digest; no endpoint or credential is logged.
- Core accepts exactly one advertised function with a bounded call ID and an empty JSON object, rechecks the current conversation revision, derives the proposal request ID from the chat request, and invokes the existing proposal command. Unknown, malformed, oversized, multiple, unadvertised, and stale calls return rejection without proposal generation.
- The normal chat request receipt replays the same tool result for the same request ID while the process cache is alive. These outer receipts and the unsupported-provider cache are not durable across process restart. The proposal command and existing review/approval flow remain authoritative for any later write.

## Verification evidence

- Focused command: `:app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.api.v1.AiFeatureApiV1Test' --tests 'dev.powerfulcalendar.ai.AiChatClientTest' --offline --rerun-tasks --max-workers=1 --console=plain` — **36/36 PASS**: API suite **24/24**, provider-client suite **12/12**, no failures/errors/skips.
- Full command: `:app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon` — **861/861 JVM tests**, **135 XML suites**, 0 failures/errors/skips; both source boundaries, Debug APK, lint, and AndroidTest Kotlin compilation passed.
- Lint: **0 errors / 53 warnings**. `git diff --check` passed.
- Debug APK: **66,896,494 bytes**, SHA-256 `FFAE06695EA60354BAA798834CB7F282A95FA0E158FEAD4862646B09FA4C09D4`.
- Focused log SHA-256: `3C11159D16B6E788BF4997347ECCE0336C17F309BD0B34541C94E2F01B292875`.
- Final full-gate log SHA-256: `92DA0C84A1084E5AB6CE2A6A923F163AC7BD9AFD0944908A686F2E2B354C2A50`.
- Logs are under `.local/android-test/reports/core-ai-tool-orchestration-20261102/`.

One earlier full-gate attempt hit a transient SQLite lock in the unrelated `SystemCalendarImportTest.rejectedItemsArePersistedAsWarningsWithoutCalendarWrites` case. The isolated case passed **1/1**, and the subsequent full gate passed all 861 tests. No unrelated source or test was changed.

## Limits and remaining work

The local loopback provider tests cover schema submission, returned-call parsing, and the bounded unsupported-schema fallback. No live provider was called. ADB had no connected device and no AVD was configured; Android instrumentation, real Native/Web chat-to-review behavior, packaged WebView behavior, and physical/OEM behavior are **NOT TESTED**.

Only the proposal-preview function is registered. `calendar.query.v1`, web search, document tools, arbitrary dispatch, and multi-step orchestration remain unimplemented. Provider/tool outer receipts are process-local. PHASE 1 remains open, and no skin default switch or Native removal was authorized.
