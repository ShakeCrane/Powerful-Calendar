# Issue：将冻结 Native 年份/月选择弹窗迁移到默认 Skin

**状态：**年月选择器面板、内容、焦点、校验、真实触控及系统栏遮罩/恢复窄切片 `PASS`；Month 整页继续 `PARTIAL`。
**代码提交：**选择器 `800cb630dd9037688908ffbfb2785b0c0a063830`；系统栏 `c8d8724720f395d425fe8fb1116e2b781d780aaa`。
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

## 原始未闭合差异（后续已由 Addendum 关闭）

Compose AlertDialog 的遮罩会压暗 Android 系统栏；Web `<dialog>::backdrop` 目前只覆盖 WebView，系统状态栏和导航栏未变暗。在四个屏幕采样点，Native 为 `(97,98,100)`，Web 为 `(250,250,250)`。这是 Host/platform presentation 的明确差异。不能通过本次 CSS 说它已关闭；需要独立实现并验证 Web 弹窗期间的系统栏 scrim 生命周期，然后再将选择器整体状态升级。

所以原始记录只签核弹窗面板/内容/触控窄切片，不代表整页 Month、全套 Native parity 或 Product Cutover 通过。系统栏后续结果见 Addendum；Month/Native parity 继续 `PARTIAL/IN_PROGRESS`，Product Cutover `HOLD`。

## 2026-11-06 Addendum：系统栏遮罩与生命周期（窄切片 PASS）

Web 模态打开时，Clear 通过已有 MessagePort 发送严格限定的 v1 `presentation/modalOpen:boolean`；Host 只接受精确字段和布尔值，并要求当前 Skin 具备 `host.read`。Host 将不可点击、不可聚焦、对无障碍隐藏的轻量 View 临时挂到现有 Activity DecorView，只绘制 WindowInsets 标出的 status/navigation bar 条带。弹窗关闭或 WebView 释放时移除该 View。该信号不携带 Host action、业务数据或写权限，不触碰 Core/Calendar Store，也不改变网页弹窗面板亮度。

| 项目 | 最终实测 |
|---|---|
| 参考设备 | 冻结 APK 与当前 APK 均在隔离 `PowerCal_Native095_Parity_20261105` / `emulator-5580`、API 35、1080×2400、420 dpi、WebView 124.0.6367.219。设备身份在最终安装前重新核实。 |
| 系统栏像素 | 冻结 Native `(500,20),(500,55),(500,2350),(500,2380)` 均为 `(97,98,100)`；Web 同点均为 `(97,97,97)`，差异每通道不超过 3。弹窗关闭后的四点恢复至打开前基线由 instrumentation 等待和断言。 |
| 弹窗表面 | 年份框采样 Native `(101,101,102)`、Web `(100,100,101)`；月份面板 Web 保持 `#E7EBF4`。截图断言确认面板仍可见，系统栏遮罩没有把 Web 面板二次压暗。真实触控、取消/遮罩关闭、跳转/选月和 Store/Camera 不变量完整通过。 |
| 构建和测试 | 串行 Gradle 全门禁 PASS；JUnit XML 141 份、976 tests、0 failures/errors/skips；两项源码边界、APK、Lint、AndroidTest Kotlin 编译/组装 PASS。最终 AVD instrumentation `monthYearMonthDateAndTodayTransitionsMatchWithoutMovingTimelineOrWritingStore` `OK (1 test)`。Chromium Month E2E PASS，Node syntax PASS，默认分发测试 2/2 PASS。 |
| 包身份 | Debug APK SHA-256 `590E3897512122EA216C92751B8CE6E63FD939A5EF03A4355E555149C3197753`。唯一 `powercal-clear-default.pcskin` 为 10 files、410037 bytes、SHA-256 `5D39562345B00DD719B89E2CCAB51A59964DFB8530AFC8AF046FF06B790EAEA7`；`build_default_clear.py --verify --apk` 确认 APK 资源逐字节一致。 |
| 截图/日志 | 年弹窗最终 Web SHA-256 `149FC804AF187995C027744194071B00C022885771765653F8B01EBA589B5913`；月弹窗 `FDB90DEE33F471C2E11D44A9F34A19024575B671430E602EA560C042130A67C0`。最终设备日志 `system-bar-decor-instrumentation-final.log`，完整串行门禁 `gradle-systembar-final-gate.log`，截图和失败尝试日志均位于 `.local/android-test/reports/native095-month-picker-audit-20261106/`。 |

排查记录保留了两种无效窗口路径：Activity 内 Compose 条带没有改变截图中的系统栏像素；WindowManager dim-behind 虽能压暗系统栏，也错误地压暗了 Web 弹窗面板。最终 DecorView 条带只影响 system-bar Insets，并通过最终像素、面板采样和真实触控确认。

**结论：**年月选择器及系统栏开/关生命周期窄切片 `PASS`。这不签核 Month 的暗色主题、空/密集状态、其它日期状态、Month 整页或其它 Native 页面。Native 0.9.5 迁移仍 `IN_PROGRESS`，Product Cutover 仍 `HOLD`。
