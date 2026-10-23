# PC-ISSUE-081 — Open the oldest draft first

Date: 2026-10-24

Candidate: `REC-P0-037` (reconstructed P0; historical mapping remains `UNMAPPED`)

Status: **CORE CONTROLLER PASS / API 35 display layer NOT TESTED / overall freeze HOLD**

## Finding

The queue contract orders actionable drafts by their durable `queueOrder`, independently of event identity and timestamps. The existing store query orders pending cards by `queue_order ASC`; the missing evidence was that opening the controller after a database reopen preserves that order and initially focuses the oldest card.

## Change and evidence

Added `PowerStoreTest.openingDeckAfterStoreReopenFocusesOldestQueueOrder`. It creates two draft cards, closes and reopens the same SQLite database, then opens a `DeckController` backed by `StoreDeckPersistence`. The regression asserts the controller card list follows the two persisted queue orders and `currentId` points to the oldest draft.

This is a core PowerStore/DeckController test under Robolectric API 34. It does not exercise Compose rendering or an API 35 device restart. No production code changed.

## Verification

- Focused `:app:testDebugUnitTest --tests dev.powerfulcalendar.core.PowerStoreTest.openingDeckAfterStoreReopenFocusesOldestQueueOrder --offline --rerun-tasks --console=plain`: **1/1 PASS**. Log `.local/android-test/reports/freeze-20261024/pc-issue-081-oldest-queue-targeted-20261024.log`, SHA-256 `56043D7990B6FD9525C0E78F929FACFB349BC229EDFCB7A03EE34755247832BB`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-081-targeted-PowerStoreTest.xml`, SHA-256 `E432A0B1CCFBDC810025652E29FBA39C6F00A7D4D8DEF1D86BDAD99D6C3FF4FB`.
- Full `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **433/433 tests across 56 suites; zero failures, errors, or skips; APK build successful; lint 0 errors / 58 warnings**. Log `.local/android-test/reports/freeze-20261024/pc-issue-081-full-gradle-20261024.log`, SHA-256 `435E1B57D7D42F83ED7D7C6DB932561ED5517C9020FB9CD7EA288D710C0295CA`. Full `PowerStoreTest` XML SHA-256 `3874DD67D69DF91537B35AD6F354C4C22BB5D1C059E45A0364F77F4604D14F16`; lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256: `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`.

## Git and disposition

- Test-only commit `2ff7bb50eb1c35e978317c6428b04e6363398d27` (`test(草稿): 验证重开后最早队列项先打开`) is pushed to `origin/main`.
- `REC-P0-037` is signed PASS for the core controller ordering and focus contract. The API 35 display layer remains outside this sign-off; overall core freeze remains **HOLD**.
