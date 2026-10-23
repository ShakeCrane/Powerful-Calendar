# PowerCal 核心基线冻结：任务理解清单
版本：2026-10-23 交接快照；执行前必须按本地仓库重新核对。
根目录：E:\Project\Powerful-Calendar
目标：闭环测试、最小修复、逐项回归与签核，**满足证据标准才宣布核心基线 GO**；否则给出准确 HOLD 及阻塞。本文不是验收完成证明。

## 1. 当前真实现场与不得丢失的工作
- 当前执行更新（覆盖下方历史快照中的旧状态）：最新已推送代码/测试提交为 `05aef89a181426f2a3533ffb87be9c884223bf8d`；Issue 036–045 已分别完成核心存储证据核查、独立结案报告、台账更新并推送。涉及人工提案追加队尾、时间戳与 queueOrder 分离、revision 仅在 commit 后递增、独立 commitId 唯一、显式幂等键跨重启不重复 durable side effects、过期 baseRevision 冲突与草稿保留、显式 reapproval 门控、整对象替换跨重启持久化，以及禁止静默字段合并。最新完整 Gradle 为 407/407，55 suites，Lint 0 errors/55 warnings（406 tracked + 1 受保护未跟踪测试）；候选总体 91 PASS / 0 FAIL / 9 BLOCKED / 103 NOT TESTED / 6 SPEC UNRESOLVED，冻结仍 HOLD，0 个历史原始 ID 映射。Issue 002 仍 PARTIAL / BLOCKED：免费模型结构化阶段最近返回 HTTP 429。按用户最新指示，本轮不做 UI 工作；变浅横向边缘可接受，当前时间标记覆盖中轴按设计保留。
- 未跟踪 `design/`、`app/src/test/java/dev/powerfulcalendar/ui/v2/DraftDeckFeedbackTest.kt`、`docs/issues/PC-OBS-005-commit-feedback.md` 属于受保护的其他资产/工作，未修改、未暂存、未提交。
- Issue 001（ICS）：代码修复已提交；本轮在隔离 `emulator-5556` 通过纽约 DST 周重复系列的 Android SAF 导出、`icalendar 7.3.0` 独立解析与本地时间跨回拨展开核对。完整工作树 Gradle 回归报告 364 项通过，其中 1 项来自受保护未跟踪测试（363 tracked + 1 untracked）；不能将其描述成已提交树的 364 项。设备已恢复 GMT/自动时区，数据库中的两条合成验收事件仍保留。因缺外部日历导入器与实体设备，Issue 仍 PARTIAL / HOLD；证据见 `docs/issues/PC-ISSUE-001-ICS-recurrence.md`、`issue001-saf-dst-emulator5556.ics`、`issue001-dst-independent-parse-emulator5556.json` 和 `issue001-emulator5556-postcheck.json`（均位于 `.local/android-test/reports/freeze-20261023/`）。
- Issue 003（备份）：类型化非空库拒绝与可操作错误说明已提交；API 35 的 SAF 空库恢复、篡改/无效输入拒绝、非空目标拒绝与数据指纹证据均已记录。最新完整工作树 Gradle 回归报告 364 项通过，其中包含上述 1 项受保护未跟踪测试；不能将其描述成已提交树的 364 项。OS 实际撤销 SAF 权限和实体设备验收仍缺证，因此保持 PARTIAL / HOLD。详见 `docs/issues/PC-ISSUE-003-restore-feedback.md`。
- Issue 005（墓碑恢复）：`PowerStore.proposeRestoreDraft` 与 `DeckController.beginRestore` 提供显式恢复提案路径；确认前保持 tombstone，非 user actor 被拒绝，确认后复用 eventSeq 并重建提醒。SQLite 与控制器定点测试 2/2、完整 Gradle 回归 366/366 PASS。核心代码已推送；当前没有应用动作调用 `beginRestore`，也未做设备验收，故产品路径保持 PARTIAL / HOLD。独立报告 `docs/issues/PC-ISSUE-005-retired-event-restoration.md`。
- Issue 006（备份契约证据）：8 个重建候选项已逐项签核，未知格式/版本、悬空引用、nextOrder、墓碑、草稿队列、提交历史及凭据/调度句柄排除均有定点测试。完整工作树 Gradle 372/372 PASS，Lint 0 errors/55 warnings；371 tracked + 1 项受保护未跟踪测试；只在 JVM SQLite/Robolectric 运行，未使用模拟器或真机。生产逻辑与 UI 未修改。独立报告 `docs/issues/PC-ISSUE-006-backup-roundtrip-contract.md`。
- Issue 007（预检后并发写入）：确定性两 store 测试在预检完成后写入草稿，恢复事务内检查拒绝覆盖并保留分配器；1/1 定向测试和 373/373 全量 Gradle 通过。Robolectric 真实线程锁交错未作为签核证据，其中一份中间探索日志被后续最终日志覆盖，详情见报告。没有 Android 设备验收。
- Issue 008（数据库迁移原子性）：在首条重复草稿已被归并并审计后，于第二次更新时注入 SQLite trigger 失败；事务回滚新表、审计和首条状态变化，原 schema v3 的三条 PENDING 草稿保留。移除故障后同库迁移成功。定向迁移套件 4/4、完整 Gradle 374/374，Lint 0 errors/55 warnings；仅改测试，无生产逻辑/UI 改动，也没有设备验收。独立证据见 `docs/issues/PC-ISSUE-008-database-migration-atomicity.md`。
- Issue 009（重复实例身份与例外隔离）：7 个纯时间模型测试通过，覆盖有效日期边界与锚点、有限窗口、单例移动保留原 key、跳过不重排、移动不替换自然实例、例外按 eventSeq 隔离。完整 Gradle 375/375，Lint 0 errors/55 warnings；仅改核心测试，无 UI/生产代码修改和设备验收。DST gap/fold 产品政策仍未签核。独立报告见 `docs/issues/PC-ISSUE-009-recurrence-instance-identity.md`。
- Issue 010（跨午夜重复活动边界）：新增两条核心时间模型回归，证明有效日期按原实例开始日期判定、23:30–次日 01:15 持续时长保持 105 分钟。定向类测试 19/19、完整 Gradle 377/377，Lint 0 errors/55 warnings；没有生产代码/UI或设备改动。`REC-P0-072/073` 已签 PASS，独立报告 `docs/issues/PC-ISSUE-010-cross-midnight-recurrence-boundary.md`。待办列表投影 `REC-P0-064/065` 仍 NOT TESTED。
- Issue 011（日期范围 Memo 完成持久化）：新增隔离 SQLite/Robolectric 回归，日期键完成被拒绝且不递增 revision，`single` 完成状态和起止日期跨关闭/重开保留。定向测试 1/1、完整 Gradle 378/378，Lint 0 errors/55 warnings；仅测试变更，无生产/UI/设备操作。`REC-P0-009` 已签 PASS，独立报告 `docs/issues/PC-ISSUE-011-spanning-memo-completion.md`。
- Issue 012（旧记录未跟踪状态）：在 on-disk v1 升级测试中确认没有 `tracking` 字段的历史 Activity 仍为未跟踪、无完成键，完成写入被拒绝且 revision 不变。定向测试 1/1，完整 Gradle 378/378，Lint 0 errors/55 warnings；仅改测试，无生产/UI/设备操作。`REC-P0-010` 已签 PASS，独立报告 `docs/issues/PC-ISSUE-012-legacy-untracked-record-contract.md`。
- Issue 013（提醒与完成跟踪独立）：新增 SQLite/Robolectric 回归，分别对跟踪开/关的 Activity 只改提醒偏移并提交，证明跟踪状态不变且完成键不增。定向测试 1/1、完整 Gradle 379/379，Lint 0 errors/55 warnings；仅测试变更，无生产/UI/设备通知测试。`REC-P0-011` 已签 PASS，独立报告 `docs/issues/PC-ISSUE-013-reminder-tracking-separation.md`。
- Issue 014（完成跟踪与事件启用独立）：新增 SQLite/Robolectric 回归，对启用和禁用的 Activity 分别切换完成跟踪，证明 `enabled` 与提醒偏移均保持不变。定向测试 1/1、完整 Gradle 380/380，Lint 0 errors/55 warnings；仅测试变更，无生产/UI/设备操作。`REC-P0-014` 已签 PASS，独立报告 `docs/issues/PC-ISSUE-014-tracking-enabled-separation.md`。
- Issue 015（提醒触发与完成状态隔离）：新增两个 Robolectric API 34 测试，分别证明 delivery handler 的合成 `ACTION_FIRE` 只记录 `FIRED` 而不完成事件，以及通知取消没有完成回调。Issue020 后定向 2/2、完整 Gradle 384/384，Lint 0 errors/55 warnings；仅测试路径调整，无生产/UI/用户数据改动。`REC-P0-012/013` 签候选 PASS；Android 广播生命周期、AlarmManager 送达、通知栏手势及真机/OEM 仍未测试。独立报告 `docs/issues/PC-ISSUE-015-reminder-completion-isolation.md`。
- Issue 016（Alarm 单点时间语义）：在 `TemporalProtocolTest` 新增纯 JVM 模型回归，使用带残留结束文本的表单确认 Alarm 仍仅保存开始时刻，`endMillis` 为空。提交后定向 35/35、完整 Gradle 383/383，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/设备或业务数据改动。`REC-P0-001` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-016-alarm-point-semantics.md`。
- Issue 017（Activity 与提醒 Trigger 身份分离）：加强 SQLite/Robolectric 测试，确认 Activity 选择精确 Alarm 提醒后记录仍为 `ACTIVITY`，Trigger 保留同一 `eventSeq` 且不生成第二事件。提交后定向 2/2、完整 Gradle 383/383，Lint 0 errors/55 warnings；仅补充测试断言，无生产/UI/设备或业务数据改动。`REC-P0-015` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-017-trigger-event-kind-separation.md`。
- Issue 018（即时 Deadline 单点语义）：在 `TemporalProtocolTest` 新增纯 JVM 模型回归，使用含残留开始文本的即时 Deadline 表单确认只保留截止 instant，`startMillis` 与 `endMillis` 均为空。提交后定向 36/36、完整 Gradle 384/384，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/设备或业务数据改动。`REC-P0-003` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-018-instant-deadline-point-semantics.md`。
- Issue 019（日期型 Deadline 精度）：Robolectric API 34 的 SQLite/PowerStore 测试确认提交 `2026-10-24` 后仍为 DATE 精度、`deadlineMillis=null`、类型为 DEADLINE；定向 1/1 PASS，并在 Issue020 修复后的全量 384/384 中通过。`REC-P0-004` 签候选 PASS；无生产、测试或界面代码修改。独立报告 `docs/issues/PC-ISSUE-019-date-only-deadline-precision.md`。
- Issue 020（提醒回归夹具并发锁）：Issue019 的全量回归发现 Issue015 测试并发轮询时触发 SQLite 锁冲突；最小修复将 delivery handler 暴露为模块内函数，由测试同步调用，`onReceive` 的 `goAsync` 与后台线程行为未改。修复后定向 2/2、完整 Gradle 384/384，Lint 0 errors/55 warnings。原失败日志/XML与哈希、临时探索未留存的说明及通过证据见 `docs/issues/PC-ISSUE-020-reminder-receiver-test-race.md`。Android 广播框架生命周期、AlarmManager 实际送达仍未验证；不新增候选 PASS。
- Issue 021（不完整 Activity 草稿恢复）：新增 PowerStore/Robolectric SQLite 回归，证明不完整 Activity 拒绝提交且没有正式对象，INVALID 草稿在数据库重开后保留；补充结束时刻后可转回 PENDING 并成功提交。定向提交后 1/1、完整 Gradle 385/385，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-005` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-021-incomplete-activity-draft-recovery.md`。
- Issue 022（一日 Memo 本地日期）：新增 PowerStore/Robolectric SQLite 回归，证明单日 `memoStartDate` 跨数据库重开保留，且不生成结束日期、正式事件时间或 Deadline 字段。提交后定向 1/1、完整 Gradle 386/386，Lint 0 errors/55 warnings；仅增加测试代码，无生产/UI/业务数据改动。`REC-P0-007` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-022-one-date-memo-local-date.md`。
- Issue 023（日期范围 Memo 边界）：补齐 `spanningMemoHasOneCompletionInstanceAndKeepsItAcrossRestart` 的断言，验证类型仍为 MEMO、两个本地日期边界跨 SQLite 重开保留，正式事件时间和 Deadline 字段为空。提交后定向 1/1、完整 Gradle 386/386，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-008` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-023-date-span-memo-boundaries.md`。
- Issue 024（Activity 起止区间持久化）：新增 PowerStore/Robolectric SQLite 回归，证明提交及数据库重开后 Activity 类型、精确 `startMillis`/`endMillis` 与真实结束锚点保持，且没有 Deadline 字段。提交后定向 1/1、完整 Gradle 387/387，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-002` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-024-activity-interval-persistence.md`。
- Issue 025（类型转换保留永久事件身份）：新增 PowerStore/Robolectric SQLite 回归，证明 ALARM 转 ACTIVITY 时要求明确结束时刻，沿用同一个 `eventSeq`，revision 只递增一次，身份计数及分配器在重启后不变。提交后定向 1/1、完整 Gradle 388/388，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-020` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-025-kind-conversion-keeps-event-identity.md`。
- Issue 026（导入来源元数据与事件身份分离）：新增 PowerStore/Robolectric SQLite 回归，证明不同 `(source, externalId)` 对分配独立 `eventSeq`，相同组合重复导入幂等，来源元数据和内部身份跨数据库重开保留。提交后定向 1/1、完整 Gradle 389/389，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-016` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-026-import-source-metadata-identity.md`。
- Issue 027（拒绝导入不消耗事件序号）：新增 PowerStore/Robolectric SQLite 回归，证明无效 Activity 导入不分配身份；修复后用同一 source/externalId 重试得到 eventSeq=1，数据库重开后记录、分配器水位与历史不变。提交后定向 1/1、完整 Gradle 390/390，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-028` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-027-rejected-import-does-not-consume-sequence.md`。
- Issue 028（无日期 Memo 不生成虚假时间）：新增 PowerStore/Robolectric SQLite 回归，证明保存及数据库重开后 memoStartDate、memoEndDate、startMillis、endMillis、Deadline 字段、anchorMillis 与 spanEndMillis 均为空；纯模型合同测试同时确认无假时间。提交后定向 2/2、完整 Gradle 391/391，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-006` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-028-undated-memo-no-synthetic-time.md`。
- Issue 029（删除身份跨重启退休与不复用）：新增 PowerStore/Robolectric SQLite 回归，证明 DELETE tombstone、retired 状态和序号水位跨数据库重开保留；删除后创建的新草稿取得下一序号，旧身份持续 retired。提交后定向 2/2、完整 Gradle 392/392，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-029/030` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-029-retired-identity-not-reused-after-restart.md`。
- Issue 030（独立草稿身份）：新增 PowerStore/Robolectric SQLite 回归，证明两个不同事件的 create 草稿拥有唯一 draftId 和各自 eventSeq 映射，数据库重开后映射及待处理队列保持。提交后定向 2/2、完整 Gradle 393/393，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-031` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-030-independent-draft-identities.md`。
- Issue 031（暂存编辑保留草稿身份）：新增 PowerStore/Robolectric SQLite 回归，证明暂存内容编辑保留原 draftId 和目标 eventSeq，更新版本而不分配新事件身份，SQLite 重开后草稿与待处理队列仍在。提交后定向 2/2、完整 Gradle 394/394，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-032` 签候选 PASS，重建候选文字已按 V4 编辑语义校准但仍 UNMAPPED，独立报告 `docs/issues/PC-ISSUE-031-staged-draft-keeps-event-identity.md`。
- Issue 032（同事件草稿单槽跨重启）：按 CORE_EDITING_V4 修正了重建候选 `REC-P0-033` 的冲突表述；新增 PowerStore/Robolectric SQLite 回归，证明连续提案复用一个 draftId 与 queueOrder，重开后只有一个可操作草稿，旧草稿撤销不能制造第二个活动槽。提交后定向 2/2、完整 Gradle 395/395，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。候选仍 UNMAPPED，独立报告 `docs/issues/PC-ISSUE-032-one-actionable-draft-per-event.md`。
- Issue 033（每个事件一个当前正式快照）：新增 PowerStore/Robolectric SQLite 回归，证明同一事件连续三次整对象提交后，正式查询仅返回 revision 3 最新快照，重开后仍唯一，三条历史提交仍保留。提交后定向 2/2、完整 Gradle 396/396，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-034` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-033-single-current-formal-snapshot.md`。
- Issue 034（草稿 queueOrder 独立于 eventSeq）：新增 PowerStore/Robolectric SQLite 回归，以逆序入队证明 queueOrder 独立分配、唯一并在重开后保留队列映射和顺序。提交后定向 2/2、完整 Gradle 397/397，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-035` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-034-independent-queue-order.md`。
- Issue 035（草稿队列水位单调递增）：新增 PowerStore/Robolectric SQLite 回归，提交和丢弃草稿后重开数据库，新草稿 queueOrder 仍严格大于所有旧位置。提交后定向 2/2、完整 Gradle 398/398，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-036` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-035-monotonic-queue-watermark.md`。
- Issue 036（人工提案追加队尾）：新增 PowerStore/Robolectric SQLite 回归，确认新人工提案 queueOrder 晚于已有草稿，关闭并重开数据库后 ID/顺序/标题保持。提交后定向 2/2、完整 Gradle 399/399，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-038` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-036-human-proposal-appends-queue.md`。
- Issue 037（时间戳不改变队列顺序）：新增 PowerStore/Robolectric SQLite 回归，让较早队列项的 updatedAtMillis 晚于后项，仍按 queueOrder 返回且跨重启不变。提交后定向 2/2、完整 Gradle 400/400，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-042` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-037-timestamp-does-not-reorder-queue.md`。
- Issue 038（正式修订号仅在提交时递增）：新增 PowerStore/Robolectric SQLite 回归，创建和编辑待处理替换提案后正式对象 revision 仍为 1，跨重启保持；实际提交后才成为 revision 2。提交后定向 2/2、完整 Gradle 401/401，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-044` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-038-revision-advances-only-on-commit.md`。
- Issue 039（独立提交的 commitId 唯一性）：新增 PowerStore/Robolectric SQLite 回归，两次独立提交 ID 非空且不同，历史跨重启仍包含两条唯一 ID；同操作重试仍返回原 ID。提交后定向 2/2、完整 Gradle 402/402，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动。`REC-P0-045` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-039-unique-commit-id.md`。
- Issue 040（显式 commit 重试幂等性）：新增 PowerStore/Robolectric SQLite 回归，同一 idempotencyKey 跨重启重试返回原 commitId/revision；提交历史、发布计数、提醒意图均未增加。提交后定向 2/2、完整 Gradle 403/403，Lint 0 errors/55 warnings；仅测试代码，无生产/UI/业务数据改动，未覆盖系统级闹钟送达。`REC-P0-046` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-040-idempotent-commit-retry.md`。
- Issue 041（过期 baseRevision 冲突）：新增 PowerStore/Robolectric SQLite 回归，形式 revision 升至 2 并重开数据库后，旧提案提交返回 Conflict，正式标题/完成状态/revision/历史保持；仅测试代码，无生产/UI/业务数据改动，编辑器提示不在范围。提交后定向 2/2、完整 Gradle 404/404，Lint 0 errors/55 warnings。`REC-P0-047` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-041-stale-revision-conflict.md`。
- Issue 042（AI 冲突草稿重启后保留）：新增 PowerStore/Robolectric SQLite 回归，AI 批次保留 Conflict 草稿；再次重开后 ID、完整提案、baseRevision、queueOrder 与可操作状态均保留，正式标题和完成状态不变，AI 锁已释放。仅测试代码，无生产/UI/业务数据改动。提交后定向 1/1、完整 Gradle 405/405，Lint 0 errors/55 warnings。`REC-P0-048` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-042-conflict-draft-retention.md`。
- Issue 043（过期提案显式重审）：新增 PowerStore/Robolectric SQLite 回归，重开后默认提交仍 Conflict 并保留新正式状态；只有 `approveStale=true` 显式获批时，整份提案才提交为 revision 3。仅测试代码，无生产/UI/业务数据改动。提交后定向 1/1、完整 Gradle 406/406，Lint 0 errors/55 warnings。`REC-P0-050` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-043-explicit-stale-reapproval.md`。
- Issue 044（整对象替换持久化）：新增 PowerStore/Robolectric SQLite 回归，重启后正式快照与替换提案一致，提案清空的地点/备注未与旧值合并，eventSeq/createdAt 保持且 revision 升至 2。仅测试代码，无生产/UI/业务数据改动。提交后定向 1/1、完整 Gradle 407/407，Lint 0 errors/55 warnings。`REC-P0-051` 签候选 PASS，独立报告 `docs/issues/PC-ISSUE-044-whole-snapshot-replacement.md`。
- Issue 045（阻止静默字段合并）：复用 Issue 043 的 `staleDraftRequiresExplicitReapprovalAfterRestart` 精确断言，未批准时保留新版字段、获批时整对象替换。未新增测试代码；独立报告明确标注共享证据。`REC-P0-052` 签候选 PASS，完整回归 407/407。报告 `docs/issues/PC-ISSUE-045-no-silent-stale-field-merge.md`。
- PC-QA-GATE-001：历史只确认总数 209、P0 126、P1 82、未分类 1；只读检查未找到原始逐案 ID。已建立 208 条 RECONSTRUCTED 候选行和 1 条未解决历史槽位；备份、墓碑、迁移、重复与核心数据语义批次共 66 条候选 PASS，状态为 PASS 91 / BLOCKED 9 / NOT TESTED 103 / SPEC UNRESOLVED 6；候选 P0/P1 分配仍待审核，0 个原始 ID 有映射。候选状态、审计和结构验证见 `docs/CORE_FREEZE_ACCEPTANCE_LEDGER.csv` 与 `docs/issues/PC-QA-GATE-001-209-case-ledger.md`。最新完整工作树回归 407 项通过（406 个已跟踪测试 + 1 个受保护未跟踪测试），代码/测试基线 `05aef89a181426f2a3533ffb87be9c884223bf8d`；定点回归与全量回归均不能映射为缺失的 209 个历史 ID。
- .local/android-test 是仓库内测试资产及模拟器工具位置；保留既有截图、日志、失败样本、只读数据库快照、AVD。
- 原测试环境：Android 15 API 35，PowerCal_API35，emulator-5554；原生测试场景与模拟器数据可能包含历史资料，不得随意清库。破坏性恢复用独立可丢弃实例。
- CommandCode 地址：https://api.commandcode.ai/provider/v1；模拟器已有密钥（仅检查存在，不读/输出明文）；原模型 stealth/glyph-cluster:free。
- 历史阶段中 JVM 343/343（含 Issue 002 新增 7 项）、Skin Studio 37/37、Debug 构建、Lint 通过；**再次检查当前基线**，不可直接转化为 209 项验收通过。
- 总设计：核心产品合同 docs/CORE_CONTRACT_FREEZE_2026-10-21.md；当前冻结风险 docs/FREEZE_ISSUE_REPORT_2026-10-23.md；模拟器回归 docs/ANDROID_EMULATOR_*QA_2026-10-23.md；Issue 独立报告 docs/issues/。

## 2. 从高到低的待办清单

### P0-A 接管与可追溯验收表
- [ ] 只读审计 Git 工作树/未提交改动/源代码与文档/运行测试进程/模拟器与现有数据库状态/API 配置状态。
- [ ] 记录基线 SHA、Debug APK SHA256、Android 版本与 serial、测试时区、测试脚本/证据路径。
- [x] P0-A 原始清单只读审计完成：在当前文件/可达 Git 历史中未找到 209 个原始 case ID；审计哈希及来源盘点见 `docs/issues/PC-QA-GATE-001-209-case-ledger.md`。
- [x] 建立 208 条明确标记 RECONSTRUCTED/UNMAPPED 的候选行和 1 条未解决历史槽位；不得将 364 JVM 测试当 209 项，也不得臆造历史签核。
- [ ] 执行候选行、审核建议优先级、追索历史 209 项原表；每次更新均注明规格来源、前置条件、环境、步骤、预期/实际、状态、证据、Git/APK 基线与复测日期。
- [ ] 建立当前 PASS / FAIL / BLOCKED / NOT TESTED / PREVIOUS PASS 区分、原始结果索引与统一缺陷登记。

### P0-B PC-ISSUE-002 AI 超时与结构化提案失败（当前 OPEN / PARTIAL / BLOCKED）
- [x] 已审查并接续原 AI 改动；代码、策略与安全测试已提交。当前没有未提交 AI diff，不得从旧快照误判为仍有 WIP。
- [ ] 复现两阶段调用：此前 free 模型首次 HTTP 200 空内容，盲重试后 45 秒等待超时，总约 62 秒；调整预算后的对照中两个 HTTP 200 分别约 14.8/5.7 秒，但没有成功生成审查卡。
- [ ] 核验 AiCompletionPolicy：免费模型第一次较充足输出预算、不对空内容盲目重复计费调用；出错显示真实分类，仍无未经授权写入。
- [ ] 核验 AI 标记只作为“有提案意图”提示；Skill 实际读取**本轮原始用户输入**，不得采信 AI 编造的 source、历史聊天、文件提示注入、系统示例文本。
- [ ] 真实 Android UI 完成：自然语言 → 模型 → 意图提示 → 结构化提案 → 可审查卡 → 同意/拒绝/写草稿/发布 → 数据库与 UI → 重启后的持久化；加入重复点击幂等、冲突、批量授权、错误撤销。
- [ ] 文件/OCR/ICS 输入以不可信内容处理；模型不拥有直接发布/删除权。
- [ ] 用时间戳/HTTP 状态/结果类型诊断，**不记录密钥、headers、提示正文、私人日历数据**。
- [ ] 免费模型优先。付费只能 deepseek/deepseek-v4.1-flash，有限单次必要对照；禁止其它付费模型、无限循环或自动增加成本。
- [ ] 端到端真正通过再关闭 Issue；最新免费调用结构化阶段 HTTP 429，需在服务可用时继续受控复测。报告中保留 PARTIAL / BLOCKED，不造 PASS。

### P0-C 已修 Issue 的实际 UI 与互操作补验
- [ ] PC-ISSUE-001：代码单测、此前固定时区 SAF 样本、本轮纽约 DST 周系列的真实 SAF 导出、独立解析和序列化回环已有证据；单测覆盖 DAILY/WEEKLY/MONTHLY、INTERVAL/COUNT/UNTIL、BYDAY/EXDATE 与 fail-closed。仍需兼容外部日历导入往返及实体设备验收；复杂 override/active range 不得静默导出单次事件。详细结果见独立 Issue 报告。
- [ ] PC-ISSUE-003：SAF 非空数据库恢复应**拒绝且说明原因**；检查 JSON 错误、SHA256 篡改、权限问题、旧格式明确授权、合法空库恢复、回读校验、原数据/提醒状态不改变或按合同重建。禁止对现存 emulator-5554 清库。
- [ ] Issue 001 的代码与当前可用模拟器验收已有证据，外部导入/真机仍待签核；Issue 003 的设备验收也未签核。对照独立 issue 文档，发现残留缺陷就继续修复、回归、更新报告。

### P1-D PC-ISSUE-004 横屏/小屏/时间轴
- [x] 默认 Activity 固定竖屏且不基于传感器旋转。
- [x] API 35 隔离设备在关闭自动旋转并请求 ROTATION_90 时，应用仍保持 ROTATION_0 与 1080×2400；原旋转设置已恢复。未主动使用陀螺仪/方向传感器。
- [x] 产品方向策略：始终使用竖屏；系统旋转请求或传感器方向变化不触发应用横屏。横屏仅作为平台/窗口管理器强制覆盖时的兼容性场景进行验证。
- [x] 移除时间轴背景的纵向色带来源；24 个画布像素样本一致。蓝色 NOW 线与午夜虚线保留；独立报告记录前次修复为何不彻底。
- [x] 横向布局兼容截图已在 API 35 隔离设备采集；常规产品仍固定竖屏。若未来平台/窗口管理器无视方向锁，仍需在该类真实覆盖环境检查布局。
- [ ] 横屏、小屏、字体与显示缩放、输入法、TalkBack 触摸命中/遮挡/标签重叠真实复现，再做最小布局调整。
- [x] 用户复核确认：蓝色当前时间标记可覆盖中央轴；其淡化边缘可以保留，不再作为 UI 缺陷追修。继续保留中央轴、时间数字/短刻度、蓝色标记与午夜分隔线。本轮不做 UI 修改。
- [ ] 保留无限重叠事件独立框及左右水平滚动，不恢复三栏上限/×N 聚合；仅保留纵向缩放，不能恢复双指水平缩放。

### P1-E PC-ISSUE-GREEDY-LABEL-FOLDING 标题视觉冲突（独立时间轴验收项）
- [x] 折叠对象是实际渲染的单行标题矩形；事件框、数量、时间几何和泳道分配不随标题显隐改变。短框标题锚定真实开始位置；框内/框外模式使用相同的碰撞策略。
- [x] 标题候选按屏幕 Y 与稳定事件 ID 排序；每个可视区域只同当前可见 lead 比较，隐藏候选不更新 lead；边缘相切视为不冲突。
- [x] API 35 隔离模拟器前后截图、实测标题矩形与 A/B/C/D 碰撞轨迹已记录于 `docs/issues/PC-ISSUE-GREEDY-LABEL-FOLDING.md`；8 个纯策略单测、5 个 Compose 测试及 Gradle 全量回归通过。
- [ ] 真机/OEM 与 TalkBack 手工检查仍属设备级冻结门槛；本项代码与 API 35 回归单独签核 PASS，不据此宣告核心冻结 GO。

### P2-E 待验证 Issue 与技术债
- [ ] PC-OBS-005：追查“发布后 UI 立即看不到”的刷新时序，拿事务写入与界面渲染证据，复现才修。无证据标 NOT REPRODUCED。
- [ ] PC-TECH-006：最新完整回归 55 Warning/0 Error；按实际安全/兼容影响排序评估，不为零警告而大改代码。日志见 `.local/android-test/reports/freeze-20261023/issue001-dst-full-gradle-20261023.log`。
- [ ] 过程监控：冷启动/崩溃/内存压力、12 次进程重启、AI 会话与草稿恢复、系统重启的可靠性。

### P0-F 四类对象与核心业务全面回归
- [ ] Alarm：时间点、提醒方法、重复、启用与完成分离。
- [ ] Activity：起止、跨日、重叠水平排布、修改删除、重复。
- [ ] Deadline：仅日期或精确时间不被擅补为 23:59、完成与通知语义。
- [ ] Memo：备忘与待办、日期/无日期、完成撤销、剩余数量红点。
- [ ] Draft 与 AI：必填阻断、提案卡、批量处理、授权/拒绝、草稿状态机、幂等、防越权/冲突、异常恢复；聊天记录持久化与可清除。
- [ ] 时间轴/日历/设置/各页面交互、顶部顺序“今日→memo→待办→新建”、双击刻度尺回 24h。
- [ ] Schema 迁移、ICS、原生备份、异常回滚、导入去重、时区/DST、每周/月重复/单次例外与移动。
- [ ] 提醒：AlarmManager 注册仅为前置；取证实际触发 FIRED、通知实际送达与操作、权限拒绝/允许、Doze、后台、重启、时区切换、厂商系统限制。
- [ ] Android API 和 OEM 真机、不同显示/字体/无障碍；设备不可得则标 DEVICE BLOCKED，不把模拟器 PASS 当真机 PASS。

## 3. 推荐自动化/证据格式
- Gradle：.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --console=plain
- 彻底重跑：.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --console=plain
- Python：python -m unittest discover -s tools/skin-studio/tests -v
- Android ADB：.\.local\android-test\sdk\platform-tools\adb.exe -s emulator-5554 ...
- 日志与脚本放 .local/android-test/reports/<batch>/，Git 中的每个 Issue 单独写 docs/issues/PC-ISSUE-xxx-*.md。
- 单项必需字段：ID / 需求来源 / 优先级 / 前置状态 / 步骤 / 预期 / 实际 / 环境 / PASS|FAIL|BLOCKED|NOT TESTED|PREVIOUS PASS / 证据 / Git SHA / APK SHA256 / Issue / 复测时间。
- 每解决一个 Issue **立即输出一份用户能看到的报告**并写入仓库：根因、最小变更、验证、受影响模块、残留问题、提交/推送结果。不用等待用户每步批准再继续，遇不可逆操作或重大决策才停。

## 4. 核心冻结 GO / HOLD 判定
GO 必须同时满足：① 209 项可追溯并完成相应审核，P0=126/P1=82/其余 1 项定级；② 所有 P0/P1 对应真实环境的必需测试 PASS（包括真机/不同 API 若合同要求）；③ 严重 Issue 全部修复且有修复后回归证据；④ 完整 JVM/静态/Lint/构建/设备测试、备份、权限、提醒、AI 安全闭环；⑤ 唯一 Git 基线、APK SHA256、证据索引、未决风险完整；⑥ 未引入数据损毁、密钥泄露和未授权动作。
不满足任一条件：**HOLD**，列阻塞与必要的人类输入。可另外声明“模拟器子集通过”，但不能偷换为核心正式 GO。

## 5. 非本轮范围与安全禁令
- 独立的 **36 项 .pcskin 完全分离/原生 UI cutover** 应在核心冻结后开始；当前仅保证 skin boundary/安全回退不回归。
- 不清除用户数据或模拟器既有测试资产，不覆盖 design/、Issue002 WIP；不 git reset --hard / git clean / force push / 改写历史。
- 不把 API 密钥、私人备份、请求体、完整聊天、私人日志提交 Git；只检查密钥是否存在，不读明文；任何对照都最小成本。
- 不无凭据/无设备宣称功能通过；不为了凑冻结结果删除受保护功能、跳过测试、修改既定规格。
- 工程原则：先调查/证据/根因，再最小修改、单测/设备复验、提交；长期可靠性、重启后恢复、幂等、服务生命周期简单，优先于一次勉强跑通。

## 6. 最终交付
1. 209 项签核台账与证据索引；
2. 各 Issue 的逐项结案报告及原子提交；
3. 全量测试的日志/截图/录像/数据库验证材料；
4. docs/CORE_FREEZE_VERDICT.md（GO/HOLD、完成数量、阻塞风险、基线 SHA 与 APK SHA256）；
5. 真正达成 GO 才执行核心版本冻结；之后另起 36 项 skin cutover 验收。
