# PC-ISSUE-SEPARATION-REMINDER-DIAGNOSTICS — 皮肤提醒诊断与重协调状态

日期：2026-10-28

状态：**Host、Clear、Studio Mock 与本地自动化回归 PASS；Android WebView/AVD/实体设备 NOT TESTED；SEP-11 整体 NOT TESTED。**

## 问题与根因

SEP-11 的 Clear 设置页可以显示提醒权限和调用 Host 重协调，但没有每个事件的提醒状态，也不能区分正在处理、成功、受阻、仍待处理、执行异常或批次上限造成的遗漏。此前 `ReminderScheduler.reconcile()` 没有返回可审计结果；有界查询达到既有处理上限时，调用方无法判断仍有候选未检查。仅在 Clear 加 UI 会让它在真实 Host 下拿不到数据；仅加 Host 投影又会让 Studio/Clear 把零失败计数的截断批次错误显示为普通异常或成功。

此修复扩展现有 Host/Bridge 和 Clear/Studio，不改变 Store schema、事件数据、提醒触发时间、权限规则或核心调度策略。

## 修复内容

### Host 与稳定投影

- 新增 `WebSkinReminderProjection`，仅在 `skinSettings` / `SETTINGS` 状态提供 `reminderDiagnostics` 与 `reminderReconcile`；其他页面这两个字段为 JSON `null`。
- 延续 Native Tools 的可见性规则：只投影 `enabled && reminderMinutes >= 0` 的记录；Skin 每次最多显示 40 条，并基于完整 Host 候选集准确设置 `truncated`。
- 确定性排序为提醒时刻/事件锚点、事件 kind、规范化标题、标题和 Host 私有 `eventSeq` tie-breaker。`eventSeq` 仅参与排序，不序列化。
- 每项只包含合成 `displayId`、限长标题和截断标志、kind、desired state、允许列表内的 Android 提示和最近执行值。不会向 Skin 投影事件身份、occurrence key、notes、location、凭据或异常详情。
- 新增 `docs/contracts/web-skin-reminder-state-v1.schema.json`，定义字段、上限、状态枚举和空值行为；更新 `docs/V3_HOST_BRIDGE_EXTENSION.md`。

### 重协调计数与溢出

- `ReminderScheduler.reconcile()` 返回只读 `ReconcileReport`：`checked`、`scheduled`、`blocked`、`pending`、`failed`、`truncated`。
- 保持现有每组每轮最多处理 400 条 active/blocked 意图、100 条 CANCELLED 清理；用 401/101 哨兵查询标记 `truncated`，哨兵不处理，也不计入 `checked`。
- `checked` 统计实际处理的 active/blocked 项和取消清理尝试。缺少精确闹钟权限属于 `blocked`，不计为失败；pending、异常或截断批次不能报告成功。
- Host 状态为 `IDLE → RUNNING → SUCCEEDED/FAILED`。运行中 action 禁用且 Host handler 防重入；取消、异常和刷新失败均不会永久留下 RUNNING 状态。错误详情不会进入 Skin projection。

### Clear 与 Studio

- Clear 设置页显示权限摘要、每事件状态、Android 提示、最近执行结果，以及被截断的标题标记；状态区具有 `role=status` 和 `aria-live=polite`。
- 重协调失败提示区分 pending、失败和达到处理上限。`truncated=true` 且 `failed/pending=0` 时明确显示“本轮达到处理上限，仍有提醒未检查”，不会误报成功。
- Studio 增加 pending、失败、截断和状态不可用场景；Mock 遵循正式 schema，明确不调用真实 Android 调度器、不读写日历数据。
- 更新 Clear 包内置 asset；`build_default_clear.py --verify` 确认该包与 canonical source 一致。

## 自动化验证

### 当前合并工作树完整门禁

基线为 `codex/separation-skin-settings`，测试在 Track A Host 改动与 Track B Clear/Studio 改动集成后运行。命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果：**BUILD SUCCESSFUL；647/647 JVM tests、90 suites、0 failures/errors/skips；88/88 Gradle tasks。** Source boundary、Debug APK、AndroidTest APK 构建通过；lint **0 errors / 52 warnings**。

当前完整门禁中的定向子集：

- Phase 1 编辑器生命周期/默认值六个测试类：**51/51 PASS**，包括秋季 fold earlier-offset 固定 Clock 回归。
- 提醒 Host/Bridge 子集（`ReminderValidationTest`、`WebSkinReminderProjectionTest`、`WebSkinReminderDiagnosticsIntegrationTest`）：**27/27 PASS**。

Studio 与前端验证：

- `python -m unittest discover -s tools/skin-studio/tests -v`：**72/72 PASS**，10.248 秒。
- `node --check`：Clear `app.js`、Studio `v3-studio.js`、Chrome E2E 测试均通过。
- `python tools/skin-studio/build_default_clear.py --verify`：PASS；Clear package 8 files、382786 bytes。
- `node tools/skin-studio/tests/e2e_v3_source.cjs`：**PASS**。Chrome Headless 检查 Studio 项目往返、设置页六种提醒状态、无隐私字段、无写入、权限与重协调 Mock、RUNNING/成功/pending/失败/截断/不可用、固定导航栏下列表可滚动、四类新建和既有编辑器场景。截断场景实测文案为“提醒同步未完成：本轮达到处理上限，仍有提醒未检查。检查 400 项，安排 400 项，阻止 0 项。”
- `git diff --check` 与 Schema JSON 解析：PASS。

### 环境与逐文件证据

环境：Windows PowerShell；JDK `C:\Program Files\Java\jdk-20`（20.0.1）；Android SDK `E:\android-sdk`；Gradle 8.14.3、离线；Chrome Headless；Python 3.11。当前完整门禁通过进程环境变量指定 JDK/SDK，未创建或修改 `local.properties`。

忽略目录下的可复核证据：`.local/android-test/reports/separation-20261028/reminder-diagnostics/`。

| 证据 | SHA-256 |
| --- | --- |
| 合并后完整 Gradle 日志 `combined-full-gate.log` | `BB291342803CC50953ABD3888A2B6C6524BBFB07A781986E35C7AAE292133701` |
| 完整 JUnit XML（90 suites） | `app/build/test-results/testDebugUnitTest/`，并存于 `full-junit/` |
| Debug APK | `FF41B0AC9D9B87F4177756F2D18D281E18717811716C7CEDD97D36BB4F83A164` |
| AndroidTest APK | `0B1954E9438ADABD3C87E7B31C0FAD008DE4052747EE7B547DFF0B90C6FD1B7F` |
| lint XML | `EE1FE6A116F68E742ECC87AA7415CABE947A6E9073579D315C43F36FD9384238` |
| Skin Studio Python 完整日志 | `A375733F211E697FFD727D9AD757C634D2B7C3A02512BCFB77971434C7940FBB` |
| Chrome E2E 完整日志 | `D562C9D3B60E3AF93C44BF7659D0DA36AC3AA07206732191BF6D1FA76BAF556F` |
| bundled Clear asset | `5EA7DA58DDE8722320AB6F32B16E854293260788EB930F8E0CCE1321252D8080` |

Chrome 截图：

- `.local/android-test/reports/separation-20261028/reminder-diagnostics/chrome-e2e/reminder-diagnostics-settings.png`
- `.local/android-test/reports/separation-20261028/reminder-diagnostics/chrome-e2e/reminder-diagnostics-last-row.png`

截图只证明 Chrome Studio Preview/Mock 的布局和滚动，不是 Android WebView 证据。

### 失败尝试及修正

- 首次当前基线复跑使用默认 Oracle Java shim 无输出；显式 JDK 后发现默认 Gradle user home 锁文件写入受沙箱限制；按许可使用完整 Gradle 测试权限重跑。首次 Gradle 任务还因 worktree 没有 `local.properties` 且进程未设置 Android SDK 退出；之后明确设置 `ANDROID_HOME`/`ANDROID_SDK_ROOT=E:\android-sdk`，未改仓库配置，门禁完整通过。
- Chrome E2E 首轮没有切换到 `reminder-truncated`：测试把 Studio 外层的 `#scenario` 选择器当作 Skin iframe 内元素操作。测试修正为在 Studio 主上下文切换场景后，Host Mock 与界面均验证通过。
- 随后的 E2E 断言曾在长日志被保留窗口截断后才查找更早的权限 Mock 记录；已将断言移到操作后即时核验。曾出现一次组件重排断言失败，之后完整 Chrome E2E 复跑成功并通过该重排及所有后续场景。以上是测试 harness/单次失败记录，不隐藏在最终 PASS 中。

## 未验证边界与结论

- 本轮未运行 Android emulator/AVD、真实 Android WebView 或实体/OEM 设备测试。AndroidTest APK 构建通过只证明可编译；不代表启动或运行通过。实体设备测试由用户执行。
- 未验证真实系统权限回跳、AlarmManager 实际触发、进程死亡/重启、厂商后台限制、恶意 skin、完整 Settings 与 Native/Skin 功能等价。
- 本 Issue 的 Host/Kotlin/Schema/Clear/Studio 自动化范围 PASS；SEP-11 还有其他 Settings 功能与设备证据未闭环，整体保持 **NOT TESTED**。

## Git 与所有权

- 工作分支：`codex/separation-skin-settings`；本轮在独立 worktree 中集成 Track A Host 和 Track B Clear/Studio，不接触主工作树的 ledger、`design/`、D05 测试及 Issue 资产。
- 并行修改的 `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinManagementInstrumentedTest.kt` 被编入 AndroidTest APK，但不属于本 Issue；它未被暂存或提交。
- 实现与测试提交：`75d8e21b003007f54ff7369cef4c50d041512bc4`（`feat(分离): 增加皮肤提醒诊断与同步状态`）。最终 Issue/进度/验收矩阵文档提交随后完成；两个提交只通过普通 fast-forward 推送，不使用 force push。
