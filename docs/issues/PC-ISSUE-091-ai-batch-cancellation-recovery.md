# PC-ISSUE-091 — AI 批次取消后释放锁并拒绝旧授权

日期：2026-10-24

候选：`REC-P1-061`（重建 P1；历史映射仍为 `UNMAPPED`）

状态：**核心批次恢复 PASS / 整体冻结 HOLD**

## 发现

`AiBatchRunner` 已在 `finally` 中使用 `NonCancellable` 结束批次，但缺少一条直接回归来证明协程取消后锁能释放、未完成草稿不会被错误发布，并且旧会话在数据库重开后也不能再写入。已有的孤儿批次测试覆盖进程所有者失效后的恢复与旧 epoch 拒写；本次补足 runner 取消路径。

## 变更

- 新增 `PowerStoreTest.cancelledAiBatchRunnerReleasesLockAndFencesRemainingScopeAcrossRestart`，在第一项完成后向 runner 注入 `CancellationException`。
- 验证已完成项仍存在、第二项仍为待处理草稿、批次记录为 `CANCELLED` 且锁释放。
- 关闭并重开同一个 SQLite 数据库后，再验证锁仍释放、取消状态持久化、旧 `AiSession` 提交返回 `NotAuthorized`，且没有生成第二个正式事件。
- 与既有 `orphanedBatchIsFencedAndUnlockedOnRecovery` 合并覆盖两条核心路径：运行中取消的 `finally` 清理，以及旧进程 owner token 恢复后的 stale epoch 隔离。
- 仅增加核心测试；生产代码、界面、业务数据与设计资产均未修改。

## 验证

- 定向 Robolectric API 34：**1/1 PASS**，0 failures/errors/skips。日志 SHA-256：`3D6BEDC5462DF72ADCE56A06A479A92FA2EB6521AFD400769A2C6D61554DE5DB`；JUnit XML SHA-256：`4B9C5709A0E8F9689B3E249B5CDCC5E3C5541DE507C29A95BE2BE602B2321D02`。
- 完整命令 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**445/445 across 56 suites**，0 failures/errors/skips，`BUILD SUCCESSFUL`；Lint 0 errors / 58 warnings。日志 SHA-256：`BDEB8CB1A9047D1E30A7FEDD0A2E63AEA30B21744748F298BF90360F5F82BB5D`。
- 完整 `PowerStoreTest` XML SHA-256：`8BABBB9E7BF980EC6E88D3C42498AC31634E6942D932A797678C6EEE66E798C5`；Lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`86D48004E3CE00E94A459A4CCAA929A9B55F8816DDBE7E10D6E9937D0C11ED94`。
- 日志和复制后的 JUnit XML 位于 `.local/android-test/reports/freeze-20261024/pc-issue-091-*`。

## 范围与结论

- `REC-P1-061` 对核心批次取消清理、SQLite 持久化和 stale epoch 拒写签为 **PASS**。
- owner token 替换模拟进程遗留状态，没有执行 OS 级强杀；本回归也没有调用模型服务或测量网络超时。本结论只覆盖批次 runner/store 的状态恢复与授权边界，不代表实时服务超时体验或设备行为。
- 原始 209 项逐案清单及映射仍缺失，整体核心冻结继续 **HOLD**。

## Git

- 测试提交 `65fb3ca8bda51cf7c6bd91794789e2b152e9603a` — `test(ai): 验证批次取消后释放锁并隔离旧授权` — 已推送。
- 本独立报告及台账签核随后单独提交与推送。
