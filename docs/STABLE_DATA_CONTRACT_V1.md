# PowerCal 0.9 — 稳定数据契约 v1（拟冻结）

**决定日期**：2026-10-20
**状态**：发布前的**规范冻结候选**，不是已完成的代码保证。
**目标**：允许先发布功能不完整的个人稳定试用版，但今后的升级必须能够原地保留已有事件、草稿、重复规则、身份和必要审计信息。
**唯一权威数据源**：当前 Android 应用沙盒 SQLite `powercal-v2.db`；旧版 `powerful-calendar.db` 是只读迁移来源，不参与双写。
**产品约束来源**：`PRODUCT_BASELINE_G1-G11.md`、`PROTOCOL_V0_RESEARCH.md`。
**代码事实**（当前 `PowerStore.kt`）：`DATABASE_NAME = "powercal-v2.db"`，`VERSION = 2`，已有严格 v1 → v2 增量升级，降级拒绝，存在原生 JSON 导出/仅空库恢复；**本文件不会把尚未实现的安全措施写成已通过**。

## 1. 发布策略和兼容承诺

1. **协议先稳定，功能分期开**：0.9 首先保证 Create/Replace/Delete/Convert、事件显示、手工草稿编辑、离线访问、原生完整备份与恢复；尚无完整语义的功能隐藏、只读或明确显示“不支持”。不以 UI 完成度替代数据安全。
2. **SQLite 是唯一实时持久化权威**。Compose 页面、AI 提案、导出 ICS 和 Android AlarmManager 都不能拥有第二套独立事件真相。ICS 不是备份。
3. **SQLite schema version ≠ product versionCode ≠ native backup format version**。分开维护、单独升级、在测试中标明。禁止把数据库升级序号直接映射为 APK 版本号。
4. **本机升级须原地保留用户数据**。禁止 `DROP TABLE`、`onUpgrade` 中删除重建、自动删除旧 DB、重新分配旧 `eventSeq`、覆盖仍存在的目标库来恢复备份。
5. **明确未实现的 migration 必须拒绝启动对应写入路径并提示升级问题**；绝不自动创建空库来掩盖读旧版本失败；默认备份原 DB 再迁移。
6. **签名与包名是数据兼容的一部分**：首次作为个人稳定试用版安装前，固定 `applicationId=dev.powerfulcalendar` 及未来升级共用的正式签名证书；`versionCode` 每次发行单调增长。使用 Debug 签名开始存用户正式数据会造成以后切换 Release 签名无法覆盖升级。签名密钥存储在仓库外、离线备份，不能提交；不要在用户设备上反复换 keystore。
7. **稳定版不依赖皮肤**：PowerCal Glass 默认皮肤足够；Claude 素材替换接口只是 UI 扩展，不得修改 SQLite 结构和 JSON 语义。

## 2. 永久语义与身份不变量（稳定契约）

| 概念 | 长期含义 |
| --- | --- |
| `eventSeq` | 事件的永久身份，第一次新事件草稿创建时分配；删除和丢弃不复用。恢复同一墓碑需要显式批准 |
| `draftId` | 每份草稿独立身份；多个草稿可引用同一个 `eventSeq` |
| `queueOrder` | 每张草稿自己的入队次序，不等于 `eventSeq` 或事件时间 |
| `revision` | 当前正式事件的提交修订版 |
| `baseRevision` | 草稿提出时的正式版本；旧版不能静默覆盖 |
| `commitId` | 唯一、持久的提交幂等键，重复操作不得创建第二次正式提交 |
| `originalSlotKey` | 重复实例原始槽位身份，单次移动后不因有效时间变化而改变 |
| `TemporalKind` | 仅 Alarm（START）、Activity（START+END）、Deadline（END 或仅日期）；时间类型转换保留 eventSeq |
| `TimePrecision / zone intent` | 日期精度不伪造午夜/23:59；保留民用时间、时区和 DST 解释所需的信息 |
| `Trigger / ScheduleIntent / Execution` | 事件语义与安卓调度/触发结果各自独立。权限阻断时不能把“已保存”视为“已响铃” |

`TemporalRecord` 的 JSON payload 为**当前完整快照**，事件类型、时间精度、原始本地时间与 timezone 策略、重复规则、例外/覆盖、来源、业务字段都必须在受控 codec 中编码/解码。数据库索引列（`anchor_ms`、`span_end_ms`、`kind` 等）是查询投影，不应成为无法由正式快照解释的第二套业务真相。

对已存未知 JSON 字段：新增 reader 必须保留或明确迁移，不能通过 decode→encode 的旧版本写操作静默丢弃未来字段。若尚未实现安全的未知字段往返，应在遇到未知重大版本时**拒绝写入**。版本升级前须针对已发行版本的真实备份样本建立兼容性测试。

## 3. 当前 SQLite 表与演化策略

**当前 v2 表**：`meta`, `identities`, `objects`, `drafts`, `commits`, `ai_sessions`, `ai_session_drafts`, `source_links`, `migrations`, `schedule_intents`, `executions`。

这些名称是现在的物理实现记录，不要求永远固定内部列布局。但下面的逻辑实体、版本检查和迁移能力是长期契约。

- 添加新表、列、索引：使用逐版本、确定性、幂等或一次性事务迁移；提供旧 schema 样本升级测试。
- 重命名/拆分：必须迁移并验证每条事件/草稿/提交/重复实例/来源引用后才允许移除旧列；不允许丢旧字段、重置事务历史或复用序号。
- 运行升级步骤前制作安全备份/恢复点；迁移失败留下完整旧数据库及失败证据，不转换为空数据库。
- 恢复或导入必须检验 referential integrity：所有 draft/commit/sourceLink 所属 eventSeq 合法；`next_seq` > 所有曾分配序号；`next_order` > 所有卡片 queueOrder；`eventSeq` 与 `draftId` 分离；重复实例 originalSlotKey 在例外中可追溯。
- 更新提交应在一个事务中完成正式对象、revision、CommitRecord 和调度 desired intent 的修改；Android 实际调度是可重试的异步后效。
- 不保存外部登录密钥为 event/draft JSON；AI 运行中的临时会话不能恢复出合法写权限。

## 4. 原生备份：发布前必须完成的格式契约

**目前代码实际状态**：`exportBackup()` 返回含 `format=powercal-native` / `version=2` / 数据数组的 JSON；`restoreBackup()` 目前只严格检查 `format` 且只接受空库，未对 `version` 做严格拒绝或迁移、未验证文件 hash，亦未保证跨多数据库实例读到同一个一致性快照。这三项是**稳定试用版前的阻断项**，不应假装已完成。

建议从首次稳定发行起固定 **BackupEnvelope v1**：

```json
{
  "format": "powercal-native",
  "backupFormatVersion": 1,
  "databaseSchemaVersion": 2,
  "appVersionCode": 0,
  "exportedAtEpochMs": 0,
  "storeLineageId": "UUID-of-database-lineage",
  "payloadSha256": "hex-digest-of-canonical-payload-bytes",
  "payload": {
    "identities": [],
    "objects": [],
    "drafts": [],
    "commits": [],
    "sourceLinks": [],
    "migrations": [],
    "nextSeq": 1,
    "nextOrder": 1
  }
}
```

上例仅示意**字段结构**，零值不是发行版本、没有可用的 hash；实际 writer 必须填入真实值并自测。把 backup format 从 DB 版本分离以后，即使 SQLite 表发生增量变化，也可独立维持向后导入兼容。

### 4.1 Canonical payload 与哈希边界（0.9 RC 已实现，冻结候选）

为避免平台/序列化顺序导致同一数据产生不同摘要，`payloadSha256` 定义为：

1. 仅对 `payload` 对象计算；包裹字段不参与。
2. 对象键按 Unicode 码点升序排列；数组顺序保持不变（顺序有业务含义）。
3. 字符串按 JSON 转义；布尔与 `null` 使用字面量。
4. 数字先按十进制文本解析为 `BigDecimal`，`stripTrailingZeros()` 后用 `toPlainString()` 输出，
   因此 `1`、`1.0`、`1e0` 的规范化结果均为 `1`。
5. 规范化结果为 UTF-8 字节，SHA-256 后输出 64 位小写十六进制。

`databaseSchemaVersion`、`appVersionCode`、`exportedAtEpochMs`、`storeLineageId` 由 reader 单独
校验格式/范围，但**不被 payload SHA-256 覆盖**：该哈希用于检测损坏/篡改，不是密码学签名或来源认证。
reader/writer 必须复用同一 canonical 实现；任何规范化规则变化都必须提升 `backupFormatVersion`，
不能在同一版本内静默改变摘要语义。

- 导出：在 SQLite **单一一致性读快照**中读取相关实体和 high-water marks，生成结构化 payload，再制作 hash；文件写入返回成功后需重新读取并验证 hash/字段数量，才显示“备份成功”。通过 SAF URI 写入时，注意云存储/中断写入可能无法原子化；失败不得给出成功文案。
- 恢复：**预检版本、hash、JSON 尺寸/结构、身份引用、重复字段与限额**，明确拒绝未知未来格式或提供显式迁移。恢复只能进入空库或通过用户显式“先备份现有库再执行替换”事务化流程；0.9 优先保留空库恢复。
- 恢复必须保留完整事件快照、身份及墓碑、草稿及队列、提交历史、重复例外、来源与迁移必要证据、高水位；不能只恢复 Event 列表。
- 恢复不导入 AI 授权、活动锁、旧调度 PendingIntent、可复用 API 密钥，也不默认把原设备上一切闹钟标记“已在新设备成功调度”。重建调度并展示 permission/blocked 状态。
- 为现有早期 `format=powercal-native,version=2` 的老式文件明确实现/测试兼容读取，或者在正式版发行前禁止把该旧备份格式视为稳定长期格式。此项不能以后通过改 `version` 名义暗中忽略。
- 用户应能手动导出到可离开手机的文件，并从完全新装的 App 恢复。当前 `android:allowBackup=false`，**没有自动系统云备份兜底**；必须明确告知用户需自己保存备份。
- 备份中包含私人日程、备注、地理位置等信息。0.9 如暂不做加密备份，导出界面必须清楚提示该文件为**明文敏感数据**；不得自动上传第三方服务。

## 5. 首次稳定安装与升级链路

**当前 Android 工程实况**：`applicationId=dev.powerfulcalendar`；`versionCode=2`，`versionName=0.2.0`，目前只是 Debug 构建可用；未见稳定 Release 签名配置。这些都是事实，不应将 Debug APK 当作长期升级起点。

**首次稳定版发布前（必须）**：

1. 为个人稳定试用版建立同一把**长期 Release 签名密钥**，仅在受控安全设备保管，至少一份独立离线备份；Keystore 密码不进入仓库、文档或日志。
2. 从头构建 Release 安装包，保留 applicationId；发行版 versionCode 应高于以后需要原地升级的每个已发布版本。
3. 用 **V0 安装 → 真实保存事件/草稿/重复规则/授权状态 → 安装升级版覆盖 V0 → 检验事件/身份/草稿/提交和提醒** 测试矩阵，禁止仅用 Robolectric 新建空库测试代替。
4. 做一次 **备份 → 清空测试设备 App → 新装 → 恢复 → 校验对象数量、内容和序号高水位 → 测试恢复后第一次更新与调度** 的端到端流程。
5. 如果用户已经安装当前 Debug 签名版本，不能承诺 Release 包直接覆盖：必须先在旧 APK 导出可验证的完整备份，再卸载 Debug，装 Release 并恢复。**未验证恢复前不要卸载有价值数据的安装包。**
6. 全部验证通过后再冻结首次稳定版的 schema version / backup format version / app versionCode 组合，并记录 APK SHA-256 与签名证书 fingerprint。

## 6. 0.9 MVP 范围控制

**必须能用且必须稳定**：离线新建 Alarm/Activity/Deadline，正式存储/草稿恢复、查找/浏览/编辑、独立语义的时间轴、手工备份/恢复、升级/签名、提醒 blocked 状态、崩溃后安全重开。已有单 AI 功能可以作为 Beta 启用，但一旦启用必须遵守独占授权/fencing，不能作为可绕过保护的快捷入口。

**可留到后续 0.9.x / 1.0**：完整重复范围编辑、正式操作补偿撤销、来源疑似重复复核、旧闹钟完整引导、流式 AI 进度、更多 Trigger、关联闹钟投影、真正动态液态玻璃、可在线替换皮肤、大型窗口超过 2048 个实例的无限数据分页。

**可延期不代表可误报成功**：不支持的重复实例编辑要显式拒绝；输入尾部强杀风险写入已知问题；过量 recurrence 的 2048 截断必须明显告知用户而非隐形漏显示；导入旧闹钟保持 disabled，直到确切交接。若这些信息无法保证，相关入口应暂时受限。

## 7. 交给 DSH 的最小实现清单（优先级顺序）

**Release P0（阻断开始存主数据）**
- 存储契约编码及 versioned migrations 审查：含 schema/JSON codec 向前兼容测试，以及 v1→v2 真实升级样本验证。
- 可靠 BackupEnvelope v1：一致性快照、严格版本/哈希/引用验证、原生读写并回读核验、早期 backup v2 文件兼容策略。
- 第一次固定签名、稳定 applicationId 和单调 versionCode；原地覆盖安装与全新恢复的设备测试。
- 应用前后台、强杀、存储耗尽、SQLite/WAL 恢复异常测试；至少在用户主动切页/退出、onStop 的保存不丢失；尾部 300ms 仍有风险须显式限制/提示。
- 目前没有真机，因此设备通过之前只能标记 **Release Candidate（RC）**，不能宣称完全稳定。

**Release P1（非阻断，接续开发）**
- 重复范围编辑、旧闹钟 handoff、补偿撤销、AI 逐项进度与更细粒度性能优化。
- 长窗口超过 2048 个实例的分页/溢出明确标识。
- Claude 皮肤及素材替换。

### 最小验收矩阵

| Test | Pass 条件 |
| --- | --- |
| Fresh install | 从 Release APK 新装，创建/编辑/丢弃/恢复同一事件的多卡 |
| In-place upgrade | V0 → V1 同包名同证书，安装不清数据，旧正式对象、草稿、版本、序号不变 |
| Schema migration | 持真实 DB v1→v2，再对未来 v2→v3 虚拟旧库做迁移验证；不丢列、不退回空库 |
| Back up integrity | 备份在完整读取后可验证 SHA，损坏/截断/未知格式明确拒绝，原库不受影响 |
| Fresh restore | 新装空库恢复所有正式事件、草稿、序号高水位、重复例外；AI 锁不恢复 |
| Failure injection | 写入被中断、设备低空间、进程终止时无伪成功及永久锁 |
| Reminder recovery | 恢复后新授权允许再调度，旧版本广播不产生错误响铃 |
| Future compatibility | 旧 v1 BackupEnvelope 在以后新版本仍有解析或受控迁移路径 |
| Signing | 受控证书 ID 与下一版一致；不能误用 Debug 签名覆盖 Release |
| Privacy | 备份未包含 API Key；明文备份风险已明确提示 |

## 8. 交付和变更纪律

本文件定义**现在应冻结的事实与未来保护承诺**，不宣称实现已满足全部标准。DSH 只能按经过验证的增量迁移演化 schema。所有数据影响需有真实设备或 Robolectric SQLite 迁移证据、独立 Reviewer、失败回退方案，不能直接把 `VERSION` 从 2 改成 3 后重建表。

开发应从隔离分支完成，保持 `core/`、UI、Release 配置的文件责任分离。旧版 APK/备份样本和可恢复证据需要归档；密钥存私有安全地点，不附在聊天或提交中。

**冻结条件**：完成 Release P0、给出独立验收证据后，将本文状态更改为 “APPROVED FOR STABLE USER DATA”，附上首次可长期升级的 APK versionCode、DB schema version、backup format version、签名 fingerprint 和样本恢复测试结果。在此之前，只称候选合同与试用 RC。
