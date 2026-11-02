# Core Feature API v1 — Calendar, Event, Recurrence, Memo, Draft/Review, AI, Reminder, Import/Export, Settings/Data, and Core Diagnostics slices

- **Status:** Calendar window and bounded event queries, Event, Recurrence next-occurrence, occurrence-by-key, and bounded occurrence-window queries, Memo, Draft/Review, AI Proposal/Feature/Query, Reminder, Import/Export, Settings/Data, and Core Diagnostics vertical slices implemented; PHASE 1 remains in progress.
- **Contract source:** `:core-api`, package `dev.powerfulcalendar.core.api.v1`, including `CoreFeatureApiV1.kt`, `CoreDiagnosticsFeatureApiV1.kt`, `CalendarFeatureApiV1.kt`, `EventFeatureApiV1.kt`, `RecurrenceFeatureApiV1.kt`, `MemoFeatureApiV1.kt`, `DraftReviewFeatureApiV1.kt`, `AiProposalFeatureApiV1.kt`, `AiFeatureApiV1.kt`, `ReminderFeatureApiV1.kt`, `ImportExportFeatureApiV1.kt`, and `SettingsDataFeatureApiV1.kt`.
- **Runtime adapter:** `app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt`.
- **Updated:** 2026-11-02.

This document describes the implemented Calendar, Event, Recurrence queries, Memo, Draft/Review, AI, Reminder, Import/Export, Settings/Data, and Core Diagnostics API boundaries. It does not declare full Core extraction, full domain coverage, runtime cutover, or Native/Skin parity. The Kotlin source is authoritative when a detail here drifts.

## 1. Contract boundaries

The feature contracts have separate jobs:

| Contract | Owner | Responsibility |
| --- | --- | --- |
| Core Feature API | `:core-api` | Versioned domain operations, queries, DTOs, results, revisions, and change notifications |
| Core Diagnostics Feature API | `:core-api` | Versioned read-only Store diagnostic values for status presentation |
| Memo Feature API | `:core-api` | Versioned unified todo occurrence projections and a current-occurrence visibility query |
| Native Presentation Contract | `:skin-api` | Native renderer inputs and callbacks; contains presentation models, not Core business services |
| Web Bridge Transport Contract | `WebEditWire` / `WebEditCommand` | Versioned JSON command decoding and Host reply transport; it is not a domain business API |

```text
Native presentation ─┐
                     ├─ Host adapters ─ CoreFeatureApiV1 ─ PowerCalCoreFeatureApiV1 ─ existing Core services/PowerStore
Web transport ───────┘       │
                             └─ owns confirmation UI, Android access, and transport/session projection
```

`:core-api` is a pure Kotlin JVM module. Its public contract contains no Android, Compose, `PowerStore`, repository, or internal Core entity types. `PowerCalApp` creates one `PowerCalCoreFeatureApiV1` per `PowerStore` and shares its domain services across Native callbacks and Web Host adapters. The runtime adapter currently lives in the app module and delegates to existing Core services such as `NewEventEditSession`, `EditSession`, recurrence logic, `PowerStore`, and the platform reminder scheduler; moving that implementation into a separately distributable Core runtime is not complete.

The Host is the only owner of Android context, database construction, trusted confirmation UI, and concrete deletion approval issuance. A Skin receives DTOs and opaque editor handles, never a Store reference or an approval issuer. Ordinary text-chat requests and canonical transcript query/mutation now enter through `AiFeatureApiV1`; the Android Host adapter retains the existing private-file format and performs persistence I/O.

## 2. Version and DTO rules

- `CoreFeatureApiV1`, `CoreDiagnosticsFeatureApiV1`, `CalendarFeatureApiV1`, `EventFeatureApiV1`, `RecurrenceFeatureApiV1`, `MemoFeatureApiV1`, and `SettingsDataFeatureApiV1` report `apiVersion == 1`.
- `EventKindV1` is `ALARM`, `ACTIVITY`, `DEADLINE`, or `MEMO`.
- `EventFormV1` is the editable value. `EventRecordV1` is an immutable formal-event query result with identity and `revision`.
- Recurrence weekday values in `EventRecurrenceV1.byDay` use ISO numbers: Monday `1` through Sunday `7`. Recurrence scope and occurrence keys preserve the existing Core recurrence semantics.
- `EventSelectionV1` accepts either an instant in epoch milliseconds or a local ISO date, never both. A zone is passed separately as an IANA `zoneId`.
- `EventEditorHandleV1` is an opaque, process-local reference. Its random ID is not authorization and is invalid after the adapter instance/process is gone.
- `EventEditorSnapshotV1.capabilities` describes operations available to the editor UI. Capability presence alone is not an authorization check; each operation still validates its handle, revision, lock state, and any required Host approval.
- Core-owned values are preserved on form updates. The adapter retains source/zone and recurrence details that the editor may not rewrite; Core validates and persists the resulting form.

## 3. Calendar query APIs v1

`CoreFeatureApiV1.calendar` exposes the platform-neutral `CalendarFeatureApiV1`. A `CalendarWindowQueryV1` requires a non-empty half-open instant window `[fromMillis, toMillis)` and a valid IANA `zoneId`.

- `queryWindow(query, source)` filters the supplied immutable `EventQuerySnapshotV1`; it performs no Store I/O and returns each matching formal-event owner once, preserving the source `featureRevision`.
- `queryWindow(query)` scans the authoritative Store through bounded reads in one read transaction, returns each matching formal-event owner once in axis-anchor order, and checks the Event feature revision before returning. A revision race returns `stale = true` for the Host to refresh.
- A timed event or recurrence owner matches when an effective occurrence intersects the window. Matching delegates to the existing `RecurrenceEngine`, retaining original-slot identity, override moves, recurrence boundaries, DST behavior, and interval overlap. The result contains owners; existing Host projections continue to expand display occurrences and apply their presentation rules.
- Date-only deadlines use the visible calendar dates in the query zone while retaining the owner's date interpretation. A non-repeating dated MEMO matches when its inclusive start/end dates overlap the visible dates; an undated MEMO does not match. Repeating MEMOs continue through the existing recurrence engine.
- Native and Web Host timeline/month reads use this same service. Timeline windows cover the existing 17-day horizon. Month windows cover the existing 42-day grid; Native month navigation requests the window for each selected month. A synchronous Web Host state refresh queries its live window when Compose's remembered window has not caught up with the action.
- Calendar projections receive only owners matching the current window. `PowerCalApp.reload()` now reads a bounded first Event page, exact formal count, Memo page, and its visible Calendar windows; it does not load the complete formal-event snapshot. The full `queryEvents()` snapshot remains available to callers that explicitly require it. `MonthVisualContext` exposes `EventRecordV1` for its current and per-month Skin data, while `TimelineSkinContext` exposes `EventRecordV1` and identity/revision callbacks. Both keep Core records private behind the Native Host adapter; the timeline camera uses the separate Native presentation DTO and its `yOf` method retains the existing true-time mapping.
- `queryEvents(CalendarEventQueryV1, source)` reuses `queryWindow` for an inclusive local-date range in the requested `zoneId`, or searches the supplied snapshot by a case-insensitive title substring; an optional `EventKindV1` narrows either selector. Callers must supply both date endpoints and either a date range or nonblank title. A range spans at most 366 calendar dates, titles at most 120 characters, and the result at most 100 formal event owners (default 20). `totalMatches` and `truncated` describe result limiting. Each detached hit carries event identity/revision, kind/title, temporal fields and precision, event zone, full recurrence, and source; notes, location, reminders, and completion state are omitted. A recurrence owner appears once, regardless of occurrences in the window.
- `queryEvents(CalendarEventQueryV1)` provides the Store-backed equivalent. It streams bounded owner pages, retains only the capped results, returns the exact total and revision, and reports stale if the revision changes during the scan.
- The `calendar.query.v1` chat tool is Core-registered as read-only and requires calendar-context permission. It is advertised only when the chat request opts in; `AndroidAiModelPortV1` also checks the persisted sharing preference, and Core independently rejects calls when the request flag is off. Core strictly parses the bounded schema, rechecks the conversation revision, returns the typed query result in the execution receipt, and creates the user-visible summary without a second provider call. Tool results are capped by the registry output bound; chat/tool receipt replay remains process-local. Querying does not write formal events or drafts.
- The API changes no Store transaction, recurrence policy, database schema, or backup format.

The pure-Kotlin contract is `CalendarFeatureApiV1.kt`; the Store-backed adapter is `StoreCalendarFeatureApiV1.kt`, with `SnapshotCalendarFeatureApiV1` retained as the immutable-snapshot projection helper. Verification limits and evidence are in the [Calendar API Issue](issues/PC-ISSUE-CORE-CALENDAR-API-V1.md).

## 4. Event operations

| Operation | API behavior |
| --- | --- |
| `queryEvents()` | Returns all current formal events in stable axis-anchor order and the adapter's `featureRevision`. Draft cards are not included. Host screens should use `queryEventsPage` or a bounded Calendar query. |
| `queryEventsPage(cursor, limit)` | Returns at most 500 active formal Event DTOs in axis-anchor order (default 100), plus the exact formal count and a keyset cursor. Cursor continuation is fenced by the Event API instance and `featureRevision`; stale cursors return `stale = true`. |
| `queryEvent(eventSeq)` | Returns one current formal event as a detached `EventRecordV1`; unknown, invalid, and retired identities return `null`. The record carries its current event revision. |
| `recoverAbandonedHumanEdit(staleAfterMillis)` | Removes a human-editor lease only when it belongs to another process and is older than the threshold. The default is 5 minutes; the Store rejects thresholds below 60 seconds. Returns `true` only when a lease was removed. This is lease recovery, not a formal-event mutation, so it does not change `featureRevision` or publish an Event change. |
| `searchEvents(query, snapshot)` | Searches the supplied immutable `EventQuerySnapshotV1`; it performs no Store I/O and returns the snapshot's feature revision with display-only hits (`eventSeq`, `revision`, `kind`, `title`). Blank queries return no hits. Title matching is case-insensitive; raw or formatted sequence matching is retained; snapshot order is retained; the API caps results at eight. Deck surfaces display at most four. |
| `searchEvents(query)` | Store-backed, bounded-memory search over active Events. It is suspending so the Native Host can perform the scan on an I/O dispatcher; it preserves axis order and the eight-hit cap. |
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

### Memo query API v1

`CoreFeatureApiV1.memos` exposes the unified todo query as stable `MemoEntryV1` DTOs. Both Native and Web Host paths share this service; Memo rendering receives no `TemporalRecord` or other Core entity.

- `queryEntries(source, nowMillis, includeCompleted)` projects the supplied immutable `EventQuerySnapshotV1` and preserves its `featureRevision`. It retains the existing 365-day lookback, seven-day future horizon, per-series recurrence expansion limit of 1200, skipped/completed occurrence handling, and deterministic ordering by start, event sequence, and original occurrence key.
- `queryEntriesPage(nowMillis, includeCompleted, cursor, limit)` scans Store owners in bounded pages and returns at most 250 globally ordered occurrence DTOs, with exact whole-query `totalEntries` and `openEntries`. Continuations bind the query parameters, Event feature revision, and Memo API instance; stale cursors or a revision race return `stale = true`.
- Projection still includes only enabled, completion-tracked events across ALARM, ACTIVITY, DEADLINE, and MEMO. Undated entries keep `startMillis == null`; date-only deadlines and date-span Memo remain stored unchanged. Repeated completion is keyed by the immutable original slot, and non-repeating items use `single`.
- `queryCurrentEntry(eventSeq, occurrenceKey, nowMillis, includeCompleted)` re-reads the current owner through the adapter and returns a DTO only if the event remains enabled, tracked, and within the same projection window. The Web Host compares the current revision and completion state before calling the existing Event completion command, which performs the final transactional validation.
- Native presentation and compiled runtime renderers receive `MemoEntryV1` values and opaque row-key callbacks. Native and Web Host paging now requests further pages through this API; exact counts and the existing 250-entry Web page size are preserved.
- No schema, backup format, Store transaction, recurrence rule, completion semantics, or Native fallback behavior changed.

The pure-Kotlin contract is `MemoFeatureApiV1.kt`; the Store-backed adapter is `StoreMemoFeatureApiV1.kt`. The [Memo API Issue](issues/PC-ISSUE-CORE-MEMO-API-V1.md) records tests, build evidence, and the untested device scope.

## 5. Receipts, errors, revisions, and idempotency

`EventEditorOpenResultV1` distinguishes `Opened`, `Stale`, `Missing`, `Locked`, and `Invalid`. Editor updates distinguish `UPDATED`, `NOT_FOUND`, `LOCKED`, and `INVALID`. Validation distinguishes `READY`, `INCOMPLETE`, `INVALID`, and `NOT_FOUND`.

Mutation receipts use `EventMutationStatusV1`: `CREATED`, `STAGED`, `UPDATED`, `DELETED`, `NO_CHANGE`, `CONFLICT`, `INVALID`, `LOCKED`, `NEEDS_RESTORE`, `NOT_AUTHORIZED`, `FAILED`, or `DUPLICATE_REQUEST_ID`. Receipts can include event/revision/draft identity, a related event, `errorCode`, a user-facing message, and `refreshRequired`.

For mutation and checkpoint requests:

- A request ID must be nonblank, at most 128 characters, and contain no ISO control characters.
- Each adapter instance retains up to 128 request receipts in memory. Replaying the same ID and operation fingerprint returns the cached receipt (mutation replay sets `duplicate = true`). Reusing an ID with a different fingerprint returns `DUPLICATE_REQUEST_ID` or an invalid checkpoint receipt.
- This cache is bounded and process-local. It is not a durable exactly-once guarantee across process death, adapter recreation, or eviction. Durable workflow recovery continues to rely on Core drafts/checkpoints and existing transaction rules.

`featureRevision` begins at zero for each adapter instance and increments when this adapter publishes a feature change. It is not the database revision and is not a cross-process sequence. Formal `revision` remains the optimistic concurrency token enforced by Core. Query results pair the current formal records with the adapter revision observed for that query.

## 6. Change notifications

`EventFeatureChangeV1` contains a feature revision, one of `FORMAL_CREATED`, `FORMAL_UPDATED`, `FORMAL_DELETED`, or `DRAFTS_CHANGED`, and optional event identity/revision. `PowerCalApp` subscribes and reloads its event projection when it observes a newer revision.

**Current limit:** only writes that publish through this API instance advance its feature revision or notify its listeners. Existing `DeckController` / direct `PowerStore` operations, other Host code paths, external Store instances, and process recreation are not observed automatically. Consumers must not treat the current change stream as a complete database change feed.

## 7. Authorization and integrity

- Event identities and `expectedRevision` are checked before opening/saving; Core transactions remain the final authority against concurrent writes.
- Deletion approval is issued by internal Host code only after its trusted confirmation flow. The concrete token is tracked by object identity, bound to editor handle and current event revision, and removed when consumed or the editor closes. Constructing a value that merely implements the marker interface grants no authority.
- `EventFormV1` input does not authorize a write by itself. Core editor update/validation, transactional save, and the explicit Host approval for formal deletion remain required.
- A `LOCKED`, `CONFLICT`, `INVALID`, or `FAILED` result does not imply that a formal write occurred. The caller must retain or reload editor state according to the receipt.

## 8. Draft/Review API v1 slice

`CoreFeatureApiV1.draftReview` exposes the platform-neutral `DraftReviewFeatureApiV1` contract. In `PowerCalApp`, Native Deck and Web Deck operations use the same Host-created API service and `DeckController`. Its DTOs project persisted cards, the selected form, conflict/restore prompts, completion summary, and command receipts without exposing `DeckController` or `PowerStore` types through `:core-api`.

- `draftVersion` is the persisted card version. `stateRevision` is an in-memory monotonic revision paired atomically with the controller snapshot; edits require both values, and the controller checks the revision again when applying the edit. Approved commit, conflict-confirmation, and restore commands recheck the exact state revision when the serialized command starts.
- `beginFormalReplacement` and `beginRestore` require the reviewed formal/tombstone revision. They create proposals and do not alter the formal event. If a reviewed commit must checkpoint a dirty form, that stage uses the reviewed persisted draft version as its optimistic precondition and stops on mismatch without rebasing stale form data over the newer card. A successful stage is followed by `PowerStore.commit` with the resulting version, checked again inside the write transaction. Stale formal conflicts return a separate `REVIEW_REQUIRED` result and require `confirmConflictReplacement` with the reviewed current revision and draft version.
- Restore remains a separate approval. A dirty reviewed form is checkpointed against its reviewed draft version without retrying over a concurrent update; the resulting draft version, reviewed tombstone revision, and exact form are then checked inside the `PowerStore.restoreDraft` transaction. A stale tombstone, draft, or form returns a conflict and leaves the identity retired and proposal pending.
- `edit`, `flush`, `select`, `move`, `jump`, `commit`, conflict approval, restore approval, `discard`, and `undoDiscard` return `DraftReviewReceiptV1`. Draft queue changes publish `DRAFTS_CHANGED`; successful create/replace/delete/restore commits publish the corresponding formal change through the shared Event API instance.
- `queryPendingDrafts()` reads the persisted queue and returns detached `DraftReviewCardV1` values in Store `queue_order ASC`; it retains the Store's existing `PENDING`, `CONFLICT`, and `INVALID` filter. This query is separate from the editor snapshot: it does not open or refresh `DeckController`, flush dirty input, write Store rows, or publish a change.
- `queryDraft(draftId)` reads one persisted draft by identity and returns a detached `DraftReviewCardV1` for any persisted status, including terminal and conflict states. Non-positive or missing IDs return `null`; the query does not open/refresh the editor, flush input, write Store rows, advance revisions, or publish a change.
- `queryRetiredEventsPage(offset, limit)` returns a bounded detached page, preserving `PowerStore.retiredPage` order (`updated_ms DESC, event_seq DESC`) and total. The API accepts offsets `0..1,000,000` and page sizes `1..100`.
- `queryRetiredEvent(eventSeq)` rereads one detached formal snapshot and returns it only while its identity is still retired; invalid, missing, and active identities return `null`. The Store lookup and retirement check are serialized together.
- Web Bridge restore listing and every tombstone revalidation now use these DraftReview queries. The Bridge retains its existing page normalization, revision checks, separate Host restore approval, and transaction-time Store validation; the queries themselves do not modify Store state, editor snapshots, feature revisions, or publish changes.
- Web Bridge persisted-draft rereads now use `queryDraft`; its seven conflict, restore, commit, and resolution checks retain their existing status/version, Deck revision, and Host approval conditions.
- Native Deck's built-in controls remain Host-driven. Runtime/imported Deck callbacks that request a formal commit, conflict replacement, or restore first open Host-owned confirmation UI. Commit/restore review displays the exact candidate's type, title, date/time, zone, location, notes, recurrence, reminder, enable/tracking state, and completed instances; conflict review adds the current revision and field differences. The UI checks its captured Deck state revision before calling the API.
- Draft/Review request receipts are retained in a bounded, process-local 128-entry cache. Durable request replay across adapter recreation/process death is not provided; do not describe the receipt cache as exactly-once persistence. A bounded Robolectric characterization reproduced that retrying `createDraft` with the same request ID and form after adapter recreation returns `APPLIED` with a second draft identity. The Store write carries no request ID; see the [Draft/Review API Issue](issues/PC-ISSUE-CORE-DRAFT-REVIEW-API-V1.md). Process-death replay was not directly exercised.
- Explicit user-owned text capture creates a draft through `DraftReviewFeatureApiV1.createDraft`. It accepts an `EventFormV1`, rejects recurrence-edit scope fields, uses the Store's existing user draft transaction and AI lock, and returns the new draft identity/version. The adapter snapshots collection fields before queueing and fingerprints them canonically for request replay. The Host opens the returned draft ID. AI proposal generation and batch control use `AiFeatureApiV1`; proposal staging/application remains on `AiProposalFeatureApiV1`.

These are Calendar, Event, Memo, Draft/Review, AI Proposal/Feature/Query, Reminder, Import/Export, and Settings/Data vertical slices, not full Phase 1 closure. Native and Web Deck formal searches use `searchEvents`, both Memo completion Host paths use `setOccurrenceCompletion`, Web event-open prevalidation, AI proposal target capture, and Web edit current-record rereads use `EventFeatureApiV1.queryEvent`, Memo projection and current-occurrence visibility use `MemoFeatureApiV1`, reviewed proposal actions use `aiProposals`, AI generation, chat query dispatch, and batch commands use `ai`, and Host reminder diagnostics/recovery use `reminders`. The compiled Draft Deck renderer now receives `DraftReviewSnapshotV1`, `EventFormV1`, and `EventKindV1` through `DeckVisualContext`; its Host retains Core conversion, editing, and approval. Other runtime projections and authorized workflows still need reconciliation. Other domain APIs remain outside this coverage.

## 9. AI Proposal API v1 slice

`CoreFeatureApiV1.aiProposals` exposes the platform-neutral `AiProposalFeatureApiV1` contract. Native and Web review paths map the Host-owned `AiInterpretation` into detached API DTOs and call the same Store adapter. The API returns typed per-item outcomes and does not expose `PowerStore`, `AiDraftService`, or Host approval issuance through `:core-api`.

- `createDrafts` stages explicit user-reviewed proposals and never publishes a formal event. Existing target revisions are checked before staging, duplicate target operations in one review are blocked, and an existing human draft is preserved. Its no-task outer request replay cache remains process-local.
- `applyReviewedProposals` acts only on selected review positions. `PUBLISH` requires a one-use Host-issued approval bound to the exact request fingerprint. Existing-target operations carry the reviewed revision; the Store checks it again inside the SQLite transaction. Pending task-backed drafts must still match the reviewed form and operation, and commits use the reviewed draft version and target revision.
- Per-item replay uses the existing `ai_task_receipts` table. Conversation tasks use their task/revision identity. Taskless API applies use an internal, deterministic task namespace derived from the request ID, so exact retries after API recreation reuse the original staged or committed item. The outer API request cache is still process-local. No database schema or backup format changed.
- Expired task requests may replay exact existing receipts. Staging can reuse an exact existing item receipt; publishing additionally requires a terminal receipt, enforced again inside the Store transaction. An expired request cannot create a missing receipt. Target revision and receipt fingerprint checks remain inside the transaction where required.
- Store change snapshots are captured while the Store monitor is held; feature changes are prepared without callbacks, then listeners run only after both the Store monitor and proposal mutation lock are released.
- Ordinary text chat, transcript query/append/clear/rewind, bounded `calendar.propose.v1` and `calendar.query.v1` dispatch, structured proposal generation, reviewed text/image evidence analysis, and AI batch selection, state, cancellation, recovery, reviewed execution, and stuck-session release now route through `CoreFeatureApiV1.ai`. Conversation persistence uses the Host-owned `AiConversationHistoryPortV1`; proposal staging/application still uses `aiProposals`. URI grants, pickers, bounded file/image reads, local OCR, consent and provider credentials remain Host-owned; web tools and multi-step orchestration are not implemented.

## 10. AI Feature API v1 slice

`CoreFeatureApiV1.ai` exposes the pure Kotlin `AiFeatureApiV1` contract. The Android Host supplies `AndroidAiModelPortV1`, which loads the existing protected AI settings and credentials and calls the existing `AiClient`; credentials and Android context do not enter `:core-api` DTOs.

- `queryConversation`, `appendConversationMessage`, `clearConversation`, and `rewindConversation` own the canonical in-memory transcript and monotonic process-local revision. Mutations require the expected revision; rewind resolves a stable message ID exactly once and preserves the existing user/assistant truncation and edit-prefill behavior. Snapshots are detached and the retained transcript is bounded to the latest 200 messages.
- `persistConversation(expectedRevision)` saves only a current snapshot through `AiConversationHistoryPortV1`; stale saves are rejected and persistence calls are serialized. `AndroidAiConversationHistoryPortV1` maps to the existing `powercal-chat-history.json` AtomicFile format and writes on `Dispatchers.IO`; the API does not expose Android storage details.
- `chat` accepts a request ID and expected conversation revision, then reads the bounded canonical Core transcript (up to 80 messages / 480,000 characters) to assemble `AiChatModelRequestV1`. Callers cannot supply alternate model history. It returns typed `AiChatReceiptV1` errors, and passes at most 60 formal events only when the Host opts in. The Android adapter reloads protected settings and `AiClient` rechecks the saved calendar-sharing preference before including context.
- `queryBatchState()` returns a detached snapshot of the shared AI batch lock and optional session. Host preflight may use `locked` to report temporary unavailability; it is not write authorization, and Core/Store commands retain the final lock enforcement.
- A process-local 128-entry request receipt cache replays a completed result for the same request ID and content fingerprint; reusing an ID for different content returns `DUPLICATE_REQUEST_ID`. This cache is not durable across process recreation. Explicit intent with a validated task invokes the existing proposal-generation path and returns review DTOs only. Missing tasks return a clarification; neither chat nor proposal generation stages or publishes events.
- Core offers the versioned `calendar.propose.v1` and `calendar.query.v1` chat functions. The Host serializes only Core registry definitions allowed by the request and saved-sharing preference; provider calls return as untrusted DTOs. Core accepts one bounded call, validates its name, ID, argument schema, permission scope, and conversation revision, then routes proposal calls to `generateProposal` or query calls to `CalendarFeatureApiV1`. Proposal calls still require explicit intent and a validated task and return review-only candidates. Query calls return bounded detached read results and a deterministic summary; they never modify formal events or drafts and are not followed by another model call. Unknown, malformed, oversized, multiple, unauthorized, or stale calls return a typed execution receipt. Providers returning HTTP 400/422 for a tool-enabled request get one bounded plain-chat retry; unsupported provider/model keys are held in a process-local cache capped at 64 entries and 12 hours. The outer chat/tool receipt is process-local.
- `generateProposal` accepts one text request or a validated conversation-task snapshot and returns detached, reviewable proposal DTOs only. The adapter supplies bounded calendar context under the existing persisted-sharing preference and request opt-in, and binds target revisions from the trusted local snapshot. Generation does not create drafts or publish events; the Host passes reviewed output to `aiProposals` for staging or application.
- `analyzeReviewedTextEvidence` accepts typed reviewed file text, reviewed OCR text, or unresolved ICS evidence. `analyzeConsentedImageEvidence` accepts bounded image bytes after the Host's explicit consent flow; neither command accepts a URI or reads a file. Both bind context to the current conversation revision, cap text at 24,000 characters and images at 6 MB, and return explanation plus an untrusted proposal source only. Unresolved ICS output cannot return a proposal source. Completed requests use a 128-entry process-local replay cache; a reused ID with different evidence is rejected, while cache state is not durable across process recreation. The commands do not stage or publish events. The Host keeps URI grants, pickers, file reads, local OCR and consent; any proposal source must use the separate `generateProposal` and review APIs.
- `selectBatchDrafts` gives the model bounded local candidate snapshots. The model returns IDs only; the feature API resolves them against the exact supplied IDs, versions, and operations, rejecting duplicates, out-of-scope IDs, and over-limit selection. The explicit “all” path is a Host-set flag and skips model selection.
- `runReviewedBatch` consumes a one-use Host approval bound by object identity to the exact mode and reviewed candidate snapshots. The Store rechecks draft versions and allowed operations when it creates the existing fenced AI session; `AiBatchRunner` retains per-item transactional commits and cancellation behavior. Cancellation, interrupted recovery, and stuck-session release use the same API, with release still requiring the existing stale-session eligibility and a second explicit Host approval.
- Queried session state and persisted terminal reports use typed DTOs. Native/Web presentation projections omit session identity, lock epoch, draft scope IDs, and detailed stored failure text. Host execution receipts retain the existing detailed result for Host feedback.
- Approval markers and the outer generation request have no durable replay guarantee. The batch workflow continues to rely on Store-backed session fencing and transactions. No schema, backup format, or frozen business semantics changed.
- The compiled `AiVisualContext` now exposes conversation messages, proposal review, and batch mode as versioned API DTOs. Image/OCR transport, document/file selection and consent, provider configuration, web tools, and multi-step orchestration remain outside this slice.

The pure Kotlin contract is `AiFeatureApiV1.kt`; Android provider and persistence adapters are `AndroidAiModelPortV1.kt` and `AndroidAiConversationHistoryPortV1.kt`, with Store-backed API state in `StoreAiFeatureApiV1.kt`. The ordinary chat slice and its initial evidence are in the [AI Conversation API Issue](issues/PC-ISSUE-CORE-AI-CONVERSATION-API-V1.md); transcript state commands and their follow-up evidence are in the [AI Conversation State API Issue](issues/PC-ISSUE-CORE-AI-CONVERSATION-STATE-API-V1.md); reviewed evidence intake is in the [AI Evidence Intake API Issue](issues/PC-ISSUE-CORE-AI-EVIDENCE-INTAKE-API-V1.md); proposal dispatch is in the [AI Proposal Tool Dispatch Issue](issues/PC-ISSUE-CORE-AI-PROPOSAL-TOOL-DISPATCH-V1.md); calendar query dispatch is in the [AI Calendar Query Tool Issue](issues/PC-ISSUE-CORE-AI-CALENDAR-QUERY-TOOL-V1.md); earlier batch/generation scope is in the [AI Feature API Issue](issues/PC-ISSUE-CORE-AI-FEATURE-API-V1.md).

The transcript state API adds API-level tests for revisions, persistence fencing, edit/retract/clear behavior and JSON-field compatibility; focused and full-gate counts and artifact hashes are recorded in the [AI Conversation State API Issue](issues/PC-ISSUE-CORE-AI-CONVERSATION-STATE-API-V1.md). No claim of packaged WebView or Native/Clear parity follows from JVM/build evidence.

## 11. Reminder API v1 slice

`CoreFeatureApiV1.reminders` exposes the platform-neutral `ReminderFeatureApiV1` contract. Native and Web Host paths share the same adapter, which reads durable schedule state from the existing `PowerStore` and delegates OS scheduling to the existing `ReminderScheduler`.

- `queryPermissions()` reports exact-alarm and notification permission requirements and current grants. When an API instance has no Android Host context, the permission fields are `null`.
- `queryEvent(eventSeq)` returns the current formal-event revision, its durable schedule intent (trigger ID, schedule revision, occurrence key, instant, exact/inexact requirement, state, and optional platform hint), and the latest execution marker.
- `reconcile(PENDING)` uses the existing bounded lifecycle reconciliation. `reconcile(EXHAUSTIVE)` uses the existing keyset-paged manual recovery path, including pending and cancelled work. The returned value reports attempted/failed counts, capped pending/blocked counts (`401` means 401 or more), status, and the first blocked hint. It is an operation receipt, not durable receipt storage and not proof of OS delivery.
- A contextless adapter returns `UNAVAILABLE` from reconciliation and leaves durable intents unchanged. Repeated reconciliation continues to rely on the existing scheduler's idempotent desired-state handling.
- The API adds no reminder policy, timing calculation, database schema, or backup format. Android settings launch remains a Host capability. OS receivers and boot recovery continue to use the same central scheduler.

The implementation is in `ReminderFeatureApiV1.kt` and `AndroidReminderFeatureApiV1.kt`; verification and limitations are recorded in the [Reminder API Issue](issues/PC-ISSUE-CORE-REMINDER-API-V1.md).

## 12. Import/Export API v1 slice

`CoreFeatureApiV1.importExport` exposes the platform-neutral `ImportExportFeatureApiV1` contract. Native and Web Host actions use the same service instance. Android file pickers, Calendar permissions, URI reads/writes, readback I/O, legacy-backup consent, and post-restore scheduler reconciliation remain trusted Host capabilities.

- ICS parsing stays local. `prepareIcsImport` delegates to the existing bounded `IcsImportPlanner`, returning typed candidate forms and unresolved untrusted evidence. Duplicate matching checks formal records, pending CREATE drafts, and earlier candidates in the same plan. `createIcsDraft` delegates to `DraftReviewFeatureApiV1.createDraft`; ICS input therefore remains reviewable and does not publish formal events. `queryIcsImportCheckpoint` and `recordIcsImportCheckpoint` expose the bounded receipt as a platform-neutral DTO; the Android adapter still uses the existing SharedPreferences representation and synchronous `commit`. The receipt stores only fingerprint, draft IDs, and problem count, remains capped at 12 recent inputs, and is a recovery hint rather than authorization. The Host revalidates its draft IDs through `existingDraftIds` and still runs candidate duplicate checks; missing or discarded drafts never cause candidates to be skipped.
- System Calendar provider reads and permission prompts stay in Android Host code. Each imported event is sent through the API's begin/import/finish batch commands, which delegate to the existing `PowerStore` migration and `importStructured` transactions. Source-link idempotence and imported-alarm handoff rules remain Store-owned. Newly imported records publish `FORMAL_CREATED`; retries that find an existing source link do not publish a second change.
- ICS export takes a fresh Store snapshot through the adapter and calls the existing `IcsCodec.exportActivities`; it remains an Activity-only, intentionally lossy format. Existing user disclosures remain in the Host.
- Backup export, bounded readback verification, and restore delegate to `PowerStore.exportBackup`, `BackupEnvelope.verifyReadback`, and `PowerStore.restoreBackup`. The API returns the envelope as an opaque UTF-8 JSON document for trusted Host file I/O and enforces the existing 64 MiB bound. Hash/reference checks, the empty-target transactional restore, and the explicit legacy-v2 consent gate are unchanged. Successful restore publishes `STORE_RESTORED`; reminder reconciliation and UI refresh remain Host work.
- The API adds no schema or backup-format changes and does not expose Android, Store, or Core entity types in `:core-api`. It does not send ICS evidence to an AI provider.

Implementation is in `ImportExportFeatureApiV1.kt` and `StoreImportExportFeatureApiV1.kt`. Android picker/SAF and device interoperability evidence remains separately gated; see the [Import/Export API Issue](issues/PC-ISSUE-CORE-IMPORT-EXPORT-API-V1.md) and the [ICS recovery checkpoint API follow-up](issues/PC-ISSUE-CORE-ICS-IMPORT-CHECKPOINT-API.md).

## 13. Settings/Data API v1 slice

`CoreFeatureApiV1.settingsData` exposes the platform-neutral `SettingsDataFeatureApiV1` contract.
The Native protected-profile slot, the Web profile editor Host route, and the first-run system
Calendar import prompt all use the same service instance. The API owns the profile-name and
one-time-prompt queries and commands; Android preference access remains inside the app adapter.

- `querySettings` reads the existing `powercal-profile/displayName` and
  `powercal-onboarding/system-calendar-import-asked` keys. No preference names, values, schema,
  or migration behavior changed.
- `saveProfileDisplayName` accepts an empty name, rejects names longer than 80 UTF-16 code units
  and ISO control characters, and returns `UPDATED`, `NO_CHANGE`, `INVALID`, or `UNAVAILABLE`.
- `markSystemCalendarImportPromptShown` is a one-way idempotent command. Mutations increment the
  adapter's process-local feature revision and publish typed changes; invalid and repeated writes
  do not change the revision.
- A contextless adapter reports `available == false` and returns `UNAVAILABLE` from writes. The
  contract and its DTOs expose no Android, SharedPreferences, Store, Core entity, or credential type.
- AI provider configuration, API keys and file-consent state remain in the protected Host surface
  under the current skin contract. Android permission launches, pickers, skin-package operations,
  and the Import/Export API's file I/O also remain Host capabilities.

Implementation is in `SettingsDataFeatureApiV1.kt` and `AndroidSettingsDataFeatureApiV1.kt`;
Native/Web routing and scoped verification are recorded in the
[Settings/Data API Issue](issues/PC-ISSUE-CORE-SETTINGS-DATA-API-V1.md).

## 14. Core Diagnostics API v1

`CoreFeatureApiV1.diagnostics` exposes the platform-neutral `CoreDiagnosticsFeatureApiV1` query.
`queryDiagnostics()` returns `CoreDiagnosticsV1`, containing the existing Store's next sequence
preview and durable identity count. The Store adapter delegates to the existing read methods;
it does not write, publish feature changes, or change a feature revision. These two values are
independent diagnostics and do not carry a shared transactional revision.

`PowerCalApp.reload()` uses this query for its existing Native and Web system-diagnostics
projection. The DTO contains only scalar values and exposes no Android, Store, database, or Core
entity type. This moves the two direct `PowerStore` reads behind the versioned Core API without
changing their values or Store semantics. Implementation and verification scope are recorded in
the [Core Diagnostics API Issue](issues/PC-ISSUE-CORE-DIAGNOSTICS-API-V1.md).

## 15. Recurrence next-occurrence query v1

`CoreFeatureApiV1.recurrence` exposes the platform-neutral `RecurrenceFeatureApiV1` query. A
`RecurrenceNextOccurrenceQueryV1` supplies a detached `EventRecordV1`, an `afterMillis` instant,
and an IANA `localZoneId`. The result echoes the supplied event sequence and revision and returns
either the next occurrence's original slot key, start/end instants, skipped state, and moved state,
or `null` when the series has ended. It is a projection of the supplied snapshot: it does not
authorize a write or establish that the event is still current.

The app adapter delegates to the existing `RecurrenceEngine`; recurrence boundaries, original-slot
identity, override handling, and DST behavior are unchanged. Native and Web Settings diagnostics
now use this same API for their up-to-three next-occurrence previews. `PowerCalApp` retains the
revision-checked paged event scan and chooses the first three repeating records; each preview is
computed with the current instant and local zone, then both projections receive the same detached
API result.

`queryOccurrenceByKey()` accepts the same detached Event snapshot, an original slot key, and an
explicit local zone. Its result echoes event sequence/revision and returns the materialized slot
or `null`; it does not establish currentness. Web timeline event-open validation and Web formal
write review use this query instead of calling `RecurrenceEngine` in the Host. The later Core event
open/save operations and trusted approval/transaction checks remain responsible for rechecking
write authority and freshness. Native timeline and month expansion now use the bounded query in
Section 16; recurrence edit commands remain outside this query API. Evidence is in the
[Recurrence API Issue](issues/PC-ISSUE-CORE-RECURRENCE-API-V1.md); the initial Settings diagnostics
migration is also recorded in the [Core Diagnostics API Issue](issues/PC-ISSUE-CORE-DIAGNOSTICS-API-V1.md).

## 16. Recurrence bounded occurrence-window query v1

`RecurrenceFeatureApiV1.queryOccurrencesInWindow()` materializes effective occurrence slots for
the supplied detached `EventRecordV1` over a half-open `[fromMillis, toMillis)` interval. The
request rejects empty or longer-than-43-day windows and caps each event at 2,048 returned
occurrences. Core asks the existing recurrence engine for one extra slot to set `truncated`, using
its existing window-start-prioritized selection so a bounded renderer retains the nearest
carry-in intervals when starts inside the window are numerous. The result echoes event sequence
and revision; it is a projection of that snapshot and does not establish event currentness or
authorize writes. Original recurrence keys, overrides, skipped/moved flags, local-zone rules, and
DST behavior remain owned by the existing recurrence engine.

The Native Host now uses this query for both its 17-day timeline projection and 42-day month grid.
It converts each owner to a detached DTO, verifies the echoed sequence/revision, then applies the
existing date-only deadline normalization and visible-window intersection before constructing
Native presentation rows. The service is passed privately by the Host and is not exposed through
the compiled Skin renderer context. The API remains separate from Native presentation and Web
Bridge transport contracts. Web timeline/month projection still expands recurrence in its Host
projection and is the next adapter routing step; Android/WebView execution remains unverified.
Implementation and verification are recorded in the
[Recurrence window API Issue](issues/PC-ISSUE-CORE-RECURRENCE-WINDOW-API-V1.md).

## 17. Current adapter coverage and open work

Verified code paths across the Calendar, Event, Recurrence query, Memo, Draft/Review, AI Proposal, Reminder, Import/Export, Settings/Data, and Core Diagnostics slices:

- Native profile save, Web profile open/save, and the first-run system Calendar import prompt use the shared `SettingsDataFeatureApiV1`; the Web Host still applies its scoped editor gate.
- Native event creation and ordinary formal editing/deletion call the Host's shared `EventFeatureApiV1`.
- `PowerCalApp.reload` obtains the initial formal-event projection through `queryEventsPage`, retaining the exact total; the full `queryEvents()` snapshot remains for callers that explicitly need all records.
- Startup abandoned-editor recovery uses `EventFeatureApiV1.recoverAbandonedHumanEdit` from `Dispatchers.IO`; the Store continues to enforce the foreign-process ownership and stale-timeout checks.
- Native and Web Deck formal search use `searchEvents`; Store-backed search is suspending and the Native/Web Host dispatches it to I/O.
- Native and Web Memo completion commands use `setOccurrenceCompletion`, with expected event revision and expected prior completion state. Only committed changes publish `FORMAL_UPDATED`.
- Web event-open prevalidation, AI proposal target capture, and active formal-event rereads throughout Web edit/save/delete and conflict review use `EventFeatureApiV1.queryEvent`. The editor open/write operations recheck revision and occurrence through the existing Core sessions and transaction paths.
- Native, Web, and runtime Memo projections use `MemoFeatureApiV1` stable DTOs. The Web current-occurrence visibility precheck is API-owned, and runtime callbacks resolve opaque row keys in the Host.
- Explicit text-to-draft capture uses `DraftReviewFeatureApiV1.createDraft`; successful persistence publishes one `DRAFTS_CHANGED` event and returns the draft ID, event sequence, and version.
- Web event editor create/update/checkpoint/save/delete and recurrence scope use the same API instance.
- Web Bridge reads shared AI batch lock state through `AiFeatureApiV1.queryBatchState()`; `WebSkinEditService` accepts neither `PowerStore` nor `DeckController`, and all writes remain guarded by their existing Core/Store checks.
- Native and runtime `DraftDeckDialog` paths now receive and observe only `DraftReviewFeatureApiV1` snapshots. The Core adapter no longer exposes its `DeckController` as a Host-readable property, and `PowerCalApp` does not read that property.
- Web timeline movement reads a Host projection, then stages the revision-guarded replacement through `stageFormalReplacement`.
- Native and Web Draft Decks use the shared `DraftReviewFeatureApiV1` for persisted draft edits and reads, queue navigation, formal replacement proposals, conflict and restore review, commit, discard, and undo.
- Compiled Compose Draft Deck renderers receive the versioned `DraftReviewSnapshotV1`, `EventFormV1`, and `EventKindV1` DTOs through `DeckVisualContext`; Host callbacks retain Core conversion, validation, writes, and approvals. Native fallback and protected conflict/restore handling remain available.
- Native and Web AI proposal review actions use `AiProposalFeatureApiV1` for staging and reviewed publication, including Host-owned publish approval and per-item Store receipts.
- Manual structured generation, automatic conversation-task interpretation, and file/image-derived proposal generation use `AiFeatureApiV1.generateProposal`; generation returns candidates only. Reviewed file/OCR/ICS interpretation and consented image analysis use the AI evidence commands; provider results remain untrusted. AI batch candidate selection, state, cancellation, interrupted recovery, reviewed execution, and explicit stuck release also use `ai`; model output cannot issue approval.
- Native and Web Host reminder diagnostics and reconciliation use the shared `ReminderFeatureApiV1`; bounded lifecycle recovery and exhaustive user-requested recovery remain distinct modes.
- Native and Web Host timeline/month reads use Store-backed `CalendarFeatureApiV1` windows. Native month navigation asks the Host for a fresh bounded query per month; Web synchronous state refreshes select data for the live window.
- Native and Web ICS import/export, structured Calendar import, and native backup export/restore now call `ImportExportFeatureApiV1`. ICS draft persistence delegates to Draft/Review; structured imports retain Store batch receipts and source-link idempotence; successful backup restore emits the shared `STORE_RESTORED` change.
- `PowerCalApp.reload()` obtains the sequence preview and identity count through the read-only `CoreDiagnosticsFeatureApiV1`; the query leaves Store counts, event revisions, and feature notifications unchanged.
- Native and Web Settings diagnostics receive next-occurrence previews from `RecurrenceFeatureApiV1`; Native timeline/month recurrence materialization also uses its bounded window query. Web timeline/month projection still expands recurrence in the Host projection and remains to be routed through this API.
- A Robolectric adapter integration test performs a Native-side API create followed by a Web bridge create/checkpoint and observes shared feature revision/change events and Store-backed state. Its Native-side write calls the API directly; it does not mount `PowerCalApp` and drive the entire Native UI route. Separate editor Compose tests cover the API-backed editor component callbacks.

Remaining work before PHASE 1 can exit:

1. Route remaining authorized Host/Core reads and writes through versioned queries/commands and one change source. `PowerCalApp.reload()` now queries the pending draft queue and Store diagnostics through their feature APIs; Web Bridge active-event, retired-event, persisted-draft, and AI batch lock reads use Event/DraftReview/AI API queries. The bounded Settings/Data inventory classified provider credentials/file consent and selected Skin as protected Host/runtime state; the ICS recovery receipt now uses Import/Export API. Remaining Host workflows still need contract-to-call-site reconciliation.
2. Move remaining business decisions out of Web transport/Host adapters; keep the bridge focused on transport and trusted Host capabilities.
3. Maintain the separate Native Presentation Contract while keeping renderer data on versioned feature DTOs and identities. `DeckVisualContext`, `AiVisualContext`, `MonthVisualContext`, and `TimelineSkinContext` expose versioned DTOs or identity-only callbacks; their Native state stays private behind Host adapters. The current audit found no remaining Core entity in these compiled renderer data contexts.
4. Add the remaining per-domain API contracts before claiming other feature areas are covered. The bounded AI `calendar.query.v1` tool now reuses `CalendarFeatureApiV1`; Store-backed Calendar windows, Event keyset pages, and Memo occurrence pages are implemented. Draft/Review request replay and AI proposal-generation request replay remain process-local; AI Proposal item receipts are Store-backed.
5. Run required device checks when the independently owned AVD is available. Memo, Event search/completion/current-event query, AI Proposal, and Draft Deck projection device behavior are **NOT TESTED**.

### MonthVisualContext DTO boundary (2026-11-02)

The compiled Month Skin context now exposes `EventRecordV1` for its current records and per-month query; its open callback carries only `eventSeq`, `revision`, and the optional occurrence key. Native recurrence expansion and row-key-to-original-occurrence routing continue to use private Host `TemporalRecord` state behind `Native()`; the Native Presentation Contract and month geometry are unchanged. Runtime event rows use the DTO directly and retain their ordering, summaries, and 70-row limit. The separate [TimelineSkinContext DTO boundary](#timelineskincontext-dto-boundary-2026-11-02) closes the next identified renderer projection gap. Phase 1 remains open.

Focused `MonthVisualContextContractTest`, `NativeMonthProjectionTest`, `GlassComposeTest`, and `DirectEventEditorComposeTest` passed **33/33 across 4 XML suites**. The forced offline gate passed **863/863 JVM tests across 136 XML suites**, 0 failures/errors/skips; both source boundaries, Debug APK assembly, lint (**0 errors / 53 warnings**), and AndroidTest Kotlin compilation passed. APK **66,912,878 bytes**, SHA-256 `3CF8D9B979FBFF4012C4E7006D8BAF98FAC84B84CD0E33EDE841EAF6404B90D5`. Focused/full log SHA-256 `A9C4BCC0B7C2705533359F03ED22FEC5C5E84C7932DA7BDC8452C76CE158BFC8` / `92DA0C84A1084E5AB6CE2A6A923F163AC7BD9AFD0944908A686F2E2B354C2A50`. Android instrumentation, WebView/device behavior, and Native/Skin parity are **NOT TESTED**. Details and log locations are in the [MonthVisualContext DTO Issue](issues/PC-ISSUE-CORE-MONTH-VISUAL-CONTEXT-DTO-V1.md).

### TimelineSkinContext DTO boundary (2026-11-02)

The compiled timeline renderer receives versioned `EventRecordV1` records, `NativeTimelineCamera`, and callbacks carrying event identity/revision, optional occurrence key, target time, or `EventKindV1`. The Host adapter maps identities back only to a current input record. `Native()` retains private `TemporalRecord` and `TimelineCoordinate.Camera` values; the existing Native projection continues to own recurrence expansion, original-slot routing, and true-time geometry. `yOf` delegates to the existing Core coordinate function. Runtime event rows retain their 70-row cap, while the Active Visual example still renders its full list. This is a presentation boundary, not a Core API or a skin cutover.

Focused `TimelineSkinContextContractTest`, `NativeTimelineProjectionTest`, `RuntimeSkinComposeTest`, and `TimelineTitleComposeTest` passed **21/21 across 4 XML suites**. The forced serial offline gate passed **864/864 JVM tests across 137 XML suites**, with 0 failures/errors/skips; both source boundaries, Debug APK assembly, lint (**0 errors / 53 warnings**), and AndroidTest Kotlin compilation passed. APK **66,896,494 bytes**, SHA-256 `ED8A5C5046E3F8DFAFE7CAE69328020DB4A58DDD2A7F1D22F049A980F604BA0B`. Focused/full log SHA-256 `6ACE86F45C870CC4D0BFDB3CC016124F30F6CFEAC7D80234417E9B557D3A553F` / `15376DA770098996C8A855E37C759921CB9DCCE8BACD5DFD37FD1798EF5B2742`, under `.local/android-test/reports/timeline-skin-context-api-dto-20261102/`. Android instrumentation, packaged WebView/device behavior, and Native/Skin parity are **NOT TESTED**. See the [TimelineSkinContext DTO Issue](issues/PC-ISSUE-CORE-TIMELINE-SKIN-CONTEXT-DTO-V1.md).

### Draft Deck renderer DTO boundary follow-up (2026-11-01)

`DeckVisualContext.state`, `onEdit`, and `onConvert` now carry `DraftReviewSnapshotV1`, `EventFormV1`, and `EventKindV1`. Core edit/conversion and publishability checks stay inside the Host adapter; runtime renderers receive only the versioned feature values and Host callbacks. `NativeDeckState` remains a separate Native Presentation Contract. The focused DTO/edit regression and full verification are recorded in the [Draft Deck projection Issue](issues/PC-ISSUE-CORE-DRAFT-REVIEW-PROJECTION-API-V1.md). This does not complete Phase 1 or claim Native/Clear parity or device behavior.

Latest Draft/Review verification: focused API, DeckController, Store restore, Host review UI, and Web bridge tests passed **190/190**; the forced full JVM run passed **800 tests across 128 XML reports, 0 failures/errors/skips**. `verifySkinSourceBoundary`, `verifyNativeSkinBoundary`, Debug APK assembly, and `lintDebug` all passed; lint reported **0 errors / 54 warnings**. The Debug APK SHA-256 is `C22E82E434CBE5B02CD790FF72932074DA969A10BDF9095416179D3B8331AFB4` (66,683,502 bytes). Instrumentation and AVD behavior are **NOT TESTED**. Logs and the prior Event pilot’s transient full-suite failures are recorded in the current [progress entry](agent-handoff/PROGRESS.md) and the [Draft/Review Issue](issues/PC-ISSUE-CORE-DRAFT-REVIEW-API-V1.md); no cause is asserted for those earlier failures.

Latest Event search/completion verification: focused API, adapter, Deck, and Web tests **64/64 PASS**; forced full JVM **803 tests across 128 XML reports, 0 failures/errors/skips**; AndroidTest Kotlin compilation **PASS**; source-boundary checks, Debug APK assembly, `lintDebug`, and `git diff --check` **PASS** with 0 lint errors / 54 warnings. Android instrumentation and device behavior are **NOT TESTED**. The Debug APK is 67,552,075 bytes, SHA-256 `03108E62D7BE9473C65A887D87BB906EDEAAD7AFFA46B18D0415BCD864161C06`. Details and per-log hashes are recorded in the [Event search report](issues/PC-ISSUE-CORE-EVENT-SEARCH-API-V1.md), [occurrence completion report](issues/PC-ISSUE-CORE-OCCURRENCE-COMPLETION-API-V1.md), and [AndroidTest harness migration report](issues/PC-ISSUE-SEPARATION-ANDROIDTEST-FEATURE-API-MIGRATION.md). Code commit `c9948d5465bc93da7e59984342c756549df9e295` was fast-forward pushed to `origin/feat/skin-first-separation`; Phase 1 remains open.

Latest Draft creation verification: focused DraftReview API tests **13/13 PASS**; forced full JVM **806 tests across 128 XML reports, 0 failures/errors/skips**; AndroidTest Kotlin compilation, source-boundary checks, Debug APK assembly, `lintDebug`, and `git diff --check` **PASS**. Lint reported **0 errors / 54 warnings**. Android instrumentation is **NOT TESTED**. The Debug APK is 67,552,443 bytes, SHA-256 `C7C52F7EB0079968D4A0E8548BD742CD931181CFFA94D23BD41871021D8A84F3`. Per-log hashes and residual scope are recorded in the [Draft Creation Issue](issues/PC-ISSUE-CORE-DRAFT-CREATION-API-V1.md). Code commit `cb5823c573c1cbc2c59c50f751e099cee4e30d5e` was ordinary fast-forward pushed to `origin/feat/skin-first-separation`; Phase 1 remains open.

Latest AI Proposal verification: focused API and Store receipt suites **24/24 PASS**; forced serial full JVM **824 tests across 129 XML reports, 0 failures/errors/skips**. Skin and Native source-boundary checks, Debug APK assembly, `lintDebug` (**0 errors / 54 warnings**), and AndroidTest Kotlin compilation all **PASS**. The Debug APK is **66,749,038 bytes**, SHA-256 `70D6D0E70D955F82416DE352CC9E1DAA5B9E9404C7C794DF5AEF3685AA79BF9F`. One earlier full run exposed an immediate UI assertion before the asynchronous Host confirmation appeared; the isolated test passed, the test now waits for the confirmation node, and the final serial suite passed. Android instrumentation and device behavior are **NOT TESTED**. Final evidence is in `.local/android-test/reports/core-ai-proposal-api-20261101/` and the [AI Proposal Issue](issues/PC-ISSUE-CORE-AI-PROPOSAL-API-V1.md). Code commit `92200a86bb5aadcd767cc7e3a5b858dccffdca5f` was fast-forward pushed to `origin/feat/skin-first-separation`; Phase 1 remains open.

Latest Reminder API verification: focused API, Web recovery, and diagnostics tests **10/10 PASS**; nine related API/receiver/Host regression classes account for **49/49 PASS** in the final suite. Forced serial full JVM **828 tests across 130 XML reports, 0 failures/errors/skips**. Both source-boundary checks, Debug APK assembly, `lintDebug` (**0 errors / 54 warnings**), AndroidTest Kotlin compilation, and `git diff --check` **PASS**. The Debug APK is **66,749,038 bytes**, SHA-256 `291A5E275D625ABEBB602DCABE80E19A91258E16FA572F4B5D2641B0E8A2A999`. Android instrumentation, actual OS alarm delivery, and device/OEM behavior are **NOT TESTED**. Logs are under `.local/android-test/reports/reminder-feature-api-20261101/`; see the [Reminder API Issue](issues/PC-ISSUE-CORE-REMINDER-API-V1.md). Code commit `03af508` and documentation commit `c17275e7a615f6d66d1424695f8211ce5f55d884` were fast-forward pushed to `origin/feat/skin-first-separation`; PHASE 1 remains open.

Latest Calendar window API verification: focused Calendar/Event/Native timeline/month and Core API adapter selection **16/16 PASS**; forced serial full JVM **831 tests across 131 XML reports, 0 failures/errors/skips**. Both source-boundary checks, Debug APK assembly, `lintDebug` (**0 errors / 54 warnings**), AndroidTest Kotlin compilation, and `git diff --check` **PASS**. The Debug APK is **66,765,422 bytes**, SHA-256 `8AA317888327AD461AAB648A4860C833235A7C10E9BA48F9D7BC8897A3B9014E`. Android instrumentation, packaged WebView behavior, and physical/OEM behavior are **NOT TESTED**. Full log SHA-256 `D7C41F4B4D3CA6436B299E46B537A231F3986658C63FB314D08C2C6F5C1B1C1A`; final focused log `D3F2B1E0CFBFB7A9DC5C78C56673E96E9D4201602111B267A1B752C56BB6240D`. See the [Calendar API Issue](issues/PC-ISSUE-CORE-CALENDAR-API-V1.md) and `.local/android-test/reports/calendar-feature-api-20261101/`. Code commit `313562e386a14334314b7b2dc0a405e4e0465ad7` was fast-forward pushed to `origin/feat/skin-first-separation`; PHASE 1 remains open.

Latest AI Feature API v1 verification: focused AI/API/Host/Web selection **55/55 PASS across 10 XML suites**; forced serial offline gate **843/843 JVM tests across 134 XML suites, 0 failures/errors/skips**. verifySkinSourceBoundary, verifyNativeSkinBoundary, Debug APK assembly, lintDebug (**0 errors / 53 warnings**), AndroidTest Kotlin compilation, and git diff --check passed. APK **66,830,958 bytes**, SHA-256 98E719B76A055A9B7045086193653F62DE424BC81BADC79A3F8C2B3EA8A3CE18. Code commits 19eaf2c248cfae009904da26c6e68ee5896c448f and 98fda6ef08c53e1fd6fd1c25f3b0d9a17af3f2e3 were fast-forward pushed to origin/feat/skin-first-separation. Focused log focused-api-tests-final.log and full log full-gate-final.log are in .local/android-test/reports/core-ai-feature-api-20261101/ and the [AI Feature API Issue](issues/PC-ISSUE-CORE-AI-FEATURE-API-V1.md). ADB had no device and no AVD was configured; instrumentation, actual Native/Web UI routes, packaged WebView, live-provider behavior, and physical/OEM behavior are NOT TESTED. PHASE 1 remains open.

Latest Memo Feature API v1 verification: focused recurrence-window, unified projection, and completion/current-visibility tests **3/3 PASS**; forced serial offline gate **844/844 JVM tests across 134 XML suites, 0 failures/errors/skips**. Both source boundaries, Debug APK assembly, `lintDebug` (**0 errors / 53 warnings**), AndroidTest Kotlin compilation, and `git diff --check` passed. APK **66,847,342 bytes**, SHA-256 `239B74AACCB179B048572A5990D15F0351687DD8991BF6384182177BACE92724`. Focused/full log SHA-256 `A130824BF53051F7144B598C593CA4D3AC3440F40E68B7F017739E217CA1C4A9` / `1C2F57B2DC50310C4631B16300E3E8566D2827EE57DEC05F1297CB0F6E4E496F`; both are under `.local/android-test/reports/core-memo-feature-api-20261101/`. ADB listed no device and no AVD was configured; instrumentation, actual Native/Web runtime routes, packaged WebView, and physical/OEM behavior are **NOT TESTED**. PHASE 1 remains open.

### ICS ImportCheckpoint Feature API routing (2026-11-02)

`ImportExportFeatureApiV1` now queries and records the existing ICS recovery receipt through `IcsImportCheckpointV1`. `PowerCalApp` no longer calls `ImportCheckpoint` directly; the Android adapter keeps the existing SharedPreferences key/format, synchronous `commit`, and 12-input bound. The Host still confirms each saved ID exists and runs ordinary candidate duplicate checks. A receipt cannot authorize skipping a missing/discarded draft or publishing an event. The DTO remains free of Android types.

Focused `ImportExportFeatureApiV1Test`, `WebAiAttachmentFlowIntegrationTest`, and `ImportCheckpointTest` passed **12/12 across 3 XML suites**. The first forced serial full gate collected **885 tests with 884 passed and one unrelated Memo Compose paging timeout**; that exact method passed isolated **1/1**. A second forced serial gate passed **885/885 across 138 XML suites**, 0 failures/errors/skips; both source boundaries, Debug APK, lint (**0 errors / 54 warnings**), AndroidTest Kotlin compilation, and `git diff --check` passed. APK: **67,011,182 bytes**, SHA-256 `2BD62FC3FD652093A5C948D4A7E7D46F35A6BE88FD4190D4941606B39AEC5237`. Focused/full-pass/full-failure/isolated log SHA-256: `EA5B990A6EED635B3EACF1887048A7F73483D64B50C959DF46A9F48FC4E46271` / `DBAA2382DE9570F36694218EF9CE0F4A0AF1155AF11FB75E2AA676E62848E24A` / `0C37AC98E4755A5E32ED547E64487B9947776498F1F09E1AE5C2D0053D20DD6A` / `F24E9E449DE8918F24CF2818D4B38E524EB4A9354B149A036408100E16488A29`; artifacts are under `.local/android-test/reports/phase1-ics-checkpoint-api-20261102/`. ADB had no connected devices and no AVD was configured; AndroidTest compiled but was not executed. Native/Skin parity, packaged WebView and device/OEM behavior remain **NOT TESTED**. Code commit `1f8a415cbb64f2d22c2b0ae1cf1c11dfa9dd9975` follows `223fd3a22da6ebe9dde8b3a05d0e6e31e77d6453`; PHASE 1 remains open.

Latest Event current-record query v1 verification: Event API/Web AI/Web event-open suites **28/28 PASS** (6 + 15 + 7); forced serial offline gate **845/845 JVM tests across 134 XML suites, 0 failures/errors/skips**. Both source boundaries, Debug APK assembly, lint (**0 errors / 53 warnings**), AndroidTest Kotlin compilation, and `git diff --check` passed. APK **66,847,342 bytes**, SHA-256 `9C556FA6611FBCD5BF7B1C7CCFD139CDCC17EB722027B98FF4417303D2336A5A`. Focused/full log SHA-256 `C2B81E4DB5A8F3B7353C4CD52CCE98B4F1AEE962279B189C75EC32D04C707B6F` / `73D9260DA774AA4E4FF5A88A09065E91836E801BB15A1DDB29F7FE6959218495`; logs are under `.local/android-test/reports/core-event-query-api-20261101/`. Device instrumentation and packaged WebView behavior remain **NOT TESTED**; see the [Event current query Issue](issues/PC-ISSUE-CORE-EVENT-QUERY-API-V1.md).


### Event startup editor lease recovery API (2026-11-02)

`EventFeatureApiV1.recoverAbandonedHumanEdit(staleAfterMillis)` exposes the existing Store timeout cleanup as a versioned command. It removes only another process's editor lease when `touched_ms` is strictly older than the threshold. The default is five minutes; thresholds below 60 seconds are rejected. It returns whether a row was removed and does not mutate formal events, increment `featureRevision`, or publish Event change notifications. `PowerCalApp` invokes it from `Dispatchers.IO`.

Focused `EventFeatureApiV1Test` passed **8/8**; the forced serial offline gate passed **878/878 JVM tests across 137 XML suites**, 0 failures/errors/skips, both source boundaries, Debug APK assembly, lint (**0 errors / 54 warnings**), AndroidTest Kotlin compilation, and `git diff --check`. APK **67,011,182 bytes**, SHA-256 `CE50CEDE2D787590D8A68ED885398096DFF1B3D99B2F00C96C36972A974C3137`; gate log SHA-256 `0E0217931863895A408115D524D08E0EA23E119359937B3138AABD857AE442BE` in `.local/android-test/reports/core-event-lease-recovery-20261102/full-gate.log`. Device/runtime behavior is **NOT TESTED**. See [Event API Issue](issues/PC-ISSUE-CORE-EVENT-API-V1.md).

### DraftReview persisted pending queue query (2026-11-02)

`DraftReviewFeatureApiV1.queryPendingDrafts()` exposes the Store's persisted actionable queue as detached DTOs, preserving `queue_order ASC` and the existing `PENDING` / `CONFLICT` / `INVALID` selection. `PowerCalApp.reload()` uses it without opening or refreshing the Deck editor. The focused API suite passed **14/14**. Two full serial JVM runs each had **878/879 passed** because the retained Memo paging UI case timed out; that exact method passed in an isolated rerun, but the full-suite result remains **FAIL**. Separate source-boundary/APK/lint/AndroidTest-compilation checks passed; APK SHA-256 `01DFC4235E47E052D828D775919D9DA9EA535B7631F5C8AF3AAEB51A243121C1`. Device, WebView, and Native/Skin runtime behavior remain **NOT TESTED**. Full evidence and hashes are in the [Draft/Review API Issue](issues/PC-ISSUE-CORE-DRAFT-REVIEW-API-V1.md) and `.local/android-test/reports/core-draft-queue-20261102/`.

### DraftReview persisted draft-by-ID query (2026-11-02)

`DraftReviewFeatureApiV1.queryDraft(draftId)` returns a detached card for any persisted draft status and `null` for invalid/missing IDs. Web Bridge's seven persisted-draft rechecks now use this query, retaining form/status/version comparisons, Deck state revision checks, and Host-owned approvals. The regression verifies pending and discarded cards plus unchanged Store counts, editor snapshot, Event feature revision, and notifications. Focused DraftReview/Web Bridge tests passed **53/53** (16 API and 37 Bridge tests). Forced serial JVM verification passed **883/883 tests across 138 XML suites**, with 0 failures/errors/skips. Both source boundaries, Debug APK, lint (**0 errors / 54 warnings**), AndroidTest Kotlin compilation (**146 tasks**), and `git diff --check` passed. APK: **67,011,182 bytes**, SHA-256 `EDFC858B1A7A95F625F2E8FB28FDC956B7A1DD3EAD0190E9C46B7974C064E3FA`. Focused API/Bridge XML SHA-256: `72D7FA229ED55FA243130A0F1E4BA8C36072CE57560E5F141A1AE08368039314` / `B01796ECB30C993E150A5818BDB784447A1E8545538F0ECA4D23E5C62FEB0C92`; focused/full/build logs: `24C4234D60BF020D7C6A4EFB2FB6786F2B114D5BE2052C7135C4D61D34EEF51F` / `3C12CB655FE771791F4E6C762418F046B4D93F349A9F574A3D829C61E53E7D9E` / `861AE6754ABEDDF65C798C93ED01FC24FAB9D342F1B7D813B3C5CDC4962E76FA`, under `.local/android-test/reports/draft-review-query-by-id-20261102/`. Code commit `1b0c21a0cfee077caf96b709033dd45399ad440a` follows `1b016c4d13f2951be61b6849fb18260a6acd36b9`. No ADB device or AVD was configured; Android instrumentation, packaged WebView, and Native/Skin parity remain **NOT TESTED**. See the [DraftReview query-by-ID Issue](issues/PC-ISSUE-CORE-DRAFT-QUERY-API-V1.md); PHASE 1 remains open.

### Web Bridge AI lock-state API routing and Core-free constructor boundary (2026-11-02)

Web Bridge's nine temporary-lock prechecks now call `AiFeatureApiV1.queryBatchState().locked` on `Dispatchers.IO`. This snapshot is only a Host admission/status hint; formal mutations retain their existing Core/Store lock enforcement. The service accepts neither `PowerStore` nor `DeckController`; editor persistence reads go through the versioned DraftReview/Event/AI APIs.

Focused Web Bridge, recurrence, trusted-write, and Core API adapter tests passed **55/55 across four XML suites**. Both forced serial full JVM attempts collected **883 tests across 138 suites, with one failure each**, the same `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` Compose timeout. The exact method passed in isolation **1/1**; the aggregate remains **FAIL** for both attempts. Both source boundaries, Debug APK assembly, lint (**0 errors / 54 warnings**), AndroidTest Kotlin compilation (**146 tasks**), and `git diff --check` passed. APK **67,011,182 bytes**, SHA-256 `9F8C6632F5BE1ABAE556C561004B9F2867055DCDFD352AA3DBCD598352B3BEDD`. Focused/first aggregate/second aggregate/isolated/build log SHA-256: `6B4FB0AF7B8A259AD52A4964CD6E613E002CD62F78793214B0CDEC2A2BC3F18B` / `5C10170707F1D21E74EC20BB595459248DF23F7180465C91C32685ADFF463C48` / `E8F58E14A5C426E1CC0DFFF6F41BBBE34391B4C377D6872CCB8437301BFB5B91` / `E91594D3F5A25B29048A813F846D2FFE3A19699B66BB34EA78B681A33156611D` / `970724BAD47AA97C6FC135D7C86862C9CFA79D57EB2D76C36554FF33AB1FBAA4`, under `.local/android-test/reports/core-web-api-only-20261102/`. Code commits `d5c68f2eff43a8af01fdb253f8ab27f7daf8fec3`, `3726025ce8f735529427abb36c077eb646ae587a`, and `c6643876` follow `4865ad32135647d49a57a2ab81c1d034ad60a53e`. No ADB device or AVD was configured; instrumentation, packaged WebView, and Native/Skin parity remain **NOT TESTED**. A bounded PHASE 1 exit-condition check follows; this slice alone does not claim phase completion.

The two full JVM failures above describe the Web-lock slice baseline before the later Native/runtime Draft Deck API migration. The integrated run at commit `97acf03ae736d817f9d92381d75b90b6c6f43b94` passed 883/883; see the next verification section.

### Native/runtime Draft Deck through the DraftReview API (2026-11-02)

`SkinnableDraftDeck` no longer accepts or observes `DeckController`; its state subscription, current snapshot, discard identity, and error refreshes now use the shared `DraftReviewFeatureApiV1`. `PowerCalCoreFeatureApiV1` no longer exposes the controller as a Host-readable property, and `PowerCalApp` uses the same API for Native and runtime Draft Deck presentation. Commands, approval fences, conversion, and persistence behavior remain unchanged.

Focused `CoreApiAdapterTest`, `DraftReviewFeatureApiV1Test`, `GlassComposeTest`, and `GlassRenderTest` passed **42/42 across four XML suites**, 0 failures/errors/skips. Forced serial full JVM passed **883/883 across 138 XML suites**, 0 failures/errors/skips. Both source boundaries, Debug APK, lint (**0 errors / 54 warnings**), AndroidTest Kotlin compilation (**146 actionable tasks**), and `git diff --check` passed. APK: **67,011,182 bytes**, SHA-256 `F264694BE380E0D1D2D885A17173E2932311FD0BA106F9A337C4FF67E708775B`. Focused/full/build log SHA-256 `9B5826362B1F0CDC5C96970C2DF13EA5F1FB2404A01260F555E4ADE67D1FFC9D` / `7FBC2D92B3AFF4BFA938030185EA84F781CF7BD5EA1F5ACD6ABC05F3E87B8097` / `0513C2E603C65B9420BAB083AAA652C29C07A32D00812A0982FEB9968C4BC63F`; reports and JUnit XML are under `.local/android-test/reports/phase1-core-controller-encapsulation-20261102/`. Code commit `97acf03ae736d817f9d92381d75b90b6c6f43b94` follows `1118bc062216a5904d5dea6f7619283f80a41e76`. ADB listed no devices and no AVDs; Android instrumentation, packaged WebView, and device parity remain **NOT TESTED**. PHASE 1 remains open.
