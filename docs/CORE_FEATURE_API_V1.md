# Core Feature API v1 — Calendar, Event, Memo, Draft/Review, AI, Reminder, Import/Export, and Settings/Data slices

- **Status:** Calendar window queries, Event, Memo, Draft/Review, AI Proposal/Feature, Reminder, Import/Export, and Settings/Data vertical slices implemented; PHASE 1 remains in progress.
- **Contract source:** `:core-api`, package `dev.powerfulcalendar.core.api.v1`, including `CoreFeatureApiV1.kt`, `CalendarFeatureApiV1.kt`, `MemoFeatureApiV1.kt`, `DraftReviewFeatureApiV1.kt`, `AiProposalFeatureApiV1.kt`, `AiFeatureApiV1.kt`, `ReminderFeatureApiV1.kt`, `ImportExportFeatureApiV1.kt`, and `SettingsDataFeatureApiV1.kt`.
- **Runtime adapter:** `app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt`.
- **Updated:** 2026-11-01.

This document describes the implemented Calendar, Event, Memo, Draft/Review, AI, Reminder, Import/Export, and Settings/Data API boundaries. It does not declare full Core extraction, full domain coverage, runtime cutover, or Native/Skin parity. The Kotlin source is authoritative when a detail here drifts.

## 1. Contract boundaries

The feature contracts have separate jobs:

| Contract | Owner | Responsibility |
| --- | --- | --- |
| Core Feature API | `:core-api` | Versioned domain operations, queries, DTOs, results, revisions, and change notifications |
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

The Host is the only owner of Android context, database construction, trusted confirmation UI, and concrete deletion approval issuance. A Skin receives DTOs and opaque editor handles, never a Store reference or an approval issuer.

## 2. Version and DTO rules

- `CoreFeatureApiV1`, `CalendarFeatureApiV1`, `EventFeatureApiV1`, `MemoFeatureApiV1`, and `SettingsDataFeatureApiV1` report `apiVersion == 1`.
- `EventKindV1` is `ALARM`, `ACTIVITY`, `DEADLINE`, or `MEMO`.
- `EventFormV1` is the editable value. `EventRecordV1` is an immutable formal-event query result with identity and `revision`.
- Recurrence weekday values in `EventRecurrenceV1.byDay` use ISO numbers: Monday `1` through Sunday `7`. Recurrence scope and occurrence keys preserve the existing Core recurrence semantics.
- `EventSelectionV1` accepts either an instant in epoch milliseconds or a local ISO date, never both. A zone is passed separately as an IANA `zoneId`.
- `EventEditorHandleV1` is an opaque, process-local reference. Its random ID is not authorization and is invalid after the adapter instance/process is gone.
- `EventEditorSnapshotV1.capabilities` describes operations available to the editor UI. Capability presence alone is not an authorization check; each operation still validates its handle, revision, lock state, and any required Host approval.
- Core-owned values are preserved on form updates. The adapter retains source/zone and recurrence details that the editor may not rewrite; Core validates and persists the resulting form.

## 3. Calendar window query API v1

`CoreFeatureApiV1.calendar` exposes the platform-neutral `CalendarFeatureApiV1`. A `CalendarWindowQueryV1` requires a non-empty half-open instant window `[fromMillis, toMillis)` and a valid IANA `zoneId`.

- `queryWindow(query, source)` filters the supplied immutable `EventQuerySnapshotV1`; it performs no Store I/O and returns each matching formal-event owner once, preserving the source `featureRevision`.
- A timed event or recurrence owner matches when an effective occurrence intersects the window. Matching delegates to the existing `RecurrenceEngine`, retaining original-slot identity, override moves, recurrence boundaries, DST behavior, and interval overlap. The result contains owners; existing Host projections continue to expand display occurrences and apply their presentation rules.
- Date-only deadlines use the visible calendar dates in the query zone while retaining the owner's date interpretation. A non-repeating dated MEMO matches when its inclusive start/end dates overlap the visible dates; an undated MEMO does not match. Repeating MEMOs continue through the existing recurrence engine.
- Native and Web Host timeline/month reads use this same service. Timeline windows cover the existing 17-day horizon. Month windows cover the existing 42-day grid; Native month navigation requests the window for each selected month. A synchronous Web Host state refresh queries its live window when Compose's remembered window has not caught up with the action.
- Calendar projections receive only owners matching the current window. The Host still loads the complete formal-event snapshot through `queryEvents()`; this v1 query is not Store-level paging. Current Host projections also convert returned DTOs into existing Core records, so runtime projection extraction remains open work.
- The API changes no Store transaction, recurrence policy, database schema, or backup format.

The pure-Kotlin contract is `CalendarFeatureApiV1.kt`; the app adapter is `SnapshotCalendarFeatureApiV1.kt`. Verification limits and evidence are in the [Calendar API Issue](issues/PC-ISSUE-CORE-CALENDAR-API-V1.md).

## 4. Event operations

| Operation | API behavior |
| --- | --- |
| `queryEvents()` | Returns all current formal events and the adapter's `featureRevision`. Draft cards are not included. No paging/filter parameters exist in v1. |
| `queryEvent(eventSeq)` | Returns one current formal event as a detached `EventRecordV1`; unknown, invalid, and retired identities return `null`. The record carries its current event revision. |
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

### Memo query API v1

`CoreFeatureApiV1.memos` exposes the unified todo query as stable `MemoEntryV1` DTOs. Both Native and Web Host paths share this service; Memo rendering receives no `TemporalRecord` or other Core entity.

- `queryEntries(source, nowMillis, includeCompleted)` projects the supplied immutable `EventQuerySnapshotV1` and preserves its `featureRevision`. It retains the existing 365-day lookback, seven-day future horizon, per-series recurrence expansion limit of 1200, skipped/completed occurrence handling, and deterministic ordering by start, event sequence, and original occurrence key.
- Projection still includes only enabled, completion-tracked events across ALARM, ACTIVITY, DEADLINE, and MEMO. Undated entries keep `startMillis == null`; date-only deadlines and date-span Memo remain stored unchanged. Repeated completion is keyed by the immutable original slot, and non-repeating items use `single`.
- `queryCurrentEntry(eventSeq, occurrenceKey, nowMillis, includeCompleted)` re-reads the current owner through the adapter and returns a DTO only if the event remains enabled, tracked, and within the same projection window. The Web Host compares the current revision and completion state before calling the existing Event completion command, which performs the final transactional validation.
- Native presentation and compiled runtime renderers receive `MemoEntryV1` values and opaque row-key callbacks. The Web state projection keeps its existing fields and Host-controlled 250-entry paging. The query still uses the full formal-event snapshot; Store-level paging is not part of this API slice.
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
- Native Deck's built-in controls remain Host-driven. Runtime/imported Deck callbacks that request a formal commit, conflict replacement, or restore first open Host-owned confirmation UI. Commit/restore review displays the exact candidate's type, title, date/time, zone, location, notes, recurrence, reminder, enable/tracking state, and completed instances; conflict review adds the current revision and field differences. The UI checks its captured Deck state revision before calling the API.
- Draft/Review request receipts are retained in a bounded, process-local 128-entry cache. Durable request replay across adapter recreation/process death is not provided; do not describe the receipt cache as exactly-once persistence.
- Explicit user-owned text capture creates a draft through `DraftReviewFeatureApiV1.createDraft`. It accepts an `EventFormV1`, rejects recurrence-edit scope fields, uses the Store's existing user draft transaction and AI lock, and returns the new draft identity/version. The adapter snapshots collection fields before queueing and fingerprints them canonically for request replay. The Host opens the returned draft ID. AI proposal generation and batch control use `AiFeatureApiV1`; proposal staging/application remains on `AiProposalFeatureApiV1`.

These are Calendar, Event, Memo, Draft/Review, AI Proposal/Feature, Reminder, Import/Export, and Settings/Data vertical slices, not full Phase 1 closure. Native and Web Deck formal searches use `searchEvents`, both Memo completion Host paths use `setOccurrenceCompletion`, Web event-open prevalidation and AI proposal target capture use `EventFeatureApiV1.queryEvent`, Memo projection and current-occurrence visibility use `MemoFeatureApiV1`, reviewed proposal actions use `aiProposals`, AI generation and batch commands use `ai`, and Host reminder diagnostics/recovery use `reminders`. The compiled Draft Deck renderer now receives `DraftReviewSnapshotV1`, `EventFormV1`, and `EventKindV1` through `DeckVisualContext`; its Host retains Core conversion, editing, and approval. Other runtime projections and authorized workflows still need reconciliation. Other domain APIs remain outside this coverage.

## 9. AI Proposal API v1 slice

`CoreFeatureApiV1.aiProposals` exposes the platform-neutral `AiProposalFeatureApiV1` contract. Native and Web review paths map the Host-owned `AiInterpretation` into detached API DTOs and call the same Store adapter. The API returns typed per-item outcomes and does not expose `PowerStore`, `AiDraftService`, or Host approval issuance through `:core-api`.

- `createDrafts` stages explicit user-reviewed proposals and never publishes a formal event. Existing target revisions are checked before staging, duplicate target operations in one review are blocked, and an existing human draft is preserved. Its no-task outer request replay cache remains process-local.
- `applyReviewedProposals` acts only on selected review positions. `PUBLISH` requires a one-use Host-issued approval bound to the exact request fingerprint. Existing-target operations carry the reviewed revision; the Store checks it again inside the SQLite transaction. Pending task-backed drafts must still match the reviewed form and operation, and commits use the reviewed draft version and target revision.
- Per-item replay uses the existing `ai_task_receipts` table. Conversation tasks use their task/revision identity. Taskless API applies use an internal, deterministic task namespace derived from the request ID, so exact retries after API recreation reuse the original staged or committed item. The outer API request cache is still process-local. No database schema or backup format changed.
- Expired task requests may replay exact existing receipts. Staging can reuse an exact existing item receipt; publishing additionally requires a terminal receipt, enforced again inside the Store transaction. An expired request cannot create a missing receipt. Target revision and receipt fingerprint checks remain inside the transaction where required.
- Store change snapshots are captured while the Store monitor is held; feature changes are prepared without callbacks, then listeners run only after both the Store monitor and proposal mutation lock are released.
- Structured proposal generation and AI batch selection, state, cancellation, recovery, reviewed execution, and stuck-session release now route through `CoreFeatureApiV1.ai`. Proposal staging/application still uses `aiProposals`. The general chat reply, image/OCR and file intake, provider configuration/credentials, and broader tool orchestration remain Host-owned and are not claimed as complete Core APIs.

## 10. AI Feature API v1 slice

`CoreFeatureApiV1.ai` exposes the pure Kotlin `AiFeatureApiV1` contract. The Android Host supplies `AndroidAiModelPortV1`, which loads the existing protected AI settings and credentials and calls the existing `AiClient`; credentials and Android context do not enter `:core-api` DTOs.

- `generateProposal` accepts one text request or a validated conversation-task snapshot and returns detached, reviewable proposal DTOs only. The adapter supplies bounded calendar context under the existing persisted-sharing preference and request opt-in, and binds target revisions from the trusted local snapshot. Generation does not create drafts or publish events; the Host passes reviewed output to `aiProposals` for staging or application.
- `selectBatchDrafts` gives the model bounded local candidate snapshots. The model returns IDs only; the feature API resolves them against the exact supplied IDs, versions, and operations, rejecting duplicates, out-of-scope IDs, and over-limit selection. The explicit “all” path is a Host-set flag and skips model selection.
- `runReviewedBatch` consumes a one-use Host approval bound by object identity to the exact mode and reviewed candidate snapshots. The Store rechecks draft versions and allowed operations when it creates the existing fenced AI session; `AiBatchRunner` retains per-item transactional commits and cancellation behavior. Cancellation, interrupted recovery, and stuck-session release use the same API, with release still requiring the existing stale-session eligibility and a second explicit Host approval.
- Queried session state and persisted terminal reports use typed DTOs. Native/Web presentation projections omit session identity, lock epoch, draft scope IDs, and detailed stored failure text. Host execution receipts retain the existing detailed result for Host feedback.
- Approval markers and the outer generation request have no durable replay guarantee. The batch workflow continues to rely on Store-backed session fencing and transactions. No schema, backup format, or frozen business semantics changed.
- This slice does not move ordinary AI chat, image/OCR transport, document/file selection and consent, provider configuration, or the general tool registry/orchestrator into Core.

The pure Kotlin contract is `AiFeatureApiV1.kt`; the Android provider adapter and Store-backed implementation are `AndroidAiModelPortV1.kt` and `StoreAiFeatureApiV1.kt`. Scoped verification and device limits are in the [AI Feature API Issue](issues/PC-ISSUE-CORE-AI-FEATURE-API-V1.md).

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

- ICS parsing stays local. `prepareIcsImport` delegates to the existing bounded `IcsImportPlanner`, returning typed candidate forms and unresolved untrusted evidence. Duplicate matching checks formal records, pending CREATE drafts, and earlier candidates in the same plan. `createIcsDraft` delegates to `DraftReviewFeatureApiV1.createDraft`; ICS input therefore remains reviewable and does not publish formal events. The existing `ImportCheckpoint` remains a SharedPreferences recovery hint and stores only the fingerprint and draft IDs.
- System Calendar provider reads and permission prompts stay in Android Host code. Each imported event is sent through the API's begin/import/finish batch commands, which delegate to the existing `PowerStore` migration and `importStructured` transactions. Source-link idempotence and imported-alarm handoff rules remain Store-owned. Newly imported records publish `FORMAL_CREATED`; retries that find an existing source link do not publish a second change.
- ICS export takes a fresh Store snapshot through the adapter and calls the existing `IcsCodec.exportActivities`; it remains an Activity-only, intentionally lossy format. Existing user disclosures remain in the Host.
- Backup export, bounded readback verification, and restore delegate to `PowerStore.exportBackup`, `BackupEnvelope.verifyReadback`, and `PowerStore.restoreBackup`. The API returns the envelope as an opaque UTF-8 JSON document for trusted Host file I/O and enforces the existing 64 MiB bound. Hash/reference checks, the empty-target transactional restore, and the explicit legacy-v2 consent gate are unchanged. Successful restore publishes `STORE_RESTORED`; reminder reconciliation and UI refresh remain Host work.
- The API adds no schema or backup-format changes and does not expose Android, Store, or Core entity types in `:core-api`. It does not send ICS evidence to an AI provider.

Implementation is in `ImportExportFeatureApiV1.kt` and `StoreImportExportFeatureApiV1.kt`. Android picker/SAF and device interoperability evidence remains separately gated; see the [Import/Export API Issue](issues/PC-ISSUE-CORE-IMPORT-EXPORT-API-V1.md).

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

## 14. Current adapter coverage and open work

Verified code paths across the Calendar, Event, Memo, Draft/Review, AI Proposal, Reminder, Import/Export, and Settings/Data slices:

- Native profile save, Web profile open/save, and the first-run system Calendar import prompt use the shared `SettingsDataFeatureApiV1`; the Web Host still applies its scoped editor gate.
- Native event creation and ordinary formal editing/deletion call the Host's shared `EventFeatureApiV1`.
- `PowerCalApp.reload` obtains formal events through `queryEvents()`.
- Native and Web Deck formal search use `searchEvents` over an API query snapshot. The Native Host reuses its latest snapshot so typing does not synchronously read the Store; the Web Host queries and filters on its I/O dispatcher.
- Native and Web Memo completion commands use `setOccurrenceCompletion`, with expected event revision and expected prior completion state. Only committed changes publish `FORMAL_UPDATED`.
- Web event-open prevalidation and AI proposal target capture use `EventFeatureApiV1.queryEvent`; the editor open operation rechecks current revision and occurrence before creating its session.
- Native, Web, and runtime Memo projections use `MemoFeatureApiV1` stable DTOs. The Web current-occurrence visibility precheck is API-owned, and runtime callbacks resolve opaque row keys in the Host.
- Explicit text-to-draft capture uses `DraftReviewFeatureApiV1.createDraft`; successful persistence publishes one `DRAFTS_CHANGED` event and returns the draft ID, event sequence, and version.
- Web event editor create/update/checkpoint/save/delete and recurrence scope use the same API instance.
- Web timeline movement reads a Host projection, then stages the revision-guarded replacement through `stageFormalReplacement`.
- Native and Web Draft Decks use the shared `DraftReviewFeatureApiV1` for persisted draft edits, queue navigation, formal replacement proposals, conflict and restore review, commit, discard, and undo.
- Compiled Compose Draft Deck renderers receive the versioned `DraftReviewSnapshotV1`, `EventFormV1`, and `EventKindV1` DTOs through `DeckVisualContext`; Host callbacks retain Core conversion, validation, writes, and approvals. Native fallback and protected conflict/restore handling remain available.
- Native and Web AI proposal review actions use `AiProposalFeatureApiV1` for staging and reviewed publication, including Host-owned publish approval and per-item Store receipts.
- Manual structured generation, automatic conversation-task interpretation, and file/image-derived proposal generation use `AiFeatureApiV1.generateProposal`; generation returns candidates only. AI batch candidate selection, state, cancellation, interrupted recovery, reviewed execution, and explicit stuck release also use `ai`; model output cannot issue approval.
- Native and Web Host reminder diagnostics and reconciliation use the shared `ReminderFeatureApiV1`; bounded lifecycle recovery and exhaustive user-requested recovery remain distinct modes.
- Native and Web Host timeline/month reads use `CalendarFeatureApiV1` over an immutable Event API snapshot. Native month navigation asks the Host for a fresh bounded query per month; Web synchronous state refreshes select data for the live window.
- Native and Web ICS import/export, structured Calendar import, and native backup export/restore now call `ImportExportFeatureApiV1`. ICS draft persistence delegates to Draft/Review; structured imports retain Store batch receipts and source-link idempotence; successful backup restore emits the shared `STORE_RESTORED` change.
- A Robolectric adapter integration test performs a Native-side API create followed by a Web bridge create/checkpoint and observes shared feature revision/change events and Store-backed state. Its Native-side write calls the API directly; it does not mount `PowerCalApp` and drive the entire Native UI route. Separate editor Compose tests cover the API-backed editor component callbacks.

Remaining work before PHASE 1 can exit:

1. Route remaining authorized Host/Core reads and writes through versioned queries/commands and one change source; settings/data operations outside the profile/onboarding slice and other Host workflows remain outside the migrated paths.
2. Move remaining business decisions out of Web transport/Host adapters; keep the bridge focused on transport and trusted Host capabilities.
3. Remove remaining Core entity types from runtime Skin projections while maintaining the separate Native Presentation Contract. `DeckVisualContext` no longer exposes `DeckController.DeckState`, `DraftForm`, or `TemporalKind`; `MonthVisualContext` and `AiVisualContext` still have Core-model references.
4. Add the remaining per-domain API contracts before claiming other feature areas are covered. Current v1 does not define Store-level Calendar paging. Draft/Review request replay and AI proposal-generation request replay remain process-local; AI Proposal item receipts are Store-backed. Calendar and Memo queries still use the full Host event snapshot.
5. Run required device checks when the independently owned AVD is available. Memo, Event search/completion/current-event query, AI Proposal, and Draft Deck projection device behavior are **NOT TESTED**.

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

Latest Event current-record query v1 verification: Event API/Web AI/Web event-open suites **28/28 PASS** (6 + 15 + 7); forced serial offline gate **845/845 JVM tests across 134 XML suites, 0 failures/errors/skips**. Both source boundaries, Debug APK assembly, lint (**0 errors / 53 warnings**), AndroidTest Kotlin compilation, and `git diff --check` passed. APK **66,847,342 bytes**, SHA-256 `9C556FA6611FBCD5BF7B1C7CCFD139CDCC17EB722027B98FF4417303D2336A5A`. Focused/full log SHA-256 `C2B81E4DB5A8F3B7353C4CD52CCE98B4F1AEE962279B189C75EC32D04C707B6F` / `73D9260DA774AA4E4FF5A88A09065E91836E801BB15A1DDB29F7FE6959218495`; logs are under `.local/android-test/reports/core-event-query-api-20261101/`. Device instrumentation and packaged WebView behavior remain **NOT TESTED**; see the [Event current query Issue](issues/PC-ISSUE-CORE-EVENT-QUERY-API-V1.md).
