# PC-ISSUE-SEPARATION-NATIVE-MONTH-CHIP-RADIUS-20261105 — 月历日期格圆角覆盖

**状态：**选中日期/今天形状窄切片 `PASS`；Month 页面完整视觉与交互仍 `PARTIAL`。

## 冻结依据与根因

冻结源码 `b1f7629f0c52812e1887443872f43aa6c49908c4` 的 `MonthScreen.kt` 使用 `GlassShapes.chip`，而 `PowerTheme.kt` 将其定义为 `RoundedCornerShape(14.dp)`；日期格高度为 52dp。Web 基础 `style.css` 曾写 24px，实际 Native-095 主样式 `native-095.css` 又覆盖到 18px，所以只改基础文件不会改变正式 WebView 结果。首次 E2E computed-style 检查实际得到 `18px`，确认覆盖规则。

## 修复

将 `style.css` 和 `native-095.css` 中选中日期半径改为 14px，并将 `native-095.css` 的“今天”边框半径一并设为 14px。官方 `build_default_clear.py` 重建默认包；未改 Core、Host API、事件数据或 Month 业务逻辑。

## 验证

- Studio/浏览器 `e2e_v3_month_selection.cjs` computed style `14px`、日期及相机交互通过；`test_single_default_distribution.py` 2/2 PASS；JavaScript 语法通过。
- 串行 `:app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest` PASS。
- 同设备 API 35 AVD `PowerCal_Native095_Parity_20261105` / `emulator-5580`：`monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged` 1/1 PASS。仪器测试选择 2026-11-05 的真实 WebView 日期格，检查 computed style 和 52 CSS px 格高，实触发日期选择，验证 MonthFixture agenda 显示及 Store 快照不变，并截取 WebView 页面。
- 原始 Native 与 Web 截图均 1080×2400、同设备、相同日期和 MonthFixture。文件、哈希与分区指标见 `docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md`；原始测试图及日志在 `.local/android-test/reports/native095-month-radius-20261105/`。
- APK SHA-256 `A064F2F66AEEB0BC726CC15F79C6ACDA3D4179160991030C2ACD083D103DF40D`；仅含一个 `powercal-clear-default.pcskin`，源包与 APK 字节一致，SHA-256 `DDA39D12548368A33CB0F90041B61EAFE08AA7D53C710822491B29819F5345D3`。

## 未完成

Native 选中格计数文本为 primary-container 前景色，Web 仍使用次级灰；日历内部表面和 agenda 卡片颜色/间距仍有可见差异。该切片不构成 Month 页面、Studio 全能力或全产品等价签核。Product Cutover 保持 `HOLD`。
