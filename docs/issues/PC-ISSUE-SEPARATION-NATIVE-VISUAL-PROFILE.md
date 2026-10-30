# PC-ISSUE-SEPARATION-NATIVE-VISUAL-PROFILE

日期：2026-10-30
状态：**Native profile 注册切片 TESTED；完整单元回归 FAIL；完整 Native/Skin parity 未签核**
分支：`codex/skin-first-mainline`
起始基线：`e559078bcf66fd27865dd36f33d176a5cc69b21b`

## 目标与边界

按 Owner 最新说明，本项只处理 Native 呈现分离；Clear 当前问题保持独立，不在本项修复。目标是让生产 `native` profile 对十个既有 Compose surface 有显式、可检查的注册入口，并继续复用现有 Native 实现和 Host 生命周期。

本项没有修改 Core、PowerStore、提醒、AI 授权、Clear canonical source 或 `.pcskin` 资源；没有切换默认皮肤或删除 Native 回退。也没有把 Skin Studio 扩展为可编辑受信 Kotlin 源码。

## 根因

`ActiveVisualSkin.PROFILE` 虽然设为 `native`，原实现只调用 `PresentationSkinRegistry.reset()`。各路由依赖 renderer 为 `null` 隐式走 `context.Native()`，所以 Native 不是一份显式 source-compiled profile 注册表，也没有十个 surface 的具名 ID 可供验证。

Draft Deck 还具有不同的生命周期约束：通用自定义 Renderer wrapper 自己订阅 `DeckController`，Native dialog 也订阅同一个单 listener controller。若 Native adapter 经过通用 wrapper，会多建立一层观察者。因此 Native Deck 必须保持既有直接 Native 路由。

## 修改

- 新增 `app/src/main/java/dev/powerfulcalendar/ui/v2/skins/NativeVisualSkin.kt`，注册 `native/timeline`、`native/month`、`native/memo`、`native/ai`、`native/settings`、`native/navigation`、`native/deck`、`native/overlay`、`native/picker`、`native/chrome` 十个 adapter。每个 adapter 只委托对应 context 的 `Native()`。
- `ActiveVisualSkin.install()` 现在显式安装 Native profile；错误 profile 值也安全回到该 Native profile。`PresentationSkinRegistry.reset()` 仍可清空 renderer 并恢复一方直接 fallback。
- `DraftDeckDialog` 对已注册的 Native Deck renderer 保持直达 `NativeDraftDeckDialog`，不进入会增加 controller observer 的 skin wrapper 流程。
- `PresentationSkinRegistryTest` 验证十个 ID 唯一、注册/重置状态，以及编译式 profile 不改变 `.pcskin` active selection。`GlassComposeTest` 和 API 35 instrumentation 覆盖 Native profile 下 Deck 导航。
- 更新 `docs/UI_SKIN_EXTENSION_CONTRACT_V4.md` 和本报告/进度日志，说明 Native profile 与 `.pcskin`、Studio artifact 的边界。

## 验收证据

环境：Windows / Android SDK `E:\android-sdk`；Android 15、API 35 AVD，ADB serial `emulator-5554`（Gradle 显示 `PowerCal_Sep01_API35(AVD)`）。

| 检查 | 结果 | 证据 |
|---|---|---|
| `:app:verifySkinSourceBoundary` | PASS | `verifySkinSourceBoundary` task 成功 |
| 定向 Robolectric/Compose：`PresentationSkinRegistryTest` + `GlassComposeTest` | **22/22 PASS** | `app/build/test-results/testDebugUnitTest/`；新增 Deck 导航用例通过 |
| Android instrumentation：`DraftDeckTouchPriorityInstrumentedTest` | **2/2 PASS** | API 35 AVD；日志 `.local/android-test/reports/separation-native-profile-20261030/api35-instrumented.log`，SHA-256 `FC97030E65CF4200EEFC08B5C15177E28470040A6357EDDD53CDD0E319D6CFCD` |
| 完整 `:app:testDebugUnitTest` | **FAIL：728 项，727 PASS / 1 FAIL** | `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` 导航到 Memo 后 Host state 显示 `memoCount=0`，等待 60 秒超时。日志 `.local/android-test/reports/separation-native-profile-20261030/full-gradle.log`，SHA-256 `EF60F6A9BB043D19BA7AA27B1507D717A1B39590E6ED7FE3A7F9CCDAEC0DF07D`。本项未改 Clear；全量回归明确保留 FAIL。 |
| `:app:assembleDebug` | PASS | Debug APK 67,553,486 bytes，SHA-256 `389E3DEAA6A64E94EBE233734AE12EECAC49FF847AA653FD949AEE99F257D257`，位于 `app/build/outputs/apk/debug/app-debug.apk` |
| `:app:lintDebug` | PASS | 0 errors / 53 warnings；日志 `.local/android-test/reports/separation-native-profile-20261030/build-lint.log`，SHA-256 `25B4FEBAAD5E4BC6FEAFD45AE6BFECFEA20EC7E4E5E91706388906B9F316EA09` |
| Clear 源码和包资源是否变化 | 未变化 | `git diff --exit-code -- tools/skin-studio/default-clear/source app/src/main/assets/powercal-clear-default.pcskin` 成功 |

JUnit XML SHA-256：`GlassComposeTest` `C7C6B0674F1304B40372EF2A59CE817DA1D5D92EEE009F0BE8B8182DDD974E97`；`PresentationSkinRegistryTest` `668432AF6939EF318342F6FDB33BFAD4C4C5C6D5E03173AD2787C67834D4DAEF`；包含失败案例的 `WebAiProposalReviewIntegrationTest` `F689CCE0F3443A01D4751FEDA2BAA5142D699DC3CA94E7AD619D13CF0E9EF770`；API 35 instrumentation XML `2B7B4E7E59B6BAF0A85AE4655AE1258CD3C4E06D8538CC19F63748D3B2C56FFC`。副本保存在 `.local/android-test/reports/separation-native-profile-20261030/`。

## 结果与未决风险

**CODE COMPLETE：** Native profile 已通过显式 registry 关联全部十个类型化 surface，Native 回退和 `.pcskin` 选择互相独立。**TESTED：** registry、Compose Deck 导航及 API 35 Deck 手势路径通过。**INCOMPLETE：** Native 各页面并未全部迁出当前 app UI source module；本项建立的是 presentation profile adapter 边界，不声称每个 screen 的完整重构或 Native/Skin 等价。

Studio 不编辑受信 Kotlin 源码；本项未增加新的 Studio runtime capability、包 schema 或可定制字段，因此没有 Studio 代码变更。若后续目标要求 Studio 可编辑 Native 的可定制视觉部分，应单独定义其安全、无损的资源/主题接口。

完整单元回归中 Clear Memo 分页失败需要单独处理；遵照本项范围，没有在这里更改 Clear 或把失败判成环境通过。完整 SEP 状态、Skin Studio 等价、其他页面的 UI/无障碍及实体 OEM 验收仍未由本 Issue 签核。

实现提交：`439eee336903f1b16606b406c5e74734f1f05c0a`（`feat(皮肤分离): 显式注册原生呈现皮肤`），已推送到 `origin/feat/skin-first-separation`。该提交对应 APK 与所有测试日志哈希如上。

## 后续源码归属（2026-10-30）

Native screen 命名空间及 profile adapter 的源码归属后续分别由 [Native package boundary Issue](PC-ISSUE-SEPARATION-NATIVE-PACKAGE-BOUNDARY.md) 和 [Native profile ownership Issue](PC-ISSUE-SEPARATION-NATIVE-PROFILE-OWNERSHIP.md) 更新。十个 renderer ID、`ActiveVisualSkin` 选择规则及 profile 行为保持本 Issue 的定义。
