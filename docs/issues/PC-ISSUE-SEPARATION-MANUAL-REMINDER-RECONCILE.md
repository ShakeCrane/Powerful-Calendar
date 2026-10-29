# PC-ISSUE-SEPARATION-MANUAL-REMINDER-RECONCILE

- **日期：** 2026-10-30
- **状态：** `PARTIAL / HOLD`
- **验收项：** 重建矩阵 `S0-ST-20`（不是历史 209 项原始 ID）
- **实现提交：** `15479aa`（`fix(分离): 完善提醒手动重建与反馈`，已推送到 `origin/codex/separation-s0-timeline-drag`）

## 结论

Clear「设置 → 提醒可靠性」现已提供手动重建提醒调度入口。请求通过仅接受固定字段的 v3 Host Bridge，并且只有在 Settings 页面才会执行。Native Tools 与 Clear 复用同一结果反馈：正在执行、成功、权限阻塞或读取/调度失败；不会把异常显示成成功。

显式手动重建会用稳定游标分批遍历全部待/已排程意图，并清理全部取消意图。启动和普通生命周期恢复仍使用原有的有界单批路径。API 35 Robolectric 和 Chrome/CDP 回归通过。真实 Android WebView、系统权限页面、操作系统 AlarmManager 及设备厂商投递尚未验证，因此本行保持 **`PARTIAL / HOLD`**。

## 根因与修复

1. Native Tools 原来的 `onReconcile` 无论调度是否失败都会直接显示成功文案，异常没有用户可见的失败状态。
2. Clear v3 没有手动重建动作或对应状态投影，用户无法从皮肤入口重试或判断权限阻塞。
3. 调度器生命周期重建每次最多读取 400 条待/已排程意图及 100 条取消意图。显式重建若复用该单批行为，超过上限时可能遗留数据却显示成功；先前的反馈逻辑也没有检查剩余的 `SCHEDULED` 或 `CANCELLED` 行。
4. 增加只供显式手动恢复调用的 `reconcileAll`，分别按 `(instant_ms, event_seq, trigger_id)` 和 `(event_seq, trigger_id)` 使用稳定 keyset 游标。它逐批执行，避免一次把全部本地意图加载到内存；现有启动、广播和普通生命周期调用仍使用有界路径。
5. `PowerCalApp` 将 Native Tools 和 Clear action 收敛到同一个幂等状态处理器；它读取 `BLOCKED`/`PENDING` 残留并区分阻塞、失败与成功。异常只显示通用可操作提示，不把数据库错误细节暴露到皮肤。
6. Clear Settings 增加无障碍状态区和重建按钮，执行中禁用按钮。Host 使用严格 schema 解码，并在非 Settings 页面拒绝该动作。Studio Mock 可显示和接收命令，但不会访问 Android Host 或真实调度器。

## 修改内容

- `PowerStore.kt`：新增待调度与取消意图的稳定 keyset 分页查询。
- `ReminderScheduler.kt`：保留原有有界 `reconcile`，新增显式全量分页 `reconcileAll` 和尝试/失败计数。
- `PowerCalApp.kt`：Native Tools 与 Clear 复用进度、成功、阻塞、错误反馈；仅 Settings 的 Web action 可执行。
- `WebSkinHost.kt`、`WebSkinBridgeTest.kt`：新增 `reconcileReminders` 闭合 schema 和 Settings 页面门控测试。
- `WebSkinManualReminderReconcileIntegrationTest.kt`：增加 API 35 Robolectric Host 集成测试，覆盖权限阻塞后恢复、Store 读取异常、超旧批次上限清理及正式数据计数不变。
- Clear 源文件、Studio Mock 与 CDP 回归脚本增加重建动作和状态显示。
- `app/src/main/assets/powercal-clear-default.pcskin`：重建 canonical Clear 包。

## 验收证据

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| Bridge schema 与页面门控 | `PASS` | `WebSkinBridgeTest` 的 6 项用例验证动作解析、额外字段拒绝及 Settings-only 规则。 |
| 权限阻塞与恢复 | `PASS` | API 35 Robolectric/Compose 集成用例：未允许精确闹钟权限时保持 `BLOCKED` 并显示权限提示；模拟授予后转为 `SCHEDULED`。正式事件、提交数、identity 数和 next-sequence 快照均不变。 |
| 异常反馈 | `PASS` | 隔离数据库中注入 schedule 表读取失败，Host 状态成为 `ERROR` 并显示重建失败提示；不会报告成功。 |
| 超批次全量重建 | `PASS` | 隔离测试库注入 405 条待处理意图。一次手动重建跨越 400 条待处理和 100 条取消清理上限，最终 `schedule_intents` 为空；正式提交、身份及序号计数不变。 |
| 定向 JVM / Host 回归 | `PASS` | 5 suites / **31 tests**（`WebSkinManualReminderReconcileIntegrationTest`、`WebSkinBridgeTest`、`WebSkinExactAlarmSettingsIntegrationTest`、`ReminderValidationTest`、`BootReceiverRecoveryTest`），0 failures/errors/skips。日志 SHA-256：`B959A8190CC4C483031E0570620B0DA28D62BD3DF57CF2F15A0CEA85A8829395`。 |
| 完整离线 Gradle 门禁 | `PASS` | `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain`；**707/707 JVM tests / 95 suites / 0 failures/errors/skips**；88 actionable tasks（21 executed / 67 up-to-date）；Lint 0 errors / 53 warnings。日志 SHA-256：`216D33C820593F5F3D677CD4A94433FA60D3B56499CC9DD0AF2629ED81BF5B1D`。 |
| 全量单元测试强制重跑 | `PASS` | `:app:testDebugUnitTest --offline --rerun-tasks --console=plain`：**707/707、95 suites**；32 tasks executed。日志 SHA-256：`AF68D299D0A1E337CAE85F88FAA64B1E96F783676142D071118F89F555ADE086`。 |
| Skin Studio Python | `PASS` | **61/61**；日志 SHA-256：`701D53953197FE929CD14DE58A1F76628E3D8748C45736E960C1CF1AB3C2D1B2`。 |
| Clear 源文件 Chrome/CDP | `PASS` | Chrome 156 Headless + protocol Mock Host；验证按钮发出 `reconcileReminders`，并渲染 `BLOCKED` 与 `ERROR` 消息；同时验证通知/精确闹钟权限文案和 Native 恢复入口。结果 JSON SHA-256：`D29E5D03E7FC5636161F267CDC0A2F84C9B2E421D712E87A4C5936E6E1E3308C`；截图 SHA-256：`F505F6CC087D9CFAFBFC77450C24660384DC200D1F8A6BF0CE83016DAD638D4F`。 |
| Studio Preview Mock | `PASS` | 收到重建动作并明确记录不会调用 Android Host 或修改真实调度。结果 JSON SHA-256：`A68A80B6A346BA57563CF787DAB3B298BE90DB0377A0E8B31ACBEAB7388570D2`。 |
| JavaScript、Clear 包与 APK 资源 | `PASS` | 4 个变更 `.js`/`.cjs` 文件通过 `node --check`。Canonical Clear 为 9 files / 392,812 bytes；源码包与 Debug APK 内 asset SHA-256 一致：`7d93db92e1f3b327c606a1278f07d9b9c14f58ce42db162afbebc43228a49e3b`。校验日志 SHA-256：`924AD67C8FC27529903E9D53F68765FBDBDB8DE0BA7853381CA362678BE74CFF`。 |
| APK 产物 | `PASS` | Debug APK 67,507,512 bytes，SHA-256 `1405DCD412AF5BA67FD25E98B9F60DF0423F746E6BE26560CA115088BAFC19EE`。AndroidTest APK 1,557,196 bytes，SHA-256 `371A9C1AF76B5F0530FE4A69FA2FAF9BF1AFA053256DC8B24F7264129930653E`；只组装，未执行 instrumentation。 |
| Android 设备/模拟器 | `NOT TESTED` | `E:\android-sdk\platform-tools\adb.exe devices -l` 返回空设备列表；`E:\android-sdk\emulator\emulator.exe` 不存在。真实 Android WebView→Host、OS 权限页面/AlarmManager 和 OEM 投递行为未验证。 |

## 可追溯证据位置

- [完整 Gradle 门禁日志](../../.local/android-test/reports/separation-s0-st20-manual-reconcile-20261030/full-gate.log)
- [全量强制重跑日志](../../.local/android-test/reports/separation-s0-st20-manual-reconcile-20261030/full-rerun.log)
- [定向回归日志](../../.local/android-test/reports/separation-s0-st20-manual-reconcile-20261030/focused-v8.log)
- [Skin Studio Python 日志](../../.local/android-test/reports/separation-s0-st20-manual-reconcile-20261030/skin-python.log)
- [Clear Chrome/CDP 结果与截图目录](../../.local/android-test/reports/separation-s0-st20-manual-reconcile-20261030/clear-cdp/)
- [Studio Mock 结果目录](../../.local/android-test/reports/separation-s0-st20-manual-reconcile-20261030/studio-mock-cdp/)
