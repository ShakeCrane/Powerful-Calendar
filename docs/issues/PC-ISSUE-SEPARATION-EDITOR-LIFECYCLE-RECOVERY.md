# PC-ISSUE-SEPARATION-EDITOR-LIFECYCLE-RECOVERY

- **日期：** 2026-10-30
- **状态：** `PARTIAL / HOLD`
- **验收项：** 重建矩阵 `S0-ED-03`（`RECONSTRUCTED`；不伪称历史 209 项原始 ID）
- **源码/测试提交：** `e9a3bb1e58f694057cff79b7b4006d2d17b9faf3`（`test(分离): 覆盖应用编辑器生命周期恢复`，已推送到 `origin/codex/separation-s0-timeline-drag`）
- **生产代码：** 本 Issue 未修改生产代码；审计未发现可由当前证据确认的产品缺陷，补齐的是 `PowerCalApp` 生命周期观察器的集成证据。

## 结论

API 35 Robolectric 已直接运行 `PowerCalApp` 的生命周期观察器，验证干净新建编辑器经过 `ON_STOP` 不分配持久身份，真实修改经过 `ON_STOP` 保存为一张可恢复草稿，重复 stop/start 不重复创建或推进草稿版本。另通过实际 `Activity.recreate()` 验证旧 Activity 被销毁，关闭并重新打开隔离 Store 后仍可读出同一份草稿。

这些结果证明 Activity 重建路径下由 checkpoint 写入的 Store 数据可恢复。它们没有杀掉 Android 应用进程，也没有运行 Android WebView、实际皮肤故障回退或设备/OEM 生命周期。因此 `S0-ED-03` 仍是 `PARTIAL / HOLD`。

## 只读审查与调用链

1. Native 新建入口调用 `PowerCalApp.openNewEvent()`，只创建内存中的 `NewEventEditSession` 和表单；新会话本身不写数据库。
2. `PowerCalApp` 的 `ON_STOP` 观察器读取当前活动会话。新建会话 checkpoint 在进程级 `PowerCalStores.appScope` 中运行；会话按业务 Diff 决定是否写草稿。`LifecycleCheckpointGate` 防止同一会话在同一 stop 周期重复 checkpoint，并在 `ON_START` 后重置。
3. `NewEventEditSession.checkpoint()` 对已保存但内容未变化的草稿返回原草稿，不递增版本；首次有 Diff 时建立一张草稿及唯一身份。
4. Web Host 有独立路径：Activity 停止时 `webEditing.checkpoint()`，切换皮肤时 `prepareSkinChange()`。现有 Bridge 测试 `switchingSkinsDurablyFlushesCurrentDraftBeforeEndingWebSession` 验证切换前写入；`NativeSkinRecoveryTest` 只验证 Native 页面能置于全屏 Web skin 之上，不构成真实 WebView 故障恢复。

本次未发现 Native `ON_STOP` 当前实现的确定性写入缺陷。此前主要缺少从 `PowerCalApp` observer 进入编辑会话并观察真实 Store 结果的回归，而不是只有直接调用 `session.checkpoint()` 的 Core 测试。

## 实施内容

新增 `app/src/test/java/dev/powerfulcalendar/ui/v2/PowerCalAppLifecycleCheckpointTest.kt`，使用唯一测试数据库，测试前后保存并恢复全局皮肤/引导偏好：

- `cleanTransientEditorDoesNotCreateRowsWhenPowerCalAppStops`：打开无 Diff 的 MEMO 编辑器并发送 `ON_STOP`；草稿、待处理项、正式事件、永久身份、序号水位、队列水位和提交数均保持初值。
- `dirtyTransientEditorIsCheckpointedOnceAcrossRepeatedStopCycles`：ALARM 标题修改后经生产生命周期 observer checkpoint；仅出现一张 `PENDING` 草稿，正式事件和提交数为零；再经过 `ON_START`/`ON_STOP`，草稿身份、版本和队列位置保持不变。
- `dirtyTransientEditorIsDurableAfterActivityRecreation`：真实调用 Robolectric `Activity.recreate()`；断言旧 Activity 到达 `DESTROYED`、新 Activity 到达 `RESUMED`。等待 checkpoint 后关闭 Store 并重新打开同一隔离数据库，仍读取到唯一的 `ED03_ACTIVITY_RECREATE` 草稿，且没有正式事件或提交记录。

该测试没有修改生产生命周期架构，也没有改动 AI/ICS 等其他来源的持久候选。

## 验收结果

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| `PowerCalApp` 生命周期集成测试 | `PASS` | `PowerCalAppLifecycleCheckpointTest` **3/3**，0 failures/errors/skips。三个测试分别覆盖干净 stop、真实修改与重复 stop/start、Activity 重建并重开 Store。 |
| 编辑会话及相关 UI 回归 | `PASS` | 强制定向重跑：`PowerCalAppLifecycleCheckpointTest`、`NewEventEditSessionTest`、`LifecycleCheckpointGateTest`、`NewEventEditorComposeTest`，合计 **14/14**；命令使用 `--offline --rerun-tasks --console=plain --max-workers=1`。日志 SHA-256：`54AB78D3DC42AEBB443B2C637C640AAA10D9286420783771799A18DCCF7EBFC3`。[日志](../../.local/android-test/reports/separation-s0-ed03-20261030/focused-final-r2.log) |
| 完整离线 Android 门禁 | `PASS` | `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain --max-workers=1`；**721/721 JVM，99 suites，0 failures/errors/skips**；88 actionable tasks（5 executed / 83 up-to-date）；Lint **0 errors / 53 warnings**。日志 SHA-256：`062D9B9A21F436A111315A871E881ABCF6D4A87EE6EAB3F106679C9907BE0F1A`。[日志](../../.local/android-test/reports/separation-s0-ed03-20261030/full-gradle-final-r2.log) |
| APK 构建 | `PASS`（AndroidTest 仅组装） | Debug APK：67,513,497 bytes，SHA-256 `71C8E45C967CAFDA3BC77FBCD2062AD25D20801B2CDFE8062DCFFAB4B24B097C`。AndroidTest APK：1,557,196 bytes，SHA-256 `371A9C1AF76B5F0530FE4A69FA2FAF9BF1A053256DC8B24F7264129930653E`。没有安装或运行 Instrumentation。 |
| ADB / 模拟器 | `NOT TESTED` | `E:\android-sdk\platform-tools\adb.exe devices -l` 结果只有空设备列表；`E:\android-sdk\emulator\emulator.exe` 不存在。 |
| OS 进程死亡与冷启动恢复 | `NOT TESTED` | Robolectric `Activity.recreate()` 仍处于同一测试进程；Store close/reopen 证明 SQLite checkpoint 可重开，不证明 Android 进程突然终止时异步 `appScope` 工作已完成。 |
| Android WebView、TalkBack、皮肤切换/加载失败设备时序、物理/OEM | `NOT TESTED` | Bridge 单测通过不构成 WebView、系统辅助功能或设备证据；当前没有连接的 ADB 设备或 SDK Emulator。 |

完整 Gradle 门禁最终日志为本 Issue 增强版 Activity 销毁断言及 Store 重开测试之后运行。定向和完整测试过程中未发现产品失败。测试编写早期一次 Kotlin 编译因无效测试导入失败；移除错误导入后，定向和完整门禁均通过，该失败不涉及生产代码。

## 剩余风险及后续

- `PowerCalStores.appScope.launch` 将 checkpoint 放入进程级异步任务。正常 Activity 重建已通过 Robolectric 验证；若进程在任务完成前被系统强杀，是否会丢失最后一次内存输入尚未通过冷进程/设备实验确定。当前不将此项标记为 PASS。
- Web Bridge 的 `prepareSkinChange()`、明确关闭、Native fallback 各有窄路径测试，但尚未验证 Android WebView 被销毁、重建、崩溃及切换失败时序。
- 用户负责实体设备测试；本机没有 Emulator，因此本次不安装 APK、不清理设备数据，也不声称 Android Instrumentation 已通过。
- `S0-ED-03` 保持 `PARTIAL / HOLD`。可继续推进下一验收项 `S0-ED-04` 的源码与 JVM/可用 Robolectric 验收；整个 Skin separation 阶段及 Release QA 仍未完成。

## 本地证据位置

详细日志位于 Git 忽略目录 `.local/android-test/reports/separation-s0-ed03-20261030/`。本报告和测试没有包含数据库副本、凭据、个人日历内容或原始设备日志。
