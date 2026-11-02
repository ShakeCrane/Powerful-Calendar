# PC-ISSUE-SEPARATION-ICS-IMPORT-WORKFLOW-CORE-OWNERSHIP — Core owns ICS draft-import orchestration

日期：2026-11-02

状态：Core workflow regressions and final forced offline gate **PASS**. Android instrumentation, packaged WebView and runtime Native/Skin parity **NOT TESTED**. Phase 1 remains open.

## 结论

`PowerCalApp.importIcs()` previously owned deterministic orchestration after reading the selected file: planning, duplicate selection, checkpoint recovery, candidate-to-draft staging, partial-success handling and receipt writes. Those decisions are now one `ImportExportFeatureApiV1.importIcsToDrafts()` use case backed by the existing planner, duplicate query, DraftReview API and checkpoint store. No parallel import service or new API module was introduced.

The Android Host still bounds and reads the selected URI, handles SAF/picker permissions, chooses the zone, reloads and opens the review Deck, presents status, and obtains separate consent before sending unresolved evidence to AI. Parsing, imported candidates and every successful result remain review drafts. The workflow never publishes formal events.

## 规则与修复

| 之前的责任重复 | 当前归属 | 回归覆盖 |
| --- | --- | --- |
| Host combined candidate duplicate checks with the checkpoint and draft-creation loop. | The existing Import/Export Feature parses the content, rechecks current candidate duplicates, resolves checkpoint IDs to actionable drafts, stages candidates through DraftReview, and records the existing checkpoint after each successful stage and at completion. | `icsImportWorkflowStagesAndReusesReviewDraftsOnRetry`; `icsWorkflowStagesIndependentUnitsAndRetainsUnresolvedProblems`; Clear import and AI attachment retry integration. |
| Any persisted row found by a receipt was treated as a review draft, including discarded/final cards. | `existingDraftIds` returns only drafts whose current status is actionable. Retry re-runs candidate duplicate detection; a discarded checkpoint card is omitted and a new review draft can be staged. The receipt remains a recovery hint, not authority to skip a candidate or publish an event. | `icsImportWorkflowRecreatesDiscardedDraftReferencedByCheckpoint`. |
| Two identical calls through one adapter could interleave duplicate checks and staging. | The existing adapter serializes the workflow with a per-instance `Mutex`. This is an in-process guard and does not change the checkpoint format or introduce a cross-process lock. | `icsImportWorkflowSerializesConcurrentIdenticalImports`. |
| Missing durable checkpoint storage could silently turn a retryable import into an untracked import. | The workflow checks checkpoint storage before parsing or draft writes and fails closed. Normal `PowerCalApp` construction supplies the existing application context. | `icsImportWorkflowFailsBeforeDraftWritesWithoutCheckpointStorage`. |
| The previous suspend loop used `runCatching`, which could treat coroutine cancellation as a candidate failure and continue writing an empty checkpoint. | Cancellation now propagates; ordinary candidate exceptions still contribute to `failedCount` and do not prevent independent candidates from processing. | `icsImportWorkflowPropagatesCancellationWithoutRecordingEmptyCheckpoint`. |

The recovery receipt continues using the existing SharedPreferences key, serialization, synchronous `commit` and 12-recent-run history cap. This slice changes no database schema, backup format, export format, formal-event transaction or import content rule. Unresolved recurrence groups remain visible as problems while independent valid units can stage successfully.

## 验证证据

- Final focused `ImportExportFeatureApiV1Test`: **15/15 PASS, 1 XML suite**. Command: `:app:testDebugUnitTest --tests dev.powerfulcalendar.core.api.v1.ImportExportFeatureApiV1Test --offline --rerun-tasks --max-workers=1 --console=plain`. Log SHA-256: `4ed3a299fb90651d76b2bcf9da8a476483e7c09eb7ccea3cd2df9462e1e4cf93`.
- The broader workflow/API/attachment/checkpoint focused group passed **22/22 across 3 XML suites** before the cancellation-specific test was added. Log SHA-256: `75afd9bfbc56defbb129224979f792f311aee587d6906c923ef499015ce83486`.
- The first focused attempt exposed a test-fixture error: the test tried to discard a draft before opening it in the DraftReview session. The fixture now opens/selects that draft before discard; the discard/re-import regression passes. Initial failed log SHA-256: `58ae304f8384d3d8c7538f9b431ac65ee1c10cf1965bc47212aaad1f440941e3`.
- The first cancellation-test compile attempt omitted the required `Clock` argument when constructing `StoreEventFeatureApiV1`. The test now supplies `Clock.systemUTC()`; the final focused run and full build both compile and pass. Compile-failure log SHA-256: `ee8db25fccfa6772c35dc6145a8c33fd79563818c97e6ce77f1744a42007ae2a`.
- Final forced offline gate passed **943/943 JVM tests / 139 XML suites / 0 failures, errors or skips / 154 tasks**. Both `verifySkinSourceBoundary` and `verifyNativeSkinBoundary`, Debug APK assembly, lint (**0 errors / 54 warnings**), AndroidTest Kotlin compilation and `git diff --check` passed. Full command and log are recorded in `.local/android-test/reports/phase1-ics-import-workflow-core-20261102/manifest.json`; full-log SHA-256: `bb7892ffdbb94f8e585887166a2d2c24e43ed682018e1d880df5caf4e16824e7`.
- JUnit XML manifest SHA-256: `365607106e7bc87b515fd667b7c5a6d0e0a784d442cdbb9ddb91c21de7b479b2`.
- Debug APK: **67,060,418 bytes**, SHA-256 `f82766b01e0371cce64643973c3382cd9fed5fb87a3947e30f1bde8a044605bb`.
- No ADB device or AVD was available. Android instrumentation, packaged WebView, and device Native/Skin behavior remain **NOT TESTED**.

## 修改与后续

Code commit: `14bd3d554d29c5231a05ce0c9bb3756eb16fdcf3` (`分离：由 Core 编排 ICS 草稿导入`), parent `fca2a4874d11104d75b5e9ef60084d51b865a4bd`. The code and Issue/evidence commits for this slice and the preceding timeline-move slice were fast-forward pushed to `origin/feat/skin-first-separation` and verified at `5c1f1d32546f28989b4808f0829dbfffe03e0e8b`.

The next bounded review is the `AiBatchModeV1` / `AiBatchRunner.Mode` Host boundary. Keep Runner types inside the execution adapter, align Native and Web Host command/state values to the existing stable mode DTO, and retain the Host's explicit confirmation/approval flow. This issue does not claim Phase 1 completion.
