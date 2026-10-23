# PC-ISSUE-104 — AI 共享上下文是只读投影

日期：2026-10-24

候选：`REC-P0-082`（重建 P0；历史映射仍为 `UNMAPPED`）

状态：**AI 核心投影回归通过；候选仍 NOT TESTED；整体冻结 HOLD**

## 验收范围

PC-ISSUE-089 已验证 SQLite 到 ICS 的只读投影。本次补测 AI 共享上下文：从 PowerStore 提交一条正式 Activity，只把 `store.all()` 的快照提供给 `AiClient`，经本地 HTTP stub 检查请求中的事件数组恰有一项，且该项保留正式 `eventSeq`、`revision`、类型和标题。

响应不生成提案。请求前后比较正式记录、提交历史、提醒意图、身份计数和下一个序号预览，均保持一致；没有新增草稿或第二份事件真相。测试仅使用本地 HTTP stub，没有访问外部 AI provider；只增加测试，没有修改生产代码、UI 或设计资产。

这只签核 AI 核心上下文投影和 Issue 089 的 ICS 核心投影。`REC-P0-082` 仍为 NOT TESTED。

## 验证证据

- 定向 Robolectric API 34：**1/1 PASS**。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.ai.AiChatClientTest.sharedAiContextProjectsOneFormalSnapshotWithoutWritingStore --offline --rerun-tasks --console=plain`。日志 SHA-256 `4DDB857BE80E8E0F69FA877504BCD3A1FC902191D587FAC3F761A01036FBC090`；JUnit XML SHA-256 `B1384AF9CA3967EB2DAA1BC791D7A11C583CD2E0239EB4F84B84F1B8B875B97C`。
- 完整 Gradle：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`，**456/456 across 57 suites**，0 failures/errors/skips；Lint 0 errors / 58 warnings。完整日志 SHA-256 `406C9EAD944A331E149441BA24136A84499EC863ADFE90343098DEEB549A634D`；完整 `AiChatClientTest` XML（7 tests）SHA-256 `6F715533D57120BAFA67727E77E149005045164211F7130B22DCF2B361E7B452`；Lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256 `138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 日志和完整 JUnit XML 集合保存在 `.local/android-test/reports/freeze-20261024/`，全量 JUnit XML 位于 `pc-issue-104-full-junit/`。

## 台账与 Git

- 结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-104.json`，SHA-256 `7D8CB24D683736DA27397053013F14AE29702918992A00956CD968BF3A07FD79`；台账 SHA-256 `440DCE8934F4782FC54A292DA10B1C0E88C4C051F069F7A88EAAA0D772278353`。校验为结构性，`product_behavior_tested=false`。
- 测试提交：`e2f413a74a7a504f3ca493adc5ee3ad69e497f25`（`test(ai): 验证共享上下文投影只读`），已推送。
- UI 投影和 Android SAF/设备路径未覆盖；因此候选仍 NOT TESTED，整体冻结仍 HOLD。
