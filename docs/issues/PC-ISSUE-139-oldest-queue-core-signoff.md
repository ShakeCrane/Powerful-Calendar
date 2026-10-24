# PC-ISSUE-139 — Oldest pending queue item core sign-off

Date: 2026-10-24 | Risk: P1 | Reconstructed acceptance key: `REC-P1-037` | Status: **PASS within the controller ordering boundary**

This sign-off covers the persistent core queue order and the first item selected by `DeckController.open()` after SQLite reopen. It does not sign the visible Draft Deck presentation, search/filter controls, keyboard interaction, or physical-device behavior.

## Requirement and test

`CORE_CONTRACT_FREEZE_2026-10-21.md` invariants 4 and 9–10 and `PRODUCT_BASELINE_G1-G11.md` §5 require pending cards to remain ordered by independent monotone `queueOrder`, oldest first. The corresponding reconstructed acceptance row is `REC-P1-037`; it remains `UNMAPPED` to the unavailable historical 209-item list.

The existing Robolectric API 35 test `PowerStoreTest.openingDeckAfterStoreReopenFocusesOldestQueueOrder` creates two synthetic pending cards, confirms the older card has the lower `queueOrder`, closes and reopens the same SQLite database, then calls `DeckController.open()`. Assertions check the full oldest-first card order and that the oldest card's `draftId` and `queueOrder` are current. The test source was introduced in commit `2ff7bb50eb1c35e978317c6428b04e6363398d27`; PC-ISSUE-139 adds no product or test source changes.

## Result

- Focused test: **1/1 passed**, zero failures/errors/skips.
- Full offline Gradle regression: **471/471 JVM tests**, 59 suites, zero failures/errors/skips. Source-boundary verification, Debug APK assembly, AndroidTest APK assembly, and lint passed; lint reported 0 errors / 58 warnings.
- No Compose UI was launched and no Android instrumentation, emulator interaction, or physical-device test was run. This result signs only the controller's oldest-first model state.
- The full regression included the protected pre-existing untracked `DraftDeckFeedbackTest.kt`; it was not modified or staged for this Issue.

## Evidence

- Focused log `.local/android-test/reports/freeze-20261024/pc-issue-139-targeted-r2.log`; SHA-256 `879C07F74B2EE3A45152B367C560255BA18E6F899DD7F1BC2E8CFD867E838D06`.
- Focused JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-139-artifacts/PowerStoreTest-targeted.xml`; SHA-256 `BC59F5A16248907C3AEB32382E109502499D1D25C6187F80BEF99087B6FAA4A6`.
- Full Gradle log `.local/android-test/reports/freeze-20261024/pc-issue-139-full-gradle.log`; SHA-256 `D30BCDD343E2FE62E7820A7D5A543813291CAD7F75DBECF32C196360A953D07B`.
- Preserved full-run JUnit XML: `.local/android-test/reports/freeze-20261024/pc-issue-139-artifacts/full-results/` (59 suites); summary JSON SHA-256 `6D748FAC2CBFCF94031FDD53F379311083B945C7FDFBD6CED00DFB78713B736B`.
- Lint XML SHA-256 `2BEABC23C862180FE0301AC2F3A3FABDBC432D106CFDDFBC0E1188879BCD1B47`.
- Debug APK SHA-256 `35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4`; AndroidTest APK SHA-256 `D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A`.
- The reconstructed-ledger structural validator remains structural-only (`product_behavior_tested=false`); its current artifact and ledger digest are recorded in `CORE_FREEZE_VERDICT.md` and `PC-QA-GATE-001`.

Overall core freeze remains **HOLD**.
