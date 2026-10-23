# PC-ISSUE-052 — Retire event sequence when first-draft insertion fails

Date: 2026-10-23

Severity: P0 candidate

Status: **POWERSTORE IDENTITY REGRESSION PASS / OVERALL FREEZE HOLD**

## Finding

`newDraft` previously allocated an event identity and inserted its first draft inside one SQLite transaction. If the draft insert failed after allocation, SQLite rolled back both the identity row and `next_seq`. The next attempt could then reuse the sequence that the product contract says must never be reused, even after a failed first draft.

A `BEFORE INSERT ON drafts` trigger reproduced the failure. The regression expected one permanently allocated identity, but the pre-fix store reported `identityCount=0` after the aborted transaction.

## Change

- New event creation now commits its sequence allocation in a separate transaction and marks the reservation retired while the draft is being written.
- The draft transaction activates that identity only after the draft insert and read-back succeed. If insertion fails or the process stops between these transactions, the reserved sequence remains retired and `next_seq` has already advanced.
- Existing-event draft updates remain in one transaction. UI and business data are unchanged.

## Verification evidence

Environment: Robolectric API 34, isolated PowerStore SQLite database; no Android device.

- Pre-fix fault-injection regression: **1 test failed as expected**; after the draft trigger aborted, `identityCount` was 0 instead of 1. Log `.local/android-test/reports/freeze-20261023/pc-issue-052-prefixed-failed-first-draft-sequence-20261023.log`, SHA-256 `211ECF5E47D721648BBD304648E44096173BFBF643C875BC7E177FF1C57CE336`.
- Post-fix targeted command: `:app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerStoreTest.failedFirstDraftInsertRetiresItsAllocatedSequence" --tests "dev.powerfulcalendar.core.PowerStoreTest.firstDraftAllocatesASequenceEvenIfItIsDiscarded" --offline --rerun-tasks --console=plain` — **2/2 PASS**, zero failures/errors/skips. The fault-injected draft leaves no pending card, preserves retired sequence 1 and advances the high-water mark to 2; after removing the trigger, retry gets sequence 2 and activates it. The existing discard regression also passes.
- Targeted log `.local/android-test/reports/freeze-20261023/pc-issue-052-postfix-targeted-final-20261023.log`, SHA-256 `D5DCCE52E1E0B6FA32314F2EAF1FFBFC65F834F76860F27EB05A79C299E6C860`; JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-052-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `B830C9D40B60F30F24EC9C6F009F97B4ED5551279D58E1D776FDB587692838DA`.
- Full command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 409/409 tests across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. This includes 408 tracked tests and one protected untracked test; that protected file was not modified or staged.
- Full log `.local/android-test/reports/freeze-20261023/pc-issue-052-full-gradle-20261023.log`, SHA-256 `EACE03207765FED33CD83882FAD41F9CC477BB48BD26EA5CF14D1B6FD1A07FEE`. All 55 suite XMLs are preserved under `.local/android-test/reports/freeze-20261023/pc-issue-052-full-junit/`; `PowerStoreTest` XML SHA-256 `1B4C2D53312F5D81119384965BF64410D32401256EFFDC6BBDDFE5992C62A0EE` (61 tests, including both identity regressions).
- Summary `.local/android-test/reports/freeze-20261023/pc-issue-052-full-gradle-summary.json`, SHA-256 `C962E07FFFEC8D19B028CF918EF71360C3585EE649AEEFD405BF00A04A89F2B3`; lint XML SHA-256 `134709DFD801FA4D5CA9A5E60FBA14F00BC577C783175492C4121EB15F385A11`; Debug APK SHA-256 `80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`.

## Candidate sign-off and limits

`REC-P0-026` is PASS in the reconstructed ledger for a failure after identity allocation but during first-draft insertion. The row remains `UNMAPPED` to the unavailable original 209-case checklist. This Robolectric/SQLite regression does not claim physical-device behavior; overall core freeze remains **HOLD**.

## Git

The source and regression test were committed as `c2f2ad5efb5b84b887fb38b4ee03916c0c23906e` (`fix(存储): 失败草稿分配后永久退休序号`) and pushed to `origin/main`. The synchronized ledger and freeze-document update is committed separately.

Structural-only ledger validation: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-052.json`, SHA-256 `C3DE85012BF0F935CFE419DDB745D891D76B33ED4B8ED4C4C50A5AE9F04EDF15`; CSV SHA-256 `E5C5FBD748B1A4AFD2ACB0802E438780B32DAB1AD17712A25716D0CEAB0AB3AF`. It confirms 209 unique candidate IDs, the 126/82/1 priority partition, 98/0/9/95/7 status counts, and `REC-P0-026` PASS. This validation checks ledger structure and counts only; `product_behavior_tested` is false.
