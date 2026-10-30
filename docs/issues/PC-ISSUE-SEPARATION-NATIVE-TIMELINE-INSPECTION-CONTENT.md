# PC-ISSUE-SEPARATION-NATIVE-TIMELINE-INSPECTION-CONTENT

**状态：CLOSED — CODE COMPLETE / TESTED**

**日期：2026-10-30**

**代码提交：** `6d8a459d04b4d4c1e6250a9091c98ad8789600ca`

**范围：** 将 Native 时间轴 AI 锁定时的只读事件详情内容迁入 `:native-skin`；Host 保留投影和皮肤感知弹窗外壳。

## 根因与修复

`NativeTimelineScreen` 和 Host 投影已分居 `:native-skin` / `:app`，但 `app/.../nativeui/TimelineScreen.kt` 仍直接创建标题、摘要、备注、只读提示和“完成”按钮。Native timeline 的一块产品展示仍留在 Host 适配器。

复用 Native `NativeEventInspection` renderer 绘制详情内容。Host 只把 Native timeline DTO 映射成 `NativeEventInspectionModel`，并继续将模块输出的标题/正文/按钮组合进 `SkinAlertDialog`，因此现有皮肤感知弹窗外壳不变。renderer 支持调用方传入只读提示和按钮文案：时间轴仍显示“AI 正在处理卡组，暂时只可查看。”和“完成”；其他 Native 查看路径保留原默认文案。事件选择、详情数据、AI 锁定状态和关闭回调未变。未修改 Clear、冻结 Core、默认皮肤选择或 Native 回退。

## 修改文件

- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeEventInspection.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/TimelineScreen.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/NativeEventInspectionComposeTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/NativeTimelineInspectionAdapterComposeTest.kt`
- `docs/UI_SKIN_EXTENSION_CONTRACT_V4.md`
- `docs/SEPARATION_REMOTE_GOVERNANCE_AND_ROADMAP_2026-10-30.md`
- `docs/agent-handoff/PROGRESS.md`
- 本报告

## 测试与构建证据

环境：Windows、仓库 worktree、离线 Gradle 缓存、API 35 Robolectric；Android SDK `E:\android-sdk`。

定向命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest --tests '*NativeEventInspectionComposeTest' --tests '*NativeTimelineInspectionAdapterComposeTest' --offline --rerun-tasks --max-workers=2 --console=plain
```

最终定向结果：**4/4 PASS**：Native event-inspection renderer 3/3，时间轴 Host adapter → Native renderer → skin-aware dialog 1/1；Native/Skin 源码边界通过。最终定向日志 SHA-256：`781640A2BB01B9BDADEFC4E6EA5C389770D16F6CA0D4DBB0DB68CAA17AA0D88F`。

完整门禁命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=2 --console=plain
```

结果：**BUILD SUCCESSFUL，152/152 tasks**；全量 JVM **780/780，123 suites，0 failures/errors/skips**；AndroidTest Kotlin 编译、Debug APK、lint 和两项源码边界检查通过。完整门禁日志 SHA-256：`915E2C6AE3B6F09090C7837FACB195D71842963E0DA04CE478EFEBD92415455C`。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，66,516,562 bytes，SHA-256：`F042C5AA496099E577F8B04FB7C2BADD25FE2ED9E9C17B0FC26D49017E52A487`。

日志位于忽略目录 `.local/android-test/reports/native-timeline-inspection-content-separation-20261030/`。`git diff --check` 通过。

## 剩余验证

- 时间轴锁定事件详情的 Native 内容：**CODE COMPLETE / TESTED**。
- SEP-01 整体仍 **BLOCKED**：WebView 手指拖动到 Host/Camera 写回的 S0-TL-04 真实链路尚无闭环证据；本 Issue 不改变该状态。
- 本机 ADB 无在线设备；Android Emulator、真机/OEM、TalkBack 行为 **NOT TESTED**。AndroidTest 源码已编译，本次未执行仪器测试。
- Clear 已坏且按所有者指示不在本次修改范围内；Clear 源码及资源未修改。

## 提交记录

代码提交 `6d8a459d04b4d4c1e6250a9091c98ad8789600ca`（`refactor(皮肤分离): 迁入时间轴只读查看内容`）已普通推送到 `origin/feat/skin-first-separation`。合同、SEP 状态、进度与 Issue 报告由后续单独文档提交记录。
