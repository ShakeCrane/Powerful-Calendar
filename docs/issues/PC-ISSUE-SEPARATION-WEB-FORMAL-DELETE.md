# PC-ISSUE-SEPARATION-WEB-FORMAL-DELETE

## 状态

**历史状态（2026-10-28）：PARTIAL / OPEN**。Host 桥的受保护删除流程、Clear 删除入口及 Skin Studio Mock 已实现并通过 JVM/浏览器回归；API 35 WebView instrumentation 因 AVD 断连未取得测试结果。SEP-07 与整体 Native/Skin parity 仍为 `NOT TESTED`，不能把浏览器 Mock 当成设备行为或完整功能等价。最新重建验收 `S0-ED-05` 的状态见本文末尾 2026-10-30 复核记录。

**当前状态（2026-10-31）：PARTIAL / HOLD。** API 35 Clear WebView 已通过触屏删除取消/批准、并发版本冲突拒绝、无修改旧会话安全退出及墓碑恢复取消/批准并复用原身份；同一隔离 Memo 与 Activity fixture 的 Native/Clear 正式删除结果均已匹配（2/2）。SEP-07 仍缺 Alarm/Deadline、重复范围、TalkBack 及实体/OEM 验收，不代表该项已签核。

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

实际 Android 触屏删除、并发版本冲突和正式事件墓碑恢复已在 2026-10-31 follow-up 完成。仍需覆盖 TalkBack、完整同数据 Native/Skin 删除与恢复等价回放及实体/OEM 验收；SEP-07 保持开放，整体分离与 cutover 状态不变。

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
- SEP-07 继续 **PARTIAL / HOLD**：API 35 已覆盖触屏删除、并发版本冲突和墓碑恢复；TalkBack/无障碍实际操作、完整同数据 Native/Clear 回放及实体/OEM 验收仍未完成。

## API 35 stale-delete conflict and tombstone restore follow-up（2026-10-31）

新增 `WebSkinFormalDeleteTouchInstrumentedTest.staleDeleteApprovalPreservesConcurrentVersionAndRefreshesClearTimeline` 与 `clearRestoreOfDeletedFormalEventWaitsForHostApprovalAndReusesIdentity`，补齐真实 Android WebView → WebSkin Bridge → Host Compose → 隔离 PowerStore 的版本冲突和恢复路径。未改变冻结事件语义或 Native UI。

- 并发冲突用例在 Clear 删除审查对话框打开时，通过同一隔离数据库的第二个 Store 连接提交正式事件新版本，再批准旧删除审查。Host 拒绝过期删除；新版仍活动、revision 为 2、只增加并发写入那一条 commit、identity/序号不变、没有待处理草稿。旧会话无有效 Diff，可安全退出；`CloseEvent` 完成后 Clear 时间轴显示并使用新版记录。单项 API 35 PASS。
- 恢复用例通过 Clear 正式删除 Host 确认生成 tombstone，再从“已删除事件”打开同身份恢复草稿。点击发布只进入恢复审查准备，不提前恢复事件；Host 取消保留墓碑和恢复草稿。再次申请并经 Host 明确批准后，原 eventSeq 恢复、revision 为原始 revision +2，正式标题和 identity 保持、序号水位不变、pending 清零。单项 API 35 PASS。
- 旧编辑器关闭的根因是 `CloseEvent` 对所有会话都先 checkpoint；干净会话遇到新 revision 时 checkpoint 拒绝，导致编辑器无法退出。`EditSession.formalVersionIsCurrent()` 为 Host 提供只读版本判定；Bridge 仅在“无有效 Diff 且版本已变化”时无写入释放会话，dirty stale 会话仍保留输入并拒绝不安全关闭。PowerCalApp 仅在正式 `CloseEvent` 结束后刷新投影，避免把普通 `CloseDeck` 混入刷新路径。

### 最新验证

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| API 35 删除触屏、冲突、恢复 instrumentation 类 | **3/3 PASS** | Android 15 / `emulator-5570` / WebView 124；最终日志与 SHA-256 记录在 `.local/android-test/reports/sep07-delete-conflict-20261031/`。删除触屏用例发送 `SOURCE_TOUCHSCREEN` 并校验 `event.isTrusted=true`；冲突和恢复用例使用真实 Clear WebView 与 Host 对话框。 |
| JVM 冲突关闭回归 | **PASS** | `WebSkinEditBridgeTest.formalDeleteReviewRejectsChangedRevision` 验证干净 stale editor 关闭不修改新版；`formalEditorSaveReviewRejectsChangedRevisionAndKeepsTheProposal` 验证 dirty stale editor 保留输入。定向 `WebSkinEditBridgeTest` 与相关 Compose suites 已通过。 |
| 完整离线 Android 门禁 | **784/784 JVM、125 suites、0 failures/errors/skips；171/171 tasks PASS** | `verifySkinSourceBoundary`、`verifyNativeSkinBoundary`、Debug/AndroidTest APK 组装和 Lint 均通过；Lint 0 errors / 55 warnings。最终门禁日志、APK hashes、设备日志 hashes 见证据目录。 |
| Skin Studio | **63/63 PASS** | `.local/android-test/reports/sep07-delete-conflict-20261031/skin-studio-tests.log`。本次不改 Studio 或 Clear 源码。 |

### 可复核 SHA-256

| 产物 | SHA-256 |
| --- | --- |
| 完整门禁日志 `final-with-restore-gradle-gate.log` | `7D069648272A5320A3C17219D91E814C393A497A0F948914D3B7765FEF044C49` |
| 最终 API 35 三用例日志 `final-device-class-with-restore.log` | `685550A129E4F48398287B5F9E54DA3C794C0668824184479DF3FC1C8BC20B61` |
| 定向恢复用例日志 `restore-device-third.log` | `FB03D1F982B0E8E15BDEFEF39B95ADDBF83983CEC7483BA23E94D112DBC24E10` |
| Skin Studio 测试日志 `skin-studio-tests.log` | `D975246C79E0CDF09EE56E6A3F3C956723D927316F6D5DCB3E995604DC439B5D` |
| Debug APK `app-debug.apk` | `8CC46EED2FE1569CB349C7C913C3BFFE85344E4C6E9C527342373367C3E8F886` |
| AndroidTest APK `app-debug-androidTest.apk` | `20918717C8692DB695A612DAC5F35F79ADBCAE2412E79E044BD153BC384C1308` |

两次首次恢复尝试的用例等待顺序错误地在“生成恢复草稿”后立即等待 Host 恢复确认按钮，遗漏了用户须先显式点击“发布”才会进入 tombstone restore prompt；保留失败日志并修正步骤后通过。一次早期全量运行因将所有 `closed` 状态都触发 reload，连带使临时 `CloseDeck` Compose 回归超时；刷新条件已收窄至 `CloseEvent`，受影响类定向通过，最终全量回归通过。上述失败均为本次实现/验证路径，未隐藏。

本 follow-up 只签核实际冲突拒绝及恢复闭环。TalkBack、完整 Native/Clear 配对 replay 与实体/OEM 仍为 NOT TESTED；SEP-07、整体 Skin parity 和 cutover 继续 **PARTIAL / HOLD**。本工作树其他 Agent 对 `WebSkinFormalDeleteInstrumentedTest.kt` 与 `WebSkinRecurrenceInstrumentedTest.kt` 的未提交改动没有纳入本次提交。

## Matched Native/Clear formal Memo deletion replay（2026-10-31）

新增 `sameFormalMemoDeleteProducesEquivalentNativeAndClearTombstones`，在两个隔离 Store 中播种完全相同的无日期 MEMO，再分别从 Native Memo 页面和打包的 Clear WebView 执行正式删除。两边在确认前都保持原记录、1 次既有提交、1 个身份、序号水位 2、0 草稿/待处理项；取消不写入。批准后两边生成相同的墓碑业务字段，`eventSeq=1`、`revision=2`、共 2 次提交、1 个身份、序号水位仍为 2、0 草稿/待处理项。Clear 删除按钮由 Android `SOURCE_TOUCHSCREEN` 触发，轨迹显示 `event.isTrusted=true`、action=`deleteEvent`。

- 配对回放 **1/1 PASS**；`WebSkinAllKindsTrustedWriteInstrumentedTest` **10/10 PASS**，源提交 `4b681f4`。API 35 Android 15 / WebView 124 / `emulator-5570`。此前真实删除/冲突/恢复类在旧测试源码上通过 **3/3**。
- 复跑时发现删除按钮测试先执行受 CSS 平滑滚动影响的 `scrollIntoView`，只等到按钮刚进入视口就按中间帧坐标注入触摸。保留失败 XML/logcat；将测试滚动指定为 `behavior:'instant'` 后，`WebSkinFormalDeleteTouchInstrumentedTest` **3/3 PASS**，其中真实触屏取消/批准用例另独立重复 **2/2 PASS**。这是 harness 坐标稳定性修复，没有改 Runtime 或产品交互。
- 在修正后的源码提交 `2e4886a769e026b135ff86db186521e366dc9d4e` 上，边界检查、完整离线 Gradle/Debug 与 AndroidTest APK/Lint 门禁成功（172 tasks；JVM 测试结果为同一产品源码 `4b681f4` 的 784/784、125 suites，修正只改 AndroidTest 文件）；Studio Python **63/63 PASS**。Debug APK SHA-256 `83C9EDAD5DBE47F3D78C5E9BC35435CAD59C674FBF79D66C6054A982C51D767D`；修正后的 AndroidTest APK SHA-256 `C0CF33F244B5708DDFF2BFAF4C66D8DFFAE26BA06FDFEBA539F6FFA93DD82F0F`。
- 代码提交 `2e4886a` 已推送；原配对回放测试在 `4b681f4` 已推送。可复核源码归档 SHA-256 `C02747835A173805A223B5779A4D4ABBD5CA4296A73AB58B8BF6E37220168F27`；门禁、Studio、设备 XML/logcat、失败重现与两次重跑的哈希均列于 `.local/android-test/reports/sep07-native-clear-delete-parity-20261031/manifest.json`（manifest SHA-256 `C3EA64E74209559524F0E3DEB780EF3E87A09CE703AA0ED0ADDE0D9453EE5CB3`）。

本回放只关闭一个 no-date Memo 的 Native/Clear 删除等价样例。TalkBack、Alarm/Activity/Deadline、重复系列/单次范围、更多并发冲突与恢复配对、实体/OEM 仍未完成；SEP-07 继续 **PARTIAL / HOLD**。其他 Agent 的两个未提交 AndroidTest 文件没有被纳入本次提交。

## Matched Native/Clear formal Activity deletion replay（2026-10-31）

新增 `sameFormalActivityDeleteProducesEquivalentNativeAndClearTombstones`。两个隔离 Store 以相同的 ACTIVITY（`eventSeq=1`、revision 1、相同开始/结束时间）开始；Native 从真实时间轴事件卡打开编辑器，Clear 从真实 WebView 时间轴打开同一事件。两边删除请求等待各自 Host 确认，取消后原记录和提交数不变；批准后两边形成相同的 Activity 墓碑，revision 2、共 2 次提交、1 个身份、序号水位 2、无草稿/待处理项。Clear 两次 `SOURCE_TOUCHSCREEN` 点击均报告 `isTrusted=true` 且 action=`deleteEvent`。

- Activity 配对回放 **1/1 PASS**；包含 Memo 与 Activity 配对删除的 `WebSkinAllKindsTrustedWriteInstrumentedTest` **11/11 PASS**，Android 15 / API 35 / WebView 124 / `emulator-5570`，源码提交 `24db390fc0bd1b88c4704929a82fc9eccf7e4310`。
- 同一版本完整 Gradle 边界/单元测试/Debug 与 AndroidTest APK/Lint/设备类门禁成功，172 tasks（6 executed、166 up-to-date）。JVM 产品源码未变；同一基线 `4b681f4` 的 JVM 全套为 784/784、125 suites、无失败/错误/跳过。APK：Debug SHA-256 `83C9EDAD5DBE47F3D78C5E9BC35435CAD59C674FBF79D66C6054A982C51D767D`；AndroidTest SHA-256 `D200E4CC538EFB7BFFF6F24BBDBEF7A6BF19548FC366038D6E7FD84638543EAC`。
- 证据目录 `.local/android-test/reports/sep07-native-clear-delete-parity-20261031/`；本次 clean-source manifest `manifest-24db390.json` SHA-256 `077B656FE13C6D5343ECFD290D5A99D43512A240CCE81794E804376612519EF0`，源码归档 SHA-256 `380D287DDD7159F05B20C714B6E9B4A511A2C15C4A162A4DFF455F29ECB8376D`。实现/测试提交 `24db390` 已推送。

本次扩展只签核 Activity 与 Memo 两个单次正式事件删除配对。Alarm、Deadline、重复系列范围、更多冲突/恢复状态、TalkBack 和实体/OEM 尚未验证；SEP-07 继续 **PARTIAL / HOLD**。两个其他 Agent 的未提交测试文件仍未更改、未暂存。

## Matched Native/Clear formal ALARM and exact-time DEADLINE deletion replay（2026-10-31）

在 `5b253cb` 新增的 `sameFormalAlarmAndInstantDeadlineDeleteProducesEquivalentNativeAndClearTombstones` 中，分别为 ALARM 与精确时刻 DEADLINE 建立相同起始 Store fixture，经 Native 正式编辑器与打包 Clear WebView 执行取消和批准删除。每种事件取消均不写入；批准后 Native/Clear 墓碑业务字段一致，revision 为 2，既有身份与序号不变，计数为 4 次提交（2 次播种、2 次删除）、2 个身份、下一个序号 3，且无草稿/待处理卡。Clear 的取消和批准通过 Android `SOURCE_TOUCHSCREEN` 输入；轨迹中两次 DOM click 均为 `isTrusted=true`、action=`deleteEvent`。精确时刻 DEADLINE 使用日期时间字段，不代表纯日期 DEADLINE。

- 本配对测试 **1/1 PASS**；包含现有 CREATE/EDIT/DELETE/Host-review 与冲突测试的 `WebSkinAllKindsTrustedWriteInstrumentedTest` **12/12 PASS**，设备为 Android 15 / API 35 / WebView 124 / `emulator-5570`。Focused 测试及全类 XML、runner 输出、两类实际触屏轨迹已留存。
- 同一 `5b253cb` 源码的离线全门禁通过：JVM **784/784**、125 suites，无失败/错误/跳过；两项 Skin 边界检查、Debug 与 AndroidTest APK、Lint 通过，172 tasks（23 executed、149 up-to-date），Lint 0 errors / 55 warnings。Debug APK SHA-256 `97EDDB79D0FE5DA0CCCE849CC35D5A2D6CD187E91F28940B73A749B3BF20BA3C`；AndroidTest APK SHA-256 `0AA051A6A1A88BDCB44C5D9E0420DFCFC8CCB711EC3243A27BB3648B64A9B637`；完整门禁/设备类日志 SHA-256 `AFEDCC35B0CBFD70C42E0F5303C34769B5E356F86F70D44172374FE92B3B7BF2`。
- 可复核证据索引 `.local/android-test/reports/sep07-native-clear-delete-parity-20261031/alarm-deadline-full-class/manifest-5b253cb.json`，SHA-256 `6ED5CFADB302AB16C1D1C67B4A4593F4EE9914C0275A915D427C9BC69D408B6D`。测试代码提交 `5b253cb21947660f00f0400ad54dff8511f9f741` 已正常推送到 `origin/feat/skin-first-separation`。

这是 API 35 模拟器上四类事件的单次正式 DELETE 等价证据；没有改 Clear、Host、Core、Native 或 Studio 生产源码。SEP-07/SEP-16 仍为 **PARTIAL / HOLD / INCOMPLETE**：重复系列范围、其他操作配对、恢复/冲突的更广组合、TalkBack 与实体/OEM 验收仍未闭环。本次未触碰两个其他 Agent 对 `WebSkinFormalDeleteInstrumentedTest.kt`、`WebSkinRecurrenceInstrumentedTest.kt` 的未提交修改；本次也未重跑 Studio，因为其源码及接口没有变化。

## Recheck on fetched integration baseline e236aa0 (2026-10-31)

API 35 / Android 15 / WebView 124 on `emulator-5570`: `WebSkinFormalDeleteTouchInstrumentedTest` and the complete `WebSkinAllKindsTrustedWriteInstrumentedTest` passed **16/16**. The run re-exercised actual Clear touchscreen cancel/approve, stale-revision refusal, restoration, and same-fixture Native/Clear formal tombstones for MEMO, ACTIVITY, ALARM and exact-time DEADLINE. The Clear runtime smoke also passed **26/26** across navigation, Memo, timeline/month, AI, Settings and renderer recovery; scoped logcat scan found no uncaught JS syntax/type/reference error or Android fatal exception. No product implementation changed, and SEP-07 remains PARTIAL/HOLD for recurring scopes, broader recovery/conflict combinations, accessibility and physical/OEM evidence. Full XML/logcats and build hashes are indexed at `.local/android-test/reports/skin-first-clear-sep07-20261031/manifest.json` (SHA-256 `87C0C6F00298622B45916C3C9E12A5CB6E991A56ADDB0EC5DA1FB3EBBCD73D42`). The two other-agent test modifications remain outside this change.
