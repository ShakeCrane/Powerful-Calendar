# PC-ISSUE-004 — 横屏时间轴灰线与标签拥挤

日期：2026-10-23（北京时间）
优先级：P2
状态：**PARTIAL / HOLD** — 灰色装饰线和标签遮挡已修复并通过 API 35 模拟器回归；真机、IME 和 TalkBack 实际触摸交互仍缺验收证据。

## 复现与预期

- 需求来源：`docs/agent-handoff/CORE_FREEZE_TASK_UNDERSTANDING_2026-10-23.md` 的显示适配/无障碍检查项，以及 Goal Mode 对时间轴视觉元素的约束。
- 环境：Android 15 / API 35 隔离 AVD `emulator-5556`，横屏 UI 2400×1080，字体缩放 1.0；显示时区 GMT。原 `emulator-5554` 未操作。
- 修复前截图 `.local/android-test/reports/freeze-20261023/issue004-landscape-current.png`：灰色淡横线与现时刻区域重叠，刻度标签也靠近现时刻徽标。SHA-256：`BBE2CE3B851D16D8AA01A6F6E639E28CB6BFC92307A2E2F7925080D14AF3D86F`。
- 预期：移除额外灰色装饰线，保留中央竖直刻度尺、数字与短刻度、蓝色现时刻线、午夜虚线分隔；现时刻标签避开刻度文本和事件卡片。

## 根因

`PowerTheme.kt::axisBackground()` 在渐变中心叠加了 `gridLine` 颜色（alpha 0.05），形成额外的横向灰带。现时刻徽标固定放在现时刻线上，没有检测刻度文本/事件卡片；窄屏或大字体下刻度文本也可能互相挤压。中央刻度尺只有描述语义，没有 `onClick` 语义动作，因此无障碍树将其标为不可点击。

## 修复

- 删除渐变中心的灰色停靠点；原生时间轴绘制、蓝色现时刻线和午夜虚线分隔保持。
- 现时刻徽标搜索不遮挡刻度文本和事件卡片的位置；拥挤时保留短刻度并省略互相覆盖的时间文字。
- 空时间轴提示在低于 480dp 或字体缩放高于 1.3 时隐藏，避免占用横屏小视口。
- 中央尺保留物理双击手势，并新增可访问的语义点击动作，二者调用同一 24 小时复位逻辑。
- 未修改数据库、事件数据或设计资产；代码提交：`16e00d55435c5029e07784ec5d945dfd7fb37ac5`。

## 验证证据

| 检查 | 实际结果 | 证据 |
|---|---|---|
| 最新 Debug APK 横屏截图 | PASS；未见额外灰色中心灰带；蓝色现时刻线、竖直刻度尺和午夜虚线仍在 | `.local/android-test/reports/freeze-20261023/issue004-accessibility-device-regression/landscape-fixed.png`，SHA-256 `8644124407D99343364C8D80A18840F594F5B5222820178C576F6D32A802A7F3` |
| 最新 APK UI 树 | PASS；复位刻度尺节点 `clickable=true`，bounds `[1137,326][1263,807]` | 同目录 `landscape-fixed.xml`，SHA-256 `22BB5E2C0A085F8AFABE08DEF82F9F20628450BA2197F9E9C5D40BB1B3CB4C11` |
| 视口/字体矩阵 | PASS：纵屏 1x/2x、横屏 1x/2x、320dp 小屏；显示设置测试后恢复 | `.local/android-test/reports/freeze-20261023/issue004-device-regression/matrix.json`；截图 `portrait-1x.png`、`portrait-2x.png`、`landscape-1x.png`、`landscape-2x.png`、`small-320dp.png` |
| ADB 时间轴手势 | PASS：缩放至 16 小时/屏，再双击中央尺复位 24 小时/屏 | 同目录 `matrix.json`（`zoomed_to_16h=true`、`reset_to_24h=true`）、`gesture-reset.png`，SHA-256 `83A15DE0FCF6D2F6D21D9105A3E62556511D87374F84D9DB8A9AD3023BF48651`。此手势证据来自语义点击补丁之前的同一视觉修复构建；最新构建的语义复位动作另由下一项验证。 |
| 无障碍语义回归 | PASS；语义树有点击动作，执行动作调用复位回调 | `TimeAxisAccessibilityTest.rulerExposesAnAccessibleResetAction`；最终完整 JVM 套件包含该测试 |
| 完整工程回归 | PASS：348 项 JVM 测试，0 failures / 0 errors / 0 skipped；`verifySkinSourceBoundary`、`assembleDebug`、`lintDebug` 成功；Lint 0 errors、56 warnings | `.local/android-test/reports/freeze-20261023/issue004-full-regression-accessibility-final.log`，SHA-256 `620F96690514490EA0694B7FDCFE7EDBE5F6866BF0D49A4D763CA71C76037394` |
| 最终测试 APK | SHA-256 `178AACEB9D505A7C3BCD5680A3E8F98A5D7C7251A9D44EEB1FB66202D600E522` | `app/build/outputs/apk/debug/app-debug.apk` |

TalkBack 服务在 API 35 模拟器上曾成功绑定，系统报告 `touchExplorationEnabled=true`；Android Accessibility Suite 的通知权限提示反复覆盖应用界面。该可选通知权限未授予，系统权限及显示设置已恢复；因此没有把被系统弹窗遮挡的画面算作 TalkBack 应用交互通过。最新 APK 的普通 UI 树和 Compose 语义动作测试通过，但 TalkBack 实际双击、真机触摸命中、IME 遮挡及长时稳定性仍是 **NOT VERIFIED / HOLD**。字体 2x 下工具栏有换行，需结合真机可读性再审。

迭代记录：初轮语义测试以普通 `performClick()` 调用后未触发断言回调；测试改为直接执行 `SemanticsActions.OnClick` 后，目标用例及最终全量回归通过。初轮失败日志 `.local/android-test/reports/freeze-20261023/issue004-full-regression-accessibility.log` 保留作审计记录。隔离 AVD 已正常关机（保留其磁盘数据），`emulator-5554` 保持运行。

## Git 与结论

- 实现提交：`16e00d55435c5029e07784ec5d945dfd7fb37ac5`（中文 Conventional Commit）；报告和冻结总表在随后文档提交中记录。
- 代码已完成普通回归；本 Issue 仍为 **PARTIAL / HOLD**，不可据此判定核心冻结 GO。
- 继续项：可取得 Android 真机时补做 TalkBack 实际激活、横屏触摸命中和 IME 遮挡检查；之后继续处理核心冻结中尚可自主推进的验收缺口。
