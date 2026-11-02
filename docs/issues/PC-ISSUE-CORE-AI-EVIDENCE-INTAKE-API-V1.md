# PC-ISSUE-CORE-AI-EVIDENCE-INTAKE-API-V1 — Reviewed AI evidence intake

- **Date:** 2026-11-02 (Asia/Shanghai)
- **Priority:** P1 — PHASE 1 Core Feature API
- **Status:** PASS for the verified JVM/build scope; live provider, Android instrumentation, and packaged WebView behavior NOT TESTED
- **Scope:** Reviewed file/OCR/ICS text analysis and explicitly consented image analysis through `AiFeatureApiV1`
- **Code commit:** `939fb7f` (`feat(分离): 接入 Core AI 证据入口 API`), pushed to `origin/feat/skin-first-separation`
- **Phase status:** PHASE 1 remains OPEN. This slice does not establish full Core extraction, Native/Skin parity, or cutover readiness.

## Expected behavior

Reviewed evidence interpretation must use a typed Core Feature API. Android URI grants, pickers, bounded reads, local OCR, preview, and user consent remain Host responsibilities. Evidence and model output remain untrusted, and evidence analysis must not create or publish formal events. Unsupported ICS semantics remain explanation-only.

## Implementation

- Added typed `AiReviewedTextEvidenceRequestV1` and `AiConsentedImageEvidenceRequestV1` commands to `AiFeatureApiV1`, with separate model-port DTOs and typed receipts.
- Core enforces a 24,000-character text bound, 2,000-character preflight-hint bound, 6 MB image bound, image MIME syntax, valid zone, request ID, filename, and current conversation revision. It supplies only the latest eight canonical conversation messages to the provider adapter and rejects a result if the conversation changes while the provider call is in flight.
- Completed requests use a bounded 128-entry process-local receipt cache. Exact request retries replay; reusing an ID with different evidence returns `DUPLICATE_REQUEST_ID`. Cache state is not durable across process recreation.
- `ICS_UNRESOLVED` is explanation-only: Core removes any model-produced `proposalSource` from the receipt. Other proposal-source strings remain untrusted and require the separate `generateProposal` and review paths. Evidence analysis performs no Store writes, draft staging, or publication.
- `AndroidAiModelPortV1` retains provider configuration and credentials. `PowerCalApp` now calls the shared API for reviewed file/OCR/ICS text and consented images; no screen code calls `AiClient` directly for those paths. Host URI access, byte reads, picker, local OCR, consent storage, and fallback behavior remain in their existing adapters.
- Provider prompts label filenames, extraction hints, and evidence as untrusted; truncated evidence is explicitly marked incomplete. Original file bytes do not enter the text-evidence command. Image bytes are sent only after the Host consent action and never enter conversation history.
- No schema, backup format, event semantics, or default skin selection changed. Native fallback remains available.

## Verification evidence

Environment: Windows PowerShell, repository-local Android SDK at `E:\android-sdk`, offline Gradle build. No live provider was called; the prompt-boundary client test uses a loopback stub. ADB reported no connected devices and no AVD was configured.

| Check | Result | Evidence |
| --- | --- | --- |
| `AiFeatureApiV1Test` | **19/19 PASS**, 0 failures/errors/skips | `.local/android-test/reports/core-ai-evidence-intake-20261102/focused-api-final.log`, SHA-256 `066323FDE0237ADD1FD47E7C3E3CDE68E339F0CE5B2598DEA8112A2F698CB3F8` |
| `AiChatClientTest` | **10/10 PASS**, 0 failures/errors/skips | `.local/android-test/reports/core-ai-evidence-intake-20261102/focused-ai-client-final.log`, SHA-256 `89D553A1F9192477DEBA213E4808EDDBDAD273154A502A17E871F2ED12F6B87A` |
| Forced serial offline full gate | **BUILD SUCCESSFUL**; **854/854 JVM tests across 135 XML suites**, 0 failures/errors/skips; both source boundaries, Debug APK, lint, and AndroidTest Kotlin compilation passed | `.local/android-test/reports/core-ai-evidence-intake-20261102/full-gate-final.log`, SHA-256 `1F61D7E1F7BE63B01AFEDBBC550524BC216146B1D723FF5923AA8B97AEB99679` |
| Lint | **0 errors / 53 warnings** | `app/build/reports/lint-results-debug.xml` |
| `git diff --check` | **PASS** | Final source/document diff check |
| Debug APK | **66,880,110 bytes**, SHA-256 `71C22668D1F285413DACB75BDA840DE1F37ED5805BE55551CA441CA0D1B9B6AC` | `app/build/outputs/apk/debug/app-debug.apk` |
| Android instrumentation, actual Native UI, packaged WebView, physical/OEM device, and live-provider behavior | **NOT TESTED** | No ADB device or configured AVD; AndroidTest sources compiled only |

The full-gate command was:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
```

The AI Feature API tests cover revision fencing, process-local replay, changed-payload duplicate rejection, model output bounds, no formal writes, ICS proposal-source suppression, and image MIME/size bounds. The client test verifies that source metadata and extracted evidence remain in user content and do not enter the trusted system prompt.

## Limits and next work

- Receipt caching is process-local and is not a durable provider-call receipt.
- Host-based extraction and review do not prove correct behavior for every file format, Android picker, local OCR engine, or consent lifecycle on devices.
- Live provider compatibility, Native/Clear parity, packaged Android WebView, and physical/OEM behavior remain **NOT TESTED**.
- General tool orchestration is not implemented. The next bounded action is to evaluate the smallest safe orchestration slice using existing commands, Host capabilities, validation, approval binding, receipts, and retry behavior.
- No acceptance row is promoted to PASS by this Issue, and no skin cutover is authorized.

## Source paths

- `core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/AiFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/StoreAiFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/AndroidAiModelPortV1.kt`
- `app/src/main/java/dev/powerfulcalendar/ai/AiClient.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/test/java/dev/powerfulcalendar/core/api/v1/AiFeatureApiV1Test.kt`
- `app/src/test/java/dev/powerfulcalendar/ai/AiChatClientTest.kt`
- `docs/CORE_FEATURE_API_V1.md`
- `docs/AI_AGENT_PROTOCOL_V2.md`
- `docs/UI_SKIN_EXTENSION_CONTRACT_V4.md`

## 2026-11-02 follow-up — OCR/text proposal-source handoff

The Host now forwards a nonblank `proposalSource` returned by reviewed file/OCR evidence analysis
to the existing `AiFeatureApiV1.generateProposal` command. Any result is shown as an explicit
reviewable proposal; this path does not create a draft or publish an event. Calendar context is
disabled for this follow-up so reviewed attachment text is not combined with unrelated event
context. The loopback integration regression also checks redaction and confirms Store/formal-event
counters remain unchanged until a separate review action.

`WebAiAttachmentFlowIntegrationTest` is **7/7 PASS** within the full JUnit gate. Full gate:
**934/934 tests / 139 XML suites**, zero failures/errors/skips; both source boundaries, APK,
lint, AndroidTest Kotlin compilation, and diff check PASS. Log SHA-256
`8B6475049A2AED96C6920381347874C94AD3EE728D9AAE767AEBF0BDFDFF7092`; evidence directory
`.local/android-test/reports/phase1-core-feature-api-20261102/`. The loopback stub was used; no live
provider, Android device, or packaged WebView was tested.
