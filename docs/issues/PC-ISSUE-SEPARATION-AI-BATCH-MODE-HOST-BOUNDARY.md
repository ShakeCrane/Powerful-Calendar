# AI 批次模式 Host 类型边界与编辑器状态门控

**状态：PASS（本地代码及验证已完成；远端推送待网络恢复）**
**阶段：Core-first / Skin-first PHASE 1 — Core Feature API**
**代码提交：** `2c725e356498d0d02a3373a6be5daf0fc6dd2351`
**证据目录：** `.local/android-test/reports/phase1-ai-batch-mode-api-type-20261102/`

## 问题

Native 和 Web 的 AI 批次选择虽已存在稳定的 `AiBatchModeV1`，Host 仍在 Web action、Native 映射、共享待处理状态和 UI 投影中暴露内部 `AiBatchRunner.Mode`。这让表现层依赖执行器类型，削弱了同一 Core 功能入口的边界。

另一个实际 Host 状态差异是：Web 页面上仍可直接收到 `PrepareAiBatch`，即使新建事件编辑器已经打开。原处理会在聊天中追加批次范围提示；它不会绕过后续审核或 Core 授权，也不会写入事件，但会在编辑器仍活动时启动不相容的交互。Native 模态层会挡住通常的菜单点击。

## 修复与边界

- Native/Web Host action、状态、投影和范围选择统一使用已有 `AiBatchModeV1`。只有 `StoreAiFeatureApiV1` 在调用 Runner 时转换到 `AiBatchRunner.Mode`。
- Web action handler 和 Native/Web 共享的批次对话入口都拒绝存在 `newEventSession` 时的模式选择。拒绝发生在设置待处理模式或追加聊天消息之前。
- 批次授权语义未变：开始范围对话不构成正式操作授权；Host 仍需显示具体操作清单并显式确认，Core 仍执行原有的一次性授权与事务。
- 没有增加 API、持久化字段或备份格式。

## 回归与验证

- 针对性组：**15/15 PASS，4 个 XML suite，0 failures/errors/skips**。覆盖 Native/Web 稳定模式投影、审核 UI，以及新建事件编辑器打开时 Web 批次动作不改变批次阶段、聊天条数或 Store 计数。日志 `focused-with-host-guard-retry.log`，SHA-256 `384140b12fa8c19d2a5b423bbbd7d254dfd94d29e053ee4b48e35b07dc30a0bd`。
- 初次新增回归的测试选择器匹配到了两个可访问的“新建”节点，因此测试在检查 Host 防护前失败。测试改为比较所有同名节点的打开前后数量后重跑通过；该失败日志 `focused-with-host-guard.log`，SHA-256 `7d8c952150c303f05adbf5e80c0ee7bf415cf786021b2648fe6ed6ee63b60d27`。这是测试断言歧义，不是产品逻辑失败。
- 首次 Gradle 启动的子进程没有 `ANDROID_HOME`，在依赖解析前停止；之后显式设置 `ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 为 `E:\android-sdk` 并完成全部验证。初次启动日志被后续重跑写入同一路径时覆盖，故没有单独日志哈希。
- 最终强制离线全量门禁：**944/944 JVM 测试，139 XML suites，0 failures/errors/skips，154 tasks**；`verifySkinSourceBoundary`、`verifyNativeSkinBoundary`、Debug APK、`lintDebug`、`compileDebugAndroidTestKotlin` 和 `git diff --check` 均通过。Lint **0 errors / 54 warnings**。命令：

  ```powershell
  $env:ANDROID_HOME='E:\android-sdk'
  $env:ANDROID_SDK_ROOT='E:\android-sdk'
  .\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain
  ```

- 全量日志 `full-gate-after-host-guard.log`，SHA-256 `3e0f46dc0f2de5b91f103ba7bf47516437c1bbe01074e76b4df5e0b15030d2f4`。JUnit 清单 `junit-full-manifest.txt`，SHA-256 `22bc9c5a8f4742676685d5bc7a44381a7ba5817136c620fb60c6ea9187b974b1`；证据 JSON `manifest.json`，SHA-256 `1c9fac50def18847febadcf9f1f355a7ddf4655b4426547c2b12389a8dd9db30`。
- Debug APK：**67,060,418 bytes**，SHA-256 `12279731a29142f377b0acaebfdc60e9bd375b68351c7848b75de3e416f725f2`。
- 源码搜索中 `AiBatchRunner.Mode` 只剩 Core 执行适配器 `StoreAiFeatureApiV1.kt:770–771`。
- 环境：Windows 11、Gradle 8.14.3、JDK 20.0.1、Android SDK `E:\android-sdk` / API 36。ADB 无设备，AVD 列表为空；Android instrumentation、打包 WebView 和真实设备 Native/Web parity **NOT TESTED**。

## Git 状态

代码提交为中文提交 `2c725e356498d0d02a3373a6be5daf0fc6dd2351`（`分离：统一 AI 批次模式 Host 边界`）。本次从 GitHub 读取和普通快进推送均因访问 `github.com:443` 超时而未完成；本地最近一次已核实的 `origin/feat/skin-first-separation` 是 `a5d0cfbf405af91d8e235d701bb980bed380e70b`。推送会在网络恢复后重试，不会强制改写远端。

Phase 1 继续开放；这份 Issue 只关闭本切片，不代表 Core Feature API 阶段完成。

## Follow-up — DraftReview DTO贯穿批次审核（2026-11-02）

Host 原先把 `DraftReviewFeatureApiV1.queryPendingDrafts()` 返回的稳定 `DraftReviewCardV1` 再映射回内部 `DraftCard`，随后 Native 审核、AI 候选生成和 Web 批次投影继续依赖 Core 实现类型。现在该批次路径从 API 查询到 Host 审核快照都保留 `DraftReviewCardV1`；候选映射直接读取版本化字段。Core 队列查询已经只返回 `PENDING`、`CONFLICT`、`INVALID`，所以移除了重复的 Host `isActionable` 过滤。

Native Compose 仍接收独立的 `NativeAiBatchAuthorizationModel`，由该 DTO 投影生成；Web 使用相同 API DTO 生成批次摘要。Host 不再把审核卡片还原为 Core `DraftCard`。刷新成功时，批次选择使用 `reload()` 发布的同一份草稿快照；若事件 revision 在发布前变化导致刷新作废，Host 才重新查询草稿并同步待处理计数。既有确认、一次性授权、Store 版本复核及逐项事务均未改变，也没有新增 API、数据库字段或备份格式。

- **代码提交：** `98947ce81bcd446fde09e0f2d7844afb9ab2ebcb`（`分离：批次审核贯穿稳定草稿 DTO`）。
- **针对性测试：** `AiReviewComposeTest`、`NativeAiProjectionTest`、`WebAiBatchProjectionTest`、`WebAiProposalReviewIntegrationTest` 共 **30/30 PASS，4 个 XML suites**；focused log SHA-256 `68FC5872C952FB33A0C796F07E80DE114D6E06F4D44825D7960D81E3BE10BB4B`。
- **最终完整离线门禁：** **944/944 JVM，139 XML suites，0 failures/errors/skips，154 tasks**；`verifySkinSourceBoundary`、`verifyNativeSkinBoundary`、Debug APK、Lint、AndroidTest Kotlin 编译及 `git diff --check` 均 PASS。Lint **0 errors / 54 warnings**。命令与逐 suite 清单保存在 `.local/android-test/reports/phase1-ai-batch-draftreview-dto-20261102/`。
- **产物：** Debug APK **67,060,418 bytes**，SHA-256 `4362C26AA5F7D0D239F43C814DFA2AC3B57A7B10E34FF893F5F4EF961FE1FC10`；最终全量日志 SHA-256 `3E0F46DC0F2DE5B91F103BA7BF47516437C1BBE01074E76B4DF5E0B15030D2F4`；JUnit manifest SHA-256 `7AD18B237E52402008D4DE1B5629F4C78AE007662117A119297478097ED0FC62`。
- **环境与限制：** Windows 11、Gradle 8.14.3、JDK 20.0.1、Android SDK `E:\android-sdk` / API 36。ADB 没有设备，AVD 列表为空；Android instrumentation、打包 WebView 和设备 Native/Web 行为 **NOT TESTED**。Phase 1 保持开放。
