# PC-ISSUE-SEPARATION-WEBVIEW-RENDERER-RECOVERY

- **日期：** 2026-10-31
- **状态：** 此故障场景 `PASS`；父项 SEP-14 仍 `INCOMPLETE / HOLD`
- **验收来源：** 重建矩阵 SEP-14（非历史 209 项 ID）
- **验证提交：** `89e6cd3e24a3ed65964620f4d31a9cde3a9a20a6`，已推送到 `origin/feat/skin-first-separation`

## 结果

已在 API 35 AVD 的 PowerCal Runtime 中加载实际打包的 Clear，等待 WebView Host Bridge 握手完成，然后终止其真实 renderer 进程。Clear 发生 renderer crash 后，PowerCal 自动持久切回 Native，显示“皮肤加载失败，编辑已存档并恢复安全界面。”提示，并进入 Native“工具与设置”恢复页。隔离数据库中既有正式记录及 Store 计数均不变。

测试没有复现 renderer 故障处理缺陷；修复工作是将该既有恢复链路纳入可重复的设备级故障注入回归。本次没有修改 Runtime、Bridge、冻结业务源码、Clear 包或默认皮肤。

## 调用链与验证边界

1. `WebSkinHost.kt` 收到 `WebViewClient.onRenderProcessGone` 后触发 `WebSkinRoot` 的失败状态，移除损坏的 WebView 并展示原生内容。
2. `PowerCalApp.kt` 的失败处理尝试 `webEditing.prepareSkinChange()`，通过 `SkinPackageManager.selectNativeForEmergencyRecovery()` 同步保存 Native 选择，切换到 `Page.TOOLS` 并显示安全提示。
3. 新增的 `WebSkinRendererRecoveryInstrumentedTest.kt` 使用唯一临时数据库写入一条 Memo 基线事件，加载 APK 内的 Clear 包并确认 `window.PowerCalSkin.receive` 已就绪；随后调用 `WebViewRenderProcess.terminate()`。
4. 测试检查 Native 皮肤偏好已持久化、Native Tools 页及恢复提示可见，并比较崩溃前后的正式记录、待处理草稿数、提交数、身份数和下一个事件序号。

## 验收证据

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| API 35 WebView renderer 故障注入 | `PASS` | `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.powerfulcalendar.ui.v2.WebSkinRendererRecoveryInstrumentedTest`；`codex-clear-api35`，Android 15 / API 35，1/1。测试 XML：`app/build/outputs/androidTest-results/connected/debug/TEST-codex-clear-api35(AVD) - 15-_app-.xml`，SHA-256 `409D51CFCACBF7E1D385115AE7794B3874FD8B07A725AD35641825428216AF72`。 |
| 真实 renderer crash 记录 | `PASS` | instrumentation Logcat `app/build/outputs/androidTest-results/connected/debug/codex-clear-api35(AVD) - 15/logcat-dev.powerfulcalendar.ui.v2.WebSkinRendererRecoveryInstrumentedTest-clearRendererExitPersistsNativeRecoveryAndPreservesStore.txt` 含 `Renderer process (...) crash detected`；SHA-256 `51310015C6F853B6DFD8DEACF90C62003F1C99499500C5E88C976CC88C8D2785`。 |
| 全量 JVM / Android 构建门禁 | `PASS` | `:app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=1 --console=plain`；784/784 tests、125 suites、0 failures/errors/skips，152/152 tasks。日志 SHA-256 `18333BCA06856FACAD675B476B8798085FBC82444D6DB57CB2E94D7FD0252B48`。 |
| APK | `PASS` | `app/build/outputs/apk/debug/app-debug.apk`，66,516,951 bytes，SHA-256 `7D6A3A37C86116DEB94A1CFEEC5E1235AD0245BBD4C1AFBCAEA096A9B727B634`。 |

聚焦 instrumentation 日志 SHA-256：`9CEE1184DA2172C40FF609F50E9A7CE555249445BAFDDD8E1740989D1E30ABE0`。聚焦与完整 Gradle 日志位于忽略目录 `.local/android-test/reports/sep14-renderer-recovery-20261031/`。测试实际通过后，没有对设备执行卸载、清库、清 AVD 或清缓存。

## 尚未验证

本结果只覆盖已加载 Clear 的 WebView renderer 进程终止与 Native 紧急恢复。它**不代表 SEP-14 完成**。以下仍未验证：renderer 退出瞬间存在未保存的 dirty editor；普通 Activity/process death 与重启后的草稿恢复；损坏/恶意包和脚本异常；升级失败；设备重启后的数据保持；实体设备/OEM/WebView 版本差异。

## Git

- 测试提交：`89e6cd3e24a3ed65964620f4d31a9cde3a9a20a6` — `test(皮肤分离): 验证Clear WebView崩溃恢复`
- 本报告、SEP-14 状态补充和进度记录随后单独提交，避免报告 SHA 冒充测试时的代码基线。
