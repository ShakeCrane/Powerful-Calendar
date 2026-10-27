# PC-ISSUE-SEPARATION-AI-MULTITURN-SUPPLEMENT

日期：2026-10-27

状态：**Skin Host 的不完整提案补充流程回归通过；SEP-10 / S3 整体仍未签核。**

## 范围与根因

核心合同要求缺失字段形成不完整候选，不得由模型猜测必填时间；候选可补充细节后再次验证，最终写入仍须用户授权。SEP-10 已有结构化解析和多轮任务单测，也有单轮 `PowerCalApp` 提案确认测试，但缺少一条贯通 Skin → Host Bridge → chat history/task snapshot → structured interpretation → 新审核卡 → 可信确认的 Compose Host 集成回归。

本轮没有复现生产代码缺陷，也没有更改业务实现。新增测试针对上述证据缺口，验证现有补充闭环和授权边界。

## 回归行为

新增 `WebAiProposalReviewIntegrationTest.supplementCompletesIncompleteProposalWithoutBypassingHostConfirmation`，通过本机 HTTP stub 驱动真实 `PowerCalApp`、`AiClient`、`AiConversationTaskSnapshot` 和 Web Host Bridge。模拟两次聊天、两次结构化解析：

1. 用户要求创建只有标题、日期和开始时刻的 ACTIVITY。Stub 返回缺结束时刻的追问；结构化返回为空，现有 fallback 显示不可发布的不完整审核卡。
2. 用户通过 `SUPPLEMENT` Bridge action 进入补充流程。此动作不改变当前 review，也不写入 Store。
3. 用户补充结束时刻。后续 structured request 包含原始请求和补充消息；assistant 提示以非权威上下文传递。模型 marker 中伪造的 `source` 不进入 structured request。
4. Host 以新 review ID 显示完整、可发布的候选；草稿、正式事件、身份和提交计数仍与测试前相同。
5. 旧不完整 review ID 的 `PUBLISH` 请求不能操作新候选。当前 review 的 `PUBLISH` 仅弹出可信 Compose Host before/after 确认；取消后新 review 仍在，Store 无变化。

测试只使用 loopback HTTP stub，没有访问真实 Provider、外部网络或付费模型。动作通过 Host Bridge 投递；Studio Chrome E2E 另验证可选提案及 supplement/stage/reject Mock，不将 Mock 结果伪称为 Android WebView 证据。

## 测试证据

定向测试命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests 'dev.powerfulcalendar.ui.v2.WebAiProposalReviewIntegrationTest.supplementCompletesIncompleteProposalWithoutBypassingHostConfirmation'
```

结果：**1/1 PASS**。日志 SHA-256：`D19FCC207FCAC28A1C9CFF40ED5978BC31549F33C7275F51F3B8B9041CE99E89`。

相关 Host 提案、投影和 AI task pipeline 定向类共 **22/22 PASS**：`WebAiProposalReviewIntegrationTest` 5 项、`WebAiProposalReviewTest` 5 项、`WebSkinHostProjectionTest` 11 项、`AiTaskPipelineTest` 1 项。最终完整门禁的 JUnit XML 对以上套件逐项给出 0 failure/error/skip。

完整离线 Android 门禁：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果：**591/591 JVM 测试、83 个测试套件、0 failure/error/skip，88/88 Gradle tasks；BUILD SUCCESSFUL**。Source-boundary 验证、Debug APK、AndroidTest APK 和 lint 均完成；lint 0 errors / 52 warnings。完整日志 SHA-256：`4954DACDF90973BD6B849777A38B60C7DEBCCEFB64674CE04EDBF5288B5DDDE6`。

Skin Studio / Skin 轨迹复核：

- Skin Studio Python：**42/42 PASS**。日志 SHA-256：`9CED4049FB0D8D76462DCB6D5E2B220967656B194C13D0F61E467AC11DC87ED6`。
- Chrome source round-trip、附件 Host-action Mock、AI selectable groups 与 supplement/stage/reject Mock 通过；确认 Mock 未写真实日历。日志 SHA-256：`770F64A46E489987B810EC0314F18A0F35FC6451C39E62C48774D7A732369510`。
- `build_default_clear.py --verify`：8 个文件、376,661 字节通过；bundle SHA-256 `0e17aabfe216e8ecb0775dafb8ea66857e25687b09e1167b7c68e1d9ffd81623`。

证据目录：`.local/android-test/reports/separation-20261027/ai-multiturn-skin/`。Debug / AndroidTest APK SHA-256：`B3115D80E25CBA24B1A6177D1EED5506DB613D467123E1BCE99268B31296780D` / `219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6`。测试源码 SHA-256：`A8F24A579DFE6EFE761DA06BF54316828D021D42BF8DCCFB914357EBFC8BAB4A`。

## 边界与剩余验收

- 本测试运行在 API 35 Robolectric Compose Host；没有运行 Android WebView、模拟器或实体设备。没有执行 live Provider 测试。
- 覆盖一个 ACTIVITY CREATE 的两轮补充。REPLACE/DELETE/转换、依赖操作原子组、目标版本冲突、多候选混合补充及进程死亡后恢复仍待验证。
- Studio 场景仍是 non-writing Mock；组件任意重构能力及实际 WebView UI parity 未由本测试签核。
- SEP-10 完整 Native/Skin parity 仍是 **NOT TESTED**，S3 仍开放。

## Git

测试提交：`ba30d9a5538f47195a98f8fb05365f8cf45c70d1`（`test(skin): 验证 AI 补充细节多轮提案流程`），已推送至 `origin/feat/skin-first-separation`。报告、矩阵和进度记录随后另行提交。
