# PowerCal R1 import / AI / semantic-skin implementation

Implemented on the isolated branch `feat/r1-import-ai-compat`. The original main
worktree remains untouched, including parallel `design/` work.

## Product decisions retained

- Q1=A: item-level partial success; all pieces sharing one RFC UID are atomic.
- Q2=A: provider/model-scoped, explicit opt-in for **extracted file text** only.
  Already parsed events, raw images, existing calendar records and source file bytes
  are outside this consent. Consent can be revoked in host-owned AI settings.
- Q3: static files and one-time Android calendar snapshot; no monitoring or sync.
- Q4=A: minimal import receipt and reconciliation on re-selection; no automatic
  indefinite source-file retention.
- AI drafts remain proposals requiring review. No importer publishes directly.
- Soft response-length prompt; tool output/JSON never treated as user-facing prose.
- v3 skin bridge extends *additively*, without changing ZIP schema, WebView sandbox
  or the design source of the other Agent.

## Implemented behavior

1. File selected in the AI attachment flow: MIME, display-name and header sniffing
   route ICS to the **complete** local parser before the 24K-char AI evidence limit.
2. ICS structural preflight and semantic grouping by UID; independent valid groups
   become new drafts even when other groups fail. A recurrence exception never
   becomes an independently inserted calendar event.
3. Nested VALARM properties no longer overwrite VEVENT properties; all-day
   VEVENTs without DTEND use the RFC one-day default and keep an explicit
   date-precision annotation. DURATION-bearing VEVENTs can be imported.
4. Recovery receipts contain only file hash, draft IDs and unresolved count.
   They use synchronous persistent writes; re-selecting a static file checks
   existing drafts and formal records to avoid ordinary duplicates.
5. Unresolved ICS snippets may be explained by the configured AI with explicit
   first-use consent; the complete ICS and successfully parsed groups are not
   sent. Unsupported recurrence transformations never directly create a draft.
6. Other extracted textual documents use the existing typed AI proposal and
   review path, followed by clarification when needed.
7. AI output parser no longer clips visible replies at 1,000 characters;
   incomplete internal markers are suppressed. Empty responses get a bounded
   second attempt with a larger token budget; multipart textual responses work.
8. Native timeline: left horizontal lane scroll disabled, right retains
   single-finger horizontal scrolling.
9. Host-owned `optionGroups` data and `invokeOption` allowlisted action added
   to v3 bridge; static v3 skins remain compatible. See
   [SEMANTIC_OPTION_BRIDGE_V1.md](SEMANTIC_OPTION_BRIDGE_V1.md).
10. First launch offers a one-time Android system calendar import prompt,
    with persistent dismissal and no ongoing sync.

## Non-goals and follow-up risks (not silently declared complete)

- Full RFC5545 coverage is **not** achieved: RECURRENCE-ID/RDATE overrides, custom
  VTIMEZONE definitions, VTODO domain mapping and other uncommon RRULE constraints
  remain deferred with explicit diagnostics. A parser rejecting these is a
  correctness boundary, not a claim that they were imported.
- All-day ACTIVITY data cannot preserve a first-class all-day precision flag;
  the original intent is annotated in notes and represented as a day interval.
- File import recovery is **reselection-based**. A process restart cannot
  continue unresolved file reading without choosing the file again; the
  checkpoint is not a background task scheduler or a content archive.
- The UI currently shows only the first five unresolved RFC groups for AI help,
  and reports that this selection is incomplete when needed.
- Built-in visual skins that hard-code menu entries do not automatically render
  a new option. Only a skin that renders a host-provided semantic group can
  automatically show later sibling options.
- Real model/provider error handling, Android file-provider behavior, crowded
  left-wing touch targets, TalkBack and real-device WebView compatibility
  require physical/emulator test evidence before release.
- A real user-provided failing ICS fixture has not been supplied in this thread,
  so regression tests exercise constructed cases rather than that exact file.

## Verification commands

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug --console=plain
```

The build may require `local.properties` pointing to the locally installed Android SDK.
