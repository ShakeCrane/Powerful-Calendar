# PowerCal Android 15 Emulator Smoke QA · 2026-10-23

## Baseline and scope

- Product artifact: `PowerCal-0.9.5-rc.1-debug.apk`, Android versionName `0.9.5-rc.1`, versionCode `905`
- Source baseline: `378fc1a`
- Emulator: Android 15 (API 35), Google APIs x86_64, Pixel-like 1080x2400 (420 dpi), Windows Hypervisor Platform / WHPX, Android Emulator 37.2.12
- Test AVD: `PowerCal_API35`, serial `emulator-5554`
- All locally controlled emulator/download/device data: `/.local/android-test/` (Git ignored)
- Collected run evidence: `.local/android-test/reports/smoke-20261023-025053/`
- This is a **real emulator smoke/interaction pass**. It does NOT certify 209-item release-freeze acceptance, physical-device compatibility or skin separation.
- Native UI only; no AI API configured or network-assisted model calls issued.

## Confirmed PASS on emulator

| Area | Tested action | Evidence |
|---|---|---|
| Infrastructure | Official Emulator ZIP and Android 35 system-image ZIP retrieved, sizes and Google repository SHA-1 verified | `tools/android-emulator/Download-OfficialArchives.ps1` output |
| Acceleration | WHPX available and used by x86_64 emulator | emulator stdout |
| Startup | Debug APK installed, initial launch alive | `initial.png`, `initial.xml`, `result.json` |
| Restart | `am force-stop` then relaunch works | `restarted.png`, `restarted.xml`, `result.json` |
| Onboarding | First-time system-calendar-import prompt displayed, `暂不导入` used | `restarted.png`, `home.png` |
| Timeline | Native 24-hour timeline visible; persistent vertical grey centre divider absent; hour ticks and current-time marker remain | `home.png` |
| Draft creation | Timeline `+` → Activity opens a full expanded editor with start/end pickers and reminder settings | `draft-edit.png`, `draft-edit.xml` |
| Draft publish | Enter `PC_EMU_UI_TEST_01`, submit draft: deck reports one committed | `post-publish.xml` |
| Timeline consistency | Published formal event #0001 appears for 12:00–13:00 | `event-visible.xml` |
| Formal editing | Tap event opens full edit form including matching shared fields, pickers and reminders | `formal-edit.png`, `formal-edit.xml` |
| Persistence | Rename to `PC_EMU_UI_TEST_01_EDITED`, save, close; the renamed title remains on the timeline after force-stop/relaunch | `edited-visible.xml`, `edited-restarted.xml` |
| Navigation | Month page, AI Assistant page, Settings page, Timeline page all open and expose populated UI trees | `month.xml`, `ai.xml`, `settings.xml`, `timeline.xml` |
| Memo | Memo centre opens; create undated Memo `PC_EMU_MEMO_01`, publish, exit; memo centre displays one item with the title | `memo-publish-retry.xml`, `memo-list.xml` |

## Observations needing reproduction

- Undated Memo: after entering the title, the first publish tap did not immediately produce a completion state in the snapshot; a subsequent publish tap produced `已提交 1 项` and the Memo later appeared in the inbox. The available evidence does **not** establish whether this was input focus/IME, touch timing, UI refresh, or a product logic failure. Re-test with deterministic view/IME waits and request logs before assigning severity.
- The screenshot confirms the long **vertical** grey centre divider has been removed. Thin **horizontal** day separators and the blue current-time marker are intentionally still visible; these are different features.
- The formal editing screenshot confirms shared field controls, but does not certify every event type, recurrence configuration, keyboard mode, or delete/conflict flow.

## Not tested; must not be marked PASS

- 209-item full manual freeze checklist and the separate future skin-extraction checklist
- System Calendar/ICS real imports, archive backup/restore, schema upgrade/rollback and destructive recovery
- AI model connector, multi-agent/chat history, file/image attachments, AI proposal authorization and batch conflict reconciliation
- Reminder delivery at actual wall-clock times, exact-alarm permission paths, notification-channel/Doze behavior
- Alarm, Deadline, recurring occurrence instances, memo completion/undo, advanced recurrence and DST
- Cutover to factory `.pcskin`, WebView editing with Clear, Studio import/export, third-party package equivalence
- API 26–34 and API 36 platform variants; real-device OEM behavior, gesture feel, performance, accessibility and long soak testing
- Concurrent editing, interrupted background work, repeated publish idempotence and process-death during a dirty edit

## Freeze recommendation

**HOLD.** This pass validates a useful cross-screen activity and memo smoke path, not a feature-complete frozen baseline. Continue the full version freeze test matrix in isolated emulator data, then physical-device critical acceptance before default-skin separation.

## Reproducing

Run from `E:\Project\Powerful-Calendar`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/android-emulator/Download-OfficialArchives.ps1
# Once verified ZIP archives have been extracted into .local/android-test/sdk:
powershell -NoProfile -ExecutionPolicy Bypass -File tools/android-emulator/Start-PowerCalAvd.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/android-emulator/Smoke-PowerCal.ps1 -Adb ".\.local\android-test\sdk\platform-tools\adb.exe"
```

The installer/start scripts are reproducibility helpers, not a claim that the full manual interaction matrix is automated. Do not wipe the existing test AVD if its retained evidence or data is needed. Avoid device-wide ADB commands without explicit `-s emulator-5554` targeting.
