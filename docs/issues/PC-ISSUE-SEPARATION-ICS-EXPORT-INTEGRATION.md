# PC-ISSUE-SEPARATION-ICS-EXPORT-INTEGRATION

- **日期：** 2026-10-30
- **状态：** `PARTIAL / HOLD`
- **验收项：** reconstructed `S0-ST-14`
- **代码提交：** `96290186fa8ab097cf432361e3f654c82339b64c`、`19b631b83f1d1d136607b3d2e36defdf341339a0`（均已推送到 `origin/codex/separation-s0-timeline-drag`）

## 结论

Clear 月历的数据选项现可通过 `calendar.export.ics` 请求既有 Android Host ICS 导出入口。Host 仍使用 Android `CreateDocument` 和 `IcsCodec.exportActivities`；没有更改 ICS 序列化、Store 或核心事件语义。Native Tools、Clear 选项和导出成功提示均说明 ICS 只包含活动，并披露活动的启用/停用状态、活动提醒、闹钟、截止、备忘和完成状态不会被完整保留；完整恢复须使用原生备份。

独立复核发现 `TemporalRecord.enabled` 不写入 ICS，且初始提示漏报了该损失。后续提交 `19b631b` 补齐三个用户提示，并增加投影、Compose 成功提示及 Chrome Mock 的断言。集成测试中的活动记录设为 `enabled=false`，仍会出现在导出的 ICS 活动中；因此该字段不能通过本格式恢复。

本 Issue 只签核 API 35 Robolectric Host 路由、Clear 浏览器预览和本地 Store 不变的自动化子集。浏览器预览使用非写入 Mock；Robolectric 直接调用生产 Host action。两者都没有验证生产 Android WebView 到 Host 的真实连通，也没有打开系统 SAF 文件选择器。因此 `S0-ST-14` 保持 `PARTIAL / HOLD`，不代表设备级功能等价或第三方日历兼容性通过。

## 根因与修复

1. Clear 投影的 `SemanticOptions` 原来只有日历导入选项，没有 ICS 导出选项；Host action 路由也没有把 Clear 导出请求接到已有的 `CreateDocument` 启动器。
2. ICS 编码器只构造标准 `CalendarEvent` 数据；不会序列化 PowerCal 的 `enabled`、提醒意图或非活动类型。原有 Native 提示没有完整披露活动提醒、Memo、完成及活动启用状态的损失。
3. `calendar.export.ics` 现在作为闭合 Host 选项投影。`PowerCalApp` 将它路由到原有 `CreateDocument("text/calendar")` 与 `IcsCodec.exportActivities`，Clear 将选项说明作为可见副文案显示。浏览器 Studio Mock 只记录 Host 请求，不创建文件或读取日历。
4. Native Tools 文案、Clear 选项与成功结果均明确披露损失。取消选择器和输出异常仍由既有结果处理路径显示状态，不写 Store。

## 修改内容

- `SemanticOptions.kt`：注册 `calendar.export.ics` 并列出完整语义损失提示。
- `PowerCalApp.kt`：接入既有 Android 导出启动器；补全成功提示和 Native Tools 的导出说明，没有改动 `IcsCodec`。
- `SemanticIcsExportIntegrationTest.kt`：通过生产 `PowerCalApp` 和 Activity Result 注册器覆盖导出、取消、输出失败；断言 ICS 只含活动、没有 `VALARM`，并对比记录、历史、草稿数、身份数、事件序号与提交数。测试种入一条 `enabled=false`、有提醒配置的活动，以及 Alarm、Deadline 和 Memo。
- `WebSkinBridgeTest.kt`、`WebSkinHostProjectionTest.kt`：覆盖闭合 action 解码、Host 选项注册和损失提示字段。
- Clear 源码与 Skin Studio Preview：显示选项副文案；Studio Mock 明确声明不读真实日历、不创建文件；Chrome/CDP E2E 检查选项、损失提示和非写入行为。
- `powercal-clear-default.pcskin`：验证其与 canonical Clear 源一致；本次新增文案由 Host 投影，官方 Clear 包装无漂移。

## 验收证据

| 场景 | 结果 | 证据边界 |
| --- | --- | --- |
| Clear Host ICS 请求启动系统文档写入契约 | `PASS` | API 35 Robolectric 记录 `ACTION_CREATE_DOCUMENT`、`text/calendar` 和文件名；不是实际系统 SAF UI |
| 导出的数据范围及无副作用 | `PASS` | 四种类型的隔离 Store 夹具导出后，内部解析只有一条 Activity；无 `VALARM`。记录、历史、草稿、身份、序号及提交计数不变。被导出的活动原始 `enabled=false`，其状态未由 ICS 表示 |
| 取消选择器、输出流失败 | `PASS` | 取消不写输出；注入输出异常显示错误；两者均不改变 Store、身份、序号和提交数 |
| Clear 选项与语义损失说明 | `PASS` | Chrome 156/CDP 检查 DOM 中选项与活动启用/停用状态、提醒、其他类型、完成状态和原生备份说明；Mock 收到 action，确认未创建文件且未读取真实日历 |
| 定向 Android JVM 回归 | `PASS` | 34/34：集成 3、Bridge 4、Host projection 21、ICS recurrence 6；0 failure/error/skipped |
| Skin Studio Python 回归 | `PASS` | 61/61 |
| JS 语法及 canonical Clear/APK 同源 | `PASS` | 三个 `node --check` 通过；canonical package 和 APK 内资源 SHA 相同 |
| 完整离线 Gradle 门禁 | `PASS` | Source boundary、683/683 JVM（91 suites，0 failures/errors/skips）、Debug/AndroidTest APK assembly；Lint 0 errors、53 warnings。88 tasks，13 executed、75 up-to-date |
| Android WebView → 生产 Host、Android SAF/OEM 与第三方日历 | `NOT TESTED` | 无在线设备，SDK 没有 `emulator.exe`；AndroidTest APK 仅编译。Preview Mock 和 Robolectric 分别验证两端，不能视作真实 WebView E2E |

### 定向命令与日志

定向回归：

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.ui.v2.SemanticIcsExportIntegrationTest' --tests 'dev.powerfulcalendar.ui.v2.WebSkinBridgeTest' --tests 'dev.powerfulcalendar.ui.v2.WebSkinHostProjectionTest' --tests 'dev.powerfulcalendar.interop.IcsExportRecurrenceTest' --offline --console=plain
```

结果：**34/34 PASS**。日志 `.local/android-test/reports/separation-s0-st14-ics-export-20261030/followup-focused-final.log`，SHA-256 `C24B2D8D1DF3068B8E3459B3FFF84763A63408DF6022DA35B614CBC0B52C2C93`。

聚焦 JUnit XML：

- `SemanticIcsExportIntegrationTest.xml`（3）：`033499D2B6C8E0E1DDE109A2BE5189C99964AC679FE147313EEB0F7E4621BE3A`
- `WebSkinBridgeTest.xml`（4）：`3D82708B32894476B25811911618A6D3D22FE042F84F89682EB0A44A3B1E99F2`
- `WebSkinHostProjectionTest.xml`（21）：`AAB0387F2383B763548E1B8AFDA91A15454512A060DD6140AC539CCD9C04FCD9`
- `IcsExportRecurrenceTest.xml`（6）：`1690D16C8478CE061F875EF830C99A8D54AE76926B6BB158FF7CE12FFAA38FBE`

Skin Studio Python：`python -m unittest discover -s tools/skin-studio/tests -v`，**61/61 PASS**。日志 `.local/android-test/reports/separation-s0-st14-ics-export-20261030/followup-python-final.log`，SHA-256 `54972E1A6D75B5BB700D86ADEE57C8396B5AF538A906A783D745B80494319E93`。

Chrome/CDP：`node tools/skin-studio/tests/e2e_v3_source.cjs`，完整 E2E 通过；新增断言结果为 `BROWSER ICS EXPORT OPTION MOCK PASS {visible:true,lossWarningVisible:true,clicked:true}`，Mock 收到 Host 请求且 `fileCreated:false, calendarRead:false`。日志 `.local/android-test/reports/separation-s0-st14-ics-export-20261030/followup-e2e-final.log`，SHA-256 `CC7587FCF63C2259C96F50316CCB20DBF1C710B5936AC6503E5FBA6E4364A3D5`。`node --check` 对 Clear `app.js`、`v3-studio.js`、E2E 脚本均通过。

官方 Clear 包验证：9 files / 392,225 bytes，package 与 APK 内资源 SHA-256 均为 `753C32256AF76D5AE5CDE883A2EDF87D50D0B50E256B80B49E453F6B12DCF5B0`。

### 完整 Gradle 门禁及构建产物

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
```

结果：`BUILD SUCCESSFUL`；683/683 JVM、91 suites、0 failures/errors/skips；88 actionable tasks（13 executed，75 up-to-date）；Lint 0 errors / 53 warnings。完整日志 `.local/android-test/reports/separation-s0-st14-ics-export-20261030/followup-full-gradle-final.log`，SHA-256 `0EBF7501BD313D153A959A89F98978148267C2752C5A167583776FD7EB7E986A`。

| 产物 | 大小 | SHA-256 |
| --- | ---: | --- |
| Debug APK | 67,500,603 bytes | `9BABEB71B864AD5E04ED5A342312F93AB726B6BDEDC1FA24D4EA43BCA5AB25DE` |
| AndroidTest APK | 1,557,196 bytes | `9F8C04CABD4FCA21009773FDF0B8C0BC873CC9B424F8E4F6426E0DEE4CBB5B34` |

## 环境、偏差与未解决风险

- 环境：Windows 11、JDK 20.0.1、Gradle 8.14.3、Android SDK `E:\android-sdk`、API 35 Robolectric、Chrome 156/CDP。Worktree 没有 `local.properties`；补充回归第一次未给 Gradle 子进程设置 SDK 路径，在解析测试依赖前退出（非测试失败）。随后为命令设置 `$env:ANDROID_HOME='E:\android-sdk'` 并成功重跑。启动失败原始日志 `.local/android-test/reports/separation-s0-st14-ics-export-20261030/followup-focused-sdk-config-missing.log`，SHA-256 `6DBD29AA65A4E027A89665774C4848D708FA4CB607FE876BD56C16064AEBEA4C`。
- `adb devices -l` 没有连接设备，`E:\android-sdk\emulator\emulator.exe` 不存在。AndroidTest APK 构建成功，但没有运行 instrumentation、生产 Android WebView、真实系统 SAF chooser、实体设备/OEM 测试；实体设备由用户验收。
- Chrome/CDP 使用 Studio Preview Mock，不访问真实数据库或文件；Robolectric 测试直接向生产 Host action 分发请求。没有真实 WebView 到 Host 的合并链路证据。
- 导出的 ICS 使用 PowerCal 自身 parser 做内部解析，不构成 Apple/Google/Outlook 或其他第三方客户端兼容性证明。活动启用状态、活动提醒及其他非 ICS 语义必须依赖完整原生备份恢复。
- 首次漏报的启用状态问题已由 follow-up 提示和回归断言修复；没有修改核心 `IcsCodec`。上述未测试项使该验收行仍为 `PARTIAL / HOLD`。

## Git 交付

- `96290186fa8ab097cf432361e3f654c82339b64c` — `feat(分离): 提供 Clear Host ICS 导出入口`
- `19b631b83f1d1d136607b3d2e36defdf341339a0` — `fix(分离): 补充 ICS 活动状态损失提示`
- 两个提交均已推送到 `origin/codex/separation-s0-timeline-drag`。本 Issue 报告与矩阵/进度更新随后单独提交。

## 签核状态

本地 Host/Store/Preview Mock 自动化范围通过；实际设备的 Clear WebView、SAF provider/OEM 行为、Native/Clear 同机配对复演和第三方客户端互操作未测试。故 `S0-ST-14` 保持 **`PARTIAL / HOLD`**，不签核完整 Native/Clear parity 或 separation GO。
