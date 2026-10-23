# PC-ISSUE-008 — 数据库升级失败回滚与重试

日期：2026-10-23（北京时间）
优先级：P0
状态：**SQLite 核心迁移契约 PASS；整体核心冻结继续 HOLD**

## 发现与根因

`PowerStore.onUpgrade()` 已按 schema 版本顺序执行 v1→v4 迁移；既有测试覆盖成功升级，以及 lineage 列已写入但版本号尚未更新时的重试。缺少的证据是：v4 迁移已经创建新表并开始归并草稿后发生 SQLite 写入失败时，整个升级事务是否回滚、旧库能否原样保留并再次打开。

因此本 Issue 关闭的是一项核心验收证据缺口；本轮没有发现需要修改生产迁移逻辑的数据缺陷。只增加了确定性 SQLite 回归测试，没有改生产代码或 UI。

## 故障注入与实际结果

测试在隔离的临时磁盘数据库中建立 schema v3 fixture，并为同一事件准备三条 `PENDING` 草稿。迁移先成功归并较新的重复草稿，再由 SQLite trigger 在第二次归并（`draft_id=19`）时抛出明确的 `injected v4 migration failure`。测试随后验证：

1. `PowerStore` 打开数据库时确实收到该 trigger 的异常；
2. 迁移创建的 `draft_migration_audit` 表随事务回滚，schema `user_version` 仍为 3；
3. 先前已成功更新的草稿状态和审计写入也回滚；三条旧 `PENDING` 草稿全部保留；
4. 移除临时 trigger 后，重新打开同一数据库可完成 v4 升级；原正式对象、墓碑与序号高水位保留，两个重复草稿均被归并并写入迁移审计。

fixture 是依照仓库历史 schema DDL 构造的合成 on-disk 数据，不是用户的真实数据库备份。本项没有读写现有应用数据库，也没有使用 Android 模拟器或真机。

## 定向回归

命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerStoreSchemaUpgradeTest" --offline --rerun-tasks --console=plain
```

结果：**4 passed / 0 failed / 0 errors / 0 skipped**。完整测试类 XML：`.local/android-test/reports/freeze-20261023/pc-issue-008-migration-targeted-partial-write-junit-20261023.xml`，SHA-256 `D4921F5336FBC8C44D8FEF774F5022C786F2FCC0AF34CA0498259E45DD2CF37C`。Gradle 日志：`.local/android-test/reports/freeze-20261023/pc-issue-008-migration-targeted-partial-write-20261023.log`，SHA-256 `BDF989FBB7D5F170E5C949684B85E76C8670F89119324D8783FB935B3711E7A0`。

该类的关键案例为 `failedV4MigrationRollsBackAndCanRetryWithoutLosingLegacyRows`。版本轴独立性另由 `VersionContractTest.schemaBackupAndPackageVersionsAreSeparateAxes` 覆盖；对应 JUnit XML SHA-256 为 `D760AE1AE849615EDA62852EABC04EA7B2F905992CFCA4D6119737F52ACD59C9`。

## 完整 Gradle 回归

命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

结果：**BUILD SUCCESSFUL；374 tests passed、0 failures/errors/skips；Lint 0 errors / 55 warnings。** 共 373 个已跟踪测试及 1 个受保护、未跟踪的 `DraftDeckFeedbackTest.kt`。后者未修改、未暂存；因此不能把 374 描述成干净提交树中的测试数。完整日志：`.local/android-test/reports/freeze-20261023/pc-issue-008-migration-full-gradle-final-v2-20261023.log`，SHA-256 `34E173117BE41997BEA9BB564C5B103B24B3B957B9CFC182F8E128DC35DF2744`。

Debug APK SHA-256：`63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`。验证环境是 JVM SQLite / Robolectric（schema 测试声明 SDK 34）；没有设备验收。

## 候选验收签核与边界

以下五个重建候选项分别按精确测试证据记为 PASS：`REC-P0-083`（schema、备份与 package 版本轴独立）、`REC-P0-085`（有序迁移链）、`REC-P0-086`（多表迁移原子性）、`REC-P0-087`（失败后保留旧库并可重试）、`REC-P0-088`（合成历史 schema fixture 的行内升级保留）。原始 209 个历史 case ID 仍然没有映射；这些候选行不等价于历史验收项。

台账结构检查：209 行、209 个唯一 ID、优先级 126/82/1 分区。结构验证 artifact：`.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-008.json`；更新后的 ledger SHA-256 `BE5DA4AB9C5DEBEE39AEBDD55BBFC75CC8F6CC78504552AB36EC8E8CFA433A98`。验证脚本只检查结构，不证明行为或历史映射。

更新后候选状态：**44 PASS / 0 FAIL / 9 BLOCKED / 150 NOT TESTED / 6 SPEC UNRESOLVED**。核心冻结仍为 **HOLD**：历史 209 项原表及 0 项原始 ID 映射、未测行为、平台/设备验收和其他开放 Issue 仍未完成。

## Git

- `2131bb52eb270401953df8151e0a7561be32cdee` — `测试(冻结): 覆盖数据库迁移失败回滚与重试`
- `a484c1afaf5a320bc95227b1b177f78ca0cbb256` — `测试(冻结): 精确校验迁移故障注入来源`
- `ef901e0d5878bb4b25c61ed10dd1021cf312ca97` — `测试(冻结): 验证迁移部分写入后的整体回滚`

以上测试提交已推送到 `origin/main`。本报告及冻结台账更新随后单独提交推送。
