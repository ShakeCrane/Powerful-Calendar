# PC-ISSUE-SEPARATION-NATIVE-SOURCE-ISOLATION

日期：2026-10-30
状态：**Native source grouping CODE COMPLETE；定向 Compose/Android 验证 PASS；全量套件本次未重跑**
分支：`codex/skin-first-mainline`
基线：`51190871daf4d366fbe3d13d0cdfc3c241119e46`

## 目标

把原生页面实现从通用 route/Host 编排文件旁边归组到单独 Native UI source 目录，同时保留当前调用 API 与运行行为。依照 Owner 最新说明，本项只处理 Native；Clear 的问题保持原状。

## 修改

- 将 `TimelineScreen.kt`、`MonthScreen.kt`、`MemoCenterScreen.kt`、`AiChatScreen.kt`、`DraftDeck.kt` 和 `NativePowerNavigation.kt` 移入 `app/src/main/java/dev/powerfulcalendar/ui/v2/native/`。
- 把 `NativeToolsPage` 及只供它使用的 `SectionCard`、`ToolRow`、`StatusLine` 从 `PowerCalApp.kt` 移入 `native/NativeToolsPage.kt`。抽出函数体与起始基线源文件的尾部内容在换行归一后逐字一致；只增加文件头 KDoc 和 imports。
- 保留 Kotlin 声明包 `dev.powerfulcalendar.ui.v2`、函数签名、路由、Host 回调、Store/controller 与系统权限调用；不改变持久化或 UI 行为。`PowerCalApp.kt` 继续负责宿主状态与路由编排。
- 更新皮肤契约的源码地图、进度记录和本 Issue。

这是源目录分组，**不是新的 Gradle module、进程、APK 或安全沙箱**。Native 页面仍是可信 app source；上一 Issue 的 `NativeVisualSkin` typed adapters 仍是 profile 边界。本项不表示 Skin Studio 已能编辑受信 Kotlin 页面源码。

## 验证

环境：Windows，Android SDK `E:\android-sdk`；Android 15 / API 35 AVD `emulator-5554`。

| 检查 | 结果 | 证据 |
|---|---|---|
| `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin` | PASS | 三类 source 全部编译成功 |
| 定向 `PresentationSkinRegistryTest` + `GlassComposeTest` | **22/22 PASS** | registry profile 与 Native Deck UI 回归 |
| API 35 `DraftDeckTouchPriorityInstrumentedTest` | **2/2 PASS** | Native profile 下跨卡导航与真实触摸优先级 |
| `:app:verifySkinSourceBoundary :app:assembleDebug :app:lintDebug` | PASS | lint 0 errors / 53 warnings |
| NativeToolsPage 源体对比 | PASS | 与基线 `PowerCalApp.kt` 抽取边界后的正文逐字一致（忽略 CRLF/LF 差异） |
| 完整 `:app:testDebugUnitTest` | **本项未重跑** | 最近完整回归记录仍为 728 项中 727 PASS、Clear Memo 分页 1 FAIL；详细证据见 [上一 Native profile Issue](PC-ISSUE-SEPARATION-NATIVE-VISUAL-PROFILE.md)，不据 focused tests 宣称全量 PASS |
| Clear 资源/源码 | 未变化 | 本 Issue 没有 Clear 路径改动 |

源码/编译定向 Gradle 日志：`.local/android-test/reports/separation-native-source-layout-20261030/targeted-unit.log`，SHA-256 `72AD6AD7729984C4CDA98A94E84DAF49C1E5E0111E6266050A7B00C55227A1A2`。

API 35 instrumentation 日志：`.local/android-test/reports/separation-native-source-layout-20261030/api35-instrumented.log`，SHA-256 `39BE60075D0ADCD84FD6B9F344F971318049FCB09A09D5A3A879FF185760A96B`。

Build/lint 日志：`.local/android-test/reports/separation-native-source-layout-20261030/build-lint.log`，SHA-256 `061B8962A5E6FAE999C976EFB3FD11A75DC543C745892BE9140845C5BADBAE22`；同一命令成功执行 `assembleDebug` 和 `lintDebug`。

JUnit XML SHA-256：`GlassComposeTest` `3DD7EE7C3731598D451D7073D67F229265D907DAB8785EF642C0D1422FCC86A8`；`PresentationSkinRegistryTest` `3BFDC7EA7E839FFFB3DB549DB9E418BBD9341047C922AB5FE206E2B0EAB3E8BA`；API 35 instrumentation `AB43D2120407D52D686B8B378F3E62D6A1246399AA40E89663F1397E05DF1A1A`。XML 副本位于同一 `.local/android-test/reports/separation-native-source-layout-20261030/`。

当前 Debug APK：67,555,343 bytes，SHA-256 `F90AE2032704DEA89A26A1F6D08C2DF2F425D22E5025F3B53C09245E6EFF2B23`，位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 尚未签核

当前 Native 页面与适配器仍在同一 Android app module 和 Kotlin package。文件目录隔离不构成编译期依赖隔离；是否进一步拆 Gradle module 需基于现有 source boundary、循环引用与受信 Settings 能力评估。本 Issue 没有拆 module，以免改变业务依赖或生命周期。

没有完整重跑 728 项测试，最近全量回归的 Clear Memo 失败仍开放；Native Settings/OEM 权限页、TalkBack、不同字号以及全部 SEP Native/Skin 双版本回放没有由本 Issue 签核。Clear 不在当前实现范围。

实现提交：`7a1d0bd94cff07d3baba6fd3d7439c01f09befe5`（`refactor(皮肤分离): 归组原生界面源码`），已推送至 `origin/feat/skin-first-separation`。

## 后续状态（2026-10-30）

目录归组后仍留在公共 Kotlin package 的缺口，已由 [Native package boundary Issue](PC-ISSUE-SEPARATION-NATIVE-PACKAGE-BOUNDARY.md) 的实现提交 `9dcdf70828a65e5e0ccb996ac64251426d13ccb6` 处理：Native screen 文件现在声明 `dev.powerfulcalendar.ui.v2.nativeui`。它们仍处于原 `:app` Gradle module；本后续提交没有声称建立 module 隔离或安全沙箱。
