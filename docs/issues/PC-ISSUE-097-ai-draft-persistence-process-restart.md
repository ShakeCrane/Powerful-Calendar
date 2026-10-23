# PC-ISSUE-097 — AI 待审草稿跨 SQLite 重开持久化

日期：2026-10-24

候选：`REC-P1-056`（重建 P1；历史映射仍为 `UNMAPPED`）

状态：**核心持久化部分有证据；候选仍 NOT TESTED；整体冻结 HOLD**

## 验收范围

新增 `PowerStoreTest.parsedAiCreateProposalPersistsAsAnUnpublishedDraftAcrossRestart`。测试用本地结构化 JSON 经 `AiResponseParser` 解析，再由 `AiDraftService` 创建 CREATE 草稿。关闭并重开同一 SQLite 数据库后，验证草稿 ID、eventSeq、版本、操作、表单内容、PENDING 状态及待审队列顺序不变；正式事件和提交历史始终不存在。测试没有调用模型或网络。

这只签核核心解析结果到待审草稿的持久化边界。台账候选要求 API 35 模拟器及免费 provider，并以“选择写入草稿”为动作；本次没有运行该 provider 或显式选择路径，因此不将 `REC-P1-056` 升为 PASS。

## 回归验证

- 定向 Robolectric API 34：**1/1 PASS**。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.core.PowerStoreTest.parsedAiCreateProposalPersistsAsAnUnpublishedDraftAcrossRestart --offline --rerun-tasks --console=plain`。日志 `.local/android-test/reports/freeze-20261024/pc-p1-056-draft-restart-targeted-postcommit.log`，SHA-256 `4DC8DA0976A15409FE2225F2A8FDEAA8372D6A637462287EB6ED590D6FEC9548`；JUnit XML `.local/android-test/reports/freeze-20261024/pc-p1-056-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`，SHA-256 `E5C019C289F1B5ACABFB4E70AE757D527A49F6B97A53BA5AF388E65B62CED851`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**451/451 across 57 suites**，0 failures/errors/skips；Lint 0 errors / 58 warnings。451 项包括 450 个已跟踪测试及一项受保护的未跟踪测试；该受保护文件未修改、未暂存。
- 完整日志 `.local/android-test/reports/freeze-20261024/pc-p1-056-core-persistence-full-gradle-20261024.log`，SHA-256 `98810B840B70FA446CBDF8C1821353DC720CD948A4637C110ADD7D367D14847C`；完整 `PowerStoreTest` XML `.local/android-test/reports/freeze-20261024/pc-p1-056-full-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`，SHA-256 `516A9CCDF77D112FA7C97B9A971DD69F8DD76CBDF4DA91511CFFECEC5BF5B1A7`；Lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256 `D941A9BF701896C7D9E232540856D5854EA9EC8CFD6AEB2AB0AA9CDD8DE72ABB`。

环境中没有 `adb`，没有进行 API 35 模拟器/provider 或真实设备验收。没有修改 UI。

结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-097.json`，SHA-256 `66163C61D912A6B2984023F017ADD825D4E3DC346051253B955A92FEF8269EC3`；台账 SHA-256 `BC18A6B477FF77AC67EEC3F632424D294FC80410BAE18478D5F40EA8142C88A9`。该校验只确认 209 行唯一 ID、126/82/1 优先级分区和状态计数，不测试产品行为，也不映射历史用例。总体仍为 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED。

## Git

- 测试提交：`0aa6fa50a00925246aff7dd13822ad1ef15ece4f` — `test(ai): 验证待审草稿跨重启持久化`，已推送。
- 本证据报告、台账和冻结摘要随后单独提交并推送。
