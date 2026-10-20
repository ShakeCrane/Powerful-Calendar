# Powerful Calendar — UI Skin and Render Extension Contract v2

**Purpose:** a designer/Claude/other code agent should be able to replace virtually all
*visual decisions* while the PowerCal event, persistence, timing, AI and authorization
semantics remain correct. Skinning must be a supported interface, not large search-and-replace
edits to business code.

## 1. Two separate extension tiers

### A. Portable, user-importable `.pcskin` (implemented)

A non-executable ZIP described by [SKIN_PACK_V1.md](SKIN_PACK_V1.md). The app can import,
verify, select, persist, delete and fall back to the native style. A user or Claude can
produce this pack without compiling the app, but its power is **deliberately bounded**.

Current v1 controls:
- light and dark Material theme colors and semantic alarm/activity/deadline/now/grid colors;
- typography relative scale (0.85–1.20), native fonts only;
- general card/bar corner radii, point-marker line thickness;
- predeclared bitmap artwork slots (PNG/WebP) with SHA-256 integrity checks.
- bundled original glass theme exists permanently and can be exported as an editable template.

A .pcskin does **not** provide CSS, JavaScript, plugins, TTF loading, WebView script,
arbitrary animation programs, scheduling logic or database access. A novel screen layout
cannot be encoded in this v1 archive.

### B. Typed compiled Compose Renderers (design contract; NOT ALL IMPLEMENTED)

For genuinely arbitrary UI forms, the safe extension level is a **compiled renderer**
built and reviewed with the app source. It is not a dynamically loaded untrusted ZIP.
Designers should be able to replace the visuals through named slots without changing
domain models. The proposed stable API is:

```kotlin
// Conceptual ABI; must be implemented and tested before depending on it externally.
interface PowerCalVisualRenderer {
    val id: String
    val supportedAbi: Int // v2
    @Composable fun TimelineAxis(model: AxisRenderModel, events: List<TemporalProjection>)
    @Composable fun AlarmPoint(model: PointVisual, onClick: () -> Unit)
    @Composable fun ActivityInterval(model: IntervalVisual, onClick: () -> Unit)
    @Composable fun DeadlinePoint(model: PointVisual, onClick: () -> Unit)
    @Composable fun MonthGrid(model: MonthVisual, onSelect: (LocalDate) -> Unit)
    @Composable fun AiMessageBubble(model: ChatMessageVisual, onAction: (UiActionId) -> Unit)
    @Composable fun DraftCard(model: DraftCardVisual, onAction: (UiActionId) -> Unit)
    @Composable fun SettingsSection(model: SettingsSectionVisual)
}
```

The interface above is a **target design**; classes such as `AxisRenderModel` and
`UiActionId` are not yet shipped. Do not write an external agent prompt claiming the
interfaces are already available for compilation.

Renderer model fields should be **presentation-only**: dimensions in Dp, theme semantic
colors, visual state, text labels, accessibility semantics, screen density and a stable
occurrence presentation key. Pass only well-defined callbacks to the host; no store
instance, private API key, uncontrolled disk access or network client reaches a renderer.

## 2. Required visual slots

A theme should define all of these, with the app's original implementation as fallback:

| Area | Slot | Non-negotiable semantic behavior |
| --- | --- | --- |
| Global | BrandHeader, IconSet, Typography, Backdrop, SurfaceCard | Compose accessibility labels and touch targets |
| Axis | AxisLine, NowLine, DayDivider, HourTick, ZoomFeedback | Real timestamps, DST, anchored viewport |
| Left | AlarmPoint, AlarmStackCount | ALARM is a point, never an artificial duration rectangle |
| Right | ActivityInterval, DeadlinePoint, RightOverflowCount | Activity is an interval; deadline is a point |
| Gestures | AxisZoomFeedback, HorizontalRightScaleFeedback | Y pinch changes time; X pinch stretches right side from center axis |
| Calendar | YearSelector, MonthSelector, MonthGrid, DaySummary | Quick year/month selection and swipe-to-month |
| AI | ChatBubble, AgentStatus, ActionChip, ToolResult, AttachmentPreview | Actions are suggestions until validated by a Skill |
| Review | DraftDeckBackdrop, DraftCard, ConflictDiff, CommitFeedback | Draft identity and versioning preserved |
| Settings | ProfileCard, AIConfigCard, ImportCard, BackupCard, Diagnostics | Never expose secret text in logs or previews |
| Alerts | NotificationPreview, AlarmPreview, PermissionBlock | Reminder policy separate from event kind |

Assets must never turn “deadline” into a false interval or hide that a record is
not yet formally published. Every possible presentation includes semantic labels and
accessible fallback rendering. Slot names are design targets unless listed under v1.

### V1 bitmap identifiers actually supported

`TIMELINE_BACKGROUND`, `DECK_BACKDROP`, `MONTH_BACKGROUND`,
`COMPLETION_EMBLEM`, `BRAND_MARK`, `ALARM_MARKER`, `ACTIVITY_BADGE`,
`DEADLINE_MARKER`, `AI_AVATAR`, `SETTINGS_ICON`.

These are case sensitive; bitmap slots may use an original built-in fallback. No external
file may define arbitrary Kotlin classes or call into the host.

## 3. Layout and gesture architecture

Draw the central time ruler at the viewport's **fixed horizontal center**. Its vertical
coordinate is a wall-clock aware projection with one canonical conversion function
`TimelineCoordinate.yOf`; never invent a second mapping inside a theme.

- Left alarm lane is a fixed-width column of line/point markers with stack-count affordances.
- Only the right activity/deadline region scales horizontally.
- Pinch X/Y components are independent; a diagonal pinch may change BOTH right-side
  horizontal scale and temporal zoom. Do not route all diagonal pinches to a single axis.
- The right region always starts at a fixed gutter next to the central ruler; stretching
  flows **away from** the ruler, shrinking flows **toward** it.
- Overflow markers remain reachable near the ruler even when right-side visual columns
  extend beyond the viewport.
- **Hard geometry invariant (2026-10-21):** for an ACTIVITY the rendered
  rectangle's top edge is exactly TimelineCoordinate.yOf(startMillis);
  its bottom edge is exactly TimelineCoordinate.yOf(endMillis), subject only
  to clipping at the viewport boundaries. Never apply a minimum rectangle
  height or fake event duration to fit a label, icon, 48dp hit target or tooltip.
  If the interval is too short, hide text and use an independent, separate
  accessible inspection affordance. Subpixel intervals remain geometrically
  meaningful even when a display raster cannot distinguish both physical pixels.
- Each point marker retains its eventSeq and occurrence key; touching an event must not
  turn a recurring occurrence into a new formal record.
- Every renderer must support large font accessibility, landscape, system dark/light mode,
  reduced motion, high contrast and screen reader descriptions.

## 4. Declarative skin package format

The exact supported file format, value ranges, known keys, path restrictions and
SHA-256 verification are specified in [SKIN_PACK_V1.md](SKIN_PACK_V1.md).
Unknown ABI versions/fields must be rejected, not silently ignored if they can affect
perceived correctness. Keep original as undeletable recovery theme.

Every picture must have a valid PNG/WebP MIME sniff and dimensions within safe decode
limits; external fonts would require future ABI negotiation and safe sandbox rendering.
Bitmap SHA-256 is an integrity check only, not a trusted creator signature.

### Proposed future declarative knobs (NOT in v1)

`text.familyId`, `iconMapping`, `materialShapeRef`, `alarmLineCap`,
`alarmPointSizeDp`, `deadlineLineCap`, `intervalBorderStyle`,
`axisGridOpacity`, `chatBubbleCornerMap`, `monthCellTypography`,
`transitionPreset`, `reducedMotionFallback`.

Versioned schema + feature negotiation + rendering tests are required before these keys
can be imported. Do not add ad hoc dynamic expression evaluation in place of JSON data.

## 5. Visual API boundaries

The business layer owns: eventSeq, draftId, revision, time precision, recurrence,
reminder method/timing, scheduler status, permissions, transaction state and AI tool
receipts. The rendering layer may observe immutable projections of those facts.

It may request only host-defined intents (OpenEvent, ProposeEdit, SelectMonth,
GoToday, OpenPending, ConfirmDraft, PreviewNotification, etc.).
No renderer can directly call `PowerStore.commit` or mutate field values outside the
existing draft editor. The render lifecycle is independent of background schedule
reconciliation and must survive configuration changes.

A first-party adapter can register a custom renderer only if it passes the ABI contract
and automated accessibility, unit and screenshot tests. It should be reviewed like
source code, not treated as a safe user-imported skin.

## 6. Quality gate and fallback

- Unpack/validate before activating; never partially switch themes on a failed import.
- Default theme must render without ANY external images or optional keys.
- Test empty, one event, many overlapping events, all three temporal types, repeating
  occurrences, date-only deadlines, AI candidate buttons and pending card conflicts.
- Confirm all text is legible in light and dark modes, including red/green accessibility.
- Test 0.85x and 1.20x type scales, 360dp and larger widths, landscape and TalkBack.
- Test touch target sizes, swipe and pinch hit testing, drag preview and focus/IME.
  **Never increase activity rectangle height to meet a touch target requirement.**
  Test 1-minute events at 24h/viewport and events clipped at the top or bottom;
  assert exact timestamp-to-edge alignment in pixel space.
- Test cold start, force stop, dark-mode toggle, corrupted pack and active pack removal.
- Skin failures must revert to original without changing formal records or backups.
- Store no external assets in the SQLite calendar database or signing/credential folders.

## 7. Ready-to-copy handoff prompt for Claude / designers

```text
You are designing a Powerful Calendar (PowerCal) Android Compose skin or compiled
presentation module, not rewriting the app domain.

Read:
1. docs/UI_SKIN_EXTENSION_CONTRACT_V2.md (this contract)
2. docs/SKIN_PACK_V1.md (supported portable ZIP ABI)
3. docs/PRODUCT_BASELINE_G1-G11.md (semantic sources, tempered by newer contracts)
4. app/src/main/java/dev/powerfulcalendar/ui/v2/SkinAssets.kt
5. app/src/main/java/dev/powerfulcalendar/ui/v2/SkinVisualTokens.kt
6. app/src/main/java/dev/powerfulcalendar/ui/v2/PowerTheme.kt

Before authoring, explicitly choose:
A) a user-importable .pcskin v1: colors, sizes, images, no code;
B) a compiled Compose renderer code proposal: arbitrary layout is possible only
   after implementing its typed visual interface and tests in the repository.

Keep the central vertical time axis invariant; left ALARM is a time point;
right ACTIVITY is a duration rectangle, DEADLINE a distinct time point.
Horizontal pinch is anchored at the ruler and changes only the right-hand area.
An AI-suggested action is not a published event. All operations go through Draft Deck.

Create a full light/dark visual system, reusable components, font/icon strategy,
scalable spacings, empty/loaded/error states, accessibility and reduced-motion
variants. Do not touch eventSeq/revision/recurrence/PowerStore/ReminderPolicy.
Provide sample screenshots in actual Android Compose and a clear verification
matrix; don't claim a feature can be imported unless the v1 ZIP ABI actually
accepts it. If a needed interface does not exist, propose its exact contract
and implementation as a separate reviewed source change.
```

## 8. Versioning policy

- `.pcskin`: v1 format, in-app import, backwards compatible.
- Compiled renderer ABI: v2 *proposal* only, must be implemented before release.
- Breaking a declarative key requires schemaVersion increment and migration policy.
- Visual customization shall never require a calendar DB schema migration.
