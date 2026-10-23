# PC-ISSUE-113 — AI/ICS 只读核心投影签核

日期：2026-10-24

候选：`REC-P0-082`（重建 P0；历史 209 项映射仍为 `UNMAPPED`）

状态：**AI 与 ICS 核心只读投影 PASS；整体冻结 HOLD**

## 签核范围

本次将验收限定为核心数据路径：从 SQLite 正式快照读取，投影为 ICS 或 AI 请求上下文时，不新增第二份事件真相、不创建草稿，并且不改变正式记录、提交历史、提醒意图、身份计数或序号预览。Android 平台、SAF、外部日历和设备路径不属于本次签核范围。

## 定向回归

Robolectric API 34 下两条用例各 **1/1 PASS**：

- `PowerStoreTest.icsActivityProjectionDoesNotCreateSecondFormalEventAcrossRestart`：ICS 导出并解析出一条正式 Activity；投影前后及 SQLite 重开后，正式记录、历史、提醒意图、身份计数与序号预览不变。
- `AiChatClientTest.sharedAiContextProjectsOneFormalSnapshotWithoutWritingStore`：本地 HTTP stub 收到恰好一条正式快照，保留 eventSeq、revision、类型与标题；响应不生成提案或草稿，存储不变量不变。

合并定向运行日志 SHA-256：`C61ED6D67744211B6B463BD99B6DB44B9D32812828425A4A20C07BF15B4995C5`。定向 `PowerStoreTest` XML SHA-256：`DCA7E6C8AB9CFD585308002D60E729490A46BC9C8D933FD228EB0C030B55C326`；定向 `AiChatClientTest` XML SHA-256：`089D7540AC6BC31394638A2B60AE084C2A9D9248C3A15274396058DE3054B30E`。

## 完整回归

命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。

**BUILD SUCCESSFUL**；460/460 测试、57 个套件，0 failures/errors/skips；lint 0 errors / 58 warnings。完整日志 SHA-256：`05B62B132096D301ABD30A4A7C64D9F267AD66D31B3CC0CADB8A6AE0B6B21260`。完整 `PowerStoreTest` XML SHA-256：`6F05422A2116413AD01CB41ADBB9CA75BACB26A09E1EC0D18CD8B67D22F463E8`；完整 `AiChatClientTest` XML SHA-256：`B8F88C761824CBDFB7D4AE71173345C0BAA7072AE478CB9E0FB650B8E7604FF3`；lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。

日志及 JUnit XML 位于 `.local/android-test/reports/freeze-20261024/pc-issue-113-*`。测试基线提交：`69913bed3fb12de41ebc06497d040dfd39477c7f`；没有修改应用或测试源码。

## 台账与边界

- `REC-P0-082` 对“AI/ICS 核心只读投影不创建第二份事件真相”签为 **PASS**。Android 平台、SAF、外部日历及设备行为未据此签核。
- 候选统计：**142 PASS / 0 FAIL / 9 BLOCKED / 51 NOT TESTED / 7 SPEC UNRESOLVED**；P0 **118/0/1/2/5**，P1 **24/0/8/49/1**。历史 209 项仍无映射，整体冻结保持 **HOLD**。
- 最新结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-113.json`，SHA-256 `46B6F9A7ECDDE04D3FC04A998E734DBBEB54D347A7131FB40BEB3F1D7102162C`；CSV SHA-256 `55FB44E600F987C4B5B445382089E539949AEA23E5048804B56D16B674DDA73C`。结构校验为只读行数、唯一 ID、必填字段、优先级及证据引用校验；`product_behavior_tested=false`。
