# PC-ISSUE-SEPARATION-WEB-KIND-CONVERSION

## 状态

**PARTIAL / OPEN**。Host 的四类转换 patch、Clear 类型选择器和 Studio 非写入预览已实现。2026-10-31 在 Android 15/API 35 AVD 上新增并通过一条同 fixture Native/Clear 正式事件 `ALARM→ACTIVITY` 转换配对：批准前均不写入，Clear Host 审查取消零写入，批准后表单、eventSeq、revision 和 Store 计数一致。此证据仅覆盖一个转换方向；其余类型组合、Studio 编辑后实际运行、恢复/冲突和无障碍等仍未完成，因此 SEP-05 保持 **PARTIAL / HOLD / INCOMPLETE**。

## 需求与复现

Native 正式事件编辑器允许将事件转换为 `ALARM`、`ACTIVITY`、`DEADLINE` 或 `MEMO`，转换规则由冻结的 `TemporalConversion` 决定。Web 皮肤应通过既有 Host 编辑会话和草稿卡组执行相同转换，并保持 Host 对正式数据写入的审查边界。

修复前，`editor.patch` / `deck.patch` 的字段白名单不含 `kind`，`WebEditWire.patch` 也没有对应转换分支。以 `kind` 发出语义 patch 会被判为不可写字段；正式编辑的相关路径返回 `invalid`，重复实例桥接在解析命令时拒绝该字段。修复前测试运行确认：22 项中 3 项失败，具体失败见红测证据。

## 根因与修复

根因是 Web 编辑协议没有把 Native 已支持的事件类型转换接入 Host 表单 patch；现有正式保存前 Host 审查和单次重复实例字段限制本身已存在，但无法收到有效类型转换后的表单。首次 Host 修复后 Clear UI 仍未暴露选择入口；本次 follow-up 补齐该 UI。

在 [WebSkinEditBridge.kt](../../app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridge.kt) 中，将 `kind` 加入受限 patch 字段，并严格解析四种 `TemporalKind` 名称。有效值统一调用 `TemporalConversion.convert(form, target).form`；未知值返回 invalid。该 patch 同时供 `editor.patch` 与 `deck.patch` 使用，不直接写数据库。

正式事件仍沿既有 Host 审查与重新校验路径保存；测试确认转换审查批准后保留原 `eventSeq`，仅在确认后递增 revision，且不创建额外草稿。草稿卡组继续经现有 `DeckController` 转换与暂存。`SINGLE_OCCURRENCE` 仍只允许其原有标题和时间字段，类型转换被拒绝。

当时（2026-10-28）更新了 [V3_HOST_BRIDGE_EXTENSION.md](../../docs/V3_HOST_BRIDGE_EXTENSION.md)、[SKIN_EDIT_BRIDGE_S1_S2_2026-10-22.md](../../docs/SKIN_EDIT_BRIDGE_S1_S2_2026-10-22.md) 和 [SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md](../../docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md)，并记录 Clear 类型选择器缺失。该缺口随后已修复；2026-10-31 的配对设备证据见本文末尾。

## 验证结果

### 修复前回归（根因复现）

- 命令：`.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests dev.powerfulcalendar.ui.v2.WebSkinEditBridgeTest --tests dev.powerfulcalendar.ui.v2.WebSkinRecurrenceBridgeTest`
- 结果：22 项，3 项失败。正式编辑返回 `invalid` 而非 `edited`；kind patch 抛出 `Field is not writable`；单次重复实例的命令在解析阶段被拒绝。
- 日志：`.local/android-test/reports/separation-20261027/web-kind-conversion/focused-red-compilefixed.log`
- SHA-256：`6AE7742C697B4FFDB97E8F298F3657136A4B604D4ECF606ED6475207632B23DD`

### 修复后定向回归

- 同一命令通过，23 项通过、0 失败。`WebSkinEditBridgeTest` 13 项、`WebSkinRecurrenceBridgeTest` 10 项。
- 覆盖四种目标类型的核心转换一致性、未知类型拒绝、editor/deck patch、正式事件转换需 Host 审查并保留身份、转换后不产生额外草稿，以及单次重复实例拒绝转换。
- 日志：`.local/android-test/reports/separation-20261027/web-kind-conversion/focused-green-final.log`
- SHA-256：`CA59F2113D13765F6E288D14F34B2A9F0FAF69EAE425BF83D5292C79AF3E5E92`

### 完整离线 Gradle 门禁

- 命令：`.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain`
- 结果：`BUILD SUCCESSFUL`，59 个 Gradle tasks 执行；83 个 JVM 测试套件、597 项测试通过，0 failures / errors / skipped；`verifySkinSourceBoundary`、Debug APK 构建、`lintDebug` 均通过。
- Lint：0 errors、52 warnings。告警包含 target API、弃用 API 和 Kotlin 类型推断等项；本报告未逐条归属这些告警。
- 完整日志：`.local/android-test/reports/separation-20261027/web-kind-conversion/full-gate.log`
- 日志 SHA-256：`451AAC35C219165F41F166D7E610C4106C159FF49E1479E78612838AF6421535`
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- APK SHA-256：`656D2084A74B12A229A34DA0B8EF586D760BF5057F334B69B1B93597756B799B`
- `git diff --check` 对本次拥有的六个实现、测试和合同文件通过。

## 设备证据与残余项

- Android WebView / AVD：类型选择器的 instrumentation 用例已经加入并编译进 AndroidTest APK，但没有安装运行。当前配置的 `E:\android-sdk\emulator\emulator.exe` 和用户 SDK 下的 emulator launcher 均不存在；`adb devices -l` 因 ADB daemon 启动失败退出 1。状态为 `BLOCKED / NOT TESTED`，不能把浏览器预览当作 Android WebView 结果。
- 实体/OEM 设备：本轮未运行，按用户分工留给用户验证。
- Clear 类型选择器与转换后的条件字段在 Edge 中通过 Studio Preview Mock 验证；Mock 明确不写真实日历数据，也不代表可信 Android Host 审查。
- Native 与 Clear 的完整操作对照、其它事件类型的设备交互、键盘/窗口行为、无障碍、取消/恢复及设备 parity 尚未验证；SEP-05 和总体分离验收保持 `NOT TESTED`。

## Git 交付

- 分支：`feat/skin-first-separation`
- 实现及合同提交：`0f944401ca28705429383955489fd70cc83b5426`，提交说明：`feat(分离): 支持宿主桥事件类型转换`
- 已推送：`origin/feat/skin-first-separation`
- 本次提交只含 Host 桥实现、两份 JVM 测试和三份桥接合同/矩阵文档。并行修改的 `docs/agent-handoff/PROGRESS.md` 与 `tools/skin-studio/default-clear/source/` 文件保持在工作树中，没有暂存或覆盖。

## 下一步

此前建议的 AVD 运行已由后续证据补做；这不代表剩余转换组合或整体 SEP-05 已关闭。

## Clear UI follow-up — `kind` 转换入口（2026-10-28）

### 实现

- `tools/skin-studio/default-clear/source/web/edit.js`：正式编辑与草稿编辑都显示无障碍标签为“事件类型”的四选项控件，并通过现有 `editor.patch` / `deck.patch` 发送 `kind`。`SINGLE_OCCURRENCE` 表单隐藏该控件，继续只允许现有标题和时间字段。Host 的 kind 投影到达时，选择控件失焦后按权威表单重新渲染相关时间字段。
- `tools/skin-studio/v3-studio.js`：预览 Mock 对四种合法 kind 执行对应的字段锚点转换，并把正式编辑保存留在显式、可取消的非写入 Mock 审查中。Mock 不实现或授予 Host 权限。
- `tools/skin-studio/tests/e2e_v3_source.cjs`：实际 Edge Studio 页面覆盖草稿 ACTIVITY→DEADLINE、正式编辑 ACTIVITY→ALARM、`SINGLE_OCCURRENCE` 无类型控件、整系列保存进入 Mock 审查、取消后保留表单，以及重复事件后续编辑流程。
- `WebSkinKindConversionInstrumentedTest.kt`：使用随机隔离 SQLite 和真实 PowerCal Compose + Clear WebView，断言单次范围隐藏类型控件、整系列显示四种选项、转换未审批前不改 Store、取消无写入，批准后保留 `eventSeq` 和重复规则、revision 增一且只新增一个 commit。
- 从 canonical `default-clear/source` 重新生成并验证 APK bundle；本次只提交本任务生成的 bundle，不包含并行 worktree 中尚未提交的其它 Clear 源文件。

### 回归与证据

- 详细证据目录：`.local/android-test/reports/separation-web-kind-conversion-20261028/`，包含最终日志、失败尝试日志、83 个完整 JUnit XML、lint XML 和 Debug/AndroidTest APK。
- JavaScript 语法检查：`node --check` 对 `edit.js`、`v3-studio.js` 和 `e2e_v3_source.cjs` 均通过。
- Edge Studio E2E 命令：`$env:CHROME_PATH='C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'; node tools/skin-studio/tests/e2e_v3_source.cjs`。最终诊断运行和重复运行均通过，覆盖源码/项目/资源导入导出往返，以及上述 kind 转换和 Mock 审查。两份日志 SHA-256 均为 `EEB107ECAF1C569053CD46D7798911262D4F9851CF303EAB1EDD3E4CFB6FBE5C`。此证据使用 Mock Host，不是 Android WebView。
- Skin Studio Python 全套：**51/51 PASS**。日志 SHA-256 `E511607A75563FDB52CDF49D2F0D8A3DB505067852BF8BB5586AB88475854DD8`。`build_default_clear.py --verify` 通过，8 files / 377,973 bytes；bundle SHA-256 `683E1CF8FEDD5F46A7099B7DBBF8A637EFD60CEAA2647EFCA25EA2C3A329EDD1`。
- 完整离线 Gradle 命令 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain`：**601/601 JVM tests、83 suites、0 failures/errors/skips、88/88 tasks**；source boundary、Debug / AndroidTest APK 和 lint 通过，lint **0 errors / 52 warnings**。完整日志 SHA-256 `9393A342DEC973CDFCB345B9462E2309B1671DB14C7431FE0D064A82D9EB3C27`。83 份 JUnit XML 已归档。AndroidTest Kotlin 定向编译也成功，日志 SHA-256 `C1614A151E3851BB4F52A42CC9BF8348445F9D7A839BC94305BEC4A24D72A3C8`。
- Debug APK SHA-256 `BE9FFF581016286D36B42DF82E9277DCEE2628E2581F7550FD92C93594EA5FCC`；AndroidTest APK SHA-256 `60F08FFC420D8C67860FE9D85938C5237CF3C48CD49D5EFC260DC1187C5C9B90`。
- 截至 2026-10-28，该次 follow-up 的真实 AVD 未测；ADB daemon 无法启动，两个检查位置都没有 emulator launcher。2026-10-31 后续 API 35 AVD 配对回放已补做，见本文末尾。实体设备仍未触碰。
- 迭代中保留了一次 Studio E2E 能力声明断言失败日志（SHA-256 `A6B3C47F6651874259F3511AF7F5325E36FF28E816F3DAABE6030D6634E74E42`）；随后增加实际值诊断，两次诊断运行均通过。另两次更早失败分别由未重建的隔离 bundle 和跨 frame 测试误查 parent DOM 引起，均已纠正测试准备/断言路径。最终结论以最后两次 E2E 通过及 51/51 Studio 全套为准，同时保留上述失败日志供追溯。

### Git 与状态

- 实现/测试/bundle 提交：`3c1ec3a8635fd0816647fe1de0985f7d329e0a38`，`feat(分离): 为 Clear 编辑器加入事件类型转换`，已推送至 `origin/feat/skin-first-separation`。
- 本 follow-up 的报告和矩阵另作文档提交。共享 `docs/agent-handoff/PROGRESS.md` 及并行 Clear 源码修改仍归其他 Agent，本轮未编辑、暂存或提交。
- 截至 2026-10-28，该补丁只补齐 UI 入口和 Studio 支持；Host 原转换由此前提交 `0f944401ca28705429383955489fd70cc83b5426` 提供。当时真实 WebView 和 Native/Skin 配对尚未执行；后续证据见下。

## API 35 Native/Clear 类型转换配对补证（2026-10-31）

- 新增 `sameFormalAlarmConversionToActivityProducesEquivalentNativeAndClearRecords`，在隔离的同 fixture Stores 中分别打开 Native 正式编辑器与已打包 Clear WebView。Native 通过触屏选择 ACTIVITY 并输入结束时刻；Clear 经现有 `kind` 选择字段发出 ACTIVITY patch，再输入同一结束时刻。Native 保存、Clear 实际 `SOURCE_TOUCHSCREEN` 保存和 Host 审查取消/批准均走真实 Host/Store 路径。
- 审查前两侧正式 ALARM 均未改变。Clear 取消后 Store 仍为 1 commit；再次批准后两侧均得到相同 ACTIVITY `DraftForm`，开始/结束时刻为本地 15:00/16:00，保留 `eventSeq=1`，revision 为 2。最终两 Store 均为 2 commits、1 identity、next sequence 2、0 drafts、0 pending。触屏 Save 轨迹的 `isTrusted=true`。
- 目标配对 **1/1 PASS**；完整 `WebSkinAllKindsTrustedWriteInstrumentedTest` 在 Android 15/API 35/WebView 124（`emulator-5570`）**14/14 PASS**。测试类日志 SHA-256 `E322B9CEB3B7D9529871171DF5D502FBBD8153EACCCDCFC0426205BF036D4186`；目标 logcat SHA-256 `24F9369A8DCF9432383A6335A605B33E53BCDBD869BBD827F90C18FF38E0968A`。
- 完整离线 Gradle 门禁 `:app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 成功：**784/784 JVM，125 suites，0 failures/errors/skips；171 tasks（12 executed、159 up-to-date）**；Lint 0 errors / 55 warnings。日志 SHA-256 `A3AC7C4C384860A3089E94CA0D92E0ED371B52586CE248B2B8CB878F98F71CDE`。Debug APK SHA-256 `83C9EDAD5DBE47F3D78C5E9BC35435CAD59C674FBF79D66C6054A982C51D767D`；AndroidTest APK SHA-256 `A2A3A182182828D71E444C3E94338C14C1651E7C3A78465C38FA7C4C70C3B719`。
- 完整本地证据和哈希索引：`.local/android-test/reports/sep05-kind-conversion-parity-20261031/manifest.json`。该增量只改 AndroidTest 与状态文档，无生产代码或 Studio 源码变更。其他转换方向、Studio 编辑后构建/导入并在 Runtime 复播、TalkBack 和实体/OEM 仍未验证；SEP-05 与整体分离继续 **PARTIAL / HOLD / INCOMPLETE**。
