# PC-ISSUE-009 — 重复实例身份与例外隔离

日期：2026-10-23（北京时间）
优先级：P0
状态：**核心时间模型候选项 PASS；整体核心冻结继续 HOLD**

## 发现与处理

重复规则的原始槽位身份、单次移动、跳过、有效日期边界和例外归属属于纯时间模型合同。本轮审计发现，多项现有 JVM 测试已经覆盖这些行为，但台账没有逐项证据引用；另有两处断言需要更精确地证明“移动的是一个槽位”和“例外只属于一个序列”。

没有发现需要修改生产时间模型的缺陷。本 Issue 只增强测试断言并将精确测试映射到重建候选行；未修改生产代码、UI 或既有事件数据。

## 定向证据

7 个精确 JVM 测试全部通过：

- 有效日期起止边界均包含在内，且不重设循环锚点；旧循环只在请求的两日窗口中投影实例。
- 移动的实例始终保留其原始 `slotKey`；从 2026-08-21 移至 2026-10-20 后，原槽位与自然发生的 2026-10-20 槽位各自出现一次。
- 跳过 2026-10-21 不改变前后循环槽位。
- 对 `eventSeq=1` 的例外不会跳过具有相同日规则的 `eventSeq=2` 实例。
- 横跨纽约春季 DST gap 移动的实例保留原始 key 与已解析的有效 instant；本项只核对实现已有的 Java 时间解析结果，不定义新的 DST gap 产品政策。

定向命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerCalMemoContractTest.recurrenceEffectiveDatesDoNotShiftCycleAnchor" --tests "dev.powerfulcalendar.core.PowerCalMemoContractTest.occurrenceExceptionsAreScopedToTheirOwnSeries" --tests "dev.powerfulcalendar.core.TemporalProtocolTest.anOldRecurringSeriesStillMaterialisesInACurrentWindow" --tests "dev.powerfulcalendar.core.TemporalProtocolTest.skippedOccurrenceDoesNotShiftTheRestOfTheSeries" --tests "dev.powerfulcalendar.core.TemporalProtocolTest.movedOccurrenceKeepsItsOriginalKey" --tests "dev.powerfulcalendar.core.OccurrenceWindowTest.anInstanceMovedFromTwoMonthsAgoIsFoundInTodaysWindow" --tests "dev.powerfulcalendar.core.OccurrenceWindowTest.movingAnInstanceAcrossTheSpringForwardGapKeepsTheEffectiveInstant" --offline --rerun-tasks --console=plain
```

结果：**7 passed / 0 failed / 0 errors / 0 skipped**。Gradle 日志：`.local/android-test/reports/freeze-20261023/pc-issue-009-recurrence-targeted-20261023.log`，SHA-256 `44B4AF96CDD9013F2274BC2F921C4634845FF6F55990AE659C83C85BEB87F2A4`。三个目标类的 JUnit XML 保存在同目录：`pc-issue-009-targeted-TEST-dev.powerfulcalendar.core.OccurrenceWindowTest.xml`（SHA-256 `6D27213110C143EE95EEBD463A734670EED982F2D9F8EF3A02E7B47473103888`，2 项）、`pc-issue-009-targeted-TEST-dev.powerfulcalendar.core.PowerCalMemoContractTest.xml`（SHA-256 `695F0957CAA2254388A5C954FF8FF01B5575FCBB19A7245C253A18C10242B4F9`，2 项）和 `pc-issue-009-targeted-TEST-dev.powerfulcalendar.core.TemporalProtocolTest.xml`（SHA-256 `AEEDF53FA6B608396558F65ABA21D6EFA25FF7C88A27DDCF1838705962C48822`，3 项）。

## 完整回归

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

结果：**BUILD SUCCESSFUL；375 tests passed、0 failures/errors/skips，54 个 JUnit suites；Lint 0 errors / 55 warnings。** 包含 374 个已跟踪测试和 1 个受保护的未跟踪 `DraftDeckFeedbackTest.kt`；该文件未修改、未暂存。完整日志：`.local/android-test/reports/freeze-20261023/pc-issue-009-recurrence-full-gradle-20261023.log`，SHA-256 `0818734F22900F3742E6D4E852F3AD7E1FF442998F154199EA0B4FD8492B90EF`。Debug APK SHA-256：`63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`。

## 候选签核与限制

以下 9 个重建候选项按精确行为证据记为 PASS：`REC-P0-060`、`REC-P0-061`、`REC-P0-062`、`REC-P0-063`、`REC-P0-066`、`REC-P0-067`、`REC-P0-068`、`REC-P0-070`、`REC-P0-071`。DST gap/fold 的完整产品规则仍按 `SPEC UNRESOLVED` 保留；此回归不替用户决定规则。没有 Android 设备验收。

更新后的 ledger SHA-256 为 `37C26957DF9F6D79A2BE11DC64F51BE8A5D85207AF74B27C893F572DEC1FCE58`；结构验证 artifact：`.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-009.json`。结构验证不证明历史映射。候选总状态为 **53 PASS / 0 FAIL / 9 BLOCKED / 141 NOT TESTED / 6 SPEC UNRESOLVED**，因此总体验收继续 **HOLD**。

## Git

- `67648e5b6c4c744034884e408f8835b208306b29` — `测试(冻结): 覆盖重复实例身份与例外隔离`（已推送）
- 本独立报告、台账、冻结判定与交接更新随后单独提交并推送。
