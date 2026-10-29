# PC-ISSUE-SEPARATION-DRAFT-DECK-LIFECYCLE — Clear 草稿卡组生命周期与空队列完成态

日期：2026-10-29
状态：**Bridge/Clear 实现、JVM 持久恢复、Studio Preview Mock 与桌面浏览器回归 PASS；Android WebView 生命周期/重启和设备 parity NOT TESTED。`S0-DK-02` 保持 PARTIAL / unsigned。**

## 问题与验收范围

本 Issue 覆盖 `S0-DK-02` 的持久化草稿编辑、暂存、提交、丢弃、撤销和重开恢复。需要区分两个环节：生产路径中 Host/Core 已拥有版本化草稿、checkpoint 和完成统计；Clear Bridge/UI 没有完整呈现 Controller 状态，Studio Preview Mock 也没有模拟相同的卡组生命周期。

本次修复让持久化卡组在最后一张草稿被提交或丢弃后显示准确的空队列/完成状态，并让 Mock 能用于生命周期回归。JVM 测试验证实际 `PowerStore` 关闭重开和新 Bridge 恢复；Chrome 测试使用 Skin Studio 的非写入 Mock。后者不构成 Android WebView 或真实日历写入证据。

## 根因

1. `DeckController.State` 已包含 `notice`、`completion`、`lastDiscardId` 等字段，但 `WebSkinEditService.deckState()` 没有投影 `notice` 和 `completion`。Clear 在卡组清空时因此无法展示本次处理结果，无法区分“尚无待处理草稿”与“处理已完成”。
2. Clear 的非冲突卡组操作区在无当前卡片时仍显示“暂存”和“丢弃草稿”。这些操作没有有效目标，可能给用户造成空操作仍有效的错觉。
3. Studio Preview Mock 中 `deck.undo` 恒返回 `missing`；`deck.commit`/`deck.discard` 直接关闭卡组，未验证 Host 审核、关闭后重开、空队列、撤销或完成投影。该 Mock 不能可靠覆盖现有 Native/Core 语义。
4. 之前缺少 Bridge 级测试：修改后 checkpoint，再关闭 Store、创建新 Store 与 Bridge 并重新打开草稿。已有 Core 测试不能单独证明 Clear Bridge 能恢复正确投影。

## 修复内容

- `WebSkinEditBridge.kt` 在持久化卡组状态中补充 `completion: null | {published, discarded, conflicts}` 与 `notice`；transient 新建会话明确投影为 `null`，不伪造持久卡组处理结果。
- Clear 在没有当前草稿时显示空状态；存在完成统计时显示本次发布、丢弃和冲突数量。空队列不再提供无目标的“暂存/丢弃草稿”操作；撤销只在 Host 提供 `lastDiscardId` 时显示。完成字段和最近丢弃身份也加入渲染指纹，避免状态变更被去重逻辑吞掉。
- Studio Preview Mock 覆盖完整的本地演示状态流：flush 保存表单和版本，close/reopen 恢复；丢弃从活动队列移除，undo 恢复同一张草稿；提交要求单独的模拟 Host 确认；批准只修改 Mock fixture 并返回 `committed_preview_only`，不写 Store/日历。被丢弃或提交的卡片不再参与活动搜索、导航或替换/恢复候选。
- 新增隐藏的专用 `deck-lifecycle` Preview 场景和浏览器 E2E，避免改变 Skin Studio 的普通使用场景。
- 新增 Bridge 回归：重开真实测试数据库与新 Bridge 后恢复 checkpoint 内容、版本与队列数量；验证最后一张草稿丢弃后的完成统计、同会话 undo，以及关闭/重开后撤销身份被清空、Store 中已丢弃行仍保留。
- 更新 Bridge 扩展合同，明确完成摘要是展示状态，undo 与 Native 一致，属于当前卡组会话能力。`DeckController.open()` 会清空 `undoDraftId`、`lastDiscardId` 和 completion；已丢弃 Store 行仍保留，但本次没有引入跨会话的持久化撤销功能。

## 验证证据

环境：Windows PowerShell；Gradle Wrapper 8.14.3；Edge 156；独立 Robolectric 测试数据库；Skin Studio Preview 使用本机 Puppeteer/Edge。所有本地测试产物位于 Git 忽略目录 `.local/android-test/reports/separation-s0-dk-02-20261029/`。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 新增 Bridge 回归 | **PASS**。测试覆盖真实 Store checkpoint、关闭并重新打开数据库、创建新 Controller/Bridge、恢复表单与版本；也覆盖最后一张草稿丢弃后 `completion`、同会话 undo 和关闭/重开后撤销身份被清空、丢弃行仍保留。 | `WebSkinEditBridgeTest.kt` 中 `clearDeckCheckpointRecoversThroughFreshBridgeAfterStoreReopen` 与 `clearDeckProjectsCompletionAndKeepsUndoSessionScoped`；聚焦 Bridge/Controller/Store 类 **146/146 PASS**。日志 SHA-256 `697D2E82EE84BCDFF5B5358EEA198139EBEE6FFDC7B295DDAF3E46DFD1FDE96C`。 |
| 完整离线 Gradle 门禁 | **PASS：639/639 JVM 测试、84 suites、0 failures/errors/skips；88/88 tasks**。包含源码边界检查、Debug APK、AndroidTest APK 构建和 `lintDebug`。AndroidTest APK 仅构建，未安装执行。 | 命令：`.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain`。最终日志 SHA-256 `55112D6CA954DC0153B5A3550B5E7A11E09289FDE4339F97D4A7B470C4B72B29`。JVM XML 汇总：84 suites / 639 tests / 0 failures / 0 errors / 0 skipped。Debug APK SHA-256 `9CA66191D3252DB6325D76BE62F1508E18798D2FBB71DC1E70E8CA1B508EA5F9`；AndroidTest APK SHA-256 `33FC27215E84DE4D2BFCC919C1D46854879AB4A49FE1CAA0A1C45778A0B6DC31`。 |
| Skin Studio Python | **61/61 PASS**。首轮遇到 Windows SYSTEM 所有权触发的嵌套 Git `dubious ownership`；最终运行只对该进程设置 `safe.directory`，未修改全局 Git 配置。 | `skin-studio-python-final.log` SHA-256 `C38E56CA4C241AD9DFA2A3B56B3E76DB9DC1A57973795702D6A5F5D833E8953A`。 |
| Clear 生命周期 Preview E2E | **PASS（桌面 Preview Mock）**。Edge 156 覆盖修改→flush→关闭→重开、丢弃→撤销、Host 模拟确认取消/批准、空队列完成摘要、禁用空表单发布及零页面异常；批准明确标注只改 Preview fixture。 | `tools/skin-studio/tests/e2e_v3_deck_lifecycle.cjs`；日志 SHA-256 `13F4F2747BA6332363274503E40A8F524C0D01150A70764EE5C29D6439794BD1`；完成态截图 `deck-completion-preview.png` SHA-256 `587792B7273CD110FC3D449DB4DD18FFCFE5BEE738B2B0F978B7C55`。 |
| 已有相邻卡组回归 | **PASS**。草稿搜索/选择/导航 E2E 与冲突/恢复 E2E 均复跑通过。 | `browser-deck-search-regression.log` SHA-256 `ED32CA8A9490E983749637A421C7AF336D4B3EDAF29F80063805A84D5DBC009A`；`browser-conflict-restore-regression.log` SHA-256 `4D878FB894119E2BF8BF0684FF8508D1EAFCC97D0214CB2CB4DC411E2AB2BA4F`。 |
| JavaScript、diff 与 Clear 包 | **PASS**。修改的 Studio/Clear/E2E JavaScript `node --check`，`git diff --check` 和 Clear/APK 内资源校验通过。规范 Clear 包 9 files / 389,653 bytes；APK 内资源逐字节一致。 | 包 SHA-256 `9ad7425d1963a11c0be11214c36ee73f7c597456dfba62378d4943613b0a7c55`；Clear 构建日志 SHA-256 `086AC9F0859120597D04A020046C30FBB6E117D4B651BA089AC4A3EAC17376F6`；APK 校验日志 SHA-256 `6FDBCA14755414A93606F03C1F7032DAB1F35D69FD34C04346F7650619069DEB`。Debug APK SHA-256 `9CA66191D3252DB6325D76BE62F1508E18798D2FBB71DC1E70E8CA1B508EA5F9`；AndroidTest APK SHA-256 `33FC27215E84DE4D2BFCC919C1D46854879AB4A49FE1CAA0A1C45778A0B6DC31`。 |
| Android WebView、AVD、真机与 Native/Clear 配对回放 | **NOT TESTED**。本机 SDK 仅有 API 36 平台；缺少 Emulator 可执行文件，`adb devices -l` 为空。Chrome Preview Mock 不是 Android WebView 证据。 | 设备侧状态见本轮工作目录检查；实体设备测试由用户执行。 |

测试执行说明：新 Bridge 测试在实现前曾以 `No value for completion` 失败，之后随状态投影修复通过；该首次失败没有单独保留为日志文件。浏览器测试初期的 Puppeteer 模块路径与元素截图 API 配置错误已在测试脚本/启动参数修正，最终保存的 E2E 日志为表中所列。上述属于测试夹具/启动问题，不计作产品测试通过。

Preview Mock 的 `deck.commit` 只检查本地选择/版本状态并要求模拟确认，不会运行 Host 的完整字段合法性、正式事件 revision 和持久化写入校验；Mock E2E 仅证明交互流程。Mock 的冲突完成计数未建模，本报告不把其 `0` 计作冲突语义验证。JVM 重开测试显式调用了 `service.checkpoint()`，没有覆盖 `PowerCalApp.ON_STOP`、Activity 重建或 Android WebView 实际恢复时序。

## 修改文件

- Host：`app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridge.kt`
- JVM 回归：`app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridgeTest.kt`
- Clear：`tools/skin-studio/default-clear/source/web/edit.js`
- Studio Mock 与 E2E：`tools/skin-studio/v3-studio.js`、`tools/skin-studio/tests/e2e_v3_deck_lifecycle.cjs`
- 生成资产：`app/src/main/assets/powercal-clear-default.pcskin`
- Bridge 合同与矩阵/进度：`docs/V3_HOST_BRIDGE_EXTENSION.md`、`docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`、`docs/agent-handoff/PROGRESS.md`

## 结论与交付

Bridge 状态投影、Clear 空队列完成态、隔离 Store 重开恢复和桌面 Mock 交互均已有回归证据。当前用户可撤销最近一次丢弃的能力只在本次打开的卡组会话内有效，符合 Native `DeckController` 既有语义；Store 中的丢弃行继续保留。没有新增 Android WebView 生命周期结论。

代码提交 `157b1f08fcc5c9742a2b6226e3cc31374afdadde`（`fix(分离): 补齐 Clear 草稿卡组生命周期状态`）已推送至 `origin/codex/separation-s0-timeline-drag`。独立报告、验收矩阵、Bridge 合同与进度记录随文档提交推送。完整 `S0-DK-02` **保持 PARTIAL / unsigned**，还需 API 35 Android WebView 生命周期/重启验证与配对 Native/Clear 设备回放；实体设备由用户完成。该缺口不阻止继续推进其他可在当前环境验证的分离项。
