# PC-ISSUE-SEPARATION-BACKUP-ROUNDTRIP — Skin Host 备份文件回调往返

日期：2026-10-28

状态：**Host / Robolectric 文件结果回调子范围 PASS；Android SAF/Provider、WebView 和 SEP-11 总体验收仍 NOT TESTED**

## 根因与范围

前置 Issue `PC-ISSUE-SEPARATION-BACKUP-SKIN-ACTION.md` 只验证皮肤设置动作触发了 `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`，没有派发 Picker 返回的 URI。它证明了入口路由，却没有证明 Host 能写出备份、回读校验、读取备份并恢复数据。

本 Issue 补齐隔离的 JVM/Robolectric Host 回调路径。生产代码、备份格式、Core 恢复策略和 Studio 均未修改；测试使用随机 SQLite 名称和 Robolectric `ShadowContentResolver`，不接触用户文件或数据库。

## 回归覆盖

扩展 `WebSkinBackupActionIntegrationTest.skinBackupActionsExportAndRestoreThroughHostFileResults`：

1. 取消导出文件选择：没有写入字节，Store 计数不变。
2. 完成导出：派发文件 URI，验证 envelope/hash，并将完整 canonical payload 与 Host 执行前从源 Store 独立导出的 payload 比较；等待生产 Host 报告 SHA-256 回读校验成功；源 Store 不变。
3. 取消恢复选择：目标 Store 保持空白。
4. 派发哈希被篡改的备份：Host 显示恢复失败，目标 Store 的草稿、待处理、identity、commit 和正式事件计数均不变。
5. 将 Provider 内容恢复为有效文件后重试：恢复成功；事件字段、eventSeq、identity、commit、草稿及待处理计数与来源一致。
6. 对已恢复的非空目标再次恢复：Host/Core 拒绝覆盖，目标数据与计数保持不变。

既有同类测试仍验证皮肤设置投影不含私人事件标题/备注，以及两个语义动作只请求 Host 文件选择器。整个类共 **3/3 PASS**。

## 验证结果

定向命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests dev.powerfulcalendar.ui.v2.WebSkinBackupActionIntegrationTest
```

结果 **3/3 PASS**，0 failures/errors/skips。Gradle 日志 SHA-256：`57CFA040DBA4C3508D8C05278A0701CF6A4783BBDFDEF23D7E71E1B489F704C9`；JUnit XML SHA-256：`3B4D6113AE07BB5A40F8328D8B21B53AAFD085F11AA24005012E9E71EC644946`。

完整离线门禁：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果 **BUILD SUCCESSFUL；650/650 JVM tests、90 suites、0 failures/errors/skips、88/88 Gradle tasks**；source boundary、Debug/AndroidTest APK 构建通过；Lint **0 errors / 52 warnings**。

- 完整日志 SHA-256：`2CEFEC7EB959706547266138737EA2388B39016D6F9C8CA0AA78EDCABBA53168`
- Debug APK SHA-256：`29AB0792A35ABC35856B842526A208CBDA8AB3417D69D1CEAD916B3B91997448`
- AndroidTest APK SHA-256：`06F91487E2465995F89CF2C9D6A9DE53843BBD5430E191423A249BEA109BA97E`
- Lint XML SHA-256：`A016BF0CED62484371F5EA3DC4D91A951F67A6AA212635A67751E0CB7471ABFA`
- `git diff --check`：PASS

完整证据位于 `.local/android-test/reports/separation-backup-roundtrip-20261028/`；JUnit XML 位于 `app/build/test-results/testDebugUnitTest/`。测试迭代中早期的自定义 Provider 超时及错误回读时间戳断言日志保留在该本地目录，均未计作 PASS；最终夹具改用 `ShadowContentResolver`，并以独立的源 Store payload 验证导出内容。

Studio 源码没有变化，本 Issue 未重跑 Studio Python/Chrome E2E；之前的 Studio Mock/Chrome Headless 证据仍仅为 `PREVIOUS PASS`，来源 `PC-ISSUE-SEPARATION-LEGACY-MIGRATION.md`。此证据不代表文件操作 Mock 等同 Android 文件系统。

## 未验证边界

- 本测试在 Robolectric 中派发 Activity Result URI，**不**是 Android 系统 DocumentsUI、真实 `DocumentsProvider`、Android WebView、AVD Instrumentation 或实体设备测试。
- 未测试真实 Provider 的权限撤回、短写、断开、回读不支持及 Android 进程死亡恢复。
- 未执行 Native/Skin 完整双版本回放；凭据、权限、ICS、皮肤删除及其他设置仍未闭环。
- 因此本回归只签核 Host 在 Robolectric 文件结果回调下的导出/恢复和安全失败路径。SEP-11、S4 和 Skin-First 总目标继续 **NOT TESTED / OPEN**。

## Git

- 测试提交：`b2f81a0b83379b5fd68ad79aa8098488af9b3a05`（`test(分离): 覆盖备份恢复文件回调往返`），已推送至 `origin/codex/separation-legacy-import`。
- 本报告、功能矩阵和进度记录随后以独立文档提交推送。
