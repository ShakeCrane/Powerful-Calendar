# PC-ISSUE-CORE-AI-CONVERSATION-API-V1 — 普通对话接入 Core Feature API

- **日期：** 2026-11-02（Asia/Shanghai）
- **优先级：** P1 — PHASE 1 Core Feature API
- **状态：** JVM / 源码边界 / APK 构建 **PASS**；设备及打包 WebView **NOT TESTED**
- **代码提交：** `3f64cfa`（`feat(分离): 接入 Core AI 对话 API`）
- **阶段状态：** PHASE 1 仍进行中；本切片不代表完整 Core 提取、SEP-09 完整验收或 cutover GO。

## 目标与结果

普通文本聊天此前由 `PowerCalApp` 直接调用 `AiClient.chat`，编译期 `AiVisualContext` 还暴露内部聊天消息、提案解释和批次模式类型。本切片将普通聊天请求、有限事件上下文读取和显式提案生成编排接入共享 `AiFeatureApiV1`，并让 Skin 渲染上下文只接收版本化 API DTO。

## 实现

- 在纯 Kotlin `:core-api` 增加 `AiConversationMessageV1`、`AiChatRequestV1`、`AiChatReceiptV1`、角色/意图/状态 DTO，以及 `AiFeatureApiV1.chat` 和 `AiModelPortV1.chat`。
- Store 适配器校验时区、消息角色、消息标识、历史条数和字符上限；请求最多 80 条消息、累计 480,000 个字符、单条最多 6,000 个字符。
- `shareCalendarContext` 未开启时不查询事件上下文；开启时仅传入最多 60 条正式事件。Android provider adapter 重新读取受保护设置，既有 `AiClient` 再次检查已保存的共享偏好。
- 128 项进程内收据缓存按 request ID 和 SHA-256 内容指纹识别完成请求：同一请求返回原收据并标记 `replayed`，同 ID 不同内容返回 `DUPLICATE_REQUEST_ID`。该缓存不跨进程持久化。
- 只有 `EXPLICIT` 意图携带的任务进入既有 `generateProposal` 路径。缺少任务时返回澄清；生成结果仍是待审核提案，聊天 API 不创建草稿、不发布正式事件，也不签发审批。
- `AiVisualContext` 现在只暴露 `AiConversationMessageV1`、`AiProposalReviewV1` 和 `AiBatchModeV1`。Native adapter 在 Host 边界将这些值映射为既有 Native 展示模型。
- 聊天记录持久化、清空/回退、图片/OCR 与文件接入、provider 凭据和通用工具编排仍属于后续工作。没有改数据库 schema、备份格式或冻结 Core 业务语义。

## 预期与实测

- **预期：** Native 与 Web 普通文本聊天经同一版本化 Core AI API 调用；上下文分享沿用既有用户偏好；模型输出不能授权写入；Skin 渲染器不再接收 AI Core 内部类型。
- **实测：** `PowerCalApp.submitAiChatInput` 调用 `aiFeatureApi.chat`；Android 端仍持有 provider 配置与凭据；Store adapter 对请求、可选上下文和提案生成负责；写入仍须经既有提案审核 API。API 回归覆盖上下文开关、收据重放/冲突、显式提案、缺失任务澄清、无写入和无效请求拒绝。

## 验证证据

环境：Windows PowerShell，Gradle Wrapper 8.14.3，`ANDROID_HOME=E:\android-sdk`，离线依赖，单 Gradle worker。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| `AiFeatureApiV1Test` + `NativeAiProjectionTest` | **15/15 PASS**，2 个 XML suite | `.local/android-test/reports/core-ai-chat-api-20261102/focused.log`；SHA-256 `396481CFD8F1F1FADBE62BF8B6C49ED757C9E03FBEA9BDFE0B5477E9DE0253FC` |
| 强制串行全量离线门禁 | **BUILD SUCCESSFUL**；**851/851 JVM**，134 XML suites，0 failures/errors/skips；两个 Skin source boundary、APK、lint、AndroidTest Kotlin compile 均 PASS | `.local/android-test/reports/core-ai-chat-api-20261102/full-gate.log`；SHA-256 `66437669BD4590A81A14A33E2DC0268BA5FFE3738A13F2D022E26DD82062876B` |
| lint | **0 errors / 53 warnings** | `app/build/reports/lint-results-debug.xml` |
| Debug APK | **66,863,726 bytes**；SHA-256 `FF712DAC4B8743FD5315C4F430D0794367CA6D3B6C342A6D44BA391A88691616` | `app/build/outputs/apk/debug/app-debug.apk` |
| 首轮完整门禁 | 850 passed / 1 failed：既有 `memoPagingContinuesTheSortedProjectionWithoutStoreWrites` 等待 Host 数据时超时；随后该测试单项及整个 `WebAiProposalReviewIntegrationTest` 类均通过，最终完整门禁 851/851 通过 | 保留首轮 `.local/android-test/reports/core-ai-chat-api-20261102/full-gate-first-attempt.log`，SHA-256 `6B3C8B1E4F81D1F8C671D039F431F88C1D67B71260A2296DF82D13B94C8038EE`；单项/整类日志分别为 `memo-isolated.log` / `memo-class.log` |
| `git diff --check` | **PASS** | 提交前检查 |
| Android instrumentation、Native/Web 设备路由、打包 Android WebView、Live provider、实体/OEM | **NOT TESTED** | ADB 无连接设备，`emulator -list-avds` 无已配置 AVD；AndroidTest 仅编译，未执行 |

完整门禁命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
```

## 限制与后续

- 对话记录仍由 Host 持久化；清空/回退和基于历史 revision 的状态命令尚未进入 Core API。
- API 层请求收据仅进程内有效；provider 断连或进程中断后的远端幂等语义未建立。
- 本次不是 Native/Clear 等价回放，也不提升 SEP-09 parity 状态；运行时、设备和 OEM 证据仍需单独取得。
- **唯一后续行动：** 将聊天记录查询、持久化、清空与回退接入版本化 Core AI conversation state API，并保留现有本地文件格式及 Web 历史 revision 检查。

## 主要源码

- `core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/AiFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/StoreAiFeatureApiV1.kt`
- `app/src/main/java/dev/powerfulcalendar/core/api/v1/AndroidAiModelPortV1.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PresentationSkinRuntime.kt`
- `app/src/test/java/dev/powerfulcalendar/core/api/v1/AiFeatureApiV1Test.kt`
