# PC-ISSUE-SEPARATION-WEBVIEW-RENDERER-RECOVERY

- **日期：** 2026-10-31
- **状态：** 此故障场景 `PASS`；父项 SEP-14 仍 `INCOMPLETE / HOLD`
- **验收来源：** 重建矩阵 SEP-14（非历史 209 项 ID）
- **验证提交：** `d46e341673da6eb24d6b6fb6344d43de55de8481`，已推送到 `origin/feat/skin-first-separation`

## 结果

已在 API 35 AVD 的 PowerCal Runtime 中加载实际打包的 Clear，等待 WebView Host Bridge 握手完成，然后终止其真实 renderer 进程。Clear 发生 renderer crash 后，PowerCal 自动持久切回 Native，显示“皮肤加载失败，编辑已存档并恢复安全界面。”提示，并进入 Native“工具与设置”恢复页。隔离数据库中既有正式记录及 Store 计数均不变。

测试没有复现 renderer 故障处理缺陷；修复工作是将该既有恢复链路纳入可重复的设备级故障注入回归。本次没有修改 Runtime、Bridge、冻结业务源码、Clear 包或默认皮肤。

## 调用链与验证边界

1. `WebSkinHost.kt` 收到 `WebViewClient.onRenderProcessGone` 后触发 `WebSkinRoot` 的失败状态，移除损坏的 WebView 并展示原生内容。
2. `PowerCalApp.kt` 的失败处理尝试 `webEditing.prepareSkinChange()`，通过 `SkinPackageManager.selectNativeForEmergencyRecovery()` 同步保存 Native 选择，切换到 `Page.TOOLS` 并显示安全提示。
3. 新增的 `WebSkinRendererRecoveryInstrumentedTest.kt` 使用唯一临时数据库写入一条 Memo 基线事件，加载 APK 内的 Clear 包并确认 `window.PowerCalSkin.receive` 已就绪；随后调用 `WebViewRenderProcess.terminate()`。
4. 测试检查 Native 皮肤偏好已持久化、Native Tools 页及恢复提示可见，并比较崩溃前后的正式记录、待处理草稿数、提交数、身份数和下一个事件序号。

## 验收证据

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| API 35 WebView renderer 故障注入 | `PASS` | `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.powerfulcalendar.ui.v2.WebSkinRendererRecoveryInstrumentedTest`；`codex-clear-api35`，Android 15 / API 35，1/1。测试 XML：`app/build/outputs/androidTest-results/connected/debug/TEST-codex-clear-api35(AVD) - 15-_app-.xml`，SHA-256 `409D51CFCACBF7E1D385115AE7794B3874FD8B07A725AD35641825428216AF72`。 |
| 真实 renderer crash 记录 | `PASS` | instrumentation Logcat `app/build/outputs/androidTest-results/connected/debug/codex-clear-api35(AVD) - 15/logcat-dev.powerfulcalendar.ui.v2.WebSkinRendererRecoveryInstrumentedTest-clearRendererExitPersistsNativeRecoveryAndPreservesStore.txt` 含 `Renderer process (...) crash detected`；SHA-256 `51310015C6F853B6DFD8DEACF90C62003F1C99499500C5E88C976CC88C8D2785`。 |
| 全量 JVM / Android 构建门禁 | `PASS` | `:app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=1 --console=plain`；784/784 tests、125 suites、0 failures/errors/skips，152/152 tasks。日志 SHA-256 `18333BCA06856FACAD675B476B8798085FBC82444D6DB57CB2E94D7FD0252B48`。 |
| APK | `PASS` | `app/build/outputs/apk/debug/app-debug.apk`，66,516,951 bytes，SHA-256 `7D6A3A37C86116DEB94A1CFEEC5E1235AD0245BBD4C1AFBCAEA096A9B727B634`。 |

聚焦 instrumentation 日志 SHA-256：`9CEE1184DA2172C40FF609F50E9A7CE555249445BAFDDD8E1740989D1E30ABE0`。聚焦与完整 Gradle 日志位于忽略目录 `.local/android-test/reports/sep14-renderer-recovery-20261031/`。测试实际通过后，没有对设备执行卸载、清库、清 AVD 或清缓存。

## 尚未验证

本结果只覆盖已加载 Clear 的 WebView renderer 进程终止与 Native 紧急恢复。它**不代表 SEP-14 完成**。后续章节分别验证 Activity 重建、dirty editor checkpoint、冷启动草稿恢复、坏归档回退及未捕获脚本运行错误；以下仍未验证：恶意或无异常但行为不正确的皮肤、升级失败、设备重启后的数据保持、实体设备/OEM/WebView 版本差异。

## Follow-up — Clear 编辑器 Activity 重建恢复

API 35 AVD 的新增真实 WebView 回归覆盖了两条 Activity 重建路径：未修改的新建编辑器重建前后均不产生 Store 写入；标题有实际修改时，重建后恰好恢复一张可从 Clear “待办”入口重新打开的草稿，正式事件与提交记录仍为零。该回归没有发现生产代码缺陷，因此只增加 Android instrumentation 测试，没有修改 Runtime、Bridge 或业务代码。

- 设备回归：`ClearActivityRecreationRecoveryInstrumentedTest` **1/1 PASS**，Android 15 / API 35，AVD `codex-clear-api35`。覆盖无 Diff 不写入、脏输入的 Activity 重建、Clear 待处理卡组重开与事件/身份/队列水位计数。
- Connected log SHA-256：`8609457D4F177A15EC252CF3A939333669C364C71546A57C03646C257E93F3AA`；JUnit XML SHA-256：`01929D722B15C2D83A66C8EBD19E0BDDE859DE8FDB2A4F555E5A9C597A8CBA8F`。
- 完整离线门禁 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`：**784/784 JVM、125 suites、0 failures/errors/skips**；源码边界与 Debug 构建通过；Lint **0 errors / 55 warnings**。日志 SHA-256：`BFDB3D9BBCA7ABB85A82BEF0B58A257037962B9199F83C0ECA877E2B43B6E3CD`。Debug APK SHA-256：`F924AF9CABD50ECF6F3D68CB0CFB595D81669466A35A8C27C75C7B95320F1F46`。
- 新测试位于 `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/ClearActivityRecreationRecoveryInstrumentedTest.kt`；全部详细证据保存在忽略目录 `.local/android-test/reports/sep14-activity-recreation-20261031/`。

此项当时只证明 Activity 重建后的恢复；后续冷进程恢复证据见本文最后一节。损坏皮肤/脚本、升级失败、设备重启、实体设备/OEM 仍为 **NOT TESTED**；SEP-14 保持 **INCOMPLETE / HOLD**。

## Follow-up — dirty transient editor during renderer termination

在 API 35 AVD 的实际 Clear WebView 中打开 ACTIVITY 新建编辑器并修改标题。renderer 故障前验证表单只存在于内存：正式事件、草稿、待处理项、提交和身份均为 0，下一序号为 1。随后通过 `WebViewRenderProcess.terminate()` 终止真实 renderer。Host 故障回调完成 checkpoint 后切换至 Native 安全页；Store 恰好保存一张保留完整标题的 ACTIVITY 草稿，没有创建正式事件或提交记录，身份、事件序号和队列序号各只前进一次。没有发现生产代码缺陷，本次仅增加 Android instrumentation 回归。

- 聚焦用例 `clearDirtyTransientEditorIsCheckpointedWhenRendererProcessTerminates` **1/1 PASS**；`WebSkinRendererRecoveryInstrumentedTest` 整类 **2/2 PASS**。设备为 `codex-clear-api35` / `emulator-5568`，Android 15 / API 35 / WebView 124.0.6367.219；通过直接 `adb shell am instrument` 运行。
- 设备轨迹：`draft=1; title=RENDERER_DIRTY_EDITOR_CHECKPOINT; formal=0; commits=0; identities=1; sequence=2; native=true`。聚焦 runner SHA-256 `D4E91E6B971A5D46907B56F75A98E6542211D761661ACF87AFC752DA73B2F437`；整类 runner SHA-256 `DC80BB91414D95FAF6AEA2D6FD2DEC35BEEFDE073FC3B76046EFE311A55B6671`；Logcat 轨迹 SHA-256 `A0DFAE84DE7B72D888EB8B0644209BDE96386C2C9FB3B7CB6D923DE5BCF9E8F9`。
- 完整离线门禁 **784/784 JVM、125 suites、0 failures/errors/skips；171/171 tasks PASS**，包含 Native/Skin 源码边界、AndroidTest Kotlin 编译、Debug 与 AndroidTest APK、Lint **0 errors / 55 warnings**。门禁日志 SHA-256 `C3E05B6DA1AED17F95EFB411217CB83555FC27FEBBB68075C5989815E16814D5`。
- Debug APK：66,517,863 bytes，SHA-256 `779B0CF777BD5E1EE377249CD4156DB426DC87690B6D35EDD458E5F27A27CD1B`。AndroidTest APK：1,496,216 bytes，SHA-256 `D554AC3E0F2B35294E1485DA3229EF5B91AEF655E841B02330E3862D6F3C4053`。
- 证据目录：`.local/android-test/reports/sep14-dirty-renderer-20261031/`。测试使用唯一临时数据库并恢复测试偏好；清理后没有 `skin-renderer-*` 数据库残留。没有卸载应用、清除应用数据或擦除 AVD。
- 测试提交：`d46e341673da6eb24d6b6fb6344d43de55de8481` — `test(皮肤分离): 验证脏编辑器renderer故障恢复`，已推送。

这项新增证据关闭 dirty transient editor + renderer termination 场景；冷进程恢复由本文最后一节补充验证。损坏皮肤/脚本、升级失败、设备重启和实体设备/OEM 仍未验证，因此 SEP-14 继续 **INCOMPLETE / HOLD**。

## Follow-up — Clear 脏草稿冷进程恢复与运行链路核验

补充了 `ClearProcessRestartRecoveryInstrumentedTest`。第一阶段在实际打包的 Clear 中打开 ACTIVITY 新建编辑器、修改标题，再将 Activity 移到后台触发 `ON_STOP`；Store 只产生一张含完整标题的待处理草稿，正式事件与提交仍为 0。脚本随后 force-stop 两个进程包，第二阶段确认 PID 和进程 token 均变化，数据库中的 `draftId`、`eventSeq` 和标题与 marker 一致；通过 Clear 的“待办”入口打开草稿后，Host `deck.open=true`，没有重复写入。

- `dirtyClearEditorCheckpointsOnStopForColdProcessRecovery` 与 `coldProcessRestartRestoresTheDirtyClearEditorDraft` 分别 **1/1 PASS**；两阶段 PID 为 `4927` 与 `5116`。设备轨迹：`draft=1; formal=0; commits=0; sameIdentity=true; clear=true`。轨迹 SHA-256 `45CCE95DB3495E8D69E3661FB3C7980B392F5C63CA786B59C53B3B89B610C1D3`；prepare/restore runner SHA-256 分别为 `92CE75E874E7B9F46F9040CBE9A66407D2230EBE3AAE3B75C555C9ADBFB7894E` 与 `502F2D60D6C38A0444619BB600D047B3BBEE59F58A4BC4B0764DE0F61B6784C5`。
- 本次没有复现 Clear 的生产运行故障。前两次新探针失败是测试把 `evaluateJavascript` 返回的 JSON 编码字符串直接与原始文字比较；保留的诊断轨迹已证明真实 DOM 点击通过 `routeAction`，发送 `deck.open`，收到 Host `opened` 回执且 Host 状态为 open。断言改成布尔比较后，两阶段恢复测试通过。该测试问题不应记作产品缺陷。
- 额外 API 35 WebView 定向回归 **27/27 PASS**，覆盖 Clear 待办搜索与提案、Memo、Activity 重建、renderer 故障、时间轴缩放/复位/拖动、AI 提案审查、设置入口、时间轴/月历创建上下文、类型转换及正式删除触控。逐类 runner、状态和哈希在 `.local/android-test/reports/sep14-cold-process-20261031/clear-runtime-recheck-manifest.json`，manifest SHA-256 `264F6D5FBDCD1F69C16A2C8C3E964BB070BC0ADC4255DBD8EA9FDA2D8A21B257`。该目录的 logcat 扫描没有发现未捕获的 JavaScript SyntaxError、TypeError 或 ReferenceError（SHA-256 `40DBBE051FC847941AC8E94D689FE484D9633911375255D2808275124D338F44`）。
- 使用的是仓库现有的 `PowerCal_API35` **只读** AVD，serial `emulator-5570`，Android 15 / API 35 / WebView 124.0.6367.219；不是原 `codex-clear-api35` 实例。原实例的 qemu 与 ADB shell 在本轮验证时无响应，因此没有擦除、强制结束或覆盖它。它上面唯一测试 UUID `8ffe06a7-212f-41d0-b830-fb0f1571eabd` 的合成恢复夹具无法经 ADB 检查或清理；本轮未触及 `powercal-v2.db`。只读 AVD 上的本轮 UUID fixture 已确认清理完毕。
- 完整离线门禁：**784/784 JVM、125 suites、0 failures/errors/skips；171/171 tasks PASS**；Native/Skin 边界、AndroidTest Kotlin 编译、Debug 与 AndroidTest APK、Lint 均通过，Lint 0 errors / 55 warnings。门禁日志 SHA-256 `29A7F367D9AE545CCACC1E2D43A723AE8107A7E28547EF04DD11E5D8A831576E`。Debug APK SHA-256 `779B0CF777BD5E1EE377249CD4156DB426DC87690B6D35EDD458E5F27A27CD1B`；AndroidTest APK SHA-256 `32374ACEED6F973E6DC7C4631BA5AA96F4101854344E7191DAFCC51E9208FAE4`（1,508,964 bytes）。
- 测试提交 `0ff36e7`（`test(皮肤分离): 验证Clear冷进程草稿恢复`）已推送到唯一集成主线。其他 Agent 对 `WebSkinFormalDeleteInstrumentedTest.kt` 与 `WebSkinRecurrenceInstrumentedTest.kt` 的修改只参与本轮只读验证，未编辑、暂存或包含在本提交中。

此证据关闭 SEP-14 的脏新建编辑器 `ON_STOP` checkpoint 与冷进程恢复场景。损坏/脚本异常皮肤、升级失败、设备重启、实体设备/OEM 仍未验证，SEP-14 继续 **INCOMPLETE / HOLD**。

## Git

- 测试提交：`89e6cd3e24a3ed65964620f4d31a9cde3a9a20a6` — `test(皮肤分离): 验证Clear WebView崩溃恢复`
- Activity 重建回归测试提交：`5d6b421` — `test(皮肤分离): 验证Clear Activity重建恢复`，已推送至 `origin/feat/skin-first-separation`。
- 本报告、SEP-14 状态补充和进度记录随后单独提交，避免报告 SHA 冒充测试时的代码基线。

## Follow-up — 已选中损坏归档的冷启动 Native 回退（2026-10-31）

新增 `WebSkinCorruptPackageColdRecoveryInstrumentedTest`，验证一份已持久化选择、但不是有效 ZIP 的 `.pcskin` 在应用进程冷启动时如何恢复。测试使用 API 35 模拟器和 UUID 专属测试包；启动前通过 ADB 确认 SharedPreferences 指向该坏包，再 force-stop 并由测试规则直接启动真实 `MainActivity`。`restoreSelection()` 捕获解码失败，持久化选择回到 `powercal-default`，注册表恢复 Native；坏归档不会进入皮肤目录列表。前台无 WebView，Native Compose“时间轴”节点可见。

Store 前后快照相等，涵盖正式对象序号/版本/类型、待处理草稿身份、草稿及待处理数量、提交数、身份数、下一事件序号和队列水位。恢复后只清除本测试 UUID 的坏包与 marker，并把皮肤选择及首次启动偏好恢复到各自原有键值和文件存在状态。没有清除应用数据、修改正式记录、卸载应用或擦除 AVD。

- 模拟器：`emulator-5570`，Android 15 / API 35，WebView `124.0.6367.219`。prepare **1/1 PASS**；冷启动 restore **1/1 PASS**。启动前 ADB 确认选中的坏包 ID 为 `pc-corrupt-96cacfd3`；restore 断言 PID 与进程 token 更新、Native 选择与注册表恢复、坏包仍可诊断但不出现在目录、无残留 WebView、时间轴语义节点可见，以及 Store 快照相等。
- 最终 prepare / restore runner SHA-256：`3240EB7EFA5631DE4181EDF3DD675D5BE17C9D85EDC1F09459BF5D820B427A23` / `F03E93C21FF6D0F3A68615F2C4B03FCA413135A44164CC61EC8260E7AA88F081`。门禁日志 SHA-256：`4A0C2D4C1164334344C561CC14B2DC238378A3ABEFAE95C937ABD5870AC1748B`。
- 完整离线门禁 `:app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain`：**785/785 JVM、125 suites、0 failures/errors/skips；171/171 tasks PASS**。Debug APK：66,518,075 bytes，SHA-256 `ED2F8F9827EF820361CC663F9C89D7F84A2545AB6DB313F36587C2E1DA1D064B`；AndroidTest APK：1,690,264 bytes，SHA-256 `AD3253649208E839E74F0F0FF3547748780E3E9D73674C4C11FE304ACD02D2A5`。
- 详细忽略目录证据：`.local/android-test/reports/sep14-corrupt-package-cold-recovery-20261031/`。最初使用独立 `ComponentActivity` 的全局 Accessibility 探针没有绑定到 `MainActivity` Compose 树，该次断言失败且未计入通过；改用 `MainActivity` Compose 规则后，最终两阶段均通过。没有发现需要修改 Runtime/Host/Core 的产品缺陷。

这只关闭“已选择、不可解码 ZIP 在冷启动时回退”的窄场景。未捕获 JavaScript 错误的 Native 恢复由后续 follow-up 覆盖；被捕获/静默的功能故障、恶意但不抛错的皮肤、升级失败、设备重启及实体/OEM 恢复仍为 **NOT TESTED**，SEP-14 继续 **INCOMPLETE / HOLD**。测试提交 `84902b5` 已推送到唯一集成主线。

## Follow-up — 未捕获 JavaScript 运行错误的 Native 恢复（2026-10-31）

**复现与根因：** 在 API 35 的实际 WebView 里，从 Clear 皮肤包的内存副本启动 ACTIVITY 新建编辑器并改标题，再触发 `web/app.js` 的未捕获 `Error`。修复前定向测试 **1/1 FAIL**：Android Chromium 明确记录 `Uncaught Error: SEP14_UNCAUGHT_SKIN_RUNTIME_ERROR`，但 Host 没有接到故障通知，皮肤仍留在编辑器，标题修改没有恢复草稿。原有 `WebViewClient` 只覆盖主框架资源错误和 renderer 退出。

**修复：** 通用 `WebSkinHost` 新增 `WebChromeClient` 监听，只把 `skin.powercal.local` 来源、ERROR 级且以 `Uncaught` 开头的运行错误交给现有恢复路径；`WebSkinRoot` 用每 bundle 的原子闸门合并 renderer、资源及脚本错误回调，避免同一故障重复 checkpoint/恢复。没有改 Clear 生产包、Native 页面、Bridge 业务语义或 Core。测试在内存构造的 Clear 副本里注入触发器，不安装或覆盖任何皮肤包。

- API 35 `emulator-5570` / Android 15 / WebView `124.0.6367.219`：修复前 **1/1 FAIL**，修复后定向 **1/1 PASS**，完整 `WebSkinRendererRecoveryInstrumentedTest` **3/3 PASS**。脏 ACTIVITY 标题被 checkpoint 为恰好一张草稿；正式事件与 commit 均为 0，身份数为 1、下个序号为 2，Native 回退页可见。前后日志与实际 Chromium console 记录位于 `.local/android-test/reports/sep14-js-error-recovery-20261031/`，manifest SHA-256 `70510F65E293AEF58F8010B77AA0A7F2E5B799A60B342B38D8E6C65A9EB20D53`。
- 最终完整离线门禁：**785/785 JVM、125 suites、0 failures/errors/skips、171/171 tasks PASS**；Lint **0 errors / 57 warnings**。日志 SHA-256 `9F148E42E53F5B1EA9988B63191626B3BBA527EBD282F3255B970248F45EEE04`。Debug APK 为 66,518,075 bytes / SHA-256 `DE876D09D11689F98131C9DAD0A977AA1271E1657612B6A95DB89ABBBB80C31C`；AndroidTest APK 为 1,711,180 bytes / SHA-256 `B772B29958724AEFDD1B974EB625C7ED154870C6B7462BA7FEAF257443417965`。
- 生产 Host 与回归测试提交 `f5fb8d420def3a933b365bc0f9f4a62323dd5c77`（`fix(皮肤分离): 捕获脚本异常并触发原生恢复`）已快进推送至 `origin/feat/skin-first-separation`。其他 Agent 的 FormalDelete/Recurrence AndroidTest 修改保持未触碰、未暂存；它们参与 AndroidTest APK 编译，但本次定向恢复类没有执行。

该 follow-up 关闭“皮肤来源的未捕获 JavaScript 异常”恢复切片；没有证明 caught/silent failures、恶意但不抛错的行为、升级失败、设备重启或实体/OEM。SEP-14 仍为 **PARTIAL / HOLD**。

2026-10-31 Studio Python 全量回归暴露了一条过期静态断言：它仍要求源码出现 `onSkinFailure()`，而 `f5fb8d4` 已把回调收束为每 bundle 幂等的 `reportFailure → currentOnSkinFailure()`。仅更新测试契约以检查 compare-and-set 闸门、当前回调调用和传给 `SecureWebSkin` 的转发；没有生产源码变更。Studio Python suite **65/65 PASS**，`WebSkinRendererRecoveryInstrumentedTest` 在 API 35 / `emulator-5570` 复跑 **3/3 PASS**。JUnit XML SHA-256 `adb77c6823ea5bd63ef11b8eaec5d9d3edc6c0b3f78d187f3296601a86cbeb38`；完整日志位于 `.local/android-test/reports/sep15-studio-clear-bridge-20261031/studio-python-tests.log` 与 `renderer-recovery-android-test.log`。SEP-14 总项保持 **PARTIAL / HOLD**。
