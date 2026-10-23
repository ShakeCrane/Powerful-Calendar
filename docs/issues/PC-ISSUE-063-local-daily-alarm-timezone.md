# PC-ISSUE-063 — Daily Alarm follows the current local timezone

Date: 2026-10-24

Severity: P0 candidate

Status: **CORE SCHEDULING AND SQLITE REGRESSION PASS / ANDROID DEVICE PATH NOT VERIFIED / OVERALL FREEZE HOLD**

## Finding

The product baseline says a local daily Alarm follows local wall-clock time by default. Before this fix, `RecurrenceEngine.nextOccurrence` projected every recurrence in the record's saved zone. A New York 07:30 daily Alarm therefore remained at 20:30 in Tokyo after a timezone change. `BootReceiver` responded to timezone changes by reconciling the existing absolute-time intent, without rebuilding it from the current timezone.

The pre-fix regression reproduced this: expected 07:30 in Tokyo, got 20:30. The test also preserves the original occurrence key across the timezone change, so completion and delivery identity stay stable.

## Change

- Daily Alarm `nextOccurrence` and `occurrenceForKey` use the current local zone for their effective instant; stable slot keys remain anchored to the record's saved zone. Other recurrence kinds retain their saved-zone behavior.
- Recovery rebuilds only daily Alarm schedule intents from the current system zone before `ReminderScheduler.reconcile` applies them. This changes the scheduler intent, not the formal event, its identity, or its revision.
- No UI, database schema, user database, or device state was changed.

## Verification

Environment: deterministic JVM `America/New_York` → `Asia/Tokyo` timezone change, Robolectric API 34, isolated SQLite. No Android device was used.

- Before fix: `TemporalProtocolTest.dailyAlarmFollowsCurrentLocalZoneWhileKeepingItsOriginalSlotKey` failed 1/1 with expected 07:30 and actual 20:30. Log: `.local/android-test/reports/freeze-20261024/pc-issue-063-daily-alarm-timezone-before-fix-20261024.log`, SHA-256 `123692BAB706622F2CD139F7679F3E1F987EEC63FD3A3E61E59FBF943900E897`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-063-before-fix-TemporalProtocolTest.xml`, SHA-256 `BA0141F69C23901BA90BF06A57DE68A806280FE3805E63368A6D9C414F34292B`.
- An intermediate broader projection change made `GlassPresentationTest.dstGeometryUsesElapsedTimeAndWallClockRecurrences` fail because that test intentionally uses the record's New York zone. I narrowed the change to scheduling and occurrence-key validation, leaving the general timeline projection unchanged. The intermediate full-run log is preserved at `.local/android-test/reports/freeze-20261024/pc-issue-063-full-gradle-20261024.log`, SHA-256 `D290420173C0089503A4F30B67317F43D294E6744BE6EEE35178E8A1AFD9A480`; its failing XML is `.local/android-test/reports/freeze-20261024/pc-issue-063-before-fixture-correction-GlassPresentationTest.xml`, SHA-256 `034B7BD6A3C93F1B33436CA445AF54B13FC6DD0F56D17F3D059D0025FA3716D5`.
- Final targeted tests: **2/2 PASS**. The recurrence regression confirms local 07:30, stable occurrence key, and key lookup; the PowerStore regression confirms the persisted intent moves to the new local instant without changing the event revision or creating another formal object. Log: `.local/android-test/reports/freeze-20261024/pc-issue-063-timezone-targeted-final-20261024.log`, SHA-256 `AE9E5738BDD4F85C596C54EDFA54472DB923129932A7F0C152D13D4EA624C8F8`. Targeted JUnit XML: `.local/android-test/reports/freeze-20261024/pc-issue-063-targeted-junit-final/TEST-dev.powerfulcalendar.core.TemporalProtocolTest.xml` SHA-256 `F8123AE7D02FB45A44E58EC87F87B6C2286CC4D6A588A40A65D5A7AC0A99E555`; `.local/android-test/reports/freeze-20261024/pc-issue-063-targeted-junit-final/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml` SHA-256 `30155EDCF14F8623024E3EA17136DD4E40B3787CB81AE04F93C6CB492D52DDF9`.
- Final full command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, **417/417** tests across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. This is 416 tracked tests plus the protected untracked `DraftDeckFeedbackTest.kt`, which was not modified or staged. Log: `.local/android-test/reports/freeze-20261024/pc-issue-063-full-gradle-final-20261024.log`, SHA-256 `1EC0BF04269D7FDF08E8927A507424FECD9BD78E125C12EA37C4047DC5007810`.
- Full JUnit XML hashes: `.local/android-test/reports/freeze-20261024/pc-issue-063-full-junit/TEST-dev.powerfulcalendar.core.TemporalProtocolTest.xml` SHA-256 `F79D15651FBA40AD6FAF17B1816194E139E72F87E73400ED507C56586B3D9C29`; `TEST-dev.powerfulcalendar.core.PowerStoreTest.xml` SHA-256 `6CE5C16317F9548DFF869BC2B85B1B596BAE67524D154734649DFA1FC1DFCBB1`; `TEST-dev.powerfulcalendar.ui.v2.GlassPresentationTest.xml` SHA-256 `E1E0DB32D0358E4B5E0F32D9B23707392E7918C3E647CC1480D812B99826BC0A`.
- Debug APK SHA-256: `BB09CBA105BECEE9EFC9950940AA407B3C1AEEEAC39EC00AFC093B9177B69F4D`.

`REC-P0-076` is PASS for the core scheduler/store contract and remains `UNMAPPED` to the absent historical checklist. Android `TIMEZONE_CHANGED` delivery, real `AlarmManager` rescheduling, OEM behavior, and physical-device behavior were not exercised. The overall freeze remains **HOLD**.

## Git

- Code/test commit: `f708dfcf076f122cb77f23bd6e5a1ca7f02001ee`
- Message: `fix(提醒): 时区切换重算每日闹钟`
- Status: pushed to `origin/main`.
- This independent report and synchronized freeze ledger are committed separately.
