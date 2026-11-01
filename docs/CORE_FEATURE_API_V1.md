# Core Feature API v1 — Event, Draft/Review, AI Proposal, and Reminder slices

- **Status:** Event, Draft/Review, AI Proposal, and Reminder vertical slices implemented; PHASE 1 remains in progress.
- **Contract source:** `:core-api`, package `dev.powerfulcalendar.core.api.v1`, `CoreFeatureApiV1.kt`, `DraftReviewFeatureApiV1.kt`, `AiProposalFeatureApiV1.kt`, and `ReminderFeatureApiV1.kt`.
- **Runtime adapter:** `app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt`.
- **Updated:** 2026-11-01.

This document describes the implemented Event, Draft/Review, AI Proposal, and Reminder API boundaries. It does not declare full Core extraction, full domain coverage, runtime cutover, or Native/Skin parity. The Kotlin source is authoritative when a detail here drifts.

## 1. Contract boundaries

The feature contracts have separate jobs:

| Contract | Owner | Responsibility |
| --- | --- | --- |
| Core Feature API | `:core-api` | Versioned domain operations, queries, DTOs, results, revisions, and change notifications |
| Native Presentation Contract | `:skin-api` | Native renderer inputs and callbacks; contains presentation models, not Core business services |
| Web Bridge Transport Contract | `WebEditWire` / `WebEditCommand` | Versioned JSON command decoding and Host reply transport; it is not a domain business API |

```text
Native presentation ─┐
                     ├─ Host adapters ─ CoreFeatureApiV1 ─ PowerCalCoreFeatureApiV1 ─ existing Core services/PowerStore
Web transport ───────┘       │
                             └─ owns confirmation UI, Android access, and transport/session projection
```

`:core-api` is a pure Kotlin JVM module. Its public contract contains no Android, Compose, `PowerStore`, repository, or internal Core entity types. `PowerCalApp` creates one `PowerCalCoreFeatureApiV1` per `PowerStore` and shares its domain services across Native callbacks and Web Host adapters. The runtime adapter currently lives in the app module and delegates to existing Core services such as `NewEventEditSession`, `EditSession`, recurrence logic, `PowerStore`, and the platform reminder scheduler; moving that implementation into a separately distributable Core runtime is not complete.

The Host is the only owner of Android context, database construction, trusted confirmation UI, and concrete deletion approval issuance. A Skin receives DTOs and opaque editor handles, never a Store reference or an approval issuer.

## 2. Version and DTO rules

- Both `CoreFeatureApiV1` and `EventFeatureApiV1` report `apiVersion == 1`.
- `EventKindV1` is `ALARM`, `ACTIVITY`, `DEADLINE`, or `MEMO`.
- `EventFormV1` is the editable value. `EventRecordV1` is an immutable formal-event query result with identity and `revision`.
- Recurrence weekday values in `EventRecurrenceV1.byDay` use ISO numbers: Monday `1` through Sunday `7`. Recurrence scope and occurrence keys preserve the existing Core recurrence semantics.
- `EventSelectionV1` accepts either an instant in epoch milliseconds or a local ISO date, never both. A zone is passed separately as an IANA `zoneId`.
- `EventEditorHandleV1` is an opaque, process-local reference. Its random ID is not authorization and is invalid after the adapter instance/process is gone.
- `EventEditorSnapshotV1.capabilities` describes operations available to the editor UI. Capability presence alone is not an authorization check; each operation still validates its handle, revision, lock state, and any required Host approval.
- Core-owned values are preserved on form updates. The adapter retains source/zone and recurrence details that the editor may not rewrite; Core validates and persists the resulting form.

## 3. Event operations

| Operation | API behavior |
| --- | --- |
| `queryEvents()` | Returns all current formal events and the adapter's `featureRevision`. Draft cards are not included. No paging/filter parameters exist in v1. |
| `searchEvents(query, snapshot)` | Searches the supplied immutable `EventQuerySnapshotV1`; it performs no Store I/O and returns the snapshot's feature revision with display-only hits (`eventSeq`, `revision`, `kind`, `title`). Blank queries return no hits. Title matching is case-insensitive; raw or formatted sequence matching is retained; snapshot order is retained; the API caps results at eight. Deck surfaces display at most four. |
| `setOccurrenceCompletion(eventSeq, expectedRevision, occurrenceKey, expectedCompleted, completed, requestId)` | Explicitly changes one tracked, enabled occurrence. It checks the lock, current event revision, original recurrence slot, and observed completion state before delegating to the Store transaction. Actual changes return `UPDATED` and publish one `FORMAL_UPDATED`; a valid already-satisfied request returns `NO_CHANGE`; stale state returns `CONFLICT`; unavailable or invalid occurrences return `INVALID`; locked writes return `LOCKED`. Request receipts use the bounded mutation replay cache. |
| `openNewEvent(kind, zoneId, selection)` | Validates the zone/selection, creates an in-memory editor with defaults from the injected `Clock`, and returns an opaque handle and snapshot. Opening alone does not write an event. |
| `openFormalEvent(eventSeq, expectedRevision, occurrenceKey)` | Rejects invalid IDs, a locked Store, missing/stale events, or an invalid occurrence key; otherwise opens the existing Core edit session. |
| `editorSnapshot(handle)` | Returns current form, baseline, version, capabilities, and recovery state for a live handle, or `null` if it is not owned by this API instance. |
| `validateNewEvent(handle)` | Returns `READY`, `INCOMPLETE` with missing fields, `INVALID`, or `NOT_FOUND`. This is advisory preflight; save still performs Core validation and commit checks. |
| `updateNewEvent` / `updateFormalEvent` | Updates the in-memory editor form. It does not publish a formal event. Core-owned fields are restored before applying the update. |
| `selectFormalRecurrenceScope` | Applies the existing single-occurrence, this-and-future, or whole-series edit scope to the Core edit session. |
| `saveNewEvent(handle, form, requestId)` | Runs Core validation and commit. A valid form may return `CREATED`; incomplete content can return `STAGED` as a recoverable draft. The handle is released after successful formal creation. |
| `saveFormalEvent(handle, form, requestId)` | Uses the Core edit session and expected revision. A this-and-future edit may update the original and create the future segment; `relatedEventSeq` identifies the new segment. Conflicts request refresh. |
| `deleteFormalEvent(handle, approval, requestId)` | Requires a Host-issued, event/revision-bound, single-use approval. A forged, expired, or mismatched approval returns `NOT_AUTHORIZED` or `CONFLICT`. |
| `stageFormalReplacement(eventSeq, expectedRevision, form, requestId)` | Validates the current formal revision and stages the replacement through Core draft storage. It does not alter the formal event. The Host/Web interaction still decides how the candidate is reviewed and committed. |
| `checkpointNewEvent` / `checkpointFormalEvent` | Persist recovery checkpoints through the existing Core editor sessions and return a checkpoint receipt. |
| `closeNewEvent` / `discardNewEvent` | Close-and-checkpoint or discard a new-event session. Closing a changed session may return a staged draft; discard removes its owned draft when allowed. |
| `closeFormalEvent` / `heartbeatFormalEvent` / `isFormalEventCurrent` | Release/renew/check the lease held by a formal edit session. |
| `discardFormalChanges` | Restores the current formal edit session's baseline without changing the formal record. |
| `subscribe(listener)` | Receives changes published by mutations routed through this adapter instance. The returned `AutoCloseable` removes the listener. |

## 4. Receipts, errors, revisions, and idempotency

`EventEditorOpenResultV1` distinguishes `Opened`, `Stale`, `Missing`, `Locked`, and `Invalid`. Editor updates distinguish `UPDATED`, `NOT_FOUND`, `LOCKED`, and `INVALID`. Validation distinguishes `READY`, `INCOMPLETE`, `INVALID`, and `NOT_FOUND`.

Mutation receipts use `EventMutationStatusV1`: `CREATED`, `STAGED`, `UPDATED`, `DELETED`, `NO_CHANGE`, `CONFLICT`, `INVALID`, `LOCKED`, `NEEDS_RESTORE`, `NOT_AUTHORIZED`, `FAILED`, or `DUPLICATE_REQUEST_ID`. Receipts can include event/revision/draft identity, a related event, `errorCode`, a user-facing message, and `refreshRequired`.

For mutation and checkpoint requests:

- A request ID must be nonblank, at most 128 characters, and contain no ISO control characters.
- Each adapter instance retains up to 128 request receipts in memory. Replaying the same ID and operation fingerprint returns the cached receipt (mutation replay sets `duplicate = true`). Reusing an ID with a different fingerprint returns `DUPLICATE_REQUEST_ID` or an invalid checkpoint receipt.
- This cache is bounded and process-local. It is not a durable exactly-once guarantee across process death, adapter recreation, or eviction. Durable workflow recovery continues to rely on Core drafts/checkpoints and existing transaction rules.

`featureRevision` begins at zero for each adapter instance and increments when this adapter publishes a feature change. It is not the database revision and is not a cross-process sequence. Formal `revision` remains the optimistic concurrency token enforced by Core. Query results pair the current formal records with the adapter revision observed for that query.

## 5. Change notifications

`EventFeatureChangeV1` contains a feature revision, one of `FORMAL_CREATED`, `FORMAL_UPDATED`, `FORMAL_DELETED`, or `DRAFTS_CHANGED`, and optional event identity/revision. `PowerCalApp` subscribes and reloads its event projection when it observes a newer revision.

**Current limit:** only writes that publish through this API instance advance its feature revision or notify its listeners. Existing `DeckController` / direct `PowerStore` operations, other Host code paths, external Store instances, and process recreation are not observed automatically. Consumers must not treat the current change stream as a complete database change feed.

## 6. Authorization and integrity

- Event identities and `expectedRevision` are checked before opening/saving; Core transactions remain the final authority against concurrent writes.
- Deletion approval is issued by internal Host code only after its trusted confirmation flow. The concrete token is tracked by object identity, bound to editor handle and current event revision, and removed when consumed or the editor closes. Constructing a value that merely implements the marker interface grants no authority.
- `EventFormV1` input does not authorize a write by itself. Core editor update/validation, transactional save, and the explicit Host approval for formal deletion remain required.
- A `LOCKED`, `CONFLICT`, `INVALID`, or `FAILED` result does not imply that a formal write occurred. The caller must retain or reload editor state according to the receipt.

## 7. Draft/Review API v1 slice

`CoreFeatureApiV1.draftReview` exposes the platform-neutral `DraftReviewFeatureApiV1` contract. In `PowerCalApp`, Native Deck and Web Deck operations use the same Host-created API service and `DeckController`. Its DTOs project persisted cards, the selected form, conflict/restore prompts, completion summary, and command receipts without exposing `DeckController` or `PowerStore` types through `:core-api`.

- `draftVersion` is the persisted card version. `stateRevision` is an in-memory monotonic revision paired atomically with the controller snapshot; edits require both values, and the controller checks the revision again when applying the edit. Approved commit, conflict-confirmation, and restore commands recheck the exact state revision when the serialized command starts.
- `beginFormalReplacement` and `beginRestore` require the reviewed formal/tombstone revision. They create proposals and do not alter the formal event. If a reviewed commit must checkpoint a dirty form, that stage uses the reviewed persisted draft version as its optimistic precondition and stops on mismatch without rebasing stale form data over the newer card. A successful stage is followed by `PowerStore.commit` with the resulting version, checked again inside the write transaction. Stale formal conflicts return a separate `REVIEW_REQUIRED` result and require `confirmConflictReplacement` with the reviewed current revision and draft version.
- Restore remains a separate approval. A dirty reviewed form is checkpointed against its reviewed draft version without retrying over a concurrent update; the resulting draft version, reviewed tombstone revision, and exact form are then checked inside the `PowerStore.restoreDraft` transaction. A stale tombstone, draft, or form returns a conflict and leaves the identity retired and proposal pending.
- `edit`, `flush`, `select`, `move`, `jump`, `commit`, conflict approval, restore approval, `discard`, and `undoDiscard` return `DraftReviewReceiptV1`. Draft queue changes publish `DRAFTS_CHANGED`; successful create/replace/delete/restore commits publish the corresponding formal change through the shared Event API instance.
- Native Deck's built-in controls remain Host-driven. Runtime/imported Deck callbacks that request a formal commit, conflict replacement, or restore first open Host-owned confirmation UI. Commit/restore review displays the exact candidate's type, title, date/time, zone, location, notes, recurrence, reminder, enable/tracking state, and completed instances; conflict review adds the current revision and field differences. The UI checks its captured Deck state revision before calling the API.
- Draft/Review request receipts are retained in a bounded, process-local 128-entry cache. Durable request replay across adapter recreation/process death is not provided; do not describe the receipt cache as exactly-once persistence.
- Explicit user-owned text capture creates a draft through `DraftReviewFeatureApiV1.createDraft`. It accepts an `EventFormV1`, rejects recurrence-edit scope fields, uses the Store's existing user draft transaction and AI lock, and returns the new draft identity/version. The adapter snapshots collection fields before queueing and fingerprints them canonically for request replay. The Host opens the returned draft ID. AI proposal writes use the separate `AiProposalFeatureApiV1`; AI batch execution and import checkpoint contracts remain separate.

These are Event, Draft/Review, AI Proposal, and Reminder vertical slices, not full Phase 1 closure. Native and Web Deck formal searches now use `searchEvents`, both Memo completion Host paths use `setOccurrenceCompletion`, Native/Web reviewed proposal actions use the shared `aiProposals` service, and Host reminder diagnostics/recovery use `reminders`. The Memo visibility precheck and other Host flows still perform direct Store reads; `DeckVisualContext` and `MemoVisualContext` still project Core types to runtime rendering; and authorized workflows outside the shared adapters do not publish to their change stream. Other domain APIs remain outside this coverage.

## 8. AI Proposal API v1 slice

`CoreFeatureApiV1.aiProposals` exposes the platform-neutral `AiProposalFeatureApiV1` contract. Native and Web review paths map the Host-owned `AiInterpretation` into detached API DTOs and call the same Store adapter. The API returns typed per-item outcomes and does not expose `PowerStore`, `AiDraftService`, or Host approval issuance through `:core-api`.

- `createDrafts` stages explicit user-reviewed proposals and never publishes a formal event. Existing target revisions are checked before staging, duplicate target operations in one review are blocked, and an existing human draft is preserved. Its no-task outer request replay cache remains process-local.
- `applyReviewedProposals` acts only on selected review positions. `PUBLISH` requires a one-use Host-issued approval bound to the exact request fingerprint. Existing-target operations carry the reviewed revision; the Store checks it again inside the SQLite transaction. Pending task-backed drafts must still match the reviewed form and operation, and commits use the reviewed draft version and target revision.
- Per-item replay uses the existing `ai_task_receipts` table. Conversation tasks use their task/revision identity. Taskless API applies use an internal, deterministic task namespace derived from the request ID, so exact retries after API recreation reuse the original staged or committed item. The outer API request cache is still process-local. No database schema or backup format changed.
- Expired task requests may replay exact existing receipts. Staging can reuse an exact existing item receipt; publishing additionally requires a terminal receipt, enforced again inside the Store transaction. An expired request cannot create a missing receipt. Target revision and receipt fingerprint checks remain inside the transaction where required.
- Store change snapshots are captured while the Store monitor is held; feature changes are prepared without callbacks, then listeners run only after both the Store monitor and proposal mutation lock are released.
- AI batch selection, locking, cancellation, and recovery remain on their existing path. This API slice does not claim that batch operations or every AI/provider tool call have moved behind the feature API.

## 9. Reminder API v1 slice

`CoreFeatureApiV1.reminders` exposes the platform-neutral `ReminderFeatureApiV1` contract. Native and Web Host paths share the same adapter, which reads durable schedule state from the existing `PowerStore` and delegates OS scheduling to the existing `ReminderScheduler`.

- `queryPermissions()` reports exact-alarm and notification permission requirements and current grants. When an API instance has no Android Host context, the permission fields are `null`.
- `queryEvent(eventSeq)` returns the current formal-event revision, its durable schedule intent (trigger ID, schedule revision, occurrence key, instant, exact/inexact requirement, state, and optional platform hint), and the latest execution marker.
- `reconcile(PENDING)` uses the existing bounded lifecycle reconciliation. `reconcile(EXHAUSTIVE)` uses the existing keyset-paged manual recovery path, including pending and cancelled work. The returned value reports attempted/failed counts, capped pending/blocked counts (`401` means 401 or more), status, and the first blocked hint. It is an operation receipt, not durable receipt storage and not proof of OS delivery.
- A contextless adapter returns `UNAVAILABLE` from reconciliation and leaves durable intents unchanged. Repeated reconciliation continues to rely on the existing scheduler's idempotent desired-state handling.
- The API adds no reminder policy, timing calculation, database schema, or backup format. Android settings launch remains a Host capability. OS receivers and boot recovery continue to use the same central scheduler.

The implementation is in `ReminderFeatureApiV1.kt` and `AndroidReminderFeatureApiV1.kt`; verification and limitations are recorded in the [Reminder API Issue](issues/PC-ISSUE-CORE-REMINDER-API-V1.md).

## 10. Current adapter coverage and open work

Verified code paths across the Event, Draft/Review, AI Proposal, and Reminder slices:

- Native event creation and ordinary formal editing/deletion call the Host's shared `EventFeatureApiV1`.
- `PowerCalApp.reload` obtains formal events through `queryEvents()`.
- Native and Web Deck formal search use `searchEvents` over an API query snapshot. The Native Host reuses its latest snapshot so typing does not synchronously read the Store; the Web Host queries and filters on its I/O dispatcher.
- Native and Web Memo completion commands use `setOccurrenceCompletion`, with expected event revision and expected prior completion state. Only committed changes publish `FORMAL_UPDATED`.
- Explicit text-to-draft capture uses `DraftReviewFeatureApiV1.createDraft`; successful persistence publishes one `DRAFTS_CHANGED` event and returns the draft ID, event sequence, and version.
- Web event editor create/update/checkpoint/save/delete and recurrence scope use the same API instance.
- Web timeline movement reads a Host projection, then stages the revision-guarded replacement through `stageFormalReplacement`.
- Native and Web Draft Decks use the shared `DraftReviewFeatureApiV1` for persisted draft edits, queue navigation, formal replacement proposals, conflict and restore review, commit, discard, and undo.
- Native and Web AI proposal review actions use `AiProposalFeatureApiV1` for staging and reviewed publication, including Host-owned publish approval and per-item Store receipts.
- Native and Web Host reminder diagnostics and reconciliation use the shared `ReminderFeatureApiV1`; bounded lifecycle recovery and exhaustive user-requested recovery remain distinct modes.
- A Robolectric adapter integration test performs a Native-side API create followed by a Web bridge create/checkpoint and observes shared feature revision/change events and Store-backed state. Its Native-side write calls the API directly; it does not mount `PowerCalApp` and drive the entire Native UI route. Separate editor Compose tests cover the API-backed editor component callbacks.

Remaining work before PHASE 1 can exit:

1. Route remaining authorized Host/Core reads and writes through versioned queries/commands and one change source; current direct or separate paths include AI batch operations, ICS and structured Calendar import, backup restore, AI draft generation, and Memo visibility checks.
2. Move remaining business decisions out of Web transport/Host adapters; keep the bridge focused on transport and trusted Host capabilities.
3. Remove Core entity types from runtime Skin projections while maintaining the separate Native Presentation Contract.
4. Add the required per-domain API contracts before claiming other feature areas are covered. Current v1 does not define Calendar paging, AI batch control, import/export, or Settings/Data APIs. Draft/Review request replay and AI outer request replay remain process-local; AI Proposal item receipts are Store-backed. Calendar and runtime Skin projections still need bounded, API-owned data paths.
5. Run required device checks when the independently owned AVD is available. Instrumentation for Event search/completion and AI Proposal API behavior is **NOT TESTED**.

Latest Draft/Review verification: focused API, DeckController, Store restore, Host review UI, and Web bridge tests passed **190/190**; the forced full JVM run passed **800 tests across 128 XML reports, 0 failures/errors/skips**. `verifySkinSourceBoundary`, `verifyNativeSkinBoundary`, Debug APK assembly, and `lintDebug` all passed; lint reported **0 errors / 54 warnings**. The Debug APK SHA-256 is `C22E82E434CBE5B02CD790FF72932074DA969A10BDF9095416179D3B8331AFB4` (66,683,502 bytes). Instrumentation and AVD behavior are **NOT TESTED**. Logs and the prior Event pilot’s transient full-suite failures are recorded in the current [progress entry](agent-handoff/PROGRESS.md) and the [Draft/Review Issue](issues/PC-ISSUE-CORE-DRAFT-REVIEW-API-V1.md); no cause is asserted for those earlier failures.

Latest Event search/completion verification: focused API, adapter, Deck, and Web tests **64/64 PASS**; forced full JVM **803 tests across 128 XML reports, 0 failures/errors/skips**; AndroidTest Kotlin compilation **PASS**; source-boundary checks, Debug APK assembly, `lintDebug`, and `git diff --check` **PASS** with 0 lint errors / 54 warnings. Android instrumentation and device behavior are **NOT TESTED**. The Debug APK is 67,552,075 bytes, SHA-256 `03108E62D7BE9473C65A887D87BB906EDEAAD7AFFA46B18D0415BCD864161C06`. Details and per-log hashes are recorded in the [Event search report](issues/PC-ISSUE-CORE-EVENT-SEARCH-API-V1.md), [occurrence completion report](issues/PC-ISSUE-CORE-OCCURRENCE-COMPLETION-API-V1.md), and [AndroidTest harness migration report](issues/PC-ISSUE-SEPARATION-ANDROIDTEST-FEATURE-API-MIGRATION.md). Code commit `c9948d5465bc93da7e59984342c756549df9e295` was fast-forward pushed to `origin/feat/skin-first-separation`; Phase 1 remains open.

Latest Draft creation verification: focused DraftReview API tests **13/13 PASS**; forced full JVM **806 tests across 128 XML reports, 0 failures/errors/skips**; AndroidTest Kotlin compilation, source-boundary checks, Debug APK assembly, `lintDebug`, and `git diff --check` **PASS**. Lint reported **0 errors / 54 warnings**. Android instrumentation is **NOT TESTED**. The Debug APK is 67,552,443 bytes, SHA-256 `C7C52F7EB0079968D4A0E8548BD742CD931181CFFA94D23BD41871021D8A84F3`. Per-log hashes and residual scope are recorded in the [Draft Creation Issue](issues/PC-ISSUE-CORE-DRAFT-CREATION-API-V1.md). Code commit `cb5823c573c1cbc2c59c50f751e099cee4e30d5e` was ordinary fast-forward pushed to `origin/feat/skin-first-separation`; Phase 1 remains open.

Latest AI Proposal verification: focused API and Store receipt suites **24/24 PASS**; forced serial full JVM **824 tests across 129 XML reports, 0 failures/errors/skips**. Skin and Native source-boundary checks, Debug APK assembly, `lintDebug` (**0 errors / 54 warnings**), and AndroidTest Kotlin compilation all **PASS**. The Debug APK is **66,749,038 bytes**, SHA-256 `70D6D0E70D955F82416DE352CC9E1DAA5B9E9404C7C794DF5AEF3685AA79BF9F`. One earlier full run exposed an immediate UI assertion before the asynchronous Host confirmation appeared; the isolated test passed, the test now waits for the confirmation node, and the final serial suite passed. Android instrumentation and device behavior are **NOT TESTED**. Final evidence is in `.local/android-test/reports/core-ai-proposal-api-20261101/` and the [AI Proposal Issue](issues/PC-ISSUE-CORE-AI-PROPOSAL-API-V1.md). Code commit `92200a86bb5aadcd767cc7e3a5b858dccffdca5f` was fast-forward pushed to `origin/feat/skin-first-separation`; Phase 1 remains open.

Latest Reminder API verification: focused API, Web recovery, and diagnostics tests **10/10 PASS**; nine related API/receiver/Host regression classes account for **49/49 PASS** in the final suite. Forced serial full JVM **828 tests across 130 XML reports, 0 failures/errors/skips**. Both source-boundary checks, Debug APK assembly, `lintDebug` (**0 errors / 54 warnings**), AndroidTest Kotlin compilation, and `git diff --check` **PASS**. The Debug APK is **66,749,038 bytes**, SHA-256 `291A5E275D625ABEBB602DCABE80E19A91258E16FA572F4B5D2641B0E8A2A999`. Android instrumentation, actual OS alarm delivery, and device/OEM behavior are **NOT TESTED**. Logs are under `.local/android-test/reports/reminder-feature-api-20261101/`; see the [Reminder API Issue](issues/PC-ISSUE-CORE-REMINDER-API-V1.md). Code commit `03af508` is committed locally and will be fast-forward pushed with its documentation; PHASE 1 remains open.
