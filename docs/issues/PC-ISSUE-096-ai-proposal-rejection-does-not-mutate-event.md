# PC-ISSUE-096 — AI 拒绝修改提案不影响正式事件

日期：2026-10-24

候选：`REC-P1-055`（重建 P1；历史映射仍为 `UNMAPPED`）

状态：**PASS / 整体冻结 HOLD**

## 验收范围

冻结规格要求拒绝一个针对现有事件的 AI 修改提案时，只丢弃提案，不改变正式事件。本轮没有复现生产缺陷；`AiBatchRunner` 的 DISCARD 路径已遵守该规则，缺少的是覆盖 AI 批次入口、正式历史与 SQLite 重开的逐案回归。

## 回归验证

新增 `PowerStoreTest.theAiBatchDiscardOnlyRejectsProposalAndPreservesFormalEventAcrossRestart`：先提交一条正式 Activity 并保存其完整快照与历史，再为同一事件创建 AI REPLACE 提案，授权该草稿后通过 `AiBatchRunner.Mode.DISCARD` 拒绝。测试验证批次报告为 `discarded=1 / published=0 / kept=0 / cancelled=false`，提案成为 DISCARDED，正式事件和提交历史完全不变；批次完成并释放锁。关闭并重开 SQLite 后，上述状态与快照仍保持。

- 定向 Robolectric API 34：**1/1 PASS**。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.core.PowerStoreTest.theAiBatchDiscardOnlyRejectsProposalAndPreservesFormalEventAcrossRestart --offline --rerun-tasks --console=plain`。日志 `.local/android-test/reports/freeze-20261024/pc-issue-096-ai-proposal-discard-targeted-postcommit-r1.log`，SHA-256 `2BB2140C3364F1AA753A5224A1748E1E08CE4D19EFF6614E0A9F395379425647`；JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-096-targeted-junit-postcommit-r1/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`，SHA-256 `19673B14DB766CF4107620BFE441E1C95D79D62B00AE1A3A0AC2705F7E1B3278`。
- 完整冻结 Gradle 命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**450/450 across 57 suites**，0 failures/errors/skips，Lint 0 errors / 58 warnings。450 项含 449 个已跟踪测试与一个受保护的未跟踪测试；该受保护文件未修改、未暂存。
- 完整日志 `.local/android-test/reports/freeze-20261024/pc-issue-096-full-gradle-final-20261024-r1.log` SHA-256：`43A8E132DECBCD3057072447A5F3EE646B2B8FF5F3ED67DBAD5719CE7A6D894E`；完整 `PowerStoreTest` XML `.local/android-test/reports/freeze-20261024/pc-issue-096-full-junit-r1/testDebugUnitTest/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml` SHA-256：`984329BE08AC7EE7A3BEC95B780ADD5BD0BECBF8DDCC506F7B146D60AF5B46FE`；Lint XML `.local/android-test/reports/freeze-20261024/pc-issue-096-full-junit-r1/lint-results-debug.xml` SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK `app/build/outputs/apk/debug/app-debug.apk` SHA-256：`D941A9BF701896C7D9E232540856D5854EA9EC8CFD6AEB2AB0AA9CDD8DE72ABB`。
- 完整 JUnit XML、Lint XML 与原始日志保存在 `.local/android-test/reports/freeze-20261024/pc-issue-096-*`。

## 结论与限制

这是核心批次与 SQLite 回归，不调用 AI 模型/网络，也不签核界面交互、Android 系统行为或实体设备。未发现需要生产代码修复的问题；本 Issue 通过补齐验收证据关闭，`REC-P1-055` 记为核心语义 PASS。整体冻结仍为 HOLD。

## Git

- 测试提交：`65f6cabd653878f77127e167a583bea31043e5e3` — `test(ai): 验证拒绝提案不影响正式事件`，已推送。
- 本独立结案报告、台账与结构校验快照随后单独提交并推送。
