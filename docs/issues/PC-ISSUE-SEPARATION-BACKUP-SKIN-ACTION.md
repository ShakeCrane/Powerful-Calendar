# PC-ISSUE-SEPARATION-BACKUP-SKIN-ACTION

日期：2026-10-28

状态：**Bridge 请求、隔离 Compose/Robolectric 路由与 Studio Mock PASS；真实 Android 文件选择器/Provider NOT TESTED；SEP-11 总体仍 NOT TESTED**

## 根因与范围

原生 `NativeToolsPage` 已有原生数据备份导出和空库恢复流程，但 Clear 皮肤设置页没有相应入口。用户从皮肤设置开始处理备份时只能跳回原生工具页，Skin 无法组织这两项业务操作。

本 Issue 仅把两项现存 Host 功能加入受限的语义选项，不重写备份格式、Core 存储、文件访问或恢复策略。

## Host 与隐私边界

- `data.backup.export` 只触发已有 `CreateDocument("application/json")`。Host 调用 `PowerStore.exportBackup()` 写入所选目标，并在文件提供方支持时通过受大小上限保护的回读和 envelope 校验确认结果。
- `data.backup.restore` 只触发已有 `OpenDocument()`。Host 读取有大小上限的 UTF-8 文件并交给现有 envelope 与 Core 校验；Core 只允许恢复到空数据库，不覆盖已有 PowerCal 数据。旧 flat v2 仍经过原有可信来源确认对话框。
- 备份是可能包含私人日历和草稿信息的明文 JSON。Clear 选项明确提示风险和空库限制。
- 皮肤仅能发送闭合白名单里的 option ID；不接收 URI、备份正文、事件行、凭据或 Core 写入能力。Studio Preview 两个动作都只是非写入 Mock。

## 修改文件

- `app/src/main/java/dev/powerfulcalendar/ui/v2/SemanticOptions.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinBridgeTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinHostProjectionTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinBackupActionIntegrationTest.kt`
- `tools/skin-studio/default-clear/source/web/app.js`
- `tools/skin-studio/v3-studio.js`
- `tools/skin-studio/tests/e2e_v3_source.cjs`
- `app/src/main/assets/powercal-clear-default.pcskin`
- `docs/V3_HOST_BRIDGE_EXTENSION.md`
- `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`
- `docs/agent-handoff/PROGRESS.md`
- 本 Issue 报告

## 验证

定向 JVM/Robolectric 回归：`WebSkinBridgeTest`、`WebSkinHostProjectionTest`、`WebSkinBackupActionIntegrationTest`，共 **17/17 PASS**。

新集成用例在隔离 SQLite 中写入包含敏感标题与备注的测试事件，进入生产 `PowerCalApp` 的皮肤设置状态后，验证事件投影为空且不含测试正文；随后通过生产 Bridge action 观察 `CreateDocument` 和 `OpenDocument` 的实际 Activity Result Intent，并验证 Store 计数不变。该用例不派发文件 URI，因此不会读取、写入或恢复真实文件。

定向回归命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain `
  --tests dev.powerfulcalendar.ui.v2.WebSkinBridgeTest `
  --tests dev.powerfulcalendar.ui.v2.WebSkinHostProjectionTest `
  --tests dev.powerfulcalendar.ui.v2.WebSkinBackupActionIntegrationTest
```

结果 **17/17 PASS、3 suites、0 failures/errors/skips**。JUnit XML 分项为 Bridge 3/3、Host projection 13/13、生产 `PowerCalApp` Compose/Robolectric picker Intent 路由 1/1。定向日志 SHA-256：`02ADD72B64B4EB943EED0B47408B6EA6301544A1CC6276E0F131509A2CF68C1A`。

完整门禁命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果 **BUILD SUCCESSFUL；605/605 JVM tests、84 suites、0 failures/errors/skips、88/88 tasks**；source boundary、Debug APK、AndroidTest APK 均成功；lint **0 errors / 52 warnings**。

- 完整门禁日志 SHA-256：`DEC16279EBA4D728BC1F4D193A5DE6581E784F069ABDCF9494562DB152F38DE4`。
- Debug APK SHA-256：`0E9D967CFD70285D3456150ED591B96F5E7878F1A67A8B5314C8D71954F7A14E`。
- AndroidTest APK SHA-256：`60F08FFC420D8C67860FE9D85938C5237CF3C48CD49D5EFC260DC1187C5C9B90`。
- Lint XML SHA-256：`B8E60095EA2B6F88FAFCF7E6EAA75A48D921C030B7731C1B932C3C2F6AE3064E`。
- Studio Python suite：**51/51 PASS**；日志 SHA-256 `D79ED9D46720441DA456B6C936770DE796354B12A95B9A78091DC7759DF28DAD`。
- Edge Headless Studio E2E：PASS；日志 SHA-256 `F6A3458B8B5D984C5E4D5DE5A9CC97D0219865110E87E0DE351AA036C9ED0B29`。
- Clear v3 bundle：**8 files / 378,625 bytes**，SHA-256 `A48297B61E8A10305B59A17DD258F4C798681A6C42BAE8B09B784BC94E3A8BA0`；`build_default_clear.py --verify` 通过。
- `node --check`（Studio、Clear app、E2E）、`git diff --check` 均通过。

证据保存在仓库根目录 `.local/android-test/reports/separation-skin-backup-20261028/`（忽略目录，不纳入提交）。完整 Gradle 门禁使用 JDK 20.0.1 / Gradle 8.14.3；本次产物哈希已在上文列出。

## 未验证与剩余范围

- 未运行 Android WebView/AVD、Android 系统文件选择器或真实 DocumentsProvider；不声称真实文件创建、取消、回读、Provider 中断或恢复 UI 通过。
- 未完成 Native/Skin 双版本相同文件和数据集回放。
- AI 凭据、完整设置、皮肤管理及 SEP-11 其余功能仍未闭环；SEP-11 维持 `NOT TESTED`，S4 继续进行。
- 备份明文敏感风险与 `STABLE_DATA_CONTRACT_V1.md` 一致，皮肤入口没有扩大文件权限或绕过 Host。

## 提交

实现提交 SHA 与推送状态将在代码提交完成后补录；本报告将单独记录最终提交及远端状态。
