# PC-ISSUE-SEPARATION-MEMO-OCCURRENCE-TOGGLE — Memo 重复实例完成状态隔离

日期：2026-10-29
验收项：`S0-ME-02`；父项 `SEP-03`
状态：Host/JVM、Chrome Studio Mock 与 API 35 Native/Clear 重复 Memo 完成配对 **PASS**；SEP-03 全功能等价仍 **PARTIAL / HOLD**。

## 结论

Memo 完成状态按正式事件 owner、revision 和 occurrence key 重新验证。Host 在执行前重新读取事件，并确认版本和该 occurrence 的当前完成值；有效切换只提交一次并刷新投影。既有 JVM 与 Studio Preview Mock 验证了陈旧动作拒绝和槽位隔离。2026-10-31 又在 API 35 Android 15 模拟器上执行同一重复 Memo fixture 的 Native/Clear 实际交互配对：两路均只完成目标 occurrence，owner revision 与提交数各增加一次，邻接实例仍未完成；Clear 点击来自 Android touchscreen 注入且 `event.isTrusted=true`。

没有发现需要修改的 Host/Core 持久化实现缺陷。这个设备回放关闭了重复 Memo 完成状态的 Native/Clear 基础路径，但没有覆盖 Memo 全部入口、陈旧 Android replay、Studio 修改包、完整恢复与无障碍服务，所以 `S0-ME-02` 有已验证切片，`SEP-03` 仍 **PARTIAL / HOLD**。

## 根因与工作对应

| 发现 | 修改 | 证据 |
| --- | --- | --- |
| 既有 JVM 覆盖没有把重复 Memo 的选中槽位、owner revision、重复动作及进程重开串成一次生产路径回归。 | 增加 PowerCalApp Compose/Robolectric 集成测试：从真实 Store 创建每日重复 Memo，通过 Host bridge 切换指定 key；检查相邻 key 不变、revision/commit 只增一次、旧请求无副作用、重开 Store 后状态保留。 | `webMemoToggleIsOccurrenceScopedRevisionGuardedAndStableAcrossRestart`，1/1 PASS；被覆盖的是 Host/Store 路径，不是 Android WebView。 |
| Studio Preview Mock 使用单一完成布尔值，无法模拟同一正式事件的多个 occurrence，也没有 owner revision 语义。 | Mock 改为按 `eventSeq:occurrenceKey` 记录完成状态，revision 按 owner 更新；严格匹配 `eventSeq`、`expectedRevision`、key 与期望完成状态，拒绝过期/不匹配 action。 | Chrome 156/CDP 对规范 Clear 包执行按钮交互并检查槽位隔离、可访问名称及两类 stale replay。 |
| 缺少真实 Clear WebView 对按钮、Host bridge、revision guard 和恢复持久性的端到端 instrumentation。 | 增加 Android instrumentation；原有 `WebSkinRecurrenceInstrumentedTest` 的 stale/restart 方法仍未执行。本次另在 `WebSkinAllKindsTrustedWriteInstrumentedTest` 加入同 fixture Native/Clear 的目标 occurrence 完成配对。 | 新配对在 API 35 模拟器 **1/1 PASS**，验证真实 Clear WebView→Host→Store、revision 与相邻槽位；不覆盖 stale replay 或 Store 重开。 |

## 修改文件

- `app/src/test/java/dev/powerfulcalendar/ui/v2/PowerCalCasualSuggestionComposeTest.kt`：生产 Host/Store completion path 的 occurrence、revision、replay 和 restart 回归。
- `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinRecurrenceInstrumentedTest.kt`：真实 Clear Android WebView instrumentation 回归；只编译，未运行。
- `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinAllKindsTrustedWriteInstrumentedTest.kt`：本次新增 Native/Clear 重复 Memo 完成状态配对，API 35 实际运行。
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
| Android 设备可用性（2026-10-29 原始运行） | 当次 **NOT TESTED / BLOCKED**：没有已连接 ADB device；SDK 中无 Emulator launcher 与 system image。 | 这是原始运行的历史状态。2026-10-31 已在 API 35 `emulator-5570` 执行本 Issue 追加的 Native/Clear 配对场景，证据见下文。 |

## 过程中的独立测试同步问题

首次完整回归中，既有 `memoPagingContinuesTheSortedProjectionWithoutStoreWrites` 在请求第 251 项后读到旧的 250 项 Compose snapshot。诊断显示 Host action 已执行（`stateRevision` 从 3 增至 4），但测试在 Compose 空闲/重组前开始轮询。该独立测试只补了 `ui.waitForIdle()`；分页逻辑没有改动。隔离回归与之后完整门禁均通过。独立提交 `1965f93030444cead1643c42cb8500ad89d9bb46`，详情见 `PC-ISSUE-SEPARATION-MEMO-PAGING.md`。

## 未验证项

- Native/Clear 重复 Memo 完成的基础设备回放已通过；一次性日期 Memo 在 Studio 导出皮肤上的打开/退出/重开，以及完成后切换已完成列表并撤销，也已完成 API 35 配对回放。Android stale revision/replay、重复 occurrence 完成后再次打开/撤销、无障碍服务与 Activity/进程恢复仍 **NOT TESTED**。
- 实体/OEM 验收仍未执行。API 35 AVD 结果不能替代实体设备验证。
- 因其余 Memo 场景未闭环，父项 `SEP-03` 保持 **PARTIAL / HOLD**；本 Issue 不为其余行为签核。

## 2026-10-31 Studio 导出包上的一次性 Memo 完成与撤销配对

- 在既有 Browser Studio 导出包（`.pcskin` SHA-256 `3a754789878c3ab7042351b3af0cd58e204abad4770ba69eac25b940d156aaf7`）上扩展 Android 回放。Native Compose 与安装后的 Studio-authored Clear WebView 对同一隔离日期 Memo 各执行两次无差异打开/退出，然后执行完成 → 查看已完成 → 撤销 → 返回未完成列表。Skin 的 checkbox 与过滤器操作均为 `SOURCE_TOUCHSCREEN` 并校验 `isTrusted=true`。
- 两端每次完成/撤销后均逐项核对 Store：revision/commit 由 1 增至 2、再至 3；最终仅一个正式事件和身份、next sequence 为 2、queue order 保持基线，无草稿或 pending；历史操作恰为 `CREATE / COMPLETE / REOPEN`。Native 与 Skin 最终 `DraftForm` 相等。
- API 35 / Android 15 / WebView 124 / `emulator-5570`，`StudioAuthoredSkinBridgeInstrumentedTest` **2/2 PASS**（其中 Studio Month Bridge 既有回归 1 项、Memo 配对流程 1 项）。XML SHA-256 `7B8EE912F189583845D68957B701DF0595D4743B99DAB63AB32296186AF6707F`；focused Gradle log SHA-256 `F03A5C0EAFC6069B6667DED9E51E461DBBE726EC7E4346422A2359493254C516`；触摸回放 marker SHA-256 `62CBF8635A7DEF3FE5054A5A425A3FB0D0452852D10250C8EBF26265A9184AB8`。具体命令与 Store 断言在 `StudioAuthoredSkinBridgeInstrumentedTest#studioAuthoredSkinAndNativeMemoNoDiffReopenAndCompletionUndo`。
- 证据清单 `.local/android-test/reports/sep03-memo-open-reopen-studio-20261031/verification-manifest.json` 随本次测试更新。该用例关闭一次性日期 Memo 的完成/撤销交互切片，不覆盖 recurring occurrence 的 stale replay/undo，也不替代生命周期恢复、系统无障碍及实体/OEM 验收；因此 `SEP-03` 仍 **PARTIAL / HOLD**。

## 提交

- `c50d5aec22471b6522ef2748f8fae221c3f2e60a` — `test(分离): 补齐 Memo 单实例完成状态回归`（已推送）。
- `1965f93030444cead1643c42cb8500ad89d9bb46` — `test(分离): 等待 Memo 分页状态刷新`（已推送；独立 S0-ME-01 测试同步修正）。

## 2026-10-31 Android 15 Native/Clear 配对回放

- 同一 `Asia/Shanghai` 每日重复 MEMO fixture（3 个实例）分别经 Native Compose 和 APK 内 Clear WebView 完成首个实例。Native checkbox 触屏后，Core 记录只包含 `2026-11-01T00:00`；Clear Memo 导航和 checkbox 通过 `SOURCE_TOUCHSCREEN` 注入，均断言 `event.isTrusted=true`。两路 `eventSeq=1`、revision `1→2`、commit `1→2`、identity `1`、next sequence `2`；剩余实例为 `2026-11-02T00:00` 与 `2026-11-03T00:00`，draft/pending 均为 0。
- API 35 `emulator-5570` focused pair **1/1 PASS**，同一 trusted-write instrumentation class **17/17 PASS**；完整离线 Gradle gate **785/785 JVM、125 suites、0 failures/errors/skips、171/171 tasks PASS**，Native/Skin boundaries、Debug/AndroidTest APK 和 lint 通过。
- 设备类日志 SHA-256：focused Gradle `D4E9CEDCA46BDC814171AA6744D9190EC472FABF7D107D5D1B3AEEE69EA1AE76`，class `4E6E672EB70B9EA2B6FDEC69B7DA751F2AAB933CF71A108D6781E3BE12A94BFE`；完整 Gradle gate `C12A5ECE61CA522296D821EA24DAACB51196091022461300AAE74EE02E13CA06`。单测 XML `772E98A5F54E6539E85287023579206F14E729D730E4C4747F215A69DA6DBAEE`；focused WebView/Host logcat `B401CB949CCF4AFE67EBEB8B90B4774277665437D48D9D657B277513BCBFAFDC`。Debug / AndroidTest APK SHA-256 `DE876D09D11689F98131C9DAD0A977AA1271E1657612B6A95DB89ABBBB80C31C` / `3A2EB64DD2EF7D2F87CAAD9BEC77B04FEB02129105BE915E1AA4386F46AB4831`。
- 完整日志、XML 与触屏追踪保存在 `.local/android-test/reports/sep03-memo-completion-parity-20261031/` 及 Gradle connected-test 输出目录。新增测试提交 `db5b67a1a9f93aa5e7c96483f256c5fe8fce324d` 已推送至 `origin/feat/skin-first-separation`；Core、Host 和 Clear 生产源码均未修改。

## 2026-10-31 Studio-authored Clear Memo no-diff open/reopen

- 真实 Chrome/CDP Browser Studio 导入 APK 内的 Clear 包，使用源码编辑器增加月份按钮/Bridge 绑定，并编辑 Memo 标题样式；实际 `.pcskin v3` 导出后重导入、继续编辑并二次导出。最终包 395,815 bytes，SHA-256 `3a754789878c3ab7042351b3af0cd58e204abad4770ba69eac25b940d156aaf7`；能力声明和文件集合保留，7 个未编辑文件逐字节不变。
- API 35 Android 15 / WebView 124 / `emulator-5570` 使用相同日期 Memo 分别运行 Native Compose 和该 Studio 导出包：两端均完成打开、无差异退出、再次打开、再次退出。每一步比较正式对象、历史、commit、identity、next sequence、queue order、draft 和 pending 快照，均未产生变化；Clear 两次打开与退出均由可信 Android 触摸完成。Studio 修改的 Memo 标题最小高度在 Runtime 计算为 44px。定向类 **2/2 PASS**（月历 Bridge 与 Memo 重开两项）；Memo 用例实际 Store 写入为 0。
- Skin Studio Python 全套 **65/65 PASS**；强制离线 Gradle 门禁 **785/785 JVM、125 suites、0 failures/errors/skips、171/171 tasks PASS**，Native/Skin boundaries 与 Debug/AndroidTest APK 构建通过，Lint 0 errors/55 warnings。门禁日志 SHA-256 `b1689f52aeb906e5ff4d1b95ee69e1606cd572015edee0416b05867113cacc2a`；Debug/AndroidTest APK SHA-256 `de876d09d11689f98131c9dad0a977aa1271e1657612b6a95db89abbbb80c31c` / `f1dc88d6d0941d6e7beeb49bf0f63ef058a3d98fb582b5fb8d44095619b47ccd`。AndroidTest APK 为 worktree-specific，包含未暂存且未执行的两个并行 Agent 测试文件。Android JUnit XML SHA-256 `7b8ee912f189583845d68957b701df0595d4743b99dab63ab32296186af6707f`；设备日志 SHA-256 `6da835f0e8254d97c3ee92f6d4f36e3b22e69ded6705fd93c49087b07a8785ff`；Python 日志 SHA-256 `2e2e6cd9eb8664143da1d6a875d80b72e1eb315243a3f21e8123d9e90c4f4483`；manifest SHA-256 `e0f2e6798edc99810c28b151310307acd199f4ebd006773272a9b7785b3e5e4d`。证据清单 `.local/android-test/reports/sep03-memo-open-reopen-studio-20261031/verification-manifest.json`。
- 首轮失败均是测试夹具问题：Native Compose Activity 恢复了 Memo 页状态，测试起初假设从时间轴开始；另一次测试将含引号的 CSS selector 未转义地拼进 `evaluateJavascript`。页面/selector 检测现已修正，旧新 `.pcskin` 的 HTML/JS 字节一致，故障不在 Clear 生产包。成功日志在 PASS 标记后记录 WebView renderer 随测试 Activity 退出而终止、随后进程正常退出；该用例不把 teardown 当作 SEP-14 恢复验收。
- 这只关闭 SEP-03 的单实例无差异编辑器打开/重开切片。Android stale replay、重复槽完成后的重开/撤销、完整 Activity/进程恢复、系统无障碍及实体/OEM仍未验证；SEP-03 保持 **PARTIAL / HOLD**。
