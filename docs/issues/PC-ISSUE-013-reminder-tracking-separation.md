# PC-ISSUE-013 — 提醒设置与完成跟踪保持独立

日期：2026-10-23（北京时间）

优先级：P0（重建候选 `REC-P0-011`）

状态：**核心存储回归 PASS；核心冻结整体 HOLD**

## 需求与审计结果

冻结合同 `docs/CORE_CONTRACT_FREEZE_2026-10-21.md` §2 规定，完成跟踪、提醒调度和事件启用状态是独立状态。

审查 `DraftForm.from`、整份快照提交及 SQLite 读写后，没有发现生产代码缺陷。现有测试没有直接证明只改提醒设置时，跟踪开启和关闭的记录都保留原状态。本 Issue 增加一个隔离 SQLite/Robolectric 测试，分别创建跟踪关闭与开启的 Activity，只改提醒偏移量并提交。未修改生产代码、UI、真实数据库或事件业务数据。

## 定向回归

命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerStoreTest.changingReminderPreservesOptionalCompletionTracking" --offline --rerun-tasks --console=plain
```

结果：**1 passed / 0 failures / 0 errors / 0 skipped**。两个状态下提醒偏移量均更新，`trackingEnabled` 保持原值，完成键集合仍为空。

定向日志：`.local/android-test/reports/freeze-20261023/pc-issue-013-reminder-tracking-targeted-final-20261023.log`，SHA-256 `4B0AF0679CC40DB014476E00835B747082BAB56E89978E863EC3715A2CB11F7F`。JUnit XML：`.local/android-test/reports/freeze-20261023/pc-issue-013-targeted-final-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`，SHA-256 `49FFE07282EB24C5830FB62B2E9C411D96CEF57799481043A394F848EA548A62`。

## 完整回归

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

结果：**BUILD SUCCESSFUL；379 tests passed / 0 failures / 0 errors / 0 skipped，54 个 JUnit suites；Lint 0 errors / 55 warnings。** 包含 378 个已跟踪测试和 1 个受保护的未跟踪 `DraftDeckFeedbackTest.kt`；该文件未修改、未暂存、未提交。

完整日志：`.local/android-test/reports/freeze-20261023/pc-issue-013-reminder-tracking-full-gradle-20261023.log`，SHA-256 `923C16CE33833C278D5D7D4E72960FD1301CEDA2602BE04A00D49FCE8F30CF44`。54 个 JUnit XML 已保存在 `.local/android-test/reports/freeze-20261023/pc-issue-013-full-junit/`。Debug APK SHA-256：`63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`。

## 候选签核与限制

- `REC-P0-011`：PASS。只更新提醒偏移不会改变跟踪开启/关闭状态。
- 此项仅验证核心快照及 SQLite 契约，没有 Android 通知交付或设备测试，也不映射缺失的历史 209 项原始 ID。核心冻结整体继续 HOLD。
- 更新后候选状态为 **58 PASS / 0 FAIL / 9 BLOCKED / 136 NOT TESTED / 6 SPEC UNRESOLVED**；P0 为 39 PASS、82 NOT TESTED。台账 SHA-256 `E57653BB9C941D8DB274E195223A3BEC8988E789F5BED90D8965617BD9BE9E8A`；结构验证 artifact `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-013.json` 仅验证 CSV 结构与分区，不验证产品行为或历史映射。

## Git

- `eea569cf50466ca0de6ab9ccd7e6ac9983149172` — `测试(冻结): 验证提醒修改隔离完成跟踪`（已推送）
- 本报告、台账、冻结判定与交接状态另行原子提交并推送。
- 受保护的 `design/`、`DraftDeckFeedbackTest.kt` 与 `PC-OBS-005` 未修改、未暂存。

## 后续

继续检查不涉及页面的核心状态独立性与持久化合同；通知触达仍需独立运行时证据。
