# PC-ISSUE-F02-002 — Web timeline snapshot used a stale calendar projection

## Status

RESOLVED in F-02 commit `639bf2cbd2a4cd60cb88c262ae126dfa69704d98` on `codex/skin-single-default-closure`.

## Observed behavior

With a deterministic event already present in the isolated Store, the Core calendar-window query and `WebSkinTimelineProjection` both returned the event, but a newly activated WebView could receive an empty timeline item list. After the state-feed correction made the event visible, a long-press move could still be rejected as `stale` because move revision validation used the older remembered projection.

## Cause and correction

`PowerCalApp` selected the remembered `timelineCalendarRecords` list when the state query matched the remembered query key, even when the corresponding `calendarWindows` entry held the current Core result. The move action projected revisions from the same potentially stale list. Both paths now read the exact current Core window for the active Camera query and retain the remembered projection only as fallback.

The Host timeline projection also no longer assigns visual `side` or `lane`. The default Skin calculates those presentation positions from true event bounds; Core event identity, revision, recurrence identity, and valid move checks remain authoritative.

## Evidence

- Regression coverage: `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/TimelineDragWebViewInstrumentedTest.kt`, `sameActivityFixtureLongPressMoveProducesEquivalentNativeAndClearDrafts`, asserts the Core API window and projection before comparing Native/Clear move drafts.
- Focused JVM Host projection tests assert that timeline DTOs carry event kind/identity/bounds without Host-owned visual lane or side.
- API 35 isolated AVD drag/move parity execution passed; Today/24h reset and independent left/right scrolling also passed.
- Full offline Gradle gate passed: JVM 967/967, both source boundaries, Debug APK, Lint (0 errors, 56 warnings), and AndroidTest Kotlin compilation.
- APK SHA-256: `878a70f8c5fe4411ad28a149a37b1335fab1104f0c6594e0c35afcc070d6cff2`.
- Device identity, per-run logs, and package verification: `.local/android-test/reports/F-02-20261104/`.

## Scope and residuals

Core query semantics, Store transactions, recurrence rules, and reminder behavior were not changed. The entire SSA-02 acceptance item remains PARTIAL: full overlap/accessibility coverage, Studio round-trip, and physical/OEM verification are still required. Product Cutover remains HOLD.
