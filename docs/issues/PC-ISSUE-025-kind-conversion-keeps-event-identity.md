# PC-ISSUE-025 — 类型转换保留永久事件身份

日期：2026-10-23

状态：**REC-P0-020 的 PowerStore/Robolectric 回归通过；核心冻结仍 HOLD**

## 验收范围与发现

将已提交的 ALARM 转换为 ACTIVITY 时，转换必须继续使用原来的 `eventSeq`，不能创建第二个正式对象或消耗新的身份序号。若目标类型需要结束时刻，必须显式补齐该字段。数据库关闭并重开后，事件身份与转换结果仍须保持。

已有测试分别覆盖类型转换的必填字段和永久身份概念，但没有一条将两者贯通到真实 SQLite 提交及重启。本次没有发现生产行为缺陷。

## 最小变更

新增 `PowerStoreTest.convertingAlarmToActivityKeepsEventIdentityAcrossRestart`。测试先提交一个 ALARM，使用 `TemporalConversion` 转为 ACTIVITY 并确认结束时刻仍是必填项；补入明确结束时刻后通过 `DraftOperation.CONVERT` 提交。测试检查转换前后 `eventSeq` 相同、revision 仅递增一次、`identityCount` 和 `nextSequencePreview` 不变，并在关闭/重开数据库后复核。

只增加测试代码，没有修改生产逻辑、UI、设计资产或用户业务数据。

## 回归证据

- 提交后定向运行 `:app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.PowerStoreTest.convertingAlarmToActivityKeepsEventIdentityAcrossRestart' --offline --rerun-tasks --console=plain`：**1/1 PASS**。代码提交 `1317340c8b503e44374378d00365e4d1326a3457`。日志 `.local/android-test/reports/freeze-20261023/pc-issue-025-kind-conversion-targeted-postcommit-20261023.log`，SHA-256 `8C96D3E0F8DAECD520868080DCEBE28E0B188FE60A2CE0C560B0E399E36901A3`；JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-025-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`，SHA-256 `53F9DD28E683C07709881480F7BEEEBCE922D0E2AAFE900ABCD9ACDAC6AF8CA7`。
- 完整 Gradle 回归 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**388/388 PASS**，55 suites，0 failures/errors/skips；source boundary 与 Debug assemble 通过；Lint 0 errors、55 warnings。包含 387 个已跟踪测试和 1 个受保护的未跟踪测试。日志 `.local/android-test/reports/freeze-20261023/pc-issue-025-kind-conversion-full-gradle-20261023.log`，SHA-256 `DAEDC1207E6F7660AA482FFD2FC4F40DEF3A72DBD7820A02F6E29ACDE02FE6B7`。完整 `PowerStoreTest` XML 为 41/41，SHA-256 `1CDFB12601E8250548ECEC12ABEC3A7F4A6B62E8D3A0ECAEAA7BB3BE854F8783`。
- Debug APK SHA-256：`EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`。使用 Robolectric API 34 与隔离 PowerStore SQLite；未使用模拟器或真机。
- 更新后账本结构校验：209 行、209 个唯一 ID，优先级分区 126/82/1；状态 70 PASS、0 FAIL、9 BLOCKED、124 NOT TESTED、6 SPEC UNRESOLVED。结构 JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-025.json`；账本 SHA-256 `35DF66D44D751D4F259DF2C3A9C433D30A13E8691B768DE435A87FC19F807EEC`。该校验只检查台账结构，不测试产品行为。

## 签核与边界

`REC-P0-020` 签候选 PASS。该候选仍为 RECONSTRUCTED，不能映射到缺失的原始 209 项 ID；本回归仅签核核心模型和 SQLite 身份边界，不构成 UI、Android 设备或真机验收。整体核心冻结继续 HOLD。

- 测试提交：`1317340c8b503e44374378d00365e4d1326a3457`（`test(冻结): 验证类型转换保留事件身份`），已推送至 `origin/main`。
- 本报告及台账更新将作为独立中文 Conventional Commit 提交并推送。
