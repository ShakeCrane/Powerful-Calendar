# PC-ISSUE-146 — AI 提案动作与批次授权范围回归

日期：2026-10-24

优先级：P1，PC-ISSUE-002 的本地审核交互补充

状态：**指定的 Compose 行为通过；PC-ISSUE-002 仍为 PARTIAL / BLOCKED，核心冻结仍 HOLD**

## 发现与根因

重建验收矩阵中的 `REC-P1-052`（提案卡提供同意、写入草稿、拒绝、补充细节）和 `REC-P1-053`（批次审核显示准确范围和数量）仍为 NOT TESTED。现有实现包含这些交互，但相关审核内容嵌在 `PowerCalApp`，没有一对一的 Compose 行为回归，不能用全量 Gradle 数量代替签核。

根因是审核视图没有独立的可测试边界。本 Issue 将批次授权审核提取为 `AiBatchAuthorizationReview`，保留原有文案、样式、提示和授权流程；授权动作接收审核时的固定草稿快照。新增 API 35 Robolectric Compose 测试，直接验证可见内容、用户动作回调、范围和授权快照。此次未改变数据库或 AI 请求行为。

## 验收结果

### `REC-P1-052` — 提案审核动作

- CREATE 与非 CREATE 分组均提供“批量同意”“写入草稿”“批量拒绝”“补充细节”四种动作。
- 回调仅包含当前分组中可处理提案的精确索引；被阻止的提案不进入动作范围。
- 忙碌期间四种动作均禁用。

结果：**PASS（限定为本地 Compose 行为）**。未声称真实模型生成或服务商可用性通过。

### `REC-P1-053` — 批次范围审核

- 审核内容显示总草稿数、每种操作的数量，以及每项事件序号、操作和标题。
- 显示正式删除的不可撤销提示。
- 只有明确点击授权后才向父级回调审核快照；回调中的 `draftId`、`draftVersion` 与被审核项目逐项一致。
- 取消不授权；忙碌时确认按钮禁用。

结果：**PASS（限定为本地 Compose 行为）**。没有执行 `PowerStore.beginAiBatch`、模型请求或正式日历写入。

## 验证证据

- 定向测试：`AiReviewComposeTest` **4/4 PASS**。日志 `.local/android-test/reports/freeze-20261024/pc-issue-146-targeted-r5.log`，SHA-256 `74F1BF71ED2361EA7602281AB38E816B220AC604B9536D7C28C5603BE974A99B`。
- 定向 JUnit XML：`app/build/test-results/testDebugUnitTest/TEST-dev.powerfulcalendar.ui.v2.AiReviewComposeTest.xml`，SHA-256 `829D0C7E470FF741E84144736D1912F83112A7738D657915EA72CB8A1B6A71E5`。
- 完整命令：

      .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain

- 结果：**BUILD SUCCESSFUL**；482/482 JVM 测试、62 个测试套件，0 failures / 0 errors / 0 skipped；源边界检查、Debug APK、AndroidTest APK 构建通过；Lint 0 errors / 58 warnings。
- 完整 Gradle 日志：`.local/android-test/reports/freeze-20261024/pc-issue-146-full-gradle-final.log`，SHA-256 `0386A8AAFD604B8C2992EEDB79F79DFA1767E2436204232880D622CE9CD27155`。
- Debug APK SHA-256：`D42540FF97CEC67B4672C7F2474920B85CD0C78D905EF2901A79BF27FFDA58C7`；AndroidTest APK SHA-256：`D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A`。
- 结构校验结果（只校验矩阵结构，不是产品行为测试）：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r12-pc-issue-146.json`，SHA-256 `FAA809C1589F8C49F5104BD2A1F30ACEEDD8BE4A6944F8BAA615783A44D6277F`；台账 SHA-256 `224B1F1DD2D7F85ACA8E0395AA3C19EE87967CFD583C03E58589835F7BB1481E`。结果为 182 PASS / 0 FAIL / 8 BLOCKED / 13 NOT TESTED / 6 SPEC UNRESOLVED；`product_behavior_tested=false`。

测试为 Robolectric API 35 Compose 虚拟测试。本次 shell 的 PATH 与标准用户 SDK 目录均未找到 ADB/emulator 可执行文件，因此未运行独立 AVD 或实体设备，也未请求 AI 服务商。测试开发期间的失败均保留：首轮漏传 mode 参数导致编译失败（日志 SHA-256 `5BFB2CC5F468EE025F823F9D5E80690D36F68FDCFDF31967F530D9B092EA4A27`）；第二轮测试断言导入失败（`B86C890EC843DED9FF28C5767EB0CA4D360A5776059CF79113F5571E118B9CD3`）；随后两轮因 Compose 测试未滚动至目标按钮而未触发对应回调（`FF1EC639BC9E0F0323BAEBDCA9094005BB76C021B967C0781151C83ADFC7974B`、`4B3B09A0C9494235B184F7193B7C00133EF875E586DE9979B6D230C14969E9AA`）。修正实现/导入与测试滚动步骤后，定向 4/4 和全量 Gradle 均通过；这些早期失败没有被计入通过结果。

## 剩余限制

PC-ISSUE-002 的免费模型结构化阶段曾返回 HTTP 429/503，仍未闭环。已授权 DeepSeek V4.1 Flash 的 live 两阶段草稿结果仍只适用于 PC-ISSUE-134 声明的范围，不替代免费服务验收。实体设备由用户测试；本 Issue 不签核设备/OEM、TalkBack、真实 provider、发布/丢弃事务或进程恢复行为。

## Git

代码与回归测试已提交并推送：`8d1f3abbc0df5b58def766f67dc5fd8081b8e860`（`refactor(ai): 提取审核预览并补齐授权回归`）。冻结报告与台账记录随后单独提交并推送。
