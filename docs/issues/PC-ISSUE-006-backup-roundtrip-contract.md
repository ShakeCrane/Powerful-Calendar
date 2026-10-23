# PC-ISSUE-006 — 原生备份引用与往返契约证据补齐

日期：2026-10-23（北京时间）
优先级：P0

状态：**核心备份契约定向 PASS；整体冻结继续 HOLD**

## 发现

备份实现已有格式校验、草稿/提交引用校验、水位保护、墓碑序列化、逻辑数据白名单和恢复后重建提醒等路径，但候选验收台账中有多项没有精确对应的测试执行证据。本轮只读对照确认上述校验路径存在；没有发现需要修改生产逻辑的缺陷。本 Issue 补的是逐项回归证据，不把相邻测试或汇总测试数代替用例签核。

## 增补的回归覆盖

新增/签核的 JVM SQLite 测试明确验证：

- 未知备份 `format` 与未知 `backupFormatVersion` 在目标库写入前拒绝。
- 草稿引用不存在身份、提交引用不存在草稿时均在写入前拒绝。
- 低于队列高水位的 `nextOrder` 被拒绝，目标库保持空且分配器不变。
- 导出及恢复保留墓碑的 `retiredMs`、对象 `retired` 状态、可查询的墓碑快照与 `nextSeq` 水位。
- 草稿 `draftId`、`queueOrder`、草稿逻辑内容及修订提交历史往返一致。
- API key 哨兵值、AI 授权数据、旧设备执行历史和调度句柄不进入 Envelope；恢复后凭据保持不变，提醒意图由活动闹钟数据重建。

没有修改生产代码或 UI 文件。物理设备、Android SAF 和 OEM 场景不属于本批测试；相应平台门槛仍保留在冻结 HOLD 中。

## 测试证据

定向命令运行 7 个精确测试：**7 passed / 0 failed / 0 errors / 0 skipped**。

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.unknownBackupFormatIsRejectedBeforeAnyRowIsWritten" --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.unknownFutureBackupFormatVersionIsRejected" --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.danglingDraftAndCommitReferencesAreRejectedBeforeAnyWrite" --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.aLowNextOrderWatermarkIsRejected" --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.roundTripPreservesDraftIdentityQueueOrderAndCommittedRevisionHistory" --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.retiredEventExportPreservesTombstoneAndSequenceWatermark" --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.nativeBackupExcludesApiCredentialsAndSchedulerHandlesThenRebuildsSchedule" --offline --rerun-tasks --console=plain
```

Final targeted log: `.local/android-test/reports/freeze-20261023/pc-issue-006-backup-contract-targeted-final-20261023.log`, SHA-256 `A7E99AF0F314D1129C229014B2CB2127B90E26E3126A8368B02380097B3DEF09`.

The initial targeted run caught a test assertion wording mismatch: the validator reported “引用了不存在的身份” while the new assertion expected “缺少身份”. The rejection itself occurred; the assertion was aligned to the actual error contract and the complete targeted group was rerun successfully. The initial log is retained at `.local/android-test/reports/freeze-20261023/pc-issue-006-backup-contract-targeted-20261023.log`, SHA-256 `8FC682637B4C8614B16C118ADD866A70752602CDE25162621409D6697A4B86F3`.

Full Gradle command:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

Result: **BUILD SUCCESSFUL; 372 tests passed, 0 failures/errors/skips; Debug APK assembled; Lint 0 errors / 55 warnings.** This working-tree run comprised 371 tracked tests and one protected, untracked `DraftDeckFeedbackTest.kt`; the protected test was not modified or staged. Full log: `.local/android-test/reports/freeze-20261023/pc-issue-006-backup-contract-full-gradle-final-20261023.log`, SHA-256 `A77F74970305AB3A4CE82FE49544726092EEBA718401D325A1953384435DCB36`. APK SHA-256: `9677B9816B9028EF5C25EC84025ABA56DDB92BEB5CF36B2197AC5615315BB0AE`.

## Candidate sign-offs and limits

The evidence is individually mapped to reconstructed rows `REC-P0-094`, `095`, `096`, `098`, `099`, `100`, `101`, and `109`. All remain `UNMAPPED` to the unavailable historical 209-case IDs. The structural-only ledger validation reports 209 unique rows and the 126/82/1 priority partition; it does not prove historical equivalence or behavior beyond the cited tests. Validation artifact: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-006.json`; ledger SHA-256 `ABD313A4A35350347A864D2B472E2A540C461B9D39982DD691A058C11E9F05A9`.

Candidate totals now stand at 38 PASS, 0 FAIL, 9 BLOCKED, 156 NOT TESTED, and 6 SPEC UNRESOLVED. No original historical ID is signed off. Therefore this Issue closes only its eight reconstructed core backup checks; PC-QA-GATE-001 and the overall core freeze remain **PARTIAL / HOLD**.

## Git

- Test commit: `5df9e00b04ad50dc5c4e12154cf117cc4a159a23` — `测试(冻结): 补齐备份引用与往返契约覆盖`; pushed to `origin/main`.
- The independent report and freeze-ledger updates are committed separately after the test commit.
