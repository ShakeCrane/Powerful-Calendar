# PC-ISSUE: Web recurrence interval must not narrow frozen Core

Date: 2026-11-03
Phase: Core-first / Skin-first PHASE 1
Status: **CODE FIXED / JVM VERIFIED; Android runtime NOT TESTED**
Code commit: `eae3667917ca277477271928bfd539c3566408d3`

## Problem and Core-first contract

The Web Bridge accepted `recurrenceInterval` only in `1..365`, while frozen Core requires a positive `Int` and has historical support for intervals beyond 400 days. This was a transport-layer decision incorrectly reducing Core's public feature capability. Native and Core were not changed to match this erroneous restriction.

## Minimal fix

In `WebSkinEditBridge.kt`, expand the wire field's string length from 3 to 10 and parse using `toIntOrNull()?.takeIf { it > 0 }`. This preserves the bounded frame and type-safe signed 32-bit integer parser; invalid, zero, negative, overflow, and oversized strings remain rejected. No Store schema, Core business logic, reminder scheduling, approval policy, or Skin UI styling changes were made.

## Regression evidence

- `WebSkinEditBridgeTest.recurrenceIntervalAbove365UsesTheSameCoreRulesInWebDeck` sends a real versioned `deck.patch` with interval 366; verifies the Web Deck projection, flush to the review draft, absence of early formal writes, trusted Host approval, and committed Core recurrence.
- `WebSkinEditBridgeTest.recurrenceIntervalWireKeepsIntegerAndLengthBounds` checks 366 and `Int.MAX_VALUE` accepted, plus negative, zero, overflow, nonnumber and overlength input rejection.
- Focused Gradle rerun: `BUILD SUCCESSFUL` (2/2 tests).
- Final serial offline gate: **946/946 JVM tests across 139 suites**, zero failures/errors/skips; **154 Gradle tasks** completed successfully, both source boundaries, `assembleDebug`, `lintDebug`, and `compileDebugAndroidTestKotlin`.
- Full gate log: `.local/android-test/reports/handoff-recurrence-20261103/full-gate-serial.log` SHA-256 `15B925A8FD044B6763C60FB00C5B355A256E2D477D02DF1A4CFBE66C0ADEDE86`.
- Debug APK SHA-256 `68F5F8308A6204C45662F4B39E5C4A4564D98F161953BEEF0DB33B0742042AF6`.
- Test XML: `app/build/test-results/testDebugUnitTest/TEST-dev.powerfulcalendar.ui.v2.WebSkinEditBridgeTest.xml`.

An earlier attempt that ran a second full forced build in parallel with an already-running serial Gradle process failed at `processDebugUnitTestJavaRes` while reading the Kotlin output directory. The later sole serial gate passed. This is a build concurrency race, not an established product failure. The prior agent's first focused attempts also contained a malformed JSON test fixture; the committed test uses `JSONObject` construction and is green.

## Remaining scope

No ADB device/AVD was connected during this slice. Packaged Android WebView behavior, actual Skin interaction and device Native/Skin parity remain **NOT TESTED**. This fixes one interface parity gap only; Phase 1 and final cutover remain HOLD. Do not conflate this interval constraint with the independent Calendar query-window limit of 365 days.
