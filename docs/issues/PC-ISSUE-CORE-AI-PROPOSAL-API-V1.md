# PC-ISSUE-CORE-AI-PROPOSAL-API-V1 — Reviewed AI Proposal Feature API v1

- **Date:** 2026-11-01 (Asia/Shanghai)
- **Priority:** P1 — PHASE 1 Core Feature API
- **Status:** IMPLEMENTED / VERIFIED for JVM and build gates; Android instrumentation NOT TESTED
- **Scope:** Native and Web Host review/apply paths for structured AI proposals
- **Code commit:** `92200a86bb5aadcd767cc7e3a5b858dccffdca5f`, pushed to `origin/feat/skin-first-separation`
- **Phase status:** PHASE 1 remains OPEN; this is one vertical slice, not full AI/tool orchestration or Native/Skin parity signoff.

## Problem and expected behavior

Native and Web reviewed AI proposal actions needed a shared, versioned Core Feature API boundary. A model-produced proposal must remain untrusted input: staging cannot publish a formal event, and publication must require explicit Host-owned approval bound to the reviewed request. Per-item outcomes and retries must remain safe across adapter recreation, while target revision and draft-version conflicts must be rejected inside the Store transaction.

## Implementation

- Added platform-neutral `AiProposalFeatureApiV1` DTOs and contract, exposed through `CoreFeatureApiV1` and the Store-backed `PowerCalCoreFeatureApiV1` adapter.
- Native and Web Host review paths map `AiInterpretation` into detached DTOs and share the Host-created API for staging and selected-item publication.
- `PUBLISH` requires a one-use, expiring Host-issued approval bound to the exact request fingerprint. Results report typed status and receipt information per selected item.
- Target revisions, pending task-backed draft form/operation/version, receipt fingerprints, and expired-task terminal state are checked in the existing Store transaction. Store listeners are invoked only after the Store monitor and API mutation lock have been released.
- Existing `ai_task_receipts` provide durable per-item receipts. Taskless `applyReviewedProposals` uses a deterministic internal task namespace derived from the request ID, so exact CREATE/REPLACE/DELETE retries after API recreation reuse the durable item receipt.
- No database schema or backup format changed. The outer API request cache and standalone no-task `createDrafts` replay remain process-local. AI batch selection/execution, provider orchestration, and other AI Host paths remain separate.

## Review findings resolved

Independent review identified and regressions now cover:

1. An expired pending receipt could be reopened between a precheck and commit. Terminal-only receipt enforcement now runs inside the Store transaction; a second Store connection regression covers the boundary.
2. A taskless CREATE publish retry after API recreation could create a second formal event. The stable taskless receipt namespace now provides durable item replay.
3. Taskless REPLACE/DELETE target validation did not consult the durable item receipt. Validation now uses the same request-derived receipt identity; replay coverage includes both operations.
4. A `createDrafts` review containing both stale and current operations for the same event could stage the current operation. Duplicate-target detection now runs over the whole eligible review in Core before target revision checks; all operations for that event are blocked, while independent CREATE items can still stage.

Final reviewer follow-up found no actionable findings. Reviewers did not run the verification gates reported below.

## Verification evidence

All logs are under `.local/android-test/reports/core-ai-proposal-api-20261101/` in the isolated worktree. Hashes are SHA-256.

| Check | Result | Evidence |
| --- | --- | --- |
| Focused `AiProposalFeatureApiV1Test` and `PowerStoreAiTaskReceiptTest` suites | **24/24 PASS** | `focused-final.log` — `03F4D66A822D843A5492DC7905D63F210B436625A9DA8BD3A2898E42EBFF2496` |
| Web Host confirmation timing regression, isolated after correction | **1/1 PASS** | `isolated-web-confirmation-fixed.log` — `913F5D5BAB6AE3DD7B7DA2669F13A206DCC385E11136956C28D25D9AB2F2A826` |
| Forced serial offline full gate: both source boundaries, full JVM, Debug APK, lint, AndroidTest Kotlin compilation | **BUILD SUCCESSFUL**; JVM **824/824**, 129 XML reports, 0 failures/errors/skips; lint 0 errors / 54 warnings | `final-gates-serial.log` — `3AABFBC93893373569DFAE440941E013B4A7FA5391F50E160140D15942F96A61` |
| Debug APK | **66,749,038 bytes** | `app/build/outputs/apk/debug/app-debug.apk` — SHA-256 `70D6D0E70D955F82416DE352CC9E1DAA5B9E9404C7C794DF5AEF3685AA79BF9F` |

The final full-gate command was:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain
```

### Earlier full-run failure and correction

The first forced parallel full run completed 824 JVM tests but had one failure in `WebAiProposalReviewIntegrationTest.replaceAndDeleteCandidatesCommitOnlyAfterTrustedHostConfirmation`: the test asserted the confirmation node immediately, before asynchronous Host UI state exposed it. The isolated case passed. The test now waits up to five seconds for the confirmation node; the isolated rerun and final forced serial full gate passed. The original failed run is retained as `final-gates.log`, SHA-256 `41EC2D644F186B462025E40073D5ACB49BB09F9CC1677DECE924FC11EC820325`. This documents a test synchronization defect and its correction; it is not reported as a passing run.

## Limits and remaining work

- Android instrumentation, packaged WebView behavior for this API slice, and physical/OEM behavior are **NOT TESTED**. No emulator or device was used in this slice.
- The outer API request cache and standalone no-task `createDrafts` replay do not survive process death. Durable guarantees described here apply to per-item Store receipts for `applyReviewedProposals`.
- AI batch selection/execution, AI draft generation, provider/tool orchestration, imports, reminders, backup/restore, other Host/Core paths, and runtime Skin projections containing Core types remain outside this slice.
- Native fallback remains available. No default switch, Native removal, or cutover was authorized. No individual acceptance row is marked PASS by this report.

## Source paths

- `core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/AiProposalFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/StoreAiProposalFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/AiProposalFeatureMapping.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebAiProposalReview.kt`
- `app/src/main/java/dev/powerfulcalendar/core/PowerStore.kt`
- `app/src/test/java/dev/powerfulcalendar/core/api/v1/AiProposalFeatureApiV1Test.kt`
- `app/src/test/java/dev/powerfulcalendar/core/PowerStoreAiTaskReceiptTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebAiProposalReviewIntegrationTest.kt`

## Follow-up — mixed stale/current duplicate targets (2026-11-02)

`StoreAiProposalFeatureApiV1.createDrafts()` previously premarked stale operations in the adapter. `AiDraftService` then built its duplicate-target set only from unblocked items, so a current operation sharing the same event identity could proceed. The adapter now passes the detached review unchanged; Core computes duplicate targets before revision screening and classifies every same-target operation as `BLOCKED`. A lone stale operation remains blocked, and independent CREATE proposals retain the existing partial-success behavior. Store's expected-revision checks still fence the eventual draft transaction.

The new regression mixes a stale REPLACE, a current DELETE for the same event, and an independent CREATE. It asserts both target operations are blocked, only the independent draft is staged, and the formal event title/revision remain unchanged. `AiProposalFeatureApiV1Test` is **17/17 PASS** (JUnit XML SHA-256 `310B04DF35876A32DA33540B29EA331ABAE447BCE4685C7D3E38297BCED76D05`). The forced single-worker full JVM is **906/906 PASS across 139 XML suites**, with both source boundaries, APK, lint (0 errors / 54 warnings), AndroidTest Kotlin compilation, and 154 actionable tasks passing. APK SHA-256 is `A184290D72852F71F9A7C487C48ECE0AFE2847DACA2057E35D1A3B9732374D47`; full gate log SHA-256 is `8EB354C25C4AA9E2DFD81776A29A02EF530D26919AB3D9E09A503385DAED9EE4` in `.local/android-test/reports/phase1-reminder-completion-20261102/`.

Follow-up code commit `71d21afa819acb3ef0f20efdca12b29d772faf41` was validated with the same full gate. Android instrumentation and on-device Native/Web parity remain **NOT TESTED**. PHASE 1 remains open.
