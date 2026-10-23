# PC-ISSUE-107 — 跟踪切换与提醒意图核心验收签核

日期：2026-10-24

候选：`REC-P0-017`（重建候选，历史 209 项映射仍为 `UNMAPPED`）

状态：**候选定义范围 PASS；PowerCal 整体冻结仍 HOLD**

## 验收边界与判定

台账为本候选声明的环境是 Robolectric API 34、隔离的 PowerStore SQLite、待处理提醒意图及同库重开，并明确排除界面、Android 系统调度和设备。验收要求是切换完成跟踪不能隐式禁用事件或取消期望提醒。

此前 PC-ISSUE-087 的 PowerStore 回归覆盖跟踪开、关两个方向，并验证事件启用状态、提醒策略、原始发生项键、触发时刻和持久提醒意图经过 SQLite 重开保持。PC-ISSUE-102 的 Robolectric 回归补足更新修订后的 `ReminderScheduler.reconcile`：新修订重新成为 `SCHEDULED`，模拟 AlarmManager 中仅有一个闹钟，旧修订广播被拒绝。

这些证据满足该重建候选的明示范围，因此将 `REC-P0-017` 从 `NOT TESTED` 签为 `PASS`。本结论不声称真实 Android 系统投递通过，也不把重建候选映射到缺失的历史 209 个用例。

## 验证证据

- 定向回归 **2/2 PASS**：
  - `PowerStoreTest.changingCompletionTrackingPreservesReminderIntentAcrossRestart`
  - `ReminderValidationTest.changingCompletionTrackingReconcilesTheReminderAtTheNewRevision`
  - 命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.core.PowerStoreTest.changingCompletionTrackingPreservesReminderIntentAcrossRestart --tests dev.powerfulcalendar.reminders.ReminderValidationTest.changingCompletionTrackingReconcilesTheReminderAtTheNewRevision --offline --rerun-tasks --console=plain`
  - 日志 SHA-256：`77AB328142E24F2EA8129557FA6329DF41D078A1DB272883A302F91F1559C162`。
  - SQLite 测试 XML SHA-256：`C18AB0C374531449A15618BE5505B10887CD78BFC215BC9060F68DC25D9EF610`；提醒调度测试 XML SHA-256：`CFC2DD46C73375BC50A6FB2846CBE3ECC6C8F7C381F9FBDEAA2C765B4976962E`。
- 完整 Debug 回归命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。结果 **BUILD SUCCESSFUL**，460/460 测试、57 个套件，0 failures/errors/skips；lint 0 errors / 58 warnings。完整日志 SHA-256：`BAA54DD3B711D5BF06AA01ECAF0426AB88478CDCF5C26052F90028B0E7ED896A`。
- 全量 `PowerStoreTest`（87 项）XML SHA-256：`59EEE53554EDE3DCEA6B4D2CB516D391C14A546D6988D417D49F9902DC2D19B5`；全量 `ReminderValidationTest`（17 项）XML SHA-256：`73F5FD2BAA18E8DDC7E030E9214316660C4477EA5459FD79F6759217B1304F12`；lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`。
- Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 完整 JUnit XML 位于 `.local/android-test/reports/freeze-20261024/pc-issue-107-full-junit/`；定向日志与 XML 位于同目录下的 `pc-issue-107-*` 文件。完整回归包含一个受保护的未跟踪测试文件；该文件未修改、未暂存。

## 台账与交付

- `docs/CORE_FREEZE_ACCEPTANCE_LEDGER.csv` 将 `REC-P0-017` 标为 `PASS`，证据仍限定于上述候选范围。
- 结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-107.json`，SHA-256 `B8AEF4DE9137931A66578EEAE219EEB38CD8D7C01515E128693E5C5192ED0FE8`；CSV SHA-256 `4A83C16E8D29F918D79415FE119B27FACFB887DAE0BF65150D260C0AAE68E1F6`。它验证 209 行、唯一 ID、字段与证据完整性及 126/82/1 分区；`product_behavior_tested=false`。
- 冻结候选计数现为 **136 PASS / 0 FAIL / 9 BLOCKED / 57 NOT TESTED / 7 SPEC UNRESOLVED**；P0 为 **112/0/1/8/5**，P1 为 **24/0/8/49/1**。历史用例仍无映射，整体保持 **HOLD**。
- 没有修改应用生产代码、测试代码、业务数据或设计资产。
