# PC-ISSUE-SEPARATION-SYSTEM-CALENDAR-IMPORT

Date: 2026-10-30

Scope: `S0-ST-11` — system calendar import through the Clear Host bridge

Status: **PARTIAL / HOLD**. The automated Host/Store slice passes. Android WebView, an actual device Calendar Provider, and owner-side device acceptance remain untested.

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

- Production Android WebView dispatch, the OS permission prompt/revocation path, and reads from a real device Calendar Provider were **NOT TESTED**. The Compose integration test calls the same Host action handler directly; it is not a Clear WebView end-to-end test.
- Current code maps imported rows to `ACTIVITY` with a zero-minute offset and creates a PowerCal reminder at the event start. It does not read/copy source Calendar Provider reminders. The contract mentions a reminder intent but does not specify source-reminder import versus this default versus disabled reminders. The test records current behavior; it does not constitute owner approval of a new policy.
- The provider adapter reads `ALL_DAY`, but the current `CalendarEvent.toRecord()` conversion does not preserve that flag explicitly. The new fixture uses timed events, so all-day import semantics remain **NOT TESTED** and need a contract/model decision before full sign-off.
- A store rejection is persisted as a migration warning and surfaced in the Host outcome; the deterministic AI-lock-race test verifies this path. No Studio Mock or production WebView UI replay was added because neither can verify the Android provider or permission screen.

Commit SHA is recorded in `docs/agent-handoff/PROGRESS.md` after the implementation commit is created.
