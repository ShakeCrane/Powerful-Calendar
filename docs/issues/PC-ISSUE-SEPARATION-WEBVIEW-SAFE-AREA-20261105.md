# PC-ISSUE-SEPARATION-WEBVIEW-SAFE-AREA-20261105 — 冻结 Native 与 Web 内容视口错位

**状态：**已修复并验证（API 35 隔离 AVD）；其他设备/切口形态未验证。

## 现象

在 `PowerCal_Native095_AI_20261105`（API 35，1080×2400，420 dpi）上，Native 内容从系统栏下方 y=63 开始，WebView 原先由 `statusBarsPadding()` 放到 y=128，导致默认 Skin 的页面结构整体下移约 65 px。该差异影响 Timeline、Memo、AI 等页面的原生视觉对照。

## 原因与修复

该窗口的 `rootWindowInsets` 状态栏 top inset 为 128 px，而系统 `status_bar_height_default` 为 63 px；后者与冻结 Native 的可见内容边界一致。`WebSkinRoot` 现在优先读取默认状态栏高度并以它为 WebView 顶部 padding，平台 inset 仅作资源缺失时的 fallback。默认皮肤 CSS 对 Memo 空态位置及 AI 页标题字号/行距做了局部补偿，保留同一 Web v3 Runtime 与正式 `.pcskin` 解析路径。

## 验证

- 修复提交：`96fa026594e8f085c6221c6bbda83a311829e779`；AI 字号微调：`1e11116be0b22922988e8a0c777826ad0c7e8629`。
- 完整 Gradle 门禁：974/974 JVM，0 failure/error/skip；源码边界、Debug assemble、Lint、AndroidTest Kotlin 编译通过。
- 更新后的包：Studio 默认分发测试 2/2、Node 语法检查、Debug assemble 和 `build_default_clear.py --verify --apk ...` 通过。
- API 35 AVD 上真实截图与 UIAutomator bounds：WebView `[0,63][1080,2337]`；Native/Web AI 标题、说明文字和空态对齐到约 0–4 px；Memo 空态文字边界约差 1–2 px。
- 证据清单：`.local/android-test/reports/native095-parity-20261105/native095-ai-host-inset-evidence.md`。最终 APK SHA-256 `996247646604CA935029920B5C2A2CA6CC1624ECF3C62B44A1CF6D1BA68546C0`；唯一默认包 SHA-256 `D9CFAD9FFD368D60BE6D75316952463FFF6D42BAD97DB3F116B526312275B6DB`。

## 限制

本修复只证明该 API 35 AVD。不同 OEM 状态栏资源、横屏及 display cutout 的安全区尚未验证。AI 本轮只验证空首页，不代表配置、消息、附件或提案审核等功能完成；整体原生视觉/交互等价仍为 `PARTIAL`，Product Cutover 继续 `HOLD`。
