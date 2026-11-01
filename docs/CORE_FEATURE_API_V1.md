# Core Feature API v1 — Event and Draft/Review slices

- **Status:** Event pilot and Draft/Review vertical slice implemented; PHASE 1 remains in progress.
- **Contract source:** `:core-api`, package `dev.powerfulcalendar.core.api.v1`, `CoreFeatureApiV1.kt` and `DraftReviewFeatureApiV1.kt`.
- **Runtime adapter:** `app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt`.
- **Updated:** 2026-11-01.

This document describes the implemented Event and Draft/Review API boundaries. It does not declare full Core extraction, full domain coverage, runtime cutover, or Native/Skin parity. The Kotlin source is authoritative when a detail here drifts.

## 1. Contract boundaries

The three contracts have separate jobs:

| Contract | Owner | Responsibility |
| --- | --- | --- |
| Core Feature API | `:core-api` | Versioned event operations, queries, DTOs, results, revisions, and change notifications |
| Native Presentation Contract | `:skin-api` | Native renderer inputs and callbacks; contains presentation models, not Core business services |
| Web Bridge Transport Contract | `WebEditWire` / `WebEditCommand` | Versioned JSON command decoding and Host reply transport; it is not the event business API |

```text
Native presentation ─┐
                     ├─ Host adapters ─ EventFeatureApiV1 ─ PowerCalCoreFeatureApiV1 ─ existing Core services/PowerStore
Web transport ───────┘       │
                             └─ owns confirmation UI, Android access, and transport/session projection
```

`:core-api` is a pure Kotlin JVM module. Its public contract contains no Android, Compose, `PowerStore`, repository, or internal Core entity types. `PowerCalApp` creates one `PowerCalCoreFeatureApiV1` per `PowerStore` and injects the same instance into the Native event callbacks and `WebSkinEditService`. The runtime adapter currently lives in the app module and delegates to existing Core services such as `NewEventEditSession`, `EditSession`, recurrence logic, and `PowerStore`; moving that implementation into a separately distributable Core runtime is not complete.

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

This is a Draft/Review vertical slice, not full Phase 1 closure. The Web bridge still performs some formal-event searches and Store reads, `DeckVisualContext` still projects Core types to runtime rendering, and direct authorized workflows outside the shared adapter do not publish to its change stream. Memo completion and the other domain APIs also remain outside this slice.

## 8. Current adapter coverage and open work

Verified code paths in this event pilot:

- Native event creation and ordinary formal editing/deletion call the Host's shared `EventFeatureApiV1`.
- `PowerCalApp.reload` obtains formal events through `queryEvents()`.
- Web event editor create/update/checkpoint/save/delete and recurrence scope use the same API instance.
- Web timeline movement reads a Host projection, then stages the revision-guarded replacement through `stageFormalReplacement`.
- A Robolectric adapter integration test performs a Native-side API create followed by a Web bridge create/checkpoint and observes shared feature revision/change events and Store-backed state. Its Native-side write calls the API directly; it does not mount `PowerCalApp` and drive the entire Native UI route. Separate editor Compose tests cover the API-backed editor component callbacks.

Remaining work before PHASE 1 can exit:

1. Route remaining formal-event reads/writes in `WebSkinEditService` and Native Host through a domain API, including formal search and direct completion updates.
2. Ensure all authorized formal mutations that refresh Skin projections publish through one change source, including workflows that currently bypass this adapter.
3. Move remaining business decisions out of Web transport/Host adapters; keep the bridge focused on transport and trusted Host capabilities.
4. Remove Core entity types from runtime Skin projections while maintaining the separate Native Presentation Contract.
5. Add the required per-domain API contracts before claiming other feature areas are covered. Current v1 does not define Calendar paging, completion, AI, reminders, import/export, or Settings/Data APIs. Draft/Review request replay remains process-local.
6. Run a clean full JVM suite, boundary/build/lint gates, and required device checks when the independently owned AVD is available. Instrumentation for this Draft/Review slice is **NOT TESTED**.

Latest Draft/Review verification: focused API, DeckController, Store restore, Host review UI, and Web bridge tests passed; the forced full JVM run passed **797 tests across 128 XML reports, 0 failures/errors/skips**. `verifySkinSourceBoundary`, `verifyNativeSkinBoundary`, Debug APK assembly, and `lintDebug` all passed; lint reported **0 errors / 54 warnings**. The Debug APK SHA-256 is `B1A4EC21E59461B35597CC08972627148457302252A3B0818967F315A9B552B6` (66,683,502 bytes). Instrumentation and AVD behavior are **NOT TESTED**. Logs and the prior Event pilot’s transient full-suite failures are recorded in the current [progress entry](agent-handoff/PROGRESS.md) and the [Draft/Review Issue](issues/PC-ISSUE-CORE-DRAFT-REVIEW-API-V1.md); no cause is asserted for those earlier failures.
