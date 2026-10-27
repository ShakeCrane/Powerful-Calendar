# PC-ISSUE-SEPARATION-WEBVIEW-SESSION-ORIGIN

- **状态：** 本次 Host 修复与 JVM/构建门禁 `PASS`；SEP-17 Android instrumentation 与设备行为 `NOT TESTED`，整体设备安全验收不签核。
- **日期：** 2026-10-28
- **分支：** `codex/separation-skin-settings`
- **修复基线：** `6a13c828453bc538e313e63ee41ff15fb5553325`
- **实现提交：** 见本报告提交后的 Git 记录；提交完成后回填。

## 问题与影响

所有 v3 skin WebView 过去共用固定来源 `https://skin.powercal.local/`。WebView 每次重建虽然销毁旧 View 与消息通道，但固定来源没有区分新旧 skin 会话；若一个来源上的 Service Worker 能注册并持久化，它就可能跨 WebView 会话继续关联同一来源。原配置限制了普通 WebView 的网络、文件和 content 访问，却没有安装 Android 的进程级 Service Worker 拦截策略，也没有明确关闭 WebView cookie。

这是根据 Android WebView 的共享 Service Worker 控制器与来源隔离模型识别出的持久状态边界风险。本次没有在真实 Android 环境中复现恶意 skin 接管，也不把风险推断写成已观察到的攻击。Android 官方接口说明： [ServiceWorkerController](https://developer.android.com/reference/android/webkit/ServiceWorkerController)、[ServiceWorkerWebSettings](https://developer.android.com/reference/android/webkit/ServiceWorkerWebSettings)、[ServiceWorkerClient](https://developer.android.com/reference/android/webkit/ServiceWorkerClient) 与 [CookieManager](https://developer.android.com/reference/android/webkit/CookieManager)。

## 修复内容

1. **每个 WebView 会话使用新的合成 HTTPS 来源。** `WebSkinOrigin` 以 UUID 生成 `<32位随机值>.skin.powercal.local`。资源响应、顶层跳转、加载完成校验及 WebMessage 目标来源均绑定当前会话的精确来源和 entrypoint；拒绝其他 scheme/host、显式端口、userinfo、query 与非当前 entrypoint。
2. **在创建 WebView 前安装全局 Service Worker 与 cookie 限制。** `WebSkinServiceWorkerPolicy` 将 Service Worker 的 file/content/network 访问关闭、缓存设为 `LOAD_NO_CACHE`，并为所有 Service Worker 请求返回 403。调用 `CookieManager.setAcceptCookie(false)` 禁用此进程 WebView cookie 接受能力；不会清除设备现有 cookie 数据。
3. **初始化失败回退。** 如果策略安装或 WebView 初始化抛错，Compose 不继续创建皮肤 WebView，而是调用现有失败回退路径回到 Native UI。
4. **保留现有信任边界。** v3 包仍离线运行，Host Bridge、CSP、包文件路径校验和原生恢复入口继续有效；没有改变 skin capability 或 Core 数据层。

## 修改文件

- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinHost.kt`：会话来源、全局安全策略安装、失败回退。
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinOrigin.kt`：新增纯 JVM 来源生成与精确校验。
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinServiceWorkerPolicy.kt`：新增进程级 Service Worker/cookie 策略和可测配置端口。
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinOriginTest.kt`：新增 3 个来源与拒绝路径测试。
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinServiceWorkerPolicyTest.kt`：新增策略配置单元测试。
- `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinServiceWorkerPolicyInstrumentedTest.kt`：新增真实 Android API 状态检查；本轮只编译，尚未在设备上执行。
- `docs/SKIN_PACK_V3.md`、`docs/V3_HOST_BRIDGE_EXTENSION.md`、`docs/SKIN_RUNTIME_PARITY_V1.md`：更新当前安全边界说明。
- `docs/SKIN_EDIT_BRIDGE_S1_S2_2026-10-22.md`：添加带日期的安全修订，保留历史示例并说明其已过时。
- `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`：新增 SEP-17，状态保持 `NOT TESTED`。

## 测试与验收结果

| 检查 | 结果 | 证据/限制 |
| --- | --- | --- |
| `WebSkinOriginTest` | PASS，3/3 | 独立来源、精确来源限制、entrypoint 精确匹配 |
| `WebSkinServiceWorkerPolicyTest` | PASS，1/1 | 验证 file/content/network 关闭及无缓存策略 |
| `WebSkinHostProjectionTest` | PASS，14/14 | 原有 Host 投影回归 |
| `:app:verifySkinSourceBoundary` | PASS | 最终完整门禁日志 |
| `:app:testDebugUnitTest` | PASS，615/615；87 个 XML 测试套件，0 failure/error/skip | 最终完整离线门禁 |
| `:app:assembleDebug` | PASS | APK SHA-256：`6FAE9759D0D6D5E7F921F15A45CE875BD6D20A21D6D131E3B068D031F293BF4A` |
| `:app:assembleDebugAndroidTest` | PASS，instrumentation APK 编译 | APK SHA-256：`6FBA400C709EDBE947F89626FBC50AB1F3550381D9FC18297C20661760F1A8A4`；不代表测试在设备运行通过 |
| `:app:lintDebug` | PASS，0 errors / 51 warnings | 有告警但 lint 任务成功；包含 Android deprecated API 与项目既有 Kotlin 告警 |
| Skin Studio Python 回归 | PASS，53/53 | 通过允许 loopback/临时原子写入的执行环境重跑 |
| Clear 包校验 | PASS，8 files / 379252 bytes | SHA-256：`7b3fbb7c402e5c776894497d96ba1ceee7189e8acb48eee70ece2e9c19ccf81a` |
| `node --check tools/skin-studio/default-clear/source/web/app.js` | PASS | 退出码 0 |
| `git diff --check` | PASS | 无空白错误 |
| Android Service Worker instrumentation | NOT TESTED | `adb devices -l` 在提升权限的只读检查中只返回设备列表标题；没有连接的模拟器/设备 |
| 实体/OEM 设备验证 | NOT TESTED | 本轮没有真实设备可用；不能据构建结果推断设备通过 |

完整离线 Gradle 命令：

```powershell
gradle :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
```

执行结果：`BUILD SUCCESSFUL in 1m 54s`，`88 actionable tasks: 18 executed, 70 up-to-date`。最终日志位于忽略目录 `.local/android-test/reports/separation-20261028/webview-session-origin/final-full-gate.log`，SHA-256：`879A4B38C42861A7EECA91FD2877B126ECD51DE20AA6765568CEF53204FC790D`。定向测试与 AndroidTest 编译日志 `focused-and-androidtest-build.log` SHA-256：`76084C40AD298A2D02FA2D90C869045F66EF07B7D5B02C096901354DA0D0268E`。Studio 回归日志 `studio-python-elevated.log` SHA-256：`AEDDA52FFBCEB713205B905A43D62CD1B1810F57224BAEA1D7AB578F7762C8CE`。

## 试验中发现并修正的事项

- 第一版来源测试使用 Android `Uri`，在本地 JVM 测试环境因 Android API 未 mock 而失败；改成纯 JVM `java.net.URI` 校验后，来源边界可稳定单测。
- Service Worker 控制器是 Android 平台 API，Robolectric 环境不能作为其平台行为证据；将策略赋值抽象到 `SettingsPort` 做 JVM 单测，并单独保留 Android instrumentation 检查。
- instrumentation 首次编译时把 `CookieManager.acceptCookie()` 写成属性访问；按 Java API 方法调用修正后 AndroidTest APK 编译通过。
- Studio Python 测试在普通沙箱执行时因 loopback socket 与临时原子替换权限限制报错；在允许这些本地操作的受限提升执行中 53/53 通过。未修改 Studio 生产代码来绕过测试环境限制。

## 剩余风险与后续

- SEP-17 保持 `NOT TESTED`。需在可用 AVD 上运行 `WebSkinServiceWorkerPolicyInstrumentedTest`，再补充 WebView 实际加载/失败回退回归；物理/OEM 行为仍由后续设备 QA 签核。
- `CookieManager.setAcceptCookie(false)` 是 Android 进程 WebView 级设置。当前源码仅发现此处创建生产 WebView；未来如增加需要 cookie 的 WebView，必须重新审查统一策略，不能静默恢复 cookie。
- 随机来源和拦截策略降低跨会话 Service Worker 复用与网络访问风险，但没有声称完成完整恶意 skin 渗透测试、WebView 生命周期/进程死亡恢复测试或 Release QA。
- 当前分离 Goal 及总体验收矩阵仍未完成；本 Issue 的 JVM/构建 PASS 不构成整体冻结或 cutover GO。
- `docs/agent-handoff/PROGRESS.md` 在并行 `feat/skin-first-separation` 工作树中有未提交修改，本次未触碰该文件以避免覆盖并行 Agent 的所有权；本 Issue 与 SEP-17 矩阵行记录本次状态。

## Git 交付

- 代码、测试、本报告及关联契约文档将作为本 Issue 的受控提交推送到 `origin/codex/separation-skin-settings`。
- 提交 SHA 在提交后写回此处；未推送前不宣称远端交付完成。
