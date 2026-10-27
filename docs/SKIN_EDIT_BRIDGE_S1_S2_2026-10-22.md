# Skin-First S1/S2: host-owned editing command foundation

> Historical 2026-10-22 checkpoint: the current `WebSkinEditBridge.kt` exposes additional
> bounded reminder, tracking and recurrence fields, and Clear's `web/edit.js` now sends
> editing commands. The narrower statements below describe that checkpoint, not current
> implementation. See `SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md` for the current audit.

## 2026-10-27 addition: recurring event scope and host review

The v1 command envelope remains unchanged. `editor.open` accepts its legacy
`eventSeq,expectedRevision` fields, or additionally `occurrenceKey` containing the
original local slot (`yyyy-MM-ddTHH:mm`). The Host re-reads the record, checks its
revision and validates that the key belongs to that repeating series. Timeline and
month state retain their old prefixed `occurrenceKey` and add `originalOccurrenceKey`
for a repeating instance; clients must not strip prefixes or infer an original slot
from a moved instance's displayed time.

`editor.scope` takes `sessionId,scope`, where scope is `SINGLE_OCCURRENCE`,
`THIS_AND_FUTURE` or `WHOLE_SERIES`. A legacy open without an occurrence can choose
only the whole series. `editor.discardChanges` takes `sessionId` and restores the
current scope baseline. The `editing.editor` projection adds `requiresRecurrenceScope`,
`canEditSelectedOccurrence`, `recurrenceScope` and `awaitingConfirmation`. A scope
must be chosen before patching a repeating event; single-instance patches are limited
to title and time. Scope cannot change while fields differ from the scope baseline.

Saving changed repeat-related content returns `confirmation_required`: the Host owns
a modal showing the original occurrence, scope and complete before/after fields.
Only the Host's confirmation resolves its private snapshot; there is no Web confirm
command or projected approval token. Cancel preserves input. The Host compares the
record and form again before calling the original `EditSession` save or atomic split;
an outdated snapshot returns conflict. The later result is published in
`editing.lastResult`. A skin change checkpoints the draft and cancels an outstanding
review. Repeating the same request returns its cached outcome.

Clear implements these controls in `web/edit.js`; Studio exposes that source and
provides explicitly labelled **mock** host approval buttons in its preview. Those
buttons never authorize Android writes. This addition does not claim that unrelated
ordinary edit/deck writes have a trusted Host confirmation or that all AI approval,
device accessibility and recovery cases are complete.

Date: 2026-10-22. Current milestone: **protocol and host coordination,
not complete Clear Web editor/deck UI and not factory cutover**.

## Architectural invariants

- Factory Clear and imported v3 skins share the same capability-gated
  WebMessagePort command handler. There are no skin-ID privilege branches.
- `WebSkinEditService` is remembered once by the PowerCal host and reuses
  its existing `DeckController(StoreDeckPersistence(store))`.
  There is no second queue or SQLite adapter created by the WebView.
- A formal Web event owns a host-side `EditSession`; only an opaque session
  ID reaches the skin, never the PowerStore lease token. A native active
  editor blocks opening a competing Web editing session.
- Existing v1 `type:action` messages still work; new write requests are
  additive `type:command` messages and receive an explicit
  `type:result` response with the same request ID.
- Request IDs are scoped to the host service, cached for the last 64
  completions. Replaying the same command returns its previous result;
  reusing the same ID for a different command is rejected.
- Requests have exact field sets, integer revisions, bounded content and
  typed `DraftForm` field patches. Only title, start/end text, notes and
  location may be modified at S1/S2. Provenance, time zone, recurrence,
  completion history, permissions and reminder settings are **not writable**
  via these commands.
- The host performs all transaction writes, not the skin. Storage,
  command sequencing, editor lease, AI locks and conflict detection
  remain in the original controllers.

## Wire format

All Web requests use the existing transferred WebMessagePort (offline
`https://skin.powercal.local/`, no network).

Example opening the host's formal editor:

    {"version":1,"type":"command","payload":
      {"name":"editor.open","requestId":"req_20261022_abcd",
       "eventSeq":42,"expectedRevision":3}}

Host response:

    {"version":1,"type":"result","payload":
      {"requestId":"req_20261022_abcd","status":"opened"}}

The existing v1 state frame gains `payload.editing` with
`editor`, `deck`, and `lastResult`. Snapshot changes trigger
the same state feed; skins should use a typed result for completion,
and state for current authoritative values.

### Commands implemented at this checkpoint

- `editor.open`: `eventSeq,expectedRevision`
- `editor.patch`: `sessionId,field,value`
- `editor.save`: `sessionId`
- `editor.close`: `sessionId` (checkpoint first; failure keeps lease)
- `deck.open`: `draftId` (nullable)
- `deck.create`: `kind` (ACTIVITY, ALARM, DEADLINE, MEMO)
- `deck.patch`: `draftId,draftVersion,field,value`
- `deck.select`: `draftId` (controller flushes before changing card)
- `deck.flush`, `deck.close`
- `deck.commit`, `deck.discard`: `draftId,draftVersion`
- `deck.undo`

Results use one of `opened,created,edited,selected,saved,flushed,closed,
committed,discarded,restored,stale,locked,invalid,missing,conflict,
restore_required,failed,duplicate_request_id`, plus an optional bounded
detail. No client should infer success from a state change alone.

### Deliberately not exposed

- `editor.delete`: formal deletion still requires protected confirmation.
- `deck.confirmConflict` and `deck.confirmRestore`: MUST be forbidden
  until the host can prove that the user reviewed an exact, fresh snapshot
  and a trusted confirmation surface authorizes it. A skin-generated
  `confirm=true` is not sufficient.
- Arbitrary form replace, raw `source`, recurrence, timezone or
  reminder mutation, dynamic database operations and AI batch approval.

## Lifecycle guarantees and remaining risks

- App background checkpoint uses the same host-owned edit service.
- Explicit skin replacement calls `prepareSkinChange()`: checkpoint
  formal edits, flush draft edits, release lease only after success.
  Failed persistence blocks normal skin replacement.
- Re-opening a deck already owned by the Web editor does not reset its
  in-memory dirty form. Switching cards is delegated to
  `DeckController.select()`, which refuses to lose pending text.
- Host deactivation invalidates old Web message channels. Delayed
  commands verify the original owner, active package and port before
  execution or acknowledgement.
- Fatal WebView recovery attempts to checkpoint before switching to
  the native safety interface.

**Important:** In this S1/S2 checkpoint the Clear HTML/CSS/JS has NOT
yet been wired to these new commands. The visible Clear edit/deck flows
still use existing native UI and are not finished Skin-First surfaces.
The field whitelist must be extended (with careful safe encoding and
tests) before an editor can claim full event parity. The next phase is
S3/S4, including the trusted confirmation flow for high-risk actions.

## QA

- Robolectric real-SQLite tests:
  `WebSkinEditBridgeTest` (closed schema, metadata preservation,
  direct-edit save+replay, draft stage+commit+replay,
  checkpoint/close, repeated open, safe skin switch).
- Run `gradlew :app:verifySkinSourceBoundary :app:testDebugUnitTest`.
- Android device testing of WebView channel reconnects, Android
  lifecycle, keyboard/focus and host failure recovery remains required.

The APK is **not built** during this checkpoint.
