# PC-ISSUE-SEPARATION-AI-BATCH-BRIDGE — Clear AI 批次生命周期桥接

日期：2026-10-29
阶段：Skin Separation `S0-AI-04a`
性质：重建验收项（`RECONSTRUCTED`，不是历史 209 项原始 ID）
状态：**PARTIAL / HOLD**

## 目标与范围

让 Clear 皮肤可以发起 AI 批量发布/丢弃范围选择，查看 Host 返回的审核、运行、停止和恢复状态，并将所有正式写入继续限制在 PowerCal Host 的显式审核授权之后。本 Issue 只处理批次 Bridge 与其预览回归；父项 `S0-AI-04` 中 AI 聊天历史清理和 rewind 仍是独立缺口。

## 根因

1. 原有 Clear/Studio Bridge 没有批次范围选择、状态投影、取消、停止和恢复动作，因此该路径只能由 Native Host 页面触发，皮肤无法提供对应操作状态。
2. 原有 `releaseStuckAiBatch()` 可把任何被锁定的 `REQUESTED`、`RUNNING` 或 `CANCEL_REQUESTED` 会话直接改成失败并解除锁。它没有要求先请求停止、等待失联期限，也没有把用户确认绑定到特定 session/epoch。
3. 应急释放没有记录计数未知；投影层可能把缺失数字呈现为零，造成结果已知的假象。
4. AI 提案审核和批次范围选择没有在 Host 入口及皮肤控件两侧完整互斥。
5. Native 回退聊天页开始范围选择后没有就地取消入口；切换回退界面时选择态不够完整。

## 修复

- Bridge 新增 `aiBatch` 只读状态投影及 `prepareAiBatch`、`cancelAiBatch`、`releaseStuckAiBatch` 动作。投影不含 session ID、锁 epoch、草稿 ID、审核版本或原始错误文本。
- 范围选择只收集 Host 所有的候选；皮肤答复不会授权写入。现有 Host 审核弹窗仍是发布或丢弃的最终授权点。
- 停止中的批次只在 `CANCEL_REQUESTED` 心跳至少 10 分钟未更新后显示恢复操作。Host 确认绑定当前 session ID 与 epoch；Store 事务重新核对锁、epoch、session、状态和超时，并在成功释放后推进 epoch，使旧授权失效。
- 应急释放报告显式标记 `outcomeKnown=false`，不投影发布/丢弃/保留计数。因为无法知道释放边界上的进行中操作是否已提交，界面要求用户检查正式事件和剩余草稿。
- Host 拒绝在提案审核期间启动批次；Clear/Studio 互斥禁用冲突控件。Native 回退页增加取消范围选择入口，并在批次操作期间禁用清聊天/清缓存动作。
- Studio Preview 增加非写入 Mock 场景，覆盖审核取消/确认、部分停止、超时恢复确认及已知部分计数。Preview Mock 不执行 Store 或正式日历写入。

## 修改文件

实现提交 `9c67da744feb542dde8d5b2ed42541a20a1a7319`（`修复：收紧 AI 批次取消与恢复边界`）包含：

- Store：`PowerStore.kt` 的恢复前置条件和事务 fencing。
- Host/Native：`PowerCalApp.kt`、`AiChatScreen.kt`、`PresentationSkinRuntime.kt`。
- Web Bridge：`WebSkinHost.kt`、`WebAiBatchProjection.kt`、`WebAiBatchProjectionTest.kt`。
- 回归：`PowerStoreTest.kt`、`WebSkinHostProjectionTest.kt`、`WebAiProposalReviewIntegrationTest.kt`。
- Clear/Studio：canonical `.pcskin`、`default-clear/source/web/{app.js,index.html,style.css}`、`v3-studio.js`、`tests/e2e_v3_source.cjs`。
- Bridge 合同：`docs/V3_HOST_BRIDGE_EXTENSION.md`。

## 测试证据

| 检查 | 结果 | 证据与边界 |
|---|---|---|
| Skin Studio Python 单测 | **PASS：61/61** | `.local/android-test/reports/separation-s0-ai-batch-20261029/studio-unittest-hardening.log`；SHA-256 `93f9b8307d7cd6baa2bc8e16b26c4aa09201571a030b82613b98972e266168e1`。 |
| Chrome Headless/CDP Preview E2E | **PASS** | `.local/android-test/reports/separation-s0-ai-batch-20261029/studio-e2e-hardening.log`；SHA-256 `ef7c692fc32bbd620cd1cdf082997008ad2d0fa767d6b41dfebf6f1364fc055c`。覆盖提案/批次互斥、范围选择、Host Mock 取消/确认、部分停止和超时恢复；明确没有调用真实 AI Provider 或写入日历/草稿。它只验证 Studio/浏览器 Mock。 |
| JavaScript 语法 | **PASS** | `node --check` 检查 `default-clear/source/web/app.js`、`v3-studio.js`、`tests/e2e_v3_source.cjs`。 |
| Clear canonical 包一致性 | **PASS** | `python tools/skin-studio/build_default_clear.py --verify`；9 个文件、390,997 bytes；SHA-256 `009a27cc4271d13184fcda7482f7fdec9c35d85a6f42b4c8e0380a660e7d7105`。 |
| Android Skin 源码边界 | **PASS** | `:app:verifySkinSourceBoundary --offline --rerun-tasks --no-daemon --console=plain`；`.local/android-test/reports/separation-s0-ai-batch-20261029/source-boundary-final.log`；SHA-256 `7e7c0b6a7dc165079f1714cd5623885fe9367156df4ab58e2f511111011cb2f7`。 |
| 空白/冲突标记检查 | **PASS** | `git diff --cached --check`，实现提交前通过。 |
| 新增 Store、Bridge 和 Compose 回归 | **NOT TESTED** | 已增加覆盖运行态拒绝释放、10 分钟边界、事务 fencing、计数未知、Host/Bridge 互斥和真实 PowerCalApp 授权/恢复路径的测试；没有编译或执行，不能作为 PASS。 |
| 完整 Gradle / APK / lint | **BLOCKED / NOT TESTED** | 执行 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain`。失败于依赖解析：仓库 SDK `E:\Project\Powerful-Calendar\.local\android-test\sdk` 缺少 `platforms/android-36`，尚未进入 Kotlin 编译或测试。日志 `.local/android-test/reports/separation-s0-ai-batch-20261029/gradle-final.log`；SHA-256 `9bd263341f93ef0e1f4215a835f2fb9a59a481a75a7c8fdaa2bde9ae9c03afd4`。本次没有 APK。 |

## 未验证项与剩余风险

- Android 单元测试、Compose/Host 集成测试、Debug APK、AndroidTest APK 和 lint 均 **NOT TESTED**。源码新增的 Kotlin 测试尤其需要在 SDK API 36 可用后执行。
- 生产 Android WebView → Bridge → Host → Store 的端到端传输、真实审核与批次写入 **NOT TESTED**。桌面浏览器 Preview E2E 不能代替这条链路。
- `.local/android-test/sdk/platforms` 没有已安装平台；本机 ADB 在线设备列表为空。本轮没有模拟器或实体设备验证。实体设备/OEM 验收由用户执行。
- `S0-AI-04` 的 AI 历史清理与 rewind 未纳入本切片；仍须单独实现并验证。
- 没有调用 DeepSeek 或其他 Provider；没有读取、清除用户数据库、凭据或 AVD 数据。

因此本 Issue 记录的 Studio/Preview 与源码边界子项为 PASS；Android 实现验收仍 **PARTIAL / HOLD**，不得据此签核父项或宣布分离验收完成。

## Git

- 实现提交：`9c67da744feb542dde8d5b2ed42541a20a1a7319` — `修复：收紧 AI 批次取消与恢复边界`。
- 本 Issue、进度和 Parity 更新采用独立文档提交。

## 后续状态（2026-10-29）

本报告撰写时，AI 历史清理与 rewind 仍是未实现的独立缺口。后续已加入 Clear/Host 历史清理与回退实现，详见 [PC-ISSUE-SEPARATION-AI-CHAT-HISTORY](PC-ISSUE-SEPARATION-AI-CHAT-HISTORY.md)。该后续切片的 Studio 证据通过，但 Kotlin/Android 与生产 WebView→Host 验收仍为 **NOT TESTED**；不能据此解除本 Issue 或父项 `S0-AI-04` 的 **PARTIAL / HOLD**。
