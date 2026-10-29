# PC-ISSUE-SEPARATION-EDITOR-CREATE-SESSION

- **日期：** 2026-10-30
- **状态：** `PARTIAL / HOLD`
- **验收项：** 重建矩阵 `S0-ED-01`（RECONSTRUCTED；不是历史 209 项原始 ID）
- **实现提交：** `52e6d754344d74d99983b45c2bbd40677598d45a`（`test(分离): 覆盖新建会话时钟与默认值`，已推送到 `origin/codex/separation-s0-timeline-drag`）

## 结论

只读调查未复现新建事件的产品默认值或持久化缺陷：`NewEventEditSession` 已在内存构建 baseline，复用 `EditorDefaults` 生成四类表单；明确退出时只有 meaningful diff 才调用持久化 checkpoint。既有测试已覆盖四类事件 20 次打开/关闭无写入、Diff 恢复、显式时间轴/月历选择以及保存/恢复。

发现的证据缺口是 Bridge 层的 `deck.create` 使用系统时钟，但测试无法为该 Host 命令传入固定 `Clock`，所以默认值此前只在 Core 工厂单测中确定性验证。此次增加一个仅供 Host 调用的可选时钟上下文，默认仍为 `Clock.systemUTC()`；Web 命令的封闭 schema 没有时钟字段，皮肤不能指定它。新增 Bridge 回归直接验证四类返回表单和无写入关闭行为，不改变产品默认规则或 Core 实现。

S0-ED-01 的 Kotlin/Robolectric、Clear/Studio Preview、构建和静态资源同源切片通过。没有可用 Android 设备或模拟器，因此生产 Android WebView→Host、TalkBack 和实体/OEM parity 仍未测试，本项保持 `PARTIAL / HOLD`。

## 根因与修改

1. `WebSkinEditService.execute(CreateDeck)` 已调用统一的 `NewEventEditSession.open`，并收到 Host 选择的时区与时间轴/月历选择；但创建会话默认读取系统时钟，Bridge 测试无法固定时刻。
2. 在 `execute` 增加内部 `newEventClock` 参数并传给会话工厂，默认值保持为 UTC 系统时钟。现有生产调用不传此参数，实际运行行为不变；Wire decoder 不读取它。
3. 新测试固定 `2026-10-25T15:07:42.987Z` 与 `Asia/Tokyo`，检查 ALARM `2026-10-26 00:08`、ACTIVITY `00:08–01:08`、date-only DEADLINE `2026-10-26`（`DAY_START`、0 分钟）和无日期 MEMO。四个表单均为 `dirty=false`；逐一关闭后正式事件、草稿、pending 数量、identity、sequence、queue order 和 commit 计数不变。

## 验收证据

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| 定向 Bridge/Core 回归 | `PASS` | `WebSkinEditBridgeTest`、`EditorDefaultsTest`、`NewEventEditSessionTest` 共 **46/46**，0 failures/errors/skips。包括新增固定 Clock 的 Bridge 路径、现有四类事件 20×打开/关闭零写入、显式 timeline/month selection、baseline/Diff 与恢复测试。日志 SHA-256：`05BAF879B5B6F2A66FAC389EB0B3E6B3253FF7B55EF61970021A06DBDDD4B5CC`。 |
| 完整 Gradle 门禁 | `PASS` | `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain --max-workers=1`；**715/715 JVM tests、98 suites、0 failures/errors/skips**；88 actionable tasks（13 executed / 75 up-to-date）；Lint 0 errors / 53 warnings。日志 SHA-256：`C69B4062BA1BD09D2E696449547F2A9605A558020BDD94C242448C53BE178D3A`。 |
| 全量强制重跑 | `PASS` | `:app:testDebugUnitTest --offline --rerun-tasks --console=plain --max-workers=1`；**715/715、98 suites、0 failures/errors/skips**，32/32 tasks executed。日志 SHA-256：`0FA6AD5F5A5E87E7876649621907E3BA7D6F0F04662B5758F13F1E77C7916436`。 |
| Skin Studio Python | `PASS` | `python -m unittest discover -s tools/skin-studio/tests -v`：**61/61**。日志 SHA-256：`0D691CACAFEEE52C3DCB642004A20E487794258942B49B1CDA429E5AD2828C49`。 |
| Clear 同源校验 | `PASS` | `python tools/skin-studio/build_default_clear.py --verify`：9 files / 394,087 bytes，SHA-256 `544eb2b9ec1bd1ea3c3cf9d5949d944cba48018bc08478f60c08eea5a4e1cf3f`。本 Issue 未改 Clear 源码或 APK 中的 Clear 资源。 |
| Chrome/CDP 月历创建 | `PASS` | 无 Puppeteer 依赖的 `e2e_v3_month_selection.cjs`：选中 `2027-06-19` 后 Preview Mock 创建的 ACTIVITY 起始日期为 `2027-06-19`，原 timeline camera 保持不变。此项证明 Clear→Studio Preview 的选中日期显示路径，不代替 Android Host。结果 JSON SHA-256：`80712F5998E351601B5F8412900A9D159D48D7D90DCF0F6E2660A36D8C1D3CF8`；截图 SHA-256：`DD4A3C0FE5C4DA52C5F34AB8B7180A1165070E667C1E816712377A18909E4DBA`。 |
| Studio source Preview | `PASS` | `e2e_v3_source.cjs` 检查 Clear 可编辑源码、ACTIVITY→DEADLINE 类型切换、表单字段重建和 transient 编辑器退出；另有 source/package round-trip 及非写入 Preview 情景。Mock 不读写真实日历。日志 SHA-256：`E25509D4BC4E46BB3E319F443C35DD60FCB784F71DB7B2F0DFB5FD92E0ECC97E`。 |
| Puppeteer 可选 E2E | `BLOCKED` | `e2e_v3_browser.cjs` 启动前因环境缺少 `puppeteer` 模块而退出；未安装依赖。对应场景已有固定时钟 Bridge、无依赖 Chrome/CDP 月历及 Studio Preview 覆盖，但不把这些结果描述为该脚本通过。日志 SHA-256：`A7752F148FECC081178EF0BB615F60303529F18DE271097A5BB012557756B223`。 |
| Android 设备 / 模拟器 | `NOT TESTED` | `E:\android-sdk\platform-tools\adb.exe devices -l` 无设备；`E:\android-sdk\emulator\emulator.exe` 不存在。Debug APK SHA-256 `DF65ACA593BB0A391490E4E598352A7E1E8BB8FABCB902BABA2BF88E5DCC70A3`（67,513,518 bytes）；AndroidTest APK SHA-256 `371A9C1AF76B5F0530FE4A69FA2FAF9BF1AFA053256DC8B24F7264129930653E`（1,557,196 bytes），已组装但 instrumentation 未运行。 |

## 未解决风险与下一步

- Clear 的实际 Android WebView→Host MessagePort、TalkBack/无障碍服务、Activity/进程重建和实体/OEM 行为没有设备证据。
- Chrome/CDP 与 Skin Studio Preview 使用 Mock Host；不能证明生产 Host 的时钟读取和 Android WebView 路由。Bridge 固定时钟回归覆盖生产 Host service 与真实 Store，但属于 Robolectric。
- S0-ED-01 因设备 parity 未签核，维持 `PARTIAL / HOLD`。下一项可继续审查 S0-ED-02 的字段校验、保存/发布及失败恢复，不需要等待设备结果。

## 证据文件

日志、JSON 与图片保存在 Git 忽略路径 `.local/android-test/reports/separation-s0-ed01-20261030/`：

- 定向回归：[focused.log](../../.local/android-test/reports/separation-s0-ed01-20261030/focused.log)
- 完整 Gradle 门禁：[full-gate.log](../../.local/android-test/reports/separation-s0-ed01-20261030/full-gate.log)
- 强制全量重跑：[full-rerun.log](../../.local/android-test/reports/separation-s0-ed01-20261030/full-rerun.log)
- Studio Python：[skin-studio-python.log](../../.local/android-test/reports/separation-s0-ed01-20261030/skin-studio-python.log)
- Clear 包校验：[clear-package-verify.log](../../.local/android-test/reports/separation-s0-ed01-20261030/clear-package-verify.log)
- 月历 CDP JSON/截图：[month-selection-result.json](../../.local/android-test/reports/separation-s0-ed01-20261030/month-selection-cdp/month-selection-result.json) · [month-selected-studio-preview.png](../../.local/android-test/reports/separation-s0-ed01-20261030/month-selection-cdp/month-selected-studio-preview.png)
- Studio source E2E：[browser-e2e.log](../../.local/android-test/reports/separation-s0-ed01-20261030/clear-source-e2e-final/browser-e2e.log)
- Puppeteer 依赖阻塞：[clear-preview-e2e.log](../../.local/android-test/reports/separation-s0-ed01-20261030/clear-preview-e2e.log)
