# PC-ISSUE-CORE-EVENT-SEARCH-API-V1 — Formal event search through Event Feature API v1

Date: 2026-11-01 (China Standard Time) | Priority: P1
Status: **IMPLEMENTED / VERIFIED; PHASE 1 OPEN**

## Goal and result

Move Native and Web Draft Deck formal-event search behind the versioned Event Feature API while preserving the existing search behavior and avoiding Store reads for each query keystroke. This closes the formal-search bypass only; it does not close Phase 1 or establish full Native/Skin parity.

The API now accepts an EventQuerySnapshotV1 and returns an EventSearchSnapshotV1 containing only event sequence, revision, kind, and title. It searches the supplied snapshot without Store I/O, retains the snapshot feature revision, preserves input order, performs case-insensitive title matching plus raw/formatted sequence matching, returns no hits for blank input, and caps results at eight. Native and Web Deck surfaces display at most four hits. Native uses the latest Host-loaded snapshot; Web obtains and searches its snapshot on the I/O dispatcher. Both retain the hit revision when opening a guarded formal-replacement proposal.

The independent read-only review found no actionable findings and confirmed the search rules and both call paths. The reviewer ran no tests.

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

The Debug APK at app/build/outputs/apk/debug/app-debug.apk is 67,552,075 bytes, SHA-256 `03108E62D7BE9473C65A887D87BB906EDEAAD7AFFA46B18D0415BCD864161C06`. It was not installed on a device.

Logs are in the ignored directory `.local/android-test/reports/core-event-search-completion-20261101/`:

- `focused-final-v2.log` — SHA-256 `999EE10F8088119C45B619AF5E45A4F60C586B312A0F55066E7744746385B3B5`.
- `full-jvm-final.log` — SHA-256 `7747FC1513088D62269C92374E0D0B7CC1901C9F72E381D55C5C021C7CBE7178`.
- `android-test-compile-final.log` — SHA-256 `2E6062946E55A8E8013E5B874F00FDB0EAF9B8136B788BB23B96A859B4DFDDCC`.
- `boundary-apk-lint-final-v2.log` — SHA-256 `0502BA9261D5D6657E57EC7699A8BD0AF95CA3884E5BE8E47A69243A979B8B32`.

## Remaining work

- Search operates only on the immutable-by-contract API snapshot supplied by the Host. Snapshot freshness is the caller's responsibility.
- Phase 1 still has other Host/Core read and write bypasses, incomplete domain APIs, and runtime Skin projections containing Core types.
- Packaged WebView behavior, Native/Skin device parity, and cutover remain **NOT TESTED / NOT SIGNED OFF**. Native fallback remains available.

## Git delivery

Implementation commit `c9948d5465bc93da7e59984342c756549df9e295` on `codex/core-feature-api-event`, based on remote tip `d3bb0700d6c3911c651025e92c1906e8975eae14`, was ordinary fast-forward pushed to `origin/feat/skin-first-separation` after a freshness check.
