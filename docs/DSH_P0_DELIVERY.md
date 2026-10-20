# DSH 底层接口回补 P0 · 交付记录

**日期:** 2026-10-20 · **基线:** `c37b331`（`codex/powercal-glass`，130 项测试通过）
**分支:** `dsh/p0-interfaces`（隔离开发，未合并 `main`，未发布 APK）
**范围:** DSH-07 / DSH-08 / DSH-09 的底层接口与正确性修复；**未改动 Glass 视觉、页面布局或动画**。

配套审计：[DSH_P0_AUDIT.md](./DSH_P0_AUDIT.md)

---

## 1. 结论

| 编号 | 状态 | 一句话 |
| --- | --- | --- |
| DSH-07 | 已修复 | 存储 IO 不再发生在任何控制器监视器内；持久化命令串行、可排队取消、已接受的写入不会因取消而丢失 |
| DSH-08 | 已修复 | 批量授权与“用户审阅的那个 `draftVersion`”绑定，在同一事务内校验并可被提交期复查 |
| DSH-09 | 已修复 | 重复实例按**有效时间**检索，远距离移入/移出、DST、跨月、重复修改都不会漏或泄漏 |

验证：**151 项单元测试，0 失败**；`assembleDebug` 成功；`lintDebug` **0 错误、13 警告**（与 UI 交付基线持平，无新增类别）。
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
    suspend fun flush(): Boolean
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
- **取消**：排队中的命令可被取消（`withLock` 可取消）；一旦进入原子写入，在 `NonCancellable`
  上下文中完成并记录版本/`dirty` 结果，避免“已落库但状态不知道”。
- **切页不丢尾部输入**：`flushFully()` 在切换前最多重试 3 次；若写入期间又有输入，最新文本会再落库后才换卡。
- **单一面板**：仍只有 `DeckController` 一个状态机与一个命令队列，没有引入第二套生命周期管理器。

### 2.4 测试

`DeckControllerConcurrencyTest`（5 项，真实线程）：
- `editingNeverWaitsForStorageIo`：慢 `stage()` 期间 `edit()` < 200ms 返回。
- `concurrentMovesSerializeInsteadOfBothLandingOnTheSameIndex`：两个并发 `move(1)` 依次前进到第 3 张。
- `aQueuedCommandCanBeCancelledBeforeItTouchesStorage`：排队中的 `refresh` 取消后不访问存储。
- `cancellingACommandMidWriteStillRecordsTheAcceptedInput`：写入中取消，文本仍落库且版本/dirty 正确。
- `navigationRacingAnEditStagesTheLastCharacterBeforeSwitching`：慢首写 + 并发输入，换卡前最后一字已持久化。

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

`nextOccurrence(record, after)`：取“所有有效时间 ≥ after 的 override”与“自然实例窗口内最早的槽位”的较小者；
override 不受 400 天常规窗口限制。

### 4.3 测试

`OccurrenceWindowTest`（9 项）：
- 60 天前的槽位移到今天 → 今天的窗口能查到，键仍是原槽位；
- 未来月的槽位移到更早的窗口 → 能查到；
- 今天移到 60 天后 → 今天窗口不再泄漏；
- 跨 DST 春季缺口移动 → 有效瞬间按 03:30 EDT 解析，键不变；
- 同一实例连续修改 → 只保留一个条目；
- `nextOccurrence` 找到远距离移入的实例；远期 override 不会压过更早的自然实例；
- 自然实例仍只出现一次；
- 跨夜活动（20:00–04:00）在一天窗口内正确出现两次（昨夜尾巴 + 今日开始）。

### 4.4 下游影响

- `ReminderScheduler.validate()` 使用同一查询：移动过的实例在有效时刻仍能通过校验；原有 9 项提醒测试通过。
- UI `displayOccurrences()` 无需改动：远距离移入的实例会出现在目标日期，移出的不再出现在原日期。
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
| `RecurrenceEngine.occurrences/nextOccurrence` | 语义改为有效时间；签名不变 | 调度、时间轴投影、月历 |

`main` 分支未受影响；`codex/powercal-glass` 未被覆盖。

---

## 6. 验证证据

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
# BUILD SUCCESSFUL
# 151 tests / 13 suites / 0 failures / 0 skipped
# lintDebug: 0 errors, 13 warnings
```

- APK：`app/build/outputs/apk/debug/app-debug.apk`（本地构建产物，未发布、未入库）
- 测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`
- Lint：`app/build/reports/lint-results-debug.html`（13 条与 UI 交付基线一致：依赖/targetSDK 建议、旧资源、既有业务层 `UseKtx` 等）

**没有设备验证**：本轮未连接 `adb` 设备，也没有配置模拟器；真机项（真实键盘/选择手柄、强杀尾部窗口、
60/120Hz 帧时长、厂商提醒限制）仍需按 GLASS_DEVICE_QA.md 执行。

---

## 7. 对 DSH-01 / 03 / 05 的评估（真机验收前是否必须）

| 编号 | 结论 | 理由 |
| --- | --- | --- |
| DSH-01 occurrence 草稿命令（THIS/FOLLOWING/SERIES） | **不阻塞真机验收**，但阻塞“重复事件编辑”对外宣称 | 当前重复拖动被明确拒绝，不会误改系列；DSH-09 已让实例投影正确。设备验收可先覆盖非重复事件。若要发布重复编辑功能，必须先做该命令。 |
| DSH-03 可观察快照 + 稳定 laneKey | **不阻塞**；建议在与 Compose 状态重构同期做 | DSH-09 已提供按有效时间的实例查询；真正缺的是带 revision/lock 的 observable snapshot。当前 UI 用只读投影，数据量在个人日历规模内可接受。 |
| DSH-05 旧闹钟显式 handoff 状态/命令 | **建议在面向真实用户迁移前完成**，不阻塞内部验收 | 迁移的闹钟默认停用，避免重复响铃；但 `enabled` 不能证明原时钟已关闭，UI 只能解释风险。若真机验收包含“从旧版迁移”场景，应把该场景标注为“需人工关闭原闹钟”。 |

DSH-02/04/06 不属于本轮，也未被 UI 伪装实现。

---

## 8. 已知限制

- 强杀进程前最后约 300ms 的输入仍无绝对保证（UI `ON_STOP` flush 为最佳努力）。
- `commit()` 在最终 flush 与提交之间的窗口依赖 UI 的 `busy` 禁用输入；领域层没有“提交时再比对 form”的二次校验。
- `nextOccurrence` 对超出 400 天窗口的**自然**槽位仍以 override 为优先；极端远期自然实例不会被枚举（与既有设计一致）。
- `beginAiBatch` 要求调用方传入审阅版本；旧调用方式已移除，第三方代码需迁移。
