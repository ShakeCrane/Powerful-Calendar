# PC-ISSUE-SEPARATION-NATIVE-MONTH-PAGE-SWIPE-20261106

**状态：PASS（整页 Month 横向手势命中、阈值和触控仲裁窄切片；Month 页面整体仍 PARTIAL）**
**代码提交：** `7e98e393ddf075db206e37a7bfa599afea6f47bd`
**日期：** 2026-11-06

## 冻结基准与问题

Owner 指定的视觉和交互基准是冻结 Native PowerCal 0.9.5-rc.1，源码 SHA `b1f7629f0c52812e1887443872f43aa6c49908c4`，原始 APK SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。

Native `MonthScreen.kt::NativeMonthScreen` 将横向切月手势绑定到整页 `LazyColumn`，平台触摸 slop 后必须再移动严格大于 72 dp。原 Web v3 只从 `#monthGrid` 记录起点，且阈值边缘行为不同；在 agenda 事件卡上滑动还需避免把抬手 click 误当成打开事件。

## 修改

- `WebSkinHost` 将 Android `ViewConfiguration.scaledTouchSlop / density` 注入 Web entry HTML 的 presentation-only meta。Web 继续使用相同 `.pcskin` 解析、Runtime 和 Host action。
- 默认 Clear 在整个 `#monthPage` 监听真实触控起止；保留纵向滚动和 `pointercancel` 行为，横向导航要求平台 slop 之后的位移严格大于 72 dp 对应 CSS 距离。
- Android WebView 124 实测可信 PointerEvent 的 `pointerType` 为空字符串。实现只接受可信空类型或明确 touch/pen，排除 mouse；recognized horizontal drag 消费关联 click，避免点开 agenda 事件。
- Studio browser E2E 覆盖整页各区域、mouse 排除、短 tap、平台 slop、严格阈值、竖向操作和 Camera 不变。
- 正式包通过 `tools/skin-studio/build_default_clear.py` 重建，未直接编辑编译 ZIP。

## 原始 APK 与 WebView 实测

- 冻结原始 APK 运行在隔离 AVD `PowerCal_Native095_RawFixture_20261106` / `emulator-5590`；当前 Web v3 APK 运行在隔离 AVD `PowerCal_Wave2_QA35_20261104` / `emulator-5556`。两台均 API 35、1080×2400、420 dpi；WebView `124.0.6367.219`。原始 APK 未替换，Web 包安装在独立测试应用。
- 使用确定性 fixture 对照 Native Compose 和 WebView：5 dp 位移保留 picker 点击；20 dp 横移被认作手势，取消点击但不切月；8 dp 平台 slop 下，79 dp 总距离不导航、81 dp 导航。页头、月份区域、agenda 事件卡及页尾均验证切月。
- 滑过 agenda 事件卡不会触发事件打开；月份 picker/日期点击与手势后的选中状态符合预期；Timeline Camera 与手势前后的 Store 计数不变。测试 Store 只在建立 fixture 时写入，断言区间内没有业务写入。
- 独立 APK 复核中，页头 208 物理像素（79.24 dp）保持 November，214 px（81.52 dp）切至 December，反向滑动恢复 November。

## 验证结果

- 串行 Gradle 完整门禁 PASS：978/978 JVM、0 失败/错误/跳过；`verifySkinSourceBoundary`、`verifyNativeSkinBoundary`、`assembleDebug`、`lintDebug`、`compileDebugAndroidTestKotlin` 均 PASS。
- `NativeClearMonthOccurrenceParityInstrumentedTest.nativeAndClearMonthGridSwipesNavigateOneMonthWithoutSelectingOrWriting` 在隔离 API 35 AVD 1/1 PASS。
- Studio distribution 测试 2/2、v3 preview 测试 11/11、变更 JS 语法检查和 Browser E2E PASS。
- 初次完整 JVM 门禁出现既有 `SystemCalendarImportTest.rejectedItemsArePersistedAsWarningsWithoutCalendarWrites` SQLite `SQLITE_BUSY`；定向重跑通过，后续完整门禁 978/978 通过。没有删除断言、修改 Core 或掩盖异常。
- 早期 Android 尝试分别定位到测试 helper 的触控区域要求过窄，以及 Android WebView 124 真实可信 PointerEvent 使用空 `pointerType`；修正后最终设备测试通过。早期 Browser E2E header timeout 的日志也保留，最终在独立 profile/report 目录重跑通过。

最终 Debug APK SHA-256：`479B01BF9D4D8DC2CFD30839047DAFA7025DA7BE75A327732DBE75DF645181EB`。正式包 `powercal-clear-default.pcskin` SHA-256：`E5CA9803AAD5A450AE639773477A7F2EDEF047334CFA567527B2E3296738EAB8`；10 个条目，APK 仅有一份 `.pcskin`，源码与 APK 资源字节一致。

完整命令、重试日志、Android instrumentation 日志、XML 与截图哈希：`.local/android-test/worktrees/skin-single-default-closure/.local/android-test/reports/native095-month-page-swipe-20261106/manifest.md`。原始截图：

- Native 页头 208/214 px：`3A1125A5BA1A86A866C198FB292E3C7C8246C216FF2A4B4EF376C3254455CC29` / `59618D27C515C9323AFE8C3D772B4BE6524C1C4F2B89254B3D6BE5CDB78E901C`。
- Web 页头 208/214 px：`50593851EDB1A0D1A49A57DF173401BE5F26B58D6B03E0ADFA4510F400BCA486` / `B0D9BCAB797095432E4425AADA9ECAA9C9955ED83FE75A6E63E7ECEB1CB34070`。

## 未覆盖范围

PASS 仅适用于整页水平手势命中区域、平台 slop、严格阈值、touch/mouse 仲裁及关联 click 抑制。Month 重复投影的 400 owner / 350 item 截断、其它视觉状态、月历完整功能、其它 Native 页面、TalkBack、真机和 OEM 兼容仍未签核；Month 和全项目视觉/交互等价继续 PARTIAL。Core 业务规则、持久化皮肤 ID、Native 恢复路径及 Product Cutover 状态均未改变，Product Cutover 仍 HOLD。
