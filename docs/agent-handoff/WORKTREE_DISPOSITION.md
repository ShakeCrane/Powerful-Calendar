# PowerCal Worktree 处置台账（G-02）

审计日期：2026-11-03
审计范围：全部 Git Worktree、其 Git 可见未提交状态，以及与当前集成主线不同的已提交历史。
施工位置：E:/Project/Powerful-Calendar/.local/android-test/worktrees/skin-single-default-closure

## 基准与统计口径

- 唯一集成主线：origin/feat/skin-first-separation，审计时 HEAD 为 b6f559bee65a34cbb85ddcd5d440e7c4d3cafc35。审计期间刷新远端后未移动。
- 冻结 Core 主线：origin/main 为 af229d9829b692dc4f4d6c5189809f65bef6675f；冻结源码提交 b1f7629f0c52812e1887443872f43aa6c49908c4 是其祖先。main 是单独冻结基线，不作为本分离施工分支。
- Worktree 数量来自 git worktree list --porcelain：29。
- 脏 Worktree 数量来自逐树 git status --porcelain=v1：18。
- 表中“落后/独有”按 git rev-list --left-right --count origin/feat/skin-first-separation...HEAD 记录，左值为主线独有提交数，右值为该 HEAD 独有提交数；合并基点为 git merge-base 结果。
- 2 个本地分支引用含主线未有的提交：codex/separation-skin-settings（77 个）和 codex/separation-legacy-import（85 个）。另有 2 个 detached Worktree 停在这条提交链的中间点；它们不是另外两个分支。所有 Worktree 的 git ls-files -u 均为空；跨工作区同路径修改仍构成潜在冲突。
- 全程只读其他 Worktree。未读本地日志、数据库、凭据或令牌。为避免扫描大型本地证据/缓存，main 的 .local/、__pycache__ 仅按目录登记；design/ 只统计文件元数据与有限路径样例，没有读取设计文件内容。
- 测试未运行。Android、Studio 和 AI 改动的验证状态不因本次 Git 审计而改变。

## 全部 Worktree 清单

“HEAD；最近提交”中的 HEAD 是完整 40 位 SHA，最近提交主题来自 git log -1。除标明共享合并基点的 4 个工作区外，合并基点均等于该行 HEAD。

| # | Worktree 完整路径 | 分支 | HEAD；最近提交主题 | 合并基点；落后/独有 | Git 状态 | 处置 |
|---:|---|---|---|---|---|---|
| 1 | E:/Project/Powerful-Calendar | main | af229d9829b692dc4f4d6c5189809f65bef6675f；docs(freeze): owner-sign core scope and open skin separation phase | HEAD；523/0 | 脏 11 | KEEP_PROTECTED |
| 2 | E:/Project/Powerful-Calendar/.local/android-test/reports/freeze-20261023/issue-greedy-label-folding/pre-fix-worktree | detached | 89ea4fd6fbdee74fface1abd259b406f42df2029；docs(qa): 记录时间轴 Issue 004 回归证据 | HEAD；912/0 | 干净 | KEEP_PROTECTED |
| 3 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/ai-error-studio-verify-20261027 | detached | b0af01efc512a41a797cc56433ee2dd3050282c8；docs(studio): 记录浏览器工程编辑验收 | HEAD；487/0 | 脏 6 | KEEP_PROTECTED |
| 4 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/chatgpt-core-handoff-20261103 | chatgpt/phase1-host-boundary-20261103 | 9fd9046056014552f9ddc7211a2c16c74e067293；docs(test): record isolated API 35 emulator readiness | HEAD；4/0 | 脏 4 | KEEP_PROTECTED |
| 5 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/core-feature-api-event-20261101 | codex/core-feature-api-event | 674d8c631313c92dc99016c5ca7aa1ed772fed7a；文档：记录批次审核草稿 DTO 边界 | HEAD；7/0 | 脏 3 | KEEP_PROTECTED |
| 6 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/delivery-closure-20261030 | detached | 6cd755621cc1c599dd72871d4e1eb77442998d4a；docs(分离): 收口阶段交付与SEP验收状态 | HEAD；352/0 | 干净 | KEEP_PROTECTED |
| 7 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/delivery-final-88b0444-20261030 | codex/skin-first-mainline | c09d39d8161b113ad62ce8348760a2620017869b；test(皮肤分离): 验证IME优先返回等价行为 | HEAD；140/0 | 脏 2 | KEEP_PROTECTED |
| 8 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/handoff-recurrence-20261103 | codex/handoff-recurrence-20261103 | b6f559bee65a34cbb85ddcd5d440e7c4d3cafc35；docs(plan): add file-level single-default skin execution roadmap | HEAD；0/0 | 干净 | KEEP_PROTECTED |
| 9 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/legacy-import-fix | codex/separation-legacy-import | d43bac3c5a2b6ea6325c50874d6354b55fd43662；docs(分离): 补录 Bridge JS 审计提交与证据 | 172288a7c8bf3132aa5c0f10a3ac768f86626d9f；473/85 | 干净 | REVIEW_FOR_MERGE |
| 10 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/p02-today-512cfd5 | detached | 512cfd5ac5584a840725e5ecabfe9069f7c88ca9；docs(freeze): 更新人工签核交接证据 | HEAD；530/0 | 脏 7 | KEEP_PROTECTED |
| 11 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/pc-d05-prod-572cf10 | codex/pc-d05-production-qa | c02c766d881701bfb38cd86e27b90adbab7ba651；test(reminders): 补充生产提醒进程与重启恢复验证 | HEAD；533/0 | 干净 | KEEP_PROTECTED |
| 12 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/pc-issue-166-controller-current | detached | d943ddc9fd86b75194d6c7d9b7ba87f5236528e1；test(Phase1): 复验编辑器生命周期与默认时间基线 | HEAD；546/0 | 脏 1 | KEEP_PROTECTED |
| 13 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/pc-issue-172-final-7ceb7fa | detached | 7ceb7fa8b65aefd4174cf0420d1d166486c89484；docs(编辑器): 记录Phase1当前基线复验 | HEAD；555/0 | 脏 20 | KEEP_PROTECTED |
| 14 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/pc-issue-173-glass-fallback | detached | 465c9ecd13e6ab6adc902defc23673abc8c743e0；docs(AI聊天): 记录控制器集成提交号 | HEAD；543/0 | 脏 2 | KEEP_PROTECTED |
| 15 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/pc-phase1-dst-earlier-offset | detached | 9a1e3780125fd6734fe3a70fd383413159d62de9；docs(冻结): 核准日期截止签核边界 | HEAD；535/0 | 脏 3 | KEEP_PROTECTED |
| 16 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/phase1-72372ad | detached | 72372adbcc2fef890c85bcca8b6835560e090b9b；docs(验收): 补录Phase1主线复验 | HEAD；548/0 | 干净 | KEEP_PROTECTED |
| 17 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/phase1-review-d600aaf | detached | d600aafd82024dd589ca708a957b8678ca3092ac；docs(编辑器): 补录Phase1重验与AVD恢复缺口 | HEAD；557/0 | 干净 | KEEP_PROTECTED |
| 18 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/phase1-task-7ceb7fa | detached | 7ceb7fa8b65aefd4174cf0420d1d166486c89484；docs(编辑器): 记录Phase1当前基线复验 | HEAD；555/0 | 脏 1 | KEEP_PROTECTED |
| 19 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/phase1-verify-6ddf9b9 | detached | 6ddf9b98a4520f804b0b1e3a5e9d0ca973595f14；docs(冻结): 记录 D05 模拟器分类与阻塞 | HEAD；558/0 | 干净 | KEEP_PROTECTED |
| 20 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/sep01-tl04-webview-touch-20261030 | codex/sep01-tl04-webview-touch | 88b04441a5110dd9fd12f9563e27b7e01d8c0537；docs(separation): consolidate remote branches and prioritize parity roadmap | HEAD；349/0 | 脏 1 | KEEP_PROTECTED |
| 21 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/sep11-tracka-host-projection | detached | 6af2c0a6e14ac0669c44a0abe0fb30e77e1810df；docs(提醒): 记录权限恢复问题结案证据 | 172288a7c8bf3132aa5c0f10a3ac768f86626d9f；473/75 | 脏 10 | KEEP_PROTECTED |
| 22 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/sep11-trackb-reminder-diagnostics | detached | 1610297706d39bb531a8a2d28b0b1b8feae9a36e；docs(分离): 记录 AtomicFile 回归交付 SHA | 172288a7c8bf3132aa5c0f10a3ac768f86626d9f；473/73 | 脏 6 | KEEP_PROTECTED |
| 23 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/separation-s0-inventory | codex/separation-s0-inventory | 057b39e5d2fd85e9725e7faffbba7ff0ea51c37c；docs(分离): 回填 S0-TL-05 提交证据 | HEAD；450/0 | 干净 | KEEP_PROTECTED |
| 24 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/separation-s0-timeline-drag | codex/separation-s0-timeline-drag | 3d099d37d44fe82e72435517f0325d23cc7239e7；docs(分离): 校正SEP验收证据引用 | HEAD；350/0 | 脏 1 | KEEP_PROTECTED |
| 25 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/skin-eol-fresh-1a7de91 | detached | 1a7de9142791279892b0f6a69e27f51422e5412a；fix(skin): 固定 Clear 皮肤源码检出换行 | HEAD；522/0 | 干净 | KEEP_PROTECTED |
| 26 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/skin-first-separation | feat/skin-first-separation | cc31026f81d4df6704dd89704a4ee93f030a5c00；docs(分离): 补充分离阶段接手与双轨验收约束 | HEAD；472/0 | 脏 5 | KEEP_PROTECTED |
| 27 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/skin-settings-shell | codex/separation-skin-settings | c81690602c980e234500c0fd7714cf3ebd1205a4；docs(分离): 归档提醒诊断验收证据 | 172288a7c8bf3132aa5c0f10a3ac768f86626d9f；473/77 | 脏 1 | REVIEW_FOR_MERGE |
| 28 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/skin-single-default-closure | codex/skin-single-default-closure | b6f559bee65a34cbb85ddcd5d440e7c4d3cafc35；docs(plan): add file-level single-default skin execution roadmap | HEAD；0/0 | 干净（审计前） | KEEP_ACTIVE |
| 29 | E:/Project/Powerful-Calendar/.local/android-test/worktrees/skin-web-kind-selector-15b5480 | detached | 15b5480d2c3e1902c9f790018b22384228a97980；docs(分离): 记录 Clear 正式删除验证 | HEAD；475/0 | 脏 5 | KEEP_PROTECTED |

## 脏 Worktree 的具体内容

### WT-01 — main

- 已跟踪修改：app/src/test/java/dev/powerfulcalendar/ui/v2/SkinPackageManagerTest.kt（新增 28 行测试）；docs/CORE_FREEZE_ACCEPTANCE_LEDGER.csv（2 行差异）。diff --stat 合计 2 个文件，29 insertions、1 deletion。
- 未跟踪测试：app/src/androidTest/java/dev/powerfulcalendar/reminders/D05ProductionReminderLifecycleInstrumentedTest.kt（生产提醒生命周期/进程恢复）；app/src/test/java/dev/powerfulcalendar/ui/v2/DraftDeckFeedbackTest.kt（草稿卡组反馈）。
- 未跟踪 Issue：docs/issues/PC-ISSUE-SEPARATION-TRUSTED-FORMAL-WRITES.md、PC-ISSUE-SEPARATION-WEB-FORMAL-DELETE.md 各 2 字节，属于待补全的小占位文件；PC-OBS-005-commit-feedback.md 为 2,792 字节观察记录。
- design/ 下有 4 组未跟踪设计工程，共 771 个文件、273,408,276 字节：default-skin-01（77）、magical-world-cat（357）、minecraft-world（46）、minecraft-world-dsh（291）。包括设计说明、tokens、HTML/CSS/JS、图片、字体、脚本及 .pcskin 资源；保持完整，不加入本轮提交。
- .local/ 是本地 Android 测试/Worktree/报告数据，未递归扫描；tools/__pycache__/ 与 tools/tests/__pycache__/ 是 Python 缓存目录，均原样保留。
- 风险：main 的 Core 冻结验收 CSV 不可由分离工作覆盖。SkinPackageManagerTest.kt 也出现在未合入分支的文件差异中，后续比较前须核对测试意图。

### WT-03 — ai-error-studio-verify-20261027

- 已跟踪修改：docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md、docs/issues/PC-ISSUE-SEPARATION-AI-CHAT-ERROR-RECOVERY.md、tools/skin-studio/README.md、tools/skin-studio/index.html、tools/skin-studio/tests/e2e_v3_source.cjs、tools/skin-studio/v3-studio.js。
- diff --stat：6 个文件，49 insertions、5 deletions。归属为 Studio 浏览器编辑/错误恢复与对应 E2E、Parity/Issue 证据；HEAD 无独有集成提交。和 WT-22、WT-29 的 Studio 文件重叠。

### WT-04 — chatgpt-core-handoff-20261103

- 已跟踪修改：app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinRecurrenceInstrumentedTest.kt、app/src/main/assets/powercal-clear-default.pcskin（二进制变化）、app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinHost.kt、tools/skin-studio/default-clear/source/web/app.js。
- diff --stat：4 个文件，38 insertions、7 deletions。属于 recurrence/WebView Host 与 Clear 页面诊断改动。
- 此工作区尚待 Android 回归；本次未运行 AndroidTest。其 recurrence 测试和 WT-07 同路径；皮肤包/app.js 与 WT-22、WT-29 有重叠。保留整个修改集，不以代码检查代替设备证据。

### WT-05 — core-feature-api-event-20261101

- 已跟踪修改：docs/SEPARATION_REMOTE_GOVERNANCE_AND_ROADMAP_2026-10-30.md、docs/agent-handoff/PROGRESS.md、docs/issues/PC-ISSUE-SEPARATION-AI-BATCH-MODE-HOST-BOUNDARY.md。
- diff --stat：3 个文档，6 insertions、5 deletions。与 WT-21、WT-26 共改 PROGRESS.md；仅保留登记。

### WT-07 — delivery-final-88b0444-20261030

- 已跟踪修改：app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinFormalDeleteInstrumentedTest.kt（121 行差异）、WebSkinRecurrenceInstrumentedTest.kt（87 行差异）。
- 合计 2 个 AndroidTest 文件，198 insertions、10 deletions。本轮未运行这些设备测试。recurrence 测试与 WT-04 同路径。

### WT-10 — p02-today-512cfd5

- 已跟踪修改：app/src/main/java/dev/powerfulcalendar/core/TemporalModels.kt、app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt、app/src/test/java/dev/powerfulcalendar/core/TemporalProtocolTest.kt、app/src/test/java/dev/powerfulcalendar/ui/v2/PowerCalCasualSuggestionComposeTest.kt、docs/CORE_CONTRACT_FREEZE_2026-10-21.md、docs/PRODUCT_BASELINE_G1-G11.md、docs/agent-handoff/PHYSICAL_QA_AUDIT_AND_FIX_PLAN_2026-10-25.md。
- diff --stat：7 个文件，108 insertions、21 deletions。涉及日期/时间模型、首页与测试；与 WT-15 改同一 TemporalModels.kt 和 Core 冻结契约文档，且涉及冻结 Core，未经独立授权不得合并。
- PowerCalCasualSuggestionComposeTest.kt 与 WT-12 的同路径未跟踪测试重叠。

### WT-12 — pc-issue-166-controller-current

- 未跟踪：app/src/test/java/dev/powerfulcalendar/ui/v2/PowerCalCasualSuggestionComposeTest.kt（12,119 字节），编辑器/日常建议 Compose 测试。与 WT-10 的已跟踪修改同路径，须先比对内容与测试基线。

### WT-13 — pc-issue-172-final-7ceb7fa

- 已跟踪修改：DeepSeekFlashChatDraftFlowProbeTest.kt、AiChatHistoryStore.kt、AiClient.kt、AiDraftService.kt、AiModels.kt、BackupEnvelope.kt、PowerStore.kt、PowerCalApp.kt、VersionContractTest.kt、AiChatClientTest.kt、AiChatDraftPipelineTest.kt、BackupEnvelopeTest.kt。
- 未跟踪：AiTaskSnapshotInstrumentedTest.kt、PowerCalAiMultiTurnInstrumentedTest.kt、app/src/debug/AndroidManifest.xml、app/src/debug/res/xml/network_security_config.xml、AiConversationTaskSnapshot.kt、AiChatMultiturnTaskPipelineTest.kt、AiConversationTaskSnapshotTest.kt、PowerStoreAiTaskReceiptTest.kt、AiProposalReviewComposeTest.kt。
- 跟踪文件 diff --stat 为 12 个文件、683 insertions、119 deletions；未跟踪内容覆盖多轮任务快照、AI 草稿/审批与收据测试。PowerStore.kt、BackupEnvelope.kt、PowerCalApp.kt 涉及存储和核心/Host 边界，需单独审查 Core 冻结与兼容性；未对这些改动做测试或安全结论。
- 与 WT-10、WT-21 都改 PowerCalApp.kt；该工作区与 WT-18 共用同一 HEAD，但修改了不同测试文件。

### WT-14 — pc-issue-173-glass-fallback

- 已跟踪修改：app/src/test/java/dev/powerfulcalendar/ui/v2/GlassComposeTest.kt、TimelineTitleComposeTest.kt。
- diff --stat：2 个测试文件，60 insertions、1 deletion。测试用途由文件名可见；本轮未执行。

### WT-15 — pc-phase1-dst-earlier-offset

- 已跟踪修改：app/src/main/java/dev/powerfulcalendar/core/TemporalModels.kt、app/src/test/java/dev/powerfulcalendar/core/EditorDefaultsTest.kt、docs/CORE_CONTRACT_FREEZE_2026-10-21.md。
- diff --stat：3 个文件，66 insertions、1 deletion。与 WT-10 在日期模型和冻结契约上重叠；Core 冻结语义需要独立 Issue/授权，不合并。

### WT-18 — phase1-task-7ceb7fa

- 已跟踪修改：app/src/test/java/dev/powerfulcalendar/ui/v2/NewEventEditorComposeTest.kt（43 行差异）。HEAD 与 WT-13 相同，但本工作区保留不同的未提交测试修改。

### WT-20 — sep01-tl04-webview-touch-20261030

- 未跟踪：app/src/androidTest/java/dev/powerfulcalendar/ui/v2/TimelineDragWebViewInstrumentedTest.kt（27,172 字节），WebView 时间轴拖动设备测试。
- WT-24 有同路径文件；两份 SHA-256 均为 EF0F2E05007AA2F92188C1902A1732D4848AA2A7659142A14A0B5A09CF667234，确认是完全相同的未跟踪文件。后续只保留一份进入受控提交，当前两份都不动。

### WT-21 — sep11-tracka-host-projection（detached）

- 已跟踪修改：app/src/main/java/dev/powerfulcalendar/reminders/ReminderScheduler.kt、app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt、app/src/test/java/dev/powerfulcalendar/reminders/ReminderValidationTest.kt、docs/V3_HOST_BRIDGE_EXTENSION.md、docs/agent-handoff/PROGRESS.md。
- 未跟踪：app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinReminderProjection.kt、app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinReminderDiagnosticsIntegrationTest.kt、WebSkinReminderProjectionTest.kt、docs/contracts/web-skin-reminder-state-v1.schema.json、docs/issues/PC-ISSUE-SEPARATION-REMINDER-DIAGNOSTICS-TRACK-A.md。
- 跟踪 diff --stat：5 个文件，276 insertions、29 deletions。用途为 Host 提醒诊断/状态投影、调度恢复、测试和契约。HEAD 含 75 个未进集成线提交，属于 WT-27/WT-09 那条提交链的中间快照；该 detached 工作区仍有额外未提交成果。

### WT-22 — sep11-trackb-reminder-diagnostics（detached）

- 已跟踪修改：app/src/main/assets/powercal-clear-default.pcskin（二进制变化）、tools/skin-studio/default-clear/source/web/app.js、style.css、tools/skin-studio/index.html、tests/e2e_v3_source.cjs、v3-studio.js。
- diff --stat：6 个文件，304 insertions、17 deletions。与 WT-03、WT-29 在 Studio 编辑器文件重叠；与 WT-04/WT-29 在默认 skin 包重叠，与 WT-04 在 app.js 重叠，与 WT-26 在 style.css 重叠。
- HEAD 含 73 个主线未有提交，位于 WT-21/WT-27/WT-09 的共同历史链中。未运行 Studio 或 Android 测试。

### WT-24 — separation-s0-timeline-drag

- 未跟踪：app/src/androidTest/java/dev/powerfulcalendar/ui/v2/TimelineDragWebViewInstrumentedTest.kt。与 WT-20 的文件字节级相同，见 WT-20 SHA-256。保留，不重复提交。

### WT-26 — 旧 skin-first-separation

- 此 Worktree 的 HEAD 落后集成主线 472 个提交，另有 5 个未提交文件：docs/agent-handoff/PROGRESS.md、tools/skin-studio/default-clear/source/project.json、default-clear/source/web/fonts/LICENSE-Inter.txt、index.html、style.css。
- PROGRESS.md 有 1,763 行差异（871 insertions、892 deletions）；其余属于 Clear 项目/样式/字体许可材料。虽然分支落后且同时存在新施工 Worktree，这些本地材料未归属、未比对、未提交；一律保留，不在此处施工。

### WT-27 — skin-settings-shell

- 未提交修改：app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinManagementInstrumentedTest.kt，191 行差异。
- HEAD 含 77 个主线未有提交，99 个相对合并基点变化路径，涵盖 WebSkin Host、提醒状态/调度、Studio 源、契约和 Issue。该 Worktree 为 REVIEW_FOR_MERGE；其未提交 AndroidTest 仍受保护，本轮不运行、不覆盖。

### WT-29 — skin-web-kind-selector-15b5480

- 已跟踪修改：app/src/main/assets/powercal-clear-default.pcskin（二进制变化）、tools/skin-studio/default-clear/source/web/edit.js、tools/skin-studio/tests/e2e_v3_source.cjs、tools/skin-studio/v3-studio.js。
- 未跟踪：app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinKindConversionInstrumentedTest.kt（11,836 字节）。
- 跟踪 diff --stat：4 个文件，107 insertions、8 deletions。归属为事件类型转换 UI/测试与 Studio 编辑能力。和 WT-03、WT-22 存在 Studio 文件重叠；skin 包还与 WT-04、WT-22 重叠。

## 主线尚未包含的已提交成果

分支之间的关系先于“候选合并”判断：

- codex/separation-skin-settings 的 HEAD c81690602c980e234500c0fd7714cf3ebd1205a4 是 codex/separation-legacy-import 的祖先。两者间计数为 legacy-import 独有 8 个提交、skin-settings 独有 0 个提交；这是同一条线的前后两个分支点，不是两套互不相关的 162 个提交。
- WT-22 的 1610297706d39bb531a8a2d28b0b1b8feae9a36e 与 WT-21 的 6af2c0a6e14ac0669c44a0abe0fb30e77e1810df 也是该提交链上的中间点（两者相差 2 个提交）。这些 detached HEAD 仍有未提交改动，不能丢弃。

| 主线未有提交的工作树/引用 | 基点、提交差异 | 具体成果与主要路径 | 建议 |
|---|---|---|---|
| WT-09 / codex/separation-legacy-import | 基点 172288a7c8bf3132aa5c0f10a3ac768f86626d9f；85 个独有提交、106 个变化路径 | Bridge JavaScript 静态审计、旧数据迁移、备份恢复、提醒、Skin 管理/恢复和 Studio。路径包括 app/src/main/java/dev/powerfulcalendar/core/EditSession.kt、PowerStore.kt、data/LegacyImporter.kt、reminders/NotificationHelper.kt、ReminderScheduler.kt、ui/v2/SkinPackageManager.kt、WebSkinHost.kt、WebSkinEditBridge.kt、WebSkinFailureRecovery.kt、WebSkinOrigin.kt、WebSkinReminderProjection.kt、WebSkinServiceWorkerPolicy.kt；增加 WebSkinManagementInstrumentedTest.kt、WebSkinServiceWorkerPolicyInstrumentedTest.kt 和多个 JVM 测试、schema、Issue 报告及 tools/skin-studio 代码。 | REVIEW_FOR_MERGE；先按路径和 Issue 审查、核对测试证据；Core/提醒/数据相关变更须按冻结规则单独审批。 |
| WT-27 / codex/separation-skin-settings | 同一基点；77 个独有提交、99 个变化路径 | 同一功能链的提醒权限恢复、提醒诊断/投影、AtomicFile 并发回归、Web 编辑重开恢复、损坏 Skin 回退、Studio Bridge/组件编辑和契约。代表路径包括 app/src/main/java/dev/powerfulcalendar/reminders/ReminderScheduler.kt、ui/v2/PowerCalApp.kt、WebSkinHost.kt、WebSkinReminderProjection.kt，app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinManagementInstrumentedTest.kt，以及 tools/skin-studio/default-clear/source/web/app.js、edit.js、v3-studio.js、project_ops_v3.py、skin_studio.py。 | REVIEW_FOR_MERGE；这是 WT-09 的祖先分支。先确认 77 个提交及 WT-27 的未提交 AndroidTest，再决定是否审阅后续 8 个提交。 |
| WT-21 / detached HEAD 6af2c0a6… | 同一基点；75 个主线未有提交，92 个变化路径 | 提醒权限恢复修复及上述 Bridge/Skin/Studio 共同历史；另外有本台账前节列出的 10 个未提交提醒投影/测试/契约文件。 | KEEP_PROTECTED；通过分支引用审阅提交，通过本 Worktree 单独审阅未提交文件。 |
| WT-22 / detached HEAD 16102977… | 同一基点；73 个主线未有提交，87 个变化路径 | AtomicFile 测试替身竞态和 Web 编辑重开恢复等共同历史；另有 6 个未提交 Clear/Studio 文件。 | KEEP_PROTECTED；视为 WT-09/WT-27 历史链中的快照，不独立 cherry-pick。 |

以上描述的是 Git 可观察的提交/文件范围，不等于这些提交已通过当前验收。此轮没有重跑 JVM、Studio、WebView 或 Android 设备测试；旧提交中的历史测试记录仍需以原证据出处判断。

## 冲突与重复劳动候选

没有未合并索引冲突；下面是不同 Worktree 对相同路径的并发/历史重叠，合入前应逐文件比对：

1. 日期/Core：WT-10 与 WT-15 同改 TemporalModels.kt 和 CORE_CONTRACT_FREEZE 文档；WT-01 另有 SkinPackageManagerTest 改动，而 WT-09/WT-27 提交历史也改过该测试。Core 冻结边界需要单独审查。
2. PowerCalApp.kt：WT-10、WT-13、WT-21 有未提交修改；属于首页/Host 行为交叠。
3. AI/建议测试：WT-10 修改和 WT-12 未跟踪的 PowerCalCasualSuggestionComposeTest.kt 同路径。
4. WebView recurrence：WT-04 和 WT-07 同改 WebSkinRecurrenceInstrumentedTest.kt；当前尚无本轮 Android 回归证据。
5. Timeline 拖动：WT-20 与 WT-24 有完全相同 SHA-256 的 TimelineDragWebViewInstrumentedTest.kt 未跟踪副本。
6. Studio：WT-03、WT-22、WT-29 共同触及 v3-studio.js / e2e_v3_source.cjs；WT-03 与 WT-22 还共同改 index.html；WT-22 与 WT-26 共同改 default Clear 的 style.css。
7. 默认 Skin 资源：WT-04、WT-22、WT-29 均修改 powercal-clear-default.pcskin；WT-04 与 WT-22 还修改默认 Clear 的 app.js。二进制包需从同源源码重建并对照，不能挑一个覆盖其他。
8. 交接文档：WT-05、WT-21、WT-26 都改 docs/agent-handoff/PROGRESS.md。
9. 提醒链：WT-21 的提醒投影/调度未提交修改与 WT-09/WT-27 的已提交提醒实现属于同一开发链，审阅时要区分提交已有内容与未提交增量。

## 处置结论

- KEEP_ACTIVE：仅 WT-28，作为 G-01 建立的唯一施工 Worktree。
- REVIEW_FOR_MERGE：WT-09、WT-27。保留完整分支与工作区；先核验冻结 Core 边界、路径重叠、Android/Studio 测试证据，再决定提交级处置。
- KEEP_PROTECTED：其余 26 个 Worktree。包括所有脏区、冻结 main、测试复现基线和干净历史快照；即使落后数百提交，也不能仅据此归档或删除。
- ARCHIVE_CANDIDATE：无。本轮尚未将各工作区的 ignored 本地证据、构建产物与外部测试记录完整映射到可复原档案。
- CLEANUP_CANDIDATE：无。没有足够证据证明删除任一 Worktree 或本地材料安全。
- 没有执行 merge、cherry-pick、reset、clean、删除、Studio/Android 测试或 Core 修改。

审计台账只描述采样时点。G-02 提交只包含本文件；受保护工作区仍须由其当前所有者继续管理。
