# PC-ISSUE-150 — AI 结构化提案阶段读超时回归

日期：2026-10-24
状态：**本地客户端超时边界 PASS；PC-ISSUE-002 / REC-P1-047 仍 BLOCKED**

## 问题与修复

`AiClient` 已为 HTTP 连接和读取设置 20 秒、45 秒默认超时，但免费模型结构化提案阶段缺少超时回归，无法证明读超时会明确失败且不会无限等待或错误地产生提案。

本 Issue 将连接/读取超时作为有正值校验的客户端参数，保留运行时默认值 20,000ms / 45,000ms，以便测试注入短超时。超时发生时沿用阶段级错误处理；没有自动重试，也不生成 proposal 或 raw reply。

## 回归证据

新增 `AiChatClientTest.readTimeoutInFreeStructuredProposalReturnsStageErrorWithoutRetry`。本地 loopback 服务接受唯一一次结构化请求后保持连接但不返回响应；客户端读取超时设为 150ms。断言验证：

- 仅发出 1 次请求；
- 返回结构化提案阶段的超时提示；
- 不产生 proposal 或 raw reply；
- 测试凭据哨兵不会出现在结果中。

定向测试 **1/1 PASS**。日志 SHA-256：`9CB7FC4C39FD02D178CAC3CD122AD396D48DFA056F5A56FA329B173CC82DA3E1`。`AiChatClientTest` JUnit XML 10/10，无失败/错误，SHA-256：`23EBAA32B27CCAC1A62985C8B6E204C0B63AA897B4E5370DDAC820E8AE7BC773`。

完整离线 Gradle 回归命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

结果 **BUILD SUCCESSFUL**：489/489 JVM 测试、64 suites、0 failures/errors/skips；源码边界、Debug APK、AndroidTest APK 组装通过；Lint 0 errors / 58 warnings。完整日志 SHA-256：`A9836C3D6FA385C4E156D1264782447E1FAE0037008E8FA3DB5AE160AAE6284C`。Debug APK SHA-256：`16B2042B81BE6ABC00EC77B47A842E2069FCEF33E0D655A6BD151C522DA4676A`；AndroidTest APK SHA-256：`315593E3B6CB6D0608C08545E15E571E7D41E199D95B69DB2B1FBF1924750544`；Lint XML SHA-256：`44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F`。

完整 Gradle 在当时工作区运行，489 项中包含 1 项来自预先存在、受保护的未跟踪文件 `app/src/test/java/dev/powerfulcalendar/ui/v2/DraftDeckFeedbackTest.kt`（其 JUnit XML 为 1/1 PASS）；因此该次总数是 488 项跟踪测试加这 1 项。此文件未由本 Issue 修改或暂存。

## 模拟器配置复查与范围

使用仓库内 Android SDK 对 API 35 `emulator-5554`、`emulator-5556` 进行只读检查：两个设备都安装 `dev.powerfulcalendar` 0.9.5-rc.1。5554 的 shared-preferences 目录不存在；5556 仅列出 `powercal-onboarding.xml` 与 `powercal-ui.xml`，没有 `powercal-ai.xml`。没有读取任何偏好值或凭据，没有启动应用、改动设置或发出 provider 请求。证据 JSON SHA-256：`E2FBEFF529095C17472739E6A8F85006E9E2923F029856156AD30077C32C1F1A`。

这项检查与 loopback 回归都不验证真实免费 provider。此前免费 provider 聊天阶段 HTTP 200、结构化阶段 HTTP 429 的结果仍未闭环；当前模拟器也没有可用 AI 配置，因此 `REC-P1-047` 继续 **BLOCKED**，PC-ISSUE-002 继续 **PARTIAL / HOLD**。没有调用付费模型。实体设备测试由用户负责。

## 交付

代码与回归测试已按约定提交并推送：`423f29019183728ac4b8048692e26ba518cea163`（`test(ai): 覆盖免费提案阶段读超时`）。本报告只签核本地客户端超时失败边界，不将其扩大为 provider 可用性或端到端提案验收。


## 台账复核

更新 REC-P1-047 的实际结果与证据后，结构校验通过：210 行（209 active RECONSTRUCTED + 1 RETIRED），184 PASS、0 FAIL、8 BLOCKED、11 NOT TESTED、6 SPEC UNRESOLVED；风险优先级 P0 144 / P1 64 / P2 1。artifact .local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r16-pc-issue-150.json SHA-256 5F4D3C33AEC94EA4A950BE4BA6A1104485556EF6BA7A23B25A9C1A8477F49F71；ledger SHA-256 93461981B84F2372C85DD61A3924B0BD39F30A5B2B4D89B2C95C4B2DEC9AD066。该校验只验证台账结构，不测试产品行为，REC-P1-047 仍 BLOCKED。
