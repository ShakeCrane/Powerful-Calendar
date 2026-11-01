# PC Issue — TimelineSkinContext stable Core DTO boundary v1

- **Date:** 2026-11-02
- **Phase:** PHASE 1 — Establish Core Feature API
- **Status:** **PASS for the compiled Timeline Skin DTO boundary; PHASE 1 remains open**
- **Code commit:** `5ab465269dd7a62d3d3bd0ca79e1cfc5b74e2be1`
- **Base:** `fcae04186674042e1962317b46a880f5a52f4e9c`

## Gap and result

The compiled runtime Timeline Skin context exposed `TemporalRecord`, `TimelineCoordinate.Camera`, and callbacks typed with Core entities. This made a replaceable renderer depend on Host/Core implementation types.

`TimelineSkinContext.records` now exposes `EventRecordV1`; camera values use `NativeTimelineCamera` from the Native Presentation Contract. Open callbacks carry event sequence, revision, and the optional occurrence key; move callbacks carry event sequence, revision, and target time; create callbacks use `EventKindV1`. The Host adapter resolves callback identities against the current input records before invoking its existing handlers. `Native()` keeps the original `TemporalRecord` list and Core camera private, so Native recurrence projection, original-slot routing, and the `TimelineCoordinate.yOf` implementation remain authoritative. Runtime event rows retain the 70-row cap, and the Active Visual example retains its complete-list behavior. No frozen event semantics, Store schema, default skin, or fallback behavior changed.

- **Expected:** a compiled renderer receives versioned event DTOs and stable callback identities, with the established camera presentation contract and true-time mapping.
- **Actual:** the new regression confirms DTO/camera types, rejects a stale revision at the adapter boundary, routes current open/move/create actions, maps camera changes, and matches `yOf` to the existing Core coordinate function. Existing Native projection and Compose suites pass.

## Contract and checks

- `TimelineSkinContextContractTest`: **1/1** — exposed DTO and camera types, current/stale event identity, occurrence-key open, move, create, camera conversion, and true-time `yOf` mapping.
- `NativeTimelineProjectionTest`: **4/4**; `RuntimeSkinComposeTest`: **6/6**; `TimelineTitleComposeTest`: **10/10**.
- Focused command: `:app:testDebugUnitTest --tests 'dev.powerfulcalendar.ui.v2.TimelineSkinContextContractTest' --tests 'dev.powerfulcalendar.ui.v2.NativeTimelineProjectionTest' --tests 'dev.powerfulcalendar.ui.v2.RuntimeSkinComposeTest' --tests 'dev.powerfulcalendar.ui.v2.TimelineTitleComposeTest' --offline --rerun-tasks --max-workers=1 --console=plain` — **21/21 across 4 XML suites**, 0 failures/errors/skips.
- Full command: `:app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon` — **864/864 JVM tests across 137 XML suites**, 0 failures/errors/skips; both source boundaries, Debug APK assembly, lint, and AndroidTest Kotlin compilation passed.
- Lint: **0 errors / 53 warnings**. `git diff --check` passed.
- Debug APK: **66,896,494 bytes**, SHA-256 `ED8A5C5046E3F8DFAFE7CAE69328020DB4A58DDD2A7F1D22F049A980F604BA0B`.
- Focused log SHA-256: `6ACE86F45C870CC4D0BFDB3CC016124F30F6CFEAC7D80234417E9B557D3A553F`.
- Full-gate log SHA-256: `15376DA770098996C8A855E37C759921CB9DCCE8BACD5DFD37FD1798EF5B2742`.
- Logs are under `.local/android-test/reports/timeline-skin-context-api-dto-20261102/`.
- **Environment:** Windows PowerShell, JDK 20, repository-local Android SDK `E:\android-sdk`; Gradle ran offline, forced reruns, one worker, and no daemon.

## Limits and remaining work

ADB reported no connected device and `emulator -list-avds` returned no configured AVD. Android instrumentation, packaged WebView behavior, actual Native/Skin parity, and physical/OEM behavior are **NOT TESTED**. This change does not establish skin cutover or remove Native fallback.

PHASE 1 remains open. The next bounded gap is the standalone read-only `calendar.query.v1` Core tool dispatch, with existing calendar-sharing authorization and conversation revision fencing; see the current roadmap and [Core Feature API v1](../CORE_FEATURE_API_V1.md).
