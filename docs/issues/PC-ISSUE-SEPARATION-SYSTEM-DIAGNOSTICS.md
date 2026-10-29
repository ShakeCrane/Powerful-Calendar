# PC-ISSUE-SEPARATION-SYSTEM-DIAGNOSTICS

- **日期：** 2026-10-30
- **状态：** `PARTIAL / HOLD`
- **验收项：** 重建矩阵 `S0-ST-22`（RECONSTRUCTED；不是历史 209 项原始 ID）
- **实现提交：** `050798e5c21df8182c93f5a19d8d802d5c119464`（`feat(分离): 补齐核心系统诊断投影`，已推送到 `origin/codex/separation-s0-timeline-drag`）

## 结论

Clear Settings 现在展示 Native Tools 核心只读诊断字段：正式事件数量、下一个事件序号、已分配身份数量、数据库版本/名称、当前视觉效果、减少动画状态，以及最多三条重复事件的下一次触发摘要。该数据只在 Settings 页面投影；序号/身份水位以字符串传输以保留 Long 精度，重复标题和规则详情不进入 `systemDiagnostics`。

投影单元测试、API 35 Robolectric/Compose 集成、Chrome 156/CDP Clear 源码和 Skin Studio Preview Mock 回归通过。完整 Gradle 门禁为 **714/714 JVM tests、98 suites、0 failures/errors/skips**，全量强制重跑同样通过。实际 Android WebView→Host、Android 无障碍服务和实体/OEM 行为没有设备证据，因此 S0-ST-22 保持 **`PARTIAL / HOLD`**，不据此签核设备 parity 或整体 GO。

## 根因与修复

1. Native `NativeToolsPage` 已显示 `records.size`、`UiDiagnostics.nextSequence/identities`、`PowerStore.VERSION/DATABASE_NAME`、Host `GlassEffects` 状态及三条 `RecurrenceEngine.nextOccurrence` 结果，但 v3 Host state 没有这些值；Clear Settings 因此无法显示同源诊断。
2. 新增 `WebSkinSystemDiagnosticsProjection`，复用 Host 已有正式记录和计数，不增加数据库查询或写操作。序号使用 `TemporalRecord.formatSeq`；身份数量序列化为字符串；数据库元信息与 Native 常量保持一致。
3. 重复事件摘要复用 `RecurrenceEngine`，最多三条，传入当前 Host `nowMillis` 与本地 `ZoneId`；显示沿用 Native 的日期时间格式和“没有后续实例”文案，不投影重复标题或规则内容。
4. Clear Settings 增加 `role=list` / `role=listitem` 的只读诊断表，并通过文本节点显示值。Studio Mock 与 CDP 回归覆盖字段、列表语义和第四条重复预览被裁掉的边界。
5. 更新 parity matrix 前言，移除“整体诊断仍只在 Native”的过期描述，明确 S0-ST-18..22 的 Clear Settings 范围。

## 修改文件

- `PowerCalApp.kt`：只在 `SETTINGS` 将核心系统诊断 projection 写入 Web Bridge state，其他页面使用空对象。
- `WebSkinSystemDiagnosticsProjection.kt`：新增可独立测试的投影，包含序号/身份水位、数据库和系统状态、最多三条下一次重复实例摘要。
- `WebSkinSystemDiagnosticsProjectionTest.kt`：固定时间及时区覆盖投影字段、Long 精度、重复数量上限、结束系列与 DST 回拨。
- `WebSkinSystemDiagnosticsIntegrationTest.kt`：通过 API 35 `PowerCalApp` 验证设置页数据、时间轴页隐藏、重复规则上限和零写入副作用。
- Clear `app.js`、`index.html`、`style.css`：新增只读诊断列表及无障碍语义。
- `v3-studio.js` 与 Clear/Studio Chrome/CDP 脚本：增加模拟值并验证渲染。
- `app/src/main/assets/powercal-clear-default.pcskin`：重建 canonical Clear 包。
- parity matrix、进度记录和本报告：记录状态及可复查证据。

## 验收证据

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| 投影单元测试 | `PASS` | 3 项：计数/格式序号/完整 Long identity/数据库/effects 字段；最多三条重复摘要和标题排除；到期系列返回“没有后续实例”；America/New_York DST 秋季回拨仍投影本地 09:00。 |
| Host / Compose 集成 | `PASS` | API 35 Robolectric 预置 5 条正式事件（4 条重复），在 Settings 验证正式计数、序号/identity、DB 元信息、Host effects 和最多三条递归摘要；切到 Timeline 后 `systemDiagnostics` 为空。前后 commit、identity、next-sequence 和正式事件快照一致。 |
| 定向回归 | `PASS` | `WebSkinBridgeTest`、`WebSkinSystemDiagnosticsIntegrationTest`、`WebSkinSystemDiagnosticsProjectionTest` 共 **10/10**。日志 SHA-256：`E116A47D62CDC762158B5706DC0923CD1ED7CB6339012305FAEDB7A2FB5F98E4`。 |
| 完整离线 Gradle 门禁 | `PASS` | `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain`；**714/714、98 suites、0 failures/errors/skips**；88 actionable tasks（12 executed / 76 up-to-date）；Lint 0 errors / 53 warnings。日志 SHA-256：`D3EFA60D9EA2A642CA42F6A5416D4E9E66E434DB060E7A0E766F7EB4E992BA08`。 |
| 全量强制重跑 | `PASS` | `:app:testDebugUnitTest --offline --rerun-tasks --console=plain`；**714/714、98 suites、0 failures/errors/skips**，32/32 tasks executed。日志 SHA-256：`A4C1AA47446A9E3ACD1AF5DE808877AEADACF33E40F18323CDC4D723962E1F20`。 |
| Skin Studio Python | `PASS` | `python -m unittest discover -s tools/skin-studio/tests -v`：**61/61**。日志 SHA-256：`BD58D41F7D1A7602EDD4E6B6506E8DA75B4EF02684F008432946583E302E94D5`。 |
| JavaScript/CJS 语法 | `PASS` | Clear `app.js`、Studio `v3-studio.js`、两份 CDP 回归脚本通过 `node --check`。日志 SHA-256：`5E6674658B6E66910BBBDE6618BC599C8CFF9F0982FD9F7036766EA333D62EC9`。 |
| Chrome/CDP Clear 源码 | `PASS` | Chrome 156 Headless + protocol Mock Host 验证正式事件/序号/identity/数据库/effects/减少动画/重复摘要全部显示；诊断容器为 `role=list`，9 行为 `listitem`，第 4 条重复摘要不显示。结果 JSON SHA-256：`13DDF86FE903283C2A007863F8BFE7A11671E7CF0F52037C5C9E1F714E1F4525`；截图 SHA-256：`356626E821A9FE18AE16D2A9605CA061E38E81FD0F94BEFA4E909E997C1E66C2`。 |
| Skin Studio Preview Mock | `PASS` | Canonical 9-file Clear 包进入 Settings 后显示系统诊断摘要，检查序号、DB、effects 和 list role；Preview Mock 不访问真实 Host/系统设置。结果 JSON SHA-256：`DEC7423F36D925BF7804EB6EE270F2BB417167BCEF8B665E51BD150CA0B6A24D`。 |
| Clear 包与 APK 内置资源 | `PASS` | canonical 包 9 files / 394,087 bytes；源码构建结果和 APK 内 `assets/powercal-clear-default.pcskin` 字节一致，SHA-256：`544eb2b9ec1bd1ea3c3cf9d5949d944cba48018bc08478f60c08eea5a4e1cf3f`。 |
| APK 构建 | `PASS` | Debug APK 67,509,599 bytes，SHA-256 `006DBA8B1DD3ADD87080A6484E9C0D59419FCA30B59F3DB8C93BE92B1E90BE47`；AndroidTest APK 1,557,196 bytes，SHA-256 `371A9C1AF76B5F0530FE4A69FA2FAF9BF1AFA053256DC8B24F7264129930653E`。AndroidTest APK 已组装但 instrumentation 未运行。 |
| Android 设备 / 模拟器 | `NOT TESTED` | `E:\android-sdk\platform-tools\adb.exe devices -l` 无设备；`E:\android-sdk\emulator\emulator.exe` 不存在。生产 Android WebView→Host、TalkBack/真实无障碍服务、实体设备及 OEM 行为未验证。 |

## 已知限制与剩余工作

- Chrome 使用桌面 Chromium 和 protocol Mock Host；Studio 使用非写入 Preview Mock。二者均不能替代生产 WebView/Android 设备测试。
- recurrence 摘要使用 Host 本地时区和现有 `RecurrenceEngine`；本次不改变重复规则、触发、事件保存或 Native 诊断行为。
- S0-ST-22 的本地 JVM、Bridge 与浏览器实现切片通过；真实设备项仍 `NOT TESTED`，所以本项和整体 parity 继续 `PARTIAL / HOLD`。

## 可追溯证据位置

详细日志和捕获结果位于 Git 忽略的 `.local/android-test/`：

- 最终完整门禁：[full-gate-final.log](../../.local/android-test/reports/separation-s0-st22-system-diagnostics-20261030/full-gate-final.log)
- 最终强制重跑：[full-rerun-final.log](../../.local/android-test/reports/separation-s0-st22-system-diagnostics-20261030/full-rerun-final.log)
- 最终定向回归：[focused-final.log](../../.local/android-test/reports/separation-s0-st22-system-diagnostics-20261030/focused-final.log)
- Skin Studio Python：[skin-python-final2.log](../../.local/android-test/reports/separation-s0-st22-system-diagnostics-20261030/skin-python-final2.log)
- JavaScript/CJS 语法：[node-checks-final2.log](../../.local/android-test/reports/separation-s0-st22-system-diagnostics-20261030/node-checks-final2.log)
- Clear 包构建：[package-build.log](../../.local/android-test/reports/separation-s0-st22-system-diagnostics-20261030/package-build.log)
- Clear Chrome/CDP 结果与截图：[clear-cdp-final](../../.local/android-test/reports/separation-s0-st22-system-diagnostics-20261030/clear-cdp-final/)
- Studio Preview Mock 结果：[studio-mock-cdp-final](../../.local/android-test/reports/separation-s0-st22-system-diagnostics-20261030/studio-mock-cdp-final/)
