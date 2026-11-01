# PC-ISSUE-CORE-MEMO-API-V1 — Memo query and stable runtime DTO

- **Date:** 2026-11-01 (Asia/Shanghai)
- **Priority:** P1 — PHASE 1 Core Feature API
- **Status:** PASS for the verified JVM/build scope; Android instrumentation and packaged WebView behavior NOT TESTED
- **Scope:** Unified todo projection, Web current-occurrence visibility precheck, and removal of Core entity types from Memo runtime rendering
- **Code commit:** `62a6d12d270d2fa4d3b2f1a5e458b4c26487d4f6` (parent: `b88aff8787fe71d7fd5a491fe135cb19e82d63da`)
- **Phase status:** PHASE 1 remains OPEN. This slice does not establish full Core extraction, Native/Skin parity, or cutover readiness.

## Expected behavior

Memo and unified todo behavior must remain owned by the existing Core recurrence and Store logic while Native and Web rendering consume stable values. The visibility check before a Web completion action must re-read the current event revision, completion state, and current visible occurrence. Repeated occurrence identity, disabled/tracking filters, undated items, date precision, horizon, sorting, and paging must remain unchanged.

## Implementation

- Added pure Kotlin `MemoFeatureApiV1`, `MemoEntryV1`, and `MemoQuerySnapshotV1` to `:core-api`, exposed as `CoreFeatureApiV1.memos`.
- Added `StoreMemoFeatureApiV1`. It projects the immutable Event API snapshot into detached Memo DTOs and uses the existing `RecurrenceEngine`. Its current-entry query re-reads one owner through `PowerStore` inside the adapter and returns only an occurrence that remains visible under the same filters and horizon.
- Preserved enabled and tracking filters, the 365-day lookback, seven-day future horizon, 1200 occurrence limit per repeating event, stable occurrence keys, completion inclusion, and sort order.
- Routed `PowerCalApp.reload`, day refresh, Native and Web Memo state, and the Web completion visibility precheck through the Memo API. The completion transaction remains `EventFeatureApiV1.setOccurrenceCompletion` with expected revision and completion state.
- Changed `MemoVisualContext` and compiled Memo renderers to receive `MemoEntryV1` with row-key callbacks. Native row resolution remains Host-owned. Web JSON fields, 250-entry paging, and command wire format remain unchanged.
- Updated existing JVM and AndroidTest callers to use the API service. No database schema, backup format, Store transaction, recurrence behavior, completion semantics, or default-skin behavior changed.

## Verification evidence

Environment: Windows PowerShell, repository-local Android SDK `E:\android-sdk`, Gradle 8.14.3, offline build. ADB reported no connected device and the SDK had no configured AVD.

| Check | Result | Evidence |
| --- | --- | --- |
| Memo API recurrence-window, unified projection, completion and current-visibility tests | **3/3 PASS**, 0 failures/errors/skips | `.local/android-test/reports/core-memo-feature-api-20261101/focused-memo-api.log`, SHA-256 `A130824BF53051F7144B598C593CA4D3AC3440F40E68B7F017739E217CA1C4A9` |
| Forced serial offline gate: source boundaries, JVM suite, Debug APK, lint, AndroidTest Kotlin compilation | **BUILD SUCCESSFUL**; **844/844 JVM tests**, 134 XML reports, 0 failures/errors/skips; lint 0 errors / 53 warnings | `.local/android-test/reports/core-memo-feature-api-20261101/full-gate.log`, SHA-256 `1C2F57B2DC50310C4631B16300E3E8566D2827EE57DEC05F1297CB0F6E4E496F` |
| `git diff --check` | **PASS** | Final source and documentation diff check |
| Debug APK | **66,847,342 bytes** | `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `239B74AACCB179B048572A5990D15F0351687DD8991BF6384182177BACE92724` |
| Android instrumentation / actual Native UI / packaged Android WebView / physical or OEM device behavior | **NOT TESTED** | No connected ADB device or configured AVD; AndroidTest Kotlin compilation is not runtime evidence |

The forced full-gate command was:

```powershell
$env:ANDROID_HOME='E:\android-sdk'
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
```

## Limits and next work

- `MemoFeatureApiV1.queryEntries` takes the current full `EventQuerySnapshotV1`; Store-level event paging remains unimplemented.
- The current-entry query and JVM/compile evidence do not prove behavior through a device Native screen or packaged Android WebView.
- `DeckVisualContext` still passes Core entity types to a runtime renderer. Web event-open and AI proposal target capture were subsequently routed through `EventFeatureApiV1.queryEvent`; see the [Event current query report](PC-ISSUE-CORE-EVENT-QUERY-API-V1.md).
- Native fallback remains available. This report does not authorize a skin cutover or mark any broader parity row PASS.
- The next PHASE 1 action is to remove Core entity types from runtime Draft Deck projections while retaining Host-owned actions and conflict/restore gates.

## Source paths

- `core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/MemoFeatureApiV1.kt`
- `core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/StoreMemoFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/MemoProjection.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PresentationSkinRuntime.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinMemoProjection.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/TodoProjectionTest.kt`
- `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/MemoFeatureApiTestSupport.kt`
