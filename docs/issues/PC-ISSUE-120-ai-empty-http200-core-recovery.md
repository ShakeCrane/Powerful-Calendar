# PC-ISSUE-120 — 免费模型 HTTP 200 空回复只请求一次

日期：2026-10-24

关联：`PC-ISSUE-002-ai-timeout-proposal.md`（补充核心传输证据；不关闭原 Issue）

状态：**本地空回复恢复契约 PASS；免费 provider 端到端仍 PARTIAL / BLOCKED**

## 发现与范围

PC-ISSUE-002 的既有修复要求免费模型首次获得足够输出预算，并且 HTTP 200 但没有可见内容时不得盲目重复同一请求。此前 `AiCompletionPolicyTest` 覆盖了策略判断，但缺少经完整 `AiClient.interpret` 与本地 HTTP 传输层的回归。本 Issue 新增一个 loopback HTTP 模拟测试：结构化阶段返回 `finish_reason=stop` 且 content 为空，验证首请求预算、请求次数、错误阶段和无提案结果。

没有修改生产代码，没有连接外部 provider、没有使用付费模型，也没有向日历写入。此次只补足本地客户端的回归保护；免费模型先前的 HTTP 429/503 和端到端提案链路仍未解决。

## 修复归属与结果

原先空回复盲重试的生产修复已包含在 PC-ISSUE-002 的提交 `bb04bfae30dc30877d41807a71236778b16617cc`。本 Issue 的根因是该保护仅有纯策略单测，没有集成测试证明 HTTP 客户端确实只发送一次。本次新增 `AiChatClientTest.emptyHttp200InFreeStructuredProposalFailsOnceWithoutPublishing`，未发现新的生产缺陷，因此没有生产代码修补。

本机模拟服务确认只收到 1 次结构化请求，首轮 `max_tokens=2048`；空回复立即返回含 `structured-proposal` 阶段及 `stop` 原因的 warning，proposals 与 rawReply 均为空。模拟器仅绑定 `127.0.0.1`，测试代码在首响应后短暂继续监听，以捕获任何重复请求。

## 验证证据

- 定向测试：**1/1 PASS**，0 failures/errors/skips。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.ai.AiChatClientTest.emptyHttp200InFreeStructuredProposalFailsOnceWithoutPublishing --offline --rerun-tasks --console=plain`。
- 定向日志 SHA-256：`2E34ED41ACD5CF60221BA5DB6CE9F3BD89CB0E67A90BC37787AFA88EE404668A`；定向 JUnit XML SHA-256：`F490D6CABB2E1FEC3919A68D9625508164E72EE45130C3327819C9E4178DF305`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。**BUILD SUCCESSFUL**；461/461 测试、57 套件，0 failures/errors/skips；lint 0 errors / 58 warnings。
- 完整日志 SHA-256：`0FCD052D2ABA1510946FD5CAFDDFE9C85CA5A784FD50EBD7E63E5E272E2D9C42`；完整 `AiChatClientTest` XML SHA-256：`FB212AEDF64E19724AC53396EEC96EB492102FE045FF0EC5742F601822738BB2`。
- lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 原始日志、JUnit XML、lint XML 与 APK 位于 `.local/android-test/reports/freeze-20261024/pc-issue-120-*`。测试提交 `b51b0e830c9ff968b994cda564dcdd481986d46e` 已推送。

## 对核心冻结的影响

这项本机回归没有为 209 个重建候选新增行，也不改变 PASS/NOT TESTED 统计。PC-ISSUE-002 仍为 **PARTIAL / BLOCKED**：免费服务的结构化阶段此前返回 HTTP 429/503，且没有通过受控真实 provider 请求确认可审查提案链路。遵守现有限流，不重复发起请求，不用未经授权的付费模型替代。整体核心冻结继续 **HOLD**。
