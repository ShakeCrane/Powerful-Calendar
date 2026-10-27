# PC-ISSUE-SEPARATION-WEB-KIND-CONVERSION

## 状态

**PARTIAL / OPEN**。Host 的四类转换 patch 已通过回归；2026-10-28 已为 Clear 正式编辑器和草稿编辑器补上类型选择器，并同步 Studio 非写入预览。完整离线门禁、Edge Studio E2E 和 AndroidTest 编译通过，但 Android WebView/AVD 交互没有执行，Native/Skin 完整等价也未完成；SEP-05 和整体 parity 继续 `NOT TESTED`。

## 需求与复现

Native 正式事件编辑器允许将事件转换为 `ALARM`、`ACTIVITY`、`DEADLINE` 或 `MEMO`，转换规则由冻结的 `TemporalConversion` 决定。Web 皮肤应通过既有 Host 编辑会话和草稿卡组执行相同转换，并保持 Host 对正式数据写入的审查边界。

修复前，`editor.patch` / `deck.patch` 的字段白名单不含 `kind`，`WebEditWire.patch` 也没有对应转换分支。以 `kind` 发出语义 patch 会被判为不可写字段；正式编辑的相关路径返回 `invalid`，重复实例桥接在解析命令时拒绝该字段。修复前测试运行确认：22 项中 3 项失败，具体失败见红测证据。

## 根因与修复

根因是 Web 编辑协议没有把 Native 已支持的事件类型转换接入 Host 表单 patch；现有正式保存前 Host 审查和单次重复实例字段限制本身已存在，但无法收到有效类型转换后的表单。首次 Host 修复后 Clear UI 仍未暴露选择入口；本次 follow-up 补齐该 UI。

在 [WebSkinEditBridge.kt](../../app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridge.kt) 中，将 `kind` 加入受限 patch 字段，并严格解析四种 `TemporalKind` 名称。有效值统一调用 `TemporalConversion.convert(form, target).form`；未知值返回 invalid。该 patch 同时供 `editor.patch` 与 `deck.patch` 使用，不直接写数据库。

正式事件仍沿既有 Host 审查与重新校验路径保存；测试确认转换审查批准后保留原 `eventSeq`，仅在确认后递增 revision，且不创建额外草稿。草稿卡组继续经现有 `DeckController` 转换与暂存。`SINGLE_OCCURRENCE` 仍只允许其原有标题和时间字段，类型转换被拒绝。

更新了 [V3_HOST_BRIDGE_EXTENSION.md](../../docs/V3_HOST_BRIDGE_EXTENSION.md)、[SKIN_EDIT_BRIDGE_S1_S2_2026-10-22.md](../../docs/SKIN_EDIT_BRIDGE_S1_S2_2026-10-22.md) 和 [SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md](../../docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md)。矩阵明确标注 Clear 编辑器尚无类型选择器，SEP-05 保持 `NOT TESTED`。

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

在可用的隔离 API 35 AVD 上运行本报告新增的 `WebSkinKindConversionInstrumentedTest`，确认真实 Clear WebView 的 `SINGLE_OCCURRENCE` 隐藏选择器、整系列转换 Host 审查/取消/批准及正式事件身份保留；随后补齐全四类 Native/Skin 对照。完成前 SEP-05 与整体分离验收维持 `NOT TESTED`，不据此宣布功能 parity 或切换 GO。

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
- 真实 AVD 未测。只读 `adb devices -l` 状态日志 SHA-256 `EE3823CBD4248DAD20FA8E16CC8CB4F1AF488E4D96FBF4C2F97D55117E5B2D82`；ADB daemon 无法启动，两个检查位置都没有 emulator launcher。实体设备没有触碰。
- 迭代中保留了一次 Studio E2E 能力声明断言失败日志（SHA-256 `A6B3C47F6651874259F3511AF7F5325E36FF28E816F3DAABE6030D6634E74E42`）；随后增加实际值诊断，两次诊断运行均通过。另两次更早失败分别由未重建的隔离 bundle 和跨 frame 测试误查 parent DOM 引起，均已纠正测试准备/断言路径。最终结论以最后两次 E2E 通过及 51/51 Studio 全套为准，同时保留上述失败日志供追溯。

### Git 与状态

- 实现/测试/bundle 提交：`3c1ec3a8635fd0816647fe1de0985f7d329e0a38`，`feat(分离): 为 Clear 编辑器加入事件类型转换`，已推送至 `origin/feat/skin-first-separation`。
- 本 follow-up 的报告和矩阵另作文档提交。共享 `docs/agent-handoff/PROGRESS.md` 及并行 Clear 源码修改仍归其他 Agent，本轮未编辑、暂存或提交。
- 本补丁只补齐 UI 入口和 Studio 支持；Host 原转换仍由此前提交 `0f944401ca28705429383955489fd70cc83b5426` 提供。由于真实 WebView 和完整 Native/Skin 回放未执行，本 Issue 仍为 **PARTIAL / OPEN**。
