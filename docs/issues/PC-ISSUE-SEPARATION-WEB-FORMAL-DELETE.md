# PC-ISSUE-SEPARATION-WEB-FORMAL-DELETE

## 状态

**历史状态（2026-10-28）：PARTIAL / OPEN**。Host 桥的受保护删除流程、Clear 删除入口及 Skin Studio Mock 已实现并通过 JVM/浏览器回归；API 35 WebView instrumentation 因 AVD 断连未取得测试结果。SEP-07 与整体 Native/Skin parity 仍为 `NOT TESTED`，不能把浏览器 Mock 当成设备行为或完整功能等价。最新重建验收 `S0-ED-05` 的状态见本文末尾 2026-10-30 复核记录。

**当前状态（2026-10-31）：PARTIAL / HOLD。** API 35 Android WebView 的重复系列删除、Host 取消/批准与 Store 删除闭环已经通过实际 Android 触屏 3/3；整项仍缺并发冲突/恢复、TalkBack、完整 Native/Clear 对照及实体/OEM 验收，不代表 SEP-07 已签核。

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

## 当前未完成事项

实际 Android 触屏确认/取消/批准已在 2026-10-31 follow-up 完成。仍需覆盖 TalkBack、正式事件并发版本冲突、正式事件恢复及草稿恢复的 Native/Skin 等价回放；SEP-07 保持开放，整体分离与 cutover 状态不变。

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

## API 35 Android WebView follow-up（2026-10-30）

- **窄项状态：`TESTED`；SEP-07 整体仍 `NOT TESTED`。** 在本轮隔离的 `PowerCal_Sep01_API35`（Android 15 / API 35）执行 `WebSkinFormalDeleteInstrumentedTest.formalRecurringEventDeleteWaitsForHostConfirmationAndCancellationPreservesSeries`，最终 **1/1 PASS，0 failures/errors/skips**。测试运行于真实 Android WebView、WebSkin Bridge、Host Compose 确认及隔离 PowerStore；WebView 内的事件与删除按钮由测试在页面 DOM 触发，Host 取消/批准由 Compose 测试语义触发，故不把它描述成真实手指触摸或 TalkBack 验收。
- 取消阶段断言正式重复系列、身份数、提交数与待处理数不变；批准阶段断言正式事件/所有实例从 Store 移除、只增加一项 DELETE 提交、身份数不变、无待处理草稿，编辑器关闭。测试只使用 UUID 数据库、偏好和缓存名称空间，并在 `finally` 删除自身 fixture；没有打开或清理应用默认数据库。
- 首次运行在取消断言处发现既有测试把 `Int` 返回的 `pendingCount()` 与 `Long(0L)` 比较；已改为类型一致的 `0`。第二次 AVD 在 UTP 结果收集阶段断连，报告为 `Expected 1 tests, received 0`，不计 PASS。使用同一隔离 AVD、无 wipe 重启后第三次运行通过。相关日志：`.local/android-test/reports/sep07-formal-delete-api35-20261030/`。

| 证据 | 结果 |
| --- | --- |
| `connected-test-run-3.log` | SHA-256 `5C3C4D412A20DB99E63D128E1CAF40084D36A6CF0494098412914F736EF98861` |
| AndroidJUnit XML（1 test, 0 failures） | SHA-256 `1DFE6BD8EDB22F993DB39CB3EC7523D8EF65374EF63A646C68810AC254AFFBEA` |
| Android test result PB | SHA-256 `ACD1EC1DB446044598C3D4A6F84713C2E3AA14ECDFAAD57F0C2C315CFAB3C791` |
| 运行期间 logcat | SHA-256 `99698B115B515E602416AE4FE67EE14F60D5BFE4609E3B6D01D847EAEEE6BE00` |

截至 2026-10-30，SEP-07 尚缺真实触摸与 TalkBack、正式事件并发冲突、恢复工作流、Native/Clear 同数据完整回放及实体/OEM 验收。次日的实际触摸验证见下节；其余未完成项仍保持未签核。

## API 35 touchscreen follow-up（2026-10-31）

- 在 `codex-clear-api35`（Android 15 / API 35，`emulator-5568`，WebView 124.0.6367.219）对当前集成 Clear 执行新增的 `WebSkinFormalDeleteTouchInstrumentedTest.touchscreenDeleteWaitsForHostApprovalAndCancelPreservesSeries`。测试从实际渲染 WebView 测量事件和删除按钮矩形，将屏幕坐标转换后发送 Android `SOURCE_TOUCHSCREEN` `MotionEvent`，并要求 WebView 产生 `event.isTrusted == true` 的点击及 `deleteEvent` action；不是 DOM click 或 Compose 语义代替触摸。
- 测量记录：事件入口 CSS 矩形 `(216.095,471.155)-(300.095,515.155)`，屏幕触点 `(676.56,1429.72)`；删除按钮 CSS 矩形 `(209.595,728.238)-(343.548,770.238)`，屏幕触点 `(724.993,2101.522)`。触摸删除后 Host 确认出现；取消不改变正式系列、identity 数、commit 数或 pending 数。再次触摸并批准后，所有系列实例消失，恰好增加一项 commit，identity 数不变且无 pending 草稿。
- 同一直接 instrumentation 命令连续 **3/3 PASS**。第一次尝试暴露测试等待 Compose 对话框完全收起前便再次触摸的 harness race；保留失败日志，随后增加对话框消失、Compose idle 和短暂帧间隔，再连续通过三次。生产代码未因该测试 harness 问题修改。
- 证据目录：`.local/android-test/reports/sep07-touch-delete-20261031/`。三次通过日志 SHA-256：`47C1F2FB371F9BE3FCB951D342348A3C24FCFAEFA92BD58B6B7E37A2BFB3EC55`、`FDF271E3C497F857F37A7312AEFC0839BC0C61FD6971DD73074953E35AB3541C`、`5A76B649A1C2ACBADB31C8F032534E9C3179E18693D8032A150BDDD29764C784`；失败 harness 日志 SHA-256 `D9376ED30923255354744CFDA7AA4E8A7FB2D4005A05D4E319820130C03F96AF`；实际坐标及可信点击轨迹 SHA-256 `2A2B42B6B0B839A9EE059B9BA9E2054E5702C5B21B89B2413FEBA794E40A2C54`。
- 同一工作树完整离线门禁：`verifySkinSourceBoundary`、`verifyNativeSkinBoundary`、Debug/AndroidTest 组装、Lint 和 **784/784 JVM tests（125 suites，0 failures/errors/skips）**全部通过，171/171 tasks 成功；Lint 0 errors / 55 warnings。完整日志 SHA-256 `73797AAFB602EC08E3689C0CFC2ECA52E85799A9476D9844B9AEC966C1516A90`。Debug APK SHA-256 `779B0CF777BD5E1EE377249CD4156DB426DC87690B6D35EDD458E5F27A27CD1B`；AndroidTest APK SHA-256 `0749F2A89444948719C48393D05028D316164F79244A6FAB842650E973655016`。该门禁工作树还含两份其他 Agent 的未提交 AndroidTest 源码；本次提交只包含本测试与其文档，未包含那两份文件。
- SEP-07 继续 **PARTIAL / HOLD**：TalkBack/无障碍实际操作、并发版本冲突、正式事件恢复、完整同数据 Native/Clear 回放及实体/OEM 验收仍未完成。
