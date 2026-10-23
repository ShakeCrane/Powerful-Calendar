# PC-ISSUE-092 — AI 批次主动取消后返回并持久化部分结果

日期：2026-10-24

候选：`REC-P1-060`（重建 P1；历史映射仍为 `UNMAPPED`）

状态：**核心主动取消与部分结果报告 PASS / 整体冻结 HOLD**

## 发现

已有 `PowerStoreTest.cancelStopsFurtherAiWritesAndKeepsFinishedOnes` 直接验证 store 在收到取消请求后拒绝后续写入，但没有调用 `AiBatchRunner` 来证明它会停止剩余批次、返回准确的部分结果，并将报告持久化供 SQLite 重开后读取。

## 变更

- 新增 `PowerStoreTest.theAiRunnerStopsAfterUserCancelAndPersistsPartialOutcomeAcrossRestart`。
- 在首个批次项完成后的进度回调中请求取消；runner 随后检测到 `CANCEL_REQUESTED`，不提交第二项。
- 验证返回报告为 `published=1`、`kept=0`、`cancelled=true`；已完成事件保留，未完成草稿仍可审查，批次锁释放，session 状态为 `CANCELLED`。
- 验证报告 JSON 与状态跨 SQLite 关闭/重开保留，且旧 session 不能重放提交。
- 仅增加核心测试；生产代码、界面、业务数据与设计资产均未修改。

## 验证

- 定向 Robolectric API 34：**1/1 PASS**，0 failures/errors/skips。日志 SHA-256：`FE85E768847D06B41AEC5128FD8CC85C528C997DD1127439EAA282FF05B0163D`；JUnit XML SHA-256：`F776710373A58963149903B83ED69D2667D4D27AB40F6DCFDBA924636013F638`。
- 完整命令 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**446/446 across 56 suites**，0 failures/errors/skips，`BUILD SUCCESSFUL`；Lint 0 errors / 58 warnings。日志 SHA-256：`4A8039764F0D6B25A47B8E0B4C9FC31445E15A64382FAA3EFD6363A61C10933A`。
- 完整 `PowerStoreTest` XML SHA-256：`C28E2121D4A756EA2DF06BDACBE360AC24A0868D2E97AF5300A99FAF7350FDDB`；Lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`86D48004E3CE00E94A459A4CCAA929A9B55F8816DDBE7E10D6E9937D0C11ED94`。
- 日志和复制后的 JUnit XML 位于 `.local/android-test/reports/freeze-20261024/pc-issue-092-*`。

## 范围与结论

- `REC-P1-060` 对 runner 主动取消、阻止后续提交、部分结果报告及 SQLite 持久化签为 **PASS**。
- 该回归直接请求 store 取消，不触发模型网络调用，也不代表界面交互、真实服务端超时或设备行为。
- 原始 209 项逐案清单及映射仍缺失，整体核心冻结继续 **HOLD**。

## Git

- 测试提交 `d79b0f6d11870c5a18c6b7401ebe18e72fe95c05` — `test(ai): 验证主动取消返回部分批次结果` — 已推送。
- 本独立报告及台账签核随后单独提交与推送。
