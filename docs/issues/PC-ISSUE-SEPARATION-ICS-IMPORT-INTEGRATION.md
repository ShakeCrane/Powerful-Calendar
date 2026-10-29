# PC-ISSUE-SEPARATION-ICS-IMPORT-INTEGRATION

**日期：** 2026-10-30

**状态：** `PARTIAL / HOLD`

**验收项：** reconstructed `S0-ST-13`

**代码/测试提交：** `5e20d14`、`4bdd135`（均已推送到 `origin/codex/separation-s0-timeline-drag`）

## 范围与结论

本 Issue 补足 Clear Host 文件导入链的集成证据，覆盖月历语义选项 `calendar.import.file`、AI 文件附件、ICS 本地解析、用户待处理草稿、重复导入去重、恢复收据，以及 2,000,000 字符输入上限。

API 35 Robolectric 中，生产 `PowerCalApp` 和 Web Host Bridge 的月历文件选项把 ICS 文件交给 Android 文档选择契约；两个有效事件成为 `CREATE / PENDING` 用户草稿，没有正式事件或提交记录。随后从 AI `PickAiFile` 附件入口重导同一文件，草稿、身份计数和事件序号均不增加，现有导入收据只保留一条。超出上限的输入不会写入 Store、事件序号或收据。测试未配置 AI 服务，ICS 由本地路径处理。

本轮没有复现生产逻辑缺陷；原有风险是 parser、receipt 的独立测试没有经过真实 Host 入口验证路由与持久化组合。当前结论只签核上述 Host/Robolectric 子集，不构成完整的 Native/Clear parity 或 S0-ST-13 全项签核。

## 调用链与根因

1. Clear 月历 `calendar.import.file` 与 AI 附件 `PickAiFile` 都由 Host 打开文档选择器。AI 附件路径先按 MIME、文件名 `.ics` 后缀或 `BEGIN:VCALENDAR` magic 分类；日历文件在 AI 文本提取、网络请求和授权流程之前进入本地 `importIcs`。
2. `importIcs` 将输入限制为最多 2,000,000 个解码字符，再调用 `IcsImportPlanner`。候选项与正式事件及待处理 CREATE 草稿去重，写入用户草稿和 `ImportCheckpoint`，不自动发布正式事件。
3. 原有 parser/checkpoint 单元测试没有覆盖从两个 Host 入口到数据库、收据和事件序号的完整路径，也没有 Host 级超限拒绝回归。

本轮只新增/调整测试，没有改动生产业务代码。

## 修改内容

- 在 `WebAiAttachmentFlowIntegrationTest` 中通过生产 `PowerCalApp` 与 Bridge 执行 Clear 月历 `calendar.import.file`，验证 SAF `ACTION_OPEN_DOCUMENT` 契约并导入两项活动。
- 从 AI 附件 `PickAiFile` 入口重导相同 URI，验证两个 Host 入口共享本地 ICS 路径及幂等结果。
- 断言草稿是 `CREATE / PENDING`、来源为 `ics`、所有者为用户；正式事件数和提交记录为零；收据包含相同文件指纹和两个草稿 ID。
- 新增 2,000,001 个 ASCII 字符的超限输入用例，断言解析失败后草稿、正式事件、身份数、提交数、事件序号及收据均未变化。
- 清理随机测试 namespace 对应的 `powercal-import-checkpoints` 偏好，避免测试数据泄漏到后续用例。

## 验收记录

| 场景 | 结果 | 证据边界 |
| --- | --- | --- |
| Clear 月历 `calendar.import.file` Host 选项打开系统文档选择契约 | `PASS` | API 35 Robolectric 的 `PowerCalApp`/Bridge；验证 `ACTION_OPEN_DOCUMENT`，没有实际 Android chooser UI |
| 导入两个独立 ICS 活动 | `PASS` | 两张用户 `CREATE / PENDING` 草稿，指纹收据映射两张草稿；无正式事件、无提交记录 |
| 同一 ICS 从 AI 附件入口再次导入 | `PASS` | 原草稿、身份计数和事件序号不变；相同文件只保留一条收据 |
| 2,000,001 字符文件超过上限 | `PASS` | 无 Store/事件序号/收据写入；只测试 ASCII 字符超限路径 |
| ICS planner 与 receipt 现有回归 | `PASS` | `IcsImportPlannerTest` 8/8、`ImportCheckpointTest` 1/1 |
| 相关 Host/Bridge suites | `PASS` | 39/39：附件集成 6、planner 8、checkpoint 1、Bridge 4、Host projection 20 |
| Native Tools 专用 ICS 可见入口和 Native/Clear 完整 parity | `NOT TESTED` | 当前矩阵记录 `NativeToolsPage` 没有可见 `onImportIcs` 行；本测试不声称 Native 独立行等价 |
| Android WebView、真实 SAF chooser/授权、设备或 OEM 行为 | `NOT TESTED` | 没有在线 ADB 设备；SDK 不含 `emulator.exe`；AndroidTest 仅编译打包 |

## 定向测试

```text
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.ui.v2.WebAiAttachmentFlowIntegrationTest.clearMonthFileImportAndAiAttachmentShareLocalIcsImportAndIdempotence' --tests 'dev.powerfulcalendar.ui.v2.WebAiAttachmentFlowIntegrationTest.skinPickedOversizedIcsIsRejectedBeforeAnyCalendarPersistence' --offline --console=plain
```

结果：**2/2 PASS**。日志 `.local/android-test/reports/separation-s0-st13-ics-import-20261030/focused-final.log`，SHA-256 `E5D821051DD359FF7ECFEC0E4A7141EEDF00DCBB25DEFFC73B27D3D5E10AA9C7`。

相关 suites 命令：

```text
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.ui.v2.WebAiAttachmentFlowIntegrationTest' --tests 'dev.powerfulcalendar.interop.IcsImportPlannerTest' --tests 'dev.powerfulcalendar.interop.ImportCheckpointTest' --tests 'dev.powerfulcalendar.ui.v2.WebSkinBridgeTest' --tests 'dev.powerfulcalendar.ui.v2.WebSkinHostProjectionTest' --offline --console=plain
```

结果：**39/39 PASS**。日志 `.local/android-test/reports/separation-s0-st13-ics-import-20261030/related-final.log`，SHA-256 `FBCED235E5B96CD0BE6F8AE1F41E166F086E38880099ED3EAB73606A7C3E6E9E`。

最终相关 JUnit XML SHA-256：

- `WebAiAttachmentFlowIntegrationTest.xml`：`500AE6A9C098CFEE11EA23F7C86C9E1CB45B265BB0ED1D8ED10DB90E1ABF591C`
- `IcsImportPlannerTest.xml`：`780F7A8DFE407134574C8CD81D62D548217BCB58604AA69008A0CF4A3F8D6985`
- `ImportCheckpointTest.xml`：`6D9008E3382C63B8F362FD09713B17F29E28C45A7A608E957BDF7EE6032F5C66`
- `WebSkinBridgeTest.xml`：`C02462AA4EE6CEAD28F8C13A4FDA0118B5BD11A3394617A301E9ED8257990A48`
- `WebSkinHostProjectionTest.xml`：`CC0A3178759E37CC602A94ED27D935E3B2F0A574513643EB89C82ABC065A18A3`

## 完整离线 Gradle 门禁

```text
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
```

结果：**BUILD SUCCESSFUL**；源码边界验证通过；JVM **679/679**、**90 suites**、0 failures / errors / skipped；Debug APK 与 AndroidTest APK 均编译/打包成功；Lint **0 errors、53 warnings**。执行 88 个 Gradle tasks。完整日志 `.local/android-test/reports/separation-s0-st13-ics-import-20261030/full-gradle-final.log`，SHA-256 `D3DE37F5B3E0C9BBE71B0BC3CB3BEE78938E83B7780F92252C6EFA98FE57D706`。

| 产物 | 大小 | SHA-256 |
| --- | ---: | --- |
| Debug APK | 67,500,112 bytes | `7E1FCEA722A7FC86F9792794B3560E24F5B448AE1BA2F559D54F0460315222F9` |
| AndroidTest APK | 1,557,196 bytes | `9F8C04CABD4FCA21009773FDF0B8C0BC873CC9B424F8E4F6426E0DEE4CBB5B34` |

环境：Windows PowerShell、Android SDK `E:\android-sdk`、API 35 Robolectric。`adb devices -l` 无在线设备，`E:\android-sdk\emulator\emulator.exe` 不存在；未运行 Android instrumentation、Android WebView、真机或 OEM 测试。没有截图，因为本次只签核 Host 路由与 Store 状态，不声称视觉/系统选择器行为。

## 剩余风险与状态

- 当前实现的阈值是 **2,000,000 个解码字符**，不是已证明的 2 MiB 字节上限。本次只验证了 2,000,001 个 ASCII 字符被拒绝；恰好上限、多字节文本占用、provider 中断/读取异常的端到端 UI 恢复没有测试。
- `NativeToolsPage` 的直接 `onImportIcs` 可见入口以及其与 Clear Month 选项的交互 parity 未签核。Clear 月历与 AI 附件的 Host route 已由 Robolectric 覆盖，但真实 WebView/设备 file chooser 尚未执行。
- 本报告不等价于外部日历客户端导入/再导出、系统日历 provider 权限行为或 S0 分离 GO。

因此，`S0-ST-13` 只在“Clear Host ICS 文件路径的本地导入、去重、草稿收据和超限无副作用”子集上有 `PASS` 证据；整行保持 **`PARTIAL / HOLD`**。
