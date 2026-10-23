# PC-ISSUE-119 — AI 缺失字段核心恢复签核

日期：2026-10-24

候选：`REC-P1-051`（重建 P1；历史 209 项映射仍为 `UNMAPPED`）

状态：**缺失时间恢复与不可推断类型澄清的窄化核心契约 PASS；provider/API 35 路径未签核；整体冻结 HOLD**

## 问题与签核范围

此前 PC-ISSUE-101 和 PC-ISSUE-103 分别记录了缺失时间的草稿恢复、缺类型时不猜测的修复证据，但 `REC-P1-051` 仍保持 NOT TESTED，两个核心分支尚未在本次独立回归中合并验证。本报告把签核范围限定为本地解析器与 PowerStore/SQLite：缺失的 Activity 起止时间保持空值；无效提交不能生成正式事件或历史；INVALID 草稿跨 SQLite 重开保留；补齐时间后回到 PENDING 并只提交一次。若事件类型缺失且没有可靠语义线索，解析器须请求澄清，不创建提案/草稿，也不预留事件身份。

API 35 免费 provider 请求链路、实际 Android 设备行为及提案后续路径不属于本次证据。没有修改应用或测试源码，也没有调用 provider。

## 根因与修复归属

缺少起止时间时，核心模型保留空值并在显式提交校验时拒绝不完整事件；这是阻止编造时间的预期安全边界，PC-ISSUE-101 补上了草稿跨重开及修复后提交的集成回归。缺少类型且没有语义线索时，解析器原本会安全地不生成提案，但旧提示只说“已忽略”，没有告诉用户如何继续；PC-ISSUE-103 已将提示改为明确请求类型澄清。本 Issue 119 没有新增代码修复，负责把这两个已修复/已验证的核心分支独立合并回归并完成 `REC-P1-051` 的窄化签核。

## 验证结果

- 定向命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.core.PowerStoreTest.aiProposalWithoutDecidableKindRequestsClarificationWithoutReservingData --tests dev.powerfulcalendar.ai.AiResponseParserTest.unknownKindIsDroppedRatherThanCoerced --tests dev.powerfulcalendar.ai.AiResponseParserTest.missingTimeStaysMissing --tests dev.powerfulcalendar.core.PowerStoreTest.parsedAiActivityWithMissingTimeRemainsRepairableDraftAcrossRestart --offline --rerun-tasks --console=plain`。
- 定向 Robolectric/parser 回归：**4/4 PASS**，0 failures/errors/skips。覆盖未知类型不被强行转换、缺失时间不被编造、无类型线索时不分配身份、缺时 Activity 草稿重开/修复后单次提交。
- 定向日志 SHA-256：`CBD542CB5B29ECE3C57F5F5820F69653A1CFC7C2357AA2645CED18207BF2A27C`。
- 定向 `AiResponseParserTest` XML SHA-256：`843F7C30E8437A7740E28D54ECC934A5220638121B3FA549904186924DFE36B2`；`PowerStoreTest` XML SHA-256：`5F0C74D3941FBFDF326D17C51793439F2D273187EACD695E96D0CBF65641DDC1`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。**BUILD SUCCESSFUL**；460/460 测试、57 个套件，0 failures/errors/skips；lint 0 errors / 58 warnings。
- 完整日志 SHA-256：`8678BE5056C47C28E251DB755D0B2A2F58990377DC1591F9B8DA18660EBE7985`；完整 `AiResponseParserTest` XML SHA-256：`D047753A1894A881ED5909DAF7108E8BFB8BB221A6E71EB18932F7F87D9DAB80`；完整 `PowerStoreTest` XML SHA-256：`E8824616622B34D1DE4C425E05E326857947488168BC1275AD5F115018F7E0FE`。
- lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 日志、JUnit XML、lint XML 和 APK 保存在 `.local/android-test/reports/freeze-20261024/pc-issue-119-*`。回归执行时的 Git HEAD 为 `cc23328ef8d7528846ab40f560647cfc003cddc1`；工作区中既有的未跟踪文件没有被修改或暂存。

## 台账与结论

- `REC-P1-051` 对上述缺失字段核心恢复契约签为 **PASS（窄化范围）**。provider/API 35 和设备链路仍未验证。
- 候选统计：**148 PASS / 0 FAIL / 9 BLOCKED / 45 NOT TESTED / 7 SPEC UNRESOLVED**；P0 **119/0/1/1/5**，P1 **29/0/8/44/1**。历史 209 项仍无映射，整体冻结保持 **HOLD**。
- 最新结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-119.json`，SHA-256 `9D9C9ADF65C655E8C985772B3B0144FDB8CACF3D27A311E002E974A524F15512`；CSV SHA-256 `70204387131BD14FD70A96F43298598B6D365E68BB754DDDFFF3726CAD90E200`。结构校验只检查账本结构、计数和证据引用，不签核产品行为或历史映射（`product_behavior_tested=false`）。
