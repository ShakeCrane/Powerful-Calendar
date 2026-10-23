# PC-ISSUE-108 — 整组重复编辑核心范围签核

日期：2026-10-24

候选：`REC-P0-080`（重建候选，历史 209 项映射仍为 `UNMAPPED`）

状态：**候选定义的核心持久化范围 PASS；整体冻结仍 HOLD**

## 验收边界与判定

台账为该候选声明的环境是 Robolectric API 34、隔离的 PowerStore SQLite、`EditSession` 与同库重开，并明确不包含界面或设备。原动作“Select whole series”会让人理解为需要验证范围选择界面，与该核心环境不一致；因此将这个重建候选的动作收窄为“Edit whole recurring rule through EditSession”，预期仍是应用整组重复规则并保留原系列锚点。历史 209 项映射仍为 `UNMAPPED`，本次不修改或签核历史原项。

现有 `PowerStoreTest.wholeRecurringSeriesEditKeepsIdentityAndAnchorAcrossRestart` 在既有系列上修改标题、重复次数与例外项，通过 `EditSession.save()` 更新同一正式记录。测试验证 `eventSeq` 不变、revision 只前进一次、`startMillis` 锚点不变，投影出的发生项键符合新规则；重开 SQLite 后，记录、投影、身份水位与历史仍一致。

这些检查覆盖收窄后的重建候选及其核心持久化预期，因此将 `REC-P0-080` 从 `NOT TESTED` 签为 `PASS`。编辑范围选择交互不属于该行声明的测试环境，本次不作此项断言；也未声称 API 35 或实体设备行为通过。

## 验证证据

- 定向 Robolectric API 34：**1/1 PASS**。测试：`wholeRecurringSeriesEditKeepsIdentityAndAnchorAcrossRestart`。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.core.PowerStoreTest.wholeRecurringSeriesEditKeepsIdentityAndAnchorAcrossRestart --offline --rerun-tasks --console=plain`。
- 定向日志 SHA-256：`50B251FEEF9B2A248A851A7D83C421C596F7174582F1387B75EBC3A8DADDE52A`；JUnit XML SHA-256：`8F1EC68F069A760F869CFE40D82367DEF4E032454D4831B36690888F81952921`。
- 完整 Debug 回归命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。结果 **BUILD SUCCESSFUL**，460/460 测试、57 个套件，0 failures/errors/skips；lint 0 errors / 58 warnings。完整日志 SHA-256：`F37900D58C2FCCDFBB3F5FEB4CC620463585182E2327102D36BD24E613DA8208`。
- 全量 `PowerStoreTest` XML SHA-256：`B08E306E6478A28B9091FAFD9A4D3B6A4183879AEC458A1425C68544C9DCE253`；lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 完整 JUnit XML 位于 `.local/android-test/reports/freeze-20261024/pc-issue-108-full-junit/`；定向日志与 XML 位于同目录下的 `pc-issue-108-*` 文件。完整回归包含一个受保护的未跟踪测试文件；该文件未修改、未暂存。

## 台账与交付

- `docs/CORE_FREEZE_ACCEPTANCE_LEDGER.csv` 将 `REC-P0-080` 标为 `PASS`，范围限定为候选明示的 EditSession/SQLite 核心持久化行为。
- 结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-108.json`，SHA-256 `7554EB9924140E345AE9A3292C46CF1B64DA00374396CD48CAEA1D4B87984AB1`；CSV SHA-256 `798E12FD3FF6174450BBCF593B48D2084981098F857127A842D85E55C492E251`。校验 209 行、唯一 ID、必填字段、证据完整性及 126/82/1 分区；`product_behavior_tested=false`。
- 冻结候选计数为 **137 PASS / 0 FAIL / 9 BLOCKED / 56 NOT TESTED / 7 SPEC UNRESOLVED**；P0 为 **113/0/1/7/5**，P1 为 **24/0/8/49/1**。历史用例仍无映射，整体保持 **HOLD**。
- 没有修改应用生产代码、测试代码、业务数据或设计资产。
