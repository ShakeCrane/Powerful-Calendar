# PowerCal v3 host-side extension — implementation contract

This is an **additive** extension of .pcskin v3 / WebMessagePort v1. No ZIP schema
bump, UI source rewrite, new permission or automatic publishing authority.

## State

The host pushes a complete `{version:1,type:"state",payload:{...}}` frame on
each relevant Compose state update (and once on connection). Existing fields
remain valid: `bridgeVersion`, `page`, `aiLocked`, `pendingCount`,
`reducedMotion`, and `events`. `hostDataReady` is true only after the initial durable
Store snapshot and its initial projections (including Memo entries) have loaded, even
when the Store is empty. Before that frame, a skin should queue outbound actions and
commands rather than treating an empty projection as the loaded calendar.

New:
- `optionGroups`: host-owned semantic choices; render every option of a group
  with the same style. Each item carries `id,label,detail,icon,enabled`.
  Skins may render these groups in their Settings page as well as a separate
  data-options page. Invoke an item only with its registered `invokeOption`
  ID; file pickers, system permissions, migration and restore confirmations
  remain Host-owned. Keep a native recovery route available if the skin fails.
- `skinChoices` and `selectedSkinId` are populated only on `page="SETTINGS"`.
  Each choice contains only its installed catalogue `id`, display `name`, and
  `builtIn` flag. Outside Settings the list is empty and the selected ID is null;
  skin package paths, manifest contents, credentials, and preference storage are
  never projected.
- `timeline`: `zoneId`, `anchorMillis`, `hours`,
  `visibleStartMillis`, `visibleEndMillis`, `nowMillis`,
  `itemLimit` (300), `truncated`, and `items`.
- Each timeline item: `eventSeq`, `revision`, `occurrenceKey`,
  `originalOccurrenceKey` (null for a non-repeating item),
  `kind`, `title`, `side`, `lane`, `startMillis`,
  `endMillis`, `precision`, and `deadlineDate`.
- `startMillis` is the **displayed occurrence**, not merely the series
  origin. For date-only deadlines, inspect `precision=DATE` and
  `deadlineDate`; `endMillis` is null. Times are epoch milliseconds.
- `truncated=true` means not all visible items fit the bounded host
  projection. Do not infer that omitted occurrences are nonexistent.
- `month` is present while the Month page is active and contains `zoneId`,
  `month` (`YYYY-MM`), `selectedDate` (`YYYY-MM-DD`), `gridFrom`, bounded
  `items`, and `truncated`. The selected day and displayed month are Host-owned
  view state and are independent from the timeline camera. Month items are
  projected from actual host occurrences; a skin must not synthesize missing
  recurrence occurrences.
- AI state includes `chatAttachmentAvailable` and `chatAttachmentBusy` booleans.
  Availability reflects the active AI page and Host edit/review locks; busy
  covers the system picker or a file/image review dialog. Neither field contains
  a URI, filename, MIME type or attachment bytes.
- AI chat state includes `chatMessages` (the latest 40 Host-owned messages;
  each row has a stable Host-local `messageId`, `role`, and content capped at
  1,600 characters), `chatHistoryRevision`, and
  `chatHistoryActionsAvailable`. Clear and rewind requests must echo the current
  revision and Host-projected message ID. The revision advances only when the
  history actually changes; stale requests and no-op mutations do not rewrite
  the history. `chatPrefillText` plus `chatPrefillRevision` lets the Host request
  an intentional composer update; skins apply the text only when that revision
  changes so ordinary state renders preserve user typing. Only explicit
  edit-and-resend replaces the composer; clear and retract/assistant rewind
  preserve any unsent text. These fields expose no event or draft write
  authority.
- `aiBatch` is a read-only status projection with `phase` (`IDLE`,
  `SELECTING`, `AWAITING_HOST_REVIEW`, `RUNNING`, `CANCEL_REQUESTED`,
  `INTERRUPTED`, `COMPLETED`, `CANCELLED`, or `FAILED`), operation counts,
  aggregate scope/selection counts, cancellation/recovery affordances, optional
  live `progress` (`done` / `total`) while the matching Host session is active,
  and a sanitized terminal report. Progress is process-local, session-fenced,
  and cleared when the runner ends; it never contains session IDs, lock epochs, draft
  IDs, reviewed versions, or raw failure/exception text. Its presence does not
  authorize or perform a write. `canRelease` becomes true only after a batch is
  `CANCEL_REQUESTED` and its heartbeat has been stale for at least ten minutes.
  Reports mark `outcomeKnown`; an emergency release uses `false` and omits
  operation counts because the Host cannot infer whether an in-flight operation
  committed at the release boundary.
- The skin may place the ruler on the far left, move event lanes to the
  right, change icons and artwork, and draw decorations entirely itself.
  This never changes formal event times or stored recurrence.

## Actions

Actions must be posted on the transferred WebMessagePort with exact v1
envelope and known fields only:

```json
{"version":1,"type":"action","payload":{"name":"invokeOption","id":"calendar.import.file"}}
```
```json
{"version":1,"type":"action","payload":{"name":"selectSkin","id":"powercal-default"}}
```
```json
{"version":1,"type":"action","payload":{"name":"panTimeline","deltaMinutes":30}}
```
```json
{"version":1,"type":"action","payload":{"name":"zoomTimeline","direction":"in"}}
```
```json
{"version":1,"type":"action","payload":{"name":"resetTimelineZoom"}}
```
```json
{"version":1,"type":"action","payload":{"name":"jumpTimelineDay","date":"2026-10-22"}}
```
```json
{"version":1,"type":"action","payload":{"name":"selectMonthDate","date":"2026-10-23"}}
```
```json
{"version":1,"type":"action","payload":{"name":"selectMonth","month":"2027-04"}}
```
```json
{"version":1,"type":"action","payload":{"name":"pickAiFile"}}
```
```json
{"version":1,"type":"action","payload":{"name":"pickAiImage"}}
```
```json
{"version":1,"type":"action","payload":{"name":"clearChat","historyRevision":12}}
```
```json
{"version":1,"type":"action","payload":{"name":"rewindChat","messageId":"host-message-id","historyRevision":12,"editAndResend":true}}
```
```json
{"version":1,"type":"action","payload":{"name":"loadMoreMemo"}}
```

```json
{"version":1,"type":"action","payload":{"name":"prepareAiBatch","mode":"publish"}}
```
```json
{"version":1,"type":"action","payload":{"name":"cancelAiBatch"}}
```
```json
{"version":1,"type":"action","payload":{"name":"releaseStuckAiBatch"}}
```

- `panTimeline` accepts nonzero integer minutes [-2880, 2880]. It does not
  change the time span or introduce horizontal zoom.
- `zoomTimeline` accepts only `direction: "in"` or `"out"`, with no additional
  payload fields. It changes the vertical span by the same 1.5 factor as the
  Native zoom controls, clamped by `TimelineCoordinate` to 0.25–48 hours; the
  camera's center instant is unchanged. `resetTimelineZoom` accepts only its
  name and restores a 24-hour span while retaining that center. Both actions
  are ignored outside the active timeline page and write no calendar data.
  On the timeline page, `today` uses the configured zone's current date,
  centers local noon and resets to 24 hours. On the Month page it selects the
  current local date and month without leaving that page or changing the
  timeline camera, matching the Native Month control.
- `jumpTimelineDay` accepts an ISO date from years 1900–2200 and retains
  the existing camera span.
- On the Month page, `selectMonthDate` accepts a valid ISO date in the currently
  displayed month (1900–2100) and changes only the selected-day projection.
  `selectMonth` accepts `YYYY-MM` in years 1900–2100 and selects that month's
  first day. `browseMonth` navigates relative to the displayed month and also
  selects its first day, matching Native arrows and month-picker behavior.
  These actions do not change the timeline camera or write calendar data.
  Creating an event from the Month page uses the selected local date captured
  by the Host; the skin does not submit a timestamp.
- Existing navigation, the additive `memo` page action, and
  `openEvent(eventSeq,expectedRevision[,occurrenceKey])` remain. Skins pass the
  projected `originalOccurrenceKey` as `occurrenceKey` when opening a repeating
  occurrence; legacy actions may omit it. The Host re-reads the current owner,
  checks revision/writer availability, verifies any supplied key belongs to
  that repeating owner, and forwards it to `EditSession`. A key cannot bypass
  those checks.
- `invokeOption` only accepts registered host IDs. When AI/editor writes
  are locked, option rows are disabled in state and invocation is denied.
  Rendering the same projection in Settings does not grant a new capability or
  bypass the Host action checks and confirmation flows.
- `selectSkin` accepts exactly `name` and an installed catalogue `id`, only on
  Settings. The Host resolves that ID against its current installed catalogue,
  ignores the currently selected skin, and opens the existing trusted activation
  confirmation. Confirmation still runs the Host editor checkpoint and package
  validation; cancellation does not change selection. The action cannot submit a
  path or package bytes, and it does not write calendar records. Preview Mock
  confirmation is simulated and cannot activate an Android skin.
- `data.legacy.migrate` is a Host-owned migration request. It opens the existing
  native confirmation before accessing the legacy database; the importer opens
  that source read-only, keeps the file, and records a finished migration batch
  even when the source has no `events` table. A skin cannot trigger migration
  without that confirmation.
- Existing `editor.patch` and `deck.patch` accept the semantic field `kind`
  with exactly one of `ALARM`, `ACTIVITY`, `DEADLINE`, or `MEMO`. The Host
  applies the frozen `TemporalConversion` rules and returns the updated form;
  an unsupported kind is `invalid`. This changes only the host-owned editing
  session or draft form. Formal publication still requires the existing
  trusted Host review and revalidation. A `SINGLE_OCCURRENCE` editor rejects
  kind changes and permits only its established title/start/end fields.
  Clear's formal-event and draft editors expose the four kinds through this
  existing patch; the selector is omitted for `SINGLE_OCCURRENCE`. Studio's
  conversion/confirmation flow is a non-writing Preview Mock, not Android
  Host approval or WebView evidence.
- `editor.delete` accepts exactly `sessionId` and only requests deletion from
  the active host-owned formal editor. It always returns `confirmation_required`
  before a write; the Android Host reviews the captured event/form snapshot,
  rechecks the active session and formal record revision, then calls the existing
  `EditSession.delete()`. Cancelling writes nothing. A repeated confirmed command
  is replay-safe. Deleting a repeating formal event retires the full series and
  its occurrences, consistent with the existing Native `deleteFormalEvent` path.
  The official Clear editor exposes this request as “删除正式事件”, or
  “删除整个重复系列” when the current form repeats. The recurrence label and
  adjacent warning communicate the whole-series effect; both controls route only
  the current session ID and never write from the skin. Skin Studio's matching
  confirmation is a non-writing preview Mock, not Android authorization.
- `prepareAiBatch` accepts exactly `name` plus `mode: "publish" | "discard"`.
  It only starts a Host-owned range-selection conversation. A skin command or
  range-selection answer does not authorize the batch. Selected snapshots are
  shown in the trusted Host review; only its explicit confirmation starts the
  existing Store transaction and runner. Batch range selection and AI proposal
  review are mutually exclusive; the Host rejects a batch request while a
  proposal review is active, and Clear disables both sets of conflicting
  controls.
- `cancelAiBatch` accepts only `name`. During range selection it abandons
  that transient selection without writes; during a running batch it requests
  an idempotent stop. Completed operations remain committed and unfinished
  drafts remain pending for a fresh review.
- `releaseStuckAiBatch` accepts only `name` and opens a trusted Host
  confirmation only for a `CANCEL_REQUESTED` batch whose heartbeat has been
  stale for at least ten minutes. The Host binds that prompt to the exact
  session ID and epoch; the Store transaction checks the active epoch, state,
  and timeout again. Skin code cannot provide or choose the binding IDs. A
  successful emergency release fences old authorization and reports its
  operation counts as unknown. Startup recovery marks a batch owned by an
  earlier process `INTERRUPTED`, fences its epoch and leaves unfinished drafts
  pending.
- Studio Preview models these interactions with non-writing Mock state. It is
  not evidence for Android WebView-to-Host transport, Store authorization or
  real batch writes.
- `pickAiFile` and `pickAiImage` accept only the `name` field and only while
  the AI page is active and no conflicting review, edit or picker is pending.
  They launch the existing Android Host picker. The file path retains local
  ICS detection/import and bounded text extraction with its existing consent
  review; the image path retains the existing image/OCR review. URI and bytes
  never enter a skin projection. These requests do not create drafts or events.
- `clearChat` accepts exactly `name` and the current nonnegative
  `historyRevision`. `rewindChat` accepts exactly `name`, `messageId`,
  `historyRevision`, and boolean `editAndResend`. For a user message,
  `editAndResend=true` removes that message and all later turns, then asks the
  skin to prefill the original text; `false` retracts that message and later
  turns. For an assistant message, `false` keeps the selected reply and removes
  later turns; `true` is rejected. Missing, duplicate, stale, or otherwise
  invalid targets do not mutate history. Clearing an already empty history and
  rewinding the final assistant reply are no-ops. These Host actions only update
  the app-private chat history; they do not create or alter formal events,
  drafts, commit records, event identities, or the current unsent composer text.
  They are unavailable while a conflicting editor, batch operation, picker, or
  attachment review is active.
  Studio Preview covers these flows with a non-writing Mock; it does not prove
  production Android WebView transport.
- Memo list state is projected from the complete, deterministically sorted
  Native `TodoProjection` result within its existing recurrence window. On the
  `memo` navigation action, the Host refreshes its current formal-record
  snapshot. On the Memo page, it initially sends a prefix of at most 250 rows in
  `memoEntries`, plus `memoTruncated` and `memoRemainingCount`. The no-argument
  `loadMoreMemo` action requests the next Host-controlled page of up to 250
  rows. The Host rebuilds the same sorted prefix from current records, so a
  changed list cannot cause cursor duplicates or skipped rows. It is accepted
  only while the Memo page is active and does not write calendar data. Outside
  that page, `memoEntries` is empty, `memoTruncated` is false, and
  `memoRemainingCount` is zero. `memoCount` continues to report the full
  incomplete count in the existing projection window.
- host.read gates state projections. host.action gates every inbound action and
  command message, including editor/deck commands. This keeps existing
  action-only v3 packages working.
- host.edit remains accepted and preserved by v3 tooling for package
  compatibility, but is inert in Bridge v1 and cannot grant access alone.
  Skin Studio must not describe it as an effective permission. Assigning it
  independent authority requires a versioned capability policy and migration
  tests.
- A capability allows a request to reach Host validation; it does not approve
  a formal write. Sensitive writes continue through trusted Host confirmation.
  Old v3 skins with hard-coded menus continue to load. They will not
  automatically display future sibling options without rendering the
  shared group.

## Draft deck search and navigation (2026-10-29)

The existing v1 edit-command envelope is unchanged. These exact-schema commands
extend the Host-owned persistent draft deck; they do not grant the skin direct
Store access or formal-write authority:

```json
{"version":1,"type":"command","payload":{"name":"deck.search","requestId":"clear_abc123_1","query":"planning"}}
```
```json
{"version":1,"type":"command","payload":{"name":"deck.jump","requestId":"clear_abc123_2","steps":1}}
```
```json
{"version":1,"type":"command","payload":{"name":"deck.proposeReplace","requestId":"clear_abc123_3","eventSeq":42,"expectedRevision":3}}
```

- `deck.search` accepts only a string `query` of at most 120 UTF-16 code units
  with no ISO control characters. It is available only while a persistent deck
  is open; a transient `deck.create` session returns `unsupported`. The Host
  uses the existing `DraftQueueSearch` and `FormalEventSearch` functions. A
  blank query projects the ordinary queue. A nonblank query projects at most
  four draft cards and four formal records; an unfiltered queue projects at
  most 80 cards. The state reports `searchQuery`, `searchTruncated`, `truncated`,
  total `cardCount`, one-based `position`, `canGoPrevious`, `canGoNext`, and
  bounded `formalMatches` (`eventSeq,revision,title,kind`). Search writes no
  calendar data.
- `deck.jump` accepts only integer `steps` from −1000 through 1000. The Host
  delegates to `DeckController.jump`, which flushes the current form before
  changing the selected card. Navigation does not change persistent
  `queueOrder`; this command does not implement queue reordering. Results use
  the existing `moved`, `locked`, `missing`, `unsupported` and `failed` statuses.
- `deck.proposeReplace` accepts only its exact integer `eventSeq` and
  `expectedRevision` fields, and only proceeds for a positive target/revision
  pair in the current bounded formal search results. The Host re-reads the
  formal event and rechecks its revision and
  writer lock. It reuses an existing compatible `REPLACE` / `CONVERT` draft or
  asks the existing `PowerStore.proposeReplace` path to create one, then opens
  that draft in the Host-owned deck. It never modifies or publishes the formal
  event. A stale result is rejected; a successful request returns `opened`
  with the draft ID. Formal publication remains subject to the existing trusted
  Host review and current-record checks.
- Persistent deck state includes `transient:false`; a new in-memory editor
  opened through `deck.create` includes `transient:true`. Clear hides persisted
  deck search, card selection and navigation for the transient editor. This
  marker is descriptive state, not an authorization token.
- Persistent deck state also projects `completion:null|{published,discarded,conflicts}`
  and `notice`. A completion summary is emitted only after the
  current deck session empties the queue; it is presentation state, not
  a durable receipt. `lastDiscardId` and `deck.undo` are session-scoped, matching
  Native `DeckController.open()` behavior. The discarded draft row remains in
  Store across restart, but reopening a deck clears that session's undo affordance.
- All commands remain behind the existing `host.action` gate; projections
  remain behind `host.read`. Clear source and CSS are editable in Skin Studio.
  Studio Preview implements these commands against seeded, non-writing Mock
  data; its results demonstrate UI command wiring only, not Android Store or
  Android WebView behavior.

## Conflict review and deleted-event recovery (2026-10-29)

The v1 command envelope also exposes read-only tombstone browsing and actions
that request, but never supply, trusted formal-write approval:

```json
{"version":1,"type":"command","payload":{"name":"deck.retired","requestId":"clear_abc123_4","offset":0}}
```
```json
{"version":1,"type":"command","payload":{"name":"deck.beginRestore","requestId":"clear_abc123_5","eventSeq":42,"expectedRevision":4}}
```
```json
{"version":1,"type":"command","payload":{"name":"deck.requestConflictReview","requestId":"clear_abc123_6","draftId":9,"draftVersion":2,"eventSeq":42,"expectedRevision":5}}
```

- `deck.retired` accepts only an integer offset in `0..1,000,000`. The Host
  reads one page of at most 50 deleted formal records, ordered by most recent
  update and then descending event sequence. State returns `retiredResults`
  (`eventSeq,revision,title,kind,startText,endText`), `retiredOffset`,
  `retiredTotal`, page flags and `retiredLoaded`. It performs no writes. The
  Store API is additive and read-only; there is no database migration.
- `deck.beginRestore` accepts only a positive event sequence and revision that
  appear together in the current tombstone page. The Host re-reads the
  tombstone, checks that it is still retired, checkpoints the current card and
  delegates proposal creation to the existing `DeckController` /
  `PowerStore.proposeRestoreDraft` path. The event remains deleted; the command
  creates or resumes a reviewable draft and never allocates a new event
  identity or commits a formal event.
- Persistent deck state exposes `conflict:null|object` and
  `restore:null|object`, including draft identity/version, target identity and
  revision, the current/tombstone form, proposed form and readable field
  changes. These are display snapshots, not authorization credentials.
- `deck.keepConflict` and `deck.keepRestore` only dismiss their respective
  prompts while retaining the actionable draft. `deck.requestConflictReview`
  and `deck.requestRestoreReview` accept only the exact draft/version/event/
  revision tuple shown in state. They open a private Android Compose review;
  no `approved` field, JavaScript callback or Mock result can approve the
  operation. Conflict replacement and restore approval re-read the persisted
  draft and live formal/tombstone snapshot immediately before delegating to
  the existing controller/store transaction. Conflict replacement also sends
  the revision shown by Compose into `PowerStore.commit`; the SQLite write
  transaction compares it with the then-current revision before accepting the
  stale replacement. This closes the race between Host preflight and commit.
  Cancelling the Compose review keeps both the proposal and its prompt open;
  the explicit Keep action closes the prompt while retaining the draft. A stale
  snapshot is rejected for renewed review.
- Clear exposes the full before/after conflict fields, keep and replacement
  requests, a paginated deleted-event picker, and the same-event recovery
  review/keep actions. Studio Preview fixtures are non-writing and verify only
  command wiring. They do not prove Android WebView or device behavior.

## Timeline new-event context

The bundled Clear timeline offers all four event kinds and sends the existing
deck.create command with only the selected kind. It does not send a time,
zone, event identity, or persistence instruction. When the active Host page is
the timeline, PowerCalApp captures its current camera and zone while handling
that command, snaps the camera anchor with the same TimelineCoordinate.snap
rule used by the native timeline menu, and supplies the resulting
EditorSelection.AtInstant to the existing transient NewEventEditSession.
This keeps the camera authoritative and prevents a skin from choosing an
arbitrary timestamp through the wire protocol.

The standard kind-specific precision remains owned by EditorDefaults:
Alarm and Activity receive a minute-precision time, Activity receives its
one-hour suggested end, date-only Deadline remains date-only unless an
explicit timeline instant selects exact-time mode, and Memo retains its
existing date semantics. Opening and closing without a meaningful edit still
does not persist a draft. Month, Memo, and other non-timeline creation paths
continue to use their existing selection/default behavior.

## Timeline move

Clear's timed event buttons support a long-press vertical drag. A plain click
continues to open the event editor. The skin sends the raw target instant and
the revision it rendered; it cannot choose the snap interval, time zone, edit
operation, or occurrence scope:

```json
{"version":1,"type":"command","payload":{"name":"timeline.move","requestId":"clear_abc123_1","eventSeq":44,"expectedRevision":3,"targetMillis":1792581900000}}
```

The Host accepts this only on the active timeline page and only when that
`eventSeq` and revision occur in the Host's current bounded timeline
projection. It re-reads the formal record, applies the Native
`TimelineCoordinate.snap(targetMillis, camera.hours, zone)` rule, and
atomically checks the displayed source revision while staging a
`DraftOperation.REPLACE` candidate through the existing store/deck. ACTIVITY
moves retain their elapsed duration; ALARM and exact-time DEADLINE move their
time anchor. The existing formal event is not changed or published. The Host
opens the resulting draft for review; any eventual formal write still follows
the established trusted review/commit path.

Repeating events are refused because this command does not carry an explicit
occurrence-edit scope. MEMO and date-only DEADLINE are not timed timeline move
targets. A target that snaps to the current anchor returns `unchanged` without
creating a draft. A missing/stale projection or owner revision returns
`stale`; an active editor, deck, or protected operation returns `locked`;
unsupported event/page cases return `unsupported`. Existing request-ID replay
protection makes retransmission idempotent. Skin Studio's Preview Host models
this command without persistent writes and is not Android WebView evidence.

## Sandboxing and lifecycle

The host still runs package HTML/CSS/JS entirely offline, from the exact
`https://skin.powercal.local/` namespace; no remote access, credentials,
PowerStore, arbitrary native function calls or file access.
The active page channel is disposed/replaced during reload; a stale channel
must not reach the host. State is a bounded projection, not a database view.
All settings, risky changes, confirmations and emergency recovery stay native.
Skin Studio's AI attachment preview only records a non-writing Mock request;
it does not open a system picker or read, upload or import a file.

## QA gates

- `:app:verifySkinSourceBoundary`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:lintDebug`
- Android instrumented WebView test needed for active-state broadcast,
  channel reloading/crashes, background/resume, reduced-motion behavior,
  touch gestures and screen readers. JVM tests do not substitute for
  device-level acceptance.

This document specifies current **host functionality**, not a claim that
the separate Skin Studio editor supports those bindings visually yet.
