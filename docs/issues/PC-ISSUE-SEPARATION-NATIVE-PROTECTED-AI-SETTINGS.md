# PC-ISSUE-SEPARATION-NATIVE-PROTECTED-AI-SETTINGS

**状态：CLOSED — CODE COMPLETE / TESTED**

**日期：2026-10-30**

**代码提交：** `8afabd80232cf33ce063b9d650f0286e184cfea8`

**范围：** 将 Native 设置页 AI 配置卡片与非密钥字段呈现迁入 `:native-skin`，保留 API key 输入与所有持久化/授权状态在 Host。

## 根因与修复

Native Settings 主体和个人资料卡片已位于 `:native-skin`，但 AI 配置卡片、端点/模型输入、开关、文件授权撤销按钮及保存操作的显示仍由 `HostProtectedSettings.kt` 直接绘制，造成 Native 设置页展示边界不完整。

新增 `NativeProtectedAiSettingsSection`。`:skin-api` DTO 只包含端点、模型、开关、dirty 状态和文件处理授权状态，不含 API key、Android `Context`、偏好对象或 AI 存储服务。Host 继续读取并保存 `AiSettingsStore`、检查/撤销文件处理授权，并通过 `protectedApiKeyField` 插槽组合密码输入框；密钥值不传入 Native renderer。按钮仍调用原 Host 保存与保存并启用逻辑，设置行为未变。未修改 Clear、冻结 Core、默认皮肤选择或 Native 回退。

## 修改文件

- `skin-api/src/main/java/dev/powerfulcalendar/ui/v2/SettingsVisualContext.kt`
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeProtectedAiSettingsSection.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/HostProtectedSettings.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/NativeProtectedAiSettingsSectionComposeTest.kt`
- `docs/UI_SKIN_EXTENSION_CONTRACT_V4.md`
- `docs/SEPARATION_REMOTE_GOVERNANCE_AND_ROADMAP_2026-10-30.md`
- `docs/agent-handoff/PROGRESS.md`
- 本报告

## 测试与构建证据

环境：Windows、仓库 worktree、离线 Gradle 缓存、API 35 Robolectric；Android SDK `E:\android-sdk`。

定向命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest --tests '*NativeProtectedAiSettingsSectionComposeTest' --tests '*NativeProtectedProfileSectionComposeTest' --tests '*NativeToolsPageProtectedSlotsComposeTest' --offline --rerun-tasks --max-workers=2 --console=plain
```

结果：**7/7 PASS**：Native AI settings 2/2、Native profile 2/2、Native Settings protected slots 3/3；两项源码边界均通过。定向日志 SHA-256：`3ADA14F834149F141BF4937554304C45DBBBA5580FEE0D72E2D503CA4EA46756`。

完整门禁命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=2 --console=plain
```

结果：**BUILD SUCCESSFUL，152/152 tasks**；全量 JVM **778/778，122 suites，0 failures/errors/skips**；AndroidTest Kotlin 编译、Debug APK、lint 以及两项源码边界检查通过。完整门禁日志 SHA-256：`E31E9CEA80DDD48604EEB3F711C729C05ADE88F1F2DC14DD6903099F7C659981`。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，66,516,562 bytes，SHA-256：`0015F118472E03652DD1EB849D77C796C2F8530B61C12F516A9B1AA561C4E471`。

证据日志保存在忽略目录 `.local/android-test/reports/native-protected-ai-settings-separation-20261030/`。`git diff --check` 通过。

## 剩余验证

- Native AI settings renderer：**CODE COMPLETE / TESTED**；API key value remains Host-only.
- SEP-11 设置/权限整体：仍 **INCOMPLETE**；其他权限、文件/备份和设备路径不在此 Issue 内。
- 本机 `adb devices -l` 没有在线设备；Android Emulator、真机/OEM、TalkBack 行为 **NOT TESTED**。AndroidTest 源码已编译，本次未执行仪器测试。
- Clear 已坏且按所有者指示不在本次修改范围内；Clear 源文件及 APK 资源均未修改。

## 提交记录

代码提交 `8afabd80232cf33ce063b9d650f0286e184cfea8`（`refactor(皮肤分离): 迁入原生AI设置卡片`）已普通推送到 `origin/feat/skin-first-separation`。合同、路线图、进度与 Issue 报告由后续单独文档提交记录。
