# Native 0.9.5 普通设置入口退役

状态：**窄范围 PASS**。Clear 的普通设置不再导航到 Native Tools；独立 MainActivity 恢复路径仍可访问。Native/Web 设置的视觉等价仍未测试，完整视觉迁移继续 `IN_PROGRESS`，Product Cutover `HOLD`。

## 冻结基准

- 实现提交：`a767c2f20cbc360d8e178fc99e2bbc053b099c0e`（`refactor(skin): 移除默认设置中的普通 Native 工具入口`）。

- 冻结源码：`b1f7629f0c52812e1887443872f43aa6c49908c4`。
- 冻结 APK：`.local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-b1f7629-debug-core-HOLD.apk`。
- APK SHA-256：`DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。

## 问题与处理

此前默认 Clear Settings 显示“打开原生完整设置与恢复工具”，以 `nativeSettings` 通过 Web bridge 切换 `Page.TOOLS`，使 NativeToolsPage 成为普通日常 UI 路径。现在移除了该 Web DOM 和对应 CSS/JS 路由；`decodeBridgeAction` 不再接受 `nativeSettings`，PowerCalApp 普通路由分支也已删除。非法/旧 Skin action 不会离开当前 Settings 页面。

NativeToolsPage 保留给 MainActivity 的独立 `nativeRecoverySignal`。该恢复路径及其脏编辑器检查点未更改。提醒/权限、ICS、系统日历、备份/恢复、资料和受保护 AI 的既有 Web/Host actions 继续存在，没有删通用 Host/SAF/授权能力。正式默认皮肤仍是唯一 `powercal-clear-default.pcskin`。

## 真实验证

- 隔离 AVD `PowerCal_Wave2_QA35_20261104` / `emulator-5556`，API 35、1080×2400、420 dpi。更新安装当前 Debug APK 后检查；未清除应用数据、未卸载、未触碰系统日历。测试后 selected skin 仍是 `powercal-clear-default`。
- Android instrumentation 三项各 1/1：
  - `ClearSettingsOptionsInstrumentedTest#clearSettingsRetireSkinCatalogueAndKeepProtectedOperations`：旧目录和入口不可见，伪造 `nativeSettings` 被拒，页面/包选择不变，Store 无写入。
  - `ClearSettingsOptionsInstrumentedTest#nativeAndClearProtectedProfileSaveMatchThroughHostTools`：资料仍通过 Host；Recovery signal 仍可进入 Native Tools。
  - `NativeRecoveryGestureCheckpointInstrumentedTest#emergencyRecoveryCheckpointsDirtyEditorBeforeSwitching`：有修改编辑器先保存检查点，草稿保留，正式 commit 数为 0。
- 串行 Gradle 完整门禁 `BUILD SUCCESSFUL`；JVM 142 suites / 987 tests / 0 failures / 0 errors / 0 skipped；源码边界、Debug APK、Lint、AndroidTest Kotlin 编译和组装通过。
- Studio 默认 Clear Python 测试 5/5；受影响 JS `node --check` 全部通过。Chromium/CDP E2E 的正式 Settings Mock、Studio Mock、Clear Studio bridge 均 PASS。Mock Host 结果不视为 Android 权限弹窗或真实 Settings 的视觉验收。
- 正式 `build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk` PASS：APK 中恰好一份 `.pcskin`；ID `powercal-clear-default`；包源/APK SHA 均为 `88ea98745669139bd4c454b4e23c1555cd29450de3469e906d11df99124f8a50`。
- Debug APK SHA-256：`B41DCD08421B4BE91D40FC0B205945BCEB0D3884B0DD4F38C8D80B8C505F9A6C`。
- 完整命令、日志、测试方法与设备信息：`.local/android-test/reports/native-settings-route-20261107/manifest.md`。

## 限制

本任务验证导航安全性、Host action 保留、Native 恢复及数据不变量；没有重拍原始 APK 和新版 WebView 的同屏 Settings 对照，也没有签核卡片几何、文案、字体、焦点或滚动。`ClearSettingsOptionsInstrumentedTest.kt:279` 有一条 Java deprecated `isChecked` 警告。首次 Browser E2E 的异步 action 等待超时和旧 route 断言失败均保留在本地日志；修正等待握手和测试期望后最终通过。

## 后续

下一步用冻结 Native 0.9.5 与新版 WebView 在隔离 API 35 设备上配对检查 Settings 页面结构/组件/可见状态/点击行为；历史皮肤目录为 Owner 退役项，不复刻。保留 Native Tools 作为独立安全恢复，不以本次窄范围 PASS 宣称全页视觉等价。
