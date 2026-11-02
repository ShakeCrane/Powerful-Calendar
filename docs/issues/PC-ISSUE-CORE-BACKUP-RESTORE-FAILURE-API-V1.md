# PC-ISSUE-CORE-BACKUP-RESTORE-FAILURE-API-V1

- **Date:** 2026-11-02
- **Status:** `PASS` for API/Host/JVM/build coverage; device and SAF behavior `NOT TESTED`
- **Phase:** PHASE 1 — Establish Core Feature API
- **Branch:** `codex/core-feature-api-event`
- **Code commit:** `88bfd17222d6e1dadee54fd695c5faf2dbb27501`

## Problem and expected behavior

`ImportExportFeatureApiV1.restoreBackup()` allowed implementation exceptions to cross the Core API boundary. `PowerCalApp` recognized `LegacyBackupConsentRequired` directly, while `RestoreFailureExplanation` inspected `PowerStore.NonEmptyRestoreTargetException`, JSON exception types, and error-message text. The restore safety policy was enforced by Core/Store, but Host behavior was coupled to implementation details and could drift when messages or exception classes changed.

Expected behavior: keep the existing refusal, validation, consent, transaction, and restore semantics; expose stable failure categories through the existing Import/Export API so Host can ask for legacy-file consent and render safe explanations without reading Core exception types or messages.

## Change

Added `BackupRestoreFailureKindV1` and `BackupRestoreExceptionV1` to `:core-api`. The Store adapter maps oversized input, invalid JSON, explicit legacy consent, non-empty target, integrity failure, invalid backup, and other restore errors to those categories. The API exception contains no implementation cause or raw file/provider text. `PowerCalApp` branches on `LEGACY_CONSENT_REQUIRED`; restore feedback maps the other categories to localized, privacy-safe guidance. SAF URI access, picker state, user confirmation, and post-restore scheduler reconciliation remain Host responsibilities.

The empty-target transaction, SHA/reference checks, flat-v2 opt-in, backup format, database schema, and successful restore notifications are unchanged. The regression verifies that a non-empty restore refusal leaves the current draft intact and that the old-format consent gate does not write data.

## Verification

Focused command:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.api.v1.ImportExportFeatureApiV1Test' --tests 'dev.powerfulcalendar.ui.v2.RestoreFailureExplanationTest' --offline --console=plain
```

Result: **15/15 PASS across 2 suites**, 0 failures/errors/skips (10 Import/Export API tests and 5 restore explanation tests). Focused log SHA-256 `2AC1EE694C7F710865E33EC14FFDB2B313B6229B356897E1F85F52DC46790A6D`; focused XML manifest SHA-256 `69E986B641B7E4DCE062885E64CDDB4C0DEB8F45C8A0423AB55CBC2256D6EF5A`.

The complete offline gate passed **929/929 JVM tests across 139 XML suites**, with 0 failures/errors/skips. `verifySkinSourceBoundary`, `verifyNativeSkinBoundary`, Debug APK assembly, `lintDebug` (0 errors / 54 warnings), AndroidTest Kotlin compilation, and `git diff --check` passed; 154 tasks completed.

- APK: **67,060,334 bytes**, SHA-256 `7FF945EA6D387EA28068D27A4A969AAF1937380E68A9CD1EBE1CEEC24C2158F8`.
- Full gate log SHA-256: `73F13C986FE1E6FF868092861F7E122625FE300E00E0250517E4652687652832`.
- Full JUnit manifest SHA-256: `DF8D9526B2C375B9AC595437394819654E0210BDB27B2826C1E4CE49229BF94C`.
- Focused/full reports are under `.local/android-test/reports/phase1-restore-api-errors-20261102-150000/`.

The first focused run exposed one test-classification miss: the adapter matched `hash`, while the existing Core validation message uses `SHA-256`. The mapping was corrected to recognize both forms; the focused rerun and full gate then passed. This was a test-detected adapter classification issue, not a restore data mutation.

## Limits

`adb devices -l` showed no attached device and the local AVD list was empty. Android instrumentation, packaged WebView, actual SAF/provider behavior, and device-level Native/Web parity remain `NOT TESTED`. This report does not close Phase 1 or the Release QA/cutover gates.
