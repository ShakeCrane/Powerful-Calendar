# Native 0.9.5 双翼初始边缘位置复核

**状态：窄切片 PASS；Timeline 整页及 Native/Web 全局视觉等价仍 PARTIAL。**

## 基线与问题

- Owner 冻结源码：`b1f7629f0c52812e1887443872f43aa6c49908c4`。
- 冻结参考 APK：SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。
- 代码/仪器测试提交：`e9ec15e113025cb1f819e5d37b3eba7cd49c9e4d`。
- 问题来源是一次基于源码注释的错误预期：冻结 `TimelineScreen.kt` 以 `Int.MAX_VALUE` 创建左翼滚动状态，但 API 35 上真实 APK 完成启动和布局后观测值为 `scroll=0`。此次没有证明 Compose 内部为何将其钳制到 0；验收依据是冻结 APK 的可见运行行为，不是单独的初始化表达式。
- 当前 Web v3 在相同八事件夹具下也从 `scrollLeft=0` 启动。未保留 `app.js` 产品代码改动；本提交仅调整/新增验证代码与 Browser E2E 断言。

## 实际设备对照

两台均为隔离 API 35 AVD、1080×2400、420 dpi、GMT。冻结 AVD 为 `PowerCal_Native095_MenuReference_20261107` / `emulator-5560`，运行原始 APK；Web AVD 为 `PowerCal_Wave2_QA35_20261104` / `emulator-5556`，System WebView `124.0.6367.219`。相同八事件数据为左翼 ALARM lane 0–3、右翼 ACTIVITY lane 0–3。

| 指标 | 冻结原始 APK | 当前 Web v3 | 结果 |
|---|---:|---:|---|
| 左翼初始 scroll | 0 / max 458 px | 0 / max 174 CSS px | 起始边缘一致 |
| 左翼外侧 lane 3 事件 | x=32–253 px | x≈31.46–251.65 px | 横向边缘差小于 1.4 px |
| 右翼 lane 1 事件 | x=824–1045 px | x≈823.36–1043.55 px | 横向边缘差小于 1.5 px |
| 左右翼初始 scroll | 均为 0 | 均为 0 | 两翼各自保持外侧起点 |

屏幕 y 几何并非本切片的等价结论：Native 左事件顶边为 1047 px，Web 换算约 1041.64 px（约 5.36 px 差）。本切片只签核横向初始外边缘、双翼独立滚动及命中行为，不声称逐像素或整页等价。

两侧均通过真实触控打开指定事件并无修改关闭；左右翼分别滑动时另一翼位置保持不变。Native 原有 QA Store 基线为 3 drafts、8 commits、11 identities（含保留身份记录）；触控与关闭前后计数一致。Web 使用 UUID 隔离 Store，8 commits、8 identities；触控与关闭前后计数一致。测试未清空 AVD、未修改系统日历；Native AVD 保留其专用合成夹具。

## 验证结果

- `Native095RawAndWebDualWingInstrumentedTest#frozen095NativeApkDualWingFixtureAndEventTouch`：冻结 APK 目标测试 **1/1 PASS**。
- `Native095CurrentWebDualWingInstrumentedTest#currentWebV3StartsBothWingsAtOuterEdgesAndKeepsThemIndependentlyTouchable`：真实 Android WebView 目标测试 **1/1 PASS**。
- 串行 Gradle 门禁：**988/988 JVM，0 failures/errors/skips**；两项源码边界、assembleDebug、lintDebug、AndroidTest 编译均 PASS。
- `node --check tools/skin-studio/tests/e2e_v3_browser.cjs`：PASS；完整 `e2e_v3_browser.cjs`：`BROWSER E2E PASS`。
- `python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk`：PASS。唯一默认包 `powercal-clear-default.pcskin`，10 项 / 420396 bytes，源码与 APK SHA-256 同为 `dd49636784dfab7ee356a114d3d4db0963087f37a4e1f0bc3ce505b766f9d14e`。Debug APK SHA-256 `3E95E2992AD89EAE7BC8C0B59084F39504A27432A3533532BB78B1D6AD58EA56`。

## 图像证据

- 冻结原始 APK：`.local/android-test/reports/native095-timeline-initial-left-20261107/native095-original-apk-dual-wing-final.png`，SHA-256 `6D99B750900D4207618A16B9C9DEE2BEF4FC11386BE5AD421B1FF5C63A0AAF33`。
- 当前 Web v3：`.local/android-test/reports/native095-timeline-initial-left-20261107/web-v3-dual-wing.png`，SHA-256 `B38F9E797A0AF6C279411819ED8658327E7C921B24F813179F51C6270F947525`。
- 命令、目标设备身份、复测记录与运行输出：`.local/android-test/reports/native095-timeline-initial-left-20261107/manifest.md`。

## 验收边界与后续

本结果只关闭“左翼初始 lane/scroll 与双翼独立触控”这一误判差异。日期型 Deadline 的独立日期带与 23:59 时间轴锚点尚未在同一 API 35 Native APK/Web fixture 上完成 A/B，Browser E2E Mock 断言不能代替设备证据；该项为下一验证点。空/暂存、AI 锁定、恢复/冲突、删除确认、完成/撤销、IME/无障碍以及 Timeline 其他密集/空态均未由此签核。Native 安全恢复保持，正式 Cutover 继续 HOLD。
