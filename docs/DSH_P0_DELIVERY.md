# DSH 底层接口回补 P0 · 交付记录

**日期:** 2026-10-20 · **基线:** `c37b331`（`codex/powercal-glass`，130 项测试通过）
**分支:** `dsh/p0-interfaces`（隔离开发，未合并 `main`，未发布 APK）
**范围:** DSH-07 / DSH-08 / DSH-09 的底层接口与正确性修复；**未改动 Glass 视觉、页面布局或动画**。

配套审计：[DSH_P0_AUDIT.md](./DSH_P0_AUDIT.md)

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
