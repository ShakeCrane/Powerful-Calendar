# PC-ISSUE-SEPARATION-CLEAR-TIMELINE-GEOMETRY — Clear 时间轴真实几何与缩放控制

- Date: 2026-10-29
- Status: **PARTIAL — 本 Issue 所列实现及窄范围回归通过；Android WebView 与 Native/Clear 配对验收仍 NOT TESTED**
- Worktree: `codex/separation-s0-inventory`
- Base commit: `6bf2a792a8edf9ee6825543f16751f038bfa4f89`
- Implementation, regression, matrix and Issue report commit: `4125e909865bcf0d30b8d158be932a5ab2b0eaa5` (`feat(分离): 修复 Clear 时间轴几何并接入缩放`)

## Scope and disposition

本 Issue 处理 Clear 时间轴分离盘点中的两个具体缺口：`S0-TL-01` 的垂直缩放/复位和左右独立泳道滚动，以及 `S0-TL-06` 的真实时间几何、点事件和纯日期 Deadline 表示。它记录的是一次可审计的实现切片，不等价于完整 Native/Skin 时间轴 parity，也不升级父项 `SEP-01`。

Studio 真实预览在 Google Chrome Headless 中通过 CDP 使用 **Mock Host**。这证明了该浏览器预览中的 HTML/CSS/JS 行为，不证明 Android WebView、Native/Clear 同场景等价、第三方 OEM 或实体设备行为。`S0-TL-01` 与 `S0-TL-06` 因而仅有窄范围实现证据，完整 parity 状态继续为 **NOT TESTED**。

`S0-TL-05` 的实测标题矩形贪心折叠没有在本 Issue 中实现；它仍是独立待办。`S0-TL-04` 拖动改期也仍是 Bridge 能力缺口。

## Reproduced defect and root cause

对 24 小时视口中的 10:00–10:01 Activity 做 Chrome 几何采样，在最终 CSS 调整前的可视化快照中，其视觉事件矩形为 **84 × 6 CSS px**，按钮为 84 × 44 px。真实时间高度按 renderer 使用的 `clientHeight=468` 计算约为 `468 / 1440 = 0.325 CSS px`。当前差异与视觉层上下各 3 px padding 相符；本次工作版本将纵向 padding 改为 0 后，视觉矩形下降到接近真实时间高度。

根因分两层记录：被审计的已提交基线在 `app.js` 中曾将短事件视觉高度设置为至少可视区的 4%，并对无结束时间项合成 `start + 4`，两者都违背真实时间几何；本次重构移除了这两条替代规则。其后的一分钟 pre-fix Chrome 采样是在本轮可变工作树中、最终 padding 调整前运行，并不是一个保存了完整源码快照的不可变 Git 基线。采样日志没有记录当时 computed style；CSS 改动还涉及盒模型调整，因此现存证据能证明“几何/CSS 修复后高度从 6 px 降到 0.3125 px”，但不能单独隔离证明纵向 padding 是全部 6 px 的唯一来源。Issue 将 padding 记为有直接代码差异支持的原因，不夸大成排他的因果证明。

修复后渲染使用可见区裁切后的真实开始/结束时间计算视觉矩形；视觉层取消纵向 padding 和虚构最小高度，可点击按钮仍独立保持 44 px 高。Chrome 中 1 分钟事件 CSS 目标高度为 **0.325 px**，浏览器最终量得 **0.3125 px**（与目标差约 0.0123 px，落在回归断言 0.02 px 最小容差内；按 surface `getBoundingClientRect()` 的 467.65625 px 计算期望约 0.3248 px）。独立按钮仍为 **84 × 44 px**。5 小时事件按 `clientHeight=468` 目标为 97.5 px，实测 97.5 px；按表面小数矩形高度计算为 97.43 px。跨越视口的长事件可视部分为 468 px，容器小数矩形高度为 467.65625 px，差异来自浏览器 client/bounding-box 子像素取整。

旧 Clear 时间轴还没有独立水平滚动翼、原生等价缩放动作或日期级 Deadline 呈现。旧实现将 ALARM / Deadline 与 Activity 共用矩形生成路径，且日期级截止被错误地当作时间点处理。本次改为：左右各有独立滚动泳道；Host 提供受限的 zoom-in/out 与 24 小时复位命令；ALARM 和精确时刻 Deadline 用锚定点线，纯日期 Deadline 放在独立日期条。正式事件时间字段和持久化记录没有被 UI 几何调整改写。

## Changes

- `WebSkinHost.kt` 增加封闭 schema 的 `zoomTimeline(direction=in|out)` 与 `resetTimelineZoom` 动作解码；拒绝未知方向、错误类型及额外字段。
- `PowerCalApp.kt` 在 Timeline 页面处理上述动作，沿用 Native 的 1.5 缩放步长和 `TimelineCoordinate.clampHours` 范围；状态更新不写 Store，现有视口中心时刻保持不变。
- Clear `app.js` 重构时间轴绘制：Activity 使用真实区间并按当前可视面裁切，点事件依真实时刻定位，日期级 Deadline 使用独立日期条；点击按钮目标与视觉矩形分离，事件名称和时间保留在 accessible name/title 中。左右翼各自保留水平滚动位置并按实际 lane 数展开，不合并事件。
- Clear `index.html` / `style.css` 增加缩放/复位控件、独立滚动翼、lane 容器、点事件/日期条样式，并移除短事件视觉块的最小高度和上下 padding。
- Skin Studio 的 timeline-geometry 场景扩展到 25 个事件：覆盖两侧各 12 条泳道、超短与长 Activity、ALARM、精确时刻/日期级 Deadline；浏览器回归检查点击命中、名称、滚动及缩放边界。
- `powercal-clear-default.pcskin` 由规范 Clear 源重新构建，确认 APK 模板和 Studio 源一致。
- 增加 Host 动作解码与 Compose 状态回归，覆盖缩放上下界、中心时刻保留、无持久化写入和非法消息拒绝。

## Verification

所有通过结果均对应下表具体环境和范围；没有将 Chrome Mock 结果称为 Android 设备结果。

Chrome 使用 Studio Mock 的 CDP 回归（Studio server 和 Chrome CDP endpoint 已运行）：

```powershell
$env:POWERCAL_EVIDENCE_DIR = '.local/android-test/reports/separation-s0-timeline-followup'
node .local/android-test/reports/separation-s0-timeline-followup/cdp_timeline_e2e.cjs
```

| Check | Result | Evidence |
| --- | --- | --- |
| 最终 CSS 调整前的 Chrome 几何快照（本轮中间工作树） | **FAIL reproduced**：1 分钟视觉矩形 84 × 6 px；button 84 × 44 px。它是样式调整前证据，不是 immutable `6bf2a79` APK/source replay。 | `.local/android-test/reports/separation-s0-timeline-followup/chrome-e2e-before-20261029-065819.log` SHA-256 `47897A96B30957683E923218C472994897AA3F865CFC51CEDF03F34D7A62E16A`；`timeline-geometry-before-fix.png` |
| 修复后 Google Chrome Headless Studio Mock，1460×960 CSS px | **PASS**：25 个事件；25 个独立点击命中；左右各 12 lanes 且可独立滚动；24h→16h；缩放夹限 0.25h/48h；复位 24h；错误 payload 被 Mock 拒绝且不改视口。1 分钟视觉高度 0.3125 px、按钮高 44 px；5 小时 97.5 px；可见区长事件 468 px；点线 Y=507.25 px；日期 Deadline 进入日期条 | `.local/android-test/reports/separation-s0-timeline-followup/chrome-e2e-tight-20261029-070617.log` SHA-256 `27B29B44482553895699C75D598CDAD73E6C13F0538A9838CF22C8E0E32C527E`；最终图 `timeline-geometry-chrome.png`；矩形 JSON `timeline-geometry-measurements.json` |
| Skin Studio Python | **59/59 PASS**：`python -m unittest discover -s tools/skin-studio/tests -v` | `.local/android-test/reports/separation-s0-timeline-followup/studio-python-final.log` SHA-256 `9D82BAE7DBB1646ADEB584FCB67E7581D4A5E88412F520AEA5513BEC6AB96D27` |
| Clear 规范包构建 / 验证 | **PASS**：8 files，380,049 bytes；package SHA-256 `0790173E60E4F991EAA2072B43DF04039AFC7AB80416770A285B96CD0B3118CD` | `clear-build-final.log` 与 `clear-verify-final.log` SHA-256 均为 `FE2E2931CFBF23201E106EA3240832AFF9500B81BC3D7540AEF2034597FF029D` |
| Gradle 完整离线门禁 | **88/88 tasks PASS**：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain`；JVM **613 tests / 84 XML suites / 0 failures / 0 errors / 0 skipped** | `full-offline-gate-final.log` SHA-256 `139B37AAC1C5AD038EA8AF78EF37336FA305E0D5F8CB46C90F18BE9ADB102BE9` |
| Lint | **PASS**：0 Error / 0 Fatal，52 warnings | Gradle gate 生成的 `app/build/reports/lint-results-debug.xml`；warning 为仓库现有及依赖/弃用类提示，未在本 Issue 中扩展处理 |
| JS syntax | **PASS**：对 Clear `app.js`、Studio `v3-studio.js`、`e2e_v3_browser.cjs` 执行 `node --check`，均退出 0 | 命令结果在本轮执行记录中 |
| Debug APK | **PASS built**；SHA-256 `E0C795C7D734479E4F5D0C9B403A28733F1C49646917667E2228F853B1A50809` | `app/build/outputs/apk/debug/app-debug.apk` |
| AndroidTest APK | **PASS built, NOT RUN**；SHA-256 `82B1EFB2ACF5B4ED2677CEEFE50AB9E4F787E6A44439420E0C65372BE64EC755` | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |

## Before/after measurements and screenshots

| Element | Before | After | Interpretation |
| --- | --- | --- | --- |
| 10:00–10:01 Activity visual rectangle | Final CSS adjustment 前的中间工作树快照：84 × 6 px | 84 × 0.3125 px measured; CSS target 0.325 px | 44 px transparent button hit area retained separately; before snapshot is not an immutable source baseline |
| 10:00–15:00 Activity | The committed baseline substituted minimum geometry; intermediate one-minute capture was not a 5h case | 84 × 97.5 px | Matches 468px `clientHeight`/24h scale for 5h; `getBoundingClientRect()` basis yields ≈97.43px |
| 16:00–next-day 16:00 Activity across 24h viewport | The committed baseline used synthetic/minimum geometry | Visual height 468 px; surface rectangle height 467.65625 px | Represents only visible viewport portion; difference is browser client/bounds subpixel rounding |
| 14:00 point marker | not independently projected | horizontal marker at Y=507.25 px | Position matches actual visible interval projection |

Screenshots and raw geometry are under `.local/android-test/reports/separation-s0-timeline-followup/` (ignored, not committed):

- Before: `timeline-geometry-before-fix.png` (SHA-256 `37E5A2FBABCA4D0811A33FF95821749606D6D60184D910D9D1A0375A862430B6`)
- After: `timeline-geometry-chrome.png` (SHA-256 `DBEE31C8D900662B5BFA7B1AD4DB0561D5520E2F6AECCF7E566D66D9E64667FB`)
- Measured DOM rectangles: `timeline-geometry-measurements.json` (SHA-256 `3FA064A4CA6D612EFD59BCBCCB9ABDD9500F13C1A6FFB90EE15E6FC0041456A3`)

## Remaining evidence and risks

- The current shell has no `adb` or `emulator` executable on `PATH`; this run did not start or alter any AVD/device. **Android WebView instrumentation and physical/OEM device behavior are NOT TESTED.**
- Chrome uses Studio's non-writing Mock Host. The run does not prove production Host-to-WebView delivery, persistence behavior on a device, touch gesture arbitration, or visual equality against Native at matched screen sizes.
- The AndroidTest APK was assembled, but instrumentation was not run. `613/613` JVM tests and a successful APK build do not substitute for Android WebView verification.
- Greedy rendered-title collision folding (`S0-TL-05`), title lead/hide sequences, accessibility after folded titles, and hit-testing with overlapping titles remain open and require their own measured-rectangle regression. This Issue does not silently claim them.
- `S0-TL-04` drag-to-move and broad timeline Native/Skin parity remain open. `S0-TL-01`, `S0-TL-06`, and parent `SEP-01` remain **NOT TESTED** as complete parity rows.
- No event database, user data, AVD data, credential, or unrelated worktree asset was intentionally touched. Browser/Studio tests use only the non-writing preview fixture.

## Files changed

Production and test files:

- `app/src/main/assets/powercal-clear-default.pcskin`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinHost.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/PowerCalCasualSuggestionComposeTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinHostProjectionTest.kt`
- `docs/V3_HOST_BRIDGE_EXTENSION.md`
- `tools/skin-studio/default-clear/source/web/app.js`
- `tools/skin-studio/default-clear/source/web/index.html`
- `tools/skin-studio/default-clear/source/web/style.css`
- `tools/skin-studio/tests/e2e_v3_browser.cjs`
- `tools/skin-studio/v3-studio.js`

This report, the corresponding two child rows in `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`, and this Issue entry in `docs/agent-handoff/PROGRESS.md` document the disposition. Runtime screenshots/logs stay in the ignored `.local` evidence directory; no generated runtime artifact is staged.

## Next action

Continue with the independent `S0-TL-05` greedy title folding task. Preserve its key constraints: use actual rendered text rectangles in common screen coordinates; compare each candidate only with the current visible lead; hide titles only; keep event geometry, tap targets and accessibility semantics. After implementation, run a separate Android WebView gate when a safe emulator is available and retain the complete Native/Skin parity rows as NOT TESTED until matched replay evidence exists.
