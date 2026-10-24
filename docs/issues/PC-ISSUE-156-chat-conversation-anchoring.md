# PC-ISSUE-156 — 聊天消息滚动锚点

日期：2026-10-24
优先级：P1
状态：**REC-P1-071/072 的 API 35 原生聊天列表行为 PASS**

## 需求与范围

依据 `CORE_CONTRACT_FREEZE_2026-10-21.md` §Invariant 12：底部锚定时收到新消息应跟随最新消息；用户阅读历史时，新回复不得把阅读位置拉回底部。两项均为重建验收，历史 ID 保持 `UNMAPPED`。

新增两条 AndroidJUnitRunner 用例，使用 API 35 `emulator-5556`、竖屏 1080×2400，在隔离 `ComponentActivity` 中组合生产的 `SkinAppShell` 与 `NativeAiChatScreen`。每条测试使用 32 条合成消息和空回调，没有打开 PowerStore、读取聊天历史、发送真实消息或调用 AI；未修改生产代码。测试覆盖原生聊天列表行为，不签核完整 `MainActivity`、持久化历史或模型响应路径；没有运行实体设备测试。

## 验收结果

### REC-P1-071：底部锚定时跟随新消息 — PASS

初始 32 条合成历史稳定停在末条 `CHAT_HISTORY_31`。追加最新助手消息后，该消息保持可见，屏幕矩形为 `[32,2008,682,2151]`；编辑器矩形为 `[169,2169,885,2316]`，最新消息底边位于编辑器顶边上方。

### REC-P1-072：阅读历史时不跳到底部 — PASS

将列表滚动到索引 0 后追加助手回复，首条 `CHAT_HISTORY_00` 仍显示；`CHAT_LATEST_WHILE_READING` 不在当前 LazyColumn 的已组合语义节点中，未触发自动跳底。

定向 instrumentation **2/2 PASS**。几何与轨迹记录：`.local/android-test/reports/freeze-20261024/pc-issue-156-chat-anchor-geometry-20261024.txt`，SHA-256 `2AC416F4BC01740BF45497CF6CC59BB5E91B9EA449DA7E87D1585EFE07668AA1`。AndroidJUnitRunner 日志 SHA-256 `E34F2C620B2160B9AE5A5ACC514819D3812B5D028724D000A850D5A948F6F5B9`。

## 完整 Gradle 回归

命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

结果 **BUILD SUCCESSFUL**：491/491 JVM 测试、66 个 suites、0 failures/errors/skips；source boundary 与 Debug/AndroidTest APK 组装通过；lint 0 errors / 58 warnings。491 项包含 490 个已跟踪测试及受保护、预先存在的未跟踪 `DraftDeckFeedbackTest.kt` 测试。完整 Gradle 日志 SHA-256：`FCF1303CADDDE1242BFA844949C7FBE18BD2B3CD474E5FB9EB267ED7841858D2`。

- 定向 AndroidTest 构建日志 SHA-256：`2890BDD8263017E9D8540EE76528C95628D61DA5ED1F514EF024785FA7C6B225`。
- Debug APK SHA-256：`EE7E4850A147AC3B3F21326DCB209B0967F14DE6990D56EE05A2976FEB252BCD`。
- AndroidTest APK SHA-256：`FE0FCCAE5E7AF7B106F3E5E81B5EC6693CC82AD4CDCE9376792502B658F2CA76`。
- lint XML SHA-256：`44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F`。

## 台账与 Git

重建台账 r22 结构校验记录 210 项活动验收（P0/P1/P2 为 144/65/1）：188 PASS、0 FAIL、8 BLOCKED、8 NOT TESTED、6 SPEC UNRESOLVED；历史 ID 映射 0，`product_behavior_tested=false`。`REC-P1-071/072` 仅对上述原生聊天列表子范围签核 PASS。

- 台账 SHA-256：`DD90187282077CB434A73D70492DF87E948CEF888B5553FAE49BEE2B5EAB5BE5`。
- 结构校验 JSON：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r22-pc-issue-156-chat-anchor.json`；SHA-256：`3CBB20CE34883E1F1DE834A40692608B2AC6912F8E3B6922EE114984CFD265C0`。
- 测试提交：`642b746b2ec60b2cff65458c27c445b0d901d8e3`（`test(core-freeze): 覆盖聊天消息滚动锚点`），已推送到 `origin/main`。

## 结论

原生聊天 Compose 列表满足底部跟随和历史阅读锚定两项行为。此结果不代表聊天持久化、真实 AI 响应或实体设备/OEM 行为已通过；整体冻结仍为 **HOLD**。
