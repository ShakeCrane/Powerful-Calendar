# PC-ISSUE-SEPARATION-DRAFT-DECK-CONFLICT-RESTORE

- **状态：** 本 Issue 覆盖的草稿版本冲突审核与已删除正式事件恢复路径 PASS；SEP-08 完整 parity 仍为 **NOT TESTED**。
- **日期：** 2026-10-28
- **分支：** `codex/separation-skin-settings`
- **实现提交：** `e0988ab1fab89818617fb07b549815aba54d38db`（桥接协议、Host 审核界面、Clear/Studio 路径与回归）

## 问题与根因

Native 草稿卡组已有冲突替换和已删除事件恢复能力，但这些动作只能由 Native 界面发起。Clear Web skin 能收到并显示冲突/恢复状态，却没有可调用的 Host 审核请求；`WebEditWire` 也有意拒绝直接 `deck.confirmConflict` / `deck.confirmRestore`。因此用户在 Skin 卡组遇到冲突或恢复候选时，没有可达的受信任审核流程。

不能把“批准”布尔值交给皮肤。皮肤是不可信调用方，必须由 Host 显示并控制完整的确认界面，且在用户确认时重新核验当前状态。

## 修复

1. 新增无参数、封闭 schema 的 `deck.reviewConflict` 与 `deck.reviewRestore`。它们只请求 Host 打开审核，不携带批准标记、事件字段或授权令牌。
2. `WebSkinEditService` 捕获 Host 审核对应的冲突/恢复快照；审核期间锁定其他皮肤命令，避免确认面板展示期间状态继续被皮肤修改。
3. `PowerCalApp` 提供 Host 所有的 Compose 冲突和恢复对话框。冲突对话框展示当前修订及完整前后快照，并提供保留、覆盖、删除草稿等明确选择；恢复对话框说明恢复同一事件序号并生成新修订。
4. Host 确认时再次核对捕获的提示、选中草稿及表单。冲突替换继续走 `DeckController.confirmConflictReplacement()` 的正式版本检查；恢复继续走 `DeckController.requestRestore()`。状态过期时不写入，保留草稿并要求重新审查；取消只关闭提示，不写入。
5. 请求结果进入既有有界重放缓存，重复命令不会重复提交。Clear 显示恢复操作入口；Studio 增加明确标注为非写入 Host Mock 的冲突/恢复场景。

## 回归测试

修复前的 schema 回归在 `WebSkinEditBridgeTest.kt:80` 以 `deck.reviewConflict` 解码结果为 `null` 复现缺失能力。修复后新增并通过：

- `webConflictResolutionRequiresFreshHostReviewAndKeepsTheDraft`：冲突触发 Host 审核；审核面板打开后再产生修订变化，旧审核确认不得提交；刷新审核后允许一次完整快照替换；重放不会重复提交。
- `webDeletedEventRestoreRequiresHostConfirmationAndPreservesIdentity`：取消审核保持 tombstone、草稿和提交数；确认恢复原事件序号、递增修订；重放不产生第二次写入。
- `closedSchemaRejectsMetadataOverwritesAndUntrustedInput`：请求命令可解码，但直接确认命令及额外 `approve=true` 均被拒绝。
- Chrome E2E 的 `deck-conflict` / `deck-restore` 场景：验证 Clear 请求、Host Mock 展示、取消保留草稿、重新请求和确认流程。该证据是 Studio 浏览器 Mock，不代表 Android WebView 行为。

## 测试结果

| 检查 | 结果 |
| --- | --- |
| `WebSkinEditBridgeTest` 完整套件 | PASS；20/20，0 failures/errors/skips |
| 定向 Host 冲突与恢复回归 | PASS；包含过期审核拒绝、取消无写入、身份保留及幂等重放 |
| 完整 Gradle 离线门禁 | PASS；611/611 JVM tests、85 suites、0 failures/errors/skips、88/88 tasks |
| Source boundary / Debug APK / AndroidTest APK / lint | PASS；构建与 source boundary 完成；lint 0 errors、52 warnings |
| Skin Studio Python | PASS；51/51 |
| Studio Chrome E2E | PASS；新增两个 Host Mock 场景及既有回归通过 |
| JavaScript 语法 | PASS；三个变更 JS 文件均通过 `node --check` |
| Clear canonical bundle | PASS；8 files、379252 bytes |
| Android WebView / AVD / 实体设备 | NOT TESTED；本轮没有可用连接设备，也没有运行设备级交互 |

完整门禁命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

Studio 验证命令：

```powershell
node --check tools/skin-studio/default-clear/source/web/edit.js
node --check tools/skin-studio/v3-studio.js
node --check tools/skin-studio/tests/e2e_v3_source.cjs
node tools/skin-studio/tests/e2e_v3_source.cjs
python -m unittest discover -s tools/skin-studio/tests -v
python tools/skin-studio/build_default_clear.py --verify
```

环境：Windows PowerShell、JDK 20、仓库 Android SDK `E:\android-sdk`；Gradle 离线运行。Bridge 测试使用 Robolectric 与隔离 SQLite。Studio Chrome E2E 连接本机预览和非写入 Mock。

## 可追溯证据

原始日志保存在 Git 忽略目录 `.local/android-test/reports/separation-deck-recovery-20261028/`：

| 文件 | SHA-256 |
| --- | --- |
| `full-gradle-gate.log` | `9D00F0C4D875803D664CDD1F63F67D822866A9FFD98A551579D40DCB6E09466C` |
| `bridge-focused.log` | `02D809553D20C2127C838587FD24D7E7285A3395D64F37AA3B1FBA8B2F23EDE2` |
| `studio-python.log` | `9454DC49EAD5920B90993E1FE942D51BF20AE53BDCF615DF5295FEC20B7938D8` |
| `studio-e2e.log` | `862D93C91C687C86FCAAB62486EEE74E4C060236F7CCD53C7B2DE5C393DF8C82` |

构建产物与源包：

- Debug APK SHA-256：`AE4A6F6C36D6AF63346D1A92F0ED00D0EBDE7B838CC4BEA63033A5A224B9AF93`
- AndroidTest APK SHA-256：`FD52D9A141D801274FA4E58E1F7B20F113525CE3A8117A92D76DA41A88619BF9`
- Clear package SHA-256：`7B3FBB7C402E5C776894497D96BA1CEEE7189E8ACB48EEE70ECE2E9C19CCF81A`
- Lint XML SHA-256：`F51F69DF17F24319EB3070C9D89013DFAF1AED06787566816518B883D42002BD`
- Bridge suite XML SHA-256：`172267AA9383CA5100CC33497FEDFFC92A11019CEAB237691FBF1559143E1F33`

## 影响边界与剩余风险

本修复只为 Skin draft deck 接通已存在的正式恢复能力，不更改 Core 数据格式、事件时间、并发版本语义或皮肤授权边界。皮肤只能提出审核请求；只有 Host 确认界面能触发写入。

本 Issue 的 JVM/SQLite 回归、Studio Mock、APK 构建均通过，但没有实际驱动 Android Compose 对话框或 Android WebView，也没有 API 35 AVD/实体/OEM 证据。SEP-08 还包含完整草稿卡组操作、设备恢复、AI 锁定等范围，继续保持 **NOT TESTED**；核心分离目标不因本 Issue 通过而判为 GO。
