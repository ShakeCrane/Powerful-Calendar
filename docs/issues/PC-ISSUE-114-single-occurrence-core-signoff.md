# PC-ISSUE-114 — 显式单次重复发生项核心例外签核

日期：2026-10-24

候选：`REC-P0-078`（重建 P0；历史 209 项映射仍为 `UNMAPPED`）

状态：**显式单次发生项核心修改 PASS；默认范围选择与其他编辑范围未签核；整体冻结 HOLD**

## 签核范围

本次将候选收窄到 PowerStore 核心操作：调用方提供原始发生项键后，只修改该发生项并持久化例外。默认范围如何选择、此项及以后/整组范围选择不属于本次签核。

## 验证结果

`PowerStoreTest.editingOneRecurringOccurrencePersistsOverrideWithoutChangingOtherSlotsAcrossRestart` 在 Robolectric API 34 中创建五项每日序列，将 2026-10-21 07:30 的指定发生项改为 09:15。用例验证原始键、序列锚点和其他发生项时间保持不变；正式身份不变，revision 只增加一次。关闭并重开同一 SQLite 数据库后，记录和投影一致，身份计数仍为 1，历史记录数为 2。

- 定向回归：**1/1 PASS**。日志 SHA-256：`BAB165A4794804133E481480EE8EA4F7F80600CFBF0D92F2C5E658029DF049AD`；JUnit XML SHA-256：`F56CEEE8CCC5F70DDC827D3B8B0B1398137F15B1915CFDA337D3F1D1E67EA563`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。**BUILD SUCCESSFUL**；460/460 测试、57 个套件，0 failures/errors/skips；lint 0 errors / 58 warnings。
- 完整日志 SHA-256：`A470AB4A0A3573ECA02678948028E05C0B119778C047C558EBFBCD1E7C0F782A`；完整 `PowerStoreTest` XML SHA-256：`6618F91438181D5C3E7ED7A978ECF92F2357E51EDB2901B51403283835EF5661`；lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 日志和 JUnit XML 位于 `.local/android-test/reports/freeze-20261024/pc-issue-114-*`。测试基线：`54e4de4269956aa802d414ebd766bc905d09049b`。没有修改应用或测试源码。

## 台账与结论

- `REC-P0-078` 对“显式单次发生项例外仅改变指定项，并跨 SQLite 重开持久化”签为 **PASS**。默认范围选择、其他编辑范围和设备行为不在本结论内。
- 候选统计：**143 PASS / 0 FAIL / 9 BLOCKED / 50 NOT TESTED / 7 SPEC UNRESOLVED**；P0 **119/0/1/1/5**，P1 **24/0/8/49/1**。历史 209 项仍无映射，整体冻结保持 **HOLD**。
- 最新结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-114.json`，SHA-256 `3048D54BB5F8CCEB6762269685B535DD2AC5791EDFE0FA98744877040872B1F4`；CSV SHA-256 `1CC18C043E491828FCB23BCE54B02192ACECA1B63C376A9C57268EE1297BEAF1`。结构校验仅验证行数、唯一 ID、必填字段、优先级及证据引用；`product_behavior_tested=false`。
