# PC-ISSUE-CORE-DRAFT-CREATION-API-V1 — Durable explicit draft creation through DraftReview API v1

Date: 2026-11-02 (China Standard Time) | Priority: P1
Status: **IMPLEMENTED / VERIFIED; PHASE 1 OPEN**

## Owner decision and result

On 2026-11-02 the owner selected durable last-write-wins behavior for `DraftReviewFeatureApiV1.createDraft`: the same `requestId` with later content replaces the mapped draft and remains effective after application restart and BackupEnvelope restore. The owner authorized the additive database schema and backup compatibility work.

PowerStore schema v6 adds `draft_request_receipts`, keyed by request ID and storing the canonical form fingerprint plus the mapped draft/event identity. An exact form replay returns the same mapping with `duplicate = true`. A different form atomically overwrites the same actionable CREATE draft, retaining its `draftId`, `eventSeq`, and queue position while incrementing `draftVersion` and resetting review status to `PENDING`. The request receipt and draft mutation commit together. New identities are still reserved as retired before the final transaction so failed draft writes never reuse a sequence.

If the mapped draft is terminal, a different later form creates a fresh reviewable draft identity. It does not mutate the terminal draft or its formal event. The operation remains subject to the AI batch lock and does not grant commit, conflict-replacement, delete, or restore approval. The DraftReview adapter serializes create requests with editor saves and force-reloads a replaced current card so an older dirty editor value cannot overwrite the latest request.

BackupEnvelope format v1 carries `draftRequestReceipts` in schema v6 payloads. Schema v6 backups require the array; schema v1–v5 backups may omit it and restore with no durable createDraft receipt. The envelope format version remains separate from the SQLite schema version. The generic 128-entry adapter cache remains in place for other Draft/Review commands; AI `createDrafts` outer request replay remains process-local.

## Verification

| Check | Result |
| --- | --- |
| Focused DraftReview, PowerStore, BackupEnvelope, migration, and version-contract suites | **PASS** |
| Forced serial full JVM suite | **902/902 PASS**, 139 XML suites, 0 failures/errors/skips |
| Native/Skin and Skin source boundaries | **PASS** |
| Debug APK assembly | **PASS**, 67,964,347 bytes; SHA-256 `B0BF8D59BDABB7B420C408A586D8770714D0C8113A38DF91D90B7334A0667E49` |
| `lintDebug` | **PASS**, 0 errors / 54 warnings |
| AndroidTest Kotlin compilation | **PASS**; instrumentation was not run |
| `git diff --check` | **PASS** |
| Android device / AVD / WebView parity | **NOT TESTED**; no ADB device or AVD was available |

Coverage includes same-ID exact replay and changed-form overwrite, Store recreation, BackupEnvelope restore, dirty-editor protection, AI batch lock, invalid recurrence scope, and terminal-draft behavior preserving the formal event. Migration tests cover v5 database → v6, v5 backup omission, v6 missing-array rejection before target mutation, and pre-v5 compatibility.

The first forced full run completed 902 tests with two failures: the version contract still expected schema v5, and the Web diagnostics integration test waited for `formalCount` but not its recurrence preview before asserting the preview count. The version expectation was updated. The test wait now also requires the asserted three recurrence previews. The exact integration method passed in isolation, and the final forced serial run passed all 902 tests. No production diagnostics behavior was changed.

Logs are under the ignored directory `.local/android-test/reports/phase1-draft-request-replay-20261102/`:

- `focused-final.log` — SHA-256 `A8BD3C8DFEFF7B977B115F0BF74F83366BA113A790ED78B63C9B6096ED1C6749`.
- `isolated-web-diagnostics-after-wait.log` — SHA-256 `45307FED1EEBE4005B236BA299D6208935FF61086FE6C42D7BCFFF35F7CEAF0F`.
- `full-jvm-final.log` — SHA-256 `9AAE6A7202CEB12757CB943996C002417C0596B32C99A6D83FCD849DA1F2BDBB`.
- `build-gate-final.log` — SHA-256 `A497AEBFB2457C7B820E00A194C586EA459837AEED325FFADF64059EC30CBC90`.
- The two initial aggregate attempts are retained as `full-jvm.log` and `full-jvm-rerun.log`; they document the corrected schema expectation and premature test wait.

The Debug APK was not installed. Android instrumentation, packaged WebView, physical/OEM behavior, and Native/Skin runtime parity remain untested.

## Remaining work

- Reconcile the remaining Phase 1 Host call sites against the feature API contracts and route any uncovered Core business operation through the appropriate versioned API.
- Runtime Skin projections still contain Core types in some paths. Phase 1 is open; device parity, Phase 2 runtime separation, and cutover are not signed off. Native fallback remains available.

## Git delivery

Implementation commit `f46855b8d632752e3d360a0347d44b2b8e306a7a` on `codex/core-feature-api-event`, based on `d8a336e7f17fd2314dd8322eb885e43587c1f1af`, contains the Store schema/backup changes, API serialization, and regression tests.

## Follow-up — Core-owned text-to-draft parsing (2026-11-02)

`DraftReviewFeatureApiV1.createDraftFromText(TextDraftRequestV1)` now owns deterministic Chinese parsing and the resulting reviewable ACTIVITY form. The start is populated only when date and time are both explicit and unambiguous; the end remains blank; trimmed source text is retained in notes; the response carries the existing durable `createDraft` receipt plus the interpretation note. The command cannot publish a formal event. `PowerCalApp` now supplies the captured submission time, zone, source, and request ID, then handles I/O dispatch, feedback, reload, and review navigation.

The previous parser returned a legacy `CalendarEvent` and the Host made the consequential form decisions. The parser now lives under Core and returns a neutral parsed-draft result; text parsing no longer sits in the presentation callback. No database or backup change was needed because persistence uses the owner-approved schema v6 `createDraft` path.

Focused `DraftReviewFeatureApiV1Test` and `InteropTest` passed **32/32** in the initial check. The integrated focused set passed **24/24 DraftReview** and **8/8 Interop**; its complete 83-test focused run passed. The forced serial full gate passed **926/926 JVM tests across 139 suites**, 0 failures/errors/skips; both boundaries, APK, lint (0 errors / 54 warnings), AndroidTest Kotlin compilation, and diff check passed (154 tasks). APK SHA-256 `51B8A7E6DD1D795098B82BF964C59FE28DF93714A9B5B257AD15A3500A6F7028`. Logs, XML, and manifest are under `.local/android-test/reports/phase1-core-text-timeline-ics-20261102-134500/`; full-log SHA-256 `5093FFF4E19340D1D171D93471E1CD6F8168237373B9599318A204D6AA978BA7`, JUnit-manifest SHA-256 `B30450DA1488A8422599E43C7B99AAAB0F5217F5410AD5AC41692CF9EC57F146`.

No ADB device or AVD was available; Android instrumentation, packaged WebView, and runtime Native/Skin parity remain **NOT TESTED**. Code commit `38357b5945696e52ca4b034816dfc7c9b0f6dde5`; PHASE 1 remains open.