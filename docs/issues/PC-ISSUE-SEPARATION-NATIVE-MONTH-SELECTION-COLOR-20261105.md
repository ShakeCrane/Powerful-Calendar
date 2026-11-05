# PC-ISSUE-SEPARATION-NATIVE-MONTH-SELECTION-COLOR-20261105 — 月历选中日内容色

**状态：**选中日期数字与计数颜色窄切片 `PASS`；Month 页面完整视觉/交互仍 `PARTIAL`。

## 冻结依据与差异

视觉基准是冻结提交 `b1f7629f0c52812e1887443872f43aa6c49908c4`。`app/src/main/java/dev/powerfulcalendar/ui/v2/PowerTheme.kt:83-84` 将浅色 primary container / 内容色定义为 `#DCE5FF` / `#0B2A66`，`:111-112` 将深色值定义为 `#1B3A7A` / `#DCE5FF`。冻结 `MonthScreen.kt` 把日数字与项目计数放在使用 `primaryContainer` 的 Compose `Surface` 内，因此二者继承 `onPrimaryContainer`。

Web 之前把选中格文字设为 `--label`，并由 `.month-count` 强制覆写成 `--label-2`，与 Compose 内容色语义不同。

## 修复

在默认 Skin `native-095.css` 中增加与冻结 Material 容器配色一致的 `--on-accent-container` 浅/深主题 token。选中日期使用此 token，选中格计数继承父级前景色。通过 `build_default_clear.py` 正式重建 `.pcskin`。Core、Host API、业务状态、重复展开、日历 Store 和用户数据未改。

## 验证

- `node --check tools/skin-studio/tests/e2e_v3_month_selection.cjs`：PASS。
- `node tools/skin-studio/tests/e2e_v3_month_selection.cjs`：PASS；实际浏览器 computed style 中选中格、日期数字和计数均为 `rgb(11, 42, 102)`；页面导航、日期/年月/Today、Camera 和新建预览流程通过。深色 token 静态核对为 `#DCE5FF`。
- `python -m unittest discover -s tools/skin-studio/tests -p test_single_default_distribution.py`：2/2 PASS。
- `:app:verifySkinSourceBoundary`、`:app:verifyNativeSkinBoundary`、`assembleDebug`、`lintDebug`、AndroidTest Kotlin 编译及组装：PASS。完整 Gradle 门禁执行了 975 个 JVM 测试，其中 `SystemCalendarImportTest.rejectedItemsArePersistedAsWarningsWithoutCalendarWrites` 1 项因 `commitCount()` 查询报 SQLite `SQLITE_BUSY`；因此全量 JVM 门禁为 FAIL，本切片没有修改该事务或测试。
- API 35 隔离 AVD `PowerCal_Native095_Parity_20261105` / `emulator-5580`：`monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged` 1/1 PASS。新 WebView 真实触控后，选中格、数字和计数颜色通过；`MonthFixture` agenda 可见，Store 快照不变。
- 两张 1080×2400 截图来自同一 AVD、同一 2026-11-05 选中日期及 `MonthFixture`。Native 原始 APK 图位于 `.local/android-test/reports/native095-month-radius-20261105/month-native-original-20261105.png`，SHA-256 `9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A`。WebView 图位于 `.local/android-test/reports/native095-month-count-20261105/month-web-selected-20261105.png`，SHA-256 `64ECBB6639B0AB45A794DF9FE311BDAEB54E9234ABF37BD4C69FFDE34D079BF5`。精确 `#DCE5FF` 填充框 Native `(471,627)-(609,757)`、Web `(471,630)-(611,762)`；文字颜色通过，而格子仍有数像素位置/尺寸差异。
- Debug APK SHA-256 `6CEA8B862C55C23F76262FE7D1B59F30A1B795EBFEE5AB78F7CCB7A321E7C64C`。包内仅含一个 `assets/powercal-clear-default.pcskin`，与源码字节一致；包 SHA-256 `85E2DD18688CF622823BFBF23233DD6957617CDF772CC551E1323CAF54EBE120`。

## 剩余差异

Month 网格表面及 agenda 卡片表面/间距仍须单独迁移与对照。本问题不构成 Month 页面完整等价，也不改变产品 Cutover `HOLD`。
