# PC-ISSUE-CORE-DIAGNOSTICS-API-V1 — Core Diagnostics Feature API v1

Date: 2026-11-02 (China Standard Time) | Priority: P1
Status: **IMPLEMENTED; scoped verification PASS; PHASE 1 OPEN**

## Goal and result

Move the last two `PowerCalApp.reload()` Store reads—`nextSequencePreview()` and
`identityCount()`—behind a platform-neutral, versioned Core query, preserving their existing
values and read-only behavior.

The pure Kotlin `CoreDiagnosticsFeatureApiV1` exposes `queryDiagnostics()` and returns platform-
neutral values in `CoreDiagnosticsV1`. The Store adapter delegates the count reads and supplies
schema/database metadata. `PowerCalApp.reload()` uses the shared `PowerCalCoreFeatureApiV1`
provider; Native and Web Settings receive the same diagnostics values. The query performs no
writes, feature revision changes, or notifications. The values are independent reads and carry no
shared transactional revision.

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

## Follow-up — Recurrence preview API (2026-11-02)

`RecurrenceFeatureApiV1.queryNextOccurrence()` now calculates the settings preview from a detached
`EventRecordV1` and returns the original recurrence-slot key, interval, skipped/moved flags, and
snapshot identity. The adapter delegates to the existing `RecurrenceEngine`; this changes the
calculation boundary without changing recurrence policy or persistence.

`PowerCalApp` keeps its revision-fenced, paged Event scan and selects the first three repeating
events. The shared API calculates previews using the current instant and local zone, and both
Native and Web Settings projections consume the same API DTOs. This covers the settings preview
only; it does not route other recurrence computations or add recurrence edit commands.

### Follow-up verification

| Check | Result |
| --- | --- |
| Focused Core Diagnostics, recurrence API, Native Settings, and Web diagnostics suites | **13/13 PASS** across 5 XML suites; 0 failures/errors/skips |
| Forced serial offline JVM and build gate | **888/888 PASS** across 139 XML suites; 0 failures/errors/skips |
| `verifySkinSourceBoundary` and `verifyNativeSkinBoundary` | **PASS** |
| `assembleDebug` | **PASS**, 67,011,182 bytes; SHA-256 `B0ABF5B94B34CE2E699AC1C1D48AE0AC3ADCB525990186B44C7D0BDCDA8BB5C3` |
| `lintDebug` | **PASS**, 0 errors / 54 warnings |
| `compileDebugAndroidTestKotlin` | **PASS**; instrumentation compiled, not executed |
| Android device / AVD | **NOT TESTED**; no connected device or configured AVD |

Evidence is under `.local/android-test/reports/phase1-recurrence-feature-api-20261102/`:

- `focused-recurrence-api-final.log` SHA-256 `7173BD79C7C73C7AFA28FF93AC4782B0E017E8031A36E54678EBA6369FABFDB6`
- `focused-junit-manifest.csv` SHA-256 `EF6E5D27F7B3F6B27D7D4FF1717B53EFAD93917A98B74A7378F26E4FC9E66E06`
- `full-gate.log` SHA-256 `0E0217931863895A408115D524D08E0EA23E119359937B3138AABD857AE442BE`
- Full JUnit suite manifest: `full-junit-manifest.csv` (139 suites, 888 tests)

The APK was built from this worktree and was not installed. No database schema, backup format,
formal event state, or frozen recurrence semantics changed. Packaged WebView, Native/Clear parity,
instrumentation, and physical/OEM behavior remain **NOT TESTED / NOT SIGNED OFF**.

## Scope and remaining work

No database schema, backup format, frozen business rule, default Skin selection, or Native fallback
changed. The query does not claim a revision-coherent snapshot. Device, packaged WebView, and
Native/Clear runtime parity remain **NOT TESTED / NOT SIGNED OFF**. PHASE 1 is open.

The previously noted Web Bridge retired-event page and tombstone query gap has since been addressed
by `DraftReviewFeatureApiV1`; see the [retired-event query Issue](PC-ISSUE-CORE-DRAFT-RETIRED-QUERY-API-V1.md).
Web occurrence-by-key reads have since been routed through `RecurrenceFeatureApiV1`; see the
[Recurrence API Issue](PC-ISSUE-CORE-RECURRENCE-API-V1.md) for its contract and current verification.
The user's draft-create replay decision is now durable across restart and backup restore; the Event
API implementation and its evidence are recorded in the Event API Issue. The current audit
identified the explicitly confirmed legacy database migration as a remaining Store bypass:
`PowerCalApp` invokes `LegacyImporter` with a Store reference instead of routing each imported row
through the existing `ImportExportFeatureApiV1` structured-import operations. This migration
should preserve its existing user confirmation, read-only source, source-link idempotence and
warning behavior.

## Git delivery

Implementation commit `f833d56aff5e07fad831c80bd8900ffa2c1f3f1b` follows
`ef705bfedd5e99fc39f43aa2d616a4dc58707d08` on `codex/core-feature-api-event`. The recurrence
follow-up code commit `17fa37b05d6c0384caa2966d1bfcf2513f83cb83` and documentation commit
`3846429cbbfac794bda2c581dfa0295b09ed7974` were fast-forward pushed to
`origin/feat/skin-first-separation` after fetch confirmed remote base
`e3b6ba95e07d00a59b207c7226109281a2b0b2b9`.

## Follow-up — Native/Web storage diagnostics use the Core DTO (2026-11-02)

The initial API slice still left `PowerStore.VERSION` and `PowerStore.DATABASE_NAME` in both
`PresentationSkinRuntime` and `WebSkinSystemDiagnosticsProjection`. Added `databaseVersion` and
the actual `store.databaseName` to the existing `CoreDiagnosticsV1` result, passed them through
`PowerCalApp.reload()`, and removed the two presentation-layer Store imports. Native display and
Web JSON retain the same fields; an isolated Store now reports its actual name. No Store schema,
backup format, event state, or mutation behavior changed.

- Focused `CoreDiagnosticsFeatureApiV1Test`, `NativeToolsPageProtectedSlotsComposeTest`,
  `WebSkinSystemDiagnosticsProjectionTest`, and `WebSkinSystemDiagnosticsIntegrationTest`:
  **11/11 PASS**, 4 suites, 0 failures/errors/skips. Focused log SHA-256
  `17BBBDA42750B9586300C659C894731E0EDEBF7C5CCE8AB56D462EDFC673065C`.
- Forced serial offline full gate:
  `:app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin`
  **PASS**, 921/921 JVM tests, 139 XML suites, 0 failures/errors/skips; 154 tasks. Both source
  boundaries, Debug APK, lint (**0 errors / 54 warnings**), AndroidTest Kotlin compilation, and
  `git diff --check` passed. Instrumentation was compiled, not executed.
- Debug APK: 67,060,334 bytes, SHA-256
  `F7663AA6A4837D10B6073104156A7F2CDC1EA8E7891FE20C39D9AC5BDD958C58`.
  Full gate log SHA-256
  `5093FFF4E19340D1D171D93471E1CD6F8168237373B9599318A204D6AA978BA7`; full JUnit manifest SHA-256
  `87F9642B0BF15A12E4FB3D158F40E6F96BCD4A1D0F539EBAC4123FDA8027DBAF`. Evidence is under
  `.local/android-test/reports/core-diagnostics-projection-20261102/`. No device was connected;
  Android runtime checks remain **NOT TESTED**.
- Code commit: `adb677e` (`分离：通过Core诊断API投影数据库信息`).

This closes the two presentation-layer metadata references only. The remaining Host workflow
audit continues; PHASE 1 is open.
