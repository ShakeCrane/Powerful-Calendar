# PC-ISSUE-SEPARATION-MEMO-OCCURRENCE-TOGGLE — Memo 重复实例完成状态隔离

日期：2026-10-29
验收项：`S0-ME-02`；父项 `SEP-03`
状态：Host/JVM 与 Chrome Studio Mock 回归 **PASS**；Android instrumentation **COMPILED / NOT TESTED**；完整 Native/Clear parity **NOT TESTED**。

## 结论

Memo 完成状态按正式事件 owner、revision 和 occurrence key 重新验证。Host 在执行前重新读取事件，并确认版本和该 occurrence 的当前完成值；有效切换只提交一次并刷新投影。JVM 回归验证了此生产调用路径、陈旧动作拒绝及 Store 重开后的持久性。Clear 包在 Studio Preview Mock 中验证了实际列表按钮、单槽位变化、owner revision 更新和过期请求拒绝。

本轮没有发现需要修改的 Host 持久化实现缺陷。实际 Android WebView instrumentation 已新增并编译，但设备不可用，不能据此签核 Android 端到端或完整 Native/Clear parity。`S0-ME-02` 与 `SEP-03` 继续 **NOT TESTED / unsigned**。

## 根因与工作对应

| 发现 | 修改 | 证据 |
| --- | --- | --- |
| 既有 JVM 覆盖没有把重复 Memo 的选中槽位、owner revision、重复动作及进程重开串成一次生产路径回归。 | 增加 PowerCalApp Compose/Robolectric 集成测试：从真实 Store 创建每日重复 Memo，通过 Host bridge 切换指定 key；检查相邻 key 不变、revision/commit 只增一次、旧请求无副作用、重开 Store 后状态保留。 | `webMemoToggleIsOccurrenceScopedRevisionGuardedAndStableAcrossRestart`，1/1 PASS；被覆盖的是 Host/Store 路径，不是 Android WebView。 |
| Studio Preview Mock 使用单一完成布尔值，无法模拟同一正式事件的多个 occurrence，也没有 owner revision 语义。 | Mock 改为按 `eventSeq:occurrenceKey` 记录完成状态，revision 按 owner 更新；严格匹配 `eventSeq`、`expectedRevision`、key 与期望完成状态，拒绝过期/不匹配 action。 | Chrome 156/CDP 对规范 Clear 包执行按钮交互并检查槽位隔离、可访问名称及两类 stale replay。 |
| 缺少真实 Clear WebView 对按钮、Host bridge、revision guard 和恢复持久性的端到端 instrumentation。 | 增加 Android instrumentation 测试：打开 Clear Memo 列表、点击指定 occurrence、检查相邻槽位、revision、commit 数、无障碍名称、陈旧请求及 Store 重开。 | AndroidTest Kotlin 编译及 APK 生成 PASS；没有设备执行，结果严格标为 NOT TESTED。 |

## 修改文件

- `app/src/test/java/dev/powerfulcalendar/ui/v2/PowerCalCasualSuggestionComposeTest.kt`：生产 Host/Store completion path 的 occurrence、revision、replay 和 restart 回归。
- `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinRecurrenceInstrumentedTest.kt`：真实 Clear Android WebView instrumentation 回归；只编译，未运行。
- `tools/skin-studio/v3-studio.js`：Preview Mock 改为 occurrence-scoped completion 和 owner revision。
- `tools/skin-studio/tests/e2e_v3_memo_occurrence_toggle.cjs`：Chrome/CDP 回归脚本，导入 APK 中规范 Clear `.pcskin`。

生产 Host action 与 Store 代码没有修改；没有添加正式事件、草稿或重复 occurrence 数据到用户数据库。Studio scenario 与 JVM/instrumentation 使用隔离测试数据。

## 测试与证据

环境：Windows PowerShell；Gradle Wrapper 8.14.3；JDK 20；Android SDK `E:\android-sdk`（平台 `android-36`）；Python 3.11.4；Node.js 24.19.0；Chrome 156。Gradle 所用 SDK 路径仅设为本次进程环境变量，没有写入工作区 `local.properties`。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 定向 Compose/Host 回归：`:app:testDebugUnitTest --tests 'dev.powerfulcalendar.ui.v2.PowerCalCasualSuggestionComposeTest.webMemoToggleIsOccurrenceScopedRevisionGuardedAndStableAcrossRestart'` | **1/1 PASS**。选中 occurrence 从未完成变为完成；相邻 recurrence slot 保持未完成；owner revision 和 commit 各增加一次；旧 revision replay 不会二次切换；关闭并重开 Store 后状态一致。 | `.local/android-test/reports/separation-s0-timeline-drag/memo-occurrence-toggle-cdp/focused-memo-toggle-build-20261029.log` SHA-256 `370CFDCB26FF5A457C741AB71F1691B49F819DC0F7AFBCFC4640D927C8F66148`。 |
| AndroidTest 编译与打包：`:app:assembleDebugAndroidTest --offline --console=plain` | **BUILD SUCCESSFUL**；新增 instrumentation 已编译并打入 APK。没有执行 instrumentation。 | 编译日志 `androidtest-compile-20261029.log` SHA-256 `7709B934330BFB76080A632614FA0A7DB73ED57CB27EBBEB072FC3AD779D1E06`；打包日志 `androidtest-assemble-20261029.log` SHA-256 `564B4D95198ECA2C06F8964810A7A97C4D94D067014456CA1C0F1E2327F5F1AE`；APK SHA-256 `ACCB3EF7F7B6E5B2C83CD230A9A639BA918D793962C9BD75B988326CE73FC472`。 |
| Chrome 156/CDP：`node tools/skin-studio/tests/e2e_v3_memo_occurrence_toggle.cjs` | **PASS（Studio Preview Mock）**。规范包 `powercal-clear-default` 共 9 个文件；切换 `eventSeq=31 / revision=2 / 2026-10-23T00:00` 后仅目标槽位完成，相邻 `2026-10-22T00:00` 未完成，owner revision 变为 3；Done filter 的 accessible label 为“恢复未完成”；旧状态及旧 revision 请求均被拒绝。 | `memo-occurrence-toggle-result.json` SHA-256 `D70B79BFAAB964A26F90C47AD1A5BAE5A54A5A6977FC1EAEA63046EEE811CD91`；截图 `memo-occurrence-toggle-studio-preview.png` SHA-256 `D11D77D9AD75ACC4DA578907C973416AC687C5152247754C59184F09ECADF9ED`；运行日志 SHA-256 `7CC824D0413299893B4E003BDC36CDFA5114038DB8A9EF9882C03789579A661D`。 |
| Skin Studio Python：`python -m unittest discover -s tools/skin-studio/tests -v` | **61/61 PASS**。 | `skin-studio-unittest-20261029.log` SHA-256 `3104154FDDB19F27AC674524818115BDFF66C7DF1D5C493BC2C93390C2E5D6BD`。 |
| 完整离线 Gradle：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` | **BUILD SUCCESSFUL**；624/624 JVM tests、84 suites、0 failures/errors/skips；59 actionable tasks（5 executed、54 up-to-date）；source boundary、Debug APK、lint 通过。Lint 为 0 errors / 52 warnings。 | `gradle-full-final-20261029.log` SHA-256 `352DBE0C2BBFEB5E270FE12B5B72D47845E7A9349A745414D3B1F9C87DB15142`；Debug APK SHA-256 `F730EDD3953C534FA7C9441D0061B4815FF33BC13EFCE407127E98D0EA76735F`。 |
| Clear/Studio Node 语法与 `git diff --check` | **PASS**。 | 与 CDP 执行同一变更；命令退出码 0。 |
| Android 设备可用性 | **NOT TESTED / BLOCKED**：没有已连接 ADB device；SDK 中无 Emulator launcher 与 system image。 | 本轮没有安装、清空或修改 AVD。CDP Studio Mock 和 JVM 测试均不代替 Android WebView。 |

## 过程中的独立测试同步问题

首次完整回归中，既有 `memoPagingContinuesTheSortedProjectionWithoutStoreWrites` 在请求第 251 项后读到旧的 250 项 Compose snapshot。诊断显示 Host action 已执行（`stateRevision` 从 3 增至 4），但测试在 Compose 空闲/重组前开始轮询。该独立测试只补了 `ui.waitForIdle()`；分页逻辑没有改动。隔离回归与之后完整门禁均通过。独立提交 `1965f93030444cead1643c42cb8500ad89d9bb46`，详情见 `PC-ISSUE-SEPARATION-MEMO-PAGING.md`。

## 未验证项

- 打包 Clear 在 Android WebView 内的真实 JS→Host→Store 路径、Android 重建/进程恢复和系统无障碍服务行为：**NOT TESTED**。
- Native/Clear 成对同数据回放、重启恢复及完整 Memo parity：**NOT TESTED**。
- 实体设备由用户执行；本机暂时没有可用 AVD。不能将 JVM/Chrome 证据提升为设备验收。
- 因上述限制，父项 `SEP-03` 和验收项 `S0-ME-02` 保持 **NOT TESTED / unsigned**；皮肤分离不因此结束。

## 提交

- `c50d5aec22471b6522ef2748f8fae221c3f2e60a` — `test(分离): 补齐 Memo 单实例完成状态回归`（已推送）。
- `1965f93030444cead1643c42cb8500ad89d9bb46` — `test(分离): 等待 Memo 分页状态刷新`（已推送；独立 S0-ME-01 测试同步修正）。
