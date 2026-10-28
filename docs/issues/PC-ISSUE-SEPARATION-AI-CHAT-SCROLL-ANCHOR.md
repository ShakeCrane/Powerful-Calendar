# PC-ISSUE-SEPARATION-AI-CHAT-SCROLL-ANCHOR

日期：2026-10-28

状态：**Clear 聊天历史滚动窄缺陷已由 Chrome/Studio 回归验证并提交；SEP-09 / S3 完整 AI 聊天等价仍为 NOT TESTED。**

## 范围

处理 Clear 皮肤 AI 聊天历史列表在读取旧消息时意外跳到底部的问题。修复只调整皮肤的滚动位置管理，不更改 Host Bridge、聊天请求、AI 授权或 Core 数据。Studio 的高级源码编辑与 `.pcskin` 导入/导出往返也纳入本次验证。

## 根因与修复

旧版 `renderChat()` 在每次 Host state 更新时清空并重建消息列表，最后无条件执行 `scrollTop = scrollHeight`。因此 Memo 等无关 Host 状态变化，以及聊天追加新消息，都会把正在阅读历史的用户送到列表末尾；Native 聊天视图则只在用户接近最新消息时跟随。

修复在重建 DOM 前记录当前 `scrollTop`、`scrollHeight` 和距底部是否不超过 48px，并保存上次渲染的消息 role/content。重建后：用户原本在底部且消息变化或内容增高时才跟随底部；否则恢复原滚动位置并限制在新列表范围内。所有正式消息和 Host 行为保持原样。

Clear 官方 `.pcskin` 通过仓库的 `build_default_clear.py` 从 canonical source 重建。对比修复前后包，条目名完全相同；内容仅 `web/app.js` 与随之更新的 `manifest.json` 变化，没有删除皮肤资源。Studio E2E 还确认 Advanced Mode 对 `web/app.js` 的源码修改在包导出、重新导入后仍保留。

## 红绿回归

### 修复前基线（预期失败）

使用提交 `cd15978dee5a3ebc461b301d4c917a435408876b` 中的旧 Clear APK 包，在隔离快照中运行当前浏览器回归。旧包 SHA-256：`9CC6BDD86A53C693B8CEC6E84950ED89EC49BCDE9FB78D773034E4147E45A454`。测试按预期以退出码 1 失败：无关 Host state 更新后 `scrollTop` 从测试设定的 220 跳到 1595，可见消息从历史消息 1 跳到历史消息 15。失败日志 SHA-256：`B8579E0766F004CECD215DE9202F49E44332AE83BA4AEB30D219427966E4080B`。

### 修复后（通过）

Chrome Headless E2E 对新的 Clear 包通过，退出码 0。测试覆盖：

- 长聊天列表处于历史阅读位置时，Memo 等无关 Host state 更新后仍保持 `scrollTop=220` 和首条可见历史消息。
- 处于历史位置时追加一条回复后仍保持历史锚点。
- 用户已在底部时追加回复，列表跟随至 `scrollTop=1709`，等于最大滚动位置。
- Studio 源码编辑 `web/app.js` 后，导出并重新导入仍保留编辑内容。

完整 E2E 还通过现有 Studio source round-trip、四类新建 Mock、Draft Deck、重复事件、冲突/恢复、附件、AI 503 手动重发与 AI 审核 Mock 场景；Mock 没有读取真实附件、调用 Provider 或写入日历。

## 验证记录

- `node --check tools/skin-studio/default-clear/source/web/app.js`：PASS。
- `node --check tools/skin-studio/tests/e2e_v3_source.cjs`：PASS。
- `python tools/skin-studio/build_default_clear.py --verify`：PASS，8 个皮肤源文件、381,127 字节；canonical APK 资产 SHA-256：`ef3694dbd9811786ca4835eda10dbf8a27f46c02f5de67877e2517f6ed155c13`。
- `python -m unittest discover -s tools/skin-studio/tests -v`：**70/70 PASS**，0 failures/errors；日志 SHA-256：`1D2F248E268025264464E51DDE04540C9DD619273E33BDC717E32060276A8F97`。
- `node tools/skin-studio/tests/e2e_v3_source.cjs`：**PASS，退出码 0**；日志 SHA-256：`2CF788257938F01EB9A7768922F316C710D812F63C0A75E0E52E8BC79CEC2F56`。
- `git diff --check`：PASS。Windows 对 E2E 文件及另一 Agent 的 AndroidTest 文件给出 LF/CRLF 转换警告；没有格式错误。另一 Agent 的 AndroidTest 修改未暂存、未提交。

证据保存在 `.local/android-test/reports/separation-20261028/chat-scroll-final-verification/`：`green/` 含完整单测/E2E 日志、退出码和截图；`red-baseline-e2e/` 含修复前失败日志；`red-baseline-snapshot/` 保存旧包隔离快照。滚动位置截图为 `green/studio-chat-scroll-history.png`，SHA-256：`0E638422AEFF70E81CB26CAFBF251B5BCDCF47A56F2E1A4DB96CC0B26E1019B9`。

## 验收边界

本次没有可用 Android SDK 或 ADB，因此没有构建 Android APK，也没有运行 Android WebView、AVD、真机/OEM 或无障碍回归。Chrome Studio 预览不是 Android WebView 证据，也不代表真实 AI Provider 验收。SEP-09 的 Host AI 客户端、附件/OCR/ICS、Provider、生命周期和完整 Native/Skin replay 仍未覆盖，矩阵总行继续为 **NOT TESTED**。

`docs/agent-handoff/PROGRESS.md` 在另一分离 worktree 中有其他 Agent 的未提交编辑，本次未覆盖该文件；本 Issue 报告和 SEP-09 矩阵备注作为本次可审计记录。

## Git

代码、回归和 canonical APK 资产提交：`2b4c7254fecf65189dd93f9bdc15153ac127fda7`（`fix(皮肤): 保持 AI 聊天历史阅读位置`），已推送到 `origin/codex/separation-skin-settings`。本 Issue 与矩阵备注单独提交，避免把另一 Agent 的未提交文件纳入提交。
