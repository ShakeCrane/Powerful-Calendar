# PC-ISSUE-SEPARATION-NATIVE-PROFILE-OWNERSHIP

- 日期：2026-10-30
- 状态：**CODE COMPLETE；22 项定向 JVM 与构建/lint PASS；Android 设备验收 NOT TESTED**
- 集成分支：`origin/feat/skin-first-separation`
- 基线：`200051be76a470b085069bd71394d2dc8de02135`
- 实现提交：`2b7049ce6415afe5ae30b496ae8be28a3085cd79`

## 目标与根因

`NativeVisualSkin` 已将 Native 注册成显式 presentation profile，但适配器文件仍放在通用 `skins/` 目录，且声明为父包 `dev.powerfulcalendar.ui.v2`。这样 Native screen 虽已有 `nativeui` package，Native 自己的十个 renderer adapter 仍与其他 skin profile 实现混放。

## 修改

- 将 `NativeVisualSkin.kt` 移到 `app/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeVisualSkin.kt`，声明包改为 `dev.powerfulcalendar.ui.v2.nativeui`，与 Native screen 实现归属一致。
- `skins/ActiveVisualSkin.kt` 仍管理当前 profile 选择，改为显式导入 Native profile；不把 Native renderer 安装逻辑混入 Host route 文件。
- `SkinnableDraftDeck` 显式导入该 Native profile，以保留既有 Native Deck 单观察者特例，避免同一控制器重复挂接监听器。没有复制 Compose screen、Store、生命周期或授权逻辑。
- 十个 renderer ID、注册顺序、fallback 和 `.pcskin` 选择行为不变。Clear 源码/资源/Studio 文件均未修改。

本变更仍处于 `:app` module，不构成编译隔离或安全边界；它仅明确 Native profile 的源码归属。

## 验证

| 检查 | 结果 |
|---|---|
| `PresentationSkinRegistryTest` + `GlassComposeTest` | **22/22 PASS** |
| 主代码与单测编译 | PASS（由定向 JVM 命令执行） |
| Android 测试编译 | PASS |
| `:app:verifySkinSourceBoundary` | PASS |
| `:app:assembleDebug :app:lintDebug` | PASS |
| Android 仪器测试 | **NOT TESTED**：ADB 无在线设备，SDK AVD 列表为空；不能用上一包边界切片的结果替代 |
| 全量 JVM 回归 | 本切片未重跑；不据 22 项定向测试宣称全量通过 |

证据目录：`.local/android-test/reports/separation-native-profile-ownership-20261030/`

- `focused-unit.log` SHA-256：`4D8475A89878C50046FDBCE9A9E157A41E9980948ED5E5FE2AC14E5C5C4D5AE3`
- `TEST-dev.powerfulcalendar.ui.v2.GlassComposeTest.xml` SHA-256：`1B302B4962EDC8665F59B7F941DF40EC92F0E8F15148B662F482E30F2FD5880F`
- `TEST-dev.powerfulcalendar.ui.v2.PresentationSkinRegistryTest.xml` SHA-256：`B77B4E5870AB7B89BA97FC7E4F566A6B77D9EBF396C1A57350BF829E6A881685`
- `build-lint.log` SHA-256：`34F7DADEDF2BCD930207686C00D64D3372FEC67BB0D0E33B8C7A861EA715F9D3`
- Debug APK：67,160,866 bytes；SHA-256 `3A6591B256725C46CA85FF204990CF48948F19CE00FEB9CDF5DD77AA4CABF917`

## 尚未完成

- Native UI 与 Host contract/context 仍在同一个 app module；不存在 Gradle 项目依赖隔离。
- 本切片没有设备级 WebView/Compose instrumentation 或实体设备验收。
- 不代表 SEP-01～SEP-16 的 Native/Skin 功能等价，不代表 Skin Studio 能编辑 Native Kotlin 实现，也不代表 Clear 已恢复。
