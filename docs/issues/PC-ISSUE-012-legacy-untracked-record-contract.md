# PC-ISSUE-012 — 旧记录迁移保留未跟踪状态

日期：2026-10-23（北京时间）

优先级：P0（重建候选 `REC-P0-010`）

状态：**核心迁移回归 PASS；核心冻结整体 HOLD**

## 需求与审计结果

冻结合同 `docs/CORE_CONTRACT_FREEZE_2026-10-21.md` §3 规定，旧的未跟踪记录在迁移后仍保持未跟踪；不能把用户的历史日历自动转成待办积压。

审查旧 v1 磁盘夹具及当前 payload 解码路径后，没有发现生产逻辑缺陷：历史 ACTIVITY payload 没有 `tracking` 字段时，解码默认值为 `false`。本 Issue 在既有真实 on-disk v1 迁移测试中补充断言，检查未跟踪状态、空完成键、拒绝完成写入以及 revision 不变。没有修改生产代码、UI、真实用户数据库或其他数据。

## 定向回归

命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerStoreSchemaUpgradeTest.upgradingRealV1DataInPlacePreservesRowsAndAddsOnlyTheReviewedTable" --offline --rerun-tasks --console=plain
```

结果：**1 passed / 0 failures / 0 errors / 0 skipped**。该隔离 v1 SQLite fixture 升级到当前 schema；原始历史 Activity 仍未启用完成跟踪、没有完成键；尝试显式完成被拒绝且修订号不变。

定向日志：`.local/android-test/reports/freeze-20261023/pc-issue-012-legacy-untracked-targeted-final-20261023.log`，SHA-256 `EBC6ACE5862884209103552DC67B558F9D33234BAAB3738275B61BED977EE76E`。JUnit XML：`.local/android-test/reports/freeze-20261023/pc-issue-012-targeted-final-junit/TEST-dev.powerfulcalendar.core.PowerStoreSchemaUpgradeTest.xml`，SHA-256 `FD92FB8B00100279524D84928833BFBE769B7979BBEB700FBBF680F547374B2E`。

## 完整回归

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

结果：**BUILD SUCCESSFUL；378 tests passed / 0 failures / 0 errors / 0 skipped，54 个 JUnit suites；Lint 0 errors / 55 warnings。** 包含 377 个已跟踪测试和 1 个受保护的未跟踪 `DraftDeckFeedbackTest.kt`；该文件未修改、未暂存、未提交。

完整日志：`.local/android-test/reports/freeze-20261023/pc-issue-012-legacy-untracked-full-gradle-20261023.log`，SHA-256 `3CF7B670871B3B3AB6744BC9027313A779F78E6B1792EAD8FC5CFCCEAE6EAA7B`。54 个 JUnit XML 已保存在 `.local/android-test/reports/freeze-20261023/pc-issue-012-full-junit/`。Debug APK SHA-256：`63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`。

## 候选签核与限制

- `REC-P0-010`：PASS。旧 v1 Activity 的缺省跟踪状态保持关闭；完成尝试没有写入完成键，也没有增加 revision。
- 本 Issue 验证隔离历史 schema 的核心迁移/解码合同，不代表真实用户数据库或 Android 设备验收，也不映射缺失的历史 209 项原始 ID。核心冻结整体继续 HOLD。
- 更新后候选状态为 **57 PASS / 0 FAIL / 9 BLOCKED / 137 NOT TESTED / 6 SPEC UNRESOLVED**；P0 为 38 PASS、83 NOT TESTED。台账 SHA-256 `DC47A5AF2A05343D5585436C0036D7BD79C699FBF39E22D700DB7DD545C548CB`；结构验证 artifact `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-012.json` 仅验证 CSV 结构与分区，不验证产品行为或历史映射。

## Git

- `96d692782bf8f68187edb53379a7a2cd065672bb` — `测试(冻结): 保持迁移历史事件未跟踪状态`（已推送）
- 本报告、台账、冻结判定与交接状态另行原子提交并推送。
- 受保护的 `design/`、`DraftDeckFeedbackTest.kt` 与 `PC-OBS-005` 未修改、未暂存。

## 后续

继续审计可在核心模型和 SQLite 范围内验证的 P0 契约；平台通知行为仍需相应设备证据。
