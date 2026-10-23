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

Structural validation: 209 unique IDs; all reconstructed rows contain source, environment, action, expected and actual fields; PASS/BLOCKED rows include evidence references. Latest validation JSON: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-017.json` (ledger SHA-256 `F60E0F065620CACA09D396E284AA41B124E6D47EDF10F20BEF2F2564B5BD3F3A`). Structural validation does not test the app or prove the historical mapping.

| Candidate bucket | PASS | FAIL | BLOCKED | NOT TESTED | SPEC UNRESOLVED |
| --- | ---: | ---: | ---: | ---: | ---: |
| P0 (proposed) | 44 | 0 | 1 | 77 | 4 |
| P1 (proposed) | 19 | 0 | 8 | 54 | 1 |
| Historical unclassified slot | 0 | 0 | 0 | 0 | 1 |
| **Total candidate rows** | **63** | **0** | **9** | **131** | **6** |

### Case-specific backup verification (2026-10-23)

Three reconstructed P0 backup-contract rows were individually exercised in JVM SQLite fixtures on code baseline `574092de1046763c4aad40d7db4a2dd9123848fa`; no device was used for this batch. Targeted Debug APK SHA-256: `53C200F398D13D636125572DBD0C4876FEA1A18585323C935F9C13F8E061E103`.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-091` — Envelope canonical payload hash | `BackupEnvelopeTest.exportProducesEnvelopeV1WithMatchingCanonicalHash` | PASS: `payloadSha256` matched SHA-256 over canonical payload bytes. |
| `REC-P0-097` — reject invalid `nextSeq` watermark | `BackupEnvelopeTest.aLowSequenceWatermarkIsRejected` | PASS: rejected `nextSeq=1`; target sequence preview remained unchanged. |
| `REC-P0-106` — reject downgrade after stripping Envelope marker | `BackupEnvelopeTest.flatV1BackupIsExplicitlyRejectedAndEnvelopeCannotBeDowngradedToLegacy` | PASS: flat v1 and stripped Envelope were rejected even with legacy consent; target identity count remained 0. |

Command: `.\gradlew.bat :app:testDebugUnitTest --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.exportProducesEnvelopeV1WithMatchingCanonicalHash" --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.aLowSequenceWatermarkIsRejected" --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.flatV1BackupIsExplicitlyRejectedAndEnvelopeCannotBeDowngradedToLegacy" --offline --rerun-tasks --console=plain`. Result: **3 passed, 0 failed/errors/skipped**. Log: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-backup-casebatch-20261023/targeted-tests.log` (SHA-256 `F14D6EB7B5D96678EF84B4B6177B69446C27DF46AC5820712C5E424921D4CAD9`). The exact method results are also present in Gradle's `TEST-dev.powerfulcalendar.core.BackupEnvelopeTest.xml`. This targeted run does not replace or inflate the separate full working-tree run.

The full working-tree run recorded with the initial backup batch included one test from protected, untracked `DraftDeckFeedbackTest.kt` (365 tracked tests plus 1 untracked). The later PC-ISSUE-006 run recorded 372 tests (371 tracked plus 1 protected untracked); the 373-test run current at the time of PC-ISSUE-007 is recorded below; the 374-test run current at the time of PC-ISSUE-008 is recorded below; the latest 375-test run is recorded under PC-ISSUE-009. Neither grouped result nor the targeted case batches establishes historical case-ID mapping.

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

### Case-specific concurrent restore recheck (2026-10-23)

`REC-P0-103` was exercised on test commit `2171c3f96fb69a1e6d14e1fb170d4c53a9439184`, Debug APK SHA-256 `63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`. A deterministic test seam runs a second `PowerStore` connection write after preflight and before restore begins its transaction; the transactional guard rejects restore and leaves the new identity, draft, and allocator untouched.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-103` — write appears after preflight | `BackupEnvelopeTest.restoreRechecksAfterWriteBetweenPreflightAndTransaction` | PASS: second SQLite store commits a draft after preflight; transaction recheck throws `NonEmptyRestoreTargetException`; only the concurrent identity/draft remain and `nextSeq=2`. |

Targeted command result: **1 passed / 0 failed/errors/skipped**. Log `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-concurrent-restore-targeted-final-20261023.log` (SHA-256 `1B1880767397A7F2B2C86A633BCBDD9B3D8E369EB1594C3802FE47FDABC886DE`). Several earlier true-lock interleavings failed inside Robolectric SQLite before the app guard could run; those are listed in `docs/issues/PC-ISSUE-007-post-preflight-concurrent-restore.md` and the local exploratory manifest. One intermediate log was overwritten when its path was reused by the final run; the missing artifact is disclosed and excluded from sign-off. Full regression: **373 passed / 0 failed/errors/skipped**, lint 0 errors / 55 warnings; log `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-concurrent-restore-full-gradle-final-20261023.log` (SHA-256 `7A4CE9C9BA3D88854799F9D820E7D1B0DD36EFAF7F0154550F41724832F8075F`). One protected untracked test was included (372 tracked + 1 protected untracked). See independent report `docs/issues/PC-ISSUE-007-post-preflight-concurrent-restore.md`.

## Case-specific database migration atomicity (2026-10-23)

`REC-P0-083/085/086/087/088` are individually signed against exact tests on code commit `ef901e0d5878bb4b25c61ed10dd1021cf312ca97`; Debug APK SHA-256 `63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`. The v3 fixture injects failure on the second duplicate-draft update, after the first update and audit insert have run; rollback restores all three PENDING rows and the original schema, then retry upgrades the same database successfully. The v1/v2 fixtures are synthetic on-disk schemas based on historical DDL, not actual user databases.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-083` — version axes | `VersionContractTest.schemaBackupAndPackageVersionsAreSeparateAxes` | PASS: package, schema and backup versions remain independently defined. |
| `REC-P0-085` — ordered migrations | `PowerStoreSchemaUpgradeTest.upgradingRealV1DataInPlacePreservesRowsAndAddsOnlyTheReviewedTable`; `PowerStoreSchemaUpgradeTest.upgradingRealV2AddsLineageInPlaceWithoutTouchingBusinessRows`; `PowerStoreSchemaUpgradeTest.retryingAHalfAppliedLineageMigrationSucceedsWithoutTouchingRows` | PASS: supported upgrade steps reach current schema and preserve legacy rows. |
| `REC-P0-086` — migration transaction | `PowerStoreSchemaUpgradeTest.failedV4MigrationRollsBackAndCanRetryWithoutLosingLegacyRows` | PASS: after one duplicate update/audit succeeds, the injected second update fails; rollback retains v3 and restores all three pending rows. |
| `REC-P0-087` — failure recovery | `PowerStoreSchemaUpgradeTest.failedV4MigrationRollsBackAndCanRetryWithoutLosingLegacyRows` | PASS: exception identifies the injected trigger; removing it allows an in-place retry to schema v4. |
| `REC-P0-088` — legacy schema preservation | `PowerStoreSchemaUpgradeTest.upgradingRealV1DataInPlacePreservesRowsAndAddsOnlyTheReviewedTable`; `PowerStoreSchemaUpgradeTest.upgradingRealV2AddsLineageInPlaceWithoutTouchingBusinessRows` | PASS on synthetic historical-schema fixtures; no real user database was opened. |

Targeted schema suite: **4/4 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-008-migration-targeted-partial-write-20261023.log` (SHA-256 `BDF989FBB7D5F170E5C949684B85E76C8670F89119324D8783FB935B3711E7A0`); full regression: **374/374 PASS**, 0 lint errors / 55 warnings. Exact logs and hashes are in `docs/issues/PC-ISSUE-008-database-migration-atomicity.md`.

## Case-specific recurrence identity and exception scope (2026-10-23)

`REC-P0-060/061/062/063/066/067/068/070/071` are individually signed against JVM tests on code commit `67648e5b6c4c744034884e408f8835b208306b29`; Debug APK SHA-256 `63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`. No Android device was used, and the tests do not set a DST gap/fold policy.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-060` | `PowerCalMemoContractTest.recurrenceEffectiveDatesDoNotShiftCycleAnchor` | PASS: an inclusive active end date does not rephase the cycle. |
| `REC-P0-061` | `PowerCalMemoContractTest.recurrenceEffectiveDatesDoNotShiftCycleAnchor` | PASS: active start date is inclusive; original anchor remains. |
| `REC-P0-062` | `PowerCalMemoContractTest.recurrenceEffectiveDatesDoNotShiftCycleAnchor` | PASS: active end date is inclusive and bounds projection. |
| `REC-P0-063` | `TemporalProtocolTest.anOldRecurringSeriesStillMaterialisesInACurrentWindow` | PASS: only instances inside the requested finite window are materialized. |
| `REC-P0-066` | `TemporalProtocolTest.movedOccurrenceKeepsItsOriginalKey` | PASS: moved time does not replace the original slot key. |
| `REC-P0-067` | `OccurrenceWindowTest.anInstanceMovedFromTwoMonthsAgoIsFoundInTodaysWindow`; `OccurrenceWindowTest.movingAnInstanceAcrossTheSpringForwardGapKeepsTheEffectiveInstant` | PASS: moving across dates preserves original key; the DST test checks effective instant without defining a policy. |
| `REC-P0-068` | `TemporalProtocolTest.skippedOccurrenceDoesNotShiftTheRestOfTheSeries` | PASS: skip affects one slot and later slots retain their original dates. |
| `REC-P0-070` | `OccurrenceWindowTest.anInstanceMovedFromTwoMonthsAgoIsFoundInTodaysWindow` | PASS: moved and natural occurrence each appear once under distinct keys. |
| `REC-P0-071` | `PowerCalMemoContractTest.occurrenceExceptionsAreScopedToTheirOwnSeries` | PASS: an exception on one event sequence does not skip the same slot in another series. |

Targeted result: **7/7 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-009-recurrence-targeted-20261023.log` (SHA-256 `44B4AF96CDD9013F2274BC2F921C4634845FF6F55990AE659C83C85BEB87F2A4`); full regression: **375/375 PASS**, lint 0 errors / 55 warnings. See `docs/issues/PC-ISSUE-009-recurrence-instance-identity.md` for the three JUnit XML hashes and full run hash.

## Case-specific cross-midnight recurrence (2026-10-23)

`REC-P0-072/073` are individually signed on code commit `ee9913b953a1165b923cb1f2238755580baeaeb3`; Debug APK SHA-256 remains `63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`. These are JVM temporal-model checks; no device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-072` | `OccurrenceWindowTest.crossMidnightActivityRecurrenceBoundsUseTheOriginalStartDate` | PASS: on Oct 21, an Oct 20 23:30 carry-in remains eligible through the inclusive Oct 20 active end; an Oct 21 start is excluded. An Oct 21 active start excludes the previous day's carry-in. |
| `REC-P0-073` | `OccurrenceWindowTest.crossMidnightActivityRecurrencePreservesItsTrueDuration` | PASS: 23:30–01:15 repeated Activity instances preserve their 105-minute duration and next-day end instant. |

Targeted class run: **19/19 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-010-cross-midnight-targeted-final-20261023.log` (SHA-256 `8CF359ED5B02675EF95C78DC74B5A7A12A453F7BB676F4E7BC60AAAF362972E2`); JUnit XML SHA-256 `C416553CA179F84969ABCE3361071197489C7C12F9F69605E710ABFECCACC778`. Full run: **377/377 PASS**, lint 0 errors / 55 warnings. See `docs/issues/PC-ISSUE-010-cross-midnight-recurrence-boundary.md` for complete commands, evidence paths, and limitations.

## Case-specific date-span Memo completion (2026-10-23)

`REC-P0-009` is individually signed on code commit `a63bb6457dd47b0c98c648abd1dcdb9409aa4c87`; Debug APK SHA-256 remains `63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`. This is an isolated JVM SQLite/Robolectric test; no device or production database was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-009` | `PowerStoreTest.spanningMemoHasOneCompletionInstanceAndKeepsItAcrossRestart` | PASS: a 2026-10-21–23 Memo accepts only `single`; a date key is rejected without a revision change; completion and both dates persist after reopening SQLite. |

Targeted result: **1/1 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-011-spanning-memo-targeted-20261023.log` (SHA-256 `AD370BF477724A308D901F97587F5CA75D595000B1705C73DD41F495559AC3CB`); JUnit XML SHA-256 `A560FF10ACEB72CBC103BFBF9AF9B1EC4B1BB9BFF11ADE25AB9898EC1CEE2321`. Full run: **378/378 PASS**, lint 0 errors / 55 warnings. See `docs/issues/PC-ISSUE-011-spanning-memo-completion.md` for complete evidence and limitations.

## Case-specific preservation of legacy tracking state (2026-10-23)

`REC-P0-010` is individually signed on code commit `96d692782bf8f68187edb53379a7a2cd065672bb`; Debug APK SHA-256 remains `63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`. The test opens an isolated on-disk v1 SQLite fixture under Robolectric; no user database or Android device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-010` | `PowerStoreSchemaUpgradeTest.upgradingRealV1DataInPlacePreservesRowsAndAddsOnlyTheReviewedTable` | PASS: v1 Activity payload without a tracking field remains untracked with no completion keys; manual completion is rejected and its revision stays unchanged. |

Targeted result: **1/1 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-012-legacy-untracked-targeted-final-20261023.log` (SHA-256 `EBC6ACE5862884209103552DC67B558F9D33234BAAB3738275B61BED977EE76E`); JUnit XML SHA-256 `FD92FB8B00100279524D84928833BFBE769B7979BBEB700FBBF680F547374B2E`. Full run: **378/378 PASS**, lint 0 errors / 55 warnings. See `docs/issues/PC-ISSUE-012-legacy-untracked-record-contract.md` for complete evidence and limitations.

## Case-specific reminder and completion-tracking independence (2026-10-23)

`REC-P0-011` is individually signed on code commit `eea569cf50466ca0de6ab9ccd7e6ac9983149172`; Debug APK SHA-256 remains `63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`. This is an isolated JVM SQLite/Robolectric check; no device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-011` | `PowerStoreTest.changingReminderPreservesOptionalCompletionTracking` | PASS: changing reminder offsets on both tracked and untracked Activity snapshots preserves their tracking state and leaves completion keys unchanged. |

Targeted result: **1/1 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-013-reminder-tracking-targeted-final-20261023.log` (SHA-256 `4B0AF0679CC40DB014476E00835B747082BAB56E89978E863EC3715A2CB11F7F`); JUnit XML SHA-256 `49FFE07282EB24C5830FB62B2E9C411D96CEF57799481043A394F848EA548A62`. Full run: **379/379 PASS**, lint 0 errors / 55 warnings. See `docs/issues/PC-ISSUE-013-reminder-tracking-separation.md` for complete evidence and limitations.

## Case-specific tracking and enabled-state independence (2026-10-23)

`REC-P0-014` is individually signed on code commit `574a2688bd9a651267e70071e705c733b16c99ae`; Debug APK SHA-256 remains `63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`. This is an isolated JVM SQLite/Robolectric check; no device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-014` | `PowerStoreTest.changingCompletionTrackingPreservesEventEnabledState` | PASS: enabling/disabling completion tracking preserves the preexisting enabled state and reminder offset. |

Targeted result: **1/1 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-014-tracking-enabled-targeted-final-20261023.log` (SHA-256 `6F9B2230A8106E07E9953FB5C9A2267D87CDB94A6FA17904C79E97DEB8B7930B`); JUnit XML SHA-256 `E97B1055C8ECDA31998DA2256E5C03570282D1D7283F107EB79539648340D357`. Full run: **380/380 PASS**, lint 0 errors / 55 warnings. See `docs/issues/PC-ISSUE-014-tracking-enabled-separation.md` for complete evidence and limitations.

## Case-specific reminder completion isolation (2026-10-23)

`REC-P0-012/013` are individually signed on code/test commit `d74d55a3bb05893606f7da223beaa0b5f7a5819d`; Debug APK SHA-256 is `63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`. The tests use Robolectric API 34 and an isolated SQLite database; they do not establish actual Android AlarmManager delivery or a user swipe in the system notification shade.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-012` | `ReminderCompletionIsolationTest.firedAlarmRecordsExecutionWithoutCompletingTrackedEvent` | PASS: synthetic receiver `ACTION_FIRE` records `FIRED` and clears `intentFor`; completion keys stay empty and revision is unchanged. |
| `REC-P0-013` | `ReminderCompletionIsolationTest.autoCancelledReminderHasNoCompletionDeleteCallback` | PASS: `FLAG_AUTO_CANCEL`, null `deleteIntent`, and unchanged completion state/revision after shadow notification cancellation. |

Post-commit targeted result: **2/2 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-015-reminder-completion-targeted-final-20261023.log` (SHA-256 `2010C351D82318FC0E3D1154BE91AD64FECD4CC8B1025117870DC8C5D2D2A673`); JUnit XML SHA-256 `912B76691F6C185AA5AEA95377663CFDE0FC2F21D2725F3627CC7BB61E7CF033`. Full regression: **382/382 PASS**, 55 suites, lint 0 errors / 55 warnings; full log SHA-256 `6719782E203DFCC6E477BFF6E5F56DFD717BEA8568514C0FB4FC14A925675CE7`. The full run used the same test source later committed as `d74d55a`; it includes 381 tracked tests plus one protected untracked test. See `docs/issues/PC-ISSUE-015-reminder-completion-isolation.md` for the precise system/device limitations.

## Case-specific Alarm point semantics (2026-10-23)

`REC-P0-001` is individually signed on code/test commit `48c4bf5e63f6cfdbe471a7c84720cf754965d975`; Debug APK SHA-256 is `63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`. This is a pure JVM `DraftForm`/`TemporalRecord` contract test; no UI or device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-001` | `TemporalProtocolTest.alarmBuildStoresOnlyItsStartPoint` | PASS: even with leftover `endText`, the built Alarm retains only its start instant, has null `endMillis`, and has a point span ending at the start. |

Post-commit targeted result: **35/35 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-016-alarm-point-targeted-final-20261023.log` (SHA-256 `7A562FD523F64B1E68F4081124567CFD83566D460F7E158A923C663CC0998ADA`); JUnit XML SHA-256 `F16B3B3C65CF844F638DBED22A2E166EC565DF184306215ED8C831D507489740`. Full regression: **383/383 PASS**, 55 suites, lint 0 errors / 55 warnings; full log SHA-256 `BF1E98E0B93766EA3C6949A7FD6C9294D29E3ECF418F199464D1C308E7109091`. The full run included the same test source later committed as `48c4bf5`; 382 tracked tests plus one protected untracked test. See `docs/issues/PC-ISSUE-016-alarm-point-semantics.md`.

## Case-specific Activity Trigger identity (2026-10-23)

`REC-P0-015` is individually signed on code/test commit `9ed3b2c5cdaef406f79f528e590bccd675e062af`; Debug APK SHA-256 is `63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`. The test uses isolated SQLite under Robolectric API 34; no UI or device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-015` | `ReminderPersistenceTest.activityCanChooseExactAlarmOrMessageIndependentlyOfKind` | PASS: the exact trigger retains the Activity `eventSeq`, the event remains `ACTIVITY`, and no second event identity is created. |

Post-commit targeted result: **2/2 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-017-trigger-kind-targeted-final-20261023.log` (SHA-256 `6F9B2230A8106E07E9953FB5C9A2267D87CDB94A6FA17904C79E97DEB8B7930B`); JUnit XML SHA-256 `3373800E3B61639B83B537EE132BC68A6C5DF66511D204E5C2D546B0167A0DBB`. Full regression: **383/383 PASS**, 55 suites, lint 0 errors / 55 warnings; full log SHA-256 `56837893A25A52E940516836DBEAD2FC3E96FAFDA4217DA624C74E32E6A5518E`. The full run used the test source later committed as `9ed3b2c`. See `docs/issues/PC-ISSUE-017-trigger-event-kind-separation.md`.

## Historical sign-off accounting

| Bucket from historical aggregate | Total | Matched to original ID | Result |
| --- | ---: | ---: | --- |
| P0 | 126 | 0 | Unassessed by original ID |
| P1 | 82 | 0 | Unassessed by original ID |
| Priority unresolved | 1 | 0 | Remains unclassified and unassessed |

The latest full working-tree Gradle run reports 383 tests PASS, 0 failures/errors/skips across 55 JUnit suites, successful skin-boundary verification and Debug assembly, and lint 0 errors / 55 warnings. It includes one test from protected, untracked `app/src/test/java/dev/powerfulcalendar/ui/v2/DraftDeckFeedbackTest.kt` (382 tracked tests + 1 untracked); the file was not modified or staged. The test assertions are committed as `9ed3b2c5cdaef406f79f528e590bccd675e062af`. Log: `.local/android-test/reports/freeze-20261023/pc-issue-017-trigger-kind-full-gradle-20261023.log` (SHA-256 `56837893A25A52E940516836DBEAD2FC3E96FAFDA4217DA624C74E32E6A5518E`). This is not a 383-to-209 mapping.

## Required next work

1. Run the 131 `NOT TESTED` rows under the environment each row requires and update only with case-specific evidence.
2. Resolve the one blocked P0, eight blocked P1, and five reconstructed spec-unresolved rows where feasible; retain external/physical/device dependencies as HOLD. The separate historical unclassified slot remains unresolved pending its source.
3. Review the source-based proposed priority allocation. New IDs must never masquerade as historical IDs.
4. Keep the one historical unclassified item visibly unresolved until its source or an explicit review decision is available.
5. Preserve the physical-device, permission, OEM, and specification gaps as HOLD.

## Scope and outcome

Core code and tests now have evidence for 38 reconstructed rows across backup, tombstone, migration, recurrence, reminder, and Alarm model contracts; 131 remain NOT TESTED, proposed priorities still need review, and historical mapping is still absent. PC-QA-GATE-001 remains **PARTIAL / HOLD**, and overall core verdict remains **HOLD**.
