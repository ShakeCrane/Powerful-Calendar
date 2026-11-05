# Month 内层玻璃网格表面迁移

**日期：**2026-11-05
**范围状态：**颜色 PASS；表面范围 PARTIAL
**页面状态：**Month 仍 PARTIAL；Product Cutover HOLD

## 基准与差异

视觉基准是冻结源码 `b1f7629f0c52812e1887443872f43aa6c49908c4` 和原始 0.9.5-rc.1 APK（SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`）。Native `PowerTheme.kt` 的浅色 `AxisPalette.glassFill` 为 `0xCCFFFFFF`，深色为 `0xB31A2130`；`GlassPanel` 在 blur 效果开启时使用该半透明填充。原 Web 月格 `#monthGrid::before` 使用不透明 `--bg-elevated`，与冻结效果不同。

## 实施

在默认 Skin 的 `native-095.css` 增加 `--month-grid-surface`：浅色 `rgba(255,255,255,.80)`、深色 `rgba(26,33,48,.70)`，并将其用于月格伪元素。没有改变外层面板、日历布局、状态投影、Core/Host API 或 Store 行为。浏览器 E2E 和 Android instrumentation 检查实际 computed background。

## 验证结果

- Chromium E2E：PASS。computed background 为 `rgba(255, 255, 255, 0.8)`；月历切换、选日、Today、Camera 和预览新建流程通过。
- 单默认分发测试：2/2 PASS；相关 JavaScript 语法检查 PASS。
- Android API 35 WebView instrumentation：1/1 PASS，方法 `monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged`。包含 computed surface、日期可信触控和 Store 快照不变断言。
- 串行 Gradle 的源码边界、Debug assemble、Lint、AndroidTest Kotlin 编译及组装：PASS。完整 JVM 门禁本切片未重跑；此前门禁有 SQLite `SQLITE_BUSY` 失败，不能据此声称完整 JVM 通过。
- Debug APK SHA-256：`D960BA9367626430506F910F727AB3A8F79D7C2BB83BCBC0A224E2669FD1E57F`。
- 正式默认包 SHA-256：`C8AB68A5A029F2908EFCAF2352FFA264D2846E75060C1BA396FAFC71F3DA748D`。APK 恰含一份 `assets/powercal-clear-default.pcskin`，与源码资源逐字节相同。

## 实屏范围差异

同设备 API 35 AVD `PowerCal_Native095_Parity_20261105` / `emulator-5580`，分辨率 1080×2400、420 dpi。原生截图 SHA-256 `9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A`；Web 截图 SHA-256 `3ABDE008043CC294CF00EA6E96A94E2F789363B5DF7E1E635BDBF0B854F79D8C`。

在月历 ROI 内，精确 `#FDFDFE` 中心面 bbox：Native `(105,338)-(973,1235)`，Web `(100,393)-(981,1244)`。因此颜色切片通过，但 Web 表面上沿低约 55 px、下沿低约 9 px，宽约多 13 px；表面范围未通过。冻结外层面板主色约 `#F2F3F3`，Web 当前约 `#F3F5FA`，描边/阴影和 agenda 表面/间距仍是独立缺口。

## 后续

下一步依据冻结 `MonthScreen.kt` / `GlassPanel` 的实际布局约束与同一 WebView 测量，收敛月格中心玻璃面的垂直边界和宽度。此后另行处理外层面板与 agenda。不得把本记录当作整页视觉等价签核。

详细命令、构建与设备日志位于 `.local/android-test/reports/native095-month-grid-surface-20261105/`（本地忽略目录）。
