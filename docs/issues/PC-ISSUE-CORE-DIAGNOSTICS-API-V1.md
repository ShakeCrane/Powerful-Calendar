# PC-ISSUE-CORE-DIAGNOSTICS-API-V1 — Core Diagnostics Feature API v1

Date: 2026-11-02 (China Standard Time) | Priority: P1
Status: **IMPLEMENTED; scoped verification PASS; PHASE 1 OPEN**

## Goal and result

Move the last two `PowerCalApp.reload()` Store reads—`nextSequencePreview()` and
`identityCount()`—behind a platform-neutral, versioned Core query, preserving their existing
values and read-only behavior.

The pure Kotlin `CoreDiagnosticsFeatureApiV1` now exposes `queryDiagnostics()` and returns only
the two scalar values in `CoreDiagnosticsV1`. The Store adapter delegates to the same existing
read methods. `PowerCalApp.reload()` uses the shared `PowerCalCoreFeatureApiV1` provider; the Skin
continues to receive the existing diagnostics values. The query performs no writes, feature
revision changes, or notifications. The two values are independent reads and carry no shared
transactional revision.

## Verification

| Check | Result |
| --- | --- |
| `CoreDiagnosticsFeatureApiV1Test` | **2/2 PASS**, 0 failures/errors/skips |
| Forced serial `:app:testDebugUnitTest --rerun-tasks` | **881/881 PASS**, 138 XML suites, 0 failures/errors/skips |
| `verifySkinSourceBoundary` and `verifyNativeSkinBoundary` | **PASS** |
| `assembleDebug` | **PASS**, 67,011,182 bytes; SHA-256 `C134A68DEAC844235342ABCCD8DB1F204DA1C089911BA02611F6F961B2EC60C4` |
| `lintDebug` | **PASS**, 0 errors / 54 warnings |
| `compileDebugAndroidTestKotlin` | **PASS**; instrumentation was compiled, not executed |
| `git diff --check` | **PASS** |
| Android device / AVD | **NOT TESTED**; `adb devices -l` listed no device and `emulator -list-avds` returned no AVDs |

Evidence is under the ignored directory `.local/android-test/reports/core-diagnostics-api-20261102/`:

- `focused.log` SHA-256 `15974BD5997125888C1DCF0582016CB215EE98DCA2573C264F25FDCE7BCBD0A9`
- Focused JUnit XML SHA-256 `C26BC031F8C7DC51225F9233C17B28B5995592F6BB262A2C8F648290336C25EF`
- `full-jvm.log` SHA-256 `9FF54462B5817C14A90A8BFF5082D52AD47D782C942A1CCFCEAE7567CB44D168`
- `build-gate.log` SHA-256 `7E27CC79DD208C30DDC2C2D70F9700B9E9345F5B46FFE24FA17C4A3F05F23C9A`

Commands were run from the dated isolated worktree with process-scoped `ANDROID_HOME=E:\android-sdk`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.api.v1.CoreDiagnosticsFeatureApiV1Test' --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
```

## Scope and remaining work

No database schema, backup format, frozen business rule, default Skin selection, or Native fallback
changed. The query does not claim a revision-coherent snapshot. Device, packaged WebView, and
Native/Clear runtime parity remain **NOT TESTED / NOT SIGNED OFF**. PHASE 1 is open.

The next API gap is the Web Bridge's direct retired-event restore page and tombstone reads; see
the current single NEXT_ACTION in the separation roadmap and `PROGRESS.md`.

## Git delivery

Implementation commit `f833d56aff5e07fad831c80bd8900ffa2c1f3f1b` follows
`ef705bfedd5e99fc39f43aa2d616a4dc58707d08` on `codex/core-feature-api-event`. Remote freshness
and fast-forward delivery are recorded after the documentation commit.
