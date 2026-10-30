# PC-ISSUE-SEPARATION-NATIVE-HOST-ADAPTER-NAMESPACE — Native Host 适配层与 renderer 目录隔离

- 日期：2026-10-30
- 状态：**本切片 CODE COMPLETE；定向测试与构建门禁已验证。完整 JVM 回归保留 1 项既有间歇失败，Android 设备 UI NOT TESTED；整体分离仍未完成。**
- 代码提交：`aab3e52e7e6edafb814f39124041353419ebf8c7`，已推送至 `origin/feat/skin-first-separation`

## 根因

`:native-skin` 已承载 Native 时间轴、卡组、月份、Memo、AI、设置、导航、编辑器、Picker、Overlay 和 Chrome 的实际 Compose renderer。但 app 仍在 `ui/v2/nativeui` 源目录中保留三个 Host 文件：时间轴的 Core 记录投影、Draft Deck 的 Store/会话和表单桥接、Native renderer 注册/路由。这些文件由 Host 持有是正确的，目录却把 Host 代码和真正的 Native renderer 混在一起，也没有自动门禁防止 renderer 回流 app。

## 修改

- 将 Host 文件移到清晰的 app Host 层：`TimelineScreen.kt` → `NativeTimelineHostAdapter.kt`，`DraftDeck.kt` → `NativeDraftDeckHost.kt`，`NativeVisualSkin.kt` → `NativeSkinHostRegistration.kt`。Native profile 注册对象同步重命名；Store、Core、会话、渲染器选择及回调逻辑不变。
- 将 Host 投影测试移出 `app/.../nativeui` 测试包，并更新生产调用点、单元测试和 instrumentation 的旧包导入。
- 扩展 `:app:verifyNativeSkinBoundary`：若 app 主源码再次出现 `**/nativeui/**/*.kt`，门禁失败并列出路径。实际 Native UI 继续留在 `:native-skin`。
- 没有改 Clear 源码/资源、冻结 Core、默认皮肤、持久化语义或 Native fallback。

## 验证

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| Native 投影、Host Compose 与相关 renderer 定向测试 | **49/49 PASS，11 suites** | 含 `NativeDeckHostProjectionTest`、时间轴标题/滚动/字体/横屏、正式编辑器、玻璃渲染及 Runtime Compose。Gradle 67 tasks；命令筛选器和运行结果见 `.local/android-test/reports/native-host-adapter-namespace-20261030/` |
| 全量 JVM | **FAIL：784 项，1 项失败，783 项通过** | `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` 在 60 秒内只观察到 `page=MEMO, memoCount=0`。完整日志 SHA-256 `90003F110A68405FB128D3F9F52654F19209B6F08273F5E831CECA6C6E237A3B`。这是历史全量回归中已反复出现的同一间歇失败；独立复跑 **1/1 PASS**，日志 SHA-256 `4E83DFB5E79F5F042274F338BBB8D3042564B50AE2ABCC203816CACC072A76E6`。根因尚未确定，因此不能将该次全量标成 PASS。 |
| Native/Skin 与源边界门禁 | **PASS** | `:app:verifyNativeSkinBoundary`、`:app:verifySkinSourceBoundary`；Host app 中 `nativeui` 源文件及旧 Host 导入扫描均为空 |
| AndroidTest 编译 | **PASS** | `:app:compileDebugAndroidTestKotlin`；覆盖本次包搬迁后的 8 个 instrumentation 调用点 |
| Debug APK 与 lint | **PASS** | `:app:assembleDebug`、`:app:lintDebug`；144/144 actionable tasks。Lint **0 errors / 55 warnings**。APK `app/build/outputs/apk/debug/app-debug.apk`，66,500,119 bytes，SHA-256 `8A793156DF9690840E28A0B19A0D5F26FB02F53FACE8B5403E1E097E5F1DA7D2`。构建日志 SHA-256 `B451F7C98966C87180F01FD46A5CC802FBBC9F19FB82EDC2C4314D798D947933` |
| 补丁检查 | **PASS** | `git diff HEAD --check`、暂存区 `git diff --cached --check` |

所有详细日志保存在忽略目录 `.local/android-test/reports/native-host-adapter-namespace-20261030/`。Android SDK 使用本机既有 `E:\android-sdk`，通过单次 shell 环境变量指定，没有修改 SDK 或仓库 `local.properties`。

## 可复现命令

PowerShell 环境：`$env:ANDROID_HOME='E:\android-sdk'`。

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:testDebugUnitTest `
  --tests '*NativeDeckHostProjectionTest' `
  --tests '*NativeDirectEventEditorModuleTest' `
  --tests '*NativeTimelineInspectionAdapterComposeTest' `
  --tests '*NewEventEditorComposeTest' `
  --tests '*TimelineTitleComposeTest' `
  --tests '*TimelineWingScrollComposeTest' `
  --tests '*TimelineFontScaleComposeTest' `
  --tests '*TimelineForcedLandscapeComposeTest' `
  --tests '*GlassComposeTest' `
  --tests '*GlassRenderTest' `
  --tests '*RuntimeSkinComposeTest' `
  --offline --rerun-tasks --max-workers=2 --console=plain

.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary `
  :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin `
  :app:assembleDebug :app:lintDebug `
  --offline --rerun-tasks --max-workers=1 --console=plain

.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary `
  :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug `
  --offline --rerun-tasks --max-workers=1 --console=plain
```

中间命令运行完整 JVM 套件并保留了上述 1 项失败；末尾命令单独重跑其余构建门禁并成功。

## 尚未签核

- 当前 `adb devices -l` 没有在线设备，`emulator -list-avds` 为空。AndroidTest 源码已编译，但 API 设备交互、Native/Skin 对照回放、实体/OEM 与 TalkBack 均 **NOT TESTED**。
- 本切片清理的是 Host/renderer 源目录边界，不等于 Native/Skin 功能 parity；SEP-01～SEP-16 仍按各自证据状态验收。Studio 完整组件树/绑定编辑、故障恢复和 AI 自动化等工作继续开放。
- Memo 分页全量间歇失败保留为独立回归缺口；目前只确认单项可通过，尚无稳定复现根因或修复。

## Git

代码提交 `aab3e52e7e6edafb814f39124041353419ebf8c7`（`refactor(皮肤分离): 将原生宿主适配移出渲染目录`）已快进推送。该 Issue 的报告、Parity 矩阵补记和进度状态作为后续单独文档提交，方便独立回档。
