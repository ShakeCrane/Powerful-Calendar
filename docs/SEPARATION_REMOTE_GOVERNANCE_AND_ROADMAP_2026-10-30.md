# PowerCal 分离阶段：远端治理与后续交付路线图

**核对日期：2026-11-02**
**实施状态：远端 Git 分支整理已完成；功能分离仍未达到 cutover GO。**
**权威代码与交付记录：** `main` 保持核心冻结；分离开发以 `origin/feat/skin-first-separation` 为唯一集成主线。
本文件是下一轮 Codex/Agent 接手入口，不能取代带测试证据的 `SEPARATION_DELIVERY_CLOSURE_2026-10-30.md`、`SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md` 和各 Issue 报告。

## 0. Core-first / Skin-first 主阶段路线（2026-11-02）

本节落实 `goal-objective.md` 的 PHASE 0–5 顺序，**优先级高于下文保留的 SEP/M1–M4 旧执行顺序**。功能矩阵继续记录覆盖与证据，不再决定主开发方向。

**当前主阶段：PHASE 1 — ESTABLISH CORE FEATURE API。** Calendar Feature API v1 代码提交 `313562e386a14334314b7b2dc0a405e4e0465ad7` 基于新鲜远端点 `c17275e7a615f6d66d1424695f8211ce5f55d884` 普通快进推送至 `origin/feat/skin-first-separation`。此前 Event 编辑器、搜索/完成、Draft 创建、Draft/Review、AI Proposal、AI Feature、Reminder 和 Settings/Data API 切片均留在同一集成历史；逐项验证和当前差距见各独立 Issue 与 [Core Feature API v1](CORE_FEATURE_API_V1.md)。AI Feature API implementation commit 19eaf2c248cfae009904da26c6e68ee5896c448f and API recovery regression commit 98fda6ef08c53e1fd6fd1c25f3b0d9a17af3f2e3 were fast-forward pushed from remote baseline b3d4ac57db46f9cc4522ba30b4ebfecdbc46aa68。并行 delivery worktree 的未提交 AndroidTest 变更仍保留，未与本任务混写。

**当前切片：Calendar、Event、Memo、Draft/Review、AI Conversation/Proposal/Feature/Evidence Intake/Proposal and Calendar Query Tool Dispatch、Reminder、Import/Export、Settings/Data Feature API v1。** 纯 Kotlin `:core-api` 提供版本化 DTO/接口；Native 与 Web Host 共用同一 `PowerCalCoreFeatureApiV1`。Calendar API 对 17 天时间轴和 42 天月视图窗口过滤 Event 快照，复用既有重复展开、日期精度和区间重叠语义；Native 月份导航每月查询，Web 同步刷新按实际活动窗口查询。Memo API 对 Event 快照执行统一待办投影，并为 Web 当前 occurrence 可见性校验提供 API-owned 重读；Native、Web 和编译期 Memo renderer 收到稳定 `MemoEntryV1` DTO。Memo 保持 365 天回看、未来七天 horizon、既有重复展开上限和排序；Memo/Calendar 查询仍接收完整正式事件快照，Store 层分页尚未实现。AI 提案经 detached DTO 暴露，正式发布要求 Host 签发且绑定请求指纹的一次性显式批准，并按条目返回收据。普通文本对话现经 `AiFeatureApiV1.chat`；Core 还拥有对话 query/append/clear/rewind 与 revision fencing，Host 通过既有持久化端口保存 transcript。Core 现以版本化命令分析已审阅的文件/OCR/未解决 ICS 文本和已获同意的图片字节；URI、picker、限量读取、本地 OCR、用户同意与凭据仍归 Host。Reminder API 查询权限、正式事件 revision、持久 schedule intent 与最新执行标记；恢复委托现有 `ReminderScheduler`，不复制调度或时序规则。Import/Export API 将本地 ICS 解析与草稿审核、结构化日历导入事务、ICS 活动导出、备份 readback 验证及空库恢复接入同一领域入口；文件选择、系统权限、SAF 和恢复后的提醒重建仍归 Host。无数据库 schema/备份格式或冻结 Core 语义变化。AI batch、结构化提案与普通聊天调用已通过 AI Feature API；AI proposal、聊天与证据分析外层收据仍是进程内。provider credentials、URI/picker、受限读取、本地 OCR 与 consent 仍归 Host；已审阅证据解释通过 Core API，聊天工具 dispatch 限定为单轮、单调用的 review-only `calendar.propose.v1` 与只读 `calendar.query.v1`；查询沿用保存的共享授权、Core revision fence 和 Calendar API，最多返回 100 条 detached DTO。web.search、任意工具和多步编排仍未实现。详情见 [`CORE_FEATURE_API_V1.md`](CORE_FEATURE_API_V1.md)、[AI Conversation API Issue](issues/PC-ISSUE-CORE-AI-CONVERSATION-API-V1.md)、[Memo API Issue](issues/PC-ISSUE-CORE-MEMO-API-V1.md)、[AI Feature API Issue](issues/PC-ISSUE-CORE-AI-FEATURE-API-V1.md)、[AI Evidence Intake API Issue](issues/PC-ISSUE-CORE-AI-EVIDENCE-INTAKE-API-V1.md)、[Calendar API Issue](issues/PC-ISSUE-CORE-CALENDAR-API-V1.md)、[Import/Export API Issue](issues/PC-ISSUE-CORE-IMPORT-EXPORT-API-V1.md)、[Reminder API Issue](issues/PC-ISSUE-CORE-REMINDER-API-V1.md)、[AI Proposal Issue](issues/PC-ISSUE-CORE-AI-PROPOSAL-API-V1.md)、[AI Calendar Query Tool Issue](issues/PC-ISSUE-CORE-AI-CALENDAR-QUERY-TOOL-V1.md)、[Draft/Review Issue](issues/PC-ISSUE-CORE-DRAFT-REVIEW-API-V1.md)、[Event search Issue](issues/PC-ISSUE-CORE-EVENT-SEARCH-API-V1.md)、[Occurrence completion Issue](issues/PC-ISSUE-CORE-OCCURRENCE-COMPLETION-API-V1.md)、[Draft creation Issue](issues/PC-ISSUE-CORE-DRAFT-CREATION-API-V1.md) 和 [AndroidTest harness migration Issue](issues/PC-ISSUE-SEPARATION-ANDROIDTEST-FEATURE-API-MIGRATION.md)。

Event API 的 `queryEvent(eventSeq)` 返回当前 detached `EventRecordV1`；Web event-open 与 AI proposal target capture 使用该查询读取当前 revision，editor API 随后重验 revision 和 occurrence。详见 [Event current query Issue](issues/PC-ISSUE-CORE-EVENT-QUERY-API-V1.md) 及下方独立验收证据。

本切片聚焦回归 **64/64 PASS**；强制全量 JVM **803/803，128 XML reports，0 failure/error/skip**；AndroidTest Kotlin 编译、`verifySkinSourceBoundary`、`verifyNativeSkinBoundary`、Debug APK assemble 和 `lintDebug` 均 PASS，lint **0 errors / 54 warnings**。APK **67,552,075 bytes**，SHA-256 `03108E62D7BE9473C65A887D87BB906EDEAAD7AFFA46B18D0415BCD864161C06`。`git diff --check` PASS。Android instrumentation/AVD 未测试，本次未触碰设备。

文本草稿 API 聚焦回归 **13/13 PASS**；强制全量 JVM **806/806，128 XML reports，0 failure/error/skip**；AndroidTest Kotlin 编译、两项 source boundary、Debug APK assemble 和 `lintDebug` 均 PASS，lint **0 errors / 54 warnings**。APK **67,552,443 bytes**，SHA-256 `C7C52F7EB0079968D4A0E8548BD742CD931181CFFA94D23BD41871021D8A84F3`。Android instrumentation/AVD 未测试；详见 [Draft Creation Issue](issues/PC-ISSUE-CORE-DRAFT-CREATION-API-V1.md)。

AI Proposal API v1 聚焦 API 与 Store 收据测试 **24/24 PASS**，单独复测 Web Host 确认 UI **1/1 PASS**。最终强制串行全量门禁 `:app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain` **BUILD SUCCESSFUL**：JVM **824/824**，129 XML reports，0 failure/error/skip；两项 source boundary、Debug APK、lint（0 errors / 54 warnings）及 AndroidTest Kotlin 编译均 PASS。APK **66,749,038 bytes**，SHA-256 `70D6D0E70D955F82416DE352CC9E1DAA5B9E9404C7C794DF5AEF3685AA79BF9F`。首次并行强制全量运行有 1 项 Web Host UI 测试在异步确认节点出现前立即断言而失败；孤立复测通过，测试改为等待该节点后，最终完整串行门禁通过。Android instrumentation/设备行为 **NOT TESTED**，本轮未触碰设备。日志及 SHA-256 见 [AI Proposal Issue](issues/PC-ISSUE-CORE-AI-PROPOSAL-API-V1.md) 与忽略目录 `.local/android-test/reports/core-ai-proposal-api-20261101/`。

Reminder API v1 聚焦接口/Web recovery/diagnostics 测试 **10/10 PASS**；九个相关 API、receiver、Host 回归类在最终结果中 **49/49 PASS**。强制串行全量门禁 **828/828 JVM，130 XML reports，0 failure/error/skip**；两项 source boundary、Debug APK、lint（0 errors / 54 warnings）和 AndroidTest Kotlin 编译均 PASS。APK **66,749,038 bytes**，SHA-256 `291A5E275D625ABEBB602DCABE80E19A91258E16FA572F4B5D2641B0E8A2A999`。最终门禁日志 SHA-256 `5C3552D8DC98C6599737835B26DADC3BA07AA261F2F876C1E081A225AF02E7B3`，聚焦日志 `AD4FBA7E4822336AA70ACB62B0BBFF8DE51590C5E34BA58B31D84FB530433348`。Android instrument/OS 实际闹钟投递/设备 OEM **NOT TESTED**。提交与完整边界见 [Reminder API Issue](issues/PC-ISSUE-CORE-REMINDER-API-V1.md)。
Calendar API v1 聚焦 Calendar/Event/Native 时间轴与月视图及 Core API adapter **16/16 PASS**；强制串行全量门禁 **831/831 JVM，131 XML reports，0 failure/error/skip**。两项 source boundary、Debug APK、lint（0 errors / 54 warnings）及 AndroidTest Kotlin 编译均 PASS。APK **66,765,422 bytes**，SHA-256 `8AA317888327AD461AAB648A4860C833235A7C10E9BA48F9D7BC8897A3B9014E`。门禁日志 SHA-256 `D7C41F4B4D3CA6436B299E46B537A231F3986658C63FB314D08C2C6F5C1B1C1A`，最终聚焦日志 `D3F2B1E0CFBFB7A9DC5C78C56673E96E9D4201602111B267A1B752C56BB6240D`。首次聚焦发现日期范围 Memo 漏选和 Windows Robolectric 临时路径过长，修复后的重跑通过。Android instrumentation、打包 WebView 和设备/OEM 行为 **NOT TESTED**。完整范围见 [Calendar API Issue](issues/PC-ISSUE-CORE-CALENDAR-API-V1.md)。

Import/Export API v1 聚焦 API 与 Native/Clear Host 回归 **25/25 PASS**；强制串行全量门禁 **834/834 JVM，132 XML reports，0 failure/error/skip**。两项 source boundary、Debug APK、lint（0 errors / 54 warnings）、AndroidTest Kotlin 编译及 `git diff --check` 均 PASS。APK **66,781,806 bytes**，SHA-256 `6CDD38126D93331D566824F2D03A9293BA2C9497FD21A46F5FEB4FA9B4DF4AD0`。门禁日志 SHA-256 `0E2EE3C8E65C85DB810CA2576173BB3B17762E989D80055E6848F24CA6D6B5D5`，聚焦日志 `23C4AC3D74CC97AF4B6FE7DE7570EE620527F5B79158B91ACEDC2CEC40C3F096`。Android instrumentation 未执行；生产 WebView-to-Host、真实 SAF/provider、物理设备/OEM 行为 **NOT TESTED**。完整边界见 [Import/Export API Issue](issues/PC-ISSUE-CORE-IMPORT-EXPORT-API-V1.md)。代码提交 `4803a75f99f1e1737225d81dbbdf8f3584b890b4` 基于 `af541cfdd7c56a2ff68c8a66fbec410b07ca0505`。

**PHASE 1 尚未退出。** 正式事件搜索与 Memo 完成命令已接入 Event API；Memo visibility query 和 runtime Memo stable DTO 现已由 Memo Feature API v1 覆盖。AI reviewed proposal 的 staging/publishing、Host reminder query/recovery、bounded Calendar window 查询，以及 ICS/structured Calendar import/export 和备份恢复已有独立 API 切片。AI batch、结构化提案、普通文本聊天及 revision-fenced transcript state 现已通过 AI Feature API；已审阅文本/OCR/ICS 与获用户同意的图片分析现经 AiFeatureApiV1；picker、URI 文件读取、本地 OCR、consent 与 provider 凭据仍属 Host；目前有 Core 校验的 review-only `calendar.propose.v1` 和只读 `calendar.query.v1` 单轮工具调用；网页搜索、任意工具和多步编排仍未实现。剩余已授权 Host/Core 读写和 Settings/Data 其他路径仍未全部经统一领域 API/变更源。Calendar 与 Memo query 仍以完整 Host 快照为输入，没有 Store 层分页。`MonthVisualContext` 与 `TimelineSkinContext` 均已向 Skin 暴露稳定 `EventRecordV1` 数据及身份回调；当前检查到的编译期 renderer contexts 不再直接向 Skin 暴露 Core entity，Native fallback 状态仍由 Host 私有持有。当前切片不代表全功能 API、Native/Skin parity、runtime separation 或 cutover PASS；Memo/真实设备/SAF 验收也未因此通过。

| 阶段 | 当前状态 | 退出条件摘要 |
| --- | --- | --- |
| PHASE 0 — Current State Reconciliation | 本轮已核对 HEAD、脏工作区、远端 freshness、模块边界，以及 Native/Web 事件调用路径；全功能域架构映射和冻结后历史变更分类沿用现有验收资料，仍需按领域补齐，不做无边界历史重查 | 架构归属、冻结语义风险及剩余 API 缺口均有可核查清单 |
| PHASE 1 — Establish Core Feature API | **进行中：Calendar window v1 + Event v1 + Memo v1 + Draft/Review v1 + AI Conversation/Proposal/Feature/Evidence Intake/Proposal and Calendar Query Tool v1 + Reminder v1 + Import/Export v1 + Settings/Data v1 vertical slices** | Core API 有真实实现；领域契约齐全；Native/Web 使用同一服务；Skin 不依赖 Core 内部实现；Host 不承担第二套业务规则；冻结语义保持 |
| PHASE 2 — Complete Runtime Separation | 未开始 | Clear、第三方 `.pcskin`、Native fallback 对所有冻结功能使用统一数据、授权、错误与恢复语义；替换 Skin 不改 Core |
| PHASE 3 — Complete Skin Studio | 未开始 | Clear 与结构不同的第三方 Skin 可任意编辑/重构、预览、构建、导出、重导入并在 Android 运行 |
| PHASE 4 — AI Autonomous Skin Authoring | 未开始 | AI 通过 Studio 正式工具端到端完成项目分析、修改、素材、构建、校验、预览、修复及交付 |
| PHASE 5 — Final Delivery | 未开始 | 同一交付基线集成 Core API/Runtime、多 Skin、Native fallback、Studio、AI、文档、测试、APK 与 `.pcskin`；实体/OEM 验收清单独立交付 |

**最近关闭 — Core AI Conversation State API v1（2026-11-02）：** `AiFeatureApiV1` 现在拥有 transcript query/append/clear/rewind 与 revision fencing；普通 chat 从 canonical snapshot 读取，并用 revision + user message ID 绑定本轮。Android Host adapter 复用原 JSON/AtomicFile 格式。Focused **42/42 PASS**；full gate **849/849 JVM、135 XML suites**，边界、APK、lint 和 AndroidTest Kotlin 编译通过。代码提交 `f3c2bd6c5fb889f598e52070a29fbe5a3308c3a7` 已推送。详细哈希、限制及 NOT TESTED 范围见 [AI Conversation State API Issue](issues/PC-ISSUE-CORE-AI-CONVERSATION-STATE-API-V1.md)。这不表示 PHASE 1 退出，也不声称设备或 WebView parity。

**此前关闭 — Core AI Conversation API v1（2026-11-02）：** 普通文本聊天接入 `AiFeatureApiV1.chat`，显式任务复用既有提案生成，Skin 上下文改用版本化对话/审核/批次 DTO。代码提交 `3f64cfa`；测试与设备限制见 [AI Conversation API Issue](issues/PC-ISSUE-CORE-AI-CONVERSATION-API-V1.md)。

**此前关闭：** `DeckVisualContext` 已改为提供 `DraftReviewSnapshotV1`、`EventFormV1`、`EventKindV1`；Host 继续持有 Core 转换、可提交性校验和审批回调，Native fallback 与冲突/恢复保护保留。Draft Deck DTO 回归、`GlassComposeTest`、846 项 JVM 与完整离线构建证据见 [Draft Deck projection Issue](issues/PC-ISSUE-CORE-DRAFT-REVIEW-PROJECTION-API-V1.md)。其他 runtime projection 仍有 Core 类型引用；设备与 Native/Clear parity 未测试。

**最近关闭 — Core AI calendar.query.v1 tool dispatch（2026-11-02）：** Core 现以保存的日历共享授权和 conversation revision fence 执行有界只读查询，复用 Calendar Feature API，并在收据内返回 detached 结果和 Core 生成的摘要；不调用第二轮模型，也不写入日历。聚焦 **48/48 PASS**，完整离线门禁 **873/873 JVM / 137 XML suites**；两项 source boundary、Debug APK、lint（0 errors / 53 warnings）、AndroidTest Kotlin 编译和 `git diff --check` 均 PASS。APK **66,929,262 bytes**，SHA-256 `38E233AD73D0E31C9F1D34B7003255900034134A84A63A1E5144FAE5A81381E7`。代码提交 `974d404c4ffe3d9ea3c090c0b059e58bca94bb54` 已快进推送；详见 [AI Calendar Query Tool Issue](issues/PC-ISSUE-CORE-AI-CALENDAR-QUERY-TOOL-V1.md)。设备、WebView 和真实供应商行为 **NOT TESTED**。PHASE 1 仍未退出。

**唯一 NEXT_ACTION：** 为 Calendar/Memo 读取实现 Store-backed 分页查询，消除当前完整 `EventQuerySnapshotV1` 物化，同时保持 feature revision、事件顺序与重复/窗口语义；Native/Web 继续共享同一 Core API。

**此前关闭 — Core AI proposal tool dispatch v1（2026-11-02）：** Core 将唯一 `calendar.propose.v1` Function Calling 调用路由到现有 `AiFeatureApiV1.generateProposal`，只返回 detached review DTO，不写入草稿或正式事件。聚焦回归 **36/36 PASS**；完整离线门禁 **861/861 JVM / 135 XML suites**，两项 source boundary、Debug APK、lint（0 errors / 53 warnings）与 AndroidTest Kotlin 编译通过。代码提交 `a3d3c94973a2698264acf53e26ff2275a81b356b`；详细证据、首次 SQLite 瞬时锁及 NOT TESTED 范围见 [AI Proposal Tool Dispatch Issue](issues/PC-ISSUE-CORE-AI-PROPOSAL-TOOL-DISPATCH-V1.md)。PHASE 1 仍未退出；有界 `calendar.query.v1` 已实现，任意工具、`web.search` 和多步编排仍未实现；设备和真实供应商行为未测试。

**最近关闭 — TimelineSkinContext stable DTO boundary (2026-11-02):** Timeline Skin now receives `EventRecordV1` records, a Native camera DTO, and identity/revision callbacks; Native time geometry and recurrence routing remain behind the Host adapter. Focused **21/21** across four XML suites; full forced offline gate **864/864 JVM / 137 XML suites**, zero failures/errors/skips; both source boundaries, Debug APK, lint (**0 errors / 53 warnings**) and AndroidTest Kotlin compilation passed. APK **66,896,494 bytes**, SHA-256 `ED8A5C5046E3F8DFAFE7CAE69328020DB4A58DDD2A7F1D22F049A980F604BA0B`. Code commit `5ab465269dd7a62d3d3bd0ca79e1cfc5b74e2be1`; details: [TimelineSkinContext DTO Issue](issues/PC-ISSUE-CORE-TIMELINE-SKIN-CONTEXT-DTO-V1.md). Android instrumentation, packaged WebView/device behavior and Native/Skin parity are **NOT TESTED**.

**此前关闭 — MonthVisualContext stable DTO boundary（2026-11-02）：** 编译期 Month Skin 的 `records`、`recordsForMonth` 现暴露 `EventRecordV1`，`onOpen` 仅携带 eventSeq/revision/occurrence key；Host 私有 Native 投影继续保留 recurrence 展开、时间几何和原始 occurrence key。聚焦 `MonthVisualContextContractTest`、`NativeMonthProjectionTest`、`GlassComposeTest`、`DirectEventEditorComposeTest` **33/33 PASS**；完整离线门禁 **863/863 JVM / 136 XML suites**，两项 source boundary、Debug APK、lint（0 errors / 53 warnings）及 AndroidTest Kotlin 编译通过。APK **66,912,878 bytes**，SHA-256 `3CF8D9B979FBFF4012C4E7006D8BAF98FAC84B84CD0E33EDE841EAF6404B90D5`。代码提交 `50739b9e0065753c36949b17bc2f9994e7376577`、`29de5e937fd17af4eaaedacc57e7cc3e85a3e8dc`。ADB 无设备且无 AVD；instrumentation、WebView 和 Native/Skin parity **NOT TESTED**。详见 [MonthVisualContext DTO Issue](issues/PC-ISSUE-CORE-MONTH-VISUAL-CONTEXT-DTO-V1.md)。

Memo Feature API v1 聚焦递归窗口、统一待办投影、完成与当前可见性测试 **3/3 PASS**；强制串行全量门禁 **844/844 JVM，134 XML reports，0 failure/error/skip**。两项 source boundary、Debug APK、lint（0 errors / 53 warnings）、AndroidTest Kotlin 编译及 `git diff --check` 均 PASS。APK **66,847,342 bytes**，SHA-256 `239B74AACCB179B048572A5990D15F0351687DD8991BF6384182177BACE92724`。聚焦/全量日志 SHA-256 `A130824BF53051F7144B598C593CA4D3AC3440F40E68B7F017739E217CA1C4A9` / `1C2F57B2DC50310C4631B16300E3E8566D2827EE57DEC05F1297CB0F6E4E496F`，位于 `.local/android-test/reports/core-memo-feature-api-20261101/`。ADB 无在线设备且没有配置 AVD；instrumentation、Native/Web runtime、打包 WebView 和设备/OEM 行为 **NOT TESTED**。代码提交 `62a6d12d270d2fa4d3b2f1a5e458b4c26487d4f6` 基于 `b88aff8787fe71d7fd5a491fe135cb19e82d63da`。详见 [Memo API Issue](issues/PC-ISSUE-CORE-MEMO-API-V1.md)。
Memo Feature API v1 聚焦递归窗口、统一待办投影、完成与当前可见性测试 **3/3 PASS**；强制串行全量门禁 **844/844 JVM，134 XML reports，0 failure/error/skip**。两项 source boundary、Debug APK、lint（0 errors / 53 warnings）、AndroidTest Kotlin 编译及 `git diff --check` 均 PASS。APK **66,847,342 bytes**，SHA-256 `239B74AACCB179B048572A5990D15F0351687DD8991BF6384182177BACE92724`。聚焦/全量日志 SHA-256 `A130824BF53051F7144B598C593CA4D3AC3440F40E68B7F017739E217CA1C4A9` / `1C2F57B2DC50310C4631B16300E3E8566D2827EE57DEC05F1297CB0F6E4E496F`，位于 `.local/android-test/reports/core-memo-feature-api-20261101/`。ADB 无在线设备且没有配置 AVD；instrumentation、Native/Web runtime、打包 WebView 和设备/OEM 行为 **NOT TESTED**。代码提交 `62a6d12d270d2fa4d3b2f1a5e458b4c26487d4f6` 基于 `b88aff8787fe71d7fd5a491fe135cb19e82d63da`。详见 [Memo API Issue](issues/PC-ISSUE-CORE-MEMO-API-V1.md)。

Event current-record query v1 为 EventFeature API 增加按 identity 读取当前 detached DTO 的查询；Web event-open 和 AI proposal target capture 通过 API 检查当前 revision，后续 editor API 再验证 occurrence 和 writer state。聚焦 Event API/Web AI/Web event-open tests **28/28 PASS**；强制串行全量 **845/845 JVM，134 XML reports，0 failure/error/skip**。两项 source boundary、APK、lint（0 errors / 53 warnings）、AndroidTest Kotlin 编译及 `git diff --check` 均 PASS。APK SHA-256 `9C556FA6611FBCD5BF7B1C7CCFD139CDCC17EB722027B98FF4417303D2336A5A`。聚焦/全量日志 SHA-256 `C2B81E4DB5A8F3B7353C4CD52CCE98B4F1AEE962279B189C75EC32D04C707B6F` / `73D9260DA774AA4E4FF5A88A09065E91836E801BB15A1DDB29F7FE6959218495`，位于 `.local/android-test/reports/core-event-query-api-20261101/`。代码提交 `e7c2a6bd8a1a1db2a5c5e11b87289b2da229bd31` 基于 `ceec9f16513126008036b62008a47f60c1301e2a`。设备无 ADB/AVD，instrumentation 和 packaged WebView/device behavior **NOT TESTED**；详情见 [Event current query Issue](issues/PC-ISSUE-CORE-EVENT-QUERY-API-V1.md)。

## 1. 当前版本基准及实际证据

| 用途 | 指向 | 解释 |
| --- | --- | --- |
| 原生业务核心 | `b1f7629f0c52812e1887443872f43aa6c49908c4` | 2026-10-27 核心冻结的应用业务源码 |
| Core 冻结标签 | `core-freeze-2026-10-27` | 指向核心冻结签核提交 `af229d9` |
| 交付构建源 | `6cd755621cc1c599dd72871d4e1eb77442998d4a` | 727 JVM / 61 Studio Python 的阶段候选构建、APK、皮肤和源码 ZIP 对应此 SHA |
| 阶段标记 | `skin-separation-stage-2026-10-30` | 指向 `3d099d37d44fe82e72435517f0325d23cc7239e7`，后两笔只有阶段交付报告修改；非 Release |
| 分离开发主线 | `origin/feat/skin-first-separation` | 集成和后续新功能唯一目标分支；本轮 Draft/Review API 代码提交为 `a12e107b868243e0563d5ca904edaf38c777f070`，基于 `a3b51394bc0c546f1d7d4e02a2bd8cef9a8d22e9` 普通快进推送 |

阶段构建在 `6cd7556` 报告 **88/88 Gradle Tasks、727/727 JVM（100 suites）、Lint 0 Error、Skin Studio Python 61/61、Clear APK 包同源、JS 4/4 语法检查、APK v2 签名验证**；这些是历史证据，本次远端整理**没有重新运行测试或制作新 APK**，不得写成新 HEAD 再次完整跑过。

`main` **不是**分离集成分支，仍保留完整 Native 回退。默认皮肤切换和完全移除原生 UI 均未经授权。分离目标依然是**完整功能等价 + Skin Studio 可以无损编辑/重构皮肤任一部分 + AI 根据规范自动化制作皮肤**；当前正式 cutover = HOLD。

## 2. 远端分支整理结果

整理前远端共 9 条分支。对每个拟删除分支先核验远端 SHA，再以 Git `merge-base --is-ancestor` 确认已包含在分离主线中；没有 force push、重置、删除本地分支或 worktree。

**保留的 4 条远端分支：**

| 远端分支 | 整理时 SHA | 治理结论 |
| --- | --- | --- |
| `main` | `af229d9` | 冻结核心；不接分离试验提交 |
| `feat/skin-first-separation` | `3d099d3` | 唯一分离集成主线；后续应逐里程碑普通快进推送 |
| `codex/separation-legacy-import` | `d43bac3` | **未合并，保留评审**；不能直接删/强并 |
| `codex/separation-skin-settings` | `c816906` | **未合并，保留评审**；其 worktree 还有未提交 AndroidTest |

**已删除的 5 条远端分支（均为已合并或完全重复）：**
`codex/separation-s0-timeline-drag`（与交付标签同 SHA）、
`codex/separation-s0-inventory`、
`codex/pc-d05-production-qa`、
`feat/r1-import-ai-compat`、
`integration/rc2-review-20261020`。

删除的仅是远端**分支引用**。历史提交仍由当前分离主线祖先链保存，本地分支与各 worktree、未提交试验文件没有删除。**不要在旧 `codex/separation-s0-timeline-drag` 工作树重新推送该已退休的远端分支**，需要进一步开发请另建清晰命名分支或从当前集成主线开新隔离工作树。

两条未合并分叉各约有 85 / 77 个相对当前交付主线独有的历史提交，且跨越旧共同基线；**这个计数不等于 85 / 77 个独立功能**。它们与当前 Host、PowerCalApp、Skin、Studio 和文档存在重叠，不能整支合并来“解决分支数量”。

**待审分叉流程（先于删除这两条分叉）：**
1. 逐分支列出独有功能、文档、测试及它们对应的 SEP 项，识别当前主线已有的等价实现。
2. 将候选变更分类：已有且更完整 / 独立有效 / 与冻结核心冲突 / 过时或已替代。
3. 对独立有效项单独开 Issue，在临时集成分支上做最小 cherry-pick 或重新实现，并同时补 Runtime、Studio、文档及回归。
4. 在同一 SHA 上通过受影响定向门禁与构建，正常合入 `feat/skin-first-separation`；明确证据后才允许退休分叉，**不强合**。

**本地治理限制：** 历史记录共有 23 个 worktree，多个含未提交资料或诊断测试。此次只整理远端，不清空本地目录；任何未来本地减量必须按每个 worktree 的未跟踪文件、修改、日志、实验数据、活动进程逐项核对，不得用 `git clean` / `reset --hard` / `worktree prune` 盲清。

## 3. SEP-01～SEP-16 的下一步真实工作

以下来自阶段交付报告，`TESTED` 仅代表**列出的局部路径**，不是整个 SEP 的 Native/Skin 等价 PASS：

| SEP | 本阶段状态 | 下一轮验收目标 |
| --- | --- | --- |
| SEP-01 时间轴 | **PARTIAL / HOLD**（提交 `10da1cc` 的 API 35 生产 WebView 触摸→Host→Store 拖动闭环 1/1；缩放后复位触摸 2/2，立即复位重复 3/3。完整 Native/Clear 配对、TalkBack 与实体/OEM 未签核） | 完成相同事件/相机/时区的 Native/Skin 配对回放；保持时间、刻度、双翼和贪心标题；补设备矩阵 |
| SEP-02 月历 | 部分路径 TESTED | 选日、新建、打开重复实例、返回与状态恢复双版本回放 |
| SEP-03 Memo/Todo | 部分路径 TESTED | 翻页、完成状态、恢复、重复和极端内容对照 |
| SEP-04 新建 | 主要路径 TESTED | 四类型、不同入口、默认时间、无 Diff、重建和 DST 全流程双版本回放 |
| SEP-05 正式编辑 | **CODE COMPLETE**; Native formal editor shell in `:native-skin`; Host Compose/Robolectric/full JVM tested; device not signed off | Android 真实设备编辑、类型转换、冲突与恢复 |
| SEP-06 重复实例 | 部分范围 TESTED | 单次/此后/整个系列，occurrence key、身份、提醒和重启后对照 |
| SEP-07 正式删除 | **PARTIAL / HOLD**（API 35 生产 WebView、Host Compose 与 Store 的重复系列取消/批准闭环 1/1；该测试用 DOM 点击与 Compose 语义触发，不是触摸验收） | 补真实触摸/TalkBack、并发版本冲突、恢复工作流与 Native/Skin 配对；不能据此签核整项 |
| SEP-08 草稿卡组 | Native Compose renderer **CODE COMPLETE** in `:native-skin`; focused Host/Compose cases TESTED; overall SEP remains PARTIAL | 批次导航、patch、flush、提交/拒绝、撤销、IME 与异常恢复的完整双路径回放；AndroidTest 设备执行 |
| SEP-09 AI 聊天 | 部分路径 TESTED；Native 图片授权、OCR 核对与提取文件/ICS 预检视图已 CODE COMPLETE 于 `:native-skin`，focused Compose/Host 附件及全量 JVM TESTED；设备 NOT TESTED | 真正 Picker/OCR 权限与设备闭环、记录持久化、错误/重试及可用免费 Provider |
| SEP-10 AI 提案 | 部分路径 TESTED；Native 提案审核卡、批量授权、卡住批次强制解除审核及锁定期间只读事件详情均 CODE COMPLETE 于 `:native-skin`；本路径 focused Compose 2/2，当前全量 JVM 774/774 TESTED；Android Emulator NOT TESTED（无 AVD，SDK emulator package metadata 缺失） | 四按钮、完整 CREATE/REPLACE/DELETE、多轮、不完整提案及跨进程恢复；正式发布只经 Host 授权 |
| SEP-11 设置/权限 | **INCOMPLETE**；Native 个人资料、AI 配置卡片（API key 输入仍为 Host slot）、首次系统日历导入提示、旧数据库迁移/旧备份确认展示已 CODE COMPLETE 于 `:native-skin`；AI settings/profile/protected-slot 定向 7/7，当前全量 JVM 778/778 TESTED；AI 存储与密钥仍由 Host 保管；Android Emulator NOT TESTED | 设置页面完整皮肤化，敏感能力经受信 Host，覆盖权限、ICS、备份、提醒诊断与设备验收 |
| SEP-12 导航/Picker | **INCOMPLETE**; Native root/notice/modal/system Picker renderers CODE COMPLETE in `:native-skin`; Picker Robolectric + full JVM tested | 页面等价、多窗口/IME/返回导航和 Android 设备 Picker 验收 |
| SEP-13 包格式 | 有限路径 TESTED；Native 设置页皮肤启用/删除确认已 CODE COMPLETE 于 `:native-skin`，focused Compose、Native 设置与导出路径 TESTED；设备 NOT TESTED | 官方与第三方包同权限，同源构建、导入/导出/升级及危险包检查 |
| SEP-14 切换/故障恢复 | INCOMPLETE；API 35 Clear WebView renderer 终止后切回 Native 已 1/1 TESTED（89e6cd3）；Clear WebView 的干净编辑器 Activity 重建零写入、脏标题重建后恢复唯一待处理草稿 1/1 TESTED（测试提交 5d6b421；设备证据见 .local/android-test/reports/sep14-activity-recreation-20261031/）；旧数据库迁移/旧格式备份确认路径有 JVM 证据 | 冷进程死亡/重启、renderer 退出时脏编辑恢复、损坏/脚本失败、升级失败、设备重启数据保持及实体/OEM 验收 |
| SEP-15 Skin Studio | **INCOMPLETE**；浏览器现可显式编辑三项 Host 能力请求，源/资源回归 62/62 与 Chrome 导出重导入通过 | 完成全定制内容的无损编辑/重构、可视结构能力、绑定及 AI 工作流；跨皮肤与 Android 行为仍需验证 |
| SEP-16 可信写入 | **INCOMPLETE**；API 35 Clear WebView AI proposal 当前 3/3 TESTED（local stub）：CREATE Host 批准/取消、REPLACE/DELETE Host 批准、STAGE/REJECT/SUPPLEMENT；普通编辑/新建/重复事件路径有既有 AVD 证据 | 四类事件与所有增删改/草稿操作的完整 Host 拒绝/授权对照、版本冲突、AI 多轮/部分失败恢复、Native/Skin 全量回放及设备生命周期 |

当前 **没有任何一项可以仅凭阶段报告认定为完整 100% Native/Skin 等价签核**。

## 4. 旧 SEP 验收推进里程碑（保留作风险索引，不是当前阶段顺序）

以下 M1–M4 是旧 SEP-01～SEP-16 执行框架的验收风险索引。它们继续帮助 Phase 2/5 回收真实设备证据；当前主开发顺序由第 0 节 PHASE 0–5 决定。

### M1 — 先关真正阻碍日常使用的交互和授权缺口

优先项：**SEP-01 触屏拖动、SEP-07 WebView 正式删除、SEP-16 可信写入的空洞**；同步保护 SEP-04/05/06/08。

- 修复前构造可以重现的真实 Android WebView 输入轨迹，测试 Host 收到对应动作及保存/取消结果，而不只是判断屏幕能点击。
- 测量冻结 Native 与 Skin 的操作结果差异，保存具体事件身份/版本/提醒调度证据。
- 每个新增交互都同步支持 Skin Studio 定位、源码修改、预览、导出导入、AI CLI 查找与可执行说明。
- **退出条件：** SEP-01 和 SEP-07 关键失败场景有目标设备/AVD 可追溯 PASS；SEP-16 对这些正式写入操作的拒绝与授权两条路径 PASS；其余失败留明确证据。

### M2 — 关系统页面与灾难恢复的阻塞

优先项：**SEP-11、SEP-12、SEP-14**；同步补 SEP-09/10 外部入口。

- Settings、权限、文件、备份、ICS、AI 设置和皮肤管理全部提供真正可达的皮肤 UI；危险确认仍由 Host 提供受信界面。
- 恢复清单必须覆盖损坏皮肤、错误脚本、WebView 崩溃、升级失败、正在编辑/存在草稿、进程死亡与设备重启。
- **退出条件：** 用户日常操作不依赖大块旧 Native 页面；独立恢复入口在故障注入后可触达、用户业务数据不变；公开的能力声明与 Runtime/Studio 相符。

### M3 — Studio 从源码面板走向完整皮肤 IDE，文档与 Agent 自动化同步

优先项：**SEP-15**，并回归 SEP-13。

- 完整源代码/资源编辑必须覆盖任意可定制内容；可视化与结构编辑不必强制理解所有用户代码，但必须无损保留未知节点与扩展。
- 支持页面/组件树的创建、移动、删除、重组与事件绑定；任意源文件、图片、脚本、CSS、Manifest/权限均有受控修改和无损导出回入。
- 人与 Agent 操作同一份 canonical project；CLI 提供 inspect/read/edit/build/validate、稳定 JSON 回执、示例和错误码。
- 引入文档与 API/Schema/CLI/示例的自动交叉校验，确保新 Agent 阅读后可独立改造皮肤、运行测试和打包。
- **退出条件：** 官方 Clear 与一个结构完全不同的自定义皮肤都能完整导入、任意重构、预览、导出、重新导入及设备运行；业务权限未扩大，模拟 Host 不算真实设备。

### M4 — 全部 SEP 的冻结版双路径验证和独立 Cutover 决策

- 在完全隔离的测试数据库里，使用**相同输入、相同时钟/时区、相同 Provider 响应**分别操作冻结 Native 和新的 Skin。
- 比较事件、版本、occurrence、草稿、提醒、AI 审核、导入/导出、失败和重启结果；保留用户可触达功能入口证据。
- 同步测试 Android WebView、不同屏幕/字号、IME、TalkBack、减少动画、离线和 OEM；无设备就保留 NOT VERIFIED，绝不冒充 PASS。
- **退出条件：** 必需功能差异全关闭，Skin Runtime 与 Studio 均在同一 SHA 上构建验证，故障恢复和回滚证明成立，Owner 独立批准切换。否则继续 HOLD，不删除完整 Native fallback。

## 5. Codex 以后如何工作

- 进入 `feat/skin-first-separation` 的**最新远端 HEAD**，而不是停在旧的本地 `cc31026`，更不能在 dirty 的原工作树上直接 reset/pull 覆盖。
- 每次只领取一个有明确验收出口的 M1/M2/M3/M4 子项，使用独立 worktree 与任务分支；测试、Studio 支持、CLI/文档更新**双轨随提交交付**。
- 单项完成后正常合并/快进到唯一主线；提交前检查远端是否有其他 Agent 推进，冲突先对账。
- 用 **CODE COMPLETE / TESTED (部分) / BLOCKED / INCOMPLETE / FULL PARITY PASS** 明确区分状态。不要因为 727/727 JVM、61/61 Studio 或大量提交就宣称完整分离。
- 任务结束必须报告最新 SHA、已合并范围、失败场景、测试命令/日志与下一项；不可无限扩展新问题，不以每次发现新优化为由无限延迟交付。
- 继续保护 `main` 的验收 CSV、`design/`、未提交 D05/Studio/Timeline 拖动测试和用户数据库/AVD；不清理历史本地 worktree，除非逐项确认归属和可恢复性。

**官方当前结论：远端治理完成；阶段候选可验收；完整 Skin-First、Studio IDE 与全部 Native/Skin parity 仍未 GO。**
