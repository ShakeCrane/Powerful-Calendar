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

## HEAD `694ba1b` 当前基线复验（2026-10-25）

只读复核确认普通新建入口继续使用内存 `NewEventEditSession`。`HEAD 694ba1b48c8df502b4a911367217ee2dac2538d1` 相对已验证的 `d600aaf` 只有本报告与进度文档变化，`app/` 源码逐文件一致。本轮在干净 detached worktree 执行，主工作区未提交的 E01 修改及受保护资产没有进入构建或暂存。

Phase 1 定向命令在本轮 **BUILD SUCCESSFUL**；完整回归 JUnit XML 中对应六个测试类为 **31/31 PASS、0 failures/errors/skips**。完整离线门禁 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain` 为 **521/521 JVM tests、69 suites、0 failures/errors/skips、88/88 tasks**。Debug 与 AndroidTest APK 均构建成功；lint **0 errors / 53 warnings**。

- 证据目录：`.local/android-test/reports/freeze-20261025/phase1-final-head-694ba1b/`；包含两份 Gradle 日志、69 份完整 JUnit XML、两个 APK 及 lint XML。
- 定向 Gradle 日志 SHA-256：`79C16272FE4FB10C91BAB6D3B8D9CAD9A1973386A54B411C69A78746F8EE7ED7`；完整门禁日志：`F878B52FAFE50DE40953495E05A04E23EE88FFA7A0E94E0EFC2177CD5174CB06`。
- Debug APK SHA-256：`1601F2DB169A8C889791EBFE4149F4383E71980474452C8CFE22BA9A7C92A9EC`；AndroidTest APK：`9FAA01339612CA2DF1871CD047512C282F19339C1E5A7766E4E512E80B0FDFFD`；lint XML：`B213956F57C63A719B6A56F2AC1F25BB2E5484C5806845FE68BD6F4748DC055A`。

本轮没有运行 connected/device task。两台既有 AVD 的私有数据库恢复/处置仍未闭环，因此没有启动它们；更早的 API 35 Compose 2/2 结果仍为历史证据，不计作本轮复测。实体设备仍由用户执行。Phase 1 源码与 JVM/构建验收为 PASS，AVD 新复测为 BLOCKED，核心冻结维持 HOLD；秋季 DST fold 分钟文本无法区分两个同名墙上时间的限制仍未签核。

## 当前任务复验（2026-10-25，HEAD `7ceb7fa`）

本轮按 Phase 1 执行请求重新审计并验证。当前 HEAD `7ceb7fa8b65aefd4174cf0420d1d166486c89484` 的生产源码与此前审计的 `6ddf9b9` 完全一致；Phase 1 实现已在 `b46b3dd7592bb1c7de021d543630f84937a0eaef` 提交。本轮新增一条 Compose 回归测试，产品源码未改。主工作区现有 E01 AI 多轮未提交改动和受保护文件没有进入本次 Phase 1 worktree、暂存或提交。

入口审计确认时间轴、月份、Memo、PowerCal Web action、WebSkin bridge 普通新建均使用内存 `NewEventEditSession`。剩余直接 `newDraft` 调用仅用于用户真实修改的 checkpoint、文本/ICS/AI 明确候选以及拖动修改草稿，不在打开普通编辑器时运行。定向测试覆盖四类事件各 20 次打开/关闭计数不变、业务 Diff 修改后恢复、真实 Diff 退出恰好一张草稿、重复 checkpoint/关闭、完整发布直接创建、未完整表单续存同一草稿、SQLite reopen、固定 Clock/ZoneId、跨午夜和分钟精度，以及 Compose/Web bridge 路径。新增用例修改 ACTIVITY 开始时间后强制 Compose 重组，断言 `EditableText`、meaningful Diff 和一次捕获的 Clock 均保持不变。

### 本轮测试

定向命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests dev.powerfulcalendar.core.EditorDefaultsTest --tests dev.powerfulcalendar.core.NewEventEditSessionTest --tests dev.powerfulcalendar.ui.v2.WebSkinEditBridgeTest --tests dev.powerfulcalendar.ui.v2.NewEventEditorComposeTest --tests dev.powerfulcalendar.ui.v2.DirectEventEditorComposeTest --tests dev.powerfulcalendar.ui.v2.LifecycleCheckpointGateTest
```

结果 **32/32 PASS、6 suites、0 failures/errors/skips**。最终定向日志 SHA-256：`44DB388BEFCECE2555E8273F8F537FF31278CED3F08749FEB3D3A8365C6D41C9`。定向 XML 保存在 `focused-junit/`。

重组用例首轮因断言读取 `Text` label 而不是 `EditableText` 失败；失败现场显示真实字段值仍为 `2026-10-26 08:00`。测试改为读取 `SemanticsProperties.EditableText` 后通过，没有产品代码修复。失败 XML SHA-256 `C27075339CF6BCB7C9B3711D8B439F3BBE1772AB273EB1E70AFE36328A2215A6` 保存在 `failed-attempt-junit/`。

完整命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果 **BUILD SUCCESSFUL；522/522 JVM tests、69 suites、0 failures/errors/skips、88/88 tasks**；source boundary、Debug / AndroidTest APK 通过；lint **0 errors / 53 warnings**。完整日志 SHA-256：`24DF06304B35356240BCC986F53583E714800C5E467E3A944BB34213C8206663`。Debug / AndroidTest APK SHA-256：`1601F2DB169A8C889791EBFE4149F4383E71980474452C8CFE22BA9A7C92A9EC` / `9FAA01339612CA2DF1871CD047512C282F19339C1E5A7766E4E512E80B0FDFFD`；lint XML SHA-256：`9E719BBDAD89ED5B0D6DC54420AE52EB66DE7E1D90C7FDE113BE5D5B02324C50`。完整 JUnit XML 69 份位于 `full-junit/`；本次全部证据在 `.local/android-test/reports/freeze-20261025/phase1-final-7ceb7fa/`。

本次没有设备安装或 connected 运行。两台既有 AVD 的 PowerCal 私有数据库恢复/处置未闭环，因此本轮不启动它们；更早 API 35 Compose 2/2 结果保留为历史证据，不计为当前复测 PASS。实体设备按用户分工执行。Phase 1 自动化与构建范围 PASS；新 AVD 复测 BLOCKED；DST 秋季回拨的同名墙上时间不能由当前分钟格式区分，风险仍未签核。核心冻结仍 HOLD。

验收矩阵增补两项重建验收：`REC-P1-087`（新建会话无副作用、Diff 与保存幂等）和 `REC-P1-088`（统一默认时间与精度），均为 P1 / RECONSTRUCTED / PASS，历史 ID 映射仍为 0。完整矩阵和结构验证摘要见 `docs/agent-handoff/PROGRESS.md`。

## 当前工作树集成回归（2026-10-25，E01 未提交变更叠加）

为确认后续 E01 工作不会回归 Phase 1，在 `main` / HEAD `8d1a204bb2905956896db092ee7a62922b28c08d`（与 `origin/main` 同步）上，使用当时已有的未提交 E01 修改重跑 Phase 1 定向测试和完整门禁。此次没有改动产品源码；E01 的 `PowerStore.kt`、`PowerCalApp.kt` 与相关未跟踪测试保持原状。测试时工作树差异指纹及被编译的 E01 未跟踪源文件 SHA-256 记录在 `.local/android-test/reports/freeze-20261025/phase1-current-e01/verification.json`。

- 定向命令沿用上文，**32/32 PASS、6 suites、0 failures/errors/skips**；日志 SHA-256 `62D70DF908D9628C3814F705F8DBE12D781BFFDDC0D9E0F9138D1A7B9F243D4B`。
- 完整门禁使用上文命令，**539/539 JVM tests、74 suites、0 failures/errors/skips、88/88 tasks**；source boundary、Debug / AndroidTest APK 和 Debug lint 均完成。lint **0 errors / 56 warnings**。完整日志 SHA-256 `28D29F652321BB4DD54018D12BA1CC349BBC363E3F32A6AFF4DA1DD4213F7EC8`。
- Debug / AndroidTest APK SHA-256：`3A50D74F565A496CE8441ADC51643CB58A00949D001E789883F7C72E52FB22FE` / `EB4AC17BF9B8ED3F62DBDAA6F722791FB33D137011DC8ECB5C426FF75AD6DBF3`；lint XML SHA-256 `6254B2A5C445027068221A411E65E5798A8F8457FAD9716266253B2BFB52E4D4`。日志及结构化 manifest 位于 `.local/android-test/reports/freeze-20261025/phase1-current-e01/`。
- 本次未运行 connected/AVD 测试：既有 AVD 私有数据库恢复问题仍未闭环；实体设备由用户测试。更早的 API 35 Compose 结果继续作为历史证据，不计作本次复测。Phase 1 当前源码集成回归 PASS，AVD 新复测仍 BLOCKED，实体设备 NOT TESTED；秋季 DST fold 精度限制未解决，核心冻结保持 HOLD。
- Phase 1 已具备关闭并交接条件；Phase 2、Phase 3 已有各自独立交付记录。后续可以继续独立的开放冻结项（包括 E01），但这份集成回归不签核那些项目，也不改变总体验收 HOLD。

## 当前集成版本 Android 15 AVD 复验（2026-10-25，代码提交 `2692ca2`）

Phase 1 产品修复仍由 `b46b3dd7592bb1c7de021d543630f84937a0eaef` 提供；本次未改 Phase 1 产品源码。为避免使用私有数据库缺失状态未厘清的旧 AVD，在新建隔离 API 35 `emulator-5560` 上直接运行现有 `NewEventEditorLifecycleInstrumentedTest`，只用测试包随机命名 SQLite 文件并由 `finally` 清理。安装在该 AVD 的 app/test APK 与本机最终构建逐字节一致：SHA-256 分别为 `30C3AAED678F3569AB2ECB350A5298236EE37BA6DE27C169714D63222D9F5964` / `F55837EA885CE7B72FEE3D67AC011A6EAD1237F3A8DEAEFB9806550FD550E5CC`。

- `openingAndClosingAllKindsInComposeDoesNotAllocatePersistentRowsOrSequences`：四类事件分别打开并按 × 关闭，草稿、待处理、正式事件、identity、next sequence、next queue order 与 commit 均无增量。
- `changingTitleThenClosingInComposeStagesExactlyOneDraft`：真实修改 Memo 标题后退出，恰好一张待处理草稿，无正式事件或 commit。
- API 35 AndroidJUnitRunner **2/2 PASS**；日志 SHA-256 `B288A0BF52E989362CF8F4E7008C3880734BFD54DE7A696050A17994A5F3BD0F`，`.local/android-test/reports/freeze-20261025/pc-issue-172-current-review/phase1-current-2692-5560.log`。测试后测试包的 `pc-phase1-*` 隔离数据库文件检查无残留；5554/5556 未启动或触碰。
- 同一集成源码快照的完整离线 Gradle 门禁 **540/540 JVM、74 suites、0 failures/errors/skips、88/88 tasks**；Phase 1 六个相关 suite 为 **32/32 PASS**；lint 0 errors / 56 warnings。最终日志 SHA-256 `CBE1B167E33551AB2455CE07E3A9D68D42A2A7E09AAB169182442EFF3E35F60D`；Debug / AndroidTest APK 与 lint hash 见 PC-ISSUE-172 的 verification manifest。
- 矩阵结构复核仍为 218 rows / 217 active reconstructed；203 PASS / 0 FAIL / 10 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；历史 ID 映射 0。`REC-P1-087` 现引用本次 API 35 AVD 证据。Ledger SHA-256 `4381A8E51CE74E184C2AC5D0C99EEFCCC629548258EFB4F44F86A2E9C3B5CC03`；validator JSON SHA-256 `D3D3E2DE077E2B2209F0863D965038C59A17319D093BB6B5731588B9ABF2DC94`。结构校验不替代行为测试。

本次虚拟机证据不代表实体/OEM 验收；实体设备由用户执行。Phase 1 生命周期及默认时间自动化和 API 35 Compose 范围 PASS；秋季 DST fold 墙上分钟文本无法区分两个同名时间的精度风险仍未签核。整体核心冻结继续 HOLD。

## 当前主线复验（2026-10-25，HEAD `68cad08`）

本次复验没有改动产品源码。Phase 1 对应的生产文件在 API 35 基线 `2692ca2` 与当前 HEAD 间无差异；当前产品代码提交为 `9f9e82ae583a5d582e5b8b4307db98ec5606f149`，HEAD 的后续差异仅为文档。当前工作区受保护的未跟踪测试、`design/` 与观察 Issue 均未暂存或修改。

- 当前 HEAD 定向命令沿用本报告定向命令，结果 **32/32 PASS、6 suites、0 failures/errors/skips**。日志 SHA-256：`838F4DB4402A271FC06C7C752AF06D63FCC7C2199CC248D0E414E0CBB01E79B1`，位于 `.local/android-test/reports/freeze-20261025/phase1-current-main-68cad08/phase1-focused-current-head.log`。
- 当前产品代码快照的完整离线 Gradle 门禁由同一代码提交上的 PC-ISSUE-164 最终门禁执行：**541/541 JVM tests、74 suites、0 failures/errors/skips、88/88 tasks**；source boundary、Debug/AndroidTest APK 和 lint 均通过，lint 0 errors / 56 warnings。日志 SHA-256：`3BCA1C55B5350479176B05FDF84D771352AEE4CD396322CF4D08ECD5141ED95E`。该门禁后至当前 HEAD 没有 `app/` 路径差异。Debug / AndroidTest APK SHA-256：`517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614` / `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`。
- 在 API 35 隔离 AVD `emulator-5560` 上以显式设备序列直接运行 `NewEventEditorLifecycleInstrumentedTest`，**2/2 PASS**。测试前后应用私有数据库文件名及 SHA-256 完全相同：`powercal-v2.db`=`edc44d74ded0fd54a094a2db00b52b30623d2ac1e30af65991e06cdbc206bb4a`，空 journal=`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`；无 `pc-phase1-*` 测试库残留。安装 APK 与当前本机构建逐字节一致。Instrumentation 日志 SHA-256：`FF5AAD38B385BAE0EAEC59B2013C8002F709C472148B1E0FFD9DCF08EE037BD9`；数据库前后快照文件均为 SHA-256 `43BCD1BBADE1697912CD541AC5231DBA2167C6F46617D6E4D4A20E77EB8EFD43`。
- 验收台账结构校验通过：218 rows / 217 active reconstructed；204 PASS、0 FAIL、10 BLOCKED、2 NOT TESTED、1 SPEC UNRESOLVED、1 RETIRED；历史 ID 映射 0，结构校验明确 `product_behavior_tested=false`。当前台账 SHA-256 `91C721FBA0D1C2295535A200E30937744A67C3F8BBF04F34E32E47F078AAC2E0`；验证器自身单元测试 2/2 PASS。日志分别为 `.local/android-test/reports/freeze-20261025/phase1-current-main-68cad08/ledger-structural-validation.log`（SHA-256 `FD59C3909B236ECF5516B35FC1E0B4B842739B09949C73CE4B105BE233649C47`）及 `ledger-validator-unit-tests.log`（SHA-256 `F761C217955B2DCCBB9E9921C5E179AF3286F01E8F07797CCA85F7D3EA11CBFF`）。

本次仍未测试实体设备/OEM 行为；单次事件 fall DST fold 的同名墙上分钟精度限制仍未签核。Phase 1 可关闭并交接；Phase 2、3 已有独立交付，本次不重开。其他冻结项继续按当前矩阵推进，核心总判定仍为 HOLD。

## 当前请求复验（2026-10-25，HEAD `72372ad`）

只读入口审计再次确认普通新建仅打开内存 `NewEventEditSession`：时间轴、月份、Memo、PowerCal Web action 和 WebSkin bridge 共用会话。剩余 `PowerStore.newDraft` 调用属于真实 Diff checkpoint、用户明确触发的文本/ICS/AI 候选、正式事件恢复或拖动修改草稿。Phase 1 生产源码从 `68cad08` 到当前 HEAD 没有差异；实现仍由已推送提交 `b46b3dd7592bb1c7de021d543630f84937a0eaef` 提供。

- 定向回归：**32/32 PASS，6 suites，0 failures/errors/skips**；包括四类事件各 20 次开关不分配持久身份、Diff 恢复基线、仅真实修改暂存一次、生命周期 checkpoint/reopen、显式完整发布幂等、不完整表单续存同一草稿、固定 Clock/ZoneId/分钟边界、Web bridge 与 Compose 重组。日志 SHA-256 `529C321268D5587D9865A0ECB7F6CF95C0D49AED9E0BA71A7A347C427AB78158`。
- 为隔离主工作树其他 Agent 的未跟踪资产，在同一提交 `72372adbcc2fef890c85bcca8b6835560e090b9b` 的干净 detached worktree 完成完整离线门禁：**540/540 JVM tests、73 suites、0 failures/errors/skips、88/88 tasks**；source boundary、Debug 与 AndroidTest APK 构建成功；lint **0 errors / 53 warnings**。完整日志 SHA-256 `013AC0BAC61B0990507D97B46CF7005ADC7CA7E521C67690E7F4A2F6D9CAAACB`。Debug / AndroidTest APK SHA-256 `517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614` / `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`。
- 主工作树同命令的完整门禁未通过：受保护的未跟踪 `D05ProductionReminderLifecycleInstrumentedTest.kt:142` 引用未导入的 `assertFalse`，阻断 `compileDebugAndroidTestKotlin`。没有修改或暂存该文件。失败日志 SHA-256 `C032A1104C865759F212EEADAE7BA14C7FB39CAE6717F1F0B21C0D8D4EEF1A78`；Phase 1 定向测试不受此编译缺陷影响。详细日志位于 `.local/android-test/reports/freeze-20261025/phase1-task-72372ad-8c41/`；完整 JUnit XML 与 APK 留在 `.local/android-test/worktrees/phase1-72372ad/`。
- 本轮未连接设备。已归档的 API 35 `emulator-5560` Compose 2/2 结果可对应本轮相同 SHA-256 的 app/test APK，但仍作为既有证据而非本轮新跑结果；实体设备由用户验证。

Phase 1 主路径及当前源码自动化/构建验收 PASS。单次本地时间在秋季 DST fold 的第二次同名分钟无法由现有 `yyyy-MM-dd HH:mm` 字段表达精确 offset；产品合同明确未定义 one-off fold 规则，需保留为 SPEC UNRESOLVED，不能以重复事件的“第一次”规则自行外推。整体核心冻结继续 HOLD。

## 当前主线定向复验（2026-10-25，HEAD `d943ddc`）

本轮按当前 Phase 1 请求重跑六个定向 suite；生产源码仍无修改，普通新建生命周期与默认值实现继续由已推送提交 `b46b3dd7592bb1c7de021d543630f84937a0eaef` 提供。结果 **32/32 PASS，6 suites，0 failures/errors/skips**。

- Gradle 日志：`.local/android-test/reports/freeze-20261025/phase1-current-d943ddc-focused.log`，SHA-256 `C8C9A0513F0FE782CDB721777D06F83F6735EF904966C951F39D411F1EACEB8A`。
- JUnit XML SHA-256：`EditorDefaultsTest` `D2F76373DAAD1CD8C498E517875C6CA9CF7DE68EBDDE43CDCA79FADC3AFF77B9`；`NewEventEditSessionTest` `82D9A97CC03D836504022C9542BC5D8E71CC8C6BB9378E992B44A5B84A2C94B6`；`DirectEventEditorComposeTest` `7C55D2EED5EF79BD3CE308677CDDF22F7843488E3EBADA03954CC7BCAD229409`；`LifecycleCheckpointGateTest` `ABF0F1E972D7707CE32AAFE581AA354979888519FAB50C8F7FFD6A25F29DA737`；`NewEventEditorComposeTest` `BAC12B71F38CFECF085C04663DED79B3DD54955BA56E99D2F6EC75A43DEE39E6`；`WebSkinEditBridgeTest` `E7364A8DBAFEA308DA72453BC20C822EBF1B91EE36CC21EE3F0375A5C4C0D89A`。
- `d943ddc` 相对下一节 `3c8b16d` 仅增加文档提交，tracked app 源码相同；该源码基线的干净 worktree 完整门禁为 **540/540 JVM、73 suites、88/88 tasks**，source boundary、Debug/AndroidTest APK 与 lint 通过，APK SHA-256 为 `517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614` / `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`。本次定向复验不运行设备任务；已有 API 35 Compose 2/2 仍标作 PREVIOUS PASS，实体设备仍由用户测试。

本次不改生产或测试源码。单次事件 fall fold 的 offset 语义仍未定义，故本次 PASS 仅覆盖已定义且经回归的 Phase 1 行为；该语义边界继续保持 **SPEC UNRESOLVED / HOLD**，不据此签署完整 Phase 1 时间语义。

## 当前用户请求复验（2026-10-25，HEAD `3c8b16d`）

本轮重新审计分支、HEAD、工作区和 Phase 1 调用链。`main` 与 `origin/main` 同步；跟踪源码无差异。普通新建入口仍只构造内存 `NewEventEditSession`；保存字段归一化 Diff、×/lifecycle checkpoint、正式发布幂等与 AI/ICS 显式候选边界均与原报告实现一致。Phase 1 生产修改仍在已推送提交 `b46b3dd7592bb1c7de021d543630f84937a0eaef`；本轮没有新的生产源码修改。

- 定向命令沿用本报告六 suite 命令，**32/32 PASS、6 suites、0 failures/errors/skips**。日志 SHA-256 `086E31B6BB0523DC4A5FDA6D627178A8F8687788058C1DE0095337D90A9DF025`；focused JUnit XML 位于 `.local/android-test/reports/freeze-20261025/phase1-user-request/focused-junit/`。
- 当前工作树完整 JVM 回归与本请求范围的构建门禁：

  ```powershell
  .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
  ```

  结果 **541/541 JVM、74 suites、0 failures/errors/skips、59/59 本次命令涉及任务**；lint **0 errors / 56 warnings**。541 项包含受保护的未跟踪 `DraftDeckFeedbackTest.kt`，没有暂存。完整日志 SHA-256 `47B76C79FE412556C10FC93CF326E09865664A62E2FA9D52C91F9E45B6977104`；完整 JUnit XML、lint XML、日志副本和 Debug APK 副本位于 `.local/android-test/reports/freeze-20261025/phase1-user-request/`。Debug APK SHA-256 `517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614`，lint XML SHA-256 `6254B2A5C445027068221A411E65E5798A8F8457FAD9716266253B2BFB52E4D4`。
- 同一 HEAD 先前在干净隔离 worktree 执行的完整门禁（包含 `assembleDebugAndroidTest`）为 **540/540 JVM、73 suites、88/88 tasks 全通过**，AndroidTest APK SHA-256 `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`，见前节证据。本轮主工作树未重复该子任务，因为受保护的未跟踪 `D05ProductionReminderLifecycleInstrumentedTest.kt` 在 `assertFalse` 处存在已记录编译错误；哈希 `4FA8637731FEE4F109E2E017D2D164635876BC0551B6C8FC74F99B8F7461050D` 未变，文件未修改/暂存。
- 当前 `adb devices` 显示 `emulator-5554 / 5556 / 5560 / 5562` 全部已连接，本轮没有操作设备。先前 `emulator-5560` 的 Phase 1 Compose AndroidJUnitRunner **2/2 PREVIOUS PASS**；当时验证的生产 Debug APK hash 与当前构建相同，且从设备证据基线 `68cad08` 到当前 HEAD 无 tracked `app/` 路径差异，故只引用为历史结果。实体设备/OEM 仍由用户验收。

### 当前未签核项

合同的 2026-10-24 DST 修订只规定重复事件回拨时用第一次 offset，并明确一次性本地时间解析尚未定义。若捕获时刻是 fall fold 的第二个 01:30，现有 `yyyy-MM-dd HH:mm` 格式无法携带 offset；再解析时 `LocalDateTime.atZone` 选择第一次，可能把以“当前时间”为默认的 ALARM/ACTIVITY 映射到早一小时。不能把重复事件规则未经确认地扩展到一次性事件。该边界已请求用户裁决；裁决后需补固定 Clock 回归并评估最小兼容实现。在此之前，Phase 1 代码与自动化主路径 PASS，但严格完成签核 **HOLD**；核心总体验收仍为 **HOLD**。

## 当前请求的复验（2026-10-25，主线 HEAD `7cb847e`）

只读复核后，在隔离 worktree 对当前已提交应用树执行复验。worktree 基点为 `465c9ec`；从该基点到当前 `7cb847e` 的 `app/` 差异只有 `GlassComposeTest.kt`，其 SHA-256 与主线相同（`73ECDE4D6B7BA0D64D0CE6DC6F9AC4EA25C69AF41B26729493178FA54E7EFFEA`）。因此以下完整门禁覆盖当前主线全部已跟踪应用代码；它没有纳入主工作区的未跟踪 D05 探针和 `DraftDeckFeedbackTest.kt`。

- Phase 1 六个定向测试类 **32/32 PASS**，0 failures/errors/skips。日志：`.local/android-test/worktrees/pc-issue-173-glass-fallback/.local/android-test/reports/freeze-20261025/phase1-request-7cb847e/focused-phase1.log`，SHA-256 `F6E14EA76A3A33587F96576BDFC2A328254CE9A271D599CB37BD2EB22013E4BF`。
- 完整命令 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain` **542/542 JVM、74 suites、0 failures/errors/skips、88/88 tasks**；source boundary、Debug / AndroidTest APK 均成功；lint **0 errors / 53 warnings**。完整日志位于 `.local/android-test/worktrees/pc-issue-173-glass-fallback/.local/android-test/reports/freeze-20261025/phase1-request-7cb847e/full-offline-gate.log`，SHA-256 `476B42F7710FA358CF4F8B92E99864AD7B897C7FF8DFFA2F777BE774D1AD08B7`。
- Debug APK SHA-256 `517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614`；AndroidTest APK SHA-256 `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`；lint XML SHA-256 `B95E8834C3E3EFA3386E0391464F11FB5953ED428EA7C87FA70CE020661628C0`。完整 JUnit XML 在 worktree 的 `app/build/test-results/testDebugUnitTest/`；Phase 1 六个类合计仍为 32 项。
- 本轮没有运行 connected/AVD 测试：检查时四台 AVD 均已在线，且此前有私有数据恢复缺口；不打断或改写它们。先前 `emulator-5560` 的 Phase 1 Compose 2/2 继续标为 **PREVIOUS PASS**，不是本轮证据。实体设备由用户验收。

普通新建会话、业务 Diff、保存/退出/checkpoint 幂等和四类默认值的自动化及构建验收维持 PASS。仍未签核的唯一 Phase 1 产品语义边界是：DST 秋季 fold 中一次性本地时间的两个同名墙上时刻如何解析。当前格式无法携带 offset；合同只为重复事件规定“第一次”，并明确没有定义一次性事件规则，因此不得外推。若要求严格签核这项时间语义，需要产品所有者先确定策略；总体验收仍保持 HOLD。
