# PC-QA-GATE-001 — 209-case acceptance traceability

Date: 2026-10-23 | Severity: P0

Status: **PARTIAL / HOLD — source audit complete; initial reconstructed ledger exists; execution and priority review remain open.**

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

## Initial reconstructed candidate ledger

`docs/CORE_FREEZE_ACCEPTANCE_LEDGER.csv` contains 208 reconstructed rows and one unresolved historical slot. Proposed priority allocation is P0=126 and P1=82, grouped by risk/source area; this preserves the known aggregate for review but does not establish which historical case each row represents. All reconstructed rows are explicitly `UNMAPPED`.

Structural validation: 209 unique IDs; all reconstructed rows contain source, environment, action, expected and actual fields; PASS/BLOCKED rows include evidence references. The validation JSON is `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation.json`. Structural validation does not test the app or prove the historical mapping.

| Candidate bucket | PASS | FAIL | BLOCKED | NOT TESTED | SPEC UNRESOLVED |
| --- | ---: | ---: | ---: | ---: | ---: |
| P0 (proposed) | 6 | 0 | 1 | 115 | 4 |
| P1 (proposed) | 19 | 0 | 8 | 54 | 1 |
| Historical unclassified slot | 0 | 0 | 0 | 0 | 1 |

The latest Gradle run is from the working tree and includes one test from protected, untracked `DraftDeckFeedbackTest.kt` (363 tracked tests plus 1 untracked), so it is not a clean committed-tree test count. It remains separate group-level evidence, not a 209-row sign-off.

## Historical sign-off accounting

| Bucket from historical aggregate | Total | Matched to original ID | Result |
| --- | ---: | ---: | --- |
| P0 | 126 | 0 | Unassessed by original ID |
| P1 | 82 | 0 | Unassessed by original ID |
| Priority unresolved | 1 | 0 | Remains unclassified and unassessed |

The latest full working-tree Gradle run reports 364 tests PASS, 0 failures/errors/skips, successful skin-boundary verification and Debug assembly, and lint 0 errors / 55 warnings. It includes one test from protected, untracked `app/src/test/java/dev/powerfulcalendar/ui/v2/DraftDeckFeedbackTest.kt` (363 tracked tests + 1 untracked); the file was not modified or staged. Log: `.local/android-test/reports/freeze-20261023/issue001-dst-full-gradle-20261023.log` (SHA-256 `02A3FB2D275B2BD225BE102960DAD1D7B02AD2829028C10E312FBD8DC0F74DA5`). This is neither a clean committed-tree 364-test count nor a 364-to-209 mapping.

## Required next work

1. Run the 169 `NOT TESTED` rows under the environment each row requires and update only with case-specific evidence.
2. Resolve the one blocked P0, eight blocked P1, and five spec-unresolved reconstructed rows where feasible; retain external/physical/device dependencies as HOLD.
3. Review the source-based proposed priority allocation. New IDs must never masquerade as historical IDs.
4. Keep the one historical unclassified item visibly unresolved until its source or an explicit review decision is available.
5. Preserve the physical-device, permission, OEM, and specification gaps as HOLD.

## Scope and outcome

No application code or business data was changed for this audit. The source audit and initial candidate ledger are complete; candidate execution, priority review, and historical mapping are not. PC-QA-GATE-001 remains **PARTIAL / HOLD**, and overall core verdict remains **HOLD**.
