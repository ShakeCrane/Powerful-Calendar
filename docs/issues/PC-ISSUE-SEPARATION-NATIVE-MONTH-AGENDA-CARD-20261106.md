# PC-ISSUE-SEPARATION-NATIVE-MONTH-AGENDA-CARD-20261106

**状态：PASS（Native 0.9.5 Month agenda 卡片与标题窄视觉切片）**
**日期：**2026-11-06
**实现提交：**caa1d18e7160df5ee51ed33783b4bf4fe36df1ee

## 冻结基准

Owner 指定视觉与交互基准为冻结原生 PowerCal 0.9.5-rc.1。源码 SHA：b1f7629f0c52812e1887443872f43aa6c49908c4；参考 APK SHA-256：DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76。

冻结 MonthScreen.kt 的 agenda 卡片是 Surface，使用 skinBarShape()、surfaceContainerLow、64dp 最小高度和 16dp padding。标题、摘要和序号分别使用 Material titleMedium、bodyMedium、labelSmall；事件类型色取自冻结 PowerTheme.kt 的 kindColor。迁移改动限定在默认 Skin 的 native-095.css、重建后的正式 .pcskin、WebView/Chromium 样式断言。

## 验证

- 浏览器 E2E：node tools/skin-studio/tests/e2e_v3_month_selection.cjs PASS；验证卡面 rgb(246, 248, 252)、20px 圆角、无阴影、16px 内边距、0px 卡内间距、12px 卡片间隔、事件颜色、字号/行高和日期标题间距。
- JavaScript 语法：node --check tools/skin-studio/tests/e2e_v3_month_selection.cjs 与 node --check tools/skin-studio/default-clear/source/web/app.js PASS。
- Studio 分发测试：python -m unittest discover -s tools/skin-studio/tests -p test_single_default_distribution.py -v，2/2 PASS。
- 串行 Gradle 门禁：:app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 --no-parallel --console=plain PASS。实际 JUnit XML 141 份，共 975 tests，0 failures、0 errors、0 skipped。此前 SystemCalendarImportTest 的 SQLite SQLITE_BUSY 本轮没有复现。
- API 35 隔离 AVD PowerCal_Native095_Parity_20261105 / emulator-5580，1080×2400、420 dpi、WebView 124.0.6367.219。monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged 样式/日期触控 1/1 PASS；sameSelectedRecurringOccurrenceOpensEquivalentNativeAndClearMonthEditor 重复实例目标编辑器路径 1/1 PASS。选择日期前后 Store 快照不变。
- build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk PASS。APK 中恰好一份 .pcskin，ID powercal-clear-default；包 10 文件、409458 bytes、SHA-256 F7ECF2A168A8D65968362A8A464053DA10248148E4837DC7ACF0DCB73F0B1826，源资源与 APK 中包字节一致。Debug APK SHA-256 1A6C36F310295A0594C72C43B73F478A4F0A7C5A5C84A688BAC44FA670AA7ACD。

## 原始截图与几何

同一隔离 AVD 的 Native 截图：.local/android-test/reports/native095-month-radius-20261105/month-native-original-20261105.png，SHA-256 9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A。最终 WebView 截图：.local/android-test/reports/native095-month-agenda-20261106/month-agenda-web-final-20261106.png，SHA-256 6021DC49EED9315B105C3A84D8701B0250F9E32E220206B52FFD12DE7443E16F。

卡面 bbox Native (32,1436)-(1047,1677)、Web (32,1438)-(1050,1679)：Web 下移 2 个物理像素，右边缘多 3 个像素。标题 ink bbox Native (37,1357)-(379,1398)、Web (35,1358)-(376,1398)。比较 ROI (0,1420)-(1080,1690) 的 MAE 为 4.583，P95 通道差 2，通道差大于 2 的像素占 5.3443%，表面 mask IoU 0.948156。这说明主要 CSS 样式约束和布局参数已经对齐，同时仍保留实际栅格差异。

## 范围与后续

本 Issue 只对 agenda 卡片与所选日期标题的视觉窄切片 PASS，不代表 Month 整页通过。深色主题、Month 底部动作按钮、年月控件与完整导航触控、其它日期/空态/密集事件状态及其它 Native 页面仍未签核。重复实例的既有功能验收不替代完整视觉对照。没有修改 Core/Host、Native 页面、事件/重复规则、Skin ID 或用户数据；唯一默认 .pcskin 数量保持为一，Native 恢复与 Product Cutover 状态不变（HOLD）。

详细日志、截图、比较 JSON 和包构建结果保存在 .local/android-test/reports/native095-month-agenda-20261106/。
