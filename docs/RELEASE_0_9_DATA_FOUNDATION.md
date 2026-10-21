# 0.9 数据基础：P0 审计与 RC 交付

> 合并于 2026-10-21。以下保存原始阶段报告正文；历史时间点、测试数量、完成状态仅对其记录时的基线有效，不代表当前 HEAD 已重新验证。

## 第一部分：修复前审计（历史证据）

# PowerCal 0.9 数据基础 P0 · 只读审计与复现

**审计基线**：`release/0.9-data-foundation` @ `aac52a2`，工作区初始干净。
**隔离目标**：`release/0.9-data-foundation`（当前分支未切换，后续仍需确认没有外部 UI 修改）。
**依据**：`docs/STABLE_DATA_CONTRACT_V1.md`。
**约束**：仅处理 schema/备份/签名版本机制；不改 Glass UI 布局/皮肤/动画；不提交签名密钥；不发布 APK。

---

## 1. 基线事实

| 项 | 基线状态 |
| --- | --- |
| Branch / HEAD | `release/0.9-data-foundation` @ `aac52a2`，父提交包含 UI 与 DSH 接口阶段 |
| 工作区 | 干净 |
| Package | `applicationId=dev.powerfulcalendar` |
| 版本 | `versionCode=2`、`versionName=0.2.0`，硬编码在 `app/build.gradle.kts` |
| SQLite | `powercal-v2.db`，`PowerStore.VERSION=2`；`onUpgrade` 只接受 v1→v2，增量添加 `ai_session_drafts` |
| 旧库 | `powerful-calendar.db` 只读迁移；原库不参与双写 |
| 原生备份 | 旧式 flat JSON `{format,version,exportedMs,nextSeq,...}`；空库恢复 |
| Release 签名 | `signingReport`：debug 配置存在，release `Config/Store/Alias = null`；仓库内未发现 keystore/signing properties |
| 真机/设备 | 本轮未连接设备；不宣称升级安装、恢复或签名覆盖已真机验收 |

### 旧 schema 来源

v1 DDL 对照仓库历史提交 `46bf087`（PowerStore v1）与本分支 v1→v2 增量：缺少的唯一 DSH-08 表为 `ai_session_drafts`。本轮新增一份真实 SQLite v1 fixture（不是只调用 `onCreate` 的空库测试），含正式对象、墓碑、提交、草稿、来源链接、迁移记录、调度、执行记录和高水位。

---

## 2. 基线复现

### 2.1 SQLite v1→v2

`PowerStoreSchemaUpgradeTest.upgradingRealV1DataInPlacePreservesRowsAndAddsOnlyTheReviewedTable` 对历史 v1 表结构和数据设置 `user_version=1` 后，用当前 `PowerStore` 原地打开。**基线通过**：现有 v1→v2 迁移只增加授权版本表，正式对象、墓碑、草稿 ID / queueOrder / draftVersion、提交、sourceLink、MigrationRecord、调度执行与 next_seq 均保留。

剩余缺口：没有跨连续升级到稳定目标 schema 的验证；尚无从实际用户设备复制出的匿名化 DB 样本。

### 2.2 备份 Envelope / 哈希 / 严格版本

临时隔离探针 `BackupRedProbeTest` 在独立 Robolectric 数据库中复现，**3/3 预期断言失败**：

1. `exportBackup()` 缺少 `backupFormatVersion`、`payload`、`payloadSha256`；
2. 修改备份中的正式对象标题但不改文件，`restoreBackup()` 仍接受并恢复篡改数据；
3. 把旧 flat JSON 的 `version` 改成 999，恢复仍接受。

探针随后删除，不保留在稳定测试集中。另从 `exportBackup()` 逐表查询代码确认：方法只有 `@Synchronized`，没有在所有查询外建立一个 SQLite 读事务；`@Synchronized` 仅保护当前 `PowerStore` 实例，不能阻止另一个 helper/receiver 连接同时写。

### 2.3 恢复结构与失败边界

基线 `restoreBackup()` 以空库为前置条件并在一个 SQLite transaction 内插入，因此中途 SQL 异常会回滚；但在写入前没有完整校验 Envelope/hash/引用完整性。对象与 identity、draft/commit/sourceLink 之间的所有引用没有统一预检；JSON 解析前 UI 也没有文件字节上限。

### 2.4 签名与 versionCode

`.\gradlew.bat :app:signingReport` 显示：
- debug keystore：`C:\Users\Shake\.android\debug.keystore`，SHA-256 `E5:37:A1:A4:E4:F2:67:B4:95:FA:DE:A0:52:8E:0F:C5:52:D2:6D:5C:87:7A:9B:47:18:2F:29:B0:51:29:A7:A6`（**仅 Debug，不是稳定签名**）；
- release `Config/Store/Alias` 均为空；
- 工程硬编码 `versionCode=2` / `versionName=0.2.0`；
- 仓库内无 `.jks/.keystore/.p12/.pfx`，无 Release 签名配置。

没有稳定发布证书。根据用户约束，本 Agent **不会生成、读取、复制或提交正式 keystore/密码**。签名配置可以实现为读取仓库外环境/私有配置；到生成或使用用户需长期保管的正式材料时必须暂停并请求用户自行操作。

---

## 3. 最小修复方案（仅 P0）

1. **Schema**：在现有 v2 上做确定性增量升级（如 lineage 元数据），对 v1→目标 schema 使用真实 fixture；验证 rows、高水位、墓碑、外键式引用及 PRAGMA user_version 不重置。
2. **BackupEnvelope v1**：独立 `backupFormatVersion=1`、`databaseSchemaVersion`、`appVersionCode`、`exportedAtEpochMs`、持久 `storeLineageId`、规范化 payload SHA-256；导出跨表单一读快照。
3. **恢复**：hash/版本/尺寸/结构/身份引用/高水位预检；兼容旧 flat backup v2 时必须明确标为无 hash、不可信并获得用户确认；默认只恢复空库、transaction 原子写入、恢复后重建调度但不恢复授权/句柄。损坏/未知版本不得写入。
4. **签名机制**：稳定 version properties 与单调 versionCode 规则；Release signingConfig 仅读仓库外材料，缺失时 Release 构建明确失败。Debug key 不得用于稳定数据安装。
5. **边界**：不得覆盖非空用户库，不得自动卸载或清除数据，不得用 Debug→Release 签名切换假装覆盖安装成功。

> 正式 Release APK / Release 证书指纹被用户保管的长期 keystore 阻塞；达到该步骤时停止，不生成临时正式密钥，也不把密钥放入聊天或仓库。

---

## 4. 基线构建

本机 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 成功（基线测试 171 项；实际计数以最终复跑为准）。该结果仅是软件基线，不代表真实设备升级或备份恢复验收。


---

## 第二部分：修复后交付（历史证据）

# PowerCal 0.9 · Release Candidate 数据基础报告

**分支**：`release/0.9-data-foundation`
**基线**：`aac52a2`（0.9 稳定数据契约文档提交）
**状态**：**Release Candidate（RC）**，不是稳定版验收。
**依据**：[稳定数据契约 v1](STABLE_DATA_CONTRACT_V1.md)、[P0 审计](RELEASE_0_9_DATA_FOUNDATION.md)、[版本与签名机制](RELEASE_VERSIONING.md)。

> 本轮只处理三个阻断项：schema 原地升级、原生 BackupEnvelope v1、签名/versionCode 机制。
> 未改 Glass 视觉、页面布局或皮肤；UI 变更仅限恢复入口的校验/确认提示与备份文案。

---

## 1. 交付结论

| P0 项 | 状态 | 证据 |
| --- | --- | --- |
| SQLite v1/v2 → v3 原地升级 | 完成（软件验证） | 真实 v1、v2、半迁移旧库 fixture；见 §3 |
| 原生 BackupEnvelope v1 | 完成（软件验证） | 单快照导出、SHA-256、严格版本、引用/水位预检、事务回滚；见 §4 |
| 签名与 versionCode 机制 | 机制完成；**正式签名被用户密钥阻塞** | 版本单一来源、单调校验、证书指纹比对、Release 缺材料明确失败；见 §5 |
| 真机覆盖安装 / 全新恢复 | **未验证** | 无 adb 设备；见 §7 |

---

## 2. 代码变更

| 文件 | 变更 |
| --- | --- |
| `core/PowerStore.kt` | schema v3（`meta.lineage_id`）、有序可重试升级链、读快照事务、BackupEnvelope 导出、恢复前完整校验与事务化导入、水位/lineage 恢复；执行记录不导入 |
| `core/BackupEnvelope.kt` | 新增 v1 codec：规范化 payload、SHA-256、版本/尺寸/lineage 校验、回读字段数校验、旧 flat v2 显式同意与格式防混淆 |
| `ui/v2/PowerCalApp.kt` | 恢复前 64MB 上限、Envelope 校验错误可见、旧格式确认对话框、导出后回读校验文案；无布局/视觉改动 |
| `app/build.gradle.kts` | 版本读取自 `gradle/version.properties`；versionCode 单调校验；仅仓库外签名材料；证书 SHA-256 比对；缺失时 Release 明确失败 |
| `gradle/version.properties` / `gradle/published-versions.txt` | `900` / `0.9.0`；已发布版本历史（当前最大 2） |
| `gradle/expected-signing-sha256.txt` | 长期证书指纹记录位；为空时 Release 构建失败 |
| `.gitignore` | 排除 keystore / signing properties |

> **工作区完整性说明**：续接时发现 `PowerStore.kt` 被截断为 855 行（丢失读取/AI/备份等全部接口）。
> 已从 `aac52a2` 完整恢复（1771 行）并重新应用本轮 P0 修改；最终文件经全量测试与 diff 范围核对。

---

## 3. Schema 升级

`powercal-v2.db` 版本：2 → 3（新增 `meta.lineage_id`）。

- `onCreate`：v3 完整表结构。
- `onUpgrade`：按 1→2→3 顺序执行；每步幂等/可重试；未知路径显式抛错，绝不重建或清空。
- `addStoreLineageColumn`：先检查 `PRAGMA table_info`；已存在列时跳过 ALTER，只补齐空值。进程在
  ALTER 后、版本号提交前被杀时可安全重试。
- 降级仍显式拒绝；迁移异常由 `SQLiteOpenHelper` 事务回滚，旧库保持可打开。

**真实旧库测试**（`PowerStoreSchemaUpgradeTest`，3 项）：

| Fixture | 内容 | 断言 |
| --- | --- | --- |
| v1（`user_version=1`） | 正式对象、墓碑、提交、待处理草稿（draftVersion=4）、sourceLink、MigrationRecord、调度、执行记录、next_seq=43 | 升级到 v3 后行/内容/修订/墓碑/高水位/草稿身份全部保留；新增 AI 授权表可用；`lineage_id` 为 UUID；后续新建序号为 43 |
| v2（`user_version=2`） | 同上并已有 `ai_session_drafts`，无 lineage 列 | 原地加列，业务行不变，备份可导出 |
| 半迁移 v2（已有 lineage 列、version 仍为 2） | 模拟 ALTER 后崩溃 | 重试成功，不报重复列，业务行不变 |

覆盖矩阵中的“v1→v2”“v2→v3 虚拟旧库”与“迁移重试”均由真实 SQLite fixture 验证；不是只调用
`onCreate` 的空库测试。

---

## 4. BackupEnvelope v1

```json
{
  "format": "powercal-native",
  "backupFormatVersion": 1,
  "databaseSchemaVersion": 3,
  "appVersionCode": 900,
  "exportedAtEpochMs": 0,
  "storeLineageId": "uuid",
  "payloadSha256": "64-hex",
  "payload": { "identities": [], "objects": [], "drafts": [], "commits": [],
               "sourceLinks": [], "migrations": [],
               "nextSeq": 1, "nextOrder": 1 }
}
```

- **一致性快照**：所有表与两个水位在同一个 SQLite 读事务中读取。
- **哈希**：对规范化 JSON（对象键排序、数组顺序保留、数字规范化）的 UTF-8 字节计算 SHA-256；回读使用同一规范化。
- **严格版本**：`backupFormatVersion` 必须为 1；`databaseSchemaVersion` 必须 ≤ 当前；未知未来版本直接拒绝。
- **格式防混淆**：缺少 `backupFormatVersion` 的文件仅在“无 `payload`/`payloadSha256`、字段集合完全已知、version=2”时按旧格式处理；否则拒绝。旧 flat v1 明确拒绝并提示保留原文件。
- **恢复预检**：尺寸/行数上限、必需数组、对象/草稿/提交/来源对身份的引用、提交→草稿引用、重复 ID/序号、`nextSeq`/`nextOrder` 上下限、草稿枚举与表单解码。
- **空库判定**：identities/objects/drafts/commits/source_links/migrations/schedule_intents/executions/AI 表全部为空，且两个水位仍为 1；预检与事务内各检查一次。
- **原子性**：单事务插入；中途约束失败全部回滚（失败注入测试）。
- **恢复语义**：保留正式快照、墓碑、草稿队列与 draftVersion、提交历史、来源/迁移记录、高水位和 lineage；不恢复 AI 会话/锁、权限、PendingIntent，也**不导入旧设备执行记录**；调度从正式对象重建。
- **旧 flat v2**：无哈希/lineage，默认拒绝；调用方必须显式 `allowLegacyV2=true`（UI 有确认框），保留文件的 `nextOrder`（若存在），恢复后视为新 lineage 并给出警告。
- **导出回读**：写文件后按同一 64MB 上限回读，重新校验哈希并逐表比对行数与两个水位；提供方不支持回读时不得宣称“已校验”。

**测试**（`BackupEnvelopeTest`，15 项）：Envelope 结构与哈希、规范化键序无关、往返（对象/草稿/提交/迁移/水位/lineage/调度重建、执行记录不恢复）、篡改、缺失 hash/payload、未知 backup 版本、未来 schema、缺身份引用、低水位、重复来源链接触发中途回滚、旧格式同意→恢复、旧 v1 拒绝、剥离 Envelope 降级拒绝、旧格式未知字段拒绝且保留 nextOrder、回读行数不一致、非空库拒绝。

未覆盖：外部云盘写入中断（SAF 提供方行为）、实际 64MB/20 万行样本、真实设备进程强杀。

---

## 5. 版本与签名

- 版本唯一来源 `gradle/version.properties`：`versionCode=900`、`versionName=0.9.0`；构建校验必须大于 `gradle/published-versions.txt` 最大值（当前 2）。
- `VersionContractTest` 固定 packageId/versionName/versionCode，并断言 DB schema、backup 版本、APK 版本是三个独立轴。
- Release 签名只从仓库外环境变量读取；缺失时 `:app:assembleRelease` 在 `preReleaseBuild` 阶段失败：

```
> Task :app:verifyReleaseSigning FAILED
缺少长期 Release 签名材料。请由用户自行生成并离线保管 keystore，然后通过环境变量
POWERCAL_KEYSTORE / POWERCAL_KEYSTORE_PASSWORD / POWERCAL_KEY_ALIAS / POWERCAL_KEY_PASSWORD 提供；
仓库不会生成、读取或提交密钥。具体步骤见 docs/RELEASE_VERSIONING.md。
```

- 提供材料后，`verifyReleaseCertificate` 会把 keystore 证书 SHA-256 与
  `gradle/expected-signing-sha256.txt` 比对；为空或不一致都会使 Release 构建失败，防止换证书覆盖安装。
- 当前 `:app:signingReport`：**release 无签名配置**；debug 使用
  `C:\Users\Shake\.android\debug.keystore`，SHA-256
  `E5:37:A1:A4:E4:F2:67:B4:95:FA:DE:A0:52:8E:0F:C5:52:D2:6D:5C:87:7A:9B:47:18:2F:29:B0:51:29:A7:A6`。
  该指纹**不是**稳定签名，不能作为首次稳定安装依据。

**因此本轮无法交付正式签名 Release APK 与证书指纹。** 需要用户自行生成长期 keystore、记录指纹并
离线保管，然后按 `docs/RELEASE_VERSIONING.md` 提供四个环境变量后构建；本 Agent 不代生成、不读取、
不保存密钥，也没有用临时密钥伪造 Release 包。

---

## 6. 自动化验证

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
```

结果：**BUILD SUCCESSFUL；191 项测试，17 个套件，0 失败、0 跳过；lint 0 错误、13 警告**（与既有基线同类）。
Debug APK：`app/build/outputs/apk/debug/app-debug.apk`（版本字段见下），
SHA-256 见文末“本次验证产物”。Debug APK **不是**稳定发行包。

`aapt2 dump badging`：package `dev.powerfulcalendar`、versionCode `900`、versionName `0.9.0`、
minSdk 26、targetSdk 35。`apksigner verify --print-certs`：仅 Android Debug 证书。

---

## 7. 未验证项（必须标注）

- **老版本覆盖安装**（V0→V1 同包名同证书、数据不变）：无真机/模拟器，未验证。
- **全新安装 → 恢复 → 下一次升级/调度**：仅 Robolectric 软件验证，未真机验证。
- **正式 Release 签名与证书指纹**：被用户密钥阻塞，未生成；正向签名路径未实测。
- **真实设备进程强杀 / 低存储 / WAL 恢复**：未注入真实存储故障。
- **SAF/云盘写入中断与回读失败**：代码路径有处理，未对真实提供方验证。
- **从实际用户设备导出的匿名 DB/备份样本**：本轮没有样本，fixture 依据历史 DDL 构造。
- **哈希语义**：`payloadSha256` 按契约只覆盖 payload，是损坏/篡改检测，不是密码学签名；
  `databaseSchemaVersion`/`storeLineageId` 等包裹字段本身不被哈希保护。
- **规范化实现依赖**：canonical JSON 由本项目 `BackupEnvelope` 定义并测试，但底层字符串转义/数字
  处理依赖 Android `org.json`；未来重写编解码器必须复用同一 canonical 实现。
- 导入旧闹钟 handoff（DSH-05）、重复范围编辑（DSH-01）、observable snapshot（DSH-03）仍未实现；
  与本轮 P0 无冲突，不影响现有数据安全。

## 8. 升级/恢复操作建议（发布前由用户执行）

1. 生成并离线备份长期 Release keystore；把证书 SHA-256 写入 `gradle/expected-signing-sha256.txt`。
2. 若设备已有 Debug 版真实数据：先导出备份并验证，再卸载 Debug、安装 Release、恢复。
3. 执行契约 §7 的最小验收矩阵（新装、覆盖升级、备份完整性、新装恢复、失败注入、提醒恢复、未来兼容、签名、隐私）。
4. 全部通过后，才把 `STABLE_DATA_CONTRACT_V1.md` 状态改为 **APPROVED FOR STABLE USER DATA**，
   并记录 versionCode / schema version / backup format version / 证书指纹 / 样本恢复结果。

---

## 9. 本次验证产物

- 测试：`app/build/test-results/testDebugUnitTest/*.xml`（17 套件 / 191 项 / 0 失败）。
- Lint：`app/build/reports/lint-results-debug.html`（0 error / 13 warning）。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，63,431,830 bytes，
  SHA-256 `EFAFEA31DB211B1C81D6EBAF4E5B07C866EE87802A26D23F7AD88DEC278DB6C3`。
- Release：按设计被 `verifyReleaseSigning` 阻断，未生成签名包。
