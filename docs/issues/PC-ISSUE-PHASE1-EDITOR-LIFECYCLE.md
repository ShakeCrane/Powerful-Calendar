# PC-ISSUE-PHASE1 — 编辑器生命周期与默认时间统一

日期：2026-10-25

状态：**Phase 1 自动化与 API 35 AVD 验收 PASS；实体设备验收由用户执行；核心冻结仍 HOLD**

## 根因

普通新建入口在打开界面前直接调用 `PowerStore.newDraft`。因此仅查看编辑器就会写入草稿行、分配永久 `eventSeq` 和 `queueOrder`，增加待处理数量。入口还各自使用 `DraftForm.fresh` 的 09:00、月份日期、时间轴时刻或 Web bridge 的本地日期，导致 ALARM、ACTIVITY、DEADLINE、MEMO 的初始值随入口变化。

正式事件编辑、AI 提案、ICS 导入、文本解析及移动事件草稿是有意持久化的独立流程；本修复保留了这些流程及正式编辑的 lease、版本冲突、恢复 checkpoint。

## 修复

- `NewEventEditSession` 为普通新建建立内存 baseline、一次性捕获的 Clock/Zone 和稳定的语义 Diff。字段格式、日期/时刻精度、空白规范化按实际保存意义比较；无 Diff 的关闭/checkpoint 不写数据库。
- 时间轴、月份、Memo 页面、PowerCalApp Web action 与 WebSkinEditBridge 的普通新建入口改为共享 transient session。月份日期与时间轴时刻仍作为显式选择优先传入。
- × 只为有效 Diff 暂存一个草稿；重复关闭/checkpoint 复用同一草稿；保存完整内容直接事务创建正式事件，不先创建草稿；不完整内容暂存并可继续编辑、随后提交同一张草稿。无 Diff 保存不产生事件、草稿或提交记录。
- `PowerStore.createFormalEvent` 原子写入正式事件、identity、commit 和提醒意图，并用会话 idempotency key 防止重放重复创建。
- `EditorDefaults` 统一四类事件的初始值：ALARM/ACTIVITY 使用一次捕获的当前时间，ACTIVITY 结束建议为 +1 小时；当前时间向上取整到分钟但不跨本地日期，跨日边界保留当天 23:59；时间轴明确时刻按分钟向下取整；月份选择保留所选日期；DEADLINE 默认纯日期；普通 MEMO 仍无日期。DEADLINE 普通日期默认仍保留原 `0 / DAY_START` 提醒配置；时间轴精确时刻仍默认提醒关闭。日期选择器 fallback 也使用同一次捕获值。
- 月份选中今天时，表单默认时刻与日期/时间选择器 seed 共用同一向上取整分钟；选中其他日期时采用当前本地时分作为该日期的建议时间。
- Web 暂存前的事件序号输出为 JSON `null`，避免将尚未分配的临时会话伪装成事件序号 0。

## 验证

定向测试命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --console=plain --tests dev.powerfulcalendar.core.EditorDefaultsTest --tests dev.powerfulcalendar.core.NewEventEditSessionTest --tests dev.powerfulcalendar.ui.v2.WebSkinEditBridgeTest --tests dev.powerfulcalendar.ui.v2.NewEventEditorComposeTest --tests dev.powerfulcalendar.ui.v2.DirectEventEditorComposeTest --tests dev.powerfulcalendar.ui.v2.LifecycleCheckpointGateTest
```

最终定向回归 **28/28 PASS（6 suites）**；覆盖固定 Clock、四类默认、ZoneId/跨午夜/分钟边界、月份/时间轴选择、Diff 归一化、每类打开/关闭 20 次、序号/队列高水位、修改后恢复、关闭与生命周期幂等、SQLite 恢复、无中间草稿正式发布、不完整草稿续存、Web bridge 和 Compose 编辑器。

完整门禁命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

结果：**BUILD SUCCESSFUL**；514/514 JVM 测试、70 suites，0 failures/errors/skips；88/88 Gradle tasks；Debug 与 AndroidTest APK 均成功构建；lint 0 errors / 56 warnings。

API 35 `emulator-5554` AndroidJUnitRunner：**2/2 PASS**。真实 Compose 对话框逐一打开四种事件并按 × 退出，草稿、正式事件、identity、`next_seq`、`next_order` 和 commit 计数始终不变；另一个用例修改 Memo 标题后按 ×，恰好产生一张待处理草稿。使用随机数据库名并在 finally 中删除；测试后对应名前缀数据库数为 0。未操作 `emulator-5556`。

AVD 原有 app 与 test APK 在测试前备份，测试后重新安装并以 SHA-256 核对一致；原 app 版本仍为 905 / 0.9.5-rc.1。未清数据、卸载应用或 wipe AVD。实体设备未由本次自动化验证，按用户分工留给用户执行。

一次定向测试初次失败是新加队列高水位断言把“下一可用值”误写为 1；SQLite 初始 `next_order=1`，分配第一张草稿后应为 2。已保留失败日志，修正预期后定向与完整测试通过；这是测试预期错误，不是产品测试失败。

## 证据

本地详细证据目录：`.local/android-test/reports/freeze-20261025/pc-phase-1-editor-lifecycle/`。

- 最终完整 Gradle 日志 SHA-256：`E2476CF1BCF9A9D2A99FC3641B05ADDF58899A08ADD791C30979586CE0F7CDA8`
- 最终定向 Gradle 日志 SHA-256：`D01A3DA9F7CFAAB8E27FA8A8172859946567BAC60CAB4B2DA292CF60F08EF4E6`
- API 35 Instrumentation 日志 SHA-256：`E63216AB319C4A8FB5715892874670FE11E8CE1B7055026239707F9AB117AF50`
- Debug APK（versionCode 905 / 0.9.5-rc.1）SHA-256：`3A066D385FF3A327DC756B86C410FBA4EF31E8C9DF4C9B0C91DEF6734453C359`
- AndroidTest APK SHA-256：`FFAD98FFE11871B0C4CDC4DD645171EB6AD35A3620251FFBCAF34F944C4E6C0F`
- 完整 JUnit XML 保存在 `full-junit/`，最终定向 XML 保存在 `focused-junit-final/`；修正队列断言前的失败现场保存在 `queue-order-assertion-failure.log` 及对应 XML 目录。

## 残余风险与范围

- 设备强制停止、系统杀进程/重建期间的完整端到端行为未在 AVD 中执行；Robolectric 覆盖真实输入的存储 checkpoint 与 SQLite reopen，API 35 AVD 覆盖 Compose 开关与暂存。实体设备由用户验证。
- 现有表单以本地分钟文本表达时刻，无法编码 DST 秋季回拨的两个同名墙上时间；`LocalDateTime.atZone` 对歧义时间采用现有较早 offset 行为。本阶段未扩展持久化/编辑格式，因此该折叠小时的默认 now 对应精确 instant 尚未签核。
- 本次未开发重复事件单次/未来编辑、键盘布局、后台提醒、AI 多轮提案，也未重判这些项目。
- Phase 1 的自动化与 API 35 AVD 子项通过；Phase 2（重复事件 scope 编辑）具备开工条件。整体核心冻结仍以现有验收矩阵和未关闭项为准，不能据此宣告 GO。

实现提交 SHA：`b46b3dd7592bb1c7de021d543630f84937a0eaef`。

## 当前基线复核（2026-10-25）

本节是在当前 `main` / HEAD `1d8d437117344da36d13674fd3bf00607c27a76c` 上的复核记录；Phase 1 实现仍来自已推送提交 `b46b3dd7592bb1c7de021d543630f84937a0eaef`。复核未修改产品源码。普通时间轴、月份、Memo、PowerCal Web action 与 WebSkin bridge 的新建入口仍经 `NewEventEditSession`；剩余直接 `newDraft` 调用属于真实 Diff 检查点、文本/ICS 导入或 AI 提案。

定向回归命令沿用上文，最终结果 **31/31 PASS、6 suites、0 failures/errors/skips**。日志 SHA-256：`FDF732868B90048C79AAD20CEB6FF252E207961EC812679F925CB0476633265D`。

当前完整门禁 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain`：**522/522 JVM tests、70 suites、0 failures/errors/skips、88/88 tasks**；lint **0 errors / 56 warnings**。完整日志 SHA-256：`CBA03F198A54FF3DF380C54A91B5B9A52EFCF9A3B1FD56A9329A0429525F1014`。Debug APK SHA-256：`90BB816EC85AFD46C195C48697F1234D98D2C4571FB5209F0F1FD87B088DBDE8`；AndroidTest APK：`0C2F145987D5AAC51F19E0713BF8DD0B64B10C8B8F33A9772B2BF80899CCBF66`；lint XML：`BC35B853A90869F00471C96B11B6CDFA2B457DFC03C5D654B74CAB3ED83DF7DF`。

API 35 `emulator-5556` AndroidJUnitRunner 对原生 Compose 编辑器 **2/2 PASS**：四类事件打开/退出保持持久化计数不变；修改标题后退出恰好暂存一张草稿。只使用随机隔离 SQLite。设备原 app/test APK SHA-256 分别为 `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1` / `4D04D8DF1CA9D91516146BD62E4F6E630E7CF946A1ABF1217F66C8E035965E8C`，测试后恢复值与原值一致。数据库文件名清单前后均为 2 项，SHA-256 同为 `1339CD66F7353794C16E08D6BCC0E55E958C1FFD36D740CD19CC934792B113EA`。5554 前台处于 Google 日历首次启动页，本次未触碰。Instrumentation 日志 SHA-256：`307AA45604D9CE6624A3047C70670AADCBFD7D52C0DA79DE6C5EA8A8C7CCE588`。

本次复核日志与完整 JUnit XML 保存在 `.local/android-test/reports/freeze-20261025/pc-phase1-review-20261025/`。AVD 子项不覆盖 Activity 重建/进程强制终止或实体/OEM 行为；存储检查点与 SQLite reopen 由现有 Robolectric 测试覆盖，实体设备仍由用户执行。DST 秋季回拨的单次墙上时间表达限制仍未签核，核心冻结继续 HOLD。

## 当前基线复核（2026-10-25，HEAD `5e2b25ac5760957089254c13cd6dbbdb080b6269`）

本次只读审计后未发现 Phase 1 产品代码回归，因此没有重复修改已提交的实现。时间轴、月份、Memo、PowerCal Web action 和 WebSkin bridge 的普通新建仍进入 `NewEventEditSession`；代码中其余 `newDraft` 调用是有真实 Diff 的 checkpoint、显式文本/ICS 导入、AI 提案及事件移动候选。`DraftForm.fresh` 的 09:00 兼容工厂在应用新建入口没有调用点；正式新建通过单次捕获的 `EditorDefaultsSnapshot` 计算时间与日期选择器 seed。

当前工作区定向回归：31/31 PASS、6 个测试类、0 failures/errors/skips。完整离线门禁命令沿用上文，522/522 JVM tests、70 suites、0 failures/errors/skips、88/88 tasks；source boundary、Debug / AndroidTest APK、lint 均通过，lint 0 errors / 56 warnings。此次完整构建包含工作区既有的 D05 AndroidTest-only 探针改动；这些文件未纳入 Phase 1 文档提交。

API 35 `emulator-5556` 对 `NewEventEditorLifecycleInstrumentedTest` **2/2 PASS**：四类会话在 Compose 编辑器打开后按 × 退出，草稿、正式事件、identity、`next_seq`、`next_order` 与 commit 均保持零增量；真实修改 Memo 标题后退出只生成一张草稿。测试数据库使用随机隔离文件并由用例清理。测试前后数据库文件名清单相同；测试前备份的 app/test APK 在结束后重新安装，测试后 SHA-256 与备份一致。未清数据、卸载应用或触碰 `emulator-5554`。

当前复核证据位于 `.local/android-test/reports/freeze-20261025/pc-phase1-current-5e2b25a/`：

- 定向 Gradle 日志 SHA-256：`91C004887150454A89EF82D45E728EF76B189A5332708DD4315E80321D788803`；定向 JUnit XML 在 `focused-junit/`。
- 完整 Gradle 日志 SHA-256：`C37EFB1B531EF22AD10648D689205888808B069122C5882A8CF2EEB56CEAB4BF`；522 项 JUnit XML 在 `full-junit/`。
- AVD Instrumentation 日志 SHA-256：`A31D254958F69C6B206A6787341280C5DB3E902E99829EB79FD2CC144E3D4548`。
- Debug APK SHA-256：`1601F2DB169A8C889791EBFE4149F4383E71980474452C8CFE22BA9A7C92A9EC`；AndroidTest APK：`9FAA01339612CA2DF1871CD047512C282F19339C1E5A7766E4E512E80B0FDFFD`；lint XML：`BC35B853A90869F00471C96B11B6CDFA2B457DFC03C5D654B74CAB3ED83DF7DF`。
- `emulator-5556` 测试前后 app APK SHA-256 均为 `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1`；test APK 均为 `4D04D8DF1CA9D91516146BD62E4F6E630E7CF946A1ABF1217F66C8E035965E8C`。

Phase 1 的自动化与 API 35 Compose 范围维持 PASS，实体设备和秋季 DST fold 的分钟文本歧义仍未签核；整体核心冻结保持 HOLD。本次为基线复核和报告补录，没有重新打开已关闭的 Phase 2/3，也没有启动其他阶段。

## HEAD `6ddf9b98` 再验证与 AVD 数据恢复缺口（2026-10-25）

本次从隔离 worktree `HEAD 6ddf9b98a4520f804b0b1e3a5e9d0ca973595f14` 复核，未改产品源码，也未触碰主工作树中其他 Agent 的 AI 修改。定向测试 **31/31 PASS、6 suites**，日志 SHA-256 `88F8FFD48A62A91ADDF6947107CF15DF9842439DE4B60CE0E0EDECDF05B51059`；对应 6 份 JUnit XML 保存在 `focused-junit/`。完整离线门禁 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain` 为 **521/521 JVM tests、69 suites、0 failures/errors/skips、88/88 tasks**；lint **0 errors / 53 warnings**。Debug APK SHA-256 `1601F2DB169A8C889791EBFE4149F4383E71980474452C8CFE22BA9A7C92A9EC`，AndroidTest APK SHA-256 `9FAA01339612CA2DF1871CD047512C282F19339C1E5A7766E4E512E80B0FDFFD`。

API 35 Instrumentation 中 `NewEventEditorLifecycleInstrumentedTest` 在两台在线 AVD 各 **2/2 PASS**。但 `:app:connectedDebugAndroidTest` 没有遵守传入的单设备筛选参数，自动同时运行 `emulator-5554` 和 `emulator-5556`。任务结束后 package manager 已找不到两台设备上的 `dev.powerfulcalendar` / `.test` 包；将事前备份 APK 重新安装后，应用私有 `databases/` 目录均不存在。结果是两台 AVD 原有的 `powercal-v2.db`（各 139264 bytes）不再可用。本次没有发出 wipe 或 `pm clear` 命令，但不能声称 AVD 数据已保留，也无法从当前安装恢复原数据库。

`emulator-5556` 测试前 app/test APK 已备份并逐字节恢复，哈希为 `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1` / `4D04D8DF1CA9D91516146BD62E4F6E630E7CF946A1ABF1217F66C8E035965E8C`。`emulator-5554` 未在测试前捕获 APK；测试后仅安装了同一组已记录的 0.9.5-rc.1 基线字节，因此不能证明其 APK 与测试前完全一致。两台设备均未在重装后启动 PowerCal。

只读检查找到 `emulator-5554` 的外部 `powercal-backup.json`，文件时间为 2026-10-22 23:54 UTC，SHA-256 `FFCC82BE48A16C0F0EA8342E065FA14B6682FDAECCFC34D6C3C92E62D1CE1CB9`；它与已有主机副本相同，但早于原 AVD 数据库文件时间。另一只读 DB 副本来自 2026-10-23，大小 147456 bytes，也早于原数据库且大小不一致。未将这些旧候选覆盖到 AVD。候选副本、安装包快照、数据库文件名/大小记录、Gradle 日志及测试结果 XML 保存在 `.local/android-test/reports/freeze-20261025/pc-phase1-verification-head-6ddf9b9/`。

因此，**Phase 1 源码行为与自动化验收仍为 PASS，但本轮 AVD 数据保全未闭环，交付状态暂留 HOLD**。实体设备未触碰。需要取得与 2026-10-24 原数据库对应的可验证备份，或由所有者决定是否接受 AVD 以空数据重建；在此之前不启动这两台 AVD 上的 PowerCal，也不使用旧备份自动恢复。Instrumentation 2 个测试在每台 AVD 都通过，JUnit XML 位于 `avd/instrumented-xml/`；Instrumentation 日志 SHA-256 `5023C6C11D4B42782287020C68FEA5CCE1D6005A8D27EFF6C9132643CC4A2F34`；完整 Gradle 日志 SHA-256 `49B83BB32FEB4B428705C54355BD5339B44C33DE4DB13462B785D77797E79411`。

## HEAD `d600aafd` 当前提交基线复验（2026-10-25）

在独立 detached worktree `d600aafd82024dd589ca708a957b8678ca3092ac` 复验；主工作区的 E01 未提交源码、测试、`design/` 与 Issue 资产均未纳入测试或修改。定向命令沿用本报告第一节，六个 Phase 1 测试类 **31/31 PASS、0 failures/errors/skips**。完整离线门禁 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain` 为 **521/521 JVM tests、69 suites、0 failures/errors/skips、88/88 tasks**；lint **0 errors / 53 warnings**。

本轮未运行任何 connected/device task。此前两台 AVD 的私有数据库缺失仍未获恢复或重建处置，因此不启动它们；本轮不新增 AVD 行为证据。已有 API 35 Compose 2/2 结果仅作为前次证据，不能称为本轮复测。实体设备仍由用户验证。

- 定向 Gradle 日志 SHA-256：`11559DFD1BA9939BF831EA92863A2F2236B3D05BA663B0FD05ACF0AB296AB32B`
- 完整 Gradle 日志 SHA-256：`256063281337D411A009F19F49C2F00D87C4B48CB2899FDBF6E083EB689A361B`
- Debug APK SHA-256：`1601F2DB169A8C889791EBFE4149F4383E71980474452C8CFE22BA9A7C92A9EC`；AndroidTest APK：`9FAA01339612CA2DF1871CD047512C282F19339C1E5A7766E4E512E80B0FDFFD`
- 本轮日志及构建产物位于 `.local/android-test/reports/freeze-20261025/phase1-review-d600aaf/`；本地定向 JUnit 汇总为 EditorDefaults 6、NewEventEditSession 6、DirectEventEditorCompose 6、LifecycleCheckpointGate 1、NewEventEditorCompose 2、WebSkinEditBridge 10。

本次复验没有 Phase 1 产品源码变更。普通新建编辑会话、统一默认值和幂等保存路径在当前提交基线继续通过 JVM 自动化与构建；AVD 原数据库恢复/所有者处置仍未闭环，因此保留该数据保全风险和此前记录的 HOLD，不将本轮结果扩大解释为实体设备或 AVD 新 PASS。
