# PC-ISSUE-SEPARATION-WEB-KIND-CONVERSION

## 状态

**PARTIAL / OPEN**。本报告覆盖 Web Host Bridge 对事件类型转换的支持，Host 侧定向实现及回归已通过并推送。Clear 皮肤编辑器仍没有类型选择器，因此 Native/Skin 功能等价未完成；SEP-05 与整体 parity 不能据此签核为 PASS。

## 需求与复现

Native 正式事件编辑器允许将事件转换为 `ALARM`、`ACTIVITY`、`DEADLINE` 或 `MEMO`，转换规则由冻结的 `TemporalConversion` 决定。Web 皮肤应通过既有 Host 编辑会话和草稿卡组执行相同转换，并保持 Host 对正式数据写入的审查边界。

修复前，`editor.patch` / `deck.patch` 的字段白名单不含 `kind`，`WebEditWire.patch` 也没有对应转换分支。以 `kind` 发出语义 patch 会被判为不可写字段；正式编辑的相关路径返回 `invalid`，重复实例桥接在解析命令时拒绝该字段。修复前测试运行确认：22 项中 3 项失败，具体失败见红测证据。

## 根因与修复

根因是 Web 编辑协议没有把 Native 已支持的事件类型转换接入 Host 表单 patch；现有正式保存前 Host 审查和单次重复实例字段限制本身已存在，但无法收到有效类型转换后的表单。

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

- Android WebView / 模拟器：本次未运行，状态 `NOT TESTED`。
- 实体/OEM 设备：本次未运行，状态 `NOT TESTED`。
- Clear UI：类型选择器未实现；`tools/skin-studio/default-clear/source/web/edit.js` 由并行 Agent 持有，本轮没有修改该文件，也没有把它纳入提交。需要协调其文件所有权后补上控件与端到端交互回归。
- Native 与 Clear 的完整操作对照、键盘/窗口行为和设备 parity 未验证；SEP-05 及整体 parity 保持 `NOT TESTED`。本次 Host 桥定向测试不能替代这些验收。

## Git 交付

- 分支：`feat/skin-first-separation`
- 实现及合同提交：`0f944401ca28705429383955489fd70cc83b5426`，提交说明：`feat(分离): 支持宿主桥事件类型转换`
- 已推送：`origin/feat/skin-first-separation`
- 本次提交只含 Host 桥实现、两份 JVM 测试和三份桥接合同/矩阵文档。并行修改的 `docs/agent-handoff/PROGRESS.md` 与 `tools/skin-studio/default-clear/source/` 文件保持在工作树中，没有暂存或覆盖。

## 下一步

协调 `edit.js` 所有权后，为 Clear 编辑器加入四种类型的选择入口，复用 Web Host `kind` patch，并验证默认值/转换结果、单次重复限制、正式保存审查和取消路径。再执行实际 Android WebView 回归；完成前 SEP-05 与整体分离验收维持 `NOT TESTED`，不据此宣布功能 parity 或切换 GO。
