# PC-ISSUE-147 — 区分明确日程请求与随口提及

日期：2026-10-24
状态：本地路由修复已提交；`REC-P1-048` 端到端仍为 **NOT TESTED**；PC-ISSUE-002 仍 **PARTIAL / BLOCKED**。

## 根因

普通聊天收到任何 `type=draft` 提示后，`PowerCalApp.submitAiChatInput` 都会调用结构化提案解析。模型为随口提及生成轻量建议标记时，因此会自动打开提案审核，违背“明确日程请求自动预览、可能安排只给轻量入口”的合同。

## 修复

- 增加显式 `intent=explicit` 与建议 `intent=suggested` 分类。
- 明确标记只把**当前用户原文**送入结构化预览；模型生成的 `source` 不会成为提案输入。
- 建议标记只保留聊天按钮来源，不触发自动解析。缺少或不认识 intent 的旧/异常标记安全回退为建议。
- 两条路径都不代表写入授权；正式写入仍需提案审核与用户授权。
- 更新系统提示、AI 协议文档及本地回归。

代码与测试提交：`8e642ad0d8e1ba5786f2e2ee0bdbb16851a5b9b2`（已推送）。

## 测试证据

定向命令覆盖 `AiSkillContractTest`、`AiCalendarHintSafetyTest`、明确意图的 `AiChatClientTest` 用例和 `AiChatDraftPipelineTest`，**13/13 PASS**。验证了 explicit/suggested 分流、missing/unknown 回退、模型伪造 source 隔离，以及合成结构化结果只形成可审核的 PENDING 草稿。日志：`.local/android-test/reports/freeze-20261024/pc-issue-147-targeted-canonical.log`，SHA-256 `87B8BFEE7D29155A458C6506DEE81DF578BB64E3BFDD1F56934BB43A14C8E01F`。完整测试结果 XML：`AiSkillContractTest` SHA-256 `0635052DC53FBC5A00CB990F59E717381C4AEF8F9DA21581D9235442CEE4F6C9`；`AiCalendarHintSafetyTest` `389FDA84BC8CD7AF6ECFC987EFBA3CE5A65C2EC789FB0125D1E3B6B7A1D46CD4`；`AiChatClientTest` `3F18A63CE134C7AA101AC24071F441AE495A296F9CAF3B5AEF862C5CFB8DB36E`；`AiChatDraftPipelineTest` `5642C0A6BB15E5BD2872097D0786993D33D92DEF14979AC9AB38569F787B1497`。

随后在该代码基线执行完整离线回归：

```text
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

结果 **483/483 JVM tests、62 suites，0 failures/errors/skips**；源码边界检查、Debug APK、AndroidTest APK 组装通过；lint **0 errors / 58 warnings**。日志 `.local/android-test/reports/freeze-20261024/pc-issue-147-full-gradle-final-2.log`，SHA-256 `47848074DB10EC273E31AAACCA2F1578DC454549862070593CEA6A2EBF47C9FF`。Debug APK SHA-256 `751E4DA86160A2032E9D74052AB3651E8B76FF83649FF71906404C86A93CA068`；AndroidTest APK SHA-256 `D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A`；lint XML SHA-256 `61100B7A13A847E652F2DE5BA1080A0AF3B93E183A4F3E43FBF038213CDD2AFD`。

## 验收边界

以上是本地 parser/client/合成 SQLite 管线证据；没有调用免费或付费 provider，没有执行独立 Android 虚拟设备安装/交互，也没有进行实体设备测试。测试没有验证真实模型是否会把自然语言正确分类，也没有验证 Compose 中建议按钮的实际呈现。因此 `REC-P1-048` 保持 `NOT TESTED`，不得据此宣称免费模型或产品 UI 行为通过。`REC-P1-047` 仍受免费 provider 可用性与配置阻塞；Issue 002 继续 **PARTIAL / HOLD**。

结构校验报告仅用于验证重建台账结构，不证明产品行为：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r13-pc-issue-147.json`（SHA-256 与台账 SHA-256 见冻结快照）。整体冻结仍为 **HOLD**。
