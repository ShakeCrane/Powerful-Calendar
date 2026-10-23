# PC-ISSUE-072 — Reject stale alarm trigger identities

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-115`)

Status: **CORE RECEIVER VALIDATION PASS / ANDROID PENDINGINTENT DELIVERY AND PHYSICAL DEVICE NOT TESTED / OVERALL FREEZE HOLD**

## Finding

The contract requires a delivered alarm to match the current event revision, occurrence, and trigger identity before any audible side effect. `ReminderReceiver` extracted `triggerId`, but `ReminderScheduler.validate` did not accept or compare it. A missing trigger ID was also defaulted to `primary`, allowing a malformed or stale broadcast to impersonate the current trigger.

The regression reproduced the defect before the fix: a synthetic `ACTION_FIRE` with the correct event, revision, occurrence, and instant but `triggerId=retired-trigger` was recorded as `FIRED` instead of `IGNORED_STALE`.

## Change

- Added `ReminderPolicy.PRIMARY_TRIGGER_ID` as the canonical current trigger identity and used it when PowerStore creates and looks up the schedule intent.
- `ReminderReceiver` now forwards the broadcast's actual trigger ID to validation; a missing ID stays empty instead of being promoted to `primary`.
- `ReminderScheduler.validate` rejects any trigger identity other than the current model's `primary` before notification or scheduling side effects.
- Added receiver regressions for an unknown and a missing trigger ID. Both assert `IGNORED_STALE`, no reminder notification, and unchanged formal event revision/title.
- No timeline/UI code, business data, or design assets changed.

## Verification

Environment: Robolectric API 34 with an isolated SQLite fixture and synthetic receiver `ACTION_FIRE` intents. The tests exercise receiver validation and notification side effects, but do not deliver an OS-created Android `PendingIntent` or use a physical device.

- Before the fix, `ReminderCompletionIsolationTest.fireWithDifferentTriggerIdentityIsRejectedBeforeNotification` failed as expected: `expected IGNORED_STALE but was FIRED`. Red log `.local/android-test/reports/freeze-20261024/pc-issue-072-stale-trigger-identity-red-20261024.log`, SHA-256 `837EAE34318BE812E9EB8BBABBB7F8CB5C90EB3EF1E49436706B022EC8B93352`.
- Focused `ReminderCompletionIsolationTest` + `ReminderValidationTest`: **15/15 PASS**, zero failures/errors/skips. This includes revision, occurrence-key, trigger-instant, unknown-trigger, and missing-trigger validation. Log `.local/android-test/reports/freeze-20261024/pc-issue-072-stale-trigger-identity-targeted-20261024.log`, SHA-256 `627331652F012346A7B7AF45908B25D13913412C31531296AF1E5B5C1B2223A3`. Targeted XML: `ReminderCompletionIsolationTest` SHA-256 `72BE1CFEC8E617B78A75F264ABEE9C30E3C769CEA09BA3AC9D8009B1731F0FA9`; `ReminderValidationTest` SHA-256 `82FB0B0C9809C5C712FAF7D103BA544F84C71602A40146EF56B947A7FEB25825`.
- Full command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **BUILD SUCCESSFUL**, **425/425** tests across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. The run includes 424 tracked tests and one protected untracked test; that protected file was not modified or staged. Log `.local/android-test/reports/freeze-20261024/pc-issue-072-full-gradle-20261024.log`, SHA-256 `686B5751FFDB8934A261BA2333D74F6D95780415497F939EFC21B10AFE02420A`. Full XML: `ReminderCompletionIsolationTest` SHA-256 `B82C6A8FE584CB8E58A76ECFB8970AA9134054C0CA39EC548E49ACF8D625D997`; `ReminderValidationTest` SHA-256 `9D7CF52324194653EBB94527384E7D1293AB1CA4B74D107981D1D2B1D0B5877A`.
- Debug APK SHA-256: `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`.

`REC-P0-115` is PASS for the core receiver validation contract. Actual Android `AlarmManager`/`PendingIntent` delivery, API 35 emulator behavior, OEM behavior, and physical-device ringing remain unverified. The candidate remains `UNMAPPED` to the absent historical checklist; overall freeze remains **HOLD**.

## Git

- Code/test commit `2e74007c70fc0adccd14fdb42b6d491c58335ee6` (`fix(提醒): 校验广播触发器身份`) is pushed to `origin/main`.
- This report and synchronized ledger/verdict updates are committed separately.
