# PC-ISSUE-SEPARATION-NATIVE-DRAFT-DECK-MODULE

- 日期：2026-10-30
- 状态：**CODE COMPLETE；SEP-08 仍为 PARTIAL**
- 代码提交：`02d385c951cd3ca1f1cb0ad01dcae0decad804ed`（已推送至 `origin/feat/skin-first-separation`）
- 范围：Native 草稿卡组与共享事件表单的 Compose 渲染模块化。**Clear 未修复、未改动生产源码。**

## 根因

`DraftDeck.kt` 同时承载了 Compose 卡组/表单渲染和 Host 职责：`DeckController` 观察、串行命令、暂存/提交/撤销/冲突/恢复、Core 校验、字段转换、搜索投影及系统日期时间选择器。正式编辑器 `DirectEventEditor.kt` 也直接复用位于 Host 文件内的表单组件。因此 Native 表现层依赖 Core/Store/Controller 所在的 `:app`，无法单独归入 `:native-skin`。

## 修改

- 将草稿卡组和共享事件表单的 Compose renderer 移至 `native-skin/.../NativeDraftDeckScreen.kt`。
- 在 `skin-api/.../NativeDeckModel.kt` 新建仅含显示值、回调和视觉 Compose slots 的 Native Deck API，不向 renderer 暴露 Store、Controller 或授权能力。
- 将 `app/.../DraftDeck.kt` 收敛为 Host adapter，保留唯一 Controller observer、串行持久化流程、校验/转换、Core 搜索投影以及日期/时间 picker 解释。
- 正式编辑器通过相同模块表单 renderer 绘制，编辑会话、保存/删除确认及写入授权继续由 Host 控制。
- 表单编辑映射回当前 `DraftForm`，未显示字段（如来源、完成状态键、重复规则元数据）保持不变。
- 新增 `NativeDeckHostProjectionTest`，覆盖 Host 投影往返、纯日期 DEADLINE 精度及清除重复规则的既有语义。
- 更新 V4 分层契约与 SEP-08 路线图状态。

## 验证证据

环境：Windows / PowerShell，离线 Gradle，仓库 SDK `E:\android-sdk`。最终执行命令：

```powershell
$env:ANDROID_HOME='E:\android-sdk'
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=2 --console=plain
```

| 验证 | 结果 |
| --- | --- |
| Native/UI 模块边界检查 | PASS |
| 皮肤源码边界检查 | PASS |
| 完整 JVM 回归 | PASS，752/752；0 failures、0 errors、0 skipped |
| 本功能相关 Compose/Host 投影用例 | PASS，31/31：`GlassComposeTest` 19、`NewEventEditorComposeTest` 3、`DirectEventEditorComposeTest` 6、`NativeDeckHostProjectionTest` 3 |
| AndroidTest Kotlin 编译 | PASS；未安装或执行 instrumentation |
| Debug APK 构建 | PASS |
| Debug lint | PASS |
| ADB 设备 | NOT TESTED：ADB 无已连接设备；没有设备端可视、输入法、TalkBack 或 WebView 交互证据 |

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，66,467,410 bytes，SHA-256 `62F436927B15421FF2ECF9067914559C873FEF0CCA59E2176C161E55E1FEF34B`。
- 完整门禁日志：`.local/android-test/reports/native-deck-separation-20261030/final-gate.log`，SHA-256 `DE9BEC7E95ABAF31033379A125FFF355F51DDC68DAC5232DEC3490BB776BB1A7`。
- 单测 XML：`app/build/test-results/testDebugUnitTest/`（110 suites/files；汇总 752 tests、0 failures、0 errors、0 skipped）。
- 当时的 Git 基线：`d6e9089`；验证后的代码提交：`02d385c951cd3ca1f1cb0ad01dcae0decad804ed`。

## 未签核范围

本次证明 Native renderer 的源码模块边界和 JVM/编译构建行为，不代表完整 SEP-08 通过，也不代表 Clear 与 Native 功能等价、Android 设备行为已通过、Skin Studio 能编辑此 Native Compose renderer，或分离目标已整体完成。设备交互仍待可用模拟器/设备验证。Clear 当前已知问题独立保留，本 Issue 不处理。
