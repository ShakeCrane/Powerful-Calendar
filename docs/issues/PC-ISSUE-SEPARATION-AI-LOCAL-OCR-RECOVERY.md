# PC-ISSUE-SEPARATION-AI-LOCAL-OCR-RECOVERY — 验证本地 OCR 核对与失败恢复

日期：2026-10-28

状态：**Host OCR 交接与同步异常恢复 PASS；实际 ML Kit 识字及 SEP-09 / S3 整体验收仍 NOT TESTED。**

## 根因与修复

原 `runLocalOcr` 直接在 Composable 中创建 ML Kit recognizer 并调用 `process()`。图片 URI 解析有局部保护，但 recognizer 初始化和 `process()` 的同步异常没有统一错误边界；这些异常可从 Compose 点击回调逸出。直接耦合还使本地 OCR 的用户核对流程无法在 Robolectric 中确定性测试。

新增 Host-owned `LocalImageOcr` 边界，默认 `MlKitLocalImageOcr` 继续调用同一中文 ML Kit recognizer。recognizer 完成时关闭；输入图片读取错误映射为“无法读取图片”；初始化或同步处理错误由 Host 捕获并显示 OCR 失败提示。`PowerCalApp(store, nativeRecoverySignal)` 保持原公开入口，另由内部实现接收 OCR 服务；测试通过内部包装注入确定性 fake。

## 验收结果

1. 图片 Picker 结果只打开方式选择层，不立即识图或上传。用户明确选择“仅本地 OCR”后才调用 OCR 服务；成功文本进入“核对 OCR 识别原文”编辑框，用户可修改。取消核对不产生 AI 请求、聊天记录或日历写入。
2. OCR 服务同步抛出初始化错误时，Compose 不崩溃；用户看到具体 OCR 失败提示，附件入口仍可用，聊天与 Store 保持不变。
3. 两条路径均未创建草稿、身份、正式事件或 commit。

这些断言使用注入的 fake OCR 引擎，证明生产 Compose/Host 交接、用户选择和错误处理，不证明 ML Kit 模型实际识别图片正确。

## 修改文件

- `app/src/main/java/dev/powerfulcalendar/ui/v2/LocalImageOcr.kt`：新增 Host OCR 接口及 ML Kit 默认实现，封闭初始化、图片读取、异步结果和资源关闭路径。
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`：保留公开 `PowerCalApp` 签名，内部委托默认 ML Kit；本地 OCR 同步与异步错误统一反馈。
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebAiAttachmentFlowIntegrationTest.kt`：加入本地 OCR 用户核对成功流与同步初始化失败恢复回归。
- 更新 SEP-09 矩阵、进度记录和本 Issue 报告。

## 测试与证据

环境：Windows PowerShell、Robolectric API 35、Android SDK `E:\android-sdk`。Gradle 通过进程级 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 使用本机 SDK；未写入 `local.properties`。机器没有 emulator 可执行文件；本轮没有启动 Android WebView、AVD 或实体设备。

定向 OCR 流程：**2/2 PASS**（文本可编辑/用户取消；同步 OCR 初始化失败显示恢复提示）。日志 SHA-256：`0994DC47AF1E6055594A3EB4A333D20C3B9D6A11AFED6A000C3E7F30141BD3B9`。

完整附件集成类：**9/9 PASS**，0 failures / errors / skipped。日志 SHA-256：`F3FF373B5E23FF23DFC0FE82FF8D502370DC155B5D3030C45EE1FDA440CD7B14`。

完整离线门禁命令：

```powershell
$env:ANDROID_HOME='E:\android-sdk'
$env:ANDROID_SDK_ROOT='E:\android-sdk'
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果：**626/626 JVM tests、87 suites、0 failures / errors / skipped、88/88 tasks PASS**；source boundary、Debug APK 与 AndroidTest APK 编译通过；lint **0 errors / 52 warnings**。完整日志 SHA-256：`C9230FEACB1DC20B2B1096730CDE7C5F026181E4128EEF330B3F7B7E73F9F2A6`。

构建哈希：Debug APK `357F2D698A78D7D9643AF5F7E1291F185445D3D9226CE5F4B5E7E80288AF8FAF`；AndroidTest APK `6B89A35252F0106B6A1B9DB45918C22303CA422391644EB0A77A329F413BAEB9`；lint XML `1CD7790BFDF2A843A56BC734EDD45F0C83906ACDDF006DDE29D48352CCC0575D`。AndroidTest APK 构建时包含另一 Agent 未提交的 `WebSkinManagementInstrumentedTest.kt` 修改；未将该文件纳入暂存或提交。

Studio / Clear 源码没有变化；Skin Studio **70/70 Python**、Chrome Headless E2E、Clear bundle verify 为同日 **PREVIOUS PASS**，见本地目录 `.local/android-test/reports/separation-20261028/ai-ics-route-cb337ffd7bb94627b4309d01803bd957/`。这些结果不验证 ML Kit 或 Android WebView OCR。

## 剩余验证

- bundled ML Kit 中文模型对实际图片的识字正确率、模型不可用/下载失败、输入权限撤销、图片旋转和大图行为未测。
- 系统 DocumentsUI、Android WebView/AVD、实体设备、真实 AI Provider 与完整 Native/Skin 回放未测。
- 因此只签核 Host 的本地 OCR 选择、核对界面和同步异常恢复；SEP-09 与 S3 整体仍 **NOT TESTED / 进行中**。

测试与报告提交 SHA 在提交推送后补入本报告和进度记录。
