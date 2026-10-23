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

Structural validation: 209 unique IDs; all reconstructed rows contain source, environment, action, expected and actual fields; PASS/BLOCKED rows include evidence references. Latest validation JSON: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-039.json`, SHA-256 `8E0AF32DB5C48F5B8F81A9382BD781286EEB54BDA6A29E08B9F68BA49470ACF8`; ledger SHA-256 `6D4B739D53AF417FDD188DD5D2AEC40243CFF6408368517E0ED2C2CFE4A3C6D4`. Structural validation does not test the app or prove the historical mapping.

| Candidate bucket | PASS | FAIL | BLOCKED | NOT TESTED | SPEC UNRESOLVED |
| --- | ---: | ---: | ---: | ---: | ---: |
| P0 (proposed) | 66 | 0 | 1 | 55 | 4 |
| P1 (proposed) | 19 | 0 | 8 | 54 | 1 |
| Historical unclassified slot | 0 | 0 | 0 | 0 | 1 |
| **Total candidate rows** | **85** | **0** | **9** | **109** | **6** |

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

`REC-P0-012/013` are individually signed on stabilized code/test commit `d60809b4133a45c0551019f1edabec1195d34cf3`; Debug APK SHA-256 is `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. The tests use Robolectric API 34 and an isolated SQLite database; they do not establish actual Android AlarmManager delivery or a user swipe in the system notification shade.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-012` | `ReminderCompletionIsolationTest.firedAlarmRecordsExecutionWithoutCompletingTrackedEvent` | PASS: synchronous call to the shared delivery handler with synthetic `ACTION_FIRE` records `FIRED` and clears `intentFor`; completion keys stay empty and revision is unchanged. It does not exercise Android `onReceive`/`goAsync` lifecycle. |
| `REC-P0-013` | `ReminderCompletionIsolationTest.autoCancelledReminderHasNoCompletionDeleteCallback` | PASS: `FLAG_AUTO_CANCEL`, null `deleteIntent`, and unchanged completion state/revision after shadow notification cancellation. |

After the Issue020 stabilization commit, the targeted class result was **2/2 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-020-reminder-dispatch-targeted-final-20261023.log` (SHA-256 `2010C351D82318FC0E3D1154BE91AD64FECD4CC8B1025117870DC8C5D2D2A673`); JUnit XML SHA-256 `0AB606BDA5615C40146FDEBE0B74FCDB3EC2197DC6321CA895400C5972ABD131`. Full regression: **384/384 PASS**, 55 suites, lint 0 errors / 55 warnings; full log SHA-256 `A4A67F15FCBA59BBBBE543943337501DAFD2ECA7A37ECBF3DE3A41664F155783`. The full run used the exact source later committed as `d60809b`; it includes 383 tracked tests plus one protected untracked test. See `docs/issues/PC-ISSUE-015-reminder-completion-isolation.md` and `docs/issues/PC-ISSUE-020-reminder-receiver-test-race.md` for test scope and limitations.

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

## Case-specific instant Deadline point semantics (2026-10-23)

`REC-P0-003` is individually signed on code/test commit `ee2e6707b9dc2e1b134f91109250079dedcd85bc`; Debug APK SHA-256 is `63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`. This is a pure JVM `DraftForm`/`TemporalRecord` model test; no UI or device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-003` | `TemporalProtocolTest.instantDeadlineBuildStoresOnlyItsDeadlinePoint` | PASS: the deadline instant is retained as the sole anchor; `startMillis` and `endMillis` stay null despite residual `startText`. |

Post-commit targeted result: **36/36 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-018-deadline-point-targeted-final-20261023.log` (SHA-256 `752716BCD7F5C081AE20DDAFD703E9873E2B781A0568EFA2B4C7A529DF8BE2EA`); JUnit XML SHA-256 `9C2FB18D1F4FC38EB66282927A10F0249E668B148B8A6D48141325EC2CC86799`. Full regression: **384/384 PASS**, 55 suites, lint 0 errors / 55 warnings; full log SHA-256 `FD45C911E23DDB9CFCAB827D6FE9B49EFEBED2373440521BB6FD803747F3FA5B`. The run included the same test source later committed as `ee2e670`; 383 tracked tests plus one protected untracked test. See `docs/issues/PC-ISSUE-018-instant-deadline-point-semantics.md`.

## Case-specific date-only Deadline precision (2026-10-23)

`REC-P0-004` is signed on the tested code/test source commit `d60809b4133a45c0551019f1edabec1195d34cf3`; the post-commit targeted rerun was performed at docs-only commit `74cc48fb0e05ad08da2aaf457e31d93e390da1cd`. The Debug APK SHA-256 is `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-004` | `PowerStoreTest.dateOnlyDeadlineCommitsWithoutInventingATime` | PASS: commit retains `deadlineDate=2026-10-24`, `DATE` precision, null `deadlineMillis`, and `DEADLINE` kind. |

Post-commit targeted result: **1/1 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-019-date-only-deadline-targeted-postcommit-20261023.log` (SHA-256 `8CF359ED5B02675EF95C78DC74B5A7A12A453F7BB676F4E7BC60AAAF362972E2`); JUnit XML SHA-256 `29C2B5C35ED08F5D72D8889D1BC1D84732DABC03FF3F2CE5B6B1056293973AD1`. The same method passed in the 384/384 full regression, `PowerStoreTest` XML SHA-256 `D3EFE1BDCEA6B0825D49A7ED0B9E4536FE8A3D21BCAEE00B38E133EF9DB58D71`. See `docs/issues/PC-ISSUE-019-date-only-deadline-precision.md`.

## Case-specific reminder receiver test stabilization (2026-10-23)

Issue020 fixed test-fixture concurrency in the `REC-P0-012` regression without changing receiver dispatch behavior. The source commit is `d60809b4133a45c0551019f1edabec1195d34cf3`; this does not add candidate rows or increase the PASS count.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-012` | `ReminderCompletionIsolationTest.firedAlarmRecordsExecutionWithoutCompletingTrackedEvent` | PASS: the handler is invoked synchronously against isolated SQLite, avoiding concurrent polling during its write; Android framework `PendingResult`/`goAsync` behavior remains untested. |

The independent Issue020 report preserves the original failing full-run log and JUnit XML, their SHA-256 hashes, the passing post-fix targeted run, and the passing full regression. A temporary synchronous `onReceive` experiment exposed a missing framework `PendingResult` in the old fixture; its console output was not preserved as a separately hashable artifact and is explicitly disclosed in that report. No test-fixture output is represented as Android system-delivery evidence.

## Case-specific incomplete Activity draft retention and repair (2026-10-23)

`REC-P0-005` is signed on test commit `02e32d21c74babb284fc41f29f2ec02fca08037e`; APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. The Robolectric API 34 test uses an isolated SQLite database.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-005` | `PowerStoreTest.incompleteActivityCommitStaysUnpublishedAndCanBeRepairedAfterRestart` | PASS: incomplete Activity commit is rejected with no formal event; the INVALID draft survives reopening the database, then can be completed and committed as ACTIVITY. |

Post-commit targeted result: **1/1 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-021-incomplete-activity-draft-targeted-postcommit-20261023.log` (SHA-256 `2010C351D82318FC0E3D1154BE91AD64FECD4CC8B1025117870DC8C5D2D2A673`); JUnit XML SHA-256 `56D2E5EA5DEE655C4F23A3054AD3FA38AFD48CABF8674CFCDE61F3A3351459DA`. Full regression: **385/385 PASS**, 55 suites, lint 0 errors / 55 warnings; full log SHA-256 `830C3E74943993C467B5133208382E4FCED0A90FF9D699FF6D6D682BE550B5E3`. The full `PowerStoreTest` XML contains 38 passing tests including this method; SHA-256 `5046E297A5B28A13C59507A30BC012799F5AA1062A177A3C54791FE35F3629B0`. See `docs/issues/PC-ISSUE-021-incomplete-activity-draft-recovery.md` for the exact assertions and boundaries.

## Case-specific one-date Memo local-date semantics (2026-10-23)

`REC-P0-007` is individually signed on code/test commit `bc8c1fb8d170abbce2df029133e3c1d44e658317`; Debug APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. The regression uses Robolectric API 34 and an isolated PowerStore SQLite database; no emulator or physical device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-007` | `PowerStoreTest.oneDateMemoRemainsOneLocalDateAcrossRestart` | PASS: kind remains MEMO and `memoStartDate=2026-10-24`; `memoEndDate`, `startMillis`, `endMillis`, `deadlineDate`, and `deadlineMillis` remain null. The date-only task summary persists after closing and reopening SQLite. |

Post-commit targeted result: **1/1 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-022-one-date-memo-targeted-postcommit-20261023.log` (SHA-256 `752716BCD7F5C081AE20DDAFD703E9873E2B781A0568EFA2B4C7A529DF8BE2EA`); JUnit XML SHA-256 `F80D899F93000F712986536BBB9713D70542113449BDEBB7A087B6D820D18D13`. Full Gradle regression: **386/386 PASS**, 55 suites, lint 0 errors / 55 warnings; log SHA-256 `B7AFD09CEB3BEDB0A94B3AF0E11DF2ED7076854B06A8D5AE8AAC3EED4BF1BDB7`. Full `PowerStoreTest` XML has 39 passing tests; SHA-256 `B3E50D8B5F08C50EB2AF6CBB3E4F90166B34546B33C9D25C918501803BCAABFF`. See `docs/issues/PC-ISSUE-022-one-date-memo-local-date.md` for precise assertions and limitations.

## Case-specific date-span Memo boundaries (2026-10-23)

`REC-P0-008` is individually signed on code/test commit `65f68d29c9afb7f7866ac9c13891f2d696ef8395`; Debug APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. The regression uses Robolectric API 34 and an isolated PowerStore SQLite database; no emulator or physical device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-008` | `PowerStoreTest.spanningMemoHasOneCompletionInstanceAndKeepsItAcrossRestart` | PASS: kind remains MEMO; both local-date boundaries `2026-10-21` and `2026-10-23` persist; start/end instant and Deadline fields remain null before and after reopening SQLite. |

Post-commit targeted result: **1/1 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-023-date-span-memo-targeted-postcommit-20261023.log` (SHA-256 `BB7C134C692FD704A6D7A253FB10CE5DF189779200A40655BFF5DCCDA1DFF003`); JUnit XML SHA-256 `7BF831321D46A40E4841622C9D7416CE18A16D2415979D84FFA0290C5C7DD773`. Full Gradle regression: **386/386 PASS**, 55 suites, lint 0 errors / 55 warnings; log SHA-256 `A252A36DA48CADF048F9CF615EB1FFFAC80DE8134D2E4DEEAD846C8C1C791F3B`. Full `PowerStoreTest` XML has 39 passing tests and SHA-256 `7BF831321D46A40E4841622C9D7416CE18A16D2415979D84FFA0290C5C7DD773`. See `docs/issues/PC-ISSUE-023-date-span-memo-boundaries.md` for the exact checks and boundaries.

## Case-specific Activity start/end interval persistence (2026-10-23)

`REC-P0-002` is individually signed on code/test commit `83c6a3191cd65188498602700115ca3635a324c9`; Debug APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. The regression uses Robolectric API 34 and an isolated PowerStore SQLite database; no emulator or physical device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-002` | `PowerStoreTest.activityCommitPreservesItsStartAndEndIntervalAcrossRestart` | PASS: a committed Activity retains the exact start/end instants, uses the true end as its span end, and has no Deadline fields; kind and values survive reopening SQLite. |

Post-commit targeted result: **1/1 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-024-activity-interval-targeted-postcommit-20261023.log` (SHA-256 `E78E9DA3B7485D19D0092C527FE5B292FA1A4EDD0A3B5EF24A8EB98E1BB48660`); JUnit XML SHA-256 `14567532FF73FF7DB503A3660DA5E3029BDE81D7FA3119DDB3F95412E919A53A`. Full Gradle regression: **387/387 PASS**, 55 suites, lint 0 errors / 55 warnings; log SHA-256 `E75FE87E1E34FAAF4B55211E917EE02172CF7D96750A74430BF8219884A42848`. Full `PowerStoreTest` XML has 40 passing tests and SHA-256 `9FEBE905C060F5975A345A4D2498FD9BA419996E1C02F17C3CAE2D1F300CF4DC`. See `docs/issues/PC-ISSUE-024-activity-interval-persistence.md` for exact assertions and boundaries.

## Case-specific type conversion and identity preservation (2026-10-23)

`REC-P0-020` is individually signed on code/test commit `1317340c8b503e44374378d00365e4d1326a3457`; Debug APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. The regression uses Robolectric API 34 and an isolated PowerStore SQLite database; no emulator or physical device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-020` | `PowerStoreTest.convertingAlarmToActivityKeepsEventIdentityAcrossRestart` | PASS: ALARM-to-ACTIVITY conversion requires an explicit end time, keeps the same eventSeq, increments revision once, and leaves identity count and allocator unchanged across database reopen. |

Post-commit targeted result: **1/1 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-025-kind-conversion-targeted-postcommit-20261023.log` (SHA-256 `8C96D3E0F8DAECD520868080DCEBE28E0B188FE60A2CE0C560B0E399E36901A3`); JUnit XML SHA-256 `53F9DD28E683C07709881480F7BEEEBCE922D0E2AAFE900ABCD9ACDAC6AF8CA7`. Full Gradle regression: **388/388 PASS**, 55 suites, lint 0 errors / 55 warnings; log SHA-256 `DAEDC1207E6F7660AA482FFD2FC4F40DEF3A72DBD7820A02F6E29ACDE02FE6B7`. Full `PowerStoreTest` XML has 41 passing tests and SHA-256 `1CDFB12601E8250548ECEC12ABEC3A7F4A6B62E8D3A0ECAEAA7BB3BE854F8783`. See `docs/issues/PC-ISSUE-025-kind-conversion-keeps-event-identity.md` for exact assertions and boundaries.

## Case-specific import source metadata and internal identity (2026-10-23)

`REC-P0-016` is individually signed on code/test commit `da1fdaa4e2a2d102cf440411b6c8dada5dafb3da`; Debug APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. The regression uses Robolectric API 34 and an isolated PowerStore SQLite database; no emulator or physical device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-016` | `PowerStoreTest.structuredImportSourceMetadataDoesNotBecomeEventIdentity` | PASS: distinct `(source, externalId)` pairs receive distinct eventSeq identities, while repeating the same pair is idempotent; provenance and identities persist after reopening SQLite. |

Post-commit targeted result: **1/1 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-026-import-source-identity-targeted-postcommit-20261023.log` (SHA-256 `72F52C3B3F0127E7811342BD3857E57B342423ABA4301890162F1224A54D1485`); JUnit XML SHA-256 `4367C84BDF5887392D12DE6A51491AA312AE70EDD0C53DB20CA948E00AB517C2`. Full Gradle regression: **389/389 PASS**, 55 suites, lint 0 errors / 55 warnings; log SHA-256 `43A92556140A6DE74419A8C04DD91ECEDCE1DF534B6BD0DD79C83E31FB9AFE1E`. Full `PowerStoreTest` XML has 42 passing tests and SHA-256 `CDAC5F97384C2EA8B80B3394472F3805FB14F85DAFFBD7C2E70140F1B06C2FB1`. See `docs/issues/PC-ISSUE-026-import-source-metadata-identity.md` for exact assertions and boundaries.

## Case-specific rejected import sequence allocation (2026-10-23)

`REC-P0-028` is individually signed on code/test commit `5c8d6c50f8d4c710b65c4a6c26da6b6be4b22221`; Debug APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. The regression uses Robolectric API 34 and an isolated PowerStore SQLite database; no emulator or physical device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-028` | `PowerStoreTest.rejectedStructuredImportDoesNotConsumeSequenceBeforeValidRetry` | PASS: an incomplete Activity is rejected without allocating sequence 1; after adding its end time, retrying the same source/external ID imports as sequence 1, creates one history entry, and preserves the record, identity count, watermark, and history after SQLite reopen. |

Post-commit targeted result: **1/1 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-027-rejected-import-sequence-targeted-postcommit-20261023.log` (SHA-256 `E78E9DA3B7485D19D0092C527FE5B292FA1A4EDD0A3B5EF24A8EB98E1BB48660`); JUnit XML SHA-256 `95058B1CFD08B90F1DCE6EE1D12ED403DA179907F26D9E09E162BED807079227`. Full Gradle regression: **390/390 PASS**, 55 suites, lint 0 errors / 55 warnings; log SHA-256 `EA57B13C40C36C8749168E400C6D92FFEDADEEA211D3DA798DA87A144ACE8570`. The full `PowerStoreTest` XML contains 43 passing tests and SHA-256 `F585260CC73AC81659227AEFF5DA805EE8E28ADDB9BDC1446920F97EA57DB3EB`. See `docs/issues/PC-ISSUE-027-rejected-import-does-not-consume-sequence.md` for exact assertions and limits.

## Case-specific undated Memo semantics (2026-10-23)

`REC-P0-006` is individually signed on code/test commit `3f9426606dfa26b40f63ca11aceecc1c1e93bd34`; Debug APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. The regression uses the pure temporal model and Robolectric API 34 with an isolated PowerStore SQLite database; no UI, emulator, or physical device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-006` | `PowerStoreTest.undatedMemoKeepsDateAndTimeFieldsAbsentAcrossRestart` and `PowerCalMemoContractTest.undatedMemoNeedsOnlyTitleAndHasNoFakeTime` | PASS: all Memo date/time/deadline fields, `anchorMillis()`, and `spanEndMillis()` remain null across SQLite reopen; summary remains `无日期 · 待办`. The pure model test also verifies the undated form creates no fake time. |

Post-commit targeted result: **2/2 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-028-undated-memo-targeted-postcommit-20261023.log` (SHA-256 `DF28959E2EF61401DEC8B987114261D23711F088394B186CFAA64BA84F3C406A`); the two JUnit XML hashes are recorded in the independent Issue 028 report. Full Gradle regression: **391/391 PASS**, 55 suites, lint 0 errors / 55 warnings; log SHA-256 `754C84377941BA778AD5AF2728C0E4C7CCD10B4275586409744803EB1A22DD45`. The full `PowerStoreTest` XML contains 44 passing tests and SHA-256 `3D7E713E9D754F012F4B4F1BB540714F117AC0390E3B342675322F408B32F390`. See `docs/issues/PC-ISSUE-028-undated-memo-no-synthetic-time.md` for exact assertions and limits.

## Case-specific deletion tombstone and identity retirement (2026-10-23)

`REC-P0-029/030` are individually signed on code/test commit `8270a8a47d55ad3e91cbdadd4b2caf1d7daefb20`; Debug APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. The regression uses Robolectric API 34 and an isolated PowerStore SQLite database; no UI, emulator, or physical device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-029` | `PowerStoreTest.deletedIdentityRemainsRetiredAndIsNotReusedAfterRestart` | PASS: DELETE writes a tombstone; after database reopen, normal lookup still hides the retired event while tombstone lookup and `isRetired` confirm it remains retired. Identity count and sequence watermark persist. |
| `REC-P0-030` | `PowerStoreTest.deletedIdentityRemainsRetiredAndIsNotReusedAfterRestart` | PASS: a new draft created after reopening receives `retiredSeq + 1`; the old sequence remains retired and unavailable through normal lookup. |

Post-commit targeted result: **2/2 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-029-retired-identity-targeted-postcommit-20261023.log` (SHA-256 `A6D078A431A9870A8E89C49529A5E1F692355BB15E73A9E305C913A6325643B6`); JUnit XML SHA-256 `84C6F0785079DA67C5B5A8A13BBEF90EFFE8D30A8AFCC44FA14F991E22240E5F`. Full Gradle regression: **392/392 PASS**, 55 suites, lint 0 errors / 55 warnings; log SHA-256 `2FB780ECF6F7F4D2D81D9004FAE8437856514D1BCA31F7447948BADC3EB60A52`. The full `PowerStoreTest` XML contains 45 passing tests and SHA-256 `0731E44979C82E4C68C9DF89B03DD69EEEDD59B98CCDE77A25D4A415EC860B13`. See `docs/issues/PC-ISSUE-029-retired-identity-not-reused-after-restart.md` for exact assertions and limits.

## Case-specific independent draft identities (2026-10-23)

`REC-P0-031` is individually signed on code/test commit `9cfd0ee4ff71c13103dcbb7feab9d8bc6b11e6a3`; Debug APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. The regression uses Robolectric API 34 and an isolated PowerStore SQLite database; no UI, emulator, or physical device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-031` | `PowerStoreTest.independentCreateDraftsKeepUniqueDraftIdsAcrossRestart` | PASS: two independent create drafts have unique draft IDs and event sequences, retain the correct draft-to-event mappings, and remain in the pending queue after SQLite reopen. |

Post-commit targeted result: **2/2 PASS**, including the existing same-event one-actionable-draft test; log `.local/android-test/reports/freeze-20261023/pc-issue-030-independent-draft-targeted-postcommit-20261023.log` (SHA-256 `8CF359ED5B02675EF95C78DC74B5A7A12A453F7BB676F4E7BC60AAAF362972E2`); JUnit XML SHA-256 `34B222F57CC7F2693E20472ED3BA934209DF5C83A3788B417B59DE15A63D17D2`. Full Gradle regression: **393/393 PASS**, 55 suites, lint 0 errors / 55 warnings; log SHA-256 `54E0983E3F4020CBB5453265B10815C728960E146EB60637B9DA13F57E1418CF`. The full `PowerStoreTest` XML contains 46 passing tests and SHA-256 `922B3FA40F5B3DF2FB8AA8BB6178FBC8EF8AD1072BEA38BA63A279C6D1233B19`. See `docs/issues/PC-ISSUE-030-independent-draft-identities.md` for exact assertions and limits.

## Staged edit retains draft and event identities (2026-10-23)

`REC-P0-032` is individually signed on code/test commit `1d373f55876336a2153064d3013d09cbcfdd7f84`; Debug APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. The reconstructed row's action/expected wording was normalized to the documented V4 edit contract; it remains `UNMAPPED` to the unavailable historical case IDs. The test uses Robolectric API 34 and an isolated PowerStore SQLite database; no UI, emulator, or physical device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-032` | `PowerStoreTest.stagingDraftEditKeepsDraftAndEventIdentitiesAcrossRestart` | PASS: staging revised content retains the original `draftId` and target `eventSeq`, increments `draftVersion`, does not allocate another identity, and persists the edited draft and pending queue after SQLite reopen. |

Pre-commit targeted result: **2/2 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-031-staged-draft-identity-targeted-precommit-20261023.log` (SHA-256 `D28C3D464FFF7D5ECDD5E03FB0D2E9E05A84EABD6F2A3A8FEDEE9CD072C51224`); JUnit XML SHA-256 `2F4A4B68E6469A5E893A4E66C318696D455C9BB4C1CBA6B02742F74BF09DB08D`. Post-commit targeted result: **2/2 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-031-staged-draft-identity-targeted-postcommit-20261023.log` (SHA-256 `A6D078A431A9870A8E89C49529A5E1F692355BB15E73A9E305C913A6325643B6`); JUnit XML SHA-256 `FD7B64CD57D79E4E8E70A80E3D6BB355F6B5F3C62E5C419C60906BC5ED96A927`. Full Gradle regression: **394/394 PASS**, 55 suites, lint 0 errors / 55 warnings; full log SHA-256 `0837BE595F54D803DE16963BD3F4F32354586E8D1027AF5333E1DE97FC2D775B`. The full `PowerStoreTest` XML contains 47 passing tests and SHA-256 `9BA04D5BB571D74C44A1683FBB4A1059A2185C339CE1796B5D94085640E773F7`. See `docs/issues/PC-ISSUE-031-staged-draft-keeps-event-identity.md` for the detailed assertions and limitations.

## One actionable draft per event survives SQLite reopen (2026-10-23)

`REC-P0-033` is individually signed on code/test commit `c455351dc7268147fbc00238111315a4a43a55c1`; Debug APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. The reconstructed row was corrected to the SQLite v4 contract: one active draft per event, updated in place. It remains `UNMAPPED` to the unavailable historical case IDs. The test uses Robolectric API 34 and an isolated PowerStore SQLite database; no UI, emulator, or physical device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-033` | `PowerStoreTest.repeatedSameEventProposalReusesOneActionableDraftAcrossRestart` | PASS: successive proposals reuse one draft ID and queue order; latest content and sole actionable status persist after reopen; a discarded prior draft cannot be restored beside a new active draft. |

Pre-commit targeted result: **2/2 PASS**, including the existing same-event reuse test; log `.local/android-test/reports/freeze-20261023/pc-issue-032-one-actionable-draft-targeted-precommit-20261023.log` (SHA-256 `FF1AA6BE1243F698E52F85B624C32932D38252BE2ACE4196CEC5851EE7149591`); JUnit XML SHA-256 `CD8281705D88C92E7AE5953595FDC899347499586E7D3DD646BC3E4171AA8D33`. Post-commit targeted result: **2/2 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-032-one-actionable-draft-targeted-postcommit-20261023.log` (SHA-256 `E78E9DA3B7485D19D0092C527FE5B292FA1A4EDD0A3B5EF24A8EB98E1BB48660`); JUnit XML SHA-256 `2DFAB6A91651C8099C320CFF3239B64FA7B511488CA9ECF83C37F3FD715CA06D`. Full Gradle regression: **395/395 PASS**, 55 suites, lint 0 errors / 55 warnings; full log SHA-256 `50E2F0FD9BE0D495AE3DD6339AAE2F7983B899C9A75DFF6E7E138DF5644685F6`. The full `PowerStoreTest` XML contains 48 passing tests and SHA-256 `53A61631F305C17B6F581A9BFF37225F0A1584714675D438A7347560E675C327`. See `docs/issues/PC-ISSUE-032-one-actionable-draft-per-event.md` for the corrected requirement, assertions and limitations.

## Exactly one current formal snapshot per event (2026-10-23)

`REC-P0-034` is individually signed on code/test commit `c0c0a2912fa849013a20ba105036299b4e66d741`; Debug APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. This isolated PowerStore/Robolectric regression verifies current snapshots and history across SQLite reopen; no UI or device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-034` | `PowerStoreTest.multipleCommitsKeepOneCurrentFormalSnapshotAcrossRestart` | PASS: after three whole-snapshot commits, `all()` returns exactly one current row at revision 3; after reopen it still returns only the latest snapshot, while all three commit-history records remain available. |

Pre-commit targeted result: **2/2 PASS**, with existing whole-snapshot commit coverage; log `.local/android-test/reports/freeze-20261023/pc-issue-033-formal-snapshot-targeted-precommit-20261023.log` (SHA-256 `2010C351D82318FC0E3D1154BE91AD64FECD4CC8B1025117870DC8C5D2D2A673`); JUnit XML SHA-256 `74FC59E21FE2C4874DA6A8B8B5E0E03578B88462ED83ED8AB2A97368D1CDCADC`. Post-commit targeted result: **2/2 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-033-formal-snapshot-targeted-postcommit-20261023.log` (SHA-256 `E78E9DA3B7485D19D0092C527FE5B292FA1A4EDD0A3B5EF24A8EB98E1BB48660`); JUnit XML SHA-256 `F3BA930D37E566E770D6D1C16B199C65858FC1DAE7DAE0C21BF423627D63D246`. Full Gradle regression: **396/396 PASS**, 55 suites, lint 0 errors / 55 warnings; full log SHA-256 `C309FEF4E25520E13AB07864D1ACFA32EEFCD647D7AC62081633C89FB779ADF4`. The full `PowerStoreTest` XML contains 49 passing tests and SHA-256 `A09075019FDFA8FFA46D3663DAA24307BCCA2AD23EC40E9C86DA53BA9B5F942A`. See `docs/issues/PC-ISSUE-033-single-current-formal-snapshot.md` for detailed assertions and limits.

## Independent draft queue order persists across restart (2026-10-23)

`REC-P0-035` is individually signed on code/test commit `500889acc6a5968f984caa7e8b063b9823255983`; Debug APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. This PowerStore/Robolectric regression reverses enqueue order and event sequence order, then checks distinct positive queue positions and persisted mappings after database reopen; no UI or device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-035` | `PowerStoreTest.independentQueueOrdersPersistAcrossRestartAndDoNotFollowEventNumbers` | PASS: two active drafts retain distinct queue positions and correct event mappings after reopen, with queue order opposite to event sequence order. |

Pre-commit targeted result: **2/2 PASS**, including existing queue/event independence coverage; log `.local/android-test/reports/freeze-20261023/pc-issue-034-independent-queue-order-targeted-precommit-20261023.log` (SHA-256 `2010C351D82318FC0E3D1154BE91AD64FECD4CC8B1025117870DC8C5D2D2A673`); JUnit XML SHA-256 `95474E3662FA7550A833049B9998F62565266D61CF242CF13EB7C6CB49388AFA`. Post-commit targeted result: **2/2 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-034-independent-queue-order-targeted-postcommit-20261023.log` (SHA-256 `DF28959E2EF61401DEC8B987114261D23711F088394B186CFAA64BA84F3C406A`); JUnit XML SHA-256 `D237A71F8EB75BC26101D5C07D33A7ED4CB7E29FEB742426CB2CEAE64350CD79`. Full Gradle regression: **397/397 PASS**, 55 suites, lint 0 errors / 55 warnings; full log SHA-256 `4FFF6FDA19716F8F9CD2576C20D068190CA6D2D65356961AEAAD2C63AF0DB665`. The full `PowerStoreTest` XML contains 50 passing tests and SHA-256 `C9FCA167A5600024DF55C819C01E6F1563B604C8CD4C9E0DE4AD08D6C4278CDE`. See `docs/issues/PC-ISSUE-034-independent-queue-order.md` for details and limits.

## Monotonic queue-order high-water mark across restart (2026-10-23)

`REC-P0-036` is individually signed on code/test commit `1a74b8b336d0351e1ceeed0b1742943c1bc6f697`; Debug APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. This isolated PowerStore/Robolectric regression allocates positions to committed and discarded drafts, reopens SQLite, and verifies the next allocation exceeds both; no UI or device was used.

| Candidate row | Test | Result |
| --- | --- | --- |
| `REC-P0-036` | `PowerStoreTest.nextDraftQueueOrderAdvancesPastCommittedAndDiscardedDraftsAfterRestart` | PASS: committed and discarded queue positions remain retired; after reopen, the next draft receives a greater `queueOrder`. |

Pre-commit targeted result: **2/2 PASS**, with existing queue/event independence coverage; log `.local/android-test/reports/freeze-20261023/pc-issue-035-monotonic-queue-watermark-targeted-precommit-20261023.log` (SHA-256 `2B2F1657DB15F9874550E3FCA0B879FDFED1B29E4CD9F65A68E708751A79B23F`); JUnit XML SHA-256 `203CD45301DB58DA708501A582A2E936F84A64D1D0753A6406E1060508FF3784`. Post-commit targeted result: **2/2 PASS**, log `.local/android-test/reports/freeze-20261023/pc-issue-035-monotonic-queue-watermark-targeted-postcommit-20261023.log` (SHA-256 `DF28959E2EF61401DEC8B987114261D23711F088394B186CFAA64BA84F3C406A`); JUnit XML SHA-256 `2B1B1FA9741A9A16C6F7CEFC1CF1636C280BF9FE01FAF4980A198FEA35F9AA5E`. Full Gradle regression: **398/398 PASS**, 55 suites, lint 0 errors / 55 warnings; full log SHA-256 `CF006C08916F2BFA332F40E00D52D1B6A2742C2DAD3B872AEE2A4E7BBBBCF3B6`. The full `PowerStoreTest` XML contains 51 passing tests and SHA-256 `F691A0E980CDA4C129D6A4C80020E1FA757B11E4ABA71A5545509F703EC3DAD6`. See `docs/issues/PC-ISSUE-035-monotonic-queue-watermark.md` for details and limits.

### Case-specific human proposal queue-tail verification (2026-10-23)

`REC-P0-038` was exercised in Robolectric API 34 using an isolated PowerStore SQLite database. Test commit `f930b8714c9b94c62d8a4d294940b36a5322ea35` added `PowerStoreTest.humanProposalAppendsAfterExistingQueueAcrossRestart`: two prior user drafts are followed by a human proposal with a strictly later `queueOrder`; queue order, IDs, and titles persist after closing and reopening the database. The candidate remains `UNMAPPED` to the missing historical checklist. This evidence covers core storage only, not editor UI or device behavior. See `docs/issues/PC-ISSUE-036-human-proposal-appends-queue.md`.

Targeted pre-commit and post-commit runs each passed 2/2; full Gradle passed 399/399 tests across 55 suites, with lint 0 errors / 55 warnings. Full log SHA-256 `B7CBB97050DEC49C3AE880A5C9C080B7CF47676A108D343211A243634FEAECD9`; full `PowerStoreTest` XML SHA-256 `6D6E249FF2C27651FA8EC53316B257E61EBE972C35A30C01A8384889212978ED`; APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. Structural-only validation JSON SHA-256 `1C1FA7EF6FD9C8BBE2E71B1F7E37FCDD0EF975EF3E3B11F01F5F962D137E7439`; updated CSV SHA-256 `83EF41EAC83DEFBE93496B2F5CC9096A3E6B0AC6C7191046314B74F0DA35C8C0`.

### Case-specific timestamp versus queue-order verification (2026-10-23)

`REC-P0-042` was exercised in Robolectric API 34 using an isolated PowerStore SQLite database. Test commit `0b19f017cae097232398166fb736b0b5cf0fe5a7` added `PowerStoreTest.updatingOlderDraftTimestampDoesNotChangeQueueOrderAcrossRestart`: editing the first of two queued drafts gives it a later `updatedAtMillis` than the second, while `pendingDrafts()` still returns the first draft first. The queue order and both timestamps persist after SQLite reopen. The candidate remains `UNMAPPED` to the missing historical checklist. This evidence covers core storage only, not UI or device behavior. See `docs/issues/PC-ISSUE-037-timestamp-does-not-reorder-queue.md`.

Targeted pre-commit and post-commit runs each passed 2/2; full Gradle passed 400/400 tests across 55 suites, with lint 0 errors / 55 warnings. Full log SHA-256 `7FA263167BB49A5740AF1ABE46634C9E0EF4DC4AF8E6431B3A6AC9397BB931E8`; full `PowerStoreTest` XML SHA-256 `872BE34030F89B7A5471EF19081D946E883A21CF6CBA25AC89509A2B3ACFB613`; APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. Structural-only validation JSON SHA-256 `D49E404E2108E5A083F7F8D1D9768CB7246F3DD95011B73A8A925EDE01473F09`; updated CSV SHA-256 `7A863CB189C0C685CABE37422FBC63758FC6DE54DE7F8125FB030AEFA8D29211`.

### Case-specific revision advancement verification (2026-10-23)

`REC-P0-044` was exercised in Robolectric API 34 using an isolated PowerStore SQLite database. Test commit `a515c11478af21412fbd79ea955e1976a2fd3dba` added `PowerStoreTest.stagedMutationAdvancesFormalRevisionOnlyWhenCommittedAcrossRestart`: creating and editing a valid replacement proposal leaves the formal event at revision 1; the same revision and pending proposal survive SQLite reopen; committing then advances the formal snapshot to revision 2. The candidate remains `UNMAPPED` to the missing historical checklist. This evidence covers core storage only, not UI or device behavior. See `docs/issues/PC-ISSUE-038-revision-advances-only-on-commit.md`.

Targeted pre-commit and post-commit runs each passed 2/2; full Gradle passed 401/401 tests across 55 suites, with lint 0 errors / 55 warnings. Full log SHA-256 `09984A1F258A9E7E9E1D20C1912957A6554A8F874AEEA707DFA1BB9487AB5782`; full `PowerStoreTest` XML SHA-256 `6A84DCF7F987F89DDF8DE923410341D18A46A906CF6A05418E49F593C894E880`; APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. Structural-only validation JSON SHA-256 `0EDC3988245FE072F1DB890D0DBD0788FCD9898E4DB0F62EEEABD65DB9E611A6`; updated CSV SHA-256 `6A75660B76D6F33A2F8C8859F9D6AA7D723065AF1F70B8B1EAF2A5E5827B8EC8`.

### Case-specific unique commit-ID verification (2026-10-23)

`REC-P0-045` was exercised in Robolectric API 34 using an isolated PowerStore SQLite database. Test commit `fb0d03bd74f3d3a0de81e1e99b1f4d9652af6d79` added `PowerStoreTest.separateCommitOperationsReceiveUniqueCommitIdsAcrossRestart`: two distinct commits receive different non-empty `commitId` values, both occur once in history, and both IDs remain after SQLite reopen. The adjacent retry test confirms the same operation returns its original ID. The candidate remains `UNMAPPED` to the missing historical checklist. This evidence covers core storage only, not UI or device behavior. See `docs/issues/PC-ISSUE-039-unique-commit-id.md`.

Targeted pre-commit and post-commit runs each passed 2/2; full Gradle passed 402/402 tests across 55 suites, with lint 0 errors / 55 warnings. Full log SHA-256 `1E82BC51B97AB7FE9EC9F296C349A41E4312B25BAF2F5C872CD31BC2E1D3D769`; full `PowerStoreTest` XML SHA-256 `5C8E233AA749FAEDB902579D484B958BB6847F1B867E77FFBC9EA3F00AC1256B`; APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. Structural-only validation JSON SHA-256 `8E0AF32DB5C48F5B8F81A9382BD781286EEB54BDA6A29E08B9F68BA49470ACF8`; updated CSV SHA-256 `6D4B739D53AF417FDD188DD5D2AEC40243CFF6408368517E0ED2C2CFE4A3C6D4`.

## Historical sign-off accounting

| Bucket from historical aggregate | Total | Matched to original ID | Result |
| --- | ---: | ---: | --- |
| P0 | 126 | 0 | Unassessed by original ID |
| P1 | 82 | 0 | Unassessed by original ID |
| Priority unresolved | 1 | 0 | Remains unclassified and unassessed |

The latest full working-tree Gradle run reports 402 tests PASS, 0 failures/errors/skips across 55 JUnit suites, successful skin-boundary verification and Debug assembly, and lint 0 errors / 55 warnings. It includes one test from protected, untracked `app/src/test/java/dev/powerfulcalendar/ui/v2/DraftDeckFeedbackTest.kt` (401 tracked tests + 1 untracked); the file was not modified or staged. The tested code/test changes are committed as `fb0d03bd74f3d3a0de81e1e99b1f4d9652af6d79`. Log: `.local/android-test/reports/freeze-20261023/pc-issue-039-unique-commit-id-full-gradle-20261023.log` (SHA-256 `1E82BC51B97AB7FE9EC9F296C349A41E4312B25BAF2F5C872CD31BC2E1D3D769`). This is not a 402-to-209 mapping.

## Required next work

1. Run the 113 `NOT TESTED` rows under the environment each row requires and update only with case-specific evidence.
2. Resolve the one blocked P0, eight blocked P1, and five reconstructed spec-unresolved rows where feasible; retain external/physical/device dependencies as HOLD. The separate historical unclassified slot remains unresolved pending its source.
3. Review the source-based proposed priority allocation. New IDs must never masquerade as historical IDs.
4. Keep the one historical unclassified item visibly unresolved until its source or an explicit review decision is available.
5. Preserve the physical-device, permission, OEM, and specification gaps as HOLD.

## Scope and outcome

Core code and tests now have evidence for 56 reconstructed rows across backup, tombstone, migration, recurrence, reminder, temporal model, draft, and core data-semantics contracts; 113 remain NOT TESTED, proposed priorities still need review, and historical mapping is still absent. PC-QA-GATE-001 remains **PARTIAL / HOLD**, and overall core verdict remains **HOLD**.
