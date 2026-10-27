# PC-ISSUE-SEPARATION-WEB-FORMAL-DELETE

## 状态

**PARTIAL / OPEN**。Host 桥的受保护删除流程和回归已完成；Clear 编辑器还没有删除控件，当前源码只提示删除需要独立确认。SEP-07 与整体 Native/Skin parity 保持 `NOT TESTED`，不能把 Host 命令存在当成功能等价完成。

## 根因与 Native 对照

Native 正式事件编辑器调用 `EditSession.delete()`，最终复用 `PowerStore.deleteFormalEvent()`。删除会创建 tombstone/DELETE 提交、取消提醒意图，并把相关待处理草稿标为 `DISCARDED`。

Web Bridge 原先拒绝 `editor.delete`，没有可从 Skin 请求的正式事件删除流程。Host Compose 确认视图已能展示 DELETE 操作，但其对应路径只服务已有草稿卡组删除；正式编辑会话缺少调用 Native `EditSession.delete()` 的受审查命令。

## 修复

- `WebEditCommand.DeleteEvent` 和 `editor.delete` wire 命令只接受当前 `sessionId`；未知字段、额外 `force` 参数及无效会话不会解码。
- 命令仅捕获 Host 私有的当前事件、活动 `EditSession` 和表单快照，返回 `confirmation_required`。该命令本身不写数据库。
- Host 确认时重核会话对象、会话 ID、表单快照及正式记录完整版本；任一内容变化时返回 `conflict`，保留用户的新数据。
- 用户取消时不写库并保留编辑会话。批准后仅调用原有 `EditSession.delete()`；成功关闭编辑会话，并依照 PowerStore 现有合同保留 DELETE 提交、墓碑、提醒取消及草稿 `DISCARDED` 记录。
- requestId 重放返回已完成结果，不重复创建 DELETE 提交。重复事件删除确认文案明确说明会删除整个正式系列及所有实例，和 Native 当前整项删除语义一致。
- 修改文件：`WebSkinEditBridge.kt`、`PowerCalApp.kt`、`WebSkinEditBridgeTest.kt`、Host Bridge 契约、编辑桥文档和分离 parity 矩阵。

## 测试证据

### 修复前红测

- 定向测试先新增 `editor.delete` decoder/service 合同后运行，Kotlin 编译因缺少 `WebEditCommand.DeleteEvent` 失败，复现未实现状态。
- 日志：`.local/android-test/reports/separation-20261027/formal-delete/focused-red.log`
- SHA-256：`EA69B0110424E1310D1C221BABA9C6B0A84175F8F838149D1394BAEA375A4B68`

### 修复后定向回归

- 命令：`.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests dev.powerfulcalendar.ui.v2.WebSkinEditBridgeTest --tests dev.powerfulcalendar.ui.v2.WebSkinRecurrenceBridgeTest`
- 选中套件 **25/25 PASS**：`WebSkinEditBridgeTest` 15 项、`WebSkinRecurrenceBridgeTest` 10 项。
- 断言覆盖 schema 约束、取消前后无写入、确认后墓碑/DELETE 提交/草稿丢弃、确认期间 revision 变化导致冲突、成功/取消/冲突命令重放幂等，以及重复实例相关桥接约束。
- 日志：`.local/android-test/reports/separation-20261027/formal-delete/focused-green.log`
- SHA-256：`4EBB4337F1E748CE5139D940DA33AECB6DD93BAC0055ADEC88EDE3C021EAA7C8`

### 完整离线 Gradle 门禁

- 命令：`.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain`
- 结果：**88/88 tasks PASS**；83 个 JVM suite、599 项测试，0 failures / errors / skipped；`verifySkinSourceBoundary`、Debug / AndroidTest APK 组装和 lint 全部通过。
- Lint：0 errors、52 warnings。
- 日志：`.local/android-test/reports/separation-20261027/formal-delete/full-gate.log`
- 日志 SHA-256：`534870BB40FF138D757E21F859A20CE6FB1D419468E21CA56D8CDB3AC776CAE3`
- Debug APK SHA-256：`F8EC6D98B987803D40CC162C616FF1767AE15261866F81D51A5B1462C4FB8DFE`
- AndroidTest APK SHA-256：`EAE898E420416954792224E4EA0407EFC836AD9BF81C5D2151BEC0FA4283EBE6`
- `git diff --check` 对本次拥有的实现、测试及合同文件通过。

## 设备与 Track B 缺口

- JVM/Robolectric 是本次实际运行的行为证据；没有运行 Android WebView、模拟器 instrumentation 或实体/OEM 测试。
- `local.properties` 指向 `E:\android-sdk`，该 SDK 没有 `emulator\emulator.exe`；`adb.exe devices -l` 尝试启动 daemon 时失败（无法读取 ADB server handshake）。本轮设备项为 `NOT TESTED`，没有把 APK 组装误记成设备通过。
- Clear `web/edit.js` 目前还没有 `editor.delete` 按钮；该文件由并行 Agent 持有，本轮没有修改。必须先完成文件所有权协调，再为 Clear 加入确认前的审阅入口、取消/确认交互和无障碍标签，并通过实际 Android WebView 回归，才能考虑关闭 SEP-07。

## Git 与工作区

- 分支：`feat/skin-first-separation`
- 实现及合同提交：`fc5be0b0a72628efbf4aa11b66824c6fa305af25`，提交说明 `feat(分离): 增加正式事件受保护删除`，已推送至 `origin/feat/skin-first-separation`。
- 本报告作为紧随实现提交的独立文档提交，并推送至同一分支。
- `docs/agent-handoff/PROGRESS.md` 和 `tools/skin-studio/default-clear/source/` 存在并行未提交修改；本轮没有暂存、覆盖或提交这些资产。

## 后续

协调 `edit.js` 所有权并完成 Clear UI 操作及 WebView 回归；继续检查冲突对照、正式事件恢复及草稿恢复的 Native/Skin 等价。SEP-07 仍开放，整体分离与 cutover 不因本 Host 切片改变状态。
