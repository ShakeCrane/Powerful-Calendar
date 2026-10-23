# PC-ISSUE-004 — 横屏时间轴灰线与标签拥挤

日期：2026-10-23（北京时间）
优先级：P2
状态：**PARTIAL / HOLD** — 背景灰带已在后续复查中彻底移除并通过 API 35 回归；默认方向已锁定竖屏。真机、IME、TalkBack 和平台强制覆盖方向仍缺验收证据。

## 复现与预期

- 需求来源：`docs/agent-handoff/CORE_FREEZE_TASK_UNDERSTANDING_2026-10-23.md` 的显示适配/无障碍检查项，以及 Goal Mode 对时间轴视觉元素的约束。
- 环境：Android 15 / API 35 隔离 AVD `emulator-5556`，横屏 UI 2400×1080，字体缩放 1.0；显示时区 GMT。原 `emulator-5554` 未操作。
- 修复前截图 `.local/android-test/reports/freeze-20261023/issue004-landscape-current.png`：灰色淡横线与现时刻区域重叠，刻度标签也靠近现时刻徽标。SHA-256：`BBE2CE3B851D16D8AA01A6F6E639E28CB6BFC92307A2E2F7925080D14AF3D86F`。
- 预期：移除额外灰色装饰线，保留中央竖直刻度尺、数字与短刻度、蓝色现时刻线、午夜虚线分隔；现时刻标签避开刻度文本和事件卡片。

## 根因

`PowerTheme.kt::axisBackground()` 原先在渐变中心叠加 `gridLine`（alpha 0.05）。首次修复移除了这个色标，但仍保留 42%–58% 的 `surfaceContainerLow` 平带；用户复查后确认仍有淡横带，因此首次视觉结论不充分。后续将轴背景改为单色主题背景，才彻底去掉位置相关的装饰色带。现时刻徽标原先固定放在线上，没有检测刻度文本/事件卡片；窄屏或大字体下刻度文本也可能互相挤压。中央刻度尺曾只有描述语义，没有 `onClick` 语义动作，因此无障碍树将其标为不可点击。

## 修复

- 后续将 `axisBackground()` 改为 `SolidColor(scheme.background)`，不再渲染任何纵向位置色标；蓝色现时刻线、中央刻度尺和午夜虚线分隔保持。
- 现时刻徽标搜索不遮挡刻度文本和事件卡片的位置；拥挤时保留短刻度并省略互相覆盖的时间文字。
- 空时间轴提示在低于 480dp 或字体缩放高于 1.3 时隐藏，避免占用横屏小视口。
- 中央尺保留物理双击手势，并新增可访问的语义点击动作，二者调用同一 24 小时复位逻辑。
- `MainActivity` 声明固定 `screenOrientation="portrait"`，不使用 sensor-based orientation。API 35 隔离设备收到 90° 用户旋转请求时仍保持当前旋转 0 和 1080×2400 竖屏，测试后恢复自动旋转设置。
- 未修改数据库、事件数据或设计资产；代码提交：`16e00d55435c5029e07784ec5d945dfd7fb37ac5`。

后续补正实现提交：`abde8e2900b507cf1a50730b22de8ec55e4bac38`（移除残留背景色带并锁定竖屏）。

## 验证证据

| 检查 | 实际结果 | 证据 |
|---|---|---|
| 首次移除中心色标后的横屏截图 | **PARTIAL；当时未识别残留的 42%–58% 背景平带，用户复查证明视觉签核过早** | `.local/android-test/reports/freeze-20261023/issue004-accessibility-device-regression/landscape-fixed.png`，SHA-256 `8644124407D99343364C8D80A18840F594F5B5222820178C576F6D32A802A7F3`；补正见下节 |
| 最新 APK UI 树 | PASS；复位刻度尺节点 `clickable=true`，bounds `[1137,326][1263,807]` | 同目录 `landscape-fixed.xml`，SHA-256 `22BB5E2C0A085F8AFABE08DEF82F9F20628450BA2197F9E9C5D40BB1B3CB4C11` |
| 视口/字体矩阵 | PASS：纵屏 1x/2x、横屏 1x/2x、320dp 小屏；显示设置测试后恢复 | `.local/android-test/reports/freeze-20261023/issue004-device-regression/matrix.json`；截图 `portrait-1x.png`、`portrait-2x.png`、`landscape-1x.png`、`landscape-2x.png`、`small-320dp.png` |
| ADB 时间轴手势 | PASS：缩放至 16 小时/屏，再双击中央尺复位 24 小时/屏 | 同目录 `matrix.json`（`zoomed_to_16h=true`、`reset_to_24h=true`）、`gesture-reset.png`，SHA-256 `83A15DE0FCF6D2F6D21D9105A3E62556511D87374F84D9DB8A9AD3023BF48651`。此手势证据来自语义点击补丁之前的同一视觉修复构建；最新构建的语义复位动作另由下一项验证。 |
| 无障碍语义回归 | PASS；语义树有点击动作，执行动作调用复位回调 | `TimeAxisAccessibilityTest.rulerExposesAnAccessibleResetAction`；最终完整 JVM 套件包含该测试 |
| 完整工程回归 | PASS：348 项 JVM 测试，0 failures / 0 errors / 0 skipped；`verifySkinSourceBoundary`、`assembleDebug`、`lintDebug` 成功；Lint 0 errors、56 warnings | `.local/android-test/reports/freeze-20261023/issue004-full-regression-accessibility-final.log`，SHA-256 `620F96690514490EA0694B7FDCFE7EDBE5F6866BF0D49A4D763CA71C76037394` |
| 最终测试 APK | SHA-256 `178AACEB9D505A7C3BCD5680A3E8F98A5D7C7251A9D44EEB1FB66202D600E522` | `app/build/outputs/apk/debug/app-debug.apk` |

## 用户复查后的补正证据（2026-10-23）

用户指出淡灰横线仍在。复核发现此前只删了 `gridLine` 色标，42%/58% 的 `surfaceContainerLow` 平带还在；现已移除整段渐变，改为均匀主题底色。API 35 横屏图 `.local/android-test/reports/freeze-20261023/issue004-gray-band/landscape-after.png` 中，画布外样本（x=100/600/1800/2300，y=400/480/550/566/620/700）全部为 RGB `(243,245,250)`。中心当前时间线上采样为 RGB `(36,87,214)`，即主题蓝 `#2457D6`；边缘是这条蓝线的透明渐变，不是额外灰线。午夜分隔线仍为虚线。

| 补正检查 | 结果与证据 |
|---|---|
| 单色背景断言 | PASS：`TimelineBackgroundTest.timelineBackgroundHasNoHorizontalWashOrBand`；背景 brush 为 `SolidColor(MaterialTheme.colorScheme.background)` |
| 固定竖屏静态合同 | PASS：`MainActivityOrientationTest.activityRequestsFixedPortraitWithoutSensorRotation`；manifest `screenOrientation="portrait"`，代码中未调用 sensor orientation API |
| 系统旋转请求 | PASS（API 35 AVD）：设置 `USER_ROTATION=90` 且关闭自动旋转后，`mCurrentRotation=ROTATION_0`，应用截图仍为 1080×2400；证据 `forced-landscape-window-state.txt`、`forced-landscape-request.png`。测试后恢复 `accelerometer_rotation=1,user_rotation=0` |
| 方向截图 | 竖屏 `.local/android-test/reports/freeze-20261023/issue004-gray-band/portrait-after.png`；横向布局快照 `.local/android-test/reports/freeze-20261023/issue004-gray-band/landscape-after.png`（用于强制横向兼容布局检查）；两图像素与旋转证据汇总 `pixel-check.json` |
| 完整回归 | PASS：54 个测试套件、364 项，0 failures/errors/skipped；`verifySkinSourceBoundary`、`assembleDebug`、`lintDebug` 成功；Lint 0 errors/53 warnings。日志 `.local/android-test/reports/freeze-20261023/issue004-gray-band/full-regression-portrait-lock-final.log`，SHA-256 `F0D3C2EABDC7452949D88E7C78D4E8A9964E4DB461C28D20EF57C3CA293F94B1` |
| 最终 APK | SHA-256 `B0AB218FF70D696E25B570F0151BEC0F58B07FE545A045EE976EBD4F1173CAE1` |

Robolectric 两次 `captureToImage()` 尝试因窗口捕获超时失败（日志分别为 `targeted-background-test.log` 和 `targeted-background-test-android-rule.log`），不是背景像素断言失败；最终以单色 brush 纯测试和真实 API 35 截图像素采样验证。

TalkBack 服务在 API 35 模拟器上曾成功绑定，系统报告 `touchExplorationEnabled=true`；Android Accessibility Suite 的通知权限提示反复覆盖应用界面。该可选通知权限未授予，系统权限及显示设置已恢复；因此没有把被系统弹窗遮挡的画面算作 TalkBack 应用交互通过。最新 APK 的普通 UI 树和 Compose 语义动作测试通过，但 TalkBack 实际双击、真机触摸命中、IME 遮挡及长时稳定性仍是 **NOT VERIFIED / HOLD**。字体 2x 下工具栏有换行，需结合真机可读性再审。

迭代记录：初轮语义测试以普通 `performClick()` 调用后未触发断言回调；测试改为直接执行 `SemanticsActions.OnClick` 后，目标用例及最终全量回归通过。初轮失败日志 `.local/android-test/reports/freeze-20261023/issue004-full-regression-accessibility.log` 保留作审计记录。隔离 AVD 已正常关机（保留其磁盘数据），`emulator-5554` 保持运行。

## Git 与结论

- 实现提交：`16e00d55435c5029e07784ec5d945dfd7fb37ac5`（中文 Conventional Commit）；报告和冻结总表在随后文档提交中记录。
- 代码已完成普通回归；本 Issue 仍为 **PARTIAL / HOLD**，不可据此判定核心冻结 GO。
- 继续项：可取得 Android 真机时补做 TalkBack 实际激活、横屏触摸命中和 IME 遮挡检查；之后继续处理核心冻结中尚可自主推进的验收缺口。
