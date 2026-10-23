# PowerCal Core Freeze Verdict

Date: 2026-10-23

Verdict: **HOLD**

Code/test baseline for the latest candidate sign-offs: `2171c3f96fb69a1e6d14e1fb170d4c53a9439184`

APK used by the latest targeted tests: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`

## 209-case gate

The historical aggregate is 209 items: 126 P0, 82 P1, and one item whose priority is unresolved. The repository does not contain the original 209-row case list or a mapping from those cases to test evidence. The source audit found 0 original case IDs that can be matched to a row-level sign-off.

| Historical bucket | Count | Case-level status |
| --- | ---: | --- |
| P0 | 126 | Unassessed by original case ID |
| P1 | 82 | Unassessed by original case ID |
| Priority unresolved | 1 | Unassessed; keep unclassified |

“Unassessed by original case ID” does not mean that no related test has run. It means the existing unit tests, emulator paths, and issue evidence cannot be attributed to the missing historical cases without inventing a crosswalk. The source audit found 33 product decisions, 18 candidate protocol invariants, 22 protocol example scenarios, 10 stable-data acceptance rows, and 11 emulator paths explicitly described as grouped checks. These sets overlap and do not constitute 209 cases.

Audit evidence: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-source-audit.json` (SHA-256 `600A1D825E9349BCD183053CB2DB59F62A02A50BB862B554238D020FDDFC4E98`). Detailed issue: [`PC-QA-GATE-001`](issues/PC-QA-GATE-001-209-case-ledger.md).

The **RECONSTRUCTED** candidate ledger contains 208 specification-derived rows plus one unresolved historical slot. Candidate priorities follow risk categories and match the aggregate counts, but are proposals for review; every reconstructed row remains `UNMAPPED` to historical IDs. Fourteen backup/retirement rows now have individually mapped targeted test evidence. Structural validation confirms 209 unique rows and the 126/82/1 partition; it does not test product behavior or prove historical equivalence. Latest validation: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-007.json`.

| Candidate bucket | PASS | FAIL | BLOCKED | NOT TESTED | SPEC UNRESOLVED |
| --- | ---: | ---: | ---: | ---: | ---: |
| P0 (proposed) | 20 | 0 | 1 | 101 | 4 |
| P1 (proposed) | 19 | 0 | 8 | 54 | 1 |
| Historical unclassified slot | 0 | 0 | 0 | 0 | 1 |
| **Total candidate rows** | **39** | **0** | **9** | **155** | **6** |

## Current grouped verification

- Latest full working-tree Gradle command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 373 tests, 0 failures/errors/skips; lint 0 errors and 55 warnings. One test came from protected, untracked `DraftDeckFeedbackTest.kt`, so this is 372 tracked tests plus 1 untracked test, not a clean committed-tree count.
- Log: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-concurrent-restore-full-gradle-final-20261023.log`, SHA-256 `7A4CE9C9BA3D88854799F9D820E7D1B0DD36EFAF7F0154550F41724832F8075F`.
- These results are software/group-level evidence. They do not sign off the absent 209 case IDs.

## Open acceptance gates

- `PC-QA-GATE-001`: execute the 155 `NOT TESTED` candidate rows where feasible, resolve the 9 blocked and 5 reconstructed spec-unresolved rows where conditions allow, review proposed priorities, and keep the separate historical unclassified slot unresolved pending its source. The reconstructed 209-row candidate ledger is not the original checklist and signs off 0 original IDs.
- `PC-QA-GATE-002`: physical-device/OEM and additional Android API coverage is unavailable in this run.
- `PC-QA-GATE-003` / `PC-ISSUE-002`: the free-model structured stage returned HTTP 429; an authorized end-to-end proposal/approval/recovery path is not yet evidenced.
- `PC-ISSUE-001`: DST-bearing Android SAF export and independent parsing pass; external calendar import and physical-device acceptance remain open.
- `PC-ISSUE-003`: restore feedback and data-preservation emulator checks pass; actual OS-level SAF permission revocation and physical-device acceptance remain open.
- `PC-ISSUE-004`: the gray-band and portrait-lock fixes have API 35 evidence; physical-device, TalkBack, forced-window/OEM, IME, and long-duration coverage remain open.
- `PC-ISSUE-005`: retired-event restoration now has a JVM SQLite/controller path with explicit approval and same-identity regression evidence; there is no application action connected to `beginRestore`, and device acceptance remains open.
- `PC-ISSUE-006`: eight additional native-backup candidate checks now have one-to-one JVM SQLite evidence; platform-level restore, historical mapping, and the remaining 155 untested rows keep the gate open.
- `PC-ISSUE-007`: `REC-P0-103` now has a deterministic two-store boundary regression for the transaction-local non-empty check; real device/OS concurrency and historical mapping remain open.
- `PC-ISSUE-GREEDY-LABEL-FOLDING`: implementation and API 35 visual regression pass; physical-device/OEM and TalkBack review remain open.
- Stable release signing material remains user-owned and is not present in the repository; no stable Release install/upgrade claim is made.

## Decision

**HOLD. Do not declare the core baseline frozen.** The current build and targeted JVM evidence are useful, but the 209 cases lack historical row-level traceability and multiple required platform and end-to-end acceptance gates remain open. Continue with a clearly labeled reconstructed ledger and independent tests. Update this verdict only when evidence changes; never infer GO from a successful build or test count.
