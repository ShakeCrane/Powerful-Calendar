# PC-ISSUE-SEPARATION-AI-MESSAGE-CONTEXT-MENU-20261105 — AI 消息角色与长按操作

**状态：**Web v3 消息角色、操作菜单和 Host 内容解析切片 `PASS`；冻结原始 APK 菜单实屏配对 `NOT TESTED`；AI 页面整体 `PARTIAL`。

## 冻结行为

权威源码：`b1f7629f0c52812e1887443872f43aa6c49908c4:app/src/main/java/dev/powerfulcalendar/ui/v2/AiChatScreen.kt`。

- 角色标签为“我”和“PowerCal AI”。
- 长按顺序：从消息创建草稿；用户消息额外提供编辑并重新发送；撤回此消息及后续对话/回退到此消息；复制文字。
- 助手的结构化提案入口独立于消息正文。

## 实现

Web 消息支持触屏长按与键盘可达菜单按钮。Host state 只投影 `proposalAvailable` 布尔值，不投影 `proposalSource`。Web action 只携带消息 ID、历史版本和闭合来源枚举；Host 从当前规范聊天历史解析消息/提案内容并检查页面和版本。复制也由 Host 从规范消息读取后写入 Android 剪贴板。未改 Core API、AI 决策或正式写入规则。

## 验证

- 完整串行 Gradle 门禁：`975/975` JVM、141 suites、0 failures/errors/skips；Skin/Native 边界、Debug APK、Lint（0 errors）和 AndroidTest Kotlin compile 均通过。日志：`.local/android-test/reports/native095-ai-message-menu-20261105/full-gate-rerun.log`。
- API 35 AVD `PowerCal_Native095_AI_20261105` / `emulator-5556`，1080×2400、420 dpi、WebView 124.0.6367.219：`clearChatMessagesMatchNativeLabelsAndUseHostOwnedMenuActions` 1/1 PASS，真实长按打开菜单，检查项目顺序并执行 Host 剪贴板复制；Core Store 计数与序列预览不变。日志：`.local/android-test/reports/native095-ai-message-menu-20261105/instrument-final-ui.log`。
- Studio Host catalog Python：8/8 PASS；Node 语法检查与 `e2e_v3_source.cjs` PASS。
- Studio 全套 Python：86/89 PASS，3 项失败为 `test_full_route_registry_has_one_restore`、`test_privacy_editors_remain_host_owned_but_placeable`、`test_native_settings_and_hidden_activity_rescue_are_both_available`。记录为失败，不计入本切片通过；完整日志：`.local/android-test/reports/native095-ai-message-menu-20261105/studio-python.log`。
- Debug APK SHA-256：`40BF867049F303EA32DA6B3C06D3321C30F0A56BA5AA20B2F07A38D1D07B770D`。唯一默认包源码与 APK 字节一致，SHA-256 `81A99440F5F9E67608460611FECE5825C61229852A8372B2455216086EE88456`，ID `powercal-clear-default`。

## 限制与后续

原始冻结 APK 在既有诊断中未能恢复为 resumed Activity，故其消息菜单实屏操作与截图对照仍 `NOT TESTED`。本报告只关闭 Web 消息操作菜单/Host 内容隔离切片；AI 历史异常、附件授权、提案触控审核、失败重试及整页视觉差异仍未验收。继续以冻结 APK 和 Web v3 实际页面按相同数据配对，不将本切片扩大解释为 AI 页面或全产品 parity。Product Cutover 保持 `HOLD`。
