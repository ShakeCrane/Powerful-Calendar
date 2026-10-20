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
