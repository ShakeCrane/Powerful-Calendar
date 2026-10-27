# PC-ISSUE-SEPARATION-REMINDER-SETTINGS

日期：2026-10-28

状态：**提醒权限与调度入口切片自动化 PASS；SEP-11 总体 NOT TESTED**

## 根因与范围

Clear 的皮肤内设置页此前只能显示日历导入选项。精确闹钟授权、通知权限状态和提醒调度重建只能从 `NativeToolsPage` 操作，皮肤无法按自己的页面结构呈现这些普通提醒设置入口。

本 Issue 把有限的提醒权限状态和三个 Host action 加入现有 v3 Bridge option 白名单。Android 仍负责读取实时权限、展示系统设置/权限 UI 和运行既有提醒调度器；没有在 WebView 增加 Android 权限或数据写入能力。

## 实现

- `SemanticOptions` 增加 `reminders.exactAlarmSettings`、`reminders.notificationPermission`、`reminders.reconcile`。未知 ID 继续被 Bridge 闭合 schema 拒绝。
- 皮肤设置页的 `reminderPermissions` 投影仅包含三个布尔字段：`exactAlarmAllowed`、`notificationsAllowed`、`notificationsRuntimePermissionRequired`。该对象只在 `SETTINGS` 页投影，不含事件身份、标题、提醒内容或凭据。
- 权限按钮按状态禁用：精确闹钟已允许时不再打开请求页；Android 13+ 通知运行时权限已允许时不再发起请求。Host 在收到 action 后再次读取实时权限，不信任皮肤提交的状态。
- Android 12+ 精确闹钟入口调用现有 `ReminderScheduler.openExactAlarmSettings`。Android 13+ 通知入口调用既有 Activity permission launcher；更早版本打开该应用的系统通知设置页。旧版本没有通知运行时权限时，皮肤会显示“无需运行时授权”，不误报为已授予该权限。
- “重新同步提醒”调用现有幂等 `ReminderScheduler.reconcile`，随后刷新 Host 状态并发布新的 Bridge state revision。返回应用时 `ON_RESUME` 同样刷新状态，方便皮肤显示权限变更后的结果。
- action 仍受 `aiLocked` 和 Native 正式编辑状态约束。该入口没有直接日历写操作。
- Clear 使用 `role=status` 与 `aria-live=polite` 呈现状态；Studio Preview 对三项提醒 action 使用明确的非写入 Mock，不调用系统设置、权限对话框或真实调度器。
- Bridge 文档和 SEP-11 矩阵同步更新。由于凭据、备份/恢复、皮肤管理和设备/WebView 等价仍未覆盖，SEP-11 继续标记 `NOT TESTED`。

## 验证

定向 Gradle 命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain `
  --tests dev.powerfulcalendar.ui.v2.WebSkinBridgeTest `
  --tests dev.powerfulcalendar.ui.v2.WebSkinHostProjectionTest
```

JUnit XML：Bridge **3/3**，Host projection **13/13**，共 **16/16 PASS**，无失败、错误或跳过。日志 `.local/android-test/reports/separation-skin-reminder-settings-20261028/gradle-focused.log`，SHA-256 `D70306AC454F56406031780230ED834714E13D5271865126A38C8CE2734A10E3`。

完整离线门禁：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug `
  :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果：**BUILD SUCCESSFUL；604/604 JVM tests、83 suites、0 failures/errors/skips、88/88 tasks**。source boundary、Debug APK 与 AndroidTest APK 构建通过；lint **0 errors / 52 warnings**。日志 SHA-256 `8F2FB20D4908F00E2FF41F20A223141D45D57E550CBE9F215AF3DB645D31D65E`。

- Microsoft Edge Studio E2E：PASS。设置页显示两项导入与三项提醒 Host action、仅显示权限状态布尔投影、请求权限/调度只到 Preview Mock、可进入 Host tools Mock 并返回时间轴。完整脚本还验证源码/资源往返、重复事件 Mock、AI 附件与错误恢复 Mock、AI 审核 Mock。日志 SHA-256 `5F5FA772CCCFD254A6E862984607B47CE055560EE5193D13E8FD1D9701A9CB63`。
- Skin Studio Python suite：**51/51 PASS**。日志 SHA-256 `2E2A51431E568D9C9F9926C2E87F4C5AAC80208D32023CCD5DEF65B11F8DD33F`。
- 三个修改后的 JavaScript 文件 `node --check` 通过。
- Clear v3 包 `python tools/skin-studio/build_default_clear.py --verify` 通过，8 files / 378,603 bytes；包 SHA-256 `BE4F00D8BC0A89B5B683151E2A507B5052CC9C07FBC3507EDF385AE2968C3921`。
- Debug APK SHA-256 `CE0B111A7CB75805B26C98A9D80181F183DA2645F47470D72FE951E923D604AB`；AndroidTest APK SHA-256 `60F08FFC420D8C67860FE9D85938C5237CF3C48CD49D5EFC260DC1187C5C9B90`。

证据目录：`.local/android-test/reports/separation-skin-reminder-settings-20261028/`。未运行 connected instrumentation、Android WebView AVD 或实体/OEM 测试；Preview Mock 与 AndroidTest APK 编译不作为设备行为 PASS。

## 修改文件

- `app/src/main/java/dev/powerfulcalendar/ui/v2/SemanticOptions.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinBridgeTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinHostProjectionTest.kt`
- `tools/skin-studio/default-clear/source/web/app.js`
- `tools/skin-studio/v3-studio.js`
- `tools/skin-studio/tests/e2e_v3_source.cjs`
- `app/src/main/assets/powercal-clear-default.pcskin`
- `docs/V3_HOST_BRIDGE_EXTENSION.md`
- `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`
- 本 Issue 报告

## 剩余工作

- SEP-11 的 AI 凭据与个人设置、完整 ICS 导入/导出、备份/恢复、系统日历迁移、皮肤安装/切换/删除/导出仍未形成完整的皮肤等价入口。
- 仍需 Android WebView 验证系统权限回跳、Bridge 状态刷新、无障碍、小屏布局与 Host 恢复行为；真实设备由用户执行。
- 仍缺冻结 Native 与 Skin 对同一提醒权限、提醒重建、重启恢复操作的双版本回放。
- Studio 仍需支持完整页面/组件树任意重构；本 Issue 只验证源码编辑和非写入 Preview Mock。

## 提交

实现提交：`470f33f924588e9c26330cd0f2b4da96e2cba815`（`feat(分离): 让 Clear 设置页管理提醒权限入口`）。本报告随后以单独的文档补录提交记录该 SHA；提交不包含 `.local` 测试证据及其他 Agent 的工作。
