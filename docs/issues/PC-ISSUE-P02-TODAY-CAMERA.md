# PC-ISSUE-P02 — “今日”相机行为统一为本地中午与 24 小时视野

日期：2026-10-25

状态：**所有者规格已明确；纯相机转换、PowerCalApp Robolectric Compose 双入口回归与完整离线门禁 PASS；AVD/实体设备未测；冻结验收台账旧行待同步**。

## 规格裁决

用户明确选择：“今日中午，重置 24 小时”。从原生时间轴或任意 Web 皮肤触发“今日”时，使用动作发生时的当前时钟和配置时区，将视野锚点设为本地当天 12:00，并将跨度设为 24 小时。蓝色当前时间线继续表示真实当前时刻。该状态与首次打开相同，不保留此前缩放，也不将当前时刻移到视野中心。

此裁决取代早先 `PRODUCT_BASELINE_G1-G11.md` 的 Today=now 规则和 `REC-P1-002` 的旧预期。它不改变双击当前时间刻度尺的独立复位动作。

## 根因与修复

此前两条入口行为分叉：Web action 调用 `initialCamera()`，定位本地中午并设为 24 小时；原生按钮调用 `todayCamera(nowMillis, hours)`，定位精确当前时刻并保留缩放。原生按钮原先正是 `PC-ISSUE-143` 在此前 owner 规则下实现的行为；本次根据更新的 owner 裁决统一语义。

- `TimelineCoordinate.todayCamera(nowMillis, zone)` 现在复用 `initialCamera()`，确定性返回本地中午和 24 小时。
- `PowerCalApp` 的原生回调与 Web `today` action 都调用该共享转换；两者都在动作时更新当前时钟，Web 路径也同步 `nowMillis` 状态。
- `TemporalProtocolTest.initialAndTodayCamerasBothCentreLocalNoonAndResetTo24Hours` 使用固定 instant 和 `America/Los_Angeles`，验证同一 instant 在洛杉矶仍属前一日深夜时，Today 仍按该时区的当地日期定位 12:00；也验证跨度 24 小时且不同于精确当前 instant。
- `PowerCalCasualSuggestionComposeTest.nativeAndWebTodayActionsBothReturnToLocalNoonAnd24Hours` 驱动真实 `PowerCalApp`，先将视野缩到 36 小时并平移，再分别点击原生 Today 控件、派发 Web bridge Today action；两次都断言锚点等于状态中时区/当前日期的 12:00，跨度重置为 24 小时。
- 更新核心合同、产品基线和审计计划，记录新决定及其对旧 `REC-P1-002` 预期的取代。

## 测试证据

定向测试命令：

```text
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --console=plain --tests dev.powerfulcalendar.core.TemporalProtocolTest
```

结果：**44/44 PASS，0 failures/errors/skips**。

实际 PowerCalApp Robolectric Compose 定向回归：

```text
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --console=plain --tests dev.powerfulcalendar.ui.v2.PowerCalCasualSuggestionComposeTest
```

结果：**2/2 PASS，0 failures/errors/skips**，包括上述 native 与 Web bridge 双入口 Today 操作；JUnit XML SHA-256：`E2B719502456D7F179682514EDF522E71BE26659F0CD6083EB4F947AB4A5FD8E`。

完整离线门禁在 detached worktree（基点 `512cfd5ac5584a840725e5ecabfe9069f7c88ca9`，仅应用本 Issue 明列源码/测试/规格补丁）执行：

```text
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

- **543/543 JVM tests，74 suites，0 failures/errors/skips；88/88 tasks 成功**。
- source boundary、Debug APK、AndroidTest APK 成功；lint **0 errors / 53 warnings**。
- 完整日志 SHA-256：`2E58319ABC7A751232C3641C2D4ECD2663FFE3271A82CDED74609258D74A91FC`。
- `TemporalProtocolTest` JUnit XML SHA-256：`CF7133AC72FFD8718FFA0DF4F9EE8B08096D4C80E2A66BBC6C5B9C4E26020C98`。
- 生产/测试源码 SHA-256：`TemporalModels.kt` `6E24E38C4C03F97B6EEE6ED3E36D8B26A5B8A1E73225E9C5D6A78015B50D26B7`；`PowerCalApp.kt` `D5817AA204DB51EFF1DB10C97ECBAA9F663AE4116628A190B12A966E086472FD`；`TemporalProtocolTest.kt` `3B371034651A42CE9A5F40CA0505E41A3DCEC0A40C73E993794293EEBF2CB5DD`；`PowerCalCasualSuggestionComposeTest.kt` `AC1ACF04C0D9A2644774BB52D2729DCC666345F6AAD73178EB6F9F3DD66D33F2`。
- Debug / AndroidTest APK SHA-256：`88D812BBDE862A6CA55F301EE65F45119AB1AF1F4D6443A3E1970498D98C5486` / `268EAA8C7E0B624E73A560143FB8DD8798D183875723A86971F31940B21BA459`。
- lint XML SHA-256：`349499762A88D86EC39FD1B3DCA887C94C5922271FC85538B9E4F3CE122B9785`。

一次集成测试编译尝试因缺少 Compose test query/gesture imports 失败，修正 imports 后定向回归及完整门禁通过。首次隔离构建也因 worktree 没有本机 `local.properties` 而在 Gradle 配置阶段中止，未执行测试；补入仅含 Android SDK 路径的本机配置后完整门禁通过。上述两次失败均不是产品行为结果；首次 SDK 配置失败未留存独立日志文件。

## 验收边界与未解决项

- Robolectric Compose 已点击实际 `PowerCalApp` 原生 Today 控件，并通过真实 `WebSkinBridgeRuntime.onAction` 触发 Web 入口；验证的是 Compose JVM 仿真，不是 API 35 AVD 或实体设备。本轮没有操作在线 AVD/真机，也没有设备截图或 TalkBack 证据。
- 工作区的 `docs/CORE_FREEZE_ACCEPTANCE_LEDGER.csv` 在本 Issue 开始前已有其他 Agent 的未提交修改。该保护文件中的 `REC-P1-002` 仍记录旧的 now+preserve-zoom 结果，本轮未修改、暂存或覆盖；该行现已被所有者裁决取代，须由其变更所有者同步后才可作为当前签核引用。
- 整体冻结继续 **HOLD**；此项不覆盖实体设备、TalkBack 或其他核心验收缺口。

详细日志与 APK 位于 `.local/android-test/reports/freeze-20261025/pc-issue-p02-today-camera/` 及 `.local/android-test/worktrees/p02-today-512cfd5/`。
