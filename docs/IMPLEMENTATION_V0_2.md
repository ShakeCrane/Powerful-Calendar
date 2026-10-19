# PowerCal v0.2 — 实施记录与验证

**日期:** 2026-10-20 · **基线:** `6a894fd` → 本次提交 · **依据:** `docs/PRODUCT_BASELINE_G1-G11.md`, `docs/PROTOCOL_V0_RESEARCH.md`
**前置审计:** [AUDIT_P0.md](./AUDIT_P0.md)

---

## 1. 完成状态

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| P0 协议修复 | **完成** | 永久序号、独立队列序、整份快照提交、过期重确认、幂等提交、墓碑恢复、AI 锁与 fencing、原子事务 |
| P1 时间轴 + 待处理 | **完成（真机未验证）** | 中央纵向时间轴、NOW 线、4–48h 锚定缩放、左右分轨与溢出折叠、长按拖动吸附、玻璃卡片组、卡内单步/卡外多步滑动、暂存与 flush、完成动画 |
| P2 数据接入 + 单 AI | **完成（联网未验证）** | ICS / 系统日历 / 旧版迁移、OCR / 文本草稿、可配置 OpenAI 兼容端点、结构化校验、显式批量授权 |
| P3 提醒可靠性 | **完成（真机未验证）** | 期望调度表、revision/occurrence/instant 三重校验、权限受阻可见、错过宽限记录、boot/时区/升级重建 |
| P4 独立验收 | **部分** | 双 Reviewer 审查、86 个单测、构建与 APK；真机项全部未验证 |

---

## 2. 代码地图

```
core/
  TemporalModels.kt   三种时间形态、TimePrecision、转换规则、提交规则、轨道布局、轴几何、重复规则
  PowerStore.kt       powercal-v2.db：identities / objects / drafts / commits / ai_sessions /
                      source_links / migrations / schedule_intents / executions
  DeckController.kt   待处理交互内核（纯 Kotlin，可单测）：flush、翻页、冲突、完成门槛
  StoreDeckPersistence.kt  DeckPersistence 适配器
ai/
  AiModels.kt         配置、提案模型、严格 JSON 解析
  AiClient.kt         单次 chat/completions 调用，超时与错误可见
  AiDraftService.kt   提案 → 草稿；AiBatchRunner：独占、取消、fencing、部分成功
data/
  LegacyImporter.kt   只读迁移 v0.1，幂等，保留旧文件
reminders/
  AlarmContract / ReminderScheduler / ReminderReceiver / NotificationHelper / BootReceiver
ui/v2/
  PowerTheme.kt       PowerCal Glass 设计系统与降级
  TimelineScreen.kt   中央时间轴与手势
  DraftDeck.kt        待处理弹窗（卡组、编辑、冲突、完成）
  PowerCalApp.kt      宿主、工具页、导入、AI、诊断
MainActivity.kt       单 Activity 宿主、系统深浅色、动效偏好
```

保留但已不被引用：`data/EventStore.kt`、`data/CalendarEvent.kt`（后者仍被 ICS/系统日历/文本解析使用）、
`ui/CalendarScreens.kt`、`ui/EventEditor.kt` —— 属于 v0.1 历史原型，保留以便对照，不再进入导航。

---

## 3. 关键设计决策与偏差

| # | 决策 | 问题 | 影响 | 验证 |
| --- | --- | --- | --- | --- |
| D1 | Deadline 用「单一锚点 + `TimePrecision`」，日期级锚点取当日 00:00 **仅用于定位** | 原模型允许日期与时刻并存，且会显示 00:00，等于伪造精度 | 渲染层用日期带语义，文案不出现 00:00 | `dateOnlyDeadlineNeverBecomesAnInstant`、`dateOnlyDeadlineCommitsWithoutInventingATime` |
| D2 | 替换保留 `createdAt`、`source`、`zoneId` | 原实现用表单重建快照，会把来源悄悄改成 local | 整份替换只替换用户可见内容 | `replacementPreservesProvenanceAndCreationTime` |
| D3 | AI 批次不二次调用网络，只提交已展示的卡片 | 让"授权"变成可审计的确定动作，而非不可复现的模型判断 | 批次是机械提交；解析阶段才是联网 | `PowerStoreTest` AI 系列 6 例 |
| D4 | 卡组在授权期间冻结范围，AI 不新建卡 | PROTOCOL §4 明确"授权范围不得静默扩张" | 新卡必须等批次结束后产生 | `aiBatchLocksEveryHumanDeckWrite` |
| D5 | `processToken` 为进程级，而非实例级 | 接收器另开 `PowerStore` 会把 UI 正在跑的批次误判为孤儿 | 崩溃恢复只 fence 上一进程的会话 | `orphanedBatchIsFencedAndUnlockedOnRecovery` |
| D6 | 原生备份拒绝恢复到非空库 | 覆盖现有数据不可逆，也是用户没有要求的破坏性操作 | 恢复只能进新安装 | `nativeBackupRestoresIdentityOrderAndNeverAnAiLock` |
| D7 | ICS 导出只包含活动事件 | ICS 无法表达"只有开始"或"日期级截止"，硬导会伪造语义 | 导出显式说明有损 | 代码路径 + UI 文案；无自动化测试 |
| D8 | 玻璃效果为**分层半透明模拟**，不是真实背景模糊 | Compose 在各 API 等级没有可移植的背景采样；RenderEffect 仅 31+ 且非背景采样 | 视觉接近磨砂，低端机降级为纯色 | 主题实现；真机观感未验证 |
| D9 | 重复规则以"规则 + 原始槽位键"表达，UI 仅暴露频率 | 完整 "此实例/此后/整个系列" 交互未实现 | 模型已支持 exceptions/overrides，UI 未暴露 | `RecurrenceEngine` 6 例单测 |
| D10 | 提醒按事件维护单一 `primary` 触发器 | 多触发器 UI 属于后续工作 | 一事件当前只能有一个提醒 | `triggerOffsetRespectsReminderMinutes` |
| D11 | `uiMode` 列为 `configChanges` | 系统深浅色切换若重建 Activity 会丢失输入 | Compose 读取更新的 Configuration 直接重组 | 真机未验证 |

---

## 4. 验证证据

### 4.1 环境

| 项 | 值 |
| --- | --- |
| 机器初始状态 | 无 Android SDK、无 `JAVA_HOME`、Gradle 8.14.3 未缓存、`gradlew.bat` 损坏 |
| 本次安装 | `E:\android-sdk`（cmdline-tools + platform-tools + platforms;android-36 + build-tools;36.0.0） |
| JDK | 20.0.1（`C:\Program Files\Java\jdk-20`） |
| 网络 | 需经本机代理 `127.0.0.1:10808`；仅在用户级 `~/.gradle/gradle.properties` 与 `POWERCAL_TEST_PROXY` 中配置，未写入仓库 |

### 4.2 命令与结果

```
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon --console=plain
BUILD SUCCESSFUL
97 tests, 0 failures, 0 skipped (7 suites)
```

- APK：`app/build/outputs/apk/debug/app-debug.apk`（约 63.6 MB，主要来自中文本地 OCR 模型）
- 测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`

### 4.3 独立审查与修复（P4）

两名独立 Reviewer 在实现完成后分别审查了协议层与 UI 层，所有 blocker 与主要问题均已修复并补测：

| 审查发现 | 处理 | 证据 |
| --- | --- | --- |
| 重复规则用绝对序号与结果数比较，约 512 个槽位后静默返回空（旧闹钟会永久停响） | 区分 `index` 与 `emitted`，加迭代上限 | `anOldRecurringSeriesStillMaterialisesInACurrentWindow` |
| 批次协程被取消时锁永不释放（同一进程恢复逻辑刻意跳过） | `try/finally` + `NonCancellable` 收尾；恢复改为心跳租约；新增用户"强制解除独占" | `aStaleSameProcessBatchIsReleasedByTheLease`、`theAiRunnerReleasesTheLockAndPublishesOnlyItsScope` |
| 原生恢复清空调度表后从不重建 | 恢复时按对象重建 `schedule_intents` | `aRestoredBackupRebuildsItsDesiredSchedule` |
| 日期级截止 + 提醒会伪造 00:00 触发 | 调度层拒绝日期级截止；类型转换清除提醒 | `aDateOnlyDeadlineWithAReminderGetsNoFabricatedMidnightAlarm` |
| 冲突确认使用未版本化的布尔值，可能覆盖用户没看过的版本 | 记录 `currentRevision` 与 `approvedForm`，确认前重新校验并重算差异 | `aConflictIsRevalidatedBeforeOverwritingAnything`、`editingWhileTheConflictDialogIsOpenAlsoForcesAReconfirmation` |
| `markIntent` 无版本守卫，可能把新修订标成已调度 | 更新条件加入 `revision` / `occurrence_key`，0 行即放弃 | `scheduledIntentIsNotMislabelledWhenTheRowWasReplaced` |
| AI 的 REPLACE 会改写来源与时区 | REPLACE 继承原对象来源与时区，并按瞬间换算墙钟文本 | `aReplaceKeepsTheOriginalProvenanceAndZone` |
| 上下文外的 REPLACE 被降级为 CREATE，产生重复事件 | 保留为 blocked 提案并提示，绝不新分配序号 | `aReplaceWithAnUnknownEventIsBlockedInsteadOfBecomingADuplicate` |
| AI 上下文无逐次授权 | 改为显式开关，默认不共享；开启后最多 60 条并回报条数 | 设置页开关 + 结果提示 |
| 恢复绕过触发卡片，卡片仍留在队列 | 新增 `restoreDraft`，在同一事务内提交卡片并移出队列 | `deleteRetiresTheNumberAndLateEditsNeedExplicitRestore` |
| 时间轴手势无 touch slop，抢走 chip 与顶栏按钮的点击和长按 | 越过 `viewConfiguration.touchSlop` 才开始平移并消费 | 代码路径；真机手感未验证 |
| Delete 提案上的 X 实际执行删除 | X 恒为"不执行"，DELETE 卡片显示"不删除" | 代码路径 |
| 卡组编辑字段未按 draftId 分键，光标/选中跨卡泄漏 | `key(card.draftId)` 包裹卡片内容 | 代码路径 |
| 指针节点以内联 lambda 为 key，重组中断手势 | `pointerInput(Unit)` + `rememberUpdatedState` | 代码路径 |
| AI 批次结束时卡组仍只读、批量清空后没有完成页 | `refresh` 重读锁状态并在队列清空且解锁后生成完成页 | `completionAppearsOnlyForAnEmptyUnlockedQueueWithRealCounts` |
| 丢弃撤销仅存在于完成页 | 队列中提供"撤销上一次丢弃" | `undoingTheLastDiscardCancelsCompletion` |
| 工具页在组合期查询 SQLite | 以 `remember(records)` 缓存诊断数据 | 代码路径 |
| 溢出事件只能看到 "+N"，无法打开 | 徽标可点，列出折叠事件并可打开 | 代码路径 |
| 工具页系统返回直接退出应用 | `BackHandler` 返回时间轴 | 代码路径 |
| 导入先分配序号后校验，被拒记录会烧掉永久序号；批次期间仍可导入 | 先校验后分配；批次期间拒绝导入 | 代码路径 |
| 统计把导入/恢复计为"已发布" | 只统计有卡片关联的提交 | `statsAreDerivedFromDurableRecords` |

仍未覆盖的自动化测试：`ReminderReceiver` / `BootReceiver` 的端到端广播、`restoreDraft` 的 UI 路径、以及全部 Compose 交互（无模拟器）。这些在 §5 明确标注为未验证。

### 4.3 必测项对照（§8）

| 要求 | 证据 |
| --- | --- |
| 永久序号永不复用 | `firstDraftAllocatesASequenceEvenIfItIsDiscarded` |
| 同序号多张草稿 | `oneEventMayOwnManyCardsWithIndependentQueueOrder` |
| 草稿排序与序号独立 | `queueOrderIsIndependentOfEventNumber`、`promoteAfterCommitUsesQueueOrderNotTheOldIndex` |
| 完整对象覆盖 | `lastValidCommitWinsWithTheWholeSnapshot` |
| 过期快照不静默覆盖 | `staleCardCannotSilentlyOverwriteAndRequiresExplicitApproval`、`aConflictKeepsTheCardAndAsksForExplicitReplacement` |
| 快速滑动不丢文字 | `movingFlushesTheLatestTextFirst`、`failedSaveBlocksTheTransitionAndKeepsText` |
| AI 追加不打断位置 | `aiAppendingCardsDoesNotStealTheCurrentCard` |
| AI 批量独占与取消恢复 | `aiBatchLocksEveryHumanDeckWrite`、`cancelStopsFurtherAiWritesAndKeepsFinishedOnes`、`orphanedBatchIsFencedAndUnlockedOnRecovery` |
| 旧响应被 fencing 阻止 | `aiCannotWriteOutsideItsScopeOrAfterTheEpochIsFenced` |
| 重复点击不重复发布 | `repeatedCommitReturnsTheOriginalOutcome`、`repeatedClicksCannotPublishTwice` |
| 最后一张撤销取消完成动画 | `undoingTheLastDiscardCancelsCompletion` |
| 深色模式实时切换 | **未验证**（代码路径存在；无设备） |
| 长时间轴滚动/缩放/稳定分轨 | 数学与轨道单测通过；帧率与手感**未验证** |
| 旧闹钟不在更新后错误触发 | `aNewerRevisionFencesTheOldBroadcast` 等 8 例 |
| 原有数据库不被破坏 | `LegacyMigrationTest` 3 例（文件长度、行数、只读） |
| 备份恢复不复活失效授权/调度 | `restorationDoesNotResurrectStaleClearedAuthorization`、`aRestoredBackupRebuildsItsDesiredSchedule` |
| AI 批次端到端（发布/冲突保留/解锁） | `theAiRunnerReleasesTheLockAndPublishesOnlyItsScope`、`theAiRunnerKeepsAConflictedCardForHumanReview` |

---

## 5. 未验证与已知限制

**必须如实标注为未验证（无 adb / 模拟器 / 真机）：**

1. 实际响铃、厂商后台限制、精确闹钟权限拒绝后的真实表现。
2. 手势手感：双指缩放锚定、长按拖动手感、卡内滑动与文本选择/光标/键盘的争夺。
3. 深色模式运行时切换时输入、焦点、光标与页面状态是否保持。
4. TalkBack 语义、触控目标实测、对比度实测、低端机降级帧率。
5. Compose 在 `uiMode` 变更下不重建 Activity 的实际行为。
6. 进程被杀后恢复（相机、草稿卡、批次）的真机表现。

**功能限制：**

- 重复事件：只支持单一规则（不重复/每天/每周/每月 + 间隔），未提供"此实例/此后/整个系列"交互；
  模型已支持 `exceptions` 与 `overrides`，UI 尚未暴露。**拖动重复事件会被明确拒绝**并提示改用卡片编辑，
  避免单实例拖动被误解为整条系列移动（G5-03 的逐实例默认尚未落地）。
- 每个事件只有一个提醒触发器（`primary`），多触发器与按触发器配置未实现。
- AI 批次只对"已存在且已展示"的卡片执行发布/丢弃，不在批次中再次请求模型。
- AI 上下文共享默认关闭；开启后最多发送 60 条相关事件，未提供逐事件勾选。
- 搜索正式事件后点击结果会创建 Update 草稿；尚未实现独立的搜索结果页。
- 系统日历/ICS 导入为一次性迁移，重复导入按 `(source, externalId)` 幂等；没有模糊匹配与疑似重复的复核队列。
- ICS 导出仅活动事件，闹钟与截止不导出（有损格式，已在 UI 说明）。
- 原生备份只能恢复到空库；没有跨设备/跨 lineage 合并。
- 玻璃视觉为分层半透明模拟，不是真实背景模糊。
- 横屏/小高度设备下卡片编辑区可能被压缩；需要真机确认后再调整布局。

---

## 6. 下一阶段建议

1. 真机验收清单（响铃、权限拒绝、时区切换、重启、深浅色、TalkBack、120Hz 帧率）。
2. 重复事件编辑交互（此实例/此后/整个系列）与例外可视化。
3. 导入的模糊匹配置信度与"疑似重复"复核卡。
4. 冲突快照 diff 的移动端排版优化（当前为逐字段列表）。
5. 批量取消/恢复的进度面板（当前为横幅 + 停止按钮）。
