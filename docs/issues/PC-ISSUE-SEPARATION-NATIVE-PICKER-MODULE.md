# PC-ISSUE-SEPARATION-NATIVE-PICKER-MODULE

- 日期：2026-10-30
- 状态：**Native Picker renderer CODE COMPLETE；Robolectric/全量 JVM TESTED；SEP-12 仍 INCOMPLETE**
- 代码提交：`1ecd724710458fdb42be36fd02b1627aed6af290`（父提交 `fc568869a168138628d3c90c2bcd7269f4d831aa`，已推送至 `origin/feat/skin-first-separation`）
- 范围：将 Native 日期/时间系统 Picker 展示及 Picker visual contract 移入 `:native-skin`。未修改 Clear 源码、Core 时间语义或 Picker 的 Host/runtime 路由。

## 根因

`SkinnablePickers.kt` 同时持有 Host 的时区/时间转换与 skin 路由，还直接构造 Android `DatePickerDialog` 和 `TimePickerDialog`。Native profile 的 Picker renderer 仅调用 Host 提供的 `context.Native()` 闭包，导致 Native 系统对话框实现仍在 `:app`，无法独立列入 Native skin 模块边界。

## 修改

- 将 `PickerKind`、`PickerVisualContext` 和 `PickerVisualRenderer` 移入 `:native-skin`。上下文保留 Host 解析后的本地日期/时分/时区、Android Context 和确认回调。
- 新增 `NativePickerSkin`，在模块内创建平台 `DatePickerDialog` / `TimePickerDialog`；沿用 Android 原生对话框、24 小时制和显式确认回调。
- `SkinnablePickers` 只负责基于传入 instant 和 zone 生成请求，并路由到运行时皮肤、编译皮肤或 Native renderer；Native profile 注册 `NativePickerSkin`。
- 保留已有 Runtime Picker 确认测试；新增 Native 系统 Picker 测试覆盖初始日期、初始时分、24 小时制、确认前不回调，以及确认后的 Host 回调值。
- Native/Host 的模块职责已同步到 `UI_SKIN_EXTENSION_CONTRACT_V4.md`；SEP-12 依旧未关闭。

## 验证证据

环境：Windows / PowerShell，SDK `E:\android-sdk`，离线 Gradle，Robolectric API 35。设备清单为空。

Focused 命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.ui.v2.NativePickerModuleTest' --tests 'dev.powerfulcalendar.ui.v2.RuntimeSkinComposeTest' --offline --rerun-tasks --max-workers=2 --console=plain
```

- Native Picker 新测试 **2/2 PASS**；Runtime Picker/皮肤交互 **6/6 PASS**；总计 **8/8 PASS**。
- 首次日期确认测试在 Robolectric 主线程队列尚未排空时立即断言，测试以 `chosen == null` 失败；加入主线程 idle 后，相同生产实现重跑通过。此处是测试驱动时序问题，不是产品回调缺陷。

完整离线门禁：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=2 --console=plain
```

| 验证 | 结果 |
| --- | --- |
| Native 与 skin 源码边界 | PASS |
| 完整 JVM 回归 | **757/757 PASS**；113 suites，0 failures、0 errors、0 skipped |
| AndroidTest Kotlin 编译 | PASS；设备 instrumentation 未执行 |
| Debug APK 构建 | PASS；66,483,794 bytes，SHA-256 `1BF2102892E0C9BCBDC382025B643B34C74A711FD1C5D6774447BEC4E3AB2D19` |
| `lintDebug` | PASS |
| Android 设备 Picker 行为 | **NOT TESTED**：`adb devices -l` 无在线设备 |

- 完整门禁日志：`.local/android-test/reports/native-picker-separation-20261030/full-gate.log`，SHA-256 `320FBF1FEFA3222E6FF22F962DA02CD643162D56FBB50F5FB5A19366A2E048B6`。
- Focused retry 日志：`.local/android-test/reports/native-picker-separation-20261030/focused-retry.log`，SHA-256 `04FB320DA93DB8194CD2C742FE139F1D30F05F81F4FB2021755DE78DB9AEAA91`。
- 初次测试 harness 时序失败日志保存在同目录 `focused.log`；SHA-256 `76C2A191687F8B18AFE9FF2A299222F244284E1D60A183DED1B2121B85DCFB8E`。

## 未签核范围

本 Issue 只证明 Native system Picker 已有独立模块实现及 JVM/Robolectric 证据。它不证明 Android 真实设备上的系统 Picker、IME、窗口/返回行为，也不证明 Navigation、所有 Skin Picker 自定义 UI 或 SEP-12 完成。Native fallback 保留；Clear 目前状态未改动。
