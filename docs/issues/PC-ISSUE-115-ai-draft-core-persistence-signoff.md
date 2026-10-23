# PC-ISSUE-115 — AI CREATE 草稿核心持久化签核

日期：2026-10-24

候选：`REC-P1-056`（重建 P1；历史 209 项映射仍为 `UNMAPPED`）

状态：**解析结果到待审草稿的核心持久化 PASS；API 35/provider 选择路径未签核；整体冻结 HOLD**

## 签核范围

本次签核本地解析的 CREATE 提案经 `AiDraftService` 转为 PENDING 草稿，并跨同一 SQLite 数据库关闭/重开后保留。正式事件和提交历史不得因此产生。API 35/provider 路径及明确的写入选择流程不属于本次核心签核。

## 验证结果

`PowerStoreTest.parsedAiCreateProposalPersistsAsAnUnpublishedDraftAcrossRestart` 使用本地结构化 JSON。解析无警告；服务创建一个 CREATE/PENDING 草稿，正式事件和历史均为空。重开 SQLite 后，draftId、eventSeq、版本、操作、表单、状态和待审队列顺序保持一致，正式事件和历史仍为空。

- 定向 Robolectric API 34：**1/1 PASS**。日志 SHA-256：`77AB328142E24F2EA8129557FA6329DF41D078A1DB272883A302F91F1559C162`；JUnit XML SHA-256：`1B5BCC24D2AC8196C26BE201252CDC70ACE7E935C32DE3ACFAA55EA74AD5A011`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。**BUILD SUCCESSFUL**；460/460 测试、57 个套件，0 failures/errors/skips；lint 0 errors / 58 warnings。
- 完整日志 SHA-256：`2CBE809088E0F8A0B913DFFCEAD72FD8E0BB0359DBEEA37D21CF9EF9786D29D5`；完整 `PowerStoreTest` XML SHA-256：`13693C0DA5483C9233B31C74EC20FA9FF7C9B3A9DDDA126C70550C0242F095F1`；lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 日志与 JUnit XML 位于 `.local/android-test/reports/freeze-20261024/pc-issue-115-*`。测试基线：`1602b3ae007f2eca2e64a1bf2e9ebde08c075400`。没有修改应用或测试源码。

## 台账与结论

- `REC-P1-056` 对“本地解析的 CREATE 提案转为 PENDING 核心草稿，并跨 SQLite 重开持久化且不发布正式事件”签为 **PASS**。API 35/provider 与显式选择路径仍未签核。
- 候选统计：**144 PASS / 0 FAIL / 9 BLOCKED / 49 NOT TESTED / 7 SPEC UNRESOLVED**；P0 **119/0/1/1/5**，P1 **25/0/8/48/1**。历史 209 项仍无映射，整体冻结保持 **HOLD**。
- 最新结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-115.json`，SHA-256 `4B62D6669634D5B6C20A4C47E05585288019D311B95C43D1D34334535E5B7030`；CSV SHA-256 `42386817457F1EBDFE6C5BF131D068B492E9BBC2301F68C99C28B7201B8038A2`。结构校验仅验证行数、唯一 ID、必填字段、优先级及证据引用；`product_behavior_tested=false`。
