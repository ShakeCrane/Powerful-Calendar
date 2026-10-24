# Powerful Calendar — Unified Product Baseline G1–G11

**Status:** Product decisions confirmed; design research only; NOT IMPLEMENTED.
**Updated:** 2026-10-24. **Supersedes:** previous G1–G5 baseline and the old protocol interpretations that conflict with later user decisions.
**Authority:** Explicit later user decisions override earlier assistant recommendations and questionnaire defaults. This document defines intended behavior, not what Android MVP v0.1 already does.
**Scope:** Android-first native application; no code changes in this documentation phase.

## 1. Product mission and information flow

PowerCal is a **one-stop, offline-first, personal time-information system**, not a perpetual multi-account calendar sync client. After migration, its local authoritative event store is the **sole everyday source of truth**.

Input: Android Calendar / accessible alarm interfaces / ICS and other structured data / screenshots / natural language / a configurable single AI API. Existing external data is typically **asynchronously imported once**, normalized, deduplicated, and then managed in PowerCal. Unavailable OEM alarm-data APIs must not be bypassed or falsely claimed. Original external records are not silently deleted.

Structured imports can publish validated event records directly. Imports must be restartable and idempotent; ambiguous duplicates go to review. Imported alarm records enter the main timeline **disabled until old-alarm handoff is verified**, to avoid double ringing. A migration adapter is not a continuously syncing master.

Unstructured input (e.g. natural language or screenshot) goes through AI interpretation to a reviewable draft, not straight to a live timeline object. A person can manually create and publish an event immediately through the same card editor. AI can propose Create / Replace / Delete / Convert operations and may **publish/discard a specifically selected batch only after single-use user authorization**. User input and AI use the same event protocol.

Outgoing targets (system calendar, ICS, etc.) are explicitly selected per user rule/event. Outgoing publication creates external projections; PowerCal remains authoritative. Native complete backup/restore has higher priority than lossy ICS exchange.

## 2. Identity, order, and persistence: non-negotiable

- **eventSeq** is a globally permanent identity within one PowerCal database lineage, readable as #0042. A new event receives it **when its first draft is created**, even if that draft never publishes. A direct structured import allocates it at the corresponding ingest/publish boundary. Deleted/discarded event numbers are retired, never reassigned.
- **draftId** is an independent identity of one proposal card. An existing event has at most one actionable draft card at a time. Saving a later human or AI proposal for that event overwrites the active card in place, preserving its `draftId` and `queueOrder`; the last successfully persisted save wins. Discarded history may remain, but multiple same-event actionable cards do not coexist. A draft may represent a Delete, Replace or Convert proposal on an existing object.
- **queueOrder** is a monotone independent enqueue-order key assigned per new card. The queue is oldest-first, oldest on top. New AI/user cards for different events append to the tail; overwriting a same-event draft retains its existing slot. Searching/filtering does not reorder it. Never equate queueOrder, eventSeq, event date or update timestamp.
- **revision** is the formal event's last committed version. Every mutation is recorded by a unique idempotent commitId. Event identity is never changed by type conversion, time changes, revision increments, UI projections or export.
- **Last valid COMMIT wins.** G11-02=B explicitly selects **complete object snapshot replacement**, not automatic per-field merging. A stale draft must not silently overwrite newer committed data: compare baseRevision; display a full before/after diff and obtain explicit confirmation/rebase before full replacement. During authorized AI batch, unresolved stale draft is left blocked for later user review, never auto-merged, auto-replaced or silently dropped. After valid acceptance, the entire proposed snapshot becomes the new revision (including deliberate replacement of unrelated fields).
- A late AI response cannot overwrite a user's actively leased edit; the writer gate rejects AI writes while that human editor lease is live. Outside a live editor lease, successfully saved human and AI proposals for the same event share the single actionable card, with last persisted save winning. AI suggestions carry eventSeq, draftId and baseRevision; no draft save is a formal commit.
- A deleted event becomes retired/tombstoned by eventSeq; new proposals targeting it must explicitly ask whether to restore this **same identity**. Restoration cannot occur silently.
- Local sequence uniqueness is only guaranteed within a database lineage; independent offline forks/restores may allocate the same number, so cross-device merge requires a future namespace/collision protocol. No multi-device merge is promised now.

### Owner clarification — same-event human/AI drafts (2026-10-24)

This explicit decision supersedes G9-06's earlier allowance for multiple same-event cards and the prior wording that human and AI proposals coexist as separate drafts. Keep one actionable draft per existing event; each later successfully persisted proposal overwrites that draft in place, preserving its ID and queue slot. The formal event remains unchanged until a separate explicit confirmation/commit. The active human-editor lease continues to reject AI writes.

## 3. Temporal domain: three final event kinds

Internal controlled inheritance under TemporalObject; external protocols are connected through adapters. **Exactly three user-facing temporal shapes for v0**:

| Kind | Time semantics | Timeline presentation |
| --- | --- | --- |
| AlarmEvent | has START/trigger point; no END | LEFT of the central vertical axis |
| ActivityEvent | has START and END; occupies a time interval | RIGHT of the central vertical axis |
| DeadlineEvent | has END/deadline point; no START | RIGHT, marked as a deadline rather than an interval |

A Deadline can carry **date-only precision** (e.g. Friday), without inventing 23:59. A date-only deadline needs a date-band/day-level UI anchor rather than a false exact minute. Incomplete abstract inputs without sufficient legal time precision remain unpublished drafts.

The three kinds can convert into one another while keeping eventSeq; preserve existing semantic anchor where meaningful. If the target kind requires additional time information, mark it **required for review**; never invent critical times during conversion without a user-accepted interpretation. A Deadline may additionally have a completion/progress capability without pretending it occupies a START–END interval.

Event *kind* is not the same as Trigger: an Activity can have attached reminders or linked AlarmEvent. A left-side projection of an attached alarm-capable Trigger is not a second event identity.

TimeSpec preserves wall-clock versus fixed-instant meaning, precision, timezone policy and recurrence. Local daily alarm follows local wall time by default; timezone-bound activities preserve their intended zone. For recurrence, skip a slot whose local time falls in a DST gap while consuming its generated count/index; for a DST fold, select the first instant using the earlier offset. A recurring series has a rule + stable occurrence keys based on original slots + per-occurrence overrides; do not expand future instances as primary truth. Editing a repeated occurrence defaults to THIS INSTANCE, with options for this-and-future or whole series.

### Owner clarification — recurring DST gap/fold (2026-10-24)

This policy applies to generated recurrence instances. A nonexistent local-time slot during the spring transition is skipped and still consumes its generated index/count; it does not shift later slots. A repeated local time during the fall transition selects its first instant (earlier offset). This does not change parsing of a separately entered one-off local timestamp.

Source identity, migration information, exact-alarm eligibility, delivery mode, completion state, object status, schedule status and ringing status are separate capabilities, not inferred solely from null time boundaries.

## 4. Timeline as the central visual and interaction surface

Central **vertical** time ruler: earlier above/later below; alarms left, activities/deadlines right. A beautiful **horizontal** line shows NOW at its actual position, not always center.

Initial opening: today, noon 12:00 vertically centered, usually with 24h fitting the viewport. This is an **initial camera position, not a scroll lock**. Up/down inertial scroll is continuous across midnight. Returning normally preserves the last viewport and zoom. The one **Today** action jumps to **current time** (overrides the old proposal to jump to noon). No separate Now action required.

Two-finger continuous zoom is finger-anchor-preserving, constrained to **4–48h of time per viewport height** (subject to true DST elapsed-time semantics). The layout shall be viewport-based, with lazy data access, stable identity and no unbounded full-column relayout. The real instant coordinate remains continuous through DST shifts; calendar labels identify gaps/duplicate hours.

For overlap, earlier-starting event is nearer the center; later-starting overlapping items expand outward. Once intervals cease overlapping, lanes may be reused. Stable lane allocation should not jump merely due to a small viewport scroll. Excess lanes collapse with visible counts; date-only deadlines and small alarms use legible affordances without falsifying their exact time.

Long-press an existing event to preview moving it, use zoom-dependent time snapping. Confirm via the **same card editor** in compact-approval state or expand to full edit; no backend change before confirmation. Repeat-series move defaults to this occurrence only.

## 5. One integrated Draft Deck / Popup / pending inbox

The Popup, unscheduled inbox and event proposal review are **one integrated Draft Deck**, not three separate feature screens. UI name: **待处理**. The presence of an unsigned draft is a workflow state, **not a fourth event type**. All its cards represent real, editable protocol proposals.

- Centered floating glass popup (not bottom sheet), compact preview that expands to full editable card when user taps card (G9-01=B). Card type is shown and changeable by clicking a type selector menu.
- Background fan-stacked cards with gentle rotation/offset; foreground active input card remains comfortably upright. Compact size is controlled; full edit expands within screen and scrolls internally.
- Queue top is oldest draft. Swipe LEFT inside active card = one later queue card; RIGHT = one earlier card; **at most one card per inside swipe**. Swipe OUTSIDE card can fling through multiple cards. Vertical movement inside expanded card scrolls content. Text selection/input gestures take precedence over navigation.
- Editing updates the live card immediately; debounce persistent draft save; **before completing any page transition or exit, latest edits must be durably staged**. On save failure retain card/input and offer retry; do not treat it as published.
- A checkmark CONFIRMS the proposal (Create, Replace, Delete, Convert) and commits it as a valid formal object mutation; an X DISCARDs the **proposal**, with undo/recovery. For Delete proposal checkmark means deleting formal target, X means not deleting it. Outside-tap/system return = exit editor and preserve incomplete draft.
- After a successful card action, remove that card, promote next oldest still pending, do not reset unrelated draft identities; each card is independently idempotent. When final pending card is processed, show a **relatively rich but tasteful completion animation**, real count of published/discarded, support skipping and undo, then return to timeline **at the previous camera position**.
- Completion is only truthful when pending items are really empty and **the sole AI batch job is finished and lock released**; no premature congratulations while more items are being produced or conflicts remain.
- Search defaults to pending cards; user may switch to search formal events, tapping result creates a matching Update draft. Search/filter never changes underlying queueOrder.
- Additional cards produced by AI append to queue tail and must not steal current card focus. If there are multiple proposals for one event, the UI can indicate shared eventSeq; **do not auto-merge or silently overwrite drafts**.
- Adopt gentle spring, restrained glass edge highlights and fine animation; strong content contrast. Default **follow Android system dark/light theme live** without dropping focus/input. Low-end devices reduce costly blur gracefully; respect reduced-motion settings. Art direction: fluid, polished, simple, comfortable, frosted/liquid glass, rather than noisy decoration.

## 6. Single AI, authorization and exclusive deck editing

**Exactly ONE AI** (one assistant/controller); do not invent multi-agent routing, multiple competing AI workers or agent-to-agent ownership.

The AI can read user-permitted relevant event context, normalize ambiguous inputs, create or update draft proposals. External API endpoint/model/key are configurable. Only minimum relevant context leaves device after task-scoped permission. AI output must pass typed validation; unknown time and unsupported fields remain in review.

A user may explicitly authorize ONE bounded AI batch to publish or discard specific selected cards. While that AI batch is running:
1. All Draft Deck human editing/publishing/discarding is locked globally; event edits targeting batch-affected eventSeq from other UI routes are likewise forbidden.
2. User may **read** timeline and unrelated views, inspect progress and cancel the batch. Do not freeze entire App.
3. Only one AI and one exclusive batch operation at a time. No competing AI task may mutate the deck. There is no multi-AI orchestration.
4. Persist AI batch sessionId, authority scope, lockEpoch/fencing token and operation idempotency keys. Every commit validates authorization, event revision and current lockEpoch; stale responses after cancellation/recovery are rejected.
5. Cancel means stop subsequent commits, keep already-successful commits, preserve remaining draft cards, report partial results, then safely unlock.
6. Network timeout, process death or restart MUST NOT leave a permanent lock. Recovery must invalidate old task epoch before unlocking and must not replay completed actions.
7. AI batch meets a stale full-replacement proposal → keep conflict card for human approval; never choose a replacement silently.

Note: the requirement to forbid human card work applies **during AI batch execution**; reading a timeline, normal system reminders and database reconciliation may continue. A side-effect scheduler is not an AI author and must not edit event intent.

## 7. Import, reminders, backups and durability

Import adapters support manual/restartable migration from Android Calendar and ICS; existing system alarm inventory is not generally available on public Android API. Do not claim automatic retrieval from arbitrary OEM clocks. Structured data can bypass individual review only if strictly validated, well-deduplicated and permitted by a user-initiated migration policy; alarm triggers wait for safe handoff.

Every formal commit atomically persists event revision, commit record and durable desired scheduling state; actual Android scheduling is reconciled asynchronously. A stale Scheduled PendingIntent carries revision/occurrence/trigger identity and is checked on firing so that old 07:30 cannot ring after a valid 08:00 update. Exact alarm permission missing/revoked must produce BLOCKED and visible guidance, not silent approximate substitution for required audible alarms.

On boot/update/timezone/permission changes, recover desired schedules; a late alarm may still ring through the owner-approved inclusive 10-minute grace window, while anything later is recorded MISSED without a catch-up ring. Snooze/skip change occurrence execution, not recurrence series.

Native complete backup includes eventSeq allocation/high-water mark and retired identities, committed records, event and trigger state, recurrence exceptions, draft cards/order, migration provenance and relevant AI task recovery metadata. Do NOT back up live Android PendingIntents as reusable executable state or include raw API secrets. Standard ICS/etc. export remains intentionally lossy.

## 8. Priority decisions index (G1–G11)

Later explicit custom decisions supersede older questionnaire choices; don't resurface settled questions.

- G1 user-established: common event protocol, custom AI API structured output, shared time-object inheritance, Android adapters. Later three-shape and draft constraints take precedence.
- G2: 01=A public alarm interface; 02=B superseded by sole local source; 03=B rule-based outgoing; 04 undecided obsolete as long-term sync dropped; 05=B repeat+exceptions; 06 overridden by user central axis; 07=C adaptive continuous zoom; 08=A compact editor; 09 overridden by integrated paged Draft Deck.
- G3 all confirmed: 01=B user-selectable migration range; 02=B strict source dedup+suspect review; 03=A manual old alarm handoff; 04=A linked alarm + trigger; 05=B time semantics; 06=C lane overflow collapse; 07=A long-press drag; 08=B durable uncommitted cards; 09=B AI context permission.
- G4 all confirmed: 01=B historical three-kind requirement, final kind names/boundaries superseded by G6; 02=C optional expanded associated alarm projections; 03=A dynamic recurring occurrences; 04=A wall-clock alarm vs zoned activity; 05=A commit then asynchronous scheduling; 06=A exact-alarm failure visible; 07=A occurrence-only snooze; 08=A continuous cross-day timeline + initial noon centered; 09=A stable lane layout; 10=C compact confirmation UI; 11=A AI can propose create/change/delete.
- G5 all A: 01 permanent event identity through conversion; 02 former unscheduled-task concept superseded by Draft Deck; 03 this occurrence default; 04 actual elapsed-time DST geometry; 05 original Today=noon superseded by Today=now; 06 4–48h zoom; 07 compact/full same editor; 08 adaptive snap; 09 fenced idempotent scheduler; 10 short grace missed alarm; 11 AI stale revision check; 12 full native backup priority.
- G6: 01=B centered floating popup; 02=B compact editable preview; 03=B type menu; 04=B whole-card horizontal gestures with input precedence; 05/06/07/08/09/10/11/12 questionnaire undecided but many replaced by subsequent explicit integrated-deck, publication and Today=now rules.
- G7: 01=C name 待处理; 02 oldest-first stable sequence custom; 03=B search drafts or existing event; 04 text-edit-before-swipe persist custom; 05=A automatic next card + completion feedback; 06=B undo discard; 07=B date-precision Deadline; 08 undecided resolved G8-09=B; 09=A bounded authorization; 10=A partial batch success and remaining failures; 11=B alarm imported but disabled; 12=A unfinished formal-edit becomes Update draft.
- G8 all confirmed: 01=C fan stack; 02=C compact fixed/full expands; 03=A left next/right previous; 04=A whole-card swipe respecting text interactions; 05=A restrained spring; 06=A auto-durable draft save and flush at swipe; 07=A unchanged order during search; 08=A new AI cards appended; 09=B explicit required conversion fields; 10=A block publish without legal time; 11=B rich tasteful completion with statistics; 12=A restore previous timeline viewpoint. Extra: system-following light/dark, frosted/liquid glass.
- G9: 01=B tap card expands; 02=A horizontal/vertical gesture arbitration; 03=A single-step swipe from within; 04=A retain card on save failure; 05=A protect an active human edit from incoming AI; 06 originally allowed multiple cards per eventSeq, superseded by the owner clarification above; 07=A completion+undo; 08=A accurate numbers, no scoring; 09=A live dark mode; 10=A explicit batch scope and count.
- G10: 01=B independently enqueued proposals ordered by queueOrder; 02=A last valid commit wins; 03=A stale card retained with diff; 04=A no silent resurrection; 05=A durable save before finalized navigation; 06 formerly undecided, resolved G11-06=A.
- G11: 01=A eventSeq at first draft, never reuse; **02=B whole-object replacement** (overrides earlier per-field merge recommendation while retaining explicit stale-version reapproval); 03=A global Deck lock on AI batch, affected event edits blocked from elsewhere; 04=A cancel leaves already-committed effects; 05=A serial batch control by the **single AI**; 06=A only after AI ends/unlocks and queue truly empty show completed animation.

## 9. Explicit unresolved questions — not implied decisions

- Exact visual location of the 待处理 launcher in the time-axis navigation; search UX details.
- Date-only Deadline positioning within a day band and accessibility on zoomed timeline.
- How to handle type conversions involving ambiguous/missing time anchors and default ringtone/notification settings.
- Simultaneous alarms.
- Whether human direct edits can be initiated at all during AI batch for **unaffected** formal events (deck remains locked; affected events explicitly blocked).
- Boundaries of a batch when new AI-produced cards appear during the operation; cancellation UX and progress layout.
- Full-object replacement conflict review can show a readable full diff without overwhelming mobile display; requires prototype.
- Future backup restore fork/cross-device collision handling; currently single database lineage only.

## 10. References and implementation boundary

- Android Calendar provider: https://developer.android.com/reference/android/provider/CalendarContract
- Android alarm permission and background constraints: https://developer.android.com/develop/background-work/services/alarms
- Compose gesture recognition: https://developer.android.com/develop/ui/compose/touch-input/pointer-input
- Compose paging: https://developer.android.com/develop/ui/compose/layouts/pager
- Companion protocol & tests: [PROTOCOL_V0_RESEARCH.md](archive/PROTOCOL_V0_RESEARCH.md)

**Do not treat this documentation as an Android implementation.** Android MVP v0.1 source remains a historical prototype and may not implement permanent eventSeq, this inheritance design, Draft Deck, or AI exclusive batches.
