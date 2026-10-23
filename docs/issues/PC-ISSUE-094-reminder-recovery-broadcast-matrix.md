# PC-ISSUE-094 — 提醒恢复广播矩阵异步重建

日期：2026-10-24

候选：`REC-P0-117`（重建 P0；历史映射仍为 `UNMAPPED`）

状态：**五条非开机 receiver 分支的核心合成回归 PASS / 候选仍 NOT TESTED / 整体冻结 HOLD**

## 发现

`PC-ISSUE-083` 已证明 SQLite 重开后，直接调用 `ReminderScheduler.reconcile()` 可以重新注册持久调度；`PC-ISSUE-093` 随后证明 `ACTION_BOOT_COMPLETED` 的 `BootReceiver.goAsync()` 会重建提醒并完成 `PendingResult`。`REC-P0-117` 还列出应用升级、时区/时钟变化、精确闹钟权限变化和显式重协调入口，尚无逐分支 receiver 回归。

## 变更与覆盖

- 在 `BootReceiverRecoveryTest.recoveryBroadcastsReconcileDesiredAlarmAndFinishAsyncWork` 增加五条非开机 action 的 API 34 Robolectric 合成派发：`MY_PACKAGE_REPLACED`、`TIMEZONE_CHANGED`、`TIME_SET`、精确闹钟权限变化、`ACTION_RECONCILE`。
- 每条 action 前都取消模拟的 AlarmManager 注册，再派发 receiver；测试要求该分支进入 `goAsync()`、完成 `PendingResult`，并从持久 `SCHEDULED` 意图恢复恰好一个注册。
- 最后重开 SQLite，确认调度状态仍为 `SCHEDULED`，PowerStore 锁已释放。现有开机用例继续单独覆盖 `ACTION_BOOT_COMPLETED` 的 PENDING→SCHEDULED 路径。
- 仅增加核心 receiver 测试，没有修改生产代码、业务数据或设计资产。

## 验证证据

- 定向 Robolectric API 34：**2/2 PASS**，0 failures/errors/skips。最终日志 SHA-256：`15C9FC8800907C64CFEBEA601F91C2CAF56570B09920F6F9FFDC14238CB81803`；JUnit XML SHA-256：`A7BBB9E5A22B8BFEB6A21F851E19EDFF279F3CFF3FE0E649560912E2FE91E349`。
- 完整命令 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**448/448 across 57 suites**，0 failures/errors/skips，`BUILD SUCCESSFUL`；Lint 0 errors / 58 warnings。
- 完整 Gradle 日志 SHA-256：`3BF5BF314F9F4320B07788CB0629653AA90199A83F1429A392F2A622DE5D4116`。
- 完整 `BootReceiverRecoveryTest` XML SHA-256：`5ED78F245E9464AD0F946895B336B3C8FA765F0E9827F2B74D2EBC701A8EB908`；完整 `PowerStoreTest` XML SHA-256：`89E7D60508E31483687BEC7E2B09F11C6AEC7412F1844CBACB9C47D01FB97D56`。
- Lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`86D48004E3CE00E94A459A4CCAA929A9B55F8816DDBE7E10D6E9937D0C11ED94`。
- 原始日志与 XML 保存在 `.local/android-test/reports/freeze-20261024/pc-issue-094-*`。

## 验收边界

这些测试直接向 Robolectric receiver shadow 派发合成 Intent，不等同于 Android 系统实际发送广播。当前环境没有 `adb` 命令，无法执行 API 35 模拟器、实体设备、重启、应用升级、权限变化或系统时区变化实测。因此 `REC-P0-117` **继续保持 NOT TESTED**；本 Issue 只补齐所有已声明 receiver 分支的核心异步与重建证据，不把合成派发升级为设备签核。

## Git

- 测试提交 `3983bf7dcc1ba5b5b1ec9e84d621fe4aad77d99c` — `test(提醒): 覆盖恢复广播异步重建` — 已推送。
- 本独立报告、台账更新与结构校验摘要随后单独提交并推送。
