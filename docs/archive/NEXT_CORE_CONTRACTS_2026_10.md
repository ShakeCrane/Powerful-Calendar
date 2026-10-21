# Powerful Calendar — Core product contracts for the next implementation stages

This document fixes the product semantics requested after 0.9.2-rc.1. It is a design
contract, NOT a claim that all features are shipped. The currently shipped types are
ALARM, ACTIVITY, DEADLINE. MEMO, full multi-language resources, third-party discovery
and unrestricted autonomous Agent APIs are not yet implemented.

## A. Smooth timeline geometry

1. The time ruler is canonical: timestamp -> pixel Y through TimelineCoordinate.yOf.
2. Activity rectangle START/END edges are always projected real timestamps; short
   intervals are allowed to be subpixel. No visual minimum becomes a semantic interval.
3. Collision detection uses the half-open actual interval [start,end), not text heights,
   user-defined fonts, pixel hit targets or the current zoom. A point event overlaps
   another point only at the same instant. 09:35 and 09:50 never collide.
4. Right-side lane count is fixed for a given viewport size while pinching. It must
   not jump from one to two to three lanes at a scale threshold. Lane identity and
   eventSeq/occurrenceKey are independent of X and Y scaling.
5. Gesture input is decomposed from TWO-FINGER initial separation into independent
   x and y scale components. Apply lower time-scale sensitivity than horizontal event
   zoom, keep central ruler X fixed, and damp unintentional centroid wobble.
6. When an activity is too short or too narrow for text, render a separately clickable
   title callout; never inflate the activity. Simultaneous dense callouts require a
   collision-aware label rail in a later iteration, with list/inspection fallback
   when the viewport cannot physically hold all labels.
7. Persist local camera state and cancel fling at gesture start. Avoid multiple
   animations competing for camera ownership. Later animations must be interruptible
   and honor the OS reduced-motion preference.

Acceptance benchmark: capture real-device frame-time traces for ordinary pan,
vertical pinch, diagonal pinch, high-density interval labels, 9:35/9:50 adjacent
activities, long cross-midnight activities and 1-minute activities. Test 60/90/120 Hz
where supported. Do not claim 60/90/120fps without actual trace evidence.

## B. New fourth domain kind: MEMO (design pending migration)

### Semantic model

A MEMO is a **date-based note**, not an alarm, activity interval or deadline.
It must share the eventSeq/revision/draft/recurrence/backup/AI lifecycle with all
other PowerCal temporal records.

- Required: title and a valid calendar start date.
- Optional: inclusive end date for a multi-day note; absent end means one day.
- Optional: freeform markdown/plain notes and attachments, later.
- Strict DATE precision; no invented start 00:00 time or 23:59 deadline instant.
- No reminder by default. An optional explicit reminder policy may use
  the date's local day-start without changing MEMO's date precision.
- Repeating notes: one series = one eventSeq; occurrenceKey identifies each
  derived date/date range, not a stored formal event per day.
- A cross-day memo is a calendar band with inclusive DATE endpoints, not an ACTIVITY
  rectangle with a 24-hour artificial duration.

Suggested typed fields:

    kind = MEMO
    memoStartDate: LocalDate
    memoEndDateInclusive: LocalDate?   // nullable = one day

Persistence: add an explicit v4 upgrader and BackupEnvelope compatibility before
shipping. Extend DraftForm, TemporalRecord validation and build, recurrence, ICS
all-day mapping, ReminderPolicy, Timeline/Month/Deck, type conversion, diff,
system/calendar import and export, AI Skill schemas and all exhaustive when(kind).
Do not represent MEMO as a pretend DEADLINE just to avoid migration.

### Required tests

- Single-day and multi-day date precision across DST and calendar years.
- No false trigger or alarm unless explicitly selected.
- One sequence per indefinitely recurring note series.
- Backup schema 3 -> 4 upgrades and restores without wiping older data.
- Transparent draft add/replace/delete and stale revision conflicts.
- UI date band independent of interval geometry.

## C. On-device calendar discovery & deterministic first parsing

Supported initial scope:
1. With the user's READ_CALENDAR grant, query Android CalendarContract.Calendars
   and CalendarContract.Instances. Enumerate **only readable provider rows**, not
   arbitrary installed-app private databases. Deduplicate by provider event ID,
   calendar ID, source and occurrence where appropriate.
2. Preview per-calendar event counts/source/sync timestamp before any bulk migration.
   Require the user's chosen scope and handle Android permissions/SAF.
3. Standard ICS, RFC date fields, CSV schemas and importable backup formats use
   deterministic parsing first. Unsupported RRULE/RECURRENCE-ID/schema mismatch
   yields an evidence report, and ONLY THEN an AI clarification route.
4. No AI should re-parse known correct DTSTART/DTEND just to guess dates.
5. User-initiated ACTION_OPEN_DOCUMENT is appropriate for export files from apps
   that expose them. Android does NOT generally permit silently triggering arbitrary
   third-party "export" buttons, bypassing scoped storage or reading
   /data/data/other.app; don't promise unrestricted automation.
6. Future assisted accessibility/backup integrations require explicit platform
   capability checks, user approval and a separate security review.

Import must be idempotent, itemized, permission-aware and safe on interruption.
System Calendar direct import already exists, but provider discovery/preview is TODO.

## D. AI permissions and editing existing drafts

Draft proposals require only kind-specific required fields:
ALARM(title,start); ACTIVITY(title,start,end); DEADLINE(title,due);
MEMO(title,memoStartDate[,memoEndDate]). Location and people are optional evidence.

Target capability still to implement: calendar.draft.edit.v1, which accepts
{draftId,draftVersion,fieldPatch} and produces a FULL validated draft snapshot
under optimistic concurrency; no allocating a new sequence for an edit.
calendar.batch.authorize.v1 must permit clearly scoped user-approved
publish/discard/update of existing drafts with exact versions, bounded count,
expiry, idempotency receipts and auditable partial results. Ordinary conversation
cannot silently authorize itself. This enables near-zero repetitive manual work
WITHOUT granting a language model database-owner permissions.

## E. Localized UI and naming

Android launcher display name: PowerCal. In-app display: Powerful Calendar.
All user-visible hardcoded strings must migrate to stable Android string resource
keys before claiming localization. Initial targeted locales: zh-CN, en, ja, ko,
fr, de, es; observe Android locale/plurals and relative date format.
Do not translate persistent enum names, event IDs or skin ABI keys.

## F. UI customization boundary

Finalize rendering semantics, gestures, time precision, interaction targets and
AI approval before formal artist-driven redesign. Declarative skins may change
colors/artwork and allowed geometric tokens, not timeline time mapping or
event overlap. Compile-time visual renderers may redesign screens only after
stable typed contracts and shared real renderer previews are available.
