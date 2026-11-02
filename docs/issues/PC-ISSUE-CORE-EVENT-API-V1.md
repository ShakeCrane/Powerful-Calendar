# PC-ISSUE-CORE-EVENT-API-V1 — Event Feature API v1 editor slice

日期：2026-11-01（北京时间）｜优先级：P1
状态：**PARTIAL / EDITOR SLICE IMPLEMENTED; PHASE 1 OPEN**

## 目标与结果

建立不依赖 Compose 的 Core Feature API 契约，并让 Native 与 Web 的普通事件新建、正式编辑和删除路径使用同一 Host 创建的 Event API 实例。冻结业务实现与数据格式保持原样；此次没有修改 `PowerStore` 的持久化规则或数据库 schema。

当前变更新增纯 Kotlin JVM `:core-api`，定义 `CoreFeatureApiV1`、`EventFeatureApiV1`、稳定 DTO、状态回执、能力声明、revision 与变更通知。App 侧 `PowerCalCoreFeatureApiV1` 适配既有 Store/EditSession/NewEventEditSession；PowerCalApp 为同一 Store 创建一个实例并注入 Native 编辑器回调和 Web Bridge。Web 时间轴移动通过 API 做版本守卫并暂存 replacement。Host 仍独占 Android context、Store 构造和可信删除确认；正式删除要求绑定编辑器/事件/版本的 Host-issued 单次 approval。

正式契约及其明确限制见 [`CORE_FEATURE_API_V1.md`](../CORE_FEATURE_API_V1.md)。这是已运行的 event-editor pilot，不是可独立打包的 Core runtime，也不是 Phase 1 或 Skin parity 完成报告。

## 修复与回归内容

1. 新建、正式编辑、重复范围、正式删除、检查点、事件查询和变更事件通过版本化 API DTO 表达；Core-owned source/zone/重复字段不能由表单输入覆盖。
2. Native editor 和 Web editor 的 API handles 不公开 Store 或 Core entity。API adapter 持有编辑 session；delete approval 由 Host 内部签发并由对象身份及版本校验。
3. Web Bridge 使用显式注入的 API，不会自行构造第二个 API；默认事件时间来自 Core adapter 注入的 `Clock`，不再由 Bridge 单次命令替换时钟。
4. 修复关闭 session 后的 formal save/delete receipt 重放：请求缓存现在先于 session 存在性检查；API 测试在成功保存/删除并关闭 handle 后仍验证同一 request ID 返回 duplicate receipt，且无第二次写入。
5. Web 桥接回归覆盖 staged receipt detail、Host lock preflight、正式编辑 session 保持、重复范围与共享 API 实例。

## 验证与证据

| 检查 | 结果 |
| --- | --- |
| Event API、Native editor Compose、Web editor/recurrence/authorization 聚焦套件 | **66/66 PASS**，0 failures/errors/skips |
| `WebAiProposalReviewIntegrationTest` 独立类 | **15/15 PASS** |
| 全量 JVM forced run A | **789 tests，1 failure**：`replaceAndDeleteCandidatesCommitOnlyAfterTrustedHostConfirmation` 未找到 Host 确认节点；该方法单独 forced rerun **1/1 PASS** |
| 全量 JVM forced run B | **789 tests，1 failure**：`memoPagingContinuesTheSortedProjectionWithoutStoreWrites` 等待 60 秒后 `hostDataReady=false`；整个包含它的类独立运行 **15/15 PASS** |
| 后续标准全量 JVM run | **127 suites / 789 tests，0 failures/errors/skips，PASS** |
| `:app:verifySkinSourceBoundary`、`:app:verifyNativeSkinBoundary` | **PASS**，在两次完整 Gradle task graph 中执行 |
| `:app:assembleDebug` | **PASS**，组合门禁中执行 |
| `:app:lintDebug --offline --console=plain` | **PASS**，0 errors / 54 warnings |
| Android instrumentation / 当前并行 AVD | **NOT TESTED**；未连接或操作 AVD |

两次 forced full JVM run 均有单一且不同的 Web AI/Host 集成失败；失败方法及整个测试类在独立运行时通过。根因尚未确定，不能断言与本次 `PowerCalApp` 改动无关。随后标准全量 JVM run 在当前 HEAD 通过 **127 suites / 789 tests**，因此当前全量 JVM 状态为 **PASS**，并保留前两次 forced run 作为未解释的不稳定观察。

详细日志均位于忽略目录 `.local/android-test/reports/core-feature-api-event-20261101/`：

- `focused-jvm.log` — SHA-256 `0A75CC507F095A85C20902E1D662F3DA5F207FF0A1CAFCF1A018EBA8024E26DD`。
- `full-jvm-build-lint.log` — 组合强制门禁和 Host 确认节点失败；SHA-256 `D679CA2AB3582D01362C2A5F17A4D090FF208F56B4E44C6E56060F08F124E365`。
- `full-jvm-rerun-3.log` — 第二次最终状态 forced JVM run 与 memo paging timeout；SHA-256 `F85BE67FD1FDDFA9DFE0318D331258E8B39F36F2033B7BFB78254E013BAAAA71`。
- `full-jvm-final-attempt.log` — 后续标准全量 JVM **789/789 PASS**；SHA-256 `62EB9A45F758666302F47F2F15B309876394140715082A71678559C67AB54A82`。
- `ai-replace-delete-isolated.log` — 失败方法 isolated rerun **PASS**；SHA-256 `2BEBBD440FB8DEDD76D0B4A611F886346B9E9EE4C73FAE9ACF585AF6B91FF66E`。
- `web-ai-review-class.log` — 包含 memo paging 的测试类 **15/15 PASS**；SHA-256 `51CBB9385B7A102ADBF8B0839C79496F092FF7B275896D1CFC40389B891F9E52`。
- `lint-debug.log` — Lint **0 errors / 54 warnings**；SHA-256 `1FD9684D5B00B1047E84C0506CBAB1AE18DDB0483FC340069A2C1719AB615180`。

本次 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，66,601,582 bytes，SHA-256 `EF77D5BB3B83834E01779DD86DF498DC9D8CCE192707C90FAAB1D2CA4AD7CDB1`。它由本 worktree 当前源构建，未安装到任何设备。

## 未关闭缺口

- Native/Web 的 Draft Deck formal replace/delete/commit 仍有 `DeckController` / direct Store 路径；它们不会更新当前 adapter 的 `featureRevision` 或通知订阅者。
- Memo completion、AI/import proposal 和其他现存 Host 功能尚未通过相应 Core APIs 统一；v1 当前没有完整 Draft/Review、completion、Calendar paging、AI、Reminder、Import/Export 或 Settings/Data API。
- Web Bridge retains timeline geometry snapping and a Web-only 1900–2200 target-year gate; the latter has no matching Native check and awaits one owner policy. Repeat/type eligibility, date-only DEADLINE handling, no-op detection, event-zone form construction, and replacement staging now use the shared Event API. Formal-event search and some Store reads remain separate Host/Core boundary work.
- Native runtime 与 Web runtime 的配对测试还不是完整 `PowerCalApp` Native UI 到 Web UI 的双路径设备回放；instrumentation、TalkBack、实体/OEM 和 skin parity 均 NOT TESTED。
- API 请求收据上限 128 且仅在 adapter 实例存活期间有效；不能当作跨进程 exactly-once 保证。

## Git 与后续

实现分支：`codex/core-feature-api-event`，代码提交 `65a96b7ba97a639981d23eee7e05f8f39a85a68e`，基于 `c09d39d8161b113ad62ce8348760a2620017869b`。提交前重新 fetch 验证远端仍在该 base，并已普通快进推送至 `origin/feat/skin-first-separation`。本报告与 Progress 全量测试证据更新通过单独文档提交一并推送。

Phase 1 保持进行中。下一项为显式 Draft/Review API：收口 Native/Web Deck 的 formal 写入及统一变更来源。不得据此删除 Native fallback、切换默认 Skin 或宣称 cutover GO。

## Follow-up — Web no-op new-event save uses Event API receipt (2026-11-02)

- **Problem:** For a temporary new-event editor with no meaningful changes, `WebSkinEditBridge` returned the wire status `no_changes` from the Core-owned `meaningfulChanges` snapshot flag without calling `EventFeatureApiV1.saveNewEvent`. The Web response was correct, but the Core mutation receipt/cache and shared command path were bypassed.
- **Change:** The Bridge now calls `saveNewEvent` for this no-op. Core returns `NO_CHANGE` before Store persistence; Web maps it to the same `no_changes` response and refreshes the detached editor snapshot. No formal event, draft, or identity is written. No API contract, Store transaction, schema, or backup format changed.
- **Regression:** `WebSkinEditBridgeTest.unchangedWebNewEventUsesCoreNoChangeReceipt` verifies the request reaches Event API and returns `NO_CHANGE`, creates no Store rows, and an exact Web request replay does not invoke the API again.
- **Focused:** `WebSkinEditBridgeTest` **40/40 PASS**; XML SHA-256 `FC344CA0E43771086FE3CE420272901CDA1724C7D13980F1F55708D273EC0625`; focused log SHA-256 `A874983FB0685786D9A556CD0B2FE36E2295091C601FC433230AEFB10C6FB645`.
- **Full verification:** Forced full JVM rerun **909/909 PASS / 139 suites / 0 failures, errors, or skips**; both source boundaries, Debug APK, lint (0 errors / 54 warnings), AndroidTest Kotlin compilation, and `git diff --check` passed (154 tasks). APK **67,043,950 bytes**, SHA-256 `805F58A5748BC0A09CAAE2A2005557640FFFB14C4E1BEB273206608188F47448`. Full log SHA-256 `6A520A47F09099A58B253B16AA46B7BBEB047471D23FFD76DCF092531B55BFFE`; reports: `.local/android-test/reports/phase1-web-new-event-receipt-20261102/`. The log also contains one unassigned non-fatal `System.logW: A resource failed to call close.` warning; no corresponding test XML reports a failure.
- **Limits:** No ADB device or AVD was available. Instrumentation, packaged WebView, and runtime Native/Skin parity are **NOT TESTED**. Phase 1 remains open. Code commit `161c143c67a7e198771eb71fd9f3e11ac0a1fc7f`.


## Follow-up — startup abandoned-editor lease recovery (2026-11-02)

- **Code:** `1944a3f44e5b22b4b60186995f8373dbf971e182`, fast-forward pushed from `9bb0df583afb2c7001cc5c9ffcc4fd0489ee852b` to `origin/feat/skin-first-separation`.
- Added `EventFeatureApiV1.recoverAbandonedHumanEdit(staleAfterMillis)` to the pure Kotlin API and Store adapter. `PowerCalApp` startup now calls it on `Dispatchers.IO` instead of invoking `PowerStore` directly.
- The existing Store rule is unchanged: only a lease owned by another process and strictly older than the threshold is deleted; the default remains five minutes and thresholds below 60 seconds fail validation. A repeated recovery returns `false`. Recovery does not modify formal event data, increment Event `featureRevision`, or publish an Event change.
- **Focused regression:** `EventFeatureApiV1Test` **8/8 PASS**, 0 failures/errors/skips. The added `storeBackedApiRecoversOnlyForeignStaleHumanEditLeases` covers no lease, fresh lease, minimum-threshold rejection, default timeout, explicit 60-second recovery, repeated call, and unchanged revision/event notifications. JUnit XML SHA-256 `11B433B49EFAC9E77A556CD2BB66B29FC1C0AF8A1603DB9560949EAF05D787AB`.
- **Forced serial offline gate:** **878/878 JVM tests across 137 XML suites**, 0 failures/errors/skips; both source boundaries, Debug APK assembly, lint (**0 errors / 54 warnings**), AndroidTest Kotlin compilation, and `git diff --check` passed; 154 actionable tasks executed. APK **67,011,182 bytes**, SHA-256 `CE50CEDE2D787590D8A68ED885398096DFF1B3D99B2F00C96C36972A974C3137`. Full log `.local/android-test/reports/core-event-lease-recovery-20261102/full-gate.log`, SHA-256 `0E0217931863895A408115D524D08E0EA23E119359937B3138AABD857AE442BE`.
- No device or AVD was available; Android instrumentation and runtime WebView/Native behavior remain **NOT TESTED**. The slice changes no schema, backup format, or frozen business semantics and does not authorize a skin cutover.
- **Next PHASE 1 action:** Add a read-only pending-draft queue query to `DraftReviewFeatureApiV1` and route the remaining `PowerCalApp.reload()` call away from `store.pendingDrafts()`. Other Store diagnostics remain separate work.

## Follow-up — Web recurrence-scope eligibility delegated to Core (2026-11-02)

- **Root cause:** `WebSkinEditBridge` repeated the Core edit-session checks for repeating-event eligibility, unsaved field changes, and the occurrence key required by non-whole-series scopes. These checks already run in `EventFeatureApiV1.selectFormalRecurrenceScope()` through `EditSession.selectRecurrenceScope()`.
- **Change:** Removed the Bridge's business-rule short circuit. It still validates its own session ID, calls the shared Event Feature API, and maps API status to the existing Web result and generic validation message. Successful selection refreshes the Host projection from the API snapshot; rejected selection leaves the formal event and Store unchanged.
- **Focused regression:** `WebSkinEditBridgeTest` **38/38 PASS**, including non-repeating event, missing occurrence key, and dirty form rejection through the real Core API. JUnit XML SHA-256 `3D0BDA229DB8C3F8B7A673C427AFCF494CE592257F79A27EEC3495258C218D87`; focused log SHA-256 `852423ED97BA25EB74F9903A7DF29CE31BE07A247A1DCAC1FCD6426396FA03E3`. The first run caught a JUnit test-signature error (`JSONObject` return instead of `Unit`); the corrected rerun passed. Initial failure log SHA-256 `EFF7697FE864199978385B1B5933274D6CAF2149E5DF22E4C90780E4CB8B491E`.
- **Forced serial offline gate:** **907/907 JVM tests across 139 suites**, 0 failures/errors/skips; both source boundaries, Debug APK, lint (**0 errors / 54 warnings**), AndroidTest Kotlin compilation, and `git diff --check` passed; **154 tasks**. Debug APK **67,043,950 bytes**, SHA-256 `2CF3C26995DEFD5E010B4608CD5167F23C1BA2C8DB44DB2A88760D1BF74B257E`. Full log SHA-256 `7E53B73A1822DD7E3216045FBD32B826278291B97DE0A2E5181A736D554DFA09`; focused rerun log SHA-256 `852423ED97BA25EB74F9903A7DF29CE31BE07A247A1DCAC1FCD6426396FA03E3`.
- `adb devices -l` returned no devices. Android instrumentation, packaged WebView, and end-to-end Native/Web behavior remain **NOT TESTED**. No frozen event semantics or persistence formats changed. Code commit `0fd5c897` was fast-forward pushed to `origin/feat/skin-first-separation`; Phase 1 remains open.
- **Next PHASE 1 action:** The owner has selected that Native and Web both allow dragging date-only DEADLINE events and preserve DATE precision; the Core timeline-move command is implemented and verified below. The Web-only 1900–2200 target-year limit remains the sole unresolved move-policy decision.

## Follow-up — Native transient new-event readiness uses Event Feature API (2026-11-02)

- **Root cause:** The Native transient new-event editor called `DraftForm.isPublishable()` while projecting its form to the Skin model. This shallow predicate omits full record validation such as recurrence interval bounds. The existing Core `EventFeatureApiV1.validateNewEvent()` already performs full `DraftForm.build` validation and is also used by Web preflight.
- **Change:** Native now reads `validateNewEvent(editor.handle)` for the current editor form and maps Core status, missing fields, and invalid feedback to the existing Native presentation DTO. `READY` controls the publish label; invalid forms show “保存草稿” and Core's message. Android pickers, formatting, and editor lifecycle remain Host-owned. No API was added and save still repeats authoritative Core validation.
- **Regression:** A repeating ACTIVITY with `interval = 0` passes the shallow predicate but returns `INVALID` from the Store-backed Event API. The Compose path shows “保存草稿”, not “发布”, and the projection test verifies Core's error message.
- **Focused:** `NativeDeckHostProjectionTest` and `NewEventEditorComposeTest`: **9/9 PASS**, 2 suites, 0 failures/errors/skips. JUnit XML SHA-256 `E9D766A9888B6A6BDA8680CBE53ED48499A9D0F4DFE576DB1D315A7FEAFB98F6` / `F38E614AEB55AA856F744C93D0F47CD4FCB0553A0801581BB426D9F402FC15FE`. Focused log SHA-256 `90C856FF3F8C200EED52D5947185D03FF6772EAF1AA44B040197A10DFEA95DCF`.
- **Full verification:** Forced serial offline gate **920/920 JVM / 139 XML suites / 0 failures, errors, or skips**; both source boundaries, Debug APK, lint, AndroidTest Kotlin compilation, and `git diff --check` passed (**154 tasks**). APK **67,060,334 bytes**, SHA-256 `EB610BB7F1AC3C4EC928FEDE2C2E4AADD23A9947B9225D1665D3CEFD3092F6C9`. Full log SHA-256 `BB25CB4016F84CB9186047277E97F32EE36F55546FDE50993B1BD81EA04087CB`; logs are under `.local/android-test/reports/phase1-native-new-event-readiness-20261102/`.
- **Attempt history and limits:** An initial compilation exposed a Kotlin nullable-expression typo; the next attempts caught a missing Compose assertion import and a message assertion outside the viewport. Those were corrected; the final focused and full gates passed. No ADB device was attached. Instrumentation, packaged WebView, and runtime Native/Skin parity remain **NOT TESTED**. This slice changes no storage schema, backup format, or commit semantics. Code commit `a99b058f5fae3e72be3526a4820b267aa81c4270`. Phase 1 remains open.

## Follow-up — Native/Web timeline move rules through Event Feature API (2026-11-02)

- **Owner decision:** Both Native and Web allow dragging date-only DEADLINE events and preserve DATE precision. Moving within the same local date is a no-op. Repeating events and MEMO remain unsupported.
- **Change:** Added `EventFeatureApiV1.stageTimelineMove(eventSeq, expectedRevision, targetMillis, requestId)`. The Host supplies a target already snapped by timeline geometry. Core now owns event eligibility, formal revision checking, same-placement detection, zone-aware transformation (including ACTIVITY elapsed duration and DATE-only DEADLINE), and revision-guarded `REPLACE` draft staging. The formal event is not committed or altered. Native and Web call the same API. Web keeps transport/session/projection/lock checks and current coordinate snapping; Native keeps gesture geometry and both Hosts open the existing review Deck after staging. Native displays a neutral no-change message.
- **Focused regression:** `EventFeatureApiV1Test` and `WebSkinEditBridgeTest` **55/55 PASS across two XML suites** (12 + 43), 0 failures/errors/skips. Coverage includes DATE precision and same-date no-op, elapsed duration, stale revision after an actual formal update, replay and request-ID rebinding, single change notification, unsupported repeating/date-bearing and undated MEMO cases, and Web staging without changing the formal event. Focused log SHA-256 `C01C7ED9406474A9307CAC2E4530B9E2E8467614A04F52DBEF15B90675FFDB28`. JUnit XML SHA-256: Event API `0CD08AECE60641DE0F7522EA290DF9E9E6340D61269E2115A645E4F84C820869`; Web Bridge `B17E80A46201745D3827BE64E75C86A9628172A54220A23FA9CF36562C00970C`.
- **Full verification:** Forced serial offline gate **921/921 JVM tests across 139 XML suites**, 0 failures/errors/skips. `verifySkinSourceBoundary`, `verifyNativeSkinBoundary`, Debug APK assembly, `lintDebug` (**0 errors / 54 warnings**), AndroidTest Kotlin compilation, and `git diff --check` passed; **154 tasks**. APK **67,060,334 bytes**, SHA-256 `9631B39E4D0267C975FCA017CD4BA8440D7319F2F269BA1CFC3FCC5EF6110B22`. Full gate log SHA-256 `52A9C38EA0304EDAFBE0E8F9B4BC970768BAA6D7036417D5A156C2C6DFDB6F6D`; the 139 XML reports and per-file hashes are under `.local/android-test/reports/core-timeline-move-20261102/`, manifest SHA-256 `59623E41BA6BB558B0A568869C9411C6C9E405919197B8439E0BF35D3A5E0268`.
- **Attempt history and limits:** The first Gradle invocation had no SDK path configured; `ANDROID_HOME=E:\android-sdk` fixed that without writing local config. Subsequent compile attempts identified missing `Instant`/return syntax and test-fixture timezone, source, and import assumptions; these were corrected. Final focused and full runs pass. `adb devices -l` listed no devices. Instrumentation, packaged WebView, and runtime Native/Web parity are **NOT TESTED**. No database, backup format, or formal commit semantics changed. Code commit `44a54ab820b0a1a706f039965fae5083824c7e5a` is local pending remote reconciliation. The Web-only 1900–2200 target-year rule remains unresolved; Phase 1 remains open.
