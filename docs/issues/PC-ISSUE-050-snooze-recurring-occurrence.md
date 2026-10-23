# PC-ISSUE-050 — Delayed snooze delivery for a recurring occurrence

Date: 2026-10-23

Severity: P0 candidate

Status: **REMINDER RECEIVER REGRESSION PASS / OVERALL FREEZE HOLD**

## Finding

The snooze button schedules a delayed intent with `ACTION_FIRE` and `EXTRA_SNOOZE=true`. `ReminderReceiver` only distinguished requests by action and validated that delayed intent against the occurrence's original scheduled instant. The delayed snooze therefore became `IGNORED_STALE` and never fired.

The regression reproduces the real scheduled intent contract in Robolectric API 34. Before the fix, the focused test expected `FIRED` but received `IGNORED_STALE`.

## Change

- `ReminderReceiver` now handles a user snooze-button request (`ACTION_SNOOZE`) separately from the scheduled snooze delivery (`ACTION_FIRE` with the snooze extra).
- Scheduled snooze delivery validates through the snooze path, which does not require the delayed instant to equal the original occurrence time, and marks the notification as snoozed.
- `AlarmContract` documents the distinction. `ReminderCompletionIsolationTest.snoozedRecurringOccurrenceFiresWithoutChangingItsSeries` covers the receiver flow.
- No UI, recurrence definition, formal event time, business data, or protected asset was changed.

## Verification evidence

Environment: Robolectric API 34, isolated PowerStore SQLite fixture and `ReminderReceiver`; no physical Android device.

- Pre-fix targeted regression: **1 test failed as expected** (`expected FIRED, got IGNORED_STALE`). Log `.local/android-test/reports/freeze-20261023/pc-issue-050-snooze-recurring-prefixed-20261023.log`, SHA-256 `46CFAF3F243123ACA7CEAA3FE16F30FAEDD64CF9B9F87346D3A0F4A87797DEF8`; JUnit XML SHA-256 `DC5832B6028D4B4807712791A370509629C4901CDB8E3338A1125592E27B8225`.
- Post-fix targeted regression: **1/1 PASS**, zero failures/errors/skips. Log `.local/android-test/reports/freeze-20261023/pc-issue-050-snooze-recurring-postfix-targeted-20261023.log`, SHA-256 `AA47B7E69FFD12D50B2EA20E01414CFBD8F0C9BB7C66AB44884B583FDEF3D269`; JUnit XML SHA-256 `1CBCF7EE4E9A176AB377E7BCC5B97063D2A2103266C370AE7D0077D7F632BF2C`.
- The test schedules a snooze for one daily recurring occurrence and delivers the delayed snooze intent. It verifies the occurrence is recorded as `FIRED`, the notification is marked snoozed, and the recurrence, formal revision, completion keys, and projected series remain unchanged.
- Full command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 408/408 tests across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. This run included 407 tracked tests and one protected untracked test; that protected test was not modified or staged.
- Full log `.local/android-test/reports/freeze-20261023/pc-issue-050-snooze-recurring-full-gradle-20261023.log`, SHA-256 `D74F6F0C70E4E9C7A1CC7FD3AEE8453CF5E79883601CDE342BFF6E94A57354BD`. The 55 JUnit XML files are preserved under `.local/android-test/reports/freeze-20261023/pc-issue-050-full-junit/`; the full `ReminderCompletionIsolationTest` XML SHA-256 is `55D5FE8AF988FCF3497DAD78B774A659472AA18DC97B577A2EF033D924588EB5`.
- Gradle summary `.local/android-test/reports/freeze-20261023/pc-issue-050-full-gradle-summary.json`, SHA-256 `75135DB1889750D0C541A5CC9693991B315F289A7669341DC599490F4B608DFE`. Debug APK SHA-256: `460DE2CEE71D00996AFED280AC751B16337CDC129D2273A4A8E61C09C986A13C`.

## Candidate sign-off and limits

`REC-P0-069` is PASS in the reconstructed ledger for the tested single-occurrence snooze isolation and delayed receiver path. The row remains `UNMAPPED` to the unavailable original 209-case checklist.

This Robolectric test does not exercise Android's actual `AlarmManager` scheduling/delivery, exact-alarm permission behavior, notification permission/channel display, Doze, reboot recovery, OEM policies, or a physical device. Those remain open acceptance evidence; overall core freeze remains **HOLD**.

## Git

The code/test change was committed as `f45710a6759fc5532f3982721345c06a912afa3e` (`fix(提醒): 正确处理延迟稍后提醒广播`) and pushed to `origin/main`. The synchronized ledger and freeze-document update is committed separately so this issue has a standalone, reviewable record.

Structural-only ledger validation: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-050.json`, SHA-256 `990AFBE9036276ADFA60F183FF8928D410CB7A5BF12B1D5D3E5C39F906C110A8`; CSV SHA-256 `082A55CE541EDF3CE39D3B35AF312689179AF89046310BD8FECE565194665396`. It confirms 209 unique candidate IDs, the 126/82/1 priority partition, 95/0/9/98/7 status counts, and the `REC-P0-069` PASS row. This validation checks ledger structure and counts only; `product_behavior_tested` is false.
