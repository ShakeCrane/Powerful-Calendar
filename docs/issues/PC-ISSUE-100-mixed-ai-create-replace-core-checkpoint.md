# PC-ISSUE-100 — 混合 CREATE/REPLACE 提案核心检查点

日期：2026-10-24

候选：`REC-P1-049`（重建 P1；历史映射仍为 `UNMAPPED`）

状态：**核心解析/草稿路径有部分证据；候选仍 NOT TESTED；整体冻结 HOLD**

## 验收范围

本轮只核验核心结构化响应路径，没有改生产代码或测试代码。`AiResponseParserTest.parsesCreateAndReplaceProposals` 将同一响应解析为独立的 CREATE 与 REPLACE 提案，并保留 REPLACE 的目标 `eventSeq`。`PowerStoreTest.parsedAiCreateAndReplaceRemainDraftsUntilExplicitCommit` 再验证两项分别生成待审草稿，正式 CREATE 尚不存在，正式 REPLACE 目标的字段与 revision 不变。

没有调用免费 provider，也没有验证 API 35 上的请求和提案卡呈现；因此 `REC-P1-049` 整体仍为 NOT TESTED。没有修改 UI。

## 回归证据

- 定向 Robolectric API 34：**2/2 PASS**，覆盖两个测试类。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.ai.AiResponseParserTest.parsesCreateAndReplaceProposals --tests dev.powerfulcalendar.core.PowerStoreTest.parsedAiCreateAndReplaceRemainDraftsUntilExplicitCommit --offline --rerun-tasks --console=plain`。日志 SHA-256 `3A7B8F80497630BEE1D535737D91AF6DAEED9EF25D95F217BD41911C62088831`；parser XML SHA-256 `872C6CEC357486CC7542C7F5B643E65487374B5040D285E85068506909D0C80A`；PowerStore XML SHA-256 `EF6435640E182DB6229AF8C71B749D321762BEC45F35D30F225BA9578CD3E59A`。
- 当前代码基线完整 Gradle：**452/452 across 57 suites**，0 failures/errors/skips；Lint 0 errors / 58 warnings。日志 SHA-256 `BE3EC6AAEC213C2E2DB7A84146771DA150192D57B8BBF566CFEE5F8A7FF1B1F4`。该全量回归与代码提交 `55f3c1212eafaf306ad70e89b308fa1c3b3612c5` 同基线。

## Git

- 本报告、候选台账和冻结摘要单独提交并推送；本检查点没有新增代码提交。

结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-100.json`，SHA-256 `CC8184361CCE7C1C4921D3FEFCD5579E0D6939B8CF591820D468F72ABBB1026A`；台账 SHA-256 `040CD2EEFAF281FA018ED465194C8D6ADCAC93CCAAE40B333CAD7BD23D18E5BC`。校验只确认结构与计数，不签核 API 35/provider/UI 行为。
