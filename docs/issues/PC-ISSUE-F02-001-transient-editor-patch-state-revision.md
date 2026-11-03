# PC-ISSUE-F02-001 — transient event editor title patch rejected

## Status

RESOLVED in F-02 commit `639bf2cbd2a4cd60cb88c262ae126dfa69704d98` on `codex/skin-single-default-closure`.

## Observed behavior

On the isolated API 35 WebView creation flow, editing a newly created event title updated the DOM but the Host form retained its old title. Pressing Save therefore did not reach the expected Host review confirmation. The event store remained unchanged before explicit confirmation.

## Cause and correction

`WebEditWire.decode` requires `stateRevision` for `deck.patch`. The transient new-event projection emitted `draftVersion` but omitted `stateRevision`, so the command decoder rejected the patch before it reached the edit service. The transient projection now supplies the protocol field; durable draft versioning and Store behavior remain unchanged.

## Evidence

- Regression: `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridgeTest.kt`, `transientNewEventProjectionCarriesStateRevisionRequiredByPatchWire`.
- WebView evidence: `TimelineCreateContextWebViewInstrumentedTest` now records outgoing `deck.patch`, waits for the Host form update, and verifies the review flow; the API 35 month/timeline creation runs passed.
- Focused JVM regression passed; full JVM suite passed 967/967 with zero failures, errors, or skips. Both source boundaries, Debug APK build, Lint (0 errors, 56 warnings), and AndroidTest Kotlin compilation passed.
- APK SHA-256: `878a70f8c5fe4411ad28a149a37b1335fab1104f0c6594e0c35afcc070d6cff2`.
- Device and run evidence: `.local/android-test/reports/F-02-20261104/`, including `android-month-create-after-fix.log`, `android-month-selection-after-restart.log`, and `android-timeline-create-after-fix.log`.

## Scope and residuals

This fix changes only the transient editor protocol projection. It does not commit an event or modify Calendar Store state before the explicit Core/Host review flow. Physical/OEM testing was not performed. Product Cutover remains HOLD.
