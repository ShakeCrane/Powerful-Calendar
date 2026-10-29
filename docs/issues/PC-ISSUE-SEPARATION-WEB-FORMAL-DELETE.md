# PC-ISSUE-SEPARATION-WEB-FORMAL-DELETE

## 状态

**历史状态（2026-10-28）：PARTIAL / OPEN**。Host 桥的受保护删除流程、Clear 删除入口及 Skin Studio Mock 已实现并通过 JVM/浏览器回归；API 35 WebView instrumentation 因 AVD 断连未取得测试结果。SEP-07 与整体 Native/Skin parity 仍为 `NOT TESTED`，不能把浏览器 Mock 当成设备行为或完整功能等价。最新重建验收 `S0-ED-05` 的状态见本文末尾 2026-10-30 复核记录。

## 根因与 Native 对照

Native 正式事件编辑器调用 `EditSession.delete()`，最终复用 `PowerStore.deleteFormalEvent()`。删除会创建 tombstone/DELETE 提交、取消提醒意图，并把相关待处理草稿标为 `DISCARDED`。

原始基线中 Web Bridge 拒绝 `editor.delete`，没有可从 Skin 请求的正式事件删除流程。Host Compose 确认视图已能展示 DELETE 操作，但其对应路径只服务已有草稿卡组删除；正式编辑会话缺少调用 Native `EditSession.delete()` 的受审查命令。该 Host 缺口已由 `fc5be0b` 修复；本 follow-up 解决 Clear 编辑器未暴露该命令的问题。

## 修复

- `WebEditCommand.DeleteEvent` 和 `editor.delete` wire 命令只接受当前 `sessionId`；未知字段、额外 `force` 参数及无效会话不会解码。
- 命令仅捕获 Host 私有的当前事件、活动 `EditSession` 和表单快照，返回 `confirmation_required`。该命令本身不写数据库。
- Host 确认时重核会话对象、会话 ID、表单快照及正式记录完整版本；任一内容变化时返回 `conflict`，保留用户的新数据。
- 用户取消时不写库并保留编辑会话。批准后仅调用原有 `EditSession.delete()`；成功关闭编辑会话，并依照 PowerStore 现有合同保留 DELETE 提交、墓碑、提醒取消及草稿 `DISCARDED` 记录。
- requestId 重放返回已完成结果，不重复创建 DELETE 提交。重复事件删除确认文案明确说明会删除整个正式系列及所有实例，和 Native 当前整项删除语义一致。
- 修改文件：`WebSkinEditBridge.kt`、`PowerCalApp.kt`、`WebSkinEditBridgeTest.kt`、Host Bridge 契约、编辑桥文档和分离 parity 矩阵。
- 2026-10-28 Clear follow-up：编辑器按事件类型显示“删除正式事件”或“删除整个重复系列”，保留无障碍标签，在无有效会话或等待 Host 确认期间禁用，并只发送现有 `editor.delete` / 当前 `sessionId`；Clear 不接触数据库。
- Skin Studio Preview Mock 显式标示不写入真实数据。取消保留编辑器，批准只关闭预览 Mock 编辑器；它不代表 Android Host 批准或真实删除。

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

## 2026-10-28 Clear 与 Studio follow-up

- `tools/skin-studio/default-clear/source/web/edit.js` 增加正式删除按钮、重复系列提示、无障碍名称、会话/确认期间禁用状态及 `editor.delete` 路由。没有新增 Skin 写入能力，也没有弱化 Host 确认。
- `tools/skin-studio/v3-studio.js` 增加仅用于预览的删除 Mock；取消时保留编辑器，批准时只关闭模拟编辑器，并显示“不写入真实数据”。`tools/skin-studio/tests/e2e_v3_browser.cjs` 增加浏览器场景；另以隔离 Edge/CDP 对同一预览运行了按钮、取消、批准路径。
- 当前 Clear 包通过 `python tools/skin-studio/build_default_clear.py --verify`：8 files / 377853 bytes，SHA-256 `1249AC2413429D6121AE94F896155BD1B0D85C699B640479E4909C9879BCDAA6`。
- Skin Studio 全套 `python -m unittest discover -s tools/skin-studio/tests -v`：**51/51 PASS**。Edge/CDP 浏览器 Mock：**PASS**；确认删除按钮可访问、取消保留编辑器、批准后预览关闭且 Mock 明示无真实写入。日志：`.local/android-test/reports/skin-separation-formal-delete/formal-delete-studio-cdp.log`。
- JVM 定向回归：`WebSkinEditBridgeTest` 16 项 + `WebSkinRecurrenceBridgeTest` 10 项，**26/26 PASS**。完整离线 Gradle 命令再次通过 **601/601 JVM tests、83 suites、0 failures/errors/skips、88/88 tasks**；lint **0 errors / 52 warnings**。AndroidTest instrumentation 源码编译成功。Debug APK SHA-256 `FC0C804226CFC01217171B7A02D64F6E82A623C201DA35EE04EBDE12C8816F9E`；AndroidTest APK SHA-256 `F8D4C2AD785561410D573C9D47EF4F2EDD4A0B6B3154ECF539F6E681158EA794`。完整日志 SHA-256 `BA6EC483D63C612A2161A8C28CB09A6263FAA6FE342A4EF137584ECFB745B41`；结构化证据 `.local/android-test/reports/skin-separation-formal-delete/verification.json`。
- API 35 instrumentation 首次失败在测试等待重复事件标题字段：重复编辑器要求先选择编辑范围，测试原先的等待条件不适用；已改为等待编辑器及删除按钮。修正后再次运行时 AVD 在返回 JUnit 结果前断连，Gradle 报 `device 'emulator-5554' not found` / `Expected 1 tests, received 0`。因此**没有 Android WebView PASS**。当前 `E:\android-sdk` 不含 `emulator` 启动器，`adb devices -l` 无设备；未清理、重置或重启其他 AVD。断连 XML：`.local/android-test/reports/skin-separation-formal-delete/avd-disconnected.xml`。
- 实体/OEM 测试未执行。SEP-07 与 SEP-16 仍 `NOT TESTED`；浏览器 Mock/JVM/编译证据只覆盖本 follow-up 的代码层面。

## Git 与工作区

- 分支：`feat/skin-first-separation`
- 实现及合同提交：`fc5be0b0a72628efbf4aa11b66824c6fa305af25`，提交说明 `feat(分离): 增加正式事件受保护删除`，已推送至 `origin/feat/skin-first-separation`。
- 本报告作为紧随实现提交的独立文档提交，并推送至同一分支。
- 当前 follow-up 实现提交 `cb5e23d1ead0e4d7d21ae717bbf763685747f5e6`，提交说明 `feat(分离): 在 Clear 提供正式事件删除入口`，已正常推送至 `origin/feat/skin-first-separation`。完整 Gradle / Studio / CDP 证据对应该提交中所含的同一工作树快照；本地 `verification.json` 记录其执行结果和制品哈希。
- 并行改动 `docs/agent-handoff/PROGRESS.md`、`project.json`、Inter 字体许可证、`web/index.html` 和 `web/style.css` 保持未暂存；本 follow-up 不拥有这些变更。

## 后续

在可用 API 35 虚拟设备上重跑 Clear WebView 确认/取消/重复请求 instrumentation，并完成冲突对照、正式事件恢复及草稿恢复的 Native/Skin 等价。SEP-07 仍开放；整体分离与 cutover 不因本切片改变状态。

## Separation S0-ED-05 current verification (2026-10-30)

- **验收状态：`PARTIAL / HOLD`。** `S0-ED-05` 是重建验收 ID（`RECONSTRUCTED`），不声称恢复了历史 209 项中的原始编号。
- **分支：** `codex/separation-s0-timeline-drag`。
- **测试提交：** `3b0278aca9aaada2c7270b48bfc3f89832854f6e`（`test(分离): 覆盖重复系列正式删除`，已推送）。
- **代码结论：** 没有复现生产缺陷。Host 将删除作为待审查操作保存当前正式记录、活动编辑会话与表单快照；审批时再次核对会话、表单和完整正式记录，并由 `EditSession.delete()` / `PowerStore.deleteFormalEvent()` 在事务内再次核对 editor lease 和 expected revision。Store 删除会生成墓碑和 DELETE commit，取消提醒意图，并将关联的活动草稿置为 `DISCARDED`。本轮只新增了此前缺失的重复系列 JVM 回归，没有更改生产代码。

### 本轮测试和实际结果

新增 `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridgeTest.kt` 测试 `formalRecurringDeleteCancelsTheWholeSeriesAndDiscardsItsPendingEdit`：创建三次每日重复的正式 Activity 和一条关联待处理修改，先取消 Host 删除审查，再以新请求重新发起并批准。测试断言取消保留正式记录、重复规则、待处理修改、永久身份及提交数；批准后正式记录不再出现在活动 Store 列表、同一身份保留 tombstone 且 revision 增一、墓碑保留原重复规则、待处理修改被丢弃、恰好生成一项提交，重复审批请求不再重复提交。

| 检查 | 结果 | 可追溯证据 |
| --- | --- | --- |
| 新增重复系列删除回归 | `PASS` | 单例强制运行 **1/1**，0 failures/errors/skips；日志 SHA-256 `5308ED25933031B3197DE3346B51C3303E460A7B0A5B94B587A5FC11069B9D47`，文件 `.local/android-test/reports/separation-s0-ed05-20261030/recurring-delete-focused.log`。 |
| 删除与重复事件 Bridge 定向集 | `PASS` | `WebSkinEditBridgeTest` **37/37** + `WebSkinRecurrenceBridgeTest` **12/12**，合计 **49/49**，0 failures/errors/skips；`--rerun-tasks` 强制运行，32 个 Gradle tasks 执行。日志 SHA-256 `1D935A3AC4C28038B9139A41962AC7138A2C5DEDC4D09CB7B1239E94A791FD3C`。 |
| 完整离线 Android 门禁 | `PASS`（设备部分除外） | `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain --max-workers=1` 成功；完整 JVM 结果 **725/725，99 suites，0 failures/errors/skips**；88 actionable tasks（5 executed / 83 up-to-date）；Lint **0 errors / 53 warnings**。Gradle 日志 SHA-256 `875694B0B7BB8D612709B9F64D6193EFF608770479427598B664F38D3E789883`。 |
| 全 JVM 强制重跑 | `PASS` | `:app:testDebugUnitTest --offline --rerun-tasks --console=plain --max-workers=1`；32/32 tasks 执行；从 99 份 JUnit XML 汇总为 **725/725**、0 failures/errors/skips。日志 SHA-256 `DF9BEB898CBCA8C183CA48504BA9124594214EA47AFF9EEDAD99DFA791E201F4`。 |
| Skin Studio / Clear 包 | `PASS` | Skin Studio Python **61/61**，日志 SHA-256 `D2BAB439FB05A46D2568A53FD8389D850964486157BA10C1007A29E56C11C15B`。`build_default_clear.py --verify` 验证 9 files / 394,087 bytes，Clear SHA-256 `544eb2b9ec1bd1ea3c3cf9d5949d944cba48018bc08478f60c08eea5a4e1cf3f`；校验日志 SHA-256 `DF604CEA83C0DBB1A255BD9579EE9E8587260FFD4636E67901D2E9E63D9EFD5B`。Studio Mock/CDP 删除交互证据是旧基线 `PREVIOUS PASS`，来源为本报告上文所列的 2026-10-28 记录；不当作当前 Android WebView 证据。 |
| APK | `PASS`（仅构建/组装） | Debug APK 67,513,497 bytes，SHA-256 `71C8E45C967CAFDA3BC77FBCD2062AD25D20801B2CDFE8062DCFFAB4B24B097C`。AndroidTest APK 1,557,196 bytes，SHA-256 `371A9C1AF76B5F0530FE4A69FA2FAF9BF1AFA053256DC8B24F7264129930653E`。仪器测试源码已编译进 APK；未安装或执行。 |
| Android WebView / 模拟器 / 设备 | `BLOCKED / NOT TESTED` | `WebSkinFormalDeleteInstrumentedTest.formalRecurringEventDeleteWaitsForHostConfirmationAndCancellationPreservesSeries` 当前只确认源码编译。`E:\android-sdk\platform-tools\adb.exe devices -l` 没有设备；`E:\android-sdk\emulator\emulator.exe` 不存在。TalkBack、实体/OEM 与真实 WebView 确认、取消、并发冲突及重复请求均未运行。 |

详细忽略日志位于 `.local/android-test/reports/separation-s0-ed05-20261030/`。本轮没有导出用户数据库、生产数据、凭据或 AVD 数据。因 Android WebView 和设备行为没有实际运行，`S0-ED-05` 保持 **`PARTIAL / HOLD`**；新增加的 JVM 覆盖和全门禁通过不等于设备签核。
