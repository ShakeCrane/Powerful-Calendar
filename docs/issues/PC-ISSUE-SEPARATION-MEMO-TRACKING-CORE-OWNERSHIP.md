# PC-ISSUE-SEPARATION-MEMO-TRACKING-CORE-OWNERSHIP — Core owns intrinsic Memo tracking

日期：2026-11-02

状态：Core/API 和 Web Bridge 自动化回归、全量离线门禁 **PASS**；Android instrumentation、打包 WebView 与设备级 Native/Web parity **NOT TESTED**。Phase 1 remains open.

## 结论

冻结 Core 合约 §Invariants 2 规定 ALARM、ACTIVITY、DEADLINE 的完成跟踪可选，MEMO 则内在带有完成跟踪。Web patch parser 曾自行把 MEMO 的 `trackingEnabled` 改成 `true`；`EventFormV1` 的 API 更新路径没有同样归一化，且 Bridge 更新成功后保留本地候选表单而不是 Core editor snapshot。这使业务规则放在 Web transport 层，并允许 Host 暂时显示与 Core 最终状态不同的值。

现由 Core 的 `TemporalConversion` 和 `EventFormV1` → `DraftForm` 转换负责 MEMO 归一化。Web Wire 仅解析传入布尔值；正式事件与临时事件更新成功后，Bridge 都采用 API 返回的最新 editor snapshot。没有新增公开 feature API，也没有修改持久化或备份格式。

## 根因与修复

| 根因 | 修改 | 验证 |
| --- | --- | --- |
| 同类转换直接返回原表单，未强制 MEMO 固有完成跟踪；转入 MEMO 的转换也可能保留 false。 | Core `TemporalConversion.convert` 对相同目标和转换目标为 MEMO 的表单归一化 `trackingEnabled=true`。 | 既有 kind conversion 回归现断言所有目标为 MEMO 的结果启用 tracking。 |
| `EventFormV1.toCoreDraftForm()` 允许 API caller 传入 MEMO + tracking false。 | DTO → domain 转换根据 frozen event kind 归一化；Core API 回归覆盖新建与正式编辑 snapshot，并确认新建持久记录启用 tracking。 | `EventFeatureApiV1Test.memoCompletionTrackingIsIntrinsicAcrossFeatureFormUpdates`。 |
| Web patch parser 承担业务规则，成功后 Bridge 留存局部候选而非 Core 归一化后的值。 | Wire patch 只解析布尔值；正式与临时编辑更新后均重读并保存 `editorSnapshot`。 | `WebSkinEditBridgeTest.memoTrackingPatchIsNormalizedByCore` 确认 snapshot 为 true，且编辑阶段 Store 正式记录未变。 |

## 验证证据

- 定向回归：`EventFeatureApiV1Test` 14/14 与 `WebSkinEditBridgeTest` 45/45，合计 **59/59，2 XML suites**。最终聚焦日志 SHA-256：`D800868D71A7302F4E08907F353F228C544378DA557FE436BE1D0F5BB9DF4123`。
- 初次聚焦运行 **57/59 passed、2 failures、0 errors**。第一项失败揭示 Web Bridge 成功更新后保留了未归一化候选；第二项是旧 Web conversion 测试预期 MEMO tracking 为 false，与冻结合约不符。修复生产路径并调整该过期预期后重跑通过。失败日志 SHA-256：`5C55A568B55F491A0FE0C57C9FA75DB6DC1C2E39E922BBFEDFF4294FE486D288`。
- 强制离线完整门禁：**936/936 JVM tests / 139 XML suites / 0 failures, errors, skips / 154 tasks**；`verifySkinSourceBoundary`、`verifyNativeSkinBoundary`、Debug APK、`lintDebug`（0 errors / 54 warnings）、AndroidTest Kotlin compilation 与 `git diff --check` 均 PASS。完整日志 SHA-256：`406B9DD640065FED12C771E10354F6ED7101DC7E6BD95AB61950E45FF160812E`；JUnit XML manifest SHA-256：`A13E7E823CCCF523F5E4DBED79C9A412FA6708B56EC7064E0A396DD2A88B036F`。
- APK：67,060,418 bytes，SHA-256 `B6F1288253F4CBBCA01D3F459D092EF696CB7A17009AEAF096EE361DFEBA88A4`。
- Skin Studio **65/65 PASS**（日志 SHA-256 `8206CFA15EA88048736D4749C2A467198ECD7A13E4C495E5F05F45534C254A60`）；JavaScript syntax PASS；Clear package 9 files / 397,275 bytes PASS，package SHA-256 `ad0202fc6b9f6341cd0cb9bc1937dc4dd6fe2eda42082cea252e0c8f3f13d403`。
- 证据目录：`.local/android-test/reports/phase1-memo-rule-core-owner-20261102/`，摘要清单 `manifest.json`。ADB 没有可用设备，AVD 列表为空；instrumentation、打包 WebView 和 runtime Native/Web parity **NOT TESTED**。

## 修改与后续

代码提交：`10837a354e017be25fbbc02695add74c50d68cfa`（`分离：将 Memo 完成跟踪规则归属 Core`）。本报告、进度与路线图在后续文档提交中记录。

两次向 `origin/feat/skin-first-separation` 的普通 push 均因 GitHub HTTPS 443 不可达而失败；没有 force-push 或改写分支。恢复网络后应先 fetch 并确认 fast-forward，再重试推送。与本 Issue 直接相关的下一项是把重复实例编辑字段能力从 Bridge 的第二份规则移入 Core editor snapshot；详见进度文档的唯一 `NEXT_ACTION`。
