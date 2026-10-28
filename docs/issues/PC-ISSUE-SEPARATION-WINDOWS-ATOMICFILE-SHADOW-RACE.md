# PC-ISSUE-SEPARATION-WINDOWS-ATOMICFILE-SHADOW-RACE — Robolectric AtomicFile 读写竞态

日期：2026-10-28
优先级：P2
范围：Windows 上 `WebAiAttachmentFlowIntegrationTest` 使用的 Robolectric `AtomicFile` 替身
本 Issue 状态：**测试替身竞态修复并通过回归；Android 平台 AtomicFile 行为仍未做设备验证**

## 发现与根因

一次完整 JVM 回归中，`WebAiAttachmentFlowIntegrationTest.skinImageRequiresExplicitUploadConsentAndNeverStoresRawImageInChatHistory` 间歇超时，并抛出 `NoSuchFileException`：`powercal-chat-history.json.new` 在重命名到正式文件时已经不存在。

根因在 Windows 测试替身 `ShadowAtomicFileReplaceOnWindows.openRead()`：每次读取都会删除 `.new` 文件。聊天历史保存异步写入 `.new` 时，UI 同时读取历史；测试替身因此删掉仍由 writer 使用的临时文件，随后 `finishWrite()` 的替换操作失败。生产 `AiChatHistoryStore.save()` 未修改。

## 修改内容

- `openRead()` 现在只读已提交的基础文件，不删除 writer 正在写入的 `.new` 文件。
- 新增 `readingAtomicFileDuringWriteDoesNotDeleteThePendingReplacement`：先建立已提交内容，再开始写入替换内容；并发读仍取得旧的完整内容，待写 `.new` 保留，完成写入后基础文件变为新内容。
- 重跑原 AI 图片授权/聊天历史集成用例，确认图片授权、取消行为、历史脱敏及清空流程通过。

## 验收证据

定向回归命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain `
  --tests 'dev.powerfulcalendar.ui.v2.WebAiAttachmentFlowIntegrationTest.readingAtomicFileDuringWriteDoesNotDeleteThePendingReplacement' `
  --tests 'dev.powerfulcalendar.ui.v2.WebAiAttachmentFlowIntegrationTest.skinImageRequiresExplicitUploadConsentAndNeverStoresRawImageInChatHistory'
```

两项定向回归通过。随后完整离线门禁 **637/637 JVM 测试通过，88 个套件，0 失败、0 错误、0 跳过；59 项 Gradle 任务成功**。`verifySkinSourceBoundary`、Debug APK、Lint 均通过；Lint **0 errors / 52 warnings**。

| 检查 | 结果与证据 |
| --- | --- |
| `WebAiAttachmentFlowIntegrationTest` | **10/10 PASS**；最终 JUnit XML SHA-256：`95D2C6488F1E4557D206C29138A7B24F4B2EBDE6FFAEB8E0989A2FCAEA890B85` |
| `WebSkinEditBridgeTest` | **27/27 PASS**；最终 JUnit XML SHA-256：`7C5A702FC712F21429BCBE1731AC8DDB8F303382D5A395DECFB2D79F3FA750A0` |
| 定向回归日志 | `.local/android-test/reports/separation-20261028/windows-atomicfile-shadow/focused-regressions.log`；SHA-256：`392B03B99CE931B8BA4EA2F2F747C7F7624226A70CF729553D11B228B3FE346D` |
| 修复后完整门禁日志 | `.local/android-test/reports/separation-20261028/formal-edit-sqlite-reopen/final/full-gradle-gate-post-harness-fix.log`；SHA-256：`E34460BE1C503DAB8CDB491D5B305695351ACD175481957CC149775A933348E9` |
| 完整 JUnit XML | 88 套件、637 项；归档 SHA-256：`29DC48EA3BAFAFDE1053CA5551CE586763E03DADE8B67550FCD4F8B62329DC9E` |
| 首次全量失败日志 | `.local/android-test/reports/separation-20261028/formal-edit-sqlite-reopen/full-gradle-gate-intermittent-failure.log`；SHA-256：`869D314C26B130D2E137DF9F33B90DF2DDCD4F824867B47FECDC54E37160BC5C`。该日志记录了修复前 `NoSuchFileException`，未被最终 PASS 覆盖或删除。 |
| Lint XML / Debug APK | SHA-256：`B627D2E8AAFD74E3D89A188CA71D49E73DD69E2300AA17834B73566101419A75` / `8974331FDF045FD88C41944CCE2869D414D4FC8EAE24F69486E417F3DFF3283A`；APK 66,274,089 bytes |

## 验证边界与剩余风险

- 修复对象是 Windows Robolectric 测试替身，不是 Android 平台实现；本次通过测试证明替身不会再删除进行中的替换文件。
- 本机没有 Android Emulator 可执行文件，本 Issue 没有 AVD 或实体设备结果。真实设备上的 `AtomicFile` 并发读取语义仍未验证。
- 该定向测试修复不代表 AI、WebView 或皮肤分离的整体功能等价验收完成。

## Git

代码与独立 Issue 报告通过单独提交推送；提交 SHA 记录在交付报告中。并发 Agent 的 `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinManagementInstrumentedTest.kt` 未修改、未暂存、未提交。
