# PC-ISSUE-SEPARATION-BACKUP-EXPORT-INTEGRATION

- **日期：** 2026-10-30
- **状态：** `PARTIAL / HOLD`
- **验收项：** reconstructed `S0-ST-15`
- **实现提交：** `04873621aa3881d72c9b113d618d3b2d282be9af`，已推送到 `origin/codex/separation-s0-timeline-drag`

## 结论

Clear 月历的数据选项现提供 `data.backup.export`。它只请求既有 Android Host 的 `CreateDocument("application/json")` launcher；Host 继续调用 `PowerStore.exportBackup()`、写入所选 SAF URI，再通过 `SafBackupReader` 的 64 MiB 有界读取和 `BackupEnvelope.verifyReadback` 决定是否报告哈希校验成功。没有在 WebView 中实现第二套存储、备份编解码或文件读写逻辑。

API 35 Robolectric 覆盖 Host action 到 Activity Result 写入和回读的路径。成功时输出 Envelope v1，与 Store 导出快照的逻辑 payload 相同；取消、输出异常、回读不可用及回读水位不匹配均不会报告“已回读校验”。全程数据库记录、历史、草稿、身份、序号和提交计数保持不变。Chrome 156/CDP 另验证 Clear 展示敏感数据与未验证警告，并把动作送到明确不读写文件或数据库的 Studio Preview Mock。

因此，本地 Host/Preview 自动化切片通过。它不覆盖生产 Android WebView→Host 的真实连接、系统 SAF/provider 行为或设备恢复，故 `S0-ST-15` 整行保持 `PARTIAL / HOLD`。

## 调用链、根因与修复

1. Native Tools 已有“导出原生备份”入口：`onExportBackup` → `CreateDocument("application/json")` → `store.exportBackup()` → UTF-8 写入 → `readBackupText()` 有界回读 → `BackupEnvelope.verifyReadback()`。不能回读时提示“已写入但未经回读校验”；写入或校验异常显示失败。
2. `PowerStore.exportBackup()` 在一个 SQLite 读快照中读取记录、身份/墓碑、草稿、提交、来源链接、迁移记录、AI 任务收据和 `nextSeq` / `nextOrder`，然后创建带 canonical payload SHA-256 的 Envelope v1。相关格式、密钥排除、边界和回读比较已有 `BackupEnvelopeTest` 与 `PowerStoreTest` 覆盖。
3. Clear v3 原来没有 Host 投影选项或 `InvokeOption` 到该 launcher 的分发；已有 Core 测试并未验证 Clear Host 触发到文档写入和回读的 UI/Host 组合。
4. 新增 `data.backup.export` 闭合语义选项，并直接复用 Native 的 `createBackup` launcher。清单文案说明快照、哈希、包含内容、密钥排除、明文敏感数据和无法回读时的未验证状态。Studio Mock 只记下 Host 请求，不访问真实数据。

## 修改内容

- [SemanticOptions.kt](../../app/src/main/java/dev/powerfulcalendar/ui/v2/SemanticOptions.kt)：增加 `data.backup.export` 以及完整性和隐私说明。
- [PowerCalApp.kt](../../app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt)：把闭合 Host action 接到已有 `createBackup.launch("powercal-backup.json")`。
- [SemanticBackupExportIntegrationTest.kt](../../app/src/test/java/dev/powerfulcalendar/ui/v2/SemanticBackupExportIntegrationTest.kt)：新增 API 35 集成覆盖：验证 MIME/文件名、有效 Envelope 与 payload 回读、取消、写入失败、回读不可用、水位不匹配，以及所有场景的 Store 不变。
- `WebSkinBridgeTest`、`WebSkinHostProjectionTest`：验证 action 解码和数据/隐私提示。
- `v3-studio.js` 与 `e2e_v3_source.cjs`：Studio 预览加入非写入 Mock；Chrome 检查选项说明可见并确认没有创建文件、读取数据库或写数据库。
- [STABLE_DATA_CONTRACT_V1.md](../STABLE_DATA_CONTRACT_V1.md)：将原先描述 flat v2 的旧“目前代码实际状态”标为历史快照，并补充 2026-10-30 当前实现事实及本 Issue 的证据链接；未改变备份格式或行为合同。

## 验收证据

| 场景 | 结果 | 证据边界 |
| --- | --- | --- |
| Clear action 到文档创建契约 | `PASS` | Robolectric 通过生产 `PowerCalApp` Host action，记录 `ACTION_CREATE_DOCUMENT`、`application/json`、`powercal-backup.json`；没有实际系统 picker UI |
| 成功写入和回读 | `PASS` | 输出可由 `BackupEnvelope.verify` 验证；Envelope v1 格式、正式对象与待处理草稿 payload 和预期快照一致；生产回调显示“SHA-256 匹配” |
| 取消 | `PASS` | Activity Result 返回 null；Store 状态不变 |
| 输出失败 | `PASS` | 注入输出流异常，显示备份失败，不显示成功；Store 状态不变 |
| Provider 不支持回读 | `PASS` | 写入后注入回读异常，显示“已写入但未经回读校验”，不报告 SHA-256 成功 |
| 回读水位不匹配 | `PASS` | Provider 返回内部 SHA 有效但 `nextSeq` 被改动的 Envelope；`verifyReadback` 拒绝并显示失败，不报告成功 |
| Store 无副作用 | `PASS` | 以上路径均比较 formal records、history、draft count、identity count、sequence preview、commit count；没有变化 |
| Host/格式回归 | `PASS` | 153/153：新集成 5、Bridge 4、Host projection 22、`BackupEnvelopeTest` 24、`PowerStoreTest` 98；0 failure/error/skipped |
| Clear 浏览器预览 | `PASS` | Chrome 156/CDP 检查全部敏感/完整性文案；Preview Mock 收到 action 且 `fileCreated:false,databaseRead:false,databaseWritten:false` |
| 完整离线 Gradle 门禁 | `PASS` | Source boundary、689/689 JVM（92 suites，0 failures/errors/skips）、Debug/AndroidTest APK assembly；Lint 0 errors、53 warnings；88 tasks，12 executed、76 up-to-date |
| 真实 Android WebView、SAF/provider、设备恢复 | `NOT TESTED` | `adb devices -l` 无在线设备，SDK 不含 `emulator.exe`；AndroidTest APK 仅编译。实体设备由用户测试 |

### 定向回归

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.ui.v2.SemanticBackupExportIntegrationTest' --tests 'dev.powerfulcalendar.ui.v2.WebSkinBridgeTest' --tests 'dev.powerfulcalendar.ui.v2.WebSkinHostProjectionTest' --tests 'dev.powerfulcalendar.core.BackupEnvelopeTest' --tests 'dev.powerfulcalendar.core.PowerStoreTest' --offline --console=plain
```

结果：**153/153 PASS**。日志 `.local/android-test/reports/separation-s0-st15-backup-export-20261030/focused-final.log`，SHA-256 `9E4D359C9D4662BC955A20412A87F57E793430D23216A1485F98861EF609E08B`。

最终 JUnit XML：

- `SemanticBackupExportIntegrationTest.xml`（5）：`3D70C72C75FCCDA2BB906D170CE74F1476B1D185F8F4B69901F6F42403EEBF43`
- `WebSkinBridgeTest.xml`（4）：`2B71C5DD3DB6FD63416FEAD642F002C70670BBA7E73C9265B6778D6505F57F55`
- `WebSkinHostProjectionTest.xml`（22）：`B125E7C8282C211E403360DE9171DAC2B85C40BBB592AA6DF40C6180CA45A01C`
- `BackupEnvelopeTest.xml`（24）：`4DA81A1C89186BAAFC4605FD66432A36D4E061F909A21E0730D399D558222E7D`
- `PowerStoreTest.xml`（98）：`A18BE6F79897DECB75BC5E0E62A7447F6F3B1CEDD1BCDB6112B268747C3738B3`

### Studio、浏览器与资源验证

- `python -m unittest discover -s tools/skin-studio/tests -v`：**61/61 PASS**。最终树复跑日志 `.local/android-test/reports/separation-s0-st15-backup-export-20261030/skin-studio-python-final.log`，SHA-256 `4E31C3B96AFA53D3D5EE60B8F59819E596C8909B56DAF12707078AA7F8B92622`。
- `node tools/skin-studio/tests/e2e_v3_source.cjs`：完整 Chrome/CDP E2E 通过；包含 `BROWSER BACKUP EXPORT OPTION MOCK PASS`。日志 `.local/android-test/reports/separation-s0-st15-backup-export-20261030/clear-source-e2e-final.log`，SHA-256 `8C5055F7F68AEC9B26715D34E5EDE6BF8BA3F269A3EC5EE760B45668FC151577`。
- `node --check`：Clear `app.js`、`v3-studio.js`、`e2e_v3_source.cjs` 均通过。
- `python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk`：9 files / 392,225 bytes；package 与 APK 内 Clear 资源 SHA-256 一致，均为 `753C32256AF76D5AE5CDE883A2EDF87D50D0B50E256B80B49E453F6B12DCF5B0`。

### 完整 Gradle 门禁与产物

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
```

结果：`BUILD SUCCESSFUL`；689/689 JVM、92 suites、0 failures/errors/skips；88 actionable tasks（12 executed，76 up-to-date）；Lint 0 errors / 53 warnings。日志 `.local/android-test/reports/separation-s0-st15-backup-export-20261030/full-gradle-final.log`，SHA-256 `540B048371630AAC80CCB8F74552B6535684B70A18A8651FFFFCED171755931A`。

| 产物 | 大小 | SHA-256 |
| --- | ---: | --- |
| Debug APK | 67,500,804 bytes | `CC46A8B967571B269051C1668F8C043B2BB0FCA8CEECBF46E74A2069EC84BC67` |
| AndroidTest APK | 1,557,196 bytes | `9F8C04CABD4FCA21009773FDF0B8C0BC873CC9B424F8E4F6426E0DEE4CBB5B34` |

## 环境和偏差

- Windows 11、JDK 20.0.1、Gradle 8.14.3、Android SDK `E:\android-sdk`、API 35 Robolectric、Chrome 156/CDP。Worktree 没有 `local.properties`；运行 Gradle 时显式设置 `$env:ANDROID_HOME`。
- 初次测试夹具迭代有两条失败日志，均在测试实际逻辑通过前定位并修复：`focused-first.log` 为测试状态快照字段把 Store API 的 `Long` 错写为 `Int`（SHA-256 `347934B3507295E0BD6CE07560FB1829C3C94E78C745E79D2066420451E29C86`）；`focused-windows-path-fail.log` 为单个测试方法名过长导致 Windows Robolectric 临时数据库路径无法创建，已缩短测试名（SHA-256 `2B2D2B18D32D25A23D05615E998B28512233879F5E59C23650D457FBDB4DFAA6`）。修正后的定向和完整测试均通过。
- 未连接 ADB 设备，SDK 没有 Emulator。没有运行 Android instrumentation、生产 WebView、真实 SAF chooser/provider、OEM 或物理设备测试。Chrome Preview Mock 与 Robolectric Host 分别验证两端，不等于真实 WebView E2E。
- 当目标 provider 不支持回读，代码会如实报告写入未经验证；SAF 云端 provider 的部分写入、空间耗尽和中断行为未在实际 provider 上验证。导出文件是明文敏感数据，UI 提示用户自行保管；备份不含 API 密钥或旧设备的调度句柄/AI 授权。

## Git 交付与签核

- 实现提交：`04873621aa3881d72c9b113d618d3b2d282be9af` — `feat(分离): 暴露 Clear 原生备份导出`，已推送至 `origin/codex/separation-s0-timeline-drag`。
- `S0-ST-15` 的 Host/Clear Preview 自动化子集有证据通过；真实 WebView→Host、实际 SAF/provider/OEM 和设备恢复未测试。整行保持 **`PARTIAL / HOLD`**，不签核完整 parity 或 separation GO。
