# PC-ISSUE-SEPARATION-CLEAR-TIMELINE-DRAG — Clear 时间轴拖动改期

- 日期：2026-10-29
- 状态：**PARTIAL — 已有 API 35 Clear WebView 拖动到 Bridge/Store 的窄范围实测；完整 Native/Clear 配对与设备矩阵仍 NOT TESTED**
- Worktree：`codex/separation-s0-timeline-drag`
- 基线：`057b39e5d2fd85e9725e7faffbba7ff0ea51c37c`
- 实现、测试与独立报告提交 SHA：`1d78353e0866c7bc19c6a555fb65cbc454aea1f2`

## 范围与合同

本 Issue 处理重建验收项 `S0-TL-04`：在 Clear 时间轴上拖动一个非重复定时事件，按当前时间轴几何暂存一张替换草稿；重复事件不支持拖动。这里的“移动”不直接修改或发布正式事件，用户仍在现有 Deck 中审核候选并明确提交。

允许的目标类型为具有实际时间锚点的非重复 `ALARM`、`ACTIVITY` 和精确时刻 `DEADLINE`。重复事件、`MEMO`、纯日期 Deadline 不允许通过该命令移动。普通点击仍打开编辑器；长按后拖动才触发移动请求。

## 根因

已审计的 Clear 时间轴为事件提供按钮和打开/查看交互，但没有拖动手势，也没有可让皮肤请求移动候选的 Host Bridge 命令。若让皮肤自行写入或决定目标时区、吸附间隔、正式编辑 scope，会绕过现有 Host 负责的权限边界和 Store 版本检查。因此缺口位于 Clear gesture 与 Host 能力之间，而不是事件渲染或事件时间几何。

## 修复

- `WebSkinEditBridge.kt` 增加封闭 schema 命令 `timeline.move(eventSeq, expectedRevision, targetMillis)`。协议只接受整数毫秒；拒绝缺字段、浮点、字符串、未知字段和非法版本号。
- `PowerCalApp.kt` 只在时间轴页为命令提供 Host 构造的移动上下文：当前相机、当前 zone，以及当前投影中的 `eventSeq → revision` 集合。皮肤不能指定 snap 间隔或时区。
- Host 服务重新读取正式记录和 revision，核对该 revision 仍在当前投影中；校验编辑器/Deck/Host 锁状态；拒绝重复、Memo 和日期级 Deadline；使用 `TimelineCoordinate.snap` 和当前相机 scale 计算吸附时刻，并限制支持年份为 1900–2200。吸附后未移动则返回 `unchanged`，不创建草稿。
- 有效移动经 `movedDraftForm` 保留原始事件语义和 Activity 持续时间，创建一张 `DraftOperation.REPLACE` 候选并打开现有 Deck。正式事件、正式 identity 和 commit 保持不变；用户仍通过既有审阅路径发布。
- `PowerStore.newDraft` 增加可选 `expectedRevision`，在创建/覆盖替换草稿的同一事务内检查来源版本。若 Host 读取后事件 revision 已改变，抛出明确的 stale 结果，不留下候选。原生时间轴拖动也传入其显示 revision，防止同样的读后写竞态。
- request ID 重放返回原响应；用户关闭 Deck 后再次拖动同一事件时复用既有替换草稿，而不是增加第二张队列记录。
- Clear `app.js` 保留 click-to-edit；mouse/touch 长按 500ms 后允许纵向拖动。阈值前移动取消长按；拖动期间视觉跟手，Host 依据当前相机和真实 surface 高度换算、吸附。重复事件触摸拖动得到可访问状态提示且不打开草稿。
- Studio `timeline-drag` 情景增加非写入 Mock 命令响应、普通事件和重复事件，以便在预览中测试鼠标及触摸手势。
- `powercal-clear-default.pcskin` 与 AndroidTest delivery fixture 已从规范 Clear/Studio 源重新生成，避免包内资源落后于可编辑源码。

## 验证结果

| 验收 | 结果 | 证据 |
| --- | --- | --- |
| Wire schema、负毫秒、无效类型/额外字段拒绝 | PASS | `WebSkinEditBridgeTest.timelineMoveWireUsesClosedSchemaAndSignedIntegerTarget` |
| 有效移动生成一张 REPLACE 草稿、保留 Activity 时长、正式事件不变 | PASS | `WebSkinEditBridgeTest.timelineMoveStagesOneRevisionGuardedReplacementAndPreservesDuration` |
| no-op 不写入；request replay 与二次移动复用同一草稿；identity、commit、seq 不增长 | PASS | 同上与 Store 计数断言 |
| 重复事件、Memo、日期级 Deadline、无时间轴上下文拒绝；投影缺席或 revision 过期返回 stale | PASS | `timelineMoveRefusesRepeatingAndNonTimelineItemsWithoutWrites`、`timelineMoveRequiresOwnerProjectionForTheDisplayedRevision`、`timelineMoveRejectsStaleOwnerRevisionWithoutStaging` |
| Store 事务原子拦截过期来源 revision | PASS | `PowerStoreTest.replacementDraftCanAtomicallyRequireTheDisplayedSourceRevision` |
| Focused Kotlin suites | **116/116 PASS，2 suites** | `gradle-focused-final.log` SHA-256 `FACC2D56AE4AFF553628B585550E8CBEAB6CD4E8B35C44B57D8BAB1C762D8B85`；JUnit XML 在 `.local/android-test/reports/separation-s0-timeline-drag/focused-junit/` |
| Chrome Headless Studio Mock（Chrome 156.0.8078.48，Studio 1460×960 viewport） | **3/3 PASS**：普通点击仅打开编辑；mouse 长按拖动将草稿时刻从 `09:00` 改至 `12:00`；repeat touch 拒绝且没有草稿。状态播报“正式事件尚未改变”。正式事件仍为 `1792630800000`。 | `.local/android-test/reports/separation-s0-timeline-drag/timeline-drag-browser-cdp.json` SHA-256 `7961B2758AF84E8E57AB48A5027975815966A6662B1DA0DAB24DC46649DB46DB`；CDP 日志 SHA-256 `FDCB5E28D9999D064A90A74D9FB87CCFCB740D9B2B5091ED9D8EAACFD8E5D788` |
| 可视证据 | PASS | [拖动后待审核草稿截图](../../.local/android-test/reports/separation-s0-timeline-drag/timeline-drag-staged.png) SHA-256 `7666D6D19E5A0D3C5258AA5677F7B3EAA57150CBBEC109715E92B694A0DA496C`；截图可见 Draft Deck 的开始/结束时刻为 12:00/13:00，提示正式事项尚未改变 |
| Studio Python、语法、包一致性 | PASS | Skin Studio Python **59/59**，`node --check` 对 `app.js`、`edit.js`、`v3-studio.js`、`e2e_v3_browser.cjs` **4/4**；规范 Clear 包 9 files / 384,912 bytes，SHA-256 `D0D136EEF118DC52CE767E8416EC1BD3B3134F75302BC64C3D3ED4FCA5192BF9`；测试 fixture 包 SHA-256 `D9B34053464D9CFB959E6D9EE8827A054E300FE580DC52DB614898A8A6321868`；Python 日志 SHA-256 `5B6E0E072950A1AC806051816B51BD82F21A13014376634B80BEED5C7E80C8F8` |
| Source boundary、APK 构建 | PASS | 串行完整门禁中 `:app:verifySkinSourceBoundary`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest` 成功。Debug APK SHA-256 `8E5940F2B73703E76CB0778E849487EB8F9D8D1CD96787BE207D27BF8EEC9986`；AndroidTest APK SHA-256 `A9570307F3BDAD8F4336A48AD0868B22AB3DEE90CEEF95B858603BB08AF720AD` |
| Lint | PASS | 串行完整门禁完成 `:app:lintDebug`；**0 errors / 52 warnings**。lint XML SHA-256 `B118045B0FC0017BC4EF4DFECC6531AC98C29BC2411B3E33573FB4FDD51C1362` |
| 标准默认 worker 完整离线 Gradle JVM 回归 | **FAIL，未隐藏** | 本分支两次全量：619 tests / 1 failure，失败为 `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` 在 60 秒内未观察到 Memo 投影。独立重跑此用例 PASS；干净父基线 `057b39e` 的全量为 613 tests / 同一测试同一失败。父基线与单用例证据确认该超时在本功能前已存在。第一次失败日志 SHA-256 `A7A1A4344D4BFBE25A8B62A3826768EB977DDB3EE0B41B7480ECD23A6F2943E7`；第二次 SHA-256 `A839981EEA3B5CC7BA3B35C567B9AF5AFE5792063D71E7F1E41020C4F2C4ADBC`；父基线日志 SHA-256 `F3E6DD815899CFE55896F676E0B1E07F7BBF236D71D86822DBBF4DA5708495C6` |
| 串行完整离线 Gradle 门禁 | **PASS** | 增加 `--max-workers=1` 后完整门禁 **BUILD SUCCESSFUL**：**619/619 tests、84 suites、0 failures/errors/skips、88/88 tasks**；source boundary、Debug/AndroidTest APK 和 lint 全部通过，lint 0 errors / 52 warnings。日志 SHA-256 `EE72E74A2015EC85E414795A684797285197E5B259BAE8CD3BC2699078AA2652`；84 份 JUnit XML 保存于 `.local/android-test/reports/separation-s0-timeline-drag/full-junit/` |
| Android WebView / AVD / 实体设备 | **NOT TESTED** | 检查时 `emulator-5582` 已在线，所有权/运行任务未知；本轮未对它安装、卸载、启动应用或执行 instrumentation。Chrome Studio 使用 Mock Host，不能代替生产 WebView Bridge 传递。实体设备由用户负责。 |

## 真实坐标轨迹与截图

Chrome Studio Mock 的真实 pointer 轨迹：

```text
event #501 / revision 4: normal click → editor.open only
long press 500ms → pointer move +60 CSS px → timeline.move
Host camera 24h / surface conversion → 09:00 → snapped 12:00
Bridge result: staged; DraftForm interval 12:00–13:00
formal event startMillis before/after: 1792630800000 (unchanged)
event #502 recurring touch drag → unsupported; no draft opened
```

浏览器截图保存在忽略路径 `.local/android-test/reports/separation-s0-timeline-drag/timeline-drag-staged.png`。截图 Hash 已在上表记录；JSON trace 包含浏览器版本、viewport、前后时间、live region 和正式事件时间。未把这份 Mock 截图标记成 Android WebView 证据。

## 改动文件

- `app/src/main/java/dev/powerfulcalendar/core/PowerStore.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridge.kt`
- `app/src/test/java/dev/powerfulcalendar/core/PowerStoreTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridgeTest.kt`
- `app/src/main/assets/powercal-clear-default.pcskin`
- `app/src/androidTest/assets/skin-delivery-fixture.pcskin`
- `tools/skin-studio/default-clear/source/web/app.js`
- `tools/skin-studio/default-clear/source/web/edit.js`
- `tools/skin-studio/default-clear/source/web/index.html`
- `tools/skin-studio/default-clear/source/web/style.css`
- `tools/skin-studio/tests/e2e_v3_browser.cjs`
- `tools/skin-studio/v3-studio.js`
- `docs/V3_HOST_BRIDGE_EXTENSION.md`
- `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`
- `docs/agent-handoff/PROGRESS.md`
- `docs/issues/PC-ISSUE-SEPARATION-CLEAR-TIMELINE-DRAG.md`

## 未完成范围与后续

1. Android WebView 中从 Clear pointer/touch 到生产 Host `timeline.move`、Draft Deck 展示和用户发布的端到端链路仍需在独立 AVD 验证；在线设备 `emulator-5582` 本轮未触碰。
2. Native/Clear 在相同事件、相机、zone 和目标位置上的配对结果未测；本 Issue 仅证明 Host 复用 Native snap 规则以及 Studio Mock 手势结果。
3. 默认 worker 配置下，全量 JVM 门禁会在父基线已有 Memo 分页 Compose 用例超时；串行 full gate 已 619/619 通过。应把默认并行路径的不稳定作为独立测试稳定性缺口继续处理，不能归因或折算成 S0-TL-04 的 PASS/FAIL。
4. 仓库 Puppeteer E2E harness 本轮未运行，因为该 worktree 没有 Puppeteer 依赖；Chrome/CDP 三条动作由本地 `.local` focused CDP harness 实际驱动并留有 JSON/截图证据。已提交的 E2E 源文件已执行 `node --check`。
5. S0-TL-04 只标为 **PARTIAL**；不代表时间轴完整 parity、SEP-01 完成或 Skin cutover GO。

## Follow-up — API 35 touch-to-Bridge、缩放相机与复位点击（2026-10-30）

- 真实 API 35 WebView 回放确认了两处生产缺陷。第一，Timeline 页面同步发布已读取最新 `anchorMillis`/`hours`，但 `WebSkinBridgeRuntime.onEdit` 的闭包仍使用上一轮 composition 捕获的 `camera`；16 小时视图的 Skin 请求目标为 `2026-10-30 11:40`，Host 却按旧 24 小时相机以 15 分钟步长存成 `11:45`。现在命令处理时用实时 `anchorMillis` 和限幅后的 `hours` 重建 `Camera`，同一个实时相机用于可见事件校验与吸附。第二，事件按钮允许 WebView 原生纵向手势仲裁抢走长按；事件目标改为 `touch-action: pan-x`，让长按拖动的纵向触点流交给 JS，阈值前的触摸移动取消拖动并抑制随后的合成点击。缩放/复位按钮设为 `touch-action: manipulation`，避免连续触摸时双击判定吞掉复位点击。
- API 35 / Android 15 隔离 AVD `PowerCal_Sep01_API35`、Clear 生产 WebView：`TimelineDragWebViewInstrumentedTest` **1/1 PASS**。真实普通点击仍打开编辑器且零写入；阈值前纵向移动无编辑/写入；重复事件拖动通过 Host 返回 `unsupported`、不产生草稿；非重复 Activity 长按触摸产生新 `timeline.move` Bridge request，Host 打开现有 Deck 并暂存恰好一张 `REPLACE` 草稿，开始/结束时间为 `11:40–12:40`，正式记录、身份数和 commit 数保持不变。触摸轨迹与 WebMessagePort 命令记录在 `.local/android-test/reports/sep01-tl04-runtime-20261030/touch-drag-api35/`；JUnit `test-result.textproto` SHA-256 `B8F69483CF654BA8B35FD3BAC84958EC8BC2BE2136F2BF81FAE777631D14522A`，完整 Gradle 定向日志 SHA-256 `498C47BA4AA6F7AE96FFD0457D3D68D6BB7647E86C6DD09C23CE1FF4AD0D7490`。
- `TimelineGreedyFoldingWebViewInstrumentedTest` 在同一 AVD **2/2 PASS**；其中放大后立即复位的真实触摸用例再连续运行 **3/3 PASS**。第三次 JUnit 结果 SHA-256 `3374EDEDEF95485F5300D180BCB730195D3B71D592F0D275CCC14E3EBC553CDE`，触摸/控件轨迹 SHA-256 `60ABD558BB37D4A5EED3AEEF975ABF17BF871E2D0C62DFA7D5543A6EC71EC086`，结果副本位于 `.local/android-test/reports/sep01-tl04-runtime-20261030/reset-touch-run-3/`。较早一次 AVD 在 UTP 清理时断连，未计作 PASS；使用同一隔离 AVD 数据重新启动后完成了上述 PASS，没有 wipe 或清除设备数据。
- 完整离线门禁 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --max-workers=1` **BUILD SUCCESSFUL**：JVM **727/727、100 suites、0 failures/errors/skips**；Lint **0 errors / 53 warnings**。Skin Studio Python **61/61 PASS**，Clear 包验证 **9 files / 394,120 bytes PASS**，APK 中皮肤资源 SHA-256 与 canonical 源/checked-in 包一致：`2595704798da12d7309ea165e90e190b56f02640ddd10254b1d4f98cb55d9b81`；Debug APK SHA-256 `1A1E9D86C978B5247D633C86785E673F8CFE73C3607B9F743B8FFB16F50F0BB1`。完整 JVM XML 和 Lint 报告副本在同一 `.local/android-test/reports/sep01-tl04-runtime-20261030/` 证据目录。
- Runtime、Clear source、内嵌 `.pcskin` 与 Android 回归提交为 `10da1cc6683453cec5192b5a8e9207f965d02b55`（`fix(分离): 修复Clear时间轴拖动与复位触控`）。这仅签核长按到 Bridge/Host 暂存与复位触摸的 API 35 窄范围；真实设备/OEM、TalkBack、Native/Clear 同事件回放及整行 SEP-01 parity 仍 **NOT TESTED**，不改变 Native 回退或默认皮肤。

## Integrated-mainline reproduction (2026-10-31, evidence only)

The fix commit above is an ancestor of the current integration branch (`142a11607fa62c4979638064f5a549fb6e6f4ed3`). I reran the production Clear WebView tests on the separate QA AVD `codex-clear-api35` (Android 15 / API 35, `emulator-5568`) without Gradle UTP or package teardown; no runtime source changed.

- `TimelineDragWebViewInstrumentedTest#touchscreenTapOpensEditorAndLongPressStagesOnlyOneReplacementDraft`: **1/1 PASS**. An ordinary tap opened the editor. A repeat-event drag reached Host and returned `unsupported` without a draft. A non-repeating Activity long-press sent `timeline.move`; Host used the live 16-hour camera, snapped to **11:40**, and staged exactly one `REPLACE` draft for **11:40–12:40**. The formal event, identity count and commit count stayed unchanged. Full touch/Bridge trace: `.local/android-test/reports/sep01-timeline-drag-20261031/timeline-drag-runtime-trace.log` (SHA-256 `2EA329FD38DDC28756170CEF138D92733741BEADADED4A6B41E58521789FCABF`); direct test log SHA-256 `CD1BA01A6C3DC124E0F7C093DEFB8922467B8FBEDD423CB82DC0E9FADCBC3A35`.
- `TimelineGreedyFoldingWebViewInstrumentedTest#actualClearZoomControlsUpdateHostAndWebViewTimelineScale`: **11/11 PASS** on the same AVD (one standalone run plus ten sequential repetitions). Each run used Android touch input on Clear's zoom-in and 24h reset controls, observed Host scale **16h → 24h**, and checked the WebView label returned to 24 hours. The trace contains 11 `S0_TL05_ZOOM_API35` records (SHA-256 `61621D13472B230DAA26B17E45E88B2E6BB0854C563DF46B2333A012781642FE`); repeated-run log SHA-256 `1B781A5FBB75636347F72AF1D3C26FA9EF40B70E4ED9CF9E9129E74986C5D60D`.
- Evidence files are under `.local/android-test/reports/sep01-timeline-drag-20261031/`. Isolated `pc-s0-tl04-*` and `pc-s0-zoom-*` database and preference checks found no residue.

This reproduces the touch-to-Bridge and reset-click fixes on the integrated mainline. S0-TL-04 remains **PARTIAL** until matched Native/Clear input replay; SEP-01 and timeline parity remain **INCOMPLETE** pending full geometry/scroll/accessibility and physical/OEM checks. No default skin switch or Native fallback change is implied.
