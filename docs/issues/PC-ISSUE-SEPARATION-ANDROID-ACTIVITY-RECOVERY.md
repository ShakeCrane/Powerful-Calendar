# PC-ISSUE-SEPARATION-ANDROID-ACTIVITY-RECOVERY

**日期：** 2026-10-30

**状态：** `PARTIAL / HOLD`

**验收项：** reconstructed `S0-ST-10`
**提交：** `4978ec999c34fa514d7cb2000a024d39fb1a4fd3` (`fix(分离): 保证原生紧急回退持久并反馈失败`)

## 范围与结论

本 Issue 检查皮肤无法操作时，`MainActivity.dispatchTouchEvent` 是否能切回受保护的原生 Tools 页面，是否会改写日历数据，以及选中原生皮肤的偏好设置写入失败时是否会误报成功。

API 35 Robolectric 已验证 Activity 的真实触摸分发入口和原生页面交接，Store 状态前后相同。S0-ST-10 仍不能签核：当前 recognizer 实际判断的是一个触点在两个空间位置间交替，而产品合同及确认对话框写的是“双指、每指四下”。本次测试用同一个 pointer ID，不能证明两根实体手指，也没有 Android 设备、辅助功能或冷进程验证。手势的产品定义待负责人裁定。

## 根因

1. recognizer 有独立纯策略测试，但没有测试穿过 `MainActivity.dispatchTouchEvent`、`SkinPackageManager` 和 `PowerCalApp` 原生 Tools 页的完整回退路径。
2. `SkinPackageManager.currentId()` 对损坏的 `selected` 偏好值直接调用 `getString`；类型错误会抛出 `ClassCastException`，使 Activity 恢复入口可能在切回原生前崩溃。
3. 原生选择使用 `SharedPreferences.apply()`，调用返回时无法确认偏好已同步写入。Activity 随后无条件显示恢复成功，写入失败会被误报。
4. Web 皮肤加载失败时的安全回退也忽略了原生选择持久化结果。
5. 实现和合同存在手势定义缺口：合同写了每根手指四次，recognizer 只按坐标分区和交替顺序计数，不检查实体手指身份。

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
| API 35 Robolectric：两个空间位置交替，第 8 次进入原生 Tools | `PASS` | 此测试每次都是 pointer ID 0；仅证明当前两位置规则，不证明双指 |
| 回退前后正式记录、身份数、序号水位、待处理数、提交数 | `PASS` | 集成测试快照相等；回退不写 Store |
| 错误类型的 `selected` 偏好 | `PASS` | `SkinPackageManagerTest` 验证恢复 Native 且不崩溃 |
| 同步偏好写入返回 `false` 或抛异常 | `PASS` | 注入失败写入器；函数报告失败并切换当前进程注册表至 Native |
| 持久化失败提示文案 | `PASS` | 纯函数测试；Activity 成功路径另验证 Tools 页及成功提示 |
| 新进程/强制结束后偏好值仍可恢复 | `NOT TESTED` | Robolectric 新 manager 仍在同一进程和偏好实例内；没有冷进程测试 |
| 双指身份、Android WebView/设备触摸、TalkBack/无障碍行为 | `NOT TESTED` | 当前测试没有独立实体手指或设备输入证据 |
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

- **规格待裁定：** 是保留两处位置交替并修正文案，还是将手势改成需要同时触摸的双指模式。Android 在分别抬起的独立点击间不能可靠识别实体手指身份；当前仅按空间位置判定。裁定前不签核产品手势符合性。
- **设备待测：** 实际 Android/WebView 输入、无障碍、强制结束后的冷启动恢复均未测试。
- **存储故障语义：** 同步提交失败时，本进程会进入 Native 并明确提示；系统重启后仍可能恢复此前选中的皮肤，用户需要再次选择 Native。未声称失败写入仍具有跨进程持久性。
- **Web 加载失败次路径：** 持久化失败提示已处理，但对应 Compose 故障注入用例未补齐。

因此，`S0-ST-10` 为 **PARTIAL / HOLD**。本 Issue 只签核 Activity 级回退路径、Store 无写入和错误偏好防崩溃这些已直接验证的子项，不构成 Android 设备签核或整体分离 GO。
