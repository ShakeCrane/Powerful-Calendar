# PC-ISSUE-007 — 预检后出现并发写入时拒绝备份恢复

日期：2026-10-23（北京时间）
优先级：P0

状态：**事务内非空目标复查核心路径 PASS；整体冻结继续 HOLD**

## 发现与根因

候选 `REC-P0-103` 要求：备份开始读取时目标库为空，但在预检完成后、写事务开始前若另一连接写入数据，恢复必须在事务内重新检查目标，不能覆盖后来写入的数据。`PowerStore.restoreBackup()` 已包含该事务内检查，但原先没有确定性测试能把另一个连接的写入放在这个精确边界。

尝试用真实并发锁交错时，Robolectric SQLite 的连接/线程锁行为使测试在进入被测检查前返回 `SQLITE_BUSY`，或因事务线程归属而失败；这些探索未用于签核。为精确验证已有保护，本次把实现抽到私有 helper，并增加一个仅 `internal` 可调用的 `restoreBackupWithPreflightHookForTest`。公开 `restoreBackup` 仍以无 hook 路径调用同一实现，生产行为不变。

## 修复与回归

测试先准备独立的备份源与空目标库。通过内部测试入口，在空库预检、Envelope 校验及 payload 校验完成后，让第二个 `PowerStore` 连接提交一个新草稿，然后继续恢复。实际结果：恢复事务内复查抛出 `NonEmptyRestoreTargetException`；第二连接写入的身份、草稿及 `nextSeq=2` 保留，备份中的记录没有写入。

定向命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.powerfulcalendar.core.BackupEnvelopeTest.restoreRechecksAfterWriteBetweenPreflightAndTransaction" --offline --rerun-tasks --console=plain
```

结果：**1 passed / 0 failed / 0 errors / 0 skipped**。日志：`.local/android-test/reports/freeze-20261023/pc-qa-gate-001-concurrent-restore-targeted-final-20261023.log`，SHA-256 `1B1880767397A7F2B2C86A633BCBDD9B3D8E369EB1594C3802FE47FDABC886DE`。

此前的完整 Gradle 命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

结果：**BUILD SUCCESSFUL；373 tests passed，0 failures/errors/skips；Lint 0 errors / 55 warnings。** 工作树包含 372 个 tracked 测试和 1 个受保护的未跟踪 `DraftDeckFeedbackTest.kt`；本 Issue 没有修改或暂存该文件，也没有使用模拟器/真机。完整日志：`.local/android-test/reports/freeze-20261023/pc-qa-gate-001-concurrent-restore-full-gradle-final-20261023.log`，SHA-256 `7A4CE9C9BA3D88854799F9D820E7D1B0DD36EFAF7F0154550F41724832F8075F`。Debug APK SHA-256：`63F8DCB69D1028B8D7061B374162F59770B86FA2FA4BEB6FDA132D4393AA8B6C`。

### 失败探索记录

以下尝试没有作为通过证据：

- `pc-qa-gate-001-concurrent-restore-targeted-20261023.log`（SHA-256 `32749D7CB96BE912DE58C0291DC893F8F5DFDBE8FF73F71EA2D86AC5E12DFD4B`）与 `pc-qa-gate-001-concurrent-restore-diagnostic-20261023.log`（SHA-256 `68ECA0AD6655FC026C060767825E3530FE955B2D73D7637377BAFF7B539BB798`）：Robolectric 返回 SQLite `SQLITE_BUSY`，测试未到达类型化空库复查。
- `pc-qa-gate-001-concurrent-restore-targeted-attempt2-20261023.log`（SHA-256 `088DFB20C51DDCFF7D45E850F1A763473334800F1B317C415046FBB0CB4A1579`）：写入事务因另一个连接已开启 `BEGIN EXCLUSIVE` 而返回锁错误。
- `pc-qa-gate-001-concurrent-restore-targeted-attempt3-20261023.log`（SHA-256 `3660D890533944257F13930E7ECBD9B17BEB5CB22BD6CCE60D38145812843F6E`）：Robolectric 报告事务由非所有者线程提交。
- 一个中间探索日志曾写入 `pc-qa-gate-001-concurrent-restore-targeted-final-20261023.log`，随后该路径被成功定向运行复用覆盖；其已知失败为打开并发 writer 时的 `PRAGMA journal_mode`/SQLite 锁错误，原始内容未保留。最终成功日志现位于同一路径。该证据缺口已登记；签核仅依据后续确定性测试和完整回归。

## 验收台账与边界

`REC-P0-103` 单独记为 PASS，环境和执行细节见 `docs/CORE_FREEZE_ACCEPTANCE_LEDGER.csv`。结构验证 artifact：`.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-007.json`，ledger SHA-256 `DD2B026096778438E18234CFA9D291AC77122903925FEA95E10ECBBF61C154E0`。结构验证不证明历史 209 个 ID 的映射。

候选状态更新为 39 PASS、0 FAIL、9 BLOCKED、155 NOT TESTED、6 SPEC UNRESOLVED；原始历史 ID 仍为 0 个已映射。故本 Issue 只签核该重建候选项，整体冻结继续 **HOLD**。

## Git

- 测试与内部确定性测试入口：`2171c3f96fb69a1e6d14e1fb170d4c53a9439184` — `测试(冻结): 验证事务内非空恢复保护`；已推送到 `origin/main`。
- 本独立报告和冻结台账更新随后单独提交并推送。
