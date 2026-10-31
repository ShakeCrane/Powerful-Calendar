# PC-ISSUE-SEPARATION-NATIVE-THEME-DEFAULTS

日期：2026-10-31

状态：**Native 主题默认值归属 CODE COMPLETE；Robolectric、完整离线门禁和 API 35 Native Compose 回归 TESTED**

实现提交：`53ec4759a24c80cec876b655a3c79dd52f74151f` (`refactor(原生皮肤): 将主题基准迁入独立模块`)

## 缺口与修复

原生浅/深色 Material 配色、标题字体规则及时间轴/玻璃色板默认值曾定义在 `app/.../PowerTheme.kt`。即使所有页面 renderer 已移入 `:native-skin`，原生外观基线仍由 Host 源码持有。

新增 `NativeSkinThemeDefaults` 和 `NativeSkinAxisPalette` 到 `:native-skin`。Host 继续负责所选 skin 的合成、系统效果和 Core 色类转换，但基准 scheme、typography、轴色与未绑定色板统一从 Native module 读取。`:app:verifyNativeSkinBoundary` 现检查这些默认值留在 Native module、Host 消费该 API，且不重新声明内联 Native scheme/typography。

Compose 回归逐项锁定原有浅/深色 scheme、轴色、玻璃色、标题字重与字距，验证这次只是归属迁移，没有改变已冻结的 Native 视觉值。

## 验证

环境：Windows；Android SDK `E:\android-sdk`；验证 checkout 固定在实现 SHA。API 35 设备是只读快照 `PowerCal_API35`，ADB serial `emulator-5570`；专项仅渲染 Native Draft Deck，使用 UUID 隔离数据库并在 teardown 删除，不启动 WebView/Clear。

| 检查 | 结果 |
|---|---|
| `GlassComposeTest.nativeSkinOwnsTheUnchangedLightAndDarkThemeBaseline` | **1/1 PASS**；日志 SHA-256 `5660E73FE309E079D9BE0BE6FDE206803DB5D1AF9A29E2E4D3519D1544CA6AF7`；JUnit XML SHA-256 `ADE153D997E493E189001BA1FD47DD197169747CB85E9402A0F6B6B5A5D06066` |
| 完整离线边界/JVM/Debug+AndroidTest APK/Lint 门禁 | **785/785 JVM，125 suites，0 failures/errors/skips；171/171 tasks executed**；lint 0 errors / 51 warnings；两项 skin boundary、两种 APK 编译通过。日志 SHA-256 `C089DE37D109C4995CB7524542111B5CAB7F3F6239C9DC33F69439C6FEA6A9A7` |
| API 35 `DraftDeckThemeSwitchInstrumentedTest` | **1/1 PASS**；Light/Dark 切换后标题、焦点和当前草稿卡保持；XML SHA-256 `99C7C00002399697F830C3BD80631E8DC4B9B25A3639C2AFD40EE99EC9BF3C9D`；日志 SHA-256 `F8747E259FA45BAF45D984688CAE53C8DCF94DF1CBAF20FF0D5634575832D54C` |
| 独立模块产物 | `:native-skin:assembleDebug` 与 `:skin-api:assembleDebug` 均成功；AAR SHA-256 分别为 `415CF4C3D86A1AA44F33C470D8660A2C13D4DBC0ED3A8E91EB976E67EEAFEE61` 和 `51520ED38A0420257AA5EF05153E7416C86A5A72ECF3BC01C44313059DD68140` |

证据目录：`.local/android-test/reports/native-skin-theme-20261031/`，manifest SHA-256 `A804015D632CCF6C14B474D2AB289D6CA91E3DE8DAD01049FDBF3AD96B299CF4`。APK/AAR 位于 `.local/android-test/artifacts/native-skin-theme-53ec475/`；Debug APK SHA-256 `76F2DFA4A125A67C5C26B60FEEC8700EDFF2A03B1B4ED1F759FE03EF17DA07C6`，AndroidTest APK SHA-256 `C8A85DCAA3EA47DBA117AE6E8B4677F87E4A321DBA0A7D1121801CDFD5C0645F`。

## 范围状态

本 Issue 只关闭 Native 默认主题常量的模块归属，不代表所有 Host presentation helper 都已归类或迁移，也不签核 SEP 功能等价、Studio 对受信 Kotlin 的编辑能力、TalkBack 或实体/OEM 行为。Core、Native 页面行为、Clear 源码和 Clear 运行状态均未在本 Issue 修改或验收；Native 回退继续保留。
