# PC-ISSUE-SEPARATION-AI-MENU-ORDER-20261105 — AI 更多菜单与冻结 Native 顺序不一致

**状态：**Web 默认 Skin 顺序与文案已修复；冻结原始 APK 菜单实屏 `NOT TESTED`。

## 现象与基准

Web v3 原菜单把“清理 AI 临时缓存”放在批量发布/丢弃之前，且缓存与清空聊天的说明文案短于冻结 Native。冻结 Native `AiChatScreen.kt` 的实际 Compose 菜单顺序为：处理事件、批量发布、批量丢弃、分隔线、缓存清理、清空聊天；批量操作要求先选择范围，零待处理项时禁用。

## 修复

调整 `tools/skin-studio/default-clear/source/web/index.html` 中 AI 菜单 DOM 顺序与四项文案。Host action 名、批处理状态机和权限策略未改。正式 `.pcskin` 由 `tools/skin-studio/build_default_clear.py` 重建。

## 验证

- 提交：`aceae50b9241509d1914f54493161e28af037f40`。
- Studio 默认分发测试 `2/2 PASS`，Node 语法检查通过，Debug assemble 与 APK 资源字节校验通过。
- API 35 隔离 AVD `PowerCal_Native095_AI_20261105` / `emulator-5556` 上实际打开 Web 菜单并点菜单外关闭；截图及 SHA-256 见 `NATIVE_095_VISUAL_PARITY_AUDIT.md`。
- 原始冻结 APK 在该 AVD 上安装成功但 `MainActivity` 未恢复为前台；本轮没有清除数据，已恢复当前 APK。另一“Original”命名 AVD 的层级仍是 WebView，因此原始 Native 菜单屏幕比较标为 `NOT TESTED`，没有伪称 Native/Web 截图通过。
- 一次菜单外坐标误落在缓存清理项；App 实际提示清理 0 个应用私有临时文件且聊天/正式事件保留。未触发批量操作或清空聊天；之后使用菜单左外侧坐标确认菜单关闭。

## 后续

AI 配置与聊天业务状态仍待对照。Settings 中“打开 AI 配置与隐私设置”当前通过 `nativeSettings` 进入完整 Native Tools，属于独立迁移缺口；不得因本菜单修复宣称 AI 页或整体 Native 退役完成。Product Cutover 保持 `HOLD`。
