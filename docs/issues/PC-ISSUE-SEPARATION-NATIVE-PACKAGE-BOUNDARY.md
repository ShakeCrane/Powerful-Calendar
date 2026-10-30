# PC-ISSUE-SEPARATION-NATIVE-PACKAGE-BOUNDARY

- 日期：2026-10-30
- 状态：**CODE COMPLETE；44 项定向 JVM 与编译/构建 PASS；Android 设备验收 NOT TESTED**
- 工作分支：`codex/skin-first-mainline`
- 集成分支：`origin/feat/skin-first-separation`
- 基线：`bd20ed1cb235bb3971a54bf2ee38cd2419bad2b7`
实现提交：`9dcdf70828a65e5e0ccb996ac64251426d13ccb6`

## 目标与根因

此前 Native 屏幕虽然已放在 `ui/v2/native/` 目录，Kotlin 声明仍全部属于 `dev.powerfulcalendar.ui.v2`。目录归组便于查找，但并未建立实际的源码命名空间边界。此项把原生屏幕移入独立 `nativeui` 包，并显式适配所有仓库内调用点。

## 修改

- 将时间轴、月历、Memo、AI、Draft Deck、原生导航和 Settings 页面七个文件移到 `app/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/`，声明包改为 `dev.powerfulcalendar.ui.v2.nativeui`。
- 更新 `PresentationSkinRuntime`、`CompiledSkinTimeline`、`DirectEventEditor`、`PowerCalApp`、`SkinnableDraftDeck` 的调用导入，以及直接调用这些 Native 声明的单元测试和 Android 测试导入。
- 把只读 `TodoEntry`/`TodoProjection` 从 Native Memo screen 文件中抽到父包 `MemoProjection.kt`，使 Host、Web projection 和同包测试继续使用同一投影。提取正文与基线 `MemoCenterScreen.kt` 中的定义逐字一致。
- 未改变 Native screen 函数体（除迁移所需 imports 和包声明）、Native renderer ID、路由、业务或持久化行为。`Clear` 源码、资源、Skin Studio 文件均未修改。

此边界属于 Kotlin package 组织；Native UI 与 Host 仍在同一个 `:app` Gradle module 中。它不是进程隔离、安全沙箱、独立 APK，也不表示 Skin Studio 可以编辑受信 Kotlin 页面。

## 验证

| 检查 | 结果 |
|---|---|
| `:app:verifySkinSourceBoundary :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --console=plain` | **PASS**，三个 Kotlin source set 均重新编译 |
| 定向 JVM | **44/44 PASS**，10 个 suite：GlassCompose、GlassRender、NewEventEditorCompose、NowBadgePlacement、TimeAxisAccessibility、TimelineFontScale、TimelineForcedLandscape、TimelineTitle、TimelineWingScroll、PresentationSkinRegistry |
| `:app:verifySkinSourceBoundary :app:assembleDebug :app:lintDebug --offline --console=plain` | **PASS**，Debug APK 与 lint 成功 |
| Native Memo 投影提取对比 | **PASS**，与基线定义正文逐字一致 |
| 活动源码旧包引用扫描 | **PASS**，未发现仓库内旧 FQN 调用；仅历史报告保留旧目录文本 |
| Android 仪器测试 | **NOT TESTED**：`adb devices -l` 没有在线设备，`emulator -list-avds` 没有列出 AVD。前一 Issue 的 API 35 证据仅标 `PREVIOUS PASS` |
| 全量 `:app:testDebugUnitTest` | **本切片未运行**。最近历史全量结果为 728 项中 727 PASS、1 项 Clear Memo 分页失败；见 [Native visual profile Issue](PC-ISSUE-SEPARATION-NATIVE-VISUAL-PROFILE.md)。不将此历史结果写成本 SHA PASS |

### 可复核证据

证据目录：`.local/android-test/reports/separation-native-package-boundary-20261030/`

- `compile-source-sets.log` SHA-256：`7D7A7784B6582A930E222C67003520654741AE55ED5D80BBCBE569AAD8340ADE`
- `focused-unit.log` SHA-256：`6DEA092F886642CA14FBEA578F761B5982050D8E0BEFA4FC0C8CAE4895327A45`
- `focused-unit-summary.txt` SHA-256：`1F6729B54B865FBD1D559B815EF0AF320FA07AFBECF43166744B715CFAF62074`；目录内包含 10 份所选 suite 的 JUnit XML 副本，合计 44 tests、0 failures、0 errors、0 skipped
- `build-lint.log` SHA-256：`244D031B84F32665600E51A5C34647C62528FC4A067534D9356C7D9CBAEE8155`
- Debug APK `app/build/outputs/apk/debug/app-debug.apk`：67,166,518 bytes；SHA-256 `7B9DD354933829872F4E3F4D49BD8D5AC5CA3F42766A5B275F8918F84ED886C8`

## 剩余边界

- Native 与 Host 仍同处 `:app`，没有独立 Gradle 编译依赖边界。若继续拆 module，须单独梳理共享 Compose 类型、受信 Settings slots 和依赖环，再做最小迁移。
- 本次变更后未能运行 Android WebView / Compose instrumentation；待有可用模拟器时需在当前提交上复测 Native Timeline、Draft Deck 和 Settings。
- 本 Issue 不签核 Native/Skin 功能等价、Skin Studio 能力、Clear 行为或实体/OEM 表现。Clear 仍按现状保留。
