# PC-ISSUE-SEPARATION-REMINDER-DIAGNOSTICS-TRACK-A

日期：2026-10-28

状态：**本文件记录 Track A Host 实现与其独立测试证据；Track B 已完成并与 Track A 集成，最终合并结论见 `docs/issues/PC-ISSUE-SEPARATION-REMINDER-DIAGNOSTICS.md`。Android WebView/AVD/实体设备仍未测试；SEP-11 总体 NOT TESTED。**

> 本文件是 Track A 证据补充，不替代下方合并 Issue 报告；其最后 handoff 段落描述的是 Track A 交接时状态。

## 范围与根因

SEP-11 原有皮肤设置页只投影提醒权限布尔值，没有逐项提醒状态；`reminders.reconcile` 只调用 Scheduler 后刷新状态，无法区分运行中、完成、受阻、未完成或异常。单靠既有有界查询也无法判断候选是否超过 400/100 条处理上限，可能把未覆盖批次误报为成功。

本切片只扩展 Host/Kotlin、Kotlin 测试、JSON Schema 与文档。没有修改 Clear/Studio 源码、冻结合同、Store schema 或提醒调度策略。

## 实现

- 新增 `WebSkinReminderProjection`：仅 `skinSettings` / `SETTINGS` 状态填充 `reminderDiagnostics` 和 `reminderReconcile`；其他页面两字段为 JSON `null`。
- 延续 Native `NativeToolsPage` 的 `record.enabled && reminderMinutes >= 0` 可见性规则。Native 诊断仍使用原有前 40 项；Skin 只在 Host 内存拿到完整候选集后投影最多 40 项并正确设置 `truncated`。
- 每条诊断只序列化 `displayId`、`title`、`titleTruncated`、`kind`、`desiredState`、`androidHint`、`lastExecution`。display ID 是合成序号；`eventSeq` 只作为稳定排序的 Host 内部 tie-breaker，不序列化。`occurrenceKey`、notes、location、凭据及异常文本不输出。标题上限 120 UTF-16 code units；Android hint 和执行结果使用生产值白名单。
- 执行结果枚举核对了 `ReminderScheduler`、`ReminderReceiver` 全部 `recordExecution` 写入值：`FIRED`、`MISSED`、`BLOCKED`、`SNOOZED`、`SNOOZE_INEXACT`、`IGNORED_STALE`、`IGNORED_SNOOZE_STALE`。`MISSED` / `SNOOZED` 只进入 `lastExecution`；仅 intent `FIRED` 或执行结果精确为 `FIRED` 才映射到 `desiredState=FIRED`。
- `ReminderScheduler.reconcile()` 现在返回只读 `ReconcileReport` 计数。每个原有处理组仍最多处理 400 条；CANCELLED 清理仍最多处理 100 条。新增 401/101 哨兵读取标记 `truncated`，哨兵本身不处理，也不计入 `checked`。`checked` 包含实际处理的 active intent 和 CANCELLED 清理尝试。没有扩张每轮调度上限。
- Host 操作状态按 `IDLE → RUNNING → SUCCEEDED/FAILED` 变化。RUNNING 时设置中的重协调选项禁用，Host handler 仍防重入。缺少权限形成的 `blocked` 不算失败；任何 `pending`、异常或 `truncated` 都不能显示成功。异常内容不进入皮肤状态；Runner 异常及取消都会结束 RUNNING 状态，取消正常传播。
- 新增正式状态片段 Schema：`docs/contracts/web-skin-reminder-state-v1.schema.json`；测试比较 schema 的字段、上限、枚举与真实生产 projection/report。
- 更新 `docs/V3_HOST_BRIDGE_EXTENSION.md`，包括 Track B Preview 对 `truncated=true` 的独立提示要求。

## 回归测试

定向命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --no-daemon --console=plain `
  --tests dev.powerfulcalendar.ui.v2.WebSkinReminderProjectionTest `
  --tests dev.powerfulcalendar.ui.v2.WebSkinReminderDiagnosticsIntegrationTest `
  --tests dev.powerfulcalendar.reminders.ReminderValidationTest
```

结果：**27/27 PASS**，无失败、错误或跳过。其中 projection/schema 4/4、实际 `PowerCalApp`/Bridge Robolectric 集成 1/1、ReminderValidation 22/22。覆盖 settings-only/null 投影、41 条有效提醒截断、一条 disabled 记录过滤、隐私字段白名单、标题长度和稳定排序、执行枚举映射、RUNNING option 禁用、防重入、blocked 成功、pending/异常/truncated 失败、401 active 与 blocked sentinel、CANCELLED `checked` 计数。

完整离线 Gradle 门禁：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug `
  :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果：**BUILD SUCCESSFUL；647/647 JVM tests、90 suites、0 failures/errors/skips、88/88 tasks**。source boundary、Debug APK、AndroidTest APK 编译通过；lint **0 errors / 52 warnings**。

| 证据 | 结果 | SHA-256 |
| --- | --- | --- |
| 完整门禁日志 | `.local/android-test/reports/sep11-tracka-host-projection/full-offline-gate.log` | `FE0F1C4B17D61D78B5C1A3A8E3FAD38AD8957EBD9A1CC92781045E82BAFD2EDC` |
| 定向日志 | `.local/android-test/reports/sep11-tracka-host-projection/focused-host-reminder-tests.log` | `5910148770F3DEC173E5E69DFF9DFE84F36EA5B05A4FB85358BC1CC65EFA7563` |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | `3BB6C68CD8374D066F2AA4DF62FF4473CAA74C66195CDC7651B28624FD553A4B` |
| AndroidTest APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `06F91487E2465995F89CF2C9D6A9DE53843BBD5430E191423A249BEA109BA97E` |
| Lint XML | `app/build/reports/lint-results-debug.xml` | `54A24EFF1A4B6B2C388C2DD6932A187FA953C285DB24F2BFC4A3DB9F51AF6C5C` |

其余静态检查：`git diff --check` PASS；Schema `python -m json.tool` PASS。JDK `C:\Program Files\Java\jdk-20`，Android SDK `E:\android-sdk`，Gradle 离线。

## 剩余缺口

- Track B 在交接当时仍未消费 `reminderReconcile.truncated`。此缺口已由合并工作树中的 Clear/Studio 修复，并通过完整 Chrome E2E 复核；字段契约、最终文案、截图、测试哈希和测试 harness 修复见 `docs/issues/PC-ISSUE-SEPARATION-REMINDER-DIAGNOSTICS.md`。
- 本机未执行 Android emulator、实际 Android WebView 或实体/OEM 设备测试；APK 编译不代表设备通过。
- 系统权限回跳、真实 AlarmManager 运行、设备重启和厂商后台限制仍不是本切片验证范围。
- SEP-11 还包括凭据/个人设置、完整 ICS、备份恢复和皮肤管理等多项功能；整体矩阵仍为 **NOT TESTED**。

## 修改文件

- `app/src/main/java/dev/powerfulcalendar/reminders/ReminderScheduler.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinReminderProjection.kt`
- `app/src/test/java/dev/powerfulcalendar/reminders/ReminderValidationTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinReminderProjectionTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinReminderDiagnosticsIntegrationTest.kt`
- `docs/contracts/web-skin-reminder-state-v1.schema.json`
- `docs/V3_HOST_BRIDGE_EXTENSION.md`
- `docs/issues/PC-ISSUE-SEPARATION-REMINDER-DIAGNOSTICS-TRACK-A.md`
- `docs/agent-handoff/PROGRESS.md`

## Git 状态

基线：`6af2c0a6e14ac0669c44a0abe0fb30e77e1810df`，detached HEAD，worktree：
`E:\Project\Powerful-Calendar\.local\android-test\worktrees\sep11-tracka-host-projection`。

按用户要求未创建提交；没有修改主 worktree、Track B worktree、Clear/Studio 源码、Android instrumentation protected file 或其他受保护资产。

## 合并后最终记录（2026-10-28）

Track A 已与 Track B 合入 `codex/separation-skin-settings`。实现/测试提交：`75d8e21b003007f54ff7369cef4c50d041512bc4`（`feat(分离): 增加皮肤提醒诊断与同步状态`）。合并后完整门禁 **647/647 JVM / 90 suites / 88 tasks**，Studio Python **72/72**、Chrome Headless E2E **PASS**；完整哈希和截图索引见最终报告 `docs/issues/PC-ISSUE-SEPARATION-REMINDER-DIAGNOSTICS.md`。本记录所述 Track A 手递交时状态保留为历史事实；当前 Track B 已通过截断场景并集成。没有 Android WebView/AVD/实体设备证据，SEP-11 整体仍 **NOT TESTED**。
