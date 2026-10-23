# PC-ISSUE-077 — Reject a stale reminder broadcast after event revision

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-116`)

Status: **CORE RECEIVER PASS / API 35 ALARMMANAGER DELIVERY AND PHYSICAL DEVICE NOT TESTED / OVERALL FREEZE HOLD**

## Finding

`ReminderScheduler.validate()` already rejects a reminder whose revision no longer matches the authoritative event. The existing regression exercised that predicate, but the candidate did not have issue-level evidence that a stale `ACTION_FIRE` broadcast passed through `ReminderReceiver` without producing a notification. This was a receiver-path coverage gap; no production defect was found.

## Change and evidence

Added `ReminderCompletionIsolationTest.oldRevisionBroadcastIsRejectedBeforeNotification`. The test creates a tracked Alarm, retains its original schedule identity, commits a replacement at a newer revision, and delivers a synthetic `ACTION_FIRE` carrying the old revision, occurrence key, trigger ID, and instant. It verifies:

- the receiver records `IGNORED_STALE`;
- no active notification exists for the event;
- the formal event retains the new revision and replacement title.

The test drives `ReminderReceiver.handle()` against an isolated SQLite database under Robolectric API 34. It does not invoke Android's real `BroadcastReceiver.onReceive()` lifecycle, `AlarmManager` delivery, ringtone/audio output, or an OEM device.

## Verification

- Focused `:app:testDebugUnitTest --tests dev.powerfulcalendar.reminders.ReminderCompletionIsolationTest.oldRevisionBroadcastIsRejectedBeforeNotification --offline --rerun-tasks --console=plain`: **1/1 PASS**. Log `.local/android-test/reports/freeze-20261024/pc-issue-077-stale-revision-targeted-20261024.log`, SHA-256 `8599A5B267A70A771D667FD75828DBA5A366C34BD9293C68BE9729456031A3DF`; focused JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-077-targeted-ReminderCompletionIsolationTest.xml`, SHA-256 `64083AFB262A402CB8E8D858F67E5527CD3751EB400F2F980E6967E423CF267B`.
- Full `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **429/429 tests across 56 suites; zero failures, errors, or skips; APK build successful; lint 0 errors / 58 warnings**. Log `.local/android-test/reports/freeze-20261024/pc-issue-077-full-gradle-20261024.log`, SHA-256 `B3BE49D78F18375D52140ED6E446218FAD61735C8450440A7C42454CD008E3CF`. Full `ReminderCompletionIsolationTest` XML SHA-256 `8B76687057F165C01EA9AE3BD84E13972E4C331B2899DC16BCBC657170F7063F`; lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256: `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`.

## Git

- Test-only commit `afc99cb71acc13c7e75df6fff94996db52b387ac` (`test(提醒): 拒绝旧修订提醒广播`) is pushed to `origin/main`.

`REC-P0-116` is signed PASS for the tested core receiver boundary. Android `AlarmManager` delivery, actual audio behavior, API 35 device behavior, and physical/OEM delivery remain untested; overall freeze remains **HOLD**.
