# Native 0.9.5 日期截止时间轴几何、标记与拖动

**状态：**窄切片 PASS；Timeline 整页与 Native 0.9.5 视觉迁移仍 IN PROGRESS。Product Cutover 继续 HOLD。

**冻结基准：**`b1f7629f0c52812e1887443872f43aa6c49908c4`。

**原始参考 APK：**`.local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-b1f7629-debug-core-HOLD.apk`，SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。

## 发现与修复

冻结 Native `NativeTimelineScreen.kt` 在日期条显示日期级 Deadline，同时把同一事件绘制在真实时间轴的 Core 视觉锚点（日期结束的 `23:59`），保留事件点击目标；日期本身仍明确显示“无具体时刻”。Web v3 原先先读取可用时间轴高度、再显示日期条，导致活动几何按错误高度映射；同时把日期级 Deadline 从时间轴点过滤掉。其日期条缺少 Native 的旗标、tertiary-container 色和 16dp 内容边距。

修复位于 `tools/skin-studio/default-clear/source/web/app.js` 与 `tools/skin-studio/default-clear/source/web/native-095.css`：先呈现日期条再测量视口；使用 Core 已提供的 `visualAxisMillis` 投影值渲染对应时间轴点；芯片改为 Native 的旗标横排、色彩和边距；无障碍文本只报告日期，不把日期精度伪装成时刻。

Android 触控轨迹还复现了独立的拖动取消原因：日期芯片的 `touch-action:auto` 使 WebView 在长按后向系统派发 `pointercancel`/`touchcancel`，Host 从未收到移动动作。现将芯片设为 `touch-action:pan-x` 并禁用长按文本选择，以保留水平日期条滚动并让竖向拖动归时间轴。

## 实机验证

测试在隔离 API 35 AVD `PowerCal_Native095_Original_20261105` 上完成，最终 ADB 序列号 `emulator-5554`，1080×2400、420dpi、GMT、Android System WebView `124.0.6367.219`。用例在同一仪器运行中分别使用 Native Compose 与正式 Web v3/Host，创建同一组合成日期 Deadline 和 09:15 活动记录。

`TimelineDragWebViewInstrumentedTest` 整类结果 **3/3 PASS**。日期 Deadline 专项断言覆盖：

- 活动 top 与高度按日期条显示后的可用时间轴高度计算，误差不超过 1.5 CSS px。
- Deadline 时间轴点位于 Core `visualAxisMillis` 锚点，误差不超过 1.5 CSS px；屏幕读屏文案不宣称具体时刻。
- 日期芯片的横排旗标、Native 浅/深色容器和前景色、16 CSS px 内容起点以及 `pan-x` 触控策略。
- 点按 23:59 点打开已有 Deadline，不提交、不创建草稿。
- 长按日期条经正式 Host 移动 API 创建日期型草稿；原始事件、活动、revision、commit 数与 identity 数未改变，目标日期与 Native 参考相同。

屏幕截图均为 Android PixelCopy。原始 APK 实屏截图 `.local/android-test/reports/native095-timeline-20261106/native-timeline-nov15.png` 的 SHA-256 为 `1DA4516BF4CFF4D1A297F53D220613A902BA03D0C4243A0CAFADEE0223D002A9`。当前 Web 实屏截图 `.local/android-test/reports/native095-timeline-fix-20261106/clear-date-deadline.png` 的 SHA-256 为 `BCDD33726E881EB30316E961F3BA3DEB52744E9636DE8AC0FD74B6A92E50795C`。前者是原始四事件样例的 2026-11-15 截图，后者是两事件仪器夹具的 2026-11-06 截图；它们用于核对页面几何与视觉层级，不把不同数据集的像素差异声称为逐像素等价。同一测试夹具 Native/Web PixelCopy 分别为 `native-date-deadline.png` (`262F42D4F9821FDD78B838A7F134320EA58B389FAA3692E61B30DE1A71E2236D`) 和 `clear-date-deadline.png`（如上）；Native 这张是 Compose instrumentation 容器截图，系统栏/顶端宿主 Insets 与原始 APK 窗口不同，因此不用于替代原始 APK 作为屏幕边界基准。

## 构建与检查

- `:app:verifySkinSourceBoundary`, `:app:verifyNativeSkinBoundary`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`, `:app:compileDebugAndroidTestKotlin`：串行门禁 PASS；JUnit XML 为 **976 tests, 0 failures, 0 errors, 0 skipped**。
- `:app:packageDebugAndroidTest`：PASS。最终通过显式 ADB 序列号运行仪器测试；未使用会自动枚举所有模拟器的 connected task。
- Android 实测：`TimelineDragWebViewInstrumentedTest` **3/3 PASS**。原重复事件测试的 Host 文案断言从过时完整短句改为核验当前 `unsupported` 说明的语义；实测 Host 仍拒绝重复事件逐实例拖动，且 Store 不变。
- `node --check tools/skin-studio/default-clear/source/web/app.js` 与 `node tools/skin-studio/tests/e2e_clear_studio_bridge.cjs`：PASS。
- `python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk`：PASS；唯一包 `powercal-clear-default.pcskin` 源码与 APK 资源 SHA-256 均为 `37B3C2FBECE928F547191B759BCC8DAEFB792799233506F8EAE861036BB49BF6`。APK SHA-256：`F2F4CBC14737437155E7CB373391CC94D44DAD27892FC788BABFB262ABBA4361`。
- `git diff --check`：PASS。

## 设备处理记录与范围限制

一次早期 Gradle `connectedDebugAndroidTest` 自动命中了所有已连接设备，并卸载了原生参考 AVD 中的应用。随后已在同一隔离 AVD 重装哈希确认的原始 APK，并恢复了合成测试夹具；没有触及真机或真实用户数据。此后所有设备测试都通过 `adb -s emulator-5554` 显式指定目标 AVD。后续对照不得再调用会自动枚举设备的 connected Gradle 任务。

本窄切片不签核双翼水平滚动、框外标题与贪心折叠、所有视口/字体状态、提醒与其它 Timeline 控件或整个 Timeline 页面。它们继续按 `docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md` 分项处理。
