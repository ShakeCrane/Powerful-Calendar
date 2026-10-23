# PC-ISSUE-082 — Changing an event date must not reorder the draft queue

Date: 2026-10-24

Candidate: `REC-P0-041` (reconstructed P0; historical mapping remains `UNMAPPED`)

Status: **CORE QUEUE SEMANTICS PASS / API 35 editor presentation NOT TESTED / overall freeze HOLD**

## Finding

Queue position is a durable `queueOrder` property. Editing the event time of a draft must not move that card relative to other drafts, even when the edited event time becomes later than the next card's event time.

## Change and evidence

Added `PowerStoreTest.changingDraftEventDateDoesNotReorderQueueAfterRestart`. It creates two drafts whose event start times and queue orders are initially ascending in the same order. It then changes the first draft's event time to after the second draft's time, stages the change, closes and reopens the same SQLite database, and verifies the draft IDs and queue orders remain in their original order while the edited time persists.

This is Robolectric API 34/isolated SQLite core evidence. It does not exercise editor presentation or an API 35 device restart. No production code changed.

## Verification

- Focused `:app:testDebugUnitTest --tests dev.powerfulcalendar.core.PowerStoreTest.changingDraftEventDateDoesNotReorderQueueAfterRestart --offline --rerun-tasks --console=plain`: **1/1 PASS**. Log `.local/android-test/reports/freeze-20261024/pc-issue-082-date-edit-queue-targeted-20261024.log`, SHA-256 `21B43A8C6AADCE9F8AD564AA5FD54A027E9617EBE95FCF75D396BF38A9D5DDA5`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-082-targeted-PowerStoreTest.xml`, SHA-256 `5D51689A5B6C1C120422AE3A1617760B8CF67E6F794687587B6DC43CEA12AA66`.
- Full `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **434/434 tests across 56 suites; zero failures, errors, or skips; APK build successful; lint 0 errors / 58 warnings**. Log `.local/android-test/reports/freeze-20261024/pc-issue-082-full-gradle-20261024.log`, SHA-256 `1426EAAF0BBA6A4EABE55ADCA5FE543C8B23C10C00686B58361F12E89ECF90AA`. Full `PowerStoreTest` XML SHA-256 `3DDD799537652092CDE84A910E09853023A1239B1320DEF8E4F025A62EDB11CF`; lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256: `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`.

## Git and disposition

- Test-only commit `141750b42472ddcfe365242f7702ff4f3a717080` (`test(草稿): 验证改期不改变队列顺序`) is pushed to `origin/main`.
- `REC-P0-041` is signed PASS for the core queue-order invariant. API 35 editor presentation remains untested; overall core freeze remains **HOLD**.
