# PC-ISSUE-154 — 2× 字体缩放下时间轴控件可达性

日期：2026-10-24

优先级：P1

状态：**Robolectric API 35 Compose 子范围 PASS；REC-P1-069 完整验收 BLOCKED**

## 验收范围

来源为 `REC-P1-069`：系统字体缩放为 2× 时，时间轴工具栏和关键操作仍可用。本次只增加测试，没有修改生产 UI 或业务逻辑。真实 Android 系统字体设置、MainActivity 集成、Android Emulator/实体设备 OEM 表现尚未由本报告签核。

为避免干扰现有模拟器任务，本次没有修改 AVD 全局设置、安装 APK 或启动 PowerCal。只读检查时，`emulator-5554` 前台停在 Google Calendar 首次启动页，`emulator-5556` 有缓存的测试包进程；两台设备均保持原状。

## 定向回归

新增 `TimelineFontScaleComposeTest.doubledSystemFontScaleKeepsTimelineToolbarActionsReachable`，在 Robolectric API 35、逻辑视口 360×800dp、`LocalDensity.fontScale=2.0` 下运行真实 `TimelineScreen` Compose 内容。

测试确认并点击了“今日”“Memo”“待处理”“新建事件”“缩小时间跨度”“放大时间跨度”六个操作；“新建事件”菜单中的“活动”也成功触发回调。所有控件矩形均有正尺寸且完全位于视口内。实测视口矩形为 `[0,0,720,1600]` px；工具栏控件分别为：

| 控件 | 屏幕矩形 `[left, top, right, bottom]` px |
| --- | ---: |
| 今日 | `[356,46,444,134]` |
| Memo | `[444,46,532,134]` |
| 待处理 | `[532,46,620,134]` |
| 新建事件 | `[620,46,708,134]` |
| 缩小时间跨度 | `[24,172,104,252]` |
| 放大时间跨度 | `[130,172,210,252]` |

定向结果 **1/1 PASS**。测试没有打开 PowerStore，也没有写入日历数据。

### 夹具编译修正记录

首个测试编译尝试因 Compose 测试扩展导入和 `LocalDensity` 读取位置错误而失败，未运行产品断言；失败日志 SHA-256 `E6532B89A3EB3BE44274E64018475BA0108DA5C3017DE42A33370E9B23503010`。修正测试夹具后，定向回归通过。该失败是测试代码编译问题，不是产品回归。

| 证据 | SHA-256 |
| --- | --- |
| 定向 Gradle 日志（通过） | `C9C8F27A225EEAD72DEE07A0C2AC35A363F2B1428B38810B889464AE7FFC0B2C` |
| 定向 JUnit XML（1/1） | `79D3861D09DF5D986817D9BA2CE0143BAD5F048B0CF9F79AFB4FC94982BE4D73` |
| 首次测试夹具编译失败日志（已修正） | `E6532B89A3EB3BE44274E64018475BA0108DA5C3017DE42A33370E9B23503010` |

## 完整 Gradle 回归

命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

结果 **BUILD SUCCESSFUL**：491/491 JVM 测试、66 个 suite、0 failures/errors/skips；source boundary、Debug/AndroidTest APK 组装通过；Lint 0 errors / 58 warnings。491 项包含 490 个跟踪测试和一个预先存在、受保护且未暂存的 `DraftDeckFeedbackTest.kt` 测试；该文件没有被本 Issue 修改。

| 产物 | SHA-256 |
| --- | --- |
| 完整 Gradle 日志 | `DA5E539CFEC2550D79A15E66ABF193A8B692F578EA908A7021385C0E14FE4A0B` |
| Debug APK | `EE7E4850A147AC3B3F21326DCB209B0967F14DE6990D56EE05A2976FEB252BCD` |
| AndroidTest APK | `3228DF57FE8EAF3D38FBD25E54876FD533EE2228F05191507EBEF6B4793567E1` |
| Lint XML | `44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F` |

测试提交 `b8211496d244f9261b127ef91bdc50779de4591a`（`test(core-freeze): 覆盖2倍字号下的时间轴控件`）已推送。

结构校验 r20 保留 `REC-P1-069` 为 `BLOCKED`，因为本次 Robolectric 子项不能替代系统 `font_scale`/AVD/实体设备验证。活动重建矩阵计数为 186 PASS / 0 FAIL / 8 BLOCKED / 10 NOT TESTED / 6 SPEC UNRESOLVED，P0/P1/P2=144/65/1；历史 ID 映射为 0，`product_behavior_tested=false`。artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r20-pc-issue-154-font-scale.json` SHA-256 `FB1637E0BE5CBB31495C9FEC1BEA87810715D283F1E29601DC8662F9C5235F79`；CSV SHA-256 `2A38DFBCF5EF50F2CC46993F4B002F13A6140748C5BB0B22622448933C666792`。

## 签核边界

本测试只证明 Compose 子树在显式 2× `LocalDensity.fontScale` 下的控件边界与回调可达性。它没有改变 Android 的系统 `font_scale`，也没有在 AVD 上运行真实 `PowerCalApp`/`MainActivity`；因此 `REC-P1-069` 仍为 **BLOCKED**，等待真实系统配置下的 AVD 验证及用户负责的实体设备/OEM 验收。核心冻结仍 **HOLD**。
