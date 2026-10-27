# PC-ISSUE-176 — 左侧标题校正、AI 审核卡组恢复与截止默认提前提醒

日期：2026-10-27。
状态：修复实现与测试回归中；正式核心冻结仍为 HOLD。
明确的所有者指令：右侧时间轴标题已经真机验收并冻结，本轮不更动右侧标题字号、碰撞策略或坐标。

## 1. 左侧标题上移的根因与最小修复

在 `TimelineScreen.kt` 中，上次统一标题“框外展示”时使用了所有标题的 `topPx - textHeight - inset` 计算式。然而 Alarm 是从刻度 y **向下** 布置的点事件（线位于 y），其标题的旧语义是紧挨刻度线下方，而不是将其放到一个虚构的顶部。于是左侧 Alarm 标题被整体上移。

本轮保留两侧统一 13sp、仅框外、基于真实文本矩形的贪心折叠和独立点击命中行为；仅更正坐标：Alarm 的标签在 y + inset；Deadline 在 y - textHeight - inset；Activity 仍使用已经验收的原标题位置。对应 Compose 测试新增“左侧 Alarm 标题不能落到刻度线之上”。

## 2. 恢复以前的 AI 审核卡组

复用既有 `AiProposalReview`：**批量同意／写入草稿／批量拒绝／补充细节**。不创建第二套卡片，也不更改正式授权层。

背景：虽然组件未被删除，但显式请求经聊天模型 -> 多轮任务 -> 结构化 Skill 时，模型只追问或返回空 `events` 就导致没有候选卡。用户期望信息不足也能先看到卡片、在其中补全或暂存。

修复包括：
- `CHAT_SYSTEM_PROMPT` 明确：可识别为显式日历请求时，字段缺失也应发出结构化提案意图标记；不以缺少时间阻断 Skill。
- `DRAFT_SYSTEM_PROMPT` 明确：能确定对象类型的显式新建必须至少返回一条事件候选；未知字段留空，不凭空编造日期/目标事件。
- `AiPartialProposalFallback`：已明确授权的多轮任务在结构化结果为空/不可用时，**只在能确定单一类型且是新建操作**的前提下给出待补全候选。保留原始用户请求、时区、任务版本和已标记来源；绝不捏造日期/时分或把修改/删除改成新建。数字明确给出的多项创建可生成多张待补全卡，最多 20 张。模型已有真实候选时无条件保留，不用兜底取代。
- 未产生任何实际日历写入的预览阶段与写入草稿阶段严格区分。
- 卡片内即使选择了待补全事件，“批量同意”仍**禁用**，用户只能先「写入草稿」编辑完善或补充聊天；`AiDraftService` 执行层同时拒绝未经补全直接发布，形成 UI + 执行双门禁。既有删除提案仍可依当前目标身份授权。
- 保持原组件的“批量拒绝”文案，与历史回归一致；不借此改变批次删除正式日历的行为。

自动化证据：
- `AiPartialProposalFallbackTest` 覆盖闹钟缺少时间、五个同名任务待补全候选、精确截止默认提醒、类型不明与修改场景绝不伪造 CREATE。
- `AiPartialProposalLoopbackTest` 使用本地 OpenAI 兼容 HTTP 假服务真实走网络 JSON 构造和客户端 `interpret(task)`，验证服务端 `{"events":[]}` 时仍显示待补全提案。它不是外部 Provider 线上调用。
- `AiProposalReviewComposeTest` 验证四按钮及不完整候选不能直接发布；已有 `AiReviewComposeTest` 同步新安全语义。
- 因 2026-10-25 历史测试使用真实系统时钟而于 2026-10-27 越过 24 小时任务时效，`AiChatDraftPipelineTest` 与 `AiTaskPipelineTest` 使用固定 Clock 恢复可重现性，**绝不调整生产过期保护**。

## 3. Deadline 默认提前一小时

- 对于精确截止时刻的 `DEADLINE`，普通新建 `EditorDefaults` 默认 `reminderMinutes=60`、`reminderTiming=OFFSET`。
- AI 结构化提案如果有精确截止时刻且用户未明确提供 reminderMinutes，默认提前 60 分钟。模型提示词对此明确约束，示例 JSON 不强行塞入其他事件的 0 分钟默认值；若用户明确提供自己的偏移则优先采用。
- **仅日期、没有时分的 Deadline** 必须保留 date-only 精度和原有 DAY_START 行为，因为它不存在可供“提前一小时”相减的精确时刻；不猜测 23:59，不自动引入虚假的具体截止时间。该纯日期规格的最终细节仍另列待定。已有正式事件的提醒配置不被本轮强制覆写。

## 4. 本轮范围外与未签核

右侧标题已由用户手动验收通过；本轮仅核对没有修改其绘制位置。左侧仍需用户在新 APK 上复核。真实 AI Provider 仍缺实际请求与卡片闭环证据，不能凭本地 stub 宣布线上 READY 或 CORE FREEZE GO。已存在的并行 CSV、D05 AndroidTest、Design、DraftDeckFeedbackTest、PC-OBS-005、Python 缓存均予以保留。

本阶段最终 Git SHA、Gradle 测试计数、Lint、安装包与 SHA-256 将在交付报告中补充；自动化测试不等于实体设备验收。

## Verified local gate (pre-commit, working source)

Command: `gradlew :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`

- BUILD SUCCESSFUL (59/59 Gradle tasks)
- 561/561 JVM tests PASS, 79 suites, 0 errors, 0 failures, 0 skips
- Lint: 0 Error, 52 Warning
- Report log: `.local/android-test/reports/freeze-20261027/left-ai-deadline-full-core-r2.log`
- Built QA APK SHA256: `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`
- This working copy includes another agent's untracked `DraftDeckFeedbackTest.kt`. A clean committed Git SHA build must be used for the final reproducible count.
- No real paid/free provider credentials were used; loopback test is a local server.
- User still owns left-wing and AI real-phone acceptance, right-wing already owner-accepted.
