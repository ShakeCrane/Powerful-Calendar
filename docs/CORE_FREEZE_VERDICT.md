# PowerCal Core Freeze Verdict

Date: 2026-10-23

Verdict: **HOLD**

Code/test baseline for the latest candidate sign-offs: `02e32d21c74babb284fc41f29f2ec02fca08037e`

APK used by the latest targeted tests: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`

## 209-case gate

The historical aggregate is 209 items: 126 P0, 82 P1, and one item whose priority is unresolved. The repository does not contain the original 209-row case list or a mapping from those cases to test evidence. The source audit found 0 original case IDs that can be matched to a row-level sign-off.

| Historical bucket | Count | Case-level status |
| --- | ---: | --- |
| P0 | 126 | Unassessed by original case ID |
| P1 | 82 | Unassessed by original case ID |
| Priority unresolved | 1 | Unassessed; keep unclassified |

“Unassessed by original case ID” does not mean that no related test has run. It means the existing unit tests, emulator paths, and issue evidence cannot be attributed to the missing historical cases without inventing a crosswalk. The source audit found 33 product decisions, 18 candidate protocol invariants, 22 protocol example scenarios, 10 stable-data acceptance rows, and 11 emulator paths explicitly described as grouped checks. These sets overlap and do not constitute 209 cases.

Audit evidence: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-source-audit.json` (SHA-256 `600A1D825E9349BCD183053CB2DB59F62A02A50BB862B554238D020FDDFC4E98`). Detailed issue: [`PC-QA-GATE-001`](issues/PC-QA-GATE-001-209-case-ledger.md).

The **RECONSTRUCTED** candidate ledger contains 208 specification-derived rows plus one unresolved historical slot. Candidate priorities follow risk categories and match the aggregate counts, but are proposals for review; every reconstructed row remains `UNMAPPED` to historical IDs. Forty-one backup, retirement, migration, recurrence, reminder, temporal model, and incomplete-draft rows now have individually mapped targeted test evidence. Structural validation confirms 209 unique rows and the 126/82/1 partition; it does not test product behavior or prove historical equivalence. Latest validation: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-021.json` (ledger SHA-256 `F5EB2543E4846BC95B61CABBB9D322EA08E2A3610A26BF46EC7648CE88AD5518`).

| Candidate bucket | PASS | FAIL | BLOCKED | NOT TESTED | SPEC UNRESOLVED |
| --- | ---: | ---: | ---: | ---: | ---: |
| P0 (proposed) | 47 | 0 | 1 | 74 | 4 |
| P1 (proposed) | 19 | 0 | 8 | 54 | 1 |
| Historical unclassified slot | 0 | 0 | 0 | 0 | 1 |
| **Total candidate rows** | **66** | **0** | **9** | **128** | **6** |

## Current grouped verification

- Latest full working-tree Gradle command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 385 tests, 0 failures/errors/skips across 55 JUnit suites; lint 0 errors and 55 warnings. One test came from protected, untracked `DraftDeckFeedbackTest.kt` (384 tracked + 1 protected untracked). The tested code/test changes are committed as `02e32d21c74babb284fc41f29f2ec02fca08037e`.
- Log: `.local/android-test/reports/freeze-20261023/pc-issue-021-incomplete-activity-draft-full-gradle-20261023.log`, SHA-256 `830C3E74943993C467B5133208382E4FCED0A90FF9D699FF6D6D682BE550B5E3`.
- These results are software/group-level evidence. They do not sign off the absent 209 case IDs.

## Open acceptance gates

- `PC-QA-GATE-001`: execute the 128 `NOT TESTED` candidate rows where feasible, resolve the 9 blocked and 5 reconstructed spec-unresolved rows where conditions allow, review proposed priorities, and keep the separate historical unclassified slot unresolved pending its source. The reconstructed 209-row candidate ledger is not the original checklist and signs off 0 original IDs.
- `PC-QA-GATE-002`: physical-device/OEM and additional Android API coverage is unavailable in this run.
- `PC-QA-GATE-003` / `PC-ISSUE-002`: the free-model structured stage returned HTTP 429; an authorized end-to-end proposal/approval/recovery path is not yet evidenced.
- `PC-ISSUE-001`: DST-bearing Android SAF export and independent parsing pass; external calendar import and physical-device acceptance remain open.
- `PC-ISSUE-003`: restore feedback and data-preservation emulator checks pass; actual OS-level SAF permission revocation and physical-device acceptance remain open.
- `PC-ISSUE-004`: the gray-band and portrait-lock fixes have API 35 evidence; physical-device, TalkBack, forced-window/OEM, IME, and long-duration coverage remain open.
- `PC-ISSUE-005`: retired-event restoration now has a JVM SQLite/controller path with explicit approval and same-identity regression evidence; there is no application action connected to `beginRestore`, and device acceptance remains open.
- `PC-ISSUE-006`: eight additional native-backup candidate checks now have one-to-one JVM SQLite evidence; platform-level restore, historical mapping, and the remaining 141 untested rows keep the gate open.
- `PC-ISSUE-007`: `REC-P0-103` now has a deterministic two-store boundary regression for the transaction-local non-empty check; real device/OS concurrency and historical mapping remain open.
- `PC-ISSUE-008`: five schema-version and migration candidates have JVM SQLite evidence, including injected v4 failure rollback and same-database retry. No production or UI code changed; physical-device and historical mapping remain open. See `docs/issues/PC-ISSUE-008-database-migration-atomicity.md`.
- `PC-ISSUE-009`: nine recurrence candidates have JVM evidence for inclusive bounds, bounded projection, immutable moved-slot keys, skip behavior, single-instance override, and per-series exception scope. Production code and UI were unchanged; device acceptance and original-case mapping remain open. See `docs/issues/PC-ISSUE-009-recurrence-instance-identity.md`.
- `PC-ISSUE-010`: `REC-P0-072/073` pass JVM regressions for cross-midnight start-date membership and the preserved 105-minute activity duration; production code and UI were unchanged. The todo-list projection cases `REC-P0-064/065` remain NOT TESTED and were not inferred from recurrence-window tests. See `docs/issues/PC-ISSUE-010-cross-midnight-recurrence-boundary.md`.
- `PC-ISSUE-011`: `REC-P0-009` passes an isolated SQLite/Robolectric test proving date-span Memo completion uses one `single` key and persists across reopen. No production or UI code changed; the page-level todo query remains outside the test. See `docs/issues/PC-ISSUE-011-spanning-memo-completion.md`.
- `PC-ISSUE-012`: `REC-P0-010` passes a v1 SQLite migration regression: a legacy Activity without a tracking field remains untracked and cannot gain a completion key. No production or UI code changed. See `docs/issues/PC-ISSUE-012-legacy-untracked-record-contract.md`.
- `PC-ISSUE-013`: `REC-P0-011` passes SQLite regressions for changing reminder offsets on tracked and untracked Activity snapshots without changing completion tracking. No production or UI code changed; device notification delivery remains a separate gate. See `docs/issues/PC-ISSUE-013-reminder-tracking-separation.md`.
- `PC-ISSUE-014`: `REC-P0-014` passes SQLite regressions for changing completion tracking without changing enabled state or reminder settings. No production or UI code changed. See `docs/issues/PC-ISSUE-014-tracking-enabled-separation.md`.
- `PC-ISSUE-015`: `REC-P0-012/013` pass isolated Robolectric regressions for alarm receiver execution and notification cancellation leaving completion unchanged. Actual Android AlarmManager delivery and real notification-shade dismissal remain untested; see `docs/issues/PC-ISSUE-015-reminder-completion-isolation.md`.
- `PC-ISSUE-016`: `REC-P0-001` passes a pure JVM model regression: Alarm retains its start point and has no end even when the form carries leftover end text. See `docs/issues/PC-ISSUE-016-alarm-point-semantics.md`.
- `PC-ISSUE-017`: `REC-P0-015` passes an isolated SQLite/Robolectric regression: an exact Alarm Trigger on an Activity retains that Activity's kind and event identity. See `docs/issues/PC-ISSUE-017-trigger-event-kind-separation.md`.
- `PC-ISSUE-018`: `REC-P0-003` passes a pure JVM regression: an instant Deadline retains its deadline instant and has neither a start nor an interval end. See `docs/issues/PC-ISSUE-018-instant-deadline-point-semantics.md`.
- `PC-ISSUE-019`: `REC-P0-004` passes isolated PowerStore/Robolectric persistence assertions: date-only Deadline retains `2026-10-24` with DATE precision and null `deadlineMillis`; no time is fabricated. See `docs/issues/PC-ISSUE-019-date-only-deadline-precision.md`.
- `PC-ISSUE-021`: `REC-P0-005` passes a PowerStore/Robolectric persistence test: an incomplete Activity stays unpublished, survives database reopen as an editable invalid draft, and publishes only after repair. See `docs/issues/PC-ISSUE-021-incomplete-activity-draft-recovery.md`.
- `PC-ISSUE-020`: stabilized the `REC-P0-012` test fixture after a full regression exposed concurrent SQLite polling. The delivery handler now has deterministic synchronous test coverage; Android `onReceive`/`goAsync` framework lifecycle and actual AlarmManager delivery remain untested. See `docs/issues/PC-ISSUE-020-reminder-receiver-test-race.md`.
- `PC-ISSUE-GREEDY-LABEL-FOLDING`: implementation and API 35 visual regression pass; physical-device/OEM and TalkBack review remain open.
- Stable release signing material remains user-owned and is not present in the repository; no stable Release install/upgrade claim is made.

## Decision

**HOLD. Do not declare the core baseline frozen.** The current build and targeted JVM evidence are useful, but the 209 cases lack historical row-level traceability and multiple required platform and end-to-end acceptance gates remain open. Continue with a clearly labeled reconstructed ledger and independent tests. Update this verdict only when evidence changes; never infer GO from a successful build or test count.
