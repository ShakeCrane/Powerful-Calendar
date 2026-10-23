# PC-ISSUE-011 — 日期范围 Memo 的单次完成与持久化

日期：2026-10-23（北京时间）

优先级：P0（重建候选 `REC-P0-009`）

状态：**核心 SQLite 契约回归 PASS；核心冻结整体 HOLD**

## 需求与审计结果

冻结合同 `docs/CORE_CONTRACT_FREEZE_2026-10-21.md` §3 规定，日期范围 Memo 是一个任务、一个完成实例，不能按范围内的日期拆成多个完成项。

只读审查 `PowerStore.setCompleted`、记录编码和 SQLite 重开路径后，没有发现生产逻辑缺陷。已有持久化测试覆盖无日期 Memo；日期范围 Memo 的单完成键、非法日期键拒绝和重启保留尚无独立证据。本 Issue 只添加隔离 SQLite/Robolectric 回归测试，未修改生产代码、UI、正式数据库或业务数据。

## 定向回归

命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerStoreTest.spanningMemoHasOneCompletionInstanceAndKeepsItAcrossRestart" --offline --rerun-tasks --console=plain
```

结果：**1 passed / 0 failures / 0 errors / 0 skipped**。

测试创建 2026-10-21 至 2026-10-23 的日期范围 Memo，验证范围边界与跟踪状态；尝试以日期作为完成键会被拒绝且不增加 revision；`single` 完成后关闭并重开同一个隔离数据库，范围日期与唯一完成键均保留，revision 只增加一次。

定向日志：`.local/android-test/reports/freeze-20261023/pc-issue-011-spanning-memo-targeted-20261023.log`，SHA-256 `AD370BF477724A308D901F97587F5CA75D595000B1705C73DD41F495559AC3CB`。JUnit XML：`.local/android-test/reports/freeze-20261023/pc-issue-011-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`，SHA-256 `A560FF10ACEB72CBC103BFBF9AF9B1EC4B1BB9BFF11ADE25AB9898EC1CEE2321`。

## 完整回归

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

结果：**BUILD SUCCESSFUL；378 tests passed / 0 failures / 0 errors / 0 skipped，54 个 JUnit suites；Lint 0 errors / 55 warnings。** 包含 377 个已跟踪测试和 1 个受保护的未跟踪 `DraftDeckFeedbackTest.kt`；该文件未修改、未暂存、未提交。

完整日志：`.local/android-test/reports/freeze-20261023/pc-issue-011-spanning-memo-full-gradle-20261023.log`，SHA-256 `199FF0DB9F98590F08A5726C5A46B34E34F441CCDFEC2AABB48951F763C9005B`。完整 JUnit XML 保存在 `.local/android-test/reports/freeze-20261023/pc-issue-011-full-junit/`。Debug APK SHA-256：`63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`。

## 候选签核与限制

- `REC-P0-009`：PASS。日期范围 Memo 始终以 `single` 作为唯一完成实例，拒绝按日拆分；完成结果及日期范围可在 SQLite 重开后读取。
- 本 Issue 验证的是核心存储语义，不包含 Memo 页面查询或 Android 设备操作，也不映射缺失的历史 209 项原始 ID。整体核心冻结继续 HOLD。
- 更新后候选状态为 **56 PASS / 0 FAIL / 9 BLOCKED / 138 NOT TESTED / 6 SPEC UNRESOLVED**；P0 为 37 PASS、84 NOT TESTED。台账 SHA-256 `1A1053E865400431E3A45137960770660E7D178F41CDD91BFD86E6BB5758E435`；结构验证 artifact `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-011.json` 仅验证 CSV 结构与分区，不验证产品行为或历史映射。

## Git

- `a63bb6457dd47b0c98c648abd1dcdb9409aa4c87` — `测试(冻结): 验证日期范围备忘单次完成持久化`（已推送）
- 本报告、台账、冻结判定与交接状态另行原子提交并推送。
- 受保护的 `design/`、`DraftDeckFeedbackTest.kt` 与 `PC-OBS-005` 未修改、未暂存。

## 后续

继续验证未签核的核心存储与恢复合同；Memo 查询页面仍不在本轮修改范围。
