# PC-ISSUE-016 — Alarm 单点时间语义

日期：2026-10-23

状态：**纯时间模型候选回归通过；历史 209 项映射与整体冻结仍为 HOLD**

## 结论

只读审查确认 `DraftForm.build` 对 `ALARM` 只写入 `startMillis`，`TemporalRecord` 的 Alarm 跨度终点也等于开始时刻。此前没有一条直接测试覆盖“即使表单残留 `endText`，保存后也没有结束时刻”这一冻结合同。本 Issue 补上该回归，没有发现或修改生产逻辑。

## 候选验收项

| 候选项 | 测试 | 结果 |
| --- | --- | --- |
| `REC-P0-001` | `TemporalProtocolTest.alarmBuildStoresOnlyItsStartPoint` | PASS：带有多余 `endText` 的 Alarm 表单构建后仍保持开始时刻，`endMillis == null`、`spanEndMillis() == startMillis`，并通过记录校验。 |

这是 `TemporalModels`/`DraftForm` 的纯 JVM 模型检查；不涉及 UI、Android 设备、用户数据库或提醒调度。候选仍为 `RECONSTRUCTED` 且 `UNMAPPED`，本签核不代表原始历史用例 ID 已找到。

## 回归证据

- 提交后定向复跑：`TemporalProtocolTest` **35/35 PASS**，0 failures/errors/skips。日志：`.local/android-test/reports/freeze-20261023/pc-issue-016-alarm-point-targeted-final-20261023.log`，SHA-256 `7A562FD523F64B1E68F4081124567CFD83566D460F7E158A923C663CC0998ADA`；JUnit XML SHA-256 `F16B3B3C65CF844F638DBED22A2E166EC565DF184306215ED8C831D507489740`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。结果 BUILD SUCCESSFUL；**383/383 PASS**，55 个 suite，0 failures/errors/skips；Lint 0 errors、55 warnings。日志：`.local/android-test/reports/freeze-20261023/pc-issue-016-alarm-point-full-gradle-20261023.log`，SHA-256 `BF1E98E0B93766EA3C6949A7FD6C9294D29E3ECF418F199464D1C308E7109091`。全量 JUnit XML 归档于 `.local/android-test/reports/freeze-20261023/pc-issue-016-full-junit/`；`TemporalProtocolTest` 全量 XML SHA-256 `5D6BF70172CFE3D3EBCCE1C8BF1487BA681B3C87679A31EEF90788D3D513524D`。
- 全量 Gradle 运行在代码提交前的基线 `93b1f84fae7dd949ae79382640c94295beb5bd1d` 上，唯一未提交源码是本报告所述测试；该文件随后以 `48c4bf5e63f6cfdbe471a7c84720cf754965d975` 提交。提交后再次运行定向测试，确认已推送代码通过。
- 完整工作树测试数包含 382 个已跟踪测试和 1 个受保护的未跟踪测试 `DraftDeckFeedbackTest.kt`。该文件未修改、未暂存；不能把 383 描述为干净提交树中的测试数。
- Debug APK SHA-256：`63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`。

## 变更与提交

- 唯一代码变更：在 `app/src/test/java/dev/powerfulcalendar/core/TemporalProtocolTest.kt` 增加一个纯模型测试；生产代码、UI 与业务数据均未改动。
- 测试提交：`48c4bf5e63f6cfdbe471a7c84720cf754965d975`（`测试(冻结): 验证闹钟仅保存开始时刻`），已推送到 `origin/main`。
- 本 Issue 报告及冻结台账更新将作为独立文档提交推送。
