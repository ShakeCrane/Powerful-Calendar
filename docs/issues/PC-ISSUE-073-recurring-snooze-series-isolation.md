# PC-ISSUE-073 — Snooze one recurring occurrence without changing its series

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-119`)

Status: **CORE SNOOZE/SERIES ISOLATION PASS / ANDROID ALARMMANAGER DELIVERY NOT TESTED / OVERALL FREEZE HOLD**

## Finding

The product baseline says snooze changes occurrence execution, not the recurrence series. The reconstructed candidate had no one-to-one sign-off. An existing receiver/SQLite regression directly exercises this contract, so no product defect or code change was needed for this candidate.

## Evidence

`ReminderCompletionIsolationTest.snoozedRecurringOccurrenceFiresWithoutChangingItsSeries` creates a tracked daily Alarm, records the projected occurrences, handles the snooze action and its delayed `ACTION_FIRE`, then verifies:

- the formal event revision and recurrence rule are unchanged;
- completion keys are unchanged;
- the projected series is identical before and after snooze;
- the execution is recorded as `FIRED` and the notification identifies the delayed snooze.

Environment: Robolectric API 34 with an isolated SQLite database. The test uses synthetic receiver intents; it does not exercise Android `AlarmManager` delivery, a real notification action, an OEM alarm service, or a physical device. No production, test, UI, business-data, or design-asset file changed for this case sign-off.

## Verification

- Focused test `ReminderCompletionIsolationTest.snoozedRecurringOccurrenceFiresWithoutChangingItsSeries`: **1/1 PASS**, zero failures/errors/skips. Log `.local/android-test/reports/freeze-20261024/pc-issue-073-recurring-snooze-targeted-20261024.log`, SHA-256 `56043D7990B6FD9525C0E78F929FACFB349BC229EDFCB7A03EE34755247832BB`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-073-targeted-junit/TEST-dev.powerfulcalendar.reminders.ReminderCompletionIsolationTest.xml`, SHA-256 `4F601F5DDDF44F90B118F9892428D98316AC5CB0F2D1CA097BEC71895334ED91`.
- The latest full Gradle baseline is unchanged at code/test commit `2e74007c70fc0adccd14fdb42b6d491c58335ee6`: **425/425** tests across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. Its full `ReminderCompletionIsolationTest` XML contains this test as passing; XML SHA-256 `B82C6A8FE584CB8E58A76ECFB8970AA9134054C0CA39EC548E49ACF8D625D997`. Full log `.local/android-test/reports/freeze-20261024/pc-issue-072-full-gradle-20261024.log`, SHA-256 `686B5751FFDB8934A261BA2333D74F6D95780415497F939EFC21B10AFE02420A`.
- Debug APK for the unchanged code/test baseline SHA-256: `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`.

`REC-P0-119` is PASS for the core occurrence/series isolation contract. The candidate remains `UNMAPPED` to the absent historical checklist; actual Android alarm delivery remains unverified, and overall freeze remains **HOLD**.

## Git

- No source or test commit was required; the evidence is from the already pushed code/test baseline `2e74007c70fc0adccd14fdb42b6d491c58335ee6`.
- This report and the synchronized ledger/verdict updates are committed separately.
