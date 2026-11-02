# Core Feature API — bounded recurrence-window query v1

**Status:** Implemented; scoped JVM/build verification passed. Android instrumentation, packaged WebView behavior, and Native/Skin parity are not tested.
**Phase:** Core-first / Skin-first PHASE 1 — Establish Core Feature API.
**Baseline:** Code parent `17d99b00d0e0abb8cafe54d9826051904a08c72d` (`origin/feat/skin-first-separation` at implementation start).
**Code commit:** `9ee927ff33bea9ec1d3a979a613a391ef87e3ffe`.

## Gap

Native timeline and month Host projections expanded recurrence through `RecurrenceEngine` directly. That left this presentation path outside the versioned Core recurrence service and made it harder to enforce one bounded query contract for renderer windows.

## Contract and implementation

Added `RecurrenceFeatureApiV1.queryOccurrencesInWindow()` over a detached `EventRecordV1` snapshot. `RecurrenceOccurrenceWindowQueryV1` requires a non-empty half-open time window no longer than 43 days and a per-event occurrence cap from 1 through 2,048. The adapter asks the existing `RecurrenceEngine` for one additional slot to report `truncated`, preserving its window-start-prioritized selection and nearest carry-in behavior. The response echoes the event sequence and revision. It is a snapshot projection, not a freshness guarantee or write authorization.

Native timeline and month Host projections now call the shared service with their existing 17-day and 42-day windows. The Host verifies the echoed identity and keeps the prior date-only deadline normalization, original occurrence key, DST/local-zone semantics, visible interval filter, and timeline geometry. `PowerCalApp` supplies the same recurrence service to both projections; the service remains private to Host contexts and is not exposed to Skin renderer data. No database, backup, event state, or frozen recurrence semantics changed.

Web timeline/month projection still expands recurrence in its Host projection. Routing those projections through this API is the next bounded action.

## Verification

- Focused regressions: **50/50 PASS across 6 XML suites** (`RecurrenceFeatureApiV1Test`, `GlassPresentationTest`, `MonthVisualContextContractTest`, `NativeMonthProjectionTest`, `NativeTimelineProjectionTest`, `WebSkinRecurrenceBridgeTest`). Focused log: `.local/android-test/reports/phase1-recurrence-window-api-20261102/focused.log`, SHA-256 `A7B0CFD81F32A9ABF244FD7382D1DFEC37379616E314C5872FD8636EAC7A4A81`.
- Final forced serial JVM gate: **896/896 PASS across 139 XML suites**, 0 failures, errors, or skips. JUnit manifest: `.local/android-test/reports/phase1-recurrence-window-api-20261102/full-junit-manifest.csv`, SHA-256 `3D3D0B7725BE6138DA8A09E0520F9DF4D3C2F5DF737760C7BE1BDF91DA77E65B`. Final full log SHA-256: `508E00EFD6A011093801D218D0C15EA8A3A023F4EAF81EEA3B29B8264A0944A5`.
- Run history: an earlier final-source full serial run had 2 failures in 896 tests. Each exact failing method passed in isolation (**2/2**); the subsequent forced serial full gate passed. The isolated log is `aggregate-failures-isolated.log` in the report directory, SHA-256 `3313CA220141A9EAF18B9889B7567E494CC824F5729D49F73D2D39BED2F70E40`.
- Separate build gate: **146 tasks passed**, including `verifySkinSourceBoundary`, `verifyNativeSkinBoundary`, `assembleDebug`, `lintDebug`, and `compileDebugAndroidTestKotlin`. Lint reported 54 warnings and 0 errors; AndroidTest Kotlin compiled but instrumentation was not executed. Build log SHA-256: `5E4C440A7543297105AB18DB77968442940773E270C1170BA2C8812BFBF20B3F`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 67,011,182 bytes, SHA-256 `025FE74F6BF00371A234E2FD6B615FC5F93C64C28670B8A200C4A8DC4A5781D1`.
- `git diff --check`: PASS.

## Verification limits and delivery

The verified local SDK's ADB device list and AVD list were empty. Android instrumentation, real WebView execution, Native/Skin parity, and physical/OEM behavior are **NOT TESTED**. The bounded query and Native routing have no schema, backup-format, formal-state, or frozen business-semantic changes.

Source changes were made on `codex/core-feature-api-event` from `17d99b00d0e0abb8cafe54d9826051904a08c72d` and committed as `9ee927ff33bea9ec1d3a979a613a391ef87e3ffe`. This Issue and the roadmap/progress entries are the separate documentation commit; both commits are intended for fast-forward push to `origin/feat/skin-first-separation`.
