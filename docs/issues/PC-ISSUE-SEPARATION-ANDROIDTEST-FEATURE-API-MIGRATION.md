# PC-ISSUE-SEPARATION-ANDROIDTEST-FEATURE-API-MIGRATION — Migrate Compose test harnesses to Feature API v1

Date: 2026-11-01 (China Standard Time) | Priority: P2
Status: **HARNESS MIGRATION IMPLEMENTED; KOTLIN COMPILE PASS; INSTRUMENTATION NOT TESTED**

## Goal and result

Update AndroidTest and Compose host harnesses whose call sites still used the previous Deck, editor-session, or formal-search signatures, so the test source compiles against the current Feature API v1 production boundary.

The Deck harnesses now construct and pass a DraftReview API service, and the formal-search fixture uses EventSearchHitV1. Editor lifecycle and recurrence-scope harnesses now drive the Event Feature API v1 and its snapshots/receipts. Assertions for conflict-review contents, default formal search, IME behavior, theme/focus, gesture priority, editor close/discard lifecycle, and recurrence splitting were preserved. GlassComposeTest's callback fixture now uses the API search-hit DTO.

This resolves source-level API drift in the harnesses. It does not establish that the instrumented scenarios pass on an emulator or device.

## Verification

| Check | Result |
| --- | --- |
| `:app:compileDebugAndroidTestKotlin` | **PASS** |
| Full JVM suite, including Compose unit harnesses | **803 tests / 128 XML reports; 0 failures, errors, or skips — PASS** |
| AndroidTest instrumentation execution | **NOT TESTED** |
| AVD / physical device | **NOT TESTED**; no device was touched |

AndroidTest compile log: `.local/android-test/reports/core-event-search-completion-20261101/android-test-compile-final.log`, SHA-256 `2E6062946E55A8E8013E5B874F00FDB0EAF9B8136B788BB23B96A859B4DFDDCC`.

The independent read-only reviewer confirmed the behavior assertions were retained and reported no blocker. The reviewer ran no tests.

## Git delivery

Harness updates are included in implementation commit `c9948d5465bc93da7e59984342c756549df9e295` on `codex/core-feature-api-event`, ordinary fast-forward pushed to `origin/feat/skin-first-separation` after a freshness check. No instrumentation or cutover PASS is claimed.
