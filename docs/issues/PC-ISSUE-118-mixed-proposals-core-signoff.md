# PC-ISSUE-118 — 混合 CREATE/REPLACE 核心草稿隔离签核

日期：2026-10-24

候选：`REC-P1-049`（重建 P1；历史 209 项映射仍为 `UNMAPPED`）

状态：**解析到独立待审草稿的窄化核心契约 PASS；provider/API 35/展示路径未签核；整体冻结 HOLD**

## 签核范围

本地结构化响应包含一项 CREATE 和一项 REPLACE。签核 parser 保留两种独立操作与 REPLACE 原始 eventSeq，`AiDraftService` 创建两条待审草稿；CREATE 不生成正式事件，REPLACE 的正式对象内容与 revision 不变。API 35/free-provider 请求、提案卡呈现、批准提交流程和 SQLite 重开持久性不属于本次签核。

## 验证结果

- 定向 Robolectric API 34：**2/2 PASS**，分别验证 `AiResponseParserTest.parsesCreateAndReplaceProposals` 和 `PowerStoreTest.parsedAiCreateAndReplaceRemainDraftsUntilExplicitCommit`。
- 定向日志 SHA-256：`50B251FEEF9B2A248A851A7D83C421C596F7174582F1387B75EBC3A8DADDE52A`；parser JUnit XML SHA-256：`89C964BBD0CB5DBE7E71529242ED905AD52B94383455656F09B63848427209B8`；PowerStore JUnit XML SHA-256：`FDEC66FF44D72A1B85955DB83D696AEB9F441CC014DD94EC4B4D5BF98A7377BD`。
- PowerStore 用例确认提案无警告，生成两个草稿，保留 REPLACE eventSeq；CREATE 的正式记录不存在，REPLACE 目标的 title、revision、startMillis 未改变，两条草稿仍位于待审队列。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。**BUILD SUCCESSFUL**；460/460 测试、57 个套件，0 failures/errors/skips；lint 0 errors / 58 warnings。
- 完整日志 SHA-256：`FD49EC5A86D0A21A17953405BDAF1B52FE81778856803E9AB8D5BF39ABBE4FA4`；完整 `AiResponseParserTest` XML SHA-256：`362A4D6DB50259357E25C565F140BE68A7596A4B5B3E13598AA7BA6CC052E5EF`；完整 `PowerStoreTest` XML SHA-256：`F08D65D79F3D03CA9C927FA171E1617DD18FC85A4EA0D6483A7C5F5BB4464A12`；lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 日志、JUnit XML、lint XML 和 APK 保存在 `.local/android-test/reports/freeze-20261024/pc-issue-118-*`。测试源码基线：`fa4a2a6a8b4d4a85f1ab843e98d66ebfcc5c92c2`。没有修改应用或测试源码，没有调用 provider。

## 台账与结论

- `REC-P1-049` 对独立 CREATE/REPLACE 解析、待审草稿隔离和不变更正式对象的窄化核心契约签为 **PASS**。API 35/provider、展示、批准与重开持久性仍未验证。
- 候选统计：**147 PASS / 0 FAIL / 9 BLOCKED / 46 NOT TESTED / 7 SPEC UNRESOLVED**；P0 **119/0/1/1/5**，P1 **28/0/8/45/1**。历史 209 项仍无映射，整体冻结保持 **HOLD**。
- 最新结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-118.json`，SHA-256 `F2490DF21C9C00691FC71B760F729CF18D830BEFA2A161F4F3865FF2176D985C`；CSV SHA-256 `540B486F5493752F09B10DA863026ECB098E3134BDB25B4E186F4B09AA83A840`。结构校验只验证账本结构、状态计数和证据引用，不签核产品行为或历史映射（`product_behavior_tested=false`）。
