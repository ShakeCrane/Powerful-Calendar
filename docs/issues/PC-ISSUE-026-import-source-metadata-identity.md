# PC-ISSUE-026 — 导入来源元数据与事件身份分离

日期：2026-10-23

状态：**REC-P0-016 的 PowerStore/Robolectric 回归通过；核心冻结仍 HOLD**

## 验收范围与发现

导入来源和外部 ID 用于识别某来源中的重复输入，但不能代替 PowerCal 自己分配的永久 `eventSeq`。同一外部 ID 来自不同来源、或同一来源中的不同外部 ID，都应形成独立内部身份；同一个 `(source, externalId)` 重导则应幂等。关闭并重开数据库后，来源元数据和身份对应关系必须保留。

现有导入回归只检查相同来源与外部 ID 的重复导入，没有证明不同来源组合会分配不同内部身份。本次没有发现生产行为缺陷。

## 最小变更

新增 `PowerStoreTest.structuredImportSourceMetadataDoesNotBecomeEventIdentity`。测试导入三条记录：相同 external ID 分别来自 `calendar-a` 与 `calendar-b`，以及另一条来自 `calendar-a` 的不同 external ID。断言三条记录具有不同 `eventSeq`，各自来源字段正确；再次导入相同 `(source, externalId)` 返回 `AlreadyImported` 且不增加 identity count。关闭并重开同一隔离 SQLite 后，来源、身份计数和序号分配水位保持一致。

只增加测试代码，没有修改生产逻辑、UI、设计资产或用户业务数据。

## 回归证据

- 提交后定向运行 `:app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.PowerStoreTest.structuredImportSourceMetadataDoesNotBecomeEventIdentity' --offline --rerun-tasks --console=plain`：**1/1 PASS**。代码提交 `da1fdaa4e2a2d102cf440411b6c8dada5dafb3da`。日志 `.local/android-test/reports/freeze-20261023/pc-issue-026-import-source-identity-targeted-postcommit-20261023.log`，SHA-256 `72F52C3B3F0127E7811342BD3857E57B342423ABA4301890162F1224A54D1485`；JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-026-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`，SHA-256 `4367C84BDF5887392D12DE6A51491AA312AE70EDD0C53DB20CA948E00AB517C2`。
- 完整 Gradle 回归 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**389/389 PASS**，55 suites，0 failures/errors/skips；source boundary 与 Debug assemble 通过；Lint 0 errors、55 warnings。包含 388 个已跟踪测试和 1 个受保护的未跟踪测试。日志 `.local/android-test/reports/freeze-20261023/pc-issue-026-import-source-identity-full-gradle-20261023.log`，SHA-256 `43A92556140A6DE74419A8C04DD91ECEDCE1DF534B6BD0DD79C83E31FB9AFE1E`。完整 `PowerStoreTest` XML 为 42/42，SHA-256 `CDAC5F97384C2EA8B80B3394472F3805FB14F85DAFFBD7C2E70140F1B06C2FB1`。
- Debug APK SHA-256：`EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`。使用 Robolectric API 34 与隔离 PowerStore SQLite；未使用模拟器或真机。
- 更新后账本结构校验：209 行、209 个唯一 ID，优先级分区 126/82/1；状态 71 PASS、0 FAIL、9 BLOCKED、123 NOT TESTED、6 SPEC UNRESOLVED。结构 JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-026.json`；账本 SHA-256 `E087AF117905450FFF387150BBCD7801401B97E2FF08BE2BE4B951DD01D2C70D`。该校验只检查台账结构，不测试产品行为。

## 签核与边界

`REC-P0-016` 签候选 PASS。该候选仍为 RECONSTRUCTED，不能映射到缺失的原始 209 项 ID；本回归仅签核导入身份和 SQLite 持久化边界，不构成 UI、Android 设备或真机验收。整体核心冻结继续 HOLD。

- 测试提交：`da1fdaa4e2a2d102cf440411b6c8dada5dafb3da`（`test(冻结): 验证导入来源与事件身份分离`），已推送至 `origin/main`。
- 本报告及台账更新将作为独立中文 Conventional Commit 提交并推送。
