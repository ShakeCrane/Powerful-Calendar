# PC-ISSUE-SEPARATION-SKIN-MANAGEMENT

日期：2026-10-28

状态：**本切片的 Host 路由、皮肤目录和启用确认 PASS；完整 SEP-11 仍 NOT TESTED**

## 问题与边界

Clear 已有皮肤内设置落地页，但安装皮肤包、查看已安装目录、启用和删除仍只能从 Native Host 工具入口完成。结果是用户可以在 Clear 设置页配置提醒与日历数据，却必须离开皮肤才能管理皮肤包。

本 Issue 将现有 Host 管理操作接到 Clear 的皮肤设置页，并在 Skin Studio 里提供非写入预览 Mock。它不更换 `SkinPackageManager`、SAF、包校验、持久化或 Host 确认机制，也不声称完成 SEP-11 的全部 Native/Skin 等价。

## 根因与修复

- `SemanticOptions` 没有皮肤包导入/导出选项，Web Host 也没有已安装皮肤目录投影或启用/删除动作。
- Clear 的设置页把皮肤管理描述为仍在 Host 工具页；它无法渲染已安装目录或发起 Host 确认。
- 新增闭合 Bridge 动作 `selectSkin`、`deleteSkin`。payload 仅接受 `skinId`，ID 必须符合现有包 ID 正则；Host 再按当前已安装目录精确查找。Skin 不能传入路径、URI、包内容或自行确认。
- Host 仅在 `skinSettings` 页投影最多 14 个皮肤条目，每项仅含有界 ID/名称、内置状态、当前选择状态和操作可用状态。皮肤包选项也只在该页出现，普通页面不再显示皮肤导入/导出入口。
- 启用继续走现有可信切换确认和切换前 checkpoint；删除只接受非内置目录项并走现有可信删除确认。导入仍由 Host 文件选择器及现有包校验处理，导入不会自动启用；导出复用内置 Clear 候选包的 Host 导出流程。
- Clear 提供可访问的目录行和操作按钮；内置项不显示删除按钮。Studio 给出导入、导出、启用、删除四种非写入 Mock，不访问真实文件或日历数据。

## 修改文件

- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/SemanticOptions.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/SkinPackageManager.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinHost.kt`
- `app/src/main/assets/powercal-clear-default.pcskin`（由 canonical Clear source 重建）
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinBridgeTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinHostProjectionTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinManagementIntegrationTest.kt`
- `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinManagementInstrumentedTest.kt`
- `tools/skin-studio/default-clear/source/web/app.js`
- `tools/skin-studio/default-clear/source/web/style.css`
- `tools/skin-studio/v3-studio.js`
- `tools/skin-studio/tests/e2e_v3_source.cjs`
- `docs/V3_HOST_BRIDGE_EXTENSION.md`
- `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`

## 回归与证据

### 定向 Host 回归

命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain `
  --tests dev.powerfulcalendar.ui.v2.WebSkinBridgeTest `
  --tests dev.powerfulcalendar.ui.v2.WebSkinHostProjectionTest `
  --tests dev.powerfulcalendar.ui.v2.WebSkinManagementIntegrationTest `
  --tests dev.powerfulcalendar.ui.v2.WebSkinBackupActionIntegrationTest
```

结果：**19/19 PASS，4 suites**。覆盖合法与伪造 Bridge payload、目录字段白名单与 14 项上限、普通页面隐藏皮肤选项、生产 `PowerCalApp` 的导入/导出 Activity Result Intent、目录投影、取消切换、拒绝删除内置项和离开设置页时忽略皮肤选择。日志 SHA-256：`D9801DC0977271DD796409766CD4D94667EFDA8D399D041391ECF1D6A0F83874`。

### Android WebView 仪器回归

在新建的空白 API 35 x86_64 AVD `SkinManagementAPI35`（ADB serial `emulator-5562`）运行 `WebSkinManagementInstrumentedTest.clearSettingsShowHostCatalogueAndRequireConfirmationToSelectSkin`：**1/1 PASS**。测试通过实际 Clear WebView 设置按钮进入皮肤设置页，检查普通页没有皮肤包选项、设置页有两个内置目录项和导入/导出入口，再点击启用并验证 Android Host 确认弹窗；取消后当前皮肤不变，数据库事件、草稿和身份计数均为零。冷启动复测及最终实现复测均通过。最终仪器日志 SHA-256：`F33653B191E579C038F57FB0131A366DDC7410E5A435CF606EA84B0A2DDAEB8E`。

首次仪器运行在设置页条件断言处超时。检查表明静态 DOM 可以早于 Host MessagePort 初始化；测试已加入一个被闭合解码器拒绝、不会写入业务数据的桥接就绪探针，然后进行冷启动复测。首次失败日志保留于 `.local/android-test/reports/separation-skin-management-20261028/androidtest-webskin-attempt1.log`，冷启动与最终复测日志同目录。该失败记录为测试启动时序证据，不隐藏、不计作 PASS。

### 全量 Android 门禁

命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

最终重跑：**BUILD SUCCESSFUL；607/607 JVM tests、85 suites、0 failures/errors/skips、88/88 tasks**；源码边界、Debug APK、AndroidTest APK 构建和 lint 均成功。lint 有 **52 warnings / 0 errors**。完整日志 SHA-256：`E5B02523FF07A6E90E4A7C1B61DCD0E56A164AD781B637428E40387612E02351`。

第一次完整套件运行有一项既有 Compose 回归失败：`WebAiProposalReviewIntegrationTest.replaceConfirmationRejectsTargetChangedAfterReviewWithoutApplyingAiWrite` 在 390 行没有找到“确认 AI 批量正式操作”节点。该文件未由本 Issue 修改；隔离复跑通过，随后完整套件重跑 **607/607 PASS**。首次失败日志留在 `gradle-full-gate-attempt1.log`，隔离复跑日志为 `gradle-ai-failing-test.log`，最终成功日志为 `gradle-full-gate-retry.log`，均位于下列证据目录。该既有测试在全套运行中的时序敏感仍作为剩余风险记录。

产物与日志：

- Debug APK SHA-256：`B6C763A0721C01D95EBCD309BDBB6DBDF7CD1A692E68EC816FB87F3A70C06A4F`
- AndroidTest APK SHA-256：`A7428A2A5521B963B77B1F7A31068538C6F79D660EAD3518E5CB20861C26D807`
- Lint XML SHA-256：`1C7346531BEA8505A3BFEDE200EC994107E201B65C825377F5C5F1939EFDA4E3`
- Skin Studio Python：**51/51 PASS**；日志 SHA-256 `0564D2CEEFE503F515E628E5CBC96A18160A2B402A42D761E35045E70FCB5729`
- Edge Headless Studio E2E：PASS；皮肤设置 Mock 显示 2 个 Host 皮肤包选项、3 个目录行、1 个用户皮肤删除行，且未写入真实数据；日志 SHA-256 `BBFDD3A97FEDDC4C2C292E76D308E2483016109FF205E7AEE9671F648ABB8383`
- `node --check`：Clear `app.js`、`v3-studio.js`、`e2e_v3_source.cjs` 通过。
- Clear 候选包：**8 files / 379,111 bytes**；`build_default_clear.py --verify` 通过；包 SHA-256 `DBE04D79DA759ADAC4A30054427956163BE963EF317DE0BF75D289C53AEF340A`。
- `git diff --check` 与暂存区 `git diff --cached --check` 通过。

详细本地日志和生成文件位于仓库根目录 `.local/android-test/reports/separation-skin-management-20261028/`；AVD 使用独立数据目录 `.local/android-test/avd/SkinManagementAPI35.avd/`。原 `PowerCal_API35` AVD 未启动、未清除、未修改；测试完成后已停止本次启动的 `emulator-5562`。未运行实体/OEM 设备测试。

## 未验证与剩余范围

- 未实际打开 Android SAF 文件选择器、读取文件 URI、安装自定义 `.pcskin`、执行真实 Clear 文件导出或删除自定义皮肤包；Robolectric 只验证 Activity Result Intent，AVD 验证 WebView 到 Host 确认的链路。
- 未验证恶意包导入、系统 DocumentsProvider 中断/恢复、进程死亡恢复、凭据/提醒完整设置、全量备份/恢复以及完整 Native/Skin 数据集回放。
- 无实体/OEM 设备结果。本次仅证明一个 API 35 AVD 上的 Clear WebView 设置入口、目录投影及取消启用流程。
- 因 SEP-11 包含未覆盖的凭据、系统权限、文件操作和完整功能等价，SEP-11 继续 `NOT TESTED`，S4 不关闭；不得据此宣告 Skin-First 分离完成。

## 提交

实现提交：`9bb81a0113bf6d0461a2cf305256d75fa61e8ccb`，父提交 `3241f2e892c18bb6ba3cc5f245b1261c3bb65aca`。提交消息：`feat(分离): 将皮肤包管理接入 Clear 设置`。实现已推送到 `origin/codex/separation-skin-settings`。
