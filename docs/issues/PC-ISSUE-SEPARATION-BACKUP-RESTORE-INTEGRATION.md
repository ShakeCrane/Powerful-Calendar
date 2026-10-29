# PC-ISSUE-SEPARATION-BACKUP-RESTORE-INTEGRATION

- **日期：** 2026-10-30
- **状态：** `PARTIAL / HOLD`
- **验收项：** reconstructed `S0-ST-16`
- **实现提交：** `a145463fc75bbc9910dcd0bb2cba850ad6a12069`（恢复流程与并发保护）、`8efff1f`（Web 编辑器互斥），均推送到 `origin/codex/separation-s0-timeline-drag`

## 结论

Clear 现有“备份”语义选项中加入 `data.backup.restore`。它只向 Android Host 请求打开已有的 `OpenDocument` 文件选择器，Native Tools 与 Clear 共用同一恢复回调。读取、格式与哈希校验、空库限制、事务写入和提醒恢复继续由既有 Android Host 与 `PowerStore` 管理；没有向 WebView 开放数据库或文件读取能力，也没有另造一套恢复格式。

API 35 Robolectric 用随机命名的隔离源库和目标库验证宿主入口、取消、坏哈希拒绝、非空库保护、重复恢复拒绝、Deck 打开及加载期间的动作守卫，并确认成功恢复后提醒意图已进入 `SCHEDULED` 或 `BLOCKED` 状态。Chrome/CDP 另验证 Clear Preview 显示该选项及其限制，并把请求交给不会访问文件或数据库的 Preview Mock。

这些自动化切片通过，但生产 Android WebView 到 Host 的连接、系统文件选择器和 SAF provider、设备 Activity/进程恢复及真实 AlarmManager 行为没有可用模拟器或在线设备可验证。因此 S0-ST-16 仍为 **`PARTIAL / HOLD`**，不代表恢复 parity 已完整签核。

## 调用链、根因与修复

1. Native Tools 原有“从备份恢复”调用 `OpenDocument`。选择文件后，Host 通过 `SafBackupReader` 有界读取，再调用 `restoreAndRefresh`；取消返回 `null`，不写数据库。该路径已封装现有格式、错误说明和恢复确认行为。
2. Clear 的语义菜单原来没有恢复选项，也没有 `InvokeOption` 到 Native 恢复 launcher 的 Host 分支。已有 `BackupEnvelopeTest` 与 `PowerStoreTest` 验证了核心格式和数据库保护，却没有证明 Clear 能触达 Android Host 的文件选择与恢复回调。
3. 新增闭合的 `data.backup.restore` 语义 ID，并接到 Native 与 Clear 共用的 `launchRestoreBackup`。恢复中、原生编辑器打开、Web 编辑会话活动、Deck 打开/加载或旧格式确认期间，Host 会拒绝新的恢复动作；一次只允许一个等待中的文件选择。Native 原按钮也复用同一 launcher。
4. 回归审查发现 `openDeckAt` 在异步读取完成后才设置 `deckOpen`，所以同一帧内发出的恢复动作可能赶在 Deck 状态变更前启动。现在 Host 在发起 Deck 读取前同步设置 `deckOpening`，恢复动作和语义选项状态都检查该标志。
5. `restoreAndRefresh` 原先先加载诊断再协调提醒，诊断可能显示过期的 `PENDING`。顺序改为先调用提醒协调，再加载 Store 诊断，与应用启动时的顺序一致。恢复成功文案现在说明实际投递状态应查看“提醒可靠性”，不再暗示 OS 一定已经成功投递。
6. 选项及 Native 工具说明清楚标注：恢复只允许目标数据库为空；SHA-256 校验文件完整性但不证明来源可信；旧授权和旧执行记录不会恢复；恢复后的提醒状态可能受权限限制。flat v2 确认与迁移属于单独的 S0-ST-17，本 Issue 不将其并入签核。

## 修改内容

- [PowerCalApp.kt](../../app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt)：共享恢复 launcher；接入 Clear Host action；拦截重复选择与原生/Web 编辑器及 Deck 状态竞争；协调提醒后刷新诊断；更新恢复状态说明。
- [SemanticOptions.kt](../../app/src/main/java/dev/powerfulcalendar/ui/v2/SemanticOptions.kt)：注册 `data.backup.restore` 并提供空库、完整性、来源信任和提醒权限说明。
- [BackupRestoreHostTest.kt](../../app/src/test/java/dev/powerfulcalendar/ui/v2/BackupRestoreHostTest.kt)：新增 API 35 Compose/Robolectric Host 集成测试，所有源/目标数据库均为随机隔离测试库；覆盖 Web 编辑活动期间拒绝启动恢复选择器。
- [WebSkinBridgeTest.kt](../../app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinBridgeTest.kt)、[WebSkinHostProjectionTest.kt](../../app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinHostProjectionTest.kt)：验证恢复 action 解码、闭合 schema 和选项限制文案。
- `tools/skin-studio/v3-studio.js`、`tools/skin-studio/tests/e2e_v3_source.cjs`：Clear Preview 增加非写入恢复 Mock 与浏览器回归；不访问真实文件或日历数据库。

未修改备份封套、数据库迁移格式、`PowerStore.restoreBackup` 的空库/事务约束、系统授权语义或 Clear 生产 Web 资源。

## 验收结果

| 场景 | 结果 | 证据边界 |
| --- | --- | --- |
| 恢复选项注册与 Bridge 解码 | `PASS` | `data.backup.restore` 可由闭合语义白名单解码；未知 ID 和额外 payload 字段仍被拒绝。 |
| 空隔离库恢复 Envelope v1 | `PASS` | 生产 `PowerCalApp` Host action 启动 `ACTION_OPEN_DOCUMENT`；从独立源库恢复一个正式闹钟和一张待处理 Memo 草稿；提示数据数量与旧设备权限边界；校验持久化提醒意图状态。不是系统文件选择器 UI。 |
| 用户取消文件选择 | `PASS` | `ActivityResultRegistry` 返回 `null`；记录、草稿、身份数、序号水位、提交与提醒状态完全不变。 |
| Envelope 哈希损坏 | `PASS` | 修改 payload 但保留旧哈希；Host 显示完整性校验失败，空目标库保持不变。 |
| 非空目标库 | `PASS` | 预置现存 Memo 草稿后选择有效备份；Host 拒绝，原草稿、记录、身份数、序号、提交和提醒保持不变。Core 仍在事务内二次检查。 |
| 正在打开或已打开 Draft Deck | `PASS` | 分别验证同一帧动作和 Deck 完成打开后的直接 Host action 都不会启动恢复 picker。 |
| Web 编辑会话活动期间请求恢复 | `PASS` | 通过 Web Edit Bridge 建立 live Memo 编辑器，再请求恢复；Host 不启动 picker，隔离 Store 仍无记录或草稿，关闭 Web 编辑器成功。 |
| picker 重入及重复导入 | `PASS` | picker 等待期间连续发 action 只启动一次；恢复完成后再次选择同一备份，空库检查拒绝且完整 Store 快照不变。 |
| Core 恢复、事务回滚和竞争写入 | `PASS` | 全量 JVM 回归包含现有 `BackupEnvelopeTest`（24）与 `PowerStoreTest`（98）；这些测试使用隔离数据库。 |
| 相关 Host/Bridge/投影回归 | `PASS` | **39/39**：`BackupRestoreHostTest` 7、`WebSkinBridgeTest` 4、`WebSkinHostProjectionTest` 23、`SemanticBackupExportIntegrationTest` 5；0 failures/errors/skips。 |
| 完整离线 Gradle 门禁 | `PASS` | Source boundary、**697/697 JVM 测试 / 93 suites / 0 failures/errors/skips**、Debug APK、AndroidTest APK 组装、Lint；Lint 0 errors / 53 warnings。完整门禁 88 actionable tasks，14 executed / 74 up-to-date。另以 `--rerun-tasks` 重跑完整 JVM 测试，32/32 tasks 执行成功。 |
| AndroidTest APK 组装 | `PASS` | `:app:assembleDebugAndroidTest` 成功；仅组装，没有在线 Android 设备或模拟器可运行 instrumentation。 |
| Skin Studio、Clear 预览与资源 | `PASS` | Python **61/61**；Chrome/CDP 检查恢复选项、空库与哈希来源说明，确认 Preview Mock 报告 `fileRead:false,databaseRead:false,databaseWritten:false`；三个相关 JS 文件 `node --check` 通过。Clear 资源包 9 files / 392,225 bytes，APK 内资源哈希与 canonical package 一致。 |
| 生产 Android WebView→Host / SAF / AlarmManager | `NOT TESTED` | Robolectric Host dispatcher 和 Preview Mock 不等同于 Android WebView、系统文件选择器、SAF provider 或 OS 闹钟服务。 |

## 可复核证据

工作区：`E:\Project\Powerful-Calendar\.local\android-test\worktrees\separation-s0-timeline-drag`。测试日志和诊断输出保存在忽略目录 `.local/android-test/reports/separation-s0-st16-backup-restore-20261030/`。

| 命令/产物 | 结果与 SHA-256 |
| --- | --- |
| `:app:testDebugUnitTest` 定向四套件，`--rerun-tasks` | PASS；39 tests / 4 suites；`focused-gradle-final.log`: `31EA811F47ADDE4EDA743EA6E8EB75A11B4FF64A54E903DF428B4B458C2C0F84` |
| `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain` | PASS；697 tests / 93 suites；`full-gradle-final.log`: `50C19576AB4E3C818A455D19A849B0897688D9EF5BC3714624D78B512900CB66` |
| `:app:testDebugUnitTest --offline --rerun-tasks --console=plain` | PASS；697 tests / 93 suites；`full-gradle-rerun-final.log`: `F921C1B9787D03AB4C8CCE9DAA27A33CCC1A614113EC2DAE7597935C05A8EBDE` |
| `python -m unittest discover -s tools/skin-studio/tests -v` | PASS；61/61；`skin-studio-python-final.log`: `29607570E89E2A514569E1AD966BEAA96D0D767303E220773E08A9EDB2202ACD` |
| `node tools/skin-studio/tests/e2e_v3_source.cjs` | PASS；包含 `BROWSER BACKUP RESTORE OPTION MOCK PASS`；`clear-source-e2e.log`: `6DD1BAA010CEC768ECB46C48BC9C96A47D4FC363C3DAB8E1C944753B1E92F534` |
| Node syntax checks | PASS；`node-check.log`: `816DEF563BE57E20DFA81A8B77DB2EC49F95152542F3A2A19DF461AF356B6B82` |
| `python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk` | PASS；9 files / 392,225 bytes；`clear-package-verify.log`: `FE8B4170606F4233B438A867F3CCA62EB12BDC32D632A1D189D733C2F6B53010` |
| Debug APK | 67,503,437 bytes；SHA-256 `F062B8B2285A0E9F8455C8E5A2D32903EB77E3D90534C7DA3E512E48763A9B0F` |
| AndroidTest APK | 1,557,196 bytes；SHA-256 `9F8C04CABD4FCA21009773FDF0B8C0BC873CC9B424F8E4F6426E0DEE4CBB5B34` |
| Canonical Clear package与 APK 内资源 | 9 files / 392,225 bytes；两者 SHA-256 均为 `753C32256AF76D5AE5CDE883A2EDF87D50D0B50E256B80B49E453F6B12DCF5B0` |

环境为 Windows、JDK 20.0.1、Gradle 8.14.3、Android SDK `E:\android-sdk`（API 36）、Robolectric API 35。`adb devices -l` 没有设备，SDK 不含 `emulator.exe`；AndroidTest APK 虽已组装，instrumentation 未执行。没有清除、恢复或改写任何用户数据库。

## 剩余缺口与交付

- 生产 Clear WebView 到 Bridge/Host 的端到端连接及 WebView 页面恢复状态仍未验证；Chrome 运行的是 Studio Preview Mock。
- 系统 OpenDocument picker、实际 URI 授权、SAF cloud/local provider 中断或空间不足、OEM 文件选择器和 AlarmManager 权限结果仍未验证。
- Activity/进程在数据库事务完成后、提醒协调前终止的真实设备恢复路径未验证；下一次启动已有 reconcile → reload 恢复路径，但此处没有设备证据。
- flat v2 的二次确认/新 lineage 属于 S0-ST-17，保留单独验收，未用本 Issue 的 v1 结果代替。
- SHA-256 是 payload 完整性检查，不是文件来源认证。用户应只选择信任来源的备份；恢复仍仅允许进入空数据库。

实现提交 `a145463fc75bbc9910dcd0bb2cba850ad6a12069` 与 `8efff1f`（Web 编辑器守卫及回归用例）均已推送。Host/Preview 自动化覆盖通过，但真实 Android 环节未测，所以 S0-ST-16 保持 **`PARTIAL / HOLD`**，不作完整 parity 或 Separation GO 签核。
