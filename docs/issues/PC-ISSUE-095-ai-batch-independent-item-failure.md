# PC-ISSUE-095 — AI 批次隔离独立草稿失败并保留部分报告

日期：2026-10-24

候选：`REC-P1-058`（重建 P1；历史映射仍为 `UNMAPPED`）

状态：**PASS / 整体冻结 HOLD**

## 发现与根因

向三项独立 CREATE 草稿的中间项注入 SQLite 调度意图写入失败。修复前 `AiBatchRunner` 只发布首项，随后整批异常处理退出，第三项被跳过；定向回归失败为 `expected 2, actual 1`。失败日志 SHA-256：`62EF0C91214E7C38C0DABAC65B2A60571C9B17E38264239510B60D8184C950FC`。

根因是异常捕获位于整批循环外。单项事务已回滚，但 runner 把一次独立写入失败当成整批终止。

## 修复

- 将普通单项异常限制在当前草稿，记录草稿 ID 与失败原因、增加 `kept` 计数，然后继续后续独立项。
- `CancellationException` 仍向外传播，由现有取消清理路径释放锁并记录 `CANCELLED`。
- 无取消但存在单项失败时，批次终态记为 `FAILED`；完整成功仍记为 `COMPLETED`。
- 回归用 SQLite trigger 只让中间草稿在写入调度意图时失败，并验证失败事务没有留下正式事件、历史或调度意图。

## 验证

`theAiRunnerContinuesIndependentItemsAfterOneCommitFailureAndPersistsPartialReport` 验证：首尾两项成功发布，中间项保留为待处理草稿；报告为 `published=2 / kept=1 / cancelled=false`，失败原因和 `FAILED` 状态跨 SQLite 重开保持，批次锁释放，旧授权被拒；移除故障注入后，新批次可成功重试失败草稿。

- 最终定向 Robolectric API 34：**1/1 PASS**。日志 SHA-256：`B7BA661157DE0E9A4DD96C5F8ACBBEE26DB826EE709CD2E981E5D2AE0B88B17A`；JUnit XML SHA-256：`0D09525904E9BF11BCD4DFB01207801C03AE5869243FACB067CE8130F79291D0`。
- 完整命令 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**449/449 across 57 suites**，0 failures/errors/skips，`BUILD SUCCESSFUL`；Lint 0 errors / 58 warnings。
- 完整 Gradle 日志 SHA-256：`2F809BCB7A5C03973688D09662B7809BE64DF7A333B17FDAF8C316281DC3572D`；完整 `PowerStoreTest` XML SHA-256：`CF68196C6439FDA88371A6A0A273186D17F9B140D2B5745B696E04E07DE02803`。
- Lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`D941A9BF701896C7D9E232540856D5854EA9EC8CFD6AEB2AB0AA9CDD8DE72ABB`。
- 修复前失败日志、修复后定向日志/XML、完整日志及 JUnit XML 保存在 `.local/android-test/reports/freeze-20261024/pc-issue-095-*`。

## 范围

此项只验证本地 SQLite 与核心 batch runner 的独立项目失败、部分成功和重试语义；没有调用模型或网络，也不代表设备端授权交互签核。`REC-P1-058` 已通过这项核心契约回归；整体冻结仍受历史 209 项映射、设备验收和其他未测项约束，保持 **HOLD**。

## Git

- 代码与测试提交 `361f074538a27ad75c2a91c7da4bdee6beb46795` — `fix(ai): 继续独立批次并保留失败报告` — 已推送。
- 本独立报告、台账更新与结构校验摘要随后单独提交并推送。
