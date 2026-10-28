# PC-ISSUE-SEPARATION-CORRUPT-SKIN-BOOT-RECOVERY — 损坏皮肤启动回退

日期：2026-10-28
优先级：P1
范围：SEP-13 / SEP-14 选中皮肤在启动恢复时损坏
本 Issue 状态：**Robolectric 覆盖 PASS；SEP-13、SEP-14 整体仍 NOT TESTED**

## 调查结果

`MainActivity.onCreate()` 在设置 Compose 内容前调用 `SkinPackageManager.restoreSelection()`。该方法重新校验持久化选择的皮肤；加载失败时将选择偏好重置为 `powercal-default` 并清空活动皮肤注册表。现有包管理器测试覆盖了有效皮肤重启恢复，却没有证明已选择的 `.pcskin` 在两次启动间损坏后会走回退路径，也没有验证回退不会自动删除用户包文件。

本次没有发现生产代码缺陷；交付是对已有恢复合同补充隔离的回归证据。

## 回归覆盖

新增 `SkinPackageManagerTest.corruptSelectedSkinOnRestartFallsBackToNativeAndRetainsPackageFile`：

1. 生成随机 ID 的有效测试包并安装到 Robolectric 临时应用目录。
2. 选择该包，确认选择偏好和活动注册表都指向测试皮肤。
3. 模拟重启间文件损坏，将测试包字节替换为不完整 ZIP，再调用生产 `restoreSelection()`。
4. 验证当前及新建 `SkinPackageManager` 均报告原生皮肤，注册表没有 Web 包；损坏文件仍存在，但不会被列为可安装皮肤。
5. 在 `finally` 中只清理本测试随机 ID 的包文件并恢复原生选择。

测试不构造 `PowerStore`，不执行日历业务操作；也没有声称验证真实 Activity 进程重启或设备文件系统行为。

## 验证结果

完整离线命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果：**635/635 JVM 测试通过，88 套件，0 失败/错误/跳过；59 项任务成功**。源码边界检查与 Debug APK 构建通过；Lint **0 errors / 52 warnings**。

| 检查 | 结果与证据 |
| --- | --- |
| 定向 `SkinPackageManagerTest` | **10/10 PASS**；JUnit XML SHA-256 `CF0FE6F7EFF220AAD6D0D957753CF0EE742DD7BC525074C9E98544E3E59A3A66` |
| 定向 Gradle 日志 | `.local/android-test/reports/separation-20261028/skin-corrupt-boot-recovery/skin-package-manager-focused.log`；SHA-256 `6A1A66C2BD9B2B7358A1DE094DF923AFD9F516F5F0362BB05762847E8AB0F340` |
| 完整门禁日志 | `.local/android-test/reports/separation-20261028/skin-corrupt-boot-recovery/full-gradle-gate.log`；SHA-256 `64E213E65D2E84769FE78E30CA4B6544B608A77FD08B916420645E7B39E59163` |
| Lint 报告 | `app/build/reports/lint-results-debug.xml`；SHA-256 `B627D2E8AAFD74E3D89A188CA71D49E73DD69E2300AA17834B73566101419A75` |
| Debug APK | 66,274,089 bytes；SHA-256 `8974331FDF045FD88C41944CCE2869D414D4FC8EAE24F69486E417F3DFF3283A` |

## 验证边界

- `restoreSelection()` 通过 Robolectric 直接调用；本次没有启动真实 `MainActivity`、WebView、AVD 或实体设备。
- `adb devices -l` 当前因 ADB daemon 启动失败而不可用；本机也没有 Android emulator 可执行文件。
- 用户数据保持与损坏包删除策略仅在本测试创建的随机包路径上验证。设备重启、WebView 崩溃、恶意/损坏皮肤端到端恢复、恢复入口可用性及 Native/Skin 完整回放仍待验证。
- 因 SEP-13/SEP-14 还有上述未验证项，两个矩阵行及整体 Skin-First 分离仍为 **NOT TESTED**。
