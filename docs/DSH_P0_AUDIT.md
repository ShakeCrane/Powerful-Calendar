# DSH 底层接口回补 · P0 审计（DSH-07 / 08 / 09）

> **状态：三项均已修复，见 [DSH_P0_DELIVERY.md](./DSH_P0_DELIVERY.md)。**
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
永久回归测试为 `DeckControllerConcurrencyTest.editingNeverWaitsForStorageIo`（修复后 <200ms 断言）。

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

### 4.1 DSH-07：串行化、可取消、不丢输入的命令接口

- 所有**带 IO 的命令改为 `suspend`**，由 controller 内部的 `Mutex` 串行化；`edit()` 保持同步，只做内存更新。
- 状态写入改为“原子变换”（`update { it.copy(...) }`），状态锁只在内存临界区持有，**绝不在锁内做 IO**。
- 持久化调用放进 `withContext(ioDispatcher + NonCancellable)`：命令在**排队时**可取消；一旦开始原子写入就完成并记录结果（不会出现“已写库但状态未记”）。
- 新增 `ioDispatcher` 构造参数，测试可注入确定性调度器。
- UI 仅把 `command{}`/`leave()` 的 lambda 类型改为 `suspend`（不涉及布局/动画）。

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
  1. 先收集所有 override 中有效时间与窗口相交的实例（不受原始槽位位置限制）；
  2. 再扫描原始槽位在窗口内的自然实例，跳过已 override / exception 的键；
  3. 统一按有效开始时间过滤上界、按下界过滤；按有效开始时间排序；
  4. 身份仍是 `Occurrence.key`（原始槽位键）。
- `nextOccurrence()` 取“自然实例”与“override 实例”中最早的有效开始时间，override 不受 400 天窗口限制。
- 增加长活动（跨窗口起点）的回看槽位数。

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
