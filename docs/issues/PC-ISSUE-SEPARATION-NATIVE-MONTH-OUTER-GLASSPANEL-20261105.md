# PC-ISSUE-SEPARATION-NATIVE-MONTH-OUTER-GLASSPANEL-20261105

**状态：PASS（外层 Month GlassPanel 窄视觉切片）**
**日期：** 2026-11-05
**代码提交：** `c268902482d14b7d4462c440253e0bf9f2493454`

## 冻结目标

Owner 指定的基准为 PowerCal 0.9.5-rc.1 冻结原生界面，源码 SHA `b1f7629f0c52812e1887443872f43aa6c49908c4`，原始 APK SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。

Native `GlassPanel` 使用 28dp 圆角、`outlineVariant.copy(alpha = 0.55f)` 的 1dp 描边，效果开启时使用 2dp elevation。改动前 Web 月历面板使用页面底色、固定不透明边线和偏淡/偏宽的阴影，实屏边线比冻结原生窄 1 个设备像素。

## 修复

- 为唯一默认 Clear 的 Month 外框增加独立浅/深色面 token，浅色实测面为 `#F2F3F3`。
- 使用 `rgba(195,202,216,.55)` 描边、28px 圆角及 `0 1px 3px rgba(17,21,28,.28)` 阴影。
- Android WebView 在 DPR 2.625 下把 1px 描边呈现为 2 个设备像素；将默认 Skin 声明调至 1.5 CSS px，使截图线宽成为 3 个设备像素，与冻结 Compose 参考一致。Studio 浏览器 Preview 的缩放显示 1px，浏览器 E2E 同时固定源声明和实际预览样式。
- Android 截图前等待两个 `requestAnimationFrame` 并等待 WebView visual-state callback，避免把旧合成帧误当作已触控日期的截图。
- 通过正式 `build_default_clear.py` 重建 APK 工厂包。包 ID 仍为 `powercal-clear-default`。

## 验证证据

隔离 API 35 AVD `PowerCal_Native095_Parity_20261105`，serial `emulator-5580`，1080×2400、420 dpi，WebView 124.0.6367.219。Android instrumentation `monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged` 为 1/1 PASS；真实可信日期触控后 2026-11-05 与 `MonthFixture` 同时出现在 DOM 和截图中，Calendar Store 快照不变。

Native 原图：`.local/android-test/reports/native095-month-radius-20261105/month-native-original-20261105.png`，SHA-256 `9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A`。Web 图：`.local/android-test/reports/native095-month-outer-panel-20261105/month-web-selected-20261105.png`，SHA-256 `D0AD6368B24434BBA64A84C9593ECABC22E114FCEC54FF6B496CF9CAC9932D86`。两图的面板左边 ROI `(x=22..35,y=600..1200)` MAE 为 `0.786/255`，P95 通道差为 `2`；在 `y=900`，Native 与 Web 的描边均覆盖 `x=32..34` 共 3 个物理像素。

最终串行 Gradle 门禁中源码边界、Debug assemble、Lint、AndroidTest Kotlin 编译/组装均 PASS。JVM suite 未运行，因为变更只涉及 Skin CSS、生成包及 AndroidTest；既有 `SystemCalendarImportTest` 的 SQLite `SQLITE_BUSY` 仍未解决，也没有借本 Issue 将其标记通过。Studio 浏览器月历 E2E PASS，默认包分发测试 2/2 PASS，相关 Node 语法检查 PASS。`build_default_clear.py --verify --apk` 确认 APK 只有一份 `.pcskin` 且与源包字节一致。Debug APK SHA-256 为 `B7659FC55A3188D2915940FF879702E0FC6658E3750F0ADA0D5BBCE7C4DA7F37`；默认包 SHA-256 为 `0AE888C5BC9CB7335AC2499A6A1D85DE11EB989BF37EEE9855A92411CF2B203C`。

完整门禁日志、单项 instrumentation 日志、浏览器 E2E、像素剖面对照 JSON 和截图保存在 `.local/android-test/reports/native095-month-outer-panel-20261105/`。

## 未签核范围

这只关闭 Month 外层 GlassPanel 的浅色材质与侧边阴影切片。Month agenda 事件卡片表面/间距、深色主题、完整页面视觉/交互及其他 Native 页面仍需后续按原始 APK 对照；Month 页面整体仍为 `PARTIAL`，Product Cutover 为 `HOLD`。未修改 Core/Host 业务逻辑、Skin ID、Native 安全恢复或用户数据。
