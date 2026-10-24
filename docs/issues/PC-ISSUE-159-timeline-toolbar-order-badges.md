# PC-ISSUE-159：时间轴顶栏动作顺序与剩余数徽标

日期：2026-10-24
结果：**PASS（限 API 35 原生竖屏模拟器子项）**

## 范围

为重建验收项 `REC-P1-032` 和 `REC-P1-033` 补充 Android 仪器测试，覆盖时间轴顶栏 Today → Memo → Todo → New 的顺序、四个入口的回调，以及 Memo/Todo 剩余数量徽标。测试直接组合生产 `TimelineScreen`，使用空事件列表和合成计数，不改生产代码、不读写应用业务数据库，也不调用 AI。

## 测试与实测结果

- API 35 `emulator-5556`，原生竖屏 1080×2400、420 dpi、`user_rotation=0`。定向 AndroidJUnitRunner **2/2 PASS**。
- 顶栏动作的 `boundsInRoot` 横向中心（像素）依次为 Today `658`、Memo `774`、Todo `890`、New `1006`。按顺序触发四项后，回调轨迹为 `[today, memo, todo, new]`；New 菜单中的“活动”项回调为 `ACTIVITY`。
- 用合成未完成数验证徽标：Memo `12`、Todo `7`。两项都通过无障碍描述和实际徽标文字断言。
- 完整离线 Gradle：491/491 JVM 测试、66 suites、0 failures / errors / skipped（含 490 个跟踪测试与受保护的既有未跟踪 `DraftDeckFeedbackTest.kt`）；源码边界、Debug 与 AndroidTest APK 组装通过；Lint 0 errors / 58 warnings。

## 证据

- 测试代码：[TimelineToolbarContractInstrumentedTest.kt](../../app/src/androidTest/java/dev/powerfulcalendar/ui/v2/TimelineToolbarContractInstrumentedTest.kt)
- 定向运行：`.local/android-test/reports/freeze-20261024/pc-issue-159-toolbar-targeted-postgradle-20261024.log`，SHA-256 `522A2DEB85ED4C7B18EBFB805D7C4EDBFA7DD5DB56C95705036CFA3A49445CD5`
- 几何与回调轨迹：`.local/android-test/reports/freeze-20261024/pc-issue-159-toolbar-geometry-20261024.txt`，SHA-256 `D2A2E5617C28B36F787DBB605E9CEDA85FFFA06E7363D00779E2A966416F3C2D`
- 完整 Gradle 日志：`.local/android-test/reports/freeze-20261024/pc-issue-159-toolbar-full-gradle-20261024.log`，SHA-256 `F1903B255787C632AC7B30B26D6C8746F601DE301DF453266C41D91256579737`
- `app/build/reports/lint-results-debug.xml` SHA-256：`44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F`
- Debug APK SHA-256：`EE7E4850A147AC3B3F21326DCB209B0967F14DE6990D56EE05A2976FEB252BCD`
- AndroidTest APK SHA-256：`E2B1FDBD3A92DBB32EA73762E28B58D06D5CE60E344DA868C3051DC8BE3EA3CA`
- 测试提交：`a2b701e9ca02ffd39643a7797932ca817d149d58`（已推送）

## 签核边界

台账将 `REC-P1-032/033` 标为 `PASS`，实际结果限定为上述 API 35 原生竖屏模拟器与合成计数。没有验证完整 MainActivity 路由、持久化计数或实体设备行为；这些路径不在本次证据范围内。台账结构校验 r25 记录 210 条活动重建项：193 PASS、0 FAIL、8 BLOCKED、3 NOT TESTED、6 SPEC UNRESOLVED，优先级 P0/P1/P2 为 144/65/1；历史 209 项映射数为 0，校验器 `product_behavior_tested=false`。

结构校验产物 `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r25-pc-issue-159-toolbar.json` SHA-256 `8A3096975A584A9A45133D6F7A0C6F40833F79BA55AD9E23889781B1A65F1FCE`；验收台账 SHA-256 `D3B1B72CC3DDC2B6E09C496D03AAAF12E1F021CCEBBBB16ADBB160A339392640`。结构校验不测试产品行为，也不改变冻结判定：整体仍为 **HOLD**。
