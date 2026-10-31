# PC-ISSUE-SEPARATION-CLEAR-ANDROID-BACK-NAVIGATION

**状态：** 已修复 Android Back 路由切片；SEP-12 整体仍为 `PARTIAL / HOLD`
**日期：** 2026-10-31
**实现提交：** `2e69adf2486f73d99fb8bc1d286c65c24970b641`
**范围：** Clear 全屏 WebView 的系统返回键、皮肤临时对话框、Settings 返回和 Native 安全回退。

## 复现与根因

在 API 35 Android 15 仿真器上使用真实 Android `KEYCODE_BACK` 输入。修改前，Settings 中按 Back 会把 Host 页面从 `SETTINGS` 直接路由到 Native `TOOLS`，而预期应返回 Clear 时间轴。`baseline-runner.txt` 还记录了从 Clear 新建选择框按 Back 后 WebView 查询回调超时；该记录本身未能单独确定页面状态。两个诊断日志均保存在下方证据目录。

根因是 `PowerCalApp` 为“非时间轴页面回时间轴”“时间轴进入 Native 回退”分别注册启停不同的 Compose `BackHandler`。Clear 切换内部页面时，启停优先级可能短暂沿用旧处理器，Host 抢先执行 Native 回退。

## 修复

- Host 使用一个稳定的页面级 BackHandler：先询问当前 v3 皮肤是否消费 Back；未消费时，非时间轴页面返回时间轴，时间轴则进入保留的 Native `TOOLS` 回退页。
- Bridge 增加无写入能力的可选 `handleHostBack()` UI 回调。Host 校验当前 WebView、皮肤包及 Bridge owner 后才调用，返回值只决定 UI 路由，不扩大皮肤能力或授权业务写入。
- Clear 关闭最上层已打开的 HTML `<dialog>` 后返回 `true`；没有皮肤临时 UI 时返回 `false`。
- 同步更新皮肤契约、AI/Studio 操作规范、可编辑 Clear 源码及其打包资源。编辑仍通过现有 v3 Studio 项目/源码模型完成。

## 验收证据

**Android WebView：PASS，1/1。** 测试 `ClearNavigationBackInstrumentedTest#androidBackDismissesSkinDialogReturnsFromSettingsAndRetainsNativeRecovery` 在 `PowerCal_API35` 的 `emulator-5570`（Android 15 / API 35 / Android System WebView 124.0.6367.219）通过真实 Android Back 输入，依次验证：

1. Clear 新建选择框关闭，Host 页面仍为 `TIMELINE`；
2. Clear Settings 按 Back 返回 Clear 时间轴；
3. 无皮肤弹层时按 Back 进入 Native `TOOLS` 回退页；
4. 整个无修改路径的草稿数和身份数仍为 0，下一事件序号仍为 1。

最终 runner 日志：`.local/android-test/reports/sep12-clear-back-navigation-20261031/unified-handler-api35-runner.txt`，SHA-256 `E59AE14BA50C99F932FA7AA7D5E579C522AFFA16EEDAC479D99CAD1B0177CB92`。修改前日志：同目录 `baseline-runner.txt` 与 `diagnostic-api35-runner.txt`；后者明确记录 `expected TIMELINE, actual TOOLS`。

**Studio：局部回归 PASS。** `python -m unittest discover -s tools/skin-studio/tests -v` 为 **63/63**；新增 `e2e_v3_system_back_hook.cjs` 在 Studio Preview 中验证 Clear 弹层消费 Back、无弹层时交回 Host，结果 PASS。日志分别为 `studio-python-tests.txt`（SHA-256 `26C7783CA5AA408D3E65E3081C7A0E910CDA970B39337313C158A8EA413E4A98`）与 `studio-back-hook-e2e.txt`（SHA-256 `5C1F5BF26845388805A9BC2F6FEC5FFB33F2D9219A265DD8B6224755B68BF6D5`）。另有通用旧版浏览器 E2E 的既有选择器/脱离节点问题；本 Issue 不把它计作通过，也不据此签核完整 Studio 浏览器套件。

**集成门禁：PASS。** 命令：

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --max-workers=1 --console=plain
```

结果为 **784/784 JVM、125 suites、0 failures/errors/skips，171/171 Gradle tasks 成功**；Lint **0 errors / 55 warnings**。完整日志 `.local/android-test/reports/sep12-clear-back-navigation-20261031/full-gradle-gate.txt`，SHA-256 `FD1D10C04CB7829B0882DD3FEBD92376D721558D9AF690A94383CF863AF1065C`。

Clear 皮肤包与该 APK 中的资源逐字节一致：9 个文件、395,556 字节，SHA-256 `7c2cbbea0774df21bb8a23fba622663a886367583d6da13e5fa12ef0f297b37`。Debug APK SHA-256 `D650686340B54FE683A7262BDF4DBEFDAC0DC0BE3CCD320AAB46D700D5A5AB26`。证据目录：`.local/android-test/reports/sep12-clear-back-navigation-20261031/`。

## 状态边界

本 Issue 只证明上述 API 35 Android Back 路由和 Studio 对应源代码钩子。SEP-12 的系统 Picker、TalkBack/无障碍行为、多窗口/IME、通知与完整 Native/Skin 页面等价以及实体/OEM 行为仍未签核；通用浏览器 E2E 的完整套件也未通过。本切片不关闭 SEP-12，不代表完整 Skin-First 或 cutover GO。

## SEP-12 follow-up：Clear Host AI Settings 返回皮肤时间轴（2026-10-31）

- API 35 Android 仿真器测试 `ClearSettingsOptionsInstrumentedTest#clearHostAiSettingsBackRestoresSelectedSkinTimeline` 通过可信 Android 触控打开 Clear Settings 与 `#openAiSettings` Host 路由，再通过可访问的“返回时间轴”Host 控件返回。恢复后的 WebView 已附着且 Clear 时间轴可见。
- 选中皮肤仍为 `powercal-clear-default`；隔离 Store 的正式事件、草稿、pending、commit、identity 与下一序号快照完全不变。Android instrumentation **1/1 PASS**。XML SHA-256 `82650553651BCE7155EA63FCB00173826F7FA629BBFCA7480FBB12FEBF76442A`；设备结果 `789F1B8050ED976CD680E51D326D0D0880B45FA6CC0C8EEA418ED455B5142FA6`；UTP `DAA50AAF3D8386C563BF031D8E0DC36FF22CD192A2CE0EDEA2DDA535A81033EE`；logcat `9F488E7E5A5D3763A1063E71A361E7FE78D8B6062EB70F9E65CED42872E3AB85`；focused Gradle log `64EB2C2FBA714B5E92E50B9343A28BFCF1AE09E55B9AA123772A565339C91CC4`。证据目录：`.local/android-test/reports/sep12-clear-host-ai-settings-back-20261031/`。
- 随后在同一集成工作树强制运行离线全门禁：两个 boundary、JVM 单元测试、Debug/AndroidTest APK 与 lint，**785/785 JVM、125 suites、0 failures/errors/skips，171/171 tasks PASS**；Lint 0 errors / 55 warnings。完整日志 SHA-256 `192E3DC4E32F02FA08E42799D5650057C2DC5012CE9C35247E240C7898E09EB4`；Debug APK SHA-256 `6C30F06090D2E2EB13DCC310EF2807C0D622982BA57D38AEB9F44BA9469DA3CF`。
- 本次没有生产代码、Core 或 Bridge 变更。AndroidTest APK SHA-256 `2B26B0EA32EFDA630C7B8B966BB4EE429094E3BDAFAFC2E34E66D36E17859CF8`；该 APK 编译时包含工作区里两份其他 Agent 尚未提交的 FormalDelete/Recurrence instrumentation 源文件，它们未由本切片暂存或提交。SEP-12 仍为 **PARTIAL / HOLD**；Picker、TalkBack、IME/多窗口、完整 Native/Skin 导航配对及实体/OEM 仍未签核。
