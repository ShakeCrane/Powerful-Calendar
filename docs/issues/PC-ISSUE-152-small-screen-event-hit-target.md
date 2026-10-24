# PC-ISSUE-152 — 小屏事件点击命中与标题触控回归

日期：2026-10-24

优先级：P1

状态：**API 35 AVD 自动化触控范围 PASS；`REC-P1-067` 完整验收仍 BLOCKED；核心冻结 HOLD**

## 来源与范围

`REC-P1-067` 是依据《核心合同》Invariant 5、10、12、PC-ISSUE-004 和时间轴标题折叠要求重建的 P1 项，历史 209 项 ID 仍为 `UNMAPPED`。本 Issue 在小屏虚拟设备上验证极短事件的可点击节点、标题区域点击路由和完整标题语义；没有发现需要改动生产实现的问题，也没有修改生产代码。

测试使用内存中的虚构一分钟 Activity，不打开正式数据库、不写入用户日历或偏好。测试文件为 `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/TimelineSmallScreenInstrumentedTest.kt`。

## API 35 AVD 结果

设备为 `emulator-5556`、API 35、420dpi。运行期间通过 `wm size 840x1680` 提供 320×640dp 竖屏视口；完成后恢复到原始 1080×2400px，前台窗口回到 Launcher。没有更改旋转传感器或用户方向设置。

AndroidJUnitRunner 定向结果 **1/1 PASS**。测试验证：

- 点击可见标题矩形中心时，`onOpenEvent` 收到同一事件及其完整标题。
- 点击事件点击语义节点中心时，同样收到同一事件。
- 事件节点有点击动作，Accessibility `ContentDescription` 包含完整标题。
- 点击命中面没有扩大真实时间几何。

| 几何对象 | API 35 AVD 屏幕矩形（px） | 高度 |
| --- | --- | ---: |
| 事件点击语义节点 | `[473,973,694,976]` | 3px |
| 事件真实视觉几何 | `[473,973,694,974]` | 1px |
| 渲染标题 | `[486,977,676,1040]` | 63px |
| 测试视口 | `[0,0,840,1680]` | 1680px |

![API 35 AVD 小屏标题与事件点击回归](../../.local/android-test/reports/freeze-20261024/pc-issue-152-small-screen-title-tap-r3.png)

标题在 320dp 宽度下按现有规则截断；完整标题保留在无障碍语义，并由事件打开回调收到。自动化注入的标题中心触摸证明该 Compose 命中路径可用，不代表真实手指操作的舒适度，也没有验证详情界面最终绘制完整标题。

证据保存在 `.local/android-test/reports/freeze-20261024/`：

| 证据 | SHA-256 |
| --- | --- |
| AndroidJUnitRunner 输出 `pc-issue-152-small-screen-title-tap-r3.log` | `130AB0E1A495C5AF2EB15052134CCCBFD0B60C086AEE275F062AE261B8E8E1F2` |
| 矩形与设备恢复记录 `pc-issue-152-small-screen-geometry.txt` | `62A70B9A311FF7CA58791F202669252148C4D13822C69525ED1DFBB041B49E2B` |
| AVD 截图 `pc-issue-152-small-screen-title-tap-r3.png` | `A9D24E703A29FF295F825ECB1FDAD5250D08841A50C4CC293CF21D47A245C480` |

## 完整 Gradle 回归

运行命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

结果 **BUILD SUCCESSFUL**：490/490 JVM 测试、65 个 suite、0 failures/errors/skips；源码边界、Debug APK、AndroidTest APK 与 lint 均通过，Lint 0 errors / 58 warnings。490 项包含 489 个受版本控制测试及预先存在、受保护且未暂存的 `DraftDeckFeedbackTest.kt` 中 1 项；该文件未修改。

| 产物 | SHA-256 |
| --- | --- |
| 完整 Gradle 日志 `pc-issue-152-full-gradle.log` | `3D02559A095242A68532ED852DF3E7B5ECC5BAC36F70BEC1F1610474B5EC2057` |
| Debug APK | `EE7E4850A147AC3B3F21326DCB209B0967F14DE6990D56EE05A2976FEB252BCD` |
| AndroidTest APK | `403D4AF9E23DEB11F004AB839A6D3C1DFA7D4426659F9CC3C32E32D34AAF19C9` |
| Lint XML | `44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F` |

## 验收矩阵与交付

本次将 AVD 自动化结果和证据写入 `REC-P1-067`，但保持 **BLOCKED**：测试没有覆盖真实手指触控、实体设备/OEM 差异或 TalkBack；这些结果由用户在实体设备上验收。该结论不把单个小屏自动化结果扩展成完整设备兼容签核。

结构验证 r18：210 条活动重建项，P0/P1/P2 为 144/65/1；185 PASS / 0 FAIL / 8 BLOCKED / 11 NOT TESTED / 6 SPEC UNRESOLVED；历史 ID 映射为 0，`product_behavior_tested=false`。JSON SHA-256 `13A7EEED004852E594EB228F00C2745EF4B87A7A9DFAA41BC1705885B7F096B7`；CSV SHA-256 `B3E46A12B1AB01F33659B0AF0160539840FD6160253D673F3E046CE0770C1832`。该结构校验不代替产品行为测试。

测试提交 `6cd002f6fdbd275036d2ddb1e8ea2b54543cee2c`（`test(timeline): 覆盖小屏事件与标题触控命中`）已提交并推送。文档与台账随后单独提交、推送。冻结维持 **HOLD**。
