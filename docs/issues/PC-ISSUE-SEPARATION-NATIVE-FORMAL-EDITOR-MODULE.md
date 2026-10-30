# PC-ISSUE-SEPARATION-NATIVE-FORMAL-EDITOR-MODULE

- 日期：2026-10-30
- 状态：**Native formal editor shell CODE COMPLETE；Host/Robolectric/全量 JVM TESTED；SEP-05 的设备验收仍未签核**
- 代码提交：`eb03209299766ee392623da1c7dabb8d4a918b97`（父提交 `2131a1bf455e1c3e705bac4b7ee6ce8089dcd71c`，已推送至 `origin/feat/skin-first-separation`）
- 范围：将正式事件编辑器的 Native 外层 Compose UI 从 `:app` 移入 `:native-skin`，并新增只含展示状态与 Host 回调的 `:skin-api` context。未改 Clear 源码、Core 或持久化语义。

## 根因

`DirectEventEditor.kt` 在 Host 中直接绘制 Native Surface、类型下拉、重复范围选择、滚动表单和保存/放弃/退出/删除控件。字段表单已复用 `:native-skin` 的 `NativeDeckEventForm`，但外层 Native UI 仍留在 app；同时该文件直接依赖 `EditSession` 和 Core 类型，使视图与会话、映射逻辑难以单独审查。

## 修改

- 在 `:skin-api` 增加 `NativeDirectEventEditorContext`、重复编辑范围展示类型及选项 DTO。
- 新增 `NativeDirectEventEditor` 到 `:native-skin`，移动 Native 编辑器外层布局、类型菜单、重复范围控件、错误区域、滚动表单、保存/放弃/退出操作区和删除确认 UI。
- `DirectEventEditor.kt` 保留 Host window、`EditSession`、当前表单转换、Picker 请求构造、Core 类型/重复范围映射和所有保存/退出/删除回调。模块只接收已投影显示值与显式动作回调。
- 删除确认继续经 Host 提供的 `NativeDeckAppearance.showDialog` 显示；实际删除仍只能由 Host 回调执行。
- 新增 Native renderer 直接回调测试；既有 `DirectEventEditorComposeTest` 与 `NativeDeckHostProjectionTest` 全部通过。

## 验证证据

环境：Windows / PowerShell，SDK `E:\android-sdk`，离线 Gradle，Robolectric API 35。ADB 设备清单为空。

Focused 命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.ui.v2.NativeDirectEventEditorModuleTest' --tests 'dev.powerfulcalendar.ui.v2.DirectEventEditorComposeTest' --tests 'dev.powerfulcalendar.ui.v2.nativeui.NativeDeckHostProjectionTest' --offline --max-workers=2 --console=plain
```

- Native renderer、正式编辑会话 Compose 回归和 form 投影测试合计 **10/10 PASS**。
- 首轮集成编译的测试导入包路径错误已修复；随后测试夹具改为先选重复范围、再修改字段，符合现有规则“有未保存修改时不能切换范围”。最终 focused rerun 全部通过。

完整离线门禁：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=2 --console=plain
```

| 验证 | 结果 |
| --- | --- |
| Native 与 skin 源码边界 | PASS |
| 完整 JVM 回归 | **758/758 PASS**；114 suites，0 failures、0 errors、0 skipped |
| AndroidTest Kotlin 编译 | PASS；设备 instrumentation 未执行 |
| Debug APK 构建 | PASS；66,483,794 bytes，SHA-256 `628FD54270793FA79037B92768D322DD70B52C534BFA6259B4E682944ADEA6B0` |
| `lintDebug` | PASS |
| Android 设备编辑器 UI | **NOT TESTED**：`adb devices -l` 无在线设备 |

- 完整门禁日志：`.local/android-test/reports/native-editor-shell-20261030/full-gate.log`，SHA-256 `04FC667901DCC1BAACF237B2B2851301151C9ECF8F545D9DDB0CC2DA8B2F2F2517`。
- Focused 最终重跑日志：`.local/android-test/reports/native-editor-shell-20261030/focused-final.log`，SHA-256 `4D285260D56306B74FFAFBF620C53DBE93D3A8F2628DCD532526C705249B37BC`。

## 未签核范围

本 Issue 证明 Native editor shell 的模块实现、Host 回调集成与 JVM/Robolectric 回归。它不证明真实 Android 设备上的 IME、窗口、TalkBack、尺寸/字号适配，不完成 SEP-05 的设备验收，也不表示所有 skins/Studio 能替换正式编辑器。Native fallback 保留；Clear 当前状态不在范围内且未修改。
