# PC-ISSUE-123 — Compiled skin 源码边界静态签核

日期：2026-10-24（北京时间）｜优先级：P1｜候选：REC-P1-082

状态：静态源码边界检查 PASS（收窄范围）；核心冻结仍 HOLD

## 需求与签核范围

UI_SKIN_EXTENSION_CONTRACT_V4 的 Separation 条款要求 compiled presentation skin 只通过 host-provided actions 工作，不能自行访问 PowerCal 持久化、AI、授权、提醒或凭据能力。CORE_FREEZE_GOAL_MODE_PROMPT §7 要求核心冻结期间维持 skin source boundary，并明确独立 .pcskin 完全替代验收留到核心冻结之后。

原重建台账 REC-P1-082 的来源和环境与 action 不一致，也没有单项证据。本次将来源修正到 skin contract 与 Goal Prompt，环境修正为 Gradle 静态 gate。PASS 仅覆盖静态源码模式，不扩大为 arbitrary Kotlin 沙箱或全部运行时安全签核。

## 步骤与结果

- 基线：PowerCal 仓库 HEAD fa9bd13c1af3fa6f33b25d10876333dc71a1d9fa；Windows；不启动应用、不触碰模拟器或业务数据。
- 执行：gradlew.bat :app:verifySkinSourceBoundary --offline --rerun-tasks --console=plain。
- 预期：app/src/main/java/dev/powerfulcalendar/ui/v2/skins/**/*.kt 中不得直接引用 gate 配置的受保护服务/API。
- 实际：BUILD SUCCESSFUL，1 个 Gradle task 执行成功；检查了 5 个编译皮肤 Kotlin 源文件，没有发现配置的禁用引用。
- 当前禁用模式包括 PowerStore、PowerCalStores、DeckController、AiSettingsStore、AiClient、ReminderScheduler、getSharedPreferences、ContentResolver、SQLiteDatabase、文件/网络、动态加载、进程启动、System.load 和 kotlin.reflect. 等。
- 该 gate 是源码审查辅助，不是对同 APK 内任意受信编译 Kotlin 的安全沙箱；不签 .pcskin 动态包隔离或特定皮肤视觉/无障碍验收。

## 回归与证据

- 定向日志：.local/android-test/reports/freeze-20261024/pc-issue-123-skin-boundary-targeted.log，SHA-256 46E783468CE42C86D6E71A9290E1FF2311F369AD987F56377B71F6172160085D。
- 最新完整冻结回归在相同应用源码上完成：462/462 across 57 suites，0 failures/errors/skips，Lint 0 errors/58 warnings；日志 SHA-256 8C82E247D1FA802B4A876C68D0727A0C3E46BFEB8E6343092A1D1F29E3F10A2D。
- Debug APK SHA-256 138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096。
- 结构校验：.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-123.json，SHA-256 53E2A73D4069970CB84371933DEE24568BFA7CAA65B2B9395B41061F2A904638；候选台账 SHA-256 2BBFFF2790D683A75779482B3C0B10D290FC7628D89414FE4BF947D6885D767F。检查确认 209 个唯一 ID、126/82/1 优先级划分及必填证据字段；它不测试产品行为，也不证明历史 ID 映射。

## 结论

REC-P1-082 对静态 skin source boundary 的收窄核心标准签核 PASS。冻结统计为 149 PASS / 0 FAIL / 9 BLOCKED / 44 NOT TESTED / 7 SPEC UNRESOLVED；历史 209 项仍无原始行级映射，整体冻结保持 HOLD。

## Git

本报告、台账、verdict、QA gate 与冻结续接记录作为一个文档变更提交并推送。未修改应用源码、测试源码、UI、设计资产、模拟器数据或其他未跟踪工作。
