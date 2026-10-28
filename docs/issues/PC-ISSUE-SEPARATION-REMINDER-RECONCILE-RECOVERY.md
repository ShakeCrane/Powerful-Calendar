# PC-ISSUE-SEPARATION-REMINDER-RECONCILE-RECOVERY

日期：2026-10-28

状态：**本问题修复并通过 JVM/构建回归；Android 运行时验证未执行。SEP-11 仍 NOT TESTED。**

## 问题与范围

精确闹钟权限曾被拒绝、调度意图因此进入 `BLOCKED` 后，用户在系统设置中授予权限并触发提醒重建时，已有正式事件仍无法恢复提醒。该问题涉及提醒意图的重协调恢复。本 Issue 不更改事件、提醒或数据库 schema，不改变调度目标时间、权限语义和事件身份，也不签核整个提醒设置的 Native/Skin 等价。

## 根因与修复

- `PowerStore.dueIntents()` 只选取 `PENDING`、`SCHEDULED`。首次调度因缺少精确闹钟权限而写入 `BLOCKED` 后，后续 `ReminderScheduler.reconcile()` 永远查不到该行。
- 单一 400 条混合查询还会让大量较早的 `BLOCKED` 项反复占满批次，挤掉正常的 `PENDING` / `SCHEDULED` 重建项。
- 新增 `PowerStore.reconciliationIntents()`，将 `PENDING` / `SCHEDULED` 与 `BLOCKED` 分别做稳定的 `instant_ms,event_seq,trigger_id` 有界查询，每组最多 400 项。这样未恢复权限的行不会挤占待创建或待重建的闹钟。原 `dueIntents()` 保持不变，避免改变其他调用者的筛选语义。
- `ReminderScheduler.reconcile()` 改为使用重协调专用查询。原有 revision guard、调度与权限判断、单项异常隔离逻辑保持不变；授权状态恢复后，同一意图可按原路径再次调度。
- 当成功重排后已没有任何 `BLOCKED` 意图时，清除固定 ID 的“提醒未能按精确时间安排”状态通知；仍有其他受阻意图时保留该通知。
- 新增生产调度器回归：拒绝权限后状态为 `BLOCKED`、没有 AlarmManager 项且状态通知出现；授予权限后同一意图变为 `SCHEDULED`、只建立一个闹钟并清除最后一条状态通知。另用 400 条较早的 `BLOCKED` 意图验证 `PENDING` 与 `SCHEDULED` 仍进入同一轮重协调批次。

## 修改文件

- `app/src/main/java/dev/powerfulcalendar/core/PowerStore.kt`
- `app/src/main/java/dev/powerfulcalendar/reminders/NotificationHelper.kt`
- `app/src/main/java/dev/powerfulcalendar/reminders/ReminderScheduler.kt`
- `app/src/test/java/dev/powerfulcalendar/reminders/ReminderValidationTest.kt`
- `docs/agent-handoff/PROGRESS.md`
- `docs/issues/PC-ISSUE-SEPARATION-REMINDER-RECONCILE-RECOVERY.md`

工作区另有 `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinManagementInstrumentedTest.kt` 的并发未提交修改；它未由本 Issue 修改、暂存或提交。完整构建曾编译它，因此本次 AndroidTest APK 哈希包含该工作区状态，不应视为仅由本 Issue 源码构建的可复现产物。

## 缺陷复现证据

先添加回归测试，再运行原实现。结果为 **18 tests completed, 1 failed**：

```text
ReminderValidationTest > blockedExactIntentIsRescheduledAfterPermissionIsGranted FAILED
expected:<SCHEDULED> but was:<BLOCKED>
ReminderValidationTest.kt:149
```

- 日志：`.local/android-test/reports/separation-20261028/reminder-reconcile-recovery/blocked-intent-retry-pre-fix.log`
- SHA-256：`F1F9763DF1C6C68275DEE4E4B3E0F0C2C571256A8059C4DE298A6257D6EFD783`

## 回归与构建证据

### 定向回归

命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --no-daemon --rerun-tasks --console=plain `
  --tests dev.powerfulcalendar.reminders.ReminderValidationTest
```

结果：**19/19 PASS**，包括权限恢复、恢复后状态通知清除、400 条受阻项不饿死待处理/已安排项。最终日志 SHA-256：`BB61DBA314ED42542AD4F0D717F8A41692039C8165D69F79ED99C3FFE66FA78E`；JUnit XML SHA-256：`E9F12494AA65EA587C23931EEEF81E2E15060C6B2A14050A85BAF7F70B554745`。

### 完整离线 Gradle 门禁

命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug `
  :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果：**BUILD SUCCESSFUL；639/639 JVM tests、88 suites、0 failures/errors/skips、88/88 tasks**。`verifySkinSourceBoundary`、Debug APK、AndroidTest APK 编译和 lint 均通过；lint **0 errors / 52 warnings**。

- 完整日志 SHA-256：`DC6738A219118E7EB8955AAD8C79754FB11119F17BDD90C41D4F7B25BCEDF7E0`
- Lint XML SHA-256：`8FF4C74A759FBDEA385AC7F08CBFD6790FEE29F6E6538865BA8A7CBD577C0839`
- Debug APK SHA-256：`424DB1D284345B4934752A6B6408AFF85DBF85F43F9D3233CA158CD22D7636DC`
- AndroidTest APK SHA-256：`6B89A35252F0106B6A1B9DB45918C22303CA422391644EB0A77A329F413BAEB9`（包含上述未纳入本 Issue 的并发 AndroidTest 源文件）
- 证据目录：`.local/android-test/reports/separation-20261028/reminder-reconcile-recovery/`

### 静态工作区检查

`git diff --check` 通过。定向测试和完整门禁均使用离线 Gradle。环境使用 JDK `C:\Program Files\Java\jdk-20` 与 SDK `E:\android-sdk`，通过进程级 `JAVA_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT` 设置；没有写入或覆盖 `local.properties`。该机器没有可用的 `emulator.exe`，本次没有执行 Android 模拟器、WebView 或实体设备测试。

## 结论与未覆盖范围

修复覆盖“精确闹钟权限由拒绝转为允许后，重协调恢复原 `BLOCKED` 提醒”、受阻项不挤占其他状态，以及全部受阻项恢复后的状态通知清除，并由缺陷前失败、复核发现的边界补测和修复后回归共同证明。独立只读复核促成了批次隔离和通知生命周期处理。未证明真实系统权限变更广播、厂商后台限制、重启恢复、真实 AlarmManager 触发或物理设备行为；这些仍需 Android runtime/设备验证。权限仍被拒绝时保留 `BLOCKED` 是预期行为。

该结果只关闭本 Issue 的实现与 JVM/构建证据，不代表 SEP-11 或完整提醒设置 Host/Skin parity PASS；SEP-11 仍 **NOT TESTED**，S4 仍开放。本次不修改并发验收 CSV，也不签核未覆盖的验收项。

## 提交

实现与回归提交：`1b29ec632d4312d5c7d4e3471f316833d270f391`，父提交 `1610297706d39bb531a8a2d28b0b1b8feae9a36e`，提交说明 `fix(提醒): 恢复受阻闹钟并清理状态通知`。报告与进度记录作为独立文档提交随后推送至 `origin/codex/separation-skin-settings`。
