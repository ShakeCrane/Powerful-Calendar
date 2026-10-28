# PC-ISSUE-SEPARATION-AI-CHAT-INPUT-LIMIT — 对齐 AI 聊天输入上限

日期：2026-10-28
状态：**输入边界修复及 Host/Studio 自动化 PASS；SEP-09 完整功能等价仍 NOT TESTED；Android WebView/AVD 未测。**

## 根因与影响

Native `AiChatScreen` 的输入框使用 Kotlin `String.length` 限制 6000 UTF-16 code units。分离界面仍在 Clear HTML、Clear submit handler、Studio Mock、`WebSkinHost.decodeBridgeAction` 和 Bridge v1 schema 中限制 4000。结果是 Native 可接受的 4001–6000 UTF-16 输入无法由 Clear 提交，且 Host 会拒收。此次从当前冻结核心行为对照各消费端源码确认差异；没有更改 Core、AI 请求、授权或持久化逻辑。

## 修复

- `WebSkinHost.decodeBridgeAction` 将 `sendChat.text` 上限调整为 6000 UTF-16 code units；空白输入、闭合字段集合和整帧 8192 UTF-16 上限保持有效。
- Bridge v1 JSON Schema 的 `maxLength` 与 `x-runtimeMaxUtf16CodeUnits` 更新为 6000，并明确标准 Schema 字符计数与 Host UTF-16 运行时计数的区别。
- Clear 的 `textarea maxlength` 和 submit 检查、Studio Mock 校验同步至 6000。
- 增加 Host 回归：6000 个 ASCII 字符和 3000 个补充平面字符均通过（后者 `String.length == 6000`），6001 个 ASCII 字符被拒绝。
- Chrome Studio E2E 在 Mock 场景检查输入框声明 6000、提交 6000 字符可见、超 6000 字符不产生新消息。
- 通过官方 builder 重建 APK 内 Clear 包，确保 Android 资源与唯一源码同步。

## 验证

环境：Windows PowerShell；JDK 20.0.1；Android SDK `E:\android-sdk`（API 36 平台）；Python 3.11.4；Node.js 24.19.0；Chrome Headless。测试使用本机 Studio 服务与隔离浏览器配置。机器没有 `E:\android-sdk\emulator\emulator.exe`，本轮未运行 Android WebView/AVD 或实体设备测试。

定向 Gradle：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests dev.powerfulcalendar.ui.v2.WebSkinHostProjectionTest --tests dev.powerfulcalendar.ui.v2.WebSkinEditBridgeTest
```

**32/32 PASS**，0 failures/errors/skips。

完整离线门禁：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

**621/621 JVM tests、87 suites、0 failures/errors/skips、88/88 tasks PASS**；source boundary、Debug APK 和 AndroidTest APK 编译通过；lint **0 errors / 52 warnings**。AndroidTest APK 仅编译，未在设备上执行。

其他回归：

- `python -m unittest discover -s tools/skin-studio/tests -v`：**70/70 PASS**。
- `node --check`：Clear `app.js`、Studio `v3-studio.js`、浏览器 E2E 脚本均 PASS。
- Chrome Studio E2E PASS；日志显示 `textLimitUtf16:6000`、`acceptedAtLimit:6000`、`rejectedAboveLimit:true`。这是浏览器内的 Studio Mock，不是 Android Host/WebView 或真实 Provider 证据。
- `python tools/skin-studio/build_default_clear.py --verify`：PASS，8 files / 381125 bytes，Clear `.pcskin` SHA-256 `04C5F92D1C30D00B84E4CBD90FA572DAF8103DAA05CA8F5C2552C6A9D94600AC`。
- Bridge schema JSON 解析、`git diff --check`：PASS。

关键证据位于 `.local/android-test/reports/separation-20261028/ai-chat-input-limit/`：

- 完整 Gradle 日志 SHA-256：`66107EC66A947B3CC823A8363F146CFCF4F49F5853A99F72E242CA3D307312F1`。
- 定向 Gradle 日志 SHA-256：`61AEE6FFB036508280ED0BDC1B1A281445E243B520E11E05AD1FD33B5821897E`。
- Skin Studio 70 项测试日志 SHA-256：`F1EB7932A9EA785DEC26B0E4AE0F82C4F041FD0BD218B700C6C9CD4A16821A9C`。
- Chrome E2E 日志 SHA-256：`8DC1E115BD2D2B6A63CC82985B81A4787FFA67826632F20EB198BDF9F8FE90F9`。
- lint XML SHA-256：`B3D4AF5AC60BC562F9727C97C5A65BC4DACC0DC7AD59B44907D6B626CF863BC0`。
- Debug APK SHA-256：`0281716B2EC5E73071495E80A8B81B65BD7DC0F8ECCAA6BEDA259E8B809AEDFB`。
- AndroidTest APK SHA-256：`6B89A35252F0106B6A1B9DB45918C22303CA422391644EB0A77A329F413BAEB9`。

两项首次尝试在沙箱本机回环 socket 处以 `EACCES` / `WinError 10013` 结束，未到达产品断言；记录保存在同目录的 `skin-studio-python.log` 与 `studio-browser-e2e.log`。按授权使用仅限本机测试的沙箱外权限重跑后，分别得到 70/70 和浏览器 E2E PASS；没有访问外网 Provider 或发送真实 AI 请求。

## 交付与边界

- 实现提交：`bb627a4e3c4cc785ced6c82efb3cc73e139ec12a`（`修复(分离): 对齐 AI 聊天输入长度`），已推送至 `origin/codex/separation-skin-settings`。
- `SEP-09` 只增加这一条输入边界证据，整体行仍为 **NOT TESTED**；历史、附件、上下文、授权、错误恢复和所有 Native/Skin 成果的完整回放仍未闭环。
- 当前分支工作树中另有未提交的 `WebSkinManagementInstrumentedTest.kt` 并发修改；它虽参与 AndroidTest APK 编译，但未纳入本 Issue 的暂存或提交。另一 worktree 对 `PROGRESS.md` 和 Clear 源文件的修改也未被本 Issue 覆盖；本报告及进度更新只写在本分支工作树。
- 未修改冻结 Core 业务语义，未触碰真实日历数据、凭据、设备或用户皮肤选择。整体 Skin-First 分离目标继续进行中。
