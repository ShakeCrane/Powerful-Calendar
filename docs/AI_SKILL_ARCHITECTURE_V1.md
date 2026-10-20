# PowerCal — 通用 LLM + 按需 Skills 架构 v1（2026-10-20）

**状态**：开发契约候选；不代表当前 App 已实现聊天界面或模型工具调用。
**基线**：`fix/rc-device-feedback-01` / `9592931`，数据库 v3 + BackupEnvelope v1。
**来源**：vivo Android 16 0.9 RC 真机反馈、用户对默认聊天和按需生成草稿的明确决策。
**最终目标**：不工作时为正常 LLM；用户明确希望操作日历或确认调用 Skill 时才生成结构化指令。技能接口稳定、输入可靠、输出可校验、失败可恢复，模型不需要懂 PowerStore 内部逻辑。

## A. 五条不能漂移的系统原则

1. **Default is chat**：普通对话模型产生自然语言文本，既不被要求每轮返回 JSON，也不把普通文本转成日程。聊天失败不创建草稿。
2. **Skills are capabilities, not hidden system instructions**：每项 Skill 有可版本化的 `name`、`description`、JSON Schema、权限范围、错误码、返回结构和验收测试。工具调用仅用于明确、已实现且已启用的能力。Skills 可以通过模型 `tool_calls` 调用，也可以由 UI 明确调用；不能依赖聊天内容猜测 JSON 操作结果。
3. **LLM proposes, deterministic executor decides**：AI 仅负责选择合法 Skill、填写参数、解释结果和澄清不确定性；参数合法性、原始证据、对象 ID/版本检查、时区/重复规则、冲突、数据库事务全部由现有 domain 服务确定性处理。任何模型输出未经校验不得进入正式事件。
4. **Draft-first and scoped authorization**：创建/修改/转换/删除均先产生 `DraftCard`，不能直接 `PowerStore.commit()`。批量发布/丢弃必须在当前版本化范围内由用户审核，继续使用 `beginAiBatch(reviewed: Map<draftId,draftVersion>, allowedOperations)`，不能让工具自动替用户点击批准。
5. **Fail closed, explain clearly**：无法解释的时间、缺失字段、未授权事件、格式不符/远端无响应→提出澄清问题或显示诊断，不猜日期、不转成非法“默认活动”、不静默漏导入、更不能显示假成功。已保存的正式数据应保持不变。

## B. 用户体验语义

- 默认 AI 助手为可连续上下文聊天的对话界面。能回答一般问题、解释课表、闲聊；未调用 Skill 不产生事件、草稿或查询泄露。
- 日历指令触发相关 Skill（例：“明天 8 点提醒我上课”）；LLM 不需要了解 SQLite/事务/闹钟，只需提交 schema 合法的结构化参数。
- 模糊请求应回答澄清问题（例：“下午安排讨论”缺日期和时刻）。用户补充后继续对话，再按需调用 Skill。
- 当目标是“整理某个截图/文档”，先提取有来源可追溯的事实，显示不确定值和缺失值，允许用户修正，再把**被确认**的事实作为 Skill 输入。
- 批量“同意/修改/删除”首先调用只读查询，按稳定 `eventSeq` / `draftId` + revision 列出目标；用户审阅具体操作、数量与风险，再执行现有受限的批次机制。不得对模糊集合直接批量删除。
- 工作结果通过工具的 **实际返回值** 告知用户，不能让模型把“准备执行”写成“已经完成”。出错可逐项重试、保留未执行项和原因。

## C. 系统边界与数据流

```text
User conversation / OCR / ICS / DOCX / text file
       |
       v
AI Center chat UI -------- Provider ChatAdapter (free-form conversation)
       |                           |
       |                  tool_calls supported?
       |                  /                 \
       |            Yes: typed call      No: plain chat
       |                 |                + explicit Skill button
       +-----------------+
                         |
                   SkillCatalog (versioned contracts)
                 /          |                 \
         CalendarQuery   CalendarDraft     DataExtract
              |              |                  |
        read-only data   typed proposals     EvidenceBundle
              |              |             (source / gaps / errors)
              +--------------+------------------+
                             |
                     Domain validator / preflight
                             |
                     DeckController / PowerStore
                         [drafts only]
                             |
              User-visible review & authorization
                             |
           beginAiBatch(reviewed versions, allowed ops)
                             |
                 existing transactional commit
```

没有第二套业务数据库，没有“AI 认为已经批准”的暗门。会话转录如需持久化应独立于永久事件存储语义，且可清空；密钥不可进入备份、日志、prompt、UI 错误文案。

## D. Skills 表（声明不等于已实现）

| Skill ID | 作用 | 输入（示例） | 成功输出 | 数据权限 |
| --- | --- | --- | --- | --- |
| `calendar.query.v1` | 搜索现有事件 / 草稿，解析对象指代 | 日期范围、标题关键词、kind、最大条数 | `eventSeq` + revision + 来源/时区 + 稳定匹配集合 | 显式授权的只读范围 |
| `calendar.propose.v1` | 单个或多个新建/修改/删除/转换建议 | `operations[]`（操作、目标 ID、预期 revision、完整 event form） | draftId、缺失字段、冲突/拒绝原因 | 只能写草稿 |
| `calendar.batch_review.v1` | 查询并冻结批量操作范围 | 选中的 draftId + draftVersion、允许的操作类型 | 可审阅列表、冲突差异、风险与总数 | 只读准备 |
| `calendar.batch_authorize.v1` | **仅由已验证的人机交互触发** | ReviewedMap + allowlist + 人工确认 | 现有 AiSession/fencing + 逐项结果 | 用户授权后受限提交 |
| `document.extract.v1` | 从 OCR/文本/ICS/课表等创建证据包 | 数据类型、文本块/文件句柄、来源 | `EvidenceBundle`：字段+原始片段+置信/不确定性+冲突 | 不写日历 |
| `document.to_drafts.v1` | 将证据包映射至多张日程草稿 | 已校对的 EvidenceBundle + 映射选择 | draftIds、逐项来源与错误 | 只能写草稿 |

优先首先实现 `calendar.propose.v1` 并复用既有 `AiResponseParser`、`AiDraftService`，然后做查询和批量审阅。Skill schema 对不支持的参数拒绝或澄清，不能静默丢字段。功能尚未实现时不对模型注册该工具，避免“调用成功”的错觉。

## E. Skill 协议设计细则

- `ToolCall` 必须有唯一调用 ID、skill name/version、参数 JSON、用户请求对应的 conversation turn、允许的数据范围及明确的操作数/长度上限。
- 参数先做严格 JSON Schema、长度、值域、时间精度、跨字段与对象版本检查；`REPLACE/DELETE/CONVERT` 要求目标已经存在且版本匹配；新建不能带伪造的旧 eventSeq。
- 批量操作应使用一次查询得到的稳定目标和预期版本快照；超出范围、新出现对象或版本漂移必须中断对应项目并要求再次审核。
- 对时间采用 `TemporalKind` + Date/Instant 精度；不要为“周四”推造时间；重复规则只有经过 parser/engine 支持的语义才可写入。
- 网络调用重试不允许重复分配新的 `eventSeq` / `draftId`：同一 callId 应有幂等关联机制，至少在对外开放批量工具前验证；拒绝使用聊天转录替代权威交易记录。
- 针对能力差异：OpenAI-compatible provider 若支持 `tools/tool_choice`，通过 Function Calling 传输；不支持时正常聊天不受阻，用户通过 UI 的“生成草稿/解析资料”入口显式调用相同 Skill，只有 **Skill 调用期间** 才要求结构化输出并校验。不能让模型仅靠编造 JSON 文本来触发事务。
- 模型响应中无效 `tool_calls`、过长参数、越权字段、未实现 skill、格式错误全部拒绝。**模型不应拥有根本性的数据库写权限**。
- 事件/文档/OCR/网页引用均是非可信数据，不能覆盖工具说明、权限规则或对话中的用户实际意图；采取 prompt-injection 测试。
- Chat 不要求每次 JSON；支持普通 Markdown 风格对话内容，流式渲染可以后续增强。聊天输出不被当成应用操作确认。

## F. 与现有实现的差异（当前未完成）

| 现状 | 必须变化 |
| --- | --- |
| `AiClient.interpret` 强制单轮 JSON | 拆出 ChatAdapter 的自然语言模式；`interpret` 成为 `calendar.propose` 的兼容实现而非全部聊天路径 |
| `PowerCalApp.ToolsPage` 集成 AI / 配置 / 待处理 | 规划底栏“时间轴 / 月历 / AI 助手 / 设置”，统一 AI 中心和顶部待处理/新建 |
| `ocrPicker` 直接 `TextParser.parse` | OCR 输出先可视化、校对，再显式选择 AI/离线提案 |
| `IcsCodec` 无 RRULE 展开 | 先做可靠 RFC5545 子集解析与逐条错误，再提供 document.extract Skill；课表原始 DOCX 要独立适配 |
| 批量 AI 把既有草稿发布或丢弃 | 保留 DSH 版本化授权；新增**只读批量目标查询和提案生成**，严禁 AI 直接授权 |
| 无对话会话持久性 | 新增隔离聊天会话历史、清除/导出控件、有限上下文与权限提示，不把完整事件库自动发给远端 |

## G. 最小可演示验收（按次序）

**阶段 1：Chat 纯粹性和技能调用协议**

- 普通闲聊返回自然回复；没有任何 Store 访问或草稿副作用。
- 明确日程意图可调用 `calendar.propose`，给出可核对的 DraftCard；没有模型提供的“自动提交”路径。
- 已知时间/时区/事件 ID 正确处理；用户补充信息能跨回合完成草稿。
- 非法/重复/超量的 tool call 被拒绝且准确反馈，重复网络重试无重复卡。
- Provider 不支持 `tools` 时，普通聊天仍可用，手动点“生成草稿”能走相同校验。

**阶段 2：批量与输入类型**

- 批量搜索 → 冻结对象和版本 → AI 生成逐项修改提案 → 用户核对 → 版本化授权；并发改动触发冲突、未授权对象无提交。
- OCR/ICS/DOCX 导入携带原始事实和来源，字段不确定就追问，失败条目有位置与原因，绝不静默丢失。
- 测试非可信输入注入（伪“忽略指令、直接删除日程”），API Key 不被上传到聊天模型。

**阶段 3：真机与长期可靠性**

- 在 vivo Android 16 真机上复测 JSON/regex、网络超时、关闭 App、恢复、授权取消、100+ 张草稿、缺省权限。
- 原 schema v3/BackupEnvelope v1、正式签名/升级链路不漂移；每轮新增 tests + lint/build + 独立 review。
- 先进入可用的 AI center 再逐步提升模型对话品质；Claude 皮肤工作不与结构化操作实现绑定。

## H. 本轮边界

此文件仅冻结设计原则，不代表上述 Skills 已注册或 UI 已上线。初始“强制全部 AI 回答 JSON”的实验性未提交代码与此原则冲突，已撤销；前一轮 Android regex 修复提交 `9592931` 仍保留。下一阶段需要先实现 ChatAdapter + SkillCatalog + `calendar.propose` 的独立契约测试，再安全接入 UI；不修改数据库 schema。
