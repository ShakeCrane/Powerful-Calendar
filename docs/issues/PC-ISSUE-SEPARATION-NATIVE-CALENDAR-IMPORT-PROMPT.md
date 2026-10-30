# PC-ISSUE-SEPARATION-NATIVE-CALENDAR-IMPORT-PROMPT

**状态：PARTIAL**

**日期：2026-10-30**

**代码提交：** `f2f4d7b2952d70335a2f5c92349b0d6c7e85f281`

**范围：** 将首次系统日历导入提示的 Native 内容渲染迁入 `:native-skin`。

## 根因与修复

首次询问是否读取系统日历时，标题、一次性快照说明和两颗按钮仍由 `PowerCalApp` 直接绘制。确认后标记 onboarding 状态、检查/请求 `READ_CALENDAR` 权限并调用系统日历导入也由 Host 执行。

新增 `NativeCalendarImportPrompt` 及仅含 dialog slots 的 `:skin-api` 接口。Native module 负责说明文字和按钮；Host 保留首次提示持久化、权限判断/申请、系统内容读取和导入服务。点击“暂不导入”、系统关闭和“导入一次”的现有语义保持一致，skin-aware dialog shell 保持原路由。没有修改 Clear、Core、权限规格或默认皮肤选择。

## 修改文件

- `skin-api/src/main/java/dev/powerfulcalendar/ui/v2/NativeCalendarImportPromptModel.kt`
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeCalendarImportPrompt.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/NativeCalendarImportPromptComposeTest.kt`
- `docs/UI_SKIN_EXTENSION_CONTRACT_V4.md`
- `docs/SEPARATION_REMOTE_GOVERNANCE_AND_ROADMAP_2026-10-30.md`
- `docs/agent-handoff/PROGRESS.md`

## 测试与构建证据

环境：Windows、离线 Gradle、API 35 Robolectric。日志位于 `.local/android-test/reports/native-calendar-import-prompt-separation-20261030/`。

定向命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest --tests '*NativeCalendarImportPromptComposeTest' --offline --rerun-tasks --max-workers=2 --console=plain
```

结果：**2/2 PASS**。验证快照/不监测说明、明确导入动作、暂缓动作与系统 dismiss 均映射到 Host 回调；Native/Skin 边界通过。Focused 日志 SHA-256：`3ADA14F834149F141BF4937554304C45DBBBA5580FEE0D72E2D503CA4EA46756`。

完整 JVM 命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest --offline --rerun-tasks --max-workers=2 --console=plain
```

结果：**776/776、121 suites、0 failures/errors/skips**。日志 SHA-256：`AED16D292F9E450F37643EE3553C0466C94FBBC41FC2719557499FB1D7761310`。

构建门禁命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=2 --console=plain
```

结果：**BUILD SUCCESSFUL，144 tasks**。AndroidTest Kotlin 编译、Debug APK、lint 和源码边界均通过。APK 为 66,516,562 bytes，SHA-256：`F3D0EC5BFBD0A09C7A54ADCC71745ED8B2A3D5C5A2075B196F69041AB4323B1B`。构建日志 SHA-256：`EDFC797B1A1956CED0FEF303BDF0912CFCA656C3728D2EC3BE70EC015422AAA8`。

## 剩余验收

- Native prompt renderer：**CODE COMPLETE**；Robolectric focused/full JVM：**TESTED**。
- 真实 API 35 Android Emulator 上的系统权限对话框与日历 Provider 导入：**NOT TESTED**。当前 ADB 无设备；`emulator.exe` 与 system image 存在，但没有 AVD，SDK Manager 未识别 emulator package。创建 AVD 被 `avdmanager` 拒绝；未改写 SDK 或覆盖 AVD。详见 [Native event inspection Issue](PC-ISSUE-SEPARATION-NATIVE-EVENT-INSPECTION.md)。
- SEP-11 设置/权限整体仍 **INCOMPLETE**；本 Issue 不声称系统导入全链路设备验收通过。

## 提交记录

代码提交 `f2f4d7b2952d70335a2f5c92349b0d6c7e85f281`（`refactor(皮肤分离): 将原生日历导入提示迁入模块`）已推送到 `origin/feat/skin-first-separation`。本 Issue 与契约/路线图/进度更新随后由独立文档提交记录。
