# PowerCal Core Freeze Verdict

Date: 2026-10-23

Verdict: **HOLD**

Baseline before this verdict update: `b4ccaaf4770e4b6eaf39cd3288a9a6b83771e489`

APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `B0AB218FF70D696E25B570F0151BEC0F58B07FE545A045EE976EBD4F1173CAE1`

## 209-case gate

The historical aggregate is 209 items: 126 P0, 82 P1, and one item whose priority is unresolved. The repository does not contain the original 209-row case list or a mapping from those cases to test evidence. The source audit found 0 original case IDs that can be matched to a row-level sign-off.

| Historical bucket | Count | Case-level status |
| --- | ---: | --- |
| P0 | 126 | Unassessed by original case ID |
| P1 | 82 | Unassessed by original case ID |
| Priority unresolved | 1 | Unassessed; keep unclassified |

“Unassessed by original case ID” does not mean that no related test has run. It means the existing unit tests, emulator paths, and issue evidence cannot be attributed to the missing historical cases without inventing a crosswalk. The source audit found 33 product decisions, 18 candidate protocol invariants, 22 protocol example scenarios, 10 stable-data acceptance rows, and 11 emulator paths explicitly described as grouped checks. These sets overlap and do not constitute 209 cases.

Audit evidence: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-source-audit.json` (SHA-256 `600A1D825E9349BCD183053CB2DB59F62A02A50BB862B554238D020FDDFC4E98`). Detailed issue: [`PC-QA-GATE-001`](issues/PC-QA-GATE-001-209-case-ledger.md).

An initial **RECONSTRUCTED** candidate ledger now contains 208 specification-derived rows plus one unresolved historical slot. Candidate priorities follow risk categories and match the aggregate counts, but are proposals for review; every reconstructed row remains `UNMAPPED` to historical IDs. See [`CORE_FREEZE_ACCEPTANCE_LEDGER.csv`](CORE_FREEZE_ACCEPTANCE_LEDGER.csv). Structural validation confirms 209 unique rows and the 126/82/1 partition; it does not test product behavior or prove historical equivalence.

| Candidate bucket | PASS | FAIL | BLOCKED | NOT TESTED | SPEC UNRESOLVED |
| --- | ---: | ---: | ---: | ---: | ---: |
| P0 (proposed) | 6 | 0 | 1 | 115 | 4 |
| P1 (proposed) | 19 | 0 | 8 | 54 | 1 |
| Historical unclassified slot | 0 | 0 | 0 | 0 | 1 |

## Current grouped verification

- Full working-tree Gradle command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 364 tests, 0 failures/errors/skips; lint 0 errors and 55 warnings. One test came from the protected untracked `DraftDeckFeedbackTest.kt`, so the run is 363 tracked tests plus 1 untracked test, not a clean committed-tree count.
- Log: `.local/android-test/reports/freeze-20261023/issue001-dst-full-gradle-20261023.log`, SHA-256 `02A3FB2D275B2BD225BE102960DAD1D7B02AD2829028C10E312FBD8DC0F74DA5`.
- These results are software/group-level evidence. They do not sign off the absent 209 case IDs.

## Open acceptance gates

- `PC-QA-GATE-001`: execute the 169 `NOT TESTED` candidate rows where feasible, resolve the 14 blocked/spec-unresolved rows where conditions allow, and review proposed priorities. The initial 209-row candidate ledger is not the original checklist and signs off 0 original IDs.
- `PC-QA-GATE-002`: physical-device/OEM and additional Android API coverage is unavailable in this run.
- `PC-QA-GATE-003` / `PC-ISSUE-002`: the free-model structured stage returned HTTP 429; an authorized end-to-end proposal/approval/recovery path is not yet evidenced.
- `PC-ISSUE-001`: DST-bearing Android SAF export and independent parsing pass; external calendar import and physical-device acceptance remain open.
- `PC-ISSUE-003`: restore feedback and data-preservation emulator checks pass; actual OS-level SAF permission revocation and physical-device acceptance remain open.
- `PC-ISSUE-004`: the gray-band and portrait-lock fixes have API 35 evidence; physical-device, TalkBack, forced-window/OEM, IME, and long-duration coverage remain open.
- `PC-ISSUE-GREEDY-LABEL-FOLDING`: implementation and API 35 visual regression pass; physical-device/OEM and TalkBack review remain open.
- Stable release signing material remains user-owned and is not present in the repository; no stable Release install/upgrade claim is made.

## Decision

**HOLD. Do not declare the core baseline frozen.** The current build and targeted emulator evidence are useful, but the 209 cases lack row-level traceability and multiple required platform and end-to-end acceptance gates remain open. Continue with a clearly labeled reconstructed ledger and independent tests. Update this verdict only when evidence changes; never infer GO from a successful build or test count.
