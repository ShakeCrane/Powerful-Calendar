# Core Feature API — bounded recurrence-window query v1

**Status:** Native and Web Host routes require the Core recurrence API and the nullable direct-engine fallback has been removed. Focused and build verification pass; the latest full JVM aggregate remains **FAIL / unresolved** on two integration tests. Android instrumentation, packaged WebView behavior, and Native/Skin parity are not tested.
**Phase:** Core-first / Skin-first PHASE 1 — Establish Core Feature API.
**Baseline:** Code parent `17d99b00d0e0abb8cafe54d9826051904a08c72d` (`origin/feat/skin-first-separation` at implementation start).
**Code commit:** `9ee927ff33bea9ec1d3a979a613a391ef87e3ffe`.
**Web routing follow-up commit:** `d56ba5e4f3ba88204c9aaac2ff473d7ebe2fcecb`.

## Gap

Native and Web timeline/month Host projections expanded recurrence through `RecurrenceEngine` directly. That left these presentation paths outside the versioned Core recurrence service and made it harder to enforce one bounded query contract for renderer windows.

## Contract and implementation

Added `RecurrenceFeatureApiV1.queryOccurrencesInWindow()` over a detached `EventRecordV1` snapshot. `RecurrenceOccurrenceWindowQueryV1` requires a non-empty half-open time window no longer than 43 days and a per-event occurrence cap from 1 through 2,048. The adapter asks the existing `RecurrenceEngine` for one additional slot to report `truncated`, preserving its window-start-prioritized selection and nearest carry-in behavior. The response echoes the event sequence and revision. It is a snapshot projection, not a freshness guarantee or write authorization.

Native and Web timeline/month Host projections now call the shared service with their existing 17-day and 42-day windows. The Host verifies the echoed identity and keeps the prior date-only deadline normalization, original occurrence key, DST/local-zone semantics, visible interval filter, ordering, and output caps. `PowerCalApp` supplies the same recurrence service to both adapters; the service remains private to Host contexts and is not exposed to Skin renderer data. Every projection helper requires the API. Standalone Compose preview/test entry points use a stable stateless `CoreRecurrenceFeatureApiV1` adapter default; no direct-engine projection branch remains. No database, backup, event state, or frozen recurrence semantics changed.

## Verification

- Focused regressions: **50/50 PASS across 6 XML suites** (`RecurrenceFeatureApiV1Test`, `GlassPresentationTest`, `MonthVisualContextContractTest`, `NativeMonthProjectionTest`, `NativeTimelineProjectionTest`, `WebSkinRecurrenceBridgeTest`). Focused log: `.local/android-test/reports/phase1-recurrence-window-api-20261102/focused.log`, SHA-256 `A7B0CFD81F32A9ABF244FD7382D1DFEC37379616E314C5872FD8636EAC7A4A81`.
- Final forced serial JVM gate: **896/896 PASS across 139 XML suites**, 0 failures, errors, or skips. JUnit manifest: `.local/android-test/reports/phase1-recurrence-window-api-20261102/full-junit-manifest.csv`, SHA-256 `3D3D0B7725BE6138DA8A09E0520F9DF4D3C2F5DF737760C7BE1BDF91DA77E65B`. Final full log SHA-256: `508E00EFD6A011093801D218D0C15EA8A3A023F4EAF81EEA3B29B8264A0944A5`.
- Run history: an earlier final-source full serial run had 2 failures in 896 tests. Each exact failing method passed in isolation (**2/2**); the subsequent forced serial full gate passed. The isolated log is `aggregate-failures-isolated.log` in the report directory, SHA-256 `3313CA220141A9EAF18B9889B7567E494CC824F5729D49F73D2D39BED2F70E40`.
- Separate build gate: **146 tasks passed**, including `verifySkinSourceBoundary`, `verifyNativeSkinBoundary`, `assembleDebug`, `lintDebug`, and `compileDebugAndroidTestKotlin`. Lint reported 54 warnings and 0 errors; AndroidTest Kotlin compiled but instrumentation was not executed. Build log SHA-256: `5E4C440A7543297105AB18DB77968442940773E270C1170BA2C8812BFBF20B3F`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 67,011,182 bytes, SHA-256 `025FE74F6BF00371A234E2FD6B615FC5F93C64C28670B8A200C4A8DC4A5781D1`.
- `git diff --check`: PASS.

## Web adapter routing follow-up (2026-11-02)

`PowerCalApp` now passes `coreFeatureApi.recurrence` to the Web timeline JSON projection, month projection, and timeline-move visible-revision projection. The Web Host calls `queryOccurrencesInWindow()` for each detached event snapshot and keeps its 17-day and 42-day windows, date-only handling, visible-window selection, ordering, owner/item limits, and JSON identity fields. A projection regression compares the API-backed JSON against the existing route for multiple owners in deliberately reversed order and verifies the requested half-open windows and 2,048 occurrence cap. A Web month-to-formal-owner regression verifies the original occurrence key flows into Web event opening.

- Focused `WebSkinHostProjectionTest` and `WebSkinRecurrenceBridgeTest`: **PASS**. Log SHA-256 `D68DF88CBA10781934CDE5988154B209C87CF226DF7DB6FB6793437CED27031C` at `.local/android-test/reports/phase1-web-recurrence-window-api-20261102/focused-final.log`.
- Those two suites plus `WebSkinSystemDiagnosticsIntegrationTest` passed together. Group log SHA-256 `78EA2FE3471C55FA6E6507C152AFF5118C2155F352C34219F2C4836DE3872D42`.
- Two final-source forced serial full JVM runs each completed **897 tests with the same single failure**: `WebSkinSystemDiagnosticsIntegrationTest.settingsProjectsNativeDiagnosticsAndTimelineReceivesNoSystemDiagnostics` asserted three recurrence preview rows and observed zero. The exact test passed alone (**1/1**) and in the targeted three-class group; no cause was established, so aggregate JVM status remains **FAIL / unresolved**. Full log SHA-256 values: `51A8154B949DF2049B0FB318CF2C171D61509A7BA694AB417333DDD7D844EA13` and `04EFE81A06339DA14617043E9274E1CE16973DDE50574A922A877AD9CFBA3D73`.
- Independent build gate: **146 tasks PASS**, including both source boundaries, `assembleDebug`, `lintDebug` and `compileDebugAndroidTestKotlin`; lint reported **54 warnings / 0 errors**. Build-only log SHA-256 `4208D5D1AF60101661470471D94F2E4EDDFF2BDE020D23AF0A5C5D9F75F2C557`.
- APK: 67,011,182 bytes, SHA-256 `B9EF4093E09297F6A7883CF5A43AD9B487DC3555FFE7AEED8526A21DF76DE721`.
- ADB and AVD lists were empty. Instrumentation, actual WebView execution, Native/Skin parity, and device/OEM behavior remain **NOT TESTED**.

## Required API dependency and fallback removal (2026-11-02)

Native/Web timeline and month projection helpers and their Host contexts now require a non-null `RecurrenceFeatureApiV1`. The old one-off and repeating `RecurrenceEngine` fallback overloads are removed. Production `PowerCalApp` continues to inject the shared provider; standalone Compose previews use the stable stateless Core adapter. The existing 17-day/42-day windows, ordering, date precision, occurrence identity, filters, and limits remain unchanged.

- Focused Compose/projection regressions: **101/101 PASS across 11 XML suites**. Log SHA-256 `5CD4AE81E5F339A96D3F08EF2AB11D0D45471A3BC310A7AE625E5243470A2C65`.
- Forced serial full JVM: **897 tests, 2 failures**: `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` timed out waiting for the Memo projection, and `WebSkinSystemDiagnosticsIntegrationTest.settingsProjectsNativeDiagnosticsAndTimelineReceivesNoSystemDiagnostics` observed zero instead of three recurrence preview rows. Both exact methods passed one isolated rerun (**2/2**); no cause was established, so aggregate status remains **FAIL / unresolved**. Full log SHA-256 `F70825E3A0BF151B1895DF818C739C13829005BB63CA0BBE2CA6316F01719600`; isolated log SHA-256 `3313CA220141A9EAF18B9889B7567E494CC824F5729D49F73D2D39BED2F70E40`.
- Separate forced offline build gate: **146 tasks PASS**, including both source boundaries, Debug APK, lint (**0 errors / 54 warnings**) and AndroidTest Kotlin compilation. AndroidTest was compiled, not executed. Build log SHA-256 `E01BAD253F5D461EBD738B382D9B3C9F73B9B8AA87640A89D84695335EF41CDE`.
- Debug APK: **67,011,182 bytes**, SHA-256 `0C697DC22080A88B33102E3AC1A8C57DEE136821264455068E64042AEDBD0E62`.
- `git diff --check`: PASS. ADB and AVD lists were empty; device, packaged WebView, Native/Skin parity, and OEM behavior remain **NOT TESTED**.
- Code commit `750245f` follows `c3f4af8720ada05b4e3e0a8547c6df015f384dad`. Focused/full/isolated/build logs are under `.local/android-test/reports/phase1-recurrence-api-required-20261102/`.

## Verification limits and delivery

The verified local SDK's ADB device list and AVD list were empty. Android instrumentation, real WebView execution, Native/Skin parity, and physical/OEM behavior are **NOT TESTED**. The bounded query and Native/Web routing have no schema, backup-format, formal-state, or frozen business-semantic changes.

Native source changes were made on `codex/core-feature-api-event` from `17d99b00d0e0abb8cafe54d9826051904a08c72d` and committed as `9ee927ff33bea9ec1d3a979a613a391ef87e3ffe`. Web source changes are based on `3c19fc175304400ccb274514e2f61e9d7d8c4aad` and committed as `d56ba5e4f3ba88204c9aaac2ff473d7ebe2fcecb`. This updated Issue and the roadmap/progress entries are the separate documentation commit before fast-forward push to `origin/feat/skin-first-separation`.
