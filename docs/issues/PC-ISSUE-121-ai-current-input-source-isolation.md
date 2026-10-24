# PC-ISSUE-121 — AI 提案来源限定为本轮原始文本

日期：2026-10-24

关联：`PC-ISSUE-002-ai-timeout-proposal.md`（本地来源隔离回归；不关闭原 Issue）

状态：**客户端原文来源隔离核心契约 PASS；真实模型合规与 provider 端到端仍未签核**

## 发现与范围

PC-ISSUE-002 已规定模型输出中的提案标记只代表意图提示，不能让模型生成的 `source` 成为写入型草稿 Skill 的输入。已有纯解析器测试验证了来源函数，但此前没有经过完整 `AiClient.chat` HTTP 往返的集成断言。本 Issue 使用只绑定 `127.0.0.1` 的本地模拟服务，返回带有伪造系统示例 `source` 的提案标记，核对客户端最终只把本轮原始用户文本作为 `proposalSource`。

## 根因与结果

没有发现新的生产代码缺陷；缺口是核心来源规则缺少客户端往返回归。本次新增 `AiChatClientTest.chatProposalSourceComesOnlyFromCurrentRawUserText`。客户端隐藏内部提案标记，展示普通回复文本，并将 `proposalSource` 限定为本轮用户输入；模型返回的伪造来源不会被采用。请求只包含系统消息与本轮原始用户消息，测试未连接真实模型、未写入 Store，也没有改生产代码。

此测试没有证明真实模型始终遵守提示，也没有覆盖文件/OCR provider 上传或多轮真实模型行为。上述范围不能从本机模拟结果外推。

## 验证证据

- 定向测试：**1/1 PASS**，0 failures/errors/skips。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.ai.AiChatClientTest.chatProposalSourceComesOnlyFromCurrentRawUserText --offline --rerun-tasks --console=plain`。
- 定向日志 SHA-256：`0738A573169B323DA8D52106F8A9C2C92067F14427429B5A582477E386E09040`；JUnit XML SHA-256：`D8EFEAB421CA2781107A514EB2EDF84111C6B942FDC303E5C84AF3BA003F7F2A`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。**BUILD SUCCESSFUL**；462/462 测试、57 套件，0 failures/errors/skips；lint 0 errors / 58 warnings。
- 完整日志 SHA-256：`7E8055E6547769BC8158D35E06268BD77B4A87B16929EEE26CE297CCA754C97B`；完整 `AiChatClientTest` XML SHA-256：`590B941AB196B716DC396929B8A0B28656967B879A6D934F75FC75C112CB0703`。
- lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 原始日志、JUnit XML、lint XML 与 APK 位于 `.local/android-test/reports/freeze-20261024/pc-issue-121-*`。测试提交 `6345dbba2216b625bf95eaad7651d3b6cfc89275` 已推送。

## 对核心冻结的影响

该补充不新增 209 项重建候选，也不改变 148 PASS / 0 FAIL / 9 BLOCKED / 45 NOT TESTED / 7 SPEC UNRESOLVED。PC-ISSUE-002 仍 **PARTIAL / BLOCKED**：本地模拟只证明客户端边界，免费 provider 结构化阶段之前的 HTTP 429/503 及真实模型输出链路仍未解决。没有重复请求绕过限流，也没有使用付费模型。整体核心冻结继续 **HOLD**。
