# AI 设置路由进入 Native Tools 的日常路径

日期：2026-11-05

状态：RESOLVED（Host 表单路由切片；AI 页面整体仍 PARTIAL）

基准：`a535122c773a95315dfad7399196bde3d0888699`

修复提交：`07c62fbf16c0aa72181f92783e84a5826905fc56`

## 问题

默认 Clear Settings 的“打开 AI 配置与隐私设置”按钮和“打开原生完整设置与恢复工具”共用 `nativeSettings`。`WebSkinHost.decodeBridgeAction` 将其解码为通用 Native 路由，`PowerCalApp.kt` 随后把整个活动页切换到 `Page.TOOLS`。普通 AI 配置因此离开 Web Skin 并进入完整 Native Tools 页面。

## 修复

- AI 配置按钮改发封闭动作 `openAiSettings`；独立恢复按钮仍保留 `nativeSettings`。
- `openAiSettings` 纳入 `host_binding_catalog_v1.json`，只允许无参数调用，并由 Host decoder 拒绝任何额外字段。
- `PowerCalApp` 只在正式 Web v3 全画布且当前页为 Settings 时接受该动作，显示 Host Compose 对话框，不替换 Skin 页面。
- 对话框复用既有 Host-owned AI 设置表单。凭据由 Host 从应用私有设置加载和保存，API Key 字段仍密码遮罩；密钥不进入 Web state、DOM、action payload 或 Studio Preview Mock。
- 有未保存设置时，关闭操作会先询问是否放弃；不会静默丢弃表单更改。
- Studio Preview Mock 只记录合成路由，不启动 Android Host、不读取密钥或写入偏好。

未修改 Core 业务语义、AI API、Native 故障恢复 ID、默认皮肤 ID 或默认皮肤视觉设计方向。`powercal-clear-default.pcskin` 仍是唯一正式包。

## 验证

- 完整串行 Gradle 门禁 PASS：974/974 JVM（141 suites，0 failures/errors/skips），Skin/Native 源码边界、Debug assemble、Lint（0 errors / 63 warnings）、AndroidTest Kotlin compile 均通过。
- Android API 35 隔离 AVD `PowerCal_Native095_AI_20261105` / `emulator-5556`：`clearSettingsRoutesAiCredentialsAndContextControlsToProtectedHostUi` 1/1 PASS；`clearAiSettingsDialogCloseReturnsToSkinWithoutNativeTools` 1/1 PASS。实际 WebView 页面发起设置动作，Host 表单显示并展开设置字段；活动页面保持 SETTINGS，关闭后仍由 Skin 导航；Store 快照不变。没有清除 App 数据，未输入、检查或保存任何凭据值。
- Studio Python：`test_default_clear.py` 5/5、`test_single_default_distribution.py` 2/2、`test_host_binding_catalog.py` 7/7 PASS。Studio Mock Chromium/CDP E2E 最终 PASS；首次尝试因测试等待文案与新 Mock 日志不同而失败，更新等待文案后复跑成功。两个修改的 JS 文件 `node --check` PASS。
- 正式构建器验证 APK 只含一份默认 skin 资源；包 10 文件、408192 bytes，源码与 APK asset SHA-256 均为 `eaa55919a071107938db08e788b59a89625adf4bd7a3c6547d6ac235bf1ceeb6`。最终 Debug APK SHA-256：`a27588ea0a5e2251e14bd4bdaa269703a336a38c9fbe2bbccb2e3af2b91a7bf6`。
- 详细日志与 Studio 结果位于 `.local/android-test/reports/host-ai-settings-20261105/`。

## 剩余状态

此 Issue 只关闭 Settings 到受保护 AI Host 表单的路由问题。脏表单放弃确认分支尚未单独执行。冻结原始 APK 的 AI 设置截图仍未配对；聊天/历史、附件授权、AI 提案审核、错误/重试和真实服务商行为仍未签核。整体 Native 0.9.5 视觉/交互迁移继续 IN_PROGRESS，Product Cutover 保持 HOLD。
