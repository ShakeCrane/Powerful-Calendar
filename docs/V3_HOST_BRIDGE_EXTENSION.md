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
  `kind`, `title`, `side`, `lane`, `startMillis`,
  `endMillis`, `precision`, and `deadlineDate`.
- `startMillis` is the **displayed occurrence**, not merely the series
  origin. For date-only deadlines, inspect `precision=DATE` and
  `deadlineDate`; `endMillis` is null. Times are epoch milliseconds.
- `truncated=true` means not all visible items fit the bounded host
  projection. Do not infer that omitted occurrences are nonexistent.
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

- `panTimeline` accepts nonzero integer minutes [-2880, 2880]. It does not
  change the time span or introduce horizontal zoom.
- `jumpTimelineDay` accepts an ISO date from years 1900–2200 and retains
  the existing camera span.
- Existing navigation, the additive `memo` page action, and `openEvent(eventSeq,expectedRevision)` remain.
  Formal event opens re-read the current record and check revision/writer
  availability; an occurrence key cannot bypass those checks.
- `invokeOption` only accepts registered host IDs. When AI/editor writes
  are locked, option rows are disabled in state and invocation is denied.
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

## Sandboxing and lifecycle

The host still runs package HTML/CSS/JS entirely offline, from the exact
`https://skin.powercal.local/` namespace; no remote access, credentials,
PowerStore, arbitrary native function calls or file access.
The active page channel is disposed/replaced during reload; a stale channel
must not reach the host. State is a bounded projection, not a database view.
All settings, risky changes, confirmations and emergency recovery stay native.

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
