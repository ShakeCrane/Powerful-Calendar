# PC-ISSUE-002 — 免费模型超时与结构化提案未闭环

日期：2026-10-23（北京时间）｜优先级：P1
状态：**PARTIAL / BLOCKED；Issue 尚未关闭，核心冻结保持 HOLD**

## 复现与实际结果

环境为 PowerCal `0.9.5-rc.1`、Android 15 / API 35、仓库内模拟器 `emulator-5554`。设备系统时区为 GMT；测试主机时区为 Asia/Shanghai。AI 配置只检查到 `key_present=True`、`enabled=True`、模型 `stealth/glyph-cluster:free`，没有读取或记录密钥。

| 路径 | 实际观测 | 结果 |
|---|---|---|
| 修复前的复杂请求 | 旧探针记录总等待 64.5 秒后超时、没有日历变更；传输日志还记录了 45.015 秒 / HTTP 0 与 61.567 秒 / HTTP 200 的调用。历史证据对单次探针与调用的对应关系不充分，不把两行拼成一个请求。 | FAIL |
| 提高免费模型首轮预算后的调用 | 已有日志记录 HTTP 200、14.759 秒与 HTTP 200、5.737 秒，两次输出预算均为 2048；UI 未证明提案卡可用。历史合成请求 `PC_QA_AI_03` 的界面显示空的“无法生成结构化提案：”原因。 | PARTIAL |
| 免费模型纯文本控制请求 | HTTP 200、4.969 秒、输出预算 2048；模拟器聊天界面显示预期的合成控制回复。回复在原视口下方，手动滚动后才看见，另记为聊天跟随行为观察。 | PASS（仅传输与聊天呈现） |
| 免费模型结构化提案请求 | 合成 Activity 请求在 HTTP 503 后失败；耗时 35.807 秒、输出预算 2048；没有出现可审查卡，也没有触发任何草稿/发布操作。 | FAIL（端到端） |

关键证据均在 `.local/android-test/reports/freeze-20261023/`（Git 忽略）：

- `issue002-proposal-ui-probe.json`：API 35 UI 复测结果，SHA-256 `E6A3F08627CCABC404B1FA613D1F6A3486500210C5E04FFF92D1A8FF4351390B`。
- `issue002-proposal-ui-probe.png`：本轮 503 的模拟器画面，SHA-256 `4875ED5B4790D780564F7C3D8FC5111C456F4921A93A30DA0765CD1A19CEFCDC`。
- `issue002-control-after-scroll.png`：HTTP 200 控制回复及此前结构化失败 UI，SHA-256 `1F90049788E368446F00DFBADB273EB7BD8F8DDBD06A34883D7C013096BA6ECA`。
- `issue002-transport-perf-after.log`：预算调整后的阶段耗时日志；没有提示正文、响应正文、headers 或凭据。
- `.local/android-test/scripts/ai_issue002_ui_probe.py`：有界模拟器 UI 探针；测试文本为合成内容，探针不会按提案卡上的任何写入按钮。

早期 UI 探针曾把历史聊天里的 HTTP 401 误认成本轮错误，且页面识别条件不严；该次结果已排除，不用于判定。本报告只采用按本轮合成标记和新传输计时核验的记录。

## 根因与代码变更

1. 免费模型的首次聊天预算只有 540 tokens，结构化调用预算为 1800；旧空响应处理会再发一次请求，可能把无可见内容扩展为更长等待。新增 `AiCompletionPolicy`：`:free` 首轮至少 2048、上限 4096；空 HTTP 200 不盲目重发。自动重试仅允许精确模型 ID `deepseek/deepseek-v4.1-flash` 在确认 `finish_reason=length` 且预算不足时发生一次；其他付费模型不会自动重试，也不会自动切换模型。
2. 聊天模型生成的 `source` 可能来自示例或模型编造内容。聊天提案 Skill 现在只在模型给出意图标记时读取**本轮原始用户输入**，不把模型的 `source` 字段交给解析器；该标记本身不授权任何写入。
3. 结构化解析曾静默略过非对象事件项，导致“无提案、无警告”，界面拼出空白失败原因。解析器现在保证无可用提案时至少产生非空警告，UI 另有通用安全兜底提示。
4. Debug 遥测现在只记录固定阶段名、耗时、HTTP 状态、输出预算、提案意图布尔值及提案/警告数量，不记录请求正文、回复、URL、headers、密钥或日历字段。

改动文件：`AiClient.kt`、`AiChatActionParser.kt`、`AiModels.kt`、`PowerCalApp.kt`、新增 `AiCompletionPolicy.kt`、`AiCompletionPolicyTest.kt`、`AiCalendarHintSafetyTest.kt`。没有数据库格式或业务数据迁移。

## 回归验证

- 针对性 AI 单测：`AiCompletionPolicyTest` 与 `AiCalendarHintSafetyTest`，**9/9 PASS**，含未授权付费模型不得自动重试。
- 完整冻结 Gradle 命令：
  `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**BUILD SUCCESSFUL**；JVM **345/345**，失败 0、错误 0、跳过 0；Lint **0 errors / 56 warnings**。
- Skin Studio：`python -m unittest discover -s tools/skin-studio/tests -v`：**37/37 PASS**。
- 全量 Gradle 日志：`issue002-full-final.log`，SHA-256 `070A2E703872D9E0A6C7C10EF479F421F62783436B301CEBCB866811257F4B0C`。针对性 AI 日志：`issue002-targeted-paid-retry.log`，SHA-256 `87961D9BE777E486C31FB61EA4185D58869580A1A544EAA36DB1A77C910A32E2`。Python 日志：`issue002-skin-python.log`，SHA-256 `EE73B3E5381D39B7299D852ECB51440FC82637807F2C325403B5D7B4D44EE8A9`。
- 最终 Debug APK：versionCode `905`、versionName `0.9.5-rc.1`，SHA-256 `F47C456943A9685A85DD77DDED17C5B4DDC9A490635BF3C4CE78E4F7F97BE846`。已用 `adb install -r` 覆盖安装成功；`key_present=True`、免费模型与启用状态保留。

## 剩余缺口

- 免费服务的短文本控制请求成功，但复杂提案本轮收到 HTTP 503；先前结构化回复也没有形成可审查卡。因此不能签核“自然语言 → 提案卡”。服务端稳定性与 503 的提供方根因目前不可由本地代码确定。
- 尚未验证同意/拒绝/写入草稿后的数据库、UI、重启恢复、幂等、冲突、撤销或批量授权链。此次没有点击任何写入按钮，也没有修改正式事件。
- 付费 Flash 仅有旧的 inconclusive 记录，本轮未重复付费请求；物理真机和其他 OEM/API 仍未测试。
- 测试设备 GMT 与验收时区 Asia/Shanghai 不一致；本次用明确的未来固定日期，不据此签核时区/DST 行为。
- 另观察到在历史聊天视口发送后，新消息与回复可能留在视口之外，需作为独立 UI 问题复现/定级；本报告不把该行为并入 Issue 002 的通过结论。

## 后续受控复测（2026-10-23）

在已提交的 AI 修复基线上，用同一类纯虚构、明确“仅提案”的 Activity 输入再次跑免费模型两阶段流程。API 35 `emulator-5554` / GMT；模型仍为 `stealth/glyph-cluster:free`。实际结果：聊天阶段 HTTP 200、17.811 秒、输出预算 2048，遥测显示 `proposalIntent=true`、可见回复 72 字符；紧接的结构化提案阶段 HTTP 429、1.767 秒、输出预算 2048。未生成可审查卡，也未点击任何草稿、发布或日历写入操作，因此端到端仍未通过。

- 脱敏阶段轨迹：`.local/android-test/reports/freeze-20261023/issue002-ai-retest-20261023.json`，SHA-256 `402457CFAC21A1C4BF56C99BF7D44FFDBF7B05059C4140FD54961A476B167298`。只记录阶段、HTTP 状态、耗时、预算、意图布尔值与 UI/授权结果；不含密钥、提示正文或回复正文。
- 探针的 5 秒 UI 发送确认没有识别到已提交消息，因而没有产出原计划的截图/JSON；随后 app 私有聊天存储中的合成标记计数和 `PowerCalAiPerf` 的两阶段遥测确认请求确已发生。故以阶段遥测作为本次网络结果证据，不把探针异常误报为无请求。
- 期间曾有一个仅匹配旧版 `completion elapsedMs=...` 格式的初步解析，漏掉新的 `completion stage=...` 行。初始副本保存在 `issue002-ai-rerun-preliminary-misclassified.json`，已明确标记为被更正结果取代；不要将该副本用作验收结论。
- 请求完成后读取到设置快照 `enabled=false`、免费模型仍选中、密钥仅确认存在；该快照晚于两条 HTTP 遥测，不能据此推断请求开始时的开关状态。探针没有修改 AI 设置。

本次更精确地把失败定位到结构化提案阶段的服务端 HTTP 429；之前 API 35 上的复杂请求也曾收到 HTTP 503。免费服务限流/可用性仍在阻止卡片闭环；不重复重试或改用付费模型来掩盖这一结果。Issue 状态继续为 **PARTIAL / BLOCKED**。

## Git 与下一项

代码与测试提交：`bb04bfae30dc30877d41807a71236778b16617cc`（`fix(ai): 收紧AI提案请求与失败处理边界`），已普通推送至 `origin/main`。本报告曾单独提交并推送，以明确引用该代码基线；本轮新增的复测结果仍属 Issue 002 的补充记录，未关闭问题。历史报告提交时仅遗留未跟踪 `design/`；当前另有 `DraftDeckFeedbackTest.kt` 与 `PC-OBS-005-commit-feedback.md` 等其他任务资产，均未修改或提交。下一优先项：复验已修复的 **PC-ISSUE-001** Android SAF ICS 实际导出与重复规则回环；不清除 `emulator-5554` 既有数据，使用隔离实例执行破坏性场景。

## 本地 HTTP 200 空回复回归补充（2026-10-24）

PC-ISSUE-120 为原有“免费模型空 HTTP 200 不盲目重试”修复增加了完整客户端 loopback 回归。定向 `AiChatClientTest.emptyHttp200InFreeStructuredProposalFailsOnceWithoutPublishing` 1/1 PASS：首轮预算 2048、结构化阶段 `finish_reason=stop` 且内容为空时只发送一次请求，返回明确 warning，不产生 proposal/rawReply。定向日志 SHA-256 `2E34ED41ACD5CF60221BA5DB6CE9F3BD89CB0E67A90BC37787AFA88EE404668A`；JUnit XML SHA-256 `F490D6CABB2E1FEC3919A68D9625508164E72EE45130C3327819C9E4178DF305`。完整回归 461/461 across 57 suites，0 failures/errors/skips，Lint 0 errors/58 warnings；完整日志 SHA-256 `0FCD052D2ABA1510946FD5CAFDDFE9C85CA5A784FD50EBD7E63E5E272E2D9C42`。

该补充只证明本机客户端空回复分支，没有触发真实 provider 请求。免费模型之前的 HTTP 429/503 与自然语言到可审查提案的服务端端到端链路仍未通过，Issue 继续 **PARTIAL / BLOCKED**。独立报告：`docs/issues/PC-ISSUE-120-ai-empty-http200-core-recovery.md`。

## 提案来源客户端隔离补充（2026-10-24）

PC-ISSUE-121 为“模型标记只表达意图，提案来源必须取本轮原始用户文本”增加 `AiClient.chat` 本机 HTTP 往返回归。模拟模型返回带伪造系统示例 `source` 的标记；客户端隐藏标记并只返回当前用户原文作为 `proposalSource`。定向 **1/1 PASS**，日志 SHA-256 `0738A573169B323DA8D52106F8A9C2C92067F14427429B5A582477E386E09040`，JUnit XML SHA-256 `D8EFEAB421CA2781107A514EB2EDF84111C6B942FDC303E5C84AF3BA003F7F2A`。完整 Gradle **462/462 across 57 suites**，0 failures/errors/skips，Lint 0 errors/58 warnings；完整日志 SHA-256 `7E8055E6547769BC8158D35E06268BD77B4A87B16929EEE26CE297CCA754C97B`。

这是本地模拟的客户端边界证据，不证明真实模型服从约束；未请求 provider。此前的 429/503 仍阻止真实模型提案流程，PC-ISSUE-002 继续 **PARTIAL / BLOCKED**。独立报告：`docs/issues/PC-ISSUE-121-ai-current-input-source-isolation.md`。
