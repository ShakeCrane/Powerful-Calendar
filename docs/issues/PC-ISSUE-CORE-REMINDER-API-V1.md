# PC-ISSUE-CORE-REMINDER-API-V1 — Reminder Feature API v1

- **Date:** 2026-11-01 (Asia/Shanghai)
- **Priority:** P1 — PHASE 1 Core Feature API
- **Status:** IMPLEMENTED / VERIFIED for JVM and build gates; Android OS delivery and device behavior NOT TESTED
- **Scope:** Shared Native/Web Host queries and reconciliation for existing reminder state
- **Code commit:** `03af508c20d7239b35785fae52876f4bcc9a169e`, on `codex/core-feature-api-event`; included in the fast-forward update to `origin/feat/skin-first-separation`
- **Phase status:** PHASE 1 remains OPEN. This slice does not claim complete domain coverage, runtime separation, Native/Skin parity, or cutover.

## Problem and expected behavior

Reminder status and scheduling were assembled in Host code from Store reads and direct scheduler calls. The Core Feature API had no versioned reminder contract shared by Native and Web adapters. The new API exposes durable reminder state and supported recovery operations through one API instance while preserving the current Store, scheduler, timing policy, authorization boundary, and frozen behavior.

## Implementation

- Added platform-neutral `ReminderFeatureApiV1` DTOs and operations in `:core-api`: permission snapshot, per-event reminder snapshot, trigger requirement and schedule state, reconciliation mode/status, and bounded outcome receipt.
- Exposed `CoreFeatureApiV1.reminders` and added the Android Host adapter `AndroidReminderFeatureApiV1`. It reads the existing `PowerStore` schedule intent and execution marker, checks platform permissions through `ReminderScheduler`, and delegates reconciliation to `ReminderScheduler.reconcile` or `reconcileAll`.
- Native Tools, Web Settings diagnostics, lifecycle reconciliation, and the AI batch post-publication reconciliation use the shared API. User-requested manual recovery uses `EXHAUSTIVE`; ordinary lifecycle work remains `PENDING` and bounded. Settings-page launch and OS receivers remain Host/platform operations.
- Receipts report attempted/failed work and pending/blocked counts capped at 401 (`401` means at least 401). They are in-memory operation results: no durable reconciliation receipt or claim of actual OS delivery was added. A contextless adapter reports permission fields as `null` and returns `UNAVAILABLE` without changing durable intents.
- No reminder timing/policy, `PowerStore`, `ReminderScheduler`, receiver behavior, database schema, or backup format was changed. Native fallback remains available; no default switch or cutover was made.

## Verification evidence

All logs are under ignored `.local/android-test/reports/reminder-feature-api-20261101/` in the isolated worktree. Hashes are SHA-256.

| Check | Result | Evidence |
| --- | --- | --- |
| Focused API, Web manual recovery, and diagnostics suites | **10/10 PASS** | `focused.log` — `AD4FBA7E4822336AA70ACB62B0BBFF8DE51590C5E34BA58B31D84FB530433348` |
| Related API, receiver, and Host regression classes in final JUnit XML | **49/49 PASS** across 9 classes | `app/build/test-results/testDebugUnitTest/` from the final serial gate |
| Forced serial offline full gate: source boundaries, full JVM, Debug APK, lint, AndroidTest Kotlin compilation | **BUILD SUCCESSFUL**; JVM **828/828**, 130 XML reports, 0 failures/errors/skips; lint 0 errors / 54 warnings | `full-gates-serial.log` — `5C3552D8DC98C6599737835B26DADC3BA07AA261F2F876C1E081A225AF02E7B3` |
| Supplemental related regression run | **BUILD SUCCESSFUL** | `supplemental.log` — `CB60044371F687ECC7775350F2A9606EE005C2316D1C490A03D0E013B0CB75E6` |
| Debug APK | **66,749,038 bytes** | `app/build/outputs/apk/debug/app-debug.apk` — SHA-256 `291A5E275D625ABEBB602DCABE80E19A91258E16FA572F4B5D2641B0E8A2A999` |
| `git diff --check` | **PASS** | Run after implementation and again before documentation commit |

The final full-gate command was:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain
```

The supplemental regression classes were `ReminderValidationTest`, `ReminderCompletionIsolationTest`, `BootReceiverRecoveryTest`, `WebSkinExactAlarmSettingsIntegrationTest`, `NativeToolsPageProtectedSlotsComposeTest`, and `EventFeatureApiV1Test`, in addition to the three focused classes. The final full XML provides the per-class totals.

## Limits and separate unverified observation

- Android instrumentation, actual OS AlarmManager delivery, packaged Android WebView behavior for this API slice, and physical/OEM behavior are **NOT TESTED**. Robolectric scheduler tests do not establish OS delivery.
- A separate code-inspection observation suggests occurrence completion followed by reopening may leave a reminder intent revision stale because completion changes the event revision. This was **NOT TESTED or reproduced** in this slice. Frozen `PowerStore` behavior was not changed; any proposed core correction requires separate evidence and review.
- The API operation receipt is not persistent and does not certify the receiver fired or the user saw a notification. Permission snapshots do not replace Host confirmation or system-settings flows.
- Native fallback remains intact. No parity row is marked PASS from this API integration.

## Source paths

- `core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/ReminderFeatureApiV1.kt`
- `core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/AndroidReminderFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PresentationSkinRuntime.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinReminderDiagnosticsProjection.kt`
- `app/src/test/java/dev/powerfulcalendar/core/api/v1/ReminderFeatureApiV1Test.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinReminderDiagnosticsProjectionTest.kt`
