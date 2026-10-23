# PC-ISSUE-117 — HTTP 429 AI 客户端核心错误契约签核

日期：2026-10-24

候选：`REC-P1-062`（重建 P1；历史 209 项映射仍为 `UNMAPPED`）

状态：**窄化 AI 客户端核心错误契约 PASS；API 35/provider 和呈现路径未签核；整体冻结 HOLD**

## 签核范围

使用测试内 loopback HTTP stub 返回 429，验证结构化提案阶段错误映射：警告带阶段名和状态码；不产生提案或原始回复；不泄漏 provider 错误正文或 API key。无外部模型/provider 调用。API 35 provider 行为、提示呈现及设备路径不在本次签核范围。

根因和最小修复已由 PC-ISSUE-099 完成：`AiClient.interpret` 的 catch 分支此前把 `AiClient.complete` 产生的安全 HTTP 错误改写为通用消息，丢失结构化提案阶段；修复保留阶段上下文。本次没有再次修改应用或测试源码，仅复核核心契约。

## 验证结果

- 定向 Robolectric API 34：**1/1 PASS**。`AiChatClientTest.structuredProposalHttp429NamesFailedStageAndProducesNoProposal` 验证 warning 包含“结构化提案”和 429，`proposals` 与 `rawReply` 为空，且不含 provider 错误正文/API key。
- 定向日志 SHA-256：`205AEBEA474DEEC1E00A5C11E44809C134CC422685F3B9D5158788673DFA6459`；JUnit XML SHA-256：`DA8E5EFFAB259AD18B4348B6E1945AD5DDCF7F23F5BBE97719B8EEBF71ABD52B`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。**BUILD SUCCESSFUL**；460/460 测试、57 个套件，0 failures/errors/skips；lint 0 errors / 58 warnings。
- 完整日志 SHA-256：`2DF1BE02DDD052061A1F9EDD5DFC1CDDA823B1D752AC1D0F1F526CABC8A14B27`；完整 `AiChatClientTest` XML SHA-256：`55BE26F280AB2667650476B469BA8CE2BCF88885554408C2C20C2B81D443EDF8`；lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 日志、JUnit XML、lint XML 和 APK 保存在 `.local/android-test/reports/freeze-20261024/pc-issue-117-*`。测试源码基线：`7ffad7ef95dcdba08c7e609ceb03c2107419b2f4`。

## 台账与结论

- `REC-P1-062` 对本机 HTTP 429 的核心错误映射、脱敏与无提案契约签为 **PASS**。API 35/provider 与提示呈现路径仍未验证。
- 候选统计：**146 PASS / 0 FAIL / 9 BLOCKED / 47 NOT TESTED / 7 SPEC UNRESOLVED**；P0 **119/0/1/1/5**，P1 **27/0/8/46/1**。历史 209 项仍无映射，整体冻结保持 **HOLD**。
- 最新结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-117.json`，SHA-256 `3AD7FA01ABB82266470A7C1C2881E421E6F227D6436EB39187E33557503A2CB0`；CSV SHA-256 `B932FEB0957DE7534F42418D43076DFB1B58D3FFD4D9C279DE87007BE8458816`。结构校验仅验证账本结构、状态计数和证据引用，不签核产品行为或历史映射（`product_behavior_tested=false`）。
