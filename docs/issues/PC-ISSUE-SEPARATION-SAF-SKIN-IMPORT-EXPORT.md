# PC-ISSUE-SEPARATION-SAF-SKIN-IMPORT-EXPORT

日期：2026-10-28

状态：**系统 SAF / DocumentsUI 皮肤包导入与 Clear 导出窄范围 PASS；SEP-11 整体仍 NOT TESTED**

## 问题与范围

此前的皮肤设置切片已验证 Clear WebView 到 Host 的目录投影、导入/导出入口和启用确认，但尚未实际打开 Android 系统文件选择器，也未通过真实 `DocumentsProvider` 完成包读取和导出。因此“Activity Result Intent 正确”不能证明 SAF 文件往返已经工作。

本 Issue 在隔离 API 35 AVD 上验证系统 `OpenDocument` 与 `CreateDocument` 完整往返。生产源码没有修改；只新增真实 Android 仪器回归及对应报告/进度记录。

## 测试实现

新增 `WebSkinManagementInstrumentedTest.skinPackageImportAndExportUseTheSystemDocumentsProvider`：

1. 在 AVD 自带的 Downloads Provider 中，用随机 ID 创建一个合法 v3 `.pcskin` ZIP 夹具；清单及文件 SHA-256 在测试内生成。
2. 启动实际 `PowerCalApp` 和 Clear WebView，经 Clear 设置页的 Host 命令打开真实 Android `OpenDocument`，再由系统 DocumentsUI 进入 Downloads 并选中夹具。
3. 等待真实 `SkinPackageManager` 安装皮肤；验证导入不会自动启用，当前皮肤仍为 Clear。
4. 记录并核对正式事件、草稿、身份和 commit 数量均为零。
5. 从同一 Clear 设置页打开真实 `CreateDocument`，通过 DocumentsUI 保存 Clear 包到 Downloads。
6. 在设备外读取导出文件，与仓库 canonical Clear `.pcskin` 比较 SHA-256；测试结束后删除本测试创建的精确 URI/名称、安装包目录、隔离数据库和偏好设置。

系统 Provider 会把 `application/zip` 文件显示为带 `.zip` 后缀的名称；仪器测试按实际显示名选择。系统 Save 控件的 accessibility label 使用大写时也可以匹配。两者都是实测的系统界面行为。

## 验证证据

### API 35 系统 Provider 往返

- 环境：隔离 API 35 x86_64 AVD `SkinManagementAPI35`，ADB serial `emulator-5564`；仅安装本轮唯一 QA 包 `dev.powerfulcalendar.d05qaskinprovider20261028` 及其测试包。未启动、清除或修改原有 `PowerCal_API35` AVD / 用户 PowerCal 安装。
- 测试：`WebSkinManagementInstrumentedTest.skinPackageImportAndExportUseTheSystemDocumentsProvider`，**1/1 PASS**。
- 实际检查：自定义皮肤由系统 Picker 导入并进入安装目录，但没有被激活；Clear 经系统 CreateDocument 导出；两阶段前后正式事件、草稿、identity、commit 数均为 0。
- 导出 Clear 包 SHA-256：`DBE04D79DA759ADAC4A30054427956163BE963EF317DE0BF75D289C53AEF340A`；与仓库 canonical `powercal-clear-default.pcskin` SHA-256 完全相同。
- 最终仪器日志：`.local/android-test/reports/separation-skin-management-followup-20261028/skin-provider-roundtrip-final.log`，SHA-256 `2689EE0504F61513E77C1F2984430C83F64F9DB2E4FA5D96D7942245BBAF0F6B`。
- 导出临时文件及本测试 Downloads 夹具已按精确名称清理；没有清空 Downloads，也没有清理其他用户文件。

### Android 完整离线门禁

命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果：**BUILD SUCCESSFUL；607/607 JVM tests、85 suites、0 failures/errors/skips；88/88 tasks**。Skin source boundary、Debug APK 和 AndroidTest APK 均成功；lint **0 errors / 52 warnings**。

- 完整日志：`.local/android-test/reports/separation-skin-management-followup-20261028/full-gate-final.log`，SHA-256 `637F23418EE079F7E85E3598A381B2C74F0B3061BB1C2E8C0F9CEFE6B40B9E7B`。
- Debug APK SHA-256：`B6C763A0721C01D95EBCD309BDBB6DBDF7CD1A692E68EC816FB87F3A70C06A4F`。
- AndroidTest APK SHA-256：`FD52D9A141D801274FA4E58E1F7B20F113525CE3A8117A92D76DA41A88619BF9`。

### 失败尝试记录

- 首次系统 Picker 测试按原始 `.pcskin` 名称匹配失败；Downloads Provider 实际展示 `.pcskin.zip`。该次在应用导入前失败，修正为系统展示名称后重跑。
- 随后一次在导出对话框中因测试按大小写敏感匹配 `Save`、而系统报告 `SAVE` 未找到控件；改为大小写无关匹配。
- 一次 AVD 在 JUnit 启动前断开，没有测试结果；重启同一隔离 AVD（不 wipe）后最终 1/1 PASS。
- 一次探索性重导入尝试等候 Clear 未暴露的错误提示而超时。最终测试不把未验证的 Snackbar 当作安装结果；直接验证实际安装目录、当前选择和 Store 计数。上述日志均保留在本地证据目录，没有把失败尝试计作 PASS。

## 修改文件与提交范围

- `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinManagementInstrumentedTest.kt`：真实 SAF/Provider 回归；没有生产源码改动。
- `docs/issues/PC-ISSUE-SEPARATION-SAF-SKIN-IMPORT-EXPORT.md`：本独立结案报告。
- `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`：只补记 SAF 导入/导出子路径证据，SEP-11 总项仍为 NOT TESTED。
- `docs/agent-handoff/PROGRESS.md`：追加本轮结果及下一步缺口。

## 未验证项与结论边界

- 本测试不覆盖皮肤删除、恶意/损坏包拒绝、权限拒绝/撤销、完整备份/恢复、凭据工具、Native/Skin 全功能往返、WebView 进程死亡或实体/OEM 文件 Provider。
- 本轮没有修改 Studio，也没有重跑 Studio Python / Edge 测试；此前结果及其边界见 `PC-ISSUE-SEPARATION-SKIN-MANAGEMENT.md`。
- 没有实体设备测试；仅有隔离 API 35 模拟器证据。
- 因 SEP-11 尚含凭据、权限、ICS、备份/恢复、删除及 Native/Skin 全量对等检查，**SEP-11 和 Skin-First 分离目标不得据此标记 PASS/GO**。本 Issue 仅对 SAF 自定义皮肤导入和 Clear 导出这条路径签核 PASS。
