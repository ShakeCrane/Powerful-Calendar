# PC-ISSUE-018 — 即时时间 Deadline 单点语义

日期：2026-10-23

状态：**纯时间模型候选回归通过；历史 209 项映射与整体冻结仍为 HOLD**

## 结论

只读审查确认 `DraftForm.build` 对即时 Deadline 使用 `deadlineMillis`，不创建 Activity 式起止区间。现有覆盖包含日期精度和非法混合字段，但未直接验证带残留 `startText` 的即时 Deadline 构建结果。本 Issue 增加该纯模型回归，没有发现或修改生产逻辑。

## 候选验收项

| 候选项 | 测试 | 结果 |
| --- | --- | --- |
| `REC-P0-003` | `TemporalProtocolTest.instantDeadlineBuildStoresOnlyItsDeadlinePoint` | PASS：输入包含残留开始文本，构建结果仍只含截止 instant；`startMillis`、`endMillis` 为空，`anchorMillis` 和 `spanEndMillis` 等于截止时刻，记录校验通过。 |

测试只运行纯 JVM 时间模型，不涉及 UI、Android 设备或用户数据库。候选仍标为 `RECONSTRUCTED` 且 `UNMAPPED`，不能替代缺失的历史用例 ID。

## 回归证据

- 提交后定向复跑：`TemporalProtocolTest` **36/36 PASS**，0 failures/errors/skips。日志：`.local/android-test/reports/freeze-20261023/pc-issue-018-deadline-point-targeted-final-20261023.log`，SHA-256 `752716BCD7F5C081AE20DDAFD703E9873E2B781A0568EFA2B4C7A529DF8BE2EA`；JUnit XML SHA-256 `9C2FB18D1F4FC38EB66282927A10F0249E668B148B8A6D48141325EC2CC86799`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。结果 BUILD SUCCESSFUL；**384/384 PASS**，55 个 suite，0 failures/errors/skips；Lint 0 errors、55 warnings。日志：`.local/android-test/reports/freeze-20261023/pc-issue-018-deadline-point-full-gradle-20261023.log`，SHA-256 `FD45C911E23DDB9CFCAB827D6FE9B49EFEBED2373440521BB6FD803747F3FA5B`。全量 JUnit XML 归档于 `.local/android-test/reports/freeze-20261023/pc-issue-018-full-junit/`；`TemporalProtocolTest` 全量 XML SHA-256 `E5FF743EC5BC6DFC2912137028882F9692D50E4D054EFAE2CD67CA2A24E2CD88`。
- 完整 Gradle 回归运行于 `25b9269efd7329a97c2520c1807527c8e55f8c7f`，唯一未提交源码为本报告对应的测试，随后提交为 `ee2e6707b9dc2e1b134f91109250079dedcd85bc`。提交后再次运行定向测试并通过。
- 完整工作树结果含 383 个已跟踪测试和 1 个受保护的未跟踪测试 `DraftDeckFeedbackTest.kt`。该文件未修改、未暂存；不能把 384 描述为干净提交树中的计数。
- Debug APK SHA-256：`63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`。

## 变更与提交

- 唯一代码变更：在 `app/src/test/java/dev/powerfulcalendar/core/TemporalProtocolTest.kt` 增加一个纯模型测试；生产代码、UI 和业务数据均未改动。
- 测试提交：`ee2e6707b9dc2e1b134f91109250079dedcd85bc`（`测试(冻结): 验证即时截止为单点语义`），已推送至 `origin/main`。
- 本 Issue 报告与冻结台账更新将单独提交推送。
