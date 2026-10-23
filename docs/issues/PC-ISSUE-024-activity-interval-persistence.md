# PC-ISSUE-024 — Activity 保留真实开始与结束区间

日期：2026-10-23

状态：**REC-P0-002 的 PowerStore/Robolectric 回归通过；核心冻结仍 HOLD**

## 验收范围与发现

提交 Activity 后必须保留真实开始和结束 instant；`spanEndMillis` 必须对应结束时刻，不能把 Activity 降为单点或误写成 Deadline。SQLite 关闭并重开后，类型和两个时间值仍须一致。

现有测试覆盖了多条 Activity 存储路径，但缺少一条明确验证合法创建提交、真实起止时刻和数据库重开完整往返的独立回归。本次没有发现生产行为缺陷。

## 最小变更

新增 `PowerStoreTest.activityCommitPreservesItsStartAndEndIntervalAcrossRestart`。测试使用分钟对齐的动态时刻创建 Activity，提交后核对 kind、精确 `startMillis`、`endMillis`、`spanEndMillis` 及空 Deadline 字段；关闭并重开同一隔离 SQLite 后重复核对这些值。

只增加测试代码，没有修改生产逻辑、UI、设计资产或用户业务数据。

## 回归证据

- 提交后定向运行 `:app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.PowerStoreTest.activityCommitPreservesItsStartAndEndIntervalAcrossRestart' --offline --rerun-tasks --console=plain`：**1/1 PASS**。代码提交 `83c6a3191cd65188498602700115ca3635a324c9`。日志 `.local/android-test/reports/freeze-20261023/pc-issue-024-activity-interval-targeted-postcommit-20261023.log`，SHA-256 `E78E9DA3B7485D19D0092C527FE5B292FA1A4EDD0A3B5EF24A8EB98E1BB48660`；JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-024-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`，SHA-256 `14567532FF73FF7DB503A3660DA5E3029BDE81D7FA3119DDB3F95412E919A53A`。
- 完整 Gradle 回归 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**387/387 PASS**，55 suites，0 failures/errors/skips；source boundary 与 Debug assemble 通过；Lint 0 errors、55 warnings。包含 386 个已跟踪测试和 1 个受保护的未跟踪测试。日志 `.local/android-test/reports/freeze-20261023/pc-issue-024-activity-interval-full-gradle-20261023.log`，SHA-256 `E75FE87E1E34FAAF4B55211E917EE02172CF7D96750A74430BF8219884A42848`。完整 `PowerStoreTest` XML 为 40/40，SHA-256 `9FEBE905C060F5975A345A4D2498FD9BA419996E1C02F17C3CAE2D1F300CF4DC`。
- Debug APK SHA-256：`EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`。使用 Robolectric API 34 与隔离 PowerStore SQLite；未使用模拟器或真机。

## 签核与边界

`REC-P0-002` 签候选 PASS。该候选仍为 RECONSTRUCTED，不能映射到缺失的原始 209 项 ID；本回归仅签核核心模型和 SQLite 存储边界，不构成 UI、Android 设备或真机验收。整体核心冻结继续 HOLD。

- 测试提交：`83c6a3191cd65188498602700115ca3635a324c9`（`test(冻结): 验证活动起止区间持久化`），已推送至 `origin/main`。
- 本报告及台账更新将作为独立中文 Conventional Commit 提交并推送。
