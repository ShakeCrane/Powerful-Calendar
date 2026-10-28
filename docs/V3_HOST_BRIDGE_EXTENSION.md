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
- `month`: `zoneId`, `month` (`YYYY-MM`), `selectedDate` (ISO local date),
  `gridFrom`, `itemLimit` (350), `truncated`, and `items`. Month selection is
  host-owned presentation state, independent of the timeline camera.
- Each timeline item: `eventSeq`, `revision`, `occurrenceKey`,
  `kind`, `title`, `side`, `lane`, `startMillis`,
  `endMillis`, `precision`, and `deadlineDate`.
- `startMillis` is the **displayed occurrence**, not merely the series
  origin. For date-only deadlines, inspect `precision=DATE` and
  `deadlineDate`; `endMillis` is null. Times are epoch milliseconds.
- `truncated=true` means not all visible items fit the bounded host
  projection. Do not infer that omitted occurrences are nonexistent.
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
{"version":1,"type":"action","payload":{"name":"jumpTimelineDay","date":"2026-10-22"}}
```
```json
{"version":1,"type":"action","payload":{"name":"pickAiFile"}}
```
```json
{"version":1,"type":"action","payload":{"name":"pickAiImage"}}
```
```json
{"version":1,"type":"action","payload":{"name":"skinSettings"}}
```
```json
{"version":1,"type":"action","payload":{"name":"selectMonth","yearMonth":"2030-02"}}
```
```json
{"version":1,"type":"action","payload":{"name":"selectMonthDate","date":"2030-02-14"}}
```
```json
{"version":1,"type":"action","payload":{"name":"todayInMonth"}}
```

- `sendChat` accepts nonblank text up to 6000 UTF-16 code units, matching the Native composer. The 8192-unit message-frame limit still applies to the complete JSON envelope.
- `panTimeline` accepts nonzero integer minutes [-2880, 2880]. It does not
  change the time span or introduce horizontal zoom.
- `jumpTimelineDay` accepts an ISO date from years 1900–2200 and retains
  the existing camera span; from Month it is the explicit “view this date on
  Timeline” action and switches to the Timeline page.
- `browseMonth` accepts a nonzero integer month delta in [-12, 12].
  `selectMonth` accepts exactly `yearMonth` in canonical `YYYY-MM` form for
  years 1900–2100. Both operate only on the active Month page, select day 1 of
  the target month, and do not move or resize the Timeline camera.
- `selectMonthDate` accepts exactly an ISO `date` in years 1900–2100 and only
  selects a date inside the active displayed month. Selection is presentation
  state and performs no calendar write. `todayInMonth` selects the current
  local date using the Host clock and zone, without changing the Timeline
  camera. The general `today` action remains the separate local-noon,
  24-hour Timeline camera reset.
- `openEvent` accepts the existing `{eventSeq,expectedRevision}` payload and
  an optional canonical `originalOccurrenceKey` for a projected repeating
  slot. The Host re-reads the record/revision and verifies that the key is a
  generated slot before opening the existing scoped editor. This selects an
  editor target; it does not authorize a write.
- Existing navigation and the additive `memo` page action remain.
  Formal event opens re-read the current record and check revision/writer
  availability; an occurrence key cannot bypass those checks.
- `invokeOption` only accepts registered host IDs. When AI/editor writes
  are locked, option rows are disabled in state and invocation is denied.
  Registered options currently include the two bounded calendar imports,
  Activity-only `calendar.export.ics`, and
  `reminders.exactAlarmSettings`, `reminders.notificationPermission`, and
  `reminders.reconcile`. The latter actions only open the Android-owned
  permission flow or invoke the existing idempotent scheduler; pre-Android-13
  notification settings open the app's Android notification settings page, while
  Android 13+ uses the runtime permission request. A skin cannot
  grant a permission or select an event identity. The `reminderPermissions`
  state contains only `exactAlarmAllowed`, `notificationsAllowed`, and
  `notificationsRuntimePermissionRequired`, and is projected only on the
  `SETTINGS` page. Already-granted runtime permissions disable their request
  action; Host checks the live permission again before launch. On Android
  versions without a notification runtime permission, the skin can distinguish
  “not required” from an actual permission grant.
  ICS export uses the existing Host `CreateDocument` flow and exports Activity
  records only; the format cannot preserve other event kinds or completion state.
- The existing `settings` action continues to open the protected Android
  Host tools page for compatibility with v1 skins. The additive
  `skinSettings` action selects the skin-owned settings landing without
  leaving the Web skin. It receives the existing bounded `optionGroups`
  projection; event/timeline/month/chat collection projections are empty on
  that page. The bounded `data.backup.export` and `data.backup.restore` options
  only request the existing Android Host file pickers: backup JSON and content
  URIs are never projected to the skin. Exported backups are plaintext and
  sensitive; restore is accepted only by the existing Core empty-database and
  envelope validation checks, with the existing native legacy-consent dialog.
  The `skin.import` and `skin.exportClear` options reuse the Host-owned package
  picker and bundled-Clear exporter; install still verifies the archive and
  never activates it automatically. On this page only, `installedSkins`
  projects a bounded list of package ID, display name, built-in/current flags
  and whether actions are available. It never includes package paths or source.
  The closed-schema `selectSkin` and `deleteSkin` actions accept only `skinId`;
  the Host resolves it against the current installed catalogue. Selection keeps
  the existing trusted confirmation and `prepareSkinChange` checkpoint. Delete
  is limited to non-built-in packages and keeps the existing trusted delete
  confirmation. Studio previews all four operations as non-writing Mocks.
  Credentials and other sensitive settings remain in the Host tools page. Clear's AI
  “配置” and its settings landing's “打开宿主工具与凭据设置” continue to request
  the existing `settings` action.
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

## Sandboxing and lifecycle

The host still runs package HTML/CSS/JS entirely offline. Each WebView session
uses a fresh synthetic HTTPS origin under `<random>.skin.powercal.local`; only
that session's entrypoint and packaged resources are accepted. Cookies are
disabled and process-wide Service Worker settings deny file/content/network
access and reject worker requests. This prevents persistent worker state from
being shared between skin sessions. No remote access, credentials, PowerStore,
arbitrary native function calls or file access is exposed.
The active page channel is disposed/replaced during reload; a stale channel
must not reach the host. State is a bounded projection, not a database view.
Skins may own and freely present a settings landing page and may invoke only
bounded Host-projected actions. Credentials, Android permissions, backup/restore,
skin installation/management, risky changes, confirmations and emergency
recovery remain under trusted Host control. The compatibility `settings` action
opens the native Host tools page; `skinSettings` opens the skin-owned landing.
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
