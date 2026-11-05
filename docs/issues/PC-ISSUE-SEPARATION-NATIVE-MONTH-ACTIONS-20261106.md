# Issue: Native 0.9.5 Month 底部操作按钮迁移

**状态：**PASS（仅两个底部动作按钮的呈现、间距和触控窄切片）
**代码提交：**`b8376da7bb46c2ae41d8a86618998a282b16d816`
**执行基线：**`8448ad0e27dda05007e7e1320122a21b18027d6c`
**唯一视觉基准：**Native 0.9.5-rc.1 源码 `b1f7629f0c52812e1887443872f43aa6c49908c4`；参考 APK SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。

## 复现与发现

在同一 API 35 隔离 AVD 截取冻结 Native 与新版 WebView Month 页面。冻结 `MonthScreen.kt` 底部使用 48dp `OutlinedButton` 和 48dp `TextButton`，其 LazyColumn item 间距为 12dp。初始 Web 实现采用 1 CSS px 边框、动作区 2px gap；按钮的图标文案和边框厚度不一致。Web 端 agenda 底部 padding 4px 加动作区顶部 padding 10px，使按钮整体比 Native 低约 6 个设备像素。

## 修复

- 将唯一默认 Skin 的创建操作改为 Material Add SVG 和本地化文字，保留既有 `createDraft` / `ACTIVITY` Host 请求。
- 实现 48dp 高度、24dp 圆角、24dp 水平内容 padding、零图文间隙、14sp/500/20sp 标签、`outlineVariant` 边框。
- 把动作项间距从 2px 调到 Native 的 12dp；通过 1.5 CSS px 声明使新版 WebView 的 420dpi 设备边框实际栅格化为 3px，对应 Native 1dp 边框。
- 将 agenda 到首个操作的合成总间距从 14px 调为 12px，并增加实际 Android WebView 元素几何断言。
- `jumpTimelineDay` 和创建操作继续调用既有 Host 路径。没有修改 Core、Host、冻结 Native、默认 Skin ID 或工厂包数量。

## 验收证据

- 完整串行 Gradle 门禁通过：源码边界、JVM 975/975（0 failures/errors/skips）、APK assemble、Lint、AndroidTest Kotlin 编译和 APK 组装。
- `node --check` 对本次浏览器测试及 Clear `app.js` 通过；Chromium `e2e_v3_month_selection.cjs` 通过；发行包测试 2/2 通过。
- `python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk` 通过。APK 仅含一份 `.pcskin`；默认包 409520 bytes、10 files、SHA-256 `04AC4E3358448C18C2D0FBCF03853C9F2B16BCE3DDBDB0CD01C6DB9F01022D77`，与源包逐字节相同。APK SHA-256 `19D52E6ED16AD42855E8DD5798A3B8D1B425C60A3B9951FD637ABA3BD4A9C818`。
- AVD `PowerCal_Native095_Parity_20261105` / `emulator-5580`，Android API 35、1080×2400、420dpi、WebView `124.0.6367.219`。`monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged` 与 `monthYearMonthDateAndTodayTransitionsMatchWithoutMovingTimelineOrWritingStore` 均 1/1 通过；Store 计数/序列不变。
- Native screenshot SHA-256 `9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A`；WebView screenshot SHA-256 `51CCEC518B5D31A4C249784048A641448901A9D34B337D3C5B6CC87E2742B254`。描边采样框 Native y=1710..1835，Web y=1711..1836；高度一致，Web 低 1px。以边框颜色 ±8 RGB 掩模计算的 ROI IoU 为原始 0.4574、向上平移 Web 1px 后 0.8634。
- 本地命令日志、截图和几何 JSON：`.local/android-test/reports/native095-month-actions-20261106/`。

## 重试记录与限制

第一次 instrumentation 的样式探针缺少按钮 `gap` 字段，测试在 JSON 解析断言时失败；只补充探针字段后重建并重跑，最终通过。一次 `build_default_clear.py --verify --apk` 在应用新的源皮肤包但 APK 尚未重建时执行，因检查到了旧 APK 而失败；之后完整串行构建并重跑验证通过。Month 整页仍为 `PARTIAL`；年份/月选择对话框、深色主题、空态和密集状态尚未签核。此 Issue 不代表其他页面或 Product Cutover 通过；Product Cutover 仍 `HOLD`。
