# PC-ISSUE-SEPARATION-LEGACY-MIGRATION — Clear 旧版数据库迁移入口与批次收尾

- 日期：2026-10-30
- 环境：Windows PowerShell；Gradle Wrapper 8.14.3 / Java 20.0.1；仓库 Android SDK `E:\android-sdk`、Android 36 platform；Robolectric SDK 35；Chrome Headless/CDP；Gradle 离线运行。
- 阶段：Skin Separation `S0-ST-12`
- 性质：重建验收项（`RECONSTRUCTED`，不是历史 209 项原始 ID）
- 状态：**Host 与 Preview 自动化路径 PASS；生产 Android WebView、设备迁移及旧闹钟交接 HOLD / NOT TESTED**

## 需求与范围

Clear 需要能发起旧版 PowerCal 数据迁移；迁移必须继续由 Android Host 控制，先取得用户确认，再只读打开旧数据库，保留旧文件并让重复执行保持幂等。本 Issue 只补上 Clear 的 Host 注册选项与路由，并修复缺少 `events` 表时的迁移批次收尾。未改旧闹钟状态、提醒调度、AI 或数据库格式。

## 根因与复现

1. `LegacyImporter.migrate()` 在找到旧数据库但发现没有 `events` 表时，创建迁移批次后直接返回，没有调用 `finishMigration()`。每次重试都会留下 `finished_ms IS NULL` 的批次。
2. `SemanticOptions` 只注册了日历文件与系统日历导入。Clear 的通用选项来自 Host 投影，因此没有 `data.legacy.migrate` 就无法从 Clear 到达已有的 Android 原生迁移确认界面。

修复前新增的 `missingEventsTableClosesMigrationBatchAndIsSafeToRetry` 回归按预期失败：期待迁移批次 `(1, 0)`，实际得到 `(1, 1)`。证据日志：`.local/android-test/reports/separation-s0-st12-migration-20261030/targeted-before-fix.log`，SHA-256 `05139CCEA4405C6CC95B8CEC2B3B5D78044C6A8376D45E95166ED81BA92ECBFC`。

## 修复内容

- `LegacyImporter` 在没有 `events` 表时将警告写入迁移记录并关闭批次；重复尝试仍会产生已完成的诊断批次，但不会创建事件、身份或占用永久序号。
- Host 注册 `data.legacy.migrate`，并把该动作接到现有 `confirmingLegacy` 原生确认对话框。确认前不访问旧数据库；迁移仍只读打开旧库，保留文件。
- Bridge 与 Host 投影测试验证 ID 在允许列表中、未知 ID 仍拒绝，并确认选项说明只读迁移及保留旧数据库。
- Studio Preview Mock 展示并转发该选项，只记录动作，明确不读取、写入或删除数据库。Chrome E2E 从 Clear 月历页的“导入选项”点击该选项并验证 Mock 收到请求。
- 新增 `PowerCalApp` Robolectric/Compose Host 集成回归：核验 Host 选项投影和原生确认状态机，取消后批次、事件、身份、队列水位、提交记录均不变；确认后从隔离旧数据库导入一条记录，源文件 SHA-256 不变；重复运行后完整事件快照与正式提交数不变。该测试直接驱动 Host action handler，不声称经过生产 Clear WebView 或消息端口。

## 验收证据

| 检查 | 状态与结果 | 证据 |
|---|---|---|
| 修复前根因复现 | **FAIL（预期红测）**：缺少 `events` 表时批次未结束，实际 `(1,1)` | `.local/android-test/reports/separation-s0-st12-migration-20261030/targeted-before-fix.log`，SHA-256 `05139CCEA4405C6CC95B8CEC2B3B5D78044C6A8376D45E95166ED81BA92ECBFC` |
| 定向 Android/JVM 与 Compose 回归 | **28/28 PASS**：LegacyMigration 4、Bridge 4、Host 投影 19、PowerCalApp Host 集成 1；0 failures/errors/skips | `.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.data.LegacyMigrationTest' --tests 'dev.powerfulcalendar.ui.v2.WebSkinBridgeTest' --tests 'dev.powerfulcalendar.ui.v2.WebSkinHostProjectionTest' --tests 'dev.powerfulcalendar.ui.v2.LegacyHostMigrationTest' --offline --rerun-tasks --no-daemon --console=plain --max-workers=1`；最终日志 `.local/android-test/reports/separation-s0-st12-migration-20261030/targeted-final.log`，SHA-256 `BAA9EC443B5F7843B25FA48AD758AC691F12979BC36C3200F27E4D341DE4BB19` |
| 完整离线 Gradle 门禁 | **666/666 JVM、88 suites、0 failures/errors/skips；88/88 tasks PASS**；源码边界、Debug APK、AndroidTest APK、lint 均通过；lint 0 errors / 52 warnings | `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain --max-workers=1`；日志 `.local/android-test/reports/separation-s0-st12-migration-20261030/full-gradle-final.log`，SHA-256 `8749EABA40B6BCBF762E564857D9BEB5472F9FF6652F0BBD9D7A58858399206F` |
| Debug APK | **PASS / BUILT**，66,334,225 bytes；SHA-256 `4C516E4067F2C74FFF05F927CB469B23E137B587EAD12A66EABF76927D6DB61D` | `app/build/outputs/apk/debug/app-debug.apk`；包含的 Clear skin 与 canonical bundle 完全匹配 |
| AndroidTest APK | **PASS / BUILT ONLY**，1,337,236 bytes；SHA-256 `C23375D347A00880431B9A2807E0BFAB005973D690A8FF8BECC4DF94B6122931` | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`；没有在线 ADB 设备，未安装运行 instrumentation |
| Clear Preview 浏览器回归 | **PASS（Mock）**：月历页可见迁移选项；点击从 Clear Preview 到达 Studio Mock Host；Mock 明确未读取、写入、删除数据库 | Chrome Headless/CDP：`node tools/skin-studio/tests/e2e_v3_source.cjs`；日志 `.local/android-test/reports/separation-s0-st12-migration-20261030/studio-e2e.log`，SHA-256 `187989D52EB5DDD6732BC4C5C802CF88E18B53E153E7E09EB4C7E79919B50425` |
| Skin Studio Python 测试 | **61/61 PASS** | `python -m unittest discover -s tools/skin-studio/tests -v`；日志 `.local/android-test/reports/separation-s0-st12-migration-20261030/studio-unit.log`，SHA-256 `C2DB5768A687DC3AFD014028BB36A07B5A16E336D6F21F52E8E63C3459C34691` |
| JS 语法与 Clear 包 | `node --check` 两个变更的 JS 文件 **PASS**；canonical Clear **9 files / 392,170 bytes PASS**，源码包与 APK 内资源 SHA-256 同为 `af640cbca89a870c2d1ea733eb562ed35e6082b75b08fe7aa37afa9d4ff6f98a` | `python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk` |
| ADB / Android WebView 设备链路 | **NOT TESTED / HOLD**：`adb devices -l` 只返回设备表头，没有设备；本轮只构建了 AndroidTest APK | 生产 Android WebView → WebMessagePort → Clear 页面传输，以及真实旧数据库设备迁移尚未验证 |

## 剩余风险与状态

- 旧版数据库中的旧闹钟不会由此迁移器自动关闭或确认 handoff。原生确认对话框继续提醒用户先检查并关闭旧时钟中的闹钟，再启用 PowerCal 提醒；该交接过程没有在设备上验证。见 `docs/DSH_P0_CASE_STUDY.md` 的 DSH-05 记录。
- Robolectric 验证了真实 Host 处理与隔离 SQLite fixture；Chrome 验证的是 Preview Mock。两者共同覆盖动作/确认/迁移边界，但不等同于生产 WebView 或 Android 设备 E2E。
- 本 Issue 的 Host 代码和自动化行为为 **PASS**；`S0-ST-12` 的设备/旧闹钟交接与总体分离 parity 继续 **PARTIAL / HOLD**，不构成整体 GO。

实现提交：`2a2cb7ab7bf500d761f0594c97ee4ede797cbd7b`（`fix(皮肤分离): 接入旧版数据迁移并收尾失败批次`，已推送至 `origin/codex/separation-s0-timeline-drag`）。证据目录：`.local/android-test/reports/separation-s0-st12-migration-20261030/`。
