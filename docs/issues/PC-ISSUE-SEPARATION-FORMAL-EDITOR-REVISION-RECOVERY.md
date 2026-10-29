# PC-ISSUE-SEPARATION-FORMAL-EDITOR-REVISION-RECOVERY

- **日期：** 2026-10-30
- **状态：** `PARTIAL / HOLD`
- **验收项：** 重建矩阵 `S0-ED-04`（`RECONSTRUCTED`；历史规划中的 209 项原始 ID 未恢复）
- **测试提交：** `2a9bae5c656b43f0946153131dd11cbda5b1ab64`（`test(分离): 覆盖正式编辑冲突与恢复`，已推送到 `origin/codex/separation-s0-timeline-drag`）
- **生产代码：** 未修改。现有版本检查和恢复实现通过新增并发与重开回归；本 Issue 关闭的是验证缺口，没有宣称修复了已确认的生产缺陷。

## 结论

为正式事件编辑补上三条此前缺少的直接 JVM 回归：普通原生编辑遇到并发版本更新时不得覆盖较新的正式事件；未发布修改经持久 checkpoint、Store 关闭并重开后可继续编辑同一正式事件；Web Host 的保存审批等待期间若正式事件版本变化，审批必须冲突且保留编辑提案。三条新增测试及涉及的完整 Gradle 门禁均通过。

`S0-ED-04` 仍为 `PARTIAL / HOLD`。Robolectric/JVM Bridge 测试不是 Android WebView 运行证据；本机 ADB 设备列表为空，SDK 中也没有 `emulator.exe`。Android WebView 类型转换与保存冲突的实际桥接、Instrumentation、TalkBack 和实体/OEM 设备验证尚未完成。

## 审计范围与调用链

本次只读审查了正式编辑会话、Store 写入事务、Web 编辑桥接及其现有测试。核心调用关系为：

1. 原生正式事件编辑由 `EditSession.open(store, event)` 获取基于当前正式记录的编辑会话，表单修改在会话内暂存；checkpoint 将可恢复编辑内容写入关联草稿。
2. `EditSession.save()` 将编辑令牌与打开时的预期修订版本传给 `PowerStore`。Store 在正式写事务中比较当前 revision，发现不匹配时返回 `Conflict`，不写入提案。
3. Web 皮肤通过 `WebSkinEditService` 的 Host 命令打开、修改并请求 `SaveEvent` 审查。用户审批只允许 Host 继续处理，最终仍调用正式编辑会话和 Store 的版本保护；审批等待期间事件可能发生另一项正式修改，因此提交路径必须再次检查版本。
4. Web 编辑器的 kind conversion 复用 Core 的 `TemporalConversion`；本次没有更改转换规则或 UI/皮肤源码。

源码审查确认 Store 的事务版本门禁已经存在。过去的测试覆盖过正式删除审查中的 revision conflict，也分别覆盖编辑器保存、租约和类型转换，但没有直接证明普通正式编辑保存以及 Web `SaveEvent` 审查等待期间发生并发更新时的保留行为；正式编辑 checkpoint 重新打开 Store 后的完整恢复证据也不足。本次新增测试针对这些证据空白，没有发现需要改生产实现的复现失败。

## 修改内容

只改测试源码：

- `app/src/test/java/dev/powerfulcalendar/core/PowerStoreTest.kt`
  - `directFormalEditRejectsConcurrentRevisionAndKeepsTheProposalInMemory`：先创建并发布可跟踪 Memo；编辑器产生未发布标题提案；另一个版本化写入完成状态；随后保存必须返回 `Conflict`。验证正式标题和完成状态均保持、提案仍在编辑会话、没有新草稿/新身份/额外提交，并检查编辑租约在会话关闭后释放。
  - `directFormalEditCheckpointResumesAfterStoreReopen`：checkpoint 正式 Activity 编辑后关闭并重开同一隔离 Store；验证 checkpoint 与原正式事件关联，恢复表单包含用户修改，保存沿用同一永久身份、revision 只递增一次、待处理 checkpoint 清除，且序号水位不前移。
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridgeTest.kt`
  - `formalEditorSaveReviewRejectsChangedRevisionAndKeepsTheProposal`：Web Host 打开并修改正式 Memo，等待 Host 保存审查时通过独立版本化完成操作改写 revision；批准旧审查必须返回 `conflict`，正式事件只保留并发写入，提案继续可见，重放相同保存仍是幂等冲突。

未修改数据库 schema、生产写入逻辑、WebView、Skin Studio 或皮肤资源。新增测试使用现有隔离 Store/测试基础设施。

## 验收证据

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 三条新增回归 | `PASS` | XML 中三项测试均存在且通过：`directFormalEditRejectsConcurrentRevisionAndKeepsTheProposalInMemory`、`directFormalEditCheckpointResumesAfterStoreReopen`、`formalEditorSaveReviewRejectsChangedRevisionAndKeepsTheProposal`。 |
| 定向强制重跑 | `PASS` | `:app:testDebugUnitTest --tests dev.powerfulcalendar.core.PowerStoreTest --tests dev.powerfulcalendar.ui.v2.WebSkinEditBridgeTest --tests dev.powerfulcalendar.ui.v2.DirectEventEditorComposeTest --offline --rerun-tasks --console=plain --max-workers=1`；三个套件合计 **142/142**（PowerStoreTest 100、WebSkinEditBridgeTest 36、DirectEventEditorComposeTest 6），0 failures/errors/skips；32 个任务执行。日志 SHA-256：`41BAE2B386F17888A890DEB017C9EEDF7D4C78A0D63D5326CB07ED0EA86EF399`。 |
| 完整离线 Gradle 门禁 | `PASS` | `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain --max-workers=1`；JVM **724/724，99 suites**，0 failures/errors/skips；88 actionable tasks（5 executed / 83 up-to-date）；Lint **0 errors / 53 warnings**。日志 SHA-256：`875694B0B7BB8D612709B9F64D6193EFF608770479427598B664F38D3E789883`。 |
| Debug APK | `PASS`（构建） | `app/build/outputs/apk/debug/app-debug.apk`，67,513,497 bytes，SHA-256 `71C8E45C967CAFDA3BC77FBCD2062AD25D20801B2CDFE8062DCFFAB4B24B097C`。 |
| AndroidTest APK | `PASS`（仅组装） | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，1,557,196 bytes，SHA-256 `371A9C1AF76B5F0530FE4A69FA2FAF9BF1AFA053256DC8B24F7264129930653E`。未安装、未运行 Instrumentation。 |
| 设备可用性 | `BLOCKED` | `E:\android-sdk\platform-tools\adb.exe devices -l` 仅输出 `List of devices attached`，无设备序列号；`E:\android-sdk\emulator\emulator.exe` 不存在。 |
| Android WebView / 用户辅助功能 / 真机 OEM | `NOT TESTED` | Bridge JVM/Robolectric 结果不等价于 Android WebView、TalkBack 或物理设备证据；本轮没有可启动的模拟器或已连接设备。 |

完整与定向日志保存在 Git 忽略目录 `.local/android-test/reports/separation-s0-ed04-20261030/`。本轮没有导出测试数据库、用户日历内容、密钥或设备原始日志。

## 风险、状态与后续

- 新增回归验证的是 JVM 中 Store 和 Web Bridge 的 revision fence 与 checkpoint 行为；WebView 生命周期、真实皮肤 Host 消息与 Android 设备上的交互仍需执行。
- 现有 Studio Mock 可用于设计/脚本级检查，但不代替 Android WebView；本轮未把 Mock 结果当设备证据。
- `S0-ED-04` 因 Android WebView parity、Instrumentation、TalkBack 和 physical/OEM 证据缺失而保持 `PARTIAL / HOLD`，不据此签署分离阶段或 Release QA GO。
- 可继续审计重建矩阵中的后续独立验收项；缺少实体设备时，将设备相关行保留 `NOT TESTED`，不阻塞可在当前 JVM/浏览器环境完成的独立工作。
