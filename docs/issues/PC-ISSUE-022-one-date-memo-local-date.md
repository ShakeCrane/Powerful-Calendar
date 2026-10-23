# PC-ISSUE-022 — 一日 Memo 保留本地日期语义

日期：2026-10-23

状态：**REC-P0-007 的 PowerStore/Robolectric 回归通过；核心冻结仍 HOLD**

## 验收范围

提交一日 Memo 时，日期必须保留为 Memo 的本地日期语义，不能转换为正式事件起止时间或 Deadline。关闭并重新打开 SQLite 后，日期和待办摘要仍应保留。

## 最小变更

在 `PowerStoreTest` 新增 `oneDateMemoRemainsOneLocalDateAcrossRestart`。测试创建 `2026-10-24` 的一日 Memo 并提交，断言类型仍为 `MEMO`、`memoStartDate` 保留该日期，`memoEndDate`、`startMillis`、`endMillis`、`deadlineDate` 与 `deadlineMillis` 均为空，摘要为 `2026-10-24 · 待办`。随后关闭并重开同一隔离 SQLite 数据库，再验证相同字段与摘要。

只增加测试代码；没有修改生产逻辑、UI、设计资产或用户业务数据。测试有意不要求 `anchorMillis` 为空：模型可将 Memo 日期映射为时间轴展示锚点，但不能把它持久化为正式事件时间。

## 回归证据

- 提交后定向运行 `:app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.PowerStoreTest.oneDateMemoRemainsOneLocalDateAcrossRestart' --offline --rerun-tasks --console=plain`：**1/1 PASS**。代码基线 `bc8c1fb8d170abbce2df029133e3c1d44e658317`。日志 `.local/android-test/reports/freeze-20261023/pc-issue-022-one-date-memo-targeted-postcommit-20261023.log`，SHA-256 `752716BCD7F5C081AE20DDAFD703E9873E2B781A0568EFA2B4C7A529DF8BE2EA`；JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-022-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`，SHA-256 `F80D899F93000F712986536BBB9713D70542113449BDEBB7A087B6D820D18D13`。
- 完整 Gradle 回归 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**386/386 PASS**，55 suites，0 failures/errors/skips；source boundary 与 Debug assemble 通过；Lint 0 errors、55 warnings。日志 `.local/android-test/reports/freeze-20261023/pc-issue-022-one-date-memo-full-gradle-20261023.log`，SHA-256 `B7AFD09CEB3BEDB0A94B3AF0E11DF2ED7076854B06A8D5AE8AAC3EED4BF1BDB7`。完整 `PowerStoreTest` XML 为 39/39，SHA-256 `B3E50D8B5F08C50EB2AF6CBB3E4F90166B34546B33C9D25C918501803BCAABFF`。
- 测试 APK SHA-256：`EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`。模拟环境为 Robolectric API 34 与隔离 PowerStore SQLite；没有使用 Android 模拟器或真机。

## 签核与边界

`REC-P0-007` 签候选 PASS。它仍是 RECONSTRUCTED 候选，不能映射到缺失的原始 209 项 ID。该回归证明存储及模型字段跨重启保持，不构成 UI、设备或真机签核；核心冻结继续 HOLD。

- 测试提交：`bc8c1fb8d170abbce2df029133e3c1d44e658317`（`test(冻结): 验证一日备忘保持本地日期`），已推送至 `origin/main`。
- 本报告与候选台账更新将单独使用中文 Conventional Commit 提交并推送。
