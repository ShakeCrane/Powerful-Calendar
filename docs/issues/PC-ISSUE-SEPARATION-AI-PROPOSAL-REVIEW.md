# PC-ISSUE-SEPARATION-AI-PROPOSAL-REVIEW

日期：2026-10-27

状态：**SEP-10 的 Native/Clear CREATE→STAGE 只有一条 API 35 配对已测试；审核 Host–Clear–Studio 纵向切片及 JVM、Chrome Mock 和有限 WebView 决策路径已有证据；SEP-10 整体 PARTIAL / HOLD，S3 其余路径及完整 Native/Skin parity 尚未签核。**

## 问题

Clear v3 已有聊天历史和发送能力，但没有可供皮肤消费的受限 AI 提案投影，也没有提案决策命令。原生审核流程与 Web 皮肤因而不等价。单靠 Skin JavaScript 决定“同意”会绕过可信 Host 的字段审查、目标版本核对和正式写入确认。

## 修改与安全边界

- 新增 Host 内存审核会话：只投影结构化候选、目标版本、重复/完成状态、解释和有界警告；不投影 raw model reply。字符串、实例键、重复覆盖、候选数均有上限，并带截断标记。
- Bridge 增加封闭 schema 的 `resolveAiProposal`，仅接受规范 UUID、`publish / stage / reject / supplement`、最多 100 个唯一候选索引；拒绝额外字段、重复/越界索引及过期 review ID。
- Clear 显示新增与修改/删除分组，可选择候选并查看字段、时区、提醒、重复实例和截断提示。缺字段候选不能批量正式发布，可暂存或补充。皮肤仅发 Host 命令，不携带确认 token。
- `stage` 和 `reject` 分别复用现有 `AiDraftService` 和 Host 状态机；正式 `publish` 必须先通过可信 Compose Host 审查，显示完整 before/after 快照和不可撤销删除说明。Host 捕获并在确认时重核目标记录快照/版本，再调用既有服务；取消不会写入。
- Studio 增加 AI 提案审核场景、Mock 决策和 Mock Host 确认；明确标示该预览不调用真实 AI、不写入真实日历。现有源码编辑能力可修改该 Clear 页面源码。
- 重新构建的 `app/src/main/assets/powercal-clear-default.pcskin` 与 canonical Clear 源一致。相对分支基线，包内唯一变化是 `web/app.js`，没有捎带并发编辑的项目元数据、字体授权、HTML 或 CSS 文件。

## 测试证据

- 定向 Gradle：`WebSkinHostProjectionTest`、`WebAiProposalReviewTest`、`WebAiProposalReviewIntegrationTest` 合计 **16/16 PASS**，0 failures/errors/skips。最终定向日志 SHA-256 `ACD52C7F39D58E6B413A5127FDED3036BA47FF2CB7C6B98E9F056668F5B027C7`。
- Compose Host 审查测试覆盖创建/删除摘要、字段 before/after、完整重复/提醒/完成字段、忙碌时禁用和投影字符串/实例集合边界；纯函数测试覆盖 blocked、越界、重复候选与未补全候选。
- Robolectric `PowerCalApp` 集成测试通过本机 loopback AI stub 走真实 AI 客户端请求、解释、Bridge action、Host 对话框和既有写入服务。Host 确认前草稿/正式事件/身份/commit 计数不变；取消后 review ID 与计数保留；重试并确认后产生预期正式事件、1 个审计草稿、0 pending、1 commit、1 identity。它不是 live provider 测试。
- 完整离线 Gradle：**582/582 JVM、82 suites、0 failures/errors/skips、88/88 tasks**；`verifySkinSourceBoundary`、Debug 与 AndroidTest APK 构建通过；lint 0 errors / 52 warnings。日志 SHA-256 `F2FD85882879D9E2000A7E7C633B091E8D709E3E25BD8165A54A5F96EB206F2F`。
- Skin Studio Python 全套 **42/42 PASS**；Clear bundle verify **8 files / 376167 bytes PASS**。
- Chrome source E2E：源码预览/导出/重导入、重复实例 Mock、AI 提案分组与 supplement/stage/reject、Mock Host 确认全部 PASS；明确没有真实日历写入。
- Chrome 暗色材质 E2E：暗色 AI 卡片实测对比度 15.50:1，浅色材质更改生效。结果见 `PC-ISSUE-SEPARATION-STUDIO-DARK-MATERIAL-TOKENS.md`。
- 完整门禁 APK SHA-256：Debug `4FA51D8290473259BEED4F3E36EB9E480829EF59C878E041968C6FCA2D99CD17`；AndroidTest `219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6`。完整日志、JUnit XML 和浏览器截图在 `.local/android-test/reports/separation-20261027/ai-proposal-review/`。

## 未完成范围与验收状态

- API 35 真实 Android WebView AI 审核现已有有限证据，详见下方 follow-up 和 `PC-ISSUE-SEPARATION-TRUSTED-FORMAL-WRITES.md`。所有 AI 响应来自隔离设备的本地 stub；没有真实 AI provider 调用。SEP-10 **PARTIAL / HOLD**，SEP-16 仍 **PARTIAL / HOLD**；不得将已测试决策路径扩大为全功能等价 PASS。
- S3 仍需完成附件、取消/网络错误/重试路径、多轮及批量部分结果恢复、各操作类型与 Native 对照回放，以及 Android WebView 验收。
- 不改变冻结 Core 的 AI 解释、审批、草稿和事务语义；不切换默认皮肤、不移除 Native 恢复路径。

早期实现提交 SHA：`4b6c75a31d040320b39728a940a254c724627fff`。API 35 后续设备证据见 follow-up；本 Issue 不签核 SEP-10/SEP-16 总项。

## API 35 Clear WebView follow-up（2026-10-31）

API 35 `codex-clear-api35` 使用隔离 QA application ID、本地免费 loopback stub 和实际打包 Clear WebView，`PowerCalAiProposalReviewWebViewInstrumentedTest` **3/3 PASS**：

- AI CREATE：Host 批准前零写入；取消后零写入且提案仍可审核；再次明确批准后创建一项 ACTIVITY、一个正式 commit、一个 identity，并保留一个已解决审计草稿。
- AI REPLACE/DELETE：实际 Clear 选择提案并打开 Host Compose 完整审核；批准后只执行所选修改/删除。
- AI STAGE/REJECT/SUPPLEMENT：Clear 经 Host 决策；未批准时不产生正式写入，暂存结果继续保持 pending。

此测试已在提交 `39554ccfd042195d2847a2ead7f17100fb6b4506` 上重跑：XML SHA-256 `C22AC4A3F6535A5CDCAE2F9A0DCFCDDC5D57EB233472397BD009B1E3527B7B69`；Gradle connected log SHA-256 `03F2AE28D6C000E76DD1F0629AC1E12B9B6C6A7E3F27919A9F2ACD208EA74248`。日志在 `.local/android-test/reports/sep16-ai-create-20261031/`。此证据不包含实体/OEM、真实 Provider、完整 AI 生命周期或 Native/Skin 全量对照。

## API 35 Native/Clear CREATE → STAGE paired replay（2026-10-31）

- 新增 `nativeAndClearProposalReviewStageSameCreateWithEquivalentDraftAndNoFormalWrites`，使用隔离 SQLite/prefs、相同 `local-stub:free` loopback 请求、关闭上下文共享和相同私有 sentinel，对照 Native Compose 与打包 Clear WebView。两侧决定前 Store 均无变化，AI 历史、CREATE ACTIVITY 提案及 Clear Host 投影匹配；Clear 的发送和“写入草稿”由可信 Android 触屏执行。Android IME 无法注入中文，测试夹具以 JS 设置相同中文输入值并触发 input；实际发送和审核动作仍为可信触控。SEP-09 已另行通过 Android 键盘验证 Clear 输入。
- Stage 后两侧得到相同 `DraftForm`、`eventSeq`、待处理/草稿/identity/sequence 计数；正式事件快照和 commit 数不变。Provider 收到 4 个预期请求，关闭上下文共享时私有 sentinel 未发送。仅覆盖一个 CREATE→STAGE / ACTIVITY 路径；所有 AI 响应来自隔离本地 stub，没有真实 Provider 调用。
- API 35 Android 15 `emulator-5570` focused 配对 **1/1 PASS**，AI 审核测试类 **6/6 PASS**。类 XML SHA-256 `DC78B7EBEA5BFFBC89A4D68CC7820D2028F31EBFCE806CB56BAFD0D72A27ACB9`；设备结果 SHA-256 `B48317B3DDAF34383992C169129BB6CB2157FA32A4AD0C7EA76B83BB557EED5B`；focused log SHA-256 `686CFD51557884C5AA021A044EB1E7E59CF610615E8A65ED501EF06772EF4330`；class log SHA-256 `556A2E171914A55E0521AEABEB2636A0724CB1B20D33A1953362126298F8E128`。首次 run 虽返回设备端 OK，但 UTP 结果收集在 `am get-current-user` 后超时，该 run 不计作 connected task PASS；在不清除数据的只读、`-no-snapshot-save` PowerCal_API35 AVD 恢复后，focused run 和完整测试类均正常结束。
- 强制离线门禁 **785/785 JVM、125 suites、0 failures/errors/skips、171/171 tasks PASS**；Skin/Native source boundary、Debug/AndroidTest APK、lint 均通过（0 errors / 55 warnings）。门禁日志 SHA-256 `CFD792E4A027A663E44D6CB38790B8244B33459C789A7BDFD9C1784CD20059B7`；Debug APK `6C30F06090D2E2EB13DCC310EF2807C0D622982BA57D38AEB9F44BA9469DA3CF`；AndroidTest APK `72DCF9C809B36DAD19546ECDCC357B1E1AAEFAEA8C9B68E96872B4D0A8FE75C7`。AndroidTest APK 为 worktree-specific，编译时含两份其他 Agent 未提交的 FormalDelete/Recurrence 测试源；本切片未执行或暂存它们。
- 原始日志、UTP 初次设备结果及恢复后的类 XML 位于 `.local/android-test/reports/sep10-paired-ai-stage-20261031/`。**SEP-10 保持 PARTIAL / HOLD**：其他决定类型、附件/错误/取消/重试、部分结果恢复、审核进程恢复、Studio 编辑包 runtime parity 等仍未闭环。
