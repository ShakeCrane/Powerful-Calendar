# PC-ISSUE-160：编辑草稿时切换深浅主题

日期：2026-10-24
结果：**PASS（限隔离 Compose 编辑器主题切换子项）**

## 范围

为重建验收项 `REC-P1-073` 增加 API 35 Android 仪器测试。测试在真实 Compose `NativeDraftDeckDialog` 中编辑合成 Activity 草稿，通过 `PowerCalTheme` 的浅/深主题输入触发主题重组，再核对卡片身份、未暂存标题和输入焦点。使用随机命名的临时 PowerStore 数据库；测试结束后关闭并删除该测试数据库。没有修改生产代码、默认业务数据或 Android 系统夜间模式。

## 实测结果

- `emulator-5556` API 35，原生竖屏 1080×2400。最终构建后的定向 AndroidJUnitRunner **1/1 PASS**。
- 编辑中的草稿 ID 始终为 `1`（测试专用数据库）；标题 `THEME_SWITCH_DIRTY_TITLE` 在控制器状态和屏幕上均保留。
- 浅色 Material 背景 `Color(0.9529412, 0.9607843, 0.98039216)` 切到深色背景 `Color(0.039215688, 0.050980393, 0.07450981)`；切换后标题输入框仍保持焦点。
- 完整离线 Gradle：491/491 JVM 测试、66 suites、0 failures / errors / skipped（含 490 个跟踪测试与受保护的既有未跟踪 `DraftDeckFeedbackTest.kt`）；源码边界、Debug 与 AndroidTest APK 组装通过；Lint 0 errors / 58 warnings。

## 证据

- 测试代码：[DraftDeckThemeSwitchInstrumentedTest.kt](../../app/src/androidTest/java/dev/powerfulcalendar/ui/v2/DraftDeckThemeSwitchInstrumentedTest.kt)
- 定向运行：`.local/android-test/reports/freeze-20261024/pc-issue-160-theme-switch-targeted-postgradle-20261024.log`，SHA-256 `B463A726A57DEA43369BA360C468EB2C73DF912AF73FA921074664E16DF48EF7`
- 主题与草稿状态轨迹：`.local/android-test/reports/freeze-20261024/pc-issue-160-theme-switch-trace-20261024.txt`，SHA-256 `59E5BC08D97E2B5E3A51C18E5B125823D77B0D328F0EDEFD2056AEE2B7E96328`
- 完整 Gradle 日志：`.local/android-test/reports/freeze-20261024/pc-issue-160-theme-switch-full-gradle-20261024.log`，SHA-256 `572B1C1FBC70AD7F1519A8408D764500949B344BBA8A3CD550820F1069208BCA`
- `app/build/reports/lint-results-debug.xml` SHA-256：`44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F`
- Debug APK SHA-256：`EE7E4850A147AC3B3F21326DCB209B0967F14DE6990D56EE05A2976FEB252BCD`
- AndroidTest APK SHA-256：`72EC1D24090FAF68E2AD2D11A1C8931A80726C4C7B08E3C7786D382D0EE1E823`
- 测试提交：`2a0ed99dacdf14f7c5558b390a9400502560ace5`（已推送）

## 签核边界

台账将 `REC-P1-073` 标为 PASS，限定于同一 Compose 编辑器中通过 `PowerCalTheme(darkTheme=...)` 从浅色切到深色。测试没有更改系统 `uiMode`，也没有运行完整 `MainActivity` 配置变化路径；这些不属于本次已签核证据。结构校验 r26 记录 210 条活动重建项：194 PASS、0 FAIL、8 BLOCKED、2 NOT TESTED、6 SPEC UNRESOLVED；P0/P1/P2 为 144/65/1，历史 209 项映射数为 0，校验器 `product_behavior_tested=false`。

结构校验产物 `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r26-pc-issue-160-theme.json` SHA-256 `5165AF1041F728B98B55DEE4A8A674CBC5FB7B4FEE235CFAD15914147B6238C3`；验收台账 SHA-256 `1851D775B93E5EC1ADEA5A12C4EF3E837511F2CA6E51E86DB55E801FCE5FED92`。校验器只检查矩阵结构，不测试产品行为；正式冻结仍为 **HOLD**。
