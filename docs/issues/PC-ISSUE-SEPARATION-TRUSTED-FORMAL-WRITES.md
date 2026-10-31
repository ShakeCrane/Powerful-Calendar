# 皮肤正式写入必须经过宿主可信审查

日期：2026-10-27

状态：**本 Issue 的宿主审查实现与所列 JVM/API 35 定向验证通过；代码及报告已推送。SEP-16 全量 Native/Skin 等价仍为 NOT TESTED。**

## 问题与根因

皮肤 JavaScript 通过 WebMessagePort 发出的命令属于不可信输入。之前的 Host 只对重复事件相关的部分编辑保存要求 Android Compose 确认；普通正式事件的 editor.save 和有效草稿的 deck.commit 可以从 Web 命令直接进入持久化写入路径。即使命令受到 host.action 能力检查，这也只证明该皮肤获准请求相应动作，不能证明用户看过并批准了具体写入内容。

问题边界还包括：有效草稿提交可能创建、替换、转换或删除正式事件。若只拦截普通编辑而不拦截这些 deck 写入，脚本仍可绕过宿主审查。

真实 WebView 回归另发现一项数据传输根因：临时 clientDraftId 使用任意随机 63 位整数，经 JSON 暴露给 JavaScript Number 后可能超过 Number.MAX_SAFE_INTEGER；页面将 ID 原样发回时会发生精度丢失，Host 因 ID 不匹配拒绝 patch，表现为草稿没有应用标题等修改。

## 修改及根因对应

实现提交 725e1c79c300272ec9e2f6b1cae3a38dda364733（fix(skin): 皮肤正式写入须经宿主审查）包含 8 个源码/测试文件：

- WebSkinEditBridge 将原有重复事件待审状态扩展为 Host 私有的 pendingWriteReview。非重复正式编辑的有效 editor.save、新建临时会话的有效正式发布，以及持久化草稿 deck 的有效正式写入，先返回 confirmation_required，暂不提交写入。
- 待审记录绑定 Host 内的目标事件/版本、表单或草稿快照与操作类型。Compose 审查通过后，Host 再核对快照和版本，并调用原有 EditSession、NewEventEditSession 或 DeckController 路径。JS 不会取得可自行确认的 token；待审期间的并发写入受到阻止。取消、无变化和不完整草稿继续遵循原有语义，重复事件的既有范围审查继续使用同一 Host 机制。
- NewEventEditSession 的临时 clientDraftId 被限制在 JavaScript 可精确往返的整数范围内。它仍只标识内存会话，不分配持久化事件 ID 或序号。
- 补充 JVM 回归和真实 Compose + WebView instrumentation，检查 Host 审查前后持久化结果、审批、拒绝/取消、快照变化，以及 JS 数值 ID 往返。

修复前的回归确实失败：要求 confirmation_required，原实现返回 saved。红测日志：.local/android-test/reports/separation-20261027/source-editor/recurrence-trusted-review-before.log，SHA-256 4080BF73E16E4F57D34E09BC529495CDD1EB2EDDBDE433EA588E5AD89125A9C7。

## 测试与证据

### 定向 JVM 回归

四个定向 suite 共 **30/30 PASS**：

- NewEventEditSessionTest：7 项，包含 JavaScript 安全整数精确往返及会话副作用回归。
- WebSkinTrustedWriteReviewTest：4 项，包含普通正式编辑、新建正式发布、已有编辑草稿快照复核和正式删除审查。
- WebSkinEditBridgeTest：10 项。
- WebSkinRecurrenceBridgeTest：9 项，包含原有重复实例范围流程和重复事件 Host 审查。

最终定向日志 .local/android-test/reports/separation-20261027/source-editor/trusted-writes-focused-final.log，SHA-256 48305411FF49C09B1291E4AA945449F51172B20EB2EC3420E1F2CF51FB72D2B5。

### 完整 Android Gradle 门禁

执行：

    .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain

结果：**574/574 JVM 测试、80 suites、0 failures/errors/skips、88/88 Gradle tasks**；source boundary、Debug APK、AndroidTest APK 和 lint 均通过。Lint 为 **0 errors / 52 warnings**。完整日志 .local/android-test/reports/separation-20261027/source-editor/trusted-writes-full-gradle-final.log，SHA-256 D87418E9914E471A87FE54AA811B67DBC1668C4AA91BCF8E83CFB8F9456525EC。

完整门禁后只调整了 Android instrumentation 测试中的断言基线和 WebView 脚本隔离，没有再改生产代码；随后 assembleDebugAndroidTest 53/53 tasks 成功，日志 .local/android-test/reports/separation-20261027/source-editor/trusted-writes-androidtest-final-build.log，SHA-256 728DCBA6FB7BB3CF948FA21BDD36D771450B26B3606D0C7BB188723AB580102C。早期一次 instrumentation 尝试的测试夹具数量断言基于错误快照，已改为比较审查前后状态；最终结果以如下重跑为准。

### API 35 模拟器真实 WebView

在隔离 API 35 AVD emulator-5564、隔离 QA application ID dev.powerfulcalendar.d05qaskinrec20261027 上，运行真实 Compose Host 与 Clear WebView instrumentation，**2/2 PASS**：

- webFormalEditAndNewPublishWaitForNativeHostApproval：普通正式编辑和新建事项发布，在 Host 批准前不写入；由宿主确认后才完成写入。
- actualWebViewEditsOnlyTheSelectedRecurrence：原有重复实例编辑仍只修改选定实例。

最终 instrumentation 日志 .local/android-test/reports/separation-20261027/source-editor/trusted-writes-emulator-5564.log，SHA-256 2503C1A4E5E06BCE96D14CB296C0C74DDADC9A63363EDADA4E44B0E9C3863F8F；日志末尾为 OK (2 tests)。该次安装 APK：

- QA Debug APK SHA-256：0BFA6BABF47BB64F24A2F87D7B59146D9F9DE0DAC6DD791B923EC2D7DADFF9F4
- QA AndroidTest APK SHA-256：A2B856A60D8B6AFB642F6E8EB2224F2E3BDFF6AC4F8BB38BF9264C2CE21356E0

对应 APK 与构建日志存放于忽略目录 .local/android-test/reports/separation-20261027/source-editor/。本次没有使用真实用户数据库、生产包或实体设备。

## 签核范围与剩余风险

- **本 Issue 已验证**：Host 能阻止未确认的普通编辑与新建发布；明确批准后沿原有核心写入路径完成；相关快照/版本和临时 ID 回归通过；重复实例路径继续通过 API 35 WebView 测试。
- SEP-16 在矩阵中继续标记 **NOT TESTED**：这代表该行要求的完整 Native/Skin 业务结果等价还没有完成，不否认本报告中的窄范围安全行为证据。API 35 实测只覆盖一个普通 ALARM 编辑、一个新建 ACTIVITY 发布及重复实例场景；deck 的 CREATE/REPLACE/CONVERT/DELETE 组合、四类事件完整对照、异常恢复、进程死亡与完整生命周期尚未全部经过 WebView 端到端验证。
- 没有实体/OEM 设备测试；按用户约定由用户负责实体设备。不能把 AVD 结果当作实体设备签核。
- host.edit 与 host.action 的能力门禁差异、所有 Host 命令分派的系统安全审计，以及恢复/冲突路径的皮肤等价仍需独立完成。
- Studio / Chrome Mock 全量没有在本 Issue 重跑；修改集中于 Android Host/桥接，集成行为由 JVM 和上述真实 Android WebView 验证覆盖。其他皮肤外观、无障碍和完整功能 parity 仍未签核。
- 52 条 lint warning 未阻断构建；没有在本 Issue 中清理无关告警。
- 分离工作树里已有的 Studio project.json、字体许可证、HTML、CSS 变更未纳入代码或文档提交。git diff --ignore-space-at-eol --stat 未显示这些文件存在实质内容差异，本次保留其原状。

## 提交与推送

实现和测试提交：725e1c79c300272ec9e2f6b1cae3a38dda364733，已推送到 origin/feat/skin-first-separation。本报告、进度记录和 SEP-16 矩阵更新随后作为独立文档提交推送；两次提交分离，便于按代码或结案文档单独回档。

整体皮肤分离、功能等价、Release QA 和默认皮肤切换仍未完成；本 Issue 不构成总体 GO 或 cutover 授权。

## API 35 AI CREATE trusted-write follow-up（2026-10-31）

SEP-16 的 API 35 AI WebView 覆盖此前已通过本地 stub 检查 REPLACE/DELETE 的 Host 审核，以及 STAGE/REJECT/SUPPLEMENT 决策，但没有验证 AI `CREATE` 正式发布从 Clear 提案卡到 Host 授权再到 Store 的真实闭环。本次只补 AndroidTest，不改 AI、Bridge、Store、默认皮肤或 Clear 源码。

新增 `PowerCalAiProposalReviewWebViewInstrumentedTest.actualClearWebViewPublishesCreateOnlyAfterHostConfirmation`，使用隔离 QA application ID `dev.powerfulcalendar.d05qasep16create20261031`、唯一数据库/偏好命名空间和设备本机 `local-stub:free` loopback 服务：

1. 提案卡出现后，Store 正式事件、草稿、pending、commit、identity 和下一个序号均不变。
2. 点击 Clear 的“批量同意”后，PowerCal 显示独立 Host Compose “确认 AI 批量正式操作”对话框；未确认时 Store 仍不变。
3. 取消后无写入且提案可再次审核。
4. 再次明确确认后，Store 恰有一条预期 ACTIVITY、一个 commit、一个 identity、一个序号增量且无 pending 草稿。Core 会保留一条已解决 AI 审计草稿，因此总 draft row 增 1；这不是待处理草稿。

首次整类 3 项运行时，新增测试最后把 `draftCount` 误当成 pending 数，导致断言 `0` 对实际已解决审计行 `1` 失败；未发现 Host 未授权写入。对照 `AiDraftService` 与现有 JVM 合同后，将断言修为“正式发布时保留一条已解决审计草稿”，发布前和取消阶段仍要求计数不变。该失败保留在 `connected-android-test.log`，不冒充生产缺陷，也不隐去失败。

### 新增证据

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| AI CREATE 单项真实 Clear WebView | `PASS` | 聚焦用例 1/1，`connected-android-test-rerun.log` SHA-256 `45632A58915360CFA5FC77875426A7141CD4815859F5E3641EB36644D616618E`。 |
| Clear AI proposal WebView 类 | `PASS` | 3/3：CREATE 批准/取消、REPLACE/DELETE Host 审核、STAGE/REJECT/SUPPLEMENT；已在测试提交 `39554cc` 上重跑。XML SHA-256 `C22AC4A3F6535A5CDCAE2F9A0DCFCDDC5D57EB233472397BD009B1E3527B7B69`；Gradle 日志 `connected-android-test-post-commit.log` SHA-256 `03F2AE28D6C000E76DD1F0629AC1E12B9B6C6A7E3F27919A9F2ACD208EA74248`。 |
| Host/Core JVM 定向回归 | `PASS` | `WebAiProposalReviewIntegrationTest`、`WebSkinEditBridgeTest`、`WebSkinRecurrenceBridgeTest`、`WebSkinTrustedWriteReviewTest` **69/69**；另 `AiTaskPipelineTest` **1/1**。日志 SHA-256 `9BE639AE8C4E652F1EAFF55E1AD2922C91772D87AFE4384CA95348A7AF8412D2` / `3AA8B227949B276B3AB23B65A102487A750E120A8FDF34BD1F6B5FF599B34BFA`。 |
| API 35 QA APK | `PASS` | Debug QA APK SHA-256 `00E3921B742A2F601488BEBBD8246FB3016F576AC811E3D9AC6F7943E9961301`；AndroidTest APK SHA-256 `04BE0577802BB9727A30E1AFBA5670121DB480D8B383A8354714B741913852F7`。 |

证据目录：`.local/android-test/reports/sep16-ai-create-20261031/`。本次未接触真实 Provider、付费模型或应用默认数据库；没有卸载/清空应用数据或 wipe AVD。API 35 不是实体/OEM 验收。

这使 AI proposal 的几个常用决策拥有真实 WebView → Host 审核证据，但**不关闭 SEP-16**。四类事件完整对照、其他正式写入/草稿组合、版本冲突与并发、生命周期/进程恢复、完整 Native/Skin replay、真实 Provider 和实体/OEM 仍未全部验证。

## API 35 Clear deck CONVERT trusted-write follow-up (2026-10-31)

The editor kind-conversion flow already had WebView evidence, but it did not exercise a persisted `DraftOperation.CONVERT` through the draft deck's `deck.commit` Host review. Added `WebSkinKindConversionInstrumentedTest.clearDeckConversionWaitsForHostApprovalAndCancellationPreservesCandidate` without production-code changes.

- On API 35 AVD `codex-clear-api35` / Android 15 / `emulator-5568`, the test seeds one isolated formal ALARM plus a valid ALARM→ACTIVITY CONVERT candidate, opens the packaged Clear deck and invokes its save/publish action.
- Before Host approval, the formal event, commit count, identity count, sequence watermark, draft count and pending count remain unchanged. Cancelling the Compose review preserves the formal event and pending CONVERT candidate. Retrying and approving updates the same event to revision 2 / ACTIVITY with the expected end time; the commit count advances once, identity remains 1 and sequence remains 2.
- Focused API 35 instrumentation **1/1 PASS**; the full `WebSkinKindConversionInstrumentedTest` class **2/2 PASS**. Focused log SHA-256 `90FC035AA4B1C6246324BC782EECFC9C27A3CE58B365C00D6329DD5710AB1A63`; class log SHA-256 `0CE8E120AB75EC962C3236BF9477CB37E283532108AA81BAACA307A3FF50B31A`; filtered device trace SHA-256 `0C4E9EF2F2616725A1A7666147C1F3D7EDBF3AC12AC645C11CF3A353DE808C67`.
- Full offline Gradle gate: **784/784 JVM tests**, 125 suites, zero failures/errors/skips; both source boundaries, AndroidTest compilation, Debug and AndroidTest APK assembly and Lint passed (**171/171 tasks**, 0 lint errors / 55 warnings). Gate log SHA-256 `05F148EDF6BE543EB63AE14815B3D5FD5AC10F3FABFBD764636859BFDF97C528`. Debug APK SHA-256 `779B0CF777BD5E1EE377249CD4156DB426DC87690B6D35EDD458E5F27A27CD1B`; AndroidTest APK SHA-256 `A3D6F5FBE3DFE8BA89020A114890404525412F7FB86A99BB1D85F3BD1F5D03D2` (1,486,788 bytes).
- Logs are under `.local/android-test/reports/sep16-deck-convert-20261031/`. The test uses an isolated UUID namespace and teardown scans found no database, preference or cache residue. This is emulator-only evidence; no physical/OEM, production database or Native/Skin paired replay is claimed. The two other-Agent instrumented test edits remain separate and untouched.

This closes only the Clear deck `CONVERT` confirmation slice. SEP-16 remains **PARTIAL / HOLD / INCOMPLETE** pending broader event-kind/operation combinations, AI `CONVERT`, conflicts/concurrency, lifecycle/process recovery, Native/Skin paired replay and external device evidence.

## API 35 Clear AI CONVERT trusted-write follow-up (2026-10-31)

The frozen AI protocol explicitly supports `CONVERT` proposals. Added `PowerCalAiProposalReviewWebViewInstrumentedTest.actualClearWebViewConvertsExistingEventOnlyAfterHostConfirmation`, using the existing local free stub and Host confirmation path; no AI service, Bridge or Core implementation changed.

- On `codex-clear-api35` / Android 15 / `emulator-5568`, the fixture seeds one isolated formal ALARM. With shared context enabled, Clear sends the explicit target to a device-local `local-stub:free`; the stub returns a typed ALARM→ACTIVITY `CONVERT` proposal. The proposal is shown in the actual packaged Clear WebView and uses the existing Compose “确认 AI 批量正式操作” dialog.
- Before Host approval and after cancellation, the formal snapshot, draft/pending counts, commit count, identity count and sequence watermark remain unchanged; the proposal stays available for retry. After approval, the same event is ACTIVITY revision 2 with the expected start/end, one additional commit, unchanged identity count and unchanged sequence. Device trace: `eventSeq=1; revision=2; kind=ACTIVITY; commits=2; identities=1; sequence=2; cancel-preserved=true`.
- Focused instrumentation **1/1 PASS**; the whole `PowerCalAiProposalReviewWebViewInstrumentedTest` class **4/4 PASS** (AI CREATE, REPLACE/DELETE, STAGE/REJECT/SUPPLEMENT, and CONVERT). Focused log SHA-256 `2786469AA87F95BAC505D45C30B26C355B10F0925EB40785531FC29F3A12D930`; class log SHA-256 `8BDF1A44EE43DDED72D950581A39D7EC58AC8AA0A33FF67DE3178595F31E5882`; filtered device trace SHA-256 `72DE7100E5817C2BE7269E411D8E8410625DFC1440A9AE94C57AEBD1531174D2`.
- Full offline Gradle gate: **784/784 JVM tests**, 125 suites, zero failures/errors/skips; both source boundaries, AndroidTest compilation, Debug and AndroidTest APK assembly, and Lint passed (**171/171 tasks**, 0 lint errors / 55 warnings). Gate log SHA-256 `4EB1CEB30D035D7304FB0B6CDC7391378EDCB856576B3567512617DEC0FBE12D`. Debug APK SHA-256 `779B0CF777BD5E1EE377249CD4156DB426DC87690B6D35EDD458E5F27A27CD1B`; AndroidTest APK SHA-256 `23ED5DF06AB00C4056C625DE2EE3B8816A0F8567B87A7CB08741BB9B1335EAB8` (1,491,972 bytes).
- Evidence is under `.local/android-test/reports/sep16-ai-convert-20261031/`. The fixture database/preferences/cache were isolated and post-run residue scans were empty. No paid/external provider, production database, or physical device was used.

This closes only the AI `CONVERT` confirmation slice. SEP-16 remains **PARTIAL / HOLD / INCOMPLETE** for remaining kind/operation combinations, version conflicts/concurrency, lifecycle/process recovery, complete Native/Skin paired replay and physical/OEM evidence.

## API 35 Clear trusted writes across all four event kinds (2026-10-31)

Added `WebSkinAllKindsTrustedWriteInstrumentedTest` as a standalone test file, leaving other in-flight instrumentation files untouched. No Runtime, Core, Host, Clear skin source or Studio source changed.

- `clearTransientFormalPublishForEveryKindWaitsForHostApproval` opens each of the four kinds through Clear's visible new-event chooser. It checks the Host form kind, transient/no-write counters, read-only Host review, cancellation without writes, then retry approval. Each of ALARM, ACTIVITY, DEADLINE and MEMO creates exactly one formal event, commit, identity and sequence; it leaves draft/pending counts unchanged.
- `clearFormalEditForEveryKindWaitsForHostApprovalAndKeepsIdentity` seeds one formal event of each kind into a UUID-namespaced Store, opens it through the Clear timeline or Memo page, changes its title, and requests Host review. For every kind, the original record and counters remain unchanged before approval and after cancel; retry approval updates the same `eventSeq`, increments revision and commit exactly once, and creates no identity, draft or pending item.
- Both cases use the packaged Clear WebView, production `WebSkinEditBridge`, Android Compose Host review and isolated `PowerStore` on API 35 / Android 15 / WebView 124 / `emulator-5570`. The page controls are invoked through the instrumented WebView DOM; this proves actual packaged UI routing and Host outcome, but does not claim physical-finger/TalkBack evidence.
- Final installed-APK instrumentation class: **2/2 PASS**. Log `.local/android-test/reports/sep16-all-kinds-20261031/all-kinds-final-installed-apk.log`, SHA-256 `1BE964F29B748783D5D924E13664815FD1D2A6AFFCD0304031ECCB8ACF6CB458`.
- Full offline Gradle gate: **784/784 JVM, 125 suites, zero failures/errors/skips; 171/171 tasks PASS**, including both source boundaries, Debug and AndroidTest APK assembly and Lint (0 errors / 55 warnings). Gate log SHA-256 `BB4F1981632A2689C07501DD89110A25B629DDEFC40618885878C3B76011E1B6`.
- Installed Debug APK SHA-256 `8CC46EED2FE1569CB349C7C913C3BFFE85344E4C6E9C527342373367C3E8F886` (66,518,075 bytes); AndroidTest APK SHA-256 `13A2FE5C3487EEA4564F7AB54BE9549959994EAAFB93E808C1A1B369ABC4725C` (1,551,120 bytes).

The first AndroidTest compile reported a missing test-only Compose import; the import was added and `assembleDebugAndroidTest` then passed before device execution. This was a harness compile issue, not a Runtime behavior failure. Evidence is under `.local/android-test/reports/sep16-all-kinds-20261031/`.

This closes direct Clear Host-review coverage for ordinary transient publish and formal editor save across all four event kinds. It does **not** close SEP-16: persisted deck CREATE/REPLACE combinations, complete conflict and process-recovery matrices, same-fixture Native/Clear outcome replay, TalkBack and physical/OEM validation remain open. Existing Studio Preview confirmation is a non-writing Mock and was not rerun for this AndroidTest-only change. SEP-16 remains **PARTIAL / HOLD / INCOMPLETE**.

## API 35 Clear persisted deck CREATE / REPLACE / DELETE review (2026-10-31)

Extended the same isolated AndroidTest class to cover the formal writes that go through a persisted deck card. No production Runtime/Core/Host/Clear or Studio source changed.

- `clearPersistedDeckCreateForEveryKindWaitsForHostApproval` opens four saved CREATE cards through Clear's actual draft deck: ALARM, ACTIVITY, DEADLINE and MEMO. For each card, Host review occurs before the formal write; cancel preserves the candidate and counters; retry approval publishes the same reserved eventSeq with exactly one commit and no new identity or sequence. The test waits 650 ms between terminal actions to respect the existing `DeckController` 600 ms double-action guard.
- `clearDeckReplacementProposalWaitsForHostApprovalAndReusesIdentity` uses Clear search to locate a formal event, creates the actual Replace proposal from the Clear UI, edits its title, then verifies Host cancel preserves the original formal record and candidate. Retry approval updates the same eventSeq at revision +1 with one commit and no additional identity/sequence.
- `clearDeckDeleteCandidateWaitsForHostApprovalAndCancelPreservesEvent` seeds one valid pending DELETE candidate in an isolated Store, opens the candidate in Clear, then verifies Host cancel leaves the formal event active and pending candidate intact. Retry approval creates the tombstone once, retires the same identity, and does not advance sequence. The candidate is seeded to isolate the `deck.commit` path; AI proposal staging is covered separately above.
- Final API 35 / Android 15 / WebView 124 / `emulator-5570` installed-APK test class, now including all-kind transient publish and editor-save tests: **5/5 PASS**. Log `.local/android-test/reports/sep16-all-kinds-20261031/all-sep16-final-apk-class.log`, SHA-256 `67BB09D4E11A0A018F2BDBAE675243123E0FD65E16E5328E1A9DC9EF0EC86E18`.
- Full offline Gradle gate: **784/784 JVM, 125 suites, zero failures/errors/skips; 171/171 tasks PASS**, including both source boundaries, Debug/AndroidTest APK assembly and Lint. Gate log SHA-256 `2781BC369CCA0516253E7EB7FF7FD5A385F633AD35C8F003D7430F65A14177A8`.
- Debug APK SHA-256 `8CC46EED2FE1569CB349C7C913C3BFFE85344E4C6E9C527342373367C3E8F886` (66,518,075 bytes); AndroidTest APK SHA-256 `670A8463983C5D62886A3E197DC9327ED87B4D1B7F07E8FE73056B44CF087652` (1,560,284 bytes).

Two intermediate failures were test-harness issues and remain in the ignored evidence directory: an initial DELETE assertion looked for generic `确认`, while the Host intentionally labels that action `确认删除`; the assertion now targets that exact label. An early combined deck-CREATE run attempted the next card inside the Core's 600 ms terminal-action guard; the test now models an intentional follow-up after the guard instead of weakening the frozen double-action protection. The Host safely rejected that too-fast test action without writing. Final installed-APK class passed 5/5. Studio and AI source/interfaces did not change, so neither Studio Preview nor Studio Python tests were rerun in this slice.

This adds direct Clear review evidence for the listed persisted deck operations; it is **not** same-fixture Native/Clear parity proof. Competing-version deck conflicts, restart recovery across each operation, actual-finger/TalkBack operation and physical/OEM acceptance remain unverified. SEP-16 remains **PARTIAL / HOLD / INCOMPLETE**.

## Clean-source reproducibility check at pushed commit `720bf6b` (2026-10-31)

The preceding shared-worktree gate included two independent, uncommitted AndroidTest file edits. To bind the reported deliverable to the pushed integration source, I exported commit `720bf6bfc17960f93491ebf9be8534b4ade73de0` to an isolated clean snapshot and reran the complete gate and the full trusted-write class. The source archive SHA-256 is `03556F64F0F84CE371C9DEBF79F56C3F080DEA0F0C15D50169F03F0FD65A18D8`.

- Command: `:app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain`.
- Result: **784/784 JVM tests, 125 suites, zero failures/errors/skips; 171/171 Gradle tasks succeeded**. Lint: **0 errors / 51 warnings**. Clean gate log SHA-256 `E2E0ECFCBB4C753F69EEADF3B1E77FF917A5872B4A03AB54E1EF7F93ADD34F6B`.
- Clean-source Debug APK SHA-256 `8CC46EED2FE1569CB349C7C913C3BFFE85344E4C6E9C527342373367C3E8F886` (66,518,075 bytes); AndroidTest APK SHA-256 `CC8E2398741A3F70BF258991FBA4845474C67B071CA85E28B5DC3364A3849F2A` (1,552,468 bytes).
- Installed that exact APK pair on Android 15 / API 35 / WebView 124 `emulator-5570`, preserving app data. `WebSkinAllKindsTrustedWriteInstrumentedTest`: **5/5 PASS**; clean-source device log SHA-256 `50B2F962AD560A88A6AA44CB2B33A0C2167F7515D20A63FF92839F9BCBAF2E5A`. The log, gate output and archive are retained under `.local/android-test/reports/sep16-all-kinds-20261031/`.

This replaces ambiguity about whether the prior AndroidTest APK included other-agent edits; no such edits are present in this clean-source result. The evidence remains emulator-only and covers the five listed paths. SEP-16 remains **PARTIAL / HOLD / INCOMPLETE** for conflict/restore and restart recovery across operation paths, same-fixture Native/Clear outcome replay, TalkBack and physical/OEM acceptance.
