# PC-ISSUE-SEPARATION-NATIVE-CHROME-MODULE

- 日期：2026-10-30
- 状态：**Native Chrome renderer CODE COMPLETE；SEP-12 仍 INCOMPLETE**
- 代码提交：`a70c4d02ffd842e3e7c6ee0ca24699e3dde3bcec`（已推送至 `origin/feat/skin-first-separation`）
- 范围：Native root shell 和状态提示的 Compose renderer 分离。**未修改 Clear 生产源码。**

## 根因

`SkinnableChrome.kt` 同时承担根外壳/状态提示的 Native Compose 绘制、Native fallback 和 skin/runtime renderer 选择；`ChromeVisualContext` 与 renderer 接口也在 `:app`。因此 Native profile 的 root/notice 实现无法从 Host 模块中独立识别与审查。

## 修改

- 将 `ChromeVisualContext`、`ChromeVisualRenderer` 与 `ChromeType` 移到 `native-skin/.../ChromeVisualContext.kt`，避免为了 Compose UI 类型扩大 `:skin-api` 的 Android UI 依赖。
- 新增 `NativeChromeSkin`，负责 Native 根 Box 和状态提示。保留原背景色、状态栏/导航栏/IME inset、提示色彩、圆角、padding 与 polite live-region 语义。
- `SkinnableChrome.kt` 仍负责 Native/编译皮肤/Web/runtime renderer 选择、Native 恢复路径、`RuntimeSkinPickerHost` 和提示生命周期；`NativeVisualSkin` 显式调用模块 renderer。
- 新增 Robolectric/Compose 回归：Host 根内容正常显示且不误走 fallback slot；状态提示仍保留 polite live-region semantics。

## 验证证据

环境：Windows / PowerShell，离线 Gradle，Robolectric API 35；`E:\android-sdk`。最终 Native 模块位置下的 focused 命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest --tests dev.powerfulcalendar.ui.v2.NativeChromeModuleTest --tests dev.powerfulcalendar.ui.v2.RuntimeSkinComposeTest --tests dev.powerfulcalendar.ui.v2.NativeSkinRecoveryTest --tests dev.powerfulcalendar.ui.v2.GlassComposeTest --offline --rerun-tasks --max-workers=2 --console=plain
```

此 focused 命令 **BUILD SUCCESSFUL**；新增 `NativeChromeModuleTest` 的 2 个测试通过，且列出的既有运行时皮肤/恢复/Compose suite 均完成通过。

最终源码门禁结果：

| 验证 | 结果 |
| --- | --- |
| Native 与皮肤源码边界检查 | PASS |
| AndroidTest Kotlin 编译 | PASS；instrumentation 未在设备执行 |
| Debug APK 构建 | PASS |
| `lintDebug` | PASS |
| 完整 JVM 回归 | **FAIL：754 tests completed，1 failed**。`WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` 在等待 Clear/Web Memo Host 状态时 60 秒超时，状态仍为空；该路径不在本次 Native Chrome 源码变更内 |
| 上述唯一失败的最终源码隔离复跑 | **PASS**，单独执行同一 test 后 BUILD SUCCESSFUL。孤立成功不能抹去全量 suite 的失败，根因尚未确定 |
| Android 设备 UI | NOT TESTED：`adb devices -l` 无在线设备 |

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，66,483,794 bytes，SHA-256 `2951122B0A58223B54B8544E4E123A7358A66148228BDD4BFBE686EF125BE0439`。
- 构建/AndroidTest 编译/lint 日志：`.local/android-test/reports/native-chrome-separation-20261030/build-gate.log`，SHA-256 `CDB161544E252880B74E1A685B7B8941DCABD71FD9DA0B69AF1516180B48E181`。
- 完整 JVM 日志：`.local/android-test/reports/native-chrome-separation-20261030/full-unit.log`，SHA-256 `384D2B2084E91E94FE84D6603108771F261A8611539CD173610E8569E96A9976`。
- Focused UI/边界日志：`.local/android-test/reports/native-chrome-separation-20261030/focused.log`，SHA-256 `D9452FF452F0D35BBE5FF4F0F20627F3367413E860140581945AE75A80D09A8B`。
- 最终源码隔离复跑日志：`.local/android-test/reports/native-chrome-separation-20261030/memo-isolated-final.log`，SHA-256 `B04C40115FF001102CADAE83C7B8406F4FECA407B2ACA698C95300067C3A2F23`。
- 代码提交 `a70c4d02ffd842e3e7c6ee0ca24699e3dde3bcec`，父提交 `c161f24`。

## 未签核范围

SEP-12 仍因系统 Picker、多窗口/IME/返回导航和 Native/Skin 等价验收未完成而保持 **INCOMPLETE**。本 Issue 不证明模拟器/真机渲染、TalkBack、WebView 交互、Skin Studio 源码编辑能力或完整分离成功。Clear 保持当前状态，不在本 Issue 修复。
