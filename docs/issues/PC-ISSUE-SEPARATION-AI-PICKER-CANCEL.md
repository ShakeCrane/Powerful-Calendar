# PC-ISSUE-SEPARATION-AI-PICKER-CANCEL — 验证 AI 附件选择器取消恢复

日期：2026-10-28

状态：**文件与图片 Picker 取消回归 PASS；SEP-09 / S3 整体仍 NOT TESTED。**

## 问题与根因

Skin 的 AI 附件操作由 Host 启动 Android Picker，并在取消回调中恢复 busy/available 投影。文件 `OpenDocument` 和图片 `GetContent` 使用不同的 Activity Result contract、不同回调路径；此前已验证打开 Picker 和取消图片授权确认，但没有验证 Android Picker 本身返回 `null` 时，两条入口都能清除锁并保持无副作用。这是生命周期恢复覆盖缺口。本次通过实际 `PowerCalApp` 和 Activity Result 注册路径验证现有逻辑，没有发现生产代码缺陷，也没有修改生产代码。

## 验收结果

| 入口 | Intent | 取消后的断言 |
|---|---|---|
| AI 文件附件 | `ACTION_OPEN_DOCUMENT`, `*/*` | busy 解除、附件操作重新可用；聊天历史、提案和 Store 均未改变 |
| AI 图片附件 | `ACTION_GET_CONTENT`, `image/*` | busy 解除、附件操作重新可用；未留下“图片识别方式”确认界面；聊天历史、提案和 Store 均未改变 |

回归通过 `RecordingActivityResultRegistry` 对生产 Launcher 派发 `null` 结果。它验证 Host 的取消回调和状态恢复，不模拟 DocumentsUI 的画面，也不代表真实系统 Picker/设备行为已通过。

## 修改内容

- 在 `WebAiAttachmentFlowIntegrationTest.kt` 增加 `cancellingSkinFileAndImagePickersRestoresAvailabilityWithoutWrites`，依次取消文件和图片 Picker，并分别核对 intent、busy/available 投影、聊天/提案及数据库计数。
- 将测试用 Activity Result registry 的结果参数改为可空，加入 `cancel()` 夹具方法。
- 更新 SEP-09 矩阵、进度记录和本 Issue 报告；没有改生产代码、Skin 源码或 Core 行为。

## 测试与证据

工作环境为 Windows PowerShell、Robolectric API 35、Android SDK `E:\android-sdk`。该 worktree 没有 `local.properties`，因此通过当前进程的 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 指向已存在 SDK；未写入本地配置。SDK 中 `adb` 与 API 36 平台存在，但没有 emulator 可执行文件。

单项 Picker 取消回归：**1/1 PASS**。日志 `.local/android-test/reports/separation-20261028/ai-ics-route-cb337ffd7bb94627b4309d01803bd957/focused-picker-cancel.log`，SHA-256：`79F0C51041D2205AC9EB5CA148CDB99095E1B7383214BA8B667BE4572BDC5ED3`。

完整附件集成类：**7/7 PASS**，0 failures / errors / skipped。日志 SHA-256：`95FF15DCC4E8F3461E20890B365BD6B773A40E5BD056FA444A264FE121F0A3E2`。

完整离线门禁：

```powershell
$env:ANDROID_HOME='E:\android-sdk'
$env:ANDROID_SDK_ROOT='E:\android-sdk'
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

**624/624 JVM tests、87 suites、0 failures / errors / skipped、88/88 tasks PASS**；source boundary、Debug APK 和 AndroidTest APK 编译通过；lint **0 errors / 52 warnings**。完整日志 SHA-256：`44757F36996BAD6BB97E1752E8BCC89EFB00D5245A562AD4D294A116303BC2E4`。

构建产物：Debug APK SHA-256 `0281716B2EC5E73071495E80A8B81B65BD7DC0F8ECCAA6BEDA259E8B809AEDFB`；AndroidTest APK SHA-256 `6B89A35252F0106B6A1B9DB45918C22303CA422391644EB0A77A329F413BAEB9`；lint XML SHA-256 `B3D4AF5AC60BC562F9727C97C5A65BC4DACC0DC7AD59B44907D6B626CF863BC0`。AndroidTest APK 编译时包含另一 Agent 未提交的 `WebSkinManagementInstrumentedTest.kt`，该文件未纳入本 Issue 的暂存或提交。

Skin Studio / Clear 文件没有变化；此前同日 **70/70 Studio Python、Chrome Headless Studio E2E、Clear bundle verify 为 PREVIOUS PASS**，见 AI ICS Issue 及其本地证据目录。`git diff --check` 对本次改动通过。

## 未覆盖范围

- 未启动系统 DocumentsUI；真实 SAF Provider、Android WebView、AVD、实体设备和设备旋转/进程恢复未测。
- OCR 成功与失败、真实 AI Provider 和完整 Native/Skin 回放仍未验证。
- 本回归只签核取消返回路径，SEP-09 与 S3 整体仍 **NOT TESTED / 进行中**。

测试与报告提交 SHA 在提交并推送后补入本报告和进度记录。
