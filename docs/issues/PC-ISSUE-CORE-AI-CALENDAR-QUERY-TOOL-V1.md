# PC Issue — Core AI calendar query tool v1

- **Date:** 2026-11-02 (Asia/Shanghai)
- **Phase:** PHASE 1 — Establish Core Feature API
- **Status:** **PASS for the bounded JVM/build scope; PHASE 1 remains open**
- **Code commit:** `974d404c4ffe3d9ea3c090c0b059e58bca94bb54`
- **Base:** `0798f0604db77c8b221cbc92df1985cbe88fd11d`

## Gap and result

`calendar.query.v1` was described as a read-only formal-event query capability, but ordinary chat had no registered query tool or Core dispatch path. This slice adds the pure Kotlin `CalendarEventQueryV1` contract, a snapshot-backed Calendar API query, and one bounded `calendar_query_v1` tool dispatch through `AiFeatureApiV1.chat`.

The query accepts an inclusive local-date range, a title substring, or both, with an optional event kind. Both date endpoints are required; ranges span at most 366 calendar dates; titles are limited to 120 characters; and callers receive at most 100 results (default 20). Results are deterministic by event sequence and carry the source feature revision plus detached event identity/revision, type, title, temporal fields and precision, event zone, recurrence, and source. Notes, location, reminders, and completion fields are omitted. Date-window matching reuses the existing Calendar API and recurrence engine; a recurring formal owner appears once.

The provider may invoke only one registered tool per chat turn. Core filters context-scoped tools using the request opt-in and independently rejects query calls when that flag is off. The Android adapter filters `calendar_query_v1` using the persisted sharing preference and rechecks that preference when a provider response returns; an unadvertised call is marked truncated so Core rejects the dispatch. Query arguments are parsed strictly, unknown or malformed fields are rejected, and Core rechecks the conversation revision before reading the Event snapshot.

The structured query result stays in the Core execution receipt. Core creates a deterministic summary, displays at most 20 hits, and does not make a second provider call. Query execution does not stage drafts or modify formal events. Exact chat receipt replay remains process-local. No database schema, backup format, or frozen event semantics changed; Native fallback remains available.

## Verification evidence

Environment: Windows PowerShell, repository-local Android SDK `E:\android-sdk`, offline Gradle. No live provider was used; the provider transport and permission-filter regression use a local loopback server.

| Check | Result | Evidence |
| --- | --- | --- |
| Focused `CalendarFeatureApiV1Test`, `AiFeatureApiV1Test`, and `AiChatClientTest` | **48/48 PASS**, 3 XML suites; 0 failures/errors/skips | `.local/android-test/reports/ai-calendar-query-tool-20261102/focused-final.log`, SHA-256 `3C11159D16B6E788BF4997347ECCE0336C17F309BD0B34541C94E2F01B292875` |
| Forced serial offline gate: both source boundaries, complete JVM suite, Debug APK, lint, AndroidTest Kotlin compilation | **BUILD SUCCESSFUL**; **873/873 JVM tests**, 137 XML suites; 0 failures/errors/skips | `.local/android-test/reports/ai-calendar-query-tool-20261102/full-gate-attempt-1.log`, SHA-256 `0FD2E913632AE8636000D2894944A08F1634423A5E5DF6BE04CED3B5B4EFFB36` |
| Lint | **0 errors / 53 warnings** | `app/build/reports/lint-results-debug.txt` |
| `git diff --check` | **PASS** | Final code and documentation diff |
| Debug APK | **66,929,262 bytes**, SHA-256 `38E233AD73D0E31C9F1D34B7003255900034134A84A63A1E5144FAE5A81381E7` | `app/build/outputs/apk/debug/app-debug.apk` |
| Android instrumentation, packaged WebView, live provider, physical/OEM behavior | **NOT TESTED** | ADB listed no connected device and the SDK had no configured AVD; AndroidTest Kotlin compilation is not runtime evidence |

The forced full-gate command was:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
```

Focused regressions cover inclusive viewer-date filtering and timezone conversion, title/kind filtering, recurrence and revision projection, result capping and detached recurrence collections, invalid/unbounded arguments, sharing-off rejection, conversation revision fencing, no-write behavior, receipt replay, provider schema serialization, returned call parsing, and persisted sharing-preference rechecks.

## Limits and next work

- The query reads a supplied formal-event snapshot. Store-level Calendar and Memo paging is still not implemented; current callers may materialize the full event snapshot.
- Core/JVM and loopback evidence does not establish the installed Native UI route, Android WebView behavior, a live provider, or device/OEM behavior.
- PHASE 1 remains open. The current unique follow-up is to define and implement Store-backed paging for Calendar/Memo read paths while preserving snapshot revision, event ordering, and recurrence/window semantics.

## Source paths

- `core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CalendarFeatureApiV1.kt`
- `core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/AiFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/SnapshotCalendarFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/StoreAiFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/AndroidAiModelPortV1.kt`
- `app/src/main/java/dev/powerfulcalendar/ai/AiClient.kt`
- `app/src/test/java/dev/powerfulcalendar/core/api/v1/CalendarFeatureApiV1Test.kt`
- `app/src/test/java/dev/powerfulcalendar/core/api/v1/AiFeatureApiV1Test.kt`
- `app/src/test/java/dev/powerfulcalendar/ai/AiChatClientTest.kt`
