# PC-ISSUE-SEPARATION-NATIVE-OVERLAY-MODULE

- 日期：2026-10-30
- 状态：**Native Overlay renderer CODE COMPLETE；SEP-12 仍 INCOMPLETE**
- 代码提交：`b44671bc6fa7981e68c2a79c4a9119c21ae82ec6`（已推送至 `origin/feat/skin-first-separation`）
- 范围：Native AlertDialog 与全窗口 Dialog 的 Compose renderer 分离。**未修改 Clear 生产源码。**

## 根因

`SkinnableOverlays.kt` 同时定义 Overlay skin context/API、直接绘制 Material3 `AlertDialog` / Compose `Dialog` 的 Native fallback，以及编译/运行时 skin 的 Host 路由。Native modal renderer 因此仍与 app Host 耦合。

## 修改

- 将 `OverlayType`、`OverlayVisualContext` 和 `OverlayVisualRenderer` 移入 `native-skin/.../OverlayVisualContext.kt`。
- 新增 `NativeOverlaySkin`，接收 Host 提供的标题、正文、图标、按钮、`Modifier`、dismiss callback 与 `DialogProperties`，统一绘制 Native AlertDialog / Dialog。
- `SkinnableOverlays.kt` 保留 Host renderer 选择、运行时 skin 路由、打开/关闭时机及回调绑定；Native profile 直接委托模块 renderer。
- 新增 Compose 回归，验证 Host 提供的确认/取消动作均由原闭包执行，Native renderer 不误调用 fallback slot。全屏 Window/Dialog 与现有 Draft Deck 测试一起回归。

## 验证证据

环境：Windows / PowerShell，离线 Gradle，Robolectric API 35；SDK `E:\android-sdk`。

Focused 命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest --tests dev.powerfulcalendar.ui.v2.NativeOverlayModuleTest --tests dev.powerfulcalendar.ui.v2.RuntimeSkinComposeTest --tests dev.powerfulcalendar.ui.v2.NativeSkinRecoveryTest --tests dev.powerfulcalendar.ui.v2.GlassComposeTest --offline --rerun-tasks --max-workers=2 --console=plain
```

Focused Compose/Host 回归 **27/27 PASS**：`GlassComposeTest` 19、`NativeOverlayModuleTest` 1、`NativeSkinRecoveryTest` 1、`RuntimeSkinComposeTest` 6。

| 验证 | 结果 |
| --- | --- |
| Native 与皮肤源码边界检查 | PASS |
| 完整 JVM 回归 | **755/755 PASS**；0 failures、0 errors、0 skipped |
| AndroidTest Kotlin 编译 | PASS；instrumentation 未在设备执行 |
| Debug APK 构建 | PASS |
| `lintDebug` | PASS |
| Android 设备 UI | NOT TESTED：`adb devices -l` 无在线设备 |

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，66,483,794 bytes，SHA-256 `D0787B4047AB75FFB6F85E2AE71D100FDF58446627560548B6251E4803367DD2`。
- 构建/AndroidTest 编译/lint 日志：`.local/android-test/reports/native-overlay-separation-20261030/build-gate.log`，SHA-256 `BB3E47247750432BEFC63660D6A04386184DB01F6CEA28B3CE3493B88B69EFF0`。
- 全量 JVM 日志：`.local/android-test/reports/native-overlay-separation-20261030/full-unit.log`，SHA-256 `D164DABEAD1EBFC9662FAEF011A847E483B21E28849E055FDBCEEEFB5767F18A`。
- Focused 重跑日志：`.local/android-test/reports/native-overlay-separation-20261030/focused-retry.log`，SHA-256 `619A859EB7A00B7566AEC52D1EA573E1E3DD080E339D2028404403EABA9B71ED`。
- 并行 Gradle 曾短暂占用 `native-skin` 的 `classes.jar`，首次 focused 命令以文件锁失败；未终止并行进程，待其结束后同命令重跑成功。锁冲突日志 SHA-256 `CA0E4679662FCAC628BF98C849F31A0D510DCAC2ED4DC453E4E3E4C1F625791C`。
- 代码提交 `b44671bc6fa7981e68c2a79c4a9119c21ae82ec6`，父提交 `5b43e5d`。

## 未签核范围

SEP-12 仍因系统 Picker、多窗口/IME/返回导航以及 Native/Skin 等价验证未完成而保持 **INCOMPLETE**。本 Issue 不证明实体设备/TalkBack、WebView、Skin Studio 编写 Overlay 源码或整体分离通过。Clear 保持当前状态，不在本 Issue 修复。
