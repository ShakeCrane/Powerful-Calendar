# PC-ISSUE-SEPARATION-MEMO-OPEN-FALLBACK — 保留通用 openEvent fallback 的重复实例

日期：2026-10-29
状态：**fallback 实现与 JVM/Chrome Mock 验证 PASS；Android WebView、设备运行及 Native/Clear 配对验收 NOT TESTED，S0-ME-03 未签核。**

## 问题与根因

Clear 的规范 `edit.js` 路径会把 Memo 重复实例的原始槽位键传给 `editor.open`。当该路由返回 `false`、由 `app.js` 通用 `openEvent` fallback 接手时，原 payload 只含 `eventSeq` 和 `expectedRevision`。Host 的 `WebSkinAction.OpenEvent`、Bridge decoder 及 `PowerCalApp` 调用链也没有 occurrence key 字段。

因此 fallback 只能打开正式事件本身，无法让 `EditSession` 知道用户点选的是系列中的哪一个实例；用户无法从这个入口选择并查看准确的单次实例内容。这是 Memo 行点击入口传输实例身份丢失，不涉及 Memo 完成开关、事件持久化模型或正式事件数据。

## 修复内容

- Clear 通用 `openEvent` fallback 在按钮具有 `data-original-occurrence-key` 时，将其作为可选 `occurrenceKey` 发送；不重复或变更事件数据。
- Host Bridge 保留旧的无 key schema，同时接受带 key schema。Decoder 限制 key 长度和字符；Host 仍检查当前事件序号、revision、写入可用性，并验证 key 属于当前重复系列且可解析。
- `PowerCalApp` 将已验证的 key 传给既有 `openFormalEditor` / `EditSession` 路径。未修改 Store 或编辑器架构。
- Skin Studio Preview Mock 对 `openEvent` 校验 owner、revision 和投影中的 occurrence key；更新 V3 Bridge 合同并重新生成 canonical Clear `.pcskin`。
- 回归还验证选择“仅本次”后表单显示被点击实例的确切日期，并拒绝“有效 key + 过期 revision”。
- 独立只读审查建议直接断言所选实例日期和 key/revision 组合；已补入 Compose/WebView 回归与 stale-revision 用例。定向测试和最终完整门禁均在该补强后通过。

## 验证证据

环境：Windows PowerShell；Gradle Wrapper 8.14.3；JDK 20.0.1；Android SDK `E:\android-sdk` / `android-36`；Python 3.11.4；Node 24.19.0；Chrome 156. Android SDK 路径仅作为命令进程环境变量使用，没有写入 `local.properties` 或全局 Git 配置。

| 检查 | 结果 | 可复核证据 |
| --- | --- | --- |
| 定向 JVM 回归与 AndroidTest Kotlin 编译：`WebSkinBridgeTest`、`PowerCalCasualSuggestionComposeTest.openEventFallbackRetainsTheSelectedMemoOccurrence`、`:app:compileDebugAndroidTestKotlin` | **PASS**。Bridge 测试覆盖新旧 payload、非法 key、无效/非重复槽位及带 key 的过期 revision；Compose 测试经 `PowerCalApp` 打开重复 Memo，选择“仅本次”后可见被选日期，且退出前后 Store 计数不变。AndroidTest 新增同一 WebView fallback 与确切日期断言，成功编译。 | 完整 Gradle JUnit XML：`TEST-dev.powerfulcalendar.ui.v2.WebSkinBridgeTest.xml` SHA-256 `F54D6893DFFA5C1D329AEAD63A5281FE10B72ABD3925709A5F83420BA294EAF1`；`TEST-dev.powerfulcalendar.ui.v2.PowerCalCasualSuggestionComposeTest.xml` SHA-256 `E5057EE4F9704DF61084B04E64D4428D316CCB6A00B117E4BFD499FC7043136F`。 |
| 完整离线 Gradle（含 source boundary、JVM、Debug APK、AndroidTest APK、Lint，`--rerun-tasks`） | **BUILD SUCCESSFUL**；626/626 JVM 测试、84 suites、0 failures/errors/skips；88/88 tasks 执行成功；Lint 0 errors / 52 warnings。 | 日志 `.local/android-test/reports/separation-s0-timeline-drag/memo-open-fallback/gradle-full-final-20261029.log` SHA-256 `90AE6166492819BA8E7D62380319FC101C0A56C9B659214FF74D1D3B2683BAE5`。Debug APK SHA-256 `A0F1613128DDBD0D970151A1D7AD6447A1573E5AE1D3FFCA291335F4A316E7B9`；AndroidTest APK SHA-256 `0883557A280C1F834EFD33A177FFACD423E1269FF285391B1E395FACF1252AA5`。 |
| Chrome 156/CDP：`node tools/skin-studio/tests/e2e_v3_memo_occurrence_toggle.cjs` | **PASS（Chrome + Studio Preview Mock，不是 Android WebView）**。运行 canonical 9-file Clear 包；强制通用 fallback 后 Mock 收到 `eventSeq=31 revision=3 occurrenceKey=2026-10-23T00:00`。同一回放还检查目标与相邻 Memo 槽位状态隔离及 stale action 拒绝。 | 结果 JSON SHA-256 `40470E1C2E2026E3D02988FE426E1EAC821CCAAE23E2EFEDD01E0A5B9EB70318`；截图 SHA-256 `493A48DFE29C31A5F1B3E5CDAAEEBCB99DE89CF3171B04D7575535E518085544`。 |
| Skin Studio Python：`python -m unittest discover -s tools/skin-studio/tests -v` | **61/61 PASS**。首次调用中 3 项因子进程 Git 的 dubious-ownership 防护无法读取属性；使用当前进程级 `GIT_CONFIG_PARAMETERS` 重跑后通过，未改持久配置。 | `.local/android-test/reports/separation-s0-timeline-drag/memo-open-fallback/studio-python-20261029.log` SHA-256 `360E85B2B0DE90591E6FC9C7C23DD02E893E2A1F3A4A888037618641BA5E4D0D`。 |
| Clear package、JavaScript 语法、diff whitespace | **PASS**。Canonical package 9 files / 386,198 bytes；3 个修改的 JS 文件 `node --check` 均通过；暂存前后 `git diff --check` 通过。 | Package SHA-256 `2A325BAEB99533A7B620AF281D85E8432F09159E0DD525C42743F164E594CCF3`。 |
| Android WebView / AVD / 真机 | **NOT TESTED**。新增 instrumentation 已打包，但没有连接的 ADB device，SDK 无 `emulator.exe`；没有把 Chrome Mock 或 APK 构建结果记作 Android 运行证据。 | `adb devices -l` 仅显示空设备列表；`E:\android-sdk\emulator\emulator.exe` 不存在。 |

测试输出与 APK 保存在本地忽略目录 `.local/android-test/reports/separation-s0-timeline-drag/memo-open-fallback/` 和 `app/build/outputs/apk/`；没有将运行时数据或日志加入 Git。

## 修改文件

生产与合同：`WebSkinHost.kt`、`PowerCalApp.kt`、Clear `app.js`、Studio `v3-studio.js`、canonical `.pcskin`、`V3_HOST_BRIDGE_EXTENSION.md`。

回归：`WebSkinBridgeTest.kt`、`PowerCalCasualSuggestionComposeTest.kt`、`WebSkinRecurrenceInstrumentedTest.kt`、`e2e_v3_memo_occurrence_toggle.cjs`。

## 提交与剩余状态

- `a10c18d64fabb86052e28c04343bea3fac53cc5d` — `fix(分离): 保留 Memo fallback 的重复实例`，已推送至 `origin/codex/separation-s0-timeline-drag`。
- fallback 的特定缺陷已修复并有 Host/Compose/Chrome Mock 证据。由于 Android WebView 执行和同数据 Native/Clear 配对回放未做，重建矩阵 `S0-ME-03` 及父级 Memo parity 仍为 **NOT TESTED / unsigned**；需后续可用 Android 环境与配对回放补齐，不能将本报告解释为皮肤分离 GO。
