# P0 — 现状审计与实施计划

**日期:** 2026-10-20 · **基线:** `6a894fd` (docs) / 未提交草稿 · **审计人:** DSH 开发小队
**依据:** `docs/PRODUCT_BASELINE_G1-G11.md`, `docs/PROTOCOL_V0_RESEARCH.md`
**结论:** 三个未提交文件是**设计草稿，不可直接采信**。协议方向正确，但存在多处会导致
数据错误、AI 锁绕过与 UI 假动画的缺陷。工作区无任何已提交实现。

---

## 1. 工作区事实

| 项 | 事实 |
| --- | --- |
| 分支 | `main`，无 remote，3 个提交 |
| HEAD | `6a894fd docs: consolidate PowerCal G1-G11 identity and Draft Deck design` |
| 未提交 | `core/TemporalModels.kt`, `core/PowerStore.kt`, `ui/v2/DraftDeck.kt`（全部 untracked） |
| 已提交实现 | 仅 v0.1 原型：`MainActivity` / `CalendarEvent` / `EventStore` / `IcsCodec` / `SystemCalendar` / `TextParser` / `ReminderScheduler` / `ReminderReceiver` / `BootReceiver` |
| 测试 | 仅 `InteropTest`（5 个纯 JVM 用例），无仪器测试 |
| 三个新文件被引用 | **否**。`MainActivity` 仍使用 v0.1 的 `EventStore`，新协议层是死代码 |
| 构建工具链 | 无 Android SDK（`local.properties` 指向不存在的 `D:\Android\Sdk`），无 `JAVA_HOME`，Gradle 8.14.3 未缓存 |
| `gradlew.bat` | **提交版本损坏**：`set CLASSPATH=` 为空却同时传 `-classpath ""` 与 `-jar`，Java 启动即失败 |

> 处置原则：三个文件来源可确认（上一会话的未提交草稿），**不删除、不覆盖**，在其路径上
> 修复并演进；v0.1 数据库 `powerful-calendar.db` 原样保留，另开 `powercal-v2.db`。

---

## 2. 缺陷审计（按严重度）

### 2.1 阻断级（会导致数据错误 / 违反协议不变量）

| # | 位置 | 缺陷 | 后果 |
| --- | --- | --- | --- |
| B1 | `PowerStore.commit` | `DraftForm` 不含 `source` / `zoneId`，REPLACE/CONVERT 提交时用表单重建整份快照，`source` 回落 `"local"`、zone 回落系统时区 | 导入来源与墙钟语义被静默改写，违反"完整快照=用户确认的内容" |
| B2 | `PowerStore` | 无墓碑恢复 API。`readObject` 过滤 `retired=1`，删除后任何 `REPLACE/CONVERT/DELETE` 永远返回 `Conflict(null)` | G10-04 要求"显式确认后以同一 eventSeq 复活"，当前不可能实现 |
| B3 | `PowerStore.stageDraft` | 仅按 `draft_id` 更新，无 `draft_version` CAS | 两个入口（AI/用户）持有同一卡时后写覆盖先写，丢失已暂存文本 |
| B4 | `PowerStore` | AI 锁只覆盖 `newDraft/commit`；`discard`/`undoDiscard` 完全忽略 epoch | AI 批量期间可用 X 丢弃卡片绕过独占；G11-03 被破坏 |
| B5 | `PowerStore` | 批次只持久化 `ai_epoch/ai_scope`，无 `sessionId`、无 `state`、无 `startedAt`、无已完成项集合 | 崩溃后无法区分"该恢复"与"该作废"，且重复提交无法幂等 |
| B6 | `PowerStore.recoverInterruptedAi` | 无条件解锁 | 若在批次进行中被调用（如 Activity 重建），会中途释放锁，AI 与用户同时写卡组 |
| B7 | `PowerStore.commit` | 幂等键是 `draft_id UNIQUE`，但无显式 `commitId`，也没有 `actor` / `operation` 记录 | 满足"重复点击不重复发布"，但无法审计"谁提交了什么"，不满足 PROTOCOL §1 CommitRecord |
| B8 | `PowerStore.importStructured` | 直接写 `objects` + `revision=1`，不写 CommitRecord / MigrationRecord | 导入无来源证据，备份恢复与去重无法核验 |
| B9 | `PowerStore` | 无 `identities` 表：`next_seq` 只前进，但退役序号、分配时间不可查 | I-01 只能间接验证，备份/恢复语义不完整 |
| B10 | `PowerStore.drafts` | 嵌套 `rawQuery`（游标循环内再查同一连接） | 平台实现相关，长队列下有稳定性风险 |

### 2.2 协议语义缺陷

| # | 位置 | 缺陷 | 影响 |
| --- | --- | --- | --- |
| S1 | `TemporalRecord` | 用 `endMillis ?: deadlineDate` 双字段表达 Deadline，允许两者同时存在；`DraftForm.from` 日期优先，静默丢弃时间 | I-13 日期精度语义不封闭 |
| S2 | `TemporalRecord` | 无 `precision` 标记，UI 无法区分"08:00 截止"与"8 月 3 日截止"，日期级只能画在 00:00 | 时间轴会出现伪造的 00:00 |
| S3 | `DraftForm.build` | 转换类型时缺失字段只抛解析异常（"Text '2026-10-24' could not be parsed"） | G8-09=B 要求"明确列出必须补全的字段" |
| S4 | 全局 | 无类型转换规则对象：Activity→Alarm 保留 `endText` 但隐藏，产生的快照与确认内容不一致 | 转换不可审计 |
| S5 | 全局 | Trigger / ScheduleIntent / ExecutionRecord / Recurrence 完全缺失 | P3 的提醒可靠性无承载结构 |
| S6 | `TimelineLayout.assign` | 按全库排序分配轨道，未区分左轴/右轴；`endMillis` 取自 Activity 区间，Alarm/Deadline 长度为 0 | 零长事件永不重叠，轨道数失真 |

### 2.3 UI 级缺陷（`DraftDeck.kt`）

| # | 缺陷 | 影响 |
| --- | --- | --- |
| U1 | 未接入 `MainActivity`，无入口 | 用户打开 App 看不到任何新功能 |
| U2 | `onStage` 在组合协程（主线程）里直接执行 SQLite 写 | 每次输入停顿 320ms 卡主线程 |
| U3 | 完成动画 `animateFloatAsState(1f, spring())` 目标恒定 | **无动画**；且 2.2 秒后自动关闭，无跳过、无真实统计 |
| U4 | `if(cards.size==1) completion=true` 使用过期 prop | AI 批量运行中或仍有剩余卡时也会弹"全部完成"，违反 G11-06 |
| U5 | 冲突对话框只列"本卡新内容"，不展示当前正式快照 | 不是 G10-03 要求的完整 before/after diff |
| U6 | 卡片外横滑惯性多张浏览 | **未实现**（G8-04） |
| U7 | `Dialog` 无 `imePadding`，输入框在键盘后 | 展开编辑不可用 |
| U8 | 无 PowerCal Glass 主题、无低端降级、无 reduced-motion、无对比度校验 | 视觉标准未落地 |
| U9 | `remember(current?.draftId)` 固化表单，外部（AI）更新同卡不回填 | 用户看到旧内容 |
| U10 | `import ...pointer.consume` / 未使用导入 | 编译风险与噪音 |

### 2.4 旧版模块审计

| 模块 | 结论 |
| --- | --- |
| `EventStore` / `CalendarEvent` | 无永久序号、无 revision、无草稿；`onUpgrade = Unit` 静默升级。作为历史数据源保留，**只读迁移**，不改造其 schema |
| `ReminderScheduler` | PendingIntent 仅携带 `event_id`；**触发时不校验 revision**（I-11 直接不成立）；`canScheduleExactAlarms` 失败时静默降级为不精确闹钟且不告知（违反 G4-06） |
| `ReminderReceiver` | 无 occurrence/trigger 校验，无执行记录，无 snooze |
| `BootReceiver` | 只处理 boot / package-replaced，无 `TIMEZONE_CHANGED`、无 `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` |
| `TextParser` | 确定性中文解析，可用；作为 AI 的离线回退与"待确认草稿"来源保留 |
| `IcsCodec` | 折叠行、转义、TZID 处理基本正确；无 RRULE 展开（有告警，符合"不猜"） |
| `SystemCalendar` | 只读 Instances，不写回；符合"迁移入、规则出"；OEM 闹钟限制已在 UI 说明 |
| `MainActivity` | 纯 v0.1 布局（日/月/闹钟/工具），无时间轴、无草稿卡组 |

---

## 3. 修复与实施计划

### P0 — 协议内核（先做，其余依赖它）

1. **重写 `TemporalModels.kt`**：`TimePrecision { DATE, INSTANT }`、`TemporalRecord`（单锚点 + precision + source/zone）、`DraftForm`（字段级 `missingFields()`、转换规则 `TemporalConversion`）、`CommitRules`（全量快照 + 过期判定）、`TimelineLayout`（左右分轴、稳定轨道、溢出折叠）、`TimelineCoordinate`（Double 精度缩放锚点）。
2. **重写 `PowerStore.kt`**（`powercal-v2.db` v1）：
   `meta`（next_seq / next_order / ai_epoch）· `identities`（分配时间 + retiredAt）· `objects`（快照 + revision + retired）· `drafts`（queueOrder UNIQUE + draftVersion）· `commits`（commitId 幂等 + actor + operation）· `ai_sessions`（sessionId/epoch/state/scope/resume 信息）· `source_links` · `schedule_intents` · `executions`。
   事务 + CAS + 幂等 + 锁代际 fencing + 墓碑恢复 + 导入记账。
3. **迁移器**：`powerful-calendar.db` → v2，用户显式确认、逐条 source/externalId 去重、保留原库文件不动。
4. **测试**：JVM 纯逻辑 + Robolectric SQLite 用例，覆盖 §8 列出的协议不变量。

### P1 — 中央时间轴 + 待处理

5. `PowerTheme.kt`：PowerCal Glass 设计系统（浅/深、玻璃层级、降级、动效规格、reduced-motion）。
6. `TimelineScreen.kt`：纵向刻度、NOW 横线、4–48h 双指锚定缩放、连续滚动、左右分轨、日期带 Deadline、长按拖动吸附。
7. `DraftDeck.kt`：中央玻璃弹窗、扇形背卡、卡内单步/卡外惯性、纵向滚动、IME 避让、防抖暂存 + 切页 flush、勾/叉、撤销、真实统计完成动画、冲突 diff。
8. 状态逻辑抽为**纯 Kotlin `DeckController`**，使"快速滑动不丢字""重复点击不重复发布"可单测。
9. `MainActivity` 重写为宿主：时间轴 + 待处理 + 工具/设置。

### P2 — 数据接入 + 单 AI

10. ICS / 系统日历 → 严格校验后直接发布（`importStructured`，带 MigrationRecord）。
11. 自然语言 / OCR → 待确认草稿。
12. 单 AI：OpenAI 兼容端点、结构化 JSON、可配置模型、按选定范围的一次性批量授权、session/epoch fencing、取消与恢复、迟到响应拒写。

### P3 — 提醒可靠性

13. `schedule_intents`（eventSeq+revision+occurrenceKey+triggerId+instant）与 `executions`；提交后异步 reconcile。
14. PendingIntent 携带 seq/revision/occurrence/trigger，**触发前回查库**；过期广播记录 `IGNORED_STALE` 不响铃。
15. `canScheduleExactAlarms` 缺失 → `BLOCKED` + 引导，不静默降级。
16. boot / 时区 / 权限 / 升级 全部 reconcile；短宽限窗口内可补响，否则 `MISSED`。

### P4 — 验收

17. 构建 + 单测 + 独立 Reviewer 审查 + 修复 + 提交 + 交付报告。
18. 真机项目（响铃、OEM 限制、手势体感）**明确标注未验证**。

---

## 4. 设计变更登记

| 变更 | 原因 | 影响 | 验证 |
| --- | --- | --- | --- |
| 新增 `TimePrecision` | 原双字段模型无法阻止伪造 23:59 / 00:00 | 快照格式、UI 渲染、转换规则 | 单测：日期级 Deadline 不产生时刻 |
| `objects` 增加 `precision` / `source` 卫生 | B1 | REPLACE 不再改写来源 | 单测：替换后 source 保持 |
| `drafts` 增加 `draft_version` CAS | B3 | `stageDraft` 失败可回退到最新内容 | 单测：并发暂存只接受新版本 |
| `ai_sessions` 独立表 | B5/B6 | 崩溃恢复可判定，不误放锁 | 单测：state=RUNNING 且超过无心跳才恢复 |
| `commitId` 显式化 | B7 | 幂等与审计可查 | 单测：同 commitId 返回同 revision |
| 状态逻辑移出 Composable | U3/U4/U5 | 可单测、行为确定 | 单测：完成动画门槛、flush 顺序 |

---

## 5. 验证环境约束（须如实记录）

- 机器初始**无 Android SDK**：`local.properties` 的 `D:\Android\Sdk` 不存在。
- 网络需经本机 HTTP 代理 `127.0.0.1:10808`；已在**用户级** `~/.gradle/gradle.properties` 配置，未写入仓库。
- 无 `adb` / 模拟器 → **真机项一律标注未验证**。
- 无 `JAVA_HOME`，PATH 中 Java 为 20.0.1。
