# PowerCal Glass · UI 接管契约

日期：2026-10-20。只读审计基线：`f991243b0e6b9b7861c400baee6a6e2c47be576c`，初始工作区干净。
已完整阅读 G1–G11、Protocol v0.3、IMPLEMENTATION_V0_2、AUDIT_P0，以及现有四个 v2 页面、DeckController、TemporalModels。
历史审计描述的是旧提交，当前实现以实际 Kotlin 接口为准。

## 1. 写入边界与实际接口

仅修改 `ui/v2/`、非业务性 Activity 宿主、UI 测试与文档。core、ai、reminders、数据库与迁移代码保持原样。

| 用途 | 现有接口 | UI 义务 |
| --- | --- | --- |
| 卡组状态 | `DeckController.state` / `observe` / `open` / `refresh` | IO 命令为挂起函数并由 controller 串行；draftId 为焦点，queueOrder 为顺序，不能用 eventSeq 代替卡片身份 |
| 编辑与持久化 | `edit(DraftForm)`、`flush(lockEdits: Boolean = true): Boolean` | `edit` 只更新内存；自动防抖调用 `flush(false)`，离开/切卡先成功 flush；SQLite 同步调用开始后不能被协程中断 |
| 浏览 | `move(±1)` / `jump(steps)` / `select(draftId)`（挂起） | 卡内最多一张；卡外多张；不能重排队列；命令开始后拒绝迟到的 IME 编辑事件 |
| 发布与冲突 | `commit()` / `confirmConflictReplacement()`（挂起）、`dismissConflict()` | 展示完整快照比较；确认交给 controller 校验修订；不实现覆盖事务 |
| 丢弃与恢复 | `discard()` / `undoDiscard()` / `requestRestore()`（挂起） | X 恒为不执行提案；只宣称可撤销丢弃；正式发布没有补偿撤销接口 |
| 完成 | `DeckState.completion` / `canUndoDiscard` | 仅使用业务层完成门槛及真实统计；不以卡片数量推断成功 |
| 草稿创建 | `PowerStore.newDraft` / `proposeReplace` | 宿主既有命令入口，IO 调用；AI 锁时禁用；不直接访问 SQLite |
| 查询 | `all()`、`pendingDrafts()`、`intentFor()`、`lastExecution()` | 通过既有只读服务；不在组合期执行数据库查询 |
| AI 授权 | `beginAiBatch(Map<Long, Long>, Set<DraftOperation>)`、`AiBatchRunner.run` | Map 为 `draftId -> reviewed draftVersion`；事务内校验审阅版本、操作许可并固定批次范围 |
| 取消/恢复 | `requestAiCancel` / `releaseStuckAiBatch` | 保留已提交结果；余下卡片继续待处理；UI 不生成 fencing token |
| 提醒 | `ReminderScheduler.reconcile` / 权限查询及设置入口 | 保存、调度、触发分开呈现；权限返回时刷新；无精确权限不能宣称精确响铃 |
| 数据接入 | 原宿主的 import/export/backup/legacy 命令 | 保持校验、幂等和空库恢复规则；解释旧闹钟交接 |

`observe` 只有一个监听者；安装监听时会同步当前 state，避免旧快照覆盖较新通知。Controller 命令由内部 `Mutex` 串行；排队和 IO 派发前可取消，SQLite 同步调用开始后会完成并记录结果。自动暂存期间编辑保持可用；导航/终态命令开始后 `busy` 阻止迟到输入。`ON_STOP` flush 是排队的最佳努力，不保证进程强杀前的尾部输入已落盘。

## 2. 审计问题与设计前后目标

| 基线事实 | 本轮目标 |
| --- | --- |
| 所有工具按钮挤在单行横向滚动条，40dp 图标，时间轴被顶栏覆盖 | 导航和时间画布分区；48dp 操作；日期和缩放有独立层级 |
| 时间轴 fling 不保留 Job，新触摸不能取消旧惯性 | 新手势立即停止惯性；双指不产生单指 fling；相机用最新值累积 |
| 日期截止只在午夜坐标显示，离开午夜就消失 | 独立日期截止带，按可见日期呈现，明确无具体时刻 |
| 活动只画固定高 chip；重复事件只显示原始起点 | 活动显示时长；使用现有 RecurrenceEngine 做只读实例投影 |
| 重叠 chip 宽度互相遮挡，溢出只锚定全库第一项 | 依可用宽度决定列数；可见重叠组逐组展开 |
| 卡片紧凑态仍直接呈现表单；横屏和 IME 区域易被固定头尾挤压 | 可点击摘要主卡；展开编辑；窄高度隐藏辅助区，内部滚动，底部操作保持可达 |
| dismiss 忽略 flush 返回值，正式事件搜索跳转未 flush，controller 重复 open | 所有离开路径共用成功暂存屏障；只 open 一次 |
| 卡内手势未检查纵向位移；卡外纵向滑动会误触退出 | 比较横纵方向，文本消费优先，多指放弃；手势结束才翻页 |
| 动画偏好只在 Activity 创建时读取 | 观察系统设置实时更新；减少动画用即时状态，低内存取消装饰层 |
| diff 遗漏 recurrence、zone/source、转换前消失的时间字段 | 展示完整前后快照，包含所有重复约束/例外/覆盖，长文本可选择与滚动 |
| 授权确认重新读队列扩大范围；丢弃批次文案误称删除事件 | 固定卡片清单、数量和操作类型；区分发布删除提案与丢弃提案 |
| 工具页缺少月历，设置与输入混在长表单 | 增加月历与日程列表；工具按任务分组，模型配置折叠 |

## 3. DSH 接口状态与遗留需求

1. **重复范围命令**：`proposeOccurrenceEdit(eventSeq, originalSlotKey, scope, proposedForm)`，scope = THIS / FOLLOWING / SERIES；返回 DraftCard，提交时保留原始实例键。当前只能整体规则编辑，拖动重复事件保持拒绝，显式提示不支持本次/此后。
2. **正式发布撤销**：`proposeCompensation(commitId)` 或等价的补偿草稿命令。当前仅 `undoDiscard`，UI 不宣称发布/正式删除可撤销。
3. **响应式只读查询（DSH-03 遗留）**：仍需提供带 revision/lock/session 的 observable snapshot 和稳定 laneKey；`activeBetween` 当前仅查原始 anchor。DSH-09 已让 `RecurrenceEngine.occurrences()` 按有效时间查询并保留原始槽位 key，UI 使用该共享只读投影；全库加载不是无限数据量方案。
4. **批次进度**：AiSession 有 scope/state/report，没有逐项完成计数和结果流；UI 显示授权数、仍待处理数和运行状态，不能把剩余全队列数当本批次成功数。
5. **旧闹钟交接**：显式 handoff 状态/命令和来源时钟信息；当前 enabled 不能证明旧闹钟已关闭。UI 解释重复响铃风险，不自动打开迁入闹钟。
6. **完整领域 diff**：现有 DraftDiff 忽略重复规则、来源/时区和转换前消失的字段；本轮 UI 通过完整只读快照展示补足，建议领域层以后提供版本化审阅 DTO。
7. **Controller 异步命令与生命周期暂存（DSH-07 已实现）**：挂起命令由 controller 内部 Mutex 串行；`edit()` 只改内存；自动暂存期间输入保持可用，切页/发布等命令在暂存屏障期间拒绝迟到编辑。排队和 dispatcher 前可取消；SQLite 同步调用开始后无法中断。进程强杀前最后约 300ms 输入仍无绝对保证，`ON_STOP` flush 只是最佳努力。
8. **按审阅版本原子授权（DSH-08 已实现）**：`beginAiBatch(reviewed: Map<draftId, draftVersion>, allowedOperations)` 在同一事务校验锁、卡片状态、操作许可和审阅版本；UI 传展示快照的版本映射。
9. **跨窗口的 recurrence overrides 查询（DSH-09 已实现）**：`occurrences()` 按有效时间查询，携带原始 occurrence key；`occurrenceForKey()` 为提醒校验提供精确查找。UI 使用共享投影；时间轴的有界 occurrence 渲染上限为 2048，超过上限的窗口内起点可能截断。

补充说明：DSH-07 的前台命令忙碌状态只用于切页/发布/退出等转换；自动防抖暂存不切换输入框的 readOnly，以免干扰 IME。强杀耐久性仍需真机验证。

## 4. 验证原则

保留全部底层测试；新增有意义的 UI 投影/手势/快照测试。执行构建、单元测试、lint。
`adb devices -l` 初审无设备，SDK 无 emulator/system-image；不把 JVM 数学测试当作触控、视觉或帧率验收。
最终证据、提交记录与未验证项见 `GLASS_UI_DELIVERY.md`。
