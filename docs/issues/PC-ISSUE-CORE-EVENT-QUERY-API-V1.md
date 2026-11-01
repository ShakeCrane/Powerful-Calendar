# PC-ISSUE-CORE-EVENT-QUERY-API-V1 — Current event query and Host read routing

- **Date:** 2026-11-01 (Asia/Shanghai)
- **Priority:** P1 — PHASE 1 Core Feature API
- **Status:** PASS for verified JVM/build scope; Android instrumentation and packaged WebView behavior NOT TESTED
- **Scope:** Current single-event query DTO; Web event-open prevalidation; AI proposal target capture
- **Code commit:** `e7c2a6bd8a1a1db2a5c5e11b87289b2da229bd31` (parent: `ceec9f16513126008036b62008a47f60c1301e2a`)
- **Phase status:** PHASE 1 remains OPEN; this does not establish full Core extraction or runtime parity.

## Expected behavior

Authorized Host workflows should read current formal-event state through `EventFeatureApiV1` and consume a detached, versioned `EventRecordV1`. Web event-open must retain its editor-availability gate, current-revision check, occurrence validation, and final transactional editor-open validation. AI proposal publication review must reject missing or changed targets before displaying Host confirmation; publishing and staging continue through their existing reviewed Event/AI Proposal operations.

## Implementation

- Added `EventFeatureApiV1.queryEvent(eventSeq)`, which returns one current formal event as a detached DTO and returns `null` for invalid, missing, or retired identities.
- The Web event-open Host route now obtains its current record through the API, maps the DTO into the existing Host validation model, retains `validateOpenEvent`'s writer-availability/revision/occurrence checks, then calls `openFormalEvent`, which rechecks current revision and occurrence before creating the edit session.
- AI proposal target capture reads each selected target through `queryEvent`, checks the reviewed expected revision, and rejects missing, malformed, or stale target sets. The final confirmation and transaction-backed proposal application flow is unchanged.
- No Store schema, recurrence semantics, authorization rules, editor transaction, wire payload, or Native fallback behavior changed.

## Verification evidence

Environment: Windows PowerShell, repository-local Android SDK `E:\android-sdk`, Gradle 8.14.3, offline build. ADB reported no connected device and no configured AVD.

| Check | Result | Evidence |
| --- | --- | --- |
| Event API, Web AI proposal, and Web event-open Compose regression suites | **28/28 PASS** (6 + 15 + 7) | `.local/android-test/reports/core-event-query-api-20261101/focused-event-query-api.log`, SHA-256 `C2B81E4DB5A8F3B7353C4CD52CCE98B4F1AEE962279B189C75EC32D04C707B6F` |
| Forced serial offline gate: both source boundaries, JVM suite, Debug APK, lint, AndroidTest Kotlin compilation | **BUILD SUCCESSFUL**; **845/845 JVM tests**, 134 XML reports, 0 failures/errors/skips; lint 0 errors / 53 warnings | `.local/android-test/reports/core-event-query-api-20261101/full-gate.log`, SHA-256 `73D9260DA774AA4E4FF5A88A09065E91836E801BB15A1DDB29F7FE6959218495` |
| `git diff --check` | **PASS** | Code and documentation diff |
| Debug APK | **66,847,342 bytes** | `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `9C556FA6611FBCD5BF7B1C7CCFD139CDCC17EB722027B98FF4417303D2336A5A` |
| Android instrumentation / packaged Android WebView / physical or OEM behavior | **NOT TESTED** | No connected ADB device or configured AVD; AndroidTest Kotlin compilation is not runtime evidence |

Full-gate command:

```powershell
$env:ANDROID_HOME='E:\android-sdk'
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
```

## Limits and next work

- The Event API and Host/JVM integration suites do not prove production WebView or device behavior.
- Other authorized Host/Core reads and writes remain outside migrated APIs. Store-level Calendar/Memo paging and runtime Skin projections containing Core types remain open Phase 1 work.
- Native fallback remains available; this report does not authorize cutover or promote parity acceptance.

## Source paths

- `core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/test/java/dev/powerfulcalendar/core/api/v1/EventFeatureApiV1Test.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebAiProposalReviewIntegrationTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/PowerCalCasualSuggestionComposeTest.kt`
