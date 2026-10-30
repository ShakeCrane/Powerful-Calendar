# PC-ISSUE-SEPARATION-NATIVE-EVENT-INSPECTION

**状态：PARTIAL**

**日期：2026-10-30**

**代码提交：** `77d9da09d52d6ea1750e6ec2728288361f69959e`

**范围：** Native UI 展示 AI 批次锁定期间的正式事件只读详情。

## 根因与修复

当 AI 批次锁定正式事件操作时，`PowerCalApp` 仍在 Host 中直接绘制事件标题、摘要、备注、只读提示和关闭按钮。该原生展示片段没有进入 `:native-skin` 模块。

新增 `NativeEventInspection` 和 `NativeEventInspectionModel`。Host 继续选定事件，使用原有 `TemporalText.summarize` 生成摘要，控制弹窗生命周期，并供应现有 skin-aware dialog shell；Native renderer 只收到标题、摘要、备注三段字符串和关闭回调。AI 锁、读写授权、事件选择和持久化行为均未更改。Clear 和冻结 Core 未修改。

## 修改文件

- `skin-api/src/main/java/dev/powerfulcalendar/ui/v2/NativeEventInspectionModel.kt`
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeEventInspection.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/NativeEventInspectionComposeTest.kt`
- `docs/UI_SKIN_EXTENSION_CONTRACT_V4.md`
- `docs/SEPARATION_REMOTE_GOVERNANCE_AND_ROADMAP_2026-10-30.md`
- `docs/agent-handoff/PROGRESS.md`

## 测试与构建证据

环境：Windows、离线 Gradle、API 35 Robolectric。忽略的完整日志保存在 `.local/android-test/reports/native-event-inspection-separation-20261030/`。

定向命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest --tests '*NativeEventInspectionComposeTest' --offline --rerun-tasks --max-workers=2 --console=plain
```

最终结果 **2/2 PASS**：事件标题/摘要/备注与只读提示均显示，显式关闭触发 Host 回调；display model 的三个业务属性均为字符串。Native module 和 skin source boundary 检查通过。

首次定向运行中，结构反射测试将 Compose 编译生成的稳定性字段也计入了 DTO 字段数，导致 **1/2 FAIL**。随后将断言收窄为检查 `title`、`summary`、`notes` 三个明确属性，重跑 **2/2 PASS**。首次失败与最终 focused 日志 SHA-256：`29DC1ED55A2DCCCFFD1A2A6FC753F1E8E69C109188471C0C07135EED75554436` / `DDF40414F27CBF58E723FE5BD3C014ABCE9CA2815B90DA3B6A187CAC6C8113D6`。

完整 JVM 命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest --offline --rerun-tasks --max-workers=2 --console=plain
```

结果 **774/774、120 suites、0 failures/errors/skips**。日志 SHA-256：`05C1ACC2DFD23B6511641BC1AD14CDF8053FF3F0722A57E3DF354BBE5F2B3A9D`。

构建门禁命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=2 --console=plain
```

结果 **BUILD SUCCESSFUL，144 tasks**。AndroidTest Kotlin 编译、Debug APK、lint 和源码边界均通过。APK 为 66,500,178 bytes，SHA-256：`9933194B072F5667C2F608F215EF9C461D197F89D24315739714C39E6A7385A9`。构建日志 SHA-256：`E263815F7BF5434A1FBDA7A63234022B62CE114C8AF2D77B03E82A71A498B8E7`。

## 虚拟设备状态与剩余验收

- Robolectric API 35 Compose 回归：**TESTED**。
- ADB 在线设备：无。`emulator.exe` 存在且 Android 35 x86_64 system image 已安装，但 AVD 列表为空；SDK Manager 未识别 `emulator` package，emulator 目录缺少 `package.xml`。`avdmanager create avd` 因而拒绝创建；未安装或覆盖 SDK、未清理 AVD。Android Emulator UI 行为：**NOT TESTED**。
- Native renderer 和 Host 路由已编译；由于没有可启动 AVD，整条 AI lock → 查看 → 返回路径的虚拟设备验收仍 **NOT TESTED**。
- SEP-10 整体仍 **PARTIAL**。本 Issue 不声称 AI 全流程等价完成。

## 提交记录

代码提交 `77d9da09d52d6ea1750e6ec2728288361f69959e`（`refactor(皮肤分离): 将原生锁定事件查看迁入模块`）已推送至 `origin/feat/skin-first-separation`。本 Issue 与契约/路线图/进度更新随后由单独文档提交记录。
