# PC-ISSUE-SEPARATION-NATIVE-PROTECTED-PROFILE

**状态：PARTIAL**

**日期：2026-10-30**

**代码提交：** `b17f43c451ba57c3cab59bb7382c984f708bcd48`

**范围：** 将 Native 设置页中的个人资料卡片与姓名表单迁入 `:native-skin`。

## 根因与修复

Native 设置页主体已位于 `:native-skin`，个人资料卡片仍在 `HostProtectedSettings.kt` 中直接绘制，导致设置页面的原生展示边界不完整。

新增 `NativeProtectedProfileSection` 与两个 `:skin-api` 值类型。Host 继续在应用私有偏好中读取和保存姓名，仅向 renderer 提供姓名字符串及输入/保存回调；80 字符上限和显式点击保存的行为保持不变。AI endpoint/model/key 表单及凭据状态继续由 Host 管理，不进入 Native module。未修改 Clear、冻结 Core、默认皮肤选择或 Native 回退。

## 修改文件

- `skin-api/src/main/java/dev/powerfulcalendar/ui/v2/SettingsVisualContext.kt`
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeProtectedProfileSection.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/HostProtectedSettings.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/NativeProtectedProfileSectionComposeTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/NativeToolsPageProtectedSlotsComposeTest.kt`
- `docs/UI_SKIN_EXTENSION_CONTRACT_V4.md`
- `docs/SEPARATION_REMOTE_GOVERNANCE_AND_ROADMAP_2026-10-30.md`
- `docs/agent-handoff/PROGRESS.md`

## 测试与构建证据

环境：Windows、离线 Gradle 缓存、API 35 Robolectric。详情日志位于 `.local/android-test/reports/native-protected-profile-separation-20261030/`。

定向命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest --tests '*NativeProtectedProfileSectionComposeTest' --tests '*NativeToolsPageProtectedSlotsComposeTest' --offline --rerun-tasks --max-workers=2 --console=plain
```

结果：**5/5 PASS**。回归验证姓名输入与显式保存回调、80 字符限制、设置页 protected slots，以及 API 中不含 Context、SharedPreferences 或 AI settings store 引用。

完整 JVM 命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest --offline --rerun-tasks --max-workers=2 --console=plain
```

结果：**772/772，119 suites，0 failures/errors/skips**。

构建门禁命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=2 --console=plain
```

结果：**BUILD SUCCESSFUL，144 tasks**。AndroidTest Kotlin 编译、Debug APK、lint、Native module 与 skin source boundaries 均通过。Debug APK 为 66,500,178 bytes，SHA-256：`D0051B77757107268DF5E2DB5AE2BDE6D6E08FB7AD289831F8F91A40629C0E09`。

| 证据 | 结果 | SHA-256 |
| --- | --- | --- |
| 最终定向测试日志 | 5/5 PASS | `F64D9F4241DBC01290A918F2DA89354809DF1D8D58603E85757D6EF58DD8899B` |
| 完整 JVM 日志 | 772/772，0 failures | `4C3DB938C806B31CE707C1C8E0E50BFE9B383ECE01CBC7DE6B5174AC508E8820` |
| 构建门禁日志 | BUILD SUCCESSFUL，144 tasks | `C04BFE96E1F471E2844EECCA7FEE57F7DD1CA7FEA2192510ED914786CC499367` |

测试迭代中，首次定向编译因使用了当前 Compose 测试库不存在的 `onNode` 顶层导入而失败；改为仓库已有的 `ComposeTestRule.onNode(hasSetTextAction())` 写法后，最终定向测试和全量 JVM 均通过。首次失败日志 SHA-256：`BD85645054E44AAB5BFE412D3D9CE70E451184E93F56AAAC03C485AD5CC40688`。

## 剩余验收

- Native profile renderer：**CODE COMPLETE**；定向与完整 JVM：**TESTED**。
- Settings/permissions 整体 SEP-11：仍 **INCOMPLETE**，其他设置/权限路径尚未全部皮肤化并验证。
- Android 实机/OEM、TalkBack 与系统偏好恢复行为：**NOT TESTED**；本次 ADB 无在线设备。
- Skin Studio / Clear：本 Issue 不适用；Clear 源码未修改。

## 提交记录

代码提交 `b17f43c451ba57c3cab59bb7382c984f708bcd48`（`refactor(皮肤分离): 将原生个人资料展示迁入模块`）已推送至 `origin/feat/skin-first-separation`。本 Issue 和路线图/契约/进度更新由单独文档提交记录。
