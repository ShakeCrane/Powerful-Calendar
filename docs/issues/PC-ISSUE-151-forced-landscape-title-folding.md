# PC-ISSUE-151 — 强制横屏下标题折叠与子像素事件命中

日期：2026-10-24

优先级：P1

状态：**API 35 虚拟设备与 Robolectric 范围 PASS；实体设备/OEM 范围未验证；核心冻结保持 HOLD**

## 需求与复现

本 Issue 补充 2026-10-23 确认的横屏兼容场景：应用继续请求竖屏且不主动读取旋转传感器；系统或外部强制提供横屏尺寸时，时间轴仍须可用。标题碰撞继续遵守 2026-10-21 的贪心折叠合同，只隐藏视觉碰撞的标题，保留全部正式事件和真实时间几何。

在 API 35、800×360dp 的强制横屏视口，以 24 小时视图显示 08:00、08:10、11:00、11:10 的四个虚构一分钟 Activity。修复前，分钟对应的 Activity 框高度按真实时间缩放后被取整为 0px；整个可点击 Surface 也随之成为 0px，事件没有可触达的屏幕区域。测试断言首先在这条路径失败。

## 根因与修复

同一个 Compose `Surface` 同时承担事件可见几何和点击/长按/拖动命中区域。极短事件的真实高度可能小于一个像素，布局取整后整个 Surface 无尺寸，事件语义虽存在于模型中，却没有可用屏幕命中区域。

现在 Activity 外层使用透明的至少 1dp 交互面；原事件填充、边框、色条和时间标签绘制在内层，内层高度仍严格等于按真实开始/结束时间计算出的 `cardHeight`。标题折叠、事件起止时间、泳道和框体几何都不因点击面的最小尺寸而扩大。Alarm/Deadline 点事件仍使用既有尺寸。

## 回归结果

### Android API 35 AVD

设备 `emulator-5556`，API 35，420dpi。原显示为 1080×2400px；测试期间以 `wm size 2100x945` 临时提供 800×360dp 横屏窗口，完成后恢复为 1080×2400px。没有更改 `accelerometer_rotation` 或 `user_rotation`。

定向 AndroidJUnitRunner 结果 **1/1 PASS**：A、C 标题可见，冲突的 B、D 隐藏；四个事件节点均保留完整标题语义和点击动作；直接点击隐藏标题 B 的事件区域仍打开完整的 `Landscape_B`。可见标题矩形为 A `[1116,496,1315,559]`、C `[1116,581,1315,644]`，二者不重叠。四个一分钟事件的视觉几何均为 0px，独立事件命中区域均为 3px。截图中中央刻度与蓝色当前时间线仍绘制，且没有数量聚合标记。

![API 35 强制横屏标题折叠回归截图](../../.local/android-test/reports/freeze-20261024/pc-issue-151-emulator-screenshot.png)

截图和矩形轨迹位于 `.local/android-test/reports/freeze-20261024/`：

| 证据 | 路径 | SHA-256 |
| --- | --- | --- |
| 模拟器截图，2100×945 | `pc-issue-151-emulator-screenshot.png` | `A9210C324AA2F94384B8F222DCC6383359F1A65E6CAAC966A2B32F8B6A038FEC` |
| 屏幕与标题矩形轨迹 | `pc-issue-151-emulator-geometry.txt` | `7EFED8B796E4D9EB7A5F45B91EE14F3987DAD0249EC7C88F1CD24CBB7D4922AF` |
| 单机 instrumentation 输出 | `pc-issue-151-emulator-targeted-r2.log` | `E5C4894DFEB071000C34AEDB2E66B20A11C6BD78B84EADBE9AF8BD721669956A` |
| 首次 instrumentation 线程错误诊断 | `pc-issue-151-emulator-targeted.log` | `DE3B4A47A5CA78DA53E95A426F7E856DED24D799AA89B665474C7DC84CA56B48` |

首次使用 Gradle `connectedDebugAndroidTest` 时，Android Gradle Plugin 将测试派发到两台已连接 AVD，尽管命令提供了设备序列属性；两台均在第一个界面断言前因测试从 instrumentation 线程调用 `setContent` 而报 `CalledFromWrongThreadException`。测试改为 UI 线程设置内容后，使用显式 `adb -s emulator-5556 shell am instrument` 单机重跑通过。复核确认两台 AVD 的分辨率及原前台窗口均已恢复；测试只使用内存中的虚构事件，并在应用外部缓存目录写出截图，没有执行日历数据库或偏好设置操作。

### Robolectric 与完整 Gradle

新增 Robolectric API 35 Compose 用例通过 **1/1**，并验证 A/C 成为依次可见的 lead、B/D 隐藏、真实视觉几何仍为 0px、事件可访问且可点击，以及点击隐藏标题对应的 B 事件仍返回完整标题。定向日志 SHA-256：`8071D03C44FC5020974BFD3220091B423B072CF8D45FD1EB00BF8EF4FA5A08D`；JUnit XML SHA-256：`BC642E98F0C8011A30CD7FC90616F85B8E63F4BA6D2B2C6C3F9E4CD7E7EC0CFB`。

完整离线回归命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

结果 **BUILD SUCCESSFUL**：490/490 JVM 测试、65 个 suite、0 failures/errors/skips；源码边界、Debug APK、AndroidTest APK 与 lint 通过；Lint 0 errors / 58 warnings。490 项中含 489 个受版本控制测试和一个预先存在的受保护未跟踪 `DraftDeckFeedbackTest.kt`；它未由本 Issue 修改或暂存。完整日志 SHA-256：`ECD191B36F27485A8437CB867EA019D2A5C5B84FB593E81E3F2B5784431DC1D8`。

| 产物 | SHA-256 |
| --- | --- |
| Debug APK | `EE7E4850A147AC3B3F21326DCB209B0967F14DE6990D56EE05A2976FEB252BCD` |
| AndroidTest APK | `8DA727D262D6996F3DA5E79E26620320D7057ED6D9B35DEC14FC270FADB8E3E6` |
| Lint XML | `44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F` |

## 台账签核与交付

新增单独重建验收项 `REC-P1-084`，只签核 API 35 横屏尺寸下的标题 lead 折叠、事件真实几何、命中与无障碍语义，以及刻度保留；不扩展为竖屏锁定或实体设备签核。结构验证 r17 记录 210 条活动重建项、1 条退役项，185 PASS / 0 FAIL / 8 BLOCKED / 11 NOT TESTED / 6 SPEC UNRESOLVED；P0/P1/P2 为 144/65/1。artifact SHA-256 `C5A1730FB87861436F95ED3DED3886C804798650EAC6F1F19D71DB75CB37CF9E`，台账 SHA-256 `EEFA0692584EE11202EC53B8C5F682B5FC3BF755EBCAD1A1159ED246AA31E98B`。该校验只检查台账结构，`product_behavior_tested=false`，历史 ID 映射为 0。

代码与测试已提交并推送：`671f7c700404e18eea0c5658c300ca04ca01a49c`（`fix(timeline): 保留横屏子像素事件点击与标题折叠`）。文档、报告与台账将在独立文档提交中推送。

未覆盖实体 Android 设备、OEM 自定义窗口策略、TalkBack 实际朗读，以及用户的强制横屏设备场景；这些缺口不影响本项 AVD 窄化签核。核心冻结仍为 **HOLD**，其余未测试项、规格冲突和 provider/device 门槛继续按冻结计划处理。
