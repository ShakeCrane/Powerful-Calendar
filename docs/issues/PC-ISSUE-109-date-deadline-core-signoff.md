# PC-ISSUE-109 — 日期型 Deadline 完成后的核心精度签核

日期：2026-10-24

候选：`REC-P0-023`（重建候选，历史 209 项映射仍为 `UNMAPPED`）

状态：**重建候选核心数据语义 PASS；整体冻结仍 HOLD**

## 验收边界与判定

候选环境是 Robolectric API 34、已跟踪的日期型 Deadline、PowerStore SQLite 重开，并明确不包含界面或设备。旧预期“不要渲染成 START-END 区间”超出该行声明的测试环境，因此将重建候选预期收窄为核心数据语义：完成后保留 `DATE` 精度，不合成 instant 或 interval 字段。原始历史 209 项不存在映射，本次不签核任何历史原项。

现有 `PowerStoreTest.completingDateOnlyDeadlinePreservesDatePrecisionAcrossRestart` 创建日期为 `2026-10-24` 的已跟踪 Deadline，完成其 `single` occurrence，并验证 `deadlineDate` 不变、`precision` 为 `DATE`、`deadlineMillis/startMillis/endMillis` 为空；同一 SQLite 数据库重开后，完成键、身份与历史仍保持一致。

该证据满足收窄后的重建核心候选，故将 `REC-P0-023` 从 `NOT TESTED` 签为 `PASS`。日期带中的视觉位置和无障碍呈现没有在本次测试，也不由此候选签核。

## 验证证据

- 定向 Robolectric API 34：**1/1 PASS**。测试：`completingDateOnlyDeadlinePreservesDatePrecisionAcrossRestart`。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.core.PowerStoreTest.completingDateOnlyDeadlinePreservesDatePrecisionAcrossRestart --offline --rerun-tasks --console=plain`。
- 定向日志 SHA-256：`50B251FEEF9B2A248A851A7D83C421C596F7174582F1387B75EBC3A8DADDE52A`；JUnit XML SHA-256：`3118CABFBDCAAEAC410EF7612AE5064B45FAE6DFB504EE4EEF81A513BC4C2556`。
- 完整 Debug 回归命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。结果 **BUILD SUCCESSFUL**，460/460 测试、57 个套件，0 failures/errors/skips；lint 0 errors / 58 warnings。完整日志 SHA-256：`2D3AB12D2A6574E8E5A3690A5504356BF833855CD1D06CCD4D9AFF3E59FD8938`。
- 全量 `PowerStoreTest` XML SHA-256：`A20AFF5A56A7306FFFA55BABC6B92611D004465D855F32CA09F4A71E662F1666`；lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 全量 JUnit XML 位于 `.local/android-test/reports/freeze-20261024/pc-issue-109-full-junit/`；定向日志与 XML 位于同目录下的 `pc-issue-109-*` 文件。完整回归包含一个受保护的未跟踪测试文件；该文件未修改、未暂存。

## 台账与交付

- `docs/CORE_FREEZE_ACCEPTANCE_LEDGER.csv` 将 `REC-P0-023` 的预期限定为日期精度与无合成时间字段，并标为 `PASS`。
- 结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-109.json`，SHA-256 `C5BE9C34C74C38278E8BA64DD4041C67408F4C714F16B23AEA3EEA93917CD49B`；CSV SHA-256 `E6E28BDC1391820B55B9EC963AD9CF9083D7A966DB3D28F014A3D5DAC7AF2FEA`。校验 209 行、唯一 ID、必填字段、证据完整性及 126/82/1 分区；`product_behavior_tested=false`。
- 冻结候选计数为 **138 PASS / 0 FAIL / 9 BLOCKED / 55 NOT TESTED / 7 SPEC UNRESOLVED**；P0 为 **114/0/1/6/5**，P1 为 **24/0/8/49/1**。历史用例仍无映射，整体保持 **HOLD**。
- 没有修改应用生产代码、测试代码、业务数据或设计资产。
