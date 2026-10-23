# PC-ISSUE-110 — 提醒恢复 receiver 核心路径签核

日期：2026-10-24

候选：`REC-P0-117`（重建候选；历史 209 项映射仍为 `UNMAPPED`）

状态：**合成 receiver 核心恢复 PASS；真实 Android 系统投递未验证；整体冻结 HOLD**

## 核心验收

本项核验提醒恢复核心分支：从持久期望状态重建 AlarmManager 注册，并等待异步 receiver 工作结束。覆盖 `ACTION_BOOT_COMPLETED`、应用替换、时区变化、系统时钟变化、精确闹钟权限变化和显式重协调。测试环境为 Robolectric API 34、隔离 SQLite 和 ShadowAlarmManager；系统实际广播与设备行为不在本次证据范围。

`BootReceiverRecoveryTest.bootBroadcastReconcilesDesiredAlarmAndFinishesAsyncPendingResult` 验证开机路径把 PENDING 意图恢复为 SCHEDULED，完成 `goAsync()` 的 PendingResult，注册一个 Alarm，且 SQLite 重开后状态仍在。`recoveryBroadcastsReconcileDesiredAlarmAndFinishAsyncWork` 对五条非开机路径逐项清除现有注册再派发，验证异步工作完成并各自恢复唯一注册，最终状态跨 SQLite 重开保留且 PowerStore 锁已释放。因此 `REC-P0-117` 对合成 receiver 核心恢复行为签为 PASS。

## 验证证据

- 定向 Robolectric API 34：**2/2 PASS**，0 failures/errors/skips。日志 SHA-256：`756CA189C10D851073D6511E2932DC8D9BD750F40C369A43C4D15BDD6A86CA2E`；JUnit XML SHA-256：`C935E22DDE3E54E68B21C63C572EACC394F5D25B9A6FFEB55DF48987129218E8`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。**BUILD SUCCESSFUL**；460/460 测试、57 个套件，0 failures/errors/skips；lint 0 errors / 58 warnings。完整日志 SHA-256：`F2ADB865ADA9E859444405C631D76CCE8CFEB6A7A16002497496E1EF77393887`。
- 全量 `BootReceiverRecoveryTest` XML SHA-256：`C935E22DDE3E54E68B21C63C572EACC394F5D25B9A6FFEB55DF48987129218E8`；lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 日志与全量 JUnit XML 保存在 `.local/android-test/reports/freeze-20261024/pc-issue-110-*`。完整回归发现了工作区内一项受保护的未跟踪测试；本 Issue 未修改、未暂存该文件。

## 台账与边界

- `REC-P0-117` 的重建环境、动作和预期明确为合成 receiver 核心恢复；历史 ID 仍为 `UNMAPPED`。
- 结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-110.json`，SHA-256 `F4253D7827BFD7843976CAAF94989E5F9AEA4760119C7E900D1B1C56271899CD`；CSV SHA-256 `9F935D5F3746DAD97F8382CE0D4CCC47C0A0448EA1FD49280C6B73429118F33C`。结构校验覆盖 209 行、唯一 ID、必填字段、证据完整性和 126/82/1 分区；`product_behavior_tested=false`。
- 候选计数：**139 PASS / 0 FAIL / 9 BLOCKED / 54 NOT TESTED / 7 SPEC UNRESOLVED**；P0 **115/0/1/5/5**，P1 **24/0/8/49/1**。未映射历史项与设备验收缺口仍使整体保持 **HOLD**。
- 未测试真实 Android 广播投递、重启/升级/权限/时区系统流程、API 35 和实体/OEM 设备行为；本次不据此声称设备恢复通过。
- 没有修改应用或测试源码。
