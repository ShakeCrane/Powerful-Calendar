# PowerCal 真机首轮缺陷审计与修复计划（2026-10-25）

状态：Phase 1、Phase 2（P08）及 Phase 3 D01b 已完成各自自动化和可用的 API 35 AVD 子项；Phase 4 D05 的 PowerCal 生产路径已在 API 35 AVD 覆盖进程终止、强停后重开与系统重启恢复，实体/OEM 验收仍 BLOCKED；Phase 5–6 尚未完成；正式冻结 HOLD。
审计基线：Git 40e78bf（来源为当前仓库只读检查），对照用户实机反馈和 docs/CORE_CONTRACT_FREEZE_2026-10-21.md 的已修订合同。
范围：问题归因、复现方案、实现次序和验收门槛。任何源码猜测均需补足目标设备证据。
## 状态修订（2026-10-25，后续证据优先于下方历史快照）

- Phase 1 的一次性本地时间落在秋季 DST fold 时选择第一次出现（较早 offset），已获用户明确裁决；实现与固定 Clock 回归已提交 `572cf10bc29907e197cdd36e88c7776a187c6979`。见 `docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md` 与 `docs/CORE_FREEZE_VERDICT.md`。一次性 spring-gap 行为仍未新增规则。
- D02 标题贪心折叠详细合同已由用户补齐；生产实现和 API 35 AVD 可视回归见 `docs/issues/PC-ISSUE-GREEDY-LABEL-FOLDING.md`。核心视觉/贪心子项有证据，实体设备与 TalkBack 语音检查仍属未完成项。
- D05 已在隔离 applicationId 的 API 35 AVD 上使用生产 ReminderScheduler/BootReceiver/ReminderReceiver，完成普通进程终止、force-stop 后重开、真实系统重启后 BOOT_COMPLETED 恢复及到期通知的端到端观察。完整门禁 543/543 JVM、74 suites、88 tasks 通过；实体/OEM 未测试。见 `docs/issues/PC-ISSUE-PHASE4-D05-AVD-LIFECYCLE.md` Follow-up 与提交 `c02c766d881701bfb38cd86e27b90adbab7ba651`。
- 以上结果只签核相应自动化/AVD 子项；D05 实体/OEM 验收及 Phase 5–6 仍未完成，正式冻结继续 HOLD。下文原始审计文字保留为当时基线，不覆盖本修订。

## A. 人工验收现状：不要用自动化 PASS 覆盖真机反馈

初始 P01–P08：P01 / P03 / P04 / P05 / P06 / P07 共 6 PASS；P02 和 P08 FAIL。
D01：AI 聊天输入正常；草稿发布按钮遇到输入法时沉底，拆为 D01a PASS + D01b FAIL。
D02：初始审计时用户只说标题折叠当前正常，完整规则尚未提供；用户随后明确了时间轴标题贪心折叠合同。实现与 API 35 AVD 可视回归已通过，字号/截断另有 Robolectric 回归；实体设备与 TalkBack 仍未签核。见 `docs/issues/PC-ISSUE-GREEDY-LABEL-FOLDING.md`。
D03、D04、D06、D07、E03：NOT TESTED，用户对 D03/D07 低优先态度不等于 PASS；E03 延后。
D05：后台常规运行可提醒；“杀后台”后未提醒。杀后台方式不明确，不能先认定代码错误。
E01：FAIL（多轮任务会话能理解，但结构化提案无法闭环；详见 PC-ISSUE-172）。
E02：PASS（普通聊天未自动改日历）。
此外已发现新高优先改进：新建编辑器仅打开就生成草稿；预填时间不一致。

## B. 已确证源码原因

### B1 · 无编辑也创建草稿（P1，确认）
PowerCalApp.kt 的 WebSkinAction.CreateDraft/CreateMemo、MemoCenterScreen.onCreateMemo、MonthScreen.onCreate、SkinnableTimelineScreen.onCreate 均在打开编辑界面前调用 store.newDraft。WebSkinEditBridge.execute(CreateDeck) 同样直接调用 store.newDraft。
DraftForm.fresh 给了默认字段，导致预填值成为立即持久化的对象。
影响：用户只是打开界面，可能留下无名草稿、占用序号、增加待办数量；违背“仅有真实 diff、保存/按 × 退出时才持久化”。

与已有的正式事件编辑器区分：EditSession 已支持内存编辑及 meaningfulDiff，checkpointFormalEdit 在 original==current 时不会创建修改草稿。不能为修复新建行为破坏这条成熟路径，也不能把真正 AI/import 产生的候选草稿无条件改为临时态。

### B2 · 各入口预填时间不统一（P1，确认）
TemporalModels.kt DraftForm.fresh 默认当天 09:00；Activity 自动 09:00–10:00，Alarm 默认 09:00，Deadline 默认仅日期，Memo 默认无日期。
DraftForm.atInstant 使用传入的点击时刻；Activity 自动 +1 小时；Memo 预填日期。
PowerCalApp.kt 的月份新建传页面日期，时间轴新建传点击时刻，Web bridge 以 fresh(now 的当地日期, 默认 09:00) 处理。用户要求“以打开时当下时间为共同基准”。

### B3 · P08 修改未来单次重复事件无法保存（P1，确认端到端功能缺口）
TimelineScreen.kt 中投影有 occurrence slot key，但 open(record) 调用 onOpenEvent(owner)，回调只有 TemporalRecord，丢失具体发生实例的 key。
DirectEventEditorDialog/EditSession 对整条正式记录编辑而不是单次；DraftDeck.kt 直接提示“本次/此后范围编辑尚未支持”。
底层 PowerStore 已有 saveThisAndFutureRecurringEdit 事务；并非“内核完全不会”，而是 UI 选择 scope/传 key/不同 scope 持久化链路缺失。单次修改需按既有 recurrence.overrides / exceptions 等模型合理实现，不应意外修改整条序列。

### B4 · P02「今日」语义实现分叉（P2 或 SPEC CHECK）
PowerCalApp.kt 原生 onToday 使用 TimelineCoordinate.todayCamera(nowMillis, hours)：锚点指向当前精确时刻，保留缩放时长。Web action today 却使用 initialCamera(now, zone)：锚点指向今日中午、重设 24h。
现有“当前时间刻度尺双击→24h”是另一个动作；用户报告“点击今日反而回到当前”，与原生实现一致，但不一定符合期待。不能在未厘清“回今日 vs 回当前刻度 vs 24h 视野”定义前简单改代码；先复现两个 UI 分支并对齐产品语义。

### B5 · D01b 草稿发布按钮被 IME 挤到视野底部（P1，API 35 AVD 已复现并修复）
根因是 `DeckCardBody` 只在视口低于 320dp 时滚动整张卡片；键盘缩短了对话框，但视口仍高于该阈值，因此长表单独自滚动、底部发布/丢弃操作被裁出视野。API 35 `emulator-5556` 的 1080×1600 IME 场景复现按钮 bounds 为 `Rect(0,0,0,0)`；1080×2400 标准视野没有复现。修复后，IME 可见时整卡滚动，内层表单不再创建竞争滚动容器；无 IME 的既有 320dp 小视野阈值保持不变。标准及短视野发布/丢弃各 2/2 PASS，D01a AI 聊天 IME 回归 1/1 PASS。完整证据见 `docs/issues/PC-ISSUE-PHASE3-D01B-IME-PUBLISH.md`。制造商输入法、导航栏与实体设备仍待用户确认，不作真机 PASS 声明。

### B6 · D05 杀后台后无法提醒（P1 条件性 FAIL，归因未定）
Manifest 有 RECEIVE_BOOT_COMPLETED / exact alarm / notification 权限；ReminderScheduler 采用 AlarmManager.setExactAndAllowWhileIdle；BootReceiver 可在重启/时区/时间更改后恢复。
API 35 `emulator-5556` 上的隔离合成探针已分类：Home、Recents 滑除和进程不存在时均收到测试包 marker；只 force-stop 隔离包后，其 PendingIntent 被取消，120 秒内及重新启动后均无 marker，手动重新排程后才收到。此探针未使用 PowerCal Scheduler、精确闹钟、生产 Receiver、数据库或通知，因此不能确认也不能排除 PowerCal 缺陷。Android 15 stopped-state 与重启会清理 alarm 的平台语义已由官方文档核对。PowerCal 实际 5 分钟提醒、BootReceiver 的真实系统派发、系统重启及 OEM 省电行为仍未测试；实体设备由用户验收。详见 `docs/issues/PC-ISSUE-PHASE4-D05-AVD-LIFECYCLE.md`，REC-P1-086 保持 BLOCKED。

### B7 · E01 多轮 AI 提案失败（P1，独立记录）
参见 docs/issues/PC-ISSUE-172-ai-multiturn-intent-handoff.md：chat 看多轮，自动结构化 interpret 却收到最后一轮 user 原文（可能只有“确认”）。需要有版本、引用原用户消息的待处理日程意图，不直接相信模型编造 source。正常聊天 E02 已通过，不要破坏显式审批边界。

## C. 目标模式：编辑器“最小修改”的精确定义

- 打开新建编辑器：只构造内存 initialForm 与 baseline，零 SQLite 草稿、零永久 eventSeq / queueOrder、零正式事件。
- 默认时间是 UI 初始值，不算用户修改。打开 → 无任何用户修改 → ×，数据库与编号计数完全不变。
- 用户修改任意字段后又撤销到 baseline（归一化的业务语义一致）：视为 no-op，退出不创建草稿/提交；仅焦点、展开、滚动、光标变化不属于业务 diff。
- 新建：用户明确点击保存/发布时，根据实际字段完整性执行对应流程；不完整且有 meaningful diff 时可以留待补全草稿；按 × 退出时，有真实 diff 才安全检查点暂存草稿。× 绝不直接正式发布。
- 已有正式事件：保留 EditSession 的强一致编辑、版本冲突、可恢复 checkpoint；有真实 diff 才产生修改草稿。显式保存是否提交正式事件遵守现有界面文案与已批准产品合同。
- 生命周期 ON_STOP / crash recovery：仅对真实 dirty 状态执行受控安全检查点；不能让“打开但未修改”导致意外草稿，不能因键盘/重建布局丢失真实输入。
- AI/ICS/import 主动提案属于独立来源，只有用户授权的操作才转正式事件；本优化不可删除这些合法持久草稿。

## D. 默认时间原则（以真实 now 为公共源）

提供单一可测试的 EditorDefaults(clock, zone, kind, explicitSelection?)，每次打开捕获一次可信 now；禁止页面间分散 LocalDate.now/System.currentTimeMillis/09:00 常量。使用所选时区，不在每次重组时重算并覆盖用户输入。
- ALARM：时间输入初始指向 now（分钟精度，精确舍入规则需固定）；提示过去时刻不会响，不能悄悄滚动日期。
- ACTIVITY：开始=now，预览结束可=now+1h，+1h 为可见建议值，保存时用户可确认，不可伪称用户输入的明确时长。
- DEADLINE：默认日期为今天；保持 date-only 精度，不因统一 now 就写入隐式 23:59 或当前时分。用户选“精确时刻”再预置 now。
- MEMO：不强制日期；日期选择器可使用今天做选择起点，但不因此写入“有日期”。
- “在某个日期/时间位置新建”：用户明确选中的日期/时刻属于明确输入，优先级高于缺省 now；但若用户要求全部入口一律忽略该选择，需更新合同/交互再做。
- 默认为 current now 的推导必须保持正确 zone/DST 表达，不产生跨午夜错误。

## E. 修复执行顺序和独立验收

Phase 0 — 证据隔离：不清除真机/已有 AVD/历史草稿，先建模拟器隔离数据库与“无 diff 打开关闭”计数快照；所有 testcase 绑定 git+APK 版本。

Phase 1（最高优先）— 无副作用编辑会话与统一 now：
1. 抽离 new-event transient session / baseline / meaningfulDiff，入口不再直接 newDraft。
2. save 与 × 的写入范围区分，消费完会话且防双击重复；插入/更新序号只在真实写入时发生；不完整有差异按合同保留。
3. 统一当前时间初值，保持 Deadline date-only/Memo 可无日期。
4. 红测：打开关闭各四类 20 次 draft/identity/commit counts 不变；字段改动恢复初值→无写入；真实修改×→恰好 1 草稿；显式发布→恰好 1 正式事件；重启与后台 checkpoint 幂等。

Phase 2 — P08 单次/此后重复事件修改：
1. 时间轴从 occurrence 投影把 original occurrenceKey 传给 Editor，提供“仅本次 / 本次及以后 / 整个系列”选择。
2. 对接现有 core transaction；保留 slot key、例外、count/until、完成状态和提醒意图语义；手动选定 scope 才允许保存。
3. 红测：中间单次编辑只改变这一发生；从此后分割以前不受影响；全系列修改保持身份、拒绝过时修订；DST 边界/重启/重复点击。

Phase 3 — D01b 键盘发布按钮：
1. 复现键盘弹起、长字段滚动、软键盘占满半屏、系统导航栏；采集按钮 bounds 与 IME 区域，不遮挡/不误触。
2. 只改变草稿表单布局/Insets 消费，发布与丢弃始终可见可点或可自然滚动到；AI 聊天 D01a 回归无退化。
3. 完成记录：API 35 `emulator-5556` 标准 1080×2400 和短 1080×1600 两种视口下，发布/丢弃均 2/2 PASS；短视野按钮滚动后 y=644–770，IME 顶边 y=791；标准视野按钮 y=1433–1559，IME 顶边 y=1580。D01a 回归 1/1 PASS；完整 Gradle 522/522 JVM tests、70 suites、0 failures/errors/skips、88 tasks，lint 0 errors / 56 warnings。源代码与测试提交 `ab7b71a7670a5853ff6ad715eb45e59819375cc7`；报告 `docs/issues/PC-ISSUE-PHASE3-D01B-IME-PUBLISH.md`。实体设备未测试。

Phase 4 — D05 提醒“杀后台”分类：
1. 区分 Home 退后台、最近任务划掉、系统“强行停止”、省电管控、系统重启。
2. 同一 5 分钟未来闹钟逐组设置，核验设置权限、AlarmManager 排程、通知渠道/锁屏显示、FIRED/MISSED 日志、系统是否允许送达。
3. 仅修真实应用管理缺陷；对 Android 厂商/Force-stop 的 OS 限制如实记为限制或需用户设置，不承诺规避系统机制。

Phase 5 — E01/PC-ISSUE-172 已有独立实现/回归记录；D02 标题贪心折叠合同已明确并完成 API 35 AVD 子项（实体/TalkBack 待验）；P02“今日”规范仍需先统一产品语义再复测。各项独立签核，禁止以延长 timeout 替代多轮任务状态。
Phase 6 — 延期真机项 D03/D04/D06/D07/E03：标记 NOT TESTED 或明确 owner-excluded，绝不虚标 PASS；重启持久化 D06 和 ICS E03 在正式冻结时按真实优先级复审。

## F. 验收原则与提交

每修完一个明确缺陷：修复前可复现 FAIL → 最小补丁 → 针对测试 PASS → 全量单测和设备 UI 验证 → 单独 docs/issues 报告 → 原子 Git commit/push。
当前不推进原生 UI 删除/皮肤 cutover，不修改用户数据，不把 Android 系统 force-stop 限制当成无条件可修 Bug。
B1/B2/B3 已分别有独立交付记录；B4 是待澄清的行为定义冲突；B5/B6 仍需相应设备条件下复现与诊断。

## Phase 1 交付记录（2026-10-25）

PC-ISSUE-PHASE1 的编辑器生命周期与统一默认时间修复已完成。普通新建入口现统一使用内存会话；无 Diff 退出不写入草稿、正式事件、提交、身份、事件序号或队列序号；有 Diff 按 × 只暂存一张草稿；明确保存完整表单直接事务创建正式事件，不完整表单复用一张可恢复草稿。正式事件编辑的 lease、版本冲突和 checkpoint 未重写；AI、文本解析、ICS 导入及移动事件的显式持久化候选保持原样。

验证证据：API 35 `emulator-5554` 实际 Compose 打开/关闭四类事件与有修改退出 2/2 PASS；完整门禁 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain` 为 514/514 JVM tests、70 suites、0 failures/errors/skips、88/88 tasks，lint 0 errors / 56 warnings。完整报告、日志与 SHA-256 位于 `docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md` 及 `.local/android-test/reports/freeze-20261025/pc-phase-1-editor-lifecycle/`。

AVD 测试只在 `emulator-5554` 的随机隔离 SQLite 文件执行；运行前后原 app/test APK SHA-256 相同，未清理用户数据；`emulator-5556` 未触碰。实体设备由用户测试，本轮未声称通过。DST 秋季回拨的重复墙上时间不能由现有本地分钟文本区分，保留为未签核风险。Phase 1 自动化及 API 35 AVD 子项 PASS；Phase 2 可开工，核心冻结仍 HOLD。实现提交与推送 SHA 将补录在交付进度条目。

独立结案报告：`docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md`。实现提交：`b46b3dd7592bb1c7de021d543630f84937a0eaef`。`Phase 1 当前 HEAD 复验`小节中的 DST fold 未签核状态随后被用户 2026-10-25 的明确裁决取代；详见本文件顶部状态修订。

## Phase 2 交付记录（2026-10-25）

PC-ISSUE-PHASE2 已接通重复 occurrence 的编辑器范围选择。时间轴、月份与 Memo/Todo occurrence 路径保留并传递 original occurrence key；重复事件需显式选择“仅本次 / 本次及以后 / 整个系列”。仅本次写指定槽位 override；本次及以后调用既有原子拆分；整组保存保持正式事件身份与 revision 栅栏。checkpoint 保存 scope/key 并可从原事件恢复，泛化草稿提交/AI batch 不接受范围编辑元数据。另修复选中槽位的旧标题 override 覆盖新系列标题，以及保存成功后的 Compose 基线未刷新的问题。

验证：Robolectric Compose 覆盖 Timeline/Month/Memo occurrence key 路由、明确 scope 和单次编辑；API 35 `emulator-5554` AndroidJUnitRunner 对隔离数据库中的“本次及以后”保存 **1/1 PASS**。全量门禁 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain` 为 **522/522 JVM tests、70 suites、0 failures/errors/skips、88/88 tasks**；lint 0 errors / 56 warnings。完整日志 SHA-256 `42C63E29348DD23EAAFDC44EE0C2DD578D0F5F57DA091C6B80C8056444A01858`；Debug APK SHA-256 `90BB816EC85AFD46C195C48697F1234D98D2C4571FB5209F0F1FD87B088DBDE8`。AVD 运行前后 app/test APK 均逐字节恢复，隔离 DB 文件名清单一致；未触碰 5556。详细证据见 `docs/issues/PC-ISSUE-PHASE2-RECURRENCE-SCOPE-EDITOR.md` 与 `.local/android-test/reports/freeze-20261025/pc-issue-phase2-recurring-scope/`。

`REC-P0-127` 是本次新增的 RECONSTRUCTED scope-editor 验收项；原始 209 项仍无历史 ID 映射。实体设备由用户测试。P08 自动化及 API 35 AVD 子项 PASS；Phase 3–6 与其他冻结缺口仍在，整体核心冻结 HOLD。

台账结构校验：212 行 / 211 活动重建项，199 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2=145/65/1。台账 SHA-256 `FFBD395A80D864BB8ED9578A561B6188DD3ED35B88048E0EC31F2AC7951909A1`；结构报告 SHA-256 `09879FB7A8671E7C5DE02C69BEBF30A8B10751EE27EB576B91C3239FC1DAFC0F`。校验仅检查台账结构与状态约束（`product_behavior_tested=false`），不替代行为测试。

独立结案报告：`docs/issues/PC-ISSUE-PHASE2-RECURRENCE-SCOPE-EDITOR.md`。实现提交：`9036a2a8db2e1a669f64cc6174acdc569c4c08b3`。

## Phase 1 当前 HEAD 复验（2026-10-25，`7ceb7fa`）

按新的 Phase 1 执行请求，在 detached worktree `7ceb7fa8b65aefd4174cf0420d1d166486c89484` 验证；生产源码与此前已审计的 `6ddf9b9` 一致，本轮只增加 Compose 回归测试；原实现提交仍为 `b46b3dd7592bb1c7de021d543630f84937a0eaef`。主工作区 E01 AI 多轮改动及 `design/`、Issue、测试证据等未进入该 worktree，也未被本轮暂存或覆盖。源码审查确认普通新建入口均使用 `NewEventEditSession`；余下直接 `newDraft` 属于真实 Diff checkpoint、用户明确的文本/ICS/AI 候选或移动修改草稿。

定向命令 **32/32 PASS、6 suites、0 failures/errors/skips**；完整离线门禁 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain` **522/522 JVM、69 suites、0 failures/errors/skips、88/88 tasks**；lint **0 errors / 53 warnings**。Debug / AndroidTest APK SHA-256：`1601F2DB169A8C889791EBFE4149F4383E71980474452C8CFE22BA9A7C92A9EC` / `9FAA01339612CA2DF1871CD047512C282F19339C1E5A7766E4E512E80B0FDFFD`。日志、JUnit XML、APK 与 lint XML 位于 `.local/android-test/reports/freeze-20261025/phase1-final-7ceb7fa/`；完整门禁日志 SHA-256 `24DF06304B35356240BCC986F53583E714800C5E467E3A944BB34213C8206663`，定向日志 SHA-256 `44DB388BEFCECE2555E8273F8F537FF31278CED3F08749FEB3D3A8365C6D41C9`。新增 Compose 重组用例确认用户改过的开始时间与会话 Clock 不被重组覆盖；首次断言读取了 Compose Text label 而非 EditableText，失败 XML 已保留，修正后通过。

重建矩阵新增 `REC-P1-087`（瞬态新建会话与幂等持久化）及 `REC-P1-088`（统一默认时间、ZoneId 与日期/分钟精度），两项均仅按本轮 JVM/Robolectric 行为证据签核 PASS。结构校验为 216 rows / 215 active reconstructed；202 PASS / 0 FAIL / 9 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2=145/69/1；历史映射 0。ledger SHA-256 `3A40691D66AF8391543FA82EBB0C2156EABAA3848F00ED51164C491C3FD0AA0A`；validator JSON SHA-256 `80F1018770C595CCAE4AC34461A66ED4A53791CA699CE49490A6832C4B2CF15F`。结构校验不替代行为测试，也不恢复原始 209 ID。

本轮没有启动现有 AVD/connected 任务：其 PowerCal 私有数据库的缺失与恢复处置仍未闭环，避免再次触碰已有运行环境；先前 API 35 Compose 2/2 保留为历史证据，不宣称本轮新 PASS。实体设备仍由用户执行。Phase 1 JVM/构建验收 PASS；本轮 AVD 子项 BLOCKED，秋季 DST fold 同名分钟限制未签核；整体冻结继续 HOLD。源码未改，因此仍引用已推送的 `b46b3dd` 实现提交；本轮报告和矩阵更新单独提交。
