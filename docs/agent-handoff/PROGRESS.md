# Core freeze progress

## Separation — Studio dark material tokens (2026-10-27)

- Root cause: Studio emitted edited base material slots into `:root`, overriding Clear's authored dark text token. `v3-studio.js` now emits `:root:not(.dark)`.
- Headless Chrome test edits the real Clear text-color material while displaying AI proposal cards. Dark card text/background measured `rgb(243, 243, 246)` / `rgb(27, 27, 31)`, contrast **15.50:1**; switching to light applies the edited `#16171b`. Python Studio suite 42/42, source round-trip E2E and Clear bundle verification passed. Android WebView / device remains NOT TESTED.
- Detailed evidence: `docs/issues/PC-ISSUE-SEPARATION-STUDIO-DARK-MATERIAL-TOKENS.md`; browser JSON SHA-256 `F1CCC42029F1C0C6A4FD0276842FDADD364E32C01CC35D70268943856408629C`.
- Implementation commit `c496695b0fe3db73e181e67da731969fe881cb0c`; report and progress update are being committed separately before push.

## Separation — AI proposal review initial vertical slice (2026-10-27)

- Clear v3 now receives a bounded Host `aiProposalReview` projection and can send closed-schema `resolveAiProposal` actions for publish, stage, reject, or supplement. Formal publication waits for a trusted Compose Host review of the full before/after snapshot; the Host rechecks target snapshots/revisions before invoking the existing `AiDraftService`. Cancellation preserves the review and writes nothing. Skin JS receives no approval token.
- Added bounded deterministic recurrence/completion projection and Clear review cards; Studio adds an explicitly non-writing AI review Mock scenario. The canonical Clear package changed only in `web/app.js` and passes `build_default_clear.py --verify` (8 files / 376167 bytes; SHA-256 `fd061a35252f3f821d80ccc4b97f50a46123c23e4db9fc12e0f4435305f993ab`).
- Focused `WebSkinHostProjectionTest`, `WebAiProposalReviewTest` and `WebAiProposalReviewIntegrationTest`: **16/16 PASS**. The local HTTP AI stub drove the real app pipeline; store counters remained unchanged before confirmation and after cancel, and one formal CREATE committed after retry/confirmation. This is not a live provider test.
- Full offline gate `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain`: **582/582 JVM tests / 82 suites / 0 failures, errors or skips / 88 tasks PASS**; lint 0 errors / 52 warnings. Full log SHA-256 `F2FD85882879D9E2000A7E7C633B091E8D709E3E25BD8165A54A5F96EB206F2F`; Debug / AndroidTest APK SHA-256 `4FA51D8290473259BEED4F3E36EB9E480829EF59C878E041968C6FCA2D99CD17` / `219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6`.
- Skin Studio Python **42/42 PASS**. Chrome source round-trip and AI decision Mock passed. Clear bundle verify **8 files / 376167 bytes PASS**. Detailed evidence: `docs/issues/PC-ISSUE-SEPARATION-AI-PROPOSAL-REVIEW.md`.
- No Android WebView, emulator or physical-device AI review run; no live provider call. SEP-10 and SEP-16 remain **NOT TESTED** for full parity. S3 remains open for attachments, provider failure/cancellation/retry, multi-turn/batch recovery and operation coverage. Implementation commit `4b6c75a31d040320b39728a940a254c724627fff`; documentation commit and push SHA pending.

## 分离阶段 — Bridge v1 能力声明语义与兼容审计（2026-10-27）

- 根因/现状：v3 manifest 接受并保存 `host.edit`，但 Bridge v1 实际仅将 `host.read` 用于状态投影、将 `host.action` 用于所有入站动作和编辑器/草稿命令。若将未生效的 `host.edit` 静默升级为授权，会改变既有包权限语义；本轮维持运行时策略，明确记录它是兼容元数据，并从示例 manifest 删除该无效声明。
- 更新 Host/包管理器注释、v3 合同、Bridge 扩展文档、Studio README 和 parity 矩阵；扩展 capability policy JVM 回归，覆盖 edit-only 与 read+edit 不得获取读/写动作权限。未增加运行时能力或改动业务数据写入路径。
- `SkinPackageManagerTest` 定向 **9/9 PASS**。完整离线 Gradle 门禁 **574/574 JVM、80 suites、0 failures/errors/skips、88/88 tasks**；source boundary、Debug APK、AndroidTest APK、lint 均通过。完整日志 SHA-256 `9C6B4C7AF3B6026D40CD5E4513BE8E5885A6CA86598EB17B9A8C2824458BC61C`；APK SHA-256 `5387C51C17A7CB35C7448DB6F1AEE8DE8638064B9B5760229761800670A79AE8` / `219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6`。实现提交 `394f7e13d23f98fb018428f8b44d47c6a9a0e0e7`。
- Studio 全套 **42/42 PASS**（日志 SHA-256 `AC151E0E67DA7F5EDA7D4DB817CC977FD56C94745168B88268D3063ECE8B2EA4`）；两组定向 Python 测试各 **3/3 PASS**；Clear bundle verify 和 v3 示例打包/校验通过。详细命令、证据路径、哈希和限制见 `docs/issues/PC-ISSUE-SEPARATION-CAPABILITY-DECLARATIONS.md`。
- 本机 ADB daemon 无法启动且未发现 emulator 可执行文件；本次 Android WebView/模拟器/实体设备均 **NOT TESTED**。SEP-13/SEP-16 与整体 Native/Skin parity 继续 **NOT TESTED**；不是设备或总体分离 GO。

## 分离阶段 — 皮肤正式写入宿主可信审查（2026-10-27）

- 根因：普通 editor.save 和有效 draft-deck 正式提交在 Web command 中可直接写入；host.action 能力校验没有针对具体写入内容的人类审批。真实 WebView 还发现临时 63 位 client ID 经 JavaScript Number 往返会丢精度，导致 draft patch 指向失效 ID。
- Host 现对普通正式编辑、临时新建发布及有效 deck 正式写入统一保留私有审查快照，Compose 批准并重核快照/版本后复用原 EditSession、NewEventEditSession、DeckController 路径；JS 无审批 token。临时 ID 限制为 JS 可精确表示范围，不产生永久身份。实现与测试提交 725e1c79c300272ec9e2f6b1cae3a38dda364733 已推送到 origin/feat/skin-first-separation。
- 修复前红测明确要求 confirmation_required 而得到 saved，日志 SHA-256 4080BF73E16E4F57D34E09BC529495CDD1EB2EDDBDE433EA588E5AD89125A9C7。定向 **30/30 JVM PASS**；完整离线门禁 **574/574 JVM、80 suites、88/88 tasks**，0 failures/errors/skips，lint 0 errors / 52 warnings。日志 SHA-256 分别为 48305411FF49C09B1291E4AA945449F51172B20EB2EC3420E1F2CF51FB72D2B5 与 D87418E9914E471A87FE54AA811B67DBC1668C4AA91BCF8E83CFB8F9456525EC。
- API 35 emulator-5564 实际 Compose Host + Clear WebView **2/2 PASS**：普通正式编辑、新建 ACTIVITY 发布均等待 Host 批准；重复实例选择性编辑仍通过。instrumentation 日志 SHA-256 2503C1A4E5E06BCE96D14CB296C0C74DDADC9A63363EDADA4E44B0E9C3863F8F；QA app / AndroidTest APK SHA-256 0BFA6BABF47BB64F24A2F87D7B59146D9F9DE0DAC6DD791B923EC2D7DADFF9F4 / A2B856A60D8B6AFB642F6E8EB2224F2E3BDFF6AC4F8BB38BF9264C2CE21356E0。详细独立结案报告：docs/issues/PC-ISSUE-SEPARATION-TRUSTED-FORMAL-WRITES.md。
- SEP-16 总体 Native/Skin parity 仍标 **NOT TESTED**；本次窄范围 Host 审查证据不能替代完整 deck 操作、四种事件、异常/进程恢复、实体设备和全流程等价。实体设备由用户负责；host.edit / host.action 审计、其他皮肤能力与 Release QA/cutover 仍开放。Studio 元数据改动继续留在工作树，未纳入本次提交。

## 分离阶段 — 重复实例 Web 编辑与 Host 可信确认（2026-10-27）

- Web timeline/month 投影现将稳定重复槽位键单独交给皮肤；Clear 提供三种编辑范围、单次字段限制和恢复修改控件。重复相关有效保存先进入 Host Compose 完整 before/after 审查，JS 不持有确认 token。范围、版本冲突、取消、重放、皮肤切换及 Native `EditSession` 结果有 JVM 回归。
- 当前分离工作树最终完整离线门禁 **569/569 JVM、79 suites、0 failures/errors/skips、88/88 tasks**，Lint 0 errors / 52 warnings；Studio **42/42**；Chrome 页面 Mock 回归通过；API 35 `emulator-5564` 实际 WebView/Host 单次 ALARM 场景 **1/1 PASS**。设备测试针对 QA APK `0BA65A...7FDF`，它早于最后一个 `appScope` 生命周期调整；最后源码包含于完整 Gradle 门禁，但未在模拟器重跑。
- 独立报告：`docs/issues/PC-ISSUE-SEPARATION-RECURRING-WEB-EDITOR.md`。实现提交 `879e078a00b59c65c4b2d9624b37ec50556e0bc6` 已推送至 `origin/feat/skin-first-separation`。矩阵 SEP-06 仍 NOT TESTED（完整功能等价未完成），SEP-16 记录普通 `editor.save` / `deck.commit` 可信确认审查缺口；`host.edit` 门禁也仍待审计。
- 下一步：独立修复或裁决普通事件保存与草稿发布的 Host 可信授权缺口，再继续 Studio 完整资产/项目编辑和 AI 审核迁移。发布 QA、真机和 default-skin cutover 仍 HOLD。

## 分离阶段 — 项目元数据无损往返（2026-10-27）

- 修复 Studio 仅切换面板或编辑脚本时重新序列化第三方 `project.json` 的问题；仅真实材质/图层修改才序列化项目，未知字段保留。旧代码红测失败，新代码定向 3/3、真实 Chrome 导出/重导入和完整 Studio 42/42 PASS。报告：`docs/issues/PC-ISSUE-SEPARATION-STUDIO-PROJECT-ROUNDTRIP.md`。未执行 Android WebView 或实体设备测试，完整项目编辑和功能等价仍未完成。

## 分离阶段 — Studio 源码与能力声明、初始功能矩阵（2026-10-27）

- Studio 可编辑 Clear 的 `web/edit.js` 等附加 UTF-8 源文件；导入皮肤的 capabilities 不再被打开/预览/导出静默改写，缺失声明按 Android 默认空权限处理。旧代码红测确认源码不可访问、三组权限声明被改写及缺失声明被授予权限；新代码定向回归、真实 Chrome 预览/导出/重导入 PASS，Studio 全量 41/41 PASS。详细证据与未完成项：`docs/issues/PC-ISSUE-SEPARATION-STUDIO-SOURCE-CAPABILITIES.md`。
- 已依据 `PowerCalApp.kt`、`WebSkinHost.kt`、`WebSkinEditBridge.kt`、Clear 和 Studio 源码建立初始 `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`。该矩阵为持续调查起点，未把源码检查、Mock 或已有局部测试标为 Native/Skin 全功能等价；S0、S2–S8 继续开放。下一步细化 S0 并优先处理实例范围编辑、可信 AI 审核和 Studio 全项目编辑能力。
- `1a7de91` 的独立新检出在 `core.autocrlf=true` 下通过 Clear 资产同源校验，工作树干净；细节见 `PC-ISSUE-SEPARATION-CLEAR-CHECKOUT-EOL.md`。发布 QA 和默认切换仍 HOLD。

## 分离阶段 — Clear 检出字节一致性（2026-10-27）

- 在独立 `feat/skin-first-separation` 工作树发现 `core.autocrlf=true` 会改变 Clear 规范源码字节，导致 APK 同源校验失败。新增 `.gitattributes` 固定既有 LF 文件和 CRLF 的 `clear-design.css`，现有 APK 未更改。`build_default_clear.py --verify` PASS，Studio 全量 40/40 PASS；范围与证据见 `docs/issues/PC-ISSUE-SEPARATION-CLEAR-CHECKOUT-EOL.md`。核心范围已获 Owner 冻结，Release QA/cutover 继续 HOLD。

## P02 — Today 相机规则裁决与统一（2026-10-25）

- 用户裁决“今日中午，重置 24 小时”。原生入口此前定位当前精确时刻并保留缩放，Web 入口定位本地中午并重置 24h；`TimelineCoordinate.todayCamera(nowMillis, zone)` 现复用初始相机，两个生产入口共用此转换，并在触发时更新时钟。
- 固定 instant / `America/Los_Angeles` 回归验证当地日期中午和 24h；`TemporalProtocolTest` **44/44 PASS**。另以 Robolectric Compose 驱动实际 `PowerCalApp`，先缩放/平移，再分别点击 native Today 与派发 Web bridge action，**2/2 PASS**。完整隔离离线门禁 **543/543 JVM、74 suites、88/88 tasks**，Android APK 组装、source boundary 通过，lint 0 errors / 53 warnings。日志及 APK 哈希见 `docs/issues/PC-ISSUE-P02-TODAY-CAMERA.md`。
- 没有运行 API 35 AVD/真机。保护中的并发验收 CSV 仍把旧 `REC-P1-002` 规则记录为 PASS，本轮不修改；该历史行不能代表新裁决下的签核。整体冻结保持 HOLD。

## PC-ISSUE-PHASE4-D05 — API 35 生产提醒进程与重启恢复（2026-10-25）

- 在隔离包 `dev.powerfulcalendar.d05qa572cf10` / `emulator-5562` 完成 production exact ALARM 的进程终止、force-stop 后用户重开、真实系统重启三条 AVD 路径。普通 kill 和 force-stop/reopen 均到期投递；系统重启后 logcat 明确显示 BootReceiver 被 BOOT_COMPLETED broadcast 拉起，AlarmManager schedule 恢复，随后 kill 进程仍保留闹钟并成功投递。
- 重启场景 instrumentation **1/1 PASS**：生产 store 为 `FIRED`、`powercal-alarms` 通知存在、没有推断完成状态。重启日志 `system-reboot-bootreceiver-logcat.txt` SHA-256 `06B06A9610A897E646157907E3AC99ED4EF4FCB11D042AB7B2237CBF4DCBE93A`；恢复/kill 记录 SHA-256 `C7EA96805FE8E42F43869C88E83B0EDBBDB95314F5C96B11309CB3D537B172A9`；delivery instrumentation SHA-256 `C4AEF64F274B9E3FF31BD5315A7A5E70CDFE35C7CA3B2B74E13444B14ADAD9CA`。force-stop/reopen delivery SHA-256 `83F4BA79D8AE8B14C81EC257463AC81E69AC06A0203C682609559811B9602E3C`。
- 最终完整离线 Gradle 门禁 543/543 JVM、74 suites、88/88 tasks、lint 0 errors/53 warnings 全通过；日志 SHA-256 `4710FC30C4B334ABA2E6B683DA8639FFC1E89FABB037D7CF434553AC80B4E184`。QA Debug/AndroidTest APK SHA-256 `8AB86E91FA465D2A7DE1701CA025E7A2EDFE555D2D4BED47703620214E47E967` / `C5E217F8AEADB2DF42F67B05A1C166551484CFE8BAC08EBEAB22C49510111895`。原版 PowerCal 数据库前后文件名、长度及时间戳不变；只卸载 QA 包和测试包。全部命令与边界见 `docs/issues/PC-ISSUE-PHASE4-D05-AVD-LIFECYCLE.md` Follow-up。
- 无生产提醒源码缺陷发现。实体/OEM 省电策略仍由用户验证；`REC-P1-086` 保持 BLOCKED，且台账旧摘要尚未更新（本提交为保留主工作区另一 Agent 的并发台账修改而未编辑 CSV）。核心冻结仍 HOLD。下一个可自主推进项：继续从重建矩阵挑选可在 JVM/API 35 AVD 覆盖的未签核项。

## PC-ISSUE-PHASE1 — 一次性 DST 回拨时间裁决与实现（2026-10-25）

- 用户明确裁决：一次性本地时间落在秋季 DST fold 重复小时，选择第一次出现（较早 offset）。据此更新核心合同；`DraftForm.parseInstantIn` 明确调用 `withEarlierOffsetAtOverlap()`，不扩展到一次性春季 gap 规则。
- 固定 `America/Los_Angeles` Clock 回归验证 2026-11-01 01:30 的两个 instant 相差 3,600,000 ms（08:30Z / 09:30Z），单次 ALARM、ACTIVITY、精确时间 DEADLINE 的解析与保存都落在 08:30Z；即使 Clock 捕获第二个 01:30，分钟文本按所有者裁决解析为第一次。`EditorDefaultsTest` 7/7 PASS；Phase 1 六个 suite 合计 **33/33 PASS**。
- 隔离 detached worktree 完整离线门禁 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain`：**543/543 JVM、74 suites、0 failures/errors/skips、88/88 tasks**；Debug / AndroidTest APK 组装通过，lint **0 errors / 53 warnings**。日志 SHA-256 `DA00445E7108CA7C22490AA8D2DB2E476B4A4BE1BE8F92BED83E7E6A9006A419`；`EditorDefaultsTest` XML SHA-256 `B40ECB24B159F010F4E17A6B73331337D1B8AC867EC4D5279420E47D6131C66D`；Debug / AndroidTest APK SHA-256 `F515E4194A8E9A0D1EE08917337FCC8D79A579EFCE27400047B056711440B787` / `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`。
- 没有运行 connected/AVD 或实体设备测试；本轮不把既有设备证据算作新复测。Phase 1 产品时间规则的 DST fold 缺口现已由用户裁决并经 JVM 回归签核；一次性 spring-gap 输入规则仍未新增定义，设备/OEM 测试仍由用户执行。总冻结继续 HOLD，其他未关闭矩阵项不受本次签核影响。证据与报告见 `docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md` 末尾本次 follow-up。

## PC-ISSUE-174 — 折叠标题完整无障碍语义（2026-10-25）

- 扩展 `TimelineTitleComposeTest.measuredGreedyTitlesFoldButEveryEventRemainsClickableAndAccessible`，断言可见 A/C 与折叠 B/D 事件框的无障碍描述均包含完整标题；同时保留 B/D Canvas 隐藏和 B 可点击打开事件的断言。只改测试，无生产 UI/几何变化。
- 定向 Compose/策略测试 **17/17 PASS**；完整隔离 Gradle **542/542 JVM、74 suites、88/88 tasks**，source boundary、Debug/AndroidTest APK、lint 均通过，lint 0 errors / 53 warnings。日志、XML、APK 哈希及范围边界见 `docs/issues/PC-ISSUE-174-folded-title-accessibility-semantics.md`。
- 只签核 Compose 语义树子项。`REC-P1-063` 仍 BLOCKED，待实际 TalkBack 播报与实体设备验收；本轮未触碰在线 AVD。核心冻结保持 HOLD。

## PC-ISSUE-173 — Glass-disabled native fallback readability（2026-10-25）

- 新增 Robolectric API 35 Compose 回归，显式令生产 `PowerCalTheme` 的 `blur=false`，验证浅/深主题回退到不透明 `surface`、高光清除、`onSurface` 对比度至少 4.5:1，内容可见。`GlassComposeTest` 17/17 PASS。没有产品样式或布局修改。
- 当前主工作树门禁：543/543 JVM、75 suites、59 tasks、Debug 与 lint 通过（0 errors / 56 warnings）；为避开受保护 D05 未跟踪编译错误，在隔离 worktree 重跑完整 88 tasks，542/542 JVM、74 suites、Debug/AndroidTest APK 与 lint 全通过（0 errors / 53 warnings）。哈希、失败尝试和测试边界见独立报告 `docs/issues/PC-ISSUE-173-glass-fallback-readability.md`。
- `REC-P1-074` 仅对 blur-disabled 主题渲染分支签核 PASS；系统 low-RAM detection 和真实设备/OEM 绘制未测。台账 218 rows / 217 active reconstructed：205 PASS / 0 FAIL / 10 BLOCKED / 1 NOT TESTED / 1 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2=145/71/1。Ledger SHA-256 `A743BDB7E5AB9F51A1636425CE29714AD61709515BAFF72AE9B89CB5A497C4E5`；validator artifact SHA-256 `7A3FA25C0C16CEED5F8566ED624FCC466B9E6AA7E7E6B4DE3B65C59BE29252A8`；validator unit tests 2/2 PASS。测试、报告和验收记录提交 `1d99d882e755c38eea082d97eceb275037cb2753` 已推送。Overall core freeze remains HOLD.

## PC-ISSUE-PHASE1 — 当前主线定向复验（2026-10-25，HEAD `d943ddc`）

- 当前主线重跑六个定向 suite：**32/32 PASS、0 failures/errors/skips**。Gradle 日志 SHA-256 `C8C9A0513F0FE782CDB721777D06F83F6735EF904966C951F39D411F1EACEB8A`；六份 JUnit XML 与 SHA-256 已记入 `docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md`。
- 没有 Phase 1 生产/测试源码变化；实现仍来自已推送 `b46b3dd7592bb1c7de021d543630f84937a0eaef`。本次 HEAD 相对 `3c8b16d` 仅有文档变化；同一 tracked app 源码在干净 worktree 的完整门禁为 540/540 JVM、73 suites、88/88 tasks，包含 AndroidTest APK 和 lint。此次只重跑 Phase 1 定向测试，没有触碰 AVD/实体设备。
- 秋季 DST fold 第二个同名时间的一次性输入语义仍未定义；不把重复事件的 first-offset 规则外推。已定义主路径 PASS，此边界保持 SPEC UNRESOLVED，因此严格签核及核心冻结继续 HOLD。

## PC-ISSUE-166 — PowerCalApp 随口建议控制器集成（2026-10-25）

- 新增 Robolectric API 35 Compose 回归，使用 loopback stub 驱动真实 `PowerCalApp` / `NativeAiChatScreen`。测试观察到可选创建草稿按钮，未触发结构化 interpret，草稿/正式事件/身份/序号/队列/commit 计数均不变；定向 **1/1 PASS**，关联 AI 回归 **16/16 PASS**。没有访问真实 provider 或使用凭据。测试源码、日志与 JUnit 哈希见 `docs/issues/PC-ISSUE-166-casual-chat-suggestion-button.md`。
- 干净 detached worktree 完整门禁 **541/541 JVM、74 suites、88/88 tasks**，含 Debug/AndroidTest APK 与 lint（0 errors / 53 warnings）。REC-P1-048 仅有 UI 与本地 controller 路径证据；live free-model classification 保持 **NOT TESTED**，PC-ISSUE-002 保持 PARTIAL/BLOCKED。
- 台账结构校验：218 rows / 217 active reconstructed；204 PASS / 0 FAIL / 10 BLOCKED / 2 NOT TESTED / 1 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2=145/71/1；historical IDs mapped=0。ledger SHA-256 `CB9AF259F8149F494090CD83F78550D7144113EF048122143C94017E529BAD03`；validator artifact SHA-256 `568DF6C3FD6A6853AC78976E7C990886980A892CFC28124ABC90CE048C3B549D`；validator unit tests 2/2 PASS。结构检查不是产品行为证据。
- 本次不触碰 AVD/实体设备；历史 API 35 screen-only 子项仍按原报告范围引用。独立报告为 `docs/issues/PC-ISSUE-166-casual-chat-suggestion-button.md`。集成补测、Issue 报告、台账和冻结判定证据已随提交 `80d672bdf58a1bc2fb488e5df59d692acfd6b6bf` 推送；P1-048 未关闭，总冻结 HOLD。

## PC-ISSUE-PHASE1 — 当前用户请求复验（2026-10-25，HEAD `3c8b16d`）

- 读取并遵循根目录 `AGENTS.md`。现场为 `main` / `origin/main` 同步；跟踪源码无未提交修改。普通新建入口继续共用内存 `NewEventEditSession`；生产实现仍由已推送 `b46b3dd7592bb1c7de021d543630f84937a0eaef` 提供，本轮未改产品源码。
- 当前工作树 Phase 1 定向回归 **32/32 PASS、6 suites、0 failures/errors/skips**。日志 SHA-256 `086E31B6BB0523DC4A5FDA6D627178A8F8687788058C1DE0095337D90A9DF025`；JUnit XML 位于 `.local/android-test/reports/freeze-20261025/phase1-user-request/focused-junit/`。
- 当前工作树全量 JVM 回归、source-boundary、Debug build 与 lint 均通过：**541/541 tests、74 suites、0 failures/errors/skips；59/59 本次请求范围内 Gradle tasks；lint 0 errors / 56 warnings**。该运行包含受保护的未跟踪 `DraftDeckFeedbackTest.kt`，但未暂存。日志 SHA-256 `47B76C79FE412556C10FC93CF326E09865664A62E2FA9D52C91F9E45B6977104`；JUnit XML、lint XML 和 Debug APK 副本在 `.local/android-test/reports/freeze-20261025/phase1-user-request/`。Debug APK SHA-256 `517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614`。
- 同一 HEAD 的先前干净隔离 worktree 全完整门禁（含 AndroidTest APK 构建）为 **540/540 JVM、73 suites、88/88 tasks，全部通过**，AndroidTest APK SHA-256 `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`；证据见本 Issue 报告上一节。当前主工作树不重复编译 AndroidTest：其中受保护的未跟踪 D05 测试有独立编译错误，哈希仍为 `4FA8637731FEE4F109E2E017D2D164635876BC0551B6C8FC74F99B8F7461050D`，保持未修改、未暂存。
- 本轮只读 `adb devices` 发现 `5554 / 5556 / 5560 / 5562` 均已连接，未触碰任何设备。API 35 `emulator-5560` 既有 Phase 1 Compose **2/2 PREVIOUS PASS**；已知的生产 Debug APK hash 与当前构建相同，且 tracked `app/` 路径从该证据基线 `68cad08` 起无变化。因此仅引用历史证据，不冒称本轮设备复测。实体设备仍由用户测试。
- 核心合同修订明确 DST “第一次 offset”只适用于重复事件，并注明一次性本地时间解析规则未定义。固定 Clock 落在秋季回拨第二个 01:30 时，现有无 offset 的 `yyyy-MM-dd HH:mm` 会在解析时回到第一次 01:30，不能保证与捕获 instant 相同。该点已向用户请求产品裁决；在裁决和所需回归完成前，Phase 1 的无副作用/保存主路径验证 PASS，但完整时间语义签核 **HOLD**，总体验收继续 **HOLD**。

## PC-ISSUE-PHASE1 — HEAD `72372ad` 当前请求复验（2026-10-25）

- 复核普通新建入口均使用内存 `NewEventEditSession`；实现代码仍来自已推送 `b46b3dd7592bb1c7de021d543630f84937a0eaef`，当前 HEAD 无 Phase 1 源码差异。
- 定向 Phase 1 回归 **32/32 PASS / 6 suites**。同一 HEAD 的干净隔离 worktree（不包含主工作树未跟踪文件）完整离线门禁 **540/540 JVM / 73 suites / 0 failures/errors/skips / 88 tasks**，lint 0 errors / 53 warnings；Debug / AndroidTest APK SHA-256 `517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614` / `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`。日志、哈希及测试命令见 `docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md` 与 `.local/android-test/reports/freeze-20261025/phase1-task-72372ad-8c41/`。
- 主工作区门禁受其他 Agent 未跟踪 D05 AndroidTest 的未导入 `assertFalse` 阻断；该文件未修改/暂存，受保护 untracked assets 均保留。API 35 AVD 证据已存在且 APK hash 匹配，本轮未启动设备；实体设备仍由用户执行。
- 单次 fall DST fold 同名分钟的精确 offset 规则未定义，留为 SPEC UNRESOLVED；Phase 1 主路径测试与构建 PASS，核心冻结保持 HOLD。

## PC-ISSUE-164 — stale replacement diff and mobile review (2026-10-25)

- Core `DraftDiff` data fix remains in `f959bcc`; API/Compose review exposed that `snapshotRows` still omitted reminder method/timing, completion tracking/slots, and recurrence active-date bounds. Added those fields and Chinese reminder/frequency/weekday labels; the conflict dialog now exposes 25 complete rows.
- Final API 35 `emulator-5560` AndroidJUnitRunner **1/1 PASS**: each of the 25 labels and before/after values scrolled into view; formal record remained unchanged before explicit confirmation and advanced exactly one revision after tapping “确认整份覆盖”. The test used a randomized isolated database and removed it in `finally`. Top/bottom screenshots are stored under `.local/android-test/reports/freeze-20261025/pc-issue-164-mobile-review/`.
- Full forced-offline Gradle **541/541 JVM tests, 74 suites, 0 failures/errors/skips, 88/88 tasks**, lint 0 errors / 56 warnings. Log SHA-256 `3BCA1C55B5350479176B05FDF84D771352AEE4CD396322CF4D08ECD5141ED95E`. Final API 35 log SHA-256 `3AB94505AB84610B68A5148971C685063F5DC68371055DAB08497AAC29EDBB8E`. Debug/AndroidTest APK SHA-256 `517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614` / `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`; installed APK bytes were pulled from the emulator and matched.
- Code/test commit `9f9e82ae583a5d582e5b8b4307db98ec5606f149` is pushed. Independent report: `docs/issues/PC-ISSUE-164-stale-replacement-diff-core.md`. `REC-P0-049` is now PASS. Structural ledger validation: 218 rows / 217 active reconstructed; 204 PASS / 0 FAIL / 10 BLOCKED / 2 NOT TESTED / 1 SPEC UNRESOLVED / 1 RETIRED; P0/P1/P2 = 145/71/1; historical IDs mapped = 0. Ledger SHA-256 `19002F65EA072B2B6FD266F61DF895DF9F0DDF316D0F0E6F38272EB3D94426FB`; validator JSON SHA-256 `B5C65D501F31958D0FA557994CF007AB6A56DD64EDBF696AF58B24011B51BA52`. Structural validation does not substitute for the device test.
- Physical/OEM review remains user-owned and other freeze blockers remain open; the overall verdict stays **HOLD**.

## PC-ISSUE-PHASE1 — 当前集成版本 API 35 AVD 复验（2026-10-25）

- 在新建隔离 `emulator-5560` API 35 上运行 `NewEventEditorLifecycleInstrumentedTest`，2/2 PASS：四类编辑器打开/× 退出无草稿、事件、身份、序号、队列或 commit 增量；修改 Memo 标题后退出恰好一张可恢复草稿。测试使用随机测试库，测试代码 `finally` 清理；测试后未发现 `pc-phase1-*` 隔离库残留。此前 5554/5556 未触碰。
- AVD 安装的 app/test APK hash 与当前构建逐字节一致：`30C3AAED678F3569AB2ECB350A5298236EE37BA6DE27C169714D63222D9F5964` / `F55837EA885CE7B72FEE3D67AC011A6EAD1237F3A8DEAEFB9806550FD550E5CC`。设备日志 SHA-256 `B288A0BF52E989362CF8F4E7008C3880734BFD54DE7A696050A17994A5F3BD0F`。
- 当前完整 Gradle 集成门禁 540/540 JVM、74 suites、0 failures/errors/skips、88/88 tasks；Phase 1 六个 suite 32/32 PASS，lint 0 errors / 56 warnings。完整日志 SHA-256 `CBE1B167E33551AB2455CE07E3A9D68D42A2A7E09AAB169182442EFF3E35F60D`。产品实现仍为 `b46b3dd7592bb1c7de021d543630f84937a0eaef`；详情及证据目录见独立 Phase1 Issue 报告。
- `REC-P1-087` 已记录本次 AVD 子项；当前 ledger 为 218 rows / 217 active reconstructed，203 PASS / 0 FAIL / 10 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED，历史 ID 映射 0。Ledger SHA-256 `4381A8E51CE74E184C2AC5D0C99EEFCCC629548258EFB4F44F86A2E9C3B5CC03`；validator JSON SHA-256 `D3D3E2DE077E2B2209F0863D965038C59A17319D093BB6B5731588B9ABF2DC94`；这是结构校验而非产品行为测试。
- 实体/OEM 与秋季 DST fold 精度风险仍未签核，整体 HOLD。

## PC-ISSUE-172 — 多轮任务交接与显式审查（确定性路径 PASS；Provider BLOCKED）

- 修复跨轮信息丢失：结构化生成接收带原始时刻/时区和稳定证据 ID 的任务快照，assistant 内容仅为不可信建议；同名候选保留独立身份，审查选择、草稿写入与正式发布分离，并以事务收据防止重放重复写入。
- 红绿测试发现并修复拒绝语义缺陷：裸“不用”不能继续一个待创建任务，但在上一轮明确询问重复设置时可作为修正。旧逻辑的隔离回归 1 项 FAIL；修复后定向多轮测试 PASS。
- 完整离线 Gradle **540/540 JVM、74 suites、0 failures/errors/skips、88/88 tasks**；lint 0 errors / 56 warnings。真实 PowerCalApp Compose + loopback fake HTTP 在新建 API 35 `emulator-5560` AndroidJUnitRunner **1/1 PASS**：审查前零写入；屏幕五个同名候选；明确写草稿后恰五个唯一草稿，正式事件/提交仍为零。截图、日志和 APK hash 见 `docs/issues/PC-ISSUE-172-ai-multiturn-intent-handoff.md` 与本地 verification manifest。
- 不可提供的 Provider endpoint/credential 使真实免费/DeepSeek API 场景保持 BLOCKED；未读取任何密钥或请求 Provider。实体设备由用户执行。新 AVD 未使用既有含数据的 AVD；5554 数据库前后 hash 不变。整体冻结 HOLD。
- `REC-P1-089`（本地确定性任务交接）PASS；`REC-P1-090`（真实 Provider 路径）BLOCKED。历史 ID 未恢复，二者均为 RECONSTRUCTED。独立 Issue 报告：`docs/issues/PC-ISSUE-172-ai-multiturn-intent-handoff.md`。
- 新矩阵结构校验：218 rows / 217 active reconstructed；203 PASS / 0 FAIL / 10 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2=145/71/1；历史 ID 映射 0。Ledger SHA-256 `4381A8E51CE74E184C2AC5D0C99EEFCCC629548258EFB4F44F86A2E9C3B5CC03`；validator JSON SHA-256 `D3D3E2DE077E2B2209F0863D965038C59A17319D093BB6B5731588B9ABF2DC94`。结构校验不替代行为测试。

## PC-ISSUE-PHASE1 — E01 工作树叠加回归（2026-10-25）

- 为覆盖当前集成状态，在 `main` / HEAD `8d1a204bb2905956896db092ee7a62922b28c08d`（与 `origin/main` 同步）且 E01 尚有本地未提交变更时重跑 Phase 1：定向 **32/32 PASS、6 suites**；完整离线门禁 **539/539 JVM、74 suites、0 failures/errors/skips、88/88 tasks**；lint 0 errors / 56 warnings。完整日志 SHA-256 `28D29F652321BB4DD54018D12BA1CC349BBC363E3F32A6AFF4DA1DD4213F7EC8`，定向日志 `62D70DF908D9628C3814F705F8DBE12D781BFFDDC0D9E0F9138D1A7B9F243D4B`。
- 本次没有修改 Phase 1 产品源码；E01 的 PowerStore / PowerCalApp 变更参与编译和 JVM 回归，工作树指纹在 `.local/android-test/reports/freeze-20261025/phase1-current-e01/verification.json`。Phase 1 自动化集成回归 PASS；既有 AVD 私有数据恢复问题未解决，本次不运行 AVD，设备复测仍 BLOCKED；实体设备由用户测试，核心冻结 HOLD。
- 独立报告：`docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md`。Phase 1 可关闭；Phase 2 / 3 已分别交付，继续开放冻结项不受影响。

## PC-ISSUE-PHASE1 — 当前 HEAD 复验与验收签核（2026-10-25）

- 当前 `HEAD 7ceb7fa8b65aefd4174cf0420d1d166486c89484` 的生产源码与已审计的 `6ddf9b9` 树一致；Phase 1 产品修复已由 `b46b3dd7592bb1c7de021d543630f84937a0eaef` 提交。本轮只新增 Compose 重组回归测试。主工作区 E01 AI 多轮未提交修改及受保护资产未进入 Phase 1 隔离构建。
- Phase 1 定向回归 **32/32 PASS、6 suites、0 failures/errors/skips**；完整离线门禁 `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain` **522/522 JVM、69 suites、0 failures/errors/skips、88/88 tasks**；lint **0 errors / 53 warnings**。Debug / AndroidTest APK SHA-256：`1601F2DB169A8C889791EBFE4149F4383E71980474452C8CFE22BA9A7C92A9EC` / `9FAA01339612CA2DF1871CD047512C282F19339C1E5A7766E4E512E80B0FDFFD`。
- 最终证据位于 `.local/android-test/reports/freeze-20261025/phase1-final-7ceb7fa/`：定向日志 `44DB388BEFCECE2555E8273F8F537FF31278CED3F08749FEB3D3A8365C6D41C9`；完整门禁日志 `24DF06304B35356240BCC986F53583E714800C5E467E3A944BB34213C8206663`；完整 JUnit XML 69 份、定向 XML 6 份；lint XML SHA-256 `9E719BBDAD89ED5B0D6DC54420AE52EB66DE7E1D90C7FDE113BE5D5B02324C50`。重组回归测试源 SHA-256 `5F712C595DB846014A2BD19CD1BD2DBC18F110610FF11E91F766388E0C82EE5D`；首轮语义断言失败 XML 保留在 `failed-attempt-junit/`，修正后测试通过。
- 本轮未启动既有 AVD 或运行 connected 任务：两台 AVD 的私有数据库缺失/恢复处置仍未闭环；历史 API 35 Compose 2/2 仅作为之前的证据，不计为本轮复测。实体设备由用户测试。秋季 DST fold 的同名墙上时间精度风险仍保留；Phase 1 自动化范围 PASS、AVD 新复测 BLOCKED，整体冻结 HOLD。
- 验收矩阵补入 `REC-P1-087`（瞬态新建会话与幂等持久化）和 `REC-P1-088`（统一默认时间与精度），均为 `RECONSTRUCTED / P1 / PASS`，未伪称恢复历史 ID。结构校验：216 rows / 215 active reconstructed；202 PASS / 0 FAIL / 9 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2=145/69/1；历史 ID 映射 0。ledger SHA-256 `3A40691D66AF8391543FA82EBB0C2156EABAA3848F00ED51164C491C3FD0AA0A`；validator JSON SHA-256 `80F1018770C595CCAE4AC34461A66ED4A53791CA699CE49490A6832C4B2CF15F`。结构校验的 `product_behavior_tested=false` 不代表上述 Gradle 行为测试未执行。
- 独立 Issue 报告与审计计划均补入当前复验；产品源码修复提交 `b46b3dd7592bb1c7de021d543630f84937a0eaef`，本轮另加重组回归及报告/矩阵变更，将单独提交。

## PC-ISSUE-PHASE4-D05 — Android 15 AVD 生命周期分类（BLOCKED / PARTIAL）

- API 35 `emulator-5556` 隔离包探针观察到 Home、Recents 滑除及进程不存在时 marker 正常投递；force-stop 仅取消测试包 PendingIntent，重启该包后旧 alarm 未恢复，手动重新排程后投递成功。探针没有使用 PowerCal 生产提醒链路，不能据此判生产 PASS/FAIL。
- PowerCal 实际提醒、生产 BootReceiver 系统派发、系统重启及 OEM 省电策略仍未测试；实体设备由用户完成。Issue 报告：`docs/issues/PC-ISSUE-PHASE4-D05-AVD-LIFECYCLE.md`。探针源/manifest/报告提交 `de8a6723b02ea93fc77d826a3500803481c62639`。
- 台账新增 `REC-P1-086`，保持 BLOCKED。最新结构校验为 214 行 / 213 活动重建项，200 PASS / 0 FAIL / 9 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2=145/67/1，历史 ID 映射 0，`product_behavior_tested=false`。ledger SHA-256 `B010A4585FF55CA94C76CA9FF6791997F24B3BF16B7F07D70495CC4DDCC53892`；validator JSON SHA-256 `E5153E2CEC176CD4ED32B516556447158313A07D5A6FCDD08AE286A16CBA4B15`。
- 校验器之前硬编码 2026-10-24，阻止新增当前日期风险条目；现在按合法 ISO 日期校验并新增边界测试。2/2 unittest PASS；修复提交 `b4335b85763e450605e46d150672fa8d45f5093a`。
- D05 完整 Gradle 522/522 JVM tests、70 suites、0 failures/errors/skips、88 tasks；lint 0 errors / 56 warnings。该次构建包含合成 AndroidTest 探针，不覆盖生产提醒行为。整体冻结仍 HOLD；继续下一项可自主验证的冻结工作。

## PC-ISSUE-PHASE3-D01B — Draft Deck 输入法下的发布/丢弃操作

- 根因：键盘缩短对话框后视口仍大于 320dp；旧逻辑只在低于该阈值时滚动整张卡片，导致长表单内层滚动、卡片底部操作被裁出视野。现在 IME 可见即滚动整卡，且内层表单禁用竞争滚动；无 IME 的原 320dp 小屏规则不变。
- API 35 `emulator-5556` 标准 1080×2400 视口发布/丢弃 2/2 PASS；短 1080×1600 视口发布/丢弃 2/2 PASS。短视野操作 bounds：publish `Rect(553,644,980,770)`、discard `Rect(100,644,527,770)`，IME 顶边 y=791；标准视野操作底边 y=1559，IME 顶边 y=1580。D01a AI 聊天 IME 回归 1/1 PASS；既有 compact-screen Compose 回归 1/1 PASS。
- 完整离线 Gradle：522/522 JVM tests、70 suites、0 failures/errors/skips、88/88 tasks；source boundary、Debug/AndroidTest APK、lint 均通过，lint 0 errors / 56 warnings。日志 SHA-256 `B0B265DD93B7722E9DA39D6048F7B7975B3BB3828632C973C001C5357281A5FF`；短视野 AVD `DA465482FDD07DBBF1DC74DAE8A4E28E45915DD65894C35565DBF363E01C4577`；标准视野 AVD `19A546E617259FDB91F0C0A030C161EFEF10E703FA53437D03506D20BFE9EB5D`；D01a 回归 `4E745962F30A515F545FD2D5936FE7278F4C5DD396163ECDA8B78A4DC8E2501E`；几何轨迹 `A126603BC7795E29FE1B65F8A1E03A516786C4C7FEAC508A64986D956A000657`。
- 源码、Instrumentation 回归与独立报告提交 `ab7b71a7670a5853ff6ad715eb45e59819375cc7`，已推送 `origin/main`。报告：`docs/issues/PC-ISSUE-PHASE3-D01B-IME-PUBLISH.md`。原 AVD app/test APK 按哈希恢复，显示尺寸/密度复原，既有 DB 文件清单未变；随机测试库已清理。实体设备未测，仍由用户验收。
- 台账新增 `REC-P1-085`（RECONSTRUCTED，范围限 API 35 AVD）。结构校验：213 行 / 212 活动重建项；200 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2=145/66/1，历史 ID 映射 0。ledger SHA-256 `A37773DDEACD4E299CFF930413C2DF364EA93966FB436D38B10173B51778144F`；artifact SHA-256 `14705CDA82B553DF8FAE5B2A1FA0291E675C9FE3F71523925C9E1D2C977810CE`；结构校验的 `product_behavior_tested=false` 仅指校验器不执行产品行为。
- 下一项：Phase 4 D05，分类验证提醒在退后台、划除最近任务、强行停止、省电管控和系统重启下的行为；真机/OEM 条件由用户测试，不从 AVD 推断。

## PC-ISSUE-PHASE2 — 重复事件编辑范围与 occurrence key 路由

- 根因：timeline/month/Memo 投影的 original occurrence key 未进入正式事件编辑会话；编辑器没有显式 single / this-and-future / whole-series scope。接通 `DisplayOccurrence → EditSession` 路由与三种范围；scope/key 随正式编辑 checkpoint 恢复，禁止通用 draft publish/AI batch 把范围表单当作普通候选。
- 修复边界：单次仅能改标题/时间并写指定 occurrence override；后续范围走原子拆分；整组沿用 revision 栅栏。选中实例已有标题 override 时，新系列以实例有效值为锚点并丢弃该选中槽位旧 override，避免旧值遮盖新系列字段。保存成功时提升 Compose editor revision，刷新可见基线与按钮状态。
- Robolectric 关键测试 9/9 PASS（日志 SHA-256 `4A63F4950C317DD71C245CCAA6AD79AA61BD29381B0AABBD7F3220D00714C82D`）；附加边界组 5/5 PASS（`060017A30AB39B5A9A464EF066EB3E319DE62777F963711D67D441D19F911351`）。API 35 `emulator-5554` 实际 Compose scope/save 回归 1/1 PASS（日志 SHA-256 `AFE9E30CE432EED75BA4F70CA23CA8D42254289C292B36E6AAFB3A071BF84AFF`）。随机 SQLite DB 已删除；app/test APK 均恢复运行前 SHA-256，DB 文件名清单摘要前后同为 `1339CD66F7353794C16E08D6BCC0E55E958C1FFD36D740CD19CC934792B113EA`。
- 最终完整 Gradle：522/522 JVM tests、70 suites、0 failures/errors/skips，88/88 tasks；source boundary、Debug 和 AndroidTest APK、lint 通过；lint 0 errors / 56 warnings。完整日志 SHA-256 `42C63E29348DD23EAAFDC44EE0C2DD578D0F5F57DA091C6B80C8056444A01858`；Debug APK `90BB816EC85AFD46C195C48697F1234D98D2C4571FB5209F0F1FD87B088DBDE8`；AndroidTest APK `5692FBC5C964C63CAF61EEED71F4BA51390282C3125729F1F24210944CBAA3F3`。
- 核心实现提交 `9036a2a8db2e1a669f64cc6174acdc569c4c08b3`。独立报告 `docs/issues/PC-ISSUE-PHASE2-RECURRENCE-SCOPE-EDITOR.md`。`REC-P0-127` 新增为 RECONSTRUCTED 并签核本环境的自动化/API35 范围；REC-P0-078/079/080 原有窄化核心条目范围不变。实体设备留给用户；timeline 到编辑器在 Robolectric 覆盖，AVD 用例未启动主界面，整体冻结仍 HOLD。
- 台账结构校验：212 行 / 211 活动重建项；199 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2=145/65/1，历史 ID 映射 0。台账 SHA-256 `FFBD395A80D864BB8ED9578A561B6188DD3ED35B88048E0EC31F2AC7951909A1`；artifact SHA-256 `09879FB7A8671E7C5DE02C69BEBF30A8B10751EE27EB576B91C3239FC1DAFC0F`；仅结构验证，`product_behavior_tested=false`。
- Phase 3 D01b 已在 API 35 AVD 完成并独立签核，见本节与 `REC-P1-085`；当前继续 Phase 4 D05。

## PC-ISSUE-PHASE1 — 编辑器生命周期与默认时间统一

- 2026-10-25 当前提交 HEAD `d600aafd82024dd589ca708a957b8678ca3092ac` 隔离复验：定向 31/31 PASS；完整离线门禁 521/521 JVM、69 suites、0 failures/errors/skips、88/88 tasks；lint 0 errors / 53 warnings。Debug / AndroidTest APK SHA-256：`1601F2DB169A8C889791EBFE4149F4383E71980474452C8CFE22BA9A7C92A9EC` / `9FAA01339612CA2DF1871CD047512C282F19339C1E5A7766E4E512E80B0FDFFD`。本轮未运行 device task；此前 API 35 结果不是本轮复测，两台既有 AVD 的数据库恢复/处置仍未闭环。日志与完整记录见 `docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md`、`.local/android-test/reports/freeze-20261025/phase1-review-d600aaf/`；Phase 1 产品源码未改，数据保全风险仍 HOLD。
- 根因：普通时间轴、月份、Memo、PowerCal Web action 与 WebSkin bridge 的新建流程在打开编辑器前直接 `newDraft`，立即分配草稿/事件身份与队列序号；各入口默认时间规则不一致。现改用内存 `NewEventEditSession`、按业务语义 Diff、幂等 checkpoint/close/save，并由 `EditorDefaults(Clock, ZoneId)` 统一四类默认值。完整修复范围与根因映射见 `docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md`。
- API 35 `emulator-5554` 实际 Compose 测试 2/2 PASS：四类事件打开并按 × 退出无持久化副作用；真实修改 Memo 标题后按 × 恰好一张草稿。使用随机隔离 SQLite 并核对清理；原 app/test APK 在 AVD 测试后按 SHA-256 恢复一致。`emulator-5556` 未触碰；实体设备留给用户测试。
- 定向测试 28/28 PASS（6 suites）；完整离线 Gradle 514/514 JVM tests、70 suites、0 failures/errors/skips，88/88 tasks，source boundary、Debug/AndroidTest APK、lint 均通过；lint 0 errors / 56 warnings。最终日志 SHA-256 `E2476CF1BCF9A9D2A99FC3641B05ADDF58899A08ADD791C30979586CE0F7CDA8`；定向日志 `D01A3DA9F7CFAAB8E27FA8A8172859946567BAC60CAB4B2DA292CF60F08EF4E6`；AVD Instrumentation 日志 `E63216AB319C4A8FB5715892874670FE11E8CE1B7055026239707F9AB117AF50`；Debug APK `3A066D385FF3A327DC756B86C410FBA4EF31E8C9DF4C9B0C91DEF6734453C359`。证据位于 `.local/android-test/reports/freeze-20261025/pc-phase-1-editor-lifecycle/`。
- DST 秋季回拨的同名墙上时间不能由现有分钟文本区分，保留为未签核风险；本轮不扩大时间持久化格式。Phase 1 自动化/AVD 范围 PASS，Phase 2 可开工；整体核心冻结仍 HOLD。实现提交 `b46b3dd7592bb1c7de021d543630f84937a0eaef`（本条提交补录）。
- 2026-10-25 当前基线复核：HEAD `1d8d437117344da36d13674fd3bf00607c27a76c` 上定向 **31/31**、完整 Gradle **522/522 / 70 suites / 0 failures/errors/skips / 88 tasks**、API 35 `emulator-5556` Compose **2/2** 均 PASS；lint 0 errors / 56 warnings。原 APK 已哈希复核恢复，隔离 DB 文件名清单前后相同。复核日志、APK/测试证据与残余风险见 `docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md` 的“当前基线复核”节；仅更新了证据，无 Phase 1 产品源码变更。实体设备与 DST fold 风险不变，整体核心冻结 HOLD。
- 2026-10-25 再复核 HEAD `5e2b25ac5760957089254c13cd6dbbdb080b6269`：只读入口审计未发现普通新建绕过内存会话；定向 31/31、完整 Gradle 522/522 / 70 suites / 88 tasks、API 35 `emulator-5556` Compose 2/2 PASS，lint 0 errors / 56 warnings。随机隔离测试库已由用例清理，原 app/test APK 哈希恢复一致、数据库文件名清单前后相同。完整日志 SHA-256 `C37EFB1B531EF22AD10648D689205888808B069122C5882A8CF2EEB56CEAB4BF`；定向日志 `91C004887150454A89EF82D45E728EF76B189A5332708DD4315E80321D788803`；AVD 日志 `A31D254958F69C6B206A6787341280C5DB3E902E99829EB79FD2CC144E3D4548`。本次仅补充基线证据，没有 Phase 1 产品源码变更；报告详见 `docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md`。实体设备与 DST fold 仍未签核，核心冻结保持 HOLD。
- 2026-10-25 HEAD `694ba1b48c8df502b4a911367217ee2dac2538d1` 复验：与 `d600aaf` 的 `app/` 源码一致。Phase 1 定向命令 BUILD SUCCESSFUL，完整测试 XML 中六个相关类 31/31 PASS；完整离线门禁 521/521 JVM、69 suites、0 failures/errors/skips、88/88 tasks，lint 0 errors / 53 warnings。Debug / AndroidTest APK SHA-256：`1601F2DB169A8C889791EBFE4149F4383E71980474452C8CFE22BA9A7C92A9EC` / `9FAA01339612CA2DF1871CD047512C282F19339C1E5A7766E4E512E80B0FDFFD`。本轮未运行设备任务；AVD 私有数据库恢复/处置未闭环，实体设备仍由用户验证。日志与 JUnit XML 见 `.local/android-test/reports/freeze-20261025/phase1-final-head-694ba1b/`；报告已更新，Phase 1 代码及 JVM/构建 PASS，AVD 新复测 BLOCKED，核心冻结继续 HOLD。

## PC-ISSUE-001 — Google Calendar ICS 导入阻塞复核（REC-P1-081 仍 BLOCKED）

- `emulator-5556` API 35 上重新启动现有 DST ICS fixture，Google Calendar 的两页 welcome 已通过；随后自动进入 Google Play services `PreAddAccountActivity`，停留在 `Checking info…`。按账号/登录边界立即停止并返回 launcher；未查看账号内容、未登录、未确认导入或保存，Calendar Provider 仍为 0 calendars / 0 events，PowerCal 未启动。
- 原 fixture SHA-256 `9F9E973963C6AA3ADA592E9CA3C1661F87FE3F8A1AD33227ED7C628673353E68` 未变；本次 summary SHA-256 `23A95B2BAFF00CF2472C838091C195994BF4AAA2D606E79518BDE3AD5A8D51BE`。Google Calendar welcome 状态可能已在 5556 上前进；未清除其 app data。
- 该 client 要求添加账号，外部 ICS 导入/再导出仍无法自主完成，REC-P1-081 / PC-ISSUE-001 保持 BLOCKED / PARTIAL，整体 HOLD。详情追加在 `docs/issues/PC-ISSUE-001-ICS-recurrence.md`。
- 台账 r41：211 行 / 210 活动重建项；198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2=144/65/1，历史 ID 映射 0。artifact SHA-256 `6FA734E602F2B083F646EBC81994CAFE4837C6BEE20C39148186C69CC3425F77`，ledger SHA-256 `4E070094E3A150D6C12797BB13142C8080E199A13CDC1173621F3622AD7FED5E`；这是结构校验，`product_behavior_tested=false`。

## PC-ISSUE-171 — ReminderReceiver onReceive/goAsync 生命周期（REC-P0-012 子项 PASS）

- Robolectric API 34 通过真实 `ShadowBroadcastReceiver.onReceive` 派发给生产 `ReminderReceiver`，注入 `PendingResult` 并确认进入 `goAsync()`；等待 Future 完成后，隔离 SQLite 中 `FIRED` 已记录、提醒 intent 已消费、completion keys 仍为空、revision 不变。仅测试 JVM sandbox；生产 manifest 解析和实体 OS 派发未覆盖。PC-ISSUE-170 另有 API 35 AVD AlarmManager 投递证据。
- 定向测试及 JUnit XML 均 1/1 PASS；日志 SHA-256 `910116087C1346F09CA42385BE3BB8C2AD8FCF52C83081F07A35A1EC1B2BAA1D`，XML SHA-256 `FB2392D980D0C9FEF72E247CE4203DC9A49A19B5B73C46C57127641DBE42E46B`。完整 Gradle 497/497 JVM、67 suites、0 failures/errors/skips；88/88 tasks 执行，lint 0 errors / 58 warnings；完整日志 SHA-256 `8911CC859F7418CE501C66F2D1B4665735F00175AB5E10EB055E1879F668F404`。
- 测试提交 `3bcb6ef86b313ca33d7df77155c04dc70f8b3318` 已推送。独立报告 `docs/issues/PC-ISSUE-171-reminder-onreceive-async-lifecycle.md`。REC-P0-012 保持 PASS；manifest resolution、真实系统投递到 manifest receiver、实体/OEM 与长时稳定性仍未签核。
- 台账 r40：211 行 / 210 活动重建项；198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2=144/65/1，历史 ID 映射 0。artifact SHA-256 `C4C13745AEE22C9D209C1E6B6FCCD14E6847B4C503A4C886097A59210F5AC736`，ledger SHA-256 `E426C141D8C3DEDD15AD0EE2170518B32B369E6B0570A62096AB6635488D535A`；`product_behavior_tested=false` 仅表示结构校验器不执行产品行为测试。整体继续 HOLD。

## PC-ISSUE-170 — API 35 Android AlarmManager 投递探测（REC-P0-012 子项 PASS）

- 在 `emulator-5556` API 35 用真实 `AlarmManager.setAndAllowWhileIdle` 安排带随机 action 的广播 PendingIntent；OS 将其投递到产品进程中的非导出动态测试接收器，接收器调用生产 `ReminderReceiver.handle()`，使用产品 cache 中随机隔离的 SQLite。AndroidJUnitRunner 1/1 PASS：`FIRED`、通知已发布、`completedKeys` 仍为空。此证据不覆盖生产 manifest `ReminderReceiver.onReceive/goAsync` 包装层、实体/OEM、Doze 专项或长时稳定性。
- 定向运行 68.578 秒；trace：`eventSeq=968254649`，trigger `1792845060000`，接收 `1792845089335`。完成后临时 cache DB/sidecar/目录、通知和 PendingIntent 均清理；cache 为空、无对应待执行 alarm、通知权限恢复 denied、exact-alarm app-op 保持 default。未触碰日历业务数据库。
- 全量强制离线 Gradle 496/496 JVM、67 suites、0 failures/errors/skips；88/88 tasks 执行，lint 0 errors / 58 warnings。完整日志 SHA-256 `1079FAEC6CE85C420268DBB7B2384CA57CD4BC346AE625D073C6912B96052B1C`；定向日志 `5315100F6B37DD5415B0D71BB56A5D7324738C370B5396710E9BBB0AC5C85C84`；设备 trace/清理证据 `3AF33A778F861F765CE0D82B5AD892A6E20251FCBCB30555ED7CF0B086C1ED61`。
- 测试提交 `93c08635b6a4b1c81b7c60f3f7194e6df1178577` 已推送。独立报告 `docs/issues/PC-ISSUE-170-android-alarmmanager-delivery.md`。REC-P0-012 仍 PASS，范围限制如上；整体冻结继续 HOLD。
- 台账 r39：211 行 / 210 活动重建项；198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2=144/65/1，历史 ID 映射 0。artifact SHA-256 `AF328E0699039CBAD064B0D115A9148AD57FB5C30A4F2552844D79EC8ED23022`，ledger SHA-256 `AC66D9950F8FF95129018843B1EE23297C41783BEFDFCE3430AE4D0B30CA4246`；结构校验标志 `product_behavior_tested=false`，不代表此项产品行为测试结果。

## PC-ISSUE-169 — 系统字体 2× API 35 AVD 子项（REC-P1-069 仍 BLOCKED）

- 新增 AndroidJUnitRunner 回归，在 `emulator-5556` API 35 实际将 `Settings.System.FONT_SCALE` 从 `1.0` 临时设为 `2.0`。生产 `TimelineScreen` 的 `Resources.configuration.fontScale` 与 `LocalDensity.fontScale` 均为 2.0；Today、Memo、待处理、新建、缩放控件都可点击且完整位于 1080×2400 px 视口内，新建菜单的 Activity 选项也成功。测试后原值 `1.0` 已恢复并复核。未改生产代码或业务数据。
- 定向 AndroidJUnitRunner 1/1 PASS。最终坐标轨迹包含全部六个操作控件的语义边界；详见 `docs/issues/PC-ISSUE-169-system-font-scale-avd.md`。定向日志 SHA-256 `70CD58CA2FADA2476D34ABD8D7C7272783F5CE17BD97F249C7FE202EA5D88169`；几何/状态 SHA-256 `87483817A4AFDF40BE0973C8F0DA8278A3E25512B09E6E8D27E1EE9654126947`。
- 全量强制离线 Gradle 496/496 JVM、67 suites、0 failures/errors/skips；88 tasks 全部执行，源码边界、Debug/AndroidTest APK 与 lint 通过（0 errors / 58 warnings）。完整日志 SHA-256 `CD68BC0C2AD4ED2D882AE0E31051C9CFF75C604E0477972DD2AC935904FD7EEF`。测试提交 `97bfead75f0c8b2d2c3de21be8d05495b751c7cc` 已推送。
- 台账 r38：211 行 / 210 活动重建项，198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；历史 ID 映射 0。artifact SHA-256 `4B3A90A6F20F85A111568D4E19139D06074B1402C65D957102B41E6238D3BF16`，ledger SHA-256 `0EFEB757A68C14D614B111F53E8C59E6E2174F22E7269BC57B0C6FF627156E16`，结构校验标志 `product_behavior_tested=false`。实体/OEM 与 MainActivity 验收未完成，REC-P1-069 保持 BLOCKED，整体冻结 HOLD。

## PC-ISSUE-168 — 系统减少动态效果 AVD 验证（REC-P1-075 子项 PASS，整项 BLOCKED）

- 新增 AndroidJUnitRunner 测试验证 `emulator-5556` API 35 的实际 `Settings.Global.ANIMATOR_DURATION_SCALE=0`：`rememberSystemGlassEffects` 报告 `reduceMotion=true`、`blur=false`；`glassMotion()` 探针推进 Compose 测试帧后到达目标 1.0。最终定向 1/1 PASS。AVD 原设置未显式设置（`null`），临时置 0 后在 `finally` 中删除，之后读回 `null`；无生产代码或业务数据修改。
- 完整强制离线 Gradle 496/496 JVM、67 suites、0 failures/errors/skips；88 tasks 执行，源码边界、Debug/AndroidTest APK 和 lint 均通过。定向日志 SHA-256 `D4FABB8F940B07680590E38E5D84B27F37D8FF29D23F9451A1866E2F53FBD59F`；状态轨迹 SHA-256 `9B3F4D97AE2F95CAA1FA3A9DE01F861479FB1D4B5D273C48E1CC86A9E9AFE94E`；完整 Gradle 日志 SHA-256 `E71C621C06FDBFF38B17611CC7DB26D9E836295BC1E8BD327880323DEBF03479`；Debug/AndroidTest APK SHA-256 `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1` / `2BD1C17F32722F588B1E6DE70AEAF98E174B73861C08BE93F6007AA656AEDE82`。
- 测试提交 `4dfaff99fb27c47f98b01125f94fe0629dd15df7` 已推送。独立报告 `docs/issues/PC-ISSUE-168-reduced-motion-avd.md`。实体/OEM 行为仍由用户验收，REC-P1-075 保持 BLOCKED。台账 r37：198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；artifact SHA-256 `3BCC9875D23E0591AC52760E05DA71EE830DA8208E850B0DF1C4C33C681EE075`，ledger SHA-256 `F458A02ED1C04980E90A8EE6A221D6DDA5B06A7E15CBDE100707D997E4CDD1C2`。整体仍 HOLD。

## PC-ISSUE-167 — 当前构建 Android IME 回放（REC-P1-070 子项 PASS，整项 BLOCKED）

- 在 API 35 `emulator-5556` 当前构建重跑既有 `AiChatImeInstrumentedTest`，AndroidJUnitRunner 1/1 PASS。竖屏 1080×2400，LatinIME inset 820px、顶边 y=1580；消息区高度从 1858px 缩至 1101px，合成消息可见；编辑器在键盘上方；启用的发送按钮底部 y=1559，距 IME 21px。无生产/测试源码改动，不触碰 `PowerStore` 或 AI。
- 定向日志 SHA-256 `DF6DBF8F3D96A3ECF0FE8C5F7410F9FDFE1CCD396195252222278A12C763202B`；几何日志 SHA-256 `0FD953F7CC33DC62E171BFB53060FDC1B7802967BB0FE7E52AEA0CE20CFA81CC`。同一当前源码快照的完整 Gradle 为 496/496 JVM、67 suites、0 failures/errors/skips，88 tasks executed；日志 SHA-256 `6CB27E33765748096F388520C9AECAF65DDEAC03179B897C19D8B82F4D640835`。Debug/AndroidTest APK SHA-256 `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1` / `9C8C887B1AC86236415889A1A665E25497D3E121C318C2BAD5A3474E9A64EE4B`。
- 独立回放报告 `docs/issues/PC-ISSUE-167-ime-current-build-replay.md`。实体设备/OEM 与完整 MainActivity 路由仍未测，REC-P1-070 继续 BLOCKED。结构台账 r36 保持 198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；artifact SHA-256 `911254CE0245ED17A69150E7DEA4D87198BD08171994EB4E7C1B0D5B7BF46E4D`，ledger SHA-256 `48A6960B0CF4845FDF71D47CCC9FC32379AD2F8161C94F2D79C3D92375A8063E`。整体冻结 HOLD。

## PC-ISSUE-166 — 随口日程建议按钮的 API 35 子项（REC-P1-048 仍 NOT TESTED）

- 新增生产 `NativeAiChatScreen` 仪器回归：合成 assistant suggestion 消息展示可选“从此消息创建事件草稿”按钮；初次渲染不调用提案回调、不出现自动审核面板；仅点按后回调收到原始用户消息。API 35 `emulator-5556` AndroidJUnitRunner 1/1 PASS。无 provider 请求、无数据库/业务记录访问、无生产代码改动。
- PC-ISSUE-147 的本地 parser/client 路由测试已覆盖显式/随口分流与本地 stub（13/13），本次补足可选按钮渲染/点击子项。完整强制离线 Gradle 496/496 JVM、67 suites、0 failures/errors/skips；88 tasks executed，源码边界、Debug/AndroidTest APK 和 lint 通过。
- 测试提交 `d081a7ebb16dd7889c7386770c3bb1e63571bfbb` 已推送。独立报告 `docs/issues/PC-ISSUE-166-casual-chat-suggestion-button.md`。定向 AVD 日志 SHA-256 `34CF02E1CDE5316E48352217666080A7E57A4FAB28EE77EABEDF26607AE532AD`；全量 Gradle 日志 SHA-256 `6CB27E33765748096F388520C9AECAF65DDEAC03179B897C19D8B82F4D640835`；Debug/AndroidTest APK SHA-256 `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1` / `9C8C887B1AC86236415889A1A665E25497D3E121C318C2BAD5A3474E9A64EE4B`。
- 免费 provider 的真实自然语言分类及 `PowerCalApp.submitAiChatInput` 控制器集成未测试；故 `REC-P1-048` 继续 NOT TESTED，REC-P1-047 / PC-ISSUE-002 继续 BLOCKED。结构台账 r35：198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；artifact SHA-256 `737FBDCE9BD75E2ABE362BD8C8AD6C47A4BF7B6FD46FC5D26C0E3B137254F013`，ledger SHA-256 `9073E925A8DD2ECEDD486F429740EE143CB65726777EA1657595074BD68D840E`，`product_behavior_tested=false`（结构校验标志）。整体仍 HOLD。

## PC-ISSUE-165 — 日期级 Deadline 无障碍语义（REC-P1-068 窄化模拟器子项 PASS）

- 新增 production `TimelineScreen` API 35 Android instrumentation：在 4h/24h/48h zoom 下，日期条语义均含完整标题和 `2026-10-24 · 截止，无具体时刻`，不含虚构时分；点击后原样打开 eventSeq/title/date，`deadlineMillis=null`。三档缩放节点矩形均为 `Rect(42,268,657,405)`。无生产 UI 代码修改。
- `emulator-5556` 定向 AndroidJUnitRunner 1/1 PASS。完整强制离线 Gradle：496/496 JVM tests、67 suites、0 failures/errors/skips；88 tasks executed，source boundary、Debug/AndroidTest APK 和 lint 均通过。Skin-studio Python 37/37 PASS。
- 测试提交 `2844236dd00f4b87db50bffcc031cf4e7b74911e` 已推送。独立报告 `docs/issues/PC-ISSUE-165-date-only-deadline-accessibility.md`。定向日志 SHA-256 `12A2285C6F2F18DEFD6A26F7AA679565CF867178498FC7CDCD339E4DC37E02D`；语义矩形日志 SHA-256 `B4CDBC723D1FDCB1F31DD807C2FD86C47FE544D67D375C9E17BE5390F5FB10D8`；完整 Gradle 日志 SHA-256 `9827E96E155A152B6BA6FD4947F6BBC54749A700E6FD0FCD7E9BE0278C97DFED`；Debug/AndroidTest APK SHA-256 `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1` / `13CF8D486194FB778D0FFC68804966BD545A47F52F2E872425FDF9DEA987F12E`。
- `REC-P1-068` 整体仍 SPEC UNRESOLVED：准确 day-band 锚点仍未定，未验证 TalkBack 实际播报、OEM 服务或实体设备。结构台账 r34 保持 198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；artifact SHA-256 `1D91A98FFD4B8E99C31DAADF13538DC60624CCC45F17CFC276412B47EA5B29C1`，ledger SHA-256 `D3CBB371BC0546CB757A93BAB2B02E29A4E181A22ED02CB4A7AA19C5B96BD2A6`；`product_behavior_tested=false`（结构验证标志）。整体冻结仍 HOLD。

## PC-ISSUE-001 外部 ICS 客户端环境复查（REC-P1-081 仍 BLOCKED）

- `emulator-5556` 的 ACTION_VIEW/text/calendar 当前解析到 Google Calendar `ICalLauncher`，与早先“无处理器”记录相比环境已变化；但两次打开合成 DST ICS 均停在 Google Calendar 首次启动页，没有进入导入预览。Provider 测试前无日历记录，测试后事件数仍为 0；没有确认导入或写入，也未查看账户。
- 证据 `.local/android-test/reports/freeze-20261024/pc-issue-001-external-import-probe/summary.json` SHA-256 `8DFC47D762396B4CE0622E70E43E5B48155FC48E18958EC4DDC110878B595983`；截图 SHA-256 `6A83B31FD26EEFDFE46A722737F32D98ECFD951E533A180088B6D31C40413D7D`。Google 文档将 ICS 导入流程列为电脑端流程。没有完成外部导入/重新导出，故 `REC-P1-081` 继续 BLOCKED，PC-ISSUE-001 与整体冻结仍 HOLD。详见 `docs/issues/PC-ISSUE-001-ICS-recurrence.md`。
- 台账结构校验 r33：211 行 / 210 活动重建项，198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；历史映射 0，`product_behavior_tested=false`。artifact SHA-256 `45FE15C95BA8790282CFFA7DB1003DF45188D7D08497D46512CFB6D496A1B906`；ledger SHA-256 `F860D8FD6755FDC16A9036F170E0751B828185905F5A2E6B85610E7E19AC96BC`。

## PC-ISSUE-164 — 过期替换差异数据完整性（REC-P0-049，核心数据窄化 PASS）

- 修复前回归预期 14 个变化字段，实际差异模型只返回 8 个；缺失重复规则、来源/时区、完成槽位、提醒方式/时机。纯核心修复同时覆盖 Activity→Deadline 时被遗漏的旧开始/结束。
- 定向 TemporalProtocolTest.staleReplacementDiffIncludesEveryChangedSnapshotField 1/1 PASS；验证完整快照字段、重复例外/覆盖和完成槽位的排序确定性、跨类型时间字段移除。完整 Gradle 496/496 JVM、67 suites、0 failures/errors/skips；88 tasks，源码边界及 Debug/AndroidTest 组装通过，Lint 0 errors/58 warnings。
- 代码/测试提交 f959bccb36b5a66d8cf2667fb0d213071f833d4d 已推送；独立报告 docs/issues/PC-ISSUE-164-stale-replacement-diff-core.md。
- REC-P0-049 仅核心差异数据签核 PASS；移动端可读性原型审查仍未决，台账状态保留 SPEC UNRESOLVED。未改 UI。结构校验 r32 保持 198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；artifact SHA-256 73D21A6A05410CAFF04453BE65C37CEFDB794F8505675EF89088BD1980E1500B，ledger SHA-256 B7BC6D62808818B05D1E8A99BDBAE6FD8A2BB74C4FE698F871013AE49F529EA5。结构校验不代表产品行为，核心冻结仍 HOLD。

## PC-ISSUE-002 桌面 DeepSeek 凭据探测（2026-10-24）

- API 35 emulator-5554 上使用一次合成结构化提案请求；AiClient 仅有一条 structured-proposal 遥测，HTTP 401 / 701 ms / budget 1800。测试 1 run / 1 failure，失败于预期无 warning 的断言；没有提案，没有重试，没有设置、数据库或正式日历写入。凭据未通过本次认证；不重试或切换服务。
- 临时密钥通过应用私有目录读取，运行后应用私有文件及 staging 文件均已验证删除。首次复制因 filesDir 尚未建立而中止，未启动测试、未发请求；过程记录于 .local/android-test/reports/freeze-20261024/pc-issue-002-transfer-attempt.md。
- 强制离线完整 Gradle 495/495 JVM、67 suites、0 failures/errors/skips；88 tasks，source boundary 与 Debug/AndroidTest 组装通过，Lint 0 errors/58 warnings。完整日志 SHA-256 1459C4BD3731C8DA95B1DA49E50704BCDBF7BD9A6F4EB0A0CC14115E109882EF。runner SHA-256 515BF7F4DDDFD0BDBB992EEAA5785D5952FB338912F1BDF15BE547C145594261；脱敏 HTTP 401 诊断 SHA-256 A36B9835C3E466D482102E11DBD96FD267CAB8C110547511BED594B681264BC7。
- REC-P1-047 仍 BLOCKED：桌面凭据请求不是免费 provider 验收，既有免费结构化 429/503 未闭环。PC-ISSUE-002 保持 PARTIAL/BLOCKED，核心冻结保持 HOLD。完整证据见 docs/issues/PC-ISSUE-002-ai-timeout-proposal.md。
Structural-only ledger validation r31 confirms 211 rows, 210 active (P0/P1/P2 144/65/1), 198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED; historical IDs mapped=0 and product_behavior_tested=false. Artifact .local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r31-pc-issue-002-deepseek-auth.json SHA-256 8E1BD5EE6FED75555FFA5572791AF320D5ADA997A63B42492CD0F08EA0FB92E2; ledger SHA-256 CCB65B297FDA080A13A2369B1A5C1C6AF9C7676FBEFB55685A935F44693754EE. This validates matrix structure only, not product behavior.


## PC-ISSUE-105 提醒宽限期规格签核（REC-P0-118，窄化 PASS）

- 用户确认迟到提醒宽限期为 **10 分钟，含恰好 10 分钟边界**；超过边界记录 `MISSED` 且不补响。合同和产品基线已同步；“同时响铃”仍是独立未决规格。接收器与恢复调度器共用 `AlarmContract.isOutsideGraceWindow`。
- JVM 边界测试 1/1、API 34 Robolectric 接收器 8/8、恢复调度 17/17；定向共 26/26 PASS。API 35 `emulator-5554` AndroidJUnitRunner 1/1 PASS：直接调用接收器时 9 分钟迟到为 `FIRED` 且有通知，11 分钟为 `MISSED` 且无通知。它没有验证 OS AlarmManager 投递或重启恢复。
- 全量强制离线 Gradle 重跑：495/495 JVM tests、67 suites、0 failures/errors/skips；88 tasks executed；skin boundary、Debug/AndroidTest APK 组装通过；Lint 0 errors/58 warnings。完整日志 SHA-256 `73549B5364373678E78E6BCCFBCFCDB01CE24A8CFAE5CC341F751D98B62AAB33`；API 35 AVD 定向日志 SHA-256 `433EFEF15C892216E0E3E0906A6F00BCEE4899D8D1135B9C93F1849D00806147`；AVD XML `1985D24BD40866BC37218EC0C50B1E51F8929BA8B3F86281AE340AC2FA8133C1`；设备 logcat `ED77BB8BF935BF3EF2928369A1EB8E4304BA3AC794F7E9988C0014E78AF49F1E`。
- Debug/AndroidTest APK SHA-256 分别为 `7A5476F972473030B7DF2AE0E2CA6F305A71BD2A6092CFDA02F9B3378F40A03B` / `28994F452DCA89D42E1AC3C07EAB3DBF005C73CA8283871D0AEE2C74F84CDD21`。生产修复 `b381846a73b1b25656b784989a2521c3917d0dad` 与 AVD 测试 `02f64925433b4ccb8a46a22a5c784c381e1f37b7` 均已推送。早期失败尝试如实记录于 `.local/android-test/reports/freeze-20261024/pc-issue-105-exploratory-attempts.md`，SHA-256 `8B1459F6C7553357D979F5B62B8C9E32983398D33E4A937B1BD79E6C4655240A`。
- 结构台账 r30：211 rows、210 active；198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2=144/65/1，历史映射 0，`product_behavior_tested=false`。artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r30-pc-issue-105.json` SHA-256 `E330D6B59F6C9E2D31369F564A141030628E4300078892EDE25C0143D2D637E9`；ledger SHA-256 `420927B4B3D86D662D867799D2CCDB6BA1977C206BC05F4BAB65F8EFE52B2D8E`。OS 闹钟实际投递、并发响铃规格和用户负责的实体/OEM 设备仍未签核，整体 HOLD。独立报告：`docs/issues/PC-ISSUE-105-missed-alarm-grace-window.md`。

## PC-ISSUE-163 重复事件 DST gap/fold 解析（REC-P0-074/075，窄化 PASS）

- 用户确认：春季不存在的本地重复槽位跳过当天但仍消耗 count/index；秋季回拨的重复本地时刻使用第一次（较早 offset）。生产逻辑原先依赖 `LocalDateTime.atZone()`，把纽约 2026-03-08 02:30 错误顺延成可触发实例；现在把扫描边界和真实 occurrence instant 分开，gap 不产生活动/提醒实例且不推动后续日期，fold 显式取 `ZoneRules` 的首个 offset。
- 新增两个 `TemporalProtocolTest`：验证 gap 在普通/优先窗口投影、`occurrenceForKey`、`nextOccurrence` 中都跳过且仍消耗序号；覆盖 Alarm 与 30 分钟 Activity。fold 断言列表/按 key/next 均为 2026-11-01T01:30 `-04:00`。修复前重现 gap 多出 `2026-03-08T02:30`，fold 子项通过；修复后定向 2/2 PASS。
- 完整离线 Gradle 494/494 JVM、66 suites、0 failures/errors/skips；源码边界、Debug/AndroidTest APK 组装通过；Lint 0 errors/58 warnings。修复前行为失败日志 SHA-256 `B8B7230C3C3271EB784318D9A19D7D8EF72AFC2BEA43587160E1BDC0DFB9A57A`；修复后定向日志 SHA-256 `0C3E4DC55BB66D2FEAFECA1FA8F451A319702284BEBBF981735DCC32ECAE1EAF`；全量日志 SHA-256 `E3BFDDF0B3F8349A671EEC5B7784A16063F2C6A2D8CFCDBE4B78914535C7B2E4`。提交 `315492434e4ec57d308d3ca2b65b8488239a3aaa` 已推送。
- 合同已同步至 `PRODUCT_BASELINE_G1-G11.md` 与 `CORE_CONTRACT_FREEZE_2026-10-21.md`。独立报告：`docs/issues/PC-ISSUE-163-recurring-dst-gap-fold-resolution.md`。台账 r28：197 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 3 SPEC UNRESOLVED；P0/P1/P2=144/65/1；artifact SHA-256 `8F0964729F1814E6DA1BE4C5E13B81233EC59DF669C19BE403F10615158D083F`，ledger SHA-256 `B2A791BB207C74F9273E76D891023BC55E78638AF3FA67C1857542BFFC1AE630`。未签 Android scheduler/UI/设备；整体仍 HOLD。

## PC-ISSUE-047 同一事件人类/AI 草稿最后保存覆盖（REC-P0-054，窄化 PASS）

- 用户明确选择同一事件只保留一张可操作草稿，后保存的提案覆盖现有草稿，不新增第二张；已将该决定同步到 `PRODUCT_BASELINE_G1-G11.md` 和 `CORE_EDITING_V4.md`。正式事件仍须单独确认/提交；人类编辑租约期间 AI 写入仍被拒绝。
- 新增 `PowerStoreTest.humanAndAiProposalsForSameEventOverwriteInLastSaveOrderAcrossRestart`：人类→AI→人类连续保存复用同一 draftId/queueOrder，内容变化时 draftVersion 递增，只留一张可操作草稿；正式记录未变化；SQLite 重开后最终内容与身份不变。定向成功；完整离线 Gradle 492/492 JVM、66 suites、0 failures/errors/skips，源码边界与 Debug/AndroidTest APK 组装通过，Lint 0 errors/58 warnings。
- 定向日志 SHA-256 `46E826C46EA6EC4601B33C89A77461984667B55AFCF67A3DADFE0358EB3D3246`；PowerStoreTest XML SHA-256 `78ED5765B288C7A9E22571125B19F4443C118C29D4E7D8016562A4D68279F681`；完整 Gradle 日志 SHA-256 `79A07BC8CC8079BBC10ACC08E2AD9F04F3DB7FD9704FB9E4387CB1A1C30C047A`。测试提交 `f1fe242fab94b3b27e0dfd03931381f708312318` 已推送。没有生产代码、UI、provider 或设备行为签核。
- 独立结案报告：`docs/issues/PC-ISSUE-047-human-ai-draft-cardinality-spec-conflict.md`。台账 r27：195 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 5 SPEC UNRESOLVED，P0/P1/P2=144/65/1；artifact SHA-256 `DD35CF840505A206C3D749254565D4D59A2E4B2968BC4B8AE743905DE3A617B9`，ledger SHA-256 `E6B8A6B8CAB574B0147B80B7537C189851A5D9FE058AF55B5B26C778891F5A4F`。`product_behavior_tested=false`，整体仍 HOLD。

## PC-ISSUE-160 编辑草稿时切换深浅主题（REC-P1-073，窄化 PASS）

- 新增隔离 Android instrumentation：真实 `NativeDraftDeckDialog` 中把标题改为 `THEME_SWITCH_DIRTY_TITLE` 并聚焦输入框，再通过 `PowerCalTheme` 浅→深切换。API 35 `emulator-5556` 定向 1/1 PASS：Material background 从 `Color(0.9529412, 0.9607843, 0.98039216)` 变为 `Color(0.039215688, 0.050980393, 0.07450981)`，draftId、编辑内容和焦点保持。用随机临时数据库，未改生产代码、系统夜间模式或默认业务数据。
- 完整离线 Gradle 491/491 JVM、66 suites、0 failures/errors/skips；源码边界、Debug/AndroidTest APK 组装通过；Lint 0 errors/58 warnings。全量日志 SHA-256 `572B1C1FBC70AD7F1519A8408D764500949B344BBA8A3CD550820F1069208BCA`；定向日志 SHA-256 `B463A726A57DEA43369BA360C468EB2C73DF912AF73FA921074664E16DF48EF7`；状态轨迹 SHA-256 `59E5BC08D97E2B5E3A51C18E5B125823D77B0D328F0EDEFD2056AEE2B7E96328`。测试提交 `2a0ed99dacdf14f7c5558b390a9400502560ace5` 已推送。
- `REC-P1-073` 的签核仅限显式 `PowerCalTheme` 参数转换；系统 `uiMode` 变化和完整 MainActivity 配置分发未测。r26：210 active，194 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 6 SPEC UNRESOLVED；历史映射 0，`product_behavior_tested=false`。artifact SHA-256 `5165AF1041F728B98B55DEE4A8A674CBC5FB7B4FEE235CFAD15914147B6238C3`；台账 SHA-256 `1851D775B93E5EC1ADEA5A12C4EF3E837511F2CA6E51E86DB55E801FCE5FED92`。整体仍 HOLD。独立报告：`docs/issues/PC-ISSUE-160-draft-theme-switch.md`。

## PC-ISSUE-159 时间轴顶栏顺序与剩余数徽标（REC-P1-032/033，窄化 PASS）

- 新增 AndroidJUnitRunner 测试，直接组合生产 `TimelineScreen`：Today、Memo、Todo、New 的实测横向中心为 658/774/890/1006px，四个回调及 New→Activity 菜单动作均通过；合成 Memo=12、Todo=7 的可见徽标文字和无障碍描述通过。API 35 `emulator-5556` 原生竖屏 1080×2400、420dpi，定向测试 2/2 PASS。未改生产代码或访问业务数据库。
- 测试提交 `a2b701e9ca02ffd39643a7797932ca817d149d58` 已推送。完整离线 Gradle 491/491 JVM、66 suites、0 failures/errors/skips；source boundary、Debug/AndroidTest APK 组装通过；Lint 0 errors/58 warnings。完整日志 SHA-256 `F1903B255787C632AC7B30B26D6C8746F601DE301DF453266C41D91256579737`；定向日志 SHA-256 `522A2DEB85ED4C7B18EBFB805D7C4EDBFA7DD5DB56C95705036CFA3A49445CD5`；几何/回调轨迹 SHA-256 `D2A2E5617C28B36F787DBB605E9CEDA85FFFA06E7363D00779E2A966416F3C2D`。
- 台账结构校验 r25：210 active，193 PASS / 0 FAIL / 8 BLOCKED / 3 NOT TESTED / 6 SPEC UNRESOLVED；P0/P1/P2=144/65/1，历史映射 0，`product_behavior_tested=false`。artifact SHA-256 `8A3096975A584A9A45133D6F7A0C6F40833F79BA55AD9E23889781B1A65F1FCE`；台账 SHA-256 `D3B1B72CC3DDC2B6E09C496D03AAAF12E1F021CCEBBBB16ADBB160A339392640`。实体设备、MainActivity 路由与持久化徽标数据未签核；整体仍 HOLD。独立报告：`docs/issues/PC-ISSUE-159-timeline-toolbar-order-badges.md`。

## PC-ISSUE-154 2× 字体缩放下时间轴控件可达性（REC-P1-069 子项 PASS；完整项 BLOCKED）

- 新增 Robolectric API 35 Compose 子项，显式设 `LocalDensity.fontScale=2.0`、360×800dp 视口；今日、Memo、待处理、新建、缩小/放大共 6 个控件边界均在屏内并可操作，新建菜单“活动”回调成功。定向测试 1/1 PASS；未修改生产代码或 AVD/系统设置。
- 首次测试夹具编译失败（Compose 测试扩展导入/组合读取位置错误），修复后定向通过。失败日志 SHA-256 `E6532B89A3EB3BE44274E64018475BA0108DA5C3017DE42A33370E9B23503010`；通过定向日志 SHA-256 `C9C8F27A225EEAD72DEE07A0C2AC35A363F2B1428B38810B889464AE7FFC0B2C`；JUnit XML SHA-256 `79D3861D09DF5D986817D9BA2CE0143BAD5F048B0CF9F79AFB4FC94982BE4D73`。
- 完整 Gradle 491/491 JVM、66 suites、0 failures/errors/skips，source boundary、Debug/AndroidTest APK 组装通过，Lint 0 errors/58 warnings；日志 SHA-256 `DA5E539CFEC2550D79A15E66ABF193A8B692F578EA908A7021385C0E14FE4A0B`。测试提交 `b8211496d244f9261b127ef91bdc50779de4591a` 已推送。真实系统 font_scale、PowerCalApp/MainActivity、AVD 和实体设备未测，`REC-P1-069` 保持 BLOCKED。r20: 186 PASS / 0 FAIL / 8 BLOCKED / 10 NOT TESTED / 6 SPEC UNRESOLVED；artifact SHA-256 `FB1637E0BE5CBB31495C9FEC1BEA87810715D283F1E29601DC8662F9C5235F79`；ledger SHA-256 `2A38DFBCF5EF50F2CC46993F4B002F13A6140748C5BB0B22622448933C666792`。整体 HOLD。报告：`docs/issues/PC-ISSUE-154-font-scale-2x-controls.md`。

## PC-ISSUE-153 返回时间轴保留视口与缩放（REC-P1-003 窄化 PASS）

- 新增真实 `PowerCalApp` Compose API 35 instrumentation：初始相机为 anchor `1792843200000` / 24h；用缩放控件和竖向手势改为 anchor `1792965924653` / 16h；打开 Memo 并返回后两个值精确不变，定向测试 1/1 PASS。状态只读自 timeline 投影，业务数据与正式数据库未访问。
- 测试使用随机数据库、偏好名称空间和缓存目录，执行后删除。AVD 保持 1080×2400 并恢复 Launcher 焦点。最终日志 SHA-256 `D26BCBF3177F8765A455D4243E1AFC208F53BF7973AAF0D8166B71462AA53D7B`；相机轨迹 SHA-256 `680731D0EE21F9CE78193B4DDCADD755F7F5A919291C791A2F956FC85C3346FF`。
- 完整离线 Gradle 490/490 JVM、65 suites、0 failures/errors/skips；source boundary、Debug/AndroidTest APK 组装通过；Lint 0 errors / 58 warnings。完整日志 SHA-256 `30E211EB07F1EFAF18BC5735B76B7FF46663A904AB09FA26C2EC08A8B03F7257`。测试提交 `b3086bffffd97a412ecd0abefe0be419be1cc88f` 已推送。
- 记录了三次早期 instrumentation 测试夹具失败及原因，最终隔离存储回归通过。`REC-P1-003` 只签核 Timeline→Memo→Timeline 路径，不推断其他页面/进程重建。台账 r19（环境字段修订后重跑）：186 PASS / 0 FAIL / 8 BLOCKED / 10 NOT TESTED / 6 SPEC UNRESOLVED；P0/P1/P2=144/65/1。artifact SHA-256 `DF3E3697C15CA7EBB66DE4B1E7821A09114AFA8039DE34227230DEA90170D9BD`，ledger SHA-256 `AA80C181A42C015CFAD01B02E05C468F7A303CB73101D9D957DF12FA97677BD8`。整体 HOLD。独立报告：`docs/issues/PC-ISSUE-153-timeline-return-preserves-viewport.md`。

## PC-ISSUE-152 小屏事件点击命中与标题触控回归（REC-P1-067 AVD 子集 PASS；完整项 BLOCKED）

- 新增 API 35 Android instrumentation：320×640dp 竖屏视口内，标题中心和事件语义命中区中心两处自动化点击都把同一事件交给打开回调；完整标题在 Accessibility 语义中保留。真实一分钟事件几何 1px，独立点击面 3px，标题矩形 `[486,977,676,1040]`。这是测试结果，没有生产代码变更。
- 定向 AVD 测试 1/1 PASS。设备原始 1080×2400px 分辨率已恢复，焦点回到 Launcher。截图 SHA-256 `A9D24E703A29FF295F825ECB1FDAD5250D08841A50C4CC293CF21D47A245C480`；矩形/设备记录 SHA-256 `62A70B9A311FF7CA58791F202669252148C4D13822C69525ED1DFBB041B49E2B`；定向日志 SHA-256 `130AB0E1A495C5AF2EB15052134CCCBFD0B60C086AEE275F062AE261B8E8E1F2`。
- 完整离线 Gradle 490/490 JVM、65 suites、0 failures/errors/skips；source boundary、Debug/AndroidTest APK 组装通过；Lint 0 errors / 58 warnings。完整日志 SHA-256 `3D02559A095242A68532ED852DF3E7B5ECC5BAC36F70BEC1F1610474B5EC2057`。代码/测试提交 `6cd002f6fdbd275036d2ddb1e8ea2b54543cee2c` 已推送。
- `REC-P1-067` 仍 BLOCKED：自动化结果不代表真实手指触控舒适度、实体设备/OEM 或 TalkBack，用户负责实体设备测试。r18 仍为 185 PASS / 0 FAIL / 8 BLOCKED / 11 NOT TESTED / 6 SPEC UNRESOLVED，P0/P1/P2=144/65/1；artifact SHA-256 `13A7EEED004852E594EB228F00C2745EF4B87A7A9DFAA41BC1705885B7F096B7`，ledger SHA-256 `B3E46A12B1AB01F33659B0AF0160539840FD6160253D673F3E046CE0770C1832`。整体保持 HOLD。独立报告：`docs/issues/PC-ISSUE-152-small-screen-event-hit-target.md`。

## PC-ISSUE-151 强制横屏标题折叠与子像素事件点击（REC-P1-084 窄化 PASS）

- 修复真实一分钟高度取整为 0px 时整个事件 Surface 同时失去点击区域的问题：透明交互面至少 1dp；内层绘制几何仍严格按真实时长，不扩高事件。新增 Robolectric Compose 与 API 35 Android instrumentation 回归。
- AVD 800×360dp 实测 1/1 PASS：A/C 标题可见、B/D 隐藏；A/B/C/D 事件节点均保留完整标题语义和点击动作；隐藏标题 B 仍可点开。标题矩形 A `[1116,496,1315,559]`、C `[1116,581,1315,644]`；真实几何 0px、点击面 3px。中央刻度与蓝色当前时间线保留。截图 SHA-256 `A9210C324AA2F94384B8F222DCC6383359F1A65E6CAAC966A2B32F8B6A038FEC`，矩形轨迹 SHA-256 `7EFED8B796E4D9EB7A5F45B91EE14F3987DAD0249EC7C88F1CD24CBB7D4922AF`。
- 完整离线 Gradle 490/490 JVM、65 suites、0 failures/errors/skips；源码边界、Debug/AndroidTest APK、Lint 全通过，0 lint errors / 58 warnings。单测总数含 489 个受版本控制测试与一个既存受保护未跟踪 `DraftDeckFeedbackTest.kt`。完整日志 SHA-256 `ECD191B36F27485A8437CB867EA019D2A5C5B84FB593E81E3F2B5784431DC1D8`。
- 代码/测试提交 `671f7c700404e18eea0c5658c300ca04ca01a49c` 已推送。独立报告 `docs/issues/PC-ISSUE-151-forced-landscape-title-folding.md`；台账 r17 为 185 PASS / 0 FAIL / 8 BLOCKED / 11 NOT TESTED / 6 SPEC UNRESOLVED，P0/P1/P2 144/65/1，结构 artifact SHA-256 `C5A1730FB87861436F95ED3DED3886C804798650EAC6F1F19D71DB75CB37CF9E`，ledger SHA-256 `EEFA0692584EE11202EC53B8C5F682B5FC3BF755EBCAD1A1159ED246AA31E98B`。实体设备/OEM 未测；整体保持 HOLD。

## PC-ISSUE-150 AI 结构化提案阶段读超时回归（本地客户端边界 PASS）

- `AiClient` 连接/读取超时支持正值校验的参数注入，线上默认仍为 20s / 45s。loopback 服务在结构化请求后停住，150ms read timeout 定向测试 1/1 PASS：请求仅一次、阶段级超时错误、无 proposal/raw reply、无测试凭据泄露。独立报告：`docs/issues/PC-ISSUE-150-ai-proposal-read-timeout.md`。
- 完整离线 Gradle 回归 489/489、64 suites、0 failures/errors/skips；源码边界、Debug/AndroidTest APK 组装通过，Lint 0 errors / 58 warnings。完整日志 SHA-256 `A9836C3D6FA385C4E156D1264782447E1FAE0037008E8FA3DB5AE160AAE6284C`；Debug APK SHA-256 `16B2042B81BE6ABC00EC77B47A842E2069FCEF33E0D655A6BD151C522DA4676A`。代码/测试提交 `423f29019183728ac4b8048692e26ba518cea163` 已推送。
- 只读 API 35 检查：emulator-5554/5556 都装有应用，5554 无偏好目录，5556 无 AI 偏好文件；未读配置值/凭据、未启动应用或请求 provider。配置证据 SHA-256 `E2FBEFF529095C17472739E6A8F85006E9E2923F029856156AD30077C32C1F1A`。本地 timeout 测试不解决免费 provider structured HTTP 429；`REC-P1-047` 仍 BLOCKED、PC-ISSUE-002 与整体冻结仍 HOLD。
- 台账结构复核 r16：184 PASS / 0 FAIL / 8 BLOCKED / 11 NOT TESTED / 6 SPEC UNRESOLVED；P0 144 / P1 64 / P2 1，product_behavior_tested=false。artifact SHA-256 5F4D3C33AEC94EA4A950BE4BA6A1104485556EF6BA7A23B25A9C1A8477F49F71；ledger SHA-256 93461981B84F2372C85DD61A3924B0BD39F30A5B2B4D89B2C95C4B2DEC9AD066。

## PC-ISSUE-149 — 正式事件搜索生成匹配 Update 草稿（REC-P1-039，窄化 PASS）

- 提取纯 `FormalEventSearch.filter`，保持原有标题/序号匹配、顺序及最多 8 项限制。SQLite 回归验证选中搜索命中的正式记录后生成 REPLACE/PENDING 草稿，保留完整当前快照与 baseRevision、追加到现有队列尾，并在重开后保持。
- 定向 5/5 PASS；完整离线 Gradle 源边界、488/488 JVM（64 suites）、Debug/AndroidTest APK 组装及 lint 全部通过，0 failures/errors/skips，lint 0 errors/58 warnings。修正后定向日志 SHA-256 `C6AFA52BB816008BDD3A0DBF7AEDC7B2705A4D0EC33494A2B401FFD6B570FE7B`；首轮夹具断言失败日志 SHA-256 `D3391DCEC771A7645103CEED6AE039107CEFD2ADBF18434AD5FD991475980D68`；全量日志 SHA-256 `7E130576D974E04BC04729F870B254C044E2E46900E6E81E33561FE91D7D76F9`；Debug APK SHA-256 `6D8C68816D0085949D54A893D50022ACA040B8CBF54087FB957863BCF90AA71B`。
- 代码/测试提交 `0db7b534c7683059fea1f7f38c587e6d30179a73` 已推送。独立报告：`docs/issues/PC-ISSUE-149-formal-event-search-update-draft.md`。未运行 AVD/实体设备；Compose 结果点击未签。
- 最新台账结构统计：184 PASS / 0 FAIL / 8 BLOCKED / 11 NOT TESTED / 6 SPEC UNRESOLVED；结构报告 SHA-256 `45494C0672830234BB1A38739FFBB6406CBABA8465E5B5DE49941E458AA5EECF`，ledger SHA-256 `AD117D484ACE7AFBA1565397C1D01F73D3798F49A1DB0868CBF96CD0D9C82319`；整体 HOLD。

## PC-ISSUE-148 — 草稿搜索不重排待处理队列（REC-P0-040，窄化 PASS）

- 把 `DraftDeck` 内联筛选提取为纯 `DraftQueueSearch.filter`，不改变搜索显示或布局。新增两项测试锁定标题/序号匹配及 `queueOrder` 保序；同时重跑 3 个既有 SQLite 队列重开/排序测试。
- 定向 5/5 PASS；完整离线 Gradle 源边界、485/485 JVM（63 suites）、Debug/AndroidTest APK 组装与 lint 全部通过，0 failures/errors/skips，lint 0 errors/58 warnings。定向日志 SHA-256 `7CF13719EE900F581521D50756018CE3B744BC2D6072229725F77C191FCE5D8E`；全量日志 SHA-256 `4B83C3FF408732478CEF2244DEBE7B995676C3DB4CF7752BB12FFCBA9AD164FB`；Debug APK SHA-256 `5F82337249AEB8355F766A3CC00F93142C93AF7FD01EC84F0A7206FC22A17B31`。
- 代码/测试提交 `3a0fb7f01bccbf33c19c747226ef65316a5d1f70` 已推送。独立报告：`docs/issues/PC-ISSUE-148-draft-queue-search-order-stability.md`。未运行 AVD/实体设备；仅签核心过滤保序，搜索界面交互及正式事件结果未签。
- 最新台账结构统计：183 PASS / 0 FAIL / 8 BLOCKED / 12 NOT TESTED / 6 SPEC UNRESOLVED；P0 144 / P1 64 / P2 1。结构报告 SHA-256 `F0FDC211A16A4FD7072FB650ADB8FCC2B392ABC20E9E9E71A219ADA07C7D83E6`，ledger SHA-256 `BCAA0F71C6941F4FB3F45BEB2AF954041C1F15F70C4F95A7D295EAB14FA4211D`；整体 HOLD。

## PC-ISSUE-147 — 明确日程意图与随口建议分流（REC-P1-048 仍待端到端验收）

- 根因：普通聊天只要含 draft marker 就自动调用结构化提案解析，模型对随口提及给出的轻量建议也会弹出自动提案预览。现在只有 `intent=explicit` 使用当前用户原文进入自动预览；`suggested` 只保留消息按钮；缺失/未知值安全回退为建议。模型生成的 source 不作为提案输入，任何标记都不授权写入。
- 定向 parser/client/合成 SQLite 流程 13/13 PASS；完整离线 Gradle 源边界、483/483 JVM（62 suites）、Debug/AndroidTest APK 组装及 lint 通过，0 failures/errors/skips，lint 0 errors/58 warnings。定向日志 SHA-256 `87B8BFEE7D29155A458C6506DEE81DF578BB64E3BFDD1F56934BB43A14C8E01F`；全量日志 SHA-256 `47848074DB10EC273E31AAACCA2F1578DC454549862070593CEA6A2EBF47C9FF`；Debug APK SHA-256 `751E4DA86160A2032E9D74052AB3651E8B76FF83649FF71906404C86A93CA068`。
- 代码/测试提交 `8e642ad0d8e1ba5786f2e2ee0bdbb16851a5b9b2` 已推送。独立报告：`docs/issues/PC-ISSUE-147-ai-explicit-vs-suggested-chat-routing.md`。未请求 provider，未运行独立 AVD 或实体设备；`REC-P1-048` 仍 NOT TESTED，REC-P1-047 仍 BLOCKED，Issue 002 与整体冻结均 HOLD。
- 结构校验 artifact SHA-256 `779E4BE494907AC4C0D455025C5E8770B8083DFA92FFA24F63310BC17469957C`；ledger SHA-256 `2971AD843BF80038B01F0C0FB17F8A58BF9EE47DE26E43ED90D26C871CAA2583`；182 PASS / 0 FAIL / 8 BLOCKED / 13 NOT TESTED / 6 SPEC UNRESOLVED，`product_behavior_tested=false`。

## PC-ISSUE-146 — AI 提案动作与批次授权范围回归（REC-P1-052/053）

- 根因：验收矩阵中两项审核行为虽已有实现，但审核视图嵌在 `PowerCalApp` 中，缺少逐项 Compose 回归。将批次授权预览提取为 `AiBatchAuthorizationReview`，不改变原有显示样式或写入流程；新增 API 35 Robolectric Compose 测试。
- 定向 `AiReviewComposeTest` 4/4 PASS：提案区四种操作及精确索引范围、阻断项排除、忙碌禁用；批次审核总数/操作计数/逐项清单/精确 ID 与版本快照；取消和忙碌不授权。定向日志 SHA-256 `74F1BF71ED2361EA7602281AB38E816B220AC604B9536D7C28C5603BE974A99B`，JUnit XML SHA-256 `829D0C7E470FF741E84144736D1912F83112A7738D657915EA72CB8A1B6A71E5`。
- 完整离线 Gradle 源边界、482/482 JVM 测试（62 suites）、Debug/AndroidTest APK 与 lint 全部通过；0 failures/errors/skips，lint 0 errors / 58 warnings。完整日志 SHA-256 `0386A8AAFD604B8C2992EEDB79F79DFA1767E2436204232880D622CE9CD27155`。
- 代码与测试提交 `8d1f3abbc0df5b58def766f67dc5fd8081b8e860` 已推送。独立报告：`docs/issues/PC-ISSUE-146-ai-review-and-batch-authorization-compose-regression.md`。本次 shell 的 PATH 与标准 SDK 目录未发现 ADB/emulator；未跑独立 AVD、真机或 provider 请求。
- 最新台账结构统计：182 PASS / 0 FAIL / 8 BLOCKED / 13 NOT TESTED / 6 SPEC UNRESOLVED；P0 144 / P1 64 / P2 1。`product_behavior_tested=false`；历史 209 ID 仍 unmapped，冻结仍 HOLD。

## PC-ISSUE-145 — independent timeline wing scrolling (REC-P1-018/019/020)

- Root cause: the left wing compressed its lanes to fit and disabled horizontal scrolling; the right wing alone was scrollable. Both sides now use stable 84dp-max lanes and independent scroll state; the left wing opens on the lane nearest the central ruler.
- Robolectric API 35 Compose test passed 1/1 with eight overlapping lanes on each side. It checks independent movement, stable viewport bounds, near-ruler initial left lane, and that all event nodes remain present. Full offline source-boundary/JVM/Debug APK/AndroidTest APK/lint run passed 478/478 tests across 61 suites, zero failures/errors/skips; lint 0 errors/58 warnings. Full log SHA-256 `E01CBA2964475E82851D9B25028C20FE6BC45082EA128C1F1153C3A76329615D`.
- Fix/test commit `b0c9d5e6f7445ed7b1ad748eba62b565add4e9f0` is pushed. Independent report: `docs/issues/PC-ISSUE-145-independent-timeline-wing-scroll.md`. No new AVD screenshot or physical-device run; overall freeze remains HOLD.

## PC-ISSUE-144 — timeline DST tick labels (REC-P1-008/009)

- Extracted the timeline tick-label generator used by both the ruler and current-time badge collision avoidance. JVM fixtures verify the 2026 New York spring gap (01:00 to 03:00, no false 02:00 label) and fall fold (repeated 01:00 with -04:00/-05:00).
- Focused tests passed 2/2. Full offline source-boundary/JVM/Debug APK/AndroidTest APK/lint run passed 477/477 tests across 60 suites, zero failures/errors/skips; lint 0 errors/58 warnings. Full log SHA-256 `C5C62FB0BF5AE379E17E1BC1358A89B4257648CEBB45E70F2D504FD72EF79741`.
- Test commit `13e7e73a4b15865277289c43c87651460c008461` is pushed. Independent report: `docs/issues/PC-ISSUE-144-timeline-dst-tick-labels.md`. No new AVD or physical-device rendering test; recurrence DST policy remains unresolved; overall freeze remains HOLD.

## PC-ISSUE-143 — Today action centers the current instant (REC-P1-002)

- Root cause: `PowerCalApp`'s Today callback called `initialCamera()`, which returned local noon and 24-hour zoom. Added and wired `TimelineCoordinate.todayCamera(nowMillis, hours)` so Today anchors at the supplied current instant and keeps current zoom.
- Focused JVM test passed 1/1. Full offline source-boundary/JVM/Debug APK/AndroidTest APK/lint run passed 475/475 tests across 59 suites, zero failures/errors/skips; lint 0 errors/58 warnings. Full log SHA-256 `4F1264E171DE8355F15DC7A2AB194149DF97055AD542D5A1A7D2CC0CA5A51A6A`.
- Fix/test commit `fc98e29c02c8f1bb1670e57adf2872cc8e3f6a91` is pushed. Independent report: `docs/issues/PC-ISSUE-143-today-centers-now-core-fix.md`. UI/device interaction remains untested; overall freeze remains HOLD.

## PC-ISSUE-142 — timeline initial camera core sign-off (REC-P1-001)

- Reused `TemporalProtocolTest.initialCameraCentresNoonButTodayJumpsToNow`: a fixed 15:42 Asia/Shanghai instant yields a local-noon anchor and 24-hour span. `PowerCalApp` calls this model for initial state; no Compose UI or device was run, so visible first-screen/restoration behavior is outside this sign-off.
- Focused test passed 1/1. Full offline source-boundary/JVM/Debug APK/AndroidTest APK/lint run passed 474/474 tests across 59 suites, zero failures/errors/skips; lint 0 errors/58 warnings. Full log SHA-256 `897FDB560E7B4200238E1B3E621C0C5FC53FA5AB32263AF56292390C72555B1E`.
- Independent report: `docs/issues/PC-ISSUE-142-timeline-initial-camera-core-signoff.md`. Overall freeze remains HOLD.

## PC-ISSUE-141 — timeline zoom model boundaries (REC-P1-006/007)

- Added pure-model regressions for both contract zoom limits and focal-time preservation when clamped to the minimum/maximum. Focused tests passed 2/2; full offline source-boundary/JVM/Debug APK/AndroidTest APK/lint run passed 474/474 across 59 suites, zero failures/errors/skips; lint 0 errors/58 warnings. Full log SHA-256 `7B9425F20B9D56CCB23F8AC6280E60F89A8E57A2E1783B5A68D6A6AB496748E2`.
- Independent report: `docs/issues/PC-ISSUE-141-timeline-zoom-model-signoff.md`. The sign-off covers `TimelineCoordinate` math only; zoom controls, gestures, and device behavior remain untested. Overall freeze remains HOLD.

## PC-ISSUE-140 — timeline midnight coordinate core sign-off (REC-P1-004)

- Added a pure `TimelineCoordinate` regression: map 23:59 and next-day 00:01 to Y at a fixed 24-hour scale; assert monotonic time, the exact elapsed-time ratio, and inverse mapping within 2 ms. This signs geometry only, not UI scrolling, date labels, DST rendering, or device behavior.
- The initial compile attempt caught a Float/Double mismatch in the new assertion before test execution; the correction was retained and the focused test then passed 1/1. Full offline source-boundary/JVM/Debug APK/AndroidTest APK/lint run passed 472/472 tests across 59 suites, zero failures/errors/skips; lint 0 errors/58 warnings. Full log SHA-256 `076D85C120B2E9C56C4810F3663690201CD6822407CE0264A66BEE2701EDC2EF`.
- Independent report: `docs/issues/PC-ISSUE-140-timeline-midnight-coordinate-signoff.md`. `REC-P1-004` is signed only for core coordinate continuity. Overall freeze remains HOLD.

## PC-ISSUE-139 — oldest pending queue core sign-off (REC-P1-037)

- Reused the focused Robolectric API 35 test `PowerStoreTest.openingDeckAfterStoreReopenFocusesOldestQueueOrder`: two synthetic pending cards survive SQLite close/reopen in independent queue order; `DeckController.open()` returns oldest-first and selects the oldest card. This signs only controller state; no Compose UI or device interaction was tested. No source changes were made for this Issue.
- Targeted test passed 1/1. Full offline source-boundary/JVM/Debug APK/AndroidTest APK/lint run passed 471/471 tests across 59 suites, zero failures/errors/skips; lint 0 errors/58 warnings. Full log SHA-256 `D30BCDD343E2FE62E7820A7D5A543813291CAD7F75DBECF32C196360A953D07B`.
- Independent report: `docs/issues/PC-ISSUE-139-oldest-queue-core-signoff.md`. `REC-P1-037` is signed only for the oldest-first controller model boundary; search/filter UI remains untested. Overall freeze remains HOLD.

## PC-ISSUE-138 — unified Todo projection core sign-off (REC-P1-034/035/036, current risk P0)

- Added two Robolectric API 35 regressions for TodoProjection and isolated PowerStore SQLite. They cover mixed ALARM/ACTIVITY/DEADLINE/MEMO query, completed/disabled/untracked exclusion, date-only and undated source semantics, and persisted MEMO completion removal/undo across database reopen. No Compose UI or physical device was used.
- Focused class passed 2/2 (0 failures/errors/skips). One initial run failed before assertions because the generated Robolectric SQLite path was 264 characters on Windows; shortening the test/database names fixed the runner setup. The initial failure log is preserved and documented in the Issue report.
- Full offline source-boundary/JVM/Debug APK/AndroidTest APK/lint run passed: 471/471 tests across 59 suites, 0 failures/errors/skips; lint 0 errors/58 warnings. Full log SHA-256 E50624CDF5EA42C27D06F06020E0FEE9F1E4AAE850418DA486836A4605200545. Test commit 58816435bbf401978daa28322e177100faeb38cc is pushed.
- Independent report: docs/issues/PC-ISSUE-138-todo-projection-core-signoff.md. The sign-off covers the narrowed core projection only; UI and device presentation remain unverified. Overall freeze remains HOLD.

## PC-ISSUE-137 — ambiguous AI REPLACE target core sign-off (REC-P1-050, current risk P0)

- Existing targeted `PowerStoreTest.ambiguousAiReplaceTargetRequestsClarificationWithoutWritingAcrossRestart` passed 1/1 on the `cc22d49` source/test baseline. With two same-title formal records and no `eventSeq`, the proposal requests clarification; `AiDraftService` writes no draft, and records/history/queue/identity count/sequence preview remain unchanged across SQLite reopen. No provider, UI, or device was used.
- Full offline `verifySkinSourceBoundary`, JVM tests, Debug/AndroidTest APK assembly and lint passed: 469/469 tests across 58 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `C2C97732B82813EE678F04F9ACC8CE8C249D797DF808E4D88AC0FB8190C70153`.
- Independent report: `docs/issues/PC-ISSUE-137-ai-ambiguous-replace-target-core-signoff.md`. Focused log/XML hashes and APK hashes are recorded there. Live model/chat UI/API 35/physical-device behavior remains outside the sign-off; overall freeze remains HOLD.

## PC-ISSUE-136 — AI chat history process/system restart persistence (REC-P1-083 RECONSTRUCTED)

- Core contract invariant 10 requires local chat-history persistence across restart. Added an Android instrumentation regression for the actual `AiChatHistoryStore`, with two synthetic messages. Cycle 0 refused to overwrite pre-existing history; every run reloaded and compared the same messages.
- On isolated API 35 AVD `emulator-5558`, 13 invocations passed across 13 process PIDs, including one Android VM reboot (boot count 3→4) and 11 host force-stop rounds. The test removed only its synthetic history file after the final successful assertion and then verified the file was absent. No provider, credentials, UI, or physical device was involved.
- Full offline Gradle source-boundary/JVM/Debug APK/AndroidTest APK/lint run passed: 469/469 JVM tests across 58 suites, 0 failures/errors/skips; lint 0 errors/58 warnings. Debug APK SHA-256 `35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4`; AndroidTest APK SHA-256 `D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A`.
- Independent report: `docs/issues/PC-ISSUE-136-chat-history-restart-persistence.md`. Corrected continuation log SHA-256 `EA9A2F55CA6ABA4E4391AECEC1703F49EDBEC603A005B1103AB914E7A7E5C0C5`; combined summary SHA-256 `BF218E95037B8159C5E867429EE1F973CEF43A15B0E9A03B26B2F2063DA52F7B`; full Gradle log SHA-256 `52148CECAC914B46117498F4D7236D312D3254635FB404527B1FF777AAC59C59`. Two host-runner errors are disclosed in the report and excluded from the 13 passing instrumentation invocations; no app assertion failed.
- Scope is limited to the local store across process and VM restarts. UI/clear-control behavior, low-memory recovery, real-device/OEM behavior, and the full live chat path remain outside this sign-off. Overall freeze remains HOLD.

## PC-QA-GATE-001 — current risk-based reconstructed matrix (2026-10-24)

- The user confirmed the original 209-row source is unavailable and explicitly directed the work to continue without further requests for it. The matrix is reconstructed from the core contract, acceptance documents, tests, Issues, and observed behavior; historical IDs remain unmapped and the old 126/82 partition is not a target or GO gate.
- Current ledger: 209 active reconstructed rows (the count is incidental), P0 144 / P1 64 / P2 1; 182 PASS / 0 FAIL / 8 BLOCKED / 13 NOT TESTED / 6 SPEC UNRESOLVED, plus one retained retired record. All 13 untested rows have explicit NOT TESTED actual/evidence text and remain open.
- Risk review moved `REC-P0-037/040` to P1; moved `REC-P1-014`–`016`, `034`–`036`, `044/045`, `049`–`051`, and `054`–`062` to P0. Stable ID prefixes remain trace keys; `candidate_priority` is authoritative. `REC-P1-013` is RETIRED because the owner accepted the pale-gray horizontal edge. Greedy title folding remains a separate P1 sign-off (`REC-P1-021`–`030`).
- `REC-P2-001`, `REC-P1-083`, `REC-P1-050`, `REC-P1-034/035/036`, `REC-P1-037`, `REC-P1-004`, `REC-P1-006/007`, `REC-P1-001`, `REC-P1-002`, `REC-P1-008/009`, `REC-P1-018/019/020`, and `REC-P1-052/053` record PC-ISSUE-135 draft recovery, PC-ISSUE-136 chat-history recovery, PC-ISSUE-137 ambiguous-target protection, PC-ISSUE-138 Todo projection, PC-ISSUE-139 oldest-first controller state, PC-ISSUE-140 timeline coordinate continuity, PC-ISSUE-141 zoom model boundaries, PC-ISSUE-142 initial-camera model, PC-ISSUE-143 Today-to-now fix, PC-ISSUE-144 DST tick labels, PC-ISSUE-145 independent wing scrolling, and PC-ISSUE-146 proposal/batch-review boundaries. The fabricated historical placeholder was removed. Structural validator: `tools/validate_core_freeze_acceptance_ledger.py`; artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r12-pc-issue-146.json`, SHA-256 `FAA809C1589F8C49F5104BD2A1F30ACEEDD8BE4A6944F8BAA615783A44D6277F`; ledger SHA-256 `224B1F1DD2D7F85ACA8E0395AA3C19EE87967CFD583C03E58589835F7BB1481E`. Structural-only (`product_behavior_tested=false`).
- Overall result remains HOLD for open P0/P1 tests, blockers/specifications, and user-owned physical-device/OEM/TalkBack checks. The missing original list is a disclosed provenance gap, not a reason to stop the reconstructed workflow.

## PC-ISSUE-135 — Android process restart draft persistence (REC-P2-001 RECONSTRUCTED)

- Added an Android instrumentation regression using a unique synthetic SQLite database. On a fresh API 35 AVD, 13 runner invocations across 13 distinct process IDs passed, with 12 host force-stops. One AI-origin PENDING draft retained its ID, event sequence and queue order; formal events, commit history and reminder intent remained absent. Only the generated synthetic database was removed after the final successful assertion.
- Separately verified one Android VM reboot on a second fresh API 35 AVD: boot count advanced 1→2, the installed package remained, and the same synthetic pending draft retained its identity/order/status across reboot. Three instrumentation invocations passed; the synthetic database was removed only after final assertions. This does not cover physical/OEM, crash/low-memory, or live chat-session recovery.
- Full offline Gradle boundary/test/Debug APK/AndroidTest APK/lint run passed: 469/469 JVM tests, 58 suites, 0 failures/errors/skips; lint 0 errors / 58 warnings. Debug APK SHA-256 `35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4`; AndroidTest APK SHA-256 `70933105F35F5486F316148DFDA1C86A6FA7AE93CFC5DFFE78BEF5EFF9BCC92D`.
- Independent report: `docs/issues/PC-ISSUE-135-android-process-restart-draft-recovery.md`. Run log SHA-256 `67507A6BB830D72B4A4930A8B4A9F5F4F5C77138D39BEC3E36CEF11B85F3C6DE`; summary JSON SHA-256 `2C7771DA82228515788C8ED28EE513FA49E7503A8559A422DC05B6F58C5D839D`; metadata-only DB inventory SHA-256 `4BFBBC3B9CE6F6FF5E8A4F5231A75FB59F8E179ED6BA4B473CC1912C604CFE0A`. A prior exploratory attempt on an already-running emulator was excluded; its default-named database was not read or removed, and the emulator was not reused.
- System reboot log SHA-256 `AA86B2BEDF5E1936A852F1967B32B8B8EAAA25B9F1A2D2540674D88A13E076B9`; summary SHA-256 `A72CDB86C4F46F0213BD9D0B6C37385F8A2C86D5BEA80E604DBB159F43549E14`; metadata-only post-reboot inventory SHA-256 `066D3B0F21854B5C72A4F6B804D184D126EFC2E9D18E80600F5F1B1DC326F757`.
- Scope excludes live chat transcript, Activity/UI restoration, crash/low-memory and physical/OEM lifecycle behavior. The overall freeze remains HOLD. The risk-based matrix update is recorded above; historical 209 IDs and the old 126/82 split are not treated as a gate.

## PC-ISSUE-002 — read-only AVD configuration recheck

- API 35 emulator-5554 has no `dev.powerfulcalendar` package. Emulator-5556 has the package, but its shared-preference filename listing contains only `powercal-onboarding.xml` and `powercal-ui.xml`; `powercal-ai.xml` is absent. No preference values or credentials were read, the app was not launched, settings were not changed, and no provider request was sent. Evidence JSON SHA-256 824CF934453870ECA21A97D0BB3825801F70FA6918DDBD7D0FEE10F88F6CA797.
- This confirms the live retest is blocked by unavailable app configuration; it does not test product behavior. REC-P1-047 remains BLOCKED and PC-ISSUE-002 remains PARTIAL / HOLD. Structural validation of the updated ledger passed: artifact SHA-256 BD2D976A66A48EF71C543DE0906D2257BCB4F066147BAD7BF61B07D7E18E3D7C; ledger SHA-256 D500FE6D936C71C9514D2FA30645438DC74F3318A6B89BBE373B62ABCE093921; counts remain 164/0/8/30/7.
- Current-code local failure-path rerun passed 7/7: synthetic structured HTTP 429, free-model empty HTTP 200, and five completion-policy cases. No provider request was made; this does not replace REC-P1-047's blocked live free-provider requirement. Log SHA-256 84AEB6F1B874575ABB0979349EA9A459285D5A6B0F34ABC00A0ADCBB7A3A7759; test XML SHA-256 5422891B1A6C8451FED0314B39B4A26F4AECBB6A92C3ABBB817620E1F7DAE92A and 2165CDF68DA513B11BD0E66EA882AFBD7E51B33191B7410797AFCABCA124E5DE.

## PC-ISSUE-003 — current-code backup/restore core rerun

- `BackupEnvelopeTest` 24/24 plus three focused `PowerStoreTest` restore/schedule/high-water cases passed 27/27, zero failures/errors/skips. This is isolated JVM/Robolectric SQLite coverage; no UI, emulator app, or real business database was touched.
- Log SHA-256 C6AFA52BB816008BDD3A0DBF7AEDC7B2705A4D0EC33494A2B401FFD6B570FE7B; XML SHA-256 9A97B489626CDA0AE616F666C4B256A3DBB50B0B5847322F8C0DAADA3374992D and EB3B885E3A977385C93EC8D054B7D3407E662BABE1D13FA8EF0A977D6C7A8EB3. Real DocumentsUI/provider lifecycle and physical-device coverage remain open; PC-ISSUE-003 stays PARTIAL / HOLD.

## DST policy spec audit

- `PRODUCT_BASELINE_G1-G11.md` §9 explicitly leaves DST details unresolved. Current code documents gap-forward / earlier-offset fold handling, but that implementation comment is not product approval. `OccurrenceWindowTest` (19/19) and `TemporalProtocolTest` (38/38) reran successfully; current tests include a spring-gap case and wall-clock recurrence across DST, but no fold-specific regression was found. Targeted log SHA-256 6DD225D91BA3C9D9333E9FE7B55137B9679CE90E74FF361B3140811B955193F3; XML SHA-256 F913E61734A977C392D5F160F79B6D5BE85B25381A34D67DBE492A3DC1266118 / 815BED4DFC2EB57505FE9FEA5405533876C34105033B0A3DCCA186C843F23F24. REC-P0-074/075 remain SPEC UNRESOLVED pending owner decision.

## PC-ISSUE-001 — external ICS handler availability

- Read-only `ACTION_VIEW`/`text/calendar` package queries on API 35 emulators 5554 and 5556 both returned `No activities found`. No Activity was launched, no app was installed, and no application data was touched. Evidence JSON SHA-256 AC76851C91A02993DF200805345D78FB698396FE4D90A2D8B0BD4B37BEFA4933. Structural validation passed with 209 unique rows and unchanged counts; corrected artifact SHA-256 AFC1222BB7D94941E00E9676501618E7473B95642832384AF657A9F2977A0A21, ledger SHA-256 15605E858B6F6FE9519270ADB2442E78664D8D6552E5F3FF2EB1FE5B2AFFA552.
- This confirms an environment block only; no external ICS import/re-export behavior was tested. `REC-P1-081` and PC-ISSUE-001 remain BLOCKED / PARTIAL; physical-device tests remain the user's scope.

## PC-ISSUE-002 — bounded free-provider retry readiness

- Hardened the Android API 35 live probe so no paid/free provider is contacted unless the explicit instrumentation argument `powercalLiveProbe=deepseek|free` is supplied. The two accepted model IDs are fixed in test code; model override remains in memory, and the flow uses synthetic input plus a unique temporary database.
- The emulator retest was **not sent**: `emulator-5554` currently has no `dev.powerfulcalendar` package (`run-as` reports unknown package). No app was reinstalled and no credential was read or restored. The last free-provider trace remains chat HTTP 200 followed by structured HTTP 429; Issue 002 and REC-P1-047 remain PARTIAL / BLOCKED.
- Full offline regression passed 469/469 across 58 suites, zero failures/errors/skips; source boundary, Debug/AndroidTest APK assembly and lint passed (0 errors / 58 warnings). Log SHA-256 3AFAEC67B24C92E808B0767939F35AC86BA427E8D3D742D10095C5CC7AE7805E. Test-only commit c33eb36a9308066983667cbf0091b8c451a27806 is pushed. This did not execute Android instrumentation or make a provider request.

## PC-TECH-006 — lint risk classification

- Classified the latest lint XML: 58 warnings / 0 errors across platform/target (4), dependency suggestions (6), Compose conventions (14), KTX suggestions (19), launcher/resources (13), and redundant SDK conditions (2). No code, UI, dependencies, manifest, or design assets changed; this does not claim a security scan.
- The `targetSdk=35` warning and portrait-lock warnings identify an Android 16 compatibility risk: fixed orientation may be ignored in some device configurations. API 36 orientation behavior is unverified and remains open; no UI work was performed. Report: docs/issues/PC-TECH-006-lint-warning-risk-classification.md. Evidence is the PC-ISSUE-134 full Gradle log and lint XML; overall freeze remains HOLD.

## PC-ISSUE-134 — authorized DeepSeek live chat-to-pending-draft flow

- On API 35 emulator-5554, one authorized DeepSeek V4.1 Flash chat and one structured-proposal request returned HTTP 200/200. A synthetic ACTIVITY became a PENDING draft in a unique temporary SQLite database and retained identity/order/status across close/reopen; no formal write occurred. Calendar context was disabled, model override was in memory, and credentials were not logged. No UI or physical device was used.
- Instrumentation passed 1/1. Full Debug boundary/test/APK/AndroidTest APK/lint passed 469/469 across 58 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Test commit 477ddf44f1199e3316c5981e0a640c41efcdd416 is pushed. Report: docs/issues/PC-ISSUE-134-deepseek-chat-to-draft-live-flow.md.
- This signs only the authorized DeepSeek model-to-local-draft route. REC-P1-047 remains BLOCKED because the free provider's structured stage returned HTTP 429; PC-ISSUE-002 remains PARTIAL / BLOCKED and the freeze remains HOLD. Ledger totals remain 164 PASS / 0 FAIL / 8 BLOCKED / 30 NOT TESTED / 7 SPEC UNRESOLVED. Structural-only artifact .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-134.json SHA-256 FB25654B9A34DFD676713F580D1C955B24E6BCDBBE504ECE8D16150A8582465A; ledger SHA-256 CD6275A893058EB678FBD9B3BEA04EAEAB6BFEEE26BFEFDC18366074852C8E58.

## PC-ISSUE-133 — AI chat-to-pending-draft local pipeline

- Added an API 34 Robolectric loopback regression linking chat-marker parsing, current-user source isolation, structured proposal parsing, AiDraftService persistence and SQLite reopen. A fabricated model source is discarded; the original input becomes one PENDING draft with stable identity/order; no formal event/history/schedule intent is written. No provider, UI, device or DeepSeek call.
- Focused test passed 1/1. Full Debug boundary/test/APK/AndroidTest APK/lint passed 469/469 across 58 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Test commit b4f5179efa689015bb5ec73a2987a5e76f2c6b82 is pushed.
- Report: docs/issues/PC-ISSUE-133-ai-chat-draft-pipeline.md. This is local stub evidence only; REC-P1-047 remains BLOCKED on the live free-provider structured HTTP 429, so PC-ISSUE-002 and the overall freeze remain PARTIAL / HOLD. Ledger counts remain 164 PASS / 0 FAIL / 8 BLOCKED / 30 NOT TESTED / 7 SPEC UNRESOLVED. Structural-only artifact .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-133.json SHA-256 CA6AD79C2254613BE21C06FCD23A6B49756227190F32CA0C672A7CF507D3002C; ledger SHA-256 F2B851D069C7322AAFA636E7406C3B3FA9A9C94B608BD1E3DD999F6CD3966820.

## PC-ISSUE-132 — SAF URI-grant revocation safe failure (REC-P0-107 narrowed PASS)

- A synthetic API 35 ContentProvider granted then revoked read access. Android enforced the revocation with SecurityException; safe permission wording and unchanged event/history/pending/identity/sequence state were verified in a disposable SQLite database. The production SAF reader is now shared through **SafBackupReader**. No UI was opened; real DocumentsUI/provider lifecycle and physical/OEM behavior remain untested.
- Emulator instrumentation passed 1/1. Full Debug boundary/test/APK/AndroidTest APK/lint passed 468/468 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Code/test commit 0d83d48f6bea18b64e8965ab62bcb39d55f164a8 is pushed.
- Report: docs/issues/PC-ISSUE-132-saf-uri-grant-revocation.md. PC-ISSUE-003 remains PARTIAL / HOLD. Ledger: 164 PASS / 0 FAIL / 8 BLOCKED / 30 NOT TESTED / 7 SPEC UNRESOLVED; P0 120/0/0/1/5, P1 44/0/8/29/1. Structural-only artifact .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-132.json SHA-256 7D3BDB2012A2838CE6CE85F55682B38C1E211EF0A3DD64AAD55C924614AD23E2; ledger SHA-256 FD79266CD97115B90641DA75F49147F6C706FDD4B60F852663DFCB2B3754AD74. Overall freeze remains HOLD.

## PC-ISSUE-131 — AI targetless REPLACE clarification guard (REC-P1-050 remains NOT TESTED)

- Reproduced that a blocked targetless REPLACE exposed `#null` instead of asking which event to change. Updated the core parser warning while retaining the existing blocked/no-write behavior; added a Robolectric API 34 parser → AiDraftService → SQLite-reopen regression with two identical formal targets. This is core-only; no UI layout, provider, or physical-device test.
- Pre-fix targeted test failed on the clarification text. Post-fix targeted checks passed 2/2. Full Debug boundary/test/APK/lint passed 468/468 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Code/test commit `e8daade48e78997a94c86645730b0226dfdf1cab` is pushed.
- Report: `docs/issues/PC-ISSUE-131-ai-ambiguous-replace-target.md`. `REC-P1-050` stays NOT TESTED because live-model and chat UI disambiguation remain unverified; totals remain 163 PASS / 0 FAIL / 9 BLOCKED / 30 NOT TESTED / 7 SPEC UNRESOLVED. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-131.json` SHA-256 `4F97B8068029A5BBDAA5052A01A556E5FDA925290694D6EA5C24E7E3A4EB5929`; ledger SHA-256 `7C6EF6938ECAF938656A3F25B8CE30CAEEC462DC2173D2C604B53BD791FC8F72`. It validates only 209 unique rows and 126/82/1 priorities; product_behavior_tested=false. Overall freeze remains HOLD.

## PC-ISSUE-130 — DeckController live edit state during autosave (REC-P1-043 narrowed PASS)

- Added currentId/dirty assertions while background autosave is blocked: the newest accepted form stays visible on the same card and remains dirty until storage catches up. Existing regression checks nonblocking edit and saves the latest version after release. No production/UI changes.
- Focused test passed 1/1. Full Debug boundary/test/APK/lint passed 467/467 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Test commit `d838c9b6d6d7ecb8e2f6199a429d07eca0a86e12` is pushed.
- Report: `docs/issues/PC-ISSUE-130-deck-controller-live-edit-state.md`. Ledger: 163 PASS / 0 FAIL / 9 BLOCKED / 30 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5, P1 44/0/8/29/1. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-130.json`, SHA-256 `39340110A14157DEA49F09AF6206579C953D02D4355032D042C32CAD9A160A16`; ledger SHA-256 `2A02489CD27FF377E677ABDDFFFFA93190966CC53E38D6F738226CEB554D0338`. Overall freeze remains HOLD.

## PC-ISSUE-129 — DeckController movement clamps (REC-P1-040 narrowed PASS)

- Extended the existing core movement test to verify reverse movement reaches the first card and clamps there; the same test verifies one-card forward movement and last-card clamping. No production/UI changes; gesture recognition and Compose/device mapping remain untested.
- Focused test passed 1/1. Full Debug boundary/test/APK/lint passed 467/467 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Test commit `07557fe62a7ac4506f38618028aff9439b86da95` is pushed.
- Report: `docs/issues/PC-ISSUE-129-deck-controller-move-clamping.md`. Ledger: 162 PASS / 0 FAIL / 9 BLOCKED / 31 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5, P1 43/0/8/30/1. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-129.json`, SHA-256 `46B90076E13454DF792678500CA49F1D3452DBFB77825E5C567950BD06AF17D8`; ledger SHA-256 `0230EDFF3547F140680396A60984A0A70DA819FF0C30CD1EA5646C1E6D410E6D`. Overall freeze remains HOLD.

## PC-ISSUE-128 — DeckController completion counts (REC-P1-046 narrowed PASS)

- Added a core-only regression that publishes one card and discards the final card, then checks completion reports published=1 and discarded=1. The initial attempt hit the existing one-second terminal-action guard because the fake clock did not advance; adding `tick()` fixed the test setup. No production/UI changes; timeline-camera restoration and SQLite-backed stats remain outside scope.
- Corrected focused regression passed 1/1; full Debug boundary/test/APK/lint passed 467/467 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Test commit `5c741192727721cc26831a01fb9b9c9cd9b2b408` is pushed.
- Report: `docs/issues/PC-ISSUE-128-deck-completion-counts.md`. Ledger: 161 PASS / 0 FAIL / 9 BLOCKED / 32 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5, P1 42/0/8/31/1. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-128.json`, SHA-256 `C04462462D939BA4BFB4765FB6960E1219741F0C46A280836E567D1E460B0A44`; ledger SHA-256 `015CE8D4281738348B6BAFE9780D1912F0EF172998695228ADB0BF4929BDA878`. Overall freeze remains HOLD.

## PC-ISSUE-127 — DeckController retryable save and card switching (REC-P1-044/045 narrowed PASS)

- Extended the existing storage-failure regression to recover the fake store and retry the same accepted input; verified it is staged and dirty/error/busy clear. Existing controller tests verify latest-text stage before card navigation and refusal to switch after stage failure. No production code changed; claims are limited to JVM controller behavior with fake persistence, excluding UI, SQLite durability, exit/lifecycle and devices.
- Focused regression passed 1/1. Full Debug boundary/test/APK/lint passed 466/466 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Test commit `41c7f849438128960b6f453c44563141feb0d1e5` is pushed.
- Report: `docs/issues/PC-ISSUE-127-deck-controller-retryable-save.md`. Ledger: 160 PASS / 0 FAIL / 9 BLOCKED / 33 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5, P1 41/0/8/32/1. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-127.json`, SHA-256 `77CEBCB85D696C3A5121B78EE1AED29F611AA6DFBB86B7FD8642109B0FF6E5E6`; ledger SHA-256 `38E9BEE15710B9F59029E6C992B52BE1D48721A7320754994A044F08E16157C`. Overall freeze remains HOLD.

## PC-ISSUE-126 — AI commit authorization gate (REC-P1-054 narrowed PASS)

- Reproduced that `PowerStore.commit(actor="ai")` without an `AiSession` could proceed to formal commit. The core store now denies AI actors without a scoped session. The regression confirms no object/history/intent write and a pending draft; existing valid-session and replay tests remain green.
- Pre-fix targeted failure; post-fix targeted 1/1 PASS; full Debug boundary/test/APK/lint passed 466/466 across 57 suites, zero failures/errors/skips, lint 0 errors / 58 warnings. Code/test commit `fc0e69ac90d25878c660d046a98818629d6c1d39` is pushed.
- Report: `docs/issues/PC-ISSUE-126-ai-commit-requires-authorization.md`. This signs only the PowerStore gate; user-confirmation UI and live AI remain untested. Ledger: 158 PASS / 0 FAIL / 9 BLOCKED / 35 NOT TESTED / 7 SPEC UNRESOLVED. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-126.json`: SHA-256 `3DC7D2B92820502212C3BF4CCEAECFF56D67BC01EF16FAE1773F0668AB51A75B`; ledger SHA-256 `A11035534F763B61330940D8970B45AABA0D2CAA3AAD1F56804C109FB6006338`. Overall freeze remains HOLD.

## PC-ISSUE-125 — AI proposal dependent-write rollback (REC-P1-059 narrowed PASS)

- Added assertions that an injected schedule-intent write failure leaves no formal event, commit history, or desired intent and preserves the pending draft after SQLite reopen. Other independent batch items still commit; a new authorization retries the failed item. No production source changed.
- Targeted Robolectric API 34 test passed 1/1; full Debug boundary/test/APK/lint passed 465/465 across 57 suites, zero failures/errors/skips, lint 0 errors / 58 warnings. Test commit `237486011a64a4598b3dd7a3183ee2c09314f034` is pushed.
- Report: `docs/issues/PC-ISSUE-125-ai-dependent-write-rollback.md`. This signs only one proposal's dependent SQLite writes; no cross-proposal dependency group, AI network or UI claim. Ledger totals: 157 PASS / 0 FAIL / 9 BLOCKED / 36 NOT TESTED / 7 SPEC UNRESOLVED. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-125.json`: SHA-256 `49EB75C9641F72F273EA5C4532EE95B9B30E8EC67D69D273315FF33EFB59AEA0`; ledger SHA-256 `DB65C04E298584BB31EEB1B29DC0985CF2069A9FB27CC63382FDA3EB7DFA1452`. Overall freeze remains HOLD.

## PC-ISSUE-124 — core timeline lane allocation (REC-P1-016/017)

- `TemporalProtocolTest` passed 38/38; five existing pure-model cases directly cover deterministic stable ordering, input-order independence, independent lanes and lane reuse. No app/test source, UI, emulator or physical device was changed or tested.
- Report: `docs/issues/PC-ISSUE-124-timeline-lane-core-signoff.md`. Targeted log SHA-256 `88772FC37E170E12B4D118852B4BFEB7251B18606A4AA716771914B79EC71321`; XML SHA-256 `F51185351FE75B645F0DB01F7D5B41FBB6CBB2CB33D9BE5BD000FE650B838E34`.
- REC-P1-016/017 are PASS for the narrowed core model only. Ledger totals: 156 PASS / 0 FAIL / 9 BLOCKED / 37 NOT TESTED / 7 SPEC UNRESOLVED. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-124.json`: SHA-256 `B37DF31495E1AC7DF2F06AC93AD1246EC9E252CF1A2B947892B7CA2B112AC1C8`; ledger SHA-256 `C659DC487695E0C117A7205092EB1C30C5B08CED4ED1A62C7EB6A24AADBD2406`. Overall freeze remains HOLD; physical-device testing is user-owned.

## PC-ISSUE-GREEDY-LABEL-FOLDING — REC-P1-028 long-title measurement sign-off

- Fixed long-title truncation by measuring a grapheme-boundary prefix plus an explicit ellipsis at the existing 10sp floor. Full event bounds and accessibility title remain unchanged.
- Focused Compose tests passed 8/8; greedy policy tests 8/8. Full Debug boundary/test/APK/lint regression passed 465/465 across 57 suites; lint 0 errors / 58 warnings. Code/test commit `2fe0c6643547dc0c195feeb602bb47460398c782` is pushed.
- Report: docs/issues/PC-ISSUE-GREEDY-LABEL-FOLDING.md. REC-P1-028 is signed PASS narrowly for Robolectric API 35; no AVD screenshot was captured because both existing emulators were online and no isolated AVD was registered. Core freeze remains HOLD at 154 PASS / 0 FAIL / 9 BLOCKED / 39 NOT TESTED / 7 SPEC UNRESOLVED.

## PC-ISSUE-121 — AI current-input source isolation guard (PC-ISSUE-002 remains PARTIAL/BLOCKED)

- Added a local `AiClient.chat` loopback regression: when the model response includes a fabricated `source`, the client hides the internal marker and exposes only the current raw user text as `proposalSource`. No production changes or real provider call.
- Focused test passed 1/1. Full Debug boundary/test/APK/lint passed 462/462 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-121-ai-current-input-source-isolation.md. Test commit `6345dbba2216b625bf95eaad7651d3b6cfc89275` is pushed.
- Local evidence does not prove live-model compliance. PC-ISSUE-002 remains PARTIAL/BLOCKED on prior free-provider HTTP 429/503; core freeze remains HOLD at 148 PASS / 0 FAIL / 9 BLOCKED / 45 NOT TESTED / 7 SPEC UNRESOLVED.

## PC-ISSUE-120 — free-model empty HTTP 200 core recovery guard (PC-ISSUE-002 remains PARTIAL/BLOCKED)

- Added a local loopback integration regression for the existing no-blind-retry policy: first-call budget is 2048; an empty HTTP 200 with `finish_reason=stop` produces a structured-stage warning, no proposal/raw reply, and exactly one request. No production code or external provider call.
- Focused test passed 1/1. Full Debug boundary/test/APK/lint passed 461/461 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-120-ai-empty-http200-core-recovery.md. Test commit `b51b0e830c9ff968b994cda564dcdd481986d46e` is pushed.
- PC-ISSUE-002 stays PARTIAL/BLOCKED because the provider proposal stage previously returned HTTP 429/503; no retry around rate limits or paid provider request. Per latest user scope, no UI work is in this continuation. Core freeze remains HOLD at 148 PASS / 0 FAIL / 9 BLOCKED / 45 NOT TESTED / 7 SPEC UNRESOLVED.

## PC-ISSUE-119 — AI incomplete-field core recovery sign-off (REC-P1-051 narrowed PASS)

- Missing Activity times remain blank; invalid submit creates no formal event/history, INVALID draft survives SQLite reopen, and repair returns it to PENDING for one commit. An undecidable missing kind requests clarification without allocating a proposal, draft, or event identity. Provider/API 35/device paths remain outside this sign-off.
- Targeted parser/PowerStore regressions passed 4/4. Full Debug boundary/test/APK/lint passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-119-ai-incomplete-proposal-core-signoff.md.
- Freeze remains HOLD: 148 PASS / 0 FAIL / 9 BLOCKED / 45 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5; P1 29/0/8/44/1. Structural-only validation .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-119.json, SHA-256 9D9C9ADF65C655E8C985772B3B0144FDB8CACF3D27A311E002E974A524F15512; ledger SHA-256 70204387131BD14FD70A96F43298598B6D365E68BB754DDDFFF3726CAD90E200.

## PC-ISSUE-118 — mixed CREATE/REPLACE core draft isolation sign-off (REC-P1-049 narrowed PASS)

- One local structured response yields independent CREATE and REPLACE operations; `AiDraftService` creates two pending drafts, leaves the formal CREATE absent, and does not mutate the REPLACE target. Provider/API 35, presentation, approval, and reopen persistence are outside this sign-off.
- Targeted Robolectric API 34 regressions passed 2/2. Full Debug boundary/test/APK/lint passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-118-mixed-proposals-core-signoff.md.
- Freeze remains HOLD: 147 PASS / 0 FAIL / 9 BLOCKED / 46 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5; P1 28/0/8/45/1. Structural-only validation .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-118.json, SHA-256 F2490DF21C9C00691FC71B760F729CF18D830BEFA2A161F4F3865FF2176D985C; ledger SHA-256 540B486F5493752F09B10DA863026ECB098E3134BDB25B4E186F4B09AA83A840.

## PC-ISSUE-117 — AI HTTP 429 client core contract sign-off (REC-P1-062 narrowed PASS)

- The local loopback HTTP 429 stub yields a structured-proposal-stage/status warning, no proposal/raw reply, and no provider error body or API key disclosure. No external provider was called; API 35/provider and presentation paths remain outside this sign-off.
- Targeted Robolectric API 34 regression passed 1/1. Full Debug boundary/test/APK/lint passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-117-ai-http429-core-signoff.md.
- Freeze remains HOLD: 146 PASS / 0 FAIL / 9 BLOCKED / 47 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5; P1 27/0/8/46/1. Structural-only validation .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-117.json, SHA-256 3AD7FA01ABB82266470A7C1C2881E421E6F227D6436EB39187E33557503A2CB0; ledger SHA-256 B932FEB0957DE7534F42418D43076DFB1B58D3FFD4D9C279DE87007BE8458816.

## PC-ISSUE-116 — repeated commit core idempotency sign-off (REC-P1-057 narrowed PASS)

- Repeating `PowerStore.commit` for one draft returns the original revision/commitId with `duplicate=true`; history remains one row. API 35/provider approval-entry and device paths are outside this sign-off.
- Targeted Robolectric API 34 regression passed 1/1. Full Debug boundary/test/APK/lint passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-116-repeat-commit-core-idempotency-signoff.md.
- Freeze remains HOLD: 145 PASS / 0 FAIL / 9 BLOCKED / 48 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5; P1 26/0/8/47/1. Structural-only validation .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-116.json, SHA-256 8293459B130547FDD612B8249859D0E09B5355FDE9CA47D3E11CB83912F47DB9; ledger SHA-256 7D76245F0CC198183152E0B404FA7B2B947258209AACA4C4130C0CC782CA865A.

## PC-ISSUE-115 — AI CREATE draft core persistence sign-off (REC-P1-056 PASS)

- A local parsed CREATE proposal becomes one PENDING draft without formal event/history; after SQLite reopen its ID, event sequence, version, operation, form, status, and queue order remain unchanged.
- Targeted Robolectric API 34 regression passed 1/1. Full Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-115-ai-draft-core-persistence-signoff.md. API 35/provider selection is outside this sign-off.
- Freeze remains HOLD: 144 PASS / 0 FAIL / 9 BLOCKED / 49 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5; P1 25/0/8/48/1. Structural-only validation .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-115.json, SHA-256 4B62D6669634D5B6C20A4C47E05585288019D311B95C43D1D34334535E5B7030; ledger SHA-256 42386817457F1EBDFE6C5BF131D068B492E9BBC2301F68C99C28B7201B8038A2.

## PC-ISSUE-114 — explicit single-occurrence core override sign-off (REC-P0-078 PASS)

- The explicit occurrence-key PowerStore operation moves only the selected slot from 07:30 to 09:15; the anchor, original keys, other slots, and event identity remain stable. SQLite reopen preserves the record/projection, one identity, and two history rows.
- Targeted Robolectric API 34 regression passed 1/1. Full Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-114-single-occurrence-core-signoff.md. Default-scope selection and other edit scopes are outside this sign-off.
- Freeze remains HOLD: 143 PASS / 0 FAIL / 9 BLOCKED / 50 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5; P1 24/0/8/49/1. Structural-only validation .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-114.json, SHA-256 3048D54BB5F8CCEB6762269685B535DD2AC5791EDFE0FA98744877040872B1F4; ledger SHA-256 1CC18C043E491828FCB23BCE54B02192ACECA1B63C376A9C57268EE1297BEAF1.

## PC-ISSUE-113 — AI/ICS core read-only projection sign-off (REC-P0-082 PASS)

- Scoped to the core SQLite-to-ICS and AI request-snapshot projections; reading does not create a second event source or mutate formal rows, history, reminder intent, identity count, or sequence preview. The local AI stub receives one formal event and produces no proposal or draft.
- Targeted Robolectric API 34 regression passed 2/2 (ICS 1/1; AI snapshot 1/1). Full Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-113-read-only-core-projections-signoff.md. Android platform, SAF, external calendar, and device behavior are not signed by this result.
- Freeze remains HOLD: 142 PASS / 0 FAIL / 9 BLOCKED / 51 NOT TESTED / 7 SPEC UNRESOLVED; P0 118/0/1/2/5; P1 24/0/8/49/1. Structural-only validation .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-113.json, SHA-256 46B6F9A7ECDDE04D3FC04A998E734DBBEB54D347A7131FB40BEB3F1D7102162C; CSV SHA-256 55FB44E600F987C4B5B445382089E539949AEA23E5048804B56D16B674DDA73C.

## PC-ISSUE-112 — API 35 exact-alarm denial core sign-off (REC-P0-112 PASS)

- Revalidated the API 35 Robolectric scheduler/store denial branch: the EXACT reminder persists as BLOCKED with the exact-alarm permission hint, and ShadowAlarmManager receives no registration.
- Targeted regression passed 1/1. Full offline Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No app or test source changed.
- Independent report: `docs/issues/PC-ISSUE-112-api35-exact-alarm-core-signoff.md`. `REC-P0-112` is PASS for the core denial state only. Actual Android permission behavior and hint delivery remain untested.
- Freeze remains HOLD: 141 PASS / 0 FAIL / 9 BLOCKED / 52 NOT TESTED / 7 SPEC UNRESOLVED; P0 117/0/1/3/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-112.json`, SHA-256 `EC8710582C1E00B9BB43791493D62F53F7FAE9E9E0D5553406C80F34C7B70CC3`; ledger SHA-256 `6BEF65F539959780BFE2DF917F74E44F953BE8C7A64F051F03E6ED8E4A111EBC`.

## PC-ISSUE-111 — this-and-future recurrence core sign-off (REC-P0-079 PASS)

- Revalidated the four direct PowerStore transaction cases: split and migrate compatible future state, injected insert rollback, first generated slot identity retention, and first non-skipped slot/remaining-count handling.
- Targeted regression passed 4/4. Full offline Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No app or test source changed.
- Independent report: `docs/issues/PC-ISSUE-111-this-and-future-core-signoff.md`. `REC-P0-079` is PASS for the core transaction criterion; scope selection and device behavior remain outside the result.
- Freeze remains HOLD: 140 PASS / 0 FAIL / 9 BLOCKED / 53 NOT TESTED / 7 SPEC UNRESOLVED; P0 116/0/1/4/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-111.json`, SHA-256 `E7C3F871A1DD2743FC045B8251D116AAE0EF0AD47626CF24ED69B889A371B3AC`; ledger SHA-256 `8F75A6EB9BA2046EF604B0B9558BECF34ECFBF8AA9AD6EC995A8FFAA6A107E2A`.

## PC-ISSUE-110 — reminder recovery receiver core sign-off (REC-P0-117 PASS)

- Revalidated six synthetic API 34 recovery branches: boot, package replacement, timezone/time change, exact-alarm permission change, and explicit reconciliation. Receiver async completion, one rebuilt AlarmManager registration, durable SCHEDULED state after SQLite reopen, and lock release passed.
- Targeted regression passed 2/2. Full offline Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No app or test source changed.
- Independent report: `docs/issues/PC-ISSUE-110-reminder-recovery-core-signoff.md`. `REC-P0-117` is PASS for synthetic receiver core behavior only. Actual Android system broadcasts and device recovery remain untested.
- Freeze remains HOLD: 139 PASS / 0 FAIL / 9 BLOCKED / 54 NOT TESTED / 7 SPEC UNRESOLVED; P0 115/0/1/5/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-110.json`, SHA-256 `F4253D7827BFD7843976CAAF94989E5F9AEA4760119C7E900D1B1C56271899CD`; ledger SHA-256 `9F935D5F3746DAD97F8382CE0D4CCC47C0A0448EA1FD49280C6B73429118F33C`.

## PC-ISSUE-109 — date-only Deadline core sign-off (REC-P0-023 PASS)

- Narrowed the reconstructed expected result from rendered START-END presentation to the core DATE-data invariant because the row declares no UI/device environment. Completion preserves `deadlineDate` and DATE precision; `deadlineMillis/startMillis/endMillis` remain null, including after SQLite reopen. Rendering is not claimed.
- Targeted Robolectric regression passed 1/1. Full offline Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No app or test source changed.
- Independent report: `docs/issues/PC-ISSUE-109-date-deadline-core-signoff.md`. `REC-P0-023` is PASS for the narrowed reconstructed core candidate; the missing historical mapping remains unresolved.
- Freeze remains HOLD: 138 PASS / 0 FAIL / 9 BLOCKED / 55 NOT TESTED / 7 SPEC UNRESOLVED; P0 114/0/1/6/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-109.json`, SHA-256 `C5BE9C34C74C38278E8BA64DD4041C67408F4C714F16B23AEA3EEA93917CD49B`; ledger SHA-256 `E6E28BDC1391820B55B9EC963AD9CF9083D7A966DB3D28F014A3D5DAC7AF2FEA`.

## PC-ISSUE-108 — whole-series core persistence sign-off (REC-P0-080 PASS)

- Narrowed the reconstructed action from “Select whole series” to “Edit whole recurring rule through EditSession” because the declared Robolectric API 34/SQLite environment excludes a scope-selection interface. The existing test updates the stored recurrence on the same event identity, preserves the original anchor, verifies the projected occurrence keys and rechecks record/history/identity after reopen.
- Targeted regression passed 1/1. Full offline Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No app or test source changed.
- Independent report: `docs/issues/PC-ISSUE-108-whole-series-core-signoff.md`. `REC-P0-080` is PASS for its declared core persistence scope; UI scope-selection behavior is not asserted.
- Freeze remains HOLD: 137 PASS / 0 FAIL / 9 BLOCKED / 56 NOT TESTED / 7 SPEC UNRESOLVED; P0 113/0/1/7/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-108.json`, SHA-256 `7554EB9924140E345AE9A3292C46CF1B64DA00374396CD48CAEA1D4B87984AB1`; ledger SHA-256 `798E12FD3FF6174450BBCF593B48D2084981098F857127A842D85E55C492E251`.

## PC-ISSUE-107 — completion-tracking reminder core sign-off (REC-P0-017 PASS)

- Re-read the candidate’s declared environment and expected result: Robolectric API 34, isolated PowerStore SQLite, pending reminder intent and same-database reopen; UI, Android system scheduling and device are explicitly outside this reconstructed row. Revalidated the existing two core regressions: both tracking directions preserve enabled/reminder state and durable intent across SQLite reopen; API 34 reconcile schedules the updated revision once and rejects the old revision.
- Targeted tests passed 2/2. Current full offline Debug boundary/test/APK/lint run passed 460/460 across 57 suites, 0 failures/errors/skips; lint 0 errors / 58 warnings. No app or test source changed.
- Independent report: `docs/issues/PC-ISSUE-107-tracking-reminder-core-signoff.md`. `REC-P0-017` is now PASS only for its reconstructed core acceptance; Android OS delivery and original historical-case mapping are not claimed.
- Freeze remains HOLD: 136 PASS / 0 FAIL / 9 BLOCKED / 57 NOT TESTED / 7 SPEC UNRESOLVED; P0 112/0/1/8/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-107.json`, SHA-256 `B8AEF4DE9137931A66578EEAE219EEB38CD8D7C01515E128693E5C5192ED0FE8`; ledger SHA-256 `4A83C16E8D29F918D79415FE119B27FACFB887DAE0BF65150D260C0AAE68E1F6`.

## PC-ISSUE-106 — API 35 exact-alarm denial core checkpoint (REC-P0-112 remains NOT TESTED)

- Added one API 35 Robolectric `ReminderValidationTest`: exact reminder commit followed by permission-denied reconciliation persists `BLOCKED`, retains the exact-alarm permission hint, and leaves `ShadowAlarmManager` empty. Core test only; no production/UI change.
- API 35 method passed 1/1; the complete `ReminderValidationTest` class passed 17/17. Full offline Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. APK SHA-256 `138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`.
- Test commit `f30c1ce5c6e6c6e835365f751dc689ab245b5aca` is pushed. At that checkpoint, actual API 35 Android permission behavior and device guidance were untested, so REC-P0-112 remained NOT TESTED. PC-ISSUE-112 later signs the narrowed scheduler/store denial branch PASS; actual system permission behavior and guidance delivery remain untested. Reports: `docs/issues/PC-ISSUE-106-api35-exact-alarm-denial-core.md` and `docs/issues/PC-ISSUE-112-api35-exact-alarm-core-signoff.md`.
- Full log `.local/android-test/reports/freeze-20261024/pc-issue-106-verified-gradle.log`, SHA-256 `B14E35666D4C5B2159CCAFEFE12C6244C803B864E23172A266DC736A02BBD15B`. Structural-only ledger validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-106.json`, SHA-256 `BA926DB3211EF7D46F5700D4228A9577B58C4A2A8C79BC848D434495D9F72965`; ledger SHA-256 `C7912597DC9DECBBB21D5A4FB50CB8EF5665E6FA91F7A05BBBC8240AE03B2C59`.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1.

## PC-ISSUE-105 historical checkpoint — reminder grace-window branch tests (superseded by the owner sign-off above)

- At this checkpoint, three Robolectric API 34 core tests covered receiver delivery inside the then-current grace constant, a late receiver, and overdue-intent reconciliation. Product duration was not yet owner-approved, so the row remained SPEC UNRESOLVED at that time.
- Focused two-suite run passed 24/24. Standard offline Debug boundary/test/APK/lint command passed 459/459 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. APK SHA-256 `138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`.
- Historical test commit `2d89ffba5317eb9b0164682702868e5cf75c8fe4` is pushed. Current resolution and final status are recorded in the latest PC-ISSUE-105 entry at the top of this file and the independent Issue report.
- Full log `.local/android-test/reports/freeze-20261024/pc-issue-105-verified-gradle.log`, SHA-256 `2289F28174AF65C694D74D3048788FB6A0190830782AC21AB97336B0FF6DD755`. `:app:check` separately stops at the existing Release keystore gate; see the report.
- Historical checkpoint freeze counts were 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; they were superseded by r29 in the latest entry above. The checkpoint's structural artifact and hashes remain in its previously committed report version.

## PC-ISSUE-104 — AI shared-context read-only projection (candidate remains NOT TESTED)

- Added a local HTTP stub regression that passes one SQLite formal event snapshot to `AiClient`. The request contains exactly one event with its real identity/revision; the response creates no proposal, and rows, history, reminder intent, identity count, and sequence preview remain unchanged.
- Targeted Robolectric API 34 passed 1/1. Full Gradle passed 456/456 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No external provider or UI was used.
- Test commit `e2f413a74a7a504f3ca493adc5ee3ad69e497f25` is pushed. Independent report: `docs/issues/PC-ISSUE-104-ai-context-is-read-only-projection.md`. Paired with Issue 089 ICS evidence, this checkpoint left `REC-P0-082` NOT TESTED for the broader scope; PC-ISSUE-113 later signs the narrowed AI/ICS core criterion PASS.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-104.json`, SHA-256 `7D8CB24D683736DA27397053013F14AE29702918992A00956CD968BF3A07FD79`; ledger SHA-256 `440DCE8934F4782FC54A292DA10B1C0E88C4C051F069F7A88EAAA0D772278353`.

## PC-ISSUE-103 — AI missing-kind clarification (candidate remains NOT TESTED)

- Reproduced that an untyped ambiguous AI item was dropped with only an “ignored” warning. The core parser now asks which of the four event kinds the user means, while creating no proposal/draft or event identity until clarified.
- The pre-fix regression failed at the missing clarification text. Post-fix targeted Robolectric API 34 passed 4/4 across parser and PowerStore suites. Full Gradle passed 455/455 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No UI code changed.
- Fix/test commit `d381e1dc87a6ae1fe459b50aad79dd63b391fb9c` is pushed. Independent report: `docs/issues/PC-ISSUE-103-ai-missing-kind-clarification.md`. API 35/provider/card paths remain untested, so `REC-P1-051` remains NOT TESTED.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-103.json`, SHA-256 `C96848D5A9F75692FA837532EA7D3F3D91A0FD15086F4C96412536065DD74CD6`; ledger SHA-256 `31F3050CDC00C884E89A2EC219F2C53A51F35ED1D72387F89307984E625C84AC`.

## PC-ISSUE-102 — completion tracking toggle keeps reminder scheduling (candidate remains NOT TESTED)

- Added a Robolectric API 34 `ReminderValidationTest` that schedules an Activity reminder, disables completion tracking, then verifies the new revision returns to PENDING and reconciles to SCHEDULED with one Alarm; the updated revision validates and the old revision is rejected.
- Targeted test passed 1/1. Full Gradle passed 454/454 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. This is scheduler-adapter evidence only; real Android alarm/notification delivery remains untested. No UI or production code changed.
- Test commit `200d5ae4783bfe2d2b0d95acd0ce0f572a5d2ebe` is pushed. Independent report: `docs/issues/PC-ISSUE-102-tracking-toggle-keeps-reminder-scheduled.md`. `REC-P0-017` remains NOT TESTED.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-102.json`, SHA-256 `FB29AA81FCFCA98BED762116A0F6E6AFCEBC7439642421B6BBEA73FBB7DC9085`; ledger SHA-256 `671FDBD1B5371FC6B961A867713CBE0B222A03E943820294A2B3D4A6F6FDAA7E`.

## PC-ISSUE-101 — AI missing-time proposal core checkpoint (candidate remains NOT TESTED)

- Added `PowerStoreTest.parsedAiActivityWithMissingTimeRemainsRepairableDraftAcrossRestart`. The parser keeps empty ACTIVITY times blank; the proposal stays a draft, a formal commit is rejected, and the INVALID draft/form survive SQLite reopen. Filling both times restores PENDING and allows one valid commit.
- Targeted Robolectric API 34 passed 2/2. Full Gradle passed 453/453 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. At that checkpoint the missing-type and API 35/card paths were not tested; Issue 103 later added core missing-type clarification evidence, while API 35/provider/card paths remain untested. No UI changed.
- Test commit `814f3f2c6cb2a3d451495ae8492c8629d0aa7476` is pushed. Independent report: `docs/issues/PC-ISSUE-101-ai-incomplete-proposal-missing-time.md`. `REC-P1-051` remains NOT TESTED.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-101.json`, SHA-256 `7ECF656AF47EC32029B1B525ABE4636F06190BE762BE3119163422426FC1A6F4`; ledger SHA-256 `FB88F6EC57099AD2169DEF1828116634A22C2095A945E711E3013D546F00F519`.

## PC-ISSUE-100 — mixed AI CREATE/REPLACE core checkpoint (narrowed core candidate later PASS)

- Existing parser and PowerStore regressions were run directly. One structured response stays as separate CREATE and REPLACE proposals; two PENDING drafts are created, the REPLACE keeps the target eventSeq, and neither formal event is published or mutated.
- Targeted Robolectric API 34 passed 2/2 across two suites. The same code baseline's full Gradle run passed 452/452 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No provider call or UI change.
- Independent checkpoint report: `docs/issues/PC-ISSUE-100-mixed-ai-create-replace-core-checkpoint.md`. PC-ISSUE-118 later signs the narrowed core parser/draft-isolation criterion PASS; API 35/provider, presentation, approval and reopen persistence remain untested.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-100.json`, SHA-256 `CC8184361CCE7C1C4921D3FEFCD5579E0D6939B8CF591820D468F72ABBB1026A`; ledger SHA-256 `040CD2EEFAF281FA018ED465194C8D6ADCAC93CCAAE40B333CAD7BD23D18E5BC`.

## PC-ISSUE-099 — structured proposal HTTP 429 stage diagnostics

- Reproduced that a local HTTP 429 returned a generic “AI request failed” warning without naming the structured-proposal stage. Updated `AiClient.interpret` to preserve the stage in the warning. The focused test also verifies no proposal/raw reply is returned and neither the provider error body nor API key is exposed.
- Pre-fix targeted reproduction failed 1/1 on the missing stage label. Post-fix Robolectric API 34 passed 1/1. Full Gradle passed 452/452 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Used a local HTTP stub only; no external provider or UI change.
- Core fix/test commit `55f3c1212eafaf306ad70e89b308fa1c3b3612c5` is pushed. Independent report: `docs/issues/PC-ISSUE-099-structured-proposal-http-429-stage.md`. PC-ISSUE-117 later signs the narrowed AI-client core HTTP 429 contract PASS; API 35/provider and presentation paths remain untested.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-099.json`, SHA-256 `D073E87A42F7970F8094DB7E1B2E8CF3DFF03A6B4DA9487A76076F3C0CD03D20`; ledger SHA-256 `386E2BD443FC58F9BE1D3F392CF8B3E7C5020EAB17439FE7DBCE038F26CED2B5`.

## PC-ISSUE-098 — repeated approval core idempotency checkpoint (narrowed core candidate later PASS)

- The existing `PowerStoreTest.repeatedCommitReturnsTheOriginalOutcome` was run directly: a second ordinary commit of the same draft returns `duplicate=true`, the same revision/commitId, and leaves one history record. No code defect was found and no source/test file changed.
- Targeted Robolectric API 34 passed 1/1. The current code baseline's full Gradle run passed 451/451 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. API 35 approval action and device behavior remain untested; no UI was changed.
- Independent checkpoint report: `docs/issues/PC-ISSUE-098-ai-approval-idempotency-core-checkpoint.md`. PC-ISSUE-116 later signs the narrowed core commit-idempotency criterion PASS; API 35/provider approval-entry behavior remains untested.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only artifact: `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-098.json`, SHA-256 `FD48462009013801EACEF7CE707CD0D5E4D05D908F2028813BB583211AC7733E`; ledger SHA-256 `09D00A90C6CF4F49E47B5AADA8C213E12F1ABCAA877A9F7547F730183C06A8E2`.

## PC-ISSUE-097 — AI draft persistence checkpoint (status at that time NOT TESTED)

- Added `PowerStoreTest.parsedAiCreateProposalPersistsAsAnUnpublishedDraftAcrossRestart`: a locally parsed CREATE proposal becomes a PENDING draft, creates no formal event/history, and keeps its identity, form, version, status, and queue entry after SQLite reopen. No production defect was found; this is partial core evidence only.
- Targeted Robolectric API 34 passed 1/1. Full Gradle passed 451/451 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. `adb` is unavailable; API 35/free-provider selection and device behavior were not tested. No UI was changed.
- Test commit `0aa6fa50a00925246aff7dd13822ad1ef15ece4f` is pushed. Independent checkpoint report: `docs/issues/PC-ISSUE-097-ai-draft-persistence-process-restart.md`. At that checkpoint `REC-P1-056` remained NOT TESTED for the API 35/provider path; PC-ISSUE-115 later signs the narrowed core persistence criterion PASS.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-097.json` SHA-256 `66163C61D912A6B2984023F017ADD825D4E3DC346051253B955A92FEF8269EC3`; ledger SHA-256 `BC18A6B477FF77AC67EEC3F632424D294FC80410BAE18478D5F40EA8142C88A9`. Original 209 case IDs remain unmapped.

## PC-ISSUE-096 — AI proposal rejection preserves the formal event

- Added `PowerStoreTest.theAiBatchDiscardOnlyRejectsProposalAndPreservesFormalEventAcrossRestart`. It publishes an Activity, creates an AI REPLACE proposal, discards it through `AiBatchRunner`, and verifies the formal snapshot and commit history remain unchanged before and after SQLite reopen; the proposal is DISCARDED and the batch unlocks.
- Targeted Robolectric API 34 passed 1/1. Full Gradle passed 450/450 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. This was a coverage-only change; no production defect was reproduced.
- Test commit `65f6cabd653878f77127e167a583bea31043e5e3` is pushed. Independent report: `docs/issues/PC-ISSUE-096-ai-proposal-rejection-does-not-mutate-event.md`. `REC-P1-055` is PASS for the core discard/persistence contract.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1; 117 reconstructed candidates have independent evidence. Original 209 case IDs remain unmapped.
- `REC-P1-059` remains NOT TESTED: AI proposal, draft, and batch authorization models do not represent dependent-group identity, and the freeze spec does not define group membership. No group semantics or UI were invented. Continue with independent non-UI core acceptance.
## PC-ISSUE-095 — AI independent batch item failure and retry

- Added a SQLite-trigger regression for a three-item batch whose middle commit fails. The pre-fix runner published 1 item instead of the expected 2 and skipped the later independent item. Fixed `AiBatchRunner` to isolate ordinary exceptions per item, preserve cancellation propagation, report the failure and continue; a partial-failure batch now ends as `FAILED` rather than `CANCELLED`.
- The regression confirms first/last items commit, the failed draft transaction rolls back and remains pending, `published=2 / kept=1 / cancelled=false` and the report survive SQLite reopen, the lock releases, stale authorization is rejected, and a new batch retries the retained draft successfully.
- Pre-fix reproduction log SHA-256 `62EF0C91214E7C38C0DABAC65B2A60571C9B17E38264239510B60D8184C950FC`. Final targeted test passed 1/1 (log SHA-256 `B7BA661157DE0E9A4DD96C5F8ACBBEE26DB826EE709CD2E981E5D2AE0B88B17A`; JUnit XML SHA-256 `0D09525904E9BF11BCD4DFB01207801C03AE5869243FACB067CE8130F79291D0`). Full Gradle passed 449/449 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `2F809BCB7A5C03973688D09662B7809BE64DF7A333B17FDAF8C316281DC3572D`.
- Commit `361f074538a27ad75c2a91c7da4bdee6beb46795` is pushed. Independent report: `docs/issues/PC-ISSUE-095-ai-batch-independent-item-failure.md`. `REC-P1-058` is PASS for the core runner/SQLite partial-success contract; no model or network call was made.
- Freeze remains HOLD: 134 PASS / 0 FAIL / 9 BLOCKED / 59 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 23/0/8/50/1; 116 reconstructed candidates have independent evidence. Original 209 case IDs remain unmapped.
- Next: continue a feasible core acceptance item; keep remaining failures and device gaps visible.

## PC-ISSUE-094 — Reminder recovery broadcast matrix

- Added `BootReceiverRecoveryTest.recoveryBroadcastsReconcileDesiredAlarmAndFinishAsyncWork`. Five synthetic non-boot actions (app replaced, timezone changed, clock changed, exact-alarm permission changed, internal reconcile) each clear the simulated AlarmManager registration, dispatch the receiver, await `goAsync()` completion, and verify one registration is rebuilt from the durable `SCHEDULED` intent. The boot action remains covered by PC-ISSUE-093.
- Focused `BootReceiverRecoveryTest` passed 2/2 (log SHA-256 `15C9FC8800907C64CFEBEA601F91C2CAF56570B09920F6F9FFDC14238CB81803`; JUnit XML SHA-256 `A7BBB9E5A22B8BFEB6A21F851E19EDFF279F3CFF3FE0E649560912E2FE91E349`). Full Gradle passed 448/448 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `3BF5BF314F9F4320B07788CB0629653AA90199A83F1429A392F2A622DE5D4116`.
- Test commit `3983bf7dcc1ba5b5b1ec9e84d621fe4aad77d99c` is pushed. Independent report: `docs/issues/PC-ISSUE-094-reminder-recovery-broadcast-matrix.md`. `REC-P0-117` remains NOT TESTED because dispatch was synthetic and this environment has no `adb`; actual Android system delivery and physical/OEM recovery are unverified.
- Freeze remains HOLD: 133 PASS / 0 FAIL / 9 BLOCKED / 60 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 22/0/8/51/1. Original 209 case IDs remain unmapped. This loop is limited to core acceptance.
- Next: continue with a feasible core acceptance item while preserving the actual-device recovery gap.

## PC-ISSUE-093 — BootReceiver asynchronous schedule reconciliation

- Added `BootReceiverRecoveryTest.bootBroadcastReconcilesDesiredAlarmAndFinishesAsyncPendingResult`. Robolectric API 34 dispatches `ACTION_BOOT_COMPLETED` through `goAsync()`, changes the durable Alarm intent from PENDING to SCHEDULED, registers one ShadowAlarmManager task, completes `PendingResult`, and retains the result after SQLite reopen.
- Focused regression passed 1/1 (log SHA-256 `CEBEC27C47343C23D1998248D6C7FC99176310CFE41BDACECD9BA7AA58EC439F`; JUnit XML SHA-256 `C6B73D20FB7495F9E0C3AE24DF5E3F8A41918C93C0D62B2B427D8B2366FD621B`). Full Gradle passed 447/447 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `AF800BA5E17A2822EC9A79CCB9309A980DE4A199769A54F7225560BFE19FF707`.
- Test commit `86e9d97106592edc41d988db317d47687be89976` is pushed. Independent report: `docs/issues/PC-ISSUE-093-boot-receiver-async-reconciliation.md`. `REC-P0-111` passes for the Robolectric receiver path; actual system boot broadcast and physical/OEM delivery remain untested. Test-harness failures from the first two attempts are documented with hashes.
- Latest freeze remains HOLD: 133 PASS / 0 FAIL / 9 BLOCKED / 60 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 22/0/8/51/1; 115 reconstructed candidates have independent evidence. Original 209 case IDs remain unmapped. Scope remains core-only; pale horizontal edge accepted. Protected untracked files remain unchanged and unstaged.
- Next: continue with a feasible non-UI core acceptance item.

## PC-ISSUE-092 — AI batch user cancellation and partial result

- Added and pushed `PowerStoreTest.theAiRunnerStopsAfterUserCancelAndPersistsPartialOutcomeAcrossRestart`. The runner receives a cancellation request after the first item, does not publish the second item, returns `published=1 / kept=0 / cancelled=true`, releases the lock and persists the report across SQLite reopen; stale session writes remain denied.
- Focused Robolectric API 34 regression passed 1/1 (log SHA-256 `FE85E768847D06B41AEC5128FD8CC85C528C997DD1127439EAA282FF05B0163D`; JUnit XML SHA-256 `F776710373A58963149903B83ED69D2667D4D27AB40F6DCFDBA924636013F638`). Full Gradle passed 446/446 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `4A8039764F0D6B25A47B8E0B4C9FC31445E15A64382FAA3EFD6363A61C10933A`.
- Test commit `d79b0f6d11870c5a18c6b7401ebe18e72fe95c05` is pushed. Independent report: `docs/issues/PC-ISSUE-092-ai-batch-user-cancel-partial-result.md`. `REC-P1-060` is PASS for the core runner cancellation and partial-report persistence path; the model service is not called.
- Latest freeze remains HOLD: 132 PASS / 0 FAIL / 9 BLOCKED / 61 NOT TESTED / 7 SPEC UNRESOLVED; 114 reconstructed candidates have independent evidence. The original 209 case IDs remain unmapped. Scope remains core-only; pale horizontal edge accepted. Protected untracked files remain unchanged and unstaged.
- Next: continue with a feasible non-UI core acceptance item.

## PC-ISSUE-091 — AI batch cancellation cleanup and stale-epoch fencing

- Added and pushed `PowerStoreTest.cancelledAiBatchRunnerReleasesLockAndFencesRemainingScopeAcrossRestart`. Cancellation after the first completed item preserves that formal event and the unfinished draft, releases the batch lock, persists `CANCELLED`, and rejects the stale `AiSession` after reopening SQLite. Existing orphan recovery separately simulates a previous owner token and rejects its late write.
- Focused Robolectric API 34 regression passed 1/1 (log SHA-256 `3D6BEDC5462DF72ADCE56A06A479A92FA2EB6521AFD400769A2C6D61554DE5DB`; JUnit XML SHA-256 `4B9C5709A0E8F9689B3E249B5CDCC5E3C5541DE507C29A95BE2BE602B2321D02`). Full Gradle passed 445/445 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `BDEB8CB1A9047D1E30A7FEDD0A2E63AEA30B21744748F298BF90360F5F82BB5D`.
- Test commit `65fb3ca8bda51cf7c6bd91794789e2b152e9603a` is pushed. Independent report: `docs/issues/PC-ISSUE-091-ai-batch-cancellation-recovery.md`. `REC-P1-061` is PASS for the core batch state/authorization contract; live model timeout and OS-level process-kill behavior were not exercised.
- Latest freeze remains HOLD: 131 PASS / 0 FAIL / 9 BLOCKED / 62 NOT TESTED / 7 SPEC UNRESOLVED; 113 reconstructed candidates have independent evidence. The original 209 case IDs remain unmapped. Scope remains core-only; pale horizontal edge accepted. Protected untracked files remain unchanged and unstaged.
- Next: continue with a feasible non-UI core acceptance item.

## PC-ISSUE-090 — ICS TZID and VTIMEZONE serialization

- Mapped existing `IcsExportRecurrenceTest` fixed-offset and DST round-trip assertions to `REC-P1-078`. Focused Robolectric API 34 run passed 6/6; full Gradle at the same code/test baseline passed 444/444 across 56 suites, with zero failures/errors/skips and lint 0 errors / 58 warnings.
- Focused log SHA-256 `67CB928029CBB1759776544081D710802D7A47E24E60F15733D6C7DE3B569676`; JUnit XML SHA-256 `B48271D52E3B178E494F252BFAE05A6796A333FA7D1F327DBF8522F089D9C2A6`. Independent report: `docs/issues/PC-ISSUE-090-ics-timezone-definition.md`. No source changes. `REC-P1-078` is PASS for codec output; third-party consumer behavior remains outside scope.
- Latest freeze remains HOLD: 130 PASS / 0 FAIL / 9 BLOCKED / 63 NOT TESTED / 7 SPEC UNRESOLVED; 112 reconstructed candidates have independent evidence. Historical 209 IDs remain unmapped.
- Next: continue with a core-only candidate; do not start UI work.
## PC-ISSUE-089 — ICS read projection preserves one formal event source

- Added and pushed `PowerStoreTest.icsActivityProjectionDoesNotCreateSecondFormalEventAcrossRestart`. A committed SQLite Activity projects through ICS as one matching event without warnings; formal rows, history, reminder intent, identity count and sequence preview remain unchanged before and after SQLite reopen. Test-only change; no production or UI changes.
- Focused Robolectric API 34 regression passed 1/1 (log SHA-256 `394711335A4B9DE80FEE60B81FF224C85645883FBA16403AC25A496A683550AA`; XML SHA-256 `F6CC86C486E178FF119E8AB730626FDD0C7F940B557A7480DEBE850D68D051F1`). Full Gradle passed 444/444 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Log SHA-256 `EE9DA7BC1A810D3C706DAA44F7CBE0D3EED6C5557572F5690F50C45621BFE44D`.
- Test commit `13e720bce54d0b31a179e68e9fa1a86eec518c61` is pushed. Independent report: `docs/issues/PC-ISSUE-089-ics-read-projection-does-not-mutate-store.md`. At the Issue 089 checkpoint, the combined AI and platform projection scope remained incomplete; PC-ISSUE-113 later signs the narrowed AI/ICS core criterion PASS.
- Evidence handling note: the prior structural-only Issue 088 JSON was reserialized from its captured content after a filename collision; its new SHA-256 is `4D8A8F14B8D50995CC9F66D990AF1ABF47C76ECBB887187381E6D00C09230A70`. Issue 088 test logs and JUnit evidence were untouched. Issue 089 validation is stored at its own filename.
- Latest freeze remains HOLD: 130 PASS / 0 FAIL / 9 BLOCKED / 63 NOT TESTED / 7 SPEC UNRESOLVED; 112 reconstructed candidates now have independent targeted evidence. Historical 209 IDs remain unmapped. User scope remains core-only; no UI work. Protected untracked files remain unchanged and unstaged.
- Next: continue with the next feasible non-UI core acceptance item.

Updated: 2026-10-24

## PC-ISSUE-088 — date-only Deadline completion precision

- Added and pushed `PowerStoreTest.completingDateOnlyDeadlinePreservesDatePrecisionAcrossRestart`. Completion changes only the completion key/revision; `deadlineDate` and DATE precision remain, and deadlineMillis/startMillis/endMillis stay null after SQLite reopen. No UI or production code changed.
- Focused regression passed 1/1 (log SHA-256 `09908CFC9350A75119B589F1AD665E2B14626B6AF39C71ED619B127F89FBF43F`; XML SHA-256 `3EFDF79FEAA9ED6862967256710C40E1522272741CAC74B9349D000F6E02CDB5`). Full Gradle including source boundary passed 443/443 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `84C5052C61812EFEE91155A59BFC42364DC3BEEB01701EA4DD1593F6474998CE`.
- Test commit `d94d98b1de5d3573420c8fd9e29f58c8f73ae9f8` is pushed. Independent report: `docs/issues/PC-ISSUE-088-date-only-deadline-completion.md`. `REC-P0-023` remains NOT TESTED because the required UI rendering behavior was not tested.
- Latest freeze remains HOLD: 129 PASS / 0 FAIL / 9 BLOCKED / 64 NOT TESTED / 7 SPEC UNRESOLVED; 110 reconstructed candidates have independent targeted evidence. Historical 209 IDs remain unmapped.
- User scope: no UI work; faded horizontal edge is accepted, and the blue current-time marker may cover the center axis. Keep Issue 002 PARTIAL/BLOCKED and do not retry around the free-model rate limit or call unauthorized paid models.
- Protected untracked files `DraftDeckFeedbackTest.kt`, `design/`, and `PC-OBS-005` remain unchanged and unstaged.
- Next: continue with the next feasible non-UI core acceptance item.

## PC-ISSUE-087 — completion tracking and reminder state independence

- Audited the reconstructed “Pause object” wording and found no such behavior in the approved contract or core code. Narrowed `REC-P0-017` to contract Invariant 2: tracking, reminder scheduling and event enabled state are independent.
- Added and pushed `PowerStoreTest.changingCompletionTrackingPreservesReminderIntentAcrossRestart`. Both tracking directions preserve enabled/reminder fields and the equivalent pending intent across SQLite reopen. Test commit `5b610b7bc48951e1205cf18cc72fe83782d93fac` is pushed.
- Focused regression passed 1/1 (log SHA-256 `486ED3A39D00EFC5B7ECAE685292F3D1CCD365BEA6AB16A6A2D9665FCC6EFBE8`; XML SHA-256 `395F805708218E12ED551E8FDD0C667E832F28A32D30677B8D1C6CEC6EBF672C`). Full Gradle including source boundary passed 442/442 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `F36A991D37DF9CEEABD17623CFC7175740EFC05F51DDCCA882EB9ACF66FCB4CF`.
- Independent report: `docs/issues/PC-ISSUE-087-tracking-and-reminder-state-independence.md`. `REC-P0-017` remains NOT TESTED because Android scheduler delivery and physical/OEM behavior are not covered. No UI or production code changed.
- At the Issue 087 snapshot, freeze remained HOLD: 129 PASS / 0 FAIL / 9 BLOCKED / 64 NOT TESTED / 7 SPEC UNRESOLVED; 109 reconstructed candidates had independent targeted evidence. Historical 209 IDs remained unmapped.
- User scope: no UI work; faded horizontal edge is accepted, and the blue current-time marker may cover the center axis. Keep Issue 002 PARTIAL/BLOCKED and do not retry around the free-model rate limit or call unauthorized paid models.
- Protected untracked files `DraftDeckFeedbackTest.kt`, `design/`, and `PC-OBS-005` remain unchanged and unstaged.
- Next: continue with the next feasible non-UI core acceptance item.

## PC-ISSUE-086 — whole-series recurring rule persistence

- Added and pushed core regression `PowerStoreTest.wholeRecurringSeriesEditKeepsIdentityAndAnchorAcrossRestart`: `EditSession` saves the changed title/rule while keeping the event identity and original anchor; recurrence projection and stored state remain stable after SQLite reopen.
- Focused regression passed 1/1 (log SHA-256 `394711335A4B9DE80FEE60B81FF224C85645883FBA16403AC25A496A683550AA`; XML SHA-256 `25D33D615FBE7DD1FDD0AF36BA4727C467EE698B0139F69BA25546FCC129F2CE`). Full Gradle including source boundary passed 441/441 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `1C898C47EFC096A0A6919609F543DE3FC3B25F63E0FA19EF8B263DA9DC602173`.
- Test commit `49f334e8cea1005de59ef9b98a16e32f544b937e` is pushed. Independent report: `docs/issues/PC-ISSUE-086-whole-series-core-edit.md`. `REC-P0-080` remains NOT TESTED because explicit whole-series scope selection and device behavior were not exercised; this is partial core persistence evidence only.
- At the Issue 086 snapshot, freeze remained HOLD: 129 PASS / 0 FAIL / 9 BLOCKED / 64 NOT TESTED / 7 SPEC UNRESOLVED; 108 reconstructed candidates had independent targeted evidence. Historical 209 IDs remained unmapped.
- User scope: no UI work; faded horizontal edge is accepted, and the blue current-time marker may cover the center axis. Keep Issue 002 PARTIAL/BLOCKED and do not retry around the free-model rate limit or call unauthorized paid models.
- Protected untracked files `DraftDeckFeedbackTest.kt`, `design/`, and `PC-OBS-005` remain unchanged and unstaged.
- Next: continue with the next feasible non-UI core acceptance item.

## PC-ISSUE-085 — recurring this-and-future split

- Core transaction implementation and regression tests committed and pushed: `cf1be08500f02adc0edba12c9c97bc4c7d53fea1` (`feat(重复): 支持指定实例及后续序列拆分`) and `86043231d14594cce89942f8847c5fe66e7bd178` (`fix(重复): 保留首个有效实例后的剩余次数`).
- Fix covers atomic old-series truncation/new-sequence creation, compatible occurrence-state migration, rollback, and retaining the existing identity when no earlier active non-excepted occurrence exists. If the selected slot becomes the anchor and the count is unchanged, only the remaining count is retained.
- Both controlled pre-fix reproductions failed on the identity/count mismatch; final focused set passed 4/4. Full Gradle, including source boundary, passed 440/440 across 56 suites; lint 0 errors / 58 warnings. Full log SHA-256: `0F4780D20727E1F0418E337F070A9229C4D1D898E5829DCB7A8AC42BDB25D433`.
- Independent report: `docs/issues/PC-ISSUE-085-this-and-future-series-split.md`. `REC-P0-079` remains NOT TESTED because editor scope selection/call path and API 35/physical-device behavior are not covered. `REC-P0-078` default scope and `REC-P0-080` whole-series selection remain open.
- No UI changes. User accepted the faded horizontal edge and confirmed the blue current-time marker may cover the center axis. Do not reopen UI work.
- At the Issue 085 snapshot, freeze remained HOLD: 129 PASS / 0 FAIL / 9 BLOCKED / 64 NOT TESTED / 7 SPEC UNRESOLVED; 107 reconstructed candidates had independent targeted evidence. Historical 209 IDs remained unmapped.
- Next: continue with the next feasible non-UI core acceptance item; keep Issue 002 PARTIAL/BLOCKED and do not bypass free-model rate limits or use unauthorized paid models.

## PC-ISSUE-PHASE1 — HEAD 6ddf9b9 verification and AVD data recovery gap (2026-10-25)

- Isolated worktree validation: focused Phase 1 regression 31/31 across 6 suites (log SHA-256 `88F8FFD48A62A91ADDF6947107CF15DF9842439DE4B60CE0E0EDECDF05B51059`); full offline gate 521/521 JVM tests across 69 suites, zero failures/errors/skips, 88/88 Gradle tasks, lint 0 errors / 53 warnings. Debug / AndroidTest APK SHA-256: `1601F2DB169A8C889791EBFE4149F4383E71980474452C8CFE22BA9A7C92A9EC` / `9FAA01339612CA2DF1871CD047512C282F19339C1E5A7766E4E512E80B0FDFFD`.
- `NewEventEditorLifecycleInstrumentedTest` passed 2/2 on each of two API 35 AVDs. The connected-test task ignored the requested serial filter and ran both `emulator-5554` and `emulator-5556`. After task completion package manager no longer listed either app package; reinstalling known baseline APKs showed both private `databases/` directories absent. No explicit wipe or `pm clear` was issued. The original 139264-byte DBs are unavailable from the current installs.
- `emulator-5556` app/test APKs were restored byte-for-byte from pre-run captures. `emulator-5554` had no pre-run APK capture; it now has the same documented 0.9.5-rc.1 baseline APK bytes, but exact pre-run APK equivalence is unverified. Neither app was launched after reinstall.
- Recovery candidates are stale: the 5554 Download export is dated 2026-10-22; the host read-only SQLite copy is dated 2026-10-23 and 147456 bytes, versus the pre-run DB file size 139264 bytes on 2026-10-24. Neither was restored. Evidence and backups are at `.local/android-test/reports/freeze-20261025/pc-phase1-verification-head-6ddf9b9/`; report: `docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md`.
- Phase 1 code behavior remains verified PASS, but this revalidation's AVD data-preservation requirement is unresolved, so delivery remains HOLD pending a matching backup or owner disposition of the AVD data. No physical device was touched.

## PC-ISSUE-PHASE1 — current mainline revalidation (2026-10-25, HEAD `68cad08`)

- No Phase 1 product source changed. The Phase 1 production files are identical to the API 35 baseline `2692ca2`; current app code is `9f9e82ae583a5d582e5b8b4307db98ec5606f149`, with no `app/` diff through the current documentation HEAD.
- Focused Phase 1 tests passed 32/32 across six suites on current HEAD (0 failures/errors/skips); log SHA-256 `838F4DB4402A271FC06C7C752AF06D63FCC7C2199CC248D0E414E0CBB01E79B1`.
- Current app-code snapshot full Gradle gate passed 541/541 JVM tests across 74 suites, 88/88 tasks, 0 failures/errors/skips, and lint 0 errors / 56 warnings; source boundary and Debug/AndroidTest APK builds passed. Log SHA-256 `3BCA1C55B5350479176B05FDF84D771352AEE4CD396322CF4D08ECD5141ED95E`; APK SHA-256 `517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614` / `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`.
- Explicit-serial API 35 `emulator-5560` AndroidJUnitRunner passed 2/2. App and test APKs matched the local build byte-for-byte. The isolated test created no lasting `pc-phase1-*` database; before/after hashes for existing `powercal-v2.db` and its empty journal were identical. Instrumentation log SHA-256 `FF5AAD38B385BAE0EAEC59B2013C8002F709C472148B1E0FFD9DCF08EE037BD9`; before/after snapshot SHA-256 `43BCD1BBADE1697912CD541AC5231DBA2167C6F46617D6E4D4A20E77EB8EFD43`.
- Structural ledger validation passed at 218 rows / 217 active reconstructed: 204 PASS / 0 FAIL / 10 BLOCKED / 2 NOT TESTED / 1 SPEC UNRESOLVED / 1 RETIRED; historical IDs mapped=0 and `product_behavior_tested=false`. Ledger SHA-256 `91C721FBA0D1C2295535A200E30937744A67C3F8BBF04F34E32E47F078AAC2E0`; validator unit tests passed 2/2.
- Detailed report: `docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md`. Phase 1 is ready to close; physical-device/OEM testing and the one-off fall-fold minute ambiguity remain explicitly unsigned. Overall core-freeze verdict remains HOLD.

## PC-ISSUE-PHASE1 — current request revalidation (2026-10-25, HEAD `7cb847e`)

- No Phase 1 production change. Read-only entrance audit confirms timeline, month, Memo, PowerCal Web action and WebSkin bridge ordinary creates use the in-memory `NewEventEditSession`; remaining direct draft writes belong to meaningful checkpoints or explicit import/AI/formal-edit flows.
- In an isolated worktree whose only `app/` delta from current HEAD is the matching `GlassComposeTest.kt` source, Phase 1 focused regression passed **32/32 across six suites** (0 failures/errors/skips), log SHA-256 `F6E14EA76A3A33587F96576BDFC2A328254CE9A271D599CB37BD2EB22013E4BF`. Full offline gate passed **542/542 JVM tests / 74 suites / 88 tasks**, source boundary, Debug and AndroidTest APK builds, and lint (0 errors / 53 warnings). Full log SHA-256 `476B42F7710FA358CF4F8B92E99864AD7B897C7FF8DFFA2F777BE774D1AD08B7`; APK SHA-256 `517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614` / `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`; lint XML SHA-256 `B95E8834C3E3EFA3386E0391464F11FB5953ED428EA7C87FA70CE020661628C0`.
- No connected/AVD test was run: four AVDs were already online and prior private-data recovery risk remains; none was touched. Existing API 35 Compose result is PREVIOUS PASS only; no physical-device claim. Main-worktree untracked assets were excluded and left untouched.
- DST fall-fold resolution for individually entered one-off timestamps remains SPEC UNRESOLVED: the freeze contract scopes “first/earlier offset” only to recurrence and explicitly leaves one-off parsing undefined. Preserve HOLD for strict time-semantic signoff and overall freeze; do not infer the recurrence rule. Details: `docs/issues/PC-ISSUE-PHASE1-EDITOR-LIFECYCLE.md`.

## Separation — AI attachment picker entry points (2026-10-27)

- Added closed-schema Host actions pickAiFile / pickAiImage; Clear exposes accessible controls and routes them through the existing Android-owned file and image pickers. URI/content stay Host-only; opening a picker has no Store side effects. Studio implements explicit non-reading Mocks.
- Focused projection/integration tests: 14/14 PASS. Full offline Gradle: 585/585 JVM tests, 82 suites, 88/88 tasks, zero failures/errors/skips; lint 0 errors / 52 warnings. Studio Python 42/42, Chrome Mock E2E, node checks, and canonical Clear bundle verification passed.
- Full-log SHA-256 8EE258EA0CF691787F72293C67F539DBC2C7A2D7D6EBAE8DE7735D0C61916E48; Debug / AndroidTest APK SHA-256 B3115D80E25CBA24B1A6177D1EED5506DB613D467123E1BCE99268B31296780D / 219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6.
- Independent report: docs/issues/PC-ISSUE-SEPARATION-AI-ATTACHMENTS.md. Implementation commit 030c920584f19f04c2d696da89652010d7ecac5c is pushed. ADB daemon did not start; Android WebView/device, physical device, selected-file/OCR flow, live provider, and full Native/Skin replay remain NOT TESTED. SEP-09/10 remain NOT TESTED; S3 remains open.

## Separation — selected-file redaction and pending AI proposal (2026-10-27)

- Added a Robolectric API 35 integration regression for the production Host file-result path: selected synthetic text → Host extraction/privacy review → user redaction → explicit submit → local stub returns one typed CREATE proposal. The outbound request excludes the redacted secret; draft/formal/identity/commit counters remain unchanged and the proposal stays unresolved.
- Focused test passed 1/1. Final full offline Gradle gate passed **586/586 JVM tests / 83 suites / 0 failures, errors or skips / 88 tasks**; lint 0 errors / 52 warnings. JDK 20.0.1, Gradle 8.14.3. Focused log SHA-256 `9A455708E3F7873BB79E0494CB78B4D5777033BE66BAE79EBD5389AD8EB00B4D`; focused JUnit XML SHA-256 `34B0B1C463DBE882AA5255047CD645F3653867B7CFF2DC81A4EB2A87519E98E5`; Debug / AndroidTest APK SHA-256 `B3115D80E25CBA24B1A6177D1EED5506DB613D467123E1BCE99268B31296780D` / `219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6`.
- Test-only commit `52b2d0b97b89d6aa990a8d767abbe8ae8b34a268` is pushed. Independent report: `docs/issues/PC-ISSUE-SEPARATION-AI-ATTACHMENT-REVIEW.md`. SEP-09/10 remain NOT TESTED for full parity; image/OCR, ICS, chooser UI, WebView/device, live provider and proposal decisions remain unverified. S3 remains open.

## Separation — AI image upload consent and chat-history replacement (2026-10-27)

- Added Robolectric coverage for explicit image-upload consent, cancel-without-send, raw-image exclusion from persisted chat, clearing history, and replacing longer snapshots with shorter/empty content. A test-only Windows Robolectric AtomicFile shadow models Android rename replacement; a bounded read wait removes an asynchronous test-side file-lock race. No production source changed.
- Focused attachment/history regression passed three consecutive runs, 4/4 each. Full offline gate passed **588/588 JVM tests / 83 suites / 0 failures, errors or skips / 88 tasks**; lint 0 errors / 52 warnings. Final log SHA-256 `203CBEC9A015ACADD9C63853B161677DFB9E15B3A1D9CA3055A211D31DECAAED`; Debug / AndroidTest APK SHA-256 `B3115D80E25CBA24B1A6177D1EED5506DB613D467123E1BCE99268B31296780D` / `219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6`.
- Test-only commit `327a9aa9604d83bffa0154f795faeabb3913e22a` is pushed. Independent report: `docs/issues/PC-ISSUE-SEPARATION-AI-IMAGE-UPLOAD-HISTORY.md`. SEP-09/10 remain NOT TESTED as full parity rows. Android chooser UI, OCR, ICS, live provider, Android WebView/device and physical/OEM checks remain NOT TESTED. S3 remains open.

## Separation — AI chat provider failure and explicit resend (2026-10-27)

- Added a Robolectric integration regression for HTTP 503 followed by the user explicitly re-sending the identical prompt through sendChat. It verifies no hidden retry, sanitized error/context, persisted success, and zero calendar-store writes; no production source changed.
- Focused selection passed 5/5; full offline Gradle gate passed **589/589 JVM tests / 83 suites / 0 failures, errors or skips / 88 tasks**, lint 0 errors / 52 warnings. Focused/full log SHA-256: `49F4F12B45E9A8704F707EC2D79FFE24317B13EAC5F1F0AEE76F5460FE010944` / `DB2FAF8D11A47EAE304BAA06484A42DE8B419283C0E0AE5CA9BF8A328E9A6CEA`.
- Studio Python suite passed 42/42; Chrome source-roundtrip and non-writing AI Mocks passed; Clear bundle verification passed. These are not provider-error retry or device-WebView evidence.
- No live provider, Android WebView, emulator or physical-device run. Clear has no dedicated retry button; this covers user resend through the existing composer action. SEP-09/10 remain NOT TESTED as full parity; S3 stays open. Test commit: `0be7053cb59f1bb799b67d3736599dc0fd6507b3`.

## Separation — AI proposal partial resolution and stale review rejection (2026-10-27)

- Added an API 35 Robolectric integration regression through the production `PowerCalApp` and Web Host Bridge: the user stages one of two independent CREATE proposals, the other remains with a fresh review ID, replaying the old ID cannot reject it, and rejecting the active remaining proposal leaves the staged draft unchanged. Store result: 1 pending draft, 0 formal events, 1 identity, 0 commits. No production source defect was reproduced; this closes a multi-proposal coverage gap.
- Focused proposal/review/projection suites: 20/20 PASS. Full offline Android gate: **590/590 JVM tests, 83 suites, 0 failures/errors/skips, 88/88 tasks**; lint 0 errors / 52 warnings. Full log SHA-256 `0652792360766D1F032695FE399D862FD36E02EF490315096F52C310B9A24A66`; Debug / AndroidTest APK SHA-256 `B3115D80E25CBA24B1A6177D1EED5506DB613D467123E1BCE99268B31296780D` / `219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6`.
- Skin Studio Python 42/42 PASS; Chrome source round-trip and AI decision Mocks PASS; Clear bundle verification 8 files / 376661 bytes PASS. These checks do not prove Android WebView Host parity or real-provider behavior.
- Test commit `d03778d18156331b5d4152c80bc0cee27591409f` is pushed. Independent report: `docs/issues/PC-ISSUE-SEPARATION-AI-PARTIAL-REVIEW.md`; SEP-10 and S3 remain NOT TESTED/in progress. AI REPLACE/DELETE combinations, dependency groups, conflict recovery, process death, Android WebView and physical-device behavior remain open.

## Separation — AI incomplete proposal multi-turn supplement (2026-10-27)

- Added a production `PowerCalApp` + Web Bridge loopback regression: an empty structured response for a missing-end ACTIVITY produces a non-publishable review; SUPPLEMENT changes no Store state; the next user turn is combined with the original evidence for a complete candidate; an old review ID cannot publish it; current publish still waits for trusted Compose confirmation, and cancel preserves the review without writes.
- Focused Host/Bridge/proposal/task suites: 22/22 PASS. Full offline Android gate: **591/591 JVM tests, 83 suites, 0 failures/errors/skips, 88/88 tasks**; lint 0 errors / 52 warnings. Full-log SHA-256 `4954DACDF90973BD6B849777A38B60C7DEBCCEFB64674CE04EDBF5288B5DDDE6`; Debug / AndroidTest APK SHA-256 `B3115D80E25CBA24B1A6177D1EED5506DB613D467123E1BCE99268B31296780D` / `219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6`.
- Studio Python 42/42 PASS; Chrome source round-trip and non-writing AI decision Mocks PASS; Clear bundle verification 8 files / 376661 bytes PASS. No live provider, Android WebView, emulator or physical-device run.
- Test commit `ba30d9a5538f47195a98f8fb05365f8cf45c70d1` is pushed. Independent report: `docs/issues/PC-ISSUE-SEPARATION-AI-MULTITURN-SUPPLEMENT.md`; SEP-10 and S3 remain NOT TESTED/in progress. Multiple operation types, dependency groups, version-conflict recovery and process-death recovery remain open.

## Separation — AndroidTest provider isolation (2026-10-27)

- Fixed the shared SAF test provider's global authority/permission collision by deriving both from the AndroidTest applicationId; URI generation now uses the instrumentation package context. No production code or D05 probe behavior changed.
- API 35 `emulator-5564`: unique QA and test APKs coexist with the other Agent's test package; `SafBackupReaderPermissionTest` passed 1/1. The random test namespace left no DB/cache/preferences. Evidence: `docs/issues/PC-ISSUE-SEPARATION-TEST-PROVIDER-ISOLATION.md`; log SHA-256 `C4F6CD48EAAC398D75183C5C5BD188E1001AAE764C3A04FAA191D5482485EA61`.
- Full offline Android gate passed 593/593 JVM / 83 suites / 0 failures/errors/skips / 88 tasks; lint 0 errors / 52 warnings. Commit `fd6a14369c149739d3ef8e31dedce10e521adb45` is pushed.

## Separation — AI REPLACE/DELETE Host confirmation (2026-10-27)

- Added actual PowerCalApp/Bridge local-stub tests for REPLACE+DELETE and stale-target conflict recovery, plus an Android Clear WebView instrumentation test. No production business code changed.
- Related Host/AI JVM suites 24/24; full offline Android gate 593/593 JVM, 83 suites, 0 failures/errors/skips, 88 tasks; lint 0 errors / 52 warnings. API 35 emulator-5564 WebView 3/3 and SAF provider 1/1.
- Skin Studio Python 42/42, Chrome source round-trip/AI Mocks, node check and Clear bundle verify passed. Evidence: `docs/issues/PC-ISSUE-SEPARATION-AI-REPLACE-DELETE.md`. `SEP-10` remains NOT TESTED; S3 remains open.
- Test commit `93d067b2733021ff07c8139cc19f03606dbb153e` is pushed.

## Separation — Clear AI decision WebView follow-up (2026-10-27)

- Added the actual Clear WebView STAGE / REJECT / SUPPLEMENT instrumentation path and ran it on API 35 `emulator-5564`: **1/1 PASS**. One selected proposal staged, the independent proposal rejected, and an incomplete Activity was supplemented then rejected; no unapproved formal write occurred.
- A separate PUBLISH rerun ended with ADB exit 255 and no JUnit result; `emulator-5564` then returned `device not found`. Its prior PUBLISH evidence remains PREVIOUS PASS. No emulator wipe/restart or other package cleanup was attempted.
- Full offline Android gate: **593/593 JVM / 83 suites / 0 failures/errors/skips / 88 tasks**, lint 0/52. Studio Python 42/42, Chrome decision Mocks, Clear bundle verification and JS syntax check passed. Evidence: `docs/issues/PC-ISSUE-SEPARATION-AI-WEBVIEW-DECISIONS.md`.
- Test commit `5bc70f8aee4f1d1f157fd006118b29ef80858d64` is pushed. The fresh QA applicationId used was `dev.powerfulcalendar.d05qaisolateddecisions20261027`; existing app/QA packages were not replaced or cleared.
- SEP-10 / SEP-16 remain NOT TESTED and S3 stays open; next device step is to re-check 5564 when available, verify its isolated package state, and rerun PUBLISH before expanding to crash/restart recovery.

## Separation — Studio AI project operations CLI (2026-10-27)

- Added `inspect-v3`, `read-v3`, and transactional `edit-v3` over the existing v3 validator/builder. The CLI exposes manifest/project/file hashes, text and binary reads, guarded file CRUD, project JSON Patch, explicit manifest edits, and validated export to a new path. Capability changes require an explicit flag; Host still makes the authorization decision.
- Focused regression: **7/7 PASS**. Full Skin Studio suite: **49/49 PASS**. Clear bundle verify: **8 files / 376661 bytes PASS**, SHA-256 `0e17aabfe216e8ecb0775dafb8ea66857e25687b09e1167b7c68e1d9ffd81623`. Full/focused/verify logs and hashes are in `docs/issues/PC-ISSUE-SEPARATION-STUDIO-AI-PROJECT-OPS.md` and `.local/android-test/reports/separation-20261027/studio-ai-operations/`.
- Implementation commit `871f54d` (`feat(studio): 提供可调用的 v3 皮肤项目编辑器`) is pushed to `origin/feat/skin-first-separation`. Independent report: `docs/issues/PC-ISSUE-SEPARATION-STUDIO-AI-PROJECT-OPS.md`.
- This closes only the machine-callable package-editing slice. Browser visual/component editing, Android WebView/device acceptance, runtime parity, complete S5/SEP-15, and S0–S8 remain open. No Android or physical-device result is claimed.
## Separation — Browser Studio project and asset editing (2026-10-27)

- Browser Studio now edits raw `project.json`, creates/deletes supported UTF-8 files under `web/` and `assets/`, and adds/replaces/deletes arbitrary assets. Invalid project JSON blocks preview/export while retaining the editor draft; valid edits preserve unknown fields. Text-reference scanning blocks observed references before deletion. Unsupported text extensions are rejected so created files remain editable.
- Focused source editing tests 4/4 PASS; complete Skin Studio suite 50/50 PASS; real Chrome Headless source/project/resource edit-preview-export-import E2E PASS; Clear bundle verification PASS (8 files / 376661 bytes; SHA-256 `0e17aabfe216e8ecb0775dafb8ea66857e25687b09e1167b7c68e1d9ffd81623`). Logs/hashes: `docs/issues/PC-ISSUE-SEPARATION-STUDIO-BROWSER-AUTHORING.md` and `.local/android-test/reports/separation-20261027/studio-editor-authoring/`.
- Code commit `151960f` is pushed to `origin/feat/skin-first-separation`. Independent report and Studio docs are being committed separately. SEP-15/S5 remains open: no formal component schema/tree editor, Android WebView/device parity, or complete AI authoring workflow is claimed. Next, continue closing the highest-risk Host/Studio parity gaps from the SEP matrix; preserve the native recovery UI.

## Separation — S0 reconstructed Native/Host/Clear/Studio inventory expansion (2026-10-28)

- On clean isolated worktree baseline `cc31026f81d4df6704dd89704a4ee93f030a5c00` (Core frozen source `b1f7629f0c52812e1887443872f43aa6c49908c4`), expanded the 16 coarse SEP groups into **62 RECONSTRUCTED audit IDs** across timeline, month, Memo, editor/recurrence, draft deck, AI, 22 Native settings/system actions, Studio authoring and package/runtime. IDs are newly reconstructed; no historical 209 IDs are claimed.
- The matrix records missing or divergent source paths, including Clear timeline zoom/wing scroll/drag parity, temporal geometry/date precision, missing Memo-row open, deck conflict/restore and AI batch commands, Native-only system/settings routes, Studio Mock/runtime boundary and installed-package round trips. All child and parent full-parity states remain `NOT TESTED`.
- Source review found the v3 Manifest parser mismatch recorded in `S0-PKG-04`: Python rejects unknown Manifest keys; Android decode does not. This is source evidence only; no paired package fixture was executed and no format policy changed.
- Validation in this worktree: Skin Studio Python **51/51 PASS**; canonical Clear build verification **8 files / 377,973 bytes PASS**; `node --check` for `app.js`, `edit.js`, and `v3-studio.js` passed. Logs and SHA-256 are in `docs/issues/PC-ISSUE-SEPARATION-S0-FUNCTION-INVENTORY.md` and `.local/android-test/reports/separation-s0-inventory/`. These checks do not prove Native/Skin parity.
- Independent report: `docs/issues/PC-ISSUE-SEPARATION-S0-FUNCTION-INVENTORY.md`. S0 remains open for final dormant-callback/failure-path audit and reusable paired replay. No Android WebView/emulator, physical-device, live-provider or complete parity result is claimed.
