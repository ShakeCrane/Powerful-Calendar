# Q-04 physical/OEM reminder sign-off — 2026-11-05

**Status: NOT TESTED — EXTERNAL DEVICE.** No authorized QA handset was connected. The connected target was the isolated API 35 emulator only, which cannot establish OEM alarm delivery or physical-device behavior. No physical-device test was run.

## Device inventory

`E:\android-sdk\platform-tools\adb.exe devices -l` listed only `emulator-5554` (`PowerCal_Wave2_QA35_20261104`, Android API 35, model `sdk_gphone64_x86_64`). There was no physical ADB device or second emulator. The internal RC remains available at `dist/powercal/PowerCal-Release-Candidate-2026-11-05.apk`; physical results were not inferred from it or the emulator.

## Safety preconditions for a future run

- Use a separately signed/installed QA application ID. Do not update, uninstall, clear, or restore over a person's ordinary PowerCal installation or calendar data.
- Use synthetic QA events and test account/provider data only. Do not import personal ICS or backup files.
- Record device model, manufacturer, Android version/build fingerprint, OEM ROM, WebView provider/version, timezone, locale, battery policy, APK SHA-256, and app package ID before actions.
- If a separate QA build is unavailable, stop before installation; do not use `dev.powerfulcalendar` to replace a user's app.

## Physical/OEM checklist

| Case | User action | Evidence required | Result |
| --- | --- | --- | --- |
| Install/upgrade | Install or upgrade only the isolated QA package; open Timeline, Month, Memo, AI and Settings | `pm path`, package/version, APK SHA, screenshots, no user package affected | NOT TESTED |
| Permission grant/denial | Request notifications; grant and deny on separate synthetic runs; inspect exact-alarm status and system Settings return | System dialog/result, visible Skin status, Host callback, unchanged Core snapshot for permission-only actions | NOT TESTED |
| Alarm delivery | Create a near-future synthetic ALARM, verify scheduling/permission, lock screen and wait for delivery | Scheduled intent/receipt, notification timestamp/content, device time and battery state | NOT TESTED |
| Reboot/process death | Force-stop or reboot, then reopen and inspect alarm/draft/event state | Boot/process identity, no duplicate event/receipt, restored draft and scheduled state | NOT TESTED |
| OEM background policy | Repeat under default and OEM-restricted battery modes; document any required user setting | OEM policy screenshot, exact delivery result and delay, no unsupported PASS extrapolation | NOT TESTED |
| Time changes | Change timezone and clock using synthetic test data, then restore original settings | Before/after zone/clock, reminder intent, local-date and recurrence results | NOT TESTED |
| Display/accessibility | Portrait/small display, 2× font, reduced motion, TalkBack primary navigation | Device dimensions, font scale, focus traversal observation and screenshots | NOT TESTED |
| Recovery | Inject only QA-owned corrupt/missing skin package and enter Native recovery | Visible Native recovery, package/selection state, unchanged Core snapshot | NOT TESTED |

Keep each result separate by device/ROM and retain raw logs/screenshots under `.local/android-test/reports/Q-04-<date>/`. Do not sign off physical/OEM reminder delivery from API 35 emulator evidence.
