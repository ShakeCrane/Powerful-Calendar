# PC-ISSUE-SEPARATION-SKIN-FAILURE-EDITOR-RECOVERY — 原生回退中的编辑恢复

日期：2026-10-28
优先级：P1
范围：SEP-14 皮肤失效及检查点失败后的 Host 编辑恢复
本 Issue 状态：**本地验证 PASS；SEP-14 整体仍 NOT TESTED**

## 发现与根因

`SkinAppShell` 检测到 Web 皮肤失败后会显示原生安全界面并调用 `onWebSkinFailure`。该回调尝试 `WebSkinEditService.prepareSkinChange()`，即使检查点失败也会切换到原生皮肤。Host 服务为保护输入会保留正式事件 `EditSession`、内存新建会话或待处理草稿会话；但原生回退页面没有投影或操作这些 Host 会话的入口。用户只能看到“不要强制结束应用”的提示，无法在安全页面查看保留内容或重试检查点。

本问题与上一项检查点错误标志残留不同：上一项修复让存储恢复后的重试能够成功结束会话；本项补齐检查点失败时原生安全界面的可见恢复路径。

## 修改内容

- `WebSkinEditService.recoverySnapshot()` 为正式事件编辑、内存新建表单和已有待处理草稿生成只读恢复投影。投影不含 Store 引用、编辑租约或审批令牌，写入仍只能经过既有 Host 检查点。
- Web 皮肤失败且检查点失败时，`PowerCalApp` 保留该恢复投影并显示原生恢复对话框。对话框只读展示完整表单、会话类型和最近存储错误，提供“重试安全暂存”。Host 审查对话框打开时暂不覆盖该审查界面。
- 重试复用 `prepareSkinChange()`：成功后关闭 Host 会话、刷新原生数据并继续留在安全皮肤；有修改的内容保存为待处理草稿，不发布正式事件。再次失败时会话和表单继续保留，对话框显示更新后的错误。
- 添加恢复快照与 Compose 面板回归。另修正一条现有 Host AI Compose 集成测试的同步缺口：正式确认动作经过异步目标读取/校验后才显示对话框，测试现在等待实际对话框并推进 Robolectric 主 Looper，保留 10 秒硬超时。

## 验收证据

完整门禁命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

最终结果：**634/634 JVM 测试通过，88 个测试套件，0 失败、0 错误、0 跳过；59 项 Gradle 任务成功**。源码边界检查和 Debug APK 构建通过。Lint 为 **0 errors / 52 warnings**。

| 检查 | 结果与证据 |
| --- | --- |
| `WebSkinEditBridgeTest` | 26 项通过；覆盖正式编辑与新建会话的恢复快照、故障注入及重试。JUnit XML SHA-256：`09CD0CD1F43D9C6AEFF62B759B464C9B7C30DBDCAB0B84A36C14DAECE773BB8D` |
| `WebSkinFailureRecoveryTest` | 1/1 通过；确认恢复表单可见且重试入口可操作。JUnit XML SHA-256：`3EB8DE478661E847352AE86FC24FB75E3DB796044EF9A8F4FC34A9EDD47E7D72` |
| AI Host 异步确认测试 | 修正后该测试单独通过；最终全量集成套件 8/8 通过。JUnit XML SHA-256：`7AC29DA06CEF4B85E4038B0E3287782A8DECA841B1C733C77D63FF057DC1F063` |
| 最终完整门禁日志 | `.local/android-test/reports/separation-20261028/skin-failure-fallback-recovery/full-gradle-gate-final.log`；SHA-256：`3C541168C666D50E91741D7AA97CD93EE7A2BF305354ECA7A5C4C1406AE0ACFF` |
| 首次全量失败诊断 | 同目录 `full-gradle-gate.log`；SHA-256：`CAFFDA03092BF7F48CEDE016539D730537B238A09F49077E98B852666A2A9C82`。单条失败是 AI 集成测试在异步 Host 审查完成前立即断言；对应测试等待修复后，独立复跑及最终全量均通过。 |
| 精确 AI 测试复跑 | `ai-stale-target-focused-fixed.log` SHA-256：`0D0B31DA9AE2FB5FC75C44379FF1A01C41ED158BD1CE35F3C404165F763BA11D`；首次隔离复跑日志 `ai-stale-target-focused.log` SHA-256：`3D35689CB83C12605C9D070CABDF4C8039A35C3A482019F992F2609F425763D7` |
| Lint | `lint-results-debug.xml` SHA-256：`B627D2E8AAFD74E3D89A188CA71D49E73DD69E2300AA17834B73566101419A75`；52 warnings，0 errors。 |
| Debug APK | 66,274,089 bytes；SHA-256：`8974331FDF045FD88C41944CCE2869D414D4FC8EAE24F69486E417F3DFF3283A` |

JVM/Lint XML 已复制到 `.local/android-test/reports/separation-20261028/skin-failure-fallback-recovery/`。Gradle 运行使用已安装 SDK `E:\android-sdk` 的临时环境变量，未改写仓库或主工作树配置。

## 验证边界与剩余风险

- 当前没有可用 AVD：`E:\android-sdk\emulator\emulator.exe` 不存在。当前 `adb devices -l` 查询时 ADB daemon 无法启动；没有 Android WebView 或实体设备结果，本地测试仅为 Robolectric/JVM。
- 若底层存储持续不可写且操作系统在成功重试落库之前终止应用，Host 内存会话仍可能随进程丢失。此修复提供安全界面中的查看与重试入口，不声称能在数据库不可写时提供跨进程持久化。
- WebView/skin 进程死亡、损坏或恶意 `.pcskin`、设备重启恢复及 Native/Skin 完整等价回放仍未验证。因此 SEP-14 和整体分离状态继续为 **NOT TESTED**。

## Git

- 实现提交：`d10559b65d3e197a20ea7480e579553b24788d86` — `fix(分离): 补齐换肤失败后的原生编辑恢复`。
- 已推送至 `origin/codex/separation-skin-settings`。
- 并发 Agent 的 `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinManagementInstrumentedTest.kt` 未编辑、未暂存、未提交。
