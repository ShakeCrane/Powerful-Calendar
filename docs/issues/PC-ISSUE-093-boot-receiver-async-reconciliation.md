# PC-ISSUE-093 — 开机恢复 receiver 异步重建提醒

日期：2026-10-24

候选：`REC-P0-111`（重建 P0；历史映射仍为 `UNMAPPED`）

状态：**Robolectric receiver 异步恢复路径 PASS / 整体冻结 HOLD**

## 发现

原有提醒回归直接调用 `ReminderScheduler.reconcile()`，证明了 SQLite 中的期望提醒可以提交给 AlarmManager，但没有经过 `BootReceiver` 的 `goAsync()` 路径，也没有证明异步工作结束时系统 `PendingResult` 会完成。

## 变更

- 新增 `BootReceiverRecoveryTest.bootBroadcastReconcilesDesiredAlarmAndFinishesAsyncPendingResult`。
- 建立一个 SQLite Alarm，其期望调度状态为 `PENDING`，然后关闭 store 并派发 `ACTION_BOOT_COMPLETED`。
- 用 Robolectric `PendingResult` future 等待 `goAsync()` 工作完成；验证期望状态变成 `SCHEDULED`，ShadowAlarmManager 收到一个任务，SQLite 重开仍保留调度结果。
- 仅新增核心 receiver 回归；生产代码、界面、业务数据与设计资产均未修改。

## 测试过程记录

- 第一次定向运行未通过：Robolectric shadow 直接调用 receiver 时没有预置 `PendingResult`，测试夹具在读取它时失败。日志 SHA-256：`311613FF777CB156598AF017AFA64D72603B144CFCDE15D233402FA0C7C35444`。
- 第二次定向运行未通过编译：Android test stub 不公开 `PendingResult` 构造器及 `setPendingResult`。日志 SHA-256：`DB9CFD750C1F78BBA48EB1F2B16599F53DF3C51C11FBED2F8FE16A652BE66828`。
- 根因是测试夹具构造方式，不是产品代码失败。改用 Robolectric `ReflectionHelpers` 为 receiver 注入 shadow pending result 后，最终定向回归通过；前两份失败日志仍保存在 `.local`，没有覆盖。

## 验证

- 最终定向 Robolectric API 34：**1/1 PASS**，0 failures/errors/skips。日志 SHA-256：`CEBEC27C47343C23D1998248D6C7FC99176310CFE41BDACECD9BA7AA58EC439F`；JUnit XML SHA-256：`C6B73D20FB7495F9E0C3AE24DF5E3F8A41918C93C0D62B2B427D8B2366FD621B`。
- 完整命令 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**447/447 across 57 suites**，0 failures/errors/skips，`BUILD SUCCESSFUL`；Lint 0 errors / 58 warnings。日志 SHA-256：`AF800BA5E17A2822EC9A79CCB9309A980DE4A199769A54F7225560BFE19FF707`。
- 完整 `BootReceiverRecoveryTest` XML SHA-256：`67B4557F906DCD7A25C08972DEDA0B8DED3B6FAE1E9B91970DD6FC921465DDE5`；完整 `PowerStoreTest` XML SHA-256：`29721C7A818EA88F807DF15E903CD1BC9574B3ABEE8932A536D0B54736EA5438`；Lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`86D48004E3CE00E94A459A4CCAA929A9B55F8816DDBE7E10D6E9937D0C11ED94`。
- 完整证据位于 `.local/android-test/reports/freeze-20261024/pc-issue-093-*`。

## 范围与结论

- `REC-P0-111` 对 API 34 Robolectric 中 `ACTION_BOOT_COMPLETED → goAsync → SQLite 读取 → AlarmManager reconcile → PendingResult.finish()` 核心路径签为 **PASS**。
- 这不是实体设备开机，也没有覆盖 OEM 限制、后台执行时限或 API 35 系统实际广播；核心冻结仍 **HOLD**。

## Git

- 测试提交 `86e9d97106592edc41d988db317d47687be89976` — `test(提醒): 验证开机恢复异步重建` — 已推送。
- 本独立报告及台账签核随后单独提交与推送。
