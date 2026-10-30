# PC-ISSUE-SEPARATION-NATIVE-AI-STUCK-BATCH-RELEASE-REVIEW

**状态：PARTIAL**

**日期：2026-10-30**

**代码提交：** `348039009aca0d50cbaca110b518931945573e3c`

**功能范围：** Native AI 卡住批次的强制解除确认展示迁入 `:native-skin`。

## 根因与修复

PowerCal 的强制解除确认对话框仍由 `PowerCalApp` 直接构造。原生确认文案、按钮和展示结构因此留在 Host 编排代码中，Native 皮肤 renderer 边界不完整。

新增 `NativeAiStuckBatchReleaseReview` 作为只负责呈现的 Native renderer，并在 `skin-api` 增加显示所需的对话框 slot。`PowerCalApp` 继续计算 AI 会话、锁 epoch、停止时间和 10 分钟资格条件，执行原有 Store 解除操作并协调后续状态。renderer 只接收 `canConfirm` 和确认/取消回调，不获得会话 ID、锁身份或持久化能力。此改动没有改变资格条件、解除行为或冻结 Core，也没有修改 Clear。

## 修改文件

- `skin-api/src/main/java/dev/powerfulcalendar/ui/v2/NativeAiChatModel.kt`
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeAiStuckBatchReleaseReview.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/NativeAiStuckBatchReleaseReviewComposeTest.kt`
- `docs/UI_SKIN_EXTENSION_CONTRACT_V4.md`
- `docs/SEPARATION_REMOTE_GOVERNANCE_AND_ROADMAP_2026-10-30.md`
- `docs/agent-handoff/PROGRESS.md`

## 验证证据

测试运行环境为 Windows、仓库离线 Gradle 缓存和 API 35 Robolectric；日志保存在 `.local/android-test/reports/native-ai-stuck-release-20261030/`。

1. 定向命令：

   ```powershell
   .\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest --tests '*NativeAiStuckBatchReleaseReviewComposeTest' --tests '*AiReviewComposeTest' --tests '*NativeAiProjectionTest' --offline --rerun-tasks --max-workers=2 --console=plain
   ```

   结果：**10/10 PASS**。包含新 renderer 对资格关闭时禁止确认、资格开放时只调用显式确认回调的两项 Compose 回归，以及既有 AI review / projection 测试。

2. 全量离线回归命令：

   ```powershell
   .\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=2 --console=plain
   ```

   JVM 结果为 **770 项、1 项失败**：`WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` 因 Memo 投影 60 秒超时失败。该测试不属于本次 Native renderer 路径。对该测试单项隔离重跑为 **1/1 PASS**；全量失败仍如实保留，不能据隔离通过将完整回归记为通过。

3. 不含 JVM 测试任务的构建门禁：

   ```powershell
   .\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=1 --console=plain
   ```

   结果：**BUILD SUCCESSFUL，144 tasks**。AndroidTest 仅编译，未在设备运行。Debug APK 为 66,500,178 bytes，SHA-256：`E946DB02799490481DAD69C31C6F07C7D2A79D763FD774939C404F178C56CD12`。

| 证据 | 结果 | SHA-256 |
| --- | --- | --- |
| 定向测试日志 | 10/10 PASS | `D270D80563D06763CFFD018B4A4D12923343055FB3D3871699E31F8B7A5BF881` |
| JVM 全量回归日志 | 770 项 / 1 项失败 | `8DBC2503E898803161E66C5E5577AA5D4C501396F941F82B2541AEA131224E18` |
| Memo 超时用例隔离日志 | 1/1 PASS | `F202A3BCD2A1740363431F154F933E49067D253C78AFA5918D189B7108C564EF` |
| 构建门禁日志 | BUILD SUCCESSFUL，144 tasks | `8473A7D501BDE59DDD76E1DD2E938F773140EC1EA87A0B82BBC33EC710416796` |

## 验收状态与剩余事项

- Native renderer 实现：**CODE COMPLETE**。
- 资格禁用/启用和回调边界：**TESTED**，定向 10/10。
- 完整 JVM 回归：**FAIL**，有一项非本路径 Memo 超时；隔离重跑通过但不能替代完整回归。
- Debug 构建、源码边界、AndroidTest 编译及 lint：**TESTED**。
- Android 设备、WebView 与实际系统对话框行为：**NOT TESTED**；当前没有连接 ADB 设备。
- SEP-10 整体仍为 **PARTIAL**。本 Issue 不声称 AI 全流程等价完成。

## 提交记录

代码提交 `348039009aca0d50cbaca110b518931945573e3c`（`refactor(皮肤分离): 将原生AI批次解除审核迁入模块`）已推送到 `origin/feat/skin-first-separation`。本报告与合同/进度更新由独立文档提交记录。
