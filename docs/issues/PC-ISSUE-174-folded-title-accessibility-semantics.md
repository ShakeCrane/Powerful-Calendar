# PC-ISSUE-174 — 折叠标题完整无障碍语义

日期：2026-10-25

状态：**Compose 语义树子项 PASS；TalkBack 实际播报与实体设备验收仍 BLOCKED；核心冻结 HOLD**

## 发现与根因

贪心折叠后，标题 Canvas 只为可见标题创建节点；事件框仍保留完整事件无障碍描述。`TimelineScreen` 的事件 `Surface` 将事件类型、完整标题、时间摘要和事件序号写入 `contentDescription`。现有回归覆盖标题折叠、事件可点击以及长标题可访问，但没有在同一组隐藏标题案例中直接断言隐藏 B/D 的完整标题仍留在语义树中。发现的是回归断言缺口，没有发现生产语义丢失。

## 最小变更

扩展 `TimelineTitleComposeTest.measuredGreedyTitlesFoldButEveryEventRemainsClickableAndAccessible`：对 A/B/C/D 每个事件框读取 `SemanticsProperties.ContentDescription` 并断言包含完整原始标题。该用例同时确认 B/D 的标题 Canvas 不存在，且隐藏的 B 事件仍可点击并打开完整事件。只改测试，没有改生产绘制、事件几何、点击或视觉行为。

## 验证证据

- 定向 `TimelineTitleComposeTest` + `GreedyTitleFoldingTest`：**17/17 PASS**，0 failures/errors/skips。Gradle 日志 `.local/android-test/reports/freeze-20261025/pc-issue-folded-title-semantics/focused-title-accessibility.log`，SHA-256 `AE93E5F2C02C5AFDA2D1849E24952F5BE935CCF2EBDC5DEADEAB123E0308A702`。JUnit XML：`TimelineTitleComposeTest` 9/9，SHA-256 `4EBFADE69B871F357BF125CAF059D8DDD010A4B2832F6FF242387D86B1759A68`；`GreedyTitleFoldingTest` 8/8，SHA-256 `92307DDD4A4DC4C3931D17455F1A27D27FE3CC46009E42715C3540DFCDE1B4C7`。
- 完整离线门禁 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain`：**542/542 JVM、74 suites、0 failures/errors/skips、88/88 tasks**；Debug / AndroidTest APK 构建成功；lint **0 errors / 53 warnings**。完整日志在隔离 worktree `.local/android-test/worktrees/pc-issue-173-glass-fallback/.local/android-test/reports/freeze-20261025/pc-issue-folded-title-semantics/full-offline-gate.log`，SHA-256 `AD81C362122B84A8A2A39C0C0CF09B445FAD84939CA83B0BCB583426F09F3B6F`。
- Debug APK SHA-256 `517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614`；AndroidTest APK SHA-256 `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`；lint XML SHA-256 `B95E8834C3E3EFA3386E0391464F11FB5953ED428EA7C87FA70CE020661628C0`。被测测试源 SHA-256 `788D3201185015B2DC06B4F9D8B7E07DF596B2F1AA4B56C29797BF0702D74759`。

## 签核边界

该测试证明 Compose 语义树对可见和被折叠事件都保留完整标题；它没有启动 TalkBack，也不能证明语音播报、焦点顺序、手势阅读体验或 OEM 无障碍服务。验收矩阵 `REC-P1-063` 因 TalkBack 实际播报和实体设备未签核，继续 **BLOCKED**；没有把窄化子项冒充为整行 PASS。本轮未触碰四台在线 AVD；实体设备仍由用户验收。总体验收保持 **HOLD**。
