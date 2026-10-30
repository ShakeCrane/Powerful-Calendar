# PC-ISSUE-SEPARATION-SETTINGS-CONTEXT-DTO

- 日期：2026-10-30
- 本项状态：**CODE COMPLETE；731/731 JVM 测试、边界检查、AndroidTest Kotlin 编译、Debug 构建与 lint PASS；设备验收 NOT TESTED**
- Native 整体分离状态：**PARTIAL**，仍未形成独立 Gradle module
- 工作分支：`codex/skin-first-mainline`
- 集成分支：`origin/feat/skin-first-separation`
- 基线：`596277433c0b1175b63ac0e4f0f658c49ea2fc4b`
- 实现提交：`2ecd3ec9fad9df7c6bf08796432f9ad325547df9`（`refactor(皮肤分离): 收窄设置渲染上下文`）

## 目标与根因

前一 Host 设置切片已让 Native Settings 页面直接消费展示 DTO，但 `SettingsVisualContext` 仍把 `UiDiagnostics`、完整 `List<TemporalRecord>`、`PowerStore.AiSession`、`InstalledSkin`、`GlassEffects` 和 `AiBatchRunner.Mode` 暴露给同模块 Skin Renderer。Renderer 实际只使用少数显示值和 Host 动作；这些 Host/Core 类型增加了 Native 与渲染接口的编译耦合，也让接口边界与合同描述不一致。

## 修改

- 新增 `SettingsVisualContext.kt`，作为 Settings Skin API：仅持有 `SettingsDisplaySnapshot`、`SettingsSkinChoice`、`SettingsVisualEffects`、皮肤 ID/Host 回调及 Compose slots；API 类不引用 Core、AI 实现、Host 设置表单或 Native 页面类型。
- Host 在 `projectSettingsDisplaySnapshot()` 内将 Core/Android 输入投影成显示数据：待处理/正式数量、序号和身份计数、数据库信息、权限与 AI 锁定状态、提醒展示行和重复事项预览。
- `Native()`、个人资料和 AI 凭据内容均改为注入式 Composable slots；Native fallback 继续由 Host 提供，原生设置和受保护表单的显示路径保留。
- Renderer 看到的皮肤选项只含 `id/name/builtIn`；已安装皮肤导出回调只传 ID，由 Host 解析原始 `InstalledSkin`。
- Settings API 移除了页面未消费的图片选择、文本草稿、AI 草稿、AI busy 和批次启动回调；AI surface 自己的入口未改。
- 新增/扩展 3 项回归：经生产 `ToolsPage` 路由显示 Host 受保护槽位、反射检查 Settings API 不保留禁用的 Host/Core/Native 类型、验证 Host 输入投影为显示快照。
- 更新 UI Skin Contract，记录 Settings snapshot 的字段与提醒标题/执行提示属于本地日历展示数据。Clear 源码、资产与包未修改。

## 验证

成功命令（分离 worktree 中临时设置 `ANDROID_HOME=E:\android-sdk`）：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

| 检查 | 结果 |
|---|---|
| `:app:verifySkinSourceBoundary` | **PASS** |
| 全量 `:app:testDebugUnitTest` | **731/731 PASS**，101 个 XML suite，0 failures / 0 errors / 0 skipped |
| `NativeToolsPageProtectedSlotsComposeTest` | **3/3 PASS** |
| `:app:compileDebugAndroidTestKotlin` | **PASS**；仪器测试只编译，没有安装/执行 |
| `:app:assembleDebug` | **PASS**；APK 66,401,756 bytes，SHA-256 `30CA4E76CEB75470A90BCBC9333068D6B9A5CCF80D9BB489291F9A2F8C805446` |
| `:app:lintDebug` | **PASS** |
| Settings API 依赖扫描与反射回归 | **PASS**；无 Core、AI batch 实现、`InstalledSkin`、`GlassEffects` 或 `NativeToolsPage` 类型进入 API 声明 |
| `git diff --check` | **PASS** |
| API 35 / Android 设备交互 | **NOT TESTED**：ADB 设备列表为空，AVD 列表为空 |

忽略文件证据目录：`.local/android-test/reports/separation-settings-context-boundary-20261030/`

- 定向 Settings 测试日志 `focused-test-final.log` SHA-256：`79FEB3905E98D71FD261F5A9791DEFB1E76E4ABA27F13B8DFA6B4B6B45403F4B`
- 全门禁日志 `full-gate.log` SHA-256：`FC79740BB2A0DBB62E4DFE600292396AAA26AF2C22B1E0FF36B9E12A5720AB1A`
- Settings JUnit XML SHA-256：`48453E723B35CD05A3E31FE05629E34D707EA6D4C922097DEFE027BBF3B8F40C`
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`（上述 SHA-256）

## 未完成与后续

- 本接口仍是 `:app` 内部 Kotlin API；Native UI、Host 与 Skin registry 还没有独立 Gradle 编译依赖边界。
- Month、AI、Timeline、Deck 等其他 Renderer/context 仍有各自 Core 或 Host 类型依赖。该 Issue 只闭合 Settings context，不代表 Native 所有页面已能移出 `:app`。
- 真实 Android Compose/WebView、TalkBack、实体/OEM 设备验收未完成；没有用 JVM 结果替代这些证据。
- `SettingsDisplaySnapshot.reminders` 含本地事件标题、调度提示和最近执行文本；受审计的同 APK Kotlin Skin 源码可读取这些明确提供的展示值。它不获得 Store、数据库或修改 API。
- 本 Issue 不签核 SEP-01～SEP-16 功能等价、不代表 Skin Studio 能编辑 Native Kotlin、不代表 Clear 已修复。
