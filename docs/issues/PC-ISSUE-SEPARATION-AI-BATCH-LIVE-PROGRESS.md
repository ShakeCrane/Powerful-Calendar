# PC-ISSUE-SEPARATION-AI-BATCH-LIVE-PROGRESS — Clear AI 批次实时进度

- 日期：2026-10-30
- 阶段：Skin Separation `S0-AI-04a`
- 性质：重建验收项（`RECONSTRUCTED`，不是历史 209 项原始 ID）
- 状态：**实现及自动化 Host/Preview 子项 PASS；生产 Android WebView 链路 HOLD / NOT TESTED**

## 需求与范围

Clear 在批量发布/丢弃执行期间应显示 Host 当前已完成数和总数。状态只用于观察进度，不能授权操作，也不能暴露 session ID、草稿 ID、锁 epoch 或其他授权信息。本 Issue 只处理 Runner 回调至 Host 状态投影的连接和 Clear Preview 展示验证；父项 `S0-AI-04` 其余功能按原状态追踪。

## 根因

`AiBatchRunner.run(session, mode, onProgress)` 已在每个项目完成后发出 `(done, total)`，Clear `app.js` 也已渲染 `aiBatch.progress.done/total`。但 `PowerCalApp.kt` 构造 `aiBatch` 状态时始终传 `progress = null`，两端之间没有进度持有和传递机制。因此运行中的生产 Host 投影不会提供 Clear 已支持显示的进度。此前 `9bc8426` 只修正了 nullable 进度的投影编译问题，没有连接 Runner 回调。

## 修复

- 新增进程级 `WebAiBatchProgressTracker`，以 `StateFlow` 持有进度；Compose 重建后继续读取同一状态。
- `PowerCalApp` 收集该状态并传给 `WebAiBatchProjection`；运行真实批次时把 Runner 的 `onProgress` 回调写入 tracker。
- 更新按 session ID、mode 和 total fencing；只接受单调增加的 done；批次结束时仅由拥有该状态的 session 清理，旧回调不能覆盖或清除更新批次。
- 现有投影仅在 session 匹配且仍 active 时返回进度，终态投影隐藏 progress。该字段是进程内临时观察状态，不写入数据库，也不携带私有身份或授权数据。
- 扩充 Clear Studio 浏览器 E2E：在 running fixture 中断言真实 Clear 页面状态文案显示 `进度 1/3`，并继续验证停止后的部分结果。该浏览器场景是非写入 Mock。

实现提交：`c67fd0b5abdbf2a0c45f5f27c8d98e2cdb101c83`（`fix(分离): 接通 Clear AI 批次实时进度`，已推送）。

## 测试证据

| 检查 | 结果 | 证据 |
|---|---|---|
| 定向 Android/Robolectric Host 回归 | **20/20 PASS**：`WebAiBatchProjectionTest` 5/5；`WebAiProposalReviewIntegrationTest` 15/15 | 命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.ui.v2.WebAiBatchProjectionTest --tests dev.powerfulcalendar.ui.v2.WebAiProposalReviewIntegrationTest --offline --rerun-tasks --no-daemon --console=plain --max-workers=1`。日志 `.local/android-test/reports/separation-s0-ai-progress-20261030/targeted-final.log`；SHA-256 `A538E9DE979AABD6C122E4F6A9EB700065FA251518C2F02B56E286615ABBD689`。集成用例通过 `PowerCalApp` 的真实 Host 授权动作和 `AiBatchRunner` 执行 10 项；观测到 `RUNNING` 状态的中间 `done/10`，随后所有正式写入完成、待处理数为 0，终态已清除 progress。Tracker 测试覆盖旧 session fencing、mode/total 匹配、单调性及 owner 清理。 |
| 完整离线 Android Gradle 门禁 | **663/663 JVM tests、87 suites、0 failures/errors/skips；88/88 tasks PASS**；源码边界、Debug APK、AndroidTest APK、Lint 均通过；Lint 0 errors / 52 warnings | 命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain --max-workers=1`。日志 `.local/android-test/reports/separation-s0-ai-progress-20261030/full-gradle.log`；SHA-256 `DB7827B520B12D8C26FC2398536937A30408ADA84467D94A9C4A820A0CBFB317`。 |
| Debug APK | **PASS**，66,334,225 bytes；SHA-256 `6C042F46386C5071C1BFDB82A6A06BBB364F87D0A400A42B2793A0BD3589B89D` | `app/build/outputs/apk/debug/app-debug.apk`，由上述完整门禁构建。 |
| AndroidTest APK | **PASS / BUILT ONLY**，1,337,236 bytes；SHA-256 `C23375D347A00880431B9A2807E0BFAB005973D690A8FF8BECC4DF94B6122931` | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`；没有在线设备，未执行 instrumentation。 |
| Clear Preview 浏览器回归 | **PASS**；断言进度文案 `进度 1/3`、运行中可停止及部分结果保留；无真实 Provider 或日历写入 | Chrome Headless/CDP：`node tools/skin-studio/tests/e2e_v3_source.cjs`。日志 `.local/android-test/reports/separation-s0-ai-progress-20261030/studio-e2e.log`；SHA-256 `3A0EAC2016C64234F4E37E8725287EC255EF951D4AD791DFA6BDFEEFCB2A0CED`。这是 Studio Preview Mock，不替代 Android WebView。 |
| Skin Studio Python 测试 | **61/61 PASS** | `python -m unittest discover -s tools/skin-studio/tests -v`；日志 `.local/android-test/reports/separation-s0-ai-progress-20261030/skin-studio-unittest.log`；SHA-256 `12D4FA1CB0784B7682707E7A6B4EDDB73E6BBD3DB2A3C1CD7412732F7F413B49`。 |
| canonical Clear 包校验 | **PASS**；9 files / 392,170 bytes；SHA-256 `af640cbca89a870c2d1ea733eb562ed35e6082b75b08fe7aa37afa9d4ff6f98a` | `python tools/skin-studio/build_default_clear.py --verify`；日志 `.local/android-test/reports/separation-s0-ai-progress-20261030/clear-package-verify.log`；SHA-256 `9786429545FE13EA99B8997099BB2D1A0C8AF40E008415F72B0EAB70B0A71F71`。本 Issue 没有修改 Clear 生产资源。 |
| JavaScript 与差异检查 | **PASS** | `node --check tools/skin-studio/tests/e2e_v3_source.cjs` 与 `git diff --check`。 |

定向测试第一次运行捕获 `PowerCalStores` 对内部 tracker 类型的公开可见性错误；属性改为 `internal` 后重跑 20/20 通过。首次失败日志 `.local/android-test/reports/separation-s0-ai-progress-20261030/targeted.log`；SHA-256 `BDF51EC9FFF62F023E4BE1125D09A4730546566FA8EE3D4F31110C890ED53E74`。失败只发生在编译阶段，没有执行测试。此日志保留为修复轨迹，不计入最终 PASS。

## 状态与剩余缺口

- Runner → Compose StateFlow → `PowerCalApp` Host JSON 投影：**PASS**（Robolectric/Compose 集成，不是设备 WebView）。
- Clear Studio Preview 进度文案：**PASS**（Chrome Mock）。
- 生产 Android `WebView` → `WebMessagePort` → Clear UI 实机/模拟器链路：**NOT TESTED**。`adb devices -l` 没有在线设备，当前 SDK 没有 emulator binary；本轮未运行 instrumentation。实体设备测试由用户负责。
- Native fallback 视觉、真实 Provider、完整 Native/Clear parity：本 Issue 未覆盖，保持各自矩阵状态。

因此，本 Issue 的源代码与自动化子项为 **PASS**；实际 Android WebView 传输与设备呈现仍 **HOLD / NOT TESTED**，不能签核整体 `S0-AI-04` 或分离 GO。证据目录：`.local/android-test/reports/separation-s0-ai-progress-20261030/`。
