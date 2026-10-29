# PC-ISSUE-SEPARATION-LEGACY-FLAT-V2-RESTORE

- **日期：** 2026-10-30
- **状态：** `PARTIAL / HOLD`
- **验收项：** 重建矩阵 `S0-ST-17`（非历史 209 项原始 ID）
- **验证提交：** `c474ac9b21f52e2d846e59156e80d73efd719394`（Host 集成回归）、`789074ede1879c616fcd0881fdecd080d38a36e0`（lineage 无副作用快照），均已推送到 `origin/codex/separation-s0-timeline-drag`

## 结论

PowerCal 已实现并现由 API 35 Robolectric Host 集成回归证明：旧式 `format=powercal-native, version=2` 文件缺少 payload SHA-256 和 `storeLineageId`，普通恢复请求会停止在显式确认步骤；展示无法校验文件完整性/来源的警告后，取消不写入；用户确认后只向空目标库恢复，并为恢复后的目标数据库分配新的 UUID lineage。旧格式数据不能继承一个它从未携带的来源 lineage。

此前 Core 单元测试已经覆盖格式识别与新 lineage 分配，但没有覆盖真实 `PowerCalApp` Host 恢复入口上的确认弹窗、取消及确认后效果。这次新增两条 Host 回归，未改生产恢复逻辑。清单选项已由 S0-ST-16 接入 Clear 与 Native 共用的 Host 恢复入口；本报告复核的是该入口对 flat v2 的额外确认层。

Robolectric 和 Core 测试通过；真实 Android WebView→Host、系统 OpenDocument/SAF provider、Activity/进程恢复及设备提醒重建均未运行。因此该验收项保持 **`PARTIAL / HOLD`**，不签核完整 Native/Clear 设备 parity。

## 合同依据与 lineage 定义

- [STABLE_DATA_CONTRACT_V1.md](../STABLE_DATA_CONTRACT_V1.md) §4 要求：早期 flat v2 需明确实现兼容读取或在稳定发行前拒绝；恢复前验证文件版本/结构；并且恢复数据不应带回旧授权和旧设备上的调度执行状态。该合同当前状态注明 flat v2 需要单独确认。
- `BackupEnvelope.kt` 的旧格式 reader 将 flat v2 标记为 `legacyUnverified`，不接受未知字段或混合 Envelope 字段；在 `allowLegacyV2=false` 时抛出 `LegacyBackupConsentRequired`。明确同意后，它为目标数据生成新的 UUID `storeLineageId`。
- flat v2 本身没有来源 `lineageId`，所以“新 lineage”含义是：恢复数据所在的目标数据库拥有独立 lineage，不能伪装成导出前数据库的 lineage；不是将某个备份中不存在的 ID 迁移过去。

## 调用链与根因

1. Native 与 Clear 的 `data.backup.restore` 最终调用 `PowerCalApp.kt` 的同一 `OpenDocument` launcher，并由有界 `SafBackupReader` 读取 JSON。
2. 首次调用 `restoreAndRefresh(parsed, allowLegacy = false)`。Envelope v1 经哈希与结构验证后可进入恢复；flat v2 则触发 `LegacyBackupConsentRequired`，原始文本暂存于内存状态并显示确认对话框，不调用 Store 写入。
3. 用户取消时只清除暂存文本。用户确认后才重新解析 flat v2 并调用 `restoreAndRefresh(..., allowLegacy = true)`；Core 再次检查空库和格式规则，在事务中恢复数据并写入新 lineage，随后由 Host 重建提醒并刷新诊断。
4. 既有 `BackupEnvelopeTest.legacyFlatV2RequiresExplicitConsentThenRestoresAsNewLineage` 覆盖了 Core 分支；缺失的是宿主交互与副作用证据。这次新增的两条集成测试补上这一处证据缺口。

## 修改内容

- [BackupRestoreHostTest.kt](../../app/src/test/java/dev/powerfulcalendar/ui/v2/BackupRestoreHostTest.kt)：新增隔离源/目标数据库的 Host 测试。
  - `legacyFlatV2RequiresExplicitConsentAndCancelDoesNotWrite`：打开 flat v2 文件后验证警告可见、目标 Store 各计数与序号快照不变；点击取消后仍保持完全不变。
  - `confirmedLegacyFlatV2RestoresUnderANewLineage`：验证旧格式不含 lineage；确认后恢复正式事件与草稿，显示未验证状态，且目标 lineage 既不同于可比较的源 Envelope lineage，也不同于恢复前目标库 lineage。
- 没有改生产代码、文件格式、备份读取上限、空库规则或迁移策略。

## 验收结果

| 场景 | 结果 | 证据边界 |
| --- | --- | --- |
| flat v2 格式需要显式确认 | `PASS` | 未确认时 Host 显示“恢复早期格式备份？”并说明无 SHA-256/lineageId、需要确认来源；未写 Store。 |
| 取消确认无副作用 | `PASS` | 比较正式对象、草稿、待处理数量、身份数、序号水位、提交、提醒和 lineage 快照，前后完全相同。 |
| 明确确认后恢复 | `PASS` | 隔离源数据库中的正式闹钟与 Memo 草稿恢复到隔离空目标库；UI 标注“未验证完整性”。 |
| 恢复后生成新的 lineage | `PASS` | 导出目标 Envelope 后读回 UUID；与来源 Envelope lineage 及恢复前目标 lineage 均不同。flat v2 文件自身没有 lineage。 |
| Core 格式验证与事务回归 | `PASS` | 完整 JVM 套件包含既有 `BackupEnvelopeTest` 的拒绝/显式同意/新 lineage 用例及 Store 恢复测试。 |
| 定向 Host/Bridge/投影回归 | `PASS` | **41/41**，4 suites：`BackupRestoreHostTest` 9、`WebSkinBridgeTest` 4、`WebSkinHostProjectionTest` 23、`SemanticBackupExportIntegrationTest` 5；0 failures/errors/skips。 |
| 完整离线 Gradle 门禁 | `PASS` | Source boundary、**699/699 JVM 测试 / 93 suites / 0 failures/errors/skips**、Debug APK、AndroidTest APK 组装、Lint。Lint 0 errors / 53 issues；88 actionable tasks，14 executed / 74 up-to-date。 |
| 全量测试重新执行 | `PASS` | `:app:testDebugUnitTest --offline --rerun-tasks`，699/699，93 suites，32 tasks 全执行成功。 |
| Clear Studio Preview 浏览器验证 | `PREVIOUS PASS` | S0-ST-16 同一恢复选项与其 flat v2/哈希来源限制文案曾在 Preview/CDP 验证；Preview Mock 不会执行真实恢复，也不证明 WebView 到 Android Host 的连接。见前项报告及其日志。 |
| 真实 Android WebView/SAF 与设备恢复 | `NOT TESTED` | 当前无在线 ADB 设备，环境无 `emulator.exe`；AndroidTest APK 仅组装，未运行 instrumentation。 |

## 可复核证据

工作区：`E:\Project\Powerful-Calendar\.local\android-test\worktrees\separation-s0-timeline-drag`。日志放在忽略目录 `.local/android-test/reports/separation-s0-st17-legacy-v2-20261030/`。

| 命令/产物 | 结果与 SHA-256 |
| --- | --- |
| `:app:testDebugUnitTest` 四个定向套件，`--offline --rerun-tasks` | 41 tests / 4 suites；`focused-gradle.log`: `F2010753C57C45AFD178A1F5D41922F84FBAA007B98FB29F546ABB37CD21DFE9` |
| `BackupRestoreHostTest` 单独重跑，`--offline --rerun-tasks` | 9/9 PASS；没有出现全量日志中的 Robolectric `A resource failed to call close` 警告；`host-isolated-gradle.log`: `04C5204D6054EF427C8F38B726ECA888C4C7B37F09D26EDD4338DFC9C7B28CD0` |
| `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain` | PASS；699 tests / 93 suites；`full-gradle.log`: `EBE3E545D04AFE8C4DAFD130F796A3D922D2788BEE6754D9BFD7E90C5D72FD9E` |
| `:app:testDebugUnitTest --offline --rerun-tasks --console=plain` | PASS；699 tests / 93 suites；`full-gradle-rerun.log`: `9A47A8681BA6C05F163907CE0CFC92A4C0ED35F467030D2D1783363783219A97` |
| Debug APK | 67,502,712 bytes；SHA-256 `04951EF7CC3F53FB838D911A24DC1E659B4F3D5335D3C4ED8F0C867D15F1380E` |
| AndroidTest APK | 1,557,196 bytes；SHA-256 `9F8C04CABD4FCA21009773FDF0B8C0BC873CC9B424F8E4F6426E0DEE4CBB5B34` |
| `git diff --check` | PASS；只有本 Issue 新增的 Host 测试进入代码提交。 |

环境为 Windows、JDK 20.0.1、Gradle 8.14.3、Android SDK API 36、Robolectric API 35。测试只使用随机命名的隔离测试数据库；没有读取、清除或覆盖用户数据库。Lint 现有 53 项报告为 issues/warnings，本门禁没有 lint errors。

## 剩余缺口与交付

- Robolectric 驱动 Compose Host 确认框，不能替代真实 Android 系统文件选择器及 SAF URI 权限测试。
- 尚未在 Clear 生产 WebView 页面中完成一次真实文件选择→Host 确认→恢复→进程重启的闭环，也没有 OEM 设备提醒/权限证据。
- flat v2 没有哈希和来源认证；确认只能记录用户确认了风险，不能让文件获得真实性证明。用户应保留原文件，并在恢复后导出新的 Envelope 备份。

测试提交 `c474ac9`（`test(分离): 验证 flat v2 恢复需明确同意`）和 `789074e`（`test(分离): 将 lineage 纳入恢复无副作用快照`）均已推送。实现行为经 Core 与 Host 自动化验证，但 Android 设备路径未测试，因此 S0-ST-17 保持 **`PARTIAL / HOLD`**。
