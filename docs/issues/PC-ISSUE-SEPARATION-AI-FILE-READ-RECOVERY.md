# PC-ISSUE-SEPARATION-AI-FILE-READ-RECOVERY — 验证 AI 附件读取失败恢复

日期：2026-10-28

状态：**Host 文件读取失败恢复回归 PASS；SEP-09 / S3 整体仍 NOT TESTED。**

## 问题与根因

Host 已对 SAF 内容读取异常执行 catch/finally：显示“不上传、不创建事件”的错误并释放附件 busy 状态。但此前的集成测试覆盖了成功的 ICS 和文本文件读取，没有覆盖文件提供方在开始读取正文时抛出 I/O 错误的生产调用路径。风险是附件控件锁未释放、错误不可见，或异常路径错误地继续进入 AI 请求/日历写入。本次将该缺口接入真实 `PowerCalApp` Host 流程验证；没有发现生产代码缺陷，也没有修改生产代码。

## 验收预期与实际结果

| 检查 | 预期 | 实际 |
|---|---|---|
| 失败来源 | 用户选中文件后，ContentResolver 读取正文时失败 | Robolectric ContentResolver 提供 `agenda.txt` / `text/plain`，输入流抛出 `IOException` |
| 用户反馈 | 显示失败信息，并明确未上传、未创建事件 | Compose 中出现完整的“无法安全提取文件……没有上传给 AI，也没有创建事件”提示 |
| 状态恢复 | picker busy 解除，附件操作重新可用 | `chatAttachmentBusy=false`，`chatAttachmentAvailable=true` |
| AI 与日历边界 | 不调用 AI、不写聊天消息、草稿、身份、正式事件或 commit | loopback AI sentinel 无连接；聊天投影为空；隔离 Store 的全部计数与打开前一致；无 AI proposal |

此测试使用生产 `PowerCalApp`、`WebSkinBridgeRuntime`、Activity Result 路由和 `PowerStore`，只将 ContentResolver 输入流替换为确定性失败夹具。它验证 Host 的错误恢复逻辑，不代表真实 DocumentsUI、真实 SAF Provider 或设备行为已通过。

## 修改内容

- 在 `app/src/test/java/dev/powerfulcalendar/ui/v2/WebAiAttachmentFlowIntegrationTest.kt` 新增 `skinAiFileReadFailureReleasesPickerWithoutUploadOrCalendarWrites`。
- 在 `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md` 为 SEP-09 记录这个 Robolectric 恢复切片，整体状态保持 `NOT TESTED`。
- 更新本报告和 `docs/agent-handoff/PROGRESS.md`。
- 没有修改生产代码、Clear Skin、Studio、Core 语义、用户数据或凭据。没有访问外部 AI Provider。

## 验证命令与结果

### 单项恢复回归

```powershell
$env:ANDROID_HOME='E:\android-sdk'
$env:ANDROID_SDK_ROOT='E:\android-sdk'
.\gradlew.bat :app:testDebugUnitTest --offline --no-daemon --console=plain --tests dev.powerfulcalendar.ui.v2.WebAiAttachmentFlowIntegrationTest.skinAiFileReadFailureReleasesPickerWithoutUploadOrCalendarWrites
```

**1/1 PASS**。日志：`.local/android-test/reports/separation-20261028/ai-ics-route-cb337ffd7bb94627b4309d01803bd957/focused-read-failure-sdk-env.log`；SHA-256 `B2811BF524BFDA1F67895488A472E422D79AB2777FB713F5ABA136EF027C98E1`。

### 完整附件集成类

```powershell
$env:ANDROID_HOME='E:\android-sdk'
$env:ANDROID_SDK_ROOT='E:\android-sdk'
.\gradlew.bat :app:testDebugUnitTest --offline --no-daemon --console=plain --tests dev.powerfulcalendar.ui.v2.WebAiAttachmentFlowIntegrationTest
```

**6/6 PASS**，0 failures / errors / skipped。日志 SHA-256：`FACC2D56AE4AFF553628B585550E8CBEAB6CD4E8B35C44B57D8BAB1C762D8B85`。

### 完整离线 Gradle 门禁

```powershell
$env:ANDROID_HOME='E:\android-sdk'
$env:ANDROID_SDK_ROOT='E:\android-sdk'
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

**623/623 JVM tests、87 suites、0 failures / errors / skipped、88/88 tasks PASS**。source boundary、Debug APK 和 AndroidTest APK 编译通过；lint **0 errors / 52 warnings**。完整日志 SHA-256：`A507E1DA46B6A7A8BC401549A3986505D4202012DD8A32EC591597657FFC26AC`。

本 Issue 只改 JVM 测试和文档。Skin Studio 源码未改，相关 **70/70 Python、Chrome Headless E2E、Clear bundle verify 是 PREVIOUS PASS**，证据见前一条 AI ICS Issue 报告与 `.local/android-test/reports/separation-20261028/ai-ics-route-cb337ffd7bb94627b4309d01803bd957/`；它们不证明 Android WebView 或设备 Picker 通过。

构建产物哈希：

- Debug APK：`0281716B2EC5E73071495E80A8B81B65BD7DC0F8ECCAA6BEDA259E8B809AEDFB`。
- AndroidTest APK：`6B89A35252F0106B6A1B9DB45918C22303CA422391644EB0A77A329F413BAEB9`。构建时工作区有另一 Agent 未提交的 `WebSkinManagementInstrumentedTest.kt` 修改，该文件被编译，但未由本 Issue 暂存或提交。
- lint XML：`B3D4AF5AC60BC562F9727C97C5A65BC4DACC0DC7AD59B44907D6B626CF863BC0`。

## 环境问题与处理

首次 Gradle 尝试在依赖解析阶段因 worktree 没有 `local.properties` 且进程环境没有 `ANDROID_HOME` 而停止，没有运行测试。确认 `E:\android-sdk`、`adb` 和 API 36 平台存在后，通过当前 PowerShell 进程的 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 重跑成功；没有创建或提交本地配置。完整门禁日志中记录了授权重跑结果。

## 未覆盖范围与阶段状态

- 未启动 Android DocumentsUI；未使用真实 DocumentsProvider、Android WebView、AVD 或实体设备。
- 未验证真实 AI Provider、OCR 识别成功/失败、文件权限撤销、进程死亡恢复和 Native/Skin 对照回放。
- SEP-09 的聊天/附件/上下文/错误全量功能等价尚未闭环，仍为 **NOT TESTED**；S3 与总体分离任务继续进行。

测试与报告提交 `512bfe5b4623dd638eddbd53e6d79caf5172b44e`（`test(分离): 验证 AI 附件读取失败恢复`）已推送至 `origin/codex/separation-skin-settings`。
