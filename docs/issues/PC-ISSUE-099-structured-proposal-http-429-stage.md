# PC-ISSUE-099 — 结构化提案 HTTP 429 阶段提示

日期：2026-10-24

候选：`REC-P1-062`（重建 P1；历史映射仍为 `UNMAPPED`）

状态：**核心错误诊断修复并通过；候选仍 NOT TESTED；整体冻结 HOLD**

## 根因与修复

本地 stub 服务返回 HTTP 429 时，`AiClient.complete` 已生成带状态码且不包含 provider 错误正文的安全异常。`AiClient.interpret` 的 catch 分支随后将其改写为通用“AI 请求失败”，丢掉了结构化提案阶段信息。

最小修复是把该分支提示改为“结构化提案阶段失败”，保留原有安全错误信息。新回归通过本地 `ServerSocket` 返回 429，并验证错误包含阶段名和状态码、没有提案或原始成功回复，且不泄漏 provider 错误正文或 API key。没有调用外部模型/网络。

## 回归证据

- 修复前定向复现：**1 项失败**，稳定复现提示为 `AI 请求失败：HTTP 429；请检查端点、模型与凭据`；状态码、无提案/回复及密钥与错误正文不泄漏断言均先通过，唯一失败是缺少阶段名。日志 `.local/android-test/reports/freeze-20261024/pc-p1-062-structured-429-prefx-final.log`，SHA-256 `2A457975115309FEF6F78489E88104BB91372C9DCF276EBB3E2EBA769BB1B674`；JUnit XML SHA-256 `DE6F18B7160E8D3E434B49CF4527D2E78153349C4CDE58F9D2B2CCEEF01B0589`。
- 修复后定向 Robolectric API 34：**1/1 PASS**。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.ai.AiChatClientTest.structuredProposalHttp429NamesFailedStageAndProducesNoProposal --offline --rerun-tasks --console=plain`。日志 SHA-256 `351C6C8FB69570AD4EB4B1BD386E87A0A396D66D725C9F9395C9E04DC9F59F5E`；JUnit XML SHA-256 `7CE5EF1C95B18178115942156F0C756F3CEB82D161AD9409B15921323D696785`。
- 完整冻结 Gradle 命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**452/452 across 57 suites**，0 failures/errors/skips；Lint 0 errors / 58 warnings。完整日志 SHA-256 `BE3EC6AAEC213C2E2DB7A84146771DA150192D57B8BBF566CFEE5F8A7FF1B1F4`；完整 `AiChatClientTest` XML SHA-256 `1DC0FC3550EAAE37A01CEFA8F5A624E8947E766CA5C826B1AA22DA4FE8DD8F83`；完整 `PowerStoreTest` XML SHA-256 `BCD19D286B9FF65C08822061D2690F8E53A1B98451DDB419593CACA54DDC5E43`；Lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256 `0CE2928EF5E597475BEDF1C6BCD96AD5BD6267EF987DB7589CC959EFF1BFA7A9`。

本地 HTTP stub 验证了 AI client 的错误映射和无提案结果。环境没有 `adb`；API 35 provider 与界面提示/成功卡呈现未测，所以 `REC-P1-062` 保持 NOT TESTED。没有修改 UI。

结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-099.json`，SHA-256 `D073E87A42F7970F8094DB7E1B2E8CF3DFF03A6B4DA9487A76076F3C0CD03D20`；台账 SHA-256 `386E2BD443FC58F9BE1D3F392CF8B3E7C5020EAB17439FE7DBCE038F26CED2B5`。校验仅确认结构、唯一 ID、126/82/1 分区和状态计数；不测试产品行为或历史映射。冻结统计为 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED。

## Git

- 核心修复与回归测试：`55f3c1212eafaf306ad70e89b308fa1c3b3612c5` — `fix(ai): 标出结构化提案失败阶段`，已推送。
- 本独立报告、台账和冻结摘要随后单独提交并推送。
