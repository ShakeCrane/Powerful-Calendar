# PC-ISSUE-112 — API 35 精确闹钟拒绝核心状态签核

日期：2026-10-24

候选：`REC-P0-112`（重建候选；历史 209 项映射仍为 `UNMAPPED`）

状态：**Robolectric API 35 核心拒绝分支 PASS；真实 Android 权限行为未验证；整体冻结 HOLD**

## 核心验收

本项签核提醒 scheduler/store 的权限拒绝分支。`ReminderValidationTest.api35ExactAlarmDenialIsDurablyBlockedWithoutScheduling` 在 Robolectric API 35 中由 `ShadowAlarmManager` 模拟精确闹钟权限拒绝，提交 EXACT 提醒后运行 reconciliation。测试断言持久意图状态为 `BLOCKED`、`androidHint` 包含精确闹钟权限提示，且 ShadowAlarmManager 没有闹钟注册。

这满足收窄后的核心验收：权限缺失时阻止调度，并留下可供上层读取的原因。测试没有重新打开 SQLite 验证，也没有验证 Android 系统权限状态或提示实际送达，因此不据此声称设备行为通过。`REC-P0-112` 对核心 scheduler/store 分支签为 PASS。

## 验证证据

- 定向 Robolectric API 35：**1/1 PASS**，0 failures/errors/skips。日志 SHA-256：`D3DD61B4B2606E812775EDE4081C2E755383F1E58FF415D91BA6782C06694504`；JUnit XML SHA-256：`B5817AD4376AD7E14A9FC8507C8C49C93A361E38A430218690F85A61410C85D6`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。**BUILD SUCCESSFUL**；460/460 测试、57 个套件，0 failures/errors/skips；lint 0 errors / 58 warnings。完整日志 SHA-256：`F37900D58C2FCCDFBB3F5FEB4CC620463585182E2327102D36BD24E613DA8208`。
- 全量 `ReminderValidationTest` XML SHA-256：`8D7C88A0ABB231D602AE94909EABB4E231D20B298499F026490E00C2C467F43A`；lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 定向/全量日志与 JUnit XML 位于 `.local/android-test/reports/freeze-20261024/pc-issue-112-*`。完整回归包含工作区内一项受保护的未跟踪测试；本 Issue 未修改或暂存它。

## 台账与边界

- `REC-P0-112` 预期收窄为核心阻断状态、原因提示字段和零调度注册；没有签核 Android 系统权限或提示投递。
- 结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-112.json`，SHA-256 `EC8710582C1E00B9BB43791493D62F53F7FAE9E9E0D5553406C80F34C7B70CC3`；CSV SHA-256 `6BEF65F539959780BFE2DF917F74E44F953BE8C7A64F051F03E6ED8E4A111EBC`。结构校验覆盖 209 行、唯一 ID、必填字段、证据完整性与 126/82/1 分区；`product_behavior_tested=false`。
- 候选计数：**141 PASS / 0 FAIL / 9 BLOCKED / 52 NOT TESTED / 7 SPEC UNRESOLVED**；P0 **117/0/1/3/5**，P1 **24/0/8/49/1**。历史用例 ID 仍无映射，整体保持 **HOLD**。
- 没有修改应用或测试源码。
