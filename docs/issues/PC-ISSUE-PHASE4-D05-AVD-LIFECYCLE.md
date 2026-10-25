# PC-ISSUE-PHASE4-D05 — Android 15 AVD 提醒生命周期分类

日期：2026-10-25

状态：**PowerCal 生产提醒路径已在 API 35 AVD 完成端到端及重启恢复验证；实体/OEM 验收仍 BLOCKED；未发现需修改的生产代码缺陷**
## Follow-up 2026-10-25 — API 35 生产提醒进程与系统重启恢复

本次在专用 `PowerCal_D05_Production_API35_20261025` AVD（`emulator-5562`，Android 15 / API 35）验证实际生产 Scheduler、manifest Receiver、持久化执行状态和通知。为隔离既有用户数据，通过 `-Ppowercal.d05QaSuffix=.d05qa572cf10` 构建独立包 `dev.powerfulcalendar.d05qa572cf10`；仅向该包授予通知与精确闹钟权限，正式 app id、数据库和权限均未改变。

- 普通进程终止：生产 ALARM exact schedule 在 `am kill` 后仍在 AlarmManager；到期后生产 `ReminderReceiver` 拉起进程并投递。验证 `lastExecution=FIRED`、`powercal-alarms` 活跃通知、`completedKeys` 为空。`arm-phase-instrumentation-final.log` SHA-256 `AB5566EB10B578EC46A9BAEC3A86635AF6CC51413CA492ED5799E8C8C1A98D6D`；`verify-delivery-instrumentation-final.log` SHA-256 `9C6777A18AE73CE43EECCCCFFB879F96B27D12FECCF039B2DCD88AAA18C52E12`。
- 用户强停后重开：只对隔离包执行 `am force-stop`，确认 stopped 状态与 PendingIntent 清除；显式重新启动应用后，生产 reconcile 重建 schedule。再次 `am kill` 后到期投递验证通过，执行状态和通知断言同上。仪器化验证日志 SHA-256 `83F4BA79D8AE8B14C81EC257463AC81E69AC06A0203C682609559811B9602E3C`；重开恢复记录 SHA-256 `027C8FC7C421E1EBEEDDE7BA6015579113C4EABCEB77F5343AB8BA70C9AC5601`。
- 系统重启：重启前唯一 PowerCal-owned PendingIntent 属于隔离 QA 包；原版 app/test 包无进程或闹钟，桌面在前台。真实 `adb reboot` 后，开机初期尚无 QA 闹钟，随后 logcat 记录 `dev.powerfulcalendar.d05qa572cf10/...BootReceiver` 因 broadcast 启动；BootReceiver 重建相同 exact alarm。隔离包变为 `stopped=false`，进程终止后闹钟仍在；到期后生产 receiver 投递。`system-reboot-delivery-instrumentation.log` 为 **OK (1 test)**，SHA-256 `C4AEF64F274B9E3FF31BD5315A7A5E70CDFE35C7CA3B2B74E13444B14ADAD9CA`；重启后的 BootReceiver 系统日志 `system-reboot-bootreceiver-logcat.txt` SHA-256 `06B06A9610A897E646157907E3AC99ED4EF4FCB11D042AB7B2237CBF4DCBE93A`；rearm/kill 记录 SHA-256 `C7EA96805FE8E42F43869C88E83B0EDBBDB95314F5C96B11309CB3D537B172A9`。
- 完成后 instrumentation 测试断言数据库 `FIRED`、通知 id/`powercal-alarms` channel、没有推断完成状态，并删除本次独立 QA 数据库。随后卸载且只卸载两个 QA 包。原版 `powercal-v2.db` 仍为 159744 bytes、journal 0 bytes，修改时间保持 `2026-10-24 23:06`；前后只比较文件元数据，没有读取数据库内容。清理记录 SHA-256 `D599467C71162771AC64E864BDF69A1414AA2C33F28097C1B50C9E2AE4ACCEE8`。

本次新增 AVD 测试与 build 隔离支持，不改生产提醒源码。QA APK 构建命令：`:app:assembleDebug :app:assembleDebugAndroidTest --offline --rerun-tasks --no-daemon --console=plain --project-prop powercal.d05QaSuffix=.d05qa572cf10`。完整默认 applicationId 门禁命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain`。重启后验证命令：`adb -s emulator-5562 shell am instrument -w -e class dev.powerfulcalendar.reminders.D05IsolatedProductionAlarmInstrumentedTest#productionManifestReceiverFiresAndPostsAfterHostKillsAppProcess dev.powerfulcalendar.d05qa572cf10.test/androidx.test.runner.AndroidJUnitRunner`。QA Debug / AndroidTest APK SHA-256 分别为 `8AB86E91FA465D2A7DE1701CA025E7A2EDFE555D2D4BED47703620214E47E967` / `C5E217F8AEADB2DF42F67B05A1C166551484CFE8BAC08EBEAB22C49510111895`。最终默认 app id 完整离线 Gradle 门禁 **543/543 JVM、74 suites、0 failures/errors/skips、88/88 tasks**，含 source boundary、Debug/AndroidTest APK 与 lint（0 errors / 53 warnings）；日志 SHA-256 `4710FC30C4B334ABA2E6B683DA8639FFC1E89FABB037D7CF434553AC80B4E184`。

两次初始 host-side preflight 过滤条件误报并安全拒绝重启，期间没有发出 reboot；收窄到 PowerCal-owned PendingIntent 行后才执行。没有触碰其他 AVD、没有 wipe/clear data，也没有运行实体设备测试。本节证据只覆盖标准 Android 15 AVD，不代表 OEM 省电策略或用户设备验收。`REC-P1-086` 继续 BLOCKED，整体冻结继续 HOLD。验收台账文件未在此变更中编辑，以保留主工作区内另一 Agent 的受保护并发修改；因此台账该行的旧证据摘要尚未反映本节新结果。

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

API 35 AVD 上的 PowerCal 生产提醒路径现已覆盖普通进程终止、用户强停后重开、真实系统重启后的 BootReceiver 恢复及到期通知投递；这些场景未发现生产代码缺陷。D05 仍为 **BLOCKED / PARTIAL**，因为用户实体设备与 OEM 省电策略未验证，不能由 AVD 结果代签。`REC-P1-086` 台账行仍保持原状态且摘要尚未更新；整体核心冻结保持 **HOLD**。

## 冻结台账与可追溯性

- 台账增加 `REC-P1-086`，类型为 `RECONSTRUCTED`，状态 `BLOCKED`；它只记录隔离测试包的 AVD 生命周期分类，明确不把结果归因到 PowerCal。
- 结构校验报告：214 行（213 活动重建项、1 退休项），200 PASS / 0 FAIL / 9 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2 为 145/67/1。历史 209 项仍未映射，`product_behavior_tested=false`。台账 SHA-256：`B010A4585FF55CA94C76CA9FF6791997F24B3BF16B7F07D70495CC4DDCC53892`；报告 JSON SHA-256：`E5153E2CEC176CD4ED32B516556447158313A07D5A6FCDD08AE286A16CBA4B15`。
- 结构校验器原先把风险依据日期固定为 2026-10-24，拒绝本次合法的 2026-10-25 新行。已将校验改为严格解析 ISO 日期，并增加 2 个 unittest；`python -m unittest tools.tests.test_validate_core_freeze_acceptance_ledger -v` 为 2/2 PASS，日志 SHA-256：`F761C217955B2DCCBB9E9921C5E179AF3286F01E8F07797CCA85F7D3EA11CBFF`。修复提交 `b4335b85763e450605e46d150672fa8d45f5093a`；D05 探针与初始报告提交 `de8a6723b02ea93fc77d826a3500803481c62639`。本节台账与进度补录随后单独提交。
- 完整 Gradle 日志 SHA-256：`04BEF079D03C91266A25D1CE74CD217BB473114B8C25B98794E01E80C8BA0D7D`；该构建包含 AndroidTest 探针，不代表生产提醒路径已经端到端通过。
