# PC-ISSUE-CORE-DRAFT-CREATION-API-V1 — Explicit text draft creation through DraftReview API v1

Date: 2026-11-01 (China Standard Time) | Priority: P1
Status: **IMPLEMENTED / VERIFIED; PHASE 1 OPEN**

## Goal and result

Move explicit user text-to-draft persistence behind the shared versioned DraftReview API. Preserve the existing Store identity allocation, draft transaction, and AI-batch lock while keeping the resulting item a draft until the existing review flow authorizes a later operation.

DraftReviewFeatureApiV1.createDraft accepts an EventFormV1 and request ID. It snapshots the form's collection fields before waiting on the serialized API command, rejects recurrence-edit scope fields, and delegates persistence to PowerStore.newDraft with the existing user actor. Its receipt returns the persisted draft ID, event sequence, and version. Exact request replay returns the original receipt; mismatched request-ID reuse is rejected. The canonical fingerprint length-prefixes all fields, sorts set/map contents, and hashes the resulting encoding with SHA-256.

After persistence, one DRAFTS_CHANGED event is published for the write. Controller callbacks and API writes coordinate through the persisted-draft fingerprint so opening the newly created draft does not publish a duplicate event. The controller observer checks memory state first and reads Store state only when its persisted-card fingerprint diverges, avoiding a Store read for each ordinary edit. If post-commit notification inspection cannot complete, the API still returns and caches APPLIED with the created identities and a refresh message; it does not report the committed write as failed.

PowerCalApp's explicit text-capture action calls this API, reloads, and opens the returned draft ID through the existing DraftReview service. AI proposal and batch flows keep their dedicated authorization receipts. ICS import retains its per-item ImportCheckpoint and partial-result workflow; structured System Calendar import remains a separate formal-import path.

The final read-only review found no remaining blocker. Earlier review findings on duplicate change publication, request fingerprint ambiguity, mutable input, and post-commit failure reporting were corrected. The reviewer ran no tests.

## Verification

| Check | Result |
| --- | --- |
| Focused DraftReview API suite | **13/13 PASS** |
| Forced full JVM suite | **806 tests / 128 XML reports; 0 failures, errors, or skips — PASS** |
| AndroidTest Kotlin compilation | **PASS**; instrumentation behavior was not run |
| Skin and Native source-boundary checks | **PASS** |
| Debug APK assembly | **PASS** |
| lintDebug | **PASS**, 0 errors / 54 warnings |
| git diff --check | **PASS** |
| Android instrumentation / AVD / physical device | **NOT TESTED**; no device was touched |

The Debug APK at app/build/outputs/apk/debug/app-debug.apk is 67,552,443 bytes, SHA-256 `C7C52F7EB0079968D4A0E8548BD742CD931181CFFA94D23BD41871021D8A84F3`. It was not installed on a device.

Logs are under the ignored directory `.local/android-test/reports/core-draft-create-api-20261101/`:

- `focused-final.log` — SHA-256 `BBE3C39DB93269D8A7962CA7D94E61C376B0D2ED06362DCBFBCD4A1A12ADC33F`.
- `full-jvm-final.log` — SHA-256 `15EE63D9A627DF8BEFE6CA68E42312AE6D8E78FDC7658ECAFE65FAEFD0B21109`.
- `android-test-compile-final.log` — SHA-256 `69D4DEEA87142283A816C2AC73355052DD0B045F4D382724C1141CF22AA0D0B4`.
- `boundary-apk-lint-final.log` — SHA-256 `D3B5D9B9CE0944D58F8771BEC6C02CB8F39A8153828CAA5EDCAD48CF6A6F9EA2`.

## Remaining work

- Draft request receipts are bounded and process-local; durable replay across process death is not provided.
- ICS draft import still owns its import fingerprint/checkpoint and per-item partial results. AI proposal application, AI batch control, structured Calendar import, backup/restore, reminders, and other authorized Host/Core paths need their own versioned APIs.
- Runtime Skin projections still include Core types. Device and WebView parity, full Phase 1 closure, and cutover are **NOT TESTED / NOT SIGNED OFF**. Native fallback remains available.

## Git delivery

Implementation commit `cb5823c573c1cbc2c59c50f751e099cee4e30d5e` on `codex/core-feature-api-event`, based on remote tip `26ce18846de90af16d1591dbe54f42145aaf16b5`, was ordinary fast-forward pushed to `origin/feat/skin-first-separation` after a freshness check.
