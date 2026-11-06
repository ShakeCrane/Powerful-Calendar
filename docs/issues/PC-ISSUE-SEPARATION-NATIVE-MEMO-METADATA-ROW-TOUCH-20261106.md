# PC-ISSUE-SEPARATION-NATIVE-MEMO-METADATA-ROW-TOUCH-20261106

**状态：PASS（Memo 行元数据、行几何和打开触控窄切片；Memo 页面整体仍 PARTIAL）**
**代码提交：** `f6183f3b7c76166bf65ed069494313b0e39ecb33`
**日期：** 2026-11-06

## 参照与问题

Owner 指定的表现基准为冻结 Native PowerCal 0.9.5-rc.1，源码 SHA `b1f7629f0c52812e1887443872f43aa6c49908c4`，原始 APK SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。

冻结 `MemoCenterScreen.kt::NativeMemoCenterScreen` 每行显示标题、事件种类、按记录时区转换的本地日期、重复标记和序号；行卡片可打开相应记录。Web `WebSkinMemoProjection` 之前没有投影 kind、zoneId、repeating，Clear 行只有完成控件和标题，缺少冻结基准的信息与可点区域。

## 修改

- 在现有 Memo projection 中透传 `kind`、`zoneId`、`repeating`；不新增业务 API、不计算重复规则、不触碰 Core。
- Clear Web v3 以 Core 提供的时区格式化本地日期，显示类型、日期、重复标记和四位序号。
- Memo 行非 action 区点击转发到现有 `openEvent`；完成控件保留独立 `toggleMemo` action。
- 调整 Native 风格 CSS 使行、标题、元数据、序号布局和底色接近原始 APK。
- 经正式 `build_default_clear.py` 重建唯一默认包。

## 实测

- 冻结 APK 原样运行于隔离 AVD `PowerCal_Native095_RawFixture_20261106` / `emulator-5590`；API 35，1080×2400，420 dpi。没有清除 AVD 或 App 数据；原八条 Timeline 夹具保留，测试追加的三条确定性 Memo 夹具保留以便继续比对，并恢复引导偏好。
- 当前 Web v3 运行于隔离 AVD `PowerCal_Wave2_QA35_20261104` / `emulator-5556`；API 35、1080×2400、420 dpi、WebView 124.0.6367.219。它使用测试专用临时数据库，测试完成后关闭并删除该临时数据库。
- 原生行、Web 行的屏幕边缘最大偏差约 2.86 px；两端抽样行填色 RGB `(246,248,252)`。日期文本、无日期、重复标记、序号均在真实画面可见。Web 实际点击元数据打开正式编辑表面，Native 点击对应行打开原生编辑器。
- 两端在打开与未修改关闭后均断言 Store 计数未变、夹具记录快照未变。Native 观察计数 `drafts=1 / commits=11 / identities=11`；Web 临时 Store `drafts=0 / commits=3 / identities=3`。
- `frozen095NativeMemoRowsUseFrozenMetadataAndOpenWithoutStoreWrites` 1/1 PASS；`currentWebV3MemoRowsUseFrozenMetadataAndOpenWithoutStoreWrites` 1/1 PASS。完整串行 Gradle 门禁 977/977 JVM、0 failures/errors/skips；两项 source boundary、APK、Lint、AndroidTest 编译/组装 PASS。KDoc-only 测试说明改动后再次编译 AndroidTest PASS。
- Python 分发测试 2/2 PASS，Node `--check app.js` PASS。Studio Python 全套 86/89；失败是三个既有路由/架构源码形状断言，与本次 Memo 改动无关，已在测试日志与审计中列出，不记为 PASS。
- 正式包校验确认 APK 仅含 `powercal-clear-default.pcskin` 一份，10 项、411435 bytes；源码和 APK 资源 SHA-256 都为 `87D5A3892A83B8241FC59C5EA1012937F03ACF05659B9757099C00352F42B504`。Debug APK SHA-256 `E8C932F7D259F6EDFF601553895F37A26E55E52B980FEB871A0781A625FFB0B6`。

完整执行命令、运行日志和截图哈希：`.local/android-test/reports/native095-memo-metadata-20261106/manifest.md`。原生截图 SHA-256 `B1B6062129214CB48F2140CAAB5211015D4F568105235C3D7840D17AC8E66F31`；Web 截图 SHA-256 `8630F3DAAED4F42AE4CD6E534070239E7F32256ED256E3C924A73DD532914295`。

## 剩余边界

这不是 Memo 页面完整签核：本切片没有验证完成/撤销、重复实例完成、分页边界、所有事件类别的 Memo 呈现或筛选状态。其它页面与整体 Native→Web parity 继续 PARTIAL；Native 独立恢复与 Product Cutover HOLD 不变。后续有限差异项为 Month agenda 非网格区域的横向切月触控范围。
