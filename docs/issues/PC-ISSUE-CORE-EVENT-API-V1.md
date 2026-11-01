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
| `:app:verifySkinSourceBoundary`、`:app:verifyNativeSkinBoundary` | **PASS**，在两次完整 Gradle task graph 中执行 |
| `:app:assembleDebug` | **PASS**，组合门禁中执行 |
| `:app:lintDebug --offline --console=plain` | **PASS**，0 errors / 54 warnings |
| Android instrumentation / 当前并行 AVD | **NOT TESTED**；未连接或操作 AVD |

两次 forced full JVM run 均有单一且不同的 Web AI/Host 集成失败；失败方法及整个测试类在独立运行时通过。现有证据表明该问题只在全量运行中出现，但根因尚未确定，不能将全量 JVM 门禁标记 PASS，也不能断言与本次 `PowerCalApp` 改动无关。当前全量 JVM 状态为 **NOT CLEAN**。

详细日志均位于忽略目录 `.local/android-test/reports/core-feature-api-event-20261101/`：

- `focused-jvm.log` — SHA-256 `0A75CC507F095A85C20902E1D662F3DA5F207FF0A1CAFCF1A018EBA8024E26DD`。
- `full-jvm-build-lint.log` — 组合强制门禁和 Host 确认节点失败；SHA-256 `D679CA2AB3582D01362C2A5F17A4D090FF208F56B4E44C6E56060F08F124E365`。
- `full-jvm-rerun-3.log` — 第二次最终状态 forced JVM run 与 memo paging timeout；SHA-256 `F85BE67FD1FDDFA9DFE0318D331258E8B39F36F2033B7BFB78254E013BAAAA71`。
- `ai-replace-delete-isolated.log` — 失败方法 isolated rerun **PASS**；SHA-256 `2BEBBD440FB8DEDD76D0B4A611F886346B9E9EE4C73FAE9ACF585AF6B91FF66E`。
- `web-ai-review-class.log` — 包含 memo paging 的测试类 **15/15 PASS**；SHA-256 `51CBB9385B7A102ADBF8B0839C79496F092FF7B275896D1CFC40389B891F9E52`。
- `lint-debug.log` — Lint **0 errors / 54 warnings**；SHA-256 `1FD9684D5B00B1047E84C0506CBAB1AE18DDB0483FC340069A2C1719AB615180`。

本次 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，66,601,582 bytes，SHA-256 `EF77D5BB3B83834E01779DD86DF498DC9D8CCE192707C90FAAB1D2CA4AD7CDB1`。它由本 worktree 当前源构建，未安装到任何设备。

## 未关闭缺口

- Native/Web 的 Draft Deck formal replace/delete/commit 仍有 `DeckController` / direct Store 路径；它们不会更新当前 adapter 的 `featureRevision` 或通知订阅者。
- Memo completion、AI/import proposal 和其他现存 Host 功能尚未通过相应 Core APIs 统一；v1 当前没有完整 Draft/Review、completion、Calendar paging、AI、Reminder、Import/Export 或 Settings/Data API。
- Web Bridge 仍执行 timeline move 的策略/时间 snap、正式事件搜索及部分 Store reads；要满足 transport-only Bridge，需要将这些规则移入 Core/领域 API。
- Native runtime 与 Web runtime 的配对测试还不是完整 `PowerCalApp` Native UI 到 Web UI 的双路径设备回放；instrumentation、TalkBack、实体/OEM 和 skin parity 均 NOT TESTED。
- API 请求收据上限 128 且仅在 adapter 实例存活期间有效；不能当作跨进程 exactly-once 保证。

## Git 与后续

实现分支：`codex/core-feature-api-event`，基于 `c09d39d8161b113ad62ce8348760a2620017869b`。远端集成主线在本任务初始 fetch 时仍指向该 SHA；本报告提交前还需再次验证 freshness。当前报告随功能切片提交；只有提交及普通快进推送完成后才补录提交 SHA。

Phase 1 保持进行中。后续应先集成此已验证编辑器切片，再用显式 Draft/Review API 收口 Native/Web Deck 的 formal 写入及统一变更来源；不得据此删除 Native fallback、切换默认 Skin 或宣称 cutover GO。
