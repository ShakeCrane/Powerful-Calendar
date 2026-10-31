# PC-ISSUE-SEPARATION-CLEAR-TIMELINE-GEOMETRY — Clear 时间轴真实几何与缩放控制

- Date: 2026-10-29; API 35 Host/WebView zoom follow-up: 2026-10-30
- Status: **PARTIAL — API 35 Native/Clear camera, pan-outcome and independent-wing slices TESTED; full Native/Clear parity remains NOT TESTED**
- Worktree: `codex/separation-s0-inventory`
- Base commit: `6bf2a792a8edf9ee6825543f16751f038bfa4f89`
- Implementation, regression, matrix and Issue report commit: `4125e909865bcf0d30b8d158be932a5ab2b0eaa5` (`feat(分离): 修复 Clear 时间轴几何并接入缩放`)
- Follow-up Host state fix and AndroidTest commit: `2c4f9afe2d91c99fb016ec09d2702444be4c6f30` (`fix(分离): 发布 Clear 时间轴缩放状态并补充实测`)

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

The API 35 follow-up below verifies the actual Clear WebView zoom/reset touch path and Host projection. Continue with remaining `S0-TL-01` / `S0-TL-06` parity gaps; preserve complete Native/Skin parity as NOT TESTED until the corresponding matched replay evidence exists.

## Follow-up — API 35 zoom touch and Host projection (2026-10-30)

The first real Android WebView zoom run exposed a Host state publication defect that was not visible in the Studio Mock. `WebSkinBridgeRuntime` handled the decoded zoom action and updated Compose `hours` from 24 to 16, but synchronously published a timeline projection built with the `camera` value captured by the previous composition. Until a later recomposition, the Host projection and WebView continued to report 24 hours, so zoom-triggered title reflow could not be trusted.

`PowerCalApp.kt` now constructs the published camera from the live `anchorMillis` and clamped `hours` state at publication time. This keeps the focal instant and existing zoom bounds while ensuring the immediate Host snapshot reflects the action before recomposition. Reset publishes 24 hours through the same path. No Store or event geometry is changed by zoom.

### Device verification

- Environment: `PowerCal_API35_S0_20261030`, serial `emulator-5582`, Android 15 / API 35, WebView `124.0.6367.219`, display `1080×1920 @ 420dpi`. Existing app/test APKs were updated with `adb install -r`; app data was not cleared.
- Instrumentation class `dev.powerfulcalendar.ui.v2.TimelineGreedyFoldingWebViewInstrumentedTest`: **2/2 PASS**. `actualClearZoomControlsUpdateHostAndWebViewTimelineScale` tapped the actual Clear zoom and reset buttons; the WebView click and versioned bridge post were observed, Host projection changed to 16 hours, the visible label changed to `16 小时`, and the reset returned both Host and WebView to 24 hours. The title-folding test separately exercised the same user touch path while checking reflow.
- Reflow after horizontal scroll, horizontal clipping/restoration, 120px vertical scroll/restoration, 16-hour zoom, and 24-hour reset retained the deterministic `A visible → B folded against A → C visible compared with A → D folded against C` lead sequence. Restored rectangles matched baseline within 1 CSS px. Every stage emitted measured and screen-coordinate rectangles; evidence is in the ignored local trace below.
- The current-time marker visibility assertion was evaluated against the Host visible time bounds after zoom and reset. At the test clock, the 16-hour centered range excludes current time, so marker absence is expected; the 24-hour reset puts it back in the visible range.
- Unit rerun: **727/727 tests, 100 XML suites, 0 failures/errors/skips**. Full source-boundary / unit / Debug APK / lint Gradle gate: **BUILD SUCCESSFUL**, lint **0 errors / 53 warnings**. AndroidTest rebuild: **53/53 tasks PASS**. Skin Studio Python: **61/61 PASS**. Pure title strategy: **10/10 PASS**. Canonical Clear verification: **9 files / 394,086 bytes PASS**.

### Trace and build evidence

All detailed logs and traces are ignored local artifacts under `.local/android-test/reports/separation-s0-tl05-api35-20261030/`:

| Evidence | Result / SHA-256 |
| --- | --- |
| AndroidJUnitRunner class output `tl05-final-test-class-rerun.log` | 2/2 PASS; `438972A22823E7E17A1E16A469CD02D7A33C2C39B90433E99060B61217DC5758` |
| Reflow, Host state, title rectangles and native bounds `tl05-api35-final-run-trace.txt` | `4929252FA8A73B6F1D84D1158815AD0EF26CA1F0130FE6605A25EDCA7378E0AC` |
| AndroidTest rebuild `tl05-final-androidtest-build-rerun.log` | 53/53 tasks; `F58170CE3352DEE255F21D22CC523D7D140A75E57A88455115470924C2DF7E96` |
| Full Gradle gate `full-gradle-gate-095410.log` | BUILD SUCCESSFUL; `80C377224A8E8D9C215A0149A443A5E2B7A0E36BFE3E4C662C3AAAF8FC365E62` |
| Forced JVM rerun `full-gradle-unit-test-rerun-095649.log` | 727 tests / 100 suites; `6F3725C5C7C82DB29262F945350ACEC3BD782021554DB55F887CCA8EACBE7146` |
| Skin Studio Python `skin-studio-python-095410.log` | 61/61; `B5FEA36D9544192D0D4D5D8497EA5B970136F6FCB156C0C7EB3FFC1AB73FB043` |
| Greedy strategy `title-folding-node-final.log` | 10/10; `AD35A2781A63D7E5F46E2C6031CFCA9D23D492C6D739B83E22F1E0AF9B948348` |
| Clear canonical verification log | 9 files / 394,086 bytes; log `C5CA0FF65E4A70A32B8103595A22E5F31BBA174BD0D229B86CBB472523FC547B`; package SHA-256 `8F7F5680BFD27976B2792AAB06D051BAF0F03F4A914636EA73E82E48431F8035` |
| Debug / AndroidTest APK | `6CF8835E82479F651D2A2019F0CBBCB798C497C00AB6689C7AE186479420A0CD` / `72101800F5F580291B2019DE85F1B8019DB56CE799C196B6605A90E341D87A84` |

Code and instrumented regression were committed and pushed as `2c4f9afe2d91c99fb016ec09d2702444be4c6f30` (`fix(分离): 发布 Clear 时间轴缩放状态并补充实测`).

This signs only the actual API 35 Clear zoom/reset Host projection and the tested reflow slice. Physical/OEM devices, TalkBack, all camera/date paths, and full matched Native/Clear interaction parity remain **NOT TESTED**; `S0-TL-01`, `S0-TL-06` and parent `SEP-01` remain **PARTIAL / HOLD**.

## Matched Native/Clear Today, zoom and reset camera replay (2026-10-31)

Added `TimelineReturnViewportInstrumentedTest.nativeAndClearTodayZoomAndResetProduceTheSameCamera`. It mounts the actual Native `PowerCalApp`, then the packaged Clear skin, using separate UUID-isolated Stores. The test uses each visible Today control, zooms from 24 to 16 hours, then resets through Native's physical double-tap on the current-time ruler and Clear's `resetTimelineZoom` skin control. It compares the Host camera projection at all three points.

- On Android 15 / API 35 / WebView 124 `emulator-5570`, both flows produced the same local-noon anchor `1793448000000` and camera sequence `24h → 16h → 24h`. Zoom preserved the anchor; reset returned only the scale to 24 hours. Focused replay **1/1 PASS**; complete `TimelineReturnViewportInstrumentedTest` **2/2 PASS**. The paired Clear action is dispatched through its actual WebView control; prior direct touchscreen coverage of Clear zoom/reset remains the separate **11/11** test evidence above.
- No production source changed. Exact source commit `55d7efb8647f4f547b0cc379def75844f4ee31c1`; clean archive SHA-256 `196E950B83F291033FABCD0D187F934928C896FFEA60F46CF568567D4DF4AA78`. Full offline gate: **784/784 JVM / 125 suites / zero failures, errors or skips / 171 tasks**, source boundaries and Debug/AndroidTest APK assembly passed; Lint **0 errors / 51 warnings**. Gate log SHA-256 `B7EDA0E4E671B581D5FCE60E88FC9B08FB07B11BB61E94099F58383858E04D45`.
- Debug APK SHA-256 `8CC46EED2FE1569CB349C7C913C3BFFE85344E4C6E9C527342373367C3E8F886` (66,518,075 bytes); AndroidTest APK SHA-256 `CB1E352E1C7B0A1AA6726A2BC766B89EB53FEECBC9122326416083CA5F3D4CF4` (1,572,336 bytes). Clean class log SHA-256 `84EE48FC628169B4403A6C9C02A8CEBDC93E08C5F5A33AE7E085261D0EC45284`; focused camera trace SHA-256 `FDCC7F7042AEA5F0678B2F0AFDC44544282134473EF0931217B54EFB9006FEC5`. Evidence is retained in `.local/android-test/reports/sep01-native-clear-camera-parity-20261031/`.

This closes only the paired Today/zoom/reset camera result for that path. The later pan replay below verifies one camera outcome through each skin's existing user-facing affordance; it does not show identical pan gestures. Independent wing scrolling, TalkBack, and physical/OEM validation remain **NOT TESTED**. SEP-01 stays **PARTIAL / HOLD**.

## Matched Native/Clear timeline pan outcome (2026-10-31)

Added `nativeVerticalPanAndClearLaterControlProduceTheSameCamera` to `TimelineReturnViewportInstrumentedTest`. The isolated Native and Clear Stores start at the same state. Native uses its existing vertical timeline gesture; Clear uses its existing accessible “稍后” pan control. This compares the resulting Host camera through different UI affordances, not identical gestures or pan velocity behavior.

- On API 35 / Android 15 / WebView `124.0.6367.219`, AVD serial `emulator-5570`, both flows first set Today to local noon (`1793448000000`) at 24 hours. Native's measured 1736 px viewport is moved slowly upward by 217 px (one eighth of its height). A touchscreen tap on Clear's visible `panTimeline(+180)` control moves the same local-noon camera by three hours. Both Host snapshots end at `1793458800000`, 24 hours. The two isolated Stores remain at **0 drafts / 0 commits**.
- The new pair passes, and the complete viewport class passes **3/3** from clean source commit `1ff2447b34888ba3c399e1ad0b47d3d9c4e6817e` (tree `5cfd7f53bcdddd232ce9b788539df5a400dfdf4a`). This verifies the pan result and Clear control reachability; it does not sign identical Native/Clear gesture input, independent wing scroll, or full SEP-01.
- Exact clean-source gate: **784/784 JVM, 125 suites, 0 failures/errors/skips; 171/171 Gradle tasks**, both source boundaries, Debug/AndroidTest APK assembly and lint passed; lint **0 errors / 51 warnings**. Gate log SHA-256 `D7E0E660E63215E5345D427467C2E8BACF1032C64F4A834B19287213CD7078A3`. Skin Studio Python suite: **63/63 PASS**, log SHA-256 `AEE28B0CC416C9435AA99393B7EDE0341A75277BB04C8F066110BEC207B0AE9D`.
- Debug APK SHA-256 `8CC46EED2FE1569CB349C7C913C3BFFE85344E4C6E9C527342373367C3E8F886`; AndroidTest APK SHA-256 `5C72725C6D358C8330F393292D2F136B661CDEFC851DEFE6DD1E0E230DA5F490`. Device class log SHA-256 `BDD99B5F30074D63ED2E132D3B6D66DBD13A46D1A5F7F908E1EFA115937671D1`; interaction trace SHA-256 `FDE05437E5B76436B3A588CE15F04B12B7406DB84E8587C33A9AD5DF8B6DC784`; runner result textproto SHA-256 `7339BE1C7C6CEB6602568DE39BB1EBDF19F3C0EF9D4CAE2FE4D8A5A16F620261`. Evidence and clean APKs are under `.local/android-test/reports/sep01-native-clear-pan-parity-20261031/` and `.local/android-test/clean-gate-1ff2447/`.

The test-only code commit `1ff2447` is pushed. No Native, Host, Clear, or Core production source changed in that commit. `S0-TL-01` and SEP-01 remain **PARTIAL / HOLD** for gesture/velocity parity, independent-wing device replay, accessibility and physical/OEM validation.

## Follow-up — API 35 Native/Clear independent wing touch and Native left-wing startup (2026-10-31)

The four-lane Native replay reproduced a real initial clipping defect: before any user scroll, left `lane=0` had an empty accessibility rectangle while the left scroll range was `0..458 px`. The code passed `Int.MAX_VALUE` as `rememberScrollState`'s initial position before persisted events had loaded. Compose first measured no overflow (using `Int.MAX_VALUE` as its unbounded `maxValue` sentinel), clamped the offset to zero, then later exposed the real `458 px` range without moving the viewport. The minimal fix starts at zero and performs a one-time scroll only after `snapshotFlow` observes a finite positive range; `rememberSaveable` prevents later recompositions or restored sessions from snapping over user scrolling.

- `TimelineReturnViewportInstrumentedTest.nativeAndClearLeftAndRightTimelineWingsScrollIndependently` seeds identical isolated Stores with four overlapping events in each wing, resets both timelines to the same 24-hour camera, then sends actual Android touchscreen swipes independently through each side. Native left starts at `458/458 px`, right at `0/458 px`; left lane 0 is visible (`Rect(267,1042–488,1189)`). A left-wing swipe moves its event left coordinate from `267` to `419 px` and offset from `458` to `306 px`; the right wing stays fixed. Clear starts left at `164.19/164 px`, right at `0/164 px`; a right touch moves its event from `304.10` to `242.38 CSS px` with offset `0→61.71`, while left stays fixed. A left touch then moves its event from `111.81` to `173.52 CSS px` with offset `164.19→102.48`, while right stays fixed. Camera remains local noon / 24 hours; both isolated Stores remain at zero drafts and commits.
- Focused pair **1/1 PASS** and complete `TimelineReturnViewportInstrumentedTest` **4/4 PASS** on Android 15 / API 35, WebView `124.0.6367.219`, `emulator-5570`. The clean source archive for commit `cd9e7bef39a1d8ea59ca0f4ea8e2ed99e3037f67` (tree `09fb277b5a2746aa23cf75d9b706949784f7a2fc`) passed the full offline gate: **784/784 JVM / 125 suites / 0 failures/errors/skips; 172 Gradle tasks**, both source boundaries, Debug and AndroidTest APK builds, lint, and device class **4/4**. Skin Studio Python tests: **63/63 PASS**.
- Evidence is in `.local/android-test/reports/sep01-native-clear-wings-parity-20261031/`: clean source archive SHA-256 `A45EFD9E9528216872C96F312C5667D2C55E6BC81E307FC7AD214BCF1D9F169A`; Gradle log `AA35DF59C551F2CF7A99EB32D37AF632EEA33FC6D91F564197D62DA36A507118`; device class log `9BA3325A24CD069F708CC8F041F7DD2AA13BC9881591648FAEF9D8C5CAD106AA`; geometry trace `121C88624BBE61DB1916ED78F9A8AAEEDCE2DE9B8E7BE1DABECAE765ACBF6C96`; Studio log `74F1444666072FC604EE8C45D45AC32504338B548273F9587DE2C3D065A19858`. Debug / AndroidTest APK SHA-256: `83C9EDAD5DBE47F3D78C5E9BC35435CAD59C674FBF79D66C6054A982C51D767D` / `751D17198EFE50E9203B3A87737271C28A06ED59410E328F5553C8FE924C62F2`.

This closes the actual independent left/right horizontal scroll slice and the Native startup clipping regression only. Pan input parity, matched reset touch semantics, TalkBack, physical/OEM and remaining SEP-01 behaviors remain **NOT TESTED**; `S0-TL-01` and parent `SEP-01` stay **PARTIAL / HOLD**.


## Follow-up — Native/Clear single-finger vertical camera pan (2026-10-31)

Source review found that the packaged Clear timeline had only fixed ±180-minute buttons. It had no vertical camera gesture, so an arbitrary non-grid camera could not be reached through the Clear UI. The existing Host panTimeline action already accepts bounded integer-minute deltas; no Core or Host change was needed.

- The canonical Clear app.js now maps a trusted primary vertical touch on empty timeline space to the existing panTimeline action. Interactive controls and event cards are excluded; horizontal movement remains browser-owned. axis-scroll uses touch-action: pan-x so vertical movement changes the Host camera rather than scrolling the DOM. The Studio source is canonical and the generated .pcskin was rebuilt and checked against the APK.
- API 35 / Android 15 / WebView 124 / emulator-5570: Native and packaged Clear both started at local noon 1793448000000, zoomed to 16 hours, and used actual touchscreen vertical drags to reach 1793458800000 (15:00, +180 minutes). Clear recorded trusted pointerdown/move/up, 8 trusted move events, no pointer cancellation, and unchanged axisScroll.scrollTop. Isolated Stores remained at 0 drafts / 0 commits. Focused pair 1/1 PASS; complete TimelineReturnViewportInstrumentedTest 5/5 PASS, including independent left/right wing scrolling and the existing Today/zoom/reset and return-camera cases.
- API 35 WebView reports an empty PointerEvent.pointerType for the instrumentation-injected Android SOURCE_TOUCHSCREEN event. Runtime accepts that value only for a trusted primary left-button pointer; the test also verifies trusted down/move/up and normal completion. The first test sample was too close to Native touch slop and lost the pre-activation travel; the final 180-minute sample crosses slop on the initial move and matches the Native camera exactly.
- node --check and canonical skin verification passed. build_default_clear.py --verify --apk app-debug.apk confirms the packaged asset equals the canonical source, SHA-256 e4eee02a749c9dd8dcc0dc94f225117cfb2e0a31b255a77e430758d4cbc8f9519. Skin Studio Python tests 65/65 PASS. Forced serial offline gate: 785/785 JVM, 125 suites, 0 failures/errors/skips, 171/171 tasks PASS, Skin/Native source boundaries and Debug/AndroidTest APK assembly pass; Lint 0 errors / 55 warnings.
- Evidence under .local/android-test/reports/sep01-clear-touch-pan-20261031/: full gate SHA-256 77A6D4588776B85FCA7CB49C77BE66DDBB7A5885BE1502CC83388C399FE57DD1; focused device log 89FB3DFCCF3B4681764FE1C217C81A52D304A5364695152D24BF80EE9A7430CA; full viewport-class log 896BA483E65AFAA8084FF8695C53492CBA4695A1ADDBA9FE6C338F483480FCE4; device JUnit XML F1E97BC5BF8E1B82858FB788435D9F0D1B089B6907077A1F5940DEFCF46139B1; camera interaction logcat FC36C0ED65BF42D691BF1497CEACB72AA2520B523C65FE7513A3D03D51DA0867; Studio log F83DD19E721010DF859C9FBB1CAFC9CEE81CCDE16D34B5D3B3D7FCFE9BC68FBF; Debug APK 91E97FD5AAE8E4D8B3C1AFEA79E12451B378859CCAC7407949574ABE09BB69CC. The worktree AndroidTest APK hash is 5A71B1295944B0C6ACF4A5B5193C583AB7D29A435C85F1048E8D39BE9873BCA6 and contains two other Agent's uncommitted test edits; those files were not executed or staged in this change.

This closes single-finger vertical camera-pan input parity only. SEP-01 remains PARTIAL / HOLD for Native pinch-gesture parity, fully matched reset input, full TalkBack coverage, broader timeline interaction parity, and physical/OEM validation. Native fallback and the default skin are unchanged.

## Follow-up — matched non-grid zoomed vertical pan and camera-seeded Activity (2026-11-01)

The API 35 paired replay reproduced why Clear vertical pan could fail intermittently: `.timelineWing` allowed `pan-y`, so Android WebView could take ownership of a vertical gesture and cancel the Pointer Events stream before Clear sent `panTimeline` to Host. The wing now declares `touch-action: pan-x`, preserving horizontal lane scrolling while leaving vertical camera movement to the existing Clear handler. The handler uses the measured 8 CSS px slop (21 Android px at the tested DPR 2.623) and applies movement after the slop crossing incrementally, matching Native's touch-slop behavior. No Core or Host behavior changed.

- API 35 / Android 15 / WebView 124 / `emulator-5570`: the Native and packaged Clear flows reset to the same local-noon camera, zoom to 16h, then receive actual Android touchscreen vertical swipes targeting a non-grid +37-minute shift. Native ended at anchor `1793450226411`; Clear ended at `1793450280000`. Both stayed on the timeline at 16h and snapped to the same `2026-10-31 12:40` Activity start (`TimelineCoordinate.snap = 1793450400000`), with equal `12:40–13:40` forms. The camera difference was 53.6 seconds, within the asserted one-minute tolerance. Clear observed trusted pointer down/move/up, 63 move events, no cancellation, and `touch-action: pan-x`. Both isolated Stores remained unchanged until explicit save and then held matching records/identity counters.
- `timelineActivityCreateMatchesNativeAndClearSnappedCameraSelection`: passed as part of `TimelineCreateContextWebViewInstrumentedTest`, **5/5 PASS** on the API 35 AVD. Full direct runner log SHA-256 `BD70207CDA6FC07C1066E5FF5790D23AF08DB0CEEB54703AA45D1F1FE759A70C`, retained at `.local/android-test/reports/sep04-nongrid-zoom-camera-20261101/timeline-create-class-final.txt`. After the same source change, adjacent classes also passed directly on that AVD: `TimelineReturnViewportInstrumentedTest` **5/5** (log SHA-256 `1329CA4CA173971FD0FF605343DD0D58B95C3F1ECD5DEFA5D68996BAC24DFC34`), `TimelineDragWebViewInstrumentedTest` **2/2** (`EEB93A2F175761A9F36C1B83D5DAE17EACE40814A4BA02059425D28CA6B8D6B7`), and `TimelineGreedyFoldingWebViewInstrumentedTest` **2/2** (`A145EC55C1D5442BA700A3C6CE68E506962533561CA18E65DAE571E26BCBCB9C`). The measured camera/pointer trace SHA-256 is `7D822792E718333BC1F3E5E160CEAA24E6E805321133A03D4C9D7C983F8627FF`.
- `node --check` passed; Skin Studio Python **65/65 PASS** (log SHA-256 `D59391FD285D61B8387F3E66373990F3D53D0B44CE271AB50A2C2AB24F3EDF51`). Canonical Clear and APK asset verification passed for 9 files / 397,193 bytes, SHA-256 `4ECB330A4893F5C98883F69D59A9AC79DE36445C2B34B6A0A14A435C67C74B88`.
- Forced serial offline full gate passed: **786/786 JVM tests, 125 suites, 0 failures/errors/skips; 171/171 tasks**, both source boundaries, Debug and AndroidTest APKs, and lint (**0 errors / 56 warnings**). Gate log SHA-256 `7480D523D43EF9355F96394D67CDDD4C8222BA8ED165D23864B64E96A8686246`; Debug APK SHA-256 `EF85EA15AB31353F9A26C3EEEC3B5DBFC9CEB38BC73069752F7AB66F85965650`; AndroidTest APK SHA-256 `1892A27A1FAE91B31472244057B4F1DBB8DAD96F4FB2A11B80AEEC0EE0C7600C`.

This verifies one Native/Clear vertical gesture at a zoomed, non-grid camera and the corresponding camera-seeded Activity. It does not complete SEP-01: pinch parity, complete reset-input parity, TalkBack, broader timeline paths, and physical/OEM validation remain open. Native fallback and the default skin are unchanged.
