# PC-ISSUE-046 — Late AI proposal cannot overwrite a human update

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

An AI replacement based on an older formal revision must not overwrite a newer human update. The PowerStore/AI batch path must retain the stale proposal as a conflict while preserving the current formal state.

## Evidence

`PowerStoreTest.aiConflictDraftRemainsAvailableForHumanResolutionAcrossRestart` (test source introduced by commit `06c0534a9fc43c7cda243dbe431384c72aa3b161`) exercises this sequence: create a stale replacement, advance the formal event through a human completion update, then let the authorized AI batch attempt publication. The batch publishes zero and keeps one; the stale card is marked `CONFLICT`. After SQLite reopen, the draft is still queryable with its complete proposal, while the formal title and human completion key remain intact and the batch lock is released.

The focused regression was rerun against the current code/test baseline and passed **1/1**, zero failures/errors/skips. The same test is included in the latest full Gradle run: **407/407 tests**, 55 suites, zero failures/errors/skips; lint **0 errors / 55 warnings**. No test or production source change was needed for this candidate mapping.

- Current-baseline targeted log `.local/android-test/reports/freeze-20261023/pc-issue-046-late-ai-response-targeted-20261023.log`, SHA-256 `0CA9D96C5418228DF22D9FBFF9CC4877D84334CDA9C3AFAE3C8FC2524E02EF64`.
- Targeted JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-046-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `42F2D6BEC52102C64211BE8A8A5DCC929E579E952BA5F5CB0D98D99D2C4F2461`.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-044-whole-snapshot-replacement-full-gradle-20261023.log`, SHA-256 `236ABC1658703D51D816D1D56E24ECB2A037F10E9DEEB87D0A65C79AA3CE0663`; full `PowerStoreTest` XML SHA-256 `1F9E906660EE8FC700BE0030E176A1B0D6A871BE62A8A6EFA6BA00B829357047`.

## Candidate sign-off and limits

`REC-P0-053` is marked **PASS** in the reconstructed ledger for the durable store behavior. It remains `UNMAPPED` to the original 209-case checklist. No UI behavior is claimed; the overall core freeze remains **HOLD**.

The linked structural-only ledger validation is `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-046.json`, SHA-256 `3A4260D0DF362383130530DD2739F6C7EEC9B0985B301C7ACCAD5EDF75AAF457`; it validates row structure only, not product behavior.

## Commit and push

- Evidence test commit: `06c0534a9fc43c7cda243dbe431384c72aa3b161` (pushed to `origin/main`).
- This independent candidate report and synchronized ledger/verdict updates are committed and pushed separately.
