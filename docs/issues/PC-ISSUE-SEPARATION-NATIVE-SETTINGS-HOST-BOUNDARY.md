# PC-ISSUE-SEPARATION-NATIVE-SETTINGS-HOST-BOUNDARY

- 日期：2026-10-30
- 本项状态：**CODE COMPLETE；729/729 JVM 测试、边界检查、AndroidTest Kotlin 编译、Debug 构建与 lint PASS；设备验收 NOT TESTED**
- Native 整体分离状态：**PARTIAL**，仍在 `:app` module
- 工作分支：`codex/skin-first-mainline`
- 集成分支：`origin/feat/skin-first-separation`
- 基线：`cc3f43f663f85040e9f97c49911fd9f9a7ba018c`
- 实现提交：`32018ac8e2c72ee1815a57134f0cc0b45c88a99b`（`refactor(皮肤分离): 将原生设置受保护操作移入宿主`）

## 目标与根因

Native Settings 页面原先直接从 Android `Context`/私有偏好读取个人资料和 AI 凭据，并直接依赖 AI 设置存储、文件授权、Core 事件记录、提醒状态和重复事件引擎。Native 页面因此承担了 Host 数据访问与安全设置读写，不能作为只依赖呈现接口的 Native 实现继续拆分。

## 修改

- 新增 `HostProtectedSettings.kt`：把个人资料和 AI 凭据编辑器、私有偏好读写、`AiSettingsStore` 与文件授权操作留在 Host。表单行为与原 Native 实现保持一致。
- `SettingsVisualContext` 提供两个受控组合槽位，Native Settings 通过这些槽位显示 Host 管理的个人资料和 AI 设置，不接收原始密钥或偏好对象。
- Host 将正式事件数量、序号、数据库信息、提醒诊断、重复事件摘要、权限状态和 AI 批次状态整理为 `NativeSettingsDiagnostics`，再传给 Native Settings。Native 页面只消费展示 DTO 和 Host 回调。
- `PowerCalApp` 为 Host 诊断快照提供提醒/通知权限状态及当前时间；保留 Native Settings 原有路由、动作和展示内容。
- 新增 Compose 冒烟测试，验证通过 `SettingsVisualContext.Native()` 路径能渲染 Host 所有的资料与凭据槽位。
- 未修改 Clear 文件。本次也没有纳入两份既有未提交的 WebSkin instrumentation tests。

## 验证

成功命令（分离 worktree 中临时设置 `ANDROID_HOME=E:\android-sdk`）：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

| 检查 | 结果 |
|---|---|
| `:app:verifySkinSourceBoundary` | **PASS** |
| 全量 `:app:testDebugUnitTest` | **729/729 PASS**，101 个 XML suite，0 failures / 0 errors / 0 skipped |
| `NativeToolsPageProtectedSlotsComposeTest` | **1/1 PASS**，包含于以上全量测试 |
| `:app:compileDebugAndroidTestKotlin` | **PASS**；只编译，没有执行仪器测试 |
| `:app:assembleDebug` | **PASS**；APK 66,401,756 bytes，SHA-256 `D069B86CD9CD93561481A45A9BECA78D9F20FCFED19BEEDBFB5F005E88C6A160` |
| `:app:lintDebug` | **PASS** |
| `git diff --check` | **PASS** |
| Native Settings 源码依赖扫描 | **PASS**；`NativeToolsPage.kt` 未发现可执行代码对 Context、Android 服务、Core 记录/Store、AI 私有设置存储的直接依赖；命中项仅为注释 |
| API 35 / Android 设备交互 | **NOT TESTED**：ADB 设备列表为空，`emulator -list-avds` 无 AVD |

忽略文件证据目录：`.local/android-test/reports/separation-native-host-settings-20261030/`

- 全门禁日志 `full-gate-final.log` SHA-256：`8ABEFEE633088E906B62740AC7AF21FC4B7238CDE836238DE49A64D30CAA062E`
- Compose 冒烟测试 JUnit XML SHA-256：`4A5D7FF1C626DA7F2BFEA14F3666E6AD98EE673B2C5424A7E28C1150CBEFF9EA`
- APK：`app/build/outputs/apk/debug/app-debug.apk`（上述 SHA-256）

## 未完成与后续

- `SettingsVisualContext` 交给自定义 Skin Renderer 的对象仍包含 `UiDiagnostics`、`List<TemporalRecord>` 和 `PowerStore.AiSession`。本切片的 DTO 只用于 Native 路径，不能据此宣称所有 Renderer 的 Host/Core 边界已收紧。
- Native 页面还使用 `AiBatchRunner.Mode`、共享 `GlassEffects`、`InstalledSkin` 及父包 UI 类型；Native 源码仍属于 `:app`。形成 Gradle module 之前须将这些接口改为 skin API 数据/回调，并避免依赖环。
- 新增 Compose 测试只验证 Host 槽位沿真实 Native 调用链可见；没有在 UI 自动化中填写并保存资料/AI 设置。原设置表单逻辑与存储 API 被迁移保留，设备端键盘/权限/布局行为未验收。
- 本报告不签核 SEP-01～SEP-16 全功能等价，不代表 Skin Studio 能编辑 Native Kotlin，也不代表 Clear 已修复。

## 继续项

下一步沿 Native 分离主线收窄 `SettingsVisualContext` 的 Core-bearing 字段，确定自定义 Renderer 需要的最小展示投影和受控动作，并为迁移增加接口回归测试；随后继续拆除 Native 页面剩余父包/Core 依赖，再评估独立 Gradle module。

## Follow-up — Settings renderer DTO boundary (2026-10-30)

The previous residual “`SettingsVisualContext` still exposes `UiDiagnostics`, `TemporalRecord` and `PowerStore.AiSession`” was resolved by [PC-ISSUE-SEPARATION-SETTINGS-CONTEXT-DTO](PC-ISSUE-SEPARATION-SETTINGS-CONTEXT-DTO.md), implementation commit `2ecd3ec9fad9df7c6bf08796432f9ad325547df9`. The renderer context now carries display DTOs, skin IDs, Host callbacks and injected composable slots. The independent Gradle module boundary and other screen-context dependencies remain open; this does not complete Native separation.
