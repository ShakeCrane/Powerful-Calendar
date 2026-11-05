# PC-ISSUE-SEPARATION-NATIVE-MONTH-AGENDA-TYPOGRAPHY-20261106

**状态：PASS（浅色 Month 事件卡片文字色及 Material 字体参数窄切片；Month 整页仍 PARTIAL）**
**代码提交：** `1ffd87499c8d6d6089eb1c158178d6c39f1e9bf5`
**日期：** 2026-11-06

## 冻结参照与差异

冻结目标为 Native PowerCal 0.9.5-rc.1，源码 SHA `b1f7629f0c52812e1887443872f43aa6c49908c4`，原始 APK SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。`MonthScreen.kt` 的卡片标题、摘要和序号分别使用 Material `titleMedium`、`bodyMedium`、`labelSmall`；摘要和序号继承 `onSurface`。冻结浅色主题 `onSurface` 为 `#11151C`，不是 `onSurfaceVariant`。

此前 Web 卡片摘要、序号误用较浅的 `--label-2/--label-3`，并漏掉对应字距及序号 500 字重。修复范围限定于 `native-095.css` 和已有 Android WebView 样式断言；未变更 Host、Core 或交互语义。

## 实现与验证

- Web 标题为 `16px/500/24px/.15px`；摘要为 `14px/400/20px/.25px` 且使用 `#11151C`；序号为 `11px/500/16px/.5px` 且使用 `#11151C`。Android WebView instrumentation 对计算样式逐项断言。
- 实际设备：隔离 AVD `PowerCal_Native095_Parity_20261105` / `emulator-5580`，API 35、1080×2400、420 dpi、WebView `124.0.6367.219`。冻结 Native 与当前 Web 截图分别为 `month-native-original-20261105.png`（SHA-256 `9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A`）和 `month-web-after-typography.png`（SHA-256 `0115C306758C3A00974E351EA3C006E296C5BD433679405D51B2492423619499`）。
- 摘要深色墨迹中位 RGB Native/Web 均为 `(17,21,28)`；序号中位 RGB 也均为 `(17,21,28)`。Native/Web 摘要墨迹框分别 `(77,1554)-(298,1580)`、`(77,1555)-(304,1581)`；序号分别 `(75,1604)-(155,1625)`、`(75,1605)-(156,1626)`。颜色相同，但仍有 1px 垂直栅格差及摘要约 6px 宽度差，故这里只签核 CSS 字体参数与文字前景色，不签核逐字形几何完全相同。
- `monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged` 首次运行因测试把主题常量误估为 `(18,22,29)` 而失败；源码 `NativeSkinThemeDefaults.lightScheme.onSurface = 0xFF11151C` 核实后仅修正测试期望。最终 AndroidTest 编译/组装通过，隔离 API 35 WebView 仪器用例 `OK (1 test)`；Store 计数和序列保持不变。
- 同一产品文件状态下串行完整 Gradle 门禁成功，JUnit XML 实计 976/976、0 failures/errors/skips，源码边界、APK、Lint 与 AndroidTest compile/package 均通过。期望值改动后另行重编 AndroidTest 并执行上述目标方法，没有重跑 JVM。
- Chromium `e2e_v3_month_selection.cjs` PASS；分发测试 2/2 PASS；`build_default_clear.py --verify --apk` PASS。Debug APK SHA-256 `1AB2F1FA90223CDD07D5B181F86562BE646260A384398E53360EA93F3FCAF094`。APK 仅有一份默认包；`powercal-clear-default` 包 10 文件、410062 bytes、SHA-256 `57A5498366AD40E7BCF671F4EEB76E23B264546CA9C567C8F4D25BCE2A99FC7C`，源码与 APK 资产字节一致，已安装 `base.apk` SHA 与构建 APK 相同。

## 证据与边界

完整命令日志、首次错误与最终结果、截图、包验证和 E2E 记录位于 `.local/android-test/reports/native095-month-card-type-20261106/`。Studio Clear bridge 浏览器 E2E 首次额外检查时因测试断言沿用旧提示词而失败；它已由独立测试提交 `6d8118f4a34cc50647bdf3b5a35c7bac008d91e8` 修正，最终作者化/导出/重开 E2E PASS，详见 Issue `PC-ISSUE-STUDIO-E2E-EXPORT-TOAST-ASSERTION-20261106`。

深色卡片、密集多卡片几何、长标题换行/贪心折叠、Month 全状态及其它 Native 页面尚未签核。该窄切片不改变唯一包 ID、包数量、Core/Host、恢复路径或业务数据；Month 和总体视觉迁移仍为 `PARTIAL/IN_PROGRESS`，Product Cutover 仍 `HOLD`。
