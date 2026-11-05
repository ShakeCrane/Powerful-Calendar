# Month 内层玻璃网格表面迁移

**日期：**2026-11-05
**本窄切片：**内层玻璃面颜色与像素边界 `PASS`
**Month 整页：**`PARTIAL`；Product Cutover：`HOLD`

## 基准

唯一视觉基准为冻结源码 `b1f7629f0c52812e1887443872f43aa6c49908c4` 和原始 PowerCal 0.9.5-rc.1 APK（SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`）。Native `PowerTheme.kt` 浅色 `AxisPalette.glassFill` 是 `0xCCFFFFFF`，深色为 `0xB31A2130`；Month 的网格与月份栏共用 `GlassPanel`。

## 实施

默认 Skin 的 `native-095.css` 现将内层玻璃面放在 `.month-calendar-panel::before`，以 `top:27px; bottom:29px; width:min(331px,100%)` 对齐设备像素测量。面板建立独立层叠上下文，月份栏和网格内容置于填充层上方，避免伪元素遮挡月份标题和导航。颜色 token 对应浅色 80% 白、深色 70% `#1A2130`。未改月份数据、布局规则、Core/Host API 或 Store。

## 验证

- Chromium `e2e_v3_month_selection.cjs`：PASS，检查伪元素颜色、27px/29px/331px 几何以及月份、日期、Today、Camera 和预览新建流程。
- 默认包分发 Python 测试：2/2 PASS；相关 JS `node --check`：PASS。
- 串行源码边界、Debug assemble、Lint、AndroidTest Kotlin 编译/组装门禁：PASS。后续新增的 WebView visual-state screenshot barrier 首次以 lambda 编译失败，改用 `WebView.VisualStateCallback` 后，定向 AndroidTest 通过。
- API 35 隔离 AVD `PowerCal_Native095_Parity_20261105`（`emulator-5580`，1080×2400、420dpi）：`monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged` 1/1 PASS。再次手动安装本轮 Debug 和 AndroidTest APK 后同一用例 1/1 PASS，以便在应用卸载前取回图像；选中日触控可信、`MonthFixture` 可见，Core Store 快照不变。没有清除应用数据。
- WebView 通过 `postVisualStateCallback` 屏障后截图，确认显示 2026-11-05 和 MonthFixture，避免了旧截图中异步合成的 11-01 画面。
- 本窄切片未运行完整 JVM 套件。先前完整运行记录 975 项、1 项 `SystemCalendarImportTest.rejectedItemsArePersistedAsWarningsWithoutCalendarWrites` 遇到 SQLite `SQLITE_BUSY`；本次不将其记为通过。

## 同设备截图与边界

Native 截图 SHA-256 `9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A`；同步后的 WebView 截图 SHA-256 `163A5BE271320D25CEE3E2161866B507DF3DF9E6365B9817B81D8C798ED29A65`。在完整截图的月历 ROI `(80,300)-(1000,1300)` 中扫描精确 `#FDFDFE` 像素，Native 边界 `(105,338)-(973,1235)`，Web `(107,338)-(974,1234)`；各边差异不超过约 2px。这里记录的最大坐标为像素扫描包含端点。

Debug APK SHA-256 `896D5BAB6A01A145FDA97D53706DCB7DF30E31CE77B3E0998C89730C7B2278D6`。源码默认包为 409395 bytes，SHA-256 `0B41CAC7FA5BF65C5A5D64D97AA740D6656B93560F1269EC59359B1B9C6B5331`；APK 仅包含 `assets/powercal-clear-default.pcskin` 一份，且与源码逐字节一致。

## 未闭合差异

当前内层玻璃填充颜色和几何通过；Native 外层 Month 面板的 `#F2F3F3` 材料、outlineVariant 描边/阴影及 agenda 卡片表面与间距仍不同。下一窄切片先对照 Native `GlassPanel` 实际参数，处理外层面板材质；agenda 独立验证。Month 整页继续 `PARTIAL`，不得据此放开 Cutover。

本地详细证据位于 `.local/android-test/reports/native095-month-grid-surface-20261105/`。
