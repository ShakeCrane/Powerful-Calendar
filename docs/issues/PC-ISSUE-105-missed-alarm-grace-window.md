# PC-ISSUE-105 — 提醒宽限期与错过恢复分支

日期：2026-10-24

候选：`REC-P0-118`（重建 P0；历史映射仍为 `UNMAPPED`）

状态：**当前实现分支回归通过；宽限时长规格仍未签核；整体冻结 HOLD**

## 检查范围与结果

本 Issue 补齐提醒接收器和恢复调度器的核心回归，不涉及 UI。新增测试覆盖：

- 当前 `AlarmContract.GRACE_MILLIS` 的宽限期内收到 `ACTION_FIRE`：执行记录为 `FIRED`，通知出现。
- 超出当前宽限期收到 `ACTION_FIRE`：执行记录为 `MISSED`，不出现通知。
- 模拟进程停机期间遗留的过期 `PENDING` 调度意图：`ReminderScheduler.reconcile` 记录 `MISSED`、清除意图且不向 `ShadowAlarmManager` 补排闹钟。

测试使用当前实现常量 10 分钟，并在边界两侧留出一分钟余量，避免依赖毫秒级执行时序。没有修改生产代码、业务数据、设计资产或 UI。

这些测试确认了当前实现的两个错过处理入口；产品规格仍未正式确定“短宽限期”的准确时长。因此 `REC-P0-118` 继续保持 **SPEC UNRESOLVED**，本 Issue 不将现有 10 分钟常量视为产品签核，也不增加候选 PASS 数。

## 测试证据

- 定向 Robolectric API 34：两个相关测试类 **24/24 PASS**，0 failures/errors/skips。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.reminders.ReminderCompletionIsolationTest --tests dev.powerfulcalendar.reminders.ReminderValidationTest`。日志 SHA-256 `9C13AE8186A1595752127D7E52EC7FFBE6C08B9373AAE29105F19B0E6A48782D`；`ReminderCompletionIsolationTest` XML SHA-256 `CB4650937CC2FC8129C9A1596B8418F5856FDD882E332E5A53CCA0A067BC33B2`；`ReminderValidationTest` XML SHA-256 `C76DED10FA3CD0D0B41663947196F53B15A08E43C8C0C62BEA15CB07139F7B5C`。
- 完整 Debug 回归：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`，**BUILD SUCCESSFUL**；459/459 tests，57 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。完整日志 SHA-256 `2289F28174AF65C694D74D3048788FB6A0190830782AC21AB97336B0FF6DD755`。全量 `ReminderCompletionIsolationTest` XML SHA-256 `9B21A1B4BC8FB618EF92CC6E3A2C89CAC5DE5101164375F9D917177E9956A93E`；全量 `ReminderValidationTest` XML SHA-256 `2C60F373BBFFBF14175654C70E99F6A52F813497D642AE3DB623B06B42C41B5D`；Lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256 `138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 完整 JUnit XML 保存在 `.local/android-test/reports/freeze-20261024/pc-issue-105-full-junit/`；定向日志和 XML 保存在同目录下的 `pc-issue-105-targeted*` 文件。
- 另外执行 `:app:check` 时，测试任务已通过，但总任务在 `verifyReleaseSigning` 阶段因缺少用户离线保管的 Release keystore 而失败。门槛日志 `.local/android-test/reports/freeze-20261024/pc-issue-105-full-gradle.log` SHA-256 `0035DE4D051BEC54154639A1894985E181AE8D7D27FEBB26E124803F9B4F6289`；这不是 Debug 测试或 lint 失败，也没有生成或读取签名密钥。

## 台账与 Git

- `REC-P0-118` 已记录当前实现的测试证据，状态仍为 **SPEC UNRESOLVED**。
- 结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-105.json`，SHA-256 `9FB1049D3D268E0F08127E78D62D224261AA65547400B436AFF3706D9A4B3DC7`；台账 SHA-256 `93F9CE96FAC8E55041DE5E715BAC6CB09004DAABE834707FA1B45695BC9D045F`。校验只确认 209 行、唯一 ID 与 126/82/1 优先级分区；`product_behavior_tested=false`。
- 测试提交：`2d89ffba5317eb9b0164682702868e5cf75c8fe4`（`test(冻结): 覆盖提醒宽限期错过与恢复`），已推送。只包含提醒测试代码。
- 当前冻结统计不变：135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED。真实 Android 系统投递、API 35 与物理/OEM 行为不在本次 Robolectric 证据内；整体冻结继续 **HOLD**。
