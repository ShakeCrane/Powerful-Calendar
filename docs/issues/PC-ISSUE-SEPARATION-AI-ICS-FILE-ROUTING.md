# PC-ISSUE-SEPARATION-AI-ICS-FILE-ROUTING — 验证 AI 附件入口的 ICS 本地路由

日期：2026-10-28

状态：**本地 ICS 选路回归 PASS；SEP-09 / S3 整体仍 NOT TESTED。**

## 问题与根因

已实现的 Host 文件处理会在 AI 附件回退之前识别日历文件：MIME、扩展名或文件正文开头的 `BEGIN:VCALENDAR` 任一信号可将其导向确定性 ICS 导入器。此前回归覆盖了普通 AI 文件附件和其他 AI 聊天行为，却没有将 Skin 发出的 `pickAiFile`、Android 文件结果、内容特征识别、ICS 导入及 Host 写入边界连成一个端到端自动化用例。这是测试覆盖缺口；本次没有复现生产代码缺陷，也没有修改生产代码。

## 验收预期与实际结果

| 检查 | 预期 | 实际 |
|---|---|---|
| 文件入口 | Skin `pickAiFile` 请求 Android 文档选择 Intent | 实际 `PowerCalApp` 生成 `ACTION_OPEN_DOCUMENT`，类型为 `*/*` |
| 内容识别 | 文件名和 MIME 有误导性时，仍按 ICS 正文识别 | `misleading.txt` / `text/plain` 文件由 `BEGIN:VCALENDAR` 正确路由到本地 ICS 规划器 |
| 持久化结果 | 形成待用户检查的草稿和导入回执，不发布正式事件 | 恰好新增 1 个 ACTIVITY 草稿、1 个身份和匹配 fingerprint 的 checkpoint；正式事件及 commit 数均不增加 |
| UI 与 AI 边界 | 草稿可见；ICS 不变成 AI 提案、不上传给 Provider | Compose 中显示导入标题；没有 AI proposal；绑定在本机 loopback 的 AI sentinel 未收到连接 |
| 稳定性 | 文件处理结束后释放 picker busy 状态 | 最终状态 busy=false，草稿数与 checkpoint 一致 |

本用例使用生产 `PowerCalApp`、`WebSkinBridgeRuntime`、Android Activity Result 注册路径和 `PowerStore`；文件 URI / ContentResolver 由 Robolectric 测试夹具提供。它验证了生产 Host 对 picker 结果的处理及数据库效果，但没有启动系统 DocumentsUI，也没有读取真实磁盘文件。

## 修改内容

- 在 `app/src/test/java/dev/powerfulcalendar/ui/v2/WebAiAttachmentFlowIntegrationTest.kt` 增加 `skinAiFilePickerRoutesCalendarMagicToLocalImportBeforeAiFallback`。测试让 skin 请求选取文件，返回 MIME 与扩展名均误导的 ICS 内容，并验证 ICS planner、草稿、checkpoint、Host 状态、可见标题、事件/commit 计数和无 AI Provider 请求。
- 在 `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md` 补记该子场景证据。SEP-09 保持 `NOT TESTED`，因为系统 chooser、OCR、真实 Provider、Android WebView/设备和完整 Native/Skin 回放仍未验证。
- 没有修改 Core、Host 或 Skin 生产代码；没有接触真实日历数据、用户凭据或 AI Provider。

## 测试与证据

工作环境：Windows PowerShell；仓库 worktree `codex/separation-skin-settings`；Gradle 离线执行。Android SDK 的 `adb` 存在，但 `E:\android-sdk\emulator\emulator.exe` 不存在。本轮没有 Android WebView、AVD 或实体设备运行。AndroidTest APK 只做了编译；构建时工作区存在另一 Agent 未提交的 `WebSkinManagementInstrumentedTest.kt` 修改，该文件被编译但没有由本 Issue 暂存或提交。

### 定向回归

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --no-daemon --console=plain --tests dev.powerfulcalendar.ui.v2.WebAiAttachmentFlowIntegrationTest
```

**5/5 PASS**，0 failures / errors / skipped；新增 ICS 路由用例通过。完整日志：`.local/android-test/reports/separation-20261028/ai-ics-route-cb337ffd7bb94627b4309d01803bd957/focused-class-final2.log`，SHA-256：`6055EACB734EBC27C973343B01DC0183A029F5B877F76490C9D9A888B8ADDB2E`。

### 完整 Android/Gradle 门禁

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

**622/622 JVM tests、87 suites、0 failures / errors / skipped、88/88 tasks PASS**；source boundary、Debug APK、AndroidTest APK 编译及 lint 通过；lint **0 errors / 52 warnings**。完整日志：`.local/android-test/reports/separation-20261028/ai-ics-route-cb337ffd7bb94627b4309d01803bd957/full-gradle-gate-final.log`，SHA-256：`00898111B183D917ECE17D80E0A4616992D8AF62571A8F21AEABA0EE11636EC0`。

### Studio 和 Clear 相关检查

- `python -m unittest discover -s tools/skin-studio/tests -v`：**70/70 PASS**。日志 SHA-256：`1A51FDDC5EB31392DA9152298EAFB8C6171C8C3581C44C24A2F2E8046CF44DE8`。
- `node --check tools/skin-studio/tests/e2e_v3_source.cjs`：PASS。
- Chrome Headless Skin Studio E2E：PASS。附件能力在此处是明确的非读文件、非写日历 Mock；不作为 Android WebView 或真实文件选择证据。日志 SHA-256：`8DC1E115BD2D2B6A63CC82985B81A4787FFA67826632F20EB198BDF9F8FE90F9`。
- `python tools/skin-studio/build_default_clear.py --verify`：PASS，8 files / 381125 bytes；Clear bundle SHA-256：`04C5F92D1C30D00B84E4CBD90FA572DAF8103DAA05CA8F5C2552C6A9D94600AC`。日志 SHA-256：`700E8A004AABADAFDB71274D460262F09FF859C486E414196FB9D01323EF513B`。
- `git diff --check`（本 Issue 两个已有修改文件）：PASS。

### 构建产物哈希

- Debug APK：`0281716B2EC5E73071495E80A8B81B65BD7DC0F8ECCAA6BEDA259E8B809AEDFB`。
- AndroidTest APK：`6B89A35252F0106B6A1B9DB45918C22303CA422391644EB0A77A329F413BAEB9`。此 APK 编译时包含上述另一 Agent 留下的未提交 AndroidTest 文件；它仅作为本轮编译产物记录，不纳入本 Issue 的提交。
- Lint XML：`B3D4AF5AC60BC562F9727C97C5A65BC4DACC0DC7AD59B44907D6B626CF863BC0`。

## 失败尝试与测试夹具调整

首轮自动化执行遇到过本机环境和 Robolectric 调度问题，相关日志均保留在上述证据目录。沙箱默认权限下 Gradle wrapper 无法访问用户 Gradle 锁文件，获准的重跑才进入测试；Studio Python 首轮因本机 loopback / 临时文件权限报 11 项环境错误，授权重跑后 70/70 通过；Chrome E2E 也需本机 loopback 权限。首次测试结果回调没有在主线程派发，之后改由主线程执行；Robolectric 测试还需显式排空 Main Looper 才能观察生产协程完成状态。最终一版另将错误的 `editing.deck.open` 状态假设替换成 Compose 实际渲染标题断言。以上调整均只涉及运行权限、测试夹具或断言，没有绕过生产逻辑，也没有把失败记录当作通过证据。

## 未覆盖范围与阶段状态

- 未启动系统 DocumentsUI，未验证真实 DocumentsProvider、权限撤销、OCR、文件读取错误或进程中断恢复。
- 未访问真实 AI 服务；loopback sentinel 只用于确认本地 Provider 未被调用。
- 未验证 Android WebView、模拟器或实体设备行为，也未完成 Native/Skin 对照回放。
- 因此本 Issue 只为 SEP-09 增加一个已验证的自动化切片，不代表 SEP-09 或 S3 完成；上述设备与端到端场景仍 `NOT TESTED`。

交付提交 SHA 由同日期的 `docs/agent-handoff/PROGRESS.md` 记录；本报告、回归测试和验收矩阵说明按独立 Issue 一并交付。
