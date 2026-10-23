# PC-ISSUE-014 — 完成跟踪与事件启用状态独立

日期：2026-10-23（北京时间）

优先级：P0（重建候选 `REC-P0-014`）

状态：**核心存储回归 PASS；核心冻结整体 HOLD**

## 需求与审计结果

冻结合同 `docs/CORE_CONTRACT_FREEZE_2026-10-21.md` §2 规定，完成跟踪与事件启用状态相互独立。

审查 `DraftForm.from` 和整份快照提交后，没有发现生产代码缺陷。现有测试没有证明仅更改跟踪开关时，不会改变启用状态。本 Issue 新增隔离 SQLite/Robolectric 回归：对启用的 Activity 关闭跟踪，对禁用的 Activity 开启跟踪，并检查 `enabled` 与提醒偏移保持不变。未修改生产代码、UI、正式数据库或事件数据。

## 定向回归

命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerStoreTest.changingCompletionTrackingPreservesEventEnabledState" --offline --rerun-tasks --console=plain
```

结果：**1 passed / 0 failures / 0 errors / 0 skipped**。两个相反方向的状态切换均成功，原 `enabled` 与提醒配置保留。

定向日志：`.local/android-test/reports/freeze-20261023/pc-issue-014-tracking-enabled-targeted-final-20261023.log`，SHA-256 `6F9B2230A8106E07E9953FB5C9A2267D87CDB94A6FA17904C79E97DEB8B7930B`。JUnit XML：`.local/android-test/reports/freeze-20261023/pc-issue-014-targeted-final-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`，SHA-256 `E97B1055C8ECDA31998DA2256E5C03570282D1D7283F107EB79539648340D357`。

## 完整回归

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

结果：**BUILD SUCCESSFUL；380 tests passed / 0 failures / 0 errors / 0 skipped，54 个 JUnit suites；Lint 0 errors / 55 warnings。** 包含 379 个已跟踪测试和 1 个受保护的未跟踪 `DraftDeckFeedbackTest.kt`；该文件未修改、未暂存、未提交。

完整日志：`.local/android-test/reports/freeze-20261023/pc-issue-014-tracking-enabled-full-gradle-20261023.log`，SHA-256 `6456100DA5A05D86B1077A834326D665FF982F0CF14EE355A1E9A6BD41C31B15`。54 个 JUnit XML 已保存在 `.local/android-test/reports/freeze-20261023/pc-issue-014-full-junit/`。Debug APK SHA-256：`63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`。

## 候选签核与限制

- `REC-P0-014`：PASS。切换完成跟踪不会隐式启用或禁用事件，也不改提醒偏移。
- 本 Issue 验证核心快照和 SQLite 状态，不代表设置页面或 Android 提醒交付验收，也不映射缺失的历史 209 项原始 ID。整体核心冻结继续 HOLD。
- 更新后候选状态为 **59 PASS / 0 FAIL / 9 BLOCKED / 135 NOT TESTED / 6 SPEC UNRESOLVED**；P0 为 40 PASS、81 NOT TESTED。台账 SHA-256 `94B41A3AFE841095614F96A84A828176ED53487467C355DCCB0536CC926A9D38`；结构验证 artifact `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-014.json` 仅验证 CSV 结构与分区，不验证产品行为或历史映射。

## Git

- `574a2688bd9a651267e70071e705c733b16c99ae` — `测试(冻结): 验证完成跟踪不改变事件启用状态`（已推送）
- 本报告、台账、冻结判定与交接状态另行原子提交并推送。
- 受保护的 `design/`、`DraftDeckFeedbackTest.kt` 与 `PC-OBS-005` 未修改、未暂存。

## 后续

继续测试核心完成与提醒运行时边界；真实通知触发仍需要平台/设备证据。
