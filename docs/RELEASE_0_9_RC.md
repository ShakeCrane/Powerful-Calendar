# PowerCal 0.9 · Release Candidate 数据基础报告

**分支**：`release/0.9-data-foundation`
**基线**：`aac52a2`（0.9 稳定数据契约文档提交）
**状态**：**Release Candidate（RC）**，不是稳定版验收。
**依据**：[稳定数据契约 v1](./STABLE_DATA_CONTRACT_V1.md)、[P0 审计](./RELEASE_0_9_P0_AUDIT.md)、[版本与签名机制](./RELEASE_VERSIONING.md)。

> 本轮只处理三个阻断项：schema 原地升级、原生 BackupEnvelope v1、签名/versionCode 机制。
> 未改 Glass 视觉、页面布局或皮肤；UI 变更仅限恢复入口的校验/确认提示与备份文案。

---

## 1. 交付结论

| P0 项 | 状态 | 证据 |
| --- | --- | --- |
| SQLite v1/v2 → v3 原地升级 | 完成（软件验证） | 真实 v1、v2 旧库 fixture；见 §3 |
| 原生 BackupEnvelope v1 | 完成（软件验证） | 单快照导出、SHA-256、严格版本、引用/水位预检、事务回滚；见 §4 |
| 签名与 versionCode 机制 | 机制完成；**正式签名被用户密钥阻塞** | `gradle/version.properties`、`verifyReleaseSigning`；见 §5 |
| 真机覆盖安装 / 全新恢复 | **未验证** | 无 adb 设备；见 §7 |

---

## 2. 代码变更

| 文件 | 变更 |
| --- | --- |
| `core/PowerStore.kt` | schema v3（`meta.lineage_id`）、有序可重试升级链、`readSnapshot` 单读事务、BackupEnvelope 导出、恢复前完整校验与事务化导入、执行记录/水位/lineage 恢复 |
| `core/BackupEnvelope.kt` | 新增 v1 codec：规范化 payload、SHA-256、版本/尺寸/lineage 校验、旧 flat v2 需显式同意 |
| `ui/v2/PowerCalApp.kt` | 恢复前 64MB 上限、Envelope 校验错误可见、旧格式确认对话框、导出后回读校验文案；无布局/视觉改动 |
| `app/build.gradle.kts` | 版本读取自 `gradle/version.properties`；versionCode 单调校验；仅仓库外签名材料；缺失时 Release 构建明确失败 |
| `gradle/version.properties` | `versionCode=900` / `versionName=0.9.0` |
| `gradle/published-versions.txt` | 已发布版本历史，构建时校验单调增长 |
| `.gitignore` | 排除 keystore / signing properties |

> **工作区完整性说明**：接管续接时发现 `PowerStore.kt` 被截断为 855 行（丢失读取/AI/备份等全部接口）。
> 已从 `aac52a2` 完整恢复（1771 行）并重新应用本轮 P0 修改；最终文件经全量测试与 diff 范围核对。

---

## 3. Schema 升级

`powercal-v2.db` 版本：2 → 3（新增 `meta.lineage_id`）。

- `onCreate`：v3 完整表结构。
- `onUpgrade`：按 1→2→3 顺序执行；每步幂等/可重试；未知路径显式抛错，绝不重建或清空。
- `addStoreLineageColumn`：先检查 `PRAGMA table_info`，重复执行安全（进程在 ALTER 后被杀也能重试）。
- 降级仍显式拒绝。

**真实旧库测试**（`PowerStoreSchemaUpgradeTest`，2 项）：

| Fixture | 内容 | 断言 |
| --- | --- | --- |
| v1（`user_version=1`） | 正式对象、墓碑、提交、待处理草稿（draftVersion=4）、sourceLink、MigrationRecord、调度、执行记录、next_seq=43 | 升级到 v3 后行/内容/修订/墓碑/高水位/草稿身份全部保留；新增 AI 授权表可用；`lineage_id` 为 UUID；后续新建序号为 43 |
| v2（`user_version=2`） | 同上并已有 `ai_session_drafts`，无 lineage 列 | 原地加列，业务行不变，备份可导出 |

覆盖矩阵要求中的“v2→v3 虚拟旧库”与“v1→v2”均已由真实 SQLite fixture 验证；不是只调用 `onCreate` 的空库测试。

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
               "sourceLinks": [], "migrations": [], "executions": [],
               "nextSeq": 1, "nextOrder": 1 }
}
```

- **一致性快照**：所有表与两个水位在同一个 SQLite 读事务中读取。
- **哈希**：对规范化 JSON（对象键排序、数组顺序保留、数字规范化）的 UTF-8 字节计算 SHA-256；回读校验使用同一规范化。
- **严格版本**：`backupFormatVersion` 必须等于 1；`databaseSchemaVersion` 必须 ≤ 当前；未知未来版本直接拒绝。
- **恢复预检**：尺寸/行数上限、必需数组、对象/草稿/提交/来源/执行对身份的引用、重复 ID/序号、`nextSeq`/`nextOrder` 不低于实际高水位、草稿操作/状态枚举、表单可解码。
- **恢复原子性**：空库前置条件 + 单事务插入；中途失败全部回滚（含失败注入测试）。
- **恢复语义**：保留正式快照、墓碑、草稿队列与 draftVersion、提交历史、来源/迁移记录、执行记录、高水位和 lineage；不恢复 AI 会话/锁、权限或 PendingIntent；调度从正式对象重建。
- **旧 flat v2**：无哈希/lineage，默认拒绝；调用方必须显式 `allowLegacyV2=true`（UI 有确认对话框），恢复后视为新 lineage 并保留警告。

**测试**（`BackupEnvelopeTest`，12 项）：Envelope 结构与哈希、规范化键序无关、往返（对象/草稿/提交/执行/迁移/水位/lineage/调度重建）、篡改、缺失 hash/payload、未知 backup 版本、未来 schema、缺身份引用、低水位、重复执行触发中途回滚、旧格式同意→恢复、非空库拒绝。

未覆盖：外部云盘写入中断（SAF 提供方行为）、超大文件（UI 64MB 上限 + 行数上限，但无实际 64MB 样本）。

---

## 5. 版本与签名

- 版本唯一来源 `gradle/version.properties`：`versionCode=900`、`versionName=0.9.0`；构建校验必须大于 `gradle/published-versions.txt` 中最大值（当前 2）。
- `VersionContractTest` 固定 packageId/versionName/versionCode，并断言 DB schema、backup 版本、APK 版本是三个独立轴。
- Release 签名只从仓库外环境变量读取；缺失时 `:app:assembleRelease` 在 `preReleaseBuild` 阶段失败：

```
> Task :app:verifyReleaseSigning FAILED
缺少长期 Release 签名材料。请由用户自行生成并离线保管 keystore，然后通过环境变量
POWERCAL_KEYSTORE / POWERCAL_KEYSTORE_PASSWORD / POWERCAL_KEY_ALIAS / POWERCAL_KEY_PASSWORD 提供；
仓库不会生成、读取或提交密钥。具体步骤见 docs/RELEASE_VERSIONING.md。
```

- 当前 `:app:signingReport`：**release 无签名配置**；debug 使用
  `C:\Users\Shake\.android\debug.keystore`，SHA-256
  `E5:37:A1:A4:E4:F2:67:B4:95:FA:DE:A0:52:8E:0F:C5:52:D2:6D:5C:87:7A:9B:47:18:2F:29:B0:51:29:A7:A6`。
  该指纹**不是**稳定签名，不能作为首次稳定安装依据。

**因此本轮无法交付正式签名 Release APK 与证书指纹。** 需要用户自行生成长期 keystore 并离线保管，
然后按 `docs/RELEASE_VERSIONING.md` 提供四个环境变量后构建；本 Agent 不代生成、不读取、不保存密钥。

---

## 6. 自动化验证

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
```

结果：**BUILD SUCCESSFUL；187 项测试，17 个套件，0 失败、0 跳过；lint 0 错误、13 警告**（与既有基线同类）。
Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，62,984,800 bytes，
SHA-256 `7C6D01460F738C4BBB8D99B34832CEB0E22ACE7FF8B6EE5F42CDA9F46B37B883`。
Debug APK **不是**稳定发行包。

测试套件新增/关键项：
- `PowerStoreSchemaUpgradeTest` 2 项（真实 v1/v2 旧库）
- `BackupEnvelopeTest` 12 项（含中途失败回滚、旧格式同意、非空库拒绝）
- `VersionContractTest` 2 项
- 既有 171 项全部保留通过

---

## 7. 未验证项（必须标注）

- **老版本覆盖安装**（V0→V1 同包名同证书、数据不变）：无真机/模拟器，未验证。
- **全新安装 → 恢复 → 下一次升级/调度**：仅 Robolectric 软件验证，未真机验证。
- **正式 Release 签名与证书指纹**：被用户密钥阻塞，未生成。
- **真实设备进程强杀 / 低存储 / WAL 恢复**：未注入真实存储故障。
- **SAF/云盘写入中断与回读失败**：代码路径有处理，未对真实提供方验证。
- **从实际用户设备导出的匿名 DB/备份样本**：本轮没有样本，fixture 依据历史 DDL 构造。
- 导入旧闹钟 handoff（DSH-05）、重复范围编辑（DSH-01）、observable snapshot（DSH-03）仍未实现；
  与本轮 P0 无冲突，不影响现有数据安全。

## 8. 升级/恢复操作建议（发布前由用户执行）

1. 生成并离线备份长期 Release keystore；记录证书 SHA-256。
2. 若设备已有 Debug 版真实数据：先导出备份并验证，再卸载 Debug、安装 Release、恢复。
3. 执行契约 §7 的最小验收矩阵（新装、覆盖升级、备份完整性、新装恢复、失败注入、提醒恢复、未来兼容、签名、隐私）。
4. 全部通过后，才把 `STABLE_DATA_CONTRACT_V1.md` 状态改为 **APPROVED FOR STABLE USER DATA**，
   并记录 versionCode / schema version / backup format version / 证书指纹 / 样本恢复结果。
