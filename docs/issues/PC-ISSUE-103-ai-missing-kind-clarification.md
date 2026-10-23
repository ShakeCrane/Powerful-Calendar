# PC-ISSUE-103 — AI 提案缺少可判定类型时请求澄清

日期：2026-10-24

候选：`REC-P1-051`（重建 P1；历史映射仍为 `UNMAPPED`）

状态：**缺类型核心澄清路径通过；候选仍 NOT TESTED；整体冻结 HOLD**

## 根因与修复

`AiResponseParser` 在没有 `kind`、也没有足够语义线索推断类型时，会安全地不生成提案；但 warning 只说“已忽略”，没有依照 `CORE_CONTRACT_FREEZE_2026-10-21.md` §8 向用户澄清。原有行为不猜类型，但没有说明如何恢复。

修复只调整 `AiInterpretation.warnings` 的核心提示：明确暂不生成草稿，并询问事项属于闹钟、活动、截止还是备忘。没有猜测类型、生成提案、创建草稿或分配事件号；没有改 UI 或设计资产。

## 回归证据

- 修复前新增集成回归 **1/1 RED**：失败断言确认 warning 缺少“请说明它属于”的澄清内容。日志 SHA-256 `DB04AD5ACCA8AAB3AE2EE8F5BDC6C267731B11CA11F15BC4A767B0EE89CB71DA`。
- 修复后定向 Robolectric API 34：**4/4 PASS across 2 suites**。覆盖缺类型澄清、空提案不建草稿/不分配身份、未知类型不强制转换，以及既有缺时间恢复路径。命令：`:app:testDebugUnitTest --tests dev.powerfulcalendar.core.PowerStoreTest.aiProposalWithoutDecidableKindRequestsClarificationWithoutReservingData --tests dev.powerfulcalendar.ai.AiResponseParserTest.unknownKindIsDroppedRatherThanCoerced --tests dev.powerfulcalendar.ai.AiResponseParserTest.missingTimeStaysMissing --tests dev.powerfulcalendar.core.PowerStoreTest.parsedAiActivityWithMissingTimeRemainsRepairableDraftAcrossRestart --offline --rerun-tasks --console=plain`。日志 SHA-256 `1C46FBAF97548526C2310A033D3FF163D5976BB49066D8BE0F56573D14B597E4`；定向 parser XML SHA-256 `53735326BBED79C3A97C9BFE9BBEF6413CFF66A61706C2D07C3D7F1AB6885ABE`；定向 PowerStore XML SHA-256 `23E7D36B53282513C75A49CABE41EBC890B4F28192F35088F3258CBDF7F4A797`。
- 完整 Gradle：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`，**455/455 across 57 suites**，0 failures/errors/skips；Lint 0 errors / 58 warnings。完整日志 SHA-256 `954509F2CC1DE490D9F0582EF6ABCCB1F1344E4778BDE89278E5121189DA13F8`；完整 `AiResponseParserTest` XML SHA-256 `D55E561F94B7AA36F0DD64A40D9EA5BFF0A88DFB76168A975A0BEBB07416CADB`；完整 `PowerStoreTest` XML SHA-256 `EF09E5945AED675953690D62FBF04C0D1CDE9E51E5C60B4DF3FD8F597B0D50B7`；Lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256 `138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 完整 JUnit XML 集合保存在 `.local/android-test/reports/freeze-20261024/pc-issue-103-full-junit/`；完整 Gradle 日志与定向日志保存在 `.local/android-test/reports/freeze-20261024/`。

## 台账与 Git

- 结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-103.json`，SHA-256 `C96848D5A9F75692FA837532EA7D3F3D91A0FD15086F4C96412536065DD74CD6`；台账 SHA-256 `31F3050CDC00C884E89A2EC219F2C53A51F35ED1D72387F89307984E625C84AC`。校验为结构性，`product_behavior_tested=false`。
- 修复与回归提交：`d381e1dc87a6ae1fe459b50aad79dd63b391fb9c`（`fix(ai): 澄清无法判定的提案类型`），已推送。
- `REC-P1-051` 还要求 API 35/free-provider 路径；这些未测，所以候选仍 NOT TESTED，冻结仍 HOLD。
