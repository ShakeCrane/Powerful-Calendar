# PC-ISSUE-SEPARATION-SYSTEM-CALENDAR-IMPORT

Date: 2026-10-30

Scope: `S0-ST-11` — system calendar import through the Clear Host bridge

Status: **PARTIAL / HOLD**. The automated Host/Store slice passes. API 35 Clear WebView dispatch and real Android permission denial/grant dialogs are now tested against an empty provider; import from a non-empty device Calendar Provider and owner-side device acceptance remain untested.

## Expected behavior

The host-owned `calendar.import.system` action must check `READ_CALENDAR` before reading the provider. It imports a bounded one-time snapshot using stable external IDs, reports rejected rows, and supports safe retry after partial progress. It must not turn an interrupted operation into an unfinished migration batch or duplicate already imported events.

## Root cause and changes

The Clear option, bridge allowlist, permission launcher, and import function already existed. The parity matrix's statement that Clear had no import command was stale. The actual defect was in failure handling: `finishMigration()` ran only after the full import loop returned normally. An exception after one event committed left the batch open. The action also ignored `ImportOutcome.Rejected`, and the provider query derived its two range endpoints from separate `LocalDate.now()` calls.

The fix now captures the created batch ID before the IO dispatcher returns it to the caller, finalizes any created batch in a non-cancellable `finally`, records interruption/rejection warnings, and reloads/reconciles previously committed rows after a partial failure. Retries continue to rely on the existing `(source, externalId)` transaction key. `SystemCalendar` captures the local date once for the `-30` to `+365` day query window.

## Test evidence

- **Red reproduction before the production fix:** `SystemCalendarImportTest.systemImportClosesAndRetriesIdempotently` timed out after 20 seconds waiting for the first batch to finish. This run preceded log capture, so its console output has no standalone evidence file or hash.
- **Focused final run:** 32/32 selected tests passed across `SystemCalendarTest`, `SystemCalendarImportTest`, `WebSkinBridgeTest`, and `WebSkinHostProjectionTest`. Coverage includes permission denial with zero provider queries and zero Store changes; query columns, bounded local-date window, ordering, invalid-duration filtering, stable IDs; cancellation at batch creation; rejection warnings under an AI-lock race; an injected mid-import SQLite failure; batch finalization; retry and repeat-import idempotence; reminder-intent creation; and Bridge/Host option registration. Log: `.local/android-test/reports/separation-s0-st11-system-import-20261030/targeted-final.log`, SHA-256 `E5D821051DD359FF7ECFEC0E4A7141EEDF00DCBB25DEFFC73B27D3D5E10AA9C7`.
- **Full offline Gradle:** `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --console=plain` succeeded. **673/673 JVM tests, 89 suites, 0 failures, 0 errors, 0 skipped**; Debug APK and lint completed. Log: `.local/android-test/reports/separation-s0-st11-system-import-20261030/full-gradle-final-r3.log`, SHA-256 `4E7E3FAD9020986360E495C1D2670D766E5103C1BEB033ACD54D4AEC415A2095`.
- **Debug APK:** `app/build/outputs/apk/debug/app-debug.apk`, 67,498,300 bytes, SHA-256 `DBD9E1844510F6BFCD9C24880F4CB2D4A86ED05F11DD34FD2C04DEDBD752A221`.
- **Device availability:** `adb devices -l` returned only its header; no Android device was connected.

## Remaining HOLD items

- Import from a non-empty Android Calendar Provider, revocation from system Settings, and physical/OEM acceptance remain **NOT TESTED**. The new test exercises the production Clear WebView → Host option path and real OS prompt; the isolated AVD provider is empty, so it does not claim imported-event parity.
- Current code maps imported rows to `ACTIVITY` with a zero-minute offset and creates a PowerCal reminder at the event start. It does not read/copy source Calendar Provider reminders. The contract mentions a reminder intent but does not specify source-reminder import versus this default versus disabled reminders. The test records current behavior; it does not constitute owner approval of a new policy.
- The provider adapter reads `ALL_DAY`, but the current `CalendarEvent.toRecord()` conversion does not preserve that flag explicitly. The new fixture uses timed events, so all-day import semantics remain **NOT TESTED** and need a contract/model decision before full sign-off.
- A store rejection is persisted as a migration warning and surfaced in the Host outcome; the deterministic AI-lock-race test verifies this path. No Studio Mock or production WebView UI replay was added because neither can verify the Android provider or permission screen.

Commit SHA is recorded in `docs/agent-handoff/PROGRESS.md` after the implementation commit is created.

## Follow-up — Clear permission feedback in the actual WebView (2026-10-31)

The permission denial was reaching the existing Host callback, which set the Compose-only `message`. Clear is a full-canvas WebView and had no matching Host-state field or renderer; its existing `.toast` styling was unused. The host now projects transient `hostNotice` text (maximum 1,000 characters, null when inactive, cleared by the existing five-second Host timeout). Clear renders it through a visible `role="status"` / `aria-live="polite"` region using `textContent`. No Bridge action, write capability, Core behavior, or persistence was added. The canonical `.pcskin` was rebuilt from the editable Studio source.

### Evidence

- API 35 / Android 15 / WebView 124.0.6367.219, AVD serial `emulator-5570`: the real Clear Settings → `calendar.import.system` → Android permission-dialog path was tested for **Deny** and **Allow**. The empty system provider leaves the Store and migration snapshot unchanged. The WebView notice text, visible bounds, `role=status`, and `aria-live=polite` are asserted. Complete `ClearSettingsOptionsInstrumentedTest`: **9/9 PASS**. Log: `.local/android-test/reports/sep11-clear-calendar-permission-20261031/clear-settings-instrumentation.log`, SHA-256 `1C5213CF705822C08C6F5E92457064C7C3FBCE0A02A70F3153E803BB729DECC4`.
- Before/after API 35 WebView screenshot: `.local/android-test/reports/sep11-clear-calendar-permission-20261031/after-denial-fixed.png`, SHA-256 `9B994074DBF52A5425F70DB99FEB3E09D02F075A71023B5C8B9CB2DE461D7609`. The test restored the original denied permission state and user-decision flags; no Calendar Provider rows or test Downloads file remain.
- Skin Studio Python suite: **63/63 PASS**. Chrome 156/CDP source replay verifies Host notice rendering and accessibility; this browser replay is separate from the Android evidence. `node --check` passed and the Clear `.pcskin` verifies against canonical source.
- Full offline Gradle gate `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`: **784/784 JVM tests, 125 suites, 0 failures/errors/skips; BUILD SUCCESSFUL; lint 0 errors / 55 warnings**. Test report SHA-256 `1F26591BDD61B84C94BF64C3F80234F6C99526F6C7DC291D1A44ED866953C389`; lint XML SHA-256 `83C67F338218310BAA57178A82616341C10765370256CBF9D751D1C3E61661EA`.
- Debug APK SHA-256 `B4BEB1F562D416D047663BD35C520C4DC691C27530C15187AA69794D91DE9A61`; AndroidTest APK SHA-256 `3C5181CC8A0D914820AE02B289C25865452F9CD9A18465BE3FAE37634A756831`; canonical Clear package SHA-256 `40CCD6CB0CC91D87C355413B784633720D44A5789FDFC16A1DA6C3DB53E4478F`.
- Consolidated evidence summary: `.local/android-test/reports/sep11-clear-calendar-permission-20261031/evidence-summary.md`, SHA-256 `416761FD0BFE35BDF31D03EB6B52CD16850D94BE467971C3D4C1C124F6447D6E`.
- Runtime/test/contract commit `f2bc056ead891b631a66a56a6c1a73100ce00d2b` (`fix(皮肤分离): 显示Clear宿主状态通知`) is pushed to `origin/feat/skin-first-separation`.

This closes the permission-feedback/WebView slice only. `S0-ST-11` and SEP-11 remain **PARTIAL / HOLD / INCOMPLETE** for non-empty provider import and duplicate/retry coverage through Android WebView, system Settings revocation, reminder/all-day semantics, other Settings parity, full Native/Skin replay, and physical/OEM acceptance.
