# 单一默认皮肤验收矩阵（G-05）

基线：`ac8d1555a1c177e600e72e4edb5b450189f1fc2c`。本文件是验收定义，不是产品已完成声明。权威目标及任务见 [R2 路线图](../POWERCAL_SINGLE_DEFAULT_SKIN_EXECUTION_PLAN_2026-11-03_R2.md)；原始母项及历史证据见 [SEP 矩阵](../SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md)，冻结语义见 [Core 契约](../CORE_CONTRACT_FREEZE_2026-10-21.md) 和 [Core API v1](../CORE_FEATURE_API_V1.md)。Core 拥有业务、数据与授权；正常 UI 属于唯一默认 Skin；Native 是安全恢复；Studio 从空白创作完整皮肤；分离验收后才通过 Studio 使用 Claude 设计稿美化。

## 状态和签核规则

PASS 仅在本项全部必要功能、Studio、模拟器、真机/OEM 证据及结果对照通过并可复现时使用；PARTIAL 为部分实现或证据；FAIL 为已证实不符合；BLOCKED 为明确前置/外部阻塞；NOT TESTED 为未执行；RETIRED-BY-OWNER 为取消的旧产品要求。各行状态是本矩阵的审慎评估，不改写原 SEP。实现、JVM、Android、PHYSICAL 分开登记；本次 Android/PHYSICAL 交互均 NOT TESTED，旧设备结果仅作带来源的 PREVIOUS PASS，须在目标 SHA 重放。JVM 栏 PARTIAL 表示有本轮全量门禁和相关测试文件，非整项语义签核。没有对全部历史 Issue 做无失败断言；下列未确认全项 FAIL。签核记录必须含测试 ID、源码 SHA、APK/皮肤 SHA、命令、设备/API/OEM、输入、Store/界面结果及日志，失败与跳过不得计为通过。

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
- ANDROID QA：NOT TESTED（本轮）；重放多重叠、缩放、双击重置、Today 和命中并比较 Store 不变。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/CalendarFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/TimelineDragWebViewInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：F-01, F-02, S-02, S-03, Q-02, Q-03。

### SSA-03｜月历、日期与重复实例

- SOURCE：SEP-02, SEP-06；冻结契约与对应原 SEP 行。当前证据：旧 SEP-02 有配对实例及翻月 PASS；完整生命周期未签核。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/RecurrenceFeatureApiV1.kt；app/src/main/java/dev/powerfulcalendar/core/api/v1/CoreRecurrenceFeatureApiV1.kt。
- DEFAULT SKIN：月导航、日期选择、实例列表与日期创建入口。用户动作 → API → Core → 可观察结果：选择月份和日期并打开重复实例 → CalendarFeatureApiV1.queryWindow / RecurrenceFeatureApiV1.queryOccurrenceByKey → 保留原始实例键与选中日期 → 日历和编辑器显示同一实例。
- STUDIO：创建月网格、实例组件及日期绑定。
- ANDROID QA：NOT TESTED（本轮）；切月、选择日期、打开实例并比较键、表单和 Store。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/RecurrenceFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/NativeClearMonthOccurrenceParityInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：F-03, F-06, S-03, Q-02。

### SSA-04｜Memo 和待办

- SOURCE：SEP-03；冻结契约与对应原 SEP 行。当前证据：旧 SEP-03 有实例完成与恢复切片；全覆盖未签核。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/MemoFeatureApiV1.kt；app/src/main/java/dev/powerfulcalendar/core/api/v1/StoreMemoFeatureApiV1.kt。
- DEFAULT SKIN：未完成统一查询含四类，未定日期不伪装今天；日期范围 Memo 是一个完成实例。用户动作 → API → Core → 可观察结果：打开未完成列表并完成、撤销重复实例 → MemoFeatureApiV1.queryEntries / EventFeatureApiV1.setOccurrenceCompletion → 按原键修改完成状态 → 计数与 Open/Done 列表更新。
- STUDIO：创建列表、筛选、徽标和完成动作绑定。
- ANDROID QA：NOT TESTED（本轮）；完成并撤销后重启，比较实例键、版本、计数和列表。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/androidTest/java/dev/powerfulcalendar/ui/v2/ClearMemoProjectionInstrumentedTest.kt；app/src/test/java/dev/powerfulcalendar/core/api/v1/EventFeatureApiV1Test.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：F-04, S-02, S-03, Q-02。

### SSA-05｜四类事件创建、编辑、删除和恢复

- SOURCE：SEP-04, SEP-05, SEP-07, SEP-16；冻结契约与对应原 SEP 行。当前证据：旧 SEP-04/05/07/16 有配对切片；恢复与全流程未签核。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt；app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt。
- DEFAULT SKIN：四类精度、类型转换、验证、删除和恢复入口。用户动作 → API → Core → 可观察结果：分别新建、编辑、删除并恢复四类事件 → EventFeatureApiV1.openNewEvent/openFormalEvent/saveNewEvent/saveFormalEvent/deleteFormalEvent 与 DraftReviewFeatureApiV1.beginRestore/requestRestore → 校验版本、事务及授权 → 表单、冲突与结果可见。
- STUDIO：创建四类表单、验证提示和结果绑定。
- ANDROID QA：NOT TESTED（本轮）；每类比较审批前零正式写入、审批后记录及恢复的身份和版本；取消与过期版本不写入。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/EventFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinAllKindsTrustedWriteInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：F-05, S-02, S-03, Q-02。

### SSA-06｜重复规则三作用域与时区

- SOURCE：SEP-06；冻结契约与对应原 SEP 行。当前证据：旧 SEP-06 PARTIAL；三作用域全量设备比较未签核。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/RecurrenceFeatureApiV1.kt；core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt。
- DEFAULT SKIN：明确三作用域、日期精度与冲突提示。用户动作 → API → Core → 可观察结果：编辑单次、此后和整个系列 → EventFeatureApiV1.selectFormalRecurrenceScope / saveFormalEvent 与 RecurrenceFeatureApiV1.queryOccurrencesInWindow → 原键、例外、截断与兼容迁移正确 → 提示作用域及结果。
- STUDIO：创建作用域控件和事件绑定。
- ANDROID QA：NOT TESTED（本轮）；验证跨午夜、日期上下界、DST 缺口跳过且消耗槽位、折叠取较早偏移及单次折叠。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/RecurrenceFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinRecurringScopeParityInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：F-06, S-03, Q-02。

### SSA-07｜草稿、审核及发布

- SOURCE：SEP-08, SEP-07, SEP-16；冻结契约与对应原 SEP 行。当前证据：旧 SEP-08 有局部设备证据；冲突与恢复全量待验。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/DraftReviewFeatureApiV1.kt；app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt。
- DEFAULT SKIN：草稿卡片、搜索、替换比较、冲突和恢复结果。用户动作 → API → Core → 可观察结果：创建、修改、选择、丢弃/撤销并审核发布草稿 → DraftReviewFeatureApiV1.createDraft/editCurrentDraft/flush/select/commit/undoDiscard → 校验版本和请求 ID 后事务写入 → 卡片、冲突及收据更新。
- STUDIO：创建卡组及数据/动作绑定并重建。
- ANDROID QA：NOT TESTED（本轮）；批准前检查正式记录不变；批准后比较草稿、记录、收据与重启恢复。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/DraftReviewFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/DraftDeckSearchWebViewInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：F-05, S-02, S-03, Q-02。

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
- ANDROID QA：NOT TESTED（本轮）；隔离数据验证取消、重复导入、错误哈希与成功恢复的 Store 快照。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/ImportExportFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/ClearSettingsOptionsInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：F-08, F-09, S-03, Q-02。

### SSA-10｜提醒、重启和时区变化

- SOURCE：SEP-11, SEP-16；冻结契约与对应原 SEP 行。当前证据：CORE_FREEZE_VERDICT 有旧 API 35 重启切片；同时响铃语义未决，REC-P1-086 OEM 阻塞。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/ReminderFeatureApiV1.kt；app/src/main/java/dev/powerfulcalendar/core/api/v1/AndroidReminderFeatureApiV1.kt。
- DEFAULT SKIN：提醒开关、权限与失败状态独立显示。用户动作 → API → Core → 可观察结果：设置提醒并重启或改变时区 → ReminderFeatureApiV1.queryPermissions/queryEvent/reconcile 与 Host 调度 → 按冻结规则重建，响铃不隐式完成 → 皮肤显示状态及拒绝。
- STUDIO：创建提醒控件及权限状态绑定。
- ANDROID QA：NOT TESTED（本轮）；隔离模拟器验证准点、10 分钟宽限边界、重启、时区/DST 及完成状态不变。
- PHYSICAL：BLOCKED：REC-P1-086 尚缺物理/OEM 提醒证据；真机验证权限、重启、时区及厂商省电。
- 测试入口：app/src/test/java/dev/powerfulcalendar/core/api/v1/ReminderFeatureApiV1Test.kt；app/src/androidTest/java/dev/powerfulcalendar/reminders/ReminderAlarmManagerDeliveryInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 BLOCKED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：F-08, Q-02, Q-04。

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

- SOURCE：SEP-15；冻结契约与对应原 SEP 行。当前证据：G-04 Python 68/68 和浏览器源码往返只覆盖部分；从零完整 Android 验收未执行。
- CORE API：tools/skin-studio/skin_studio.py；tools/skin-studio/v3-studio.js；core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt。
- DEFAULT SKIN：首页、四类表单、草稿、AI、设置、恢复等完整表现。用户动作 → API → Core → 可观察结果：建立空白工程、添加页面/组件/资源/绑定并预览导出 → Studio 项目操作及 Host 绑定到既有 Core API → 业务由 Core 执行 → 导出包在 Android 完成主要用户流程。
- STUDIO：必须从空白 GUI 创作结构、绑定和资源；保留自定义源码。
- ANDROID QA：NOT TESTED（本轮）；安装该确切导出包并执行创建、审核、导航、恢复，比较 Store 和界面。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：tools/skin-studio/tests/test_project_ops_v3.py；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/StudioAuthoredSkinBridgeInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：S-01, S-02, S-03, S-04, S-05, S-06, Q-02。

### SSA-13｜包编辑、构建、导出和重新导入

- SOURCE：SEP-13, SEP-15；冻结契约与对应原 SEP 行。当前证据：G-04 builder 与浏览器往返 PASS；旧 SEP-13 母项仍 NOT TESTED。
- CORE API：tools/skin-studio/skin_studio.py；tools/skin-studio/build_default_clear.py；Host SkinPackageManager 校验包。
- DEFAULT SKIN：默认皮肤所有页面均可编辑及重建。用户动作 → API → Core → 可观察结果：导入默认包，编辑组件、嵌套资源和绑定 → Studio build/validate/import/export → 保留 manifest、未知字段与文件字节 → 预览和 Android 显示编辑结果。
- STUDIO：GUI 和版本化 API 往返，自定义资源、能力请求不丢失。
- ANDROID QA：NOT TESTED（本轮）；记录导出包哈希，重新导入并安装该确切包，触摸绑定核对结果。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：tools/skin-studio/tests/test_default_clear.py；tools/skin-studio/tests/test_v3_source_editing.py。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：S-04, S-05, S-07, Q-01。

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

- SOURCE：SEP-01, SEP-09, SEP-12；冻结契约与对应原 SEP 行。当前证据：旧 SEP 有部分 Native 小屏和 Back 切片；默认 Skin TalkBack/真机未签核。
- CORE API：core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt；Host 处理 Android 窗口和权限。
- DEFAULT SKIN：TalkBack 名称、可点击区域、缩放、IME 跟随与返回顺序。用户动作 → API → Core → 可观察结果：在大字体、小屏和键盘打开时导航、编辑并返回 → Host 窗口/Back 与 EventFeatureApiV1.editorSnapshot → 无额外 Store 写入 → 焦点、标签、控件和键盘不遮挡。
- STUDIO：可创建语义标签、焦点顺序和响应式布局。
- ANDROID QA：NOT TESTED（本轮）；使用 TalkBack、系统字体放大、窄屏与真实 IME 检查焦点、命中、遮挡和无意写入。
- PHYSICAL：NOT TESTED（本轮）；在真机重放主要动作，记录系统、权限、界面及 Store 结果。
- 测试入口：app/src/androidTest/java/dev/powerfulcalendar/ui/v2/TimelineSmallScreenInstrumentedTest.kt；app/src/androidTest/java/dev/powerfulcalendar/ui/v2/AiChatImeInstrumentedTest.kt。
- 状态：实现 PARTIAL；JVM PARTIAL（G-04 门禁与关联测试，不等于本项签核）；模拟器 NOT TESTED（本轮，历史切片见 SOURCE）；真机 NOT TESTED；总体 PARTIAL。
- 剩余及签核：按上述动作逐项获得目标构建的 Core/界面结果、Studio 往返、模拟器和真机证据，核对冻结契约后签核。后续任务：F-09, S-02, S-04, Q-03, Q-04。

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

**RETIRED-BY-OWNER：** 多皮肤目录、候选主题/皮肤商城及并行日常 UI 的产品要求被单一默认皮肤裁决取代；不是删除授权。旧 parser、安装包验证、版本迁移、候选/第三方测试夹具仍用于兼容与安全回归。用户已安装的皮肤文件、选择、草稿及历史设计资料必须保护；迁移需验证原文件可访问、无 Store 丢失及可恢复。Native 仍为安全恢复，不随目录入口退役。R-03 验证迁移后，R-04/R-05/R-06 才处理日常入口；正式切换需 Q-01～Q-05 及独立授权，安全确认无引用的清理属于 Q-06。任何旧文件或工作区均不可因本标记自动删除。

## 最短关键路径

R-01 盘点入口 → R-02/R-03 定义并验证启动及历史迁移 → R-04～R-07 收敛日常入口和恢复 → F-01～F-09 逐项完成 Core/Host 与默认 Skin 等价，同时 S-01～S-05 完成可编辑结构、绑定、预览及包往返 → Q-01～Q-04 完成新旧安装、全流程、恢复及真机/OEM 证据 → S-06 从空白项目创作完整皮肤并在 Android 运行，S-07 验证默认源码全量再编辑 → Q-05 独立授权后切换；Q-06 安全清理。视觉美化仅在完整分离与 Studio 创作验收之后进行。顺序表示依赖关系，不能以技术基线代替正式签核。
