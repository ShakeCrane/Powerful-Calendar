# Powerful Calendar — AI Agent / Skills / Evidence Protocol v2

**Product contract.** This document defines the desired, testable AI execution architecture.
It distinguishes capabilities **already shipped in the Android source** from proposed
capabilities requiring an implementation and tests. It does not grant an LLM privileged
permissions by writing instructions here.

## 1. Mission: near-zero human workload, bounded independent authority

PowerCal is designed so that the user can describe intent in natural language and may
need no further repetitive manual operations **after granting an explicit bounded policy**.
AI should do more than fill a form: it can interpret, query, compare, deduplicate,
clarify, generate proposals, select work, and explain verified results.

A generative model cannot be the authority for data correctness or permission. The sole
local PowerStore owns identity, revision, event state and commit transactions.
No provider response, OCR text, ICS property or HTML page can override a higher-level
permission. Unbounded write access or a silent approval bypass is not a capability.

**Execution path:**

1. User message or explicit file intake → trusted user intent and task-scoped consent.
2. Adapter → bounded EvidenceBundle, with source/provenance/errors/precision.
3. Agent → choose a registered Skill (or answer ordinary conversation concisely).
4. Tool transport → JSON Schema validation, limit checks and per-call ID.
5. Deterministic executor → typed ProposedOperations (draft only), never magic prose.
6. PowerStore → versioned DraftCards / permission fence / idempotent commit.
7. Optional **authorized** batch scope → final record updates + execution receipts.
8. AI reports the **actual tool outcome**, not what it intended.

## 2. Capability map and honest implementation status

| Capability ID | Meaning | Current state |
| --- | --- | --- |
| chat.reply.v1 | bounded conversational reply | implemented; stateless restart, no cloud search |
| chat.suggest_draft.v1 | recognize candidate and show action affordance | implemented via bounded untrusted hint; *not* automatic tool invocation |
| calendar.propose.v1 | normalize CREATE / REPLACE / DELETE / CONVERT into drafts | implemented via explicit structured parser and AiDraftService |
| calendar.query.v1 | narrow formal records by date/title/kind and return revisions | partial: explicit context slice, no broad standalone Tool Call |
| calendar.batch.select.v1 | select actionable draft IDs based on user description | implemented; all IDs checked against local candidate snapshot |
| calendar.batch.execute.v1 | publish or discard explicitly reviewed draft versions | implemented with persisted AiSession/fencing; no unbounded auto-approval |
| calendar.batch.modify.v1 | select formal IDs, propose partial updates, enforce snapshot revision | partial: parser inherits unchanged fields; complete autonomous orchestration missing |
| document.inspect.v1 | bounded OCR/ICS/CSV/DOCX/XLSX extraction and AI interpretation | implemented with preview; not a lossless importer for every format |
| document.to_drafts.v1 | evidence provenance to typed complete batch | partial; complex ICS repairs and ambiguities require clarification |
| web.search.v1 | real internet retrieval with URL/time/source evidence | **not implemented**; ordinary chat cannot claim access |
| agent.run.v1 | autonomous multi-step read/plan/tool/use/result loop | **not implemented**; must not be advertised as already operational |
| memory.session.v1 | durable, user-reviewable conversation summarization | **not implemented**; current context is bounded to selected recent turns |

“Implemented” does not mean independently verified on every Android device. Use Android tests,
emulated provider tests, real device/permission checks and negative tests to establish that.

## 3. Evidence and field extraction contract

For each real-world object, prioritize exactly these fields in order:

1. **WHAT**: short unambiguous activity/point/deadline title.
2. **WHEN**: precise calendar date, START, END, timezone, recurrence rule and precision.
3. **WHERE**: venue (may be absent; never invent one).
4. **WHO**: people involved (structured participants; if persistence lacks a column,
   transform to a clearly marked notes field without discarding provenance).
5. Additional facts: original excerpt, source URI/filename, recurrence exceptions,
   reminders, relevant user comments and conflicts.

For each extracted field keep metadata:
`value`, `precision` (date / instant / interval / unknown), `sourceSpan`
(filename, sheet, row, line or image crop), `evidenceStatus` (direct / inferred /
ambiguous / absent), and `confidence` used only for deciding to ask for clarification.
Confidence alone must never turn an absent time into a legal timestamp.

**Calendar rules:**
- Date and clock time use `yyyy-MM-dd HH:mm` with an explicit input zone.
- “周一 8 点” without an anchored calendar date is insufficient for an absolute start.
- “下周一” is resolvable only with a known “now” date and locale/week convention.
- “每周一” requires a true first occurrence; optional COUNT/UNTIL may be missing
  to express an unbounded series. It may require the user to supply semester start.
- Interval START and END are separate; NEVER guess an absent end by adding one hour.
- End DATE precision remains a calendar date; never invent an event deadline time.
  Its default **reminder** at local 00:00 is a separate notification policy.
- `recurrence = {freq, interval, byDay, count?, until?}` stores semantics; a repeating
  series is ONE eventSeq with per-occurrence keys, not hundreds of events.
- RRULE modifiers unsupported by the engine trigger a specific clarification; they
  cannot be silently lost by flattening to a one-off event.
- Replacements must target real eventSeq + revision; partial fields are merged
  **only when constructing a full proposed snapshot**, before independent review.
- Never merge independent proposals targeting one event without explicit version rules.

### Draft proposal payload

```json
{
  "skill": "calendar.propose.v1",
  "callId": "unique-stable-invocation-id",
  "ops": [
    {
      "operation": "CREATE",
      "kind": "ACTIVITY",
      "title": "计算机实验课",
      "start": "2026-10-26 08:00",
      "end": "2026-10-26 09:30",
      "zoneId": "Asia/Shanghai",
      "location": "教学楼 A101",
      "participants": ["某同学"],
      "recurrence": {
        "freq": "WEEKLY", "interval": 1,
        "byDay": ["MO", "WE"], "count": 16, "until": null
      },
      "reminderMethod": "MESSAGE",
      "reminderTiming": "OFFSET",
      "reminderMinutes": 0,
      "missing": [],
      "evidenceRef": "file-1#row-9"
    }
  ]
}
```

The in-app current AiResponseParser compatibility payload uses `events[]` rather than
`ops[]` and has no persisted `callId` or `evidenceRef`. A future Tool Call adapter must
translate and validate this before dispatch; documenting the shape does not implement it.

### Clarification and failure return

```json
{
  "status": "NEEDS_CLARIFICATION",
  "code": "TIME_ANCHOR_MISSING",
  "question": "这些每周一课程从哪一天开始生效？",
  "facts": ["每周一 08:00 开始"],
  "missing": ["seriesFirstDate"],
  "draftIds": [],
  "writes": 0
}
```

No tool may report success for an uncommitted draft as a formally scheduled event.

## 4. Reminders: separate event, timing, delivery

Current domain values:
- `TemporalKind`: ALARM / ACTIVITY / DEADLINE
- `ReminderMethod`: MESSAGE / ALARM
- `ReminderTiming`: OFFSET / DAY_START
- `reminderMinutes`: 0..10080 minutes in OFFSET mode; -1 disables where supported

Default new ACTIVITY/ALARM: MESSAGE at START; DATE-only DEADLINE:
MESSAGE at the start of its local deadline date. These are independent dimensions:
an ACTIVITY may request an ALARM delivery; an ALARM-kind record can request just a MESSAGE.
The local OS's AlarmManager may schedule message wakeups; that does NOT mean an actual
user-facing alarm was created. EXACT timing requires platform permission and is not guaranteed.
User-requested bulk edits must update only selected records through snapshot-preserving
REPLACE drafts. DAY_START must use a calendar day's true local start across DST rather
than subtracting 24-hour blocks from an absolute timestamp.

## 5. Native tool interfaces, constraints and error codes

Each Skill registry entry has:
`{name, schemaVersion, JSONSchema, inputMaxBytes, outputMaxBytes,
 requiresContext, permissionScope, readWriteClass, idempotencyPolicy}`.

- `calendar.query.v1`: read-only, filter by date/title/kind, max 100 candidates,
  return exact `eventSeq, revision, precision, zone, recurrence, source`.
- `calendar.propose.v1`: produce at most 100 operations (create/replace/delete/convert);
  allocate DraftCards with durable tool-call receipts. Nothing publishes yet.
- `calendar.batch.select.v1`: return exact allowed draftIds, versions and candidate counts.
- `calendar.batch.authorize.v1`: only app-originated, bound to a frozen reviewed scope.
  The model cannot generate its own approval token.
- `calendar.batch.execute.v1`: requires a valid sessionId, lockEpoch,
  per-draft version, operation allowlist and expiry; audit every result.
- `document.extract.v1`: size-bounded, reads non-executable bytes, returns provenance
  and parser warnings; injection in source content cannot authorize operations.
- `web.search.v1`: optional *future* adapter. Requires user-enabled provider/search
  tool, bounded domains/queries, HTTPS, URL/title/fetchedAt, citation/evidence excerpts,
  rate limits and explicit freshness statement; if unavailable, return TOOL_UNAVAILABLE,
  never synthesize results or pretend network access.

Errors MUST be typed and user-readable:
`INVALID_SCHEMA, UNSUPPORTED_RRULE, TIME_ANCHOR_MISSING, TIME_AMBIGUOUS,
TARGET_NOT_FOUND, OUT_OF_SCOPE, STALE_REVISION, DUPLICATE_CALL,
MODEL_UNAVAILABLE, TOOL_UNAVAILABLE, PERMISSION_REQUIRED, PARTIAL_COMMIT`.
Preserve the original evidence and per-item error reason to support feedback.

### Authorization and zero-touch operation

Human effort can approach zero under a *previously authorized task-scoped policy*:
allowed operation types, exact filters/event IDs, maximum count, maximum destructive
scope, start/expiry time, version fence, notification and revocation mechanism.
Never interpret a chat phrase or a model-generated claim as a valid authorization.
Unknown or high-impact destructive operations require more explicit confirmation
than a prior generic “help manage calendar” request. Partial success must be shown
with durable receipts and remaining work; no impossible atomicity claims across
multiple Android system side effects.

## 6. Conversational UX and latency

- Chat by default: no JSON; 60–180 Chinese characters for ordinary answers,
  at most ~350 for involved answers; no repetitive paraphrasing or filler.
- Model transport: cap output tokens; current code uses a bounded recent 40-turn /
  28k-character sliding selection. Not durable long-term memory, and not infinite context.
- The optional `chat.suggest_draft.v1` affordance is a **non-authoritative** tagged hint;
  render one action below a recognized assistant message:
  “从此消息创建事件草稿”. Clicking it invokes a separate parser + local validator.
- If a provider supports real Function Calling, negotiate exact supported tools;
  do not claim support without capability probing, semantic validation and tool receipts.
- For large files, show a brief user-facing synopsis and one high-impact question,
  not the entire table pasted back into chat. Let details live in structured preview.
- Session/history summarization, web browsing, and autonomous multi-step planning
  are dedicated later stages, not consequences of simply increasing history length.

## 7. Testing and security gates

1. Conversational-only requests produce no tool call or Store writes.
2. Event messages yield a proposal button; invalid/forged action tags never publish.
3. 40 short turns fit context; huge messages are bounded deterministically.
4. Event extraction preserves absolute dates, weekdays, times, venue, participants;
   missing initial date/end does not get a guessed value.
5. Recurrence COUNT/UNTIL/BYDAY and single-eventSeq invariant survive save/reload.
6. Bulk reminder-only REPLACE preserves title, START/END, source and recurrence.
7. Date-only deadline midnight reminder exists, while deadlineMillis remains null.
8. Every actual broadcast validates revision, occurrence key and intended trigger.
9. Untrusted ICS/DOCX/OCR messages cannot call destructive tools.
10. Duplicate invocation receipts/late responses/cancelled batch cannot duplicate commits.
11. All opt-in context and web searches are accurately disclosed and revocable.
12. Unit test + Android Lint + debug build, then real-device time/permission QA before release.
