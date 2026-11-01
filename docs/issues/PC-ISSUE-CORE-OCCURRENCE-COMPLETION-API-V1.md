# PC-ISSUE-CORE-OCCURRENCE-COMPLETION-API-V1 — Guarded occurrence completion command

Date: 2026-11-01 (China Standard Time) | Priority: P1
Status: **IMPLEMENTED / VERIFIED; PHASE 1 OPEN**

## Goal and result

Route Native and Web Memo completion toggles through one versioned Core Feature API command without changing completion, recurrence-slot, transaction, or frozen event semantics.

EventFeatureApiV1.setOccurrenceCompletion accepts the event sequence, expected event revision, original occurrence key, expected prior completion state, requested state, and request ID. Under the Store lock it validates identity and current revision, enabled/tracking state, occurrence existence, and the caller's observed completion state, then delegates the actual write to PowerStore.setCompleted and verifies the resulting revision/state. Stale revision or state returns CONFLICT; unavailable occurrences return INVALID; a locked Store returns LOCKED; an already-satisfied state returns NO_CHANGE. Exact request replay returns the cached receipt, while mismatched request-ID reuse is rejected. Only an actual verified update emits one FORMAL_UPDATED event.

Both Native Memo and Web completion paths now call this command. The Web Host retains a current-entry visibility precheck; this API migration does not remove unrelated Host/Core reads.

## Verification

| Check | Result |
| --- | --- |
| Focused Event API, adapter, Deck, and Web tests | **64/64 PASS** |
| Forced full JVM suite | **803 tests / 128 XML reports; 0 failures, errors, or skips — PASS** |
| AndroidTest Kotlin compilation | **PASS**; instrumentation behavior was not run |
| Skin and Native source-boundary checks | **PASS** |
| Debug APK assembly | **PASS** |
| lintDebug | **PASS**, 0 errors / 54 warnings |
| git diff --check | **PASS** |
| Android instrumentation / AVD / physical device | **NOT TESTED**; no device was touched |

Regression tests cover a single-occurrence update, unchanged Store commit count on no-op, exact replay, request-ID reuse, stale revision/state, invalid occurrence, and completing/reopening an original recurrence slot. The Debug APK at app/build/outputs/apk/debug/app-debug.apk is 67,552,075 bytes, SHA-256 `03108E62D7BE9473C65A887D87BB906EDEAAD7AFFA46B18D0415BCD864161C06`; it was not installed on a device.

Evidence logs are in `.local/android-test/reports/core-event-search-completion-20261101/`:

- `focused-final-v2.log` — SHA-256 `999EE10F8088119C45B619AF5E45A4F60C586B312A0F55066E7744746385B3B5`.
- `full-jvm-final.log` — SHA-256 `7747FC1513088D62269C92374E0D0B7CC1901C9F72E381D55C5C021C7CBE7178`.
- `android-test-compile-final.log` — SHA-256 `2E6062946E55A8E8013E5B874F00FDB0EAF9B8136B788BB23B96A859B4DFDDCC`.
- `boundary-apk-lint-final-v2.log` — SHA-256 `0502BA9261D5D6657E57EC7699A8BD0AF95CA3884E5BE8E47A69243A979B8B32`.

## Remaining work

- Request receipts are bounded and process-local; they do not provide durable exactly-once behavior across process death or adapter recreation.
- The Memo visibility precheck and other Host workflows still use direct Core projections or Store reads. Other authorized writes and the shared change source remain to be migrated.
- Instrumentation, WebView/device parity, and cutover are **NOT TESTED / NOT SIGNED OFF**. Native fallback remains available.

## Git delivery

Implementation commit `c9948d5465bc93da7e59984342c756549df9e295` on `codex/core-feature-api-event`, based on remote tip `d3bb0700d6c3911c651025e92c1906e8975eae14`, was ordinary fast-forward pushed to `origin/feat/skin-first-separation` after a freshness check.
