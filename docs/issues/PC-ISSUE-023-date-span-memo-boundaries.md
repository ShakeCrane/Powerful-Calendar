# PC-ISSUE-023 — 日期范围 Memo 保留两个本地日期边界

日期：2026-10-23

状态：**REC-P0-008 的 PowerStore/Robolectric 回归通过；核心冻结仍 HOLD**

## 验收范围与发现

创建 `2026-10-21` 至 `2026-10-23` 的日期范围 Memo 后，必须保留开始与结束两个本地日期；不能生成 Activity 起止时刻或 Deadline 字段。数据库关闭并重开后，Memo 类型和两个日期仍须保持。

现有 `spanningMemoHasOneCompletionInstanceAndKeepsItAcrossRestart` 已检查两个日期及完成状态的持久化，但未明确断言对象类型与正式时间字段为空。这是回归断言缺口；本次没有发现生产行为缺陷。

## 最小变更

在该测试中新增断言：提交和 SQLite 重开后对象均为 `MEMO`，`startMillis`、`endMillis`、`deadlineDate`、`deadlineMillis` 均为空；两个边界日期仍为 `2026-10-21` 和 `2026-10-23`。只修改测试代码，没有改生产逻辑、UI、设计资产或用户业务数据。

## 回归证据

- 提交前定向运行 `:app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.PowerStoreTest.spanningMemoHasOneCompletionInstanceAndKeepsItAcrossRestart' --offline --rerun-tasks --console=plain`：**1/1 PASS**。日志 `.local/android-test/reports/freeze-20261023/pc-issue-023-date-span-memo-targeted-20261023.log`，SHA-256 `8C96D3E0F8DAECD520868080DCEBE28E0B188FE60A2CE0C560B0E399E36901A3`；JUnit XML SHA-256 `9BB59987C6D7D0F21F2694CC795DD4782B4D167D4E7DFA30A15539370BD60C3E`。
- 提交后定向复跑：**1/1 PASS**。日志 `.local/android-test/reports/freeze-20261023/pc-issue-023-date-span-memo-targeted-postcommit-20261023.log`，SHA-256 `BB7C134C692FD704A6D7A253FB10CE5DF189779200A40655BFF5DCCDA1DFF003`；JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-023-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`，SHA-256 `7BF831321D46A40E4841622C9D7416CE18A16D2415979D84FFA0290C5C7DD773`。
- 完整 Gradle 回归 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**386/386 PASS**，55 suites，0 failures/errors/skips；source boundary 与 Debug assemble 通过；Lint 0 errors、55 warnings。日志 `.local/android-test/reports/freeze-20261023/pc-issue-023-date-span-memo-full-gradle-20261023.log`，SHA-256 `A252A36DA48CADF048F9CF615EB1FFFAC80DE8134D2E4DEEAD846C8C1C791F3B`。完整 `PowerStoreTest` XML 为 39/39，SHA-256 `7BF831321D46A40E4841622C9D7416CE18A16D2415979D84FFA0290C5C7DD773`。
- Debug APK SHA-256：`EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`。使用 Robolectric API 34 与隔离 PowerStore SQLite；未使用模拟器或真机。
- 更新后账本结构校验：209 行、209 个唯一 ID，优先级分区 126/82/1；状态 68 PASS、0 FAIL、9 BLOCKED、126 NOT TESTED、6 SPEC UNRESOLVED。结构 JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-023.json`；账本 SHA-256 `96DA1842800E5AA567A5968580813B8731F521E35CAFB6050832D293D4155F0B`。此校验只检查台账结构，不测试产品行为。

## 签核与边界

`REC-P0-008` 签候选 PASS。该候选仍为 RECONSTRUCTED，无法映射到缺失的原始 209 项 ID；本回归仅签核核心模型和 SQLite 字段语义，不构成 UI、Android 设备或真机验收。整体核心冻结继续 HOLD。

- 测试提交：`65f68d29c9afb7f7866ac9c13891f2d696ef8395`（`test(冻结): 验证日期范围备忘边界语义`），已推送至 `origin/main`。
- 本报告及台账更新将作为独立中文 Conventional Commit 提交并推送。
