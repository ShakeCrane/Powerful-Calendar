# PC-ISSUE-CORE-ICS-IMPORT-CHECKPOINT-API

- **日期：** 2026-11-02
- **状态：** `PASS`（Import/Export API 与 JVM/build 范围）；设备 parity `NOT TESTED`
- **阶段：** PHASE 1 — Establish Core Feature API
- **代码提交：** `1f8a415cbb64f2d22c2b0ae1cf1c11dfa9dd9975`
- **基线：** `223fd3a22da6ebe9dde8b3a05d0e6e31e77d6453`
- **分支：** `codex/core-feature-api-event`

## 问题与结论

ICS parsing、去重与草稿创建已通过 `ImportExportFeatureApiV1`，但 `PowerCalApp` 仍直接读写 `ImportCheckpoint` SharedPreferences，令恢复收据成为遗漏的 Host persistence 路径。

新增平台无关 DTO `IcsImportCheckpointV1` 和按指纹查询/同步记录方法。`PowerCalApp` 现在只调用 Import/Export API；Android adapter 调用原有 `ImportCheckpoint` 实现。偏好名称、JSON 表示、SHA-256 指纹校验、同一指纹覆盖、最多保留 12 项以及同步 `commit()` 全部保留。收据仅包含 fingerprint、draft IDs、problem count。API 不增加 SQLite schema、备份格式或第二套存储。

恢复收据仍只是提示。Host 对 receipt 中的草稿 ID 调用 `existingDraftIds`，并重新对每个当前候选执行正式记录/pending CREATE 草稿去重。收据不会授权跳过已经丢失或丢弃的草稿，也不会发布正式事件。

## 预期 / 实际

- **预期：** Native 与 Web ICS import 共用 Import/Export Feature API 查写恢复提示；写入在返回前完成持久化；当前 draft 存在性和候选去重仍由现有 API/Store 规则复核。
- **实际：** `PowerCalApp` 已无 `ImportCheckpoint` 直接调用；adapter 通过旧实现同步提交。API 适配器重建后可查询更新后的收据；contextless adapter 查询返回空，写入明确失败，不会静默丢失收据。集成回归证明 Clear ICS 文件入口和 AI 附件 ICS 重试保留相同两张 reviewable 草稿，正式事件、提交数及 identity/sequence 不变。

## 验证证据

聚焦命令：

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.api.v1.ImportExportFeatureApiV1Test' --tests 'dev.powerfulcalendar.ui.v2.WebAiAttachmentFlowIntegrationTest' --tests 'dev.powerfulcalendar.interop.ImportCheckpointTest' --offline --rerun-tasks --max-workers=1 --console=plain
```

结果 **12/12 PASS，3 XML suites，0 failures/errors/skips**。日志 `.local/android-test/reports/phase1-ics-checkpoint-api-20261102/focused.log`，SHA-256 `EA5B990A6EED635B3EACF1887048A7F73483D64B50C959DF46A9F48FC4E46271`。

主要回归：

- `ImportExportFeatureApiV1Test#icsImportCheckpointIsDurableAndUpdatedThroughFeatureApi` 验证同步可读、同指纹更新及 adapter 重建后的持久性；`#contextlessIcsCheckpointReadIsEmptyAndWriteFailsClosed` 验证不可用存储时不静默成功。
- `WebAiAttachmentFlowIntegrationTest#clearMonthFileImportAndAiAttachmentShareLocalIcsImportAndIdempotence` 验证 Host 两入口共用 API receipt，重试不复制草稿或提交，并不触碰正式记录。
- `ImportCheckpointTest#receiptSurvivesReloadAndUpdatesWithoutDuplicatingRuns` 验证底层现有同指纹覆盖规则。

Focused JUnit XML SHA-256：`ImportExportFeatureApiV1Test` `7893054DB92685F3992D9CB5A556A0C777CF33B9FC277902ACD77A54B75A1534`；`WebAiAttachmentFlowIntegrationTest` `F98030F56F6D3BAC5336207E496325A7C6A0DBF3E67E0FA977D3B86799BCC438`；`ImportCheckpointTest` `14E208D6F2C0B1AC56C2114B15FC2DCEE04A3503DF8E1FA508D01DF2B14FC7E6`。

完整门禁命令：

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain
```

第一次完整尝试：**884/885 tests passed，1 failure**，失败仅为已有 `WebAiProposalReviewIntegrationTest#memoPagingContinuesTheSortedProjectionWithoutStoreWrites` 的 Compose timeout。报错 state 包含全部 251 条 memo，但 `memoTruncated` 尚未更新；该方法单独运行 **1/1 PASS**。初次失败日志 `.local/android-test/reports/phase1-ics-checkpoint-api-20261102/full-gate.log` SHA-256 `0C37AC98E4755A5E32ED547E64487B9947776498F1F09E1AE5C2D0053D20DD6A`；isolated log SHA-256 `F24E9E449DE8918F24CF2818D4B38E524EB4A9354B149A036408100E16488A29`。

强制串行重跑结果 `BUILD SUCCESSFUL`：**885/885 JVM tests，138 XML suites，0 failures/errors/skips，154 actionable tasks**；`verifySkinSourceBoundary`、`verifyNativeSkinBoundary`、Debug APK、lint（0 errors / 54 warnings）、AndroidTest Kotlin 编译均 PASS，`git diff --check` PASS。重跑日志 `.local/android-test/reports/phase1-ics-checkpoint-api-20261102/full-gate-rerun.log` SHA-256 `DBAA2382DE9570F36694218EF9CE0F4A0AF1155AF11FB75E2AA676E62848E24A`。Debug APK **67,011,182 bytes**，SHA-256 `2BD62FC3FD652093A5C948D4A7E7D46F35A6BE88FD4190D4941606B39AEC5237`。

环境为 Windows PowerShell、本地 SDK `E:\android-sdk`、Gradle `--offline`、`--max-workers=1`。ADB 无已连接设备，SDK Emulator 有可执行文件但 `emulator -list-avds` 为空；AndroidTest 仅编译，未在设备执行。生产 WebView-to-Host、SAF/provider 和 OEM 行为、Native/Clear runtime parity 均为 **NOT TESTED**。

## Phase 状态与后续

本报告只关闭 ICS recovery receipt 的 API 路由缺口。它不代表 PHASE 1 全部退出，不改变任何 acceptance row，也不提供 release QA、device parity 或 skin cutover sign-off。下一步对 Draft/Review 与 AI Proposal 的 process-local request replay 做有界契约/调用路径盘点，区分 Store item receipts 与 API request receipt 的保证；继续保持 Native recovery/fallback 可用。
