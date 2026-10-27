# PC-ISSUE-SEPARATION-ICS-EXPORT-SKIN-ACTION

日期：2026-10-28

状态：**Clear 皮肤内 ICS 导出入口及 Studio Mock 自动化 PASS；SEP-11 总体 NOT TESTED**

## 根因与范围

现有 `NativeToolsPage` 提供 Activity ICS 导出，但皮肤设置页没有对应入口。Skin 因此不能按自己的结构组织导出操作。ICS 是有损互操作格式，不能表示 ALARM、DEADLINE、MEMO 或完成状态；必须在用户触发前清楚显示限制，且不能把事件正文交给 WebView。

本 Issue 只暴露现有 Host ICS 导出流程，不更改 ICS 格式、业务数据、事件筛选或持久化规则。

## 实现

- `SemanticOptions` 注册 `calendar.export.ics`，由闭合 Bridge `invokeOption` 白名单校验。
- Clear 设置页新增“导出 Activity ICS”入口，按钮可见地说明仅包含 Activity，其他事件类型及完成状态请使用原生备份；相同说明也进入标题和无障碍名称。
- Host action 复用现有 `exportIcs` ActivityResult launcher、Android `CreateDocument("text/calendar")` 和 `IcsCodec.exportActivities(records.filter { kind == ACTIVITY })`。用户通过 Android 文件选择器选择输出位置，事件数据不投影给皮肤。
- Studio Preview 提供显式非写入 Mock，说明它不会启动真实文件选择器或创建文件。
- Bridge 合同、SEP-11 矩阵、测试和本 Issue 报告同步更新。SEP-11 仍为 `NOT TESTED`，因为真实系统文件选择器、外部日历兼容及其余设置功能尚未验收。

## 验证

定向测试命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain `
  --tests dev.powerfulcalendar.ui.v2.WebSkinBridgeTest `
  --tests dev.powerfulcalendar.ui.v2.WebSkinHostProjectionTest
```

JUnit XML 显示 Bridge **3/3**、Host projection **13/13**，共 **16/16 PASS**，无失败、错误或跳过。日志 `.local/android-test/reports/separation-skin-ics-export-20261028/gradle-focused.log`，SHA-256 `5C23B94E5E984518FFE9B123B85E13E0D70BE5649FFECE16AF5B0F430CB53624`。

完整离线门禁：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug `
  :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果：**BUILD SUCCESSFUL；604/604 JVM tests、83 suites、0 failures/errors/skips、88/88 tasks**。source boundary、Debug APK 和 AndroidTest APK 构建通过；lint **0 errors / 52 warnings**。日志 SHA-256 `3297187330577BEF7871D5CA612B337FF494E2B437228121CBADA6C2CA46CA58`；lint XML SHA-256 `0AB755A209A47C0F1E57AF1EC53B26540028A260EAF712B36FBCCC3A3E7FB3E1`。

- Microsoft Edge Studio E2E：PASS。检查两项导入、一项 ICS 导出、Activity-only 数据损失说明、三项提醒 Host action、权限状态、Host tools Mock 和导航。导出 Mock 断言未创建或写入真实文件。日志 SHA-256 `0E8D610A2D8CF46B0F527B4904ECBA3F89B599AD3B8BF775D0952D28E72F47EF`。
- Skin Studio Python suite：**51/51 PASS**。日志 SHA-256 `294D101D0CA456496EAF4D0257CB7D72B49F5B836BF0BCA52176704194388D2C`。
- 修改后的 JS 文件 `node --check` 通过。
- Clear v3 包通过 `python tools/skin-studio/build_default_clear.py --verify`，8 files / 378,612 bytes；SHA-256 `1B6E18C2C95D056DFF9CD08BE99E9C385E403DAC331F7C07C361A3E8D47FF426`。
- Debug APK SHA-256 `2125491B2D864BD05D842EC445C3FAE5B151A0F32EA92431A5F16406E56C744E`；AndroidTest APK SHA-256 `60F08FFC420D8C67860FE9D85938C5237CF3C48CD49D5EFC260DC1187C5C9B90`。

证据目录：`.local/android-test/reports/separation-skin-ics-export-20261028/`。未运行 Android WebView/AVD、真实 Android 文件选择器或实体/OEM 测试；浏览器 Mock 和 APK 构建不作为设备验收。

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

## 未完成

- 必须用隔离测试文件走真实 Android `CreateDocument`/外部 DocumentsProvider，并验证导出文本的 MIME、UTF-8、可回读与取消路径。
- 尚未与冻结 Native 对相同 Activity 数据执行导出回放；也未验证第三方日历对产物的兼容。
- ICS 导入/导出以外的 AI 凭据、备份/恢复、个人资料、皮肤管理与其他设置仍需分别迁移或证明 Host 等价。
- Studio 仍缺完整任意组件树重构能力；本 Issue 只覆盖源码编辑及文件选择器 Mock。

## 提交

待提交后补录实现 commit SHA。本次提交仅包含该切片的 Host action、Clear/Studio 支持、测试、包、合同矩阵和本报告；`.local` 证据及其他 Agent 的修改不入 Git。
