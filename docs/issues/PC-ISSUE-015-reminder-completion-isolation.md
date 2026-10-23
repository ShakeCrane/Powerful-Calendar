# PC-ISSUE-015 — 提醒触发与完成状态隔离

日期：2026-10-23

状态：**Robolectric 候选回归通过；Android 系统与真机验收仍未完成**

## 结论

本次审查未发现提醒触发器或通知对象会自动写入事件完成状态：提醒接收器只记录 `FIRED` 执行结果，通知不注册完成回调。原问题是这两项隔离合同缺少直接回归证据，而不是已复现的生产代码缺陷。因此本 Issue 仅补充测试，没有改动生产逻辑、UI 或数据库结构。

## 验收项与测试

| 候选项 | 测试 | 结果 |
| --- | --- | --- |
| `REC-P0-012` | `ReminderCompletionIsolationTest.firedAlarmRecordsExecutionWithoutCompletingTrackedEvent` | PASS：向 delivery handler 传入合成 `ACTION_FIRE`，隔离数据库记录 `FIRED`、清除 `intentFor`，完成键仍为空且 revision 不变。 |
| `REC-P0-013` | `ReminderCompletionIsolationTest.autoCancelledReminderHasNoCompletionDeleteCallback` | PASS：通知带 `FLAG_AUTO_CANCEL` 且 `deleteIntent` 为空；取消通知后完成状态和 revision 不变。 |

测试运行于 Robolectric API 34。数据库重定向到唯一临时文件，未打开或修改应用用户数据库。通知权限在 Robolectric shadow app 中授予。

## 回归证据

- 原提交时的定向与全量结果记录在本报告对应的 `pc-issue-015-*` 日志中。后续全量回归发现原测试的 `onReceive` 直接调用与并发数据库轮询存在夹具问题，详见 [`PC-ISSUE-020`](PC-ISSUE-020-reminder-receiver-test-race.md)。
- 稳定化后的提交后定向复跑：2/2 PASS。日志：`.local/android-test/reports/freeze-20261023/pc-issue-020-reminder-dispatch-targeted-final-20261023.log`，SHA-256 `2010C351D82318FC0E3D1154BE91AD64FECD4CC8B1025117870DC8C5D2D2A673`；JUnit XML SHA-256 `0AB606BDA5615C40146FDEBE0B74FCDB3EC2197DC6321CA895400C5972ABD131`。
- 稳定化后的完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。BUILD SUCCESSFUL；384/384 测试通过，55 个 suite，0 failures/errors/skips；Lint 0 errors、55 warnings。日志：`.local/android-test/reports/freeze-20261023/pc-issue-020-reminder-dispatch-full-gradle-20261023.log`，SHA-256 `A4A67F15FCBA59BBBBE543943337501DAFD2ECA7A37ECBF3DE3A41664F155783`。全量 XML 归档于 `.local/android-test/reports/freeze-20261023/pc-issue-020-full-junit/`。
- 稳定化构建 APK SHA-256：`EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`。全量结果含 383 个已跟踪测试和 1 个受保护的未跟踪测试 `DraftDeckFeedbackTest.kt`；该文件未修改或暂存。

## 边界与未完成项

`ACTION_FIRE` 是传给 delivery handler 的合成输入，不证明 Android `AlarmManager` 实际送达，也不覆盖 `onReceive`/`goAsync` 的框架生命周期；shadow `NotificationManager` 的取消不模拟用户在真实通知栏滑动关闭。尚未进行真实 Android 设备上的闹钟触发、通知展示/手势、Doze、后台限制、重启或 OEM 行为验收。因此两条只签候选 handler/通知对象层 PASS，系统级及设备级门槛保持 NOT TESTED，核心冻结维持 HOLD。

## 变更与提交

- 初始代码变更：新增 `app/src/test/java/dev/powerfulcalendar/reminders/ReminderCompletionIsolationTest.kt`。后续 `PC-ISSUE-020` 将 handler 可见性从 private 调整为模块内可调用，以同步验证 delivery 逻辑；`onReceive` 的运行时线程与 `goAsync` 行为未修改。无 UI、数据库 schema 或用户数据改动。
- 测试提交：`d74d55a3bb05893606f7da223beaa0b5f7a5819d`（`测试(冻结): 验证提醒触发不推断完成状态`），已推送至 `origin/main`。
- 测试稳定化提交：`d60809b4133a45c0551019f1edabec1195d34cf3`（`测试(冻结): 消除提醒回归的并发锁冲突`），已推送至 `origin/main`。
- 本报告与台账更新单独提交，避免与测试提交混在一起。
