# PowerCal 核心冻结冲刺：412d9d3 候选包和门禁审计（2026-10-25）

**结论：BUILD VERIFIED；CORE FREEZE 仍为 HOLD。本轮没有真机操作或执行 Android instrumentation。**
完整应用源码基线：`412d9d31e8de4169fd6c4156b58ea5184247535b`，版本 `0.9.5-rc.1` / versionCode 905，包名 `dev.powerfulcalendar`，minSdk 26，targetSdk 35。

## 可直接用于真机复验的 Debug APK

- 文件（台式机）：`.local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-412d9d3-debug-core-HOLD.apk`
- SHA-256：`88D812BBDE862A6CA55F301EE65F45119AB1AF1F4D6443A3E1970498D98C5486`
- 文件字节：66,151,209
- Android Debug 签名：apksigner **Verifies**, v2 验证通过；命令退出码 0；Zip `testzip` 无损坏条目。
- 仅作测试安装，不是 Release 签名；已有不同签名的用户数据不可直接卸载。
- AndroidTest **测试 APK**（只为隔离/自动化使用，不是供用户安装的主 APK）：`.local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-412d9d3-androidTest-debug.apk`，SHA-256 `268EAA8C7E0B624E73A560143FB8DD8798D183875723A86971F31940B21BA459`。

## 本轮真实测试与源码隔离

| 运行范围 | 结果 | 备注 |
| --- | --- | --- |
| 主工作区强制全任务（含 AndroidTest APK） | FAIL | 被未经提交的 `D05ProductionReminderLifecycleInstrumentedTest.kt:142:17` 未导入 `assertFalse` 阻断；未修改该文件，故不归责于 HEAD |
| 主工作区核心门禁（--rerun-tasks） | **PASS** | 544/544 JVM、75 suites、59 tasks；额外 1 项为未跟踪的 `DraftDeckFeedbackTest.kt` |
| HEAD `412d9d3` 全新 detached worktree | **PASS** | **543/543 JVM、74 suites、88/88 Gradle tasks**，Debug 和 AndroidTest APK、Lint、skin source boundary 通过 |
| 隔离与主工作区 Debug APK SHA | **完全一致** | 说明交付包来自已提交的可复现应用源码 |
| Android Lint | 0 error，53 warnings（隔离已提交源码） | 兼容/弃用等警告仍需风险分类，不把 0 warning 当冻结前提 |
| 模拟器/实体测试 | **NOT RUN** | 上一轮 Android connected-test 曾影响 AVD 数据，本轮禁止无保护的仪器测试 |

命令（在 HEAD 纯净检出中）：
```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
```

- 日志：`.local/android-test/reports/final-freeze-candidate-20261025-412d9d3/committed-source-isolated-gate.log`
- 日志 SHA-256：`432DA0DA23889A24EC419B79789F51F1D170365B1C01C02C4B01D8812589306B`
- 74 个 JUnit XML：同目录 `junit-isolated-committed/`
- 原始 Lint XML：同目录 `lint-isolated.xml`
- 主工作区两次构建的原始日志也保留在同一目录。
- 临时 detached worktree 在复制完上述证据后已删除；其他既有 worktrees、`design/`、受保护的 D05/DraftDeckFeedbackTest/PC-OBS-005、并行验收 CSV 均未触碰。

## 验收台账状态与矛盾

核对时 `docs/CORE_FREEZE_ACCEPTANCE_LEDGER.csv` 总计 **218 行，其中 217 项活动项：205 PASS / 10 BLOCKED / 1 NOT TESTED / 1 SPEC UNRESOLVED，另 1 RETIRED**。历史 209 原始 ID 未恢复，均为可追溯重建项；这个缺口不能伪装为历史逐项验证。

**确证冲突**：`REC-P1-002` 现有 `action/expected` 仍是旧版“Today=当前时刻、保留 zoom”，却标 PASS。最新产品裁决及源码 `docs/issues/PC-ISSUE-P02-TODAY-CAMERA.md` 是 **“本地今日中午 + 重置 24 小时”**，已通过固定 Clock 及 Compose 双入口自动回归。此行需要**台账所有者**修正 action、expected、actual、evidence/source SHA，保留历史审计记录；本轮不覆盖并行工作中的 CSV，也不擅自将其更新为 GO。

尚存 12 项未签核 P1：`REC-P1-047`、`048`、`090`（真实 AI Provider），`063`、`067`、`069`、`070`、`075`、`076`、`086`（真机/无障碍/厂商行为），`081`（第三方 ICS），`068`（纯日期 Deadline 规格）；此外 `002` 为陈旧签核记录，应在最终核定前先更正。

## 用户真机回归与下一步

详细操作表：`docs/agent-handoff/PHYSICAL_QA_LATEST_CORE_RETEST_2026-10-25.md`。优先回归此前旧 APK 上失败/整改的行为：

1. 「今日」=本地中午+24h；不混淆当前时间蓝线。
2. 新建编辑器打开、无修改退出不留草稿；差异保存/退出幂等；默认时间一致。
3. 重复事件“仅本次/本次及以后/整个系列”范围编辑和保存。
4. 输入法弹出后，草稿“发布/丢弃”控件仍可用。
5. 后台、最近任务划掉、系统 force-stop、开机后提醒分别观察（不可把厂商 OS 限制当作无条件应用 Bug）。
6. 折叠标题 TalkBack、实体小屏/大字体/减少动画。
7. 有效 Provider 的多轮结构化审查卡（未授权绝不发布）、独立 ICS 外部导入/重新导出。

**阻塞项不因此次全门禁通过自动解除**。尤其 `REC-P1-068` 的 date-only Deadline 时间轴位置和完整播报需要所有者明确裁决；没有可用真实 Provider 时相关项保持 BLOCKED/NOT TESTED，而非使用 stub 代替。

正式基线冻结仅在修正台账、关闭全部必需 P0/P1、验证目标真机/Provider/ICS 条件并审计剩余风险后才可宣告 GO。
