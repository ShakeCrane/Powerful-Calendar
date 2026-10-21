# DSH 07–09 接口：审计、修复与验证

> 合并于 2026-10-21。以下保存原始阶段报告正文；历史时间点、测试数量、完成状态仅对其记录时的基线有效，不代表当前 HEAD 已重新验证。

## 第一部分：修复前审计（历史证据）

# DSH 底层接口回补 · P0 审计（DSH-07 / 08 / 09）

> **状态：三项均已修复，见 [DSH_P0_DELIVERY.md](DSH_P0_CASE_STUDY.md)。**
> 本文件保留接管时的只读审计与红测试证据（修复前）。

**日期:** 2026-10-20 · **基线:** `c37b331`（`codex/powercal-glass` 工作区干净）
**分支:** `dsh/p0-interfaces`（从 `c37b331` 派生，不改动 UI 交付分支）
**验证基线:** `:app:testDebugUnitTest` → **130 项、10 套件、0 失败**
**范围:** 只修 `core` 领域接口与必要的调用点；**不修改 Glass 视觉、页面布局或动画**。

---

## 1. DSH-07 — 草稿编辑持久化与同步持锁 IO

### 1.1 现状

| 位置 | 事实 |
| --- | --- |
| `DeckController.kt:114-544` | `open/refresh/flush/select/move/jump/commit/discard/undoDiscard/requestRestore/confirmConflictReplacement` 全部 `@Synchronized` |
| `DeckController.kt:198-248` | `stageWithRetry` 在持有对象监视器时调用 `persistence.stage()`（SQLite 写，最多两次） |
| `DeckController.kt:176-183` | `edit()` 同样 `@Synchronized`，只改内存状态，却要与上面的 IO 争同一把锁 |
| `DraftDeck.kt:181-229` | UI 用 `withContext(Dispatchers.IO)` 调用这些同步方法；`onStop` 用 `appScope` 再发起一次 `flush()` |
| `DraftDeck.kt:216-229` | 300ms 防抖暂存同样在 IO 协程里调用 `flush()` |

### 1.2 失败模式（可复现）

1. **主线程被存储 IO 阻塞**：`flush()` 在 IO 线程持有监视器执行 `stage()`；用户继续输入时，主线程 `edit()` 必须等待监视器。慢存储、WAL checkpoint 或大批量 `pending()` 读取时，表现为输入卡顿。
2. **取消不生效**：同步方法没有挂起点，UI 协程被取消后仍会把当前写入做完，且调用方无法知道“已取消但已落库”。
3. **串行化只在 UI 层**：`busy` 只覆盖 `command{}`/`leave()`，不覆盖 `appScope` 的 `onStop` flush。两者可并发进入 controller，靠监视器互斥，但没有“谁先谁后”的明确顺序，也没有可观察的命令结果。
4. **无命令标识**：导航与终态命令无法区分“排队中 / 执行中 / 已完成 / 已取消”，UI 只能靠 `busy` 布尔值推断。

### 1.3 复现证据

修复前实测（红）：`DshRedProbeTest.editingIsNotBlockedByInFlightStorage` —
持久化层在 `stage()` 内阻塞，后台线程发起 `flush()` 后，主线程 `edit()` **等待 302ms** 才返回。
永久回归测试为 `DeckControllerConcurrencyTest.editingNeverWaitsForBackgroundAutosaveIo`（修复后 <200ms 断言）。

---

## 2. DSH-08 — AI 批量授权原子性

### 2.1 现状

| 位置 | 事实 |
| --- | --- |
| `PowerStore.kt:905-935` | `beginAiBatch(scope: Set<Long>, allowedOperations)` 只校验：非空、未锁、卡片存在且可执行、操作在允许集合内 |
| `PowerStore.kt:1057-` | `authorize()` 校验会话状态、owner、epoch、scope、操作；**不校验 `draftVersion`** |
| `PowerCalApp.kt:781-792` | UI 先重读 `pendingDrafts()` 与审阅副本比较，再**另起事务**调用 `beginAiBatch` |
| `PowerStore.kt:496-524` | `stageDraft(..., aiSession)` 允许带 AI 会话改写卡片（当前 runner 不调用，但接口开放） |

### 2.2 失败模式

1. **审阅后修改仍被授权**：审阅副本与 `beginAiBatch` 之间若发生一次 `stageDraft`（同一卡片 `draftVersion+1`），UI 的重读比较与授权是两个事务，授权会覆盖用户没看过的内容。
2. **授权后内容可变化**：`authorize()` 不比对 `draftVersion`；AI 会话可通过 `stageDraft` 改写已授权卡片，后续提交提交的是未审阅内容。
3. **操作集合是“成员判断”**：`card.operation in allowedOperations` 不能证明“这张卡在审阅时就是该操作”，也无法阻止范围外卡片被后续追加（scope 固定，这点已成立）。

### 2.3 复现证据

修复前实测（红）：`DshRedProbeStoreTest.anEditBetweenReviewAndAuthorizationMustNotBeCommitted` —
审阅后修改卡片，仍能通过 `beginAiBatch(setOf(id), ops)` 授权并提交未审阅内容。
永久回归测试见 `AiAuthorizationTest`（7 项，使用新签名）。

---

## 3. DSH-09 — 重复实例有效时间查询

### 3.1 现状

| 位置 | 事实 |
| --- | --- |
| `TemporalModels.kt:201-255` | `occurrences(record, from, to)` 以**原始槽位时间**做二分定位与循环边界 |
| `TemporalModels.kt:237-253` | 只遍历原始槽位落在 `[from, to)` 的实例；override 仅在遍历到该槽位时套用 |
| `TemporalModels.kt:250` | 只用有效结束时间过滤下界，**没有过滤有效开始时间上界** → 被移出窗口的实例仍会返回 |
| `TemporalModels.kt:327-333` | `nextOccurrence` 以 `[after-duration, after+400d]` 的原始槽位窗口检索 |
| `GlassPresentation.kt:38` | UI 直接依赖该语义做可见窗口投影 |

### 3.2 失败模式

1. **移入窗口的实例丢失**：某实例原始槽位在 60 天前，被 override 移到今天；查询今天的窗口时循环从“今天附近的原始槽位”开始，永远看不到它。
2. **移出窗口的实例泄漏**：今天的实例被移到 60 天后，查询今天仍会返回它（上界未过滤），时间轴上出现窗口外条目。
3. **远距离重排后 `nextOccurrence` 失约**：把很久以前的实例改到未来，调度层查不到下一次提醒；把远期实例改到近期，也会漏。
4. 身份本身正确：`Occurrence.key` 始终是原始槽位键，override 只改有效时间。

### 3.3 复现证据

`OccurrenceWindowTest`：
- 原始槽位 60 天前 → 移到今天，查询今天应为 1 条（当前 0 条）。
- 今天 → 移到 60 天后，查询今天应为 0 条（当前 1 条）。
- 月度系列、DST 跨越、连续两次修改同一实例。

---

## 4. 最小修复方案

### 4.1 DSH-07：串行命令与可恢复暂存

- 所有**带 IO 的命令改为 `suspend`**，由 controller 内部的 `Mutex` 串行化；`edit()` 保持同步，只做内存更新。
- 状态写入改为“原子变换”（`update { it.copy(...) }`），状态锁只在内存临界区持有，**绝不在锁内做 IO**。
- 持久化命令通过 `withContext(ioDispatcher)` 派发，不把整段操作包进 `NonCancellable`。等待 `Mutex` 和尚未开始执行的 dispatcher 工作可取消；同步存储调用开始后不能被 Kotlin 中断，代码会完成并记录版本/dirty 状态。
- 暂存抛出异常时保留 dirty 文本并发布可重试错误。300ms 防抖与 `ON_STOP` flush 都是尽力暂存；进程在成功 flush 前被强杀时不能保证恢复内存中的尾部输入。
- 自动暂存用 `flush(lockEdits = false)`；导航和终态命令锁定编辑，刷新最新状态后有限重试再切卡。
- 新增 `ioDispatcher` 构造参数，测试可注入确定性调度器。
- UI 命令调用改为挂起调用，并在 `ON_STOP` 通过进程级 scope 排队尽力暂存（不涉及布局/动画）。

**接口变化（破坏性，需记录）：**
`open/refresh/flush/select/move/jump/commit/discard/undoDiscard/requestRestore/confirmConflictReplacement` → `suspend`。
`edit`、`observe`、`state` 不变。

### 4.2 DSH-08：按审阅版本原子授权

```kotlin
fun beginAiBatch(
    reviewed: Map<Long, Long>,                 // draftId -> 审阅时 draftVersion
    allowedOperations: Set<DraftOperation>,
    sessionId: String = UUID.randomUUID().toString()
): AiSession
```

同一事务内：
1. 未加锁、清单与操作集合非空；
2. 每张卡存在、可执行、`draftVersion == reviewed[id]`、`operation ∈ allowedOperations`；
3. 写入 `ai_sessions` 与新的 `ai_session_drafts(session_id, draft_id, draft_version, operation)`；
4. 任何审阅后变化 → 抛错，不建立会话、不加锁。

`authorize()` 追加：提交/丢弃时 `card.draftVersion` 必须等于该会话记录的审阅版本。

**Schema：** `VERSION 1 → 2`，仅**新增** `ai_session_drafts` 表；`onUpgrade(1→2)` 幂等建表，其余升级仍显式拒绝。

**接口变化：** `beginAiBatch` 签名变更；UI 传入审阅快照的版本映射；测试同步更新。

### 4.3 DSH-09：有效时间窗口语义

- `occurrences()` 改为**有效时间**语义：
  1. 先收集有效时间与窗口相交的 override；并验证原始 key 确实属于重复规则，满足 `count` / `until`，且没有被 exception 排除；
  2. 再扫描原始槽位在窗口内的自然实例，跳过已 override / exception 的键；
  3. 统一按有效开始时间过滤上界、按下界过滤；按有效开始时间排序；
  4. `limit` 限制返回条数；扫描预算同时考虑 exception / override key 数量，密集排除项不会让窗口内结果被静默截断；
  5. 身份仍是 `Occurrence.key`（原始槽位键）。
- `nextOccurrence()` 直接找 `after` 之后最早的合法自然起点，并与所有满足原始规则的未来 override 比较；不设固定天数 horizon，长活动不会用历史起点耗尽结果限额。
- 新增 `occurrenceForKey()`，提醒验证按精确原始 key 定位实例，不依赖大窗口的结果条数上限。

**接口变化：** 仅语义收紧，签名不变；UI 与调度层自动受益。

---

## 5. 非目标

- 不实现 DSH-01（occurrence 草稿命令 / THIS·FOLLOWING·SERIES）。
- 不实现 DSH-02（发布补偿撤销）。
- 不实现 DSH-03（observable snapshot）、DSH-04（逐项进度流）、DSH-05（旧闹钟 handoff 状态机）、DSH-06（版本化 diff DTO）。
- 不修改 Glass 视觉、页面布局、动画与现有 UI 测试的断言内容。

## 6. 验证计划

1. 先写红：DSH-07 阻塞、DSH-08 竞争、DSH-09 窗口/远距离/DST 用例。
2. 实施修复，转绿。
3. 全量：`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`。
4. 独立 Reviewer 只读审查；按发现修复。
5. 提交到 `dsh/p0-interfaces`，不合并 `main`、不发布 APK。

---

## 7. 当前工作树复审补充

以当前 `d7356c4` 加未提交改动为基线复查时，原有 DSH-07/08/09 定向回归通过；Reviewer 复核另指出：

- `ON_STOP` flush 是 `appScope` 中的非阻塞任务，排在 Controller 命令队列后执行；它不能证明进程强杀时数据已落盘。当前失败会把输入留为 dirty 并发布错误，设备强杀仍需真机验证。
- override 投影未校验原始 key 的 `count` / `until` 边界；长活动会让 `nextOccurrence` 的 512 条结果限额被 `after` 前的起点耗尽；固定 `limit * 4` 扫描预算会被密集 exception 消耗。以上均已补回归并修复。
- ReminderScheduler 过去通过按活动跨度扩大窗口并筛选 key，仍会被通用结果 limit 截断；现已改为按 key 精确校验。
- 后续复审发现合法自然间隔超过 400 天仍受 horizon 限制，现已移除固定 horizon，并改为按原始槽位直接搜索；同时修正单次实例投影与核心 exception/override 语义不一致，以及月历午夜半开区间边界。
- 时间轴原先绕过单次记录的共享投影，现已接入同一 helper；有界 UI 查询会优先保留窗口内开始的实例，再选择最近的跨窗活动。
- UI 时间轴仍保留 2048 个 occurrence 的渲染上限；若窗口内本身超过该值（例如大量 override 同时移入）仍会截断，作为有界渲染限制记录。

修复后 `OccurrenceWindowTest` 覆盖 count/until、weekly byDay/interval、长活动与密集 exception；`ReminderValidationTest` 覆盖长活动提醒定位；`DeckControllerConcurrencyTest` 覆盖慢自动暂存期间的生命周期 flush 排队及存储异常。全量结果见交付记录。


---

## 第二部分：修复后交付（历史证据）

# DSH 底层接口回补 P0 · 交付记录

**日期:** 2026-10-20 · **基线:** `c37b331`（`codex/powercal-glass`，130 项测试通过）
**分支:** `dsh/p0-interfaces`（隔离开发，未合并 `main`，未发布 APK）
**范围:** DSH-07 / DSH-08 / DSH-09 的底层接口与正确性修复；**未改动 Glass 视觉、页面布局或动画**。

配套审计：[DSH_P0_AUDIT.md](DSH_P0_CASE_STUDY.md)

---

## 1. 结论

| 编号 | 状态 | 一句话 |
| --- | --- | --- |
| DSH-07 | 已修复 | 存储 IO 不在状态锁内；命令串行，等待/派发阶段可取消；同步调用开始后完成并记录结果，失败保留 dirty 输入 |
| DSH-08 | 已修复 | 批量授权与“用户审阅的那个 `draftVersion`”绑定，在同一事务内校验并可被提交期复查 |
| DSH-09 | 已修复 | 重复实例按**有效时间**检索，远距离移入/移出、DST、跨月、重复修改都不会漏或泄漏 |

本轮全量验证通过：166 项测试、13 个测试套件，失败/错误/跳过均为 0；lint 0 错误、13 警告。APK 仅本地调试构建，不发布、不分发。
真机项仍未验证（本轮无设备）。

---

## 2. DSH-07 — 草稿编辑可靠持久化

### 2.1 问题（已复现）

修复前 `DeckController` 的每个方法都 `@Synchronized`，`flush()` 在持有监视器时执行 SQLite 写。
主线程 `edit()` 必须等待同一监视器：**红测试实测阻塞 302ms**（`DshRedProbeTest`，已由永久测试取代）。
同时，同步 API 没有挂起点，取消无法生效，命令顺序只能靠 UI 的布尔 `busy` 推断。

### 2.2 新接口

```kotlin
class DeckController(
    persistence: DeckPersistence,
    clock: () -> Long = System::currentTimeMillis,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val state: DeckState                      // 只读快照
    fun observe(listener: (DeckState) -> Unit)
    fun edit(form: DraftForm)                 // 非挂起：只改内存，永不等待 IO

    suspend fun open(preferDraftId: Long? = null)
    suspend fun refresh(preferDraftId: Long? = null)
    suspend fun flush(lockEdits: Boolean = true): Boolean
    suspend fun select(draftId: Long): Boolean
    suspend fun move(delta: Int): Boolean
    suspend fun jump(steps: Int): Boolean
    suspend fun commit(approveStale: Boolean = false): Boolean
    suspend fun confirmConflictReplacement(): Boolean
    suspend fun requestRestore(): Boolean
    suspend fun discard(): Boolean
    suspend fun undoDiscard(): Boolean
}
```

### 2.3 语义

- **状态**只经 `update {}` 原子变换，`stateLock` 内不做 IO；`edit()` 因此不可能被存储阻塞。
- **命令**由 `Mutex` 串行；`busy` 标志随命令发布，UI 可据此禁用终态按钮。
- **取消**：排队中的命令可被取消（`Mutex` 可取消）；拿到锁后、IO dispatcher 执行命令体前仍检查取消。同步 SQLite 调用一旦开始无法被协程中断，会自然完成并记录版本/`dirty` 结果；不会把整段读取/导航都包成 `NonCancellable`。
- **后台防抖暂存不锁编辑器**：`flush(lockEdits=false)` 与编辑并行，主线程 `edit()` 只取短状态锁；写入完成后比较快照，若有新输入保留 `dirty=true`，由下一次防抖/离开屏障继续保存。
- **切页不丢尾部输入**：导航/终态命令使用默认 `lockEdits=true`，先 `flushFully()`（最多重试 3 次）；命令开始后的迟到 IME 回调会被明确拒绝并发布错误，而不会先接受再丢弃。命令队列按 FIFO 串行。
- **单一面板**：仍只有 `DeckController` 一个状态机与一个命令队列，没有引入第二套生命周期管理器。

### 2.4 测试

`DeckControllerConcurrencyTest`（8 项，真实线程/门控 dispatcher）：
- `editingNeverWaitsForBackgroundAutosaveIo`：慢防抖 `stage()` 期间 `edit()` < 200ms 返回；新文本保持 dirty 并可再暂存。
- `concurrentMovesSerializeInsteadOfBothLandingOnTheSameIndex`：两个并发 `move(1)` 依次前进到第 3 张。
- `aQueuedCommandCanBeCancelledBeforeItTouchesStorage`：排队中的 `refresh` 取消后不访问存储。
- `cancellationAfterMutexButBeforeStorageDispatchDoesNotRunTheCommand`：已获命令锁但 IO dispatcher 尚未执行时取消，不触碰存储。
- `cancellingACommandMidWriteStillRecordsTheAcceptedInput`：写入中取消，文本仍落库且版本/dirty 正确。
- `navigationSavesTheLastAcceptedCharacterAndRejectsEditsDuringTheTransition`：导航开始前接受的最后一字在慢存储下落库；busy 期间迟到 IME 事件被拒绝而不伪称已接受。
- `lifecycleFlushQueuedBehindAutosavePersistsTheLatestAcceptedEdit`：自动暂存慢写期间接收的新输入由排队的生命周期 flush 落盘。
- `storageFailureKeepsAcceptedInputDirtyAndPublishesRetryState`：存储异常不清除输入，发布可重试错误并清理 busy 状态。

另有 `PowerStoreTest.stagedLastCharacterSurvivesStoreReopen`：暂存后的 SQLite 数据在关闭/重开 store 后保留。它**不是**系统强杀/Android 进后台真机测试。

`DeckControllerTest` 原有 15 项交互规则全部保留（改用 `runBlocking` + `Dispatchers.Unconfined` 驱动）。

### 2.5 残余风险

- 进程被系统**强杀**时，300ms 防抖窗口内未 flush 的输入仍可能丢失；UI 的 `ON_STOP` flush 是最佳努力，
  本轮没有、也无法在 JVM 测试中证明强杀语义。
- `commit()` 在最后一次 flush 与提交之间仍有一个极短窗口；UI 在 `busy` 期间禁用输入，生产路径不会触发。

---

## 3. DSH-08 — AI 批量授权原子性

### 3.1 问题（已复现）

修复前 `beginAiBatch(scope: Set<Long>, ops)` 只校验 id/操作/锁，UI 需“先重读比较、再另起事务授权”。
红测试证明：审阅后修改卡片仍可被授权并提交用户没看过的内容。
此外 `authorize()` 不比对 `draftVersion`，且 `stageDraft(aiSession)` 允许会话改写已授权卡片。

### 3.2 新接口

```kotlin
fun beginAiBatch(
    reviewed: Map<Long, Long>,            // draftId -> 审阅时的 draftVersion
    allowedOperations: Set<DraftOperation>,
    sessionId: String = UUID.randomUUID().toString()
): AiSession
```

同一事务内校验：
1. 未持有 AI 锁、清单与操作集合非空；
2. 每张卡存在、可执行、`operation ∈ allowedOperations`；
3. `card.draftVersion == reviewed[draftId]`；
4. 写入 `ai_sessions` 与 `ai_session_drafts(session_id, draft_id, draft_version, operation)`；
5. 任一条不满足 → 抛错，不建会话、不加锁。

提交/丢弃期：`authorize()` 追加“当前 `draftVersion` == 该会话记录的审阅版本”。
`stageDraft(aiSession != null)` 直接返回 `NotAuthorized`（AI 的产物是批次前的新卡片，不在批次内改卡）。
备份恢复会清除 `ai_session_drafts`，旧授权不会复活。

### 3.3 Schema

`powercal-v2.db`：`VERSION 1 → 2`，仅**新增** `ai_session_drafts` 表。
`onUpgrade(1→2)` 幂等建表；其他版本跨度仍显式拒绝。升级测试：删除该表并把 `user_version` 置 1 后重开，
授权仍可用（`upgradingAVersionOneDatabaseAddsTheReviewTableWithoutDataLoss`）。

### 3.4 测试

`AiAuthorizationTest`（7 项）：
- 审阅后编辑 → 授权失败、不加锁；
- 审阅后丢弃 → 授权失败；
- 操作集合不含该卡片操作 → 授权失败；
- 精确快照 → 授权成功且 `reviewedVersions` 正确；
- 授权后 AI 尝试改卡 → `NotAuthorized`，提交仍是原始内容；
- 范围不随新卡增长；
- v1→v2 升级后仍可授权。

`PowerStoreTest` 的 AI 锁定/取消/纪元/恢复/备份用例全部迁移到新签名并通过。

### 3.5 UI 适配（仅调用点）

`PowerCalApp` 的批量确认框改为传 `review.cards.associate { it.draftId to it.draftVersion }`，
删除“先重读再授权”的两次事务写法（原子性已下沉）；打开确认框前先 `reload()` 以展示最新版本。
无布局/动画改动。

---

## 4. DSH-09 — 重复实例有效时间查询

### 4.1 问题（已复现）

旧实现以**原始槽位**定位与截断窗口：移入窗口的实例查不到、移出窗口的实例仍返回、
`nextOccurrence` 对远距离重排失约。红测试 8 项失败。

### 4.2 新语义

`RecurrenceEngine.occurrences(record, from, to)`：
1. 先遍历全部 override，按**有效时间**与窗口求交（不受原始槽位位置限制）；
2. 再扫描原始槽位落在窗口附近的自然实例（起点向前多取一个活动时长），跳过被 override / exception 的键；
3. 统一按有效时间过滤上下界；
4. 结果按有效开始时间排序，`limit` 仅限制结果数；
5. 身份始终是 `Occurrence.key`（原始槽位键）。

`nextOccurrence(record, after)`：取“所有有效时间 ≥ after 的合法 override”与“after 之后最早的合法自然槽位”中的较早者；自然重复间隔不受固定天数 horizon 限制。

### 4.3 测试

`OccurrenceWindowTest`（17 项）：
- 60 天前的槽位移到今天 → 今天的窗口能查到，键仍是原槽位；
- 未来月的槽位移到更早的窗口 → 能查到；
- 今天移到 60 天后 → 今天窗口不再泄漏；
- 跨 DST 春季缺口移动 → 有效瞬间按 03:30 EDT 解析，键不变；
- 同一实例连续修改 → 只保留一个条目；
- `nextOccurrence` 找到远距离移入的实例；远期 override 不会压过更早的自然实例；
- override 原始 key 必须是重复规则实际生成的槽位，并满足 `count` / `until`；weekly `byDay` 与 interval 也受校验；
- 长活动不会让过去的开始时间耗尽 `nextOccurrence` 的结果限额；自然重复间隔超过 400 天仍能找到下一槽位；密集 exception 不会因固定扫描次数而隐藏窗口内实例；
- 有界 UI 投影在容量内优先保留窗口内开始的实例，再补入最近的跨窗活动；
- 自然实例仍只出现一次；
- 跨夜活动（20:00–04:00）在一天窗口内正确出现两次（昨夜尾巴 + 今日开始）。

### 4.4 下游影响

- `ReminderScheduler.validate()` 通过 `RecurrenceEngine.occurrenceForKey()` 精确验证实例，不会被大窗口的通用结果 limit 截断；移动过的实例仍按有效时间校验。
- UI 时间轴和月历均使用核心有效时间投影；单次记录的 override/exception 一致。月历日窗口按半开区间判断，午夜结束的活动不泄漏到次日。
- `GlassPresentationTest` 增加两项只读投影/日期边界回归；没有改动 Glass 的视觉、布局或动画。
- UI 时间轴投影仍以 2048 条作为有界渲染上限；超长活动的跨窗实例会让位给窗口内开始的实例，若窗口内本身超过 2048 条（例如大量 override 同时移入）仍会截断较晚项目。
- 排序变化不影响 `TimelineLayout`（其内部按时间排序）。

---

## 5. 接口变化与兼容性

| 接口 | 变化 | 调用方 |
| --- | --- | --- |
| `DeckController.open/refresh/flush/select/move/jump/commit/confirmConflictReplacement/requestRestore/discard/undoDiscard` | 同步 → `suspend`；构造参数新增 `ioDispatcher` | `DraftDeck`、`PowerCalApp`、UI 测试 |
| `DeckController.edit` / `observe` / `state` / `dismiss*` / `clearError` / `setAiLocked` | 不变 | — |
| `DeckState` | 新增 `busy` | 未使用于布局；供调用方判断 |
| `PowerStore.beginAiBatch` | `Set<Long>` → `Map<Long, Long>`（id → 审阅版本） | UI 批量确认、测试 |
| `PowerStore.stageDraft(aiSession != null)` | 恒为 `NotAuthorized` | 无生产调用方 |
| `PowerStore.VERSION` | 1 → 2（仅新增表） | 数据库升级 |
| `AiSession.reviewedVersions` | 新增字段（默认空，兼容旧行） | UI/测试可读 |
| `RecurrenceEngine.occurrences/nextOccurrence/occurrenceForKey` | 窗口语义改为有效时间；按 key 精确读取；新增方法 | 调度、提醒校验、时间轴投影、月历 |

`main` 分支未受影响；`codex/powercal-glass` 未被覆盖。

---

## 6. 验证证据

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
# BUILD SUCCESSFUL in 7s
# 166 tests, 13 suites, 0 failures, 0 errors, 0 skipped
# lintDebug: 0 errors, 13 warnings
```

- APK：`E:\Project\Powerful-Calendar\app\build\outputs\apk\debug\app-debug.apk`（63,648,715 bytes；本地构建产物，未发布、未入库）
- 测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`
- Lint：`app/build/reports/lint-results-debug.html`（13 warnings，0 errors；包含 Gradle/依赖版本、SDK/API、资源及 `UseKtx` 建议）

**没有设备验证**：本轮未连接 `adb` 设备，也没有配置模拟器；真机项（真实键盘/选择手柄、强杀尾部窗口、
60/120Hz 帧时长、厂商提醒限制）仍需按 GLASS_DEVICE_QA.md 执行。

---

## 7. 对 DSH-01 / 03 / 05 的评估（真机验收前是否必须）

| 编号 | 结论 | 理由 |
| --- | --- | --- |
| DSH-01 occurrence 草稿命令（THIS/FOLLOWING/SERIES） | **不阻塞通用真机验收**，阻塞重复事件编辑的功能验收 | 重复事件拖动明确拒绝，不会误改系列；DSH-09 的只读实例投影可验收。设备用例应确认重复事件编辑被拒绝；发布重复范围编辑前必须完成 DSH-01。 |
| DSH-03 可观察快照 + 稳定 laneKey | **不阻塞本轮设备验收**；建议在 Compose 状态重构时完成 | 当前 UI 使用只读投影和显式刷新，足以验证现有功能；带 revision/lock 的响应式快照仍是后续防止跨页面短暂陈旧状态的领域接口补强。 |
| DSH-05 旧闹钟显式 handoff 状态/命令 | **不阻塞非迁移验收；阻塞迁移场景的完整验收** | `enabled` 不能证明原时钟闹钟已关闭。真机若执行旧版迁移，只能在人工关闭原闹钟并记录此限制后验证；面向用户迁移前应实现显式 handoff。 |

DSH-02/04/06 不属于本轮，也未被 UI 伪装实现。

---

## 8. 已知限制

- 强杀进程前最后约 300ms 的输入仍无绝对保证（UI `ON_STOP` flush 为最佳努力）。
- `ON_STOP` flush 通过进程级 scope 排在当前命令后，避免与自动暂存并发；它不等待 Activity 生命周期回调，也不能替代强杀前的同步耐久性保证。存储异常保留 dirty 输入和可重试错误。
- `commit()` 在最终 flush 与提交之间的窗口依赖 UI 的 `busy` 禁用输入；领域层没有“提交时再比对 form”的二次校验。
- `occurrenceForKey()` 按原始槽位直接校验；`occurrences()` 的异常/覆盖 key 扫描预算与这些配置项数量相关，系列极大且配置项很多时仍会增加查询工作量。
- 时间轴的重复实例投影上限为 2048；窗口内开始的实例优先于跨窗 carry-in，但窗口内本身超过此值时仍会截断；如需支持极端密集 override，应另加分页或截断提示。
- `beginAiBatch` 要求调用方传入审阅版本；旧调用方式已移除，第三方代码需迁移。
