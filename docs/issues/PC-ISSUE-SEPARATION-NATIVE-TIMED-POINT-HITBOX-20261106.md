# PC-ISSUE-SEPARATION-NATIVE-TIMED-POINT-HITBOX-20261106

**状态：PARTIAL（可视时间窗内的点位热区方向已移植；窗外部分可见点事件尚未覆盖）**  
**代码提交：** `06649a334d12d0e7e9371fcc08d26a3fcc7ee9ad`  
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
