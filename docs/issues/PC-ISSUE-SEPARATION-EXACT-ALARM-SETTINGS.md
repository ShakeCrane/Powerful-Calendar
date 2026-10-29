# PC-ISSUE-SEPARATION-EXACT-ALARM-SETTINGS

- **日期：** 2026-10-30
- **状态：** `PARTIAL / HOLD`
- **验收项：** 重建矩阵 `S0-ST-19`（不是历史 209 项原始 ID）
- **实现提交：** `739ff066dfd9969fde7d9ced32f579793b594917`、`5b2e987cba4074d7e81337683f6d9495b8f965cb`（均已推送到 `origin/codex/separation-s0-timeline-drag`）

## 结论

Clear 皮肤的“设置 → 提醒可靠性”现在投影 Android 精确闹钟权限状态，并通过受限的 v3 Bridge 动作打开当前应用对应的系统精确闹钟设置页。权限未授予时，需要精确触发的提醒仍显示为 `BLOCKED`。用户从设置页返回或系统发送精确闹钟权限变化广播后，宿主刷新 Bridge 状态并重新协调持久化提醒；只有权限确实缺失的 EXACT 提醒会重新放入调度队列。模拟授予权限后，提醒恢复为 `SCHEDULED`，正式事件、提交记录、身份计数和事件序号不变。

Robolectric、Clear 浏览器源文件和 Studio Preview Mock 回归通过。真实 Android WebView、系统特殊权限设置页、系统权限状态广播及设备厂商的 AlarmManager 投递尚未执行，因此本行仍为 **`PARTIAL / HOLD`**。

## 合同与平台依据

- 重建验收矩阵原始 `S0-ST-19` 要求：提供 `onOpenExactAlarmSettings` 等价入口，缺少权限时保持 `BLOCKED`；变更前 Clear v3 没有对应动作。
- Android [`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`](https://developer.android.com/reference/android/provider/Settings#ACTION_REQUEST_SCHEDULE_EXACT_ALARM) 支持打开精确闹钟特殊访问设置，使用 `package:` URI 可定位当前应用。Android 官方[精确闹钟说明](https://developer.android.com/develop/background-work/services/alarms#declare-appropriate-exact-alarm-permission)说明了该权限与精确调度的关系。
- 现有 `AndroidManifest.xml` 已声明 `SCHEDULE_EXACT_ALARM`，`BootReceiver` 已监听 `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`；本 Issue 复用这些既有路径，没有增加权限种类或旁路。

## 根因与修复

1. 原生代码已有打开系统设置的调度器方法，Clear Settings 却没有精确闹钟权限状态投影或 Bridge 动作，用户无法从 Clear 皮肤处理缺失权限。
2. 权限被拒绝时，`ReminderScheduler` 将精确提醒记为 `BLOCKED`。`PowerStore.dueIntents()` 不会把 `BLOCKED` 行作为待调度项返回。系统授权后，Activity 返回或授权广播会调用既有的 `reconcile()`，但此前没有把这类持久化提醒重新入队，因此 `reconcile()` 无法恢复它们。
3. 预检查与 `setExactAndAllowWhileIdle()` 之间若发生权限撤销，后者会抛 `SecurityException`。旧代码始终记作通用“系统拒绝精确闹钟”，导致随后授予权限也无法识别为可重试的权限拒绝。
4. `canScheduleExactAlarms()` 是 OS 状态，不是 Compose 可观察状态。此前 `ON_RESUME` 虽执行 reconcile/reload，却没有递增 Bridge 的状态 revision；当其他投影字段没有变化时，Clear 页面可能继续展示旧权限值。
5. Bridge 现在只接受无额外字段的 `openExactAlarmSettings` 动作，并且宿主只允许在 `SETTINGS` 页面、Android 12/API 31+ 启动系统页面。设置页状态也只在 Settings 投影。
6. `reconcile()` 检测到精确闹钟访问已允许后，只将 `EXACT + BLOCKED + 缺少精确闹钟权限提示` 的行事务化恢复为 `PENDING` 并清除旧提示，随后沿用现有版本校验和调度逻辑。SecurityException 捕获时会再次读取权限状态：若访问已被撤销，则记录同一可恢复提示；若权限仍允许，则保留通用系统拒绝提示。权限仍被拒绝时不重试；其他原因的 `BLOCKED` 行不会被误解锁。
7. Activity 恢复时在 reconcile/reload 完成后递增 `WebSkinBridgeRuntime.stateRevision`，使 Web Host 的 `snapshotFlow` 发布新权限状态。BootReceiver 的权限变化广播则复用相同 reconcile 路径。

## 修改内容

- `WebSkinHost.kt`：新增严格解码的 `OpenExactAlarmSettings`、API/页面门控和权限状态计算。
- `PowerCalApp.kt`：Settings 页投影 `required/allowed` 状态；通过既有 `ReminderScheduler.openExactAlarmSettings()` 发起系统 Intent。Activity 恢复后在 reconcile/reload 收尾时递增 Bridge 状态 revision。
- `PowerStore.kt`、`ReminderScheduler.kt`：新增权限获准后的精确提醒重入队、共享的稳定权限错误标识，以及 SecurityException 时的状态重查；正式事件和提交历史不被改写。
- `WebSkinExactAlarmSettingsIntegrationTest.kt`：API 35 Robolectric/Compose 集成测试覆盖 Bridge、系统 Intent 的 action/package URI/flag、拒绝时返回仍 BLOCKED、授权后返回恢复调度和状态 revision 更新，以及 Store/identity/sequence 无写入变化。
- `ReminderValidationTest.kt`、`WebSkinBridgeTest.kt`、`BootReceiverRecoveryTest.kt`：覆盖 Core 恢复/重复 reconcile、SecurityException 状态分类、Bridge 闭合 schema、API 30/31/35 页面门控，以及 `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` 广播触发 BLOCKED→SCHEDULED。
- Clear 源文件和 Studio Mock 增加状态说明与动作；重新打包 `app/src/main/assets/powercal-clear-default.pcskin`。

## 验收证据

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| 精确闹钟状态与 API/页面门控 | `PASS` | `WebSkinBridgeTest`；API 30 不显示/不启动特殊设置入口，API 31+ 仅 Settings 页面可启动；action schema 拒绝额外字段。 |
| 拒绝状态与授权后重调度 | `PASS` | `ReminderValidationTest` 验证拒绝后为 `BLOCKED`，权限允许后变为 `SCHEDULED`，AlarmManager 仅出现一项调度。 |
| PowerCalApp/Bridge 系统设置集成 | `PASS` | API 35 Robolectric 集成测试验证 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`、当前包 `package:` URI、`NEW_TASK`；未授权返回继续 `BLOCKED`，授权后返回恢复 `SCHEDULED`。事件对象、commit 数、identity 数和 next-sequence 快照不变。 |
| 定向 Gradle 回归 | `PASS` | 4 suites / **27 tests**（`WebSkinBridgeTest`、`WebSkinExactAlarmSettingsIntegrationTest`、`ReminderValidationTest`、`BootReceiverRecoveryTest`），0 failures/errors/skips。日志 SHA-256：`FFE1480201D3174020F82C2B1F27DEE026A13FDBFAFD55346C3B323AA30CDDC2`。 |
| 完整离线 Gradle 门禁 | `PASS` | Source boundary、**703/703 JVM tests / 94 suites / 0 failures/errors/skips**、Debug APK、AndroidTest APK 组装、`lintDebug`。88 actionable tasks（15 executed / 73 up-to-date）。日志 SHA-256：`60A989FCD993421A71BF36832D568BA4D6F0FF9364B724BF963A13821EFADAFE`。 |
| 全量单元测试强制重跑 | `PASS` | `:app:testDebugUnitTest --offline --rerun-tasks --console=plain`：**703/703、94 suites**；32 tasks executed。日志 SHA-256：`E547DA90980E2F6B74AFCC3E21D33C540750A9D26AD75ECE45C43982DC407342`。 |
| Skin Studio Python | `PASS` | **61/61**；日志 SHA-256：`833B367EF40748FBBC47B6D6D216881D7D417D1C2ADADF1540D1C8984C9BE78F`。 |
| Chrome/CDP Clear 源文件 E2E | `PASS` | Chrome 156 Headless；验证拒绝/允许/API<31 状态、发出 `openExactAlarmSettings`、原生恢复入口仍在。截图：[settings](../../.local/android-test/reports/separation-s0-st19-exact-alarm-20261030/e2e-final/settings-2026-10-29T20-45-26-843Z.png)。日志 SHA-256：`1463E5229343EEED3152565DE953D84C222E587341D9E1D5B4C61044FB2C7FA5`。结果 JSON SHA-256：`BAB7454910C4175A0271C71942A1343B8853277E17E62E3779D9624BB7FCC861`。 |
| Chrome/CDP Studio Preview Mock | `PASS` | 确认 Mock 接收精确闹钟动作但不会启动 Android 系统设置。日志 SHA-256：`1F76E27927EB691D114F22CF0C676CDC1FAC1BDFB5E17A6B5BF590D3D1E4E905`。 |
| JavaScript 语法与 Clear 包校验 | `PASS` | 4 个变更 JS 文件均通过 `node --check`。Canonical Clear 为 9 files / 392,541 bytes，源码、打包资源与 Debug APK asset SHA-256 一致：`b592cf94192b320d41e402616b22032e11f1e981138769c46fdc266279821683`；最终校验日志 SHA-256：`E07F6BEB505034FA081FF74D33CD4FAEDF49A04DE7DD1210D62FF8B429F21091`。 |
| APK 产物 | `PASS` | Debug APK 67,504,106 bytes，SHA-256 `9F5D6CAE8DD537811145118B15B65E073A29D5F14C69B4BC45D0EFAAE6233519`。AndroidTest APK 1,557,196 bytes，SHA-256 `9F8C04CABD4FCA21009773FDF0B8C0BC873CC9B424F8E4F6426E0DEE4CBB5B34`。AndroidTest APK **仅完成组装，未执行 instrumentation**。 |
| Android 设备/模拟器 | `NOT TESTED` | `E:\android-sdk\platform-tools\adb.exe devices -l` 返回空设备列表；`E:\android-sdk\emulator\emulator.exe` 不存在。环境证据日志 SHA-256：`0CC1C56C9158DC6240BB7B6B6E064ABBBC6892B1C14FB51792DB7BF7A2405024`。 |

证据均保存在忽略目录 `E:\Project\Powerful-Calendar\.local\android-test\reports\separation-s0-st19-exact-alarm-20261030\`；最终修复门禁文件为 `focused-recovery-v2.log`、`full-recovery-v2.log`、`full-rerun-recovery-v2.log` 和 `clear-verify-recovery-v2.log`。此前基础 E2E 日志、Python/Node 日志、设备检查及截图也在该目录。`git diff --check` 通过，未将本地日志或截图加入 Git。

## 尚存风险与签核

- 真实 Android WebView→Bridge→Host 链路、系统特殊访问设置页是否可启动、系统授权变化广播、Android Activity 在设备上的暂停/恢复、实际 AlarmManager 投递和厂商后台限制均 **NOT TESTED**。
- Robolectric 覆盖了权限拒绝广播触发恢复和 SecurityException 权限状态分类，但没有在 `setExactAndAllowWhileIdle()` 与权限检查之间注入真实 OS 并发撤权；该竞态行为仍需设备证据。
- `assembleDebugAndroidTest` 只证明 instrumentation APK 可组装，不构成设备测试结果。需要在 Android 12+ 设备上验证拒绝、设置授权、返回应用后状态刷新与提醒实际排程；设备/OEM 测试由用户执行。
- 当前验证支持 `S0-ST-19` 的 Host/浏览器行为，但不覆盖 S0 总体，也不授权 Native 移除或皮肤切换。

因此：可验证的软件子项 **PASS**；S0-ST-19 整体维持 **`PARTIAL / HOLD`**，待 Android 系统设置和设备提醒验证后再签核。

## Git 交付

- 实现与测试：`739ff066dfd9969fde7d9ced32f579793b594917` — `fix(分离): 补齐精确闹钟设置与恢复`，已推送到 `origin/codex/separation-s0-timeline-drag`。
- 恢复边界补充：`5b2e987cba4074d7e81337683f6d9495b8f965cb` — `fix(分离): 完善精确闹钟恢复链路`，已推送到同一分支。
- 本 Issue 报告、矩阵与进度记录另行提交，避免和实现混在一个提交中。
