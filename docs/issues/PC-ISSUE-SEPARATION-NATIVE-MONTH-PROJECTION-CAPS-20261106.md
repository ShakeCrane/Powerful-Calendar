# PC-ISSUE-SEPARATION-NATIVE-MONTH-PROJECTION-CAPS-20261106

**状态：PASS（Web 月历 owner/occurrence 投影不再静默截断；Month 页面仍 PARTIAL）**
**代码提交：** `2e9651c6a8cbd1cdf22f7db3a5046c3a3243d301`
**冻结视觉基准：** Native PowerCal 0.9.5-rc.1，源码 `b1f7629f0c52812e1887443872f43aa6c49908c4`，原始 APK SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A8D73FC626F76`

## 问题与复现

Native 月历会显示传入的所有匹配记录。Web v3 的 `WebSkinMonthProjection.build()` 曾先保留最多 400 个 owner，再最多投影 350 个 occurrence。确定性 JVM 夹具在修复前复现：Native 预期 351 条，Web 只有 350 条。更大的 401 owner 夹具也会触发前置 owner 截断；12 个每日重复系列在 31 天月中应生成 372 个可见 occurrence。

Core 的窗口查询已分页读取匹配数据。丢失发生在 Web 月历展示投影，不涉及 Core 查询或业务 Store。

## 修复

- 删除 Web 月历 400 owner、350 occurrence 的硬上限；投影全部传入记录，按现有 Core recurrence API 展开和排序。
- 清除 Studio preview 模拟状态中的 350 条上限。
- 移除 Clear 页面上不再成立的截断提示。
- 使用正式 `tools/skin-studio/build_default_clear.py` 重建唯一默认包；身份保持 `powercal-clear-default`。
- 添加 3 个 JVM 回归，以及同一隔离 Store 上的 Android Native Compose / Web v3 对照。未修改 Core API、数据库或冻结业务规则。

## 验证

- 串行完整 Gradle 命令：` :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --no-daemon --max-workers=1 --no-parallel --console=plain ` — PASS，981/981 JVM，0 failures/errors/skips；两项边界、APK、Lint 与 AndroidTest Kotlin compile 均通过。
- AndroidTest 截图归档更新后再次编译并运行单项测试 — PASS，1/1。AVD `PowerCal_Wave2_QA35_20261104`，serial `emulator-5556`，API 35、1080×2400、420 dpi，WebView `124.0.6367.219`。
- 设备结果：401 个 owner row 与 Native 一致；2026-11-30 agenda 有 13 条；触摸目标打开 seq 390；测试前后 Store 快照一致。
- Studio v3 preview 11/11；single-default distribution 2/2；`app.js` 与 `v3-studio.js` 的 `node --check` PASS；`e2e_v3_month_selection.cjs` PASS。
- 包校验：Debug APK SHA-256 `C36388596CB759BD048A319BC36790BF04FB4F4D33A8962050A4E13F95CC4F34`，版本 `0.9.5-rc.1` / code `905`；APK 恰好含一份 `assets/powercal-clear-default.pcskin`。源码与 APK 包内 Skin SHA-256 均为 `974A63A4E8EC1A755DCD3ACB6A1F2E32390F588EA31E157E62586DEA6BE4B50A`，10 files / 411630 bytes。
- Android 真实截图：`native095-month-dense-native.png` SHA-256 `0D3EDE628E2F525478FE4E714CAB10046430F702B9BAD403904F1129A81EFD62`；`native095-month-dense-web.png` SHA-256 `F1D4CCBE4B37EB2CC2736CD7260527346918E146B885CF5360E592BA2C9FAFB4`。

## 限制与后续

配对截图来自隔离 App 内的 Native Compose 参考路由与 Web v3 Runtime，不是独立冻结原始 APK 在同一 401 条夹具上的截图。因此本 Issue 仅关闭“Web 投影截断导致内容缺失”，不签核 Month 视觉、其他状态、重复实例点击全套行为或整页 parity。详细设备证据在 `.local/android-test/reports/native095-month-projection-20261106/manifest.md`。Native 恢复、唯一正式默认 Skin 与 Product Cutover HOLD 保持不变。

下一项：冻结原始 APK 与 Web v3 定时 DEADLINE marker/title 的几何与实际点击目标对照。
