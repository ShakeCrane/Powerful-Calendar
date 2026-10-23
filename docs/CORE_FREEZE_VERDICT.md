# PowerCal Core Freeze Verdict

Date: 2026-10-23

Verdict: **HOLD**

Code/test baseline for the latest candidate sign-offs: `c2f2ad5efb5b84b887fb38b4ee03916c0c23906e`

APK used by the latest full regression: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`

## 209-case gate

The historical aggregate is 209 items: 126 P0, 82 P1, and one item whose priority is unresolved. The repository does not contain the original 209-row case list or a mapping from those cases to test evidence. The source audit found 0 original case IDs that can be matched to a row-level sign-off.

| Historical bucket | Count | Case-level status |
| --- | ---: | --- |
| P0 | 126 | Unassessed by original case ID |
| P1 | 82 | Unassessed by original case ID |
| Priority unresolved | 1 | Unassessed; keep unclassified |

“Unassessed by original case ID” does not mean that no related test has run. It means the existing unit tests, emulator paths, and issue evidence cannot be attributed to the missing historical cases without inventing a crosswalk. The source audit found 33 product decisions, 18 candidate protocol invariants, 22 protocol example scenarios, 10 stable-data acceptance rows, and 11 emulator paths explicitly described as grouped checks. These sets overlap and do not constitute 209 cases.

Audit evidence: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-source-audit.json` (SHA-256 `600A1D825E9349BCD183053CB2DB59F62A02A50BB862B554238D020FDDFC4E98`). Detailed issue: [`PC-QA-GATE-001`](issues/PC-QA-GATE-001-209-case-ledger.md).

The **RECONSTRUCTED** candidate ledger contains 208 specification-derived rows plus one unresolved historical slot. Candidate priorities follow risk categories and match the aggregate counts, but are proposals for review; every reconstructed row remains `UNMAPPED` to historical IDs. Seventy-five backup, retirement, migration, recurrence, reminder, temporal model, draft, and core data-semantics rows now have individually mapped targeted test evidence. Structural validation confirms 209 unique rows and the 126/82/1 partition; it does not test product behavior or prove historical equivalence. `REC-P0-054` remains SPEC UNRESOLVED because the product baseline allows multiple same-event human/AI cards while SQLite v4 allows only one actionable draft. `REC-P0-058` passes the current-baseline PowerStore no-op Save regression; `REC-P0-059` passes the current-baseline recurrence start-bound/anchor regression; `REC-P0-069` passes the recurring snooze delayed-receiver regression; `REC-P0-025/027` pass the PowerStore first-draft identity allocation regression; `REC-P0-026` passes the injected first-draft transaction failure regression; `REC-P0-089/090` pass unknown backup-field and future-format/schema rejection regressions. Latest validation: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-053.json` (structural only; `product_behavior_tested=false`). Validation SHA-256 `F89EB3716E146E5FFB58AE0820070A2B72D92B07A73480708BCAEC81D3130E70`; ledger SHA-256 `E2E45D187C2BFEDC95187DB73075B72B6AE0BADD749154A626298CB76CDD02EC`.

| Candidate bucket | PASS | FAIL | BLOCKED | NOT TESTED | SPEC UNRESOLVED |
| --- | ---: | ---: | ---: | ---: | ---: |
| P0 (proposed) | 81 | 0 | 1 | 39 | 5 |
| P1 (proposed) | 19 | 0 | 8 | 54 | 1 |
| Historical unclassified slot | 0 | 0 | 0 | 0 | 1 |
| **Total candidate rows** | **100** | **0** | **9** | **93** | **7** |

## Current grouped verification

- Latest full working-tree Gradle command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 409 tests, 0 failures/errors/skips across 55 JUnit suites; lint 0 errors and 55 warnings. This includes protected, untracked `DraftDeckFeedbackTest.kt` (408 tracked + 1 protected untracked); that file was not modified or staged. The tested code/test changes are committed as `c2f2ad5efb5b84b887fb38b4ee03916c0c23906e`.
- Log: `.local/android-test/reports/freeze-20261023/pc-issue-052-full-gradle-20261023.log`, SHA-256 `EACE03207765FED33CD83882FAD41F9CC477BB48BD26EA5CF14D1B6FD1A07FEE`.
- These results are software/group-level evidence. They do not sign off the absent 209 case IDs.

## Open acceptance gates

- `PC-QA-GATE-001`: execute the 93 `NOT TESTED` candidate rows where feasible, resolve the 9 blocked and 6 reconstructed spec-unresolved rows where conditions allow, review proposed priorities, and keep the separate historical unclassified slot unresolved pending its source. The reconstructed 209-row candidate ledger is not the original checklist and signs off 0 original IDs.
- `PC-ISSUE-047` / `REC-P0-054`: obtain an authoritative decision reconciling same-event human/AI draft coexistence in the product baseline with SQLite v4's one-actionable-draft limit. No product behavior is signed by the spec audit.
- `PC-ISSUE-048` / `REC-P0-058`: PowerStore's unchanged formal Save returns `Saved(changed=false)` without advancing revision; storage-only regression passed, with no UI or device claim.
- `PC-ISSUE-049` / `REC-P0-059`: recurrence projection honors an inclusive `activeFromDate` after the original cycle anchor without rephasing; time-model regression passed, with no editor/SQLite or device claim.
- `PC-ISSUE-050` / `REC-P0-069`: delayed `ACTION_FIRE` snooze delivery now fires the selected recurring occurrence without changing the series, formal revision, or completion keys. Robolectric regression and full Gradle passed; actual Android AlarmManager delivery and physical-device notification behavior remain unverified.
- `PC-ISSUE-051` / `REC-P0-025/027`: PowerStore allocates `eventSeq` when the first draft is created and does not reuse it after discard; focused SQLite regression passed. The then-untested transaction-failure path (`REC-P0-026`) is covered by PC-ISSUE-052.
- `PC-ISSUE-052` / `REC-P0-026`: an injected SQLite abort during first-draft insert now leaves the preallocated sequence retired; a retry receives the next sequence. Targeted 2/2 and full Gradle 409/409 passed.
- `PC-ISSUE-053` / `REC-P0-089/090`: unknown legacy backup fields and future backup/schema versions are rejected without occupying or mutating the import target. Targeted 3/3 passed; no source or test change was required.
- `PC-QA-GATE-002`: physical-device/OEM and additional Android API coverage is unavailable in this run.
- `PC-QA-GATE-003` / `PC-ISSUE-002`: the free-model structured stage returned HTTP 429; an authorized end-to-end proposal/approval/recovery path is not yet evidenced.
- `PC-ISSUE-001`: DST-bearing Android SAF export and independent parsing pass; external calendar import and physical-device acceptance remain open.
- `PC-ISSUE-003`: restore feedback and data-preservation emulator checks pass; actual OS-level SAF permission revocation and physical-device acceptance remain open.
- `PC-ISSUE-004`: portrait-lock and the prior background-band change have API 35 evidence. Per the user's latest direction, faint horizontal edges are accepted and no further UI work is in scope for this loop; physical-device, TalkBack, forced-window/OEM, IME, and long-duration coverage remain separate open gates.
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
- `PC-ISSUE-022`: `REC-P0-007` passes a PowerStore/Robolectric persistence test: a one-date Memo retains its local date and does not acquire formal start/end or Deadline fields after database reopen. See `docs/issues/PC-ISSUE-022-one-date-memo-local-date.md`.
- `PC-ISSUE-023`: `REC-P0-008` passes a PowerStore/Robolectric persistence test: a date-span Memo retains both local-date boundaries across reopen and has no formal event-time or Deadline fields. See `docs/issues/PC-ISSUE-023-date-span-memo-boundaries.md`.
- `PC-ISSUE-024`: `REC-P0-002` passes a PowerStore/Robolectric persistence test: an Activity retains its exact start/end interval and kind after database reopen, without acquiring Deadline fields. See `docs/issues/PC-ISSUE-024-activity-interval-persistence.md`.
- `PC-ISSUE-025`: `REC-P0-020` passes a PowerStore/Robolectric persistence test: converting an ALARM to ACTIVITY preserves the original `eventSeq` and identity allocator across database reopen. See `docs/issues/PC-ISSUE-025-kind-conversion-keeps-event-identity.md`.
- `PC-ISSUE-026`: `REC-P0-016` passes a PowerStore/Robolectric persistence test: source metadata scopes import deduplication but distinct source/external-ID pairs retain distinct internal event identities across database reopen. See `docs/issues/PC-ISSUE-026-import-source-metadata-identity.md`.
- `PC-ISSUE-027`: `REC-P0-028` passes a PowerStore/Robolectric regression: a rejected structured import leaves `eventSeq` allocation unchanged; repair and retry with the same source/external ID imports as sequence 1 and persists across database reopen. Test only; no production or UI change. See `docs/issues/PC-ISSUE-027-rejected-import-does-not-consume-sequence.md`.
- `PC-ISSUE-028`: `REC-P0-006` passes pure-model and PowerStore/Robolectric persistence checks: an undated Memo keeps date, instant, deadline, anchor, and span-end values null before and after SQLite reopen. Test only; no production or UI change. See `docs/issues/PC-ISSUE-028-undated-memo-no-synthetic-time.md`.
- `PC-ISSUE-029`: `REC-P0-029/030` pass a PowerStore/Robolectric regression: event deletion persists a tombstone and retires its sequence across SQLite reopen; a later event receives the next sequence. Test only; no production or UI change. See `docs/issues/PC-ISSUE-029-retired-identity-not-reused-after-restart.md`.
- `PC-ISSUE-030`: `REC-P0-031` passes a PowerStore/Robolectric regression: two independent create drafts retain unique `draftId` values and correct `eventSeq` targets across SQLite reopen. Test only; no production or UI change. See `docs/issues/PC-ISSUE-030-independent-draft-identities.md`.
- `PC-ISSUE-031`: `REC-P0-032` passes a PowerStore/Robolectric regression: staging an edit keeps the same `draftId` and target `eventSeq`, updates content/version, and preserves the pending draft after SQLite reopen. Test only; no production or UI change. See `docs/issues/PC-ISSUE-031-staged-draft-keeps-event-identity.md`.
- `PC-ISSUE-032`: `REC-P0-033` passes a PowerStore/Robolectric regression against the corrected V4 contract: successive proposals for one event reuse the sole actionable draft and queue slot across SQLite reopen. Test only; no production or UI change. See `docs/issues/PC-ISSUE-032-one-actionable-draft-per-event.md`.
- `PC-ISSUE-033`: `REC-P0-034` passes a PowerStore/Robolectric regression: multiple commits leave exactly one latest formal snapshot per event across SQLite reopen while commit history remains available. Test only; no production or UI change. See `docs/issues/PC-ISSUE-033-single-current-formal-snapshot.md`.
- `PC-ISSUE-034`: `REC-P0-035` passes a PowerStore/Robolectric regression: distinct `queueOrder` values remain independent of `eventSeq` and persist with their mappings and order across SQLite reopen. Test only; no production or UI change. See `docs/issues/PC-ISSUE-034-independent-queue-order.md`.
- `PC-ISSUE-035`: `REC-P0-036` passes a PowerStore/Robolectric regression: after committed and discarded draft positions, the next `queueOrder` advances beyond the prior high-water mark across SQLite reopen. Test only; no production or UI change. See `docs/issues/PC-ISSUE-035-monotonic-queue-watermark.md`.
- `PC-ISSUE-036`: `REC-P0-038` passes a PowerStore/Robolectric regression: a human proposal appends after existing drafts, and IDs/order/titles persist across SQLite reopen. Test only; no production or UI change. See `docs/issues/PC-ISSUE-036-human-proposal-appends-queue.md`.
- `PC-ISSUE-037`: `REC-P0-042` passes a PowerStore/Robolectric regression: an edited older draft can have a later timestamp without changing queue order, including after SQLite reopen. Test only; no production or UI change. See `docs/issues/PC-ISSUE-037-timestamp-does-not-reorder-queue.md`.
- `PC-ISSUE-038`: `REC-P0-044` passes a PowerStore/Robolectric regression: creating and staging a valid mutation leaves the formal revision unchanged across SQLite reopen; commit then advances it once. Test only; no production or UI change. See `docs/issues/PC-ISSUE-038-revision-advances-only-on-commit.md`.
- `PC-ISSUE-039`: `REC-P0-045` passes a PowerStore/Robolectric regression: independent commits receive distinct non-empty IDs, and both IDs remain in history across SQLite reopen. Test only; no production or UI change. See `docs/issues/PC-ISSUE-039-unique-commit-id.md`.
- `PC-ISSUE-040`: `REC-P0-046` passes a PowerStore/Robolectric regression: retrying the same explicit idempotency key after SQLite reopen returns the original result without duplicating history, revision, published count, or durable schedule intent. OS-level delivery remains outside this test. See `docs/issues/PC-ISSUE-040-idempotent-commit-retry.md`.
- `PC-ISSUE-041`: `REC-P0-047` passes the narrowed PowerStore contract: stale `baseRevision` returns a conflict and preserves the formal snapshot across SQLite reopen. Editor conflict presentation remains outside the evidence. See `docs/issues/PC-ISSUE-041-stale-revision-conflict.md`.
- `PC-ISSUE-042`: `REC-P0-048` passes the PowerStore persistence contract: an AI batch conflict draft remains queryable with its proposal and queue order after SQLite reopen; formal event state is preserved and the batch lock is released. See `docs/issues/PC-ISSUE-042-conflict-draft-retention.md`.
- `PC-ISSUE-043`: `REC-P0-050` passes the PowerStore stale-approval gate: default commit returns Conflict and preserves the newer snapshot across reopen; only explicit `approveStale=true` commits the full replacement. See `docs/issues/PC-ISSUE-043-explicit-stale-reapproval.md`.
- `PC-ISSUE-044`: `REC-P0-051` passes the PowerStore whole-snapshot contract across SQLite reopen, including clearing old optional fields without merging and preserving event identity metadata. See `docs/issues/PC-ISSUE-044-whole-snapshot-replacement.md`.
- `PC-ISSUE-045`: `REC-P0-052` is supported by the focused stale-reapproval regression: the default stale commit preserves the newer full snapshot, and only explicit approval replaces it. The evidence is shared with `PC-ISSUE-043` and does not add another test. See `docs/issues/PC-ISSUE-045-no-silent-stale-field-merge.md`.
- `PC-ISSUE-046`: `REC-P0-053` passes a current-baseline PowerStore/AI batch regression: a late stale AI proposal is kept as conflict and cannot overwrite the human completion update, including across SQLite reopen. See `docs/issues/PC-ISSUE-046-late-ai-response.md`.
- `PC-ISSUE-020`: stabilized the `REC-P0-012` test fixture after a full regression exposed concurrent SQLite polling. The delivery handler now has deterministic synchronous test coverage; Android `onReceive`/`goAsync` framework lifecycle and actual AlarmManager delivery remain untested. See `docs/issues/PC-ISSUE-020-reminder-receiver-test-race.md`.
- `PC-ISSUE-GREEDY-LABEL-FOLDING`: implementation and API 35 visual regression pass; physical-device/OEM and TalkBack review remain open.
- Stable release signing material remains user-owned and is not present in the repository; no stable Release install/upgrade claim is made.

## Decision

**HOLD. Do not declare the core baseline frozen.** The current build and targeted JVM evidence are useful, but the 209 cases lack historical row-level traceability and multiple required platform and end-to-end acceptance gates remain open. Continue with a clearly labeled reconstructed ledger and independent tests. Update this verdict only when evidence changes; never infer GO from a successful build or test count.
