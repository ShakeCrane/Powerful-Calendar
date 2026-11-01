# Core Feature API v1 — Event domain

- **Status:** implemented event pilot; PHASE 1 remains in progress.
- **Contract source:** `:core-api`, package `dev.powerfulcalendar.core.api.v1`, `CoreFeatureApiV1.kt`.
- **Runtime adapter:** `app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt`.
- **Updated:** 2026-11-01.

This document describes the implemented event API boundary. It does not declare full Core extraction, full event-domain coverage, runtime cutover, or Native/Skin parity. The Kotlin source is authoritative when a detail here drifts.

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

## 7. Current adapter coverage and open work

Verified code paths in this event pilot:

- Native event creation and ordinary formal editing/deletion call the Host's shared `EventFeatureApiV1`.
- `PowerCalApp.reload` obtains formal events through `queryEvents()`.
- Web event editor create/update/checkpoint/save/delete and recurrence scope use the same API instance.
- Web timeline movement reads a Host projection, then stages the revision-guarded replacement through `stageFormalReplacement`.
- A Robolectric adapter integration test performs a Native-side API create followed by a Web bridge create/checkpoint and observes shared feature revision/change events and Store-backed state. Its Native-side write calls the API directly; it does not mount `PowerCalApp` and drive the entire Native UI route. Separate editor Compose tests cover the API-backed editor component callbacks.

Remaining work before PHASE 1 can exit:

1. Route applicable event reads/writes in `WebSkinEditService` and Native Host through the feature API or a domain API, including Deck formal-search/replacement paths and direct completion updates; keep Draft/Review semantics in an explicit Core API rather than hiding them in transport code.
2. Ensure all formal mutations that must refresh Skin projections publish through one change source, or add a Store/Core event feed that also observes authorized internal workflows.
3. Move remaining business decisions out of Web transport/Host adapters; the bridge should translate transport and manage trusted Host capabilities only.
4. Keep `DraftForm`/Core type projections on the Host side and maintain the separate Native Presentation Contract.
5. Add the required per-domain API contracts before claiming other feature areas are covered. Current v1 does not define calendar paging, completion commands, full Draft/Review, AI, reminders, import/export, or Settings/Data APIs.
6. Run a clean full JVM suite, boundary/build/lint gates, and the required device checks when the independently owned AVD is available. Instrumentation for this slice is currently **NOT TESTED**.

Current test record: focused API, Native editor, and Web bridge suites passed **66/66**. The latest two forced full JVM runs each completed **789 tests with one failure** in `WebAiProposalReviewIntegrationTest`: one lacked its expected Host confirmation node, the other timed out in memo paging with `hostDataReady=false`. Each exact failing test passed alone; the entire integration class passed **15/15**. The cause of the full-suite-only failures is unknown, so the full-suite status is **NOT CLEAN**, not PASS. Source boundaries, Debug APK assembly, and separate `lintDebug` passed (0 lint errors, 54 warnings). See the current section at the top of `docs/agent-handoff/PROGRESS.md` for the latest gate run.
