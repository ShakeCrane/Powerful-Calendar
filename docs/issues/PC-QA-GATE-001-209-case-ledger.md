# PC-QA-GATE-001 — 209-case acceptance traceability

Date: 2026-10-23 | Severity: P0

Status: **PARTIAL / HOLD — source audit complete; reconstructed ledger remains in progress.**

## Finding

The freeze handoff and issue report record a historical total of 209 acceptance items (P0 126, P1 82, one priority unresolved). A read-only inventory of the current repository and its reachable Git history did not find the original 209-row case list, case IDs, or row-to-evidence mapping.

This is a traceability gap, not evidence that 209 behaviors failed or that no tests ran. Existing test totals and grouped UI checks cannot be promoted to individual case sign-offs without a defensible mapping.

## Source audit

The source files provide useful reconstruction inputs, but they are not interchangeable case rows:

- `docs/CORE_CONTRACT_FREEZE_2026-10-21.md`: 33 product review decisions, core invariants, scope clauses, and later amendments.
- `docs/archive/PROTOCOL_V0_RESEARCH.md`: 18 candidate invariants and 22 example scenarios. The file itself labels these as candidate criteria/prototype examples.
- `docs/STABLE_DATA_CONTRACT_V1.md`: 10 minimum acceptance scenarios for data, backup, migration, signing, and privacy.
- `docs/ANDROID_EMULATOR_REGRESSION_QA_2026-10-23.md`: 11 grouped Android paths, explicitly stated not to represent 11 individually mapped cases.
- `docs/FREEZE_ISSUE_REPORT_2026-10-23.md` and the handoff files repeat the aggregate and the missing-ledger finding; they do not contain the row definitions.
- Repository history contains the same source documents and planning records, but no earlier 209-row manifest. A filename inventory found no acceptance spreadsheet/document; the only matching CSV is the unrelated Android emulator `NOTICE.csv`.

Audit evidence: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-source-audit.json` (SHA-256 `600A1D825E9349BCD183053CB2DB59F62A02A50BB862B554238D020FDDFC4E98`). The audit was taken at Git baseline `b4ccaaf4770e4b6eaf39cd3288a9a6b83771e489`.

## Current sign-off accounting

| Bucket from historical aggregate | Total | Matched to original ID | Result |
| --- | ---: | ---: | --- |
| P0 | 126 | 0 | Unassessed by original ID |
| P1 | 82 | 0 | Unassessed by original ID |
| Priority unresolved | 1 | 0 | Remains unclassified and unassessed |

The latest full Gradle run is **364/364 PASS**, with 0 failures/errors/skips, successful skin-boundary verification and Debug assembly, and lint 0 errors / 55 warnings. Its log is `.local/android-test/reports/freeze-20261023/issue001-dst-full-gradle-20261023.log` (SHA-256 `02A3FB2D275B2BD225BE102960DAD1D7B02AD2829028C10E312FBD8DC0F74DA5`). This is not a 364-to-209 mapping.

## Required next work

1. Build a **RECONSTRUCTED** case ledger from the approved contract, amendments, data/release contract, protocol scenarios, and verified issue evidence. New IDs must not masquerade as historical IDs.
2. Give every row a source locator, proposed priority, environment, repeatable action, expected result, observed result, evidence path/hash, source Git/APK baseline, and status (`PASS`, `FAIL`, `BLOCKED`, `NOT TESTED`, or `SPEC UNRESOLVED`).
3. Mark inferred priority assignments as proposals for review. Do not distribute the historical 126/82 totals arbitrarily; reconcile reconstructed rows to those totals only where a source-backed rationale exists.
4. Keep the one historical unclassified item visibly unresolved until its source or an explicit review decision is available.
5. Re-run open cases where feasible; preserve physical-device, permission, OEM, and specification gaps as HOLD.

## Scope and outcome

No application code or business data was changed for this audit. No tests were claimed as newly run by this source search. The source audit is complete; the ledger and freeze gate are not. PC-QA-GATE-001 remains **PARTIAL / HOLD**, and overall core verdict remains **HOLD**.
