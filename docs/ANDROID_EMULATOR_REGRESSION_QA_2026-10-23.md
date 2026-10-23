# PowerCal 0.9.5-rc.1 · Android Emulator Regression QA / Round 2

Date: 2026-10-23
Baseline APK: `PowerCal-0.9.5-rc.1-debug.apk`, versionCode `905`
Application source baseline: `378fc1a` (test harness/docs committed subsequently)
Device: repository-local `PowerCal_API35` / Android 15 (API 35), Google APIs x86_64, emulator-5554, WHPX
Status: **FREEZE HOLD** (partial actual UI acceptance, not full 209-case sign-off).

## 1. Tested environment and isolation

All device and report work uses the emulator only, never an attached physical device. ADB actions target `-s emulator-5554`. Android system files, AVD, local captures, JSON and ICS specimens live under `.local/android-test/` (Git ignored). Repository helper scripts: `tools/android-emulator/`. Existing untracked `design/` was not touched. No production app source or APK was modified during Round 2.

Prior Round 1: `docs/ANDROID_EMULATOR_SMOKE_QA_2026-10-23.md`; baseline creation, Activity direct editing, reboot persistence and first-run UI are *not* included in the new Round 2 count.

## 2. Confirmed-pass business chains

The following 11 grouped checks represent tested paths; they are not 11 of 209 individually ID-mapped test cases.

| ID | Operation / acceptance | Result | Reproduction facts |
|---|---|---|---|
| R2-01 | Memo completion + undo | PASS | Check `PC_EMU_MEMO_01` removes it from incomplete; it appears in completed; uncheck removes it from completed; it returns to incomplete |
| R2-02 | Alarm create/publish/render | PASS | New Alarm `PC_EMU_ALARM_03`, committed; displayed on left axis at 12:00 as #0003 |
| R2-03 | Date-precision Deadline create/publish | PASS (input/publish) | `PC_EMU_DEADLINE_04` created with `2026-10-22` date-only form; after publish, timeline shows deadline label; edit still offers date-only control. Visible timeline endpoint may be represented at 23:59; do not confuse this visual representation with backing precision |
| R2-04 | Delete formal event / confirmation / restart | PASS | #0004 delete confirmation shown; cancel retains edit/event; confirm removes it; force-stop and restart do not show it again |
| R2-05 | Dirty draft exit checkpoint | PASS | New `PC_EMU_STAGE_05` edited, exit deck; pending badge goes to 1 |
| R2-06 | Draft persists across process restart | PASS | Force-stop/restart; pending badge still 1; reopened draft retains exact title |
| R2-07 | Draft discard, undo, discard again | PASS | Discard → deck 0; Undo restores same title; discard again → 0; force-stop/restart pending remains 0 |
| R2-08 | Daily recurrence and isolated single instance | PASS | Daily Activity `PC_EMU_REPEAT_06` created; month view next day `2026-10-23` contains it; nonrepeating Alarm does not appear as a next-day occurrence |
| R2-09 | Native Envelope v1 export | PASS | Android system SAF writes `powercal-backup.json`, app reports re-read SHA-256 verification; JSON envelope parses; 12827 bytes |
| R2-10 | Native backup restore and state verification | PASS | Nonempty-store restore rejected; clear *emulator app data only* after preserving backup; restore into empty database succeeds, message: 5 object records, 6 draft-history records, 9 commit records. Month/day and Memo validated. Deleted Deadline absent; next new draft sequence #0007, proving watermark retention |
| R2-11 | Blank mandatory title validation | PASS | New Activity with empty title: publish control was disabled and no formal record committed; invalid test draft discarded |

Data safety evidence:

- Local backup: `.local/android-test/reports/regression-20261023/powercal-backup.json`
- JSON includes `format`, `backupFormatVersion`, `databaseSchemaVersion`, `appVersionCode`, `storeLineageId`, `payloadSha256`, `payload`.
- Backup payload has 6 `identities`, 5 `objects`, 6 `drafts`, 9 `commits`; an event object for deleted Deadline #0004 is explicitly `retired: 1`; discarded draft #0005 has `status: DISCARDED`; neither is active.
- In Restore UI, app reported exactly 5 event objects, 6 drafts, 9 commit records; the restored day `2026-10-22` showed Activity `PC_EMU_UI_TEST_01_EDITED`, Alarm `PC_EMU_ALARM_03`, Daily Activity `PC_EMU_REPEAT_06`. Incomplete Memo view showed `PC_EMU_MEMO_01`.
- The testing app's shared Downloads backup remained intact during `pm clear dev.powerfulcalendar`; no device-wide wipe or physical phone reset occurred.
- `#0007` created after restore and discarded during water-mark test. Later another blank Activity `#0008` was used to test required-title validation and also discarded. These are test-only changes after the saved backup specimen.

## 3. Confirmed defects / issues

### R2-ISSUE-01 · ICS export drops recurring Activity rule (P1 / freeze review)

Observed: `PowerfulCalendar-activities.ics` saved successfully, syntactically contains `BEGIN:VCALENDAR` and `END:VCALENDAR`; exactly **2 VEVENTs** — `PC_EMU_UI_TEST_01_EDITED` and `PC_EMU_REPEAT_06`. Zero `RRULE` lines, even though `PC_EMU_REPEAT_06` is a daily series. Alarm, Memo and Deadline are intentionally excluded by the product's explicit Activity-only export contract, so their absence is *not* counted as a bug. But the recurring Activity is flattened to a single occurrence without a recurrence signal.

Evidence: `.local/android-test/reports/regression-20261023/PowerfulCalendar-activities.ics`; implementation `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt` `exportIcs` and `interop/IcsCodec.kt` `export()`. Current converter maps `TemporalRecord` to `CalendarEvent` without preserving recurrence; exporter emits `VEVENT` without `RRULE`.

Required decision: preserve recurrence in exported ICS when supported, or explicitly classify and warn that ICS export loses series semantics. Do **not** promote this export path as semantically lossless. Native Envelope v1 backup is a distinct, more complete contract.

### R2-ISSUE-02 · Restore refusal UI exposes exception class only (P2 UX)

Observed: selecting the verified existing backup in a **nonempty** store is correctly rejected before overwriting user data, but the visible message is only `恢复失败：IllegalArgumentException`, without a useful explanation. Source `PowerStore.restoreBackup()` carries the detailed `目标数据库不是空库...` message; outer UI `error.readable()` loses specificity.

Expected: tell the user restore is refused because the target database is nonempty and provide safe backup/recovery guidance. Keep rejection semantics unchanged.

### R2-OBS-01 · Commit-result display latency / testing race (needs reproduce)

Some UI snapshots immediately after tapping draft Publish still showed an old card, but a later visual inspection showed `已提交 1 项` and formal records existed. Do not classify as a functional failure without bounded-await, clickable-enabled state and stable postcondition tests. Record latency if reproducible under a deterministic script.

## 4. Not yet tested / cannot be marked PASS

- Full 209-case acceptance, physical-device OEM/permissions and different API levels.
- Actual notification scheduling/delivery (notification/exact alarm permission, Doze, timezone updates and alarms at wall-clock).
- Complex recurrence (weekly/monthly, exclusions, occurrence completion and conflict isolation), DST and multi-day boundary tests.
- Live AI connector: model calls, chat history, OCR, attachment imports and proposed batch-authorization paths; these were not configured/exercised.
- Malformed/tampered Envelope, backup lineage, legacy v2 consent/normalization, restore crash mid-transaction, concurrent writes and update/rollback migrations.
- ICS imported and re-expanded by independent calendar clients; recurrence loss documented above.
- Skin export/import, Clear WebView parity, third-party `.pcskin` equivalence, Studio full behavior, skin switching during dirty edits, removal of native UI/factory skin.
- Accessibility, focus/keyboard detail, screenshots at small devices, landscape, performance and prolonged running.

## 5. Freeze decision

**HOLD.** Reliable emulator validation now covers many foundational data-safety and event workflows and supports continuing toward separation, but the freeze gate is not met. A P1 semantic ICS-loss item needs a product decision/repair, a P2 restore-error message should be improved, and the remaining tests need explicit completion. Keep native baseline as a reference; do not delete it or declare skin-as-presentation parity prematurely.

Next recommended test batch: backup tampering/hash preflight and legacy handling (with independent disposable emulator data), reminder delivery under permission permutations, recurrence editing/completion, and AI proposal authorization. Prioritize evidence-based bugs before cosmetic changes.

## 6. Test-reproduction details

- Emulator `emulator-5554` remains installed as `PowerCal_API35` in the repo-local ignored directory. Avoid `-wipe-data` unless evidence backed up.
- Derived UI observations were checked through Android `uiautomator dump` and screenshots, and backend backup/ICS specimen files pulled via ADB. Some tests were one-off, deliberately bounded ADB scripts and have not yet been consolidated into a fully unattended instrumented suite.
- Scripts for boot/installation/smoke: `tools/android-emulator/`.
