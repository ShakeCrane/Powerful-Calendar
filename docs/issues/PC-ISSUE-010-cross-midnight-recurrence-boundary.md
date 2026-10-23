# PC-ISSUE-010 — 跨午夜重复活动的日期归属与持续时长

日期：2026-10-23（北京时间）

优先级：P0（重建候选 REC-P0-072 / REC-P0-073）
状态：**核心模型回归 PASS；核心冻结整体 HOLD**

## 需求与审计结果

冻结合同 `docs/CORE_CONTRACT_FREEZE_2026-10-21.md` §7 规定：跨午夜活动的重复有效日期由实例开始日期决定，结束时间不得被日期边界截断。

只读审查 `RecurrenceEngine.occurrences` 后确认：重复成员资格通过原始槽位 key 的本地日期筛选；Activity 实例结束时间由原记录的真实持续时长推导。没有发现生产逻辑缺陷。已有跨午夜测试验证了窗口 carry-in，但未直接锁定有效日期上下界及真实持续时长。本 Issue 补足这两个核心模型回归断言，没有修改生产代码、UI、数据库或用户数据，也没有把视觉泳道行为作为时间模型证据。

## 定向回归

命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.powerfulcalendar.core.OccurrenceWindowTest" --offline --rerun-tasks --console=plain
```

结果：**19 passed / 0 failures / 0 errors / 0 skipped**。

- `crossMidnightActivityRecurrenceBoundsUseTheOriginalStartDate`：查询 2026-10-21 时，`activeUntilDate=2026-10-20` 保留从 20 日 23:30 开始且仍在进行的实例，并排除 21 日开始的实例；`activeFromDate=2026-10-21` 则排除前一日 carry-in，只返回 21 日开始的实例。
- `crossMidnightActivityRecurrencePreservesItsTrueDuration`：每日 23:30–次日 01:15 的两实例均保留 105 分钟时长，前一实例的结束时刻仍为 01:15。

定向日志：`.local/android-test/reports/freeze-20261023/pc-issue-010-cross-midnight-targeted-final-20261023.log`，SHA-256 `8CF359ED5B02675EF95C78DC74B5A7A12A453F7BB676F4E7BC60AAAF362972E2`。JUnit XML：`.local/android-test/reports/freeze-20261023/pc-issue-010-targeted-junit/TEST-dev.powerfulcalendar.core.OccurrenceWindowTest.xml`，SHA-256 `C416553CA179F84969ABCE3361071197489C7C12F9F69605E710ABFECCACC778`。

## 完整回归

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

结果：**BUILD SUCCESSFUL；377 tests passed / 0 failures / 0 errors / 0 skipped，54 个 JUnit suites；Lint 0 errors / 55 warnings。** 该工作树结果包含 376 个已跟踪测试，以及一个受保护的未跟踪 `DraftDeckFeedbackTest.kt`；该文件未修改、未暂存、未提交。

完整日志：`.local/android-test/reports/freeze-20261023/pc-issue-010-cross-midnight-full-gradle-20261023.log`，SHA-256 `C155D9A2E05E2C68416AA4E0C32C8F500F20970688A5C8A8699ECAD02FD903A3`。54 个 JUnit XML 已保存在 `.local/android-test/reports/freeze-20261023/pc-issue-010-full-junit/`。Debug APK SHA-256：`63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`。

## 候选签核与限制

- `REC-P0-072`：PASS。重复实例的有效日期按原始开始日期判定，跨午夜 carry-in 不改变归属。
- `REC-P0-073`：PASS。重复 Activity 保留真实 105 分钟时长，未按日期边界截断。
- 本 Issue 只签核 JVM 时间模型合同；没有 Android 设备测试，也不映射缺失的历史 209 项原始 ID。核心冻结整体继续 HOLD。
- 更新后候选状态为 **55 PASS / 0 FAIL / 9 BLOCKED / 139 NOT TESTED / 6 SPEC UNRESOLVED**；P0 为 36 PASS、85 NOT TESTED。台账 SHA-256 `E5136BA2C2D6A2BDC7F44B30DEA6C18FFF81CA9FFB3D63692C3BB57778218D3C`；结构验证 artifact `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-010.json` 仅验证 CSV 结构与分区，不验证产品行为或历史映射。

## Git

- `ee9913b953a1165b923cb1f2238755580baeaeb3` — `测试(冻结): 覆盖跨午夜重复活动边界`（已推送）
- 本报告、逐项台账、冻结判定与交接状态另行原子提交并推送。
- 受保护的 `design/`、`DraftDeckFeedbackTest.kt` 与 `PC-OBS-005` 未修改、未暂存。

## 后续

继续审计仍可在核心模型范围内验证的冻结候选；未实现的待办查询和页面行为不会凭时间模型测试签核。
