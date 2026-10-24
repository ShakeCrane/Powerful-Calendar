# PC-ISSUE-105 — 提醒宽限期与错过恢复分支

最后更新：2026-10-24

验收项：`REC-P0-118`（重建 P0；历史映射仍为 `UNMAPPED`）

状态：**PASS（窄化到 JVM/Robolectric 与 API 35 AVD 的接收器分支）；核心冻结仍 HOLD**

## 已签核规格

用户确认迟到提醒宽限期为 **10 分钟，含恰好 10 分钟的边界**。在此范围内允许触发；超过 10 分钟时记录 `MISSED`，不补响。该决策已同步到 `docs/CORE_CONTRACT_FREEZE_2026-10-21.md` 和 `docs/PRODUCT_BASELINE_G1-G11.md`。

本签核只解决迟到宽限期；同时到达的多个提醒如何发声仍是独立未决规格。API 35 AVD 用 Android 的真实数据库、通知管理器执行接收器处理，但测试直接调用 `ReminderReceiver.handle`，没有验证 Android `AlarmManager` 的实际定时投递、重启恢复、OEM 或实体设备行为。实体设备测试由用户执行，未在本 Issue 中声称通过。

## 根因与修复

原接收器按 `lateBy > GRACE_MILLIS` 判断，恢复调度器按 `instantMillis < now - GRACE_MILLIS` 判断。两种表达在正常时间值下等价，但没有共享的边界实现或恰好等于 10 分钟的确定性测试，规格也尚未得到用户签核。

将严格超时判断收敛为 `AlarmContract.isOutsideGraceWindow(triggerAtMillis, nowMillis)`，由提醒接收器和 `ReminderScheduler` 共用。只有触发时刻严格早于 `now - 10 分钟` 才算超窗；相等时仍在宽限期内。生产行为的设计值未改变，只消除了两入口今后漂移的空间。

## 测试结果

- 新增纯 JVM 边界回归：恰好 10 分钟返回“未超窗”；提前 1ms 返回“超窗”；晚 1ms 返回“未超窗”。`AlarmContractGraceWindowTest` 1/1 PASS。
- API 34 Robolectric 接收器：宽限期内 ACTION_FIRE 记录 `FIRED` 并发布提醒；超过当前窗口的 ACTION_FIRE 记录 `MISSED` 且无提醒。`ReminderCompletionIsolationTest` 全量 8/8 PASS。
- API 34 Robolectric 恢复调度：过期持久化 `PENDING` 意图记录 `MISSED`、清除意图且不重新注册 AlarmManager 闹钟。`ReminderValidationTest` 全量 17/17 PASS。
- API 35 AndroidJUnitRunner：在 `emulator-5554`（`PowerCal_API35(AVD) - 15`）运行 1/1 PASS。9 分钟迟到的接收器处理记为 `FIRED`，Android 通知管理器中存在提醒；11 分钟迟到记为 `MISSED`，没有提醒。每次用随机隔离数据库；通知权限由宿主在测试前授予。该测试不调用 OS 闹钟投递。
- 定向命令 `:app:testDebugUnitTest --tests dev.powerfulcalendar.reminders.AlarmContractGraceWindowTest --tests dev.powerfulcalendar.reminders.ReminderCompletionIsolationTest --tests dev.powerfulcalendar.reminders.ReminderValidationTest --offline --rerun-tasks --console=plain`：**26/26 PASS**，0 failures/errors/skips。日志 SHA-256 `7A9FAFEFC95353CAF31D09639A2085C28A5D730CD200003B87FC77DBE6F6A890`。
- 完整强制重跑 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain`：**495/495 JVM tests、67 suites、0 failures/errors/skips**；88 个 Gradle tasks 均执行，source boundary、Debug APK 与 AndroidTest APK 组装通过；lint **0 errors / 58 warnings**。495 项计数包含仓库内预先存在且未修改的 `DraftDeckFeedbackTest.kt` 1 项。

## 可追溯证据

- 定向 AVD 命令日志 `.local/android-test/reports/freeze-20261024/pc-issue-105-api35-avd-5554-attempt5.log` SHA-256 `433EFEF15C892216E0E3E0906A6F00BCEE4899D8D1135B9C93F1849D00806147`；JUnit XML `app/build/outputs/androidTest-results/connected/debug/TEST-PowerCal_API35(AVD) - 15-_app-.xml` SHA-256 `1985D24BD40866BC37218EC0C50B1E51F8929BA8B3F86281AE340AC2FA8133C1`；设备 logcat SHA-256 `ED77BB8BF935BF3EF2928369A1EB8E4304BA3AC794F7E9988C0014E78AF49F1E`。logcat 明确记录 API 35、9 分钟 `FIRED`/有通知、11 分钟 `MISSED`/无通知。
- 完整 Gradle 日志 `.local/android-test/reports/freeze-20261024/pc-issue-105-full-gradle-rerun.log`：SHA-256 `73549B5364373678E78E6BCCFBCFCDB01CE24A8CFAE5CC341F751D98B62AAB33`。
- 全量 JUnit XML：`AlarmContractGraceWindowTest` SHA-256 `7635A1B201A21FD21CC9AE0494B543986F11C3BD04B5CEB01869A8CF33D387A1`；`ReminderCompletionIsolationTest` `D4840CBC02681D2E6836D7C6337C5DCCED216C38F2733EF3F2509A0F5E99B23C`；`ReminderValidationTest` `6FC4A842086CFF299E344807B44C8306CDA5BF3BE42092FBE2A3042A22FA831D`。
- Lint XML SHA-256 `44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F`；lint 文本报告为 0 errors / 58 warnings。
- Debug APK `app/build/outputs/apk/debug/app-debug.apk` SHA-256 `7A5476F972473030B7DF2AE0E2CA6F305A71BD2A6092CFDA02F9B3378F40A03B`；AndroidTest APK `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` SHA-256 `28994F452DCA89D42E1AC3C07EAB3DBF005C73CA8283871D0AEE2C74F84CDD21`。
- 非最终的 Robolectric 和 API 35 AVD 尝试、命令日志哈希及可确认的夹具原因均记录于 `.local/android-test/reports/freeze-20261024/pc-issue-105-exploratory-attempts.md`，SHA-256 `8B1459F6C7553357D979F5B62B8C9E32983398D33E4A937B1BD79E6C4655240A`。AVD 早期尝试的 instrumentation 进程崩溃没有可用 JUnit 堆栈；另两次把两条 overdue 意图同时放入夹具，使接收器的正常全局 reconcile 与显式投递顺序冲突。最终改为宿主授权通知权限、逐条创建并投递，测试通过。所有失败尝试均保留且未计为 PASS。

### 证据保管说明

本次首次定向运行时复用了旧报告引用的 `.local/android-test/reports/freeze-20261024/pc-issue-105-full-gradle.log` 文件名，覆盖了先前 `:app:check` 探索运行的本地日志。旧报告记录其 SHA-256 为 `0035DE4D051BEC54154639A1894985E181AE8D7D27FEBB26E124803F9B4F6289`，并说明失败位于 `verifyReleaseSigning`（用户离线 Release keystore 不存在），不是 Debug 测试或 lint 失败。该忽略目录原始日志字节目前无法恢复；旧结果和哈希仍保留在之前已提交的报告版本中，本次最终全量日志已记录新哈希。后续证据运行需使用唯一文件名，避免覆盖历史日志。

此前的 2026-10-24 历史检查点曾覆盖 459/459 测试，但当时产品宽限时长尚未签核，故 `REC-P0-118` 保持 `SPEC UNRESOLVED`。该检查点及旧测试提交 `2d89ffba5317eb9b0164682702868e5cf75c8fe4` 仅作历史记录，现由本次 owner 决策与回归结果取代。历史报告中记录的旧全量日志 SHA-256 `2289F28174AF65C694D74D3048788FB6A0190830782AC21AB97336B0FF6DD755`、旧 Debug APK SHA-256 `138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096` 保留用于溯源，不作为当前 HEAD 的证据。

## 台账、提交与冻结状态

- `REC-P0-118` 已从 `SPEC UNRESOLVED` 更新为 **PASS**，范围限于已列出的 JVM、Robolectric 与 API 35 AVD 直接接收器分支。
- 生产修复提交 `b381846a73b1b25656b784989a2521c3917d0dad` 和 API 35 回归提交 `02f64925433b4ccb8a46a22a5c784c381e1f37b7` 已推送。
- 结构校验 r30：211 rows，210 active reconstructed；198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2=144/65/1，历史映射 0，`product_behavior_tested=false`。artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r30-pc-issue-105.json` SHA-256 `E330D6B59F6C9E2D31369F564A141030628E4300078892EDE25C0143D2D637E9`；ledger SHA-256 `420927B4B3D86D662D867799D2CCDB6BA1977C206BC05F4BAB65F8EFE52B2D8E`。校验只检查结构与计数；本 Issue 仅签核 `REC-P0-118` 的已列接收器分支，整体核心冻结仍 HOLD。
- 核心冻结仍 **HOLD**：尚有其他未关闭验收项；Android OS 闹钟实际投递、重启后的系统恢复以及用户负责的实体/OEM 设备行为未由本 Issue 证明。
