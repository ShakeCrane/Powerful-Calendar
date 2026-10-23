# PowerCal Core Freeze Verdict

Date: 2026-10-23

Verdict: **HOLD**

Code/test baseline for the latest candidate sign-offs: `5df9e00b04ad50dc5c4e12154cf117cc4a159a23`

APK used by the latest targeted tests: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `9677B9816B9028EF5C25EC84025ABA56DDB92BEB5CF36B2197AC5615315BB0AE`

## 209-case gate

The historical aggregate is 209 items: 126 P0, 82 P1, and one item whose priority is unresolved. The repository does not contain the original 209-row case list or a mapping from those cases to test evidence. The source audit found 0 original case IDs that can be matched to a row-level sign-off.

| Historical bucket | Count | Case-level status |
| --- | ---: | --- |
| P0 | 126 | Unassessed by original case ID |
| P1 | 82 | Unassessed by original case ID |
| Priority unresolved | 1 | Unassessed; keep unclassified |

“Unassessed by original case ID” does not mean that no related test has run. It means the existing unit tests, emulator paths, and issue evidence cannot be attributed to the missing historical cases without inventing a crosswalk. The source audit found 33 product decisions, 18 candidate protocol invariants, 22 protocol example scenarios, 10 stable-data acceptance rows, and 11 emulator paths explicitly described as grouped checks. These sets overlap and do not constitute 209 cases.

Audit evidence: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-source-audit.json` (SHA-256 `600A1D825E9349BCD183053CB2DB59F62A02A50BB862B554238D020FDDFC4E98`). Detailed issue: [`PC-QA-GATE-001`](issues/PC-QA-GATE-001-209-case-ledger.md).

The **RECONSTRUCTED** candidate ledger contains 208 specification-derived rows plus one unresolved historical slot. Candidate priorities follow risk categories and match the aggregate counts, but are proposals for review; every reconstructed row remains `UNMAPPED` to historical IDs. Thirteen backup/retirement rows now have individually mapped targeted test evidence. Structural validation confirms 209 unique rows and the 126/82/1 partition; it does not test product behavior or prove historical equivalence. Latest validation: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-006.json`.

| Candidate bucket | PASS | FAIL | BLOCKED | NOT TESTED | SPEC UNRESOLVED |
| --- | ---: | ---: | ---: | ---: | ---: |
| P0 (proposed) | 19 | 0 | 1 | 102 | 4 |
| P1 (proposed) | 19 | 0 | 8 | 54 | 1 |
| Historical unclassified slot | 0 | 0 | 0 | 0 | 1 |
| **Total candidate rows** | **38** | **0** | **9** | **156** | **6** |

## Current grouped verification

- Latest full working-tree Gradle command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 372 tests, 0 failures/errors/skips; lint 0 errors and 55 warnings. One test came from protected, untracked `DraftDeckFeedbackTest.kt`, so this is 371 tracked tests plus 1 untracked test, not a clean committed-tree count.
- Log: `.local/android-test/reports/freeze-20261023/pc-issue-006-backup-contract-full-gradle-final-20261023.log`, SHA-256 `A77F74970305AB3A4CE82FE49544726092EEBA718401D325A1953384435DCB36`.
- These results are software/group-level evidence. They do not sign off the absent 209 case IDs.

## Open acceptance gates

- `PC-QA-GATE-001`: execute the 156 `NOT TESTED` candidate rows where feasible, resolve the 9 blocked and 5 reconstructed spec-unresolved rows where conditions allow, review proposed priorities, and keep the separate historical unclassified slot unresolved pending its source. The reconstructed 209-row candidate ledger is not the original checklist and signs off 0 original IDs.
- `PC-QA-GATE-002`: physical-device/OEM and additional Android API coverage is unavailable in this run.
- `PC-QA-GATE-003` / `PC-ISSUE-002`: the free-model structured stage returned HTTP 429; an authorized end-to-end proposal/approval/recovery path is not yet evidenced.
- `PC-ISSUE-001`: DST-bearing Android SAF export and independent parsing pass; external calendar import and physical-device acceptance remain open.
- `PC-ISSUE-003`: restore feedback and data-preservation emulator checks pass; actual OS-level SAF permission revocation and physical-device acceptance remain open.
- `PC-ISSUE-004`: the gray-band and portrait-lock fixes have API 35 evidence; physical-device, TalkBack, forced-window/OEM, IME, and long-duration coverage remain open.
- `PC-ISSUE-005`: retired-event restoration now has a JVM SQLite/controller path with explicit approval and same-identity regression evidence; there is no application action connected to `beginRestore`, and device acceptance remains open.
- `PC-ISSUE-006`: eight additional native-backup candidate checks now have one-to-one JVM SQLite evidence; platform-level restore, historical mapping, and the remaining 156 untested rows keep the gate open.
- `PC-ISSUE-GREEDY-LABEL-FOLDING`: implementation and API 35 visual regression pass; physical-device/OEM and TalkBack review remain open.
- Stable release signing material remains user-owned and is not present in the repository; no stable Release install/upgrade claim is made.

## Decision

**HOLD. Do not declare the core baseline frozen.** The current build and targeted JVM evidence are useful, but the 209 cases lack historical row-level traceability and multiple required platform and end-to-end acceptance gates remain open. Continue with a clearly labeled reconstructed ledger and independent tests. Update this verdict only when evidence changes; never infer GO from a successful build or test count.
