# PC-ISSUE-SEPARATION-CLEAR-GREEDY-LABEL-FOLDING — Clear 时间轴标题贪心折叠

- 日期：2026-10-29；API 35 Android WebView / Native 对照 follow-up：2026-10-30
- 状态：**PARTIAL**。Clear 源码、确定性算法、Skin Studio 往返、Edge Studio Mock 视觉/交互、Android 15 API 35 Clear WebView，以及同一正式事件夹具在 Native/Clear 两个真实 `PowerCalApp` 路径上的 lead 集合对照均通过。完整设备/OEM、TalkBack 和所有交互状态的跨皮肤 parity 仍 **NOT TESTED**。初次 Android Gradle/API 36 门禁 BLOCKED 已被实现提交 `1d78353e0866c7bc19c6a555fb65cbc454aea1f2` 上的完整离线 Gradle PASS 取代。
- 工作树：`codex/separation-s0-inventory`
- 基线：`2a02ae71fbeb8b50e2cb099533665e66243a2e53`
- 实现/回归/报告提交：`c6b6f7d10e068ee0d4d292505f643b0fab4d1164` (`feat(分离): 实现 Clear 时间轴标题贪心折叠`)
- 范围：重建矩阵 `S0-TL-05` 的 Clear 运行时标题折叠；不更改 Native 时间轴、Host 时间几何或事件存储。

## 需求和根因

`S0-TL-05` 要求按标题实际渲染后的矩形折叠视觉冲突，只隐藏标题；事件框、真实开始/结束时间、点击目标和完整无障碍名称必须保留。基线 Clear 只在时间轴事件控件中渲染标签，没有从 DOM 测量候选标题并执行可见 lead 贪心决策的机制。因此，它既无法识别不同时间区间产生的文字矩形冲突，也无法保证被隐藏的标题不继续影响后续 lead。

## 修改内容

- 新增 `web/title-folding.js` 纯函数。候选按统一可见区域、屏幕 Y、稳定事件序号、稳定 ID 排序；仅对正面积严格相交的矩形判为冲突，所以边缘相切不折叠。每个候选只与当前可见 lead 比较；隐藏项不替换 lead。候选输入被验证，决策复制矩形，不依赖输入集合遍历顺序，也不修改输入。
- Clear `app.js` 将候选创建、实际 DOM 文字测量、可见区域裁切、纯函数决策和可见性应用分开。用 `Range.getClientRects()` 测量渲染结果，按既有泳道宽度单行截断并显示省略号。候选矩形统一使用浏览器视口坐标，并裁切到时间轴视口、对应左右翼和浏览器视口；不读取事件时间重叠关系作为碰撞代理。
- Activity 标签在事件视觉框能容纳时放在框内；不足时以真实可见开始位置为上缘锚点。事件时间几何和 44px 交互按钮独立保留。ALARM 与精确 Deadline 标签同样参与实测标题折叠；纯日期 Deadline 仍显示在日期条。
- 隐藏标题以 `visibility:hidden` 呈现，标题层 `pointer-events:none` 且 `aria-hidden=true`；正式事件按钮保留完整 accessible name、原始标题和点击/键盘目标。没有增加 `×N`，事件不合并、不隐藏。
- 滚动、视口尺寸变化、时间轴重绘和字体就绪后重新计算。Skin Studio 增加 A/B/C/D 和长标题场景；Clear bundle 包含纯折叠模块。

## 定向证据

### 纯函数回归

`node --test tools/skin-studio/tests/title-folding.unit.test.cjs`：**10/10 PASS**。

覆盖 A/B 冲突、被隐藏 B 不阻止 C 成为新 lead、C/D 下一冲突段、连续冲突、水平/垂直边缘相切、独立视觉区域、只用屏幕矩形（无事件时间字段）、屏幕 Y/序号/稳定 ID 排序、平移/缩放/复位确定性、输入不可变及非法输入拒绝。

### Edge Studio Mock 实测

- 环境：Microsoft Edge Headless `Edg/156.0.4314.50`，视口 `1460×960`，Skin Studio v3 Mock Host；证据目录 `.local/android-test/reports/separation-s0-greedy-title-folding/run-20261029-16/`。
- 决策轨迹：`101/A visible → 301/B folded against A → 302/C visible → 303/D folded against C`。完整候选中 C 前有其他泳道的可见标题，因此浏览器轨迹里 C 与序号 108 的可见标题比较；纯函数隔离 A/B/C/D 测试则直接确认 C 对比 A。两种轨迹都确认隐藏 B 未成为 lead。
- A/B/C/D 是相邻且不重叠的事件时间段：10:00–10:01、10:01–10:02、11:00–11:01、11:01–11:02；实际文字矩形却显示 A/B 与 C/D 视觉相交。A 不与 C 相交。

| 标题 | 视口文字矩形（left, top — right, bottom；CSS px） | 决策 |
| --- | --- | --- |
| A | `821, 502.890625 — 875.21875, 517.890625` | visible |
| B | `821, 503.34375 — 875.203125, 518.34375` | folded against A |
| C | `821, 529.6875 — 875.234375, 544.6875` | visible |
| D | `821, 530.140625 — 876.015625, 545.140625` | folded against C |

- 左翼标题序号 201/202/203：visible → 左翼横滚裁切后 clipped → 恢复后 visible。右翼 A/B/C/D：横滚到裁切区后均 clipped，恢复后 lead 决策完全相同。
- 纵向滚动、16 小时缩放、24 小时复位、日期离开再返回后，A/B/C/D 决策保持稳定。真实鼠标点击命中折叠事件 301 的 BUTTON；标题不在 hit target，Mock Host 打开 `#301 · ACTIVITY`。Mock 编辑器字段固定为“测试活动”，因此此 Mock 本身不能证明其表单展示 `Greedy_B`；已确认宿主事件按钮仍保留完整 `Greedy_B` accessible name。实体/Android WebView 的完整标题查看仍待测。
- 28 个事件均保留（27 个时间事件、1 个日期 Deadline）；按钮与视觉事件矩形相对基线逐事件相同；中轴、当前时间线和日期 Deadline 仍存在；没有 `×N`。
- 长标题显示为单行 `非常长的时…`，实际宽 `70.375px`，在 `71px` 泳道标题宽度内；按钮 accessible name 保留完整标题。

浏览器证据及摘要 SHA-256：

| Artifact | SHA-256 |
| --- | --- |
| `title-folding-trace.json` | `FB345216332C7B426F16854167085DD116617ED39DA94F428B077C58371CEE08` |
| `clear-before-greedy-folding.png` | `6BA5206B5B93612F9BD0FB303D7FD4D99FAD9E1978F3704AF257F727A173625B` |
| `clear-after-greedy-folding.png` | `D2C351E51200751ACFCED1F02A7DBCDCB48CC0F7A443972DBDCFF573F6EA6227` |
| Edge CDP 驱动脚本 | `0E242E4457C8C4E3A4D6AC2F99C56EAEBB7A61E87A03AF930BA257C0B3A256D1` |

截图为基线包与修改后包在同一 Studio timeline-geometry 场景下的前后视图。基线包 SHA-256 `0790173E60E4F991EAA2072B43DF04039AFC7AB80416770A285B96CD0B3118CD`；修改后 Clear 包 SHA-256 `75C1663B4FD7FCE40C8EADB31BA7E2C6C8AC092330FC4CB734EE6D21ACC2CE4C`。原始图片和 JSON 留在忽略的 `.local` 证据目录，不进入 Git。

### 其他验证

| 检查 | 结果 |
| --- | --- |
| `node --test tools/skin-studio/tests/title-folding.unit.test.cjs` | **10/10 PASS** |
| Clear、folding 模块、浏览器 E2E、单元测试、Studio JS 的 `node --check` | **PASS** |
| `python -m unittest discover -s tools/skin-studio/tests -v` | **59/59 PASS**；含规范包、Studio 源码编辑、预览/导出/重新导入检查 |
| `python tools/skin-studio/build_default_clear.py --verify` | **PASS**；9 files / 383,312 bytes |
| `skin_studio.py validate ...powercal-clear-default.pcskin --json` | **PASS**；v3 schema，9 files |
| `git diff --check` | **PASS** |
| 初次完整 Gradle：source boundary、JVM、Debug APK、lint | **BLOCKED**；当时指定的本地 SDK `E:\Project\Powerful-Calendar\.local\android-test\sdk` 缺少项目要求的 `platforms;android-36`，失败文本为 `Failed to find target with hash string 'android-36'`。后续完整重跑结果见下方 Follow-up |
| Android WebView / Android instrumentation / 模拟器 / 实体设备 | **NOT TESTED**；本轮没有安装、启动或清理任何设备/AVD |
| Native 与 Clear 同场景完整 parity | **NOT TESTED** |

可复核日志 SHA-256：Node 定向测试/语法 `887CD9B5D141480538DD67DF3D07BF5A7B4C63CE9977E296C3CEB969B94CEC75`；Skin Studio Python `F39A73B768F19407AB3AC50DC8B360E016E19E2347B59F523CC09A3E93D515D7`；包构建/校验 `ED8AC858453A61FE4C13C1764F2E20F3D63B51EECAC3B984EE2F9F0CF3944D0A`；Gradle 阻塞日志 `C213C4642AE46ACFDFA2E3BD6F8D073B71B9ADFFCEDF300074CC642662747B70`。路径均位于上述 `.local` 目录。

## 剩余风险和签核

- 本结论只证明 Clear 的独立 JavaScript 策略及 Edge Studio Mock 行为；Host/WebView 注入、Android WebView 字体和触摸、Native/Clear 等价及 OEM 表现没有设备证据。`S0-TL-05` 全 parity 不能签 PASS，分离/发布 cutover 不构成 GO。
- 项目最低 WebView 若不提供 `Intl.Segmenter`，当前 fallback 使用 Unicode code point 迭代，对 ZWJ/组合字符簇的截断边界仍需验证或加强。
- 后续完整 Gradle 门禁已通过；上面的 BLOCKED 是初次环境结果，不再是当前 Gradle/APK 构建阻塞。设备侧验收仍未完成。
- 一次扩展 Mock 检查曾把固定占位编辑字段误当作完整事件标题断言，随后改为核实真实指针打开 `#301` 与完整 accessible name，并在 Edge 重跑通过；因此报告只认定打开事件和无障碍标题保留，不宣称 Mock 表单展示了原始标题。

## Follow-up verification（2026-10-29）

在同一 Clear 标题折叠实现代码树（后续实现提交 `1d78353e0866c7bc19c6a555fb65cbc454aea1f2`，包含本 Issue 的 `c6b6f7d`）上补做自动化门禁复验；本 follow-up 没有改动 S0-TL-05 生产源码：

- 纯贪心策略 `node --test tools/skin-studio/tests/title-folding.unit.test.cjs`：**10/10 PASS**。日志 `.local/android-test/reports/separation-s0-timeline-drag/title-folding-followup-node-test.log`，SHA-256 `DFAF1719A43DE82014FB966D4392D5CE87C68A68E25FD37BAAF480995F1A06B4`。
- `python tools/skin-studio/build_default_clear.py --verify`：**PASS**，9 files / 384,912 bytes，Clear 包 SHA-256 `D0D136EEF118DC52CE767E8416EC1BD3B3134F75302BC64C3D3ED4FCA5192BF9`。校验日志 SHA-256 `61EC37F0FA3F862E62CDFCC60D0DBBA1880377A41EACF3878E82BC928BFA6283`。
- S0-TL-04 同一已提交代码树上的串行完整离线门禁 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --max-workers=1 --console=plain`：**BUILD SUCCESSFUL，619/619 JVM tests、84 suites、0 failures/errors/skips、88/88 tasks**。这为包含本 Issue 皮肤资源的完整源码树补上了之前缺少的 Gradle/JVM/APK/lint 证据，不替代设备 WebView 行为验收。日志 `.local/android-test/reports/separation-s0-timeline-drag/gradle-full-serial-final.log` SHA-256 `EE72E74A2015EC85E414795A684797285197E5B259BAE8CD3BC2699078AA2652`。Debug / AndroidTest APK SHA-256 分别为 `8E5940F2B73703E76CB0778E849487EB8F9D8D1CD96787BE207D27BF8EEC9986` / `A9570307F3BDAD8F4336A48AD0868B22AB3DEE90CEEF95B858603BB08AF720AD`。
- 本 follow-up 未执行 Android WebView/Instrumentation/实体设备测试。当前可用 SDK 没有独立 Emulator 程序和系统映像；共享 ADB daemon 启动失败，本轮没有对已知在线但归属不明的 `emulator-5582` 执行设备操作。Native/Clear 同场景 parity 仍 **NOT TESTED**；S0-TL-05 及分离 cutover 继续 **PARTIAL / NOT GO**。

## Follow-up — API 35 Android WebView 实测（2026-10-30）

本次补上 Clear 真实 Android WebView 的窄范围运行证据，未修改 `title-folding.js`、Clear 生产包或 Native 时间轴实现。此前缺少的是 Android WebView 的 DOM 文字测量、点击穿透和打开完整标题证据。

- 新增 `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/TimelineGreedyFoldingWebViewInstrumentedTest.kt`。它创建独立临时数据库和四条正式 ACTIVITY（12:00、12:01、14:00、14:01），运行实际 `PowerCalApp` + bundled Clear WebView，读取 DOM 实际标题矩形、事件时间/可视几何、按钮 accessible name 和可点击状态；点击折叠 B 后验证编辑器标题为 `Fold_B`，并检查没有正式事件修订或新 commit。测试数据与 SharedPreferences 均使用随机隔离命名空间并在 finally 中清理。
- AndroidTest 代码提交 `4146c0132d8c3690365f736e81eb02d623f7715d`（`test(分离): 验证 Clear 标题折叠 WebView 交互`）已推送至 `origin/codex/separation-s0-timeline-drag`。
- 环境：`PowerCal_API35_S0_20261030` / `emulator-5582`，API 35 / Android 15，WebView `124.0.6367.219`，显示 `1080×1920 @ 420dpi`；WebView 内容视口 `412×684 CSS px`。真机/OEM 未测。
- 实际测量矩形（WebView 视口 CSS px）：A `(224.095,343.905)—(261.911,358.762)`；B `(224.095,344.155)—(261.887,359.012)`；C `(224.095,373.905)—(261.923,388.762)`；D `(224.095,374.155)—(262.696,389.012)`。A/B 与 C/D 分别真实重叠；A/C 不重叠。轨迹为 `A visible → B folded against A → C visible compared with A → D folded against C`，证明隐藏 B 没有成为后续 lead。
- AndroidJUnitRunner **1/1 PASS**。事件按钮仍为 4 个，全部原 `ACTIVITY` 时间区间/事件序号/泳道保留；实测几何匹配开始和结束时间（1 分钟可视高度 0.25 CSS px），25 个时间刻度和当前时间线仍渲染。B 标题 `pointer-events:none`，屏幕点 `(226.095,346.155)` 命中 `BUTTON.tEvent` 的事件序号 2；打开后表单标题完整为 `Fold_B`，Store 快照与 commit 数未变。
- 截图：`.local/android-test/reports/separation-s0-tl05-api35-20261030/s0-tl05-greedy-folding-api35.png`，SHA-256 `F3BE4BA18DA3526A809AC7E82BF95E0EF41C1AFEB689E183C02B5F95C0FBDEFA`。原始 rectangles/hit trace：`timeline-greedy-folding-api35-logcat-trace.txt`，SHA-256 `C10ACE817B28F4DE2286C36F73417BFEB2EDCDDBB96F8569DACA93D7B0176EBC`；最终 AndroidJUnit log SHA-256 `1DB56E886D367D8A0BA0431D7935263C9E1B8340376B9F582890E57108B28E87`。Debug APK SHA-256 `195BA8FAE40F3B487C1345CEA242C9380A2B1088FBED38CB57E6418E340BD78A`；AndroidTest APK SHA-256 `D006B41DCE367C544A451E31C849BFCCC5292840C26651AB44928F814D262518`。
- 定向验证：贪心策略 **10/10 PASS**，log SHA-256 `AD35A2781A63D7E5F46E2C6031CFCA9D23D492C6D739B83E22F1E0AF9B948348`；Skin Studio Python **61/61 PASS**，log SHA-256 `098D5DE055178172078D260911572D9AC363BA387F77B451E2088600B89CC875`；Clear canonical verify **9 files / 394,086 bytes PASS**，package SHA-256 `8F7F5680BFD27976B2792AAB06D051BAF0F03F4A914636EA73E82E48431F8035`。
- Gradle `verifySkinSourceBoundary + testDebugUnitTest + assembleDebug + lintDebug`：BUILD SUCCESSFUL；另强制重跑 JVM **727/727、100 suites、0 failures/errors/skips**。门禁日志 SHA-256 `48394B8091FDFEA4FC0C6453BEF78469AB21EA0B3DE0A687B58BEA5358D1C08B`；强制 JVM 日志 SHA-256 `F73DB96764DECCAD40FAAC28DE1B4E5B267F575E53AA95570016F3EFA9E671C3`；lint `0 errors / 53 warnings`。AndroidTest 重编译命令 `:app:assembleDebugAndroidTest --offline --rerun-tasks --no-daemon --console=plain` 53/53 tasks PASS；Clear verify log SHA-256 `C5CA0FF65E4A70A32B8103595A22E5F31BBA174BD0D229B86CBB472523FC547B`。
- 保留失败证据：前几次 AVD 在 JUnit 返回前退出，日志没有测试结果；另一次初始夹具把 C/D 放在 13:00/13:01，44px 按钮热区覆盖 B 标题测试点，观测到点击落在序号 4。将 C/D 移至 14:00/14:01 后，不改变 A/B、C/D 文本碰撞关系，最终点击落在 B 自身序号 2 并通过。原诊断失败日志 `timeline-greedy-folding-instrumented-hittrace-r2.log` SHA-256 `C6214624D30CCA4B6CC46647DA86A88B4F814F99F41784147407D7B0909FEEC0`，没有把夹具失败伪报成产品缺陷或 PASS。

本 follow-up 只签核 Clear/API 35 上述子项。左右翼滚动/裁切与缩放重算由既有 Edge Studio Mock 测试覆盖；当时 Native 与 Clear 同场景实际 Android 渲染对照尚未运行，因此 `S0-TL-05` 总体及 Separation/Cutover 仍 **PARTIAL / HOLD**。

## Follow-up — API 35 Native/Clear 同夹具对照（2026-10-30）

在同一个 AndroidJUnitRunner 场景中，先让真实 `PowerCalApp` 走 Native Compose 时间轴，再激活 bundled Clear 并通过真实 Android WebView 重放同一 `PowerStore` 中的同四条正式 ACTIVITY。对照不依赖两个独立的仿造 fixture；Clear 的矩形/触摸断言仍在同一测试里执行。

- AndroidTest 增量提交 `1c59a79fc423e73ee287843880bec926a7063d90`（`test(分离): 验证 Native 与 Clear 标题 lead 一致`）已推送。它在 Native 真实 Compose 语义树中断言 A/C 标题存在、B/D 标题不存在，四个事件按钮都有点击语义且无障碍描述包含完整标题；随后显式断言 Clear 可见 lead 集等于 Native 可见 lead 集。
- API 35 Native measured title bounds（Android root px；viewport `1080×1920`，density `2.625`）：A `[601,918,711,981]`，C `[601,1028,711,1091]`；Native 可见集 `Fold_A/Fold_C`。同一 Store fixture 的 Clear `Range` rectangles 与比较轨迹见前一 follow-up。
- AndroidJUnitRunner **1/1 PASS**；直接日志 `.local/android-test/reports/separation-s0-tl05-api35-20261030/timeline-greedy-folding-native-parity-final1.log`，SHA-256 `C53564041F627B7F3A918187E634B81A9E3C3922CE4E0821F4D09ECB6540E7F0`。Native/Clear trace `.local/android-test/reports/separation-s0-tl05-api35-20261030/timeline-greedy-folding-native-parity-final-trace.txt`，SHA-256 `C46A4E6F1FE75EE18426EE369B56CE8EDF297FEEC38249417F3366FF60155345`。
- 最终 API 35 Clear 截图 SHA-256 `B78A0D44BEAE1D72C7238ED43F0E9967D7F131B8425569992F5A887F4BC8FBAB`；Debug APK SHA-256 `195BA8FAE40F3B487C1345CEA242C9380A2B1088FBED38CB57E6418E340BD78A`；含 Native/Clear 对照断言的 AndroidTest APK SHA-256 `BAB430859788F73130DA9AD20902F7477AFB4050537148BF1A99E84CD5158392`。强制 AndroidTest 重新编译 53/53 tasks PASS，日志 SHA-256 `D2FC24E0C14D89CAFC74B00B1CCEC94C7B7B575C7E570F60AFAA29D4419FD885`。

Native/Clear 同夹具的标题可见集和完整事件辅助语义现为 **PASS（限 API 35 本 fixture）**，不等同于全功能 parity。实际触摸设备/OEM、TalkBack，以及滚动/缩放/裁切在 Native 与 Clear 之间的完整端到端同场景对照仍 **NOT TESTED**；`S0-TL-05` 总体和 cutover 继续 **PARTIAL / HOLD**。

## Follow-up — API 35 Clear scroll/zoom reflow and Host state (2026-10-30)

本 follow-up 用真实触屏事件扩展同一正式事件 fixture，覆盖左右横滚、裁切、回滚、纵向滚动、16 小时缩放和 24 小时复位后的重排。测试未修改折叠算法或 Clear 生产资源。

- AndroidJUnitRunner 测试类 `dev.powerfulcalendar.ui.v2.TimelineGreedyFoldingWebViewInstrumentedTest` **2/2 PASS**，环境 API 35 / Android 15 / WebView `124.0.6367.219` / `emulator-5582`。新增单独的 `actualClearZoomControlsUpdateHostAndWebViewTimelineScale` 测试用真实触摸点击缩放和复位控件，验证 DOM click、桥接消息、Host timeline 投影及 WebView 标签分别从 24→16→24 小时。
- 同夹具折叠测试在真实缩放与滚动后都断言 lead 决策 `A visible → B folded against A → C visible compared with A → D folded against C`。横向滚动 8 CSS px 后测量矩形同步平移；横向裁切时四个标题均标记 `clipped`，四个正式事件按钮仍在；恢复滚动后矩形回到 baseline ±1 CSS px。纵向滚动 120 CSS px 后矩形同步上移，返回顶部后同样恢复。16 小时缩放与 24 小时复位后，标题 lead 及重置矩形稳定。
- 本次 API 35 实测的基线 Clear 标题矩形（WebView screen CSS px）：A `(224.0952,343.9048)—(261.9107,358.7619)`；B `(224.0952,344.1548)—(261.8869,359.0119)`；C `(224.0952,373.9048)—(261.9226,388.7619)`；D `(224.0952,374.1548)—(262.6964,389.0119)`。A/B 与 C/D 矩形分别相交，A/C 不相交。缩放后 C/D 变为约 `y=388.90/389.27`，而 lead 序列不变；复位回到 baseline。实际 Native/Clear baseline 可见 lead 仍均为 A/C，原生测量边界和同 fixture 结果记录在此前 follow-up 中。
- 缩放还暴露了独立的 `S0-TL-01` Host 投影问题：动作已修改 Compose `hours`，但同一同步发布回调读取旧 composition 捕获的 `camera`，导致 Host/WebView 暂时仍为 24 小时。以最小方式修复 `PowerCalApp.kt`，在发布快照时从 live `anchorMillis` 和夹限后的 `hours` 构建相机。独立原因、修复和测试证据记录在 [时间轴几何/缩放 Issue](PC-ISSUE-SEPARATION-CLEAR-TIMELINE-GEOMETRY.md)。
- 完整强制 JVM 回归 **727/727、100 suites、0 failures/errors/skips**；完整 Gradle source-boundary/JVM/Debug/lint gate、AndroidTest rebuild、Skin Studio Python **61/61**、纯折叠策略 **10/10**、Clear canonical verify（9 files / 394,086 bytes）均通过。Lint 为 **0 errors / 53 warnings**。

新增设备轨迹、runner 输出、APK 和日志 SHA-256 收录在关联 Issue 的 Follow-up 表中；完整可复核轨迹位于忽略目录 `.local/android-test/reports/separation-s0-tl05-api35-20261030/tl05-api35-final-run-trace.txt`，SHA-256 `4929252FA8A73B6F1D84D1158815AD0EF26CA1F0130FE6605A25EDCA7378E0AC`。AndroidTest runner log SHA-256 `438972A22823E7E17A1E16A469CD02D7A33C2C39B90433E99060B61217DC5758`。生产/测试提交 `2c4f9afe2d91c99fb016ec09d2702444be4c6f30` 已推送。

此证据只覆盖 API 35 Clear 实测重排和此前已通过的 Native/Clear baseline lead-set 子项。TalkBack、实体/OEM、完整跨皮肤滚动/缩放/裁切同场景 parity 仍 **NOT TESTED**；`S0-TL-05` 总体保持 **PARTIAL / HOLD**。
