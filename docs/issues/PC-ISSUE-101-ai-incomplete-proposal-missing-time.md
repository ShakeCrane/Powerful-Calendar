# PC-ISSUE-101 — AI 缺时间提案的核心保留与修复

日期：2026-10-24

候选：`REC-P1-051`（重建 P1；历史映射仍为 `UNMAPPED`）

状态：**缺时间核心路径有证据；候选仍 NOT TESTED；整体冻结 HOLD**

## 验收范围

新增 `PowerStoreTest.parsedAiActivityWithMissingTimeRemainsRepairableDraftAcrossRestart`，并与现有 `AiResponseParserTest.missingTimeStaysMissing` 一起验证缺时间路径。ACTIVITY 的空 start/end 保持空值并发出待补字段警告；解析结果进入 PENDING 草稿。尝试正式提交会得到 INVALID，不创建正式事件或历史；INVALID 状态及缺失字段跨 SQLite 重开保留。补齐开始和结束时间后，草稿恢复为 PENDING 并只提交一次，正式记录保存准确时间。

这签核的是缺时间的核心路径。候选还包含缺类型分支；该分支以及 API 35/free-provider/卡片交互没有在本轮验证，所以 `REC-P1-051` 保持 NOT TESTED。没有修改 UI 或生产代码。

## 回归证据

- 定向 Robolectric API 34：**2/2 PASS**。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.ai.AiResponseParserTest.missingTimeStaysMissing --tests dev.powerfulcalendar.core.PowerStoreTest.parsedAiActivityWithMissingTimeRemainsRepairableDraftAcrossRestart --offline --rerun-tasks --console=plain`。日志 SHA-256 `D295AB8C71FA252A537C55F8E92028E94C680A023B62A279DA60E915C0E7CD3C`；parser XML SHA-256 `B5CDA3DD76BFD071FF307631F71EC3C0351D77414A0FD724AA5A58A150687F8E`；PowerStore XML SHA-256 `5F8E20959DB6A8FDFAF08BAC3C2477E93261663CD6CE6BCCE0C925DEF360C849`。
- 完整冻结 Gradle 命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**453/453 across 57 suites**，0 failures/errors/skips；Lint 0 errors / 58 warnings。完整日志 SHA-256 `A3820FFF67696CEF4CE6B25FA8A328026C93EABA81CF48D2EB803259D5207976`；完整 `AiResponseParserTest` XML SHA-256 `281B3B85101B08EAAC8D6E722C70A7319A7CBBB85C23C24454C95F4A23B6273E`；完整 `PowerStoreTest` XML SHA-256 `CBE9F0DD9D9CF3A348DEF903B7AC6DA05988F5922A99D20771A33DF37CF0E7C8`；Lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256 `0CE2928EF5E597475BEDF1C6BCD96AD5BD6267EF987DB7589CC959EFF1BFA7A9`。

## Git

- 测试提交：`814f3f2c6cb2a3d451495ae8492c8629d0aa7476` — `test(ai): 验证缺时间提案可修复恢复`，已推送。
- 本报告、候选台账和冻结摘要随后单独提交并推送。

结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-101.json`，SHA-256 `7ECF656AF47EC32029B1B525ABE4636F06190BE762BE3119163422426FC1A6F4`；台账 SHA-256 `FB88F6EC57099AD2169DEF1828116634A22C2095A945E711E3013D546F00F519`。校验仅确认行结构、唯一 ID、优先级分区和状态计数，不签核缺类型或设备行为。
