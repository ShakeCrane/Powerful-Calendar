# PC Issue — MonthVisualContext stable Core DTO boundary v1

- **Date:** 2026-11-02
- **Phase:** PHASE 1 — Establish Core Feature API
- **Status:** **PASS for the compiled Month Skin DTO boundary; PHASE 1 remains open**
- **Code commits:** `50739b9e0065753c36949b17bc2f9994e7376577`, `29de5e937fd17af4eaaedacc57e7cc3e85a3e8dc`
- **Base:** `786b4a23c56e884a22d6eb24ac206462bc90bb25`

## Gap and result

The compiled runtime Month Skin context exposed `TemporalRecord` in its current records, per-month query, and open callback. That tied the Skin projection to an internal Core entity despite Calendar API v1 already returning `EventRecordV1`.

`MonthVisualContext.records` and `recordsForMonth` now expose `EventRecordV1`; `onOpen` carries only `eventSeq`, `revision`, and the optional occurrence key. The context keeps its `TemporalRecord` snapshots private behind `Native()`. Native month recurrence expansion, month geometry, row-key resolution, and original occurrence keys continue through the existing Host projection. Runtime event rows consume the stable DTO and retain the existing ordering, summary text, and 70-row cap. The Native Presentation Contract and frozen event semantics were not changed.

- **Expected:** compiled Month Skin code can read versioned event snapshots and request an open by stable event identity without depending on a Core entity type.
- **Actual:** the new compile-time DTO regression passes; the callback preserves the same event identity, revision, and occurrence key, and the Native projection regression and Compose suite pass after the change.

## Contract and checks

- `MonthVisualContextContractTest` verifies the Skin-visible collections are typed as `EventRecordV1`, preserves DTO values, resolves the requested month, and routes event identity, revision, and occurrence key to the Host callback.
- `projectRuntimeSkinEventRows` preserves time ordering, timed/date-only summaries, and the prior 70-row limit.
- `NativeMonthProjectionTest` and `GlassComposeTest` cover the existing Native month projection/Compose paths after the boundary change.
- Focused command: `:app:testDebugUnitTest --tests 'dev.powerfulcalendar.ui.v2.MonthVisualContextContractTest' --tests 'dev.powerfulcalendar.ui.v2.NativeMonthProjectionTest' --tests 'dev.powerfulcalendar.ui.v2.GlassComposeTest' --tests 'dev.powerfulcalendar.ui.v2.DirectEventEditorComposeTest' --offline --rerun-tasks --max-workers=1 --console=plain` — **33/33 PASS across 4 XML suites**, 0 failures/errors/skips.
- Full command: `:app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon` — **863/863 JVM tests across 136 XML suites**, 0 failures/errors/skips; both source boundaries, Debug APK assembly, Lint, and AndroidTest Kotlin compilation passed.
- Lint: **0 errors / 53 warnings**. `git diff --check` passed.
- Debug APK: **66,912,878 bytes**, SHA-256 `3CF8D9B979FBFF4012C4E7006D8BAF98FAC84B84CD0E33EDE841EAF6404B90D5`.
- Focused log SHA-256: `A9C4BCC0B7C2705533359F03ED22FEC5C5E84C7932DA7BDC8452C76CE158BFC8`.
- Full-gate log SHA-256: `92DA0C84A1084E5AB6CE2A6A923F163AC7BD9AFD0944908A686F2E2B354C2A50`.
- Final logs are under `.local/android-test/reports/month-visual-context-identity-20261102/`; earlier pre-identity-hardening evidence is preserved under `.local/android-test/reports/month-visual-context-api-dto-20261102/`.
- **Environment:** Windows PowerShell with repository-local Android SDK `E:\android-sdk`; Gradle ran offline, with forced reruns, one worker, and no daemon. `git diff --check` passed.

## Limits and remaining work

ADB reported no connected device and `emulator -list-avds` returned no configured AVD. No device screenshot or instrumentation result is available. Android instrumentation, packaged WebView behavior, actual Native/Skin runtime parity, and physical/OEM behavior are **NOT TESTED**. This is a compiled runtime context boundary and does not establish skin cutover or remove the Native fallback.

`TimelineSkinContext` still exposes `TemporalRecord` data to runtime Skin code. Other authorized Host/Core paths and the remaining Phase 1 contracts are also open; see the roadmap and [Core Feature API v1](../CORE_FEATURE_API_V1.md). The next projection slice is `TimelineSkinContext`.
