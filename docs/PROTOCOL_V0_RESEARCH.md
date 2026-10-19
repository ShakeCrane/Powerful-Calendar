# PowerCal Temporal Protocol v0.3 — Research Specification and Verification

**Status:** Candidate protocol, NOT IMPLEMENTED or empirically validated.
**Updated:** 2026-10-20. **Authority:** [Unified G1–G11 Product Baseline](./PRODUCT_BASELINE_G1-G11.md).
Replaces conflicting details of the preceding research sketch. Names are logical concepts; no promise of one Kotlin type/table per entry.

## 1. Model boundaries

Define a controlled TemporalObject inheritance model with **AlarmEvent** (start/point), **ActivityEvent** (start/end range), and **DeadlineEvent** (end/deadline point). They share eventSeq, identity, revisions, title/metadata, provenance, temporal specification and reusable execution abilities. External Android/ICS/JSON formats enter through adapters; they are not internal authoritative schema.

Do not resurrect the earlier general unscheduled-task entity as a fourth type. **Draft Deck is proposal state, not an event kind.** Time boundaries are semantics, not nullable fields from which ringing or completion can be implicitly derived. An ActivityEvent may have a Trigger without a second AlarmEvent; visual left-side projections still refer to the same owner identity. A date-only Deadline is fully representable.

### Logical entities

| Entity | Key fields | Domain role |
| --- | --- | --- |
| EventIdentity | eventSeq, allocationTime, retiredAt?, lineageId | Permanent unique, never reused within this store lineage |
| TemporalObject | eventSeq, kind, revision, snapshot, createdAt, updatedAt, lifecycle | Exactly one current formal snapshot for one event |
| TemporalSpec | precision, timeKind, start?, end?, zoneStrategy, zoneId?, wallRule?, resolvedInstants? | Intent, timezone and DST policies |
| RecurrenceSeries | eventSeq, seriesRule, exceptions, seriesRevision | Owns recurrence; normal future instances are derived |
| Occurrence | seriesId + originalSlotKey, effectiveStart?, effectiveEnd?, exception | Stable occurrence identity across moving one instance |
| Trigger | triggerId, ownerEventSeq, occurrenceScope, mode, offsetOrInstant, enabled | Attached notification/ringing behavior |
| ScheduleIntent | ownerEventSeq, revision, triggerId, occurrenceKey, instant, requirement, desiredState | Durable intent reconciled to Android scheduler |
| ExecutionRecord | executionId, scheduledRevision, occurrenceKey, result, snoozeState, timestamp | Actual execution and failure evidence |
| DraftCard | draftId, eventSeq, queueOrder, operation, baseRevision?, proposedSnapshot, draftVersion, state | Independently editable card; one event may have many cards |
| CommitRecord | commitId, eventSeq, previousRevision?, newRevision, actor, operation, timestamp | Durable idempotent formal update |
| AiBatchSession | sessionId, lockEpoch, authorizedDraftIds, allowedOperations, state, cancellation | Single-AI batch ownership and recovery |
| MigrationRecord | source, externalUid?, contentFingerprint, batchId, validation, commitRef? | Import provenance; no long-term source authority |
| OutboundRecord | eventSeq, target, projectionRef, idempotencyKey, outcome | Explicit outward dispatch, without making target authoritative |

### Sequence allocation and binding

1. Allocate eventSeq **atomically** from a monotone high-water allocator at first creation of a new event draft. G11-01=A means discarded/unpublished events still permanently consume a sequence. Structured direct import allocates at its first authoritative ingest transaction; a recognized existing event must reuse its existing eventSeq.
2. Allocation of eventSeq and DraftCard identity/order is an atomic local operation. A draft referring to a **pre-existing** event receives no new eventSeq, only a new draftId and queueOrder.
3. Use a monotone queueOrder per DraftCard creation. The UI lists ascending queueOrder. eventSeq does **not** control queue position. Two cards #0042 may be separated by #0067.
4. Discarding a card does not delete the eventSeq registry entry. Deleting the current formal TemporalObject creates a tombstone/retirement state; never reuse its eventSeq.
5. Imported source UID and fuzzy-match candidates resolve identity **before or at** sequence allocation; external records do not magically contain internal PowerCal eventSeq.
6. If two restored offline forks produce the same numeric eventSeq, no unsupported silent merge; detect lineage mismatch. Future multi-device design must add stable namespace or conflict resolution.

## 2. Draft operation semantics: full replacement, not field merge

**Final choice: G11-02=B**. Each Create, Replace, Convert, Delete proposal is a card; for Replace and Convert its proposedSnapshot is a **complete intended TemporalObject**, not a patch automatically merged by field. The most recent **valid committed** snapshot is authoritative.

- A card carries baseRevision from when proposed; its own draftVersion increments during editing, separate from the official event revision.
- On publish, atomically check authorization, current AI lock token, draft state, baseRevision, event lifecycle and unique commitId.
- If current formal revision differs from the card's baseRevision: mark CONFLICT; display a **complete snapshot diff** and require explicit human approval of replacement against the now-current revision. After approval, issue a new, version-current commit; **do not silently merge unrelated fields**. This can intentionally overwrite fields modified in between. Indicate that loss clearly.
- In AI batch mode, when such conflict arises, **leave card pending for human review**; no automatic overwrite or repeated blind retry. User can accept after AI lock is released.
- An authorized AI batch can publish eligible selected drafts directly, but cannot change authorization scope or bypass stale-revision protection.
- If two cards of the same event are both valid against v4, committing one to v5 makes the other stale. There is always **one** current formal object; never two.
- CommitRecord and desired ScheduleIntent must be durably and atomically associated with the revision, but Android AlarmManager side effects happen later asynchronously.
- Duplicate commitId is idempotent: return the original outcome, not a second revision or second exported event.
- User-visible Undo of an already committed update cannot erase evidence or pretend side effects never occurred: issue an explicit compensating **new revision** (with proper scheduler reconcile). Undo of merely discarded draft can restore the same draftId/queueOrder if retention policy allows.
- If formal event is deleted, later proposals referencing the eventSeq stay blocked until **explicit restoration approval**, reactivating the same identity. Do not silently create a replacement event under a different number or resurrect.

### Representative full-snapshot conflict

v1: meeting at 15:00, location Room A.
Card D1 from v1 proposes full {16:00, Room A}.
Card D2 from v1 proposes full {14:00, Room A}; D2 validly commits v2.
A human later approves D1 while v2 exists: UI must show 14:00 → 16:00 and all other differing fields, then allow explicit full-snapshot replacement as v3.
No automatic field-level merging is performed, and a slow AI response alone cannot accept D1.

## 3. Draft Deck UX is a projection of persisted operations

The single Popup/inbox is named **待处理**. Open via dedicated pending launcher; exact button position remains design work. Search current drafts; optional search of formal events creates Update proposals. Foreground card identity tracked by draftId, not page index; visible numbered event is eventSeq.

- Stable ascending queueOrder, oldest at top. AI new results append tail, without jumping selected draft.
- Centered glass card: compact mode; tap card to expand full form. Background 2–3 fan-rotated layers. Follow Android system light/dark themes at runtime.
- Horizontal swipe **inside** card: left next, right previous, one page per physical swipe; input fields/text selection take precedence. Vertical swipe inside expanded body scrolls content. Card **outside** area permits inertial multi-page browse but must not steal Android system back gesture or accidentally dismiss unsaved edits.
- Before any finalized navigation/dismiss, flush latest edits to persistent draftVersion. Input UI can respond instantly without blocking each keystroke; if storage fails, retain edits and block transition with retry.
- Checkmark accepts this proposal and commits a formal event operation; X discards proposal (undo supported); outside/back dismisses editor with draft saved. A Delete proposal's checkmark deletes formal event, X does not.
- Post-action remove only the successfully finalized card, promote next card. A rejected or conflicted card remains.
- When queue empty and **no AI batch is still running**, enter completion state with accurate published/discarded counts, richer tasteful congratulation animation, skip & undo; after animation return to precisely the prior timeline viewport+scale.
- Theme transitions preserve draftId, cursor, input content and internal card scroll. Reduce blur/motion gracefully.

### DraftCard states (logical)

DRAFT_CREATED → EDITING ↔ STAGED → VALIDATING → COMMITTING → COMMITTED → REMOVED_FROM_QUEUE
Branches: INVALID (missing legal time, no commit), CONFLICT (stale official version), SAVE_FAILED (stay on card), DISCARDED (undoable retention), CANCELED. Actual UI animation phases are **not** persistence states.

Taskless/unscheduled **drafts** may be retained indefinitely by user policy, but formal publish requires valid time semantics. Deadline date-only is valid. There is no independently published no-time event projected onto timeline.

## 4. One-AI global batch exclusion and failure recovery

Only **one AI** is supported. Not multiple AI workers or multi-agent coordinators. AI can propose card updates and, with explicitly bounded one-off authorization, process selected DraftCards.

Session state:
REQUESTED → USER_AUTHORIZED → ACQUIRING_LOCK → RUNNING → (CANCEL_REQUESTED | COMPLETED | FAILED) → RECONCILING → LOCK_RELEASED.
On process death: RECOVERY inspects persisted session and fencing, rejects stale worker responses/commit attempts, reconciles idempotent work, and releases or explicitly reports lock failure. Never leave an infinite lock.

**Global Deck lock** during an authorized AI batch forbids human Deck edits, publication, discards and entry through other routes targeting affected formal events. Read-only timeline and unrelated views remain available; system alarm execution and maintenance continue independently.

Session authorizes an exact selected draftId set (or explicitly user-selected defined batch scope), allowed action types and permission epoch. It never expands authorization silently when another card arrives. Single AI processes this scope in serial or in a carefully serialized commit loop; no competing batch writer.

Every AI commit requires current sessionId + lockEpoch + authorized item membership and eventRevision checks. Cancellation:
- invalidate epoch / prevent **new** writes;
- let already completed atomic commits stand;
- retain unprocessed/conflict/failed cards and counts;
- reconcile schedule desired state;
- release lock deterministically;
- show accurate partial report.

Race: cancel and commit at same time. The commit either linearizes **before** cancellation fencing (then is an accepted completed result) or **after** (then is rejected); cannot have ambiguous partial state.

AI completion message must not appear while job active, even if pending cards temporarily equal zero. G11-06=A: completion only after batch ends, lock released, and queue is truly empty. Otherwise show progress/partial failure report rather than misleading success.

## 5. Timeline temporal geometry and type projection

Rendering contract: vertical time axis fixed horizontally at screen center; elapsed time maps continuously to y, earlier above/later below. Alarm start point on left; Activity range and Deadline point/date-band on right. NOW is horizontal across screen at current actual y.

Initial camera center at 12:00 today only on first opening/default reset; ordinary return persists camera. **Today** moves to actual NOW (not noon) by explicit later decision. No separate Now action.

Continuous anchored pinch zoom, bounded to 4–48 hours per viewport height; day/date labels adjusted around DST. Overflow lanes fold rather than rearrange unpredictably; inside card edit requires confirmation. For a single repeated occurrence moved across date boundary, its stable key is still the original occurrence slot.

Fuzzy times are inferred by AI into candidate time semantics, but cannot bypass validation: a date-only Deadline is valid; a Deadline with no date or a required Activity missing an endpoint remains a draft.

## 6. Scheduling, imports, backups and permissions

- Preserve separate object lifecycle (active/paused/tombstoned), schedule lifecycle (desired/pending/scheduled/blocked/missed), and actual firing lifecycle (ringing/snoozed/dismissed).
- Desired alarm keyed by eventSeq, revision, original occurrence key and triggerId. Android broadcast must revalidate these against current authoritative store **before audible side effects**. Outdated broadcast does nothing and is logged.
- Reliable audible exact alarms use eligible Android APIs and runtime permission checks. If unavailable, show BLOCKED, with grant guidance or external-clock fallback; never silently substitute an approximate alarm and label it exact. Calendar reminders may be approximate with explicit disclosure.
- Restart, permission changes, app upgrade and timezone change must reconcile. Each recurrence is a semantic rule, not a giant stored list of future occurrences. DST policy for gaps/folds needs dedicated edge tests.
- Structured migration may direct commit after validation & dedup; imported alarms are initially disabled until user confirms handoff, avoiding double ringing. A system clock's stored alarms cannot generally be enumerated via public universal API.
- Native backup/restore preserves eventSeq allocator and retired record, event snapshots/revisions, occurrence exceptions, triggers, drafts/queueOrder, authorization recovery records and source history as applicable. Restoration must **not replay old AI batch authority** or restore stale system alarm handles; rebuild desired schedules after permissions are checked.
- API credentials must not be embedded in exported data, unencrypted event records or public logs. AI context sharing requires user authorization for relevant events.

## 7. Testable invariants (candidate freeze criteria)

| ID | Invariant |
| --- | --- |
| I-01 | eventSeq is unique, monotone on new-event allocation, never reused, even after failed first draft / deletion |
| I-02 | draftId and queueOrder independent of eventSeq; repeated same eventSeq across multiple cards is legal |
| I-03 | one committed current snapshot per eventSeq, despite arbitrarily many simultaneous proposals |
| I-04 | last valid **committed full snapshot** wins; stale proposal must show diff and have explicit confirmation before full replacement |
| I-05 | no unconfirmed/unauthorized draft write may affect formal object, delivery intent or outgoing publication |
| I-06 | AI batch is globally exclusive for Deck, one AI only; all commits fenced with session + epoch + selected scope |
| I-07 | cancel/crash rejects late AI results; completed commits stay, remaining cards remain, lock is eventually released |
| I-08 | browser position maps by draftId and queueOrder, never by fragile current list index |
| I-09 | card swipe finalized only when editing data is durably staged, or fails safely without losing memory input |
| I-10 | formally deleting object retires identity; explicit restoration reactivates same eventSeq, never silently |
| I-11 | changed event revision fences any old pending alarm before ringtone is activated |
| I-12 | occurrence identity survives single-instance rescheduling; skipped/snoozed execution not whole-series mutation |
| I-13 | date-only Deadline does not acquire fabricated 23:59; incomplete unanchored draft not published |
| I-14 | new AI cards append, existing card focus stays stable; searches preserve queueOrder |
| I-15 | completion feedback implies AI finished, Deck unlocked, no pending cards, accurate outcome totals |
| I-16 | native restore prevents duplicate committed operations and stale locks; external sources do not regain authority |
| I-17 | system dark/light switch preserves active editor identity/input/focus, with readable contrast and fallback glass |
| I-18 | full replacement conflict re-confirmation is **not** silently converted to a field-wise merge |

## 8. Read-only verification and prototype test matrix

| Scenario | Expected outcome |
| --- | --- |
| First draft #0042 never published then discarded; new event created | #0042 never reused; allocator advances |
| New AI card targets old #0042 after #0071 is in queue | Its eventSeq=#0042 but queueOrder larger than #0071; appended |
| D1 and D2 on same event v2; D2 commits first | D1 stale, requires human diff/rebase for whole snapshot |
| D1 overrides newer v3 after explicit confirmation | One v4 snapshot for same eventSeq; full specified payload replaces v3 |
| Deleted #0042 receives delayed AI edit | proposal retained as conflict; no silent restoration |
| AI batch of 10 selected cards, user attempts to edit #0042 | Deck locked, affected event edit denied; other pages read-only available |
| User cancels AI at exact time of seventh commit | seventh either fully committed before fence, or not committed; never ambiguous |
| AI receives HTTP response after cancellation | rejected by session/epoch token, no later write |
| AI batch crashes with six success, four pending | safe recovery unlock, six remain committed, four remain, report partial |
| AI batch emptied queue while AI is still running | no complete-success animation yet |
| AI batch ends, queue empty, lock released | accurate success animation, skip/undo possible, prior timeline viewport preserved |
| 100-card queue, user editing #0009, AI appends 12 | current draftId stays, new cards appear at end, stable queue order |
| Type Convert Deadline(date-only) → Activity | date anchor retained; missing start/end fields require explicit review |
| Imported alarm is already active on old OEM clock | PowerCal migrated alarm disabled until safe handoff |
| Android precise permission denied after formal commit | alarm schedule BLOCKED, not silently approximated |
| Android delivers old scheduled alarm after object revision change | no obsolete ring; execution record indicates ignored stale trigger |
| Active card edits a final character then user outside fast-flings | last input staged before final navigation; no disappearing text |
| Keyboard open, user selects text and swipes horizontally inside field | text input wins; no unintended page change |
| Card X undo during final completion animation | card restored, completion canceled, no false empty-queue claim |
| System switches to night mode mid-input | caret/text/keyboard/card identity preserved; color and contrast update |
| Native backup restore (no current jobs) | allocator, eventSeq, queueOrder, historical commits and drafts preserved; schedules recomputed |
| 60 Hz / 120 Hz phones and 100 cards | measure frame timing/jank; no incorrect page identity, lost data or duplicate action |

## 9. Prototype freeze assessment and outstanding research

**Frozen for a UI prototype (not implemented):**
- centered compact-to-expanded translucent card, fan-stacked background, night-mode-aware glass theme;
- within-card one-page swipe, outside-card fast browsing, text/vertical scroll priority;
- permanent event number displayed separately from queue order;
- full snapshot review, per-card check/X, undoable discard, durable edits and truthful completion;
- one-AI batch Deck exclusion and read-only status.

**Still unresolved (must not be guessed into backend):**
- mobile UX for readable **full snapshot** conflict diff, especially stale cards in AI batch;
- import identity matching without PowerCal numbers from source;
- date-only Deadline's precise visual anchor and conversion UX;
- cancel/recovery UI and exact lock-expiration mechanism;
- grace interval and same-time multi-alarm behavior, DST gaps/folds;
- cross-lineage future database/backup merge; duplicate external source handling;
- verification of OEM UI and exact-alarm permission behavior on physical Android devices.

Android documentation for context:
https://developer.android.com/reference/android/provider/CalendarContract
https://developer.android.com/develop/background-work/services/alarms
https://developer.android.com/develop/ui/compose/layouts/pager
https://developer.android.com/develop/ui/compose/touch-input/pointer-input

**No source-code changes authorized as part of this research document.**
