# PC-ISSUE-CORE-IMPORT-EXPORT-API-V1

- **日期：** 2026-11-01
- **状态：** `PASS`（Import/Export Feature API v1 代码与 JVM/Host 自动化范围）
- **阶段：** PHASE 1 — Establish Core Feature API
- **代码提交：** `4803a75f99f1e1737225d81dbbdf8f3584b890b4`
- **基线：** `af541cfdd7c56a2ff68c8a66fbec410b07ca0505`
- **分支：** `codex/core-feature-api-event`

## 结论

新增纯 Kotlin `ImportExportFeatureApiV1`，并在 `PowerCalCoreFeatureApiV1` 中绑定唯一 Store adapter。Native 与 Web Host 现在通过该 API 处理 ICS 本地解析与草稿创建、结构化 Calendar 导入批次、Activity ICS 导出、原生备份生成与回读校验，以及备份恢复。写入和验证仍委托现有 `DraftReviewFeatureApiV1`、`PowerStore`、`BackupEnvelope` 和 `IcsCodec`，未实现第二套存储事务或格式。

本报告的 `PASS` 只覆盖 API adapter、Robolectric/Host 路由、所选 JVM 回归和构建门禁。Android instrumentation 未执行；真实生产 WebView-to-Host、Android SAF/provider、物理设备和 OEM 行为仍为 `NOT TESTED`。既有 S0-ST/SEP 验收项及 parity 状态没有因此变更，也没有把它们标记为 PASS。

**预期 / 实际：** 预期 Native 与 Web Host 共用同一版本化入口，且既有草稿审核、source-link 去重、ICS 损失披露、备份校验和空库恢复约束继续生效。实际：API/Host 回归显示 ICS 只产生待审草稿、结构化重试命中同一 source link、ICS 仍只导出 Activity、备份读回/恢复使用原有验证逻辑；25 个聚焦用例及完整门禁全部通过。

## 契约与实现

- `:core-api` 新增 `ImportExportFeatureApiV1` 与版本化 DTO；不包含 Android、Store、Core 实体或 JSON 库类型。备份 JSON 作为 Host 专用的 opaque UTF-8 文档传输。
- ICS 解析仍由本地 `IcsImportPlanner` 完成。API 返回候选表单和待处理证据；去重检查正式记录、pending CREATE 草稿和同批先前候选。Host 仍将候选写为 Draft/Review 草稿，写入调用委托 `draftReview.createDraft`。`ImportCheckpoint` 仍只是 SharedPreferences 恢复提示，不授权跳过缺失草稿。未解决证据不由该 API 自动发给 AI。
- System Calendar provider 查询和 `READ_CALENDAR` 权限仍归 Android Host。每条记录经 API 的 begin/import/finish 批次方法委托 `PowerStore.beginMigration`、`importStructured`、`finishMigration`；保留 source-link 去重、migration 计数和已存在的导入校验。新正式记录向共享 Event API 发布 `FORMAL_CREATED`，重复源链接不重复发布。
- ICS 导出通过 adapter 获取 Store 当前 Activity 快照，并调用既有 `IcsCodec.exportActivities`。格式仍只包含 Activity；启用状态、提醒、其他事件类型和完成状态的现有损失披露保持不变。
- 备份导出和恢复分别委托 `PowerStore.exportBackup` 与 `PowerStore.restoreBackup`；回读校验委托 `BackupEnvelope.verifyReadback`。入口继续限制 64 MiB UTF-8 数据。SHA/引用验证、只恢复到空库、事务原子性以及 flat v2 显式同意门槛保持原状。恢复成功发布 `STORE_RESTORED`；文件 URI、SAF 读写、提醒重建和 UI 刷新仍由 Host 执行。
- 未改变数据库 schema、BackupEnvelope/ICS 格式、Core 冻结语义或 Native fallback；未触碰 acceptance CSV、设备、AVD、用户数据库或凭据。

## 验证证据

聚焦命令：

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.api.v1.ImportExportFeatureApiV1Test' --tests 'dev.powerfulcalendar.ui.v2.SystemCalendarImportTest' --tests 'dev.powerfulcalendar.ui.v2.SemanticIcsExportIntegrationTest' --tests 'dev.powerfulcalendar.ui.v2.SemanticBackupExportIntegrationTest' --tests 'dev.powerfulcalendar.ui.v2.BackupRestoreHostTest' --tests 'dev.powerfulcalendar.ui.v2.CoreApiAdapterTest' --offline --rerun-tasks --max-workers=1 --console=plain
```

结果：**25/25 PASS，0 failures/errors/skips**，6 个 XML suites。报告文件 `.local/android-test/reports/import-export-feature-api-20261101/focused.log`，9,606 bytes，SHA-256 `23C4AC3D74CC97AF4B6FE7DE7570EE620527F5B79158B91ACEDC2CEC40C3F096`。

主要 test IDs（均通过）：

- 新 API：`ImportExportFeatureApiV1Test#icsImportUsesReviewDraftApiAndDuplicateQuery`、`#structuredImportPublishesOnceAndUsesPersistentSourceLink`、`#backupReadbackAndRestoreUseTheSharedApi`。
- 结构化导入：`SystemCalendarImportTest#deniedPermissionDoesNotQueryOrWrite`、`#systemImportClosesAndRetriesIdempotently`、`#rejectedItemsArePersistedAsWarningsWithoutCalendarWrites`、`#cancellationAtBatchCreationReturnStillLeavesIdForFinalization`。
- ICS 导出：`SemanticIcsExportIntegrationTest#clearHostExportsOnlyActivitiesAndDisclosesLossWithoutMutatingStore`、`#cancelingClearIcsExportLeavesStoreAndOutputUntouched`、`#clearIcsExportOutputFailureIsReportedAndStoreRemainsUnchanged`。
- 备份导出：`SemanticBackupExportIntegrationTest#verifiedBackupExportRoundTrips`、`#unavailableReadbackIsClearlyReportedAsUnverified`、`#readbackWatermarkMismatchIsReportedAsFailure`、`#outputFailureIsReportedWithoutClaimingBackupSuccess`、`#cancelingClearBackupExportDoesNotWriteOrMutateStore`。
- 备份恢复与 API 共用：`BackupRestoreHostTest` 9/9（含 `clearRestore`、`nonEmptyTarget`、`invalidHash`、`confirmedLegacyFlatV2RestoresUnderANewLineage`、picker cancel 和 busy-gate cases）；`CoreApiAdapterTest#nativeAndWebShareSameCoreApi`。

完整离线门禁：

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain
```

结果：`BUILD SUCCESSFUL`；JVM **834/834**，132 XML suites，0 failures/errors/skips；`verifySkinSourceBoundary`、`verifyNativeSkinBoundary`、Debug APK、`lintDebug`（0 errors / 54 warnings）和 AndroidTest Kotlin 编译均 PASS。AndroidTest APK 编译不代表在设备上执行。`git diff --check` PASS。

环境：Windows PowerShell，仓库本地 SDK `E:\android-sdk`，Gradle `--offline`，forced serial `--max-workers=1`；Robolectric tests use their declared SDK 34/35 configurations. 本轮未执行设备 instrumentation，故没有设备截图或设备日志。

- Debug APK：66,781,806 bytes，SHA-256 `6CDD38126D93331D566824F2D03A9293BA2C9497FD21A46F5FEB4FA9B4DF4AD0`。
- 完整门禁日志：`.local/android-test/reports/import-export-feature-api-20261101/full-gate.log`，14,958 bytes，SHA-256 `0E2EE3C8E65C85DB810CA2576173BB3B17762E989D80055E6848F24CA6D6B5D5`。
- 聚焦日志及完整门禁日志都保存在忽略目录 `.local/android-test/reports/import-export-feature-api-20261101/`。

## 失败尝试与修正

初次运行未设置 `ANDROID_HOME`，Gradle 在测试启动前因找不到 SDK 退出；随后使用已核对的仓库 SDK `E:\android-sdk` 重跑。首轮代码编译还发现 nullable draft ID 被错误地作为集合加法处理；改为先提取非空 ID 再追加。最终聚焦与完整串行门禁均通过。这两次尝试没有产生测试 PASS 证据。

## 未验证范围与后续

Android instrumentation、打包 WebView 与生产 Host 的设备连通、DocumentsUI/SAF 实际文件提供方、跨 OEM 行为和第三方日历互操作均为 `NOT TESTED`。这项 API 工作不关闭现有 Calendar 导入、ICS、备份恢复或皮肤 parity 的设备验收，也不满足 PHASE 1 退出条件。

**下一步：** 盘点剩余 Settings/Data 路径并定义相应 API；继续 AI batch 与 AI draft-generation API；移除 runtime Skin projection 中剩余 Core 实体类型。Native recovery/fallback 保持可用。
