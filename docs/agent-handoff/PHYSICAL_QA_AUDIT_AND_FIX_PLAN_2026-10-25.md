# PowerCal 真机首轮缺陷审计与修复计划（2026-10-25）

状态：AUDIT / PLAN ONLY；未执行生产代码修改；正式冻结 HOLD。
审计基线：Git 40e78bf（来源为当前仓库只读检查），对照用户实机反馈和 docs/CORE_CONTRACT_FREEZE_2026-10-21.md 的已修订合同。
范围：问题归因、复现方案、实现次序和验收门槛。任何源码猜测均需补足目标设备证据。

## A. 人工验收现状：不要用自动化 PASS 覆盖真机反馈

初始 P01–P08：P01 / P03 / P04 / P05 / P06 / P07 共 6 PASS；P02 和 P08 FAIL。
D01：AI 聊天输入正常；草稿发布按钮遇到输入法时沉底，拆为 D01a PASS + D01b FAIL。
D02：用户称标题折叠当前正常，但“我的要求其实是”之后的完整规定尚未提供，临时 OBSERVED PASS / SPEC PENDING；不能签合同完成。
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

### B5 · D01b 草稿发布按钮被 IME 挤到视野底部（P1，代码可疑、须复现）
DraftDeck.kt 对话框外层既有 imePadding，短视野 short=true；DeckCardBody 内长表单可滚动、发布按钮在卡内底部。真实手机报告按钮移位，说明“AI 聊天 IME 测试通过”不代表发布编辑器 IME 通过。需检查实际按钮可视/可点击区域、焦点滚动、底部系统手势栏，以及可能重复 Insets 消费。先设备截图或布局 bounds 取证，再局部修约束、键盘弹出时布局与焦点滚动；切勿用全局 hack 修掉其他页面。

### B6 · D05 杀后台后无法提醒（P1 条件性 FAIL，归因未定）
Manifest 有 RECEIVE_BOOT_COMPLETED / exact alarm / notification 权限；ReminderScheduler 采用 AlarmManager.setExactAndAllowWhileIdle；BootReceiver 可在重启/时区/时间更改后恢复。
但是“从最近任务划掉”“设置里强行停止”“厂商一键清理/省电限制”“用户关闭后台权限”是不同系统状态。Android 强行停止通常取消已排定闹钟并阻止后台组件重启，不能保证应用跨 force-stop 自我唤醒。须先确定操作方式与手机系统，记录下次触发是否仍被系统允许。不要以无效常驻服务/反复唤醒对抗系统。

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

Phase 4 — D05 提醒“杀后台”分类：
1. 区分 Home 退后台、最近任务划掉、系统“强行停止”、省电管控、系统重启。
2. 同一 5 分钟未来闹钟逐组设置，核验设置权限、AlarmManager 排程、通知渠道/锁屏显示、FIRED/MISSED 日志、系统是否允许送达。
3. 仅修真实应用管理缺陷；对 Android 厂商/Force-stop 的 OS 限制如实记为限制或需用户设置，不承诺规避系统机制。

Phase 5 — E01/PC-ISSUE-172 AI 多轮，P02“今日”规范统一及 D02 标题最终需求核对；各自独立复测，禁止以延长 timeout 替代多轮任务状态。
Phase 6 — 延期真机项 D03/D04/D06/D07/E03：标记 NOT TESTED 或明确 owner-excluded，绝不虚标 PASS；重启持久化 D06 和 ICS E03 在正式冻结时按真实优先级复审。

## F. 验收原则与提交

每修完一个明确缺陷：修复前可复现 FAIL → 最小补丁 → 针对测试 PASS → 全量单测和设备 UI 验证 → 单独 docs/issues 报告 → 原子 Git commit/push。
当前不推进原生 UI 删除/皮肤 cutover，不修改用户数据，不把 Android 系统 force-stop 限制当成无条件可修 Bug。
已发现源码根因 B1/B2/B3；B4 是行为定义冲突；B5/B6 需要真实设备复现与诊断。
