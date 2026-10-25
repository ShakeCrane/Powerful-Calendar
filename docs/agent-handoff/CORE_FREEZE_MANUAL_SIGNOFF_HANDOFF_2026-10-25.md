# PowerCal 核心冻结：外部签核交接（2026-10-25）

状态：**HOLD**。本文件整理当前重建验收台账中尚未签核的 P1 项，供实体设备、外部服务或产品负责人补充证据。它不把窄范围自动化结果升级为完整 PASS。

## 现场基线

- 仓库：`main`；HEAD 与 `origin/main` 均为 `b8819f6d54155701d35f73a4edba117b40b3a32b`。
- 本次只读审计发现 P0 未关闭项 0、P2 未关闭项 0；P1 未签核项 12：10 BLOCKED、1 NOT TESTED、1 SPEC UNRESOLVED。
- 工作区原有 `docs/CORE_FREEZE_ACCEPTANCE_LEDGER.csv` 修改及其他 Agent 的未跟踪测试、`design/` 和 Issue 文件均保留，未纳入本交接提交。
- 四个 Android 模拟器进程仍在运行；本次未连接或更改任何模拟器、应用数据、系统设置或外部日历。
- 实体设备验收由用户执行。AI 测试只能使用免费 provider，或已授权的 DeepSeek V4.1 Flash；凭据请放在本地配置中，不要提交或粘贴到聊天。

## 待签核项

| 验收项 | 当前已验证范围 | 仍需补充的证据/裁决 |
|---|---|---|
| `REC-P1-047` 结构化 AI 提案 | 本地 stub 路径及受控 DeepSeek 流程有独立记录；免费 provider 的结构化阶段此前出现 429，现无可用配置。 | 配置可用的免费 provider，在目标 API 35 环境中显式请求创建事项；记录请求/响应状态、是否出现可审阅提案、是否只有用户确认后才持久化。不得记录密钥。参考 `PC-ISSUE-002`、`PC-ISSUE-134`、`PC-ISSUE-150`。 |
| `REC-P1-048` 随口提及日程 | 本地 loopback 覆盖真实 controller 到界面路径，建议操作仅在点击后执行；live free-model 分类未测试。 | 用同一可用免费 provider 输入随口日程语句，记录模型分类、界面是否只提供轻量建议、未点击前草稿/正式事件/序号/队列/commit 是否不变。参考 `PC-ISSUE-166`。 |
| `REC-P1-063` 折叠标题无障碍 | Compose 语义树确认被折叠标题的事件仍保留完整标题描述和可点击事件框。 | 在 API 35 与实体设备开启 TalkBack，逐个读出可见及折叠标题；验证焦点顺序、完整标题播报、触摸探索及事件仍可打开。参考 `PC-ISSUE-174`、`PC-ISSUE-GREEDY-LABEL-FOLDING`。 |
| `REC-P1-067` 小屏事件触达 | API 35 320×640dp 自动化可从可见标题和事件命中区域打开事件；已记录其像素命中区域较矮。 | 实体小屏用手指操作多个短事件与折叠标题；记录实际设备/密度、屏幕录制或截图、点击位置及打开结果，并单独评价是否可稳定触达。参考 `PC-ISSUE-152`。 |
| `REC-P1-068` 纯日期 Deadline | API 35 窄项已确认语义文本不虚构具体时刻，点击仍打开 DATE 精度事件。`PRODUCT_BASELINE_G1-G11.md` §2 只规定日期级锚点且不得伪造 23:59；§9 明确将日带内准确位置及缩放时间轴无障碍列为未决。 | **先由产品负责人补齐规范**：日期带在时间轴中的准确锚定/布局规则，以及 TalkBack 应播报的完整措辞。确认后再据此补测试；不从现有渲染推导新 UI 规则，在裁决前保持 SPEC UNRESOLVED。参考 `PC-ISSUE-165`、合同 §Invariants 5/10/12。 |
| `REC-P1-069` 字体缩放 2× | Robolectric 和 API 35 AVD 已验证生产时间轴关键控件仍在视口内且可点。 | 实体设备/OEM 将系统字体调至 2×，覆盖主 Activity、时间轴工具栏、事件编辑与关键操作；记录布局截断、遮挡、可触达性及设备信息。参考 `PC-ISSUE-154`、`PC-ISSUE-169`。 |
| `REC-P1-070` 聊天输入法 | API 35 AVD 的隔离生产 Compose 页面已验证真实系统 IME 下会话区域缩小、发送按钮位于键盘上方。 | 实体设备/OEM 在主 Activity 中打开输入法，验证消息区、输入框、发送键、滚动及发送后的键盘行为；保存设备信息和几何/截图证据。参考 `PC-ISSUE-155`、`PC-ISSUE-167`。 |
| `REC-P1-075` 减弱动态效果 | API 35 系统动画缩放为 0 时，Compose 探针确认 `reduceMotion=true`、blur 关闭且动画到达目标值。 | 在实体设备/OEM 启用减弱动态效果，确认动态效果关闭、内容仍可见且核心交互正常；记录设备、系统设置及结果。参考 `PC-ISSUE-168`。 |
| `REC-P1-076` 低端设备降级 | 尚无真实低端设备证据；模拟器不替代本项。 | 在真实低 RAM/低性能 Android 设备确认 blur 降级，不丢数据、不改变时间轴几何或操作结果；记录机型、Android 版本、内存档位及长时运行现象。 |
| `REC-P1-081` 外部 ICS 兼容 | API 35 外部日历入口已解析到 Google Calendar，但流程停在首次账号检查；没有导入或保存。 | 在有可用兼容日历应用的隔离/测试账号中导入并重新导出带 DST 的每周系列；对照 DTSTART、TZID、RRULE 与 occurrence 行为。勿写入个人真实日历；保留原始 ICS fixture 哈希与脱敏导出证据。参考 `PC-ISSUE-001`。 |
| `REC-P1-086` 提醒生命周期 | 合成探针只验证了平台层 Home/Recents/进程及 force-stop 行为，未使用 PowerCal 生产调度器、receiver、通知或数据。 | 在隔离测试事件上验证 PowerCal 生产提醒：Home、Recents 移除、进程退出、用户重新打开后的重建、设备重启；另记 OEM 省电影响。按 Android force-stop 平台语义如实记录，不要求规避系统停止规则。勿清除真实数据。参考 `PC-ISSUE-PHASE4-D05-AVD-LIFECYCLE` 与当前 D05 Issue。 |
| `REC-P1-090` AI 多轮意图交接 | 本地流程有记录，但当前没有可用 provider 配置，因此没有 live provider 请求。 | 配置可用的免费 provider 或授权的 DeepSeek V4.1 Flash，完成已审阅的多轮任务；记录原始用户证据如何保留、响应解析/审阅流程、明确授权前数据库不变及失败恢复。勿记录密钥。参考 `PC-ISSUE-172`。 |

## 回填格式

每项请提供：验收项 ID、设备/Android 与应用版本、复现步骤、预期与实际、通过/失败/无法执行、截图或脱敏日志位置及其 SHA-256、任何影响测试的设置。日期规范项需先记录产品裁决，再运行对应测试。外部 provider 只需提供脱敏状态和证据，不需要提供密钥。

收到证据后应逐项复核原始材料，更新对应 Issue 与台账状态，运行相关回归，并以独立中文提交记录结果。实体设备结果不能由模拟器或 Robolectric 代签。

## 本次工作边界

本次只读审计并生成这份交接文件；没有产品代码或测试代码改动，因此没有声称任何新测试通过。整体核心冻结继续 **HOLD**；待本表所需外部证据/规范裁决到位后继续逐项闭环。

## 后续证据更新（2026-10-25，审计基线 `7951a9b`）

本节优先于上方现场快照中的旧状态摘要。原始交接快照保留，以下仅更新证据和阻塞边界。

- 台账工作区版本为受保护的并发修改，SHA-256 `A743BDB7E5AB9F51A1636425CE29714AD61709515BAFF72AE9B89CB5A497C4E5`。结构性状态仍为 12 个未签核 P1：10 BLOCKED、1 NOT TESTED、1 SPEC UNRESOLVED；历史 209 项 ID 映射缺口如实保留。此文件更新不修改或暂存台账。
- `REC-P1-047` / `REC-P1-090`：桌面 DeepSeek V4.1 Flash 凭据已用于一次受控合成请求，HTTP 401，耗时 701 ms；未重试、未更换 endpoint/model，也未写入应用设置、数据库或正式事件。脱敏诊断 SHA-256 `A36B9835C3E466D482102E11DBD96FD267CAB8C110547511BED594B681264BC7`；详情见 `docs/issues/PC-ISSUE-002-ai-timeout-proposal.md`。该凭据不能作为当前可用 provider；免费 provider 的实时分类仍未测试，多轮 live-provider 流程仍阻塞。不得把密钥复制到聊天或报告。
- `REC-P1-086`：此前关于“尚未使用 PowerCal 生产调度器/receiver”的描述已被后续 API 35 隔离 AVD 证据取代。生产 exact-alarm 路径覆盖普通进程终止、force-stop 后用户重开及系统重启后的 `BootReceiver` 恢复；重启场景 instrumentation 1/1 PASS，提醒实际投递且持久状态为 `FIRED`，未推断任务完成。完整离线门禁 543/543 JVM、74 suites、88/88 tasks，lint 0 errors / 53 warnings。详情见 `docs/issues/PC-ISSUE-PHASE4-D05-AVD-LIFECYCLE.md`。真实设备/OEM 省电行为仍 BLOCKED；模拟器结果不替代该签核。
- Phase 1 编辑器生命周期与统一默认时间已完成自动化及隔离 API 35 AVD 子项。用户已裁决一次性 fall-fold 取第一次（较早 offset）；合同、`DraftForm.parseInstantIn` 和固定 Clock 回归已同步。`EditorDefaultsTest` 7/7、Phase 1 六个 suite 33/33、完整门禁 543/543 JVM 与 88/88 tasks 通过。详情见 `docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md`。一次性 spring-gap 输入规则仍未另行定义；Phase 1 不再因 fall-fold 语义保持 HOLD，核心冻结仍因其他项目 HOLD。
- 其余人工签核要求沿用本交接表：`REC-P1-063/067/069/070/075/076` 需实体设备/TalkBack/OEM 证据，`REC-P1-068` 需先由产品负责人补齐日期 Deadline 的锚定与播报规范，`REC-P1-081` 需隔离日历账号完成外部 ICS 往返验证，`REC-P1-048` 需可用免费 provider 的实时分类证据。

此处的完整测试结果来自各自 Issue 报告所列的隔离 worktree、APK 与日志哈希；本交接更新没有重新运行设备测试，也没有把历史结果说成本轮新测。工作区其他 Agent 的台账、测试、`design/` 和 Issue 修改仍保持未纳入本次提交。
