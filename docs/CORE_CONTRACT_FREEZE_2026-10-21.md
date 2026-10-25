# PowerCal Core Contract v0.9.4 — Product Freeze (2026-10-21)

Status: **approved product behavior, implementation pending validation**. This file records 33 Grilling decisions and the later owner amendments; it must not be mistaken for completed code. Supersedes conflicting drafts in `NEXT_CORE_CONTRACTS_2026_10.md`.

## Invariants

1. Four independent event objects: ALARM (instant), ACTIVITY (true start/end interval), DEADLINE (date or instant due), MEMO (undated, one date or inclusive local-date span). No parent/sibling/supersedes relationship between event objects. A multi-object replacement can be one transaction without storing business links.
2. Optional completion tracking for ALARM/ACTIVITY/DEADLINE; intrinsic for MEMO. Tracking, reminder scheduling and event enabled/disabled are independent states. Completion is never inferred from alarm firing, passing end/due time or dismissing a notification.
3. Untracked historic records remain untracked after migration; do **not** turn the user's old calendar into an unbounded todo backlog. For repeated tracked objects, completion is per immutable original occurrence key; date-range MEMO is a *single* completion instance, not daily completion. Undated MEMO is one task; undated MEMO must not be projected to midnight or “today”.
4. MEMO view at top-right is a **unified incomplete-todo query** across all kinds. Display type and original semantics. Unbounded future recurring events must be lazily projected within a bounded horizon; do not fabricate infinitely many incomplete items. Never silently delete past incomplete occurrences. Top controls order: **今日 → memo → 待办 → 新建**. Memo and todo controls carry a badge with **remaining item count**, not arbitrary alerts.
5. Events are simple rectangles/points in the precision timeline; rectangles preserve true temporal bounds. Activity label can be inside or immediately *above* the box (preference); label never expands the box. Start and end times stay within the box near the corresponding edges, and can collapse. No wrapping: downscale only as needed to readable minimum, truncate beyond approx ten Han-character-equivalent width, never enlarge because room is available. Tap always reveals full title. Collision titles may hide but event geometry may not move.
6. Remove X-pinch / horizontal scaling. Width is automatic from actual overlapping half-open temporal intervals, keeping the same rectangle width over the entire event and using available lane width. No phantom overlap from minimum visual rectangles. Remove decorative “×N” overflow below events, do not pretend an event is hidden. Keep vertical scroll and Y zoom. **Double-tap the current-time ruler resets to exactly 24h screen view**. Hide homepage “事件轴” heading/wording.
7. Repeat rule consists of frequency, stable cycle anchor, optional inclusive start/end *local dates* (unbounded/from/until/between/none); changing effective date bound must not rephase the cycle. One event sequence = one repeat rule, local instance exceptions/skips/overrides/completion state within its own event. Modifying *future rule* may atomically truncate old object and create a new independent event sequence, migrating only semantically compatible exceptions; do not persist cross-object inheritance links. For cross-midnight ACTIVITY, the occurrence's start date controls active-range membership, never truncate its true duration.
8. AI receives current **clock date, time, timezone and locale** on each call, from trusted runtime. Fields record explicit/derived/assumed evidence; missing information creates an incomplete proposal instead of hallucinated required fields or automatic type changes. Event disambiguation uses current version, type, time, recurrence scope (single / this-and-future / whole); uncertain selection asks for clarification. The user may grant scoped, expiring authorization for execution, not unrestricted future access.
9. AI structured actions: **CREATE proposal** and **MODIFY proposal** (update/delete/convert an existing event/draft). Mixed requests return two independent proposal cards, never an untrusted chat string that falsely claims execution. Each card has item checkboxes and four choices **批量同意 / 批量写入草稿 / 批量拒绝 / 补充细节**. “同意” means submit authorized valid operations to the formal calendar, “写入草稿” persists pending records, “拒绝” only discards proposal (not events). Independent items may partially succeed, dependent operations form an atomic group. Batch/item/draft IDs, optimistic versions, execution receipts, idempotence, restart recovery and audited compensating undo are mandatory; old cards cannot silently authorize changed contents.
10. Explicit calendar requests should show proposal cards automatically. Mere mention of possible schedule can offer a lightweight action affordance. Long-press AI message → create is *fallback*, not primary workflow. Chat history must persist locally across restart and be clearable through explicit cache/history controls; sensitive credentials never written into chat history. System calendar access always permission-scoped.
11. For images, if provider **actually supports** vision, submit original media directly to model; optionally use OCR for targeted uncertainty/cross-check, fallback to OCR when vision unavailable. Never blindly assume vision capability from model name. ICS and other deterministic formats use local parsers first. Uploaded content cannot authorize actions or override trusted system instructions.
12. Android IME: composer follows keyboard (like WeChat), messages resize without obstruction; bottom-anchored conversation follows latest messages, historical reading does not snap to bottom. Skin never repairs app-level keyboard/window insets. Verify on a real device.

## Review map (Grilling)

Q1 A fixed rectangle per overlap group; Q2 B hide colliding captions not geometry; Q3 B explicit scoped publish permission; Q4 B font shrink with floor; Q5 B target search and disambiguation; Q6 B separate create/modify cards; Q7 B contextual inference, incomplete proposals; Q8 B independent partial success; Q9 B natural-language recurrence modification scope; Q10 C intent vs casual mention; Q11 B selective vision/OCR cross-check; Q12 B scoped agent authorization; Q13 A single rule per seq, new seq for changed future rule; Q14 B optimistic versioned draft edits; Q15 B merge compatible proposal fields then validate; Q16 B structured persistent task context; Q17 B safely migrate future instance exceptions; Q18 B versioned stable proposal batch; Q19 B event seq allocated on draft persistence; Q20 B durable idempotent recovery; Q21 B compensating undo; Q22 B date semantics, **amended: MEMO may be undated**; Q23 B partial type conversion; Q24 B original occurrence start governs boundary; Q25 B independent cycle anchor; Q26 B lightweight todo; Q27 B per-occurrence completion; Q28 B preserve overdue unfinished plan without rolling date; Q29 A one completion per spanning memo; Q30 **amended** objects never linked; Q31 B per-event occurrence exceptions; Q32 B separate date-level Memo view; Q33 B batch card retained across partial results.

## Acceptance & scope order

A. Commit this freeze spec independently before code changes.
B. Audit and migrate PowerStore/backup representation without losing old records; unit tests include undated and spanning MEMO, recurring completion keys, inclusive recurrence bounds, no false overlaps, crash-safe behavior.
C. UI: precise auto lanes, no horizontal gesture and overflow multiplier, in/out label modes, 24h ruler double-tap, four top controls/order and badges, Memo/todo surfaces, keyboard insets, persisted chat and clear history/cache.
D. AI: runtime clock context, automatic structured create/modify cards and authorization gates; vision/OCR routing only when actual provider support can be established. Do not claim full function calling or safe autonomous publish when it is not wired.
E. `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` and inspect APK/version/sha; push verified changes. Physical-device behaviors remain *unverified* until tested on a device.

**Engineering note:** An implementation that merely builds but silently drops the planned autonomous operations or recurring-date semantics is incomplete. Preserve the spec and accurately report missing functionality.

## Amendment ? Unbounded Horizontal Lanes (2026-10-22)

This amendment supersedes the earlier three-lane cap and the associated overflow
aggregate/bar behavior for the native time-axis display.

- Show **every overlapping occurrence** as an independent event rectangle/point.
  Never replace the fourth or later occurrence with a red overflow strip, summary,
  count badge or a hidden-event deck.
- Preserve stable lane assignment across viewport scroll and vertical zoom;
  use interval overlap, deterministic start and event identity order. Reuse
  freed lanes. Actual start/end time positions remain exact.
- Do **not** compress unlimited lanes into the finite phone width (particularly
  not to the old 8dp fallback). Each wing is independently horizontally
  scrollable with a readable fixed lane width, while the central time ruler
  and vertical time camera stay fixed.
- Right wing starts at the axis and grows right. Left wing starts at the
  axis and grows left, with scroll initialized nearest the axis.
- The previous TimelineLayout(maxLanesPerSide=...) limit survives only as a
  compatibility option for explicit legacy/test callers; the native UI calls
  the default unbounded projection.

Do not build the later default skin or the Skin Studio editor around the
obsolete three-column/overflow-band assumption.

## Clarification — central ruler vs. horizontal decoration (2026-10-23)

The central **vertical** time-axis line is required and must remain visible
alongside its clock labels and ticks. The blue horizontal **current-time**
indicator must also remain. The unwanted visual is a separate, **horizontal**
pale-grey decorative stripe, not the vertical time ruler, the legitimate
midnight/day separator, or the current-time indicator.

Commit `609fc26` mistakenly deleted the continuous vertical ruler while
trying to remove the unwanted stripe. That removal was a regression, not an
approved product decision. Restore/preserve the vertical ruler in native
timeline and any default-skin implementation. Identify and address the
decorative horizontal stripe separately by visual evidence.

## Amendment — Portrait-only default orientation (2026-10-23)

- PowerCal's normal Activity orientation is fixed to portrait. Do not rotate the
  app in response to the accelerometer/gyroscope, and do not use a sensor-based
  orientation mode.
- If a platform, external display, or administrative policy still forces the
  Activity into a landscape-sized window, retain usable controls and timeline
  behavior as a compatibility fallback. Landscape is not a normal user mode.
- Verify the fixed portrait request on supported phone-sized Android versions.
  Before raising target SDK to 36+, reassess Android 16 large-screen behavior:
  orientation restrictions are ignored on displays with smallest width >=600dp
  for apps targeting API 36 or higher. See the [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16).

## Owner amendment — accepted timeline edge and current-time overlap (2026-10-24)

This amendment supersedes the 2026-10-23 clarification only for the two
explicitly accepted visuals below; the continuous vertical time axis and all
event time geometry remain part of the contract.

- A faint pale-gray horizontal edge/band is accepted. Removing it is not a
  freeze acceptance criterion; retain its prior screenshots/evidence for
  history and retire that candidate from current GO gates.
- The blue horizontal current-time indicator may overlap the central vertical
  time axis at their crossing. This layering is intentional and must not be
  treated as a missing-axis defect. Keep the actual time geometry and ruler
  behavior intact.
- Do not reopen unrelated UI changes to address either accepted visual.

## Owner amendment — recurring DST gap/fold resolution (2026-10-24)

This amendment defines the resolution for generated recurrence instances only:

- If a recurrence's local wall-clock slot does not exist during a spring DST gap, skip that
  instance and still consume its generated slot index/count. Later instances keep their original
  local dates and do not shift.
- If a recurrence's local wall-clock slot occurs twice during a fall DST fold, use the first
  instant, corresponding to the earlier offset.
- This does not define how an individually entered one-off local timestamp is parsed.

## Owner amendment — one-off DST fold input (2026-10-25)

- If an individually entered one-off local wall-clock timestamp occurs twice during a fall DST
  fold, resolve it to the first instant, corresponding to the earlier offset.
- This applies to minute-text parsing used by one-off event forms. It does not change the
  recurrence gap/fold rules above or define a new one-off spring-gap policy.

## Owner amendment — missed-alarm grace period (2026-10-24)

- A late alarm may fire through an inclusive 10-minute grace period measured from its requested trigger instant.
- Once more than 10 minutes late, record `MISSED` and do not ring as a catch-up reminder.
- This amendment resolves only the late-alarm grace duration and boundary. Simultaneous-alarm behavior remains unresolved.

## Owner amendment — Today camera behavior (2026-10-25)

- Activating **Today** from the native timeline or any Web skin returns the camera to **12:00 noon of the current local date** and resets the viewport to **24 hours**.
- The behavior uses the current clock and configured time zone when the action runs. It is the same camera state as initial opening; it does not preserve a prior zoom or center the exact current instant.
- The blue current-time indicator remains at the actual current instant and may therefore appear above or below the noon-centered viewport anchor.
- This supersedes the earlier `PRODUCT_BASELINE_G1-G11.md` Today=now behavior and the former `REC-P1-002` expected result. The double-tap current-time-ruler action remains separately specified as a 24-hour reset.
