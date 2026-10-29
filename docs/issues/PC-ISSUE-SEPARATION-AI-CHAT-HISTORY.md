# PC-ISSUE-SEPARATION-AI-CHAT-HISTORY — Clear AI 聊天历史清空与回退

日期：2026-10-29
阶段：Skin Separation `S0-AI-01` / `S0-AI-04`
性质：重建验收项（`RECONSTRUCTED`；不是恢复出的历史 209 项 ID）
状态：**PARTIAL / HOLD**

## 目标与范围

补齐 Native AI 聊天已有的清空、用户消息编辑并重发、撤回及后续、回退到助手回复等能力，使 Clear 皮肤经 Web Host Bridge 使用同一份 Host 所有的本地聊天历史。本 Issue 只涉及 AI 聊天历史，不授权任何正式事件或草稿写入。

## 根因

Native 的聊天历史保存在 app-private `AiChatHistoryStore`，原生页面已经有清空和逐消息回退操作。Clear Bridge 只投影了最近 40 条消息的角色与文本，没有投影稳定消息 ID，也没有清空、回退和 composer prefill 动作；因此 Clear 无法对 Host 历史执行等价操作。单靠在 WebView 删除 DOM 消息不会更改持久化历史，也无法安全处理并发变化。

## 修复

- 新增纯历史 reducer：按稳定 Host `messageId` 定位候选，重复 ID 拒绝；按当前历史 revision fencing；用户消息编辑并重发会移除该消息及后续轮次并返回原文本，用户撤回同样移除该消息及后续轮次；助手回退保留选中回复并移除后续轮次。
- Bridge 增加 closed-schema `clearChat` 与 `rewindChat`。请求只能携带当前 `chatHistoryRevision` 和 Host 投影的 ID，不接受皮肤提交消息正文或日历对象。空历史清空和回退到最后一条助手回复不改变历史或 revision。
- Host 控制操作可用状态，限制于 AI 页面且无正式/新建事件编辑、草稿卡组、批次、picker、附件审核或 Host 发布审核。提交消息和每次实际历史变更推进 revision；Clear 清空和回退只改 app-private chat history，不碰事件、草稿、提交记录或身份计数。
- 新增 `chatPrefillText` / `chatPrefillRevision`。Clear 只在 prefill revision 变化时更新输入框，普通 state render 不覆盖用户后来编辑的文本；只有明确的“编辑并重发”替换输入框。清空、撤回及助手回退保留未发送的输入。
- Clear 增加带无障碍名称的逐消息操作与清空聊天按钮；Studio Mock 使用独立稳定 ID、revision fencing 和非写入模拟。
- 修正 E2E 截图路径：截图写入 `POWERCAL_E2E_REPORT_DIR` 下，不再固定写入其他日期的报告目录。

## 修改文件

- Host：`PowerCalApp.kt`、`WebSkinHost.kt`、`WebAiChatHistory.kt`。
- 回归：`WebAiChatHistoryTest.kt`、`WebSkinHostProjectionTest.kt`、`WebAiProposalReviewIntegrationTest.kt`。
- Clear / Studio：`default-clear/source/web/app.js`、`index.html`、`style.css`、`v3-studio.js`、`tests/e2e_v3_source.cjs` 及派生包 `powercal-clear-default.pcskin`。
- 合同与进度：`V3_HOST_BRIDGE_EXTENSION.md`、`SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`、`docs/agent-handoff/PROGRESS.md`。

## 测试证据

所有日志与截图位于 `.local/android-test/reports/separation-s0-ai-chat-history-20261029/`，该目录由 Git 忽略。

| 检查 | 结果 | 证据与限制 |
|---|---|---|
| Skin Studio Python 单元测试 | **PASS：61/61** | `studio-python-final.log`；SHA-256 `709F9121BB845C7417686FA2A7B961454BC5067B7E9345E97AA8FE3B788ACBD6`。 |
| Microsoft Edge Studio Preview E2E | **PASS** | `studio-e2e-final.log`；SHA-256 `F2D533C305FA55DF2A2781C10213C53602C62B56095DF447286FDEB9A681047B`。包含用户消息编辑并重发、跨无关页面更新保留新输入、助手回复回退、移除后续轮次、清空且保留未发送 composer。Mock 不调用真实 AI Provider，不写入真实日历或草稿。 |
| AI 审核预览截图 | **已生成** | `studio-e2e/ai-proposal-review/ai-review-studio-mock.png`；SHA-256 `7A1462EBAA37BDB9478C07BE08A1106318AB5179BC655CFE93AB902464D301A2`。它是 Studio Mock 截图，不是 Android WebView 或设备截图。 |
| JavaScript 语法 | **PASS** | `node --check` 分别检查 `default-clear/source/web/app.js`、`v3-studio.js`、`tests/e2e_v3_source.cjs`；全部退出码 0。 |
| Clear canonical 包 | **PASS** | `python tools/skin-studio/build_default_clear.py --verify`；9 files / 391,549 bytes；SHA-256 `fecf7414c4da747d6dceb63022d0354ac809e91905d0684ed9eff201b8d892c1`。 |
| Android Skin 源码边界 | **PASS** | `:app:verifySkinSourceBoundary --offline --rerun-tasks --no-daemon --console=plain`；`source-boundary.log`；SHA-256 `7E7C0B6A7DC165079F1714CD5623885FE9367156DF4AB58E2F511111011CB2F7`。这只证明边界任务通过，不代表 Kotlin 编译或 AI Host 集成测试通过。 |
| 新增 Kotlin reducer、decoder、PowerCalApp 集成回归 | **NOT TESTED** | 已加入 closed-schema、消息投影、重复/缺失 ID、stale revision、用户/助手回退、空历史 no-op、Host 持久化与 Store 计数不变用例；Gradle 在编译前失败，不能将这些测试标为 PASS。 |
| 完整 Gradle / APK / lint | **BLOCKED / NOT TESTED** | 命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain`。`gradle-full.log` 记录 `Failed to find target with hash string 'android-36'`，在 Kotlin 编译、测试、APK 和 lint 前退出；SHA-256 `9BD263341F93EF0E1F4215A835F2FB9A59A481A75A7C8FDAA2BDE9AE9C03AFD4`。未生成 APK。 |
| Android Emulator / 真机 | **NOT TESTED** | 仓库 SDK 无 `platforms/android-36`；仓库 ADB `devices -l` 没有在线设备，`emulator -list-avds` 未列出 AVD。尝试下载 Android 官方 command-line tools 时，执行环境无法连接 `dl.google.com:443`，因此没有安装或替换 SDK。实体设备验证仍由用户执行。 |
| `git diff --check` | **PASS** | 实现暂存前退出码 0；源码边界与 Python/浏览器日志如上。 |

Android SDK 缺口也可从 [Android 官方命令行工具与 SDK Manager 说明](https://developer.android.com/studio/)核对；本机缺失状态以本 Issue 的 Gradle / ADB 证据为准。

## 证据保全说明

本次首次运行 E2E 后发现脚本此前把 `ai-review-studio-mock.png` 硬编码写入 `.local/android-test/reports/separation-20261027/ai-proposal-review/`，覆盖了该位置原有的本地截图。该截图没有找到其他副本，旧字节无法恢复。已修正脚本，当前截图保存在本 Issue 日期隔离目录；后续 E2E 不再覆盖旧报告目录。此项是本轮证据溯源缺口，不把当前截图冒充为修复前截图。

## 结论与下一步

Clear/Studio 的交互与 Mock 子项通过。Host Kotlin 代码、reducer、Bridge decoder 和持久化路径尚未编译或执行，生产 Android WebView → Host 传输也未验证，因此本 Issue 保持 **PARTIAL / HOLD**，不能签核 `S0-AI-01`、`S0-AI-04` 或完整 Native/Skin parity。API 36 SDK 可用后先运行定向 `WebAiChatHistoryTest`、`WebSkinHostProjectionTest`、`WebAiProposalReviewIntegrationTest`，再执行完整 Debug / AndroidTest / lint 门禁和可用模拟器回归。

## Git

- 实现提交：`fd3b8ee1b988517a0735749c76786e5eab7faa55` — `修复：桥接 AI 聊天历史清理与回退`。
- 输入保留跟进提交：`b71af49a473e95b6c34b051a78033a0cb375ccee` — `修复：保留 AI 聊天未发送输入`。
- 编辑器门禁提交：`32d7628adefbab72344d8c88a632224296d75580` — `修复：阻止编辑期间改动聊天历史`。
- 本 Issue、Parity 与 Bridge 合同更新在独立文档提交中记录。
