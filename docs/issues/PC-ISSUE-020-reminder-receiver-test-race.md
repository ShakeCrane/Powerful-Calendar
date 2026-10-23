# PC-ISSUE-020 — 提醒接收器回归测试并发锁冲突

日期：2026-10-23

状态：**Robolectric 测试稳定化完成；Android 广播生命周期与系统送达仍未签核**

## 发现与根因

运行 `PC-ISSUE-019` 的完整 Gradle 回归时，既有 `ReminderCompletionIsolationTest.firedAlarmRecordsExecutionWithoutCompletingTrackedEvent` 失败：测试在 receiver 后台线程写 SQLite 时，每 10ms 从另一连接轮询 `lastExecution`/schedule intent，Robolectric SQLite 抛出 `SQLiteDatabaseLockedException`。失败栈落在测试的 `await` 查询，不是完成状态断言。

继续检查还发现测试直接调用 `onReceive`，没有由广播框架设置 `BroadcastReceiver.PendingResult`。一次同步 dispatcher 探索复现了 `PendingResult.finish()` 空指针，确认直接调用不能代表 Android 广播分发生命周期。该探索输出在路径复用前未单独归档或计算哈希；本报告据当时会话输出记录此事实，不把它伪装成可复验附件。原始完整 Gradle 失败日志和 JUnit XML 已保留并有哈希。

## 最小修复

- 将 `ReminderReceiver` 的 delivery handler 设为模块内可调用，明确分离 handler 与 `onReceive` 的异步 Android 包装。
- 隔离测试同步调用同一 handler，不再启动另一写线程并轮询 SQLite；业务分支、记录内容和完成状态断言保持不变。
- `onReceive` 仍调用 `goAsync()`、启动后台线程并在完成后 `finish()`；本修复没有改变运行时调度行为。

## 回归证据

- 保留的失败全量回归：`PC-ISSUE-019` 的 Gradle 日志 `.local/android-test/reports/freeze-20261023/pc-issue-019-date-only-deadline-full-gradle-20261023.log`，SHA-256 `551DFD59864DD836E4E91D612DEA2FBB4ED6FD452223A437DB305478229F9779`；失败类 JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-019-failed-full-junit/TEST-dev.powerfulcalendar.reminders.ReminderCompletionIsolationTest.xml`，SHA-256 `754016642CA8490866BFBC9147C779D4866F6FC4134AFAA9AFEBB5C65B6947BD`。该次 384 个测试中有 1 个失败；未被本报告计为通过。
- 修复后、提交前完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。BUILD SUCCESSFUL；**384/384 PASS**，55 个 suite，0 failures/errors/skips；Lint 0 errors、55 warnings。日志 `.local/android-test/reports/freeze-20261023/pc-issue-020-reminder-dispatch-full-gradle-20261023.log`，SHA-256 `A4A67F15FCBA59BBBBE543943337501DAFD2ECA7A37ECBF3DE3A41664F155783`。全量 XML 归档于 `.local/android-test/reports/freeze-20261023/pc-issue-020-full-junit/`；目标类 XML SHA-256 `C530A85E2230F569E8A4F084379C893CC3CA2DB18251CE1990707F02EA413C6C`。
- 提交后定向复跑：`ReminderCompletionIsolationTest` **2/2 PASS**，日志 `.local/android-test/reports/freeze-20261023/pc-issue-020-reminder-dispatch-targeted-final-20261023.log`，SHA-256 `2010C351D82318FC0E3D1154BE91AD64FECD4CC8B1025117870DC8C5D2D2A673`；JUnit XML SHA-256 `0AB606BDA5615C40146FDEBE0B74FCDB3EC2197DC6321CA895400C5972ABD131`。
- 完整工作树的 384 项包括 383 个已跟踪测试及一个受保护未跟踪测试 `DraftDeckFeedbackTest.kt`；该文件未修改、未暂存。Debug APK SHA-256：`EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`。

## 边界与提交

本回归现在验证 receiver 的 delivery handler；它不验证 Android 框架设置 `PendingResult`、`goAsync` 生命周期、AlarmManager 实际送达或通知栏手势。上述系统/设备门槛继续保持未测试。

- 代码提交：`d60809b4133a45c0551019f1edabec1195d34cf3`（`测试(冻结): 消除提醒回归的并发锁冲突`），已推送到 `origin/main`。
- 仅测试路径和 handler 可见性调整；提醒处理逻辑、UI、schema 与业务数据没有变化。
