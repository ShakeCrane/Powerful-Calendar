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

Structural validation: 209 unique IDs; all reconstructed rows contain source, environment, action, expected and actual fields; PASS/BLOCKED rows include evidence references. Latest validation JSON: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-006.json` (ledger SHA-256 `ABD313A4A35350347A864D2B472E2A540C461B9D39982DD691A058C11E9F05A9`). Structural validation does not test the app or prove the historical mapping.

| Candidate bucket | PASS | FAIL | BLOCKED | NOT TESTED | SPEC UNRESOLVED |
| --- | ---: | ---: | ---: | ---: | ---: |
| P0 (proposed) | 19 | 0 | 1 | 102 | 4 |
| P1 (proposed) | 19 | 0 | 8 | 54 | 1 |
| Historical unclassified slot | 0 | 0 | 0 | 0 | 1 |
| **Total candidate rows** | **38** | **0** | **9** | **156** | **6** |

### Case-specific backup verification (2026-10-23)

Three reconstructed P0 backup-contract rows were individually exercised in JVM SQLite fixtures on code baseline `574092de1046763c4aad40d7db4a2dd9123848fa`; no device was used for this batch. Targeted Debug APK SHA-256: `53C200F398D13D636125572DBD0C4876FEA1A18585323C935F9C13F8E061E103`.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-091` — Envelope canonical payload hash | `BackupEnvelopeTest.exportProducesEnvelopeV1WithMatchingCanonicalHash` | PASS: `payloadSha256` matched SHA-256 over canonical payload bytes. |
| `REC-P0-097` — reject invalid `nextSeq` watermark | `BackupEnvelopeTest.aLowSequenceWatermarkIsRejected` | PASS: rejected `nextSeq=1`; target sequence preview remained unchanged. |
| `REC-P0-106` — reject downgrade after stripping Envelope marker | `BackupEnvelopeTest.flatV1BackupIsExplicitlyRejectedAndEnvelopeCannotBeDowngradedToLegacy` | PASS: flat v1 and stripped Envelope were rejected even with legacy consent; target identity count remained 0. |

Command: `.\gradlew.bat :app:testDebugUnitTest --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.exportProducesEnvelopeV1WithMatchingCanonicalHash" --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.aLowSequenceWatermarkIsRejected" --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.flatV1BackupIsExplicitlyRejectedAndEnvelopeCannotBeDowngradedToLegacy" --offline --rerun-tasks --console=plain`. Result: **3 passed, 0 failed/errors/skipped**. Log: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-backup-casebatch-20261023/targeted-tests.log` (SHA-256 `F14D6EB7B5D96678EF84B4B6177B69446C27DF46AC5820712C5E424921D4CAD9`). The exact method results are also present in Gradle's `TEST-dev.powerfulcalendar.core.BackupEnvelopeTest.xml`. This targeted run does not replace or inflate the separate full working-tree run.

The full working-tree run recorded with the initial backup batch included one test from protected, untracked `DraftDeckFeedbackTest.kt` (365 tracked tests plus 1 untracked). The current latest 372-test run is recorded below under PC-ISSUE-006 (371 tracked plus 1 protected untracked). Neither grouped result nor the targeted case batches establishes historical case-ID mapping.

### Case-specific retired-event restoration (2026-10-23)

`REC-P0-055` and `REC-P0-056` were exercised on code commit `40e5dd35bd238da476f67507994b6c100b646b89`, Debug APK SHA-256 `9677B9816B9028EF5C25EC84025ABA56DDB92BEB5CF36B2197AC5615315BB0AE`, using JVM SQLite fixtures under Robolectric SDK 34 and the pure Kotlin Draft Deck controller.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-055` — explicit approval before restoring a tombstone | `PowerStoreTest.explicitRestoreProposalKeepsTombstoneUntilHumanConfirmationAndReusesIdentity`; `DeckControllerTest.explicitRestoreEntryOpensProposalAndRequiresSeparateConfirmation` | PASS: proposal leaves tombstone retired, commit asks for restore, and the controller requires a separate confirmation; non-user restore actor is rejected. |
| `REC-P0-056` — restore the same event identity | `PowerStoreTest.explicitRestoreProposalKeepsTombstoneUntilHumanConfirmationAndReusesIdentity` | PASS: user confirmation reactivates the same `eventSeq` at revision +1; sequence watermark is unchanged and desired reminder is rebuilt. |

Targeted log: `.local/android-test/reports/freeze-20261023/pc-issue-005-restore-core-targeted-final-20261023.log` (SHA-256 `A60E93DB1727BE512F2C31A11EF3FBA7A50CAE661F1369A179017F9E871FBFBC`), **2 passed / 0 failed/errors/skipped**. Full Gradle regression: 366 passed / 0 failed/errors/skipped, lint 0 errors / 55 warnings; log `.local/android-test/reports/freeze-20261023/pc-issue-005-restore-full-gradle-final-20261023.log` (SHA-256 `0F0271F238F4D6ACA62B0916A132854305740ECABB7CD7CCC12108A2C58B91F3`). No Android device was used and no UI entry point invokes `beginRestore`; see `docs/issues/PC-ISSUE-005-retired-event-restoration.md` for the product-path HOLD.

### Case-specific native backup round-trip contracts (2026-10-23)

`REC-P0-094/095/096/098/099/100/101/109` were exercised on test commit `5df9e00b04ad50dc5c4e12154cf117cc4a159a23`; Debug APK SHA-256 `9677B9816B9028EF5C25EC84025ABA56DDB92BEB5CF36B2197AC5615315BB0AE`. These are JVM SQLite fixtures under Robolectric SDK 34; no emulator or physical device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-094` — unknown backup format | `BackupEnvelopeTest.unknownBackupFormatIsRejectedBeforeAnyRowIsWritten` | PASS: unknown `format` rejected before identities or allocator state changed. |
| `REC-P0-095` — unknown format version | `BackupEnvelopeTest.unknownFutureBackupFormatVersionIsRejected` | PASS: version 99 rejected before any identity row is written. |
| `REC-P0-096` — dangling references | `BackupEnvelopeTest.danglingDraftAndCommitReferencesAreRejectedBeforeAnyWrite` | PASS: dangling draft→identity and commit→draft references both rejected during preflight; target remains empty. |
| `REC-P0-098` — invalid `nextOrder` | `BackupEnvelopeTest.aLowNextOrderWatermarkIsRejected` | PASS: low queue watermark rejected; target remains empty and allocator unchanged. |
| `REC-P0-099` — retired event export | `BackupEnvelopeTest.retiredEventExportPreservesTombstoneAndSequenceWatermark` | PASS: tombstone metadata and retired object survive export/restore; sequence watermark is preserved. |
| `REC-P0-100` — pending draft identity/order | `BackupEnvelopeTest.roundTripPreservesDraftIdentityQueueOrderAndCommittedRevisionHistory` | PASS: `draftId`, `queueOrder`, and canonical draft rows survive round-trip. |
| `REC-P0-101` — committed revision history | `BackupEnvelopeTest.roundTripPreservesDraftIdentityQueueOrderAndCommittedRevisionHistory` | PASS: revision 2 snapshot and revisions 1/2 commit history survive round-trip. |
| `REC-P0-109` — exclude credentials and scheduler handles | `BackupEnvelopeTest.nativeBackupExcludesApiCredentialsAndSchedulerHandlesThenRebuildsSchedule` | PASS: API key and AI/scheduler/execution state excluded; reminder intent rebuilt from restored alarm; key unchanged. |

Targeted command result: **7 passed / 0 failed/errors/skipped**. Log: `.local/android-test/reports/freeze-20261023/pc-issue-006-backup-contract-targeted-final-20261023.log` (SHA-256 `A7E99AF0F314D1129C229014B2CB2127B90E26E3126A8368B02380097B3DEF09`). The first run had one assertion wording mismatch and is retained separately; the complete group was rerun after correcting the expectation to the validator's actual error phrase. Full regression: **372 passed / 0 failed/errors/skipped**, lint 0 errors / 55 warnings; log `.local/android-test/reports/freeze-20261023/pc-issue-006-backup-contract-full-gradle-final-20261023.log` (SHA-256 `A77F74970305AB3A4CE82FE49544726092EEBA718401D325A1953384435DCB36`). One protected untracked test was included (371 tracked + 1 protected untracked); no protected file was modified or staged. See independent report `docs/issues/PC-ISSUE-006-backup-roundtrip-contract.md`.

## Historical sign-off accounting

| Bucket from historical aggregate | Total | Matched to original ID | Result |
| --- | ---: | ---: | --- |
| P0 | 126 | 0 | Unassessed by original ID |
| P1 | 82 | 0 | Unassessed by original ID |
| Priority unresolved | 1 | 0 | Remains unclassified and unassessed |

The latest full working-tree Gradle run reports 372 tests PASS, 0 failures/errors/skips, successful skin-boundary verification and Debug assembly, and lint 0 errors / 55 warnings. It includes one test from protected, untracked `app/src/test/java/dev/powerfulcalendar/ui/v2/DraftDeckFeedbackTest.kt` (371 tracked tests + 1 untracked); the file was not modified or staged. Log: `.local/android-test/reports/freeze-20261023/pc-issue-006-backup-contract-full-gradle-final-20261023.log` (SHA-256 `A77F74970305AB3A4CE82FE49544726092EEBA718401D325A1953384435DCB36`). This is neither a clean committed-tree 372-test count nor a 372-to-209 mapping.

## Required next work

1. Run the 156 `NOT TESTED` rows under the environment each row requires and update only with case-specific evidence.
2. Resolve the one blocked P0, eight blocked P1, and five reconstructed spec-unresolved rows where feasible; retain external/physical/device dependencies as HOLD. The separate historical unclassified slot remains unresolved pending its source.
3. Review the source-based proposed priority allocation. New IDs must never masquerade as historical IDs.
4. Keep the one historical unclassified item visibly unresolved until its source or an explicit review decision is available.
5. Preserve the physical-device, permission, OEM, and specification gaps as HOLD.

## Scope and outcome

Core code and tests now have evidence for 13 reconstructed rows across the backup and tombstone batches; 156 remain NOT TESTED, proposed priorities still need review, and historical mapping is still absent. PC-QA-GATE-001 remains **PARTIAL / HOLD**, and overall core verdict remains **HOLD**.
