# PC-ISSUE-PHASE4-D05 — Android 15 AVD 提醒生命周期分类

日期：2026-10-25

状态：**AVD 平台行为分类完成；PowerCal 端到端与实体/OEM 验收 BLOCKED；未确认生产缺陷**

## 调查结论

最初的“杀后台后未提醒”不足以判定 PowerCal 故障，因为 Home 退后台、从最近任务移除、进程不存在、系统强行停止、系统重启和厂商省电清理是不同状态。

PowerCal 源码中，`ReminderScheduler.reconcile()` 从持久调度记录重建系统闹钟；`BootReceiver` 声明处理 `BOOT_COMPLETED`、应用替换、时区/时钟变化及精确闹钟权限变化；应用启动时也执行 reconcile。`PC-ISSUE-170` 已证明 API 35 AlarmManager 可以向产品进程投递并调用生产提醒处理器，`PC-ISSUE-093/094` 覆盖 BootReceiver 的 Robolectric 异步重建分支。

Android 15 会在应用进入 stopped state（例如用户强行停止）时取消该包的 PendingIntent；用户通过操作退出 stopped state 后，系统发送 `ACTION_BOOT_COMPLETED`，供应用重建调度。AlarmManager 的注册在设备关机重启后会清除。[Android 15 stopped-state 行为](https://developer.android.com/about/versions/15/behavior-changes-all?hl=zh-cn)，[AlarmManager API](https://developer.android.com/reference/android/app/AlarmManager)。这些是平台规则，不构成 PowerCal 端到端验收结果。

## AVD 探测方法

- 设备：API 35 `emulator-5556`。
- 隔离包：`dev.powerfulcalendar.test`；使用随机 32 位 token、该测试包自己的 Activity/Receiver 和 cache marker。
- 闹钟：`AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, ...)`，每次约 60 秒后触发。它是用于区分 Android 生命周期状态的合成闹钟；不是 PowerCal 的 exact alarm、Scheduler、生产 Receiver 或通知。
- Home、最近任务移除、进程不存在与 force-stop 分组依次执行。最近任务通过系统 Recents 卡片实际滑除；force-stop 只针对测试包，未 force-stop PowerCal。

## 结果

| 场景 | AVD 观察 | 判定范围 |
|---|---|---|
| Home 退后台 | 测试包 Receiver 写入 marker；名义触发 `1792870034562`，收到 `1792870079559`，约晚 45 秒 | Android 15 合成包投递 PASS；非 PowerCal 提醒 PASS |
| 从最近任务移除 | 系统 Recents 卡片 `[173,207][907,2006]` 被滑除并从任务列表移除；marker 收到。名义触发 `1792870168141`，收到 `1792870213145` | Android 15 合成包投递 PASS；非 PowerCal 提醒 PASS |
| 进程不存在 | `am kill` 初次未终止仍在 Recents 的探测 Activity；仅移除本探测任务后，触发前 PID 为空，Receiver 投递后进程 PID 为 `4710` 并写入 marker | 进程不存在时 Android 启动目标测试包并投递 PASS；不是对系统任意杀进程策略的完整证明 |
| 系统 force-stop | 只停止 `dev.powerfulcalendar.test`；状态变为 `stopped=true`，PendingIntent 从活跃闹钟列表消失，触发后 120 秒无 marker。重新启动后状态变回 `stopped=false`，原 alarm 仍未投递；手动重建原 PendingIntent 后收到 marker | Android 15 合成包 force-stop 清理行为符合平台语义；没有测试 PowerCal 的 `ACTION_BOOT_COMPLETED` 重建 |
| 系统重启 / OEM 省电策略 / PowerCal 通知与 FIRED/MISSED | 本探测未执行 | NOT TESTED；PowerCal 真机/OEM 由用户验收 |

以上 marker 都写在测试包 cache；探测未创建日历事件或通知，也没有更改 PowerCal 的权限、数据库内容或闹钟。测试后探测 cache 为空、D05 Recents 任务已移除、测试包 `stopped=false`。测试包 APK 前后 SHA-256 均为 `4D04D8DF1CA9D91516146BD62E4F6E630E7CF946A1ABF1217F66C8E035965E8C`；PowerCal APK 为已知基线 `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1`。PowerCal 数据库文件名仍为 `powercal-v2.db` 与 `powercal-v2.db-journal`；本次只比较文件名，没有读取数据库内容。未卸载应用、清除 app data 或 wipe AVD。

## 回归与证据

最终 D05 harness 变更后的完整离线 Gradle 门禁为 **BUILD SUCCESSFUL**：522/522 JVM tests、70 suites、0 failures/errors/skips、88/88 tasks；lint 0 errors / 56 warnings。该构建包含仅用于 AVD 分类的 AndroidTest 探针，不包含生产提醒源码修改。

本地证据目录：`.local/android-test/reports/freeze-20261025/pc-d05-background/`。

- 完整 Gradle 日志 SHA-256：`04BEF079D03C91266A25D1CE74CD217BB473114B8C25B98794E01E80C8BA0D7D`。
- AndroidTest APK（含探针）SHA-256：`9FAA01339612CA2DF1871CD047512C282F19339C1E5A7766E4E512E80B0FDFFD`；探针 Activity 源 SHA-256：`B4C84B3CCC8446BC7692B405F4731C9F7AE010ADBF04B4E671C92D4DF078A253`；Receiver 源：`969D0906601682BBFE7769BFF8B9643FE769A48BD7B54E82661000C3C3460D9B`。
- Home marker SHA-256：`0A5BA395B7C7D377C4DDCAB5B2E5687CFE5AA3F0BCE479E8FF7B6AF7008A84AD`；Recents marker：`D1F875939B9E3B36F509B51412BE3E789EE2B72DC71A1452C402DFB7375EDD89`；进程不存在投递摘要：`D25602A967EBCF20D88ECEC98E3E45D09CFEE95978D8F98942E377C9FA60F088`。
- force-stop 状态 SHA-256：`49C3B4A9AEDF2746BC55F33055130FAA2EA84B969C4E91F0C8BDD141FD8F5072`；无投递观察：`D27257D25FAA955F5EC90B4BCE8ED05381AA6653424AB56C91F472DCA45A621B`；手动重建后 marker：`950118B9DF7C56058D8A7745DD07901A68C842BD56CE262CEE75620BC90FF32D`；探测任务清理：`A243D271CBADCD1933EE042028A3D061A2F35781A68F614FD9F04489CCDFDAAF`。

首次探测脚本在 alarm 排程前遇到 instrumentation 上下文权限问题；Kotlin 辅助程序也有一次 AndroidTest 运行时依赖错误。这些是 harness 失败，未启动 alarm。`am task remove` 在该 ADB 版本不支持，随后改用有 bounds 记录的系统 Recents 手势完成场景。失败现场均保留在上述本地证据目录，未计为产品 PASS 或 FAIL。

## 剩余门槛

D05 仍未完成：本轮没有用 PowerCal 的真实 5 分钟未来提醒跨场景验证生产调度、权限、通知渠道、`FIRED/MISSED` 和 `BootReceiver` 实际 OS 分发；也没有 API 35 系统重启或 OEM 省电设备测试。现有 AVD 探针只证明标准 Android 的状态差异，不能覆盖用户报告的 PowerCal 现象。因此 D05 保持 **BLOCKED / PARTIAL**，没有生产代码修复，也不宣称本 Issue 的产品行为 PASS。整体核心冻结保持 **HOLD**。
