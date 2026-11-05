# Issue：将冻结 Native 年份/月选择弹窗迁移到默认 Skin

**状态：**局部 `PASS`（面板、控件、焦点和真实触控）；系统状态栏/导航栏压暗 `PARTIAL`；Month 整页继续 `PARTIAL`。
**代码提交：**`800cb630dd9037688908ffbfb2785b0c0a063830`（完整 SHA 见 CURRENT）
**唯一视觉基准：**冻结 Native 0.9.5-rc.1 源码 `b1f7629f0c52812e1887443872f43aa6c49908c4`，原始 APK SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。

## 发现与修复

Web 年/月选择器原来使用浏览器默认 dialog autofocus，可能把焦点落到年份输入并唤起 IME；尺寸、面板颜色、圆角、标题、输入框和遮罩参数也没有按冻结 Native `AlertDialog` 校准。默认 Skin 现在在 `showModal()` 后聚焦 dialog 本身，保留输入框的显式操作；年份只允许 1900–2100，超范围时禁用跳转；点击遮罩关闭两种弹窗。弹窗宽度、中心位置、固定高度、背景、圆角和内容间距按原始 APK 截图及冻结主题测量校准。

变化只在默认 Skin 的呈现/输入和对应 Android 测试；月份选择仍经现有 Host action，未改 Core/Host 业务协议或 Store。

## 验证

- 原始 APK 与 WebView 均在隔离 API 35 AVD `PowerCal_Native095_Parity_20261105` / `emulator-5580`，1080×2400、420 dpi、WebView `124.0.6367.219` 上取得真实截图。
- Native/Web 面板掩模：年份 Native `(120,924)-(959,1540)`、Web `(121,925)-(960,1539)`；月份 Native `(120,739)-(959,1724)`、Web `(121,740)-(960,1725)`。弹窗布局匹配，边缘仍有约 1px raster 差异。
- 真机触控注入及 `event.isTrusted` 检查覆盖输入年份、非法范围、关闭、遮罩点按和月份选择。取消/遮罩关闭没有改变月份状态或 Core Store；不产生正式写入。
- Android instrumentation `monthYearMonthDateAndTodayTransitionsMatchWithoutMovingTimelineOrWritingStore`：1/1 PASS。
- 串行 Gradle 完整门禁：PASS；JUnit XML 975/975、0 failures/errors/skips，源边界、APK、Lint、AndroidTest Kotlin 编译通过。浏览器 Month E2E PASS，工厂分发 2/2，Node syntax PASS。
- APK SHA-256 `6EA8DE42ABB70D75B458DF9E747449486161D2D6FE3C06C3127A8AAE9C867CA3`。唯一默认包 10 files、409855 bytes；源码与 APK 资源 SHA-256 `E585A83C1F5F2560297E66AA754B4F1DEFB4B760661475182581400E9C4D44F2` 且字节一致。
- 原始/新图及日志：`.local/android-test/reports/native095-month-picker-audit-20261106/manifest.md`。

## 未闭合差异

Compose AlertDialog 的遮罩会压暗 Android 系统栏；Web `<dialog>::backdrop` 目前只覆盖 WebView，系统状态栏和导航栏未变暗。在四个屏幕采样点，Native 为 `(97,98,100)`，Web 为 `(250,250,250)`。这是 Host/platform presentation 的明确差异。不能通过本次 CSS 说它已关闭；需要独立实现并验证 Web 弹窗期间的系统栏 scrim 生命周期，然后再将选择器整体状态升级。

所以本 Issue 只签核弹窗面板/内容/触控窄切片，不代表整页 Month、全套 Native parity 或 Product Cutover 通过。Month/Native parity 继续 `PARTIAL/IN_PROGRESS`，Product Cutover `HOLD`。
