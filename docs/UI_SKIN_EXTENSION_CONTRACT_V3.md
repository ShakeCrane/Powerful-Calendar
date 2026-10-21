# PowerCal — Functional Host / Visual Renderer Contract v3

Status: architecture and delivered vertical slice, 2026-10-21. Supersedes the architectural PROPOSAL paragraphs of UI_SKIN_EXTENSION_CONTRACT_V2.md; it does NOT change the user-importable .pcskin v1 specification.

## 1. The line between function and presentation

The presentation layer owns what, where, and how to draw: whole page layout, components, typography, motion, transitions, backgrounds, icons, accessibility affordances and visuals for states. A compiled renderer may radically reshape the page.

The functional layer owns event identity, recurrence, timezone semantics, temporal geometry, draft review, permission checks, AI safety, persistence, scheduling, history and error handling. Existing button names alone do not preserve safety. A host intent must carry the right eventSeq and occurrence identity, preconditions, current revision and distinction between proposed and committed data. Only the host can perform and report completed actions.

The production UI stays Android Jetpack Compose. The local Skin Studio HTML preview is a design and intent mock, NOT a Pixel-perfect substitute for genuine Compose previews.

## 2. Three levels of extensibility

A. Portable data package (.pcskin schemaVersion 1): color palette, typography scale, radius, point width, PNG/WebP fixed slots. Trusted only as bounded data and can be imported in Settings. Scripts, executable animations, layout code, Dex and fonts are forbidden.

B. Studio design (.pcstudio.json): editable blueprint for six screens, zones, ordering and motion (none, fade, slide, scale), plus portable manifest and bitmap data. It is an authoring file, not a version-2 .pcskin and must never be passed directly to the installed ZIP loader.

C. Compiled Compose renderer: arbitrary layout and animations written as Kotlin compiled into an audited APK. It consumes read-only presentation models and host-owned callbacks; no dynamic code loading from untrusted ZIPs.

## 3. Actual implementation, no hypothetical API claims

- Implemented: app/src/main/java/dev/powerfulcalendar/ui/v2/CompiledSkinTimeline.kt; CompiledTimelineSkin.Render, TimelineSkinContext, SkinnableTimelineScreen, CompiledSkinRuntime and skinMotion.
- PowerCalApp invokes SkinnableTimelineScreen. With no custom renderer registered it delegates exactly to native TimelineScreen using the original business callbacks.
- Two native Compose previews (SkinStudioNativeLightPreview and SkinStudioNativeDarkPreview) exercise the real screen with example ALARM, ACTIVITY and DEADLINE records.
- Browser Studio records layout/motion for TIMELINE, MONTH, MEMO, AI, SETTINGS and DECK; only TIMELINE presently has a production compiled renderer entry point.
- Not implemented: full typed immutable per-screen projection and action routing for the other five pages; dynamic installable compiled skins; renderer marketplace or arbitrary Compose hot swap.

## 4. Registry convention

Every slot must eventually have a stable unique ID, immutable read-model schema, supported host intents, semantics, accessibility text, native fallback and versioned fixtures. Current Studio design IDs:

TIMELINE: header, today, memo, deck, create, axis, alarm, activity, deadline, backdrop, status.
MONTH: monthHeader, monthNav, monthGrid, daySummary, monthDecor.
MEMO: memoHeader, memoCreate, memoItems, memoDone, memoDecor.
AI: chatHeader, messages, proposal, send, aiDecor.
SETTINGS: settingsHeader, skinSwitch, aiSettings, backup, settingsDecor.
DECK: deckTitle, draftCard, diff, commit, cancel, deckDecor.

These names are a DESIGN REGISTRY, not an assertion that all have existing compilable Kotlin APIs. Slots necessary to fulfill user-facing behavior remain reachable; purely decorative background slots may be hidden.

Candidate typed host intents: GO_TODAY, OPEN_MEMO, OPEN_DECK, CREATE_DRAFT, RESET_VIEWPORT, OPEN_ALARM, OPEN_ACTIVITY, OPEN_DEADLINE, SELECT_MONTH, SELECT_DATE, OPEN_EVENT, CREATE_MEMO, OPEN_MEMO_ITEM, TOGGLE_MEMO, SUBMIT_CHAT, REVIEW_DRAFT, SELECT_SKIN, OPEN_AI_SETTINGS, OPEN_BACKUP, OPEN_DRAFT, REVIEW_CHANGES, CONFIRM_DRAFT and CANCEL_DRAFT.

Future migration should define typed per-page intent dispatchers; avoid arbitrary string callbacks in production. The intent monitor in the browser is *simulation only*.

## 5. Non-negotiable invariants

- Central axis remains the authoritative origin of actual time coordinate projection. Use TimelineCoordinate.yOf, including the correct anchor, viewport height and zoom hours.
- ALARM and DEADLINE have point geometry. ACTIVITY has a genuine interval from start to end; a 1-minute activity must never be given an artificial minimum height to satisfy accessibility. Provide a separate hit region or inspection action.
- Preserving recurrence owner eventSeq and original occurrence key is mandatory, including edits, drags and conflict resolution. Repeated instances cannot silently become series edits.
- AI proposals remain uncommitted until a validated host Draft Deck flow. Skin cannot set permission, commit status, reminder execution state or record revision by itself.
- Motion is presentation only, may never falsify time axis endpoints or the published state; all animation must respect reduced motion / low-RAM fallbacks.
- TalkBack, keyboard/IME, high contrast, large font, screen reader descriptions, hit targets and focus must work even when the visual structure is replaced.
- Source-compiled renderers require code review, automated tests and safe fallback; user ZIP must remain non-executable. A skin switch must never mutate formal event data.

## 6. Migration sequence and verification

Phase 0: completed data-only ZIP and Skin Studio packaging/tests.
Phase 1: compiled TIMELINE adapter and native preview added.
Phase 2: extract typed immutable screen view models and typed intents from PowerCalApp; migrate Month/Memo/AI/Settings/Deck one by one, preserving original semantics and fallback.
Phase 3: test native renderers in dark/light, portrait/landscape, 0.85/1.20 typography, TalkBack, reduced motion, recurrence, DST, all three temporal types, short intervals, page switching, locked AI batches, draft publish/cancel, process restart.
Phase 4: version negotiation and (only if a real need is demonstrated) a new declarative layout ABI. Never pretend browser designer output is automatically executable in Compose.

For commands and manual skin editing see tools/skin-studio/README.md. Historical CLAUDE_SKIN_ASSET_API.md documents an earlier compiled drawable interface and must not be treated as today's complete skin surface.
