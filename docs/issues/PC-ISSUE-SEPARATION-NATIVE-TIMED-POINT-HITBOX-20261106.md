# PC-ISSUE-SEPARATION-NATIVE-TIMED-POINT-HITBOX-20261106

**状态：PARTIAL（可视时间窗内的点位热区方向和视口边缘命中 sliver 已移植；Timeline 整体仍未闭合）**
**代码提交：** `06649a334d12d0e7e9371fcc08d26a3fcc7ee9ad`（可视窗内方向）与 `7858ee357714a29fba955d9acf7b36715ba22c47`（边缘候选及命中）。
**基准：** 冻结原生 0.9.5-rc.1，源码 `b1f7629f0c52812e1887443872f43aa6c49908c4`  
**日期：** 2026-11-06

## 原因

Native `NativeTimelineScreen.kt` 对 ALARM/DEADLINE 使用 56dp 命中区：ALARM 锚点在区块顶部，Deadline 锚点在区块底部。默认 Web Skin 原来使用 44 CSS px 且统一居中，尽管线和文字接近锚点，触控归属仍不同。

## 修改

`tools/skin-studio/default-clear/source/web/app.js` 现将点事件热区设为 56 CSS px，并按事件类型沿锚点方向布置。`tools/skin-studio/tests/e2e_v3_browser.cjs` 增加 CSS 几何断言。通过官方构建器重建 `app/src/main/assets/powercal-clear-default.pcskin`。

没有修改 Core/Host 业务、数据库或 Native 恢复。出厂仍只有 `powercal-clear-default.pcskin`，ID 未变。

## 验证

- Gradle 全门禁：981/981 JVM 通过，边界检查、assembleDebug、lintDebug、AndroidTest Kotlin 编译通过。
- Edge targeted geometry probe：Alarm 56 CSS px 的上边缘与 marker 重合；Deadline 56 CSS px 的下边缘与 marker 重合。`node --check` 通过。
- 隔离 API 35 AVD 实际运行：原始 APK `PowerCal_Native095_RawFixture_20261106/emulator-5590` 与更新 Web v3 `PowerCal_Wave2_QA35_20261104/emulator-5556` 均为 1080×2400、420dpi、GMT。使用相同标题、类型、日期和时刻；两端点按可见标题均打开对应 Deadline 编辑器。Web APK 通过 `adb install -r` 安装，没有清除应用数据。
- Raw Native 热区 `[267,1112]-[477,1259]`；Web 修复后 `[267,1109]-[490,1262]`。方向相同，Web 在此 AVD 的语义框高 6px、宽 13px；这是已知测量差异。
- 标题墨迹和线条 bbox 见 `docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md` 和 `.local/android-test/reports/native095-deadline-title-20261106/manifest.md`。
- Studio Python 89 项中 3 项与本次变更无关的既有架构源码形态断言失败；完整 Browser E2E 在预览色板前置断言失败，时间轴部分未运行。单独 Edge 几何探针通过，不把完整 Browser E2E 计为 PASS。

## 残留差异

Native 会保留锚点在可视时间窗外 80px 以内的点事件，使其热区 sliver 可触。Web Host/renderer 目前精确过滤可视窗口外的点事件，因此窗口上方的 ALARM 和下方的 DEADLINE 可能没有对应节点。需要将 Android viewport 与 Web CSS viewport 的实际缩放纳入有界 margin，再用两端真实设备点击 sliver 验证。整体 Timeline 和默认 Skin parity 继续 PARTIAL，Product Cutover 继续 HOLD。

## 2026-11-06 补充：视口边缘点事件

上面的“残留差异”记录的是 `06649a3` 时的状态。本次已实现并验证有限边缘候选：Host 投影只从既有有界查询中优先保留可见项，再用剩余 `MAX_ITEMS` 名额保留最近的定时 ALARM/Deadline；Web renderer 仅绘制锚点落在视口外不超过 `80 / devicePixelRatio` CSS px 的点事件。ACTIVITY 与日期型 Deadline 的窗口规则不变，远离视口的候选不会渲染。

- 冻结原始 APK 文件与 `emulator-5590` 上已安装 `dev.powerfulcalendar` 的 `base.apk` 均为版本 `0.9.5-rc.1` / code 905，SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。设备 `PowerCal_Native095_RawFixture_20261106`，API 35，1080×2400，420 dpi；未清除应用数据。
- 在原始 APK 中通过 24h→16h/屏缩放与时间轴纵向滚动，将既有 12:00 Deadline 移至视口上缘。截图中 Native Timeline surface 顶边约为 `y=391`，截止 marker 约为 `y=461`，故 56dp 点目标有约 70 physical px 进入画面；点按 `(360,430)` 后打开对应 `编辑事件 #0012`。未保存、未修改记录。证据截图：`.local/android-test/reports/native095-edge-points-20261106/screenshots/native095-edge-zoom1-scrollup.png`（SHA-256 `FF2E74340EB23D2500FBAC83928A58F8088B94172C5478F4AEDD79A47A3A8FE7`）和 `native095-edge-raw-tap.png`（SHA-256 `773EAE34564A89F230033BEFFE87F0E2B613C615EDF77059DB6B2532E9D31C4B`）。
- 同一隔离 API 35 WebView AVD `PowerCal_Wave2_QA35_20261104` / `emulator-5556`（WebView 124.0.6367.219）运行 `TimelineDragWebViewInstrumentedTest#nativeAndClearExposeTimedPointHitSliversAtBothTimelineViewportEdges`，1/1 PASS。Native viewport 为 `[0,391,1080,2127]`；Web CSS surface 为 `top=100,bottom=762.2857`。ALARM 与 Deadline 的目标分别有 `42.2083`、`42.4940` CSS px 在视口内，浏览器实际触点命中 `.tEvent ALARM` / `.tEvent DEADLINE`；marker 分别在边界外 `14.0417`、`13.2560` CSS px。8h 远的 ALARM 不渲染。
- Native 与 Web 两条测试路径点按后 Store 均保持 `(0 drafts, 3 commits, 3 identities)`。对应截图 `native095-edge-native.png` SHA-256 `7E9C188D24AB147F40B8AD102091392848F4BBE56E33B56DE0972DD7EBAA9A3C`、`native095-edge-web.png` SHA-256 `45FA2A0412C4AF007CDE6E2C274DEE1A79D33F63CD53FC5A23CE425ED509CABE`。
- 最终串行 Gradle 门禁 PASS：982/982 JVM、0 failures/errors/skips，两个源码边界、APK、Lint 与 AndroidTest Kotlin 编译通过。APK SHA-256 `39A89C98A0789963EFABCD8DC13106D55859ADCBB24378B10D11920ACA91D634`；唯一默认包 10 个文件、411738 bytes，源码和 APK 内包 SHA-256 均为 `7AA3700A0D1E1CE1F19943CE012595E55A933B8D75FBFDBE306987537CDFE3EC`。

此补充证明边缘点位命中这一窄切片，不证明整个 Timeline、其他页面或全产品视觉等价。Web 与 Native 语义框具体尺寸差异、标题/框外布局、其余页面状态仍需逐项记录；Product Cutover 继续 HOLD。

## 2026-11-06 补充：可视窗内目标框与 raw 边缘触控

此前“Web 语义框宽 13px、高 6px”的测量使用不同相机和未配对的语义矩形。本次使用同日期、同标题、同 revision 的单一 12:00 DEADLINE 和相同 Today Camera 配对测量当前 Native Compose 与 Web v3：Native `[267,1112]-[488,1259]`；Web `[267.6283,1111.8183]-[487.8225,1258.7352]`，四边差不超过 1.5 physical px。Web DOM 仍为冻结目标 84×56；没有 CSS 调整。

在新版 WebView 的真实触控中，右边界内样点 x=487 打开事件，x=489 不打开。冻结原始 APK 的现有 `#0012` 也在 x=476、487 打开，在 x=489 不打开；没有 Save/Delete，SQLite 只读确认记录仍存在且未 retire。uiautomator 对 raw 节点曾报告较窄的 `[267,1112]-[477,1259]`；该可访问性边界与实触控范围不一致，故不据此缩小产品触控区。

最终测试 `TimelineDragWebViewInstrumentedTest#nativeAndClearVisibleDeadlineHitBoundsMatchAndRespectEdges`：1/1 Android instrumentation PASS。精确坐标、截图哈希、JUnit XML 和重试记录见 `.local/android-test/reports/native095-point-bounds-20261106/manifest.md`。本结果只关闭一个可视窗内 Deadline 目标框切片，不关闭 Timeline 整页；Product Cutover 仍 HOLD。
