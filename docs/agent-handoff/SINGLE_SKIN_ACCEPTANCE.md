# 单一默认皮肤验收矩阵（G-05）

基线：`ac8d1555a1c177e600e72e4edb5b450189f1fc2c`。本文件是验收定义，不是产品已完成声明。权威目标及任务见 [R2 路线图](../POWERCAL_SINGLE_DEFAULT_SKIN_EXECUTION_PLAN_2026-11-03_R2.md)；原始母项及历史证据见 [SEP 矩阵](../SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md)，冻结语义见 [Core 契约](../CORE_CONTRACT_FREEZE_2026-10-21.md) 和 [Core API v1](../CORE_FEATURE_API_V1.md)。Core 拥有业务、数据与授权；正常 UI 属于唯一默认 Skin；Native 是安全恢复；Studio 从空白创作完整皮肤；分离验收后才通过 Studio 使用 Claude 设计稿美化。

## 状态和签核规则

PASS 仅在本项全部必要功能、Studio、模拟器、真机/OEM 证据及结果对照通过并可复现时使用；PARTIAL 为部分实现或证据；FAIL 为已证实不符合；BLOCKED 为明确前置/外部阻塞；NOT TESTED 为未执行；RETIRED-BY-OWNER 为取消的旧产品要求。各行状态是本矩阵的审慎评估，不改写原 SEP。实现、JVM、Android、PHYSICAL 分开登记；设备证据按行更新，未注明本轮实测的历史结果只算带来源的 PREVIOUS PASS，缺少证据则记 NOT TESTED。JVM 栏 PARTIAL 表示有本轮全量门禁和相关测试文件，非整项语义签核。没有对全部历史 Issue 做无失败断言；下列未确认全项 FAIL。签核记录必须含测试 ID、源码 SHA、APK/皮肤 SHA、命令、设备/API/OEM、输入、Store/界面结果及日志，失败与跳过不得计为通过。

G-04 在 `1e57da5cbb19b4b1348fe390174f629c8871fc47` 实测 JVM 946/946、Studio Python 68/68、两个边界及 APK 构建 PASS；API 35 仅安装两包成功。它们分别证明各自范围，不证明 SEP、完整 Studio 创作、WebView 交互或 OEM 行为。旧 SEP 原表 SEP-01～12、14～16 为 PARTIAL/HOLD，SEP-13 为 NOT TESTED；历史局部 PASS 见原表行及关联 Issue。Core scope 冻结不等于发布 GO：[CORE_FREEZE_VERDICT](../CORE_FREEZE_VERDICT.md) 仍 HOLD，REC-P1-086 OEM 证据 BLOCKED；同时响铃语义在冻结契约中未决。不得将这些缺口静默改为 PASS。

以下路径相对于仓库根目录。每项还须覆盖默认皮肤的完整交互以及 Studio 从空白重建对应表现；模拟器动作应在隔离数据上执行。PHYSICAL 未单独注明时：NOT TESTED，在真实设备重复本项主要动作，记录系统版本、权限、可见结果与 Store 状态；提醒/后台行为另做 OEM 比较。所有项目前总体 PARTIAL，未完成任务均映射到既有 R/F/S/Q ID。

## 原 SEP 母项映射

| 母项 | 新项目 |
|---|---|
| SEP-01 | SSA-02, SSA-15, SSA-16 |
| SEP-02 | SSA-03, SSA-16 |
| SEP-03 | SSA-04, SSA-16 |
| SEP-04 | SSA-05, SSA-16 |
| SEP-05 | SSA-05, SSA-16 |
| SEP-06 | SSA-03, SSA-06, SSA-16 |
| SEP-07 | SSA-05, SSA-07, SSA-11, SSA-16 |
| SEP-08 | SSA-07, SSA-16 |
| SEP-09 | SSA-08, SSA-15, SSA-16 |
| SEP-10 | SSA-08, SSA-11, SSA-16 |
| SEP-11 | SSA-09, SSA-10, SSA-16 |
| SEP-12 | SSA-09, SSA-15, SSA-16 |
| SEP-13 | SSA-01, SSA-13, SSA-16 |
| SEP-14 | SSA-01, SSA-14, SSA-16 |
| SEP-15 | SSA-12, SSA-13, SSA-16 |
| SEP-16 | SSA-05, SSA-07, SSA-08, SSA-10, SSA-11, SSA-16 |

## 可签核项目

### SSA-01｜唯一默认包安装、装载与启动

- SOURCE：SEP-13, SEP-14；冻结契约与对应原 SEP 行。当前证据：G-04 APK 一包及安装 PASS，启动交互 NOT TESTED。
- CORE API：app/src/main/java/dev/powerfulcalendar/ui/v2/SkinPackageManager.kt；Host 包管理和启动，非 Core 业务 API。
- DEFAULT SKIN：首页、加载和失败提示可由皮肤表现。用户动作 → API → Core → 可观察结果：隔离新安装后启动 → Host SkinPackageManager 解析工厂包并启动 WebView → 不写业务 Store → 显示唯一默认首页；重启仍可启动。
- STUDIO：从空白项目创建入口及资源并导出可启动包。
- ANDROID QA：NOT TESTED（本轮）；新装、重启后检查包 ID、首屏、Store 快照；新默认激活仍待实现。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：tools/skin-studio/tests/test_single_default_distribution.py；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinPackageDeliveryInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：R-01, R-02, R-03, R-07, Q-01。

### SSA-02｜时间轴与事件显示

- SOURCE：SEP-01；冻结契约与对应原 SEP 行。当前证据：旧 SEP-01 有 API 35 触摸切片，完整手势和辅助功能未签核。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CalendarFeatureApiV1.kt；app/src/main/java/dev/powerfulcalendar/core/api/v1/StoreCalendarFeatureApiV1.kt。
- DEFAULT SKIN：中央时轴、蓝色当前线、独立左右翼、无上限事件；碰撞只隐藏标题；Today 本地午间/24h。用户动作 → API → Core → 可观察结果：缩放、左右滚动并点重叠事件 → CalendarFeatureApiV1.queryWindow / EventFeatureApiV1.queryEvent → 窗口及原实例不变 → 每个事件准确显示并可点击。
- STUDIO：可创建时轴组件、绑定窗口和动作，修改样式并保持命中。
- ANDROID QA：PARTIAL；F-02 在隔离 API 35 AVD 实测 Today/24h、左右翼独立滚动、月历和时间轴创建及拖动移动。多重叠布局、完整命中矩阵及无障碍仍待验收。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/CalendarFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/TimelineDragWebViewInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（F-02 全量 967/967 通过，但不等于完整验收项签核）；模拟器 PARTIAL（见 F-02 证据，完整多重叠/无障碍覆盖未完成）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：补齐多重叠命中、无障碍、Studio 往返与真机证据后对照冻结契约签核。后续任务：S-02, S-03, Q-02, Q-03。

### SSA-03｜月历、日期与重复实例

- SOURCE：SEP-02, SEP-06；冻结契约与对应原 SEP 行。当前证据：旧 SEP-02 有配对实例及翻月 PASS；F-03 补充了默认 Web Skin occurrence DOM 与冷进程重启证据，但完整生命周期仍未签核。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/RecurrenceFeatureApiV1.kt；app/src/main/java/dev/powerfulcalendar/core/api/v1/CoreRecurrenceFeatureApiV1.kt。
- DEFAULT SKIN：月导航、日期选择、实例列表与日期创建入口。用户动作 → API → Core → 可观察结果：选择月份和日期并打开重复实例 → CalendarFeatureApiV1.queryWindow / RecurrenceFeatureApiV1.queryOccurrenceByKey → 保留原始实例键与选中日期 → 日历和编辑器显示同一实例。
- STUDIO：创建月网格、实例组件及日期绑定。
- ANDROID QA：PARTIAL；F-02 实测月历选中日期并创建 ACTIVITY；F-03 在隔离 API 35 AVD 两次完成跨月 daily ACTIVITY 的 Core→Host→Web state→DOM 核对、Nov 3 实例打开、Oct↔Nov 切换及 force-stop 后冷启动；每次 Store 快照均未因查询/浏览/打开而改变。该 F-03 设备切片通过，但不代表本 SSA 整体签核。
- PHYSICAL：NOT TESTED；需在真机重放主要动作并记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinHostProjectionTest.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/NativeClearMonthOccurrenceParityInstrumentedTest.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinRecurrenceInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（F-03 Host projection 24/24 PASS；两次全量 967 项中均有 1 项 `SystemCalendarImportTest.rejectedItemsArePersistedAsWarningsWithoutCalendarWrites` 因只读 commitCount 查询触发 SQLiteDatabaseLockedException，单项隔离复测 1/1 PASS；全量门禁仍不通过）；模拟器 PARTIAL（F-03 指定 occurrence DOM、切月、选中与双冷启动切片通过，SSA 全面验收未完成）；真机 NOT TESTED；总体 PARTIAL。F-03 因必需全量 JVM 门禁失败记 BLOCKED，不据此确认月历产品缺陷。
- 剩余及签核：调查并解除全量 JVM 中可重复出现的 SQLite 锁门禁问题，再完成剩余冻结场景、Studio 月组件往返和真机证据；不得将单项 Android 切片提升为 SSA PASS。后续映射仍包括 F-06、S-02、S-03、Q-02。

**2026-11-04 Wave 2 状态更新：** F-03 task gate 现为 PASS。当前完整 JVM 套件 967/967，`SystemCalendarImportTest` 4/4、`WebSkinHostProjectionTest` 24/24；API 35 月历/重复实例指定设备切片通过，两轮冷启动和 WebView 单实例编辑测试均复验通过。SSA-03 总体仍为 PARTIAL：Studio 月组件与绑定往返、完整产品场景及真机验收尚未完成；真机为 NOT TESTED。旧 F-03 BLOCKED 叙述是首轮历史状态，详细闭环证据见下方 Wave 2 记录。

### SSA-04｜Memo 和待办

- SOURCE：SEP-03；冻结契约与对应原 SEP 行。当前证据：旧 SEP-03 有实例完成与恢复切片；全覆盖未签核。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/MemoFeatureApiV1.kt；app/src/main/java/dev/powerfulcalendar/core/api/v1/StoreMemoFeatureApiV1.kt。
- DEFAULT SKIN：未完成统一查询含四类，未定日期不伪装今天；日期范围 Memo 是一个完成实例。用户动作 → API → Core → 可观察结果：打开未完成列表并完成、撤销重复实例 → MemoFeatureApiV1.queryEntries / EventFeatureApiV1.setOccurrenceCompletion → 按原键修改完成状态 → 计数与 Open/Done 列表更新。
- STUDIO：创建列表、筛选、徽标和完成动作绑定仍未验证；由 S-02/S-03 验收。
- ANDROID QA：PASS（2026-11-04，隔离 API 35 AVD `PowerCal_ChatGPT_QA35_20261103` / `emulator-5554`，`.d05qaf04` 测试包；Memo 仪器回归 7/7）。WebView 中 Core/API、Host 和 DOM 对齐 251 条 rowKey/revision/occurrence；真实触摸覆盖 Open/Done、标题打开与关闭零写入、完成/撤销及第 251 条无日期续页；关闭并重开隔离 SQLite 数据库、构建新 Host 后完成状态仍为 revision 2，再撤销为 revision 3。OS 进程级 force-stop 冷启动 NOT TESTED。
- PHYSICAL：NOT TESTED；尚未在真机重放主要动作。
- 测试入口：`app/src/androidTest/java/dev/powerfulcalendar/ui/v2/ClearMemoProjectionInstrumentedTest.kt`；`app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinAllKindsTrustedWriteInstrumentedTest.kt`；`app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinHostProjectionTest.kt`、`WebSkinBridgeTest.kt`、`app/src/test/java/dev/powerfulcalendar/core/api/v1/EventFeatureApiV1Test.kt`。
- 状态：Clear 默认 Skin 的 F-04 场景实现 PASS；JVM PASS（全量 967/967，关联焦点测试 31/31）；模拟器 PASS（上述 7/7 场景）；Studio 与真机 NOT TESTED；整体 SSA-04 仍 PARTIAL。
- 剩余及签核：完成 Studio 页面/组件与绑定验收，补足 OS 进程级冷启动及真机证据，并核对完整冻结契约后再签核。后续任务：S-02, S-03, Q-02。

### SSA-05｜四类事件创建、编辑、删除和恢复

- SOURCE：SEP-04, SEP-05, SEP-07, SEP-16；冻结契约与对应原 SEP 行。当前证据：旧 SEP-04/05/07/16 有配对切片；恢复与全流程未签核。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt；app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt。
- DEFAULT SKIN：四类精度、类型转换、验证、删除和恢复入口。用户动作 → API → Core → 可观察结果：分别新建、编辑、删除并恢复四类事件 → EventFeatureApiV1.openNewEvent/openFormalEvent/saveNewEvent/saveFormalEvent/deleteFormalEvent 与 DraftReviewFeatureApiV1.beginRestore/requestRestore → 校验版本、事务及授权 → 表单、冲突与结果可见。
- STUDIO：创建四类表单、验证提示和结果绑定。
- ANDROID QA：PASS（F-05 本轮切片）；API 35 隔离 AVD 上 17 项四类可信写入、3 项删除/冲突触控、2 项无修改关闭/草稿生命周期，以及独立进程 prepare/force-stop/restore 2 阶段均通过；记录审批前后 Store 结果、冲突版本和恢复身份。证据：`.local/android-test/reports/F-05-20261104-wave2/manifest.md`。该结果不覆盖真机/OEM。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/EventFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinAllKindsTrustedWriteInstrumentedTest.kt。
- 状态：实现 PARTIAL（F-05 四类编辑、Host 审批、冲突刷新及恢复路径通过；Studio 四类事件表单的完整创作/往返仍未验收）；JVM PASS（WebSkinEditBridgeTest 49/49，本轮完整门禁 967/967）；模拟器 PASS（上述 F-05 切片）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：完成 Studio 事件表单创作与往返、剩余冻结场景及真机/OEM 证据后再签核。后续任务：F-06, S-04, Q-02。

### SSA-06｜重复规则三作用域与时区

- SOURCE：SEP-06；冻结契约与对应原 SEP 行。当前证据：旧 SEP-06 PARTIAL；三作用域全量设备比较未签核。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/RecurrenceFeatureApiV1.kt；core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt。
- DEFAULT SKIN：明确三作用域、日期精度与冲突提示。用户动作 → API → Core → 可观察结果：编辑单次、此后和整个系列 → EventFeatureApiV1.selectFormalRecurrenceScope / saveFormalEvent 与 RecurrenceFeatureApiV1.queryOccurrencesInWindow → 原键、例外、截断与兼容迁移正确 → 提示作用域及结果。
- STUDIO：创建作用域控件和事件绑定。
- ANDROID QA：PARTIAL（Wave 2 F-06）；隔离 API 35 AVD 上 Native 与默认 Web Skin 真实触控比较 ALARM 和纯日期 DEADLINE 的 Single、This-and-future、Whole 共六组，核对 occurrence key、系列身份/版本、拆分、取消零写入、批准后 Store 等价。设备上未执行秋季 DST fold/gap 和日期上下界场景；DST 折叠较早偏移由 JVM Core API/Bridge 测试覆盖。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/RecurrenceFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinRecurringScopeParityInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PASS（RecurrenceFeatureApiV1Test 9/9、WebSkinRecurrenceBridgeTest 16/16；全量 970/970，0 failures/errors/skips）；模拟器 PARTIAL（上述六组触控与 Store 对照通过）；Studio NOT TESTED；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：Studio 绑定和预览往返由 S-03/S-04 覆盖；完整 DST gap/fold、日期边界和真机/OEM 证据仍需在最终验收闭合。任务级 F-06 已通过，不据此提升 SSA-06 总体状态。后续任务：S-04, Q-02。

#### F-06 Wave 2 执行证据（2026-11-04）

- 任务级状态：PASS；无产品逻辑变更，只补充 API、Bridge 和设备回归。
- Core/API：每日重复 ALARM 的 2026-11-01 01:30 America/New_York 歧义槽经 RecurrenceFeatureApiV1 选择较早偏移（05:30Z，-04:00）；日期型 DEADLINE 保留本地日期、`deadlineMillis=null`。Bridge 测试验证 Single 写入同一 eventSeq、revision 递增 1、只产生原槽 override；宿主拒绝/取消在批准前 Store 不变。
- Android：隔离 AVD `PowerCal_Wave2_QA35_20261104` / `emulator-5554` / API 35；`nativeAndClearMatchAllScopesForAlarmAndDateOnlyDeadline` 1/1 PASS，内部六组触屏（两类 × 三作用域）均核对 Native/Clear 记录、身份、occurrence key、revision、提交数、草稿数与取消零写入。设备日志在 `.local/android-test/reports/F-06-20261104-wave2/android-scope-parity-renamed-final.log`。
- JVM 与门禁：API 9/9、Bridge 16/16；完整 `:app:testDebugUnitTest` 为 970/970、140 suites、0 failures/errors/skips。两个源码边界、Debug APK、Lint（0 errors/56 warnings）、AndroidTest Kotlin 编译 PASS。
- 产物：APK SHA-256 `08739af8ec4967c764e738e20885a2fc8c1c96033f4e33a3414643b48422d16e`；APK 中一份 `assets/powercal-clear-default.pcskin`，与源码逐字节一致，SHA-256 `299e4bdd78c8936f6c15db0200172e659aaa5b0e0643f0d70c115966ac35a9f4`。生产代码与默认皮肤未修改。
- 限制：DST fold/gap 未在设备执行；PHYSICAL/OEM、完整 Studio recurrence 创作 NOT TESTED。SSA-06 仍 PARTIAL，SEP 历史签核 INCOMPLETE，Product Cutover HOLD。详见 `.local/android-test/reports/F-06-20261104-wave2/manifest.md`。

### SSA-07｜草稿、审核及发布

- SOURCE：SEP-08, SEP-07, SEP-16；冻结契约与对应原 SEP 行。当前证据：旧 SEP-08 有局部设备证据；冲突与恢复全量待验。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/DraftReviewFeatureApiV1.kt；app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt。
- DEFAULT SKIN：草稿卡片、搜索、替换比较、冲突和恢复结果。用户动作 → API → Core → 可观察结果：创建、修改、选择、丢弃/撤销并审核发布草稿 → DraftReviewFeatureApiV1.createDraft/editCurrentDraft/flush/select/commit/undoDiscard → 校验版本和请求 ID 后事务写入 → 卡片、冲突及收据更新。
- STUDIO：创建卡组及数据/动作绑定并重建。
- ANDROID QA：PASS（F-05 本轮切片）；批准前/取消/冲突路径核对正式记录和版本；批准后核对提交；冷进程恢复用独立 PID/token 验证草稿、Memo 状态及 identity/sequence/commit 不变量。队列搜索、撤销等其余母项仍未全量验收。证据：`.local/android-test/reports/F-05-20261104-wave2/manifest.md`。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/DraftReviewFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/DraftDeckSearchWebViewInstrumentedTest.kt。
- 状态：实现 PARTIAL（F-05 发布确认、草稿 checkpoint、冲突保留与冷启动恢复切片通过；完整队列操作、Studio 创作及其他审核范围未签核）；JVM PASS（关联 49/49，本轮完整门禁 967/967）；模拟器 PASS（F-05 切片）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：补齐草稿队列搜索/撤销等完整流程、Studio 往返及真机/OEM 证据后再签核。后续任务：F-06, F-07, S-04, Q-02。

### SSA-08｜AI 对话、提案与人工审核

- SOURCE：SEP-09, SEP-10, SEP-16；冻结契约与对应原 SEP 行。当前证据：旧 SEP-09/10 有局部设备证据；全量审核和真实提供商未签核。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/AiFeatureApiV1.kt；core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/AiProposalFeatureApiV1.kt；app/src/main/java/dev/powerfulcalendar/core/api/v1/StoreAiFeatureApiV1.kt。
- DEFAULT SKIN：历史、上下文、附件、四种决策、取消/重试与失败。用户动作 → API → Core → 可观察结果：发送文本或经同意附件并选择同意、草稿、拒绝、补充 → AiFeatureApiV1.chat/generateProposal 与 AiProposalFeatureApiV1.assessReview/createDrafts/applyReviewedProposals → 仅授权有效项写入并返回收据 → 对话、卡片与部分结果可见。
- STUDIO：创建聊天、提案卡与状态绑定；预览 Mock 不写数据。
- ANDROID QA：NOT TESTED（本轮）；比较批准前零写入、过期授权拒绝、部分成功、幂等重试及重启历史。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/AiFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/PowerCalAiProposalReviewWebViewInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：F-07, S-02, S-03, Q-02。

### SSA-09｜设置、权限、导入和导出

- SOURCE：SEP-11, SEP-12；冻结契约与对应原 SEP 行。当前证据：旧 SEP-11 有导入、重试和错误哈希切片，完整权限/异常未签核。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/ImportExportFeatureApiV1.kt；app/src/main/java/dev/powerfulcalendar/core/api/v1/StoreImportExportFeatureApiV1.kt；权限和系统选择器由 Host 承担。
- DEFAULT SKIN：设置页、许可说明、导入导出及错误提示。用户动作 → API → Core → 可观察结果：授权后导入 ICS 或系统日历、导出备份并恢复 → ImportExportFeatureApiV1.importIcsToDrafts/exportBackup/restoreBackup 与 SettingsDataFeatureApiV1.querySettings → 校验来源、权限、重复项及完整性 → 显示进度、拒绝和结果。
- STUDIO：创建设置页与 Host 动作绑定，秘密仍留 Host。
- ANDROID QA：PARTIAL（2026-11-04，隔离 API 35 AVD `PowerCal_Wave2_QA35_20261104`）：本轮 13 个定向 instrumentation 方法通过，实际覆盖 Clear 通知拒绝、系统日历权限拒绝/允许及合成事件导入、ICS 导入草稿/导出、备份导出/空库恢复/取消/坏哈希/无效 JSON/未来 schema 拒绝、受保护凭据路由、精确闹钟设置页往返、空 Store 提醒重建及 AlarmManager FIRED。通知权限通过 Skin 请求后的允许路径、ICS 选择器取消和 Studio 空白创作本轮未测。
- PHYSICAL：NOT TESTED（本轮）；真机主要动作及 OEM 提醒投递仍待独立验收。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/ImportExportFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/ClearSettingsOptionsInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PASS（本轮全量 970/970，但不等于 SSA 整项签核）；模拟器 PARTIAL；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：补齐 Skin 请求通知权限后的允许/返回刷新、ICS 取消零写入、Studio 空白重建及真机系统行为；逐项核对冻结契约后签核。后续任务：F-08, F-09, S-03, Q-02。

### SSA-10｜提醒、重启和时区变化

- SOURCE：SEP-11, SEP-16；冻结契约与对应原 SEP 行。当前证据：CORE_FREEZE_VERDICT 有旧 API 35 重启切片；同时响铃语义未决，REC-P1-086 OEM 阻塞。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/ReminderFeatureApiV1.kt；app/src/main/java/dev/powerfulcalendar/core/api/v1/AndroidReminderFeatureApiV1.kt。
- DEFAULT SKIN：提醒开关、权限与失败状态独立显示。用户动作 → API → Core → 可观察结果：设置提醒并重启或改变时区 → ReminderFeatureApiV1.queryPermissions/queryEvent/reconcile 与 Host 调度 → 按冻结规则重建，响铃不隐式完成 → 皮肤显示状态及拒绝。
- STUDIO：创建提醒控件及权限状态绑定。
- ANDROID QA：PARTIAL（2026-11-04，隔离 API 35 AVD `PowerCal_Wave2_QA35_20261104`）：真实 AlarmManager 测试 1/1 触发 receiver、记录 FIRED 并发出测试通知；Skin 精确闹钟权限状态与设置页返回、空 Store 手动重建通过。设备重启、时区/DST、10 分钟宽限和 Skin 内提醒状态列表的完整转换未在本轮执行。
- PHYSICAL：BLOCKED：REC-P1-086 尚缺物理/OEM 提醒证据；真机验证权限、重启、时区及厂商省电。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/ReminderFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/reminders/ReminderAlarmManagerDeliveryInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PASS（本轮全量 970/970，但不等于 SSA 整项签核）；模拟器 PARTIAL；真机 BLOCKED（REC-P1-086）；总体 PARTIAL。
- 剩余及签核：在隔离设备验证重启、时区/DST、宽限边界和 Skin 状态列表；真机/OEM 权限、后台限制及投递仍需独立签核。后续任务：F-08, F-09, Q-02, Q-04。

### SSA-11｜可信授权、隔离与错误恢复

- SOURCE：SEP-16, SEP-07, SEP-10；冻结契约与对应原 SEP 行。当前证据：旧 SEP-16 有批准及过期竞态切片，完整隔离未签核。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt；core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/AiProposalFeatureApiV1.kt；Host 持有批准。
- DEFAULT SKIN：显示授权范围、确认、冲突及可恢复错误；不得持有凭据或直接访问数据库。用户动作 → API → Core → 可观察结果：提交过期版本、重放或无授权的正式写入 → EventFeatureApiV1.saveFormalEvent / AiProposalFeatureApiV1.applyReviewedProposals 经 Host 审批 → 拒绝且不改 Store → 皮肤显示错误并可重试。
- STUDIO：创建状态显示并绑定正式 Host 动作；预览不得制造批准。
- ANDROID QA：NOT TESTED（本轮）；重放过期批准、变更载荷和恶意包请求，核对版本、记录和凭据隔离。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/AiProposalFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinCapabilityGateInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：F-05, F-07, S-03, Q-02, Q-03。

### SSA-12｜Studio 从零创作完整皮肤

- SOURCE：SEP-15；冻结契约与对应原 SEP 行。G-04 Python 68/68 和浏览器源码往返为此前证据；S-02 代码提交 `d0569f14` 增加空白工程 GUI 页面/组件树及运行时投影。
- CORE API：tools/skin-studio/skin_studio.py；tools/skin-studio/v3-studio.js；core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt。
- DEFAULT SKIN：首页、四类表单、草稿、AI、设置、恢复等完整表现。用户动作 → API → Core → 可观察结果：建立空白工程、添加页面/组件/资源/绑定并预览导出 → Studio 项目操作及 Host 绑定到既有 Core API → 业务由 Core 执行 → 导出包在 Android 完成主要用户流程。
- STUDIO：S-02 浏览器验收 PASS 限定在空白工程结构编辑：新增页面，创建 13 种基础组件，嵌套/取消嵌套、重排、编辑 props/样式/绑定元数据、删除、撤销/恢复、保存重开；运行时预览 DOM 与导出重导入的 `project.json`/生成资源一致。S-04 Studio Python 78/78、Chrome E2E 与 Clear Studio Bridge 导出/重导入 E2E PASS；覆盖五页面固定数据、正式 `browseMonth` 绑定、能力拒绝、CSP/MIME/字体和资源错误。完整空白工程业务创作仍待后续签核。
- ANDROID QA：PARTIAL（S-04）；API 35 隔离 AVD 安装本轮 Studio 导出的 Clear 派生测试包（SHA-256 `267383470e215659144b1a302ee57b5daea8721bbc240cc762c87699c833d74f`，仅在 `androidTest`）。真实 Host `browseMonth` 触控 1/1 PASS，Store 行数不变；资源一致性、能力隔离及共享 Memo 回归各 1/1 PASS。空白项目五页完整业务操作尚未验证。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：tools/skin-studio/tests/test_project_ops_v3.py；tools/skin-studio/tests/test_v3_preview.py；tools/skin-studio/tests/e2e_blank_project_persistence.cjs；tools/skin-studio/tests/e2e_v3_source.cjs；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/StudioAuthoredSkinBridgeInstrumentedTest.kt。
- 状态：空白工程页面/组件 GUI 在 S-02 范围 PASS；整体实现 PARTIAL；本轮 Studio Python 78/78、浏览器 E2E PASS、API 35 设备子集 PASS；真机 NOT TESTED；总体 PARTIAL。S-04 证据：`.local/android-test/reports/S-04-20261104-wave2/manifest.md`。
- 剩余及签核：完成空白工程完整业务创作、更多正式 API 绑定和 Android 操作，补足真机证据，再核对冻结契约。后续任务映射：S-05, F-07, S-06, Q-02。

### SSA-13｜包编辑、构建、导出和重新导入

- SOURCE：SEP-13, SEP-15；冻结契约与对应原 SEP 行。G-04 builder 与浏览器往返为此前证据；S-02 新增空白页面树投影随 v3 包构建、校验及重导入的一致性证明。旧 SEP-13 母项状态不变。
- CORE API：tools/skin-studio/skin_studio.py；tools/skin-studio/build_default_clear.py；Host SkinPackageManager 校验包。
- DEFAULT SKIN：默认皮肤所有页面均可编辑及重建。用户动作 → API → Core → 可观察结果：导入默认包，编辑组件、嵌套资源和绑定 → Studio build/validate/import/export → 保留 manifest、未知字段与文件字节 → 预览和 Android 显示编辑结果。
- STUDIO：S-02 浏览器流程已证明页面树、生成运行资源、导出 `.pcskin` 与重导入保持一致；S-04 对从工厂包派生的 Studio 工程增加页面控件和样式，导出/重导入后 9 个源码/资源文件字节保持一致；其余素材与元数据往返由既有包编辑回归保护。
- ANDROID QA：PARTIAL（S-04）；安装 Studio 导出的确切测试包 SHA-256 `267383470e215659144b1a302ee57b5daea8721bbc240cc762c87699c833d74f`，真实触控绑定 `browseMonth` 并核对 Host 月份更新及 Store 零写入；API 35 WebView 资源和 capability 测试通过。真机 NOT TESTED。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：tools/skin-studio/tests/test_default_clear.py；tools/skin-studio/tests/test_v3_source_editing.py；tools/skin-studio/tests/test_project_ops_v3.py；tools/skin-studio/tests/e2e_blank_project_persistence.cjs。
- 状态：Studio 构建/重导入与 S-04 Android 设备子集 PASS；全 SEP 包验收仍 PARTIAL；真机 NOT TESTED；总体 PARTIAL。S-04 证据：`.local/android-test/reports/S-04-20261104-wave2/manifest.md`。
- 剩余及签核：完成 S-05 资源/源码无损往返全流程，继续取得冻结功能覆盖与真机证据后再签核。后续任务：S-05, F-07, S-07, Q-01。

### SSA-14｜损坏、加载失败与 Native 恢复

- SOURCE：SEP-14；冻结契约与对应原 SEP 行。当前证据：旧 SEP-14 有渲染器及损坏包切片；静默故障与 OEM 未签核。
- CORE API：app/src/main/java/dev/powerfulcalendar/ui/v2/SkinPackageManager.kt；EventFeatureApiV1.checkpointFormalEvent；Host WebView 恢复。
- DEFAULT SKIN：清楚报告加载失败；Native 为安全恢复通道。用户动作 → API → Core → 可观察结果：损坏测试包或终止 WebView → Host SkinPackageManager 与恢复检查点 → 保留未保存草稿和 Store，失败回落 Native → 恢复入口可操作。
- STUDIO：可编辑默认包并用隔离夹具注入失败。
- ANDROID QA：NOT TESTED（本轮）；比较故障前后记录、草稿、选择和重启后恢复；覆盖冷启动和静默失败。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinRendererRecoveryInstrumentedTest.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinCorruptPackageColdRecoveryInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：R-02, R-03, R-07, Q-03。

### SSA-15｜无障碍、小屏、字体和 IME

- SOURCE：SEP-01, SEP-09, SEP-12；冻结契约与对应原 SEP 行。F-09 新增默认 Skin Back、IME、renderer 恢复、2× 字体、窄屏和 reduced-motion 设备切片；TalkBack 焦点顺序及真机仍未签核。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt；Host 处理 Android 窗口和权限。
- DEFAULT SKIN：TalkBack 名称、可点击区域、缩放、IME 跟随与返回顺序。用户动作 → API → Core → 可观察结果：在大字体、小屏和键盘打开时导航、编辑并返回 → Host 窗口/Back 与 EventFeatureApiV1.editorSnapshot → 无额外 Store 写入 → 焦点、标签、控件和键盘不遮挡。
- STUDIO：可创建语义标签、焦点顺序和响应式布局。
- ANDROID QA：PARTIAL（2026-11-04，隔离 API 35 AVD `PowerCal_Wave2_QA35_20261104` / `emulator-5554`）。F-09 的 11 个唯一 Android instrumentation 方法在正确系统设置下通过：页面 Back、IME、清洁/脏编辑器退出、renderer/JS 故障恢复、2× 字体、360dp/288dp 窄屏、竖屏及减少动画；清洁退出和页面导航的 Store 快照不变，脏编辑器仅留下单个待处理草稿。TalkBack 服务已启用且绑定，但焦点探针没有观察到导航按钮顺序，故实际 TalkBack 遍历标记 NOT TESTED，F-09 不完整 PASS。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：`app/src/androidTest/java/dev/powerfulcalendar/ui/v2/ClearNavigationBackInstrumentedTest.kt`；`WebSkinRendererRecoveryInstrumentedTest.kt`；`ClearDisplayAdaptationInstrumentedTest.kt`；`TimelineSystemFontScaleInstrumentedTest.kt`；`TimelineSmallScreenInstrumentedTest.kt`；`SystemReducedMotionInstrumentedTest.kt`。
- 状态：实现 PARTIAL；JVM PASS（F-09 全量门禁 970/970，0 failures/errors/skips）；模拟器 PARTIAL（上述 F-09 设备切片通过，但 TalkBack 焦点顺序未验证）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：必须直接证明 TalkBack 主要操作的焦点遍历顺序，并完成真机复验；Studio 结构语义和完整 SSA 仍待相应任务签核。证据：`.local/android-test/reports/F-09-20261104/manifest.md`。后续映射：F-09、S-06、S-07、Q-02～Q-04。

### SSA-16｜业务等价与正式发布

- SOURCE：SEP-01, SEP-02, SEP-03, SEP-04, SEP-05, SEP-06, SEP-07, SEP-08, SEP-09, SEP-10, SEP-11, SEP-12, SEP-13, SEP-14, SEP-15, SEP-16；冻结契约与对应原 SEP 行。当前证据：SEP 母项未全部签核，G-04 仅技术基线；Cutover HOLD。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt；docs/CORE_FEATURE_API_V1.md。
- DEFAULT SKIN：全部正常 UI 由唯一默认 Skin 呈现；Native 提供安全恢复。用户动作 → API → Core → 可观察结果：用相同隔离数据重放所有用户流程 → CoreFeatureApiV1 各服务及 Host 正式批准 → 对比记录、版本、收据、提醒与恢复 → Skin 呈现等价结果。
- STUDIO：从零包和默认包均可重建并在 Android 执行。
- ANDROID QA：NOT TESTED（本轮）；记录源码、APK、皮肤哈希和逐案例结果；所有必需设备及物理门禁通过并获独立 cutover 授权。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinAllKindsTrustedWriteInstrumentedTest.kt；tools/skin-studio/tests/test_single_default_distribution.py。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：F-01, S-06, S-07, Q-01, Q-02, Q-03, Q-04, Q-05。

## 已取消旧产品要求

**RETIRED-BY-OWNER（2026-11-03 最新裁决）：** 多皮肤目录、候选主题/皮肤商城、并行日常 UI 和**历史已安装皮肤保留/导出**均取消。正式 App 只保留唯一默认 `.pcskin`，Native 只负责安全恢复。R-03 在隔离 QA 中验证旧选择收敛并**仅删除 App 私有 `files/skin-packs/` 下的历史安装 `.pcskin`**；不需要保存旧包 ID、哈希归档或用户导出渠道。旧 parser、版本迁移测试与第三方测试夹具只为兼容性验证保留，不是产品库存。Calendar Store、草稿、提醒、AI 对话、Git Worktree、Studio 项目、设计源稿、测试夹具和外部 SAF 文件不得删除。R-04/R-05/R-06 随后退役日常入口；生产 Cutover 继续 HOLD。

## 最短关键路径

R-01 盘点入口 → R-02/R-03 定义并验证启动及历史迁移 → R-04～R-07 收敛日常入口和恢复 → F-01～F-09 逐项完成 Core/Host 与默认 Skin 等价，同时 S-01～S-05 完成可编辑结构、绑定、预览及包往返 → Q-01～Q-04 完成新旧安装、全流程、恢复及真机/OEM 证据 → S-06 从空白项目创作完整皮肤并在 Android 运行，S-07 验证默认源码全量再编辑 → Q-05 独立授权后切换；Q-06 安全清理。视觉美化仅在完整分离与 Studio 创作验收之后进行。顺序表示依赖关系，不能以技术基线代替正式签核。

## R-01 Skin Runtime 入口和副作用审计附表

审计基线：d26d31b0f4935e0060271aa97070f511d4785c4f。本附表是源码调用链审计，不代表设备验收，不改变 16 项 SSA 状态。路径相对仓库根目录；分类表达后续归属，不授权本轮删除。 表内 `ui/v2/X` 简写均指 `app/src/main/java/dev/powerfulcalendar/ui/v2/X`；`MainActivity.kt` 指 `app/src/main/java/dev/powerfulcalendar/MainActivity.kt`。

### 从实际调用确认的行为

1. MainActivity.onCreate 初始化 Store 后，在 setContent 前调用 SkinPackageManager.restoreSelection()，再调用 ActiveVisualSkin.install()，最后组合 PowerCalApp。restoreSelection 读取私有 SharedPreferences powercal-skin-choice/selected。新安装无该键时 currentId() 返回 powercal-default，即 Native；Clear 包不是当前新装选择。
2. currentId() 读 selected；值类型损坏时同步选择 Native 并 reset registry。restoreSelection 对 powercal-default reset Native；对 Clear/用户包加载和校验成功才激活；未知、缺失或损坏包将 preference 写回 powercal-default 并 reset Native。currentId() 返回一个字符串不证明对应包可加载。
3. 当前 PROFILE 实值是 native。ActiveVisualSkin.install() 因而注册 NativeSkinHostRegistration。studio-example 只有编译配置改为该值才从 install() 进入；仓库内 installExample() 的调用点只有 PresentationSkinRegistryTest 两处，不是用户运行时选择器。
4. 实际并存的呈现路径：Native Compose；可信源码编译的 Compiled renderers；SkinPackageManager 对 ui.json 调 RuntimeSkinSchema.parse 后经 RuntimeSkinSurfaces/RuntimeSkinCompose 渲染的声明式 v2；active.web 非空时由 SkinAppShell 进入 WebSkinRoot/Android WebView 的 v3。SkinAssets 的 v1 resource slots 是局部外观覆盖，不是整页 runtime。
5. 页面级分流分别出现在 SkinnableTimelineScreen、MonthScreen、MemoCenterScreen、AiChatScreen、ToolsPage、SkinnableDraftDeck、SkinnablePickers 和 SkinnableOverlays。Chrome 壳优先 forceNative，其次 v3 Web，再按 v2/compiled/native renderer 选择；不能把 Chrome 分支当成所有页面的单一选择器。
6. Native 是实际 fresh-install 日常 UI。Native adapters 将 context.Native() 路由到 TimelineScreen、NativeMonthScreen、NativeMemoCenterScreen、NativeAiChatScreen、NativeToolsPage 等。Native 同时承载 Host 可信确认、附件/权限确认、SAF、系统设置等。当前 Clear 包不能独立提供所有这些 Compose 页面功能。
7. 当前多皮肤普通入口包括 Native Tools 的皮肤区、Clear v3 Settings 的 skinChoices 选择/导出/删除与 Host 导入、v2 RuntimeSkinSurfaces.settings 的 skins 节点。NativeSkinCatalogReview 是 PowerCalApp 调用的切换/删除确认对话框。它们分别由 R-04/R-05 收敛。
8. 普通确认切换先调用 WebSkinEditService.prepareSkinChange()；活动编辑经 EventFeatureApiV1.checkpointFormalEvent 持久化检查点，失败时阻止该次确认切换。随后 SkinPackageManager.select() 激活已验证包并用 SharedPreferences.apply() 写 selected。切换本身不写 Calendar Store；未完成编辑的检查点是有意的 Store 写入。Skin ID 不经 SettingsDataFeatureApiV1 profile 设置保存。
9. SAF 导入验证包后写 app-private filesDir/skin-packs/<id>.pcskin，读回哈希后再 rename；不自动激活，不改 Calendar Store。导出复制到用户指定 SAF URI，不改选择或 Store。删除经二次确认；若正在运行先选 Native 并写 preference，然后实际删除用户包文件。R-01/R-04/R-05 不能触发删除。
10. **更新裁决**：历史用户皮肤包不保留。R-03 必须在受控、仅作用于隔离 QA 应用私有 `files/skin-packs/` 的流程里验证已安装旧 `.pcskin` 的清除，同时保证 Calendar Store/草稿/提醒/AI 对话未改；无需旧包导出或再次选择入口。R-04/R-05 应移除日常枚举、导入、导出及候选选择 UI；Studio 项目和外部设计文件不在清理范围。
11. Native 恢复独立于 .pcskin、WebView 和 Studio：MainActivity 的 Activity touch 恢复识别早于 WebView 分发；manager 写 Native preference 并 reset registry；NativeSkinHostRegistration/native-skin 提供 Compose 回退。Web 故障 callback 会尝试保存编辑检查点、切 Native、进入 Tools。风险：Activity 紧急手势不调用 prepareSkinChange；Web 故障即使检查点失败也继续回退并提示不要结束进程。R-07 必须验证两种情况下编辑数据的恢复边界。
12. Studio 和正式 v3 runtime 共用 .pcskin schemaVersion 3、default-clear 源码与 host.action bridge。Studio builder/validator 产检包；Android 解析同一 ZIP。皮肤不直接获得 Store/Context/凭据/审批 token；消息经 WebSkinHost 解码，再由 PowerCalApp 持有的 PowerCalCoreFeatureApiV1 或 Host adapter 执行。共用格式不等于 WebView、功能等价或设备验收已通过。
13. Native pages、可信确认、权限/附件对话框和系统 picker 仍被日常功能使用。等对应默认 Skin 流程通过后才能把页面 bodies 从日常入口退役；Host trusted UI 和 Q-03 验证过的最小 Native recovery 子集继续保留。

### 当前调用图

    MainActivity.onCreate
      ├─ SkinPackageManager.restoreSelection()       [preference → load/validate → registry]
      ├─ ActiveVisualSkin.install()                  [PROFILE=native → NativeSkinHostRegistration]
      └─ PowerCalApp(store)
          ├─ PowerCalCoreFeatureApiV1(store, Android Host adapters)
          ├─ SkinAppShell(forceNative=tools)
          │   ├─ forceNative → NativeChromeSkin.Root
          │   ├─ active.web != null → WebSkinRoot → Android WebView
          │   │    └─ WebMessagePort → decodeBridgeAction/capability gate
          │   │         → PowerCalApp Host callback → Core API / Android Host
          │   │         → state snapshot → Web skin
          │   └─ page renderer
          │        ├─ active.runtime → RuntimeSkinSurfaces → RuntimeSkinCompose
          │        ├─ CompiledSkinRuntime / PresentationSkinRegistry
          │        └─ Native renderer → context.Native() → Native Compose page
          └─ Host-owned permissions, SAF, trusted review, reminders, recovery

RuntimeSkinPickerHost 另在 Compose 壳上呈现 Host-owned 日期/时间 modal。SkinAppShell 的 Chrome 分支不等于全页面分发。

### 入口、运行时和副作用处置表

| 文件 / 符号 | 实际调用来源 | 可见行为 | preference / Store / 文件副作用 | 当前依赖 | 分类 | R2 后续任务 | 代码证据 |
|---|---|---|---|---|---|---|---|
| app/src/main/java/dev/powerfulcalendar/MainActivity.kt：onCreate | Android 启动 | 首屏前恢复选择；fresh install 当前 Native | 读 preference；初始化 Store 和提醒渠道 | Android lifecycle、Host、manager | KEEP_HOST | R-02, R-03, R-07 | MainActivity.kt:83-92 |
| MainActivity.kt：dispatchTouchEvent | Activity 输入早于 Compose/WebView | 交替点击紧急回 Native | commit Native preference；手势自身不调 Store | Activity touch、Native recovery | KEEP_RECOVERY | R-02, R-03, R-07 | MainActivity.kt:32-68 |
| ui/v2/SkinPackageManager.kt：currentId/restoreSelection/selectNativeForEmergencyRecovery | 启动、App 状态和故障 callback | Native、Clear、用户包；未知/损坏包回 Native | 读写 selected；reset 内存 registry；不写 Calendar Store | SharedPreferences、assets、文件校验 | KEEP_HOST | R-02, R-03, R-07 | SkinPackageManager.kt:26-138 |
| SkinPackageManager.kt：installed/install/export/delete | PowerCalApp Native Tools/Web callbacks | 枚举、导入、导出、删除包 | import 写私有 skin-packs；export 写 SAF；delete 真删包；import 不激活 | SAF、文件系统、解码校验 | KEEP_HOST | R-03, R-04, R-05 | SkinPackageManager.kt:52-178 |
| ui/v2/SkinAssets.kt：PowerCalSkinPack/PowerCalSkinRegistry | manager 解码后 activate，Compose 读取 | 局部资源、v2 document、v3 bundle 的内存选择 | registry 内存状态，无自身 Store 写 | decoder、Compose slots、WebSkinRoot | KEEP_DEFAULT_SKIN | R-02, R-03, R-07 | SkinAssets.kt:73-119 |
| ui/v2/PowerCalApp.kt：Core API 与 Host adapters | MainActivity.setContent | 页面、可信确认、系统/file picker 和功能状态 | 业务经 Core API；授权和平台行为在 Host | PowerStore、Core APIs、Android ports | KEEP_HOST | R-07, F-01–F-09 | PowerCalApp.kt:232-264 |
| app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt：page switch and screen call sites | PowerCalApp Compose content | Displays current page and creates snapshots/callbacks for Timeline/Month/Memo/AI/Tools | Page state and feature calls are Host/Core-owned; no skin choice/package write in these call sites | Page composables and Core/Host feature APIs | KEEP_HOST | R-07, F-01–F-09 | PowerCalApp.kt:2539, 2620, 2728, 2776, 2799, 2867 |
| app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt：onWebSkinFailure callback | SkinAppShell passes callback to WebSkinRoot | Failed Web runtime returns to Native Tools with recovery message | Attempts edit checkpoint, commits Native preference, changes page state; checkpoint may write draft state | WebSkinRoot, WebSkinEditService, SkinPackageManager, Native Tools | KEEP_RECOVERY | R-02, R-03, R-07, Q-03 | PowerCalApp.kt:2515-2536; SkinnableChrome.kt:15-39 |
| app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt：皮肤目录状态投影和 skinChoices UI wiring | Native Tools / v2 Settings / Clear Web Settings bindings | 列出候选包并暴露选择/导入/导出/删除入口 | 初始读取 manager 列表/选择；投影自身不写 Store 或用户文件 | Native/Runtime/Web settings surfaces | RETIRE_DAILY_UI | R-04, R-05 | PowerCalApp.kt:262-267, 2431-2441, 2549-2573 |
| app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt：SAF/package host handlers | Native Tools / Web action callbacks | SAF import/export, confirmed selection/delete | Import writes user-selected package to app-private directory without activation; export copies archive; selection writes preference and may checkpoint an active edit; confirmed delete removes installed user package | manager, SAF, edit checkpoint API | KEEP_HOST | R-02, R-03, R-04, R-05, R-07 | PowerCalApp.kt:1785-1825, 2137-2158, 3152-3200; SkinPackageManager.kt:52-178 |
| native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeToolsPage.kt：皮肤 section | PowerCalApp ToolsPage | Native/Clear/用户包列表、切换、导入/导出/删除 | 委托 Host callbacks | Native Settings Compose、包目录 | RETIRE_DAILY_UI | R-03, R-04 | NativeToolsPage.kt:47-109 |
| native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeSkinCatalogReview.kt | PowerCalApp pending activation/delete | 切换/删除确认和回退提示 | 只确认；回调才调用 manager | Native Compose dialog、Host | RETIRE_DAILY_UI | R-04 | NativeSkinCatalogReview.kt:11-42；PowerCalApp.kt:3152-3200 |
| tools/skin-studio/default-clear/source/web/app.js：skinChoices/renderSettings | Clear v3 Settings DOM | 候选选择、导出、删除按钮 | 发 host.action；副作用由 Host 执行 | manifest capability、WebSkinHost | RETIRE_DAILY_UI | R-05 | app.js:610-650 |
| tools/skin-studio/default-clear/manifest.json 与 app/src/main/assets/powercal-clear-default.pcskin | Studio build；MainActivity restore | 唯一正式 APK .pcskin 当前为 Clear v3，但非新装选择 | APK 静态资源；选择另存 preference | WebSkinHost、Studio canonical source | KEEP_DEFAULT_SKIN | R-02, R-05, R-07, S-07 | manifest.json:1-24；main assets .pcskin 数量 1 |
| ui/v2/skins/ActiveVisualSkin.kt：PROFILE/install/native branch | MainActivity.onCreate after restoreSelection; invalid configuration fallback | Current profile is native and registers fallback renderers; fresh-install ordinary pages currently use this route | Process renderer registries only; Web active package registry is separate | NativeSkinHostRegistration; independently loaded Web package | KEEP_RECOVERY | R-02, R-03, R-07, Q-03 | ActiveVisualSkin.kt:22-28; MainActivity.kt:86-87 |
| ui/v2/skins/ActiveVisualSkin.kt：studio-example branch/installExample | Only compile-time PROFILE change or direct test/preview call; no user selector | Compiled example renderers replace registered renderer set | Process renderer registries only | StudioExample Kotlin renderers and typed contexts | RETIRE_AFTER_PARITY | R-06 | ActiveVisualSkin.kt:22-44; PresentationSkinRegistryTest.kt:50,93 |
| app/src/test/java/dev/powerfulcalendar/ui/v2/PresentationSkinRegistryTest.kt | Calls installExample() for renderer contract tests | Test-only; no end-user entry | Test process registry only | Compiled example source retained for tests until R-06 fixture decision | KEEP_TEST_COMPAT | R-06 | PresentationSkinRegistryTest.kt:50,93 |
| ui/v2/NativeSkinHostRegistration.kt：install 与恢复用 Native adapters | ActiveVisualSkin.install；Activity recovery 在同进程保留注册表 | Native Chrome/Tools/overlay/picker 等安全回退入口 | 只注册 renderer，不独立写 Store | Native Compose recovery、Host callbacks | KEEP_RECOVERY | R-02, R-03, R-04, R-07, Q-03 | NativeSkinHostRegistration.kt:15-90；MainActivity.kt:86-87 |
| Native 默认日常页面路由（Timeline/Month/Memo/AI/Settings/Navigation/Deck） | PROFILE=native → Native adapters → 各 `context.Native()` 页面 | 当前新装和普通页面使用 Native UI | 页面业务仍走 Host/Core；展示层自身不拥有 Store | app/native-skin Compose 页面 | RETIRE_AFTER_PARITY | R-02, R-06, F-02–F-09 | ActiveVisualSkin.kt:22-28; NativeSkinHostRegistration.kt:31-90; PresentationSkinRuntime.kt:184-208, 243-254, 325-345, 401-524 |
| ui/v2/CompiledSkinTimeline.kt：CompiledSkinRuntime.timeline dispatch | SkinnableTimelineScreen renderer dispatch | Current Native timeline or trusted compiled renderer | Registry only; actions delegate to Host/Core | Compiled renderer API | RETIRE_AFTER_PARITY | R-06, F-02 | CompiledSkinTimeline.kt:188-190 |
| ui/v2/CompiledSkinTimeline.kt：TimelineSkinContext/SkinnableTimelineScreen adapter | PowerCalApp Timeline call | Supplies timeline DTOs and callbacks to renderer, including Native recovery view | Callbacks delegate to Host/Core | Timeline projection and stable renderer context | KEEP_HOST | R-07, F-02 | CompiledSkinTimeline.kt:40-190; PowerCalApp.kt:2799 |
| ui/v2/PresentationSkinRuntime.kt：Registry 与页面 dispatch | PowerCalApp Month/Memo/AI/Tools/Navigation | Native、Compiled 示例或默认 renderer | registry 内存；动作交 Host/Core | Native page bodies、contexts | RETIRE_AFTER_PARITY | R-06, F-02–F-09 | PresentationSkinRuntime.kt:73-108, 184-208, 243-254, 325-346, 401-509 |
| PresentationSkinRuntime.kt：各 VisualContext.Native() | Native adapters/示例显式委托 | Native 完整页面回退；当前也承载日常功能 | 业务副作用在 Host/Core | Native Timeline/Month/Memo/AI/Tools | KEEP_RECOVERY | R-04, R-06, F-02–F-09, Q-03 | PresentationSkinRuntime.kt 各 context.Native() |
| ui/v2/RuntimeSkinCompose.kt、RuntimeSkinSchema.kt | manager 解析 v2 ui.json；screen surface 调 scene | 声明式 Compose 页面和组件 | action 到 Host callback；无直接 Store 能力 | v2 parser、RuntimeSkinSurfaces | RETIRE_AFTER_PARITY | R-06, F-02–F-09 | SkinPackageManager.kt:245；RuntimeSkinCompose.kt:45-82；RuntimeSkinSchema.kt:62-162 |
| ui/v2/RuntimeSkinSurfaces.kt | page composables | v2 Timeline/Month/Memo/AI/Settings/Deck/Overlay/Chrome/picker 及 skins | actions 到 Host；自身无持久化 | v2 bindings、Native slots、registry | RETIRE_AFTER_PARITY | R-05, R-06, F-02–F-09 | RuntimeSkinSurfaces.kt:25-305 |
| ui/v2/RuntimeSkinPickerHost.kt and SkinnablePickers.kt | v2 picker request | Host Compose date/time modal; confirm returns selected value | Cancel does not apply value | Android Compose picker and v2 schema | RETIRE_AFTER_PARITY | R-06, F-05, F-09 | RuntimeSkinPickerHost.kt:14-103; SkinnablePickers.kt:42 |
| ui/v2/SkinnableChrome.kt | PowerCalApp shell/notice | Native Chrome、Web root、compiled/v2 chrome、恢复提示 | recovery 交 PowerCalApp；自身不写 Store | WebSkinRoot、NativeChromeSkin、registries | KEEP_HOST | R-07, Q-03 | SkinnableChrome.kt:15-58 |
| ui/v2/WebSkinHost.kt：WebSkinRoot/decodeBridgeAction | active.web 分支、WebMessagePort | Web v3 页面在 Android WebView 运行并请求 Host 动作 | WebView 不直接持有 Context/PowerStore；Host 校验 capability/action | manifest、Host callbacks、Core APIs | KEEP_HOST | R-05, R-07, F-01–F-09 | WebSkinHost.kt:67-106, 128-186, 296-425, 496-690 |
| ui/v2/WebSkinEditBridge.kt：WebSkinEditService | PowerCalApp 创建；web edit/draft actions | 编辑、冲突、保存和切换检查点 | DraftReview/Event API 写草稿/检查点；正式提交另经确认 | DraftReviewFeatureApiV1、EventFeatureApiV1 | KEEP_HOST | R-07, F-05, F-06, Q-03 | WebSkinEditBridge.kt:429 起；prepareSkinChange():1708-1738 |
| Studio builder/validator 和 default-clear source | Studio CLI/Web editor | 编辑、预览、build/export/reimport | 改 Studio project 或包；不直写 App Store | 同一 v3 .pcskin 与 Host bridge | KEEP_DEFAULT_SKIN | R-05, S-01–S-07 | tools/skin-studio/build_default_clear.py；tests/test_default_clear.py |

混合文件按符号分类，不扩大成整文件删除许可。KEEP_TEST_COMPAT 只用于历史 parser、迁移测试和 fixtures，不表示旧 Runtime 日常可达。Native 页面通过 Skin 等价前不得退役；之后仍需 Q-03 验证最小 recovery 子集和 Host trusted UI。**Owner 已单独授权 R-03 清理应用私有 `files/skin-packs/` 中历史已安装皮肤包；不授权删除业务数据、Studio 工程、外部文件或 Git 工作区。**

### R-02～R-07 前置依赖与风险

| 任务 | 前置依赖 | 风险和约束 |
|---|---|---|
| R-02 | 本调用/副作用图、现有 Clear 包和 manager 行为 | Fresh install 当前 Native；保留 ID powercal-clear-default、工厂包校验和 Native recovery。先隔离 QA，不直接公开切换。 |
| R-03 | R-02 选择策略与新测试 | 分别处理 Native/Clear/第三方/损坏选择；清理受控应用私有目录中的旧 `.pcskin`，完成后仅有一个默认 Skin 与 Native 恢复；保护 Store/提醒/草稿/AI 数据；先在隔离 QA 完整验证。 |
| R-04 | R-03 旧选择收敛及旧包清理已验证；盘点 Native Tools 其余功能 | 移除正常产品的历史包枚举、导入/导出及候选入口；保留必要的 Host、备份/权限/提醒与 Native recovery。 |
| R-05 | R-02/R-03 行为已验证，R-04 Host 路径明确 | 清除 Clear app.js 的旧包选择/导入/导出/删除 daily entry；不提供历史皮肤保存或回选能力，不影响提醒、备份、编辑及 Host error feedback。 |
| R-06 | 对应 F/S 功能等价完成；用本图再核调用点 | 不可整文件删除：CompiledSkinTimeline 含共用 Timeline adapter，PresentationSkinRuntime 含 Native fallback，native-skin 有恢复/trusted UI；保留任务卡要求的测试 fixtures/parser tests。 |
| R-07 | R-02/R-03 迁移可靠；R-06 dispatch 范围明确；checkpoint 语义验证 | 覆盖缺包、坏包/未知 ID、偏好写失败、renderer crash 和脏编辑恢复。Activity 手势当前跳过 prepareSkinChange，Web 故障即使 checkpoint 失败仍回退；验证草稿可恢复及失败提示后才可关闭。 |

## F-01 Core / Host / Skin 有限责任边界审计（2026-11-04）

本节是源码责任审计，不是行为回归签核。没有确认新的用户可见故障；以下“边界风险”表示实现位置与 Owner 目标存在可定位的职责重叠，需要在对应的既有 F 任务中验证和收敛。不得据此提高 SSA-01～SSA-16 的状态，也不要求把整个 `:app` 迁入 `:core-api`。

### 当前调用图与已验证的接口分层

```text
MainActivity.setContent (MainActivity.kt:89-91)
  └─ PowerCalApp (PowerCalApp.kt:232-260; Host composition root)
       ├─ PowerCalCoreFeatureApiV1(existing PowerStore + Android ports)
       │    ├─ CalendarFeatureApiV1 / EventFeatureApiV1 / RecurrenceFeatureApiV1
       │    ├─ MemoFeatureApiV1 / DraftReviewFeatureApiV1 / AI / Reminder / Settings
       │    └─ existing Core services and Store (implementation behind versioned API)
       └─ SkinnableChrome → WebSkinRoot (SkinnableChrome.kt:26; WebSkinHost.kt:264)
            └─ WebMessagePort → decodeBridgeAction (WebSkinHost.kt:496-690)
                 → PowerCalApp Host action handler → feature API / trusted Android UI
                      └─ versioned DTO projection → WebSkinStateFeed (one-way) → default-clear app.js render*()
```

1. **日历与重复实例查询正确走 API：** `PowerCalApp.requestCalendarWindow` 调用 `calendarFeatureApi.queryWindow`（`PowerCalApp.kt:520-535`；契约 `CalendarFeatureApiV1.kt:110-123`）。Timeline/Month 投影调用共享 `displayOccurrences`，再通过 `RecurrenceFeatureApiV1.queryOccurrencesInWindow` 获取实例（`WebSkinTimelineProjection.kt:43`、`WebSkinMonthProjection.kt:71`、`GlassPresentation.kt:56-80`；契约 `RecurrenceFeatureApiV1.kt:85-108`）。投影核对 `eventSeq` 与 `revision` 后输出 `occurrenceKey`；Skin 只按收到的日期/标识渲染，不自行生成重复规则。此为 Core 查询 → Host 投影 → Skin 表现的正确路径。
2. **正式事件写入正确走 API 和 Host 授权：** Clear 发出的编辑动作经 Web bridge 解码和 Host 分发后，`WebSkinEditService.stageTimelineMove` 调 `EventFeatureApiV1.stageTimelineMove`（`WebSkinEditBridge.kt:713-745`；契约 `CoreFeatureApiV1.kt:290-299`）。正式编辑保存调用 `EventFeatureApiV1.saveFormalEvent`；删除由 Host 签发 Core 删除授权后再调用 `deleteFormalEvent`（`PowerCalApp.kt:3435-3448`；契约 `CoreFeatureApiV1.kt:320-338`）。Skin 不能取得 `PowerStore`、数据库、Android `Context` 或授权签发器（`WebSkinHost.kt:102-106`）。拒绝、冲突、审批及事务结果仍由 API/Host 返回给 Skin 呈现。
3. **Memo 完成状态分层正确：** Skin 的 `renderMemo` 只按 `completed` 过滤并发出 `toggleMemo` 请求（`app.js:898-918`、`app.js:245-255`）；Core `EventFeatureApiV1.setOccurrenceCompletion` 校验 event revision、occurrenceKey 与已观察状态后执行事务（`CoreFeatureApiV1.kt:257-266`）。本项确认 Skin 的过滤属于呈现；不代表分页责任已经审计通过，见 F01-R3。

### 有限边界风险与既有任务归属

| ID | 真实源码及当前行为 | 分类与风险（未确认为产品故障） | 应由谁承担 / 既有 API | 最小验证与唯一任务 |
| --- | --- | --- | --- | --- |
| F01-R1 | F-02 已收敛：WebSkinTimelineProjection.build 只输出业务身份和真实时间边界；默认 Skin 的 timelineLaneAssignments 负责视觉 side/lane（WebSkinTimelineProjection.kt:67-85；default-clear/source/web/app.js:693-722,788-812）。PowerCalApp 的 Web 状态和移动版本检查使用精确 CalendarFeatureApiV1 时间窗（PowerCalApp.kt:1982-1988,2308-2314）。 | 原 Host 预分配视觉 lane 已移除；本轮还复现并修复 WebView 激活时 Compose 记忆列表滞后、导致事件缺席或移动 revision stale 的路径。剩余时间轴完整多重叠/无障碍验收仍属 SSA-02 PARTIAL。 | 业务时间、eventSeq、revision、occurrenceKey 和移动合法性仍由 CalendarFeatureApiV1、RecurrenceFeatureApiV1、EventFeatureApiV1 提供或校验；仅布局归 Skin，不新增 API。 | WebSkinHostProjectionTest、TimelineDragWebViewInstrumentedTest.sameActivityFixtureLongPressMoveProducesEquivalentNativeAndClearDrafts、TimelineReturnViewportInstrumentedTest 及 API 35 实际 WebView 创建/移动路径均通过。证据：.local/android-test/reports/F-02-20261104/。状态：F-02 已完成。 |
| F01-R2 | `WebSkinMonthProjection` 在 Host 保存 `month/selectedDate` 并构造 42 日查询区间及每个 occurrence 的 `date`（`WebSkinMonthProjection.kt:14-21,27-78`）；Clear `renderMonth` 再依据 month/zone 计算展示格数、日期并把 `item.date` 关联到 DOM（`default-clear/source/web/app.js:850-896`）。 | **日历网格/日期映射存在 Host 与 Skin 两处呈现计算。** 月份导航、格子数量或 zone 映射发生差异时，Host 可返回实例但 DOM 仍可能漏显/错格；这是需要复验的边界风险，不是已确认故障。 | Core `CalendarFeatureApiV1` 负责事件窗口，`RecurrenceFeatureApiV1` 负责 occurrence 与身份；F-03 复验后让 Skin 拥有其月份格子/DOM 呈现。Host 保留输入校验、可信 DTO、eventSeq/revision/occurrenceKey。 | 以跨月每日重复 ACTIVITY 逐层比对 Core → Host JSON → Web state → DOM 的日期、owner/revision/occurrenceKey；执行换月、点选和 force-stop 重启。使用 `NativeClearMonthOccurrenceParityInstrumentedTest.kt`、`WebSkinHostProjectionTest.kt`、`WebSkinRecurrenceBridgeTest.kt`。**任务：F-03。** |
| F01-R3 | `PowerCalApp` 保存 `memoVisibleLimit`，用 `MemoProjectionPaging` 控制 visible count / load-more；再将分页后数据投给 Web（`PowerCalApp.kt:516-518,701-704,2334-2347`）。Skin `renderMemo` 负责 Open/Done 过滤和行 DOM（`default-clear/source/web/app.js:898-920`）。 | **纯展示分页状态由 Host 持有。** 页面结构和可见条数因此不能完全由 Skin 独立创作；目前未发现错误或数据丢失。 | `MemoFeatureApiV1.queryEntriesPage` 保持唯一数据/排序/完成状态来源；`EventFeatureApiV1.setOccurrenceCompletion` 保持完成事务。将“当前显示多少行/加载更多”作为 Skin 表现状态验证，不将筛选或完成规则下放为 Core 重复实现。 | 用超过 250 条并含重复 occurrence 的隔离数据验证首屏、续页、Open/Done、标题点击、完成/撤销和冷启动；Store 的 eventSeq/revision/occurrenceKey 逐项对照。使用 `ClearMemoProjectionInstrumentedTest.kt`、`WebSkinHostProjectionTest.kt`、`WebSkinBridgeTest.kt`。**任务：F-04。** |

### 源码责任清单（F-01 范围）

| 范畴 | 本轮定位 | 判定 |
| --- | --- | --- |
| Core 业务决策 | `core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/` 中 Calendar、Event、Recurrence、Memo、DraftReview、AI、Reminder 等版本化契约；运行时由 `PowerCalCoreFeatureApiV1` 连接现有 Store/Core 服务。 | 事件身份、revision、重复实例、范围编辑、事务、完成状态、提醒与提案审核须留在 Core/API。`PowerCalApp` 创建 API 并传入 Android ports 是 Host composition，不是 Skin 直接访问 Store。 |
| Host 平台/授权适配 | `MainActivity.kt:89-91`、`PowerCalApp.kt:232-260`、`WebSkinHost.kt:102-106,264-425,496-690`、`WebSkinStateFeed.kt:6-29`、`WebSkinEditBridge.kt:429-470,713-745`。 | 持有 Context/Store 构造、WebMessage/capability/action 校验、时区及页面状态、文件/权限/系统 UI、可信确认、把 API DTO 编成 Skin wire state。`WebSkinStateFeed` 只推送有版本的单向状态且没有写权限（`WebSkinHost.kt:294,385-402,451-456`）。继续保持不可向脚本暴露数据库或授权对象。 |
| Skin 展示/输入 | `default-clear/source/web/app.js` 的 `renderTimeline`、`renderMonth`、`renderMemo`（`693-748,850-920`），及 HTML/CSS 布局和手势。 | 页面树、视觉布局、卡片/网格排列、Open/Done 可视筛选、用户动作和显示格式归 Skin。Skin 只发送带身份/版本的请求并展示 API 结果；不得维护重复规则或自行提交正式业务数据。 |
| 待收敛交叉 | F01-R2 月历状态/网格映射；F01-R3 Memo 可见行分页状态。F01-R1 已由 F-02 收敛。 | 仅按上表映射到已有 F-03/F-04，设置对应可观测测试；F-01 不新增 API、不提高任何 SSA 状态。 |

重点调用核查结果：`PowerCalStores` 在 `PowerCalApp.kt:168-183` 创建并共享单一 `PowerStore`，Feature API 在 `PowerCalApp.kt:243-260` 装配；这属于 Android Host composition。审计到的 Web Host/编辑桥与 `native-skin` 页面源码没有直接调用 `PowerStore`、`DeckController`、`RecurrenceEngine` 或 `ReminderPolicy`；`WebSkinEditService.deck` 实际类型为 `DraftReviewFeatureApiV1`（`WebSkinEditBridge.kt:429-435`），重复实例由 `RecurrenceFeatureApiV1` 查询。提醒读取/协调经 `ReminderFeatureApiV1`（`PowerCalApp.kt:598,798-805,2323`），直接打开精确闹钟系统页是 Host 平台动作（`PowerCalApp.kt:2111,2570`）。时区由 Host 捕获并传入 API 查询/投影（`PowerCalApp.kt:240-241`）；AI 审核经 `aiProposals.assessReview`，发布审批由 Core API 签发后提交（`PowerCalApp.kt:335,1541,2614-2616`）。`WebSkinStateFeed` 的 `publish/connect/disconnect` 仅传输状态帧，不承载 action 或 Store 写入（`WebSkinStateFeed.kt:9-29`）。在本次有限扫描范围内，这些属于正确 Host/API 分层，没有发现需另立 Core 修复任务的直接调用。

### F-01 签核范围

- **源码审计：PASS**；至少三处具体边界风险及两条正确 API 路径已标出实际符号、行号、对应测试和既有任务。风险项不是已确认 FAIL。
- **产品功能、JVM、Android 模拟器、真机：本轮均未执行**；本任务是只读责任审计，设备等价仍由对应 F-02/F-03/F-04 验收。
- SSA-01～SSA-16 原有实现/JVM/模拟器/真机状态未改变；SEP 历史签核仍 INCOMPLETE，Product Cutover 仍 HOLD。

### F-02｜时间轴及导航：统一 Camera、窗口与事件交互

- 实现：PASS（本任务范围）。Skin 根据真实事件区间分配视觉 side/lane；Host timeline DTO 不再携带 presentation lane。Web 状态快照与移动 revision 校验从当前 Core CalendarWindow 取数，避免激活时使用滞后的 Compose 记忆列表。临时新建编辑器补齐 wire patch 所需 stateRevision，仍由现有 WebEdit/Core API 流程处理。
- JVM：PASS，完整 :app:testDebugUnitTest 为 967/967、0 failures/errors/skips；定向 Host projection 与 transient editor 回归合计 25/25 PASS。两条源码边界检查 PASS。
- Android API 35：PARTIAL evidence, 7/7 targeted instrumented executions PASS：Today/24h 恢复、左右时间翼独立滚动、月历选择日期创建、时间轴 ACTIVITY 创建、ALARM/DEADLINE 创建、Native/Clear 同一 ACTIVITY 移动草稿等价。设备：隔离 AVD PowerCal_ChatGPT_QA35_20261103，串号 emulator-5554。真机/OEM NOT TESTED。
- 构建与默认包：PASS；Debug APK SHA-256 878a70f8c5fe4411ad28a149a37b1335fab1104f0c6594e0c35afcc070d6cff2；APK 仅有一个 powercal-clear-default.pcskin，与构建源码字节一致，SHA-256 299e4bdd78c8936f6c15db0200172e659aaa5b0e0643f0d70c115966ac35a9f4。Lint 0 errors / 56 warnings；AndroidTest Kotlin 编译 PASS。
- 具体修复证据：新建 ACTIVITY 的 deck.patch 曾因 transient editor 快照缺少 stateRevision 被 wire decoder 拒绝；另一个 WebView 激活竞态让可见时间窗已有 Core 事件、Skin 状态却读取到空的记忆列表，并使移动检查读到旧 revision。相应 JVM 和实际 WebView 回归验证修复；Store 仅在显式确认后写入。
- 主要改动：PowerCalApp.kt、WebSkinEditBridge.kt、WebSkinTimelineProjection.kt、WebSkinHostProjectionTest.kt、WebSkinEditBridgeTest.kt、TimelineCreateContextWebViewInstrumentedTest.kt、TimelineDragWebViewInstrumentedTest.kt、默认 Clear app.js 与正式 .pcskin。完整门禁和设备日志位于 .local/android-test/reports/F-02-20261104/。
- 验收边界：SSA-02 与 SSA-03 总体状态均保持 PARTIAL；本任务结果不替代重复实例 DOM 全生命周期、完整重叠/无障碍、Studio 往返或真机验证。历史 SEP 签核仍 INCOMPLETE，Product Cutover 仍 HOLD。

### F-03 执行证据（2026-11-04）

- **任务状态：BLOCKED。** 未复现此前“Host 有 occurrence、DOM 不显示”的产品故障；F-03 指定的功能与设备检查通过，但完整 JVM 门禁两次被同一测试失败阻断。
- **可复验行为：** 隔离 API 35 AVD `PowerCal_ChatGPT_QA35_20261103`（`emulator-5554`）上创建 2026-10-31 起始的每日 ACTIVITY；Core 窗口/重复查询、Host JSON 和 WebView 月格/agenda DOM 对齐 11 月 2–5 日。每行保留 eventSeq、revision、原始 occurrence key；点击 11 月 3 日打开对应实例，选择 SINGLE_OCCURRENCE 后编辑器显示该槽位。切换 2026-10 再回 2026-11 后状态仍匹配。两次独立 prepare → `adb shell am force-stop` → restore 执行均产生新进程身份，并在浏览/打开之后保持测试 Store 快照不变。`WebSkinRecurrenceInstrumentedTest` 使用 Android trusted touch 的回归 1/1 PASS。
- **JVM/门禁：** `WebSkinHostProjectionTest` 定向 24/24 PASS；两个源码边界检查 PASS。两次默认发行变体全量运行均为 967 tests / 1 failure：`SystemCalendarImportTest.rejectedItemsArePersistedAsWarningsWithoutCalendarWrites` 在只读 `commitCount()` 查询发生 `SQLiteDatabaseLockedException`；该单测隔离重跑 1/1 PASS，故记录为全量门禁稳定性阻塞，根因未确认，不能将全量标 PASS。Debug APK、Lint（0 errors/56 warnings）、AndroidTest Kotlin 编译 PASS。一次带 QA application suffix 的全量命令不作为产品门禁结果；默认变体结果见报告日志。
- **包与改动：** 默认 APK SHA-256 `F5E5FA6FE4194AD213EF60D5933018422A818D0AFB97E7B68FD223B3F54E3522`；APK 仅含一份 `assets/powercal-clear-default.pcskin`，与源码逐字节一致，SHA-256 `299e4bdd78c8936f6c15db0200172e659aaa5b0e0643f0d70c115966ac35a9f4`。未修改产品运行时代码或默认皮肤；F-03 测试变更仅在两个 AndroidTest Kotlin 文件。真机/OEM NOT TESTED。
- **证据目录：** `.local/android-test/reports/F-03-20261104/`，含两次全量失败、单测复测、24 项定向 JVM、APK/Lint/compile、WebView trusted-touch 测试、两次冷启动 phase 日志及不同进程身份记录。
- **签核影响：** SSA-03 保持 PARTIAL；SEP 历史签核不变；产品 Cutover HOLD。后续可继续独立的 S-01；F-03 只有在全量 JVM 门禁干净后方可重新标记 PASS。

## Q-02 冻结行为重放记录（2026-11-05）

**Q-02 任务状态：BLOCKED（矩阵与本轮可执行重放已登记；SSA-08 的 Native→Clear 普通聊天等价仍缺有效结果）。** 本节是本轮实测索引，不改变原 SSA-01～SSA-16 状态，也不签核 SEP-01～SEP-16 全项。

### 本轮共同基线

- Git 分支 / 起始 HEAD：`codex/skin-single-default-closure` / `09eae6d4df1d4ae79556ec5f9f9879ccb3629f0e`。从 Clear Visual RC 构建提交 `447f3b6f7c8e2586cb787faa97ba9d27b9730cb5` 到起始 HEAD，`app/src/main`、`app/src/androidTest`、`app/src/test`、`tools/skin-studio` 均无差异；因此被拉取的安装包和本轮设备测试源码对应相同产品/测试代码。
- 设备：隔离 AVD `PowerCal_Wave2_QA35_20261104`，`emulator-5554`，Android 15 / API 35，启动完成；设备时区 `GMT`。每个 Native/Clear 对照在同一测试方法中用同输入初始化等价的隔离 Store；冻结日期/时区场景按方法夹具显式设定。
- 设备已安装 APK：`dist/powercal/PowerCal-Visual-Release-Candidate-2026-11-05.apk`，设备拉取 SHA-256 `66fa0941beda7ff5fce3dbbfa176d3152a002205faafaba5666e8ac0068617a3`；测试 APK SHA-256 `67dfa76aefba2536d5e6a6ff888eb4acaf8d1b628087701173c1326d42f2d02c`。两者分别与本地 Visual RC 和 AndroidTest APK 完全一致。
- 默认包：`powercal-clear-default`，`assets/powercal-clear-default.pcskin` 唯一一份；APK 与源码逐字节相同，SHA-256 `b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3`。`build_default_clear.py --verify --apk ...` 通过。
- 本轮直接执行 50 次 Android instrumentation 方法：47 次通过、3 次失败。Studio 权限用例的首次失败是 AVD 上既有 `POST_NOTIFICATIONS` 拒绝且 `USER_FIXED` 导致系统不再展示首次拒绝按钮；仅临时清除 `user-set/user-fixed` 标志后重跑通过，并恢复了原拒绝状态及标志。另一个 Native→Clear 聊天触摸对照连续两次在 WebView 收到 pointerdown 之前失败，均未进入 Clear 聊天 API；作为阻塞保留，不能用其失败断言为已确认产品缺陷或记为 PASS。
- 通过用例逐项比较 Core Store 的记录身份、revision、occurrenceKey、草稿/提交/完成状态和取消零写入；本轮所有测试使用隔离 Store。应用主数据库当前 SHA-256 与 Q-01 终态相同：`881b1b234520d24636d9816fe48681d0bcfb7a8b1f4d39a2c1513b33fefb9cf7`。本轮未在首个 Q-02 用例前另取一次主库哈希，因此该值记录为终态/既有基线相同，不宣称本轮独立的前后快照。
- 完整逐条日志、失败栈、实际安装 APK 与测试 APK 副本：`.local/android-test/reports/Q-02-20261105/`。

### SEP 母项逐项重放与剩余边界

| SEP / 新矩阵映射 | 本轮用户动作 → 正式 API；Core Store 对照 | 默认 Skin / Native 可观察结果 | Q-02 结果与仍未闭合项 | 本轮证据 |
| --- | --- | --- | --- | --- |
| SEP-01 → SSA-02,15,16 | 今日、缩放/复位、垂直平移、两侧翼滚动；从时间轴/月份创建 ACTIVITY、ALARM、DEADLINE，拖动移动；Calendar/Event API；Camera、eventSeq/revision、草稿与取消计数同夹具比较 | Native 与 Clear Camera/记录语义匹配；Clear 触摸创建/移动流程通过 | PASS（切片）；完整重叠命中、TalkBack、真机仍待 Q-03/Q-04 | `TimelineReturnViewport*`、`TimelineCreateContext*`、`TimelineDrag*` 日志 |
| SEP-02 → SSA-03,16 | 月份/日期导航、选中重复实例打开编辑；Calendar/Recurrence API；日期、owner、occurrenceKey、revision、写入计数对照 | Native/Clear 日期转移与实例编辑目标匹配，无导航 Store 写入 | PASS（切片）；完整跨进程/Studio 月历往返和真机仍未签核 | `NativeClearMonthOccurrenceParity*` 日志；F-03 另为 PREVIOUS PASS |
| SEP-03 → SSA-04,16 | Memo 创建、编辑、重开、单实例完成/撤销；Memo/Event API；completion key、revision、提交计数对照 | Clear 列表/Open/Done 投影与 Core 状态匹配；Native/Clear 完成结果匹配 | PASS（切片）；Studio 绑定和全场景仍未签核 | `WebSkinAllKindsTrustedWrite*` Memo 日志 |
| SEP-04 → SSA-05,16 | 打开/关闭四类新建表单，分别创建四类事件；Event/Draft API；无改动零序列/零提交，有改动只产生一个草稿 | Native/Clear 同夹具四类记录和版本一致；无修改关闭零持久化写入 | PASS（切片）；Studio 表单完整创作和真机仍未签核 | `WebSkinAllKindsTrustedWrite*`、`NewEventEditorLifecycle*` |
| SEP-05 → SSA-05,16 | 四类正式编辑、类型转换；Event API；revision、身份、提交历史一致 | Native/Clear 编辑结果匹配，可信 Host 保存后才有正式版本 | PASS（切片）；并不等于 Studio 编辑表单完整签核 | `WebSkinAllKindsTrustedWrite*` 编辑日志 |
| SEP-06 → SSA-03,06,16 | Single / This-and-future / Whole；Recurrence/Event API；原系列、occurrenceKey、revision、拆分和提交数比较 | Alarm 与日期型 Deadline 三种作用域、重复 Memo 单实例完成切片通过 | PARTIAL；设备 DST gap/fold 与日期边界未在本轮验证，SSA-06 保持 PARTIAL | `WebSkinRecurringScopeParity*`、Memo recurrence 日志；F-06 为 PREVIOUS PASS |
| SEP-07 → SSA-05,07,11,16 | 触摸删除、取消、过期版本冲突、恢复已删正式记录；Event/DraftReview API；取消零写入、身份复用、revision 对照 | Native/Clear 删除等价；Host 冲突刷新；恢复前仍需明确批准 | PASS（切片）；更广泛恢复/OEM 仍未签核 | `WebSkinFormalDeleteTouch*`、`ConflictReview*`、`WebSkinAllKindsTrustedWrite*` 删除日志 |
| SEP-08 → SSA-07,16 | Memo deck 编辑/flush/reopen/publish；编辑器脏状态跨 force-stop；DraftReview API；待处理草稿 ID/版本/Store 计数对照 | 新进程恢复原草稿及 Memo 完成实例，未重复创建正式记录 | PASS（切片）；所有队列边界、撤销与 Studio 结构仍未签核 | `WebSkinAllKindsTrustedWrite*`、`clear-process-recovery-{prepare,restore}.log` |
| SEP-09 → SSA-08,15,16 | Local-stub chat、失败后显式重试、附件同意与聊天历史冷启动；AI API；会话/Store 及未授权数据不外泄 | Clear Stub 聊天历史 force-stop 恢复通过；Native/Clear 失败重试和附件比较通过。普通聊天 Native→Clear 对照在两次执行中，AI tab 可见但测试未收到可信 pointerdown，未到达发送动作/API | **BLOCKED**；保留原日志，不断言产品缺陷，也不签等价；默认 Clear 普通聊天触摸对照是 Q-02 唯一未解 P1 观察项 | `PowerCalAiProposalReviewWebViewInstrumentedTest-nativeAndClearRealChatSend*`（两次失败）；`clear-chat-cold-cycle-{0,1}.log`（通过）；Studio local-stub chat 测试通过 |
| SEP-10 → SSA-08,11,16 | 提案 CREATE/STAGE、REPLACE/DELETE scoped approval；AI/DraftReview/Event API；批准前后 formal/draft/commit 快照比较 | Native/Clear 生成待审草稿、审批后写入及拒绝未写入结果相符 | PASS（切片）；live provider 与全套恢复边界仍 NOT TESTED | 两个 `nativeAndClear*Proposal*` AI 日志及 local stub 证据 |
| SEP-11 → SSA-09,10,16 | 通知拒绝/提醒入口、精确闹钟设置跳转、日历权限拒绝/空 provider 授权、AI 设置、ICS 选择导入草稿、篡改备份拒绝；Reminder/Settings/ImportExport API + Host SAF | 权限拒绝、取消选择、hash 错误均未造成 Core Store 写入；ICS 形成等价审核草稿 | PARTIAL；本轮未写系统 Calendar provider；F-08 的系统日历导入为 PREVIOUS PASS；OEM 实际提醒投递 NOT TESTED | 7 个 `ClearSettingsOptionsInstrumentedTest` 日志；F-08 `.local` 报告 |
| SEP-12 → SSA-09,15,16 | Settings 外部权限/Picker 往返与取消；Android Host/SAF；Store 快照不变 | Clear 与 Blank Studio 的权限/文件取消反馈可观察，取消零写入 | PARTIAL；Back/IME 部分沿用 F-09 PREVIOUS PASS；TalkBack 与物理设备待 Q-03/Q-04 | Settings、Studio 权限日志；F-09 报告 |
| SEP-13 → SSA-01,13,16 | 工厂包解析、注册和选择迁移；`SkinPackageManager`；Q-01 状态矩阵、APK 资产数/字节哈希 | 当前 Visual RC 实际只含一份 Clear；Q-01 对 Fresh/Native/unknown/损坏选择及恢复执行 24 方法 | PARTIAL；真实 package uninstall/reinstall 未测试，Q-01 只验证隔离安装状态矩阵 | Q-01 报告（PREVIOUS PASS，同 app/test APK SHA）；本轮 APK 包校验 |
| SEP-14 → SSA-01,14,16 | 脏编辑跨进程恢复、损坏包冷启动 Native 回退；Host checkpoint/recovery；事件、草稿、eventSeq/revision 前后对照 | Q-02 真实 force-stop 后恢复原草稿；Q-01 对损坏工厂/已选包恢复 Native 且 Store 快照不变 | PASS（所测恢复切片）；checkpoint-I/O 注入故障及完整 SEP 签核仍未覆盖 | Q-02 process recovery logs；Q-01 recovery logs；R-07 PREVIOUS PASS |
| SEP-15 → SSA-12,13,16 | Blank Studio 五页、组件树、四类表单、Host action/资源操作及 Force-stop 后重装载 | Studio 生成包 Android 五页与 Host API 动作通过；包与项目往返 Python/Browser 使用 S-06/S-07 PREVIOUS PASS | PASS（所测创作/运行切片）；自定义视觉稿未获，Visual RC 保持 Clear fallback；全量视觉/真机仍未签核 | 4 个 `StudioFromBlankFullRuntimeInstrumentedTest` 日志（权限用例控制重置后通过）；S-06/S-07 报告 |
| SEP-16 → SSA-05,07,08,10,11,16 | 所有正式写入/删除/AI 提案审批和冲突确认；Event/DraftReview/AI API；批准前零正式写入、批准后匹配身份/revision | Clear 与 Native 在四类事件、删除/恢复、提案作用域对照中匹配 | PARTIAL；SEP-09 普通聊天触摸等价未闭合，因此不签最终业务等价 SSA-16 | trusted-write、delete、conflict、AI proposal 测试日志 |

### 本轮失败、复验与状态裁定

- 50 个独立 instrumentation 日志：47 次 `OK (1 test)`；3 次失败执行。失败类型只有两类：首次 Studio 权限测试受 `USER_FIXED` 前置状态影响（受控清标记后通过）；Native→Clear 普通 AI chat touch comparator 两次在同一位置断言失败，均无 API 请求。其他四条 AI Native/Clear proposal/error/attachment 对照及 Clear chat 冷启动历史回放通过。
- Android 主应用数据库终态 SHA 与 Q-01 基线相同；Q-02 用例中的 Native/Clear 隔离 Store 快照均按测试断言比较。主要 app selection 为 `powercal-clear-default`；通知权限测试结束后恢复为原来的 denied/user-set/user-fixed。
- 本轮没有运行全量 JVM/Gradle：当前 Debug code 与 971/971 JVM / Source Boundary / Lint / AndroidTest compile 的已验证代码树相同，且本任务未修改产品或测试源文件。该既有门禁标记 PREVIOUS PASS，非 Q-02 本轮运行结果。
- `SSA-01～SSA-16` 原状态全部保留；真机/OEM、实际系统闹钟投递、TalkBack、SEP 整体签核仍未完成；Product Cutover 继续 HOLD。
- 历史多皮肤选择/商店/安装包导出产品需求维持 `RETIRED-BY-OWNER`；本轮只验证隔离 QA 私有目录迁移，不接触真实用户文件，不影响 Native 恢复。


### F-03 闭环复验（Wave 2，2026-11-04）

- **任务状态：PASS。** 首轮两次 full-suite 锁失败来自测试完成信号不完整：测试等待 `migrations.finished_ms` 后即开第二 SQLite 连接，彼时系统日历导入协程还要执行末尾 reload/reconcile。`SystemCalendarImportTest` 现等待导入完成后的 Host notice，再读取 commit 数；没有移除 Store 断言、增加睡眠或修改生产逻辑。具体锁持有连接未直接埋点，详见 `docs/issues/PC-ISSUE-QA-SYSTEM-CALENDAR-IMPORT-ROBOLECTRIC-CONTENTION.md`。
- **当前 JVM：PASS。** 定向回归 1/1；全量 `:app:testDebugUnitTest` 967/967，140 XML suites，0 failures/errors/skips。`SystemCalendarImportTest` 4/4，`WebSkinHostProjectionTest` 24/24。两个源边界、Debug APK、Lint（0 errors/56 warnings）、AndroidTest Kotlin 编译均 PASS。
- **当前 Android API 35：PASS（F-03 指定范围）。** 新隔离 AVD `PowerCal_Wave2_QA35_20261104` / `emulator-5554`。Native/Clear 月历同数据集方法 3/3 PASS；两个独立 UUID 均完成 prepare → force-stop → restore，跨月 ACTIVITY 的 Core/Host/DOM 日期和 occurrence key 对齐、进程身份变化、Store 未变；`actualWebViewMonthOccurrenceOpenPreservesOriginalSlotForSingleEdit` 1/1 PASS。测试结果与冷启动输出保存在 `.local/android-test/reports/F-03-20261104-wave2/`。
- **产物与范围：** APK SHA-256 `F5E5FA6FE4194AD213EF60D5933018422A818D0AFB97E7B68FD223B3F54E3522`；默认 `.pcskin` SHA-256 `299E4BDD78C8936F6C15DB0200172E659AAA5B0E0643F0D70C115966AC35A9F4`。产品运行时代码、冻结 Core 行为和默认包均未修改；仅补足测试同步等待。真机/OEM NOT TESTED；SSA-03 仍 PARTIAL；SEP 历史签核 INCOMPLETE；Product Cutover HOLD。

## Q-03 recovery, layout, and accessibility replay (2026-11-05)

**Status: BLOCKED for complete accessibility sign-off; the runtime recovery and available API 35 layout checks passed.** This evidence does not change any original SSA-01–SSA-16 status. Product Cutover remains HOLD.

- Current Release Candidate (`EC27EBE7…A8592C340`, code source unchanged from R-07) was installed by `adb install -r` on isolated AVD `PowerCal_Wave2_QA35_20261104` / `emulator-5554`, API 35. The app database digest was `881b1b234520d24636d9816fe48681d0bcfb7a8b1f4d39a2c1513b33fefb9cf7` before and after; no app data clear/uninstall occurred.
- **9/9 current instrumentation methods PASS:** small-screen event hit/accessibility; 2× system-font reachability; three Web renderer termination/uncaught-skin-error recovery checks; two corrupt-package cold-recovery phases; and two dirty-editor cold-process restart phases. Renderer/package failure reached Native without WebView, the process tests restored the synthetic pending draft, and Core Store snapshots stayed unchanged. Synthetic test DB/marker/package were removed by the tests.
- TalkBack was enabled in system Settings for one bounded system-UI check. The documented Alt+Right shortcut was attempted through ADB keyboard injection; the accessibility focus remained visually on the same Back control; screenshots before/after were byte-identical. UiAutomator probes were excluded because they turned touch exploration off. A PowerCal traversal was not attempted because system traversal was not observable. Original accessibility state was restored and verified; TalkBack focus order remains **NOT TESTED**, with F-09 still BLOCKED. Low-end physical hardware is also NOT TESTED.
- Exact logs and screenshots: `.local/android-test/reports/Q-03-20261105/manifest.md`. Current app/test APK hashes, device details, commands, and cleanup evidence are recorded there.
- No product or test source changed; prior F-09 navigation/IME and R-07 boot results are referenced only as PREVIOUS PASS. No SSA row status was raised.
