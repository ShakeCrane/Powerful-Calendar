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

实现提交 SHA：将在提交完成后补录到本报告及 `docs/agent-handoff/PROGRESS.md`。
