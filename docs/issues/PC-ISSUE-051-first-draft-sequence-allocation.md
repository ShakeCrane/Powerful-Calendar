# PC-ISSUE-051 — First draft allocates and retires its event sequence

Date: 2026-10-23

Severity: P0 candidate

Status: **POWERSTORE IDENTITY REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

The first create draft must receive its permanent `eventSeq` when the draft is created. If that draft is discarded, its allocated identity must remain retired and the next create draft must receive a different sequence.

The existing `PowerStoreTest.firstDraftAllocatesASequenceEvenIfItIsDiscarded` directly asserts both behaviors using the SQLite-backed PowerStore. The existing regression passed when rerun on the current code/test baseline, so no source change was needed.

## Candidate mapping

- `REC-P0-025`: the first `newDraft` immediately receives `eventSeq=1`, before any formal commit.
- `REC-P0-027`: after discarding that draft, the next create draft receives `eventSeq=2`; the preview advances to 3 and the identity table retains two allocations.
- `REC-P0-026` remains NOT TESTED. This test does not inject or prove rollback/failure behavior during first-draft allocation.

These are separate candidate assertions from one test method; neither is mapped to an unavailable historical case ID.

## Verification evidence

Environment: Robolectric API 34, isolated PowerStore SQLite database; no Android device.

- Command: `:app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerStoreTest.firstDraftAllocatesASequenceEvenIfItIsDiscarded" --offline --rerun-tasks --console=plain` — **1/1 PASS**, zero failures/errors/skips.
- Targeted log `.local/android-test/reports/freeze-20261023/pc-issue-051-first-draft-sequence-targeted-20261023.log`, SHA-256 `528E452DCDCB9904D6D11ABE77BAAFE1FF009E57558255934CF616ADC57BB850`.
- Targeted JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-051-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `541ABB1523C836DCC290C3EA8A876E8DCEAA8BAB8BFCF3A32D37A30296634255`; 1 test, 0 failures/errors/skips.
- The same test is present in the latest full Gradle run on code/test baseline `f45710a6759fc5532f3982721345c06a912afa3e`: **408/408 PASS** across 55 suites, with lint 0 errors / 55 warnings. Its `PowerStoreTest` XML contains 60 tests, including this exact method, and has SHA-256 `2EE44003E98165F0ADBAC654EB90DE040CBCFB1EA1C7E92AA08581EEA39A2F1A`. Full log SHA-256 `D74F6F0C70E4E9C7A1CC7FD3AEE8453CF5E79883601CDE342BFF6E94A57354BD`; Debug APK SHA-256 `460DE2CEE71D00996AFED280AC751B16337CDC129D2273A4A8E61C09C986A13C`.

No production code, UI, business data, schema, or protected asset changed. The isolated SQLite regression does not claim an Android device or process-kill test.

## Candidate sign-off and limits

`REC-P0-025` and `REC-P0-027` are PASS in the reconstructed ledger. `REC-P0-026` remains NOT TESTED. All three remain `UNMAPPED` to the original 209-case checklist. Overall core freeze remains **HOLD**.

## Git

This sign-off changes only the independent report, acceptance ledger, and synchronized freeze documentation. It is committed separately with a Chinese conventional commit and pushed to `origin/main`.

Structural-only ledger validation: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-051.json`, SHA-256 `F6EE4280BF6D2D9FF7901E58615227841B509DAFCB5790B6C2C12E58A3004D6D`; CSV SHA-256 `A2841A6FF90DCA39AF8F43390E4E1E0F4B72CB14AD4B5BC80ECAC181E28E251F`. It confirms 209 unique candidate IDs, the 126/82/1 priority partition, 97/0/9/96/7 status counts, `REC-P0-025/027` PASS, and `REC-P0-026` NOT TESTED. This validation checks ledger structure and counts only; `product_behavior_tested` is false.
