# PC-ISSUE-098 — 重复批准的核心幂等检查点

日期：2026-10-24

候选：`REC-P1-057`（重建 P1；历史映射仍为 `UNMAPPED`）

状态：**核心提交幂等部分有证据；候选仍 NOT TESTED；整体冻结 HOLD**

## 验收范围

只读审查发现现有 `PowerStoreTest.repeatedCommitReturnsTheOriginalOutcome` 覆盖普通提交的重复调用。本轮没有改生产代码或测试代码。该测试对同一草稿连续调用 `PowerStore.commit`，验证第二次结果为 `duplicate=true`，revision 和 commitId 与第一次相同，且提交历史仍只有一条。

这提供了核心提交语义的独立证据。重建候选指定 API 35 模拟器及免费 provider；本轮没有运行该环境或验证批准动作入口，所以 `REC-P1-057` 仍保持 NOT TESTED。

## 回归验证

- 定向 Robolectric API 34：**1/1 PASS**。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.core.PowerStoreTest.repeatedCommitReturnsTheOriginalOutcome --offline --rerun-tasks --console=plain`。日志 `.local/android-test/reports/freeze-20261024/pc-p1-057-repeat-approval-targeted-postcommit.log`，SHA-256 `C75233621686E0697406F270C63D5F66CCF5EFF63DEE08B6D02A648633E3466F`；JUnit XML `.local/android-test/reports/freeze-20261024/pc-p1-057-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`，SHA-256 `113FFEBB3CDB278512A9A410A7785CA77D89EA7EEC21529F83947F764C013C71`。
- 当前代码基线的完整 Gradle 回归：**451/451 across 57 suites**，0 failures/errors/skips；Lint 0 errors / 58 warnings。完整日志 `.local/android-test/reports/freeze-20261024/pc-p1-056-core-persistence-full-gradle-20261024.log`，SHA-256 `98810B840B70FA446CBDF8C1821353DC720CD948A4637C110ADD7D367D14847C`；`PowerStoreTest` XML SHA-256 `516A9CCDF77D112FA7C97B9A971DD69F8DD76CBDF4DA91511CFFECEC5BF5B1A7`。此全量回归包含上述重复提交测试。

环境没有 `adb`；API 35 模拟器和设备行为未测。没有修改 UI。

结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-098.json`，SHA-256 `FD48462009013801EACEF7CE707CD0D5E4D05D908F2028813BB583211AC7733E`；台账 SHA-256 `09D00A90C6CF4F49E47B5AADA8C213E12F1ABCAA877A9F7547F730183C06A8E2`。结构校验仅检查清单行结构、唯一 ID、优先级分区和状态计数，不测试产品行为或历史用例映射。整体仍为 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED。

## Git

- 本报告、候选台账和冻结摘要单独提交并推送；核心代码保持在提交 `0aa6fa50a00925246aff7dd13822ad1ef15ece4f`。
