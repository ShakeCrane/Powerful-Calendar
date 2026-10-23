# PC-ISSUE-017 — 提醒 Trigger 与事件类型/身份分离

日期：2026-10-23

状态：**SQLite/Robolectric 候选回归通过；整体核心冻结仍为 HOLD**

## 结论

现有存储代码将提醒计划写入独立的 `schedule_intents`，并未发现 Trigger 会把 Activity 转成 Alarm 或另建一条事件。原测试已检查 Activity 可选精确 Alarm 提醒，但缺少对事件 kind、Trigger 所属 `eventSeq` 及事件总数的直接断言。本 Issue 补强既有测试证据，没有修改生产逻辑。

## 候选验收项

| 候选项 | 测试 | 结果 |
| --- | --- | --- |
| `REC-P0-015` | `ReminderPersistenceTest.activityCanChooseExactAlarmOrMessageIndependentlyOfKind` | PASS：Activity 选择精确 Alarm Trigger 后，schedule intent 仍引用同一个 `eventSeq`，存储记录仍为 `ACTIVITY`，store 内仍只有该事件。 |

测试运行于 Robolectric API 34，使用每次测试独立的 SQLite 数据库；不涉及 UI、Android 设备或用户数据库。测试方法数量没有增加，只补充三项明确断言。

## 回归证据

- 提交后定向复跑：`ReminderPersistenceTest` **2/2 PASS**，0 failures/errors/skips。日志：`.local/android-test/reports/freeze-20261023/pc-issue-017-trigger-kind-targeted-final-20261023.log`，SHA-256 `6F9B2230A8106E07E9953FB5C9A2267D87CDB94A6FA17904C79E97DEB8B7930B`；JUnit XML SHA-256 `3373800E3B61639B83B537EE132BC68A6C5DF66511D204E5C2D546B0167A0DBB`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。结果 BUILD SUCCESSFUL；**383/383 PASS**，55 个 suite，0 failures/errors/skips；Lint 0 errors、55 warnings。日志：`.local/android-test/reports/freeze-20261023/pc-issue-017-trigger-kind-full-gradle-20261023.log`，SHA-256 `56837893A25A52E940516836DBEAD2FC3E96FAFDA4217DA624C74E32E6A5518E`。全量 JUnit XML 归档于 `.local/android-test/reports/freeze-20261023/pc-issue-017-full-junit/`；目标类 XML SHA-256 `DDAD0BD238D0F17817120B3423F995F893E951D92732C9F6B4F6856E54FDC0BC`。
- 完整回归运行于基线 `ebf3d5400ed47c5077b8a4069224cc702d05c78d`，唯一源码差异是本报告对应的测试断言，随后提交为 `9ed3b2c5cdaef406f79f528e590bccd675e062af`。提交后定向复跑通过。
- 完整工作树总数包括 382 个已跟踪测试和 1 个受保护的未跟踪测试 `DraftDeckFeedbackTest.kt`。该文件未修改、未暂存；结果不能描述为干净提交树的计数。
- Debug APK SHA-256：`63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`。

## 变更与提交

- 唯一代码变更：加强 `app/src/test/java/dev/powerfulcalendar/core/ReminderPersistenceTest.kt` 中现有 Activity 提醒测试；无生产代码、UI、schema 或业务数据改动。
- 测试提交：`9ed3b2c5cdaef406f79f528e590bccd675e062af`（`测试(冻结): 验证提醒类型与事件身份独立`），已推送到 `origin/main`。
- 本 Issue 报告及冻结台账更新将作为独立文档提交推送。
