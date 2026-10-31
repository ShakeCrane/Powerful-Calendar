# PC-ISSUE-SEPARATION-MEMO-OCCURRENCE-TOGGLE — Memo 重复实例完成状态隔离

日期：2026-10-31
验收项：`S0-ME-02`；父项 `SEP-03`
状态：Host/JVM、Chrome Studio Mock 与 API 35 Native/Clear 重复 Memo 完成/撤销、真实 Android stale-action replay、Clear Activity 重建和冷进程重启后的 Memo 投影 **PASS**；SEP-03 全功能等价仍 **PARTIAL / HOLD**。

## 结论

Memo 完成状态按正式事件 owner、revision 和 occurrence key 重新验证。Host 在执行前重新读取事件，并确认版本和该 occurrence 的当前完成值；有效切换只提交一次并刷新投影。API 35 Android 15 模拟器上的 Native/Clear 重复 Memo 配对覆盖完成、Done 筛选、撤销与邻接槽位隔离。后续 stale replay 回归在 Clear WebView 中捕获真实可信触屏产生的原始 `toggleMemo` Bridge 消息；Store 前进到 revision 2 后，将 `expectedRevision=1` 的同一原始消息经当前 WebMessagePort 重放。Host 将“Memo 已变化，请刷新”投影回 Skin，正式记录、提交数、身份数和待处理状态均不变。

没有发现需要修改的 Host/Core 持久化实现缺陷。重复 Memo 的目标槽位完成/撤销及 Android stale-action rejection 已由配对设备回放证明；Clear Activity 重建及冷进程重启后也能从 Store 重新投影两个未完成槽位和一个已完成槽位，且没有追加写入。全 Memo 入口、Studio 所有编辑包路径与系统无障碍服务仍未覆盖，所以 `S0-ME-02` 有已验证切片，`SEP-03` 仍 **PARTIAL / HOLD**。

## 根因与工作对应

| 发现 | 修改 | 证据 |
| --- | --- | --- |
| 既有 JVM 覆盖没有把重复 Memo 的选中槽位、owner revision、重复动作及进程重开串成一次生产路径回归。 | 增加 PowerCalApp Compose/Robolectric 集成测试：从真实 Store 创建每日重复 Memo，通过 Host bridge 切换指定 key；检查相邻 key 不变、revision/commit 只增一次、旧请求无副作用、重开 Store 后状态保留。 | `webMemoToggleIsOccurrenceScopedRevisionGuardedAndStableAcrossRestart`，1/1 PASS；被覆盖的是 Host/Store 路径，不是 Android WebView。 |
| Studio Preview Mock 使用单一完成布尔值，无法模拟同一正式事件的多个 occurrence，也没有 owner revision 语义。 | Mock 改为按 `eventSeq:occurrenceKey` 记录完成状态，revision 按 owner 更新；严格匹配 `eventSeq`、`expectedRevision`、key 与期望完成状态，拒绝过期/不匹配 action。 | Chrome 156/CDP 对规范 Clear 包执行按钮交互并检查槽位隔离、可访问名称及两类 stale replay。 |
| 缺少真实 Clear WebView 对按钮、Host bridge、revision guard 和恢复持久性的端到端 instrumentation。 | 在既有 Native/Clear 重复 Memo 配对中加入测试期 WebMessagePort 捕获：保存可信触屏实际发送的完整旧消息，在 Store revision 前进后经同一端口重放；不改生产 Bridge/Core。 | API 35 instrumentation **1/1 PASS**：捕获 `eventSeq=1 / expectedRevision=1 / occurrenceKey=2026-11-01T00:00 / completed=false`；Host 返回 stale 提示，当前 revision 2、commit 2、identity 1、sequence 2、draft/pending 0 均保持不变。 |

## 修改文件

- `app/src/test/java/dev/powerfulcalendar/ui/v2/PowerCalCasualSuggestionComposeTest.kt`：生产 Host/Store completion path 的 occurrence、revision、replay 和 restart 回归。
- `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinRecurrenceInstrumentedTest.kt`：真实 Clear Android WebView instrumentation 回归；只编译，未运行。
- `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinAllKindsTrustedWriteInstrumentedTest.kt`：本次新增 Native/Clear 重复 Memo 完成状态配对，API 35 实际运行。
- `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/ClearActivityRecreationRecoveryInstrumentedTest.kt`：Activity 重建后重新连接 Clear，并验证已完成重复 Memo 的 Open/Done 投影及零额外写入。
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

- 截至上文早期设备回放时，Android stale revision/replay、重复 occurrence 撤销、无障碍服务与 Activity/进程恢复仍 **NOT TESTED**；后续章节补充了 stale replay、撤销以及 Activity 重建后的 Memo 投影证据。
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
- 这只关闭 SEP-03 的单实例无差异编辑器打开/重开切片；当时 Android stale replay、重复槽撤销及生命周期尚未验证。后续覆盖见本 Issue 最后一节；SEP-03 仍 **PARTIAL / HOLD**。

## 2026-10-31 recurring Memo occurrence completion → Done filter → undo parity

- 扩展既有 `sameRecurringMemoCompletionProducesEquivalentNativeAndClearOccurrenceState`，沿用同一个 Asia/Shanghai 每日 3 槽 MEMO fixture。Native Compose 和 Clear 分别完成 `2026-11-01T00:00`，进入 Done filter，再撤销该 occurrence；两侧最终 `DraftForm`、revision、事件身份与所有未完成 occurrence 相同。历史按 revision 正序为 `CREATE / COMPLETE / REOPEN`；revision/commit 为 3，identity 为 1，next sequence 为 2，draft/pending 为 0，邻接两日槽位始终未完成。Clear 筛选与撤销使用可信 Android 触摸并断言 `event.isTrusted=true`。
- API 35 / Android 15 / WebView 124 / `emulator-5570`，instrumentation **1/1 PASS**。JUnit XML SHA-256 `9a6fc53ed972abc515bcc8723f5ed760f1a1e2f31bc8c38efee7e308138e3ba2`；focused Gradle log `44082612a3551fa762cedb4060b09bbf5dcd455c12f4eb5d2939aa0f647e3213`；设备 parity marker `f35df79e396730f2e42117c1057a20911a5458070a49cc84a8ea44b8ca509ff0`，记录目标 occurrence、revision 3、history 和 counters。
- 完整 Gradle source-boundary、Debug/AndroidTest assembly 与 lint 门禁 **171 tasks PASS**，日志 SHA-256 `6db53cd89e071c7e17807c33b73ef2d9aadafc497d92743c3fd3052556ed8e5a`。本次门禁中的 JVM task 为 `UP-TO-DATE`；同一 JVM 源码最近一次 `--rerun-tasks` 为 **785/785 / 125 suites / 0 failures/errors/skips**，日志 SHA-256 `ce38a591cc2d9efebeb58bda7ce3f14b2b14c6222b342d910c0e92ffac729e99`。Debug APK SHA-256 `de876d09d11689f98131c9dad0a977aa1271e1657612b6a95db89abbbb80c31c`；AndroidTest APK SHA-256 `24e828a0fb141eb3937462950da782f88d97f7657b5ee2ef738e075e73c19b2e` 为 worktree-specific，包含两份未提交/未执行的 FormalDelete 与 Recurrence 并行测试编辑。
- 首轮断言失败来自测试期望与既有契约不符：`history()` 按时间倒序返回，且 Native/Clear 的 undo 处于不同执行阶段。将检查改为先比较两端共同的完成态、再各自完成撤销并按时间正序检查历史；没有修改 Core、Host、Native 或 Clear 生产逻辑。
- 此切片关闭重复 Memo occurrence 的撤销等价回放。该次运行尚未覆盖 Android stale replay、Activity/进程生命周期恢复、无障碍服务和实体/OEM；后续 stale replay 证据见下节。SEP-03 保持 **PARTIAL / HOLD**。证据文件位于 `.local/android-test/reports/sep03-recurring-memo-undo-20261031/`。

## 2026-10-31 Android WebMessagePort stale revision replay

- 在既有 API 35 Native/Clear recurring Memo 配对用例中，测试临时包装同一 JS realm 的 `MessagePort.prototype.postMessage`，捕获真实 Clear trusted touchscreen 完成按钮所发送的原始 JSON action。首次操作以 `expectedRevision=1` 提交后，Store 达到 revision 2；测试恢复原型方法，再通过捕获的当前页面端口原样重放该 JSON。没有更改 Clear、Host 或 Core 生产代码，也没有放宽 CSP。
- API 35 / Android 15 / WebView `124.0.6367.219` / `emulator-5570`：`WebSkinAllKindsTrustedWriteInstrumentedTest#sameRecurringMemoCompletionProducesEquivalentNativeAndClearOccurrenceState` **1/1 PASS**。测试等待真实 Host state 投影 `Memo 已变化，请刷新`，随后核对正式记录完全相同、commit 仍为 2、identity 为 1、next sequence 为 2、draft/pending 均为 0，历史仍只有 `CREATE / COMPLETE`。设备日志 marker：`NATIVE_CLEAR_PARITY operation=STALE_REPLAY kind=MEMO eventSeq=1 expectedRevision=1 currentRevision=2 occurrenceKey=2026-11-01T00:00 accepted=false commits=2 hostNotice=Memo已变化`。
- 证据目录 `.local/android-test/reports/sep03-stale-replay-20261031/`：JUnit XML SHA-256 `0FE0D97ADBC1F4CC776AD519673A877BBF563A96F9F8966429F02ED2C388C7BE`；focused Gradle log SHA-256 `00E3EA76978C85ED401F6C8B533E2E332CCCA89E818BCBBEC8FFDF582C348D6F`；instrumentation result log SHA-256 `D850037BF6A0006C9FFE7228823811ACFC64D246C01F08E88812A735A809F25D`；device logcat SHA-256 `6061340B8F2516760F5038BA156D9ADC14E07DDB373099657456D0EB5AA57BE6`。其后强制离线全门禁 **785/785 JVM、125 suites、0 failures/errors/skips、171/171 tasks PASS**，Lint 为 0 errors/55 warnings，门禁日志 SHA-256 `C645DF1E3FF3918894EFE4A78D385B5A559C70D3FB4350ACF590C73FF642CB94`；独立 `:app:verifyNativeSkinBoundary` 亦 **PASS**，日志 SHA-256 `FB2A326FE215AC07983A5A904AA2633DEC51BBE320634E7BE0FA504F026B87B6`。Debug APK SHA-256 `DE876D09D11689F98131C9DAD0A977AA1271E1657612B6A95DB89ABBBB80C31C`；worktree-specific AndroidTest APK SHA-256 `7C9011D7872FA62CE3D7329645D05F01041BD1B2C3030F5D4883210C664640DD`，内含另外两份 Agent 未提交测试源码，本次仅执行上列 Memo 方法。设备环境 API 35 / Android 15 / WebView `124.0.6367.219`。
- 这关闭真实 Android stale-action rejection 的这一 Memo 子场景，不证明冷进程恢复、TalkBack/无障碍服务或实体/OEM 行为。`S0-ME-02` 已有 Store/JVM、Studio Mock、Native/Clear 设备写入/撤销与 Android stale replay 证据；SEP-03 仍 **PARTIAL / HOLD**。

## 2026-10-31 Clear completed Memo projection after Activity recreation

- 扩展 `ClearActivityRecreationRecoveryInstrumentedTest`：使用隔离数据库创建每日重复 Memo，通过 Store fixture 将首个 occurrence 持久化为完成状态；Clear WebView 初次连接后检查 Open 列表有 2 槽、Done 列表有 1 槽。真实 Activity `recreate()` 后重新附着 Clear Runtime，再检查相同两列表和同一 occurrence key；正式对象 revision 2、history `CREATE / COMPLETE`、commit 2、identity 1、next sequence 2，draft/pending 为 0，重建后没有新增写入。该测试证明 Activity/UI 重建后的 Host→Skin 投影，不代表进程死亡/冷启动恢复。
- API 35 / Android 15 / WebView `124.0.6367.219` / `emulator-5570`：整个 `ClearActivityRecreationRecoveryInstrumentedTest` **2/2 PASS**，既有 clean/dirty editor recreation 回归仍通过，新 Memo 投影方法也通过。JUnit XML SHA-256 `884CDD75D7C5D5DECFB372B4790FF821852BE12C472989FA3BC4249882149796`；类 Gradle log SHA-256 `53A7C3B24F68AFFF82F538E67F54E1F68B51ABED07DEAEFCA3B1A265E632A814`；设备 marker log SHA-256 `01E756395BD083781E6202E1C958715B3675919C48BEFC2B22C2FA2E69D7447E`。首轮单测断言脚本的字符串多转义一层，WebView 没有返回布尔结果；修正测试表达式后，定向方法及整个类均通过，没有生产行为缺陷证据。
- 强制离线 Gradle 全门禁 **785/785 JVM、125 suites、0 failures/errors/skips、171/171 tasks PASS**，其中 Skin/Native source boundary、Debug/AndroidTest APK 与 Lint 全过（0 errors/55 warnings）。门禁日志 SHA-256 `291E665490345A27ECC8DA5E67B10AB0C0A242DAA90AD44FBF7E4BC50363434F`；Debug APK SHA-256 `DE876D09D11689F98131C9DAD0A977AA1271E1657612B6A95DB89ABBBB80C31C`；AndroidTest APK SHA-256 `7849D6F20D6723748226234B6727CFDFC9F8234B0D647BB434CB5405C89C046A`，属于该工作树构建并含两份其他 Agent 未提交 AndroidTest 源文件；本次运行仅执行恢复测试类。证据目录 `.local/android-test/reports/sep03-memo-activity-recreate-20261031/`。
- SEP-03 仍 **PARTIAL / HOLD**：该 Slice 覆盖 Activity 重建，不覆盖正式 Memo 完成状态的冷进程重启、所有 Memo 入口、系统无障碍服务、Studio 任意编辑包全路径与实体/OEM。

## 2026-10-31 formal Memo 与脏编辑器共同经历冷进程重启

- 扩展既有 `ClearProcessRestartRecoveryInstrumentedTest` 两阶段 fixture，在同一 UUID 隔离数据库内创建一个每日重复正式 Memo，并完成首个 occurrence `2026-11-01T00:00`。`ON_STOP` 为同时打开的 ACTIVITY 编辑器只 checkpoint 一张草稿；两个独立 instrumentation phase 之间，脚本对 app 和 test package 执行真实 `am force-stop`，确认旧 PID 退出后才启动恢复 phase。
- API 35 / Android 15 / WebView `124.0.6367.219` / `emulator-5570`：prepare PID `9355`、restore PID `9517`；prepare 与 restore 各 **1/1 PASS**。恢复后正式 Memo 仍为 eventSeq 1 / revision 2 / `CREATE, COMPLETE` / commit 2；Clear Open 显示剩余 2 槽，Done 显示已完成槽。脏编辑器恢复为原 draftId/eventSeq/title 的唯一待处理草稿；正式事件仍 1 个、identity 2、next sequence 3、queue order 2，没有新增 commit。脚本确认 UUID 数据库及 marker 均无残留。
- 完整离线 Gradle 强制门禁 **785/785 JVM、125 suites、0 failures/errors/skips、171/171 tasks PASS**；Skin/Native source boundary、Debug/AndroidTest APK assembly 与 lint 通过（0 lint errors）。门禁日志 SHA-256 `985AACB47487F81E2B119AAC27E74949CA81F62B21ED128253E5DA05F4EA21C3`；debug APK SHA-256 `DE876D09D11689F98131C9DAD0A977AA1271E1657612B6A95DB89ABBBB80C31C`。实际安装用于冷进程回放的 AndroidTest APK SHA-256 `7C7535DFF199F3F97074AB8EBC78D747FADEFFE2FE4182EEE1961E794B17EE06`；强制门禁产物 AndroidTest APK SHA-256 `2CB2E4310957DE2C049D2E4FE80E27BA825A58A570FC1004915EF372CC0A0EC5`。后者编译时包含两份其他 Agent 的未提交 AndroidTest 编辑，本次没有执行这两份测试。
- 原始设备脚本输出 `.local/android-test/reports/sep03-memo-cold-process-20261031/cold-process-run.txt` SHA-256 `C7586D410783C4EC2450340168A9CB8936A145BCB4623D2D91B275B79F29AC01`；设备 System.out trace SHA-256 `C119F69C46DE5EEBA94FF82239A16CE75735A4932B88CE3607004329171D919A`。完整门禁日志与 APK 位于同一证据目录/Gradle 输出目录。
- 此回归关闭正式重复 Memo 状态与单张脏编辑器草稿在真实冷进程重启中的持久恢复切片；它不是 Native/Clear 的 Memo 写入操作配对，不覆盖其他 Memo 入口、TalkBack/无障碍服务或实体/OEM。SEP-03 与 SEP-14 仍 **PARTIAL / HOLD**。
