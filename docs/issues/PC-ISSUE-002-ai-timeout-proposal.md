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

## 授权 DeepSeek Flash 虚拟设备结构化提案复测（2026-10-24）

PC-ISSUE-122 使用模拟器现有的 HTTPS 配置与密钥，在内存中将模型设为精确 ID `deepseek/deepseek-v4.1-flash`；没有写回设置。请求只包含虚构活动，显式关闭上下文共享并传入空日历上下文。Android 15 / API 35 `emulator-5554` 返回 HTTP 200；结构化阶段耗时 3.530 秒、输出预算 1800，得到 1 条可发布 ACTIVITY 提案、0 条警告。提案标题及开始/结束时刻均与合成输入一致。只有一条结构化请求遥测记录，未触发自动重试。

- 定向 Android 仪器测试：**1/1 PASS**（总耗时 3.759 秒）；测试验证 `AiClient.interpret` 返回可发布提案，未打开界面、写数据库或执行正式事件操作。
- 测试日志：`.local/android-test/reports/freeze-20261024/issue002-deepseek-device-test.log`，SHA-256 `44540242960C718612AB56EB01485A5551DB09B55AFDED2D9457C520DBC234D3`。脱敏阶段遥测：`issue002-deepseek-perf.log`，SHA-256 `120D20442E45369AE68055EE7C45821308C4D38A30B1E0E77C9C7FF80A1E6EBB`。日志不包含提示正文、响应正文、URL、headers 或密钥。
- 最终完整 Gradle 回归：**462/462** across 57 suites，0 failures/errors/skips，Lint 0 errors/58 warnings；完整日志 SHA-256 `8C82E247D1FA802B4A876C68D0727A0C3E46BFEB8E6343092A1D1F29E3F10A2D`。Debug APK SHA-256 `138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 实际模型探针运行于代码提交 `cf573990300bfd6bd3bedfbf624536afa0daf4d5`（当时 AndroidX test runner 1.5.0 / JUnit 扩展 1.1.5）；之后只升级了测试基础设施到 runner 1.7.0 / JUnit 扩展 1.3.0，并完成 AndroidTest 编译和 lint。为遵守单次付费请求边界，没有在基础设施升级后重发模型请求。最终代码/完整回归基线为 `b3ac84070086f1ecd57a345458a1bb09edfeebcb`。

该结果只签核真实模型的单阶段结构化提案传输与解析子路径。免费服务此前的 429/503、用户聊天到提案卡的呈现、草稿持久化/重启、确认与撤销链以及实体设备/OEM 行为仍未闭环；本次 Issue 002 继续 **PARTIAL / BLOCKED**。实体设备测试由用户执行。独立报告：`docs/issues/PC-ISSUE-122-deepseek-v41-flash-emulator-probe.md`。

## 模糊 REPLACE 目标的核心消歧保护（PC-ISSUE-131）

在 API 34 Robolectric 的解析器→草稿服务→SQLite 回归中，PC-ISSUE-131 修复了没有 `eventSeq` 的歧义 REPLACE 警告显示 `#null` 的问题。该报告当时只覆盖本地核心保护。`REC-P1-050` 后由 PC-ISSUE-137 按窄化范围独立签核为核心 PASS；真实模型如何识别自然语言歧义、聊天 UI 是否显示提问仍未测。Issue 002 仍 **PARTIAL / BLOCKED**。详见 `docs/issues/PC-ISSUE-131-ai-ambiguous-replace-target.md` 与 `docs/issues/PC-ISSUE-137-ai-ambiguous-replace-target-core-signoff.md`。
## 本地聊天到待审草稿闭环补充（PC-ISSUE-133，2026-10-24）

新增 Robolectric API 34 loopback 回归，把聊天 marker、`AiClient` 对本轮原始用户文本的权威化、结构化提案解析、`AiDraftService` SQLite 持久化和数据库重开串在同一测试中。聊天模型的合成 marker 携带了伪造的恶意 `source`，客户端只把当前用户原文送入结构化阶段；最后生成一张稳定身份/队列顺序的 PENDING 草稿，正式事件、历史和提醒意图均为空。

定向 1/1 PASS；完整冻结回归 469/469、58 suites、0 failures/errors/skips；source boundary、Debug/APK AndroidTest 编译、Lint 均通过，Lint 0 errors/58 warnings。测试只使用本机 loopback 假服务，没有外部模型、界面或设备。定向日志 SHA-256 `1B343F9AB19B66F524C58C9ACEF1C95E761E5DEA2783A23B1A673A7CE2E5C1E6`；JUnit XML SHA-256 `78CD8691A2ABA35A8EB9CAF50A5CCE8C4FE0C046292ADE9B41DFB442DAE9CAEF`；全量日志 SHA-256 `862CCBC38283B8D8D8776FDB98EC246ED1F2C1E11A5337C580D0A1D5F9364F62`。代码/测试提交 `b4f5179efa689015bb5ec73a2987a5e76f2c6b82` 已推送。独立报告：`docs/issues/PC-ISSUE-133-ai-chat-draft-pipeline.md`。

此回归只签本地 client-to-SQLite 管线，不证明 live model 输出 marker、真实免费 provider 可用或聊天卡片呈现。`REC-P1-047` 继续 BLOCKED：免费服务此前聊天 HTTP 200 后结构化阶段 HTTP 429，仍没有真实可审查卡片；PC-ISSUE-002 继续 **PARTIAL / BLOCKED**，核心冻结保持 **HOLD**。本次没有调用 DeepSeek。

Latest structural-only validation after PC-ISSUE-134: artifact .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-134.json SHA-256 FB25654B9A34DFD676713F580D1C955B24E6BCDBBE504ECE8D16150A8582465A; ledger SHA-256 CD6275A893058EB678FBD9B3BEA04EAEAB6BFEEE26BFEFDC18366074852C8E58. This validates structure/counts only and does not change the BLOCKED decision.

## 授权 DeepSeek 两阶段聊天到待审草稿回归（PC-ISSUE-134，2026-10-24）

在 Android 15/API 35 的 emulator-5554 上，使用用户授权的 deepseek/deepseek-v4.1-flash 和仅含虚构活动的输入，完成真实 chat → marker/source 校验 → structured-proposal → 临时 SQLite PENDING 草稿链路。关闭 calendar context 分享；模型覆盖仅在内存；不打开界面、不访问默认日历库或执行发布。

聊天阶段 HTTP 200/4.693 秒，proposalIntent=true；结构化阶段 HTTP 200/2.769 秒，1 proposal、0 warnings。客户端只把本轮原始用户文本交给结构化阶段。待审草稿在 SQLite 重开后保留同一 draftId 与 queueOrder，状态仍 PENDING；正式事件、历史和提醒意图均为空。脱敏遥测只记录两个阶段各一条请求，没有自动重试；没有输出密钥或提示/回复正文。

定向 instrumentation 1/1 PASS；完整 Debug 回归 469/469 across 58 suites，0 failures/errors/skips；边界、APK/AndroidTest 组装和 lint 通过，0 errors/58 warnings。测试提交 477ddf44f1199e3316c5981e0a640c41efcdd416 已推送。独立报告 docs/issues/PC-ISSUE-134-deepseek-chat-to-draft-live-flow.md。

该结果证明授权 DeepSeek 的 live two-stage 路径，但不替代免费 provider 要求：先前免费模型 chat HTTP 200 后 structured HTTP 429 的结果仍使 REC-P1-047 BLOCKED，PC-ISSUE-002 仍 PARTIAL / BLOCKED。没有实体设备测试。

## 免费模型受控重测状态与实时探测门控（2026-10-24）

为遵循免费模型优先，并避免完整 AndroidTest 意外触发付费网络请求，实时探测现在必须显式传入 `powercalLiveProbe=free` 或 `powercalLiveProbe=deepseek`；代码只接受固定的免费模型 `stealth/glyph-cluster:free` 和已授权的 `deepseek/deepseek-v4.1-flash`，不把模型覆盖写回设置。输入为合成活动，calendar context 关闭，结果只写入唯一临时 SQLite。门控测试代码提交 `c33eb36a9308066983667cbf0091b8c451a27806` 已推送。

本次未发出免费模型请求：API 35 `emulator-5554` 上 `pm path dev.powerfulcalendar` 无结果，`run-as dev.powerfulcalendar` 返回 `unknown package`。因此无法复用此前模拟器私有 AI 配置；没有重装应用、读取/恢复密钥或触碰日历数据。上次可用证据仍是 chat HTTP 200、structured HTTP 429，且无 reviewable pending proposal；REC-P1-047 继续 BLOCKED，Issue 002 继续 PARTIAL / BLOCKED。

门控改动后的完整离线 Gradle 回归：**BUILD SUCCESSFUL**，469/469 JVM 测试、58 suites、0 failures/errors/skips；源码边界、Debug/AndroidTest APK 组装通过；Lint 0 errors / 58 warnings。日志 `.local/android-test/reports/freeze-20261024/pc-issue-002-live-probe-opt-in-full-gradle.log` SHA-256 `3AFAEC67B24C92E808B0767939F35AC86BA427E8D3D742D10095C5CC7AE7805E`；AndroidTest APK SHA-256 `EF502CA60DE10D95A2076DD416ABA9318D6343EF8415D17B6F0C2D85E7533958`。本次只编译 AndroidTest，没有执行 instrumentation 或外部模型调用。核心冻结继续 HOLD。

### Read-only AVD configuration availability recheck (2026-10-24)

Read-only checks found that API 35 `emulator-5554` still has no `dev.powerfulcalendar` package. `emulator-5556` has the package, but its shared-preference filename listing contained only `powercal-onboarding.xml` and `powercal-ui.xml`; `powercal-ai.xml` was absent. No preference values or credential contents were read, the app was not launched, settings were not changed, and no provider request was sent. Evidence: `.local/android-test/reports/freeze-20261024/pc-issue-002-config-availability-check.json`, SHA-256 `824CF934453870ECA21A97D0BB3825801F70FA6918DDBD7D0FEE10F88F6CA797`.

This is an environment/configuration check only, not product behavior evidence. The previous free-model result remains chat HTTP 200 followed by structured HTTP 429; REC-P1-047 remains BLOCKED and PC-ISSUE-002 remains PARTIAL / HOLD until a configured emulator is available for a bounded retest.

After recording this evidence in the reconstructed acceptance ledger, corrected structural validation passed for 209 unique rows and unchanged counts (164 PASS / 0 FAIL / 8 BLOCKED / 30 NOT TESTED / 7 SPEC UNRESOLVED). Artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-002-config-check-validated.json`, SHA-256 `BD2D976A66A48EF71C543DE0906D2257BCB4F066147BAD7BF61B07D7E18E3D7C`; ledger SHA-256 `D500FE6D936C71C9514D2FA30645438DC74F3318A6B89BBE373B62ABCE093921`. Structural validation does not test product behavior.

### Local provider-failure guard re-run (2026-10-24)

On the current code baseline, local loopback regressions were rerun for the free structured-proposal HTTP 429 case and the empty HTTP 200 case, along with all five `AiCompletionPolicyTest` cases: **7/7 PASS**, zero failures/errors/skips. The 429 path returns a stage-specific warning with no proposal/raw reply and does not expose the synthetic response body or credential sentinel. The free-model empty-200 path fails once without publishing; policy tests retain the initial free-model output budget and disallow blind/free or unauthorized-paid retries.

Command: `.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.ai.AiChatClientTest.structuredProposalHttp429NamesFailedStageAndProducesNoProposal' --tests 'dev.powerfulcalendar.ai.AiChatClientTest.emptyHttp200InFreeStructuredProposalFailsOnceWithoutPublishing' --tests dev.powerfulcalendar.ai.AiCompletionPolicyTest --offline --rerun-tasks --console=plain`.

Evidence: `.local/android-test/reports/freeze-20261024/pc-issue-002-bounded-failure-regression-rerun.log`, SHA-256 `84AEB6F1B874575ABB0979349EA9A459285D5A6B0F34ABC00A0ADCBB7A3A7759`; `AiChatClientTest` XML (2/2), SHA-256 `5422891B1A6C8451FED0314B39B4A26F4AECBB6A92C3ABBB817620E1F7DAE92A`; `AiCompletionPolicyTest` XML (5/5), SHA-256 `2165CDF68DA513B11BD0E66EA882AFBD7E51B33191B7410797AFCABCA124E5DE`. These are local loopback/client-policy tests; no provider request, emulator, UI, or business-data write occurred. They do not satisfy the live free-provider proposal requirement; REC-P1-047 remains BLOCKED and PC-ISSUE-002 remains PARTIAL / HOLD.

## 当前状态补记：歧义目标核心签核与免费模型阻塞（2026-10-24）

PC-ISSUE-137 在代码/测试基线 `cc22d49cf79d5638c3a184f88e77ca29cf4fe91a` 上重跑现有 Robolectric API 34 定向测试。两个同名正式 Activity 下，无 `eventSeq` 的 REPLACE 保持阻断并要求用户澄清；不创建 AI 草稿，正式记录、历史、待处理队列、identity 数量及下一个序号预览在 SQLite 重开前后不变。定向 1/1 PASS；完整离线 Gradle 边界、469/469 JVM、Debug/AndroidTest APK 组装与 lint 全部通过（58 suites，0 failures/errors/skips，0 lint errors / 58 warnings）。这只签本地核心保护，不签真实模型消歧、聊天 UI、API 35 provider 或设备行为。

- 独立报告：`docs/issues/PC-ISSUE-137-ai-ambiguous-replace-target-core-signoff.md`。
- 定向日志 SHA-256 `96141B47183B3B8927B1BC6265304AE3E8280EAF1F558D7606BBC4F7982DCC85`；JUnit XML SHA-256 `434D18A4CC596EFCC9B8B84F6833192B3094DA5EA5E2C6D73C93498C5DA2A595`。
- 完整 Gradle 日志 SHA-256 `C2C97732B82813EE678F04F9ACC8CE8C249D797DF808E4D88AC0FB8190C70153`。

Issue 002 本身仍为 **PARTIAL / BLOCKED**：免费服务之前的结构化阶段 HTTP 429/503 仍未闭环；API 35 `emulator-5556` 当前缺少 `powercal-ai.xml`，未读取配置值或凭据，也未重发免费请求。PC-ISSUE-134 已单独证明授权 DeepSeek V4.1 Flash 的两阶段临时草稿路径，但不替代免费 provider 验收。实体设备测试由用户执行。

### PC-ISSUE-146 本地提案与批次审核回归（2026-10-24）

`REC-P1-052/053` 已由独立报告 `docs/issues/PC-ISSUE-146-ai-review-and-batch-authorization-compose-regression.md` 窄化签核为 PASS：四种提案动作及精确可处理范围、批次总数/操作数/逐项范围、固定 draftId/version 审核快照、取消及忙碌状态不授权。Robolectric API 35 Compose 定向 4/4；完整 Gradle 482/482（62 suites），lint 0 errors / 58 warnings。未请求 provider、未执行数据库写入，也未运行独立 AVD 或实体设备。此结果不改变 `REC-P1-047` 免费模型阻塞；PC-ISSUE-002 继续 **PARTIAL / BLOCKED**。

### PC-ISSUE-147 本地显式/建议意图分流（2026-10-24）

修复了任何 draft marker 都自动触发结构化预览的问题：只有 `intent=explicit` 才把当前用户原文送入自动提案预览；`intent=suggested` 只保留轻量按钮；缺失/未知 intent 回退为建议。模型生成的 source 不会作为解析输入。定向本地 parser/client/合成 SQLite 流程 13/13 PASS；完整 Gradle 483/483（62 suites），lint 0 errors / 58 warnings。代码/测试提交 `8e642ad0d8e1ba5786f2e2ee0bdbb16851a5b9b2` 已推送，报告：`docs/issues/PC-ISSUE-147-ai-explicit-vs-suggested-chat-routing.md`。

本轮没有请求免费或付费模型，也没有运行独立 Android 虚拟设备或实体设备。真实 provider 对自然语言的分类与按钮呈现仍未测，`REC-P1-048` 保持 NOT TESTED；`REC-P1-047` 仍因免费 provider 路径未闭环而 BLOCKED，PC-ISSUE-002 继续 **PARTIAL / HOLD**。
