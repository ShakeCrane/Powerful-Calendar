# PC-ISSUE-SEPARATION-NATIVE-TIMELINE-POINT-TITLE-20261106

**状态：PASS（仅冻结原生 ALARM 时间点标题样式；Timeline 整页仍 PARTIAL）**
**代码提交：** `75a3510c5affa18c08dbb0d23482496d0240453d`
**日期：** 2026-11-06

## 冻结参照与问题

Owner 指定的参考是冻结 Native PowerCal 0.9.5-rc.1，源码 SHA `b1f7629f0c52812e1887443872f43aa6c49908c4`，原始 APK SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。

`NativeTimelineScreen.kt` 的时间点标题使用 13sp、Material `bodyLarge` 的 24sp 行高和 0.5sp 字距，颜色取事件种类主色；ALARM 标题在时间点线下 2dp。Web v3 原规则为 11px/15px、较深的 `alarm-ink`/`deadline-ink`，且 ALARM 标题距锚点 3px。直接原始 APK/Web 同夹具截图确认这造成字号、颜色与墨迹框位置差异。

## 修改

- 默认 Skin 时间点标题改为 13px、24px 行高和 0.5px 字距。
- ALARM/DEADLINE 标题改用与原生相同的事件主色。
- ALARM Web 标题锚点从 +3px 调至 +2px；DEADLINE 标题锚点从 -18px 调至 -26px，以配合 24px 行高和原生线边距。
- 使用正式 `tools/skin-studio/build_default_clear.py` 重建默认 `.pcskin`。
- Android instrumentation 增加标题布局/样式断言、原生 Compose 标题框读数，以及等待 WebView 视觉状态完成的截图步骤。

没有改动 Core/Host 业务行为、Native 恢复、皮肤 ID、正式包数量或 Store 语义。

## 实测结果

冻结原生 APK 与 Web v3 在各自隔离 API 35 AVD 上使用同一八事件夹具：1080×2400、420dpi、GMT。Native AVD `PowerCal_Native095_RawFixture_20261106` / `emulator-5590`；Web AVD `PowerCal_Wave2_QA35_20261104` / `emulator-5556`，WebView 124.0.6367.219。原始 APK App 仍为 0.9.5-rc.1；只安装了 AndroidTest APK。Web 更新采用 `adb install -r`；未清除应用数据。

- Native 标题外框：屏幕像素 `[40,1047]-[226,1110]`，186×63px。
- Web DOM：`left=15, top=350.25, width=71.4583, height=24` CSS px；按记录的 WebView/视口缩放换算为屏幕 `[39.32,1046.89]-[226.64,1109.85]`。与 Native 左/上/宽/高差分别约 0.68/0.11/1.32/0.04px。
- Web 的计算样式为 13px、24px、0.5px、`rgb(61,95,216)`；时间点线 top 为 347.5 CSS px，标题 top 为 350.25 CSS px。
- 真实截图里的蓝色标题墨迹 bbox：Native `(42,1066)-(220,1089)`，Web `(42,1065)-(222,1088)`；差 2px 宽、1px 高。
- Native 与 Web 事件点击、打开编辑器、返回均通过。两个隔离 Store 均保持 `drafts=0, commits=8, identities=8`，无夹具外写入。

串行完整 Gradle 门禁成功，141 份 JVM XML 共 977/977、0 failures/errors/skips；边界检查、Debug assemble、Lint、AndroidTest 编译/组装通过。正式分发测试 2/2、Node 语法检查和默认包校验通过。包内仍只有 `powercal-clear-default.pcskin` 一份，10 files / 410943 bytes；源码与 APK 资源 SHA-256 `95526F9DF0C1524790E48EFF3ECDCA6F6848C078BBD34825D2FC2463D0E9D58E`。Debug APK SHA-256 `AA7D061C7EE412BBF368E33B237869539E076992FEE7A8D3A7E79E3CAB79CD5D`。

## 证据和边界

完整命令、环境、日志和截图哈希见 `.local/android-test/reports/native095-alarm-point-title-20261106/manifest.md`。Web 截图通过 `WebView.postVisualStateCallback` 后采集；更早一次未等待视觉回调的空白 PixelCopy 已被最终有效截图取代。

本轮直接验收的是 ALARM 标题。DEADLINE 样式/位置使用了相应原生令牌和换算，但本轮没有对 DEADLINE 做真实设备对照，因此仍为 NOT TESTED。深色、大字体、复发事件、其它标题状态、Timeline 整页及其他 Native 页面继续各自未签核；Product Cutover 保持 HOLD。
