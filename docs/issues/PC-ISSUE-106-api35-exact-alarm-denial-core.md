# PC-ISSUE-106 — API 35 精确闹钟权限拒绝的核心状态

日期：2026-10-24

候选：`REC-P0-112`（重建 P0；历史映射仍为 `UNMAPPED`）

状态：**API 35 Robolectric 核心分支通过；候选仍 NOT TESTED；整体冻结 HOLD**

## 检查范围与结果

`ReminderValidationTest.api35ExactAlarmDenialIsDurablyBlockedWithoutScheduling` 使用 Robolectric API 35，并由 `ShadowAlarmManager` 模拟精确闹钟权限被拒绝。测试将 Activity 提醒改为 `EXACT` 并提交，然后执行 `ReminderScheduler.reconcile`，确认：

- 持久调度意图转为 `BLOCKED`。
- 意图保留“精确闹钟权限”原因，供后续处理。
- `ShadowAlarmManager` 没有精确或近似的闹钟注册。

本次只增加核心状态测试，没有修改生产代码或 UI。检查时系统未安装 `adb`，所以没有实际 API 35 Android 模拟器或设备可供权限授予/撤销验证。该 Robolectric 结果不替代真实 Android 的权限行为，`REC-P0-112` 继续保持 **NOT TESTED**。

## 测试证据

- API 35 单项定向：**1/1 PASS**，0 failures/errors/skips。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.reminders.ReminderValidationTest.api35ExactAlarmDenialIsDurablyBlockedWithoutScheduling --offline --console=plain`。日志 `.local/android-test/reports/freeze-20261024/pc-issue-106-api35-targeted-final.log` SHA-256 `DE4AF4E3EE7C419A5C00D15712F4551E8E433BC11DE6D6414F9B571EE3D28D61`；JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-106-api35-targeted-junit/TEST-dev.powerfulcalendar.reminders.ReminderValidationTest.xml` SHA-256 `D274388121E59BDD56EB63ADC2E97718BDA93E2F5CCC701AE03404AB2CE57E42`。
- `ReminderValidationTest` 全类：**17/17 PASS**，日志 SHA-256 `D3AA4953753B65686348427422B4CB1E46A743A1CDB33689CA474134E8090790`；JUnit XML SHA-256 `65C70279B9BB181E3BD0A89CAF0B375ACC715DA65C832426278F411E9EDFF0DE`。
- 完整 Debug 回归：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`，**BUILD SUCCESSFUL**；460/460 tests，57 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。日志 SHA-256 `B14E35666D4C5B2159CCAFEFE12C6244C803B864E23172A266DC736A02BBD15B`；全量 `ReminderValidationTest` XML SHA-256 `55E5FE00430F37BB25B41D8A858CE3F594241DD45C7532CF97D0D3490E8DA921`；Lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256 `138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 完整 JUnit XML 保存在 `.local/android-test/reports/freeze-20261024/pc-issue-106-full-junit/`。

## 台账与 Git

- `REC-P0-112` 记录了本次 API 35 Robolectric 核心证据，状态仍为 **NOT TESTED**；实际 Android 精确闹钟权限变化与设备行为未覆盖。
- 结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-106.json`，SHA-256 `BA926DB3211EF7D46F5700D4228A9577B58C4A2A8C79BC848D434495D9F72965`；台账 SHA-256 `C7912597DC9DECBBB21D5A4FB50CB8EF5665E6FA91F7A05BBBC8240AE03B2C59`。校验只确认 209 行、唯一 ID 与 126/82/1 优先级分区；`product_behavior_tested=false`。
- 测试提交：`f30c1ce5c6e6c6e835365f751dc689ab245b5aca`（`test(冻结): 覆盖 API 35 精确闹钟阻断`），已推送。只包含一条核心 Robolectric 测试。
- 冻结统计不变：135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED。缺少 adb/真实设备证据，整体冻结继续 **HOLD**。
