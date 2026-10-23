# PC-ISSUE-102 — 切换完成跟踪后保留提醒调度

日期：2026-10-24

候选：`REC-P0-017`（重建 P0；历史映射仍为 `UNMAPPED`）

状态：**API 34 核心调度回归通过；候选仍 NOT TESTED；整体冻结 HOLD**

## 结果

新增 `ReminderValidationTest.changingCompletionTrackingReconcilesTheReminderAtTheNewRevision`。测试先提交启用完成跟踪且带提醒的 Activity，调用 `ReminderScheduler.reconcile` 后确认系统调度适配层登记一个 Alarm。随后只关闭完成跟踪并提交新修订：Activity 仍启用，提醒策略、触发时刻和 occurrence key 保持不变；新修订的提醒意图重新进入 PENDING。再次协调后意图成为 SCHEDULED，ShadowAlarmManager 仍只有一个 Alarm；当前修订校验有效，旧修订广播被拒绝。

本次只增加 Robolectric 回归，没有发现需要改生产逻辑的缺陷；没有修改 UI、设计资产或业务数据。Robolectric 不能证明实体 Android 设备上的实际 AlarmManager/通知投递，因此 `REC-P0-017` 仍为 NOT TESTED。

## 验证证据

- 定向 Robolectric API 34：**1/1 PASS**。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.reminders.ReminderValidationTest.changingCompletionTrackingReconcilesTheReminderAtTheNewRevision --offline --rerun-tasks --console=plain`。日志 SHA-256 `3E1CC8CAF87E70B6A1BE7ACE60093AB476F976E49D8A92D2C5CFD0FA83ED6191`；JUnit XML SHA-256 `B475F2ABAED2A7EE59CC225F8C6E3FDA20868094A3C4BC4A1ED71B627C21513A`。
- 完整 Gradle：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`，**454/454 across 57 suites**，0 failures/errors/skips；Lint 0 errors / 58 warnings。完整日志 SHA-256 `5381A58E4954EE2CE28087E82012362C581C5048C60007FAD36CA3B438105732`；完整 `ReminderValidationTest` XML SHA-256 `8D4895648BBAB524D45ADE86027C1F1DA8C9963054FD6828AF9AFD7FB0F8558B`；`PowerStoreTest` XML SHA-256 `69BA0DFA0EBD57ECE05FCFCC642E4090E104EC91A311F46B20C23314CC7411BB`；Lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256 `0CE2928EF5E597475BEDF1C6BCD96AD5BD6267EF987DB7589CC959EFF1BFA7A9`。
- 第一次定向编译因测试文件漏掉 `assertFalse` 导入而未进入测试；补上导入后重新执行，以上定向及完整回归通过。初次编译日志 SHA-256 `288C83D11E2141D0097781B57CE43A26FBAF95ABEC3CA128A09AFBD35606E213`。
- 日志和 JUnit XML 保存在 `.local/android-test/reports/freeze-20261024/`。

## 台账与 Git

- 结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-102.json`，SHA-256 `FB29AA81FCFCA98BED762116A0F6E6AFCEBC7439642421B6BBEA73FBB7DC9085`；台账 SHA-256 `671FDBD1B5371FC6B961A867713CBE0B222A03E943820294A2B3D4A6F6FDAA7E`。校验为结构性，`product_behavior_tested=false`。
- 测试提交：`200d5ae4783bfe2d2b0d95acd0ce0f572a5d2ebe`（`test(reminders): 验证跟踪切换后提醒重排`），已推送。
- 候选状态与冻结统计不变：`REC-P0-017` 仍 NOT TESTED；总体仍 HOLD。
