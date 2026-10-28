# PC-ISSUE-SEPARATION-FORMAL-EDIT-REOPEN-RECOVERY — SQLite 重开后的 Web 正式编辑恢复

日期：2026-10-28
优先级：P1
范围：SEP-05 正式事件编辑、SEP-14 检查点恢复
本 Issue 状态：**SQLite/租约恢复回归 PASS；真实 Android 进程死亡 NOT TESTED；SEP-05/SEP-14 整体仍 NOT TESTED**

## 发现与根因

已有 `checkpointPersistsWebChangesAcrossClose` 只检查 Web 编辑服务写入检查点后待处理草稿存在。它没有关闭并重新打开 `PowerStore`、恢复一个遗留的编辑租约、重建 Web 编辑服务并再次打开正式事件。因此，测试没有证明已经写入 SQLite 的正式编辑检查点能在旧进程所有者失效后，通过新服务恢复为可继续编辑的表单。

这是测试覆盖缺口。本次没有复现生产恢复逻辑缺陷，也没有修改生产代码。

## 修改内容

新增 `checkpointedWebFormalEditResumesAfterAbandonedOwnerAndSqliteReopen`：

1. 建立并正式提交一个 MEMO，然后通过 `WebSkinEditService` 修改标题和备注。
2. 执行正式编辑检查点，断言仅有一张 `REPLACE` 草稿；正式事件版本、提交数、事件身份和下一个正式序号不变，检查点只推进一次待处理队列顺序。
3. 关闭数据库，将测试数据库中的编辑所有者标记为过期，再关闭并重新打开 `PowerStore`，调用 `recoverAbandonedHumanEdit`。这是 SQLite/所有者状态模拟，不是杀掉 Android 进程。
4. 创建新的 `WebSkinEditService` 并重新打开正式事件，验证 Web 表单恢复出修改后的标题和备注且保持 dirty；正式事件内容/版本及所有计数没有再次变化。
5. 关闭 Web 编辑，再打开原生 `EditSession`，确认恢复草稿内容一致且没有产生第二张草稿或新的身份/队列分配。

## 验收证据

完整门禁命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

包含本回归的完整门禁：**636/636 JVM 测试通过，88 个测试套件，0 失败、0 错误、0 跳过；59 项 Gradle 任务成功**。源码边界检查和 Debug APK 构建通过；Lint **0 errors / 52 warnings**。

| 检查 | 结果与证据 |
| --- | --- |
| `WebSkinEditBridgeTest` | **27/27 PASS**；JUnit XML SHA-256：`9A8088D6833244C5A8D4EFF8005369053CE23F4695BA287C324C968FC763A918` |
| 完整 JVM XML | 88 个测试套件、636 项；归档 SHA-256：`08A65A0A24796A1CA07AEFFB2CF3CB168395952FCF85E4CC42FEBEB7A616691C` |
| 最终 Gradle 日志 | `.local/android-test/reports/separation-20261028/formal-edit-sqlite-reopen/full-gradle-gate-final.log`；SHA-256：`DDCF6D9E42ED4AB50AFBAA5EE87FDD855B0AE27B8DED751832EF533EB029A92B` |
| Lint XML | SHA-256：`B627D2E8AAFD74E3D89A188CA71D49E73DD69E2300AA17834B73566101419A75`；52 warnings、0 errors |
| Debug APK | 66,274,089 bytes；SHA-256：`8974331FDF045FD88C41944CCE2869D414D4FC8EAE24F69486E417F3DFF3283A` |

测试日志、XML、Lint 报告和 APK 保存在 `.local/android-test/reports/separation-20261028/formal-edit-sqlite-reopen/`。第一次全量重跑曾遇到另一条 AI 附件测试的 Windows Robolectric 文件替身竞态；该问题已独立记录和修复，之后的 637 项最终完整门禁也通过，见 `PC-ISSUE-SEPARATION-WINDOWS-ATOMICFILE-SHADOW-RACE.md`。

## 验证边界与剩余风险

- 测试覆盖 `PowerStore` SQLite 关闭/重开和过期所有者恢复，未执行真实 Android OS 进程终止或 Activity/WebView 进程死亡。
- 本机没有 Android Emulator 可执行文件；本 Issue 没有 AVD 或实体设备结果。
- 测试没有覆盖完整 Native/Skin 功能等价回放。因此仅这条持久化恢复路径有窄范围 JVM 证据，SEP-05、SEP-14 和整体分离仍为 **NOT TESTED**。

## Git

本 Issue 对应提交见交付时的 Git 记录；并发 Agent 的 `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinManagementInstrumentedTest.kt` 未修改、未暂存、未提交。
