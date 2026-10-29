# PC-ISSUE-SEPARATION-REMINDER-EXECUTION-DIAGNOSTICS

- **日期：** 2026-10-30
- **状态：** `PARTIAL / HOLD`
- **验收项：** 重建矩阵 `S0-ST-21`（RECONSTRUCTED；不是历史 209 项原始 ID）
- **实现提交：** `e2faec0012a3781eee3f00891a23fe2fda006da2`（`feat(分离): 补齐提醒执行状态诊断`，已推送到 `origin/codex/separation-s0-timeline-drag`）

## 结论

Clear Settings 现在显示最多 40 项与 Native Tools 同源的提醒调度及执行状态。行内显示格式序号、标题、期望调度状态、Android 权限提示和最近执行结果。列表为只读；它不提供提交、删除或调度修改操作。只有 Bridge 页面为 `SETTINGS` 时才投影这些行，其他页面收到空数组。

投影映射、API 35 Robolectric/Compose 集成、Chrome 156/CDP Clear 源码和 Skin Studio Preview Mock 回归均通过。最终完整 Gradle 门禁 **710/710 JVM tests、96 suites、0 failures/errors/skips**；强制全量重跑结果相同。真实 Android WebView→Host、设备端无障碍和物理/OEM 行为未验证；因此本验收项保持 **`PARTIAL / HOLD`**，不得据此推断设备 parity 或整体 GO。

## 根因与修复

1. S0-ST-18 至 S0-ST-20 已把 Clear 的 Settings 路由用于通知权限、精确闹钟权限和手动重建提醒，但 Bridge state 只包含这些状态，没有 Native Tools 的 `UiDiagnostics.reminders`，Clear 因而缺少执行状态列表。
2. Native 已从启用且有提醒的正式事件中构造 `ReminderDisplay` 列表并限制为 40 项。Clear 复用这组数据，没有新增数据库查询、提醒执行或独立的排序规则。
3. `WebSkinReminderDiagnosticsProjection` 只投影显示所需的序号、标题、调度意图字段、Android 提示和最近执行结果；不传备注、地点或执行详情。Clear 使用文本节点渲染标题/状态并设置 list/listitem 语义，不截获其他业务操作。
4. 修正 parity matrix 的过期说明：Clear Settings 已承载 S0-ST-18 至 S0-ST-21 的提醒控制和诊断；更广泛的资料、凭据、备份和数据库诊断仍由 Native Tools 管理。

## 修改文件

- `PowerCalApp.kt`：只在 Settings 页面把提醒诊断列表加入 Web Host state。
- `WebSkinReminderDiagnosticsProjection.kt`：新增有界、只读的 Native 数据投影；序号沿用 `TemporalRecord.displaySeq()` 格式。
- `WebSkinManualReminderReconcileIntegrationTest.kt`、`WebSkinReminderDiagnosticsProjectionTest.kt`：验证 Host 集成、Settings-only、数据边界、40 项上限及空意图/执行结果。
- Clear `app.js`、`index.html`、`style.css`：增加可访问的执行状态区域和安全文本显示。
- `v3-studio.js` 与两份 Chrome/CDP 回归脚本：加入非写入 Mock 行并验证结果。
- `app/src/main/assets/powercal-clear-default.pcskin`：重建 Android 使用的 canonical Clear 包。
- 本报告、parity matrix 和 `docs/agent-handoff/PROGRESS.md`：记录本项验收及证据。

## 验收证据

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| 投影单元测试 | `PASS` | 2 项：45 个输入只输出前 40 项并保持输入顺序；验证格式序号、调度状态、最近结果、备注/地点排除，以及缺失 intent/execution 以 JSON null 表示。 |
| Host / Compose 集成 | `PASS` | 新增 API 35 Robolectric 用例：在 Settings 投影 ALARM 的 `BLOCKED` 意图和 `MISSED` 结果；验证备注和 execution detail 不进入 Bridge state，正式 commit、身份计数和 next-sequence 不因投影/既有重建反馈发生变化；转到 Timeline 后列表为空。 |
| 定向回归 | `PASS` | `WebSkinBridgeTest`、`WebSkinManualReminderReconcileIntegrationTest`、`WebSkinReminderDiagnosticsProjectionTest` 共 **12/12**。日志 SHA-256：`910116087C1346F09CA42385BE3BB8C2AD8FCF52C83081F07A35A1EC1B2BAA1D`。 |
| 完整离线 Gradle 门禁 | `PASS` | `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain`；**710/710、96 suites、0 failures/errors/skips**；88 actionable tasks（20 executed / 68 up-to-date）；Lint 0 errors / 53 warnings。日志 SHA-256：`A3401F8984EFB5B0DFDA87160DD1122EEAED0E4A79CC7E609473CA38284A8CED`。 |
| 全量强制重跑 | `PASS` | `:app:testDebugUnitTest --offline --rerun-tasks --console=plain`；**710/710、96 suites、0 failures/errors/skips**，32/32 tasks executed。日志 SHA-256：`C1C3BF1D4205602CEF6489A52DD73D0295006E50946298296D2D3C47C9879293`。 |
| Skin Studio Python | `PASS` | `python -m unittest discover -s tools/skin-studio/tests -v`：**61/61**。日志 SHA-256：`1C88CA585025C20D67F7379468CCEFD8D331F6848C4A6E5B950B92A7D9121655`。 |
| JavaScript/CJS 语法 | `PASS` | Clear `app.js`、Studio `v3-studio.js`、Clear CDP 与 Studio Mock CDP 四个文件均通过 `node --check`。日志 SHA-256：`5E6674658B6E66910BBBDE6618BC599C8CFF9F0982FD9F7036766EA333D62EC9`。 |
| Chrome/CDP Clear 源码 | `PASS` | Chrome 156 Headless + protocol Mock Host 显示 `#0017`、`BLOCKED`、精确闹钟提示和 `MISSED`。结果 JSON SHA-256：`8A7D7ACE8F85EB1BD346DE18968452A18228B1DF189B30B90A26EBF2354BE23F`；截图 SHA-256：`405872E0E987C463222D667CA560465E9F91891EC9CA0D58425D503911A09346`。 |
| Skin Studio Preview Mock | `PASS` | 从 canonical 9-file Clear 包进入 Settings，显示同一诊断字段；Mock 明确不启动 Android Host、不修改真实调度。结果 JSON SHA-256：`70E799A610341C3A0CCB64D1C907EC1DD9C42158CEA29FE376999A29F42186D7`。 |
| Clear 包与 APK 内置资源 | `PASS` | canonical 包 9 files / 393,427 bytes；源码构建结果与 APK 内 `assets/powercal-clear-default.pcskin` 完全一致，SHA-256：`0c3eb7dfca45cbc9e33df8c783a3ef30459d753fa5a87f0e4e00a0f66012ce4d`。 |
| APK 构建 | `PASS` | Debug APK 67,509,213 bytes，SHA-256 `26A44B528FA446D17E5FB617EF84DF81F5C32B65383139258A36E5E895F08980`；AndroidTest APK 1,557,196 bytes，SHA-256 `371A9C1AF76B5F0530FE4A69FA2FAF9BF1AFA053256DC8B24F7264129930653E`。AndroidTest APK 已构建但没有执行 instrumentation。 |
| Android 设备 / 模拟器 | `NOT TESTED` | `E:\android-sdk\platform-tools\adb.exe devices -l` 无设备；`E:\android-sdk\emulator\emulator.exe` 不存在。真实 Android WebView→Host、TalkBack/实际无障碍服务、实体设备及 OEM 系统行为未验证。 |

## 已知限制与剩余工作

- Chrome 使用桌面 Chromium 和协议级 Mock Host；Studio 使用非写入 Preview Mock。两者都不是生产 Android WebView/设备证据。
- 新投影保持 Native 现有列表顺序和 40 项上限；本次未改变 Native 的提醒筛选、调度、触发、存储或恢复语义。
- 首次普通 JVM runner 的投影测试遇到 Android `JSONObject` 未 mock 的测试环境错误。测试改为仓库现有 Robolectric API 35 runner 后，定向与完整回归通过；生产逻辑无需因该测试夹具错误修改。
- S0-ST-21 是已实现并通过本地 JVM/浏览器验证的切片，但设备项仍 `NOT TESTED`，矩阵状态为 `PARTIAL / HOLD`。不能把此结果提升为整项或整体分离 GO。

## 可追溯证据位置

以下报告目录在 Git 忽略的 `.local/android-test/` 下：

- 完整门禁日志：[full-gate-final.log](../../.local/android-test/reports/separation-s0-st21-reminder-diagnostics-20261030/full-gate-final.log)
- 强制全量重跑日志：[full-rerun-final.log](../../.local/android-test/reports/separation-s0-st21-reminder-diagnostics-20261030/full-rerun-final.log)
- 定向回归日志：[focused-final.log](../../.local/android-test/reports/separation-s0-st21-reminder-diagnostics-20261030/focused-final.log)
- Skin Studio Python 日志：[skin-python-final.log](../../.local/android-test/reports/separation-s0-st21-reminder-diagnostics-20261030/skin-python-final.log)
- JavaScript/CJS 语法日志：[node-checks.log](../../.local/android-test/reports/separation-s0-st21-reminder-diagnostics-20261030/node-checks.log)
- 包构建日志：[package-build.log](../../.local/android-test/reports/separation-s0-st21-reminder-diagnostics-20261030/package-build.log)
- Clear Chrome/CDP 结果与截图：[clear-cdp-final](../../.local/android-test/reports/separation-s0-st21-reminder-diagnostics-20261030/clear-cdp-final/)
- Studio Preview Mock 结果：[studio-mock-cdp-final](../../.local/android-test/reports/separation-s0-st21-reminder-diagnostics-20261030/studio-mock-cdp-final/)
