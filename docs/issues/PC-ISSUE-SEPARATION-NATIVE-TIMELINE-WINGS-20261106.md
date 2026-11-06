# PC-ISSUE-SEPARATION-NATIVE-TIMELINE-WINGS-20261106

**当前状态：PASS（双翼边界、独立水平滚动及原始 APK 同夹具初始视口对照；Timeline 整页仍 PARTIAL）**
**代码提交：** `f55140a2697befe483188c88052d1c5b4c8c22b4`
**日期：**2026-11-06

## 冻结参照与差异

Owner 指定基准为冻结 Native PowerCal 0.9.5-rc.1，源码 SHA `b1f7629f0c52812e1887443872f43aa6c49908c4`，参考 APK SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。此前 Web 双翼贴到视口最外侧，而 Native 两翼左右各留 12dp，并各自具有独立横向滚动区。

## 实现

默认 Skin 的 `native-095.css` 将 `.timelineWing` 改为 `width:calc(50% - 32px)`，左右边缘分别内缩 12px。扩展 `TimelineReturnViewportInstrumentedTest.nativeAndClearLeftAndRightTimelineWingsScrollIndependently`，统一测试状态栏 inset 后，测量 Native Compose 与实际 Android WebView 的物理视口边界、滚动范围、手势位移、Camera 和 Store 状态；测试对左右四个边缘及左右滚动距离都设定 2.5 物理像素容差。默认包由 `build_default_clear.py` 从源码重建。

## 实测

- 隔离 API 35 AVD `PowerCal_Wave2_QA35_20261104` / `emulator-5556`，1080×2400、420 dpi；Android System WebView `124.0.6367.219`。Native 与 Web 仪器路径使用 2026-11-06 的同一确定性八事件夹具。
- Native Compose 双翼物理边界：左 `[32,488]`、右 `[593,1049]`。Web CSS 视口 412px，设备缩放 `1080/412 = 2.621359`；Web 左 `[31.46,488]`、右 `[593,1049]`，四边与 Native 最大差小于 2.5px。
- Native 左右滚动最大范围均为 458px。Web 最大范围 174.095 CSS px，折算 456.19px，差 1.81px。
- 真实独立横向触控后，Native 两侧事件各移动 152px；Web 两侧物理位移分别约 151.83px 和 152.80px。每侧只移动本侧内容；Camera 仍在 Timeline、本地中午锚点、24h 视图。
- 同一 Core Store 保持八条正式测试事件，写入计数为 0。仪器测试 `nativeAndClearLeftAndRightTimelineWingsScrollIndependently` 为 `OK (1 test)`。
- **证据边界：**Native 图像来自当前隔离仪器中的 Native Compose 参考页面，不是所指定原始 APK 的独立进程截图；冻结 APK 尚未在本切片中直接安装并执行同一夹具。整屏截图还显示仪器壳/系统栏区域不同，本结果只签核双翼边界、滚动范围与触控行为，不签核整屏像素、事件卡视觉或整个 Timeline。
- 完整串行 Gradle 门禁 PASS；141 份 JVM XML 共 976 tests，0 failures/errors/skips；源码边界、APK 构建、Lint、AndroidTest 编译/组装 PASS。默认分发 Python 测试 2/2 PASS，包校验 PASS。
- 本地 Debug APK SHA-256 `960EE33EB990A94290646CDBF8D6C1F714031A237F5B107BCA93CE0316B67DDD`；唯一默认包 ID `powercal-clear-default`，410658 bytes，源码与 APK 资源 SHA-256 均为 `7373A2E0B7BE209AD5FCAAB304D90C5F96BD7C77DAC51B453504826FB315C3AF`。

## 证据与边界

Gradle、Android instrumentation、截图及包哈希记录见 `.local/android-test/reports/native095-timeline-wings-20261106/manifest.md`。截图 Native SHA-256 `F02AC6D5AE3B4B2018A4DE58E2339A104A317164FA77AF1B30E98E141F044E4F`，Web SHA-256 `40BB080210AA1C75A0AEE0941863CC92ECAD40DCE5D886FE9353C9F7E7582C08`。

本切片没有修改 Core、Host 业务逻辑、Native 恢复、皮肤 ID 或 Store 语义。Timeline 整页保持 `PARTIAL`；原始 APK 同夹具直接对照、其他事件类型/状态、标题/颜色/卡片视觉、深色与大字体仍需逐项迁移验证。Product Cutover 保持 `HOLD`。


## 2026-11-06 原始 APK 同夹具复核：左翼初始可见项差异已关闭

本补记更新本文上方记录的原始 APK 对照未完成状态；上方内容保留为原始实施时的历史证据边界。

冻结 APK 实际运行显示左翼初始 scroll=0，Web v3 初始则滚到了 scrollWidth，导致相同多 lane 夹具最靠左的 lane 3 不可见。renderTimeline() 现在在初始布局和首次 overflow 转换时将左翼置于 scrollLeft=0，之后仍保留用户滚动位置；无 Core/Host 业务变化。实现提交：4194d513826e6104d85c4aadee23cfb066e2a45b。

在 PowerCal_Native095_RawFixture_20261106 / emulator-5590 原始 APK 与 PowerCal_Wave2_QA35_20261104 / emulator-5556 Web v3 上，使用相同 API 35、1080×2400、420dpi、GMT 环境及八个合成事件。Native 左/右目标 x 为 32/824px；Web 换算为 31.46/823.36px。双边真实滚动各自独立；点开 WING_RIGHT_1 后关闭，Store 计数维持 drafts=0、commits=8、identities=8。原始 APK 与 Web 仪器方法均 1/1 PASS。

本次 serial Gradle gate：977/977 JVM、0 failures/errors/skips；两项源边界、assemble、lint、AndroidTest compile/package PASS。默认 .pcskin 正式构建/校验 PASS，仍只有一个 powercal-clear-default 包。APK SHA-256 070FCF76FCEA8A4CCEC86A5F6B516A7FE2FE2ACDD5DFC89861C7E920211FE192，源包/APK 资源 SHA-256 8a679cb53be26fb1b4fb53f4e0361ac02b802a80e773e74b19b45b3ec6374dca。

截图与完整日志见 .local/android-test/reports/native095-raw-dualwing-20261106/manifest.md。本修复只关闭双翼首次位置差异；Timeline 总体及其他页面仍 PARTIAL，Product Cutover HOLD。
