# PC-QA-GATE-001 — 209-case acceptance traceability

Date: 2026-10-24 | Severity: P0

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

Structural validation: 209 unique IDs; all reconstructed rows contain source, environment, action, expected and actual fields; PASS/BLOCKED rows include evidence references. The latest structural-only validation is `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-082.json` (SHA-256 `B2E1C203B87445323BCC886CCB819E97BC45862C092485234DD0090D494CC205`); ledger SHA-256 `3D0D9E502FFA54CBC7BE52FD2BD55BC266989A611369FCFF0E507D034F21711F`. It confirms 129/0/9/64/7 status counts, P0 counts 110/0/1/10/5, and `REC-P0-037`, `REC-P0-041`, `REC-P0-024`, `REC-P0-039`, `REC-P0-057`, `REC-P0-076`, `REC-P0-077`, `REC-P0-113`, `REC-P0-114`, `REC-P0-115`, `REC-P0-116`, `REC-P0-119`, `REC-P0-120`, `REC-P0-121`, `REC-P0-122`, `REC-P0-123`, `REC-P0-124`, `REC-P0-125`, and `REC-P0-126` PASS. `REC-P0-111` remains NOT TESTED: the direct scheduler regression does not verify asynchronous Android receiver completion. Structural validation does not test product behavior or prove the historical mapping; `product_behavior_tested` is false.

| Candidate bucket | PASS | FAIL | BLOCKED | NOT TESTED | SPEC UNRESOLVED |
| --- | ---: | ---: | ---: | ---: | ---: |
| P0 (proposed) | 110 | 0 | 1 | 10 | 5 |
| P1 (proposed) | 19 | 0 | 8 | 54 | 1 |
| Historical unclassified slot | 0 | 0 | 0 | 0 | 1 |
| **Total candidate rows** | **129** | **0** | **9** | **64** | **7** |

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

### Case-specific explicit commit retry verification (2026-10-23)

`REC-P0-046` was exercised in Robolectric API 34 using an isolated PowerStore SQLite database. Test commit `2f7b96bcf621e606c2f6470f7d81ee839b3a8909` added `PowerStoreTest.explicitCommitIdRetryAfterRestartDoesNotRepeatSideEffects`: after the first explicit-key commit, retrying the same draft and key after SQLite reopen returns the original event/revision/commit ID with `duplicate=true`. The history row, formal revision, published count, single pending schedule intent, and zero pending drafts remain unchanged. The candidate remains `UNMAPPED` to the missing historical checklist. This proves durable store behavior only; it does not test Android AlarmManager delivery. See `docs/issues/PC-ISSUE-040-idempotent-commit-retry.md`.

Targeted pre-commit and post-commit runs each passed 2/2; full Gradle passed 403/403 tests across 55 suites, with lint 0 errors / 55 warnings. Full log SHA-256 `51D83DBE2B6E9129931CFD91ACDCB3FB9DB84D87F6E6237F29597987DC7CC36B`; full `PowerStoreTest` XML SHA-256 `4B419D62DFC8267929986986710907EF00A717CBECFA5FE3A5E8A8C3E09D1B7C`; APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. Structural-only validation JSON SHA-256 `7BA00CF2BA185B6F2555B1391895D8AF89207A973399D3B907B20D7A12E65279`; updated CSV SHA-256 `AD2FE80ED1B3E987C299014B4925AAE13CB414B5365BF6E6A3BF0DE80EA47EAE`.

### Case-specific stale base-revision conflict verification (2026-10-23)

`REC-P0-047` was exercised in Robolectric API 34 using an isolated PowerStore SQLite database. Test commit `e8ee3e5aad3c8a8ef353d84781d3cff40f2d4127` added `PowerStoreTest.staleBaseRevisionReturnsConflictWithoutOverwritingAcrossRestart`: a revision 1 replacement proposal becomes stale after the formal record advances to revision 2; after SQLite reopen its commit returns `CommitOutcome.Conflict`, the formal title/completion state/revision remain unchanged, and history does not grow. The reconstructed expected result is scoped to the core store contract; editor conflict presentation is not included. The candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-041-stale-revision-conflict.md`.

Targeted pre-commit and post-commit runs each passed 2/2; full Gradle passed 404/404 tests across 55 suites, with lint 0 errors / 55 warnings. Full log SHA-256 `02E42FD3984B649B0AC2DC6DEF8B12855A7632DA075B9E56DEEE2CF80B713AB8`; full `PowerStoreTest` XML SHA-256 `14273D8082573684B443FCA1D0F0A741CA0BF4EE5B3869774ED9FAF73ECD7CD3`; APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. Structural-only validation JSON SHA-256 `2D90BA0C14F35EB9BE2E126D1C611437EF616721B44907EFDA5B2B87BD6C6E9D`; updated CSV SHA-256 `1ABF49D525C23F4818CBE2EB0B8B16D1997B8B6CBB27E07CDD4B6A34239E5235`.

### Case-specific AI conflict-draft retention verification (2026-10-23)

`REC-P0-048` was exercised in Robolectric API 34 using an isolated PowerStore SQLite database. Test commit `06c0534a9fc43c7cda243dbe431384c72aa3b161` added `PowerStoreTest.aiConflictDraftRemainsAvailableForHumanResolutionAcrossRestart`: an AI replacement becomes stale after the formal record advances to revision 2; the batch keeps the card with zero published, then after another SQLite reopen the conflict query returns the same draft ID, complete proposal, base revision and queue order. It remains in the actionable draft collection; the formal title and completion state remain intact and the AI lock is released. This signs off the durable store behavior only. The candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-042-conflict-draft-retention.md`.

Targeted pre-commit and post-commit runs each passed 1/1; full Gradle passed 405/405 tests across 55 suites, with lint 0 errors / 55 warnings. Full log SHA-256 `09984A1F258A9E7E9E1D20C1912957A6554A8F874AEEA707DFA1BB9487AB5782`; full `PowerStoreTest` XML SHA-256 `07B3B69FF89CFD48BFDE74F2084F22A20464650A0E63DC2A1D969FB385ECAD3A`; APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. Structural-only validation JSON SHA-256 `41D784376C9A0D9251188EAE9B78A0DE8F207F265D157306B7E950D552F84AFA`; updated CSV SHA-256 `EF8BBE74CB5AFB260EF011CE4A52BA7B54309D589A3624952361AE5DED2B2958`.

### Case-specific explicit stale-approval verification (2026-10-23)

`REC-P0-050` was exercised in Robolectric API 34 using an isolated PowerStore SQLite database. Test commit `07c7a30919fea42d090dcd7d646ea37322eb9fbd` added `PowerStoreTest.staleDraftRequiresExplicitReapprovalAfterRestart`: a revision 1 replacement becomes stale after completion advances the formal record to revision 2. Following SQLite reopen, a default commit returns `CommitOutcome.Conflict` and preserves the revision 2 title and completion key. A subsequent explicit `approveStale=true` call commits the full replacement as revision 3; the completion key changes only with that approved snapshot. This signs off the storage approval gate only. The candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-043-explicit-stale-reapproval.md`.

Targeted pre-commit and post-commit runs each passed 1/1; full Gradle passed 406/406 tests across 55 suites, with lint 0 errors / 55 warnings. Full log SHA-256 `EEFE6AE0ECBFAE4561FFE0EEDB8B31F7A6FAAF8BF846DEDB85C27377331775AA`; full `PowerStoreTest` XML SHA-256 `63EEBDFDC3469FD5B072A142D20248EBA01F7F940AC97AEB689C470682D8CD23`; APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. Structural-only validation JSON SHA-256 `4D6CCA9829F52A7A785F63CA6134451C36BDA6ED4DADD7145689E2E85A28FD79`; updated CSV SHA-256 `91888ECA9EF43468270E1C45E61F142F43DC2D477DD3014C0DAAD89ACFFF5D94`.

### Case-specific whole-snapshot replacement verification (2026-10-23)

`REC-P0-051` was exercised in Robolectric API 34 using an isolated PowerStore SQLite database. Test commit `05aef89a181426f2a3533ffb87be9c884223bf8d` added `PowerStoreTest.wholeSnapshotReplacementPersistsAcrossRestartWithoutFieldMerge`: after reopening SQLite, the replacement's new title, cleared location/notes and disabled state replace the prior values as one snapshot; event sequence and original creation time remain unchanged, and revision advances to 2. The candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-044-whole-snapshot-replacement.md`.

Targeted pre-commit and post-commit runs each passed 1/1; full Gradle passed 407/407 tests across 55 suites, with lint 0 errors / 55 warnings. Full log SHA-256 `236ABC1658703D51D816D1D56E24ECB2A037F10E9DEEB87D0A65C79AA3CE0663`; full `PowerStoreTest` XML SHA-256 `1F9E906660EE8FC700BE0030E176A1B0D6A871BE62A8A6EFA6BA00B829357047`; APK SHA-256 `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`. Structural-only validation JSON SHA-256 `8907B937B7D0B7EF8DB08204824E977FC0FBF8EE2D6C752C5BFA49D8EA0E1197`; updated CSV SHA-256 `ECD95AAF86930C9693768EF22B56D774212959E75CB23994BD41D1ACAC59CD67`.

### Case-specific stale-field no-merge verification (2026-10-23)

`REC-P0-052` maps to the same `PowerStoreTest.staleDraftRequiresExplicitReapprovalAfterRestart` assertions documented for `REC-P0-050`. A default stale commit after SQLite reopen conflicts and preserves the newer title/completion key; the completion key changes only when the complete old proposal is explicitly approved. The focused test passed targeted pre/post runs 1/1 each and is included in the latest 407/407 full Gradle run. This is a separate candidate sign-off, not an additional test. See `docs/issues/PC-ISSUE-045-no-silent-stale-field-merge.md`.

### Case-specific late AI response protection verification (2026-10-23)

`REC-P0-053` maps to `PowerStoreTest.aiConflictDraftRemainsAvailableForHumanResolutionAcrossRestart`: after a human completion update makes the AI proposal stale, the authorized AI batch publishes zero, retains a conflict draft, preserves the formal title/completion state, and releases its lock. The candidate was rechecked on the current baseline with a targeted **1/1 PASS** run and is included in the latest 408/408 full Gradle run. See `docs/issues/PC-ISSUE-046-late-ai-response.md`.

### Case-specific delayed snooze delivery verification (2026-10-23)

`REC-P0-069` maps to `ReminderCompletionIsolationTest.snoozedRecurringOccurrenceFiresWithoutChangingItsSeries`. The test sends the delayed `ACTION_FIRE` intent with `EXTRA_SNOOZE=true` for one daily recurring occurrence. Before the fix it failed with `expected FIRED, got IGNORED_STALE`; after the fix it records `FIRED`, marks the notification snoozed, and leaves recurrence, formal revision, completion keys, and projected series unchanged. The candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-050-snooze-recurring-occurrence.md`.

Targeted post-fix regression passed **1/1** (log SHA-256 `AA47B7E69FFD12D50B2EA20E01414CFBD8F0C9BB7C66AB44884B583FDEF3D269`; JUnit SHA-256 `1CBCF7EE4E9A176AB377E7BCC5B97063D2A2103266C370AE7D0077D7F632BF2C`). Full Gradle passed **408/408** tests across 55 suites; lint reported 0 errors / 55 warnings. Full log SHA-256 `D74F6F0C70E4E9C7A1CC7FD3AEE8453CF5E79883601CDE342BFF6E94A57354BD`; full `ReminderCompletionIsolationTest` XML SHA-256 `55D5FE8AF988FCF3497DAD78B774A659472AA18DC97B577A2EF033D924588EB5`; Debug APK SHA-256 `460DE2CEE71D00996AFED280AC751B16337CDC129D2273A4A8E61C09C986A13C`. Source/test commit: `f45710a6759fc5532f3982721345c06a912afa3e`. Android AlarmManager delivery and physical-device notification behavior remain untested.

### Case-specific first-draft sequence allocation verification (2026-10-23)

`REC-P0-025` and `REC-P0-027` map to `PowerStoreTest.firstDraftAllocatesASequenceEvenIfItIsDiscarded`. The existing Robolectric API 34 test asserts that the first draft receives `eventSeq=1` at creation; after it is discarded, the next draft receives `eventSeq=2`, `nextSequencePreview()` is 3, and `identityCount()` is 2. At the time of Issue 051, `REC-P0-026` remained NOT TESTED because no failed allocation transaction had been injected; Issue 052 subsequently adds that coverage. The candidates remain `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-051-first-draft-sequence-allocation.md`.

The focused test passed **1/1** (log SHA-256 `528E452DCDCB9904D6D11ABE77BAAFE1FF009E57558255934CF616ADC57BB850`; JUnit XML SHA-256 `541ABB1523C836DCC290C3EA8A876E8DCEAA8BAB8BFCF3A32D37A30296634255`). It is also present in the current 409/409 full Gradle run's 61-test `PowerStoreTest` XML, SHA-256 `1B4C2D53312F5D81119384965BF64410D32401256EFFDC6BBDDFE5992C62A0EE`. No additional source/test modification was required for this earlier sign-off.

### Case-specific failed first-draft allocation verification (2026-10-23)

`REC-P0-026` maps to `PowerStoreTest.failedFirstDraftInsertRetiresItsAllocatedSequence`. A SQLite trigger aborts the draft insert after identity reservation. Before the fix, the transaction rolled back the identity row (`expected identityCount=1, actual=0`). After the fix, the failed draft leaves no pending card, preserves sequence 1 as retired, and advances `nextSequencePreview()` to 2; after dropping the trigger, the retry receives sequence 2 and activates it. The candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-052-failed-first-draft-sequence-retired.md`.

The focused post-fix pair (`failedFirstDraftInsertRetiresItsAllocatedSequence` and `firstDraftAllocatesASequenceEvenIfItIsDiscarded`) passed **2/2** (log SHA-256 `D5DCCE52E1E0B6FA32314F2EAF1FFBFC65F834F76860F27EB05A79C299E6C860`; JUnit XML SHA-256 `B830C9D40B60F30F24EC9C6F009F97B4ED5551279D58E1D776FDB587692838DA`). Full Gradle passed **409/409** tests across 55 suites; lint reported 0 errors / 55 warnings. Full log SHA-256 `EACE03207765FED33CD83882FAD41F9CC477BB48BD26EA5CF14D1B6FD1A07FEE`; `PowerStoreTest` XML SHA-256 `1B4C2D53312F5D81119384965BF64410D32401256EFFDC6BBDDFE5992C62A0EE`; Debug APK SHA-256 `80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`. Source/test commit: `c2f2ad5efb5b84b887fb38b4ee03916c0c23906e`. All 55 JUnit XML files and the full Gradle summary are preserved under `.local/android-test/reports/freeze-20261023/pc-issue-052-*`.

### Case-specific unknown backup structure and future-version rejection (2026-10-23)

`REC-P0-089` maps to `BackupEnvelopeTest.legacyImportRejectsUnknownFieldsButPreservesItsNextOrder`: a legacy v2 payload with `futureField` is rejected, then a valid payload imports into the same isolated target and preserves `nextOrder`. `REC-P0-090` maps to `unknownFutureBackupFormatVersionIsRejected` and `backupFromANewerSchemaIsRejectedInsteadOfGuessed`; format version 99 and a newer database schema are rejected without importing identities. These candidates remain `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-053-backup-unknown-structure-rejection.md`.

The three focused tests passed **3/3** (log SHA-256 `029F8416FCDB443748A36E737559632B8F8C90BB598D6732C5C6B0275AFDA423`; JUnit XML SHA-256 `B117D99E56EFE2C8FE4612DE6994D3417E1C5635E955F6B343E45C1CA2BA4946`). The same three methods are present in the current full-baseline 22-test `BackupEnvelopeTest` XML (SHA-256 `DA216CDE3DE32BAC4A3D460E9E9F7684B7078AD389165E7FE78BCDC92D4EE500`). No source or test modification was needed. The structural-only validation JSON SHA-256 is `F89EB3716E146E5FFB58AE0820070A2B72D92B07A73480708BCAEC81D3130E70`; updated ledger CSV SHA-256 is `E2E45D187C2BFEDC95187DB73075B72B6AE0BADD749154A626298CB76CDD02EC`.

### Case-specific temporal type-conversion verification (2026-10-23)

`REC-P0-021` maps to `TemporalProtocolTest.alarmToActivityRequiresTheMissingEndTime`: an Alarm-to-Activity conversion retains the source start and returns the missing end field for review. `REC-P0-022` maps to `deadlineDateToAlarmMustNotInventATime`: converting a date-only Deadline to an Alarm leaves its start blank and reports `DraftField.START`. The model contracts pass; editor presentation is not covered. Both candidates remain `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-054-temporal-conversion-preserves-missing-fields.md`.

The focused tests passed **2/2** (log SHA-256 `9F27CEC16D6F006D1876B24D6E685E50DD2A8670EA51B8AF39957449D8D4F62B`; JUnit XML SHA-256 `4AB98B1E2E452AB8B6914524D076877206F2C3C6B059ED2A02B62484E86295DD`). They ran on code/test baseline `c2f2ad5efb5b84b887fb38b4ee03916c0c23906e`; the latest full Gradle result on that baseline is 409/409, log SHA-256 `EACE03207765FED33CD83882FAD41F9CC477BB48BD26EA5CF14D1B6FD1A07FEE`. Structural-only validation JSON SHA-256 `BF3A99B621F0B25D969E0D886ED22595B7580D1DEA504E5E148AABFAD368EA11`; updated CSV SHA-256 `E7950013F8932D9D2F8B4498F32673ADF66DD36EEFCA783008B043CDA53A76ED`.

### Case-specific runtime event SQLite persistence verification (2026-10-23)

`REC-P0-081` maps to `PowerStoreTest.multipleCommitsKeepOneCurrentFormalSnapshotAcrossRestart`: three commits leave exactly one current formal snapshot at revision 3 after closing and reopening SQLite, while all three history records remain. This directly verifies the PowerStore/SQLite current-record path; it does not cover other projection writers. The candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-055-runtime-event-sqlite-source.md`.

The focused regression passed **1/1** (log SHA-256 `0204D79B2E0FA988A6C2BD97E810C52335D59256CBD910B38582B9208422B2B1`; JUnit XML SHA-256 `DA3564895E0445B68F38D9B393C95BA8867F2DD939BB3F133F0A8DD507AFDAA9`). It is also present in the current full-baseline 61-test `PowerStoreTest` XML (SHA-256 `1B4C2D53312F5D81119384965BF64410D32401256EFFDC6BBDDFE5992C62A0EE`). Full Gradle on code/test baseline `c2f2ad5efb5b84b887fb38b4ee03916c0c23906e` passed 409/409; log SHA-256 `EACE03207765FED33CD83882FAD41F9CC477BB48BD26EA5CF14D1B6FD1A07FEE`. Structural-only validation JSON SHA-256 `CC388AA2BC2725817CB093C21289F6D6B3E850C54D3E9A8AB653798BBD24C3AE`; updated ledger CSV SHA-256 `B7E704E9A4BCC2B1093C44577E558527C3A69917E2A2F9FBA2F1118C5BF41CD6`.

### Case-specific blocked schedule state isolation verification (2026-10-23)

`REC-P0-018` maps to `PowerStoreTest.blockedScheduleStateDoesNotCompleteOrDeleteItsFormalEventAcrossRestart`. After an Alarm schedule intent is marked `BLOCKED`, the formal event remains present at the committed revision with no completed occurrence keys; the blocked intent, formal snapshot, and one commit-history record all persist across SQLite reopen. This verifies PowerStore state separation only; the Android exact-alarm permission flow is not exercised. The candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-056-blocked-schedule-does-not-mutate-event.md`.

The focused regression passed **1/1** (log SHA-256 `CCE3127D5D083C4B3BA564D24BFF4E9E42A34EA85C7EBCDA33A7FC5539F6160F`; JUnit XML SHA-256 `BA4E34678FC7B0E4B63F03A6567BC9AD994EB1ED785F79FE63C43E582BF9E1F5`). Full Gradle passed **410/410** tests across 55 suites, lint 0 errors / 55 warnings; it includes one protected untracked test (409 tracked + 1 protected). Full log SHA-256 `92E006A3038FDD32D22A806CC2D990CE6255875450256D3DAB51A23B8EE66191`; 62-test `PowerStoreTest` XML SHA-256 `A0214D48AA90000EF2BA455836350FFCF952E00D110ECF0E9A45DAD19CC8E1FF`; APK SHA-256 `80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`. Test-only commit `92dd9016d7907b1e57cd4a27920332fa72e572fc` is pushed. Structural-only validation JSON SHA-256 `A83278D62871564CD5CD6431965E0E6EA7CC0687348062ABA5C93677CA786A4D`; updated ledger CSV SHA-256 `A20D6F4718540835DE9BEF7D47E6E850A9A97B6793E27E4E91D000BA6E386DAA`.

### Case-specific reminder firing/event lifecycle separation verification (2026-10-23)

`REC-P0-019` maps to `ReminderCompletionIsolationTest.firedAlarmRecordsExecutionWithoutCompletingTrackedEvent`. A synthetic `ACTION_FIRE` handled by `ReminderReceiver` records execution as `FIRED` and removes the one-shot schedule intent; the formal event remains present with the original revision and empty `completedKeys`. This verifies receiver/SQLite state separation only; Android AlarmManager delivery and audible ringing are not exercised. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-057-ringing-state-does-not-complete-event.md`.

The focused regression passed **1/1** (log SHA-256 `70E056E358A19C4A0553BE8C5A8F9EB1931FC579D893AE37D4D68AC2F9F1575D`; JUnit XML SHA-256 `48B0A577EDF868BE040C3B132B9AAE2AFBA679DEA61DE0B4293A1932C690B429`). Full Gradle passed **410/410** tests across 55 suites, lint 0 errors / 55 warnings on unchanged code/test baseline `92dd9016d7907b1e57cd4a27920332fa72e572fc`; full log SHA-256 `92E006A3038FDD32D22A806CC2D990CE6255875450256D3DAB51A23B8EE66191`. Debug APK SHA-256 `80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`. Structural-only validation JSON SHA-256 `79161A5A18411CE0100D59F7936F6B2A11C3DD93AEA71C53046AC992C2E16B79`; updated ledger CSV SHA-256 `888A7CC0E8C8CB6B40A1FB67B1D0553E16ACC92B1692B50982BA9CCFE1C3D946`.

### Case-specific atomic formal commit and desired schedule verification (2026-10-23)

`REC-P0-110` maps to `PowerStoreTest.commitRollsBackFormalEventHistoryAndScheduleWhenDesiredIntentWriteFailsThenRecovers`. A SQLite trigger aborts desired schedule insertion after the formal snapshot, commit, and draft-finalization work has begun. The transaction leaves no formal object, commit history, or intent, while the draft remains `PENDING`. After removing the trigger, retry persists revision 1, its commit record, and a matching `PENDING` desired intent; all three remain consistent across SQLite reopen. This verifies PowerStore transaction atomicity only; Android scheduler delivery is not exercised. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-058-atomic-event-schedule-commit.md`.

The focused regression passed **1/1** (log SHA-256 `B2D3FA1EC7E25E718EE17AD2F655D264611B59CD5C1CD1BC2B251F361BD473E2`; targeted JUnit XML SHA-256 `F8518DBBA895E460463763A0F3287D419F0464446D74EBE630A5D1914699A72D`). Full Gradle passed **411/411** tests across 55 suites, lint 0 errors / 55 warnings; it includes one protected untracked test (410 tracked + 1 protected). Full log SHA-256 `57A54B40941585805737E5A48FAFAC16E052CCCEAD3EF3C1538BC67DACABB0E5`; preserved full `PowerStoreTest` XML SHA-256 `7BDCA77CE8F0BC11083B81D7C6188F0F278C8088C2F2711DFD2395C05C10FBC4`; APK SHA-256 `80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`. Test-only commit `4906601d221bba0298068a3568a1bac2ad47c382` is pushed. Structural-only validation JSON SHA-256 `E6C0FF3911A22FA638521A0ED8FACA742ADBD11558620F79A70B1B1E491EF1B8`; updated ledger CSV SHA-256 `3C7671B4C29085D92D0F26974F61C7ECAA6BBF487ABF74E9356BAF1FC91F4B71`.

### Case-specific bounded backup input verification (2026-10-23)

`REC-P0-108` maps to `BackupEnvelopeTest.boundedBackupReaderAcceptsLimitAndStopsOversizedInputAtFirstExcessByte`. The pure bounded reader accepts exactly the configured limit and rejects an oversized stream after consuming only `maxBytes + 1`; the SAF input call site delegates to it. This verifies stream-boundary behavior, not an Android document provider or a device. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-059-backup-size-cap.md`.

The focused regression passed **1/1** (log SHA-256 `D5DCCE52E1E0B6FA32314F2EAF1FFBFC65F834F76860F27EB05A79C299E6C860`; targeted JUnit XML SHA-256 `A7D77CDDC5302324161B2E61E7A493E86986EF483DAAE6929F9891C9E3B26248`). Full Gradle passed **412/412** tests across 55 suites, lint 0 errors / 55 warnings; it includes one protected untracked test (411 tracked + 1 protected). Full log SHA-256 `719C3C4D568F800BB8F48363592049F627216D1D416EF589E9656217D0802A1F`; `BackupEnvelopeTest` XML SHA-256 `4E67C31DD333B9B110BB8D347782D3D14C12FA41068179A79FDD5D4A2AF79530`; APK SHA-256 `1E8332E7A18D71A1516D3E6B3197244738418E7703071D2EBCEF1363938B4BEF`. Code/test commit `a03ada74e2413303b29d58325d24d5eb7081af3f` is pushed. Structural-only validation JSON SHA-256 `2BC80458B8CE333ED720106B293427BCA4B4CA2C88421E821FEAC46414774BF5`; updated ledger CSV SHA-256 `491344DCB84DF7FED5F8632656DD1535D1D8E9FA9EA1869D6DDF66CBB5EBBE90`.

### Case-specific app-version/schema independence verification (2026-10-23)

`REC-P0-084` maps to `BackupEnvelopeTest.backupFromNewerAppVersionRestoresWhenDatabaseSchemaIsSupported`. The test sets envelope `appVersionCode` above the running build while leaving `databaseSchemaVersion` at the supported current schema. Verification succeeds; restoring into isolated SQLite preserves the activity and creates the expected desired alarm intent. This verifies the codec and storage restore path, not Android document-provider behavior. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-060-backup-app-schema-version-independence.md`.

The focused regression passed **1/1** (log SHA-256 `99DBB5932D3D8244A5AE73074A6F6409A00484E7C47B0EFF9C8F0167275F8C4A`; targeted JUnit XML SHA-256 `92CEF9655ADEF7FFAA8111B30506F2C10C02A7D75ABE3D8D57DC8F5E2F1FA6CA`). Full Gradle passed **413/413** tests across 55 suites, lint 0 errors / 55 warnings; it includes one protected untracked test (412 tracked + 1 protected). Full log SHA-256 `01DBFA3453683AE661611AD0F60C912C47670E71387C52FC94E95DD49C0B545D`; `BackupEnvelopeTest` XML SHA-256 `6940193A90D0B7C441A96F048B0B0D0F900E5663C0E8AFF10A1F344A65F36154`; APK SHA-256 `1E8332E7A18D71A1516D3E6B3197244738418E7703071D2EBCEF1363938B4BEF`. Test-only commit `ae0335479df24414c9d9b92696edafbc1efd6d24` is pushed. Structural-only validation JSON SHA-256 `54185A2CDD808A92688742BC08868A9AC82C978D3F12B7B6D41F6C78C27FBA4D`; updated ledger CSV SHA-256 `9705B9110335BBBC0728A31296523C482DED5D8D5197B07B655505EB8298F81F`.

### Case-specific bounded recurring todo projection (2026-10-23)

`REC-P0-064` maps to `PowerStoreTest.unboundedRecurringMemoStoresOneSeriesAndProjectsOnlyTheRequestedWindow`. The test stores one daily MEMO series anchored in 2024, queries a three-day window in 2026, and returns exactly those three occurrence keys. Projection does not add identities or formal event rows; after SQLite reopen the single series remains intact. This verifies PowerStore and the recurrence model, not todo UI rendering or Android device behavior. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-061-recurring-todo-bounded-projection.md`.

The focused regression passed **1/1** (log SHA-256 `846866B86EB8B369291003E9FA2FF7139A0AFB37618669E5F9C6D24675A1D81C`; targeted JUnit XML SHA-256 `8C789D16C7CC3B2252314460DF71942BE4DBAEAEB790D3568AFC932A731CC360`). Full Gradle passed **414/414** tests across 55 suites, lint 0 errors / 55 warnings; it includes one protected untracked test (413 tracked + 1 protected). Full log SHA-256 `C4BC8F0E5EEAA0F66E707435A1AB5187275EDCA2928372EAA4F7EE2470FF13D6`; `PowerStoreTest` XML SHA-256 `A4A22E45E176C36A5CDEEFEB7739DB744754D06FF40F239BFA0D3F6288845BC6`; APK SHA-256 `1E8332E7A18D71A1516D3E6B3197244738418E7703071D2EBCEF1363938B4BEF`. Test-only commit `58ac0c8489d8a1363388c8a66f8a397c7d7610c6` is pushed. Structural-only validation JSON SHA-256 `92C218BFDE612292966666598E2DFCAAE71ECDF25C7DF5C9B411357F16390F0D`; updated ledger CSV SHA-256 `6DD738E46CD1DE0A041427E9341CF1DC1EDE0853A7845C46B25C547B385C1BA7`.

### Case-specific past incomplete recurring todo verification (2026-10-23)

`REC-P0-065` maps to `PowerStoreTest.unboundedRecurringMemoKeepsPastIncompleteOccurrenceAcrossRestart`. The test stores an unbounded daily MEMO series anchored in 2024 and queries the historical 2025-03-14 window while the occurrence remains incomplete. It returns the original occurrence key before and after SQLite reopen; `completedKeys` stays empty and the series remains one formal record. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-062-past-incomplete-recurring-todo.md`.

The focused regression passed **1/1** (log SHA-256 `2EAD5EFBAF553AA6B5CC8DA815466A2E4D4F58CC19212BA32D8D119DA91A3E46`; targeted JUnit XML SHA-256 `E5510FE9F9C8ECC3C2F81E29B39CF4897530220972EC4E064A932C241BD400C7`). Full Gradle passed **415/415** tests across 55 suites, lint 0 errors / 55 warnings; it includes one protected untracked test (414 tracked + 1 protected). Full log SHA-256 `AFFA844FBDCD8AB615E491CFB4D9ADE044C5B8CE9FE87C025A74C1E926FC4E90`; `PowerStoreTest` XML SHA-256 `DF0A260D32C7BF064D274C68E72422AE8925A6416D06526790904E73C008363B`; APK SHA-256 `1E8332E7A18D71A1516D3E6B3197244738418E7703071D2EBCEF1363938B4BEF`. Test-only commit `74f3782a972fdddbf8b4d030e00f164152c1b75d` is pushed. Structural-only validation JSON SHA-256 `387B31D3456D4481C56D02C36E47F964C3FBA8062CAD75195D9F643EF9B6B332`; updated ledger CSV SHA-256 `A5B3B23B12ED461509C2C9088032394C9F8E6C47580863AA774139368632963F`.

### Case-specific local-time daily Alarm verification (2026-10-24)

`REC-P0-076` maps to the daily Alarm timezone recurrence and schedule-intent contract in `PC-ISSUE-063`. The regression changes the current JVM timezone from America/New_York to Asia/Tokyo: the next daily Alarm remains at 07:30 Tokyo time, the original slot key remains valid, and `PowerStore` rebuilds the persisted intent without changing the formal event identity or revision. Only daily Alarm intents are rebuilt; other recurrence kinds retain their stored-zone behavior.

Before-fix targeted result: 1 failure, expected 07:30 and observed 20:30. Final targeted result: **2/2 PASS** (log SHA-256 `AE9E5738BDD4F85C596C54EDFA54472DB923129932A7F0C152D13D4EA624C8F8`). Full Gradle: **417/417** tests across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. This is 416 tracked tests plus one protected untracked test. Full log SHA-256 `1EC0BF04269D7FDF08E8927A507424FECD9BD78E125C12EA37C4047DC5007810`; APK SHA-256 `BB09CBA105BECEE9EFC9950940AA407B3C1AEEEAC39EC00AFC093B9177B69F4D`. Code/test commit `f708dfcf076f122cb77f23bd6e5a1ca7f02001ee` is pushed. Android timezone-broadcast delivery and actual AlarmManager rescheduling remain untested. Independent report: `docs/issues/PC-ISSUE-063-local-daily-alarm-timezone.md`.

### Case-specific declared-timezone Activity verification (2026-10-24)

`REC-P0-077` maps to `TemporalProtocolTest.timezoneBoundActivityKeepsItsDeclaredZoneAcrossDeviceZoneChangeAndDst`. With the JVM default zone set to Asia/Tokyo, a daily Activity in America/New_York stays at 09:00 and retains stable slot keys across the 2026 spring DST change; elapsed spacing is 23 hours. Projection, `nextOccurrence`, and `occurrenceForKey` agree.

The focused regression passed **1/1** (log `.local/android-test/reports/freeze-20261024/pc-issue-064-activity-timezone-targeted-20261024.log`, SHA-256 `EE7DEEEBB65254C6D753562384D600FAC6429CA858CCDF64F1AA67BEBE625C5D`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-064-targeted-junit/TEST-dev.powerfulcalendar.core.TemporalProtocolTest.xml`, SHA-256 `C63E671C6A0A390939A0D7E940D9389244C8C73C1C2D3C32C4E712DAAC47F2A8`). Full Gradle passed **418/418** across 55 suites, lint 0 errors / 55 warnings. Full log `.local/android-test/reports/freeze-20261024/pc-issue-064-full-gradle-20261024.log`, SHA-256 `B8C47A004349B2991582682DFB7A994604119DD511E57765EAFFB6A80B4D164B`; full `TemporalProtocolTest` XML `.local/android-test/reports/freeze-20261024/pc-issue-064-full-junit/TEST-dev.powerfulcalendar.core.TemporalProtocolTest.xml`, SHA-256 `2FD5CE5C4FE92E53D05526977C235B43C7F2F890DBBD41E6C640D403D79ED7A0`; APK SHA-256 `DB944538964299348D31E8981D6A2F701F420FE74E60B508B8C53B253EC0517D`.

Test commit `f497330abdd5851752f65f0e53112709a382b2b9` is pushed. This signs off only the recurrence model contract. No Android timezone broadcast, AlarmManager delivery, or physical-device behavior was tested. Independent report: `docs/issues/PC-ISSUE-064-activity-declared-timezone.md`.

### Case-specific attached Alarm identity verification (2026-10-24)

`REC-P0-024` maps to `ReminderPersistenceTest.activityCanChooseExactAlarmOrMessageIndependentlyOfKind`. The Robolectric API 34 / isolated PowerStore SQLite test commits an `ACTIVITY` with `ReminderMethod.ALARM`; the schedule intent uses the same `eventSeq`, the formal kind remains `ACTIVITY`, and the store contains exactly one event identity. This signs only the storage/identity contract; timeline rendering is not tested.

Focused regression: **1/1 PASS**, log `.local/android-test/reports/freeze-20261024/pc-issue-065-attached-alarm-targeted-verified-20261024.log` (SHA-256 `1405EEECED12A404006A25234E8C66666168CBEA38227A01B22EF4071EF9DDDD`); JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-065-targeted-junit-verified/TEST-dev.powerfulcalendar.core.ReminderPersistenceTest.xml` (SHA-256 `238567F02EE4E5ADE257452CFAC995220CFBABDD7ADE2634F4FB1E9EEB2514CA`). The same method appears in full `ReminderPersistenceTest` XML (SHA-256 `B993FE8DE523803F3D08FB3B514EC47C5E6355125354E52E0FD91046B416D531`). Full Gradle at unchanged baseline `f497330abdd5851752f65f0e53112709a382b2b9`: **418/418**, 55 suites, lint 0 errors / 55 warnings; full log SHA-256 `B8C47A004349B2991582682DFB7A994604119DD511E57765EAFFB6A80B4D164B`; APK SHA-256 `DB944538964299348D31E8981D6A2F701F420FE74E60B508B8C53B253EC0517D`. No production/test source changes or device/UI rendering checks. Independent report: `docs/issues/PC-ISSUE-065-attached-alarm-identity.md`.

### Case-specific direct-edit identity verification (2026-10-24)

`REC-P0-057` maps to `PowerStoreTest.directEditorCheckpointsOnceAndNoChangeSaveClearsItWithoutRevision`. The regression opens `EditSession`, changes the in-memory form, and confirms no pending draft or sequence allocation before checkpointing. Checkpoint creates one draft for the existing `eventSeq`; a repeated checkpoint reuses the same `draftId`. Discard and no-op Save leave identity count and sequence watermark unchanged. This signs the core EditSession/PowerStore contract only; UI and IME behavior are not tested.

Focused regression: **1/1 PASS**, log `.local/android-test/reports/freeze-20261024/pc-issue-066-direct-editor-identity-targeted-20261024.log` (SHA-256 `849A056CBF9FB44EC628A10B71D54E455DE3C5778917D6ABFF42799B1DA4F9EB`); JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-066-direct-editor-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml` (SHA-256 `2B37CCF0D5A58BAF1CF8330BE141CAE527061E6AA72A78B3F3295FA7EC44B1E6`). Full Gradle passed **418/418** across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. Full log `.local/android-test/reports/freeze-20261024/pc-issue-066-full-gradle-20261024.log` (SHA-256 `ACCF4E819C5284768AA3156BF4567BAB580B7196B82B7866CD5242A8635CFF51`); full `PowerStoreTest` XML SHA-256 `A23218A90D838D9BF9F9D20EC24CFF0C998DD3CB24E346AA07459F3A0E849D0E`; APK SHA-256 `DB944538964299348D31E8981D6A2F701F420FE74E60B508B8C53B253EC0517D`. Test-only commit `217b2999ec5ec57e34731bcaabf05edc3c3ff26d` is pushed. Independent report: `docs/issues/PC-ISSUE-066-direct-editor-identity.md`.

### Case-specific AI proposal queue/focus verification (2026-10-24)

`REC-P0-039` maps to `PowerStoreTest.aiProposalAppendsAtQueueTailAndRefreshPreservesCurrentFocus`. The regression seeds two user drafts, routes an already-parsed AI CREATE proposal through `AiDraftService` into the isolated PowerStore SQLite database, and verifies the new draft has the final `queueOrder`. After `DeckController.refresh()`, the previously selected second draft remains current and the AI draft is last in controller state. No network/model request or UI rendering is involved.

Focused regression: **1/1 PASS**, log `.local/android-test/reports/freeze-20261024/pc-issue-067-ai-proposal-queue-targeted-20261024.log` (SHA-256 `4BFB76CD598A1BD6234106A77DF603F44452D464BB39EFE475862F1199BD5B27`); JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-067-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml` (SHA-256 `D9DCB773E0C69D7966332EC38B46D0E02612DE9088A012C6531634EF5DED7B72`). Full Gradle passed **419/419** across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. Full log `.local/android-test/reports/freeze-20261024/pc-issue-067-full-gradle-20261024.log` (SHA-256 `CEEC58B22D9233BFE379795280FC470898FD893E480350D5EE5D77923A3F7E75`); full `PowerStoreTest` XML SHA-256 `B34C2B98B2992E2C6FC8D0C028C316B6FCA55FBB97A2351230F1389D29379E63`; APK SHA-256 `DB944538964299348D31E8981D6A2F701F420FE74E60B508B8C53B253EC0517D`. Test-only commit `feb3f2546d2130bce5a94f16a5734ec6dd576379` is pushed. Independent report: `docs/issues/PC-ISSUE-067-ai-proposal-queue-focus.md`.
## Historical sign-off accounting

| Bucket from historical aggregate | Total | Matched to original ID | Result |
| --- | ---: | ---: | --- |
| P0 | 126 | 0 | Unassessed by original ID |
| P1 | 82 | 0 | Unassessed by original ID |
| Priority unresolved | 1 | 0 | Remains unclassified and unassessed |

The latest full working-tree Gradle run reports 425 tests PASS, 0 failures/errors/skips across 55 JUnit suites, successful skin-boundary verification and Debug assembly, and lint 0 errors / 55 warnings. It includes one test from protected, untracked `app/src/test/java/dev/powerfulcalendar/ui/v2/DraftDeckFeedbackTest.kt` (424 tracked tests + 1 untracked); the file was not modified or staged. The tested code/test change is committed as `2e74007c70fc0adccd14fdb42b6d491c58335ee6`. Log: `.local/android-test/reports/freeze-20261024/pc-issue-072-full-gradle-20261024.log` (SHA-256 `686B5751FFDB8934A261BA2333D74F6D95780415497F939EFC21B10AFE02420A`). This is not a 425-to-209 mapping.

## Required next work

1. Run the 70 `NOT TESTED` rows under the environment each row requires and update only with case-specific evidence.
2. Resolve the one blocked P0, eight blocked P1, and six reconstructed spec-unresolved rows where feasible; retain external/physical/device dependencies as HOLD. The separate historical unclassified slot remains unresolved pending its source.
3. Review the source-based proposed priority allocation. New IDs must never masquerade as historical IDs.
4. Keep the one historical unclassified item visibly unresolved until its source or an explicit review decision is available.
5. Preserve the physical-device, permission, OEM, and specification gaps as HOLD.

## Scope and outcome

Core code and tests have evidence for 98 reconstructed rows across backup, tombstone, migration, recurrence, reminder, temporal model, draft, and core data-semantics contracts; 70 remain NOT TESTED, six reconstructed candidates remain SPEC UNRESOLVED, proposed priorities still need review, and historical mapping is still absent. `REC-P0-054` is unresolved because the product baseline and SQLite v4 specify conflicting same-event draft cardinality. `REC-P0-058` passes the current-baseline semantic no-op Save test; `REC-P0-059` passes the current-baseline recurrence start-bound/anchor projection test; `REC-P0-069` passes the delayed snooze receiver regression with real Android delivery still untested; `REC-P0-025/027` pass the first-draft identity allocation regression; `REC-P0-026` passes the injected first-draft insert failure regression; `REC-P0-089/090` pass the unknown backup field and unsupported format/schema rejection tests; `REC-P0-021/022` pass pure-model conversion field-preservation regressions; `REC-P0-081` passes the PowerStore/SQLite current-snapshot persistence regression; `REC-P0-018` passes the PowerStore blocked-schedule state isolation regression, with OS permission handling untested; `REC-P0-019` passes receiver/SQLite firing-state separation, with actual Android delivery untested; `REC-P0-110` passes injected PowerStore rollback and successful retry/reopen persistence for formal event, commit history, and desired schedule; `REC-P0-108` passes exact-limit backup stream acceptance and first-excess-byte rejection, with Android SAF provider input untested; `REC-P0-084` passes backup restore with a higher app version and supported database schema, with Android SAF provider input untested; `REC-P0-064` passes bounded recurrence projection for a daily MEMO todo while storing one series across SQLite reopen; `REC-P0-065` passes historical-window retrieval of an incomplete occurrence from the same stored series across SQLite reopen; `REC-P0-076` passes current-local-zone daily Alarm scheduling and intent rebuild while retaining the original occurrence key and formal event revision; `REC-P0-077` passes declared-zone Activity recurrence across a device-zone change and DST; `REC-P0-024` passes the attached Alarm single-identity PowerStore contract; `REC-P0-057` passes direct-edit identity/watermark preservation in EditSession/PowerStore; `REC-P0-039` passes parsed-AI proposal queue ordering and controller focus retention; `REC-P0-125` passes local typed CREATE/REPLACE parsing and draft storage without formal write; `REC-P0-124` passes the local AI client request-boundary checks that attachment instructions stay in untrusted user content and no tools are supplied; `REC-P0-123` passes the client boundary that suppresses calendar-context transmission when persistent sharing is disabled; `REC-P0-126` passes PowerStore's scoped single-use authorization boundary and rejects completed-session and stale-epoch replays; `REC-P0-115` passes receiver validation of trigger identity before notification; `REC-P0-119` passes the recurring snooze/series isolation regression; `REC-P0-120` passes the per-occurrence exception persistence and series-preservation regression across SQLite reopen; `REC-P0-121` passes the disabled imported-Alarm/no-schedule contract across SQLite reopen. Live model compliance, user-confirmation wiring, and the complete AI/network flow remain unverified. PC-QA-GATE-001 remains **PARTIAL / HOLD**, and overall core verdict remains **HOLD**.

### Case-specific typed AI proposal with no direct formal write (2026-10-24)

`REC-P0-125` maps to `PowerStoreTest.parsedAiCreateAndReplaceRemainDraftsUntilExplicitCommit`. Parsed CREATE and REPLACE operations are retained as pending drafts by `AiDraftService`; the CREATE has no formal event row, and the existing formal event's title, revision, and start remain unchanged. This is local parser/service/SQLite evidence only; live model/network behavior and Android device behavior were not tested. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-068-ai-proposal-no-direct-write.md`.

The focused regression passed **1/1** (log SHA-256 `102500AD61A34D7F039BAC87833C4F8C1C2DF903F122538D506BDB39921D2ACA`; focused JUnit XML SHA-256 `FAA4934C08CC20FC95519B8015B6B187A140968F06772A4F490647B5C5E12EED`). Full Gradle passed **420/420** across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings, including one protected untracked test (419 tracked + 1 protected). Full log SHA-256 `65303061E11CDA9BDBD9CEF3A86D47E70C6A5640F69CB96C199BB847D08F5F23`; full `PowerStoreTest` XML SHA-256 `60583B79475745F337E20A8033168F5CDB45AC0219EAA7655EDC2DDA57044469`; APK SHA-256 `DB944538964299348D31E8981D6A2F701F420FE74E60B508B8C53B253EC0517D`. Test-only commit `95f1be3ebb99fea96b063e48191e634c0fda1dbc` is pushed. Structural-only validation JSON and updated ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.

### Case-specific attachment prompt-injection boundary (2026-10-24)

`REC-P0-124` maps to `AiChatClientTest.analyzeEvidenceKeepsInjectedAttachmentInUntrustedUserContent`. A fake malicious attachment remains within the user-role text delimiters; the fixed system message explicitly treats extracted text as untrusted and the request contains no model tools. This validates the app's request boundary, not whether a live model obeys the policy. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-069-ai-attachment-prompt-injection-boundary.md`.

The focused regression passed **1/1** (log SHA-256 `DF967CCE7EAD6B14F318607A1F642BEB0BEACC1628A100CB1AA29E08E2BC945B`; focused JUnit XML SHA-256 `E492A189C43A585FA587046435033B52437263F10DDEE6F6A841C447BCC8D729`). Full Gradle passed **421/421** across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings, including one protected untracked test (420 tracked + 1 protected). Full log SHA-256 `F4EFE9D371D0FBF9DFAB367212C365CDFD6E41DEE3242206A68F39C2026FCB59`; full `AiChatClientTest` XML SHA-256 `F06173002CD53D22B6319EAAD86DB8BA84C1018067007118B57EFCF975880F1B`; APK SHA-256 `DB944538964299348D31E8981D6A2F701F420FE74E60B508B8C53B253EC0517D`. Test-only commit `926f9b2098d9fa529ba8887625b37f135ed1602c` is pushed. Structural-only validation JSON and updated ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.

### Case-specific calendar-context opt-out boundary (2026-10-24)

`REC-P0-123` maps to `AiChatClientTest.interpretDoesNotSendCalendarContextWhenSharingIsDisabled`. The test deliberately supplies a private event while persistent context sharing is disabled; the outbound request contains the user's text but not the event title, notes, or location. The original context remains local for response parsing. This validates the AiClient opt-out boundary, not the caller's per-request consent UI or live provider behavior. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-070-ai-context-optout-boundary.md`.

The focused regression passed **1/1** (log SHA-256 `48CEBC303D01FEA86733AFE9DD7368202FCB8667BF287550D039A894A8DD475C`; focused JUnit XML SHA-256 `B7324507C67D4EA41621BC3389133C7CC4F61FD0E5DA7CA251D2AA04D191C4F5`). Full Gradle passed **422/422** across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings, including one protected untracked test (421 tracked + 1 protected). Full log SHA-256 `35AACA17F04FCDCA7F0DE095C83123B0E70BD5F900656AB925CE6588C3C84CC4`; full `AiChatClientTest` XML SHA-256 `53B6778219F326AF61685B68DC8538C2A9A0A43BE10BB0D610526CB746A786CA`; APK SHA-256 `9C56BAEFD17D0EBF1D43BFE25BF02592C3EF20C108E92CD117EE03A6FD0BC134`. Code/test commit `ad2286864d4b89ce51e7c9dd0fae3508bd52b817` is pushed. Structural-only validation JSON and updated ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.

### Case-specific scoped single-use AI batch authorization (2026-10-24)

`REC-P0-126` maps to `AiAuthorizationTest.completedBatchApprovalCannotBeReplayed`. The regression finishes an authorized batch and confirms its session cannot commit, checks that an old authorization cannot replay under a later epoch, then verifies a fresh authorization for the reviewed version and CREATE operation can commit. Rejected attempts leave the formal event absent and the draft pending. Existing tests cover reviewed-content changes, discarded cards, operations outside the approved set, and scope growth. This is Robolectric API 34/PowerStore SQLite evidence; it does not test user-confirmation wiring, a live model, or an Android device. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-071-ai-batch-authorization.md`.

The focused regression passed **1/1** (log SHA-256 `F64F2CF0BD4AF7D0AC028F060814B587F93D1F8BE6060DBC0FE05B522A56C299`; focused JUnit XML SHA-256 `DBADF23DB7829559F08E8BEB378DF5CC7E7F29A4A1AC4E07D85BE00B7D6EBB0C`). Full Gradle passed **423/423** across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings, including one protected untracked test (422 tracked + 1 protected). Full log SHA-256 `FBC84B23CDA78D3FA92C48B787FE4A37CBAE8773DE9171789A2F41B2F7ECC646`; full `AiAuthorizationTest` XML SHA-256 `E41AE0447F2112833056D18B9C69363DDA736876930E5C87D1939A203550C81A`; APK SHA-256 `9C56BAEFD17D0EBF1D43BFE25BF02592C3EF20C108E92CD117EE03A6FD0BC134`. Test-only commit `e8eb48c96f4132c0fe9af63aac8933e0d8435257` is pushed. Structural-only validation JSON and updated ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.

### Case-specific stale alarm trigger identity (2026-10-24)

`REC-P0-115` maps to `ReminderCompletionIsolationTest.fireWithDifferentTriggerIdentityIsRejectedBeforeNotification` and `fireWithoutTriggerIdentityIsRejectedBeforeNotification`. A pre-fix receiver regression reproduced a wrongly identified alarm as `FIRED`; after the fix, unknown and missing trigger IDs are recorded as `IGNORED_STALE`, no reminder notification is posted, and the formal event revision/title remain unchanged. `ReminderValidationTest` also checks stale revisions, invalid occurrence keys, and changed trigger instants. This is Robolectric API 34 receiver/SQLite evidence; Android-created PendingIntent delivery and a physical device are not covered. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-072-stale-alarm-trigger-identity.md`.

The focused `ReminderCompletionIsolationTest` and `ReminderValidationTest` suites passed **15/15** (log SHA-256 `627331652F012346A7B7AF45908B25D13913412C31531296AF1E5B5C1B2223A3`; targeted XML SHA-256 `72BE1CFEC8E617B78A75F264ABEE9C30E3C769CEA09BA3AC9D8009B1731F0FA9` and `82FB0B0C9809C5C712FAF7D103BA544F84C71602A40146EF56B947A7FEB25825`). Full Gradle passed **425/425** across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings, including one protected untracked test (424 tracked + 1 protected). Full log SHA-256 `686B5751FFDB8934A261BA2333D74F6D95780415497F939EFC21B10AFE02420A`; full XML SHA-256 `B82C6A8FE584CB8E58A76ECFB8970AA9134054C0CA39EC548E49ACF8D625D997` and `9D7CF52324194653EBB94527384E7D1293AB1CA4B74D107981D1D2B1D0B5877A`; APK SHA-256 `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`. Code/test commit `2e74007c70fc0adccd14fdb42b6d491c58335ee6` is pushed. Structural-only validation JSON and updated ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.

### Case-specific recurring snooze/series isolation (2026-10-24)

`REC-P0-119` maps to `ReminderCompletionIsolationTest.snoozedRecurringOccurrenceFiresWithoutChangingItsSeries`. A tracked daily Alarm is snoozed and its delayed `ACTION_FIRE` is handled; the recurrence rule, formal revision, completion keys, and projected occurrence series remain identical, while execution is recorded as `FIRED`. This is Robolectric API 34/SQLite evidence using synthetic receiver intents; actual Android `AlarmManager` delivery, notification-action interaction, and physical-device behavior are not covered. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-073-recurring-snooze-series-isolation.md`.

The focused regression passed **1/1** (log SHA-256 `56043D7990B6FD9525C0E78F929FACFB349BC229EDFCB7A03EE34755247832BB`; JUnit XML SHA-256 `4F601F5DDDF44F90B118F9892428D98316AC5CB0F2D1CA097BEC71895334ED91`). At that time, the full Gradle baseline was **425/425** across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. The complete `ReminderCompletionIsolationTest` XML includes this test as passing and has SHA-256 `B82C6A8FE584CB8E58A76ECFB8970AA9134054C0CA39EC548E49ACF8D625D997`; full log SHA-256 `686B5751FFDB8934A261BA2333D74F6D95780415497F939EFC21B10AFE02420A`; APK SHA-256 `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`. Code/test baseline `2e74007c70fc0adccd14fdb42b6d491c58335ee6` is pushed. Structural-only validation JSON and ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.

### Case-specific skip-one-occurrence persistence and series isolation (2026-10-24)

`REC-P0-120` maps to `PowerStoreTest.skippingOneRecurringOccurrencePersistsWithoutChangingTheSeriesAcrossRestart`. A daily series with five generated keys is saved with one exception through `EditSession`; the event identity and anchor remain stable, only the selected key is added to the exception set, and all other original keys remain unchanged. Reopening the same SQLite database preserves the complete record and projection. This is Robolectric API 34/isolated SQLite evidence; the candidate's API 35 emulator and physical-device paths were not tested. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-074-skip-occurrence-series-isolation.md`.

The focused regression passed **1/1** (log SHA-256 `A6DF8B1298DF20686233F1FFA0F4DF397F3B1FF351D2B596C4816AC5F9B46A7C`; focused JUnit XML SHA-256 `DF9C6F6B870D2FB8F0924A5E77EEBE8322C7CA3323EDAE3FFC7A9B53441CA295`). Full Gradle `testDebugUnitTest assembleDebug lintDebug --offline --rerun-tasks` passed **426/426** across 55 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full `PowerStoreTest` XML SHA-256 `0708D187CD30FC7B5ED10DA53B28B93056ADEFB6C3402DA1D8D03CE8AA771FF5`; full log SHA-256 `6DBE2C449A70D4EC5076AA1BD70CFD6618DA1208AC7DA20A782F1649577B15EC`; APK SHA-256 `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`. Test-only commit `531186ec99046290116b5e63c8aabd57914e8558` is pushed. Structural-only validation JSON and ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.

### Case-specific imported Alarm disabled until old-device handoff (2026-10-24)

`REC-P0-121` maps to `PowerStoreTest.structuredImportIsIdempotentAndDisablesImportedAlarms`. An enabled source Alarm with a real exact-reminder trigger is imported through the default handoff-required path; the formal record is disabled, no durable schedule intent exists, and repeating the same source/external ID creates no second event. After SQLite reopen, the record remains disabled and still has no schedule intent. This is Robolectric API 34/isolated SQLite evidence; API 35 emulator, actual old OEM alarm inventory, and the end-to-end handoff workflow were not tested. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-075-imported-alarm-handoff-gate.md`.

The focused regression passed **1/1** (log SHA-256 `1570009954AC1830CADD7CE88D15C26E82EE7AE5F79CD0590C9CAB5C224C964D`; focused JUnit XML SHA-256 `783DD548AA6A668294F614BA61454E3C192D2D72CAC5F5EB0F5C07664DF15F6D`). Full Gradle `testDebugUnitTest assembleDebug lintDebug --offline --rerun-tasks` passed **426/426** across 55 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full `PowerStoreTest` XML SHA-256 `B580DD16E60CA1B90404CC913C2E608A571F0A142409E90A0035E3813472FF61`; full log SHA-256 `7C86322AC0231F9DDE9721CA5CBD07F3584D8AA4927775F599A0E8723FC3805F`; APK SHA-256 `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`. Test-only commit `25f69e1dc6ffa94078e7797e7034f881150be1c3` is pushed. Structural-only validation JSON and ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.
### Case-specific OEM alarm inventory boundary (2026-10-24)

`REC-P0-122` maps to the core adapter boundary. `SystemCalendar.readUpcoming()` queries Android Calendar `Instances`; it does not enumerate OEM clock alarms. `showSystemAlarms()` only builds `AlarmClock.ACTION_SHOW_ALARMS`, and `setSystemAlarm()` uses `ACTION_SET_ALARM` with `EXTRA_SKIP_UI=false`. Robolectric API 34 verifies the helper intent shape and handoff flags. This does not verify an API 35 resolver, physical device, OEM inventory, or end-to-end handoff. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-076-oem-alarm-inventory-boundary.md`.

Focused regression passed **2/2** (log SHA-256 `7A7681E8C46B2AD043ED28E6B004FA99759F13357AAF1BCC3EEE00132C57FDEB`; focused JUnit XML SHA-256 `FD587FE8C180A1B457C85B7BFE58F6B60545459CD9C9C510E5CEE5F7E00893E2`). Full Gradle passed **428/428** across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full `SystemCalendarTest` XML SHA-256 `1057998CFD9906C85BB87225DA296E138B0600CA73FA524EFF4F275C04E91172`; full log SHA-256 `5D145A9B243EE744B12D50C12D8BD6D818F1A9B6764C94B3970C71B9A22C8B04`; APK SHA-256 `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`. Test-only commit `539174c8036924119ed4b8efecba85644e4aa2c3` is pushed. Structural-only validation JSON and ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.
### Case-specific stale-revision reminder broadcast (2026-10-24)

`REC-P0-116` maps to `ReminderCompletionIsolationTest.oldRevisionBroadcastIsRejectedBeforeNotification`. The test stores the original schedule identity, commits a replacement at a newer revision, then calls `ReminderReceiver.handle()` with the original revision, occurrence key, trigger ID, and instant. The receiver records `IGNORED_STALE`, has no active notification for the event, and leaves the current formal revision and title intact. This is Robolectric API 34 with isolated SQLite; it does not exercise Android `onReceive()`/`goAsync()`, AlarmManager delivery, or actual audio. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-077-stale-revision-broadcast.md`.

Focused regression passed **1/1** (log SHA-256 `8599A5B267A70A771D667FD75828DBA5A366C34BD9293C68BE9729456031A3DF`; focused JUnit XML SHA-256 `64083AFB262A402CB8E8D858F67E5527CD3751EB400F2F980E6967E423CF267B`). Full Gradle passed **429/429** across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full `ReminderCompletionIsolationTest` XML SHA-256 `8B76687057F165C01EA9AE3BD84E13972E4C331B2899DC16BCBC657170F7063F`; full log SHA-256 `B3BE49D78F18375D52140ED6E446218FAD61735C8450440A7C42454CD008E3CF`; APK SHA-256 `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`. Test-only commit `afc99cb71acc13c7e75df6fff94996db52b387ac` is pushed. Structural-only validation JSON and ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.
### Case-specific exact-alarm denial remains fail-closed (2026-10-24)

`REC-P0-114` maps to `ReminderValidationTest.exactAlarmPermissionDeniedBlocksWithoutApproximateFallback`. The test explicitly selects `ReminderMethod.ALARM` and verifies the resulting desired trigger requirement is EXACT. With Robolectric `ShadowAlarmManager` configured to deny exact scheduling, `ReminderScheduler.reconcile()` persists `BLOCKED` and the exact-permission hint, with no AlarmManager task scheduled. This is Robolectric API 34/isolated SQLite evidence; Android API 35 permission changes, physical devices, and OEM behavior were not tested. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-078-exact-alarm-no-approximate-fallback.md`.

Focused regression passed **1/1** (log SHA-256 `562D7C28338E9F935F0B72C915CB810EADEEC2D6841B8BF89A648A7082D083F4`; focused JUnit XML SHA-256 `EDA0EB71B6C3F309792A45F9690C3915EE87BBE96E9BB81B06EF54A87133A567`). Full Gradle passed **430/430** across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full `ReminderValidationTest` XML SHA-256 `049886363CFB9CAC1D6A1B7CEF046888318C92A457A4CC06E08AF436654E86A6`; full log SHA-256 `D0426B8657887CF08A3DD5FB896CFBCEB9EA358A93410EB283D01B6E154C71F1`; APK SHA-256 `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`. Test-only commit `c2310688f92ef432721a93652753dc13fca9012d` is pushed. Structural-only validation JSON and ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.
### Case-specific exact-alarm permission revocation reconciliation (2026-10-24)

`REC-P0-113` maps to `ReminderValidationTest.revokingExactAlarmPermissionBlocksScheduledIntentOnNextReconcile`. The test selects `ReminderMethod.ALARM`, grants exact scheduling through `ShadowAlarmManager`, reconciles the resulting EXACT intent to `SCHEDULED`, then revokes the shadow permission and reconciles again. The durable intent becomes `BLOCKED` with an exact-permission hint. Robolectric API 34 and isolated SQLite are used. Android permission-state broadcast delivery, `BootReceiver` lifecycle, OS cancellation of an already scheduled alarm, API 35, and physical/OEM behavior were not tested. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-079-exact-alarm-permission-revoked.md`.

Focused regression passed **1/1** (log SHA-256 `F64F2CF0BD4AF7D0AC028F060814B587F93D1F8BE6060DBC0FE05B522A56C299`; focused JUnit XML SHA-256 `1D4D516A48ACD1BBEF05C16B63DAEFA328FB892E33B8FC850E307180AD0930F1`). Full Gradle passed **431/431** across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full `ReminderValidationTest` XML SHA-256 `7C27195F2F978B973401F3A4D7DC63376BE19336F7DA84B9542A385EB675B12D`; full log SHA-256 `EF9B3C190BEC5C7D0D1ED94B422893F4E44DB456E6A0CF4936FAF13ED5CC6A91`; APK SHA-256 `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`. Test-only commit `a45188fd107b05cec1467851acf647422c8230f6` is pushed. Structural-only validation JSON and ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.

### Case-specific pending reminder intent submission (2026-10-24)

`REC-P0-111` maps to `ReminderValidationTest.pendingExactIntentIsSubmittedAndMarkedScheduledIdempotently`. The focused Robolectric API 34 test verifies a persisted EXACT/PENDING intent is submitted once, transitions to SCHEDULED, and remains idempotent on a second direct `ReminderScheduler.reconcile()` call. This proves the scheduler sub-path only. Attempts to exercise the `BootReceiver.goAsync()` broadcast path did not produce reliable completion in the Robolectric harness; their failure evidence and limitation are recorded in `docs/issues/PC-ISSUE-080-pending-intent-reconciliation.md` and `.local/android-test/reports/freeze-20261024/pc-issue-080-async-receiver-test-triage.md`. Therefore `REC-P0-111` remains NOT TESTED, not PASS.

Focused regression passed **1/1** (log SHA-256 `562D7C28338E9F935F0B72C915CB810EADEEC2D6841B8BF89A648A7082D083F4`; focused JUnit XML SHA-256 `37C6158F5079C3AFD33B2D018C1DE250CBAEBAC655323FC3C21ED3AD0D9F7917`). Full Gradle passed **432/432** across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full `ReminderValidationTest` XML SHA-256 `C304D9C020C179223668EDADB3B08CE03B3F81B8E0E8E0C6C1CC2207CCAC7A58`; full log SHA-256 `C688F166B7B9081E61FFCAEA5249C4558762E4FDC039763181C3C8F92BB02C3A`; APK SHA-256 `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`. Test-only commit `d4e989cb9233d9be11ad3380234e3374613c1226` is pushed. Structural-only validation JSON and ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.

### Case-specific oldest-first DeckController open after SQLite reopen (2026-10-24)

`REC-P0-037` maps to `PowerStoreTest.openingDeckAfterStoreReopenFocusesOldestQueueOrder`. Two drafts are persisted, the same SQLite database is reopened, and `DeckController.open()` loads cards in ascending `queueOrder` and focuses the oldest draft. This is Robolectric API 34 core PowerStore/DeckController evidence; Compose rendering and API 35 device restart were not tested. The reconstructed candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-081-oldest-draft-first.md`.

Focused regression passed **1/1** (log SHA-256 `56043D7990B6FD9525C0E78F929FACFB349BC229EDFCB7A03EE34755247832BB`; focused JUnit XML SHA-256 `E432A0B1CCFBDC810025652E29FBA39C6F00A7D4D8DEF1D86BDAD99D6C3FF4FB`). Full Gradle passed **433/433** across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full `PowerStoreTest` XML SHA-256 `3874DD67D69DF91537B35AD6F354C4C22BB5D1C059E45A0364F77F4604D14F16`; full log SHA-256 `435E1B57D7D42F83ED7D7C6DB932561ED5517C9020FB9CD7EA288D710C0295CA`; APK SHA-256 `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`. Test-only commit `2ff7bb50eb1c35e978317c6428b04e6363398d27` is pushed. Structural-only validation JSON and ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.

### Case-specific event-date edit preserves draft queue order (2026-10-24)

`REC-P0-041` maps to `PowerStoreTest.changingDraftEventDateDoesNotReorderQueueAfterRestart`. The test changes the event time of the older queue item so it becomes later than the next item, stages the edit, closes and reopens the same SQLite database, and verifies the edited event time persists while draft IDs and `queueOrder` remain in their original order. This is Robolectric API 34 isolated SQLite core evidence; the candidate remains `UNMAPPED` to the original checklist. See `docs/issues/PC-ISSUE-082-edit-date-keeps-queue-order.md`.

Focused regression passed **1/1** (log SHA-256 `21B43A8C6AADCE9F8AD564AA5FD54A027E9617EBE95FCF75D396BF38A9D5DDA5`; JUnit XML SHA-256 `5D51689A5B6C1C120422AE3A1617760B8CF67E6F794687587B6DC43CEA12AA66`). Full Gradle passed **434/434** across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full `PowerStoreTest` XML SHA-256 `3DDD799537652092CDE84A910E09853023A1239B1320DEF8E4F025A62EDB11CF`; full log SHA-256 `1426EAAF0BBA6A4EABE55ADCA5FE543C8B23C10C00686B58361F12E89ECF90AA`; APK SHA-256 `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`. Test-only commit `141750b42472ddcfe365242f7702ff4f3a717080` is pushed. Structural-only validation JSON and ledger hashes are recorded above and in `docs/CORE_FREEZE_VERDICT.md`.
