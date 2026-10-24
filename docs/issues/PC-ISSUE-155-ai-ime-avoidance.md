# PC-ISSUE-155 — Android IME 下聊天区域避让

日期：2026-10-24
优先级：P1
状态：**API 35 AVD 子范围 PASS；REC-P1-070 完整验收 BLOCKED**

## 需求与范围

依据 `CORE_CONTRACT_FREEZE_2026-10-21.md` §Invariant 12 和重建验收项 `REC-P1-070`：Android 输入法弹出时，聊天编辑器跟随键盘、消息区域收缩，发送按钮保持可见。该项仍标记为 `RECONSTRUCTED`、历史 ID `UNMAPPED`。

本次只增加 Android instrumentation 回归，没有修改生产 UI、业务逻辑或应用数据。测试使用真实 Android 系统 IME，在隔离 `ComponentActivity` 中组合生产的 `SkinAppShell(forceNative = true)` 与 `NativeAiChatScreen`，聊天内容和回调均为合成数据；没有打开 `PowerStore`、访问聊天历史或调用 AI。目标设备为 `emulator-5556`（API 35，竖屏 1080×2400）；`emulator-5554` 未触碰。物理设备、OEM 输入法、TalkBack 及完整 `MainActivity` 路由仍未验证。

## 实测布局与结果

AndroidJUnitRunner 打开系统 IME 并确认 `WindowInsetsCompat.Type.ime()` 可见。最终测试 **1/1 PASS**，同时断言消息列表视口缩小、合成消息仍显示、编辑器与发送按钮位于 IME 上方，发送按钮可用。

| 测量项 | 屏幕矩形 `[left, top, right, bottom]` px | 结果 |
| --- | ---: | --- |
| Compose 根视口 | `[0,0,1080,2400]` | API 35 竖屏 |
| IME 顶边 | `y=1580`（底部 inset 820px） | 系统 IME 可见 |
| 消息列表（IME 前） | `[32,293,1048,2151]` | 高 1858px |
| 消息列表（IME 后） | `[32,293,1048,1394]` | 高 1101px，缩小 757px |
| 合成消息 | `[32,293,491,436]` | 可见，未被编辑器遮挡 |
| 编辑器 | `[169,1412,885,1559]` | 位于 IME 上方 |
| 发送按钮 | `[896,1422,1048,1559]` | 可见且启用；距 IME 顶边 21px |

原始矩形轨迹见 `.local/android-test/reports/freeze-20261024/pc-issue-155-ai-ime-final-geometry-20261024.txt`。这是隔离测试 Activity 的实测结果，不能替代完整应用路由或实体设备验收。

## 测试与构建证据

- 定向 API 35 instrumentation：`AiChatImeInstrumentedTest.openImeKeepsComposerAndSendVisibleAndResizesConversation`，**1/1 PASS**。日志 SHA-256：`1F62B52C366B9667A4D2A02ABB4768F39C1B7E35ADD41118014D1843EB96EB03`。
- 矩形轨迹 SHA-256：`354974E3FDAFF516A06CA0D3C8611B5B54814EC0354EEAF0ED43E34D4E4DA04F`。
- 完整命令 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain`：**BUILD SUCCESSFUL**；491/491 JVM 测试、66 个 suites、0 failures/errors/skips；source boundary 与 Debug/AndroidTest APK 组装通过；lint 0 errors / 58 warnings。491 项包括 490 个已跟踪测试及受保护、预先存在的未跟踪测试 `DraftDeckFeedbackTest.kt`。
- 完整 Gradle 日志 SHA-256：`0282E3AAF54F20D153A152691DCEAE614D398E7CD32A5D60BDC0725E028E1542`。
- 定向 AndroidTest 构建日志 SHA-256：`89A13E6588477F88CE0575ABE85D8FE24D6851B22159025DB40A2C0D40AA1583`。
- Debug APK SHA-256：`EE7E4850A147AC3B3F21326DCB209B0967F14DE6990D56EE05A2976FEB252BCD`；AndroidTest APK SHA-256：`DD86AF0C4C041A7E60EC88C26DFC27CC0EDE279B649A968A66E53B92ADB036DC`。
- lint XML SHA-256：`44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F`。

测试夹具第一次编译因 Compose test API 导入错误失败（`fetchSemanticsNode`、`waitUntil`、`onRoot` 未解析）；修正导入后，定向 instrumentation 与最终完整 Gradle 均通过。首次 Gradle 控制台输出被后续成功重跑复用同一路径覆盖，没有保留独立原始日志，因此不为该次夹具编译失败声称日志哈希；这次编译失败不代表产品行为测试结果。

## 台账与 Git

结构校验仅验证重建台账格式，不代表产品行为测试。r21 记录 210 项活动重建验收、P0/P1/P2 为 144/65/1；186 PASS、0 FAIL、8 BLOCKED、10 NOT TESTED、6 SPEC UNRESOLVED；历史 ID 映射数为 0，`product_behavior_tested=false`。`REC-P1-070` 保持 **BLOCKED**：已签核 AVD IME 子范围，完整应用集成及实体设备/OEM 验收仍缺。

- 台账 SHA-256：`D383603B1AAAD4DB152BEDBF1932531DF63DB4BD5CA6A372B87238407FDDA62D`。
- 结构校验 JSON：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r21-pc-issue-155-ai-ime.json`；SHA-256：`C6123739CEC74A1A14A92D5A01DF0AD728B4B5B2183BE1FE2B10F6E9A50940DD`。
- 测试提交：`caa01096a20bddc2b02116a7c7d5931d329c4097`（`test(core-freeze): 覆盖IME下聊天区域避让`），已推送至 `origin/main`。

## 结论

模拟器上已验证系统 IME 显示时聊天列表收缩，编辑器和发送按钮避开键盘。实体设备和 OEM 输入法测试由用户负责；完整冻结验收仍为 **HOLD**。
