# Native 0.9.5 密集月历 agenda 顺序与折行对照

**状态：**窄切片 PASS；Month 页面整体仍 PARTIAL。  
**冻结视觉基线：**`b1f7629f0c52812e1887443872f43aa6c49908c4`。  
**原始 APK：**`.local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-b1f7629-debug-core-HOLD.apk`，SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。

## 验证范围

在两个隔离 Android API 35 AVD 上分别安装冻结 APK 与当前 Web v3 APK，使用相同的四条合成记录和日期 `2026-11-15`。真实设备路径为冻结版 `emulator-5558`、Web 版 `emulator-5582`；屏幕 1080×2400、420 dpi、字体比例 1.0、GMT，WebView `124.0.6367.219`。

用户在月历选择 11 月 15 日并滚动到 agenda 后，两个版本的卡片顺序均为 DEADLINE `eventSeq=1`、MEMO `4`、ALARM `3`、ACTIVITY `2`。活动长标题在 Native 和 Web 中均显示为两行。分别点击第一张事件卡，两个版本都打开 `#0001 DEADLINE`。Web DOM 与 Host 投影顺序均为 `[1,4,3,2]`，相邻卡片间距为 12 CSS px。Native Compose 与 Web 的 Android 仪器对照用例 `denseMonthAgendaOrderAndLongTitleWrappingMatchNativeReferenceRoute` 结果为 `1/1 PASS`，并断言 Store 未变化。

## 证据

- 冻结 APK 实屏截图：`.local/android-test/reports/native095-month-dense-20261106/emulator-5558-month-15-scrolled.png`，SHA-256 `9559379E91E9E11E1AC80532F8A0FE6587FC39D8A158811D2BE10B3480BE7C5A`。
- Web 最终包实屏截图：`.local/android-test/reports/native095-month-dense-20261106/emulator-5582-month-final-dense.png`，SHA-256 `830790CBF809776C7CD484B0F47178754F67988FCD9BBCC09D30C64C44E7308E`。
- 测量与 DOM：`.local/android-test/reports/native095-month-dense-20261106/dense-month-measurements-final.log`、`month-geometry-comparison.json`。
- Android 仪器日志：`.local/android-test/reports/native095-month-dense-20261106/android-current-final-dense-month-test.log`。
- 最终 Web APK SHA-256：`B9D7105231CE8ECEBAE2A83E6701E4F46D0425180518B0470BAFD465719CA0EF`。默认包 `powercal-clear-default` SHA-256：`1A5C89E0A0CFA8AD30A7CEDFF28C2B382FF3962A78DF47AA00BA746E9D552918`；构建验证确认 APK 中只有一个 `.pcskin`，且资源与源码包逐字节相同。

## 修改与检查

Android 对照/回归测试位于 `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/NativeClearMonthOccurrenceParityInstrumentedTest.kt`。对应测试提交为 `b578c52e88e65ba5eee7c4e9946c6907e3c303e8`。此前的 Month 文字前景/Material 排版改动来自 `1ffd87499c8d6d6089eb1c158178d6c39f1e9bf5`。

本轮全量 Studio Python 套件实际运行 89 项，86 项通过、3 项失败；失败项为 `test_full_route_registry_has_one_restore`、`test_privacy_editors_remain_host_owned_but_placeable` 和 `test_native_settings_and_hidden_activity_rescue_are_both_available`，因此套件不标 PASS。其余当前 CSS 相关检查包括源码边界、`assembleDebug`、Lint、正式包验证、`edit.js` Node 语法和 Clear Studio bridge 浏览器 E2E，均通过。本轮没有运行完整 JVM 测试。

## 限制

两个 AVD 的真实滚动手势起始点相差约 5–6 CSS px；本证据不签核绝对屏幕纵坐标或逐像素相同。卡片边界/完整 Month 页面状态、深色主题、更多重复实例状态以及其它 Native 页面仍待对照。本 Issue 的 PASS 仅覆盖卡片顺序、长标题两行折行和真实点击目标，不提升 Month 页面整体状态。
