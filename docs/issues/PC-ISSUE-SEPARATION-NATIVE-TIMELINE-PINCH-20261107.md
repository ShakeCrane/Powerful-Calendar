# Native 0.9.5 时间轴双指缩放与 Camera 标签对齐

状态：**窄切片 PASS**。只验收空时间轴上的双指垂直缩放、双击时间尺复位、再次缩放和缩放标签精度。完整 Timeline parity 仍为 `PARTIAL`，Product Cutover 仍为 `HOLD`。

## 冻结参考与复现

- 冻结源码：`b1f7629f0c52812e1887443872f43aa6c49908c4`。
- 原始 APK：`.local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-b1f7629-debug-core-HOLD.apk`，SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。
- Native 触控实现：冻结 `TimelineScreen.kt` + `AxisPinch.kt`。时间缩放取 `AxisPinch.decompose(...).y`；双击中轴恢复 24 小时。
- 原 Web v3 没有连续双指相机手势；仅有按钮缩放和单指 Camera pan。Web 还把浮点 Camera 值原样显示，因此 Native `18.0` 对应 Web `18.004479170255458`。

## 修复

- 默认 Skin 实现 Native 的纵向 pinch 正则化、增益、中心阻尼、稳定基线、指针捕获/重基线与 pinch 后剩余单指 pan；pointer cancel 会取消整段 Camera 手势。时间尺 reset hit 的 pointerup 会先移除跟踪 pointer，避免双击后留下过期指针。
- 增加正式 `setTimelineCamera(anchorMillis,hours)` Web Host action。Android 解码器使用闭合字段与安全整数/范围检查；`PowerCalApp` 仅更新展示相机。该动作不调用 Core API 或 Calendar Store。
- Skin 和 Studio Preview Mock 按投影时区拒绝本地年份不在 1900–2200 的 Camera 目标，避免桥消息被 Host 忽略后缓存继续领先实际状态。
- “小时/屏”文字对齐 Native 的显示方式：整数不带小数，非整数显示一位小数。
- 清理 Studio Preview Mock 中不可达的 `nativeSettings` 伪动作，并修正文档说明：Native 紧急恢复来自 Host 独立路径，不是 Skin action。
- 两条过时 Studio 源码断言按当前 Host 设置函数签名和 Native recovery 路由更新；没有改产品运行逻辑。

## 验证

- 原始 Native AVD：`PowerCal_Native095_MenuReference_20261107` / `emulator-5560` / API 35 / 1080×2400 / 420 dpi。最终 AndroidTest `symmetricVerticalPinchChangesTimelineSpan` 1/1 PASS，初始 24、缩放 18.0、复位 24、重复缩放 18.0。测前/后拉回安装 `base.apk` 的 SHA 均与冻结 APK 相同。只安装 AndroidTest APK。
- Web AVD：`PowerCal_Wave2_QA35_20261104` / `emulator-5556` / API 35 / 1080×2400 / 420 dpi / System WebView `124.0.6367.219`。同一仪器测试 1/1 PASS；底层 Camera 约 18.004479 小时/屏，页面标签为 `18.0 小时 / 屏`。冷启动数据未清除。
- 全串行 Gradle 门禁 PASS：987 JVM、0 failures/errors/skips；两项 Source Boundary、APK assemble、Lint、AndroidTest Kotlin compile 均 PASS。标签变更后再单独 `:app:assembleDebug` 并在 QA AVD 重跑一次 instrumentation。
- Skin Studio 全套 Python 90/90，Host binding Catalog 9/9，相关 Node `--check` PASS，Chrome/CDP Studio Preview E2E PASS。E2E 校验拒绝跨本地年界 Camera、模拟缩放文字为 `18.0`、拒绝 `nativeSettings`。
- 最终 `.pcskin`：ID `powercal-clear-default`，10 项、415117 bytes、SHA-256 `78C13F92BB2878A559CCD57FE64FEA7F22D2137460620139C69155C7CBF5BB7E`；APK 资产字节完全一致，且仍只有一份 `.pcskin`。Debug APK SHA-256 `CF310827CA4C3A7989DB79A5D35F6F2755F2AD01E7601D28B40CA271C74492CB`。
- 证据目录：`.local/android-test/reports/native095-timeline-pinch-20261107/`。设备截图 Native `native-compare-final.png` SHA `9D8DE28FE855E2449D0F5D7460E4CE3958D3FFB4D4D5BCF028D349443418E251`；Web `web-label-final.png` SHA `23A0717910EAF1DF3EE6912883FB8D7E28701C6B85E2DD9F00517AA2000DE419`。

## 失败尝试与限制

- 首轮 QA instrumentation 复现时间尺双击未恢复 24 小时，定位为 reset hit 的 `pointerup` 被 stopPropagation 后未从相机 pointer map 移除；按真实指针生命周期修复后，双击复位及复位后再 pinch 通过。
- 一次旧截图 helper 断言失败，改用 Android `UiAutomation` 执行 `screencap` 并从隔离 AVD 拉取 PNG。一次 screenrecord 只有 Launcher，未用于验收证据。
- Studio E2E 首次从 Preview iframe 调用父窗口 `onSkinAction`，触发 JS 上下文错误；改为从 Studio Host context 调用。另有一次尝试从 IIFE iframe 直接引用私有 Skin helper，暴露函数并非全局符号；删除该不合理测试入口，改为验证正式 Studio Mock action 的本地年界拒绝和 UI 标签。
- 首次对比 builder 在 Debug APK 尚未重建时报告资源尺寸不符；运行正式 `assembleDebug` 后 `build_default_clear.py --verify --apk ...` 通过。没有手工修改 ZIP。
- 全套 Python 第一次得到 3 项旧源码形状断言失败；只更新了两处测试契约，最终 90/90 通过。没有将首次失败隐藏。
- 本轮没有拍摄密集事件集，没验证点位/双翼布局、屏幕固定时刻下的锚点像素等价、三指、`pointercancel` 实机、Host 本地年界 Android 操作或 Calendar DB 哈希。因此本 Issue 不代表 Timeline 整页通过；Store 不变仅依据 Camera-only 操作与 Host 路径，未做数据库快照。

Product Cutover remains `HOLD`.
