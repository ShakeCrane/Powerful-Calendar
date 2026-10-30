# PC-ISSUE-SEPARATION-ANDROID-ACTIVITY-RECOVERY

**日期：** 2026-10-30

**状态：** `PARTIAL / HOLD`

**验收项：** reconstructed `S0-ST-10`
**提交：** `4978ec999c34fa514d7cb2000a024d39fb1a4fd3` (`fix(分离): 保证原生紧急回退持久并反馈失败`)

## 范围与结论

本 Issue 检查皮肤无法操作时，`MainActivity.dispatchTouchEvent` 是否能切回受保护的原生 Tools 页面，是否会改写日历数据，以及选中原生皮肤的偏好设置写入失败时是否会误报成功。

API 35 Robolectric 已验证 Activity 的真实触摸分发入口和原生页面交接，Store 状态前后相同。负责人于 2026-10-30 签核保留两处屏幕位置交替的八次轻点规则，并要求提示文案不承诺识别实体手指。该产品定义已经明确；实际设备触摸输入、无障碍和冷启动仍未验证，因此 S0-ST-10 保持 PARTIAL / HOLD。

## 根因

1. recognizer 有独立纯策略测试，但没有测试穿过 `MainActivity.dispatchTouchEvent`、`SkinPackageManager` 和 `PowerCalApp` 原生 Tools 页的完整回退路径。
2. `SkinPackageManager.currentId()` 对损坏的 `selected` 偏好值直接调用 `getString`；类型错误会抛出 `ClassCastException`，使 Activity 恢复入口可能在切回原生前崩溃。
3. 原生选择使用 `SharedPreferences.apply()`，调用返回时无法确认偏好已同步写入。Activity 随后无条件显示恢复成功，写入失败会被误报。
4. Web 皮肤加载失败时的安全回退也忽略了原生选择持久化结果。
5. 初始合同与提示文案写成“双指、每指四下”，而 recognizer 按两个坐标区域交替计数，措辞超出了实现可判断的实体触点信息。负责人于 2026-10-30 确认保留位置交替规则，并修改提示文案。

## 修改

- `SkinPackageManager.currentId()` 捕获错误类型的偏好值，并尝试同步选择 Native，避免损坏偏好使恢复入口崩溃。
- 新增 `selectNativeForEmergencyRecovery()`：立即重置当前进程的活动皮肤；同步提交 `powercal-default` 并返回提交是否成功。提交返回 `false` 或抛异常时返回失败状态，不把它伪装成持久化成功。
- `MainActivity` 将提交结果传入 `PowerCalApp`。写入失败仍会进入当前进程的 Native Tools 页，但显示“未能确认回退设置已保存”的提示。
- Web 皮肤加载失败的 Native 回退使用同一同步接口；根据检查点和持久化结果显示相应提示。
- 新增真实 Activity 触摸分发回归。测试还原先前皮肤选择并重载注册表，避免在测试间留下偏好和活动注册表不一致。

## 验收记录

| 场景 | 结果 | 证据边界 |
| --- | --- | --- |
| API 35 Robolectric：同一位置连续点击不触发回退 | `PASS` | 通过 `MainActivity.dispatchTouchEvent` 发送实际 `MotionEvent` |
| API 35 Robolectric：两个空间位置交替，第 8 次进入原生 Tools | PASS | 每次使用 pointer ID 0，验证规则按两个坐标位置交替计数；设备实际触摸分发仍未验证 |
| 回退前后正式记录、身份数、序号水位、待处理数、提交数 | `PASS` | 集成测试快照相等；回退不写 Store |
| 错误类型的 `selected` 偏好 | `PASS` | `SkinPackageManagerTest` 验证恢复 Native 且不崩溃 |
| 同步偏好写入返回 `false` 或抛异常 | `PASS` | 注入失败写入器；函数报告失败并切换当前进程注册表至 Native |
| 持久化失败提示文案 | `PASS` | 纯函数测试；Activity 成功路径另验证 Tools 页及成功提示 |
| 新进程/强制结束后偏好值仍可恢复 | `NOT TESTED` | Robolectric 新 manager 仍在同一进程和偏好实例内；没有冷进程测试 |
| Android 设备实际触摸输入、TalkBack/无障碍行为、冷进程恢复 | NOT TESTED | 当前测试为 Robolectric；没有设备输入、辅助功能服务或冷进程证据 |
| Web 皮肤加载失败且同步偏好提交失败的完整 Compose 路径 | `NOT TESTED` | 已修正结果处理与文案，未添加该分支的端到端 Compose 回归 |

## 测试与构建证据

定向命令：

```text
.\gradlew.bat :app:testDebugUnitTest --tests dev.powerfulcalendar.ui.v2.ActivityRecoveryTest --tests dev.powerfulcalendar.ui.v2.SkinPackageManagerTest --offline --console=plain
```

结果：**17/17 PASS**（`ActivityRecoveryTest` 1 项、`SkinPackageManagerTest` 16 项）。日志：`.local/android-test/reports/separation-s0-st10-activity-recovery-20261030/targeted-final.log`，SHA-256 `45D4412BFDCCAEEB30967E2EA138EAB8787F2B8DC9F786895F7C159975DD9CDD`。

完整离线命令：

```text
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --console=plain
```

结果：源码边界、Debug APK、Lint 均通过；JVM **677/677**，**90 suites**，0 failures / errors / skipped；Lint **0 errors、53 warnings**。完整日志 `.local/android-test/reports/separation-s0-st10-activity-recovery-20261030/full-gradle-final.log`，SHA-256 `1770092AE808F228C08A61EDD6BD1C6A0427661B6F5FA333BA2A158F28EC5C58`。Lint 相比既有 52-warning 记录增加的两条 `UseKtx` 指向本次同步 `SharedPreferences.commit()`；其 Boolean 返回值用于防止将未确认持久化显示为成功。

Debug APK：67,500,112 bytes，SHA-256 `7E1FCEA722A7FC86F9792794B3560E24F5B448AE1BA2F559D54F0460315222F9`。

环境：API 35 Robolectric；`adb devices -l` 无在线设备；本地 SDK 没有 `emulator.exe`。本报告不声称 Android 模拟器、真机或 OEM 验收通过。中间一次测试实验额外启动 Activity 后，暴露出测试结束时提醒协调协程与 Robolectric SQLite 清理竞态；去掉该多余 Activity 启动、将提示判断改为无副作用单测后，最终定向和完整回归均通过。

## 剩余风险与验收状态

- **设备待测：** 两处位置交替规则已由负责人签核，提示文案已同步修正。实际 Android 触摸输入、TalkBack/无障碍行为和强制结束后的冷启动恢复仍未测试。

- **存储故障语义：** 同步提交失败时，本进程会进入 Native 并明确提示；系统重启后仍可能恢复此前选中的皮肤，用户需要再次选择 Native。未声称失败写入仍具有跨进程持久性。
- **Web 加载失败次路径：** 持久化失败提示已处理，但对应 Compose 故障注入用例未补齐。

因此，`S0-ST-10` 为 **PARTIAL / HOLD**。本 Issue 只签核 Activity 级回退路径、Store 无写入和错误偏好防崩溃这些已直接验证的子项，不构成 Android 设备签核或整体分离 GO。

## Follow-up — Skin Studio 静态合同与现行回退 API 对齐（2026-10-30）

后续运行 Skin Studio Python 全套时，`test_native_settings_and_hidden_activity_rescue_are_both_available` 因仍查找旧的 `select("powercal-default")` 失败。当前 `MainActivity` 已使用本 Issue 新增的 `selectNativeForEmergencyRecovery()`，并根据同步持久化结果发出 `nativeRecoverySignal`。这是测试断言落后于已提交实现，不是新生产缺陷。

仅更新静态合同测试，断言现行回退 API、持久化结果处理和 UI 信号均存在；未修改生产代码。定向测试 **3/3 PASS**，日志 `.local/android-test/reports/separation-s0-st14-ics-export-20261030/s0-st10-contract-focused.log`，SHA-256 `76AC5F02F0C2622ABEE14378532BD484DDA51BE57A07008056DAA8AE1DB815FB`。Skin Studio 完整 Python 套件 **61/61 PASS**，日志 `.local/android-test/reports/separation-s0-st14-ics-export-20261030/skin-studio-python-final.log`，SHA-256 `BE2E5662208C8735AE5D3D4045A781EBE975DCAC1534C5C68A2DFD62E52D14C2`。

独立测试提交 `4dce206`（`test(分离): 对齐原生回退静态契约`）已推送至 `origin/codex/separation-s0-timeline-drag`。该历史记录中的手势定义歧义已由本报告后续签核解决；设备触摸、无障碍和冷启动风险仍未覆盖，整体状态继续为 `PARTIAL / HOLD`。

## Follow-up — 位置交替手势规格与文案签核（2026-10-30）

负责人确认继续采用“在屏幕上相隔的两处位置交替轻点，共连续轻点 8 下”的回退规则。实现只根据坐标区域和顺序计数，提示文案不再声称能够辨认实体手指。

- PowerCalApp 的统一提示更新为：“紧急回退：在屏幕上相隔的两处位置交替快速轻点，共连续轻点 8 下，即可强制恢复原生皮肤。” recognizer KDoc 明确说明它只识别位置，不识别实体手指。
- 新增无 Activity/Store 副作用的文案回归；与 ActivityRecoveryTest 定向运行 **2/2 PASS**。命令： .\gradlew.bat :app:testDebugUnitTest --tests dev.powerfulcalendar.ui.v2.ActivityRecoveryTest --tests dev.powerfulcalendar.ui.v2.SkinRecoveryGestureCopyTest --offline --rerun-tasks --no-daemon --console=plain。日志路径：.local/android-test/reports/separation-s0-st10-gesture-copy-20261030/targeted-final.log；SHA-256 A42062F1E290636B27C21A72792944FE94A5185EAEEF739CBFA1603329ECBD22。
- 该回归包含于后续 **727/727** JVM 全量门禁，详见 S0-DK-01 API 35 follow-up。提交 1f5faa3（fix(分离): 对齐原生回退手势文案）已推送至 origin/codex/separation-s0-timeline-drag。
- S0-ST-10 仍为 **PARTIAL / HOLD**：模拟器/真机的实际触摸输入、TalkBack/无障碍和冷进程恢复未验证；这些缺口不再包含手势定义歧义。
