# PC-ISSUE-SEPARATION-SKIN-CHANGE-CHECKPOINT-RETRY — 换肤检查点失败后的恢复重试

日期：2026-10-28
范围：SEP-14 的 Host 编辑会话检查点恢复
状态：**本地 JVM / Debug 门禁 PASS；SEP-14 整体仍 NOT TESTED**

## 根因与修复

`EditSession.checkpoint()` 在 `PowerStore.checkpointFormalEdit()` 抛错时会把消息存入 `lastError`，让 Host 阻止换肤并保留正式编辑会话。这是正确的失败保护。但存储恢复后，后续检查点虽成功写入恢复草稿，旧 `lastError` 没有清除；`WebSkinEditService.prepareSkinChange()` 仍因错误标志返回 `false`，永久拒绝结束会话。用户即使在存储恢复后重试也无法完成换肤或关闭该编辑器。

修复仅在正式编辑检查点调用成功返回后清除会话的 `lastError`。异常路径仍保留错误状态；草稿、正式事件 revision、事件身份、发布授权和事务语义不变。新建事项首次草稿插入失败时仍按 `PC-ISSUE-052` 永久退休已分配的事件序号；测试明确验证这一既有合同。

## 故障注入覆盖

新增四条 `WebSkinEditBridgeTest` 回归：

1. **已有草稿暂存失败**：SQLite trigger 阻止更新；表单仍留在 Host 会话、数据库版本和内容不变。移除故障后重试只递增一次草稿版本；重复生命周期回调不再写入。
2. **临时新建事项首次落库失败**：打开阶段不写数据库。用户修改后触发首次草稿插入失败，Host 会话和输入仍可读；事件序号依 Core 合同退休。恢复存储后重试生成一张草稿和下一个事件身份；重复回调不再生成身份或草稿。
3. **正式事件编辑检查点失败**：原正式事件保持原 revision 和字段，编辑租约与新输入仍有效。存储恢复后只生成一张 `REPLACE` 恢复草稿，不发布正式事件；重复检查点幂等。
4. **待 Host 确认的正式写入遇到换肤**：先建立恢复草稿，再取消并缓存待确认请求；旧请求重放返回 `cancelled`，旧确认令牌变为 `stale`，正式事件和提交数不变。

## 验证证据

环境：Windows 11、Robolectric API 34、隔离 SQLite 数据库、离线 Gradle、SDK `E:\android-sdk`。隔离 worktree 不含 `local.properties`，本轮只为 Gradle 进程临时设置 `ANDROID_HOME` / `ANDROID_SDK_ROOT`，未修改主工作树配置。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 定向 `WebSkinEditBridgeTest` | **26/26 PASS**，0 failures/errors/skips | `web-edit-bridge-fixed-final.log` SHA-256 `CC9A00E07EA1A531B8BB427D99F3259783736CE9FA8A15BBD2F17335349AB3A8`；JUnit XML SHA-256 `98CCE6B3C01D6AF9005CA549F8D2B790048FDCD1C726C7DC66BE7A6C3D9C56A3` |
| 完整离线门禁 | **633/633 JVM tests，87 suites，0 failures/errors/skips；59/59 tasks 成功** | 命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain`；日志 `full-gradle-gate.log` SHA-256 `53B83B8FC5E1ECAC2E6A1D06266A235901AA887C9F21C6FDC4167E6C7455D2BD` |
| Lint | **0 errors / 52 warnings** | `app/build/reports/lint-results-debug.xml` SHA-256 `1CD7790BFDF2A843A56BC734EDD45F0C83906ACDDF006DDE29D48352CCC0575D` |
| Debug APK | **PASS**，66,274,089 bytes | `app/build/outputs/apk/debug/app-debug.apk` SHA-256 `97AD82F42FDABED1D7E8339A7F18D1665D272B786ED251806718507E00CDBF5C` |
| 故障前红测 | **FAIL（预期复现）**：正式编辑恢复存储后重试仍返回失败 | `web-edit-bridge-final.log` SHA-256 `6E44A4846E0EB8EAE88DFE6C3F53B7CF58CA2C3BA9B913BCB6003849483644F8` |

第一次新建事项测试还误把数据库插入失败理解为事件序号回滚。对照 `PC-ISSUE-052` 后修正为退休序号合同，随后四条恢复用例均通过；这没有修改或放宽 Core 身份规则。

## 设备验证边界

`E:\android-sdk` 有 API 36 platform-tools，但无 emulator binary；`adb devices -l` 返回空设备列表。因此本轮没有 AVD、Android WebView 或实体机运行结果。设备/实体验收仍由用户执行；这组 Robolectric 和 APK 证据不能替代设备回归。

SEP-14 仍 **NOT TESTED**：皮肤进程崩溃、进程死亡后恢复、损坏/恶意皮肤、设备恢复以及完整 Native/Skin 回放仍未覆盖。

## Git

- 实现提交：`b890d61ec6fdafbdd2b0b1a9486ae0a27b59b281` — `fix(分离): 修复正式编辑检查点恢复重试`
- 已推送到 `origin/codex/separation-skin-settings`。
- 本报告、SEP-14 矩阵和进度记录作为独立文档提交推送。
- 并行 Agent 的 `WebSkinManagementInstrumentedTest.kt` 未编辑、未暂存、未提交。

证据目录：`.local/android-test/reports/separation-20261028/skin-change-checkpoint-failure/`。
