# PC-ISSUE-SEPARATION-DRAFT-DECK-SEARCH-NAVIGATION — Clear 草稿搜索、选择与导航

日期：2026-10-29
状态：**Host Bridge、Clear 源码、JVM、Preview 与 Chrome 回归 PASS；API 35 Clear Android WebView 已验证，独立 Native Compose 基线也通过；同一夹具 Native/Clear 配对回放和实体设备验收仍 NOT TESTED。`S0-DK-01` 保持 PARTIAL / unsigned。**

## 问题与验收边界

Native 草稿卡组可以搜索待处理草稿和正式事件、选择匹配草稿、逐张或跨多张导航，并从正式事件搜索结果提出替换草稿。Clear 虽已有 `deck.open/select` 和有限卡片投影，但没有相同的正式事件搜索、Deck 跳转及从搜索结果创建替换提案的 Host 命令；Clear 页面也没有对应操作。

本 Issue 补齐上述现有 Native/Core 能力。检查 `DeckController` 后，`move` / `jump` 只改变当前卡片位置并在切换前 flush 当前修改，未发现改变永久 `queueOrder` 的重排操作。因此实现的是草稿导航与选择，没有创造 Native 不具备的持久队列重排功能。

本报告的 PASS 仅覆盖所列代码路径和测试夹具。2026-10-30 已在 API 35 模拟器的实际 Android WebView 验证 Clear 搜索与替换提案；Native Compose 通过独立基线夹具。尚未完成同一夹具的 Native/Clear 配对回放及实体设备验收，因此不签核完整 `S0-DK-01` 或整体分离。

## 根因

1. `WebSkinEditBridge` 没有 Clear 草稿搜索、卡组跳转、正式事件搜索命中后提出 `REPLACE` 草稿的命令；Clear 无法复用 `DraftQueueSearch`、`FormalEventSearch` 和 `DeckController.jump` 的既有行为。
2. Clear 草稿卡按钮位于 `#sheetFields`，原点击代理只注册在 `#sheetActions`，因此卡片虽然显示，点击不会调用 `deck.select`。
3. 正式事件搜索结果容器允许在编辑抽屉的纵向 Flex 布局中收缩。Chrome 实测时它被压缩，结果按钮只露出窄边，不能正常选择。
4. 搜索投影将筛选后的卡片数用于副标题，搜索期间总待处理数量可能显示错误。
5. `deck` 面板同时承载持久化草稿卡组和未持久化的新建编辑会话。若不区分状态，会在新建编辑器中显示不适用的持久化草稿搜索操作。
6. Skin Studio Preview Mock 的 `deck.flush` 无条件执行持久化卡组投影，会把 transient 新建编辑器变为种子草稿队列；同一操作后选择卡片会覆盖新建表单。Mock 序号搜索也缺少 Core 支持的 `#0001` 格式。专用 E2E 仅记录 `pageerror`，未将页面异常作为失败条件。

## 修复

- `WebSkinEditBridge.kt` 增加封闭 schema 的 `deck.search`、`deck.jump` 和 `deck.proposeReplace`。搜索输入限 120 字符并拒绝控制字符；跳转步数限制在 −1000..1000；替换命令只接受事件序号与预期 revision，不接收皮肤构造的表单。
- 草稿搜索复用 `DraftQueueSearch`；正式事件搜索复用 `FormalEventSearch`。投影限制为最多 4 条搜索命中；普通卡组列表最多 80 条，并明确报告截断状态。仅投影最小展示字段。
- 正式事件替换由 Host 根据当前搜索命中、Store 锁和最新 revision 再校验。复用同一事件已有的兼容 `REPLACE` / `CONVERT` 草稿；否则调用现有 `PowerStore.proposeReplace`。正式事件不在搜索或提案时改变，之后仍须走原有 Host 审核/发布路径。相同 `requestId` 重放沿用 Bridge 既有回放保护。
- `deck.jump` 委托既有 `DeckController.jump`，切换前 flush，保留原队列顺序；选择和移动后清除搜索结果缓存。
- Clear 增加草稿/正式事件搜索、替换提案、可访问的草稿卡片列表及上一张/下一张控件；将卡片点击代理注册到实际承载卡片的 `#sheetFields`。
- 固定搜索栏和正式结果列表的 Flex 收缩，结果区域保留滚动；副标题始终显示 Host 提供的待处理总数。
- Host 投影显式标注持久化 Deck 与 transient 新建会话；transient 编辑器隐藏持久化搜索、卡片选择和卡组导航，但保留当前表单操作。回归验证搜索命令不会在 transient 会话创建 Store 写入或分配身份。
- Studio Preview Mock 的 transient `deck.flush` 仅保存当前 Mock 会话快照，不切换成持久化卡组、不改写 seeded drafts；Mock 搜索支持 Core 的格式化序号匹配。专用 E2E 验证 `#6201` 命中及 checkpoint 后 transient 表单保持，并将未捕获页面异常判为失败。
- 修正综合 E2E 的两项过期夹具假设：触摸拖动后的点击等待超过产品的 900ms 抑制窗口；月份断言分别验证 31 个本月日期、完整周槽位和 7 个星期标签，不再将网格槽位计作本月日期。
- 重新从 `default-clear/source/` 构建规范 `.pcskin`，没有手改生成包。

## 验证证据

环境：Windows PowerShell；Gradle Wrapper 8.14.3；JDK 20；Android SDK `E:\android-sdk`；Python 3.11；Node 24；Chrome 156. SDK 环境变量仅对 Gradle 进程设置。测试使用 Robolectric 隔离数据库、Studio 临时 fixture 和独立 Chrome 临时 profile。

| 检查 | 结果 | 可复核证据 |
| --- | --- | --- |
| `WebSkinEditBridgeTest` 定向回归 | **25/25 PASS**，0 failure/error/skipped。断言覆盖：草稿与正式事件搜索最多 4 条且不写 Store；普通与 `#` 格式序号搜索；`deck.jump` 使用现有 Controller 且不改变 `queueOrder`；替换命中使用 revision 栅栏、重放不重复创建草稿；transient 新建界面拒绝持久化搜索且不分配身份。既有 20 轮四种事件类型的无写入退出测试继续通过。 | JUnit XML `app/build/test-results/testDebugUnitTest/TEST-dev.powerfulcalendar.ui.v2.WebSkinEditBridgeTest.xml` SHA-256 `FA2682ED808B85E3484D7AFD6A8C327BA598635649C21C3BD70CE3FD3A7A5B55`；定向日志 `.local/android-test/reports/separation-s0-dk-01-20261029/gradle-focused-final.log` SHA-256 `8F88230179B46611329595108492C347C4C55B8B5F8BF440BD7453F964ADECF9`。 |
| 全量离线 Gradle 门禁 | **BUILD SUCCESSFUL**；630/630 JVM 测试、84 suites、0 failures/errors/skips；88/88 tasks；`verifySkinSourceBoundary`、Debug APK、AndroidTest APK、`lintDebug` 均成功；Lint 0 errors / 52 warnings。命令：`$env:ANDROID_HOME='E:\android-sdk'; $env:ANDROID_SDK_ROOT='E:\android-sdk'; .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain`。 | `.local/android-test/reports/separation-s0-dk-01-20261029/gradle-full-final.log` SHA-256 `815209BA15F9C033E62A75F221DF8C5C47A79D99AE0B723F1EEFB55DF955B81E`。Debug APK SHA-256 `11E61272BFAA51698897DAB08EF7391B0B97A4F500689291B16FB17C7A29C684`；AndroidTest APK SHA-256 `0883557A280C1F834EFD33A177FFACD423E1269FF285391B1E395FACF1252AA5`。AndroidTest APK 仅成功构建，未安装运行。 |
| Skin Studio Python | **61/61 PASS**。使用进程级 Git `safe.directory` 参数运行，没有更改全局 Git 配置。 | `.local/android-test/reports/separation-s0-dk-01-20261029/studio-python-final.log` SHA-256 `AE7B71809B7EC7CC043A40B638744205ECFC5AEBC972B927D4EF95C1951FF067`。 |
| Chrome 156/CDP Clear 源码 Browser Mock | **PASS（Mock Host，不是 Android WebView）**。视口 430×900；正式命中 2 项，每项实测 42 px 高，结果容器 101 px；总草稿数仍显示 5；替换、草稿搜索、卡片选择、下一张均发出预期 Host 命令；结束位置 `2 / 6`、当前草稿 `Draft 2`。切换到 transient 状态后搜索/持久化卡片区均不存在，内存表单仍显示。 | 回放脚本 `.local/android-test/reports/separation-s0-dk-01-20261029-cdp/s0-dk-01-browser-mock.cjs`；日志 SHA-256 `F96A0F6AE595A344CFBD6B87DD84A68683FB9947AEE55A626C33B20584C44991`；结果 JSON `.local/android-test/reports/separation-s0-dk-01-20261029-cdp/run-1793266398103/result.json` SHA-256 `749D7C89C8969F85CC5D5060B74590909E7A99065CB8B1658C224DD99F500F5A`。截图：正式搜索 `.local/android-test/reports/separation-s0-dk-01-20261029-cdp/run-1793266398103/formal-search.png` SHA-256 `1660FBC705DAF298741000251683E5EEA5079FF832199F99CD918EBC0EFDCF1C`；导航 `.local/android-test/reports/separation-s0-dk-01-20261029-cdp/run-1793266398103/deck-navigation.png` SHA-256 `F792F4666FE37CCC55F60D8F5A99A32D2D1099DDB9E1912D8929969566D0BD88`；transient `.local/android-test/reports/separation-s0-dk-01-20261029-cdp/run-1793266398103/transient-state.png` SHA-256 `2EAE0DE21A5F3A43B0B24C9B4A0078B9E7346F2DED4B2ACBC38B309844AD3BE6`。 |
| Studio Preview Mock 专用浏览器回归 | **PASS**。覆盖 `#6201` 格式序号搜索、正式事件搜索与替换提案、草稿搜索、选择、导航，以及 transient 新建表单 checkpoint 后仍保留当前表单且不显示持久化搜索。未捕获页面异常计数为 0。 | `tools/skin-studio/tests/e2e_v3_deck_search.cjs`；日志 `.local/android-test/reports/separation-s0-dk-01-20261029/studio-deck-focused-final.log` SHA-256 `ED32CA8A9490E983749637A421C7AF336D4B3EDAF29F80063805A84D5DBC009A`。 |
| Skin Studio 综合 Chrome E2E | **BROWSER E2E PASS**。覆盖时间轴真实几何和实际文字矩形贪心折叠、独立翼区滚动、鼠标/触摸行为、编辑与 Host Mock 确认、草稿搜索/替换/导航、新建入口、Memo、月份、AI Review、自由图层/材质/PNG 资源、导出重导入及 Preview/WebView 资源策略。运行 Chrome 156，使用临时 profile；未调用真实 Provider 或 Android WebView。 | `tools/skin-studio/tests/e2e_v3_browser.cjs`；日志 `.local/android-test/reports/separation-s0-dk-01-20261029/studio-browser-edge-final.log` SHA-256 `C493CAA31F252E0D967DA884CFB4BDBF8BFCBE6D73E389BE1932DDD04AE7367B`。 |
| JavaScript、规范包、内嵌 APK 资产 | 所有修改的 Studio / E2E / Clear JavaScript 通过 `node --check`。`python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk` 通过；canonical Clear 与 APK 内嵌资产字节相同：9 files / 387,597 bytes。 | `.pcskin` SHA-256 `FEDC9D282672297587FE96BCC83752B51FBA01907F002299B5B7A4BA4AD941EE`，APK 内资源同值。 |
| Android WebView / AVD / 真机 / 配对 Native-Clear 回放 | **部分验证**。API 35 Android 15 模拟器、WebView 124.0.6367.219 上 Clear AndroidTest **1/1 PASS**；Native Compose 独立基线 **1/1 PASS**，使用不同夹具，不算配对回放。实体设备由用户测试；同夹具 Native/Clear 回放仍 **NOT TESTED**。证据见 2026-10-30 follow-up。 | API 35 AVD 截图和仪器日志见 follow-up；实体设备由用户测试。 |

本地详细日志、JUnit XML、浏览器 JSON 和截图存于忽略目录 `.local/android-test/reports/separation-s0-dk-01-20261029-*`；未加入 Git。

## 修改文件

- Host：`app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridge.kt`
- JVM 回归：`app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridgeTest.kt`
- Clear：`tools/skin-studio/default-clear/source/web/edit.js`、`tools/skin-studio/default-clear/source/web/style.css`
- Studio Preview Mock 与浏览器回归：`tools/skin-studio/v3-studio.js`、`tools/skin-studio/tests/e2e_v3_deck_search.cjs`、`tools/skin-studio/tests/e2e_v3_browser.cjs`
- 契约与操作文档：`docs/V3_HOST_BRIDGE_EXTENSION.md`、`docs/README.md`、`tools/skin-studio/README.md`
- 生成包：`app/src/main/assets/powercal-clear-default.pcskin`
- 独立报告与验收矩阵/进度同步见本文件及相邻变更。

## 结论与后续

正式事件搜索（包含带 `#` 的格式化序号）、替换草稿提案、草稿卡片选择/导航和 transient UI 区分的实现缺口已由 Bridge JVM、Studio Preview Mock、Chrome E2E 与包一致性证据覆盖。独立复核提出的 3 项 Preview Mock/E2E 差异已修复并验证。完整 `S0-DK-01` 仍 **PARTIAL / unsigned**：Clear Android WebView 已通过 API 35 实测，同数据集 Native/Clear 配对回放和实体设备验收尚未完成。`S0-DK-03` 仍有独立缺口：Clear 尚无完整的冲突快照替换/保留和删除后恢复 Host 操作；本次只移除了“缺少正式事件搜索”的过期描述，没有把该项标 PASS。

代码提交 `3aa03c3453475bee1ebf10f0efaf910c3ab537ab`（`fix(分离): 补齐 Clear 草稿搜索与导航`）已推送至 `origin/codex/separation-s0-timeline-drag`。本 Issue 与契约/验收进度文档作为独立文档提交随后推送；提交 SHA 见本轮交付报告与 Git 历史。

## Follow-up — Android WebView 编辑抽屉可见性与真实 Deck 回归（2026-10-30）

本节更新并取代上方 2026-10-29 的 Android WebView `NOT TESTED` 状态；历史结果保留作基线。

- 根因：Android 15 API 35 模拟器的 Android WebView `com.google.android.webview 124.0.6367.219` 中，`55vh` 与 `100dvh` 计算结果为 `0px`。当 `.sheet-panel` DOM 显示时，实际矩形仍为 `[x=0,y=684,width=412,height=0]`，搜索表单虽在 DOM 中却无法看见。调试探针显示固定全屏父容器中的百分比高度正常计算到 654.19 CSS px。
- 最小修复：Clear `.sheet-panel` 从 `max-height: calc(100dvh - 30px); min-height: 55vh` 改为相对 `#skinSheet` 的 `max-height: calc(100% - 30px); min-height: 55%`；重新生成规范 9 文件 `.pcskin`。没有改 Host 命令、Store 语义或事件数据。
- 新增真实 AndroidTest：隔离 Store 中建立 3 张待处理 MEMO 和 1 个正式 MEMO，打开实际 `PowerCalApp`/WebView；执行正式事件 `#0004` 搜索、草稿选择、卡组导航和替换提案。断言正式事件和 revision 不变、commit 数不变、身份和正式序号水位不变、队列顺序不变，并且恰好多一张 REPLACE 草稿。真实窗口使用 `PixelCopy` 截图；断言抽屉在 WebView 中可见且高度不少于视口一半，搜索和提案画面不同。
- API 35 Clear WebView 仪器回归 **1/1 PASS**（最终 Debug/AndroidTest APK，6.94 秒）；独立 Native Compose Deck 基线 **1/1 PASS**。两者不是同夹具配对回放。截图：搜索 `.local/android-test/reports/separation-s0-dk01-audit-20261030/s0-dk01-formal-search-final-verified-apk.png` SHA-256 `CB851C6CD10B9E0CE4CB98E113284C33AA611ACFB598418AAE6E4D6AAE851914`；替换提案 `.local/android-test/reports/separation-s0-dk01-audit-20261030/s0-dk01-replacement-proposal-final-verified-apk.png` SHA-256 `81787F5C90F354B3C0FF4FA32D0584780B99A869999886F1D221CAADF990C677`。两张均为 1080×1920 的模拟器 PixelCopy 图像。
- 全量离线 Gradle 强制重跑 **727/727 JVM 测试、100 suites、0 failures/errors/skips，88/88 tasks PASS**；源码边界、Debug/AndroidTest APK 构建、Lint 通过（0 errors / 53 warnings）。Skin Studio Python **61/61 PASS**；Edge/CDP Studio source E2E **PASS**；规范 Clear 与 APK 内资源逐字节一致：9 files / 394,086 bytes，SHA-256 `8f7f5680bfd27976b2792aab06d051baf0f03f4a914636ea73e82e48431f8035`。
- 环境：Android 15 / API 35、1080×1920 AVD `emulator-5582`、WebView 124.0.6367.219；Gradle 8.14.3、JDK 20、SDK `E:\android-sdk`。最终 APK SHA-256：Debug `195BA8FAE40F3B487C1345CEA242C9380A2B1088FBED38CB57E6418E340BD78A`，AndroidTest `67EAC00E441F9A60728501D7A5391C327F6322D89B707E0BC3D5FEF125E9C1F5`。全量日志 `.local/android-test/reports/separation-s0-dk01-audit-20261030/full-gradle-post-gesture.log` SHA-256 `68D8D1CFFDCA45F152608A026E81D39C54A89C58A3EBDBC11E7947BC30A278FB`；最终 WebView 仪器日志 `webview-instrumented-final-verified-apk-after-reboot.log` SHA-256 `F439256E63D1EF3E0213A5D46A97FE90C6A3C94FB39A3CC875544755E473C694`；Python 日志 `BAF094294F60432040E3AAD18A7CB16322B3648CC174EA2AE92ED685A72FA6E7`；Edge source E2E 日志 `5EE084EB5A0DACDD7E3562E2629FBE796E9E32BD9107100D82B85B8C8E983E6A`。
- 初次最终 APK 仪器启动时，模拟器进程在测试开始后退出（exit 255，无 JUnit 结果）；保留原 AVD 数据、不 wipe，按原启动参数重启后重新安装并复跑，最终为上方 **1/1 PASS**。没有将中断运行计为通过。
- 修复提交 `6214354`（`fix(分离): 修复 Android WebView 编辑抽屉高度`）已推送至 `origin/codex/separation-s0-timeline-drag`。本 Issue 仍 **PARTIAL / unsigned**，等待同夹具 Native/Clear 回放和用户实体设备验收；`S0-DK-03` 的冲突恢复缺口仍独立保留。
