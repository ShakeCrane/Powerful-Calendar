# PowerCal v3 host-side extension — implementation contract

This is an **additive** extension of .pcskin v3 / WebMessagePort v1. No ZIP schema
bump, UI source rewrite, new permission or automatic publishing authority.

## State

The host pushes a complete `{version:1,type:"state",payload:{...}}` frame on
each relevant Compose state update (and once on connection). Existing fields
remain valid: `bridgeVersion`, `page`, `aiLocked`, `pendingCount`,
`reducedMotion`, and `events`.

New:
- `optionGroups`: host-owned semantic choices; render every option of a group
  with the same style. Each item carries `id,label,detail,icon,enabled`.
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
{"version":1,"type":"action","payload":{"name":"loadMoreMemo"}}
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
- `pickAiFile` and `pickAiImage` accept only the `name` field and only while
  the AI page is active and no conflicting review, edit or picker is pending.
  They launch the existing Android Host picker. The file path retains local
  ICS detection/import and bounded text extraction with its existing consent
  review; the image path retains the existing image/OCR review. URI and bytes
  never enter a skin projection. These requests do not create drafts or events.
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
