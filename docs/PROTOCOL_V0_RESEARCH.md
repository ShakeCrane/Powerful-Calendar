# Temporal Protocol v0 — 研究草案与验证矩阵

状态：**PROPOSAL / NOT IMPLEMENTED / NOT YET FROZEN**
所属仓库：Powerful Calendar (Android)。本文件只提供数据语义、约束与验证场景，不包含可执行实现。
对应产品原则：见 [PRODUCT_BASELINE_G1-G5.md](./PRODUCT_BASELINE_G1-G5.md)。

## A. 必须分开的实体

| 概念 | 身份 / 职责 | 禁止混淆 |
| --- | --- | --- |
| TemporalObject | 全局稳定 objectId；type、revision、生命周期；ActivityEvent / AlarmEvent / TaskEvent | 不得用外部日历 ID 或 UI 位置当 objectId |
| TemporalSpecification | 时间意图：时刻、时段、全天、截止时间；墙上时间、时区、绝对时刻策略 | 不得仅将“每天 07:30”存成下次 epoch millis |
| RecurrenceSeries | 规则、时区策略、单次例外、系列版号 | 不得把未来一年实例作为唯一事实 |
| Occurrence | 由系列规则 + 发生原始身份生成的稳定实例 key | 修改本次开始时间不得导致实例 key 丢失 |
| Trigger | 关联对象/实例的通知、响铃或其他执行意图，可独立于 AlarmEvent | 在 UI 左侧显示一个关联 Trigger 不等于多出一个新闹钟 |
| ScheduleIntent | 所需下次触发的持久化指令/版本，可与 Android 实际状态 reconcile | 不等于 event.saved |
| ExecutionRecord | 实际发起、执行、贪睡、结束、错过、失败的历史 | 不得拿一次执行状态覆盖整个系列 |
| IngressRecord | 外部来源、原始 UID、文件 hash、已迁入批次、失败原因 | 迁入之后不作为持续主数据源 |
| DraftOperation | 待用户确认的新增、修改、删除，含 baseRevision/来源与候选字段 | 未确认前绝不产生响铃或更改正式数据 |
| ExportJob | 明确的用户/规则派发目标、状态和结果 | 输出副本不反向接管本地对象 |

## B. 建议的序列化外形（说明性，非最终 JSON Schema）

- envelope: schemaVersion、objectId、revision、kind、createdAt、updatedAt、archivedAt?、provenance[]
- common: title、description、location?、priority、tags[]、timeSpec?、recurrence?、triggerRefs[]
- ActivityEvent: start/end 或全天等时间规范；可选关联 AlarmEvent
- AlarmEvent: alarm wall-time / date-time、repeat、ringtoneProfile、snoozePolicy、enabled
- TaskEvent: dueAt?、scheduledStart?、scheduledEnd?、completedAt?、taskStatus；没有时间锚点则不渲染到时间轴
- Trigger: triggerId、ownerRef、occurrenceScope、relative/absolute time、deliveryMode、enabled、policy
- occurrenceKey: seriesId + original recurrence-id（原始发生槽位标识，而非修改后的实际开始时间）
- occurrenceException: occurrenceKey、overrideFields、skipFlag；“本次及以后”需要系列切分/连接语义，规则尚需决定
- draft: draftBatchId、pageId、candidateOperation、baseRevision?、validationIssues[]、userEditedFields[]、status
- scheduleIntent: triggerId、occurrenceKey、objectRevision、whenInstant、precisionRequirement、scheduleState、retryMetadata

这里的字段是研究草案，不要求立即在 Kotlin 中采用上述一对一类结构。公共字段不得强迫 TaskEvent 拥有“结束时间”，也不得强迫 AlarmEvent 拥有持续时间。

## C. 状态机候选

1. object: draft（单独草稿区）→ active ↔ paused → archived；deleted 需明确软删除/墓碑语义，未决定。
2. recurrence occurrence: pending / overridden / skipped / completed / expired；实例身份不随拖动改变。
3. scheduler: desired → reconciling → scheduled / degraded / blocked / failed → fired / canceled / missed。
4. alarm firing: eligible → ringing → snoozed → ringing → dismissed；所有转移在当前 occurrence 执行记录上。
5. draft page: proposed → edited → validated → committed → removedFromQueue；跳过/退出/重试不应改变其它页状态。
6. migration: discovered → staged → normalized → reviewed → committed → handoffChecklist / partialFailure。

## D. 协议不变量（待以测试形式固化）

**INV-01 Identity**：objectId 在修改、类型切换、来源记录解绑时稳定；kind 变化带 revision；同一导入来源原始 ID 只映射到一个可追踪本地对象。
**INV-02 Time semantics**：wall-clock repeat 和 fixed-instant 单次事件语义分离，时区偏移/DST 折叠、缺失时间需要明确政策；展示时也不得偷偷改变实际 Instant。
**INV-03 Occurrence identity**：occurrenceKey 来自原始系列槽位；单次重排不改变该 key；新实例不得复用已经跳过的 key。
**INV-04 No side effects from drafts**：未确认的 AI/手动/拖动草稿不得创建或修改执行计划；多页各自原子提交。
**INV-05 Revision fencing**：执行前必须核验最新 object revision 和 trigger/occurrence 的有效性；旧 PendingIntent 即使迟到也不得唤醒已经废弃的闹钟。
**INV-06 Durable scheduling**：对象更新和其对应调度意图须有持久化关联；失败可恢复、幂等，禁止 silent success。
**INV-07 Permission awareness**：精准权限不可用时准确闹钟不可伪称 scheduled；普通提醒近似调度应显式标记风险。
**INV-08 One-way ownership**：迁入后 PowerCal 为主源；默认不得擅自删除原始平台对象、自动开启循环同步或广播全部目标。
**INV-09 Export compatibility**：原生恢复保存全部语义；ICS 等有损投影需要事先说明不可表示的字段。
**INV-10 Context privacy**：AI 仅收到当前任务经授权的必要事件资料；API Key 不随备份导出明文或写进事件日志。
**INV-11 UI identity**：同一对象在左侧投影、右侧本体、Popup、收件箱中的可视引用不得产生额外对象。
**INV-12 Coordinate continuity**：y(t) 在实际时间轴上连续，缩放时固定手指焦点下的绝对时刻；视图重排不应修改时间源数据。

## E. 高优先级反例 / 异常验证矩阵

| 场景 | 预期结果 |
| --- | --- |
| Android 日历同一活动通过 Instances 和 ICS 各导入一次 | 准确来源 ID 去重；跨源疑似重复排队人工确认 |
| 外部一次迁入 600 条中途断电 | 事务边界清晰，重试不重复；仍可看到失败批次 |
| 重复闹钟本次拖动 07:30→08:00 | 本次 exception 生效；下一天仍按规则 07:30 |
| 系列本次发生被迁至前一天 | occurrenceKey 仍指原始发生槽位；无重复产生 |
| 用户刚编辑，AI 基于旧 revision 返回修改结果 | 展示冲突差异，不覆盖用户新版本 |
| 已保存多页草稿中的第 2 页，应用崩溃 | 已保存页面仅提交一次；其它未保存页恢复 |
| 旧 07:30 PendingIntent 在已改 08:00 后触发 | revision 失效，被丢弃并记录，不播放铃声 |
| Android 14 设备精确闹钟权限被拒绝或撤销 | alarm 显示 blocked；普通提醒如降级需告知风险 |
| 手机关机 1 小时后启动 | 超过待定宽限阈值则记 missed，而非突然补响 |
| 夏令时跳过 02:30，用户设本地 02:30 每天闹钟 | 根据明确的 DST policy 决定跳过或调整，不能无记录地偷偷偏移 |
| “01:30” 在秋季时钟回拨重复两次 | 明确第一次/第二次/两次 policy，并有唯一 occurrence 执行标识 |
| 时间轴跨午夜拖动并迅速连续双指缩放 | 缩放锚点与拖动预览连续；取消不写数据 |
| 缩到 48h，多个 5 分钟事件重叠 | 使用视觉简化/折叠，点击命中不改变时间坐标 |
| 收件箱无时间任务拖到时间轴 | 保留原 TaskEvent 身份；候选 scheduleTime 先走统一 Popup 确认 |
| 截图 OCR 识别出 4 条，其中 1 条时间不明 | 四页独立校验；已保存页消失，未保存页仍可恢复 |
| 备份→清空测试环境→恢复 | object/occurrence/trigger/drafts/索引关系保持；必须重建 Android 实际调度，不导入已失效 PendingIntent |
| 迁移旧系统闹钟但 API 不支持读取 | UI 清楚说明限制，给截图或手动迁移入口，不伪造已完成迁移 |

## F. 下一轮重点：Popup + 未安排收件箱（尚未决议）

Popup：底部 sheet vs 居中浮层、显示级别、跨页滑动与文本表单手势冲突、保存页关闭后焦点保留、手动/AI/拖动复用、版本冲突可视化。
Inbox：无时间任务与 AI 未确认草稿是否同一个概念、从时间轴如何呼出、拖放到时间轴的语义、层级/优先级排序、任务完成后行为、隐藏与恢复入口。
建议将该问题先作为 UX 语义层设计，而不要在此阶段就锁死 Compose 组件或视觉样式。

## G. 外部平台事实来源

- Android CalendarContract（Events/Instances/Reminders 不同概念）：https://developer.android.com/reference/android/provider/CalendarContract
- Android exact alarms 权限：https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- AlarmManager 行为：https://developer.android.com/reference/android/app/AlarmManager
- Compose gesture detector 只检测，不自动变换：https://developer.android.com/develop/ui/compose/touch-input/pointer-input/multi-touch
- Compose ModalBottomSheet API：https://developer.android.com/develop/ui/compose/components/bottom-sheets
