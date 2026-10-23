# PC-ISSUE-015 — 提醒触发与完成状态隔离

日期：2026-10-23

状态：**Robolectric 候选回归通过；Android 系统与真机验收仍未完成**

## 结论

本次审查未发现提醒触发器或通知对象会自动写入事件完成状态：提醒接收器只记录 `FIRED` 执行结果，通知不注册完成回调。原问题是这两项隔离合同缺少直接回归证据，而不是已复现的生产代码缺陷。因此本 Issue 仅补充测试，没有改动生产逻辑、UI 或数据库结构。

## 验收项与测试

| 候选项 | 测试 | 结果 |
| --- | --- | --- |
| `REC-P0-012` | `ReminderCompletionIsolationTest.firedAlarmRecordsExecutionWithoutCompletingTrackedEvent` | PASS：向接收器发送合成 `ACTION_FIRE`，隔离数据库记录 `FIRED`、清除 `intentFor`，完成键仍为空且 revision 不变。 |
| `REC-P0-013` | `ReminderCompletionIsolationTest.autoCancelledReminderHasNoCompletionDeleteCallback` | PASS：通知带 `FLAG_AUTO_CANCEL` 且 `deleteIntent` 为空；取消通知后完成状态和 revision 不变。 |

测试运行于 Robolectric API 34。数据库重定向到唯一临时文件，未打开或修改应用用户数据库。通知权限在 Robolectric shadow app 中授予。

## 回归证据

- 提交后定向测试：2/2 PASS，0 failures/errors/skips。日志：`.local/android-test/reports/freeze-20261023/pc-issue-015-reminder-completion-targeted-final-20261023.log`，SHA-256 `2010C351D82318FC0E3D1154BE91AD64FECD4CC8B1025117870DC8C5D2D2A673`；JUnit XML SHA-256 `912B76691F6C185AA5AEA95377663CFDE0FC2F21D2725F3627CC7BB61E7CF033`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。结果 BUILD SUCCESSFUL；382/382 测试通过，55 个 suite，0 failures/errors/skips；Lint 0 errors、55 warnings。日志：`.local/android-test/reports/freeze-20261023/pc-issue-015-reminder-completion-full-gradle-20261023.log`，SHA-256 `6719782E203DFCC6E477BFF6E5F56DFD717BEA8568514C0FB4FC14A925675CE7`。全量 JUnit XML 已归档至 `.local/android-test/reports/freeze-20261023/pc-issue-015-full-junit/`。
- Debug APK SHA-256：`63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`。
- 全量回归在测试代码提交前运行于 `9937eed73778370527c900f8a399b32e08574104`；提交 `d74d55a3bb05893606f7da223beaa0b5f7a5819d` 只包含该次回归所测的测试文件，测试源码与运行时相同。总数含 381 个已跟踪测试和 1 个受保护的未跟踪测试 `DraftDeckFeedbackTest.kt`；该受保护文件未修改、未暂存。

## 边界与未完成项

`ACTION_FIRE` 是测试直接发送给 receiver 的合成广播，不证明 Android `AlarmManager` 在系统中实际送达；shadow `NotificationManager` 的取消不模拟用户在真实通知栏滑动关闭。尚未进行真实 Android 设备上的闹钟触发、通知展示/手势、Doze、后台限制、重启或 OEM 行为验收。因此这两条只能签核为候选模型/receiver 层 PASS，系统级及设备级门槛保持 NOT TESTED，核心冻结维持 HOLD。

## 变更与提交

- 唯一代码变更：新增 `app/src/test/java/dev/powerfulcalendar/reminders/ReminderCompletionIsolationTest.kt`；无生产代码、UI、数据库 schema 或用户数据改动。
- 测试提交：`d74d55a3bb05893606f7da223beaa0b5f7a5819d`（`测试(冻结): 验证提醒触发不推断完成状态`），已推送至 `origin/main`。
- 本报告与台账更新单独提交，避免与测试提交混在一起。
