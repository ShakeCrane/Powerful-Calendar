# PC-ISSUE-CORE-SETTINGS-DATA-API-V1

- Date: 2026-11-01
- Status: **PASS** for the Settings/Data API, Native/Web Host routing, and listed JVM/build gates; device behavior **NOT TESTED**
- Branch: `codex/core-feature-api-event`
- Baseline: `86e427fdcd18b779946880755623088eb75f90c6`
- Code commit: `5795d3e581d55b95c84c482f714fbc2876d98472`

## Scope

Provide one versioned service for the local profile name and the one-time system Calendar import
prompt preference. These were read or written directly by Native Host UI, Web profile handling, and
`PowerCalApp`. Preserve the existing SharedPreferences files and keys. Keep credential storage,
AI configuration/file consent, Android permission and picker flows, skin-package management, and
Import/Export file I/O within their existing Host/API boundaries.

## Implementation

- Added `SettingsDataFeatureApiV1` to the pure Kotlin `:core-api` module and exposed it as
  `CoreFeatureApiV1.settingsData`.
- Added the Android adapter behind the API. It reads/writes the existing
  `powercal-profile/displayName` and `powercal-onboarding/system-calendar-import-asked` entries.
  No preference migration, database/schema change, or backup-format change was made.
- Profile writes preserve the existing Web bound of 80 UTF-16 code units and reject ISO control
  characters. Empty names remain allowed. Prompt marking is a one-way idempotent command.
- Changed Native profile save, Web profile open/save, and the first-run system Calendar import
  prompt state path to call the same `PowerCalCoreFeatureApiV1` instance. The Web Host still
  enforces the open-editor/page/active-skin access gate and only projects the name while the editor
  is open.
- Mutations return typed receipts, advance a process-local feature revision only when state
  changes, and publish typed changes. Contextless API instances report unavailable storage.
- The API and DTOs carry no Android, SharedPreferences, Store, Core entity, API-key, or credential
  types. AI settings and secrets remain in the protected Host slot.

## Verification

Focused command:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:testDebugUnitTest --offline --tests 'dev.powerfulcalendar.core.api.v1.SettingsDataFeatureApiV1Test' --tests 'dev.powerfulcalendar.ui.v2.NativeToolsPageProtectedSlotsComposeTest' --no-daemon --console=plain
```

Result: **6/6 PASS** across the Settings/Data API and protected Settings renderer suites. The new
API suite covers existing preference compatibility, shared service identity, validation,
idempotence, revisions/change notifications, contextless behavior, and Android/secret-free API
signatures.

Full gate:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
```

| Check | Result |
| --- | --- |
| `:app:verifySkinSourceBoundary` | **PASS** |
| `:app:verifyNativeSkinBoundary` | **PASS** |
| `:app:testDebugUnitTest` | **837/837 PASS**, 133 XML suites, 0 failures/errors/skips |
| `:app:assembleDebug` | **PASS**, 66,798,190 bytes; SHA-256 `34F4444F26C4EF0BA24AB0F3EEA0AA5DC4151469EFE745CF22889162A890081F` |
| `:app:lintDebug` | **PASS**, 0 errors / 53 warnings |
| `:app:compileDebugAndroidTestKotlin` | **PASS**; instrumentation was not run |
| `git diff --check` | **PASS** |

Ignored evidence is under `.local/android-test/reports/settings-data-feature-api-20261101/`:

- `focused.log`: 4,867 bytes; SHA-256 `01CD0820146FA2585AE9E8411C319E50EBB6187AF54D388647A8F8D4B5EEBA93`
- `full-gate.log`: 15,242 bytes; SHA-256 `38A3F6D5A61915F635FCEB714AE781686E649A4AE1000D47C3167DFF811960EB`

The first focused attempt lacked `ANDROID_HOME` and stopped before compilation/tests. Re-running
with the verified process-local `E:\android-sdk` completed the focused and full gates above.

## Limits and remaining work

- `adb devices -l` listed no device, and `emulator -list-avds` returned no AVD. Settings UI,
  packaged WebView, permission dialogs, and device restart behavior remain **NOT TESTED**.
- AndroidTest Kotlin sources compiled but instrumentation did not run. JVM results do not establish
  system Settings or provider behavior.
- This slice does not complete PHASE 1 or SEP-11. AI batch/draft-generation APIs, remaining
  Core-backed Host paths, runtime Skin projections with Core types, full Native/Skin parity, and
  device/OEM evidence remain open.
