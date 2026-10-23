# Core freeze progress
## PC-ISSUE-102 — completion tracking toggle keeps reminder scheduling (candidate remains NOT TESTED)

- Added a Robolectric API 34 `ReminderValidationTest` that schedules an Activity reminder, disables completion tracking, then verifies the new revision returns to PENDING and reconciles to SCHEDULED with one Alarm; the updated revision validates and the old revision is rejected.
- Targeted test passed 1/1. Full Gradle passed 454/454 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. This is scheduler-adapter evidence only; real Android alarm/notification delivery remains untested. No UI or production code changed.
- Test commit `200d5ae4783bfe2d2b0d95acd0ce0f572a5d2ebe` is pushed. Independent report: `docs/issues/PC-ISSUE-102-tracking-toggle-keeps-reminder-scheduled.md`. `REC-P0-017` remains NOT TESTED.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-102.json`, SHA-256 `FB29AA81FCFCA98BED762116A0F6E6AFCEBC7439642421B6BBEA73FBB7DC9085`; ledger SHA-256 `671FDBD1B5371FC6B961A867713CBE0B222A03E943820294A2B3D4A6F6FDAA7E`.

## PC-ISSUE-101 — AI missing-time proposal core checkpoint (candidate remains NOT TESTED)

- Added `PowerStoreTest.parsedAiActivityWithMissingTimeRemainsRepairableDraftAcrossRestart`. The parser keeps empty ACTIVITY times blank; the proposal stays a draft, a formal commit is rejected, and the INVALID draft/form survive SQLite reopen. Filling both times restores PENDING and allows one valid commit.
- Targeted Robolectric API 34 passed 2/2. Full Gradle passed 453/453 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Missing-type and API 35/card paths were not tested; no UI or production code changed.
- Test commit `814f3f2c6cb2a3d451495ae8492c8629d0aa7476` is pushed. Independent report: `docs/issues/PC-ISSUE-101-ai-incomplete-proposal-missing-time.md`. `REC-P1-051` remains NOT TESTED.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-101.json`, SHA-256 `7ECF656AF47EC32029B1B525ABE4636F06190BE762BE3119163422426FC1A6F4`; ledger SHA-256 `FB88F6EC57099AD2169DEF1828116634A22C2095A945E711E3013D546F00F519`.

## PC-ISSUE-100 — mixed AI CREATE/REPLACE core checkpoint (candidate remains NOT TESTED)

- Existing parser and PowerStore regressions were run directly. One structured response stays as separate CREATE and REPLACE proposals; two PENDING drafts are created, the REPLACE keeps the target eventSeq, and neither formal event is published or mutated.
- Targeted Robolectric API 34 passed 2/2 across two suites. The same code baseline's full Gradle run passed 452/452 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No provider call or UI change.
- Independent checkpoint report: `docs/issues/PC-ISSUE-100-mixed-ai-create-replace-core-checkpoint.md`. API 35/free-provider request and proposal-card presentation remain untested, so `REC-P1-049` stays NOT TESTED.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-100.json`, SHA-256 `CC8184361CCE7C1C4921D3FEFCD5579E0D6939B8CF591820D468F72ABBB1026A`; ledger SHA-256 `040CD2EEFAF281FA018ED465194C8D6ADCAC93CCAAE40B333CAD7BD23D18E5BC`.

## PC-ISSUE-099 — structured proposal HTTP 429 stage diagnostics

- Reproduced that a local HTTP 429 returned a generic “AI request failed” warning without naming the structured-proposal stage. Updated `AiClient.interpret` to preserve the stage in the warning. The focused test also verifies no proposal/raw reply is returned and neither the provider error body nor API key is exposed.
- Pre-fix targeted reproduction failed 1/1 on the missing stage label. Post-fix Robolectric API 34 passed 1/1. Full Gradle passed 452/452 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Used a local HTTP stub only; no external provider or UI change.
- Core fix/test commit `55f3c1212eafaf306ad70e89b308fa1c3b3612c5` is pushed. Independent report: `docs/issues/PC-ISSUE-099-structured-proposal-http-429-stage.md`. The API 35 success-card path is untested; `REC-P1-062` remains NOT TESTED.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-099.json`, SHA-256 `D073E87A42F7970F8094DB7E1B2E8CF3DFF03A6B4DA9487A76076F3C0CD03D20`; ledger SHA-256 `386E2BD443FC58F9BE1D3F392CF8B3E7C5020EAB17439FE7DBCE038F26CED2B5`.

## PC-ISSUE-098 — repeated approval core idempotency checkpoint (candidate remains NOT TESTED)

- The existing `PowerStoreTest.repeatedCommitReturnsTheOriginalOutcome` was run directly: a second ordinary commit of the same draft returns `duplicate=true`, the same revision/commitId, and leaves one history record. No code defect was found and no source/test file changed.
- Targeted Robolectric API 34 passed 1/1. The current code baseline's full Gradle run passed 451/451 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. API 35 approval action and device behavior remain untested; no UI was changed.
- Independent checkpoint report: `docs/issues/PC-ISSUE-098-ai-approval-idempotency-core-checkpoint.md`. `REC-P1-057` remains NOT TESTED.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only artifact: `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-098.json`, SHA-256 `FD48462009013801EACEF7CE707CD0D5E4D05D908F2028813BB583211AC7733E`; ledger SHA-256 `09D00A90C6CF4F49E47B5AADA8C213E12F1ABCAA877A9F7547F730183C06A8E2`.

## PC-ISSUE-097 — AI draft persistence core checkpoint (candidate remains NOT TESTED)

- Added `PowerStoreTest.parsedAiCreateProposalPersistsAsAnUnpublishedDraftAcrossRestart`: a locally parsed CREATE proposal becomes a PENDING draft, creates no formal event/history, and keeps its identity, form, version, status, and queue entry after SQLite reopen. No production defect was found; this is partial core evidence only.
- Targeted Robolectric API 34 passed 1/1. Full Gradle passed 451/451 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. `adb` is unavailable; API 35/free-provider selection and device behavior were not tested. No UI was changed.
- Test commit `0aa6fa50a00925246aff7dd13822ad1ef15ece4f` is pushed. Independent checkpoint report: `docs/issues/PC-ISSUE-097-ai-draft-persistence-process-restart.md`. `REC-P1-056` remains NOT TESTED.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-097.json` SHA-256 `66163C61D912A6B2984023F017ADD825D4E3DC346051253B955A92FEF8269EC3`; ledger SHA-256 `BC18A6B477FF77AC67EEC3F632424D294FC80410BAE18478D5F40EA8142C88A9`. Original 209 case IDs remain unmapped.

## PC-ISSUE-096 — AI proposal rejection preserves the formal event

- Added `PowerStoreTest.theAiBatchDiscardOnlyRejectsProposalAndPreservesFormalEventAcrossRestart`. It publishes an Activity, creates an AI REPLACE proposal, discards it through `AiBatchRunner`, and verifies the formal snapshot and commit history remain unchanged before and after SQLite reopen; the proposal is DISCARDED and the batch unlocks.
- Targeted Robolectric API 34 passed 1/1. Full Gradle passed 450/450 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. This was a coverage-only change; no production defect was reproduced.
- Test commit `65f6cabd653878f77127e167a583bea31043e5e3` is pushed. Independent report: `docs/issues/PC-ISSUE-096-ai-proposal-rejection-does-not-mutate-event.md`. `REC-P1-055` is PASS for the core discard/persistence contract.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1; 117 reconstructed candidates have independent evidence. Original 209 case IDs remain unmapped.
- `REC-P1-059` remains NOT TESTED: AI proposal, draft, and batch authorization models do not represent dependent-group identity, and the freeze spec does not define group membership. No group semantics or UI were invented. Continue with independent non-UI core acceptance.
## PC-ISSUE-095 — AI independent batch item failure and retry

- Added a SQLite-trigger regression for a three-item batch whose middle commit fails. The pre-fix runner published 1 item instead of the expected 2 and skipped the later independent item. Fixed `AiBatchRunner` to isolate ordinary exceptions per item, preserve cancellation propagation, report the failure and continue; a partial-failure batch now ends as `FAILED` rather than `CANCELLED`.
- The regression confirms first/last items commit, the failed draft transaction rolls back and remains pending, `published=2 / kept=1 / cancelled=false` and the report survive SQLite reopen, the lock releases, stale authorization is rejected, and a new batch retries the retained draft successfully.
- Pre-fix reproduction log SHA-256 `62EF0C91214E7C38C0DABAC65B2A60571C9B17E38264239510B60D8184C950FC`. Final targeted test passed 1/1 (log SHA-256 `B7BA661157DE0E9A4DD96C5F8ACBBEE26DB826EE709CD2E981E5D2AE0B88B17A`; JUnit XML SHA-256 `0D09525904E9BF11BCD4DFB01207801C03AE5869243FACB067CE8130F79291D0`). Full Gradle passed 449/449 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `2F809BCB7A5C03973688D09662B7809BE64DF7A333B17FDAF8C316281DC3572D`.
- Commit `361f074538a27ad75c2a91c7da4bdee6beb46795` is pushed. Independent report: `docs/issues/PC-ISSUE-095-ai-batch-independent-item-failure.md`. `REC-P1-058` is PASS for the core runner/SQLite partial-success contract; no model or network call was made.
- Freeze remains HOLD: 134 PASS / 0 FAIL / 9 BLOCKED / 59 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 23/0/8/50/1; 116 reconstructed candidates have independent evidence. Original 209 case IDs remain unmapped.
- Next: continue a feasible core acceptance item; keep remaining failures and device gaps visible.

## PC-ISSUE-094 — Reminder recovery broadcast matrix

- Added `BootReceiverRecoveryTest.recoveryBroadcastsReconcileDesiredAlarmAndFinishAsyncWork`. Five synthetic non-boot actions (app replaced, timezone changed, clock changed, exact-alarm permission changed, internal reconcile) each clear the simulated AlarmManager registration, dispatch the receiver, await `goAsync()` completion, and verify one registration is rebuilt from the durable `SCHEDULED` intent. The boot action remains covered by PC-ISSUE-093.
- Focused `BootReceiverRecoveryTest` passed 2/2 (log SHA-256 `15C9FC8800907C64CFEBEA601F91C2CAF56570B09920F6F9FFDC14238CB81803`; JUnit XML SHA-256 `A7BBB9E5A22B8BFEB6A21F851E19EDFF279F3CFF3FE0E649560912E2FE91E349`). Full Gradle passed 448/448 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `3BF5BF314F9F4320B07788CB0629653AA90199A83F1429A392F2A622DE5D4116`.
- Test commit `3983bf7dcc1ba5b5b1ec9e84d621fe4aad77d99c` is pushed. Independent report: `docs/issues/PC-ISSUE-094-reminder-recovery-broadcast-matrix.md`. `REC-P0-117` remains NOT TESTED because dispatch was synthetic and this environment has no `adb`; actual Android system delivery and physical/OEM recovery are unverified.
- Freeze remains HOLD: 133 PASS / 0 FAIL / 9 BLOCKED / 60 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 22/0/8/51/1. Original 209 case IDs remain unmapped. This loop is limited to core acceptance.
- Next: continue with a feasible core acceptance item while preserving the actual-device recovery gap.

## PC-ISSUE-093 — BootReceiver asynchronous schedule reconciliation

- Added `BootReceiverRecoveryTest.bootBroadcastReconcilesDesiredAlarmAndFinishesAsyncPendingResult`. Robolectric API 34 dispatches `ACTION_BOOT_COMPLETED` through `goAsync()`, changes the durable Alarm intent from PENDING to SCHEDULED, registers one ShadowAlarmManager task, completes `PendingResult`, and retains the result after SQLite reopen.
- Focused regression passed 1/1 (log SHA-256 `CEBEC27C47343C23D1998248D6C7FC99176310CFE41BDACECD9BA7AA58EC439F`; JUnit XML SHA-256 `C6B73D20FB7495F9E0C3AE24DF5E3F8A41918C93C0D62B2B427D8B2366FD621B`). Full Gradle passed 447/447 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `AF800BA5E17A2822EC9A79CCB9309A980DE4A199769A54F7225560BFE19FF707`.
- Test commit `86e9d97106592edc41d988db317d47687be89976` is pushed. Independent report: `docs/issues/PC-ISSUE-093-boot-receiver-async-reconciliation.md`. `REC-P0-111` passes for the Robolectric receiver path; actual system boot broadcast and physical/OEM delivery remain untested. Test-harness failures from the first two attempts are documented with hashes.
- Latest freeze remains HOLD: 133 PASS / 0 FAIL / 9 BLOCKED / 60 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 22/0/8/51/1; 115 reconstructed candidates have independent evidence. Original 209 case IDs remain unmapped. Scope remains core-only; pale horizontal edge accepted. Protected untracked files remain unchanged and unstaged.
- Next: continue with a feasible non-UI core acceptance item.

## PC-ISSUE-092 — AI batch user cancellation and partial result

- Added and pushed `PowerStoreTest.theAiRunnerStopsAfterUserCancelAndPersistsPartialOutcomeAcrossRestart`. The runner receives a cancellation request after the first item, does not publish the second item, returns `published=1 / kept=0 / cancelled=true`, releases the lock and persists the report across SQLite reopen; stale session writes remain denied.
- Focused Robolectric API 34 regression passed 1/1 (log SHA-256 `FE85E768847D06B41AEC5128FD8CC85C528C997DD1127439EAA282FF05B0163D`; JUnit XML SHA-256 `F776710373A58963149903B83ED69D2667D4D27AB40F6DCFDBA924636013F638`). Full Gradle passed 446/446 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `4A8039764F0D6B25A47B8E0B4C9FC31445E15A64382FAA3EFD6363A61C10933A`.
- Test commit `d79b0f6d11870c5a18c6b7401ebe18e72fe95c05` is pushed. Independent report: `docs/issues/PC-ISSUE-092-ai-batch-user-cancel-partial-result.md`. `REC-P1-060` is PASS for the core runner cancellation and partial-report persistence path; the model service is not called.
- Latest freeze remains HOLD: 132 PASS / 0 FAIL / 9 BLOCKED / 61 NOT TESTED / 7 SPEC UNRESOLVED; 114 reconstructed candidates have independent evidence. The original 209 case IDs remain unmapped. Scope remains core-only; pale horizontal edge accepted. Protected untracked files remain unchanged and unstaged.
- Next: continue with a feasible non-UI core acceptance item.

## PC-ISSUE-091 — AI batch cancellation cleanup and stale-epoch fencing

- Added and pushed `PowerStoreTest.cancelledAiBatchRunnerReleasesLockAndFencesRemainingScopeAcrossRestart`. Cancellation after the first completed item preserves that formal event and the unfinished draft, releases the batch lock, persists `CANCELLED`, and rejects the stale `AiSession` after reopening SQLite. Existing orphan recovery separately simulates a previous owner token and rejects its late write.
- Focused Robolectric API 34 regression passed 1/1 (log SHA-256 `3D6BEDC5462DF72ADCE56A06A479A92FA2EB6521AFD400769A2C6D61554DE5DB`; JUnit XML SHA-256 `4B9C5709A0E8F9689B3E249B5CDCC5E3C5541DE507C29A95BE2BE602B2321D02`). Full Gradle passed 445/445 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `BDEB8CB1A9047D1E30A7FEDD0A2E63AEA30B21744748F298BF90360F5F82BB5D`.
- Test commit `65fb3ca8bda51cf7c6bd91794789e2b152e9603a` is pushed. Independent report: `docs/issues/PC-ISSUE-091-ai-batch-cancellation-recovery.md`. `REC-P1-061` is PASS for the core batch state/authorization contract; live model timeout and OS-level process-kill behavior were not exercised.
- Latest freeze remains HOLD: 131 PASS / 0 FAIL / 9 BLOCKED / 62 NOT TESTED / 7 SPEC UNRESOLVED; 113 reconstructed candidates have independent evidence. The original 209 case IDs remain unmapped. Scope remains core-only; pale horizontal edge accepted. Protected untracked files remain unchanged and unstaged.
- Next: continue with a feasible non-UI core acceptance item.

## PC-ISSUE-090 — ICS TZID and VTIMEZONE serialization

- Mapped existing `IcsExportRecurrenceTest` fixed-offset and DST round-trip assertions to `REC-P1-078`. Focused Robolectric API 34 run passed 6/6; full Gradle at the same code/test baseline passed 444/444 across 56 suites, with zero failures/errors/skips and lint 0 errors / 58 warnings.
- Focused log SHA-256 `67CB928029CBB1759776544081D710802D7A47E24E60F15733D6C7DE3B569676`; JUnit XML SHA-256 `B48271D52E3B178E494F252BFAE05A6796A333FA7D1F327DBF8522F089D9C2A6`. Independent report: `docs/issues/PC-ISSUE-090-ics-timezone-definition.md`. No source changes. `REC-P1-078` is PASS for codec output; third-party consumer behavior remains outside scope.
- Latest freeze remains HOLD: 130 PASS / 0 FAIL / 9 BLOCKED / 63 NOT TESTED / 7 SPEC UNRESOLVED; 112 reconstructed candidates have independent evidence. Historical 209 IDs remain unmapped.
- Next: continue with a core-only candidate; do not start UI work.
## PC-ISSUE-089 — ICS read projection preserves one formal event source

- Added and pushed `PowerStoreTest.icsActivityProjectionDoesNotCreateSecondFormalEventAcrossRestart`. A committed SQLite Activity projects through ICS as one matching event without warnings; formal rows, history, reminder intent, identity count and sequence preview remain unchanged before and after SQLite reopen. Test-only change; no production or UI changes.
- Focused Robolectric API 34 regression passed 1/1 (log SHA-256 `394711335A4B9DE80FEE60B81FF224C85645883FBA16403AC25A496A683550AA`; XML SHA-256 `F6CC86C486E178FF119E8AB730626FDD0C7F940B557A7480DEBE850D68D051F1`). Full Gradle passed 444/444 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Log SHA-256 `EE9DA7BC1A810D3C706DAA44F7CBE0D3EED6C5557572F5690F50C45621BFE44D`.
- Test commit `13e720bce54d0b31a179e68e9fa1a86eec518c61` is pushed. Independent report: `docs/issues/PC-ISSUE-089-ics-read-projection-does-not-mutate-store.md`. `REC-P0-082` remains NOT TESTED: UI/AI projection and Android SAF/device paths are not covered.
- Evidence handling note: the prior structural-only Issue 088 JSON was reserialized from its captured content after a filename collision; its new SHA-256 is `4D8A8F14B8D50995CC9F66D990AF1ABF47C76ECBB887187381E6D00C09230A70`. Issue 088 test logs and JUnit evidence were untouched. Issue 089 validation is stored at its own filename.
- Latest freeze remains HOLD: 130 PASS / 0 FAIL / 9 BLOCKED / 63 NOT TESTED / 7 SPEC UNRESOLVED; 112 reconstructed candidates now have independent targeted evidence. Historical 209 IDs remain unmapped. User scope remains core-only; no UI work. Protected untracked files remain unchanged and unstaged.
- Next: continue with the next feasible non-UI core acceptance item.

Updated: 2026-10-24

## PC-ISSUE-088 — date-only Deadline completion precision

- Added and pushed `PowerStoreTest.completingDateOnlyDeadlinePreservesDatePrecisionAcrossRestart`. Completion changes only the completion key/revision; `deadlineDate` and DATE precision remain, and deadlineMillis/startMillis/endMillis stay null after SQLite reopen. No UI or production code changed.
- Focused regression passed 1/1 (log SHA-256 `09908CFC9350A75119B589F1AD665E2B14626B6AF39C71ED619B127F89FBF43F`; XML SHA-256 `3EFDF79FEAA9ED6862967256710C40E1522272741CAC74B9349D000F6E02CDB5`). Full Gradle including source boundary passed 443/443 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `84C5052C61812EFEE91155A59BFC42364DC3BEEB01701EA4DD1593F6474998CE`.
- Test commit `d94d98b1de5d3573420c8fd9e29f58c8f73ae9f8` is pushed. Independent report: `docs/issues/PC-ISSUE-088-date-only-deadline-completion.md`. `REC-P0-023` remains NOT TESTED because the required UI rendering behavior was not tested.
- Latest freeze remains HOLD: 129 PASS / 0 FAIL / 9 BLOCKED / 64 NOT TESTED / 7 SPEC UNRESOLVED; 110 reconstructed candidates have independent targeted evidence. Historical 209 IDs remain unmapped.
- User scope: no UI work; faded horizontal edge is accepted, and the blue current-time marker may cover the center axis. Keep Issue 002 PARTIAL/BLOCKED and do not retry around the free-model rate limit or call unauthorized paid models.
- Protected untracked files `DraftDeckFeedbackTest.kt`, `design/`, and `PC-OBS-005` remain unchanged and unstaged.
- Next: continue with the next feasible non-UI core acceptance item.

## PC-ISSUE-087 — completion tracking and reminder state independence

- Audited the reconstructed “Pause object” wording and found no such behavior in the approved contract or core code. Narrowed `REC-P0-017` to contract Invariant 2: tracking, reminder scheduling and event enabled state are independent.
- Added and pushed `PowerStoreTest.changingCompletionTrackingPreservesReminderIntentAcrossRestart`. Both tracking directions preserve enabled/reminder fields and the equivalent pending intent across SQLite reopen. Test commit `5b610b7bc48951e1205cf18cc72fe83782d93fac` is pushed.
- Focused regression passed 1/1 (log SHA-256 `486ED3A39D00EFC5B7ECAE685292F3D1CCD365BEA6AB16A6A2D9665FCC6EFBE8`; XML SHA-256 `395F805708218E12ED551E8FDD0C667E832F28A32D30677B8D1C6CEC6EBF672C`). Full Gradle including source boundary passed 442/442 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `F36A991D37DF9CEEABD17623CFC7175740EFC05F51DDCCA882EB9ACF66FCB4CF`.
- Independent report: `docs/issues/PC-ISSUE-087-tracking-and-reminder-state-independence.md`. `REC-P0-017` remains NOT TESTED because Android scheduler delivery and physical/OEM behavior are not covered. No UI or production code changed.
- At the Issue 087 snapshot, freeze remained HOLD: 129 PASS / 0 FAIL / 9 BLOCKED / 64 NOT TESTED / 7 SPEC UNRESOLVED; 109 reconstructed candidates had independent targeted evidence. Historical 209 IDs remained unmapped.
- User scope: no UI work; faded horizontal edge is accepted, and the blue current-time marker may cover the center axis. Keep Issue 002 PARTIAL/BLOCKED and do not retry around the free-model rate limit or call unauthorized paid models.
- Protected untracked files `DraftDeckFeedbackTest.kt`, `design/`, and `PC-OBS-005` remain unchanged and unstaged.
- Next: continue with the next feasible non-UI core acceptance item.

## PC-ISSUE-086 — whole-series recurring rule persistence

- Added and pushed core regression `PowerStoreTest.wholeRecurringSeriesEditKeepsIdentityAndAnchorAcrossRestart`: `EditSession` saves the changed title/rule while keeping the event identity and original anchor; recurrence projection and stored state remain stable after SQLite reopen.
- Focused regression passed 1/1 (log SHA-256 `394711335A4B9DE80FEE60B81FF224C85645883FBA16403AC25A496A683550AA`; XML SHA-256 `25D33D615FBE7DD1FDD0AF36BA4727C467EE698B0139F69BA25546FCC129F2CE`). Full Gradle including source boundary passed 441/441 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `1C898C47EFC096A0A6919609F543DE3FC3B25F63E0FA19EF8B263DA9DC602173`.
- Test commit `49f334e8cea1005de59ef9b98a16e32f544b937e` is pushed. Independent report: `docs/issues/PC-ISSUE-086-whole-series-core-edit.md`. `REC-P0-080` remains NOT TESTED because explicit whole-series scope selection and device behavior were not exercised; this is partial core persistence evidence only.
- At the Issue 086 snapshot, freeze remained HOLD: 129 PASS / 0 FAIL / 9 BLOCKED / 64 NOT TESTED / 7 SPEC UNRESOLVED; 108 reconstructed candidates had independent targeted evidence. Historical 209 IDs remained unmapped.
- User scope: no UI work; faded horizontal edge is accepted, and the blue current-time marker may cover the center axis. Keep Issue 002 PARTIAL/BLOCKED and do not retry around the free-model rate limit or call unauthorized paid models.
- Protected untracked files `DraftDeckFeedbackTest.kt`, `design/`, and `PC-OBS-005` remain unchanged and unstaged.
- Next: continue with the next feasible non-UI core acceptance item.

## PC-ISSUE-085 — recurring this-and-future split

- Core transaction implementation and regression tests committed and pushed: `cf1be08500f02adc0edba12c9c97bc4c7d53fea1` (`feat(重复): 支持指定实例及后续序列拆分`) and `86043231d14594cce89942f8847c5fe66e7bd178` (`fix(重复): 保留首个有效实例后的剩余次数`).
- Fix covers atomic old-series truncation/new-sequence creation, compatible occurrence-state migration, rollback, and retaining the existing identity when no earlier active non-excepted occurrence exists. If the selected slot becomes the anchor and the count is unchanged, only the remaining count is retained.
- Both controlled pre-fix reproductions failed on the identity/count mismatch; final focused set passed 4/4. Full Gradle, including source boundary, passed 440/440 across 56 suites; lint 0 errors / 58 warnings. Full log SHA-256: `0F4780D20727E1F0418E337F070A9229C4D1D898E5829DCB7A8AC42BDB25D433`.
- Independent report: `docs/issues/PC-ISSUE-085-this-and-future-series-split.md`. `REC-P0-079` remains NOT TESTED because editor scope selection/call path and API 35/physical-device behavior are not covered. `REC-P0-078` default scope and `REC-P0-080` whole-series selection remain open.
- No UI changes. User accepted the faded horizontal edge and confirmed the blue current-time marker may cover the center axis. Do not reopen UI work.
- At the Issue 085 snapshot, freeze remained HOLD: 129 PASS / 0 FAIL / 9 BLOCKED / 64 NOT TESTED / 7 SPEC UNRESOLVED; 107 reconstructed candidates had independent targeted evidence. Historical 209 IDs remained unmapped.
- Next: continue with the next feasible non-UI core acceptance item; keep Issue 002 PARTIAL/BLOCKED and do not bypass free-model rate limits or use unauthorized paid models.
