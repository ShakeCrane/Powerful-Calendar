# PC-ISSUE-GREEDY-LABEL-FOLDING — 时间轴标题贪心折叠

日期：2026-10-23（北京时间）

优先级：P1

状态：**标题贪心折叠与 API 35 可视回归 PASS；4 个收窄候选单项签核；字体下限与真机/TalkBack仍未验证；核心冻结继续 HOLD**

需求来源：2026-10-21 已确认的“标题贪心折叠”设计及 2026-10-23 补充任务。

实现提交：`0099a8a201dbc605ba74e1999110eec7517ec6f6`（`修复(时间轴): 实现标题贪心折叠`）

## 复现与根因

旧实现用事件 `anchorMillis`、`endMillis`、`side`、`lane` 和固定 18dp 条件近似判断标题冲突。它比较的是事件时间/泳道信息，不是排版后标题的实际矩形；框内标题还受固定最小事件高度门槛影响。不同时间的长标题因此可能视觉相撞，而“隐藏候选不更新 lead”的贪心合同也没有被表达。

## 修复

- 在 `GreedyTitleFolding` 中实现纯布局策略：候选标题按区域分组，再按屏幕 Y、稳定事件序号和 ID 排序；只与当前可见 lead 比较；隐藏项不更新 lead；矩形边缘相切不算相撞。
- `TimelineScreen` 先按现有单行字体缩小/截断规则测量标题，再把实际渲染矩形转换到含左右横向滚动偏移的统一视口坐标，裁切后交给折叠策略。事件时间重叠关系不参与标题碰撞判断。
- 标题默认放在框内；短框标题锚定真实开始位置并贴近上缘，允许覆盖框体；框外模式使用相同折叠规则。未通过扩展事件框容纳标题。
- 标题绘制在不接收指针事件的 Canvas 上。每个正式事件框和完整标题语义保持存在；测试覆盖隐藏标题对应事件仍可点击、可打开完整标题及长按拖动。
- 未合并/隐藏事件、未改动事件时间几何或泳道上限；没有新增 `×N` 溢出标记。

## 设备实测与前后对照

设备为独立 Android 15 / API 35 AVD `PowerCal_Greedy_API35_20261023`，序列号 `emulator-5556`，1080×2400、420 dpi、GMT。原 `emulator-5554` 未操作。使用四个虚构的一分钟事件，基线 APK 来自 `89ea4fd`，随后覆盖安装修复 APK并保留测试数据。

修复前截图中四个事件框都存在，但短框没有可见标题。修复后 A、C 标题可见，B、D 折叠；A/B/C/D 四个事件框屏幕边界与基线完全相同：A `[593,1043,814,1044]`、B `[593,1055,814,1056]`、C `[593,1133,814,1134]`、D `[593,1145,814,1146]`。

下表为 `TextMeasurer` 实测标题矩形转换到物理屏幕坐标后的结果。原始矩形相对于时间轴视口；视口顶部在屏幕 Y=391，因此屏幕 Y 加 391。标题高度为 63px。

| 事件 | 标题屏幕矩形 `[left, top, right, bottom]` | 决策 |
|---|---:|---|
| A / seq 1 | `[606,1047,806,1110]` | 可见，首个 lead |
| B / seq 2 | `[606,1059,805,1122]` | 隐藏；与 A 相撞 |
| C / seq 3 | `[606,1138,806,1201]` | 与 A 不相撞，显示并成为新 lead |
| D / seq 4 | `[606,1150,806,1213]` | 隐藏；与 C 相撞 |

此轨迹直接验证隐藏的 B 没有阻止 C 成为 lead，D 随后只与当前 lead C 比较。完整设备轨迹见 `.local/android-test/reports/freeze-20261023/issue-greedy-label-folding/actual-title-trace.log`；坐标与边界汇总见 `device-title-trace.json`。

| 证据 | 文件 | SHA-256 |
|---|---|---|
| 修复前 | `.local/android-test/reports/freeze-20261023/issue-greedy-label-folding/baseline.png` | `5E6079A5CF24F9DFF2477AE34739B4A403E1D9EDCA59CB2CDE2D81BBDACD8C1A` |
| 修复后 | `.local/android-test/reports/freeze-20261023/issue-greedy-label-folding/final.png` | `C5AD1A01BFD4C084D211BDC27EE59E36075027636CBF93A189E4B1DFA5B377E8` |
| 框外标题模式 | `.local/android-test/reports/freeze-20261023/issue-greedy-label-folding/after-outside.png` | `7BE15BF9923D9CFE4545ABABD217A62CD8615D7808A4ECFADC98FDA0751C6C55` |
| 标题矩形/碰撞轨迹 | `.local/android-test/reports/freeze-20261023/issue-greedy-label-folding/actual-title-trace.log` | `D37397648DF210C11E9A66567CB4A3AF5F057C7D17194F488CBEFBB1010F6279` |
| 修复 APK | `app/build/outputs/apk/debug/app-debug.apk` | `95AACDBD6D2121768062A8397D97A6FC37AA5FA4E3E4AAC0E296AE7B1704171E` |

## 自动化验证

- 新增 8 个纯策略单元测试：A/B 冲突后 C 成为 lead、连续冲突组、矩形相切、独立泳道、事件时间不重叠但文字碰撞、稳定排序、滚动/缩放/日期复位确定性、按实测文字边界判断。
- 新增 6 个 Compose 回归：框内及框外模式、隐藏标题事件点击与完整标题语义、缩放及 24 小时复位/日期变化、水平滚动裁切和泳道事件保留、可见标题点击穿透、标题层不拦截长按拖动。
- 全量命令：`.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。
- 结果：**52 个测试套件，362 项测试，0 failures / 0 errors / 0 skipped**；Skin source boundary、Debug APK、Lint 均成功。Lint 为 0 errors、53 warnings。
- 完整日志：`.local/android-test/reports/freeze-20261023/issue-greedy-label-folding/full-regression.log`，SHA-256 `C0CAD8E6DBB2ED1B1D27D246C5CDAE6C51CACE9F101EF49C668584FEF5B3CF56`。

## 签核边界与剩余缺口

标题贪心折叠的代码、自动化与 API 35 模拟器可视化回归 **PASS**，并单独纳入冻结验收 P1-E。真机/OEM、TalkBack 实际阅读与触摸操作尚未验证，故设备级完整签核仍为 **NOT VERIFIED**，不能据此把核心冻结从 HOLD 改为 GO。

用户补充确认（2026-10-23）：蓝色当前时间标记覆盖中央时间轴符合设计；标记边缘的淡化横向变化可接受，不需要继续作为 UI 缺陷追修。蓝色标记、中央刻度和午夜虚线均保留。后续工作按本次指示不开展 UI 修改。

## 2026-10-24 虚拟机回归与候选台账签核

新增 Compose 用例 tapOnVisibleTitleCanvasOpensItsUnderlyingEvent：在可见标题 Canvas 中心注入点击，底层事件打开并返回完整标题。目标类定向结果 6/6 PASS，JUnit XML SHA-256 44CD834F3F39151FF495C124441D90AE7178F923E50644D8B9B3FBC7585E7D42；定向日志 SHA-256 EDAF04340976C8255834CA2F8986E20712DCF5EDD2DE1CCAC8B0B799286AFC1F。8 个 GreedyTitleFolding 纯策略测试均 PASS，JUnit XML SHA-256 C302BB9167568D783A610DA54726EA4B98969DB4528CE7C18312D745C7162D03。

完整 Debug 回归命令与范围保持为 verifySkinSourceBoundary、全部 testDebugUnitTest、assembleDebug 和 lintDebug。结果：463/463 across 57 suites，0 failures/errors/skips，Lint 0 errors/58 warnings；日志 SHA-256 045E99BC413F7BCB55EF39C62039816110E913ECEE68F7EB3E7F538A26760A87。全量测试包括一个受保护未跟踪测试文件，该文件未加入提交。当前 Debug APK SHA-256 138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096。测试变更提交：56aadbe50fea59a92c2568896b2c3f1dd04e3556。

基于既有 API35 AVD 实测矩形/截图以及本次 Compose 回归，重建台账中的 REC-P1-029、REC-P1-030、REC-P1-065、REC-P1-066 按各自收窄范围签 PASS。REC-P1-028 的字体缩小下限/截断尚无专门断言，仍 NOT TESTED。真机/TalkBack/OEM 不在本次虚拟机证据内。

最新结构校验后候选统计为 153 PASS / 0 FAIL / 9 BLOCKED / 40 NOT TESTED / 7 SPEC UNRESOLVED。历史 209 case 原始清单及映射仍未找到，冻结总判定保持 HOLD。
