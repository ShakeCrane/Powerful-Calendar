# PowerCal 0.9.5-rc.1 原生视觉基准差异审计

## 裁决与基线

Owner 于 2026-11-05 明确将 **2026-10-27 冻结的 PowerCal 0.9.5-rc.1 原生界面**指定为唯一正式默认 .pcskin 的视觉、交互和页面结构基准。冻结应用源码为 b1f7629f0c52812e1887443872f43aa6c49908c4，Owner 签核见 docs/CORE_FREEZE_OWNER_SIGNOFF_2026-10-27.md。

| 被比较对象 | 基线 |
|---|---|
| 原生参考 APK | .local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-b1f7629-debug-core-HOLD.apk |
| 原生 APK SHA-256 | DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76 |
| 冻结源码 | b1f7629f0c52812e1887443872f43aa6c49908c4 |
| 首轮视觉盘点时的 Web 侧代码 | e41246d7ebe5b329adbdc657bd3e53735695efa1 |
| 首轮视觉盘点时的 Web 侧 APK SHA-256 | 49DA3DA24E928B8099B0792E1D5C1BC29E8660048D990CEB2C564DC05D79E052 |
| 隔离设备 | 新建 AVD PowerCal_Native095_Parity_20261105，Android API 35，Pixel 6 设备配置 |
| 实际显示参数 | 1080×2400，420 dpi；模拟器运行属性 sdk_gphone64_x86_64 |
| 视觉证据目录 | .local/android-test/reports/skin-baseline-parity-20261105/ |

参考 APK 与当前 APK 在上述新建 AVD 上依次安装。只在该 AVD 的 dev.powerfulcalendar 包内重置了测试应用数据；没有触碰既有 Wave2 AVD、真机或其他用户数据。测试中拒绝了首次“导入系统日历”提示，没有导入任何日历内容。打开并无修改地退出活动编辑器后，待处理卡组显示 0 张。

截图包括 native-timeline-empty.png、native-month.png、native-memo.png、native-ai.png、native-tools-top.png、native-tools-lower.png、native-draft-deck.png、native-activity-editor.png，以及 current-web-timeline.png、current-web-month.png、current-web-memo.png、current-web-ai.png、current-web-settings.png、current-web-draft-deck.png、current-web-activity-editor.png。这是两个版本的真实 Android 界面，不是 Studio Mock。截图跨越模拟器本地午夜；日期和“现在”线的屏幕位置不能当作同一时刻像素差异。本轮先完成页面/布局/点击路径审计，后续逐项验收必须使用固定时钟、相同数据集和相同设备几何。

## Native 全部日常表现面清点

冻结版实际日常界面不止时间轴。冻结 SHA b1f7629f0c52812e1887443872f43aa6c49908c4 中，路由和原生页面源码实际位于 app/src/main/java/dev/powerfulcalendar/ui/v2/，例如 PowerCalApp.kt、NativePowerNavigation.kt、NativeTimelineScreen.kt、MonthScreen.kt、MemoCenterScreen.kt、AiChatScreen.kt 与 GreedyTitleFolding.kt。分离后对应实现被抽取到 native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/；审计冻结行为时以 b1 文件为权威，审计现行恢复实现时才使用抽取后的路径。

| 页面 / 表面 | Native 组件、状态与交互 | 当前 Web v3 对应 | 差异与迁移边界 |
|---|---|---|---|
| 应用壳 / 主导航 | 冻结 Native NativePowerNavigation 固定四项：时间轴、日历、AI 助手、设置；Memo 从时间轴顶部入口打开。时间轴标题行提供品牌、日期、今日、Memo/待处理、日历、草稿与新建入口。 | 当前 Web v3 也显示四项主导航，Memo 保留为独立 Host 路由并由顶部快捷入口到达；最新真实截图 current-web-native095-timeline-final.png、current-web-native095-ai-final.png 显示了 Native 风格标题行、底栏与选中态。 | 共享壳已经接近冻结结构；剩余差异以同一时刻/设备的图标墨迹、边界和触控矩形对照。早期五项 Clear 底栏截图是历史快照，不能代表当前 Web。 |
| 时间轴 | NativeTimelineScreen：中心垂直时间尺、ALARM 左翼与 ACTIVITY/DEADLINE 右翼、真实时间区间、蓝色现在标记、框外标题、按实际文字矩形贪心折叠、双翼滚动/相机/缩放、双击时间尺复位 24 小时、今日定位本地中午、事件点击、受阻提示与四类新建。 | 当前 renderTimeline、title-folding.js、.timelineWing/.timelineLanes 通过现有正式投影/Host；最新 current-web-native095-timeline-final.png 已采用 Native 页面背景、标题、控制行与四项底栏。固定 ACTIVITY 夹具已测折叠触控和 Camera。 | 同数据 ACTIVITY 固定夹具的事件行/填色/主要文字位置接近，仍有约 1px 横向边界与字形栅格差。完整 ALARM、DEADLINE、重复实例、长标题/多泳道、大字体与空/忙/错误状态未签核；Timeline 整页仍 PARTIAL。 |
| 月历 | 冻结 Native Month：保留 Camera、今天、前后月、年份/月选择、横向切月、七列日格、日期/事件/重复实例选择与跳转时间轴。 | Web v3 的月份/日期投影与 agenda 继续使用既有 Host/Core；本轮依据同一隔离 API 35 AVD 的原始 APK 与 WebView 截图，对齐所选日期标题与 agenda 事件卡片材质、排版和间距。 | 已完成窄切片：卡面 #F6F8FC、20px 圆角、无阴影、16px 内边距、事件类型色、标题/摘要/序号字体行高、卡片间距及日期标题样式均有 computed-style 和 Android 断言；Native/Web 卡面 bbox 纵向差 2px、右边界差 3px，ROI MAE 4.583、P95 通道差 2。完整 Month 仍 PARTIAL：底部动作按钮、年月控件/导航完整触控、深色主题与全部状态组合尚未签核；重复实例既有功能证据不替代本轮视觉全页验收。 |
| Memo / 待办 | NativeMemoCenterScreen：返回与新增无日期 Memo、Open/Done、完成后可撤销、行内完成/撤销、标题打开编辑、分页；重复待办范围由 Host 提供。 | 当前 memoPage/renderMemo 采用同一返回、新增、完成筛选、分页和四项底栏；真实 WebView 空态截图 current-web-native095-memo-final.png 与 Native 结构接近。 | 空态文字基线/垂直位置、列表分隔线和行密度仍需固定数据对照；完整行点击、完成撤销和分页虽有旧 F-04 证据，未在本轮以相同 Native/Web fixture 重放。Memo 整页 PARTIAL。 |
| AI 聊天 | NativeAiChatScreen：标题/副标题、配置、待处理菜单、消息列表、消息长按菜单、附件“+”、输入/发送、忙碌状态和独立提案审核卡。 | 当前 aiPage/renderChat 已有 Native 风格标题、待处理溢出菜单、附件入口和 Host 消息动作；消息角色标签与长按菜单已补齐，Web 状态仅暴露 proposalAvailable 布尔值，Host 按 messageId/revision 取规范消息。 | 该消息动作子集 Android 触控测试通过；本次未取得原始 APK 消息菜单的实屏截图，故 Native/Web 菜单像素对照 NOT TESTED。AI 历史、附件授权、提案审核、错误/重试仍未逐状态签核；当前额外发送说明文字与 Native 空态文案布局有差异。AI 页面 PARTIAL。 |
| 设置 / 工具 | 冻结 NativeToolsPage 含资料、AI 受保护配置、通知/精确闹钟、提醒重建/诊断、系统日历导入、只读迁移、备份恢复、ICS 导出。 | 当前普通 settings Host action 路由至 Page.SETTINGS 并由 Web settingsPage/renderSettings 表现；仅显式 nativeSettings 进入 Page.TOOLS 安全恢复。AI 密钥输入为 Host Compose 受保护表面，不投影给 Web。 | 真实 Web 设置截图保持四项底栏和原生式卡片顺序；卡片白底/阴影与 Native 轮廓面、资料编辑入口和间距仍不同。原始 Native 皮肤目录是已退役产品入口，不得移植；系统 SAF、权限和可信密钥面继续归 Host。 |
| 待处理草稿卡组 | NativeDraftDeckScreen：卡片堆叠、卡内/卡外滑动、逐卡/快速浏览、搜索、恢复/删除、空态、AI 批次进度、冲突、审批/撤销/关闭。 | 当前 #pendingDeck 具备卡组、发布、搜索、恢复、删除和退出动作，调用正式 Host/Core。 | Native 是深色 scrim 上的白色浮层/叠卡；Web 当前仍为高面积的全高白色编辑/卡组面板。视觉层级、拖动/滚动手势、错误/冲突状态需移植并实触。 |
| 四类事件编辑与详情 | NativeDirectEventEditor、NativeDeckEventForm、NativeEventInspection：类型、标题、时段/日期、地点/备注、提醒/启用、重复规则/作用域、详情与前后状态；只在业务变化后暂存。 | Web edit.js 现有四类编辑、草稿、日期/时间选择与动作调用；旧 F-05/F-06 业务 API 路径保留。 | Native 是覆盖当前页面的暗 scrim 编辑面并有紧凑底部动作；Web 用高面积极白面板及纵向表单。旧 Web 截图里“1 张·已暂存”是编辑会话计数，关闭后 Store 为 0；须保持实际零写入并让可见状态不误导。四类表单与取消/审核视觉仍 PARTIAL。 |
| 审核/安全子表面 | NativeFormalWriteReview、NativeAiProposalReview、NativeAiBatchAuthorizationReview、NativeAiInputReview、NativeAiStuckBatchReleaseReview、NativeLegacyDataReview、NativeCalendarImportPrompt、NativeTextDraftDialog 与 Android 日期/时间 Picker。 | Web 有提案/正式写入 Review 卡和状态错误层；Picker、SAF、权限、密钥与可信确认继续由 Host/Android 承担。 | 业务 API 路径有旧任务覆盖，但 Compose 与 Web 每种确认、拒绝、冲突、取消布局尚未逐一配对。Native/Host 安全边界保留，Skin 只显示最小必要状态。 |

### 有限差异审计结论（2026-11-05）

- **窄范围等价证据：**四项主导航的页面顺序与角色一致；时间轴固定 ACTIVITY fixture 的双翼、时间几何、框外标题和点击 Camera 已有 WebView 证据（残留约 1px 绘制差）；本轮补齐的 AI 消息角色与长按菜单动作通过真实 Android WebView 触控和 Store 不变断言。以上均只是组件/行为子集，不代表页面整体 PASS。
- **部分接近、需继续移植：**Native 样式 Web 的 Timeline、Month、Memo、AI、Settings 实屏已有 1080×2400/420 dpi 截图。共享标题/底栏已接近；Month 选中态 14dp 圆角和 52dp 日格高度已用真实 WebView 检查，剩余选中日期计数色、格面和 agenda 卡片表面/间距仍不同。AI 空态多一行“向 AI 发送消息”说明，聊天历史和附件/提案状态未配对；Settings 卡片表面、资料编辑入口和间距仍不同。
- **明确待移植且尚无当前版本成对验收：**Draft Deck 的暗 scrim/叠卡结构、四类事件编辑器的 Native 弹层和底部操作、审核/冲突/拒绝子表面。旧编辑器截图中“1 张·已暂存”是 UI 会话计数，关闭后 Store 为 0；必须保留零写入行为并校正可见说明。
- **明确不迁移：**冻结 APK 中的皮肤候选/目录/导入导出 UI 已被 Owner 退役；当前 Native Settings 截图里的该区块不属于复刻目标。保留仅用于安全恢复的 Native 路由与 Host 可信能力。
- **对照限制：**现有 Native 与 Web 截图分别来自 2026-11-04/05，系统时间、所选日和数据状态并非全都相同。因此它们可用于页面结构差异盘点，不能作为逐像素签核。之后每个实现切片要使用同一固定时钟、同一隔离 Store fixture、同一 API 35 AVD 重拍/触控；尚未重放的状态保持 PARTIAL 或 NOT TESTED。

**审计结论：当前没有任何完整页面可据此宣称已达到 0.9.5-rc.1 全量视觉/交互等价。**

## 当前已等价、已验证与尚需移植

“功能等价”不能由同一套外观推断；以下将历史任务验证与本轮实屏观察分开。

| 行为 / 资产 | 当前结论 | 证据与下一步 |
|---|---|---|
| 唯一正式包与运行身份 | 保留 powercal-clear-default.pcskin / powercal-clear-default；当前工厂包仍是唯一正常 Web v3 包。 | 本轮只重建当前 Debug APK，未改包/manifest/assets。后续继续由正式 Studio 构建器重建，验证 APK 中仍恰好一包。 |
| Timeline Core 投影与双翼几何 | 已在分离工程有正式实现；已有 F-02 Android 交互证据。 | WebSkinTimelineProjection.kt、WebSkinHost.kt、app.js::renderTimeline、TimelineCreateContextWebViewInstrumentedTest、TimelineDragWebViewInstrumentedTest。本轮只实际比较空数据首屏；完成视觉实现后用固定事件集重新对比。 |
| 框外标题与贪心折叠 | Native 冻结契约明确；当前 Web 有 title-folding.js 基于实际候选矩形的算法及单元测试。 | GreedyTitleFolding.kt（冻结树）、NativeTimelineScreen.kt、title-folding.js、title-folding.unit.test.cjs。移植时不得改成对事件几何折叠，也不能牺牲点击区域/无障碍目标。 |
| 月历重复实例和点击 | F-03 先前设备验证为 PREVIOUS PASS。 | 见 CURRENT.md 的 F-03 证据；本轮没有创建重复事件，不重标状态。视觉迁移后重放同一 fixture 与 occurrenceKey。 |
| Memo、四类编辑、重复作用域、草稿/审批、AI、系统设置 | 有既有 F-04～F-08 任务范围和测试；本轮未重跑这些业务测试。 | 继续复用 EventFeatureApiV1、RecurrenceFeatureApiV1、MemoFeatureApiV1、DraftReviewFeatureApiV1、AI/Reminder/Settings/ImportExport API。不得复制业务校验或 Store。 |
| 手势/受保护 Host 操作 | 本轮实际确认 Native 的页面路由、Memo、草稿卡组和编辑器可触达；Web 页面与部分浮层可触达。 | 本轮未对 API/Store 逐项检查，也未执行正式写入、提醒投递或设备权限授予；这些必须标 NOT TESTED，不得由截图推断通过。 |

## 明确不迁移的冻结版旧产品 UI

冻结 APK 在“工具与设置”的皮肤卡中仍显示 Native/Clear 候选、导入皮肤包和导出候选皮肤；这些入口已由 Owner 退役，不属于原生视觉复刻目标。不迁移旧候选选择、商城、第三方包导入/导出/删除 UI；不删除用户文件或历史源码。Native 模块继续仅作为独立安全恢复及 Host 可信平台操作，不成为第二套日常界面。

## 移植顺序与验收边界

1. 先移植共享 Native 主题 tokens、系统栏/页面留白、四项底栏、页面标题和浮层/scrim。
2. 移植 Timeline 栏目、颜色/刻度与冻结手势外观；锁定真实时间几何、蓝色现在标记、双翼位置、框外标题和实测矩形折叠。
3. 逐页移植 Month、Memo、AI、Settings 卡片与空态；再移植 Draft Deck、事件表单、提案与冲突子表面。
4. 每页绑定仍使用同一现有 Web v3 Host API 与 .pcskin manifest。Native/Compose 安全确认、Android Picker/SAF 不伪装为 Skin 代码。
5. 每次视觉/交互改动在冻结 APK 与新 WebView 使用固定 API 35 AVD、固定本地时间、相同数据库 fixture 截图和触控重放。记录页面尺寸、关键边界、事件几何、操作结果和 Store 快照。未经实际测试的一项保持 NOT TESTED。
6. 不修改 Core/Host 业务语义，不创建第二包、不删除 Native 恢复，不以 Clear 旧截图或概念设计作为完成标准。该审计不改变 SSA/SEP 状态，也不构成产品发布签核。

本轮审计完成，视觉移植仍进行中。产品的公开 Release/全平台 Cutover 状态继续由现有验收矩阵和 Owner 发布门禁决定。

## 2026-11-05 实施与复验状态（进行中，不构成完整视觉签核）

本次施工基于工作分支 HEAD `e41246d7ebe5b329adbdc657bd3e53735695efa1`，对应新版 APK SHA-256 `1304586DC5E3189B4DB215D731618812D58DEC751C0C99FEEF497889F9F60E2A`。正式构建器生成的唯一工厂包为 `powercal-clear-default`，SHA-256 `ba4d9831fb774e4bc02e440b00a58b7608c7d390b9b97083e9e9596cfe930d81`；`build_default_clear.py --verify --apk ...` 确认源码包和 APK 资源字节相同，APK 内仍只有该正式包。默认皮肤 ID、冻结 Core、生产启动选择与 Native 恢复身份均未改动。

新版 Web 截图在 `.local/android-test/reports/native095-parity-20261105/`：`web-final-timeline.png`、`web-final-month.png`、`web-final-memo.png`、`web-final-ai.png`、`web-final-ai-menu.png`、`web-final-menu-dismissed-month.png`、`web-final-settings.png`。原生参考截图继续使用上面的 `skin-baseline-parity-20261105/native-*.png`。两轮截图不是同一模拟器时刻：原生月历选中 11 月 4 日，Web 月历选中 11 月 5 日；时间轴“现在”线也处于不同纵向位置。因此只能据此判断结构与页面层级，不能声称逐像素或同一日期几何通过。

### 已落地并有本轮证据的部分

- 时间轴当前实屏已显示蓝色“现在”线；中心垂直尺、24 小时视图、日期头、工具条和四项底栏均可见。空数据截图的左右泳道没有事件卡，因此框外标题、贪心标题折叠、双翼事件宽度与点击目标仍未由本轮确定性事件集验证。顶部/底部 `00:00` 边界刻度与 Native 当前窗口存在差别，必须在固定时钟、相同 Camera 与事件矩形下继续测量。
- 月历实屏结构已收敛为 Native 的返回/“今天”头、月份左右切换、七列网格、圆角矩形日期选中、选定日期摘要和创建/跳回时间轴入口。当前截图垂直边界大体对齐，但因跨午夜不能用不同选中日期进行逐像素签核；年份/月份选择和横向切月的触控对照仍待验证。
- Memo 实屏的标题、返回/新增、Open/Done 操作、分隔线、空态与四项底栏已与原生页面结构接近。截图中列表为空；完成/撤销、分页和标题打开详情仍须以同一数据快照做触控回放。
- AI 页已收起常驻批次按钮并移入溢出菜单，保留配置、待处理计数、消息区、附件入口和底部输入。新增的“清理 AI 临时缓存”只调用闭合的 Host action `clearAiTemporaryCache`；只清理应用私有、匹配前缀的临时缓存，不返回路径或密钥，不改聊天及日历 Store。手动点击页签和单项缓存动作已由 Android 触控 instrumentation 验证。
- 实际触摸切换到 Month 时，AI `<details>` 溢出菜单此前不会像 Native 菜单那样自动收起。本次为菜单外 `pointerdown` 和 Escape 增加关闭逻辑； instrumentation 真实触摸验证了菜单收起、页签跳转、返回 AI 后菜单可再次打开，且 Host 清理动作成功。缓存文件删除、聊天记录保留、Core Store 的草稿/待处理/身份/提交计数一致均通过。
- Settings 实屏采用个人资料、单一 AI、恢复、日历/数据等卡片区。已退役的皮肤目录不再显示；个人资料、密钥和可信确认仍交由 Host/Android 保护。真机/OEM 权限及系统 Picker 本轮没有重跑。

### 本轮测试状态

| 检查 | 结果 |
|---|---|
| `:app:verifySkinSourceBoundary` / `:app:verifyNativeSkinBoundary` | PASS |
| 完整 `:app:testDebugUnitTest` | PASS，973/973，0 failures/errors/skips |
| `:app:assembleDebug` / `:app:lintDebug` / `:app:compileDebugAndroidTestKotlin` | PASS |
| 工厂包源/APK 验证 | PASS，10 个文件；两侧 SHA-256 均为 `ba4d9831fb774e4bc02e440b00a58b7608c7d390b9b97083e9e9596cfe930d81` |
| 目标 Android WebView 触控测试 | PASS，隔离 AVD `PowerCal_Native095_Parity_20261105` / `emulator-5580` / API 35，`clearAiTemporaryCacheUsesHostActionAndPreservesChatAndCalendarStore` 1/1 |
| Android 测试类宽跑 | 10/11；`clearChatMessageActionsAndHistorySurviveColdProcessRestart` 需要调用方传入 `f07AiColdCycle`，未带该专用参数时在第 574 行失败。该方法不是本轮缓存操作；单独目标测试通过。不得把 10/11 写为全类通过。 |
| Studio 定向 Python | `test_default_clear.py` 5/5 PASS；`test_host_binding_catalog.py` 7/7 PASS |
| Studio 全套 Python | 88 项中 86 项通过、2 项失败：`test_architecture.PresentationBoundaryTests.test_full_route_registry_has_one_restore` 和 `test_native_recovery_contract.NativeRecoveryContractTest.test_native_settings_and_hidden_activity_rescue_are_both_available`。两者断言源代码字面形式与当前 `ActiveVisualSkin`/`MainActivity` 不一致；这些被测生产文件未由本轮修改。整体套件状态不是 PASS。 |
| Node 语法 | 默认皮肤 JS、Studio JS、浏览器 E2E 文件 `node --check` 均 PASS |
| Studio 浏览器 E2E | `e2e_v3_source.cjs` PASS：通过真实 Chromium/CDP 在 Studio 源编辑器中导入、编辑、预览、打包/重导入并运行确定性 AI/系统动作 Mock；该结果不代表 Android Runtime。`e2e_v3_browser.cjs` 仍 BLOCKED：入口依赖的 Node 模块 `puppeteer` 缺失，未安装依赖。 |
| `git diff --check` | PASS（工作副本有 Git LF→CRLF 提示，不是空白错误） |

### 仍未完成的关键差异与下一步

1. 建立固定时间、固定时区、固定 Camera 与同一事件 Store fixture；逐个核对 Native/Web 事件矩形、左右翼、框外标题矩形、贪心折叠、重叠事件的点按与无障碍目标。不得以空态截图代替。
2. 在同一 fixture 下复验月历重复 occurrence 的日期格、实例键及点开目标；校准月份/年份选择和切月交互。
3. 对比并移植 Draft Deck、四类编辑/详情、正式写入审核、AI 提案审核、文件/图片授权、批次冲突等浮层和 Compose 特效；当前仍存在浮层面积、卡片堆叠及编辑器布局差异。
4. 补足设置的权限请求/拒绝、提醒状态与重建、ICS/备份取消零写入、Android Picker 等实机操作重放；当前截图只覆盖 Settings 顶部布局。
5. 补齐 Studio Browser E2E 环境，复核源工程 → 构建 → APK WebView 的 CSS/资源加载。真机/OEM、TalkBack 与外部服务商状态维持原值。

结论：本次只完成 Native 0.9.5 方向的首轮 Web 页面迁移和一项真实 Host 交互收敛。页面截图显示部分头部、卡片和空态已经接近冻结原生结构，但尚无固定 fixture 逐项尺寸/交互比对，也未覆盖所有重要表面，不能签核“用户看到并操作与 0.9.5-rc.1 等价”。继续实施并保持 `PRODUCT_CUTOVER: HOLD`。

补充：`e2e_v3_source.cjs` 首次复测暴露其拼装 Clear 预览包时没有带上 `index.html` 新引用的 `clear-design.css`、`native-095.css` 和字体资源；夹具已改为包含实际页面依赖。随后浏览器流程按当前已移到 `#chatEmptyHint` 的空态节点验证清空聊天，最终该 E2E 全流程 PASS。该夹具和断言修正不改变产品逻辑。

## 2026-11-05 时间轴固定事件集复验与双击复位对齐

本节更新前文“时间轴还没有确定性事件集”的状态。当前在隔离 API 35 AVD `PowerCal_Native095_Parity_20261105`（`emulator-5580`、1080×2400、420 dpi、density 2.625）使用同一个 UUID 隔离 Store，依次运行当前 Native Compose 时间轴和默认 Web v3 时间轴。事件日期/时刻固定，但系统时钟未冻结；两条路由分别从运行时当前时间初始化 Camera，截图相隔数秒。该 Native 图像来自当前工程的 Native 路由；它可作为移植前后对照，但**不是**冻结原始 APK 在此事件集上的截图。原始 APK 本轮仍只有空数据页截图。因此原始 APK 与新版 WebView 的同数据、同固定 Camera 对照仍待完成。

确定性 Store 数据为设备本地 2026-11-05 的四个一分钟 ACTIVITY：`Fold_A` 12:00–12:01、`Fold_B` 12:01–12:02、`Fold_C` 14:00–14:01、`Fold_D` 14:01–14:02。Core 正式创建后，Native 与 Web 使用同一 Store 和系统时区；Camera 使用每次路由启动时的系统时钟。测试只在关闭已折叠标题对应编辑页后比较原事件快照及提交数量。

| 对照项 | 本轮实测结果 |
|---|---|
| 标题贪心折叠 | Native 与 Web 均显示 `Fold_A`、`Fold_C`；`Fold_B` 折叠到 A，`Fold_D` 折叠到 C。决策使用实测文字矩形；B/D 的事件按钮和完整无障碍标题仍保留。 |
| 真实时间几何 | Web 四个 ACTIVITY 的 interval 顶/底与 Host 投影时间差误差不超过 1.25 CSS px；相邻一分钟区间仍相邻，缩放/复位后仍有事件按钮、刻度和当前时刻线。 |
| 点按命中 | 对折叠的 `Fold_B` 可见文字区域执行 Android touchscreen 点按，打开标题为 `Fold_B` 的编辑器；正式事件快照、revision 与 commitCount 不变。 |
| 复位交互 | 24→16 小时缩放通过真实 Android 触控；对 `#axisResetHit` 发送两次真实触控后 Host 回到 24 小时。Native 与 Clear 的今日、本地中午 Camera、缩放及中轴双击复位对照用例 1/1 PASS。 |
| 本轮设备用例 | `actualClearTimelineFoldsOnlyCollidingMeasuredTitlesAndKeepsEventTapAndGeometry`、`actualClearZoomControlsUpdateHostAndWebViewTimelineScale`、`nativeAndClearTodayZoomAndResetProduceTheSameCamera` 共 3/3 PASS。早期复跑暴露的标签文案、隐藏按钮选择器和测试助手坐标层级问题均已修正；最终结果来自随后通过的编译产物。 |
| 当前工厂包 | 仍是唯一 `powercal-clear-default`，10 个文件、406599 bytes；正式构建器确认源码与 APK 资源 SHA-256 均为 `0e0f51897cdc7aa784925eebaccdac395e45cd10d7b33f22d348dd6eca26f598`。 |

同一夹具 Native 与 Web 的真实 Android 截图：

- Native Compose 路由：[native-fixed-event-geometry.png](../../.local/android-test/reports/native095-parity-20261105/native-fixed-event-geometry.png)，SHA-256 `06E906380D06203D26FE3B1B523B078F7840B0921F8387F4319DC8A5E597353D`。
- Web v3 Runtime：[web-fixed-event-geometry.png](../../.local/android-test/reports/native095-parity-20261105/web-fixed-event-geometry.png)，SHA-256 `D499775CD186D1F38FA70AD37E5A2FD3AD84B5847B19E391CFBE1E466EB55B0C`。
- 设备日志：`.local/android-test/reports/native095-parity-20261105/timeline-greedy-webview-api35-paired-screenshots.log`、`timeline-zoom-doubletap-api35.log`、`timeline-return-doubletap-api35.log`。

当前标题 bounds 尚不能直接当作同坐标系下的像素等价：Native Compose 语义 bounds 相对 Compose 根节点，Web 记录为 WebView CSS viewport 坐标；还需记录两侧屏幕原点并统一映射。空页菜单、标题/轴行起始位置与底部安全区仍能看到几何差异。此次测试主要验证右侧 ACTIVITY，不签核 ALARM 左翼、DEADLINE、重复实例、长标题剪裁、横向 lane 或大字体下的全部标题边界。

本轮为使验收真实复现 Native 冻结行为，删除了皮肤中原先隐藏的单击 `resetTimelineZoom` 兼容按钮，保留 Web Host action 但仅通过时间尺双击/键盘语义触发；其不再占用工具栏布局或形成额外无障碍入口。未修改 Core 业务语义、默认皮肤 ID 或 Native 恢复。

整体结论仍为 `IN_PROGRESS`。下一步：将 Native 与 Web 标题 bounds、事件区间、轴及可视区域统一到物理屏幕坐标，基于原始 APK 的对应布局校准 CSS；再用原始 APK 与 Web 运行包重放同一 fixture。完成后继续逐页处理 Month、Memo、AI、Settings、编辑器、草稿及审核面，不以当前 Native 路由对照替代冻结 APK 的最终实屏签核。

## 2026-11-05 冻结 APK 与 WebView 时间轴实屏复核

本节更新前文“原始 APK 与 Web 尚未同基准对照”以及“标题 bounds 尚未换算到屏幕坐标”的时间轴状态。参考为冻结原始 APK（SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`）；Web 侧为本节施工提交 `10eae7e5` 的源码树所构建的 Debug APK。两侧使用同一设备本地四条 ACTIVITY 夹具及固定 UTC 时刻 `2026-11-05 03:16:08` 截图。屏幕为 API 35、1080×2400、420 dpi；原始 APK 与 Web 主 Activity 截图来自隔离 AVD `PowerCal_Native095_Original_20261105` / `emulator-5582`。另用同一测试事件在原生 Compose 与 WebView 路由运行仪器测试，以获取可映射到物理屏幕的布局边界。该测试隔离了应用偏好与文件目录，复用并核对预置 QA Calendar Store；执行前后正式事件快照、revision 和 commitCount 一致。

本轮 Web 改动只涉及默认皮肤表现：同步原生标题字宽/位置、蓝绿色字色、刻度标签可视范围与间距；对不足 2 CSS px 的活动段，以共享物理像素边界绘制底色和 2dp 色条，避免相邻分钟段产生额外像素行。`.pcskin` 由正式 `build_default_clear.py` 重建，没有手改 ZIP；ID 和资源数量保持不变。

| 对照项 | 结果 |
|---|---|
| 原始 APK / Web 主 Activity | 使用冻结 APK 与实际 `MainActivity` Web v3 截图；不是 Studio Preview 或模拟 DOM。固定时钟、分辨率与四条事件夹具一致。完整截图的平均通道差为 3.29/255；时间轴 ROI 为 1.68/255、像素完全相同占比 97.71%。这些是截图对照指标，不代表其他页面通过。 |
| 标题与触控 | `Fold_A`、`Fold_C` 是可见贪心标题；`Fold_B`、`Fold_D` 折叠但按钮及完整无障碍标题保留。仪器将 Native 与 Web 标题框映射至物理屏幕坐标，四边误差不超过 2.5px。真实触控 `Fold_B` 打开其正确事件编辑器；关闭后正式 Store 快照与 commitCount 不变。 |
| 时间几何与刻度 | 四段 ACTIVITY 的真实起止位置误差不超过 0.25 CSS px；相邻分钟区间共享边界。轴标签必须完整位于视口，且纵向间隔不少于 4 CSS px。Today 触控通过正式 Host action 将 Camera 定位本地中午；双击复位仍回到 24 小时视图。 |
| 物理像素对照 | Native 与 Web 活动条的纵向物理行及混合填充色 `(170,206,198)` 一致。Web 的横向边界为 x=594..813，Native 为 x=593..813，存在 1px 差。标题墨迹核心框也有约 1px 的右移/宽度差；因此不是逐像素完全等价。 |
| 本轮测试 | 目标 API 35 Android WebView 测试 `actualClearTimelineFoldsOnlyCollidingMeasuredTitlesAndKeepsEventTapAndGeometry`：1/1 PASS。完整 Gradle 门禁：两项源码边界、973/973 JVM、Debug APK、Lint、AndroidTest Kotlin 编译及测试 APK 组装均 PASS。Studio 默认包 Python 测试 5/5、应用 JS 语法检查及 `e2e_v3_source.cjs` PASS。Gradle 首行另有 SDK XML v4 与旧工具版本提示；不是构建/测试失败。 |
| 正式包 | `powercal-clear-default`，10 个资源，407164 bytes。正式构建器确认源码与 APK 内包 SHA-256 均为 `505535824f4780a4a6a50edd2e22130cd36131382baaae6bed18e86565a56177`；APK SHA-256 为 `AB97D9AD1B6E57EFB43AC519D7A649F4D55C1004054DBA023F286297923B4F62`。APK 内仍只有一份 `.pcskin`。 |

实屏文件及校验值：

- 原始冻结 APK：[original-apk-native-fixture-today.png](../../.local/android-test/reports/native095-parity-20261105/original-apk-native-fixture-today.png)，SHA-256 `43B471F7C410BAB4474CE050CF24BA0845994C3A97143D8852972C0D95ACB6E2`。
- 当前 Web v3 主页面：[webv3-mainactivity-native095-final.png](../../.local/android-test/reports/native095-parity-20261105/webv3-mainactivity-native095-final.png)，SHA-256 `25DED40F5211E4683F1FBA826A4E3098B06080FB8C2F77E8260FC60A6C66FBDA`。
- 同夹具 Compose/Web 布局截图：[native095-pixel-raster-native.png](../../.local/android-test/reports/native095-parity-20261105/native095-pixel-raster-native.png)，SHA-256 `FED2805D0C1457383C3B6DACAD9683F93E8F8954E19CEB3259A904F4765ECDB0`；[native095-pixel-raster-web.png](../../.local/android-test/reports/native095-parity-20261105/native095-pixel-raster-web.png)，SHA-256 `FD18CD103835768EAA2D3B124AA6566007CA29765987046C59D7C15F5FC081EE`。
- 仪器日志：`.local/android-test/reports/native095-parity-20261105/timeline-native-pixel-raster-final.log`；边界、JVM、APK、Lint 与 AndroidTest 门禁日志：`.local/android-test/reports/native095-parity-20261105/final-gate-native095-timeline.log`。

状态：**时间轴 ACTIVITY 子集实现与交互有真实 WebView 证据，但该切片仍标记 PARTIAL；整体 Native 0.9.5 视觉/交互迁移保持 IN_PROGRESS。** 仍需验证 ALARM 左翼、DEADLINE、重复事件、横向泳道、多重重叠、长标题、缩放和无障碍目标，以及原生时间轴的其他冻结状态。本节当时指定的下一页 Month 已完成首轮实屏对照，结果见下方新增记录。全局仍保留 `powercal-clear-default` 唯一正常 Skin、`powercal-default` Native 安全恢复；未修改 Core/Host 业务 API、数据或冻结语义，Product Cutover 仍为 HOLD。

## 2026-11-05 月历首轮冻结 APK 对照与 Web 交互验证

本节记录 Month 页首轮迁移，不视为全页面验收。冻结参考包为原始 0.9.5-rc.1 APK，已从 `emulator-5580` 安装包路径读取 SHA-256，确认与授权原件一致：`DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。新版为当前施工 Worktree 的实际 Debug APK。两台 AVD 均为 Android API 35、1080×2400、420 dpi；原始 APK 在 `PowerCal_Native095_Parity_20261105` / `emulator-5580`，新版 APK 在 `PowerCal_Native095_Original_20261105` / `emulator-5582`。两者在 2026-11-05 同一 UTC/GMT 日期、相同月份、空事件库下真实打开 Month 并截图；截图没有被 Studio Preview 或 DOM mock 替代。

| 对照项 | 本轮结果 |
|---|---|
| 空态实屏布局 | 月历页头、年份/月标题、前后月箭头、七列日期、选中日期、agenda 标题、空态文案和“为这一天新建活动”按钮已接近冻结原生布局。截图量化 ROI 的绝对通道差：日历面板 MAE `7.3013/255`、P95 `13`、完全相同像素比例 `5.17%`；agenda/actions MAE `5.5143/255`、P95 `9`、完全相同像素比例 `92.80%`；内容 ROI MAE `6.4996/255`、P95 `13`、完全相同像素比例 `46.32%`。这些数值保留了实际字体抗锯齿、颜色与布局差异；没有预设或宣称像素通过阈值。 |
| 真实截图 | 原始 APK：[month-final-emulator-5580.png](../../.local/android-test/reports/native095-parity-20261105/month-final-emulator-5580.png)，SHA-256 `1B83C8B34DC4ED0EEE93B00498D6A5C993032CB89A7F3F0269701951AB81BBF2`；新版 Web：[month-final-emulator-5582.png](../../.local/android-test/reports/native095-parity-20261105/month-final-emulator-5582.png)，SHA-256 `74DA1FC1BEC4E1F7D81FE02B6D395FF2EA42D6B26071A0B9BC10098FACFA8AC6`；50/50 overlay：[month-native-web-overlay.png](../../.local/android-test/reports/native095-parity-20261105/month-native-web-overlay.png)。指标及 crop/diff 文件见同目录 `month-screenshot-metrics.json`、`native-month-content-crop.png`、`web-month-content-crop.png`、`month-native-web-diff-x3.png`。 |
| 日期操作 | API 35 Android 真实触控测试对照当前 Native Compose 路由和默认 Skin：年份输入、月份选择、日期选择、Today 和月份网格横向切换通过；新增“在时间轴查看这一天”触控后，两侧均进入 Timeline，并将 Camera 精确设为所选本地日期 12:00。Month 选择过程不改变 Timeline Camera；所有操作未改变 Store 事件、草稿、提交数、revision 或序列。测试：`monthYearMonthDateAndTodayTransitionsMatchWithoutMovingTimelineOrWritingStore`、`nativeAndClearMonthGridSwipesNavigateOneMonthWithoutSelectingOrWriting`，每项 1/1 PASS。 |
| 重复实例与编辑目标 | 同一隔离 Store 的 Native Compose / Web v3 真实触控选择同一每日重复 ACTIVITY occurrence `2026-11-07T09:00`，打开相同事件身份及仅本次编辑作用域；正式记录、revision 与 commitCount 保持不变。测试：`sameSelectedRecurringOccurrenceOpensEquivalentNativeAndClearMonthEditor`，1/1 PASS。首次复跑发现测试只等待标题，未等待异步月份投影到达；Host JSON 和 DOM 调查显示目标 occurrence 完整且一致。测试增加 Host 行和目标 DOM 行屏障后通过，没有据此修改产品逻辑。 |
| 跨进程与日期投影 | 当前 Web v3 以真实 Android WebView、Daily ACTIVITY 2026-10-31 起始跨月 fixture 检查 Core occurrence → Host projection → 月历日期 DOM，force-stop 后由新进程重新装载并再次核对 occurrence keys；prepare、restore 各 1/1 PASS，Store 数据未被额外写入。跨阶段通过直接 Android instrumentation 运行；若分两次调用 Gradle connected task，其安装流程会移除测试标记，因此那种调用方式被判为测试工具限制，不计入产品失败。 |
| 单元/构建 | 本轮完整 Gradle 门禁：974/974 JVM PASS；`verifySkinSourceBoundary`、`verifyNativeSkinBoundary`、`assembleDebug`、`lintDebug`、`compileDebugAndroidTestKotlin` PASS。Studio 默认包定向 Python 测试 5/5 PASS，月份选择 Chrome/CDP E2E PASS，变更 JS/CJS 的 `node --check` PASS。Studio 全套 88 项中 86 PASS、2 FAIL，分别为 `test_architecture.PresentationBoundaryTests.test_full_route_registry_has_one_restore` 与 `test_native_recovery_contract.NativeRecoveryContractTest.test_native_settings_and_hidden_activity_rescue_are_both_available` 的历史源码字面量断言；未修改这些无关测试，也未把它们计为通过。 |
| 工厂资源 | 正式构建器确认 `powercal-clear-default`、10 个文件、408013 bytes；源码 `.pcskin` 与 APK 内资源 SHA-256 均为 `FB1E4EFFB36823014099F05697041A836AF1077C2312187CFC5C96F34AE5F031`。APK SHA-256 为 `BB3862BBD1E54D5441007BC872D9B31FC3D9BF79C292F53E408BD8D37AD31FD1`；APK 中只有 `assets/powercal-clear-default.pcskin` 一份。 |

**剩余差异与边界：**这次原始 APK 与新版 APK 的配对截图只覆盖空日程状态；有重复事件的行和编辑器通过当前 Native Compose/Web 同版本隔离 fixture 做了触控/Store 对照，但没有从原始 0.9.5 APK 重建同一事件集并完成对应实屏图像配对。因此不能宣称重复实例 UI 的原始 APK 像素等价。年/月选择与日期触控的正式 Native/Web 比较也来自当前 Compose 参考路由；原始 APK 的年月选择器先前另有实触记录。当前 Android 设置底栏实际进入 Native `Page.TOOLS`，仍是普通用户可达的 Native 页面路径，未满足“Native 仅恢复”的最终目标；应在后续 Settings 迁移中修正，不在本 Month 切片里改路由。全局视觉与交互仍为 `IN_PROGRESS`，产品 Cutover 保持 `HOLD`。

## 2026-11-05 月历同设备几何复核与冷启动证据

本节补充并在**屏幕几何比较**上取代上节的跨 AVD 截图指标；上节的截图、操作和测试历史仍保留。参考 APK 文件名与 SHA-256 已从文件重新计算：`PowerCal-0.9.5-rc.1-b1f7629-debug-core-HOLD.apk`，`DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。此前首轮 Month 文字记录中有两处 SHA 字符误录，已在本次复核中更正；现在审计文件中的引用与原始文件计算值一致。本轮在同一隔离 API 35 AVD `PowerCal_Native095_Parity_20261105` / `emulator-5580`、1080×2400、420 dpi 上，先截取原 APK，再以 `adb install -r` 更新新版；未清除 App 数据或重置 AVD。两侧保留同一 `MonthFixture` 与选中日期。

| 对照项 | 实测结果 |
|---|---|
| 真实截图与摘要 | 原生更新前截图 [month-native-same-device-before-update.png](../../.local/android-test/reports/native095-parity-20261105/month-native-same-device-before-update.png)，SHA-256 `9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A`；新版 Web 截图 [month-web-header-nav-adjusted.png](../../.local/android-test/worktrees/skin-single-default-closure/.local/android-test/reports/native095-parity-20261105/month-web-header-nav-adjusted.png)，SHA-256 `F28C723882AA4419A9A88FB52EE945A7573C1B720D41FBD0ACE03F5DDB284CB1`。同设备分区指标原始记录为 `month-same-device-final-metrics.txt`。 |
| 系统栏与标题几何 | AVD 状态栏窗口为 y=0..63，导航栏为 y=2337..2400。Compose 与 Web 的内容视口均由 Host 系统栏 inset 处理；UIAutomator 实测 WebView bounds `[0,63][1080,2337]`，视口内 CSS safe-area top/bottom 为 0，故移除了重复 CSS 顶部 inset。原生/Web 标题墨迹 bounds 分别为 `(168,144)-(279,200)` 与 `(168,141)-(279,196)`；宽度相同，Web 标题约高 3 px、短 4 px。 |
| 图像差异 | 全屏 MAE `6.889/255`、P95 `13`、相同像素 `44.26%`；标题区 MAE `3.549`、P95 `7`、相同 `81.91%`；日历面板/网格 MAE `7.723`、P95 `13`、相同 `2.07%`；Agenda MAE `10.722`、P95 `43`、相同 `47.24%`；月份操作区 MAE `2.998`、P95 `1`、相同 `94.17%`；底栏 MAE `7.552`、P95 `49`、相同 `91.50%`。该结果保留字体栅格、网格填充和页面内容差异，没有设定像素通过阈值。 |
| Android WebView 触控 | ADB 真实触控依次操作月份前进/返回、年份选择器开关、月份选择器、日期选择、Today、MonthFixture 行及系统 Back。月份/日期切换可观察且正确；Fixture 打开对应编辑器，无修改返回后仍在 Month。运行记录 `month-real-device-touch-sequence.jsonl`，完成截图 [month-web-after-touch-sequence.png](../../.local/android-test/worktrees/skin-single-default-closure/.local/android-test/reports/native095-parity-20261105/month-web-after-touch-sequence.png)，SHA-256 `3A521FE5F95341FAC7B25C6D616805F5C8E0DA9DA7027F5101CE73DC7E3FF718`。操作未造成 Store 写入。 |
| 跨进程重复实例 | 在隔离 UUID 数据库上先执行 `prepareCrossMonthDailyOccurrenceForColdRestart`，强停 App 与测试进程，再执行 `verifyCrossMonthDailyOccurrenceAfterColdRestart`。两项真实 API 35 instrumentation 各 `1/1 PASS`；Core occurrence keys、owner、revision 与 Web 月格/agenda DOM 对应，真实触控打开目标 occurrence，且 Store 计数与 revision 不变。日志：`month-recurrence-prepare.log`、`month-recurrence-restore.log`。 |
| 构建与 Studio 检查 | 本轮完整串行 Gradle 门禁 `BUILD SUCCESSFUL`，JVM `974/974`，两个源码边界、Debug APK、Lint、AndroidTest Kotlin 编译通过。Studio 完整 Python 套件为 `86/88`；两项失败是 `PresentationBoundaryTests.test_full_route_registry_has_one_restore`（源码字面量期望）和 `NativeRecoveryContractTest.test_native_settings_and_hidden_activity_rescue_are_both_available`（历史路由/恢复字面量期望），未计通过。相关定向 `test_single_default_distribution.py` 为 `2/2`、`test_v3_preview.py` 为 `11/11`；变更的 JS 语法检查与正式默认包校验通过。Puppeteer 浏览器套件因本地缺少 Puppeteer 依赖而 `NOT TESTED`；该限制不影响真实 Android WebView 触控证据。构建日志：`month-visual-parity-final-full-gate.log`，Studio 日志：`studio-python-month-final.log`。 |
| 工厂产物 | 本轮 Debug APK SHA-256 `3D333AC5A96BDE2BF29B5FA7D9D6AEA4347A2D5084CC50F6DFAAD5DF6FEB0D4A`；源码工厂包 SHA-256 `E43A6EADC577069476F63F2E8F82A92F710D1A7D74E53CD67B48737FD7E16498`，408006 bytes。APK 内恰有一个 `assets/powercal-clear-default.pcskin`，逐字节匹配源码包；ID 仍为 `powercal-clear-default`。 |

**结论仍为 PARTIAL。**同设备校正消除了此前跨设备 status-bar 原点差造成的几何误判；它没有消除日历网格颜色/文字栅格、事件 agenda 密度等可见差异。重复实例已有 Web DOM、触控目标和冷启动验证，但尚未在冻结原始 APK 上建立相同重复数据的像素配对。其余 Memo、AI、Settings、编辑器、草稿/审核和系统权限页面仍需逐页以原始冻结界面对照。无生产默认切换；唯一正式工厂 Skin 包身份不变，Native 安全恢复与 Core/Host 行为未在本切片改动。

## 2026-11-05 Memo 同设备实屏对照（PARTIAL）

本切片以 Owner 冻结原生 0.9.5-rc.1 的 Memo 页面为参照，不以旧 Web Clear 的外观作为目标。冻结参考 APK SHA-256 为 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`，源码基线为 `b1f7629f0c52812e1887443872f43aa6c49908c4`。两侧均在新建隔离 AVD `PowerCal_Native095_Memo_20261105` / `emulator-5554` 上运行；Android API 35、1080×2400、420 dpi。先运行原始 APK 截取 Native，再用 `adb install -r` 安装当前 Worktree APK；没有清除应用数据。原有 AVD `emulator-5580` 上的旧数据库拒绝原始 APK 降级启动，Core 的降级保护正常生效；未清除该 AVD 数据，并改用新建隔离 AVD 完成配对。

| 项目 | 实际结果 |
|---|---|
| Native Open | [memo-native-empty.png](../../.local/android-test/reports/native095-parity-20261105/memo-native-empty.png)，SHA-256 `8CEF8635BA996CF7062F12AA2A8AB31EA81A1ABB058610C80590F9FCD42BADB`；显示“Memo · 未完成”、0 项、“查看已完成”、撤销提示及空态。 |
| Web Open | [memo-web-adjusted-open.png](../../.local/android-test/reports/native095-parity-20261105/memo-web-adjusted-open.png)，SHA-256 `F7D3F9BE578EF5F085CE52523C7823FB8E357E5C8DD19598C175D3C669038A89`。实屏空态 ink bounds 为 (351,1253)–(733,1294)，Native 为 (351,1254)–(732,1294)；空态 ROI MAE 为 0.935/255，精确像素占比 98.19%。 |
| Native Done | [memo-native-done-empty.png](../../.local/android-test/reports/native095-parity-20261105/memo-native-done-empty.png)，SHA-256 `B30E084613DB851A75A0B696BC9F72E583547ADEC630D0447CF21CDC95EA7843`。 |
| Web Done | [memo-web-adjusted-done.png](../../.local/android-test/reports/native095-parity-20261105/memo-web-adjusted-done.png)，SHA-256 `F60CF8682A2114A99967FE805344BCB4C954D7FC995B8C91E5AE31C3005F1C6A`；空态 ROI MAE 为 0.935/255、精确像素占比 98.19%。 |
| 页面框架差异 | Open 全内容 MAE 2.648/255、精确像素 97.29%；Done 全内容 MAE 2.668/255、精确像素 96.52%。标题/筛选区仍有字体与图标栅格差异；没有据整体截图指标宣称整页通过。 |
| 实际触控 | WebView 中打开 Memo、点击“查看已完成”，标题切换为“Memo · 已完成”，再点新增入口；新增入口打开“待处理草稿”编辑面，显示 1 张已暂存记录。随后只关闭该面板，没有发布、丢弃或编辑。由于触控前没有记录草稿数量快照，不能判断该草稿是已有还是本次创建；因此本切片不声称 Core Store 零写入。列表为空，行内完成/撤销、分页、标题打开详情仍 NOT TESTED。 |
| Runtime 与包 | Debug APK SHA-256 `5AED08E4161F64E939A4C0F79C1B5A259B97982AF3AD1D7A2C1D3AF04A97F2EA`；正式工具重建并验证的默认包 SHA-256 `D8469959DBA9DBF0DDE3B4A200B8981BB939CEE3884D6664E7D386D4F61C42F7`，源码包/APK 资源字节一致。APK 仅含 `assets/powercal-clear-default.pcskin` 一份，manifest ID 仍为 `powercal-clear-default`。 |

本次只调整 Web 默认皮肤表现：Memo 返回箭头 SVG、Open/Done 空态沿用冻结 Native 文案、Native 样式表中的页面水平留白及空态字号/偏移。未修改 Core、Host、Native 恢复、Skin ID 或默认选择。最终串行 Gradle 门禁日志为 `.local/android-test/reports/native095-parity-20261105/memo-adjusted-final-gradle-gate.log`：两个源码边界、974/974 JVM（0 failures/errors/skips）、Debug assemble、lint 和 AndroidTest Kotlin compile 均通过；`build_default_clear.py --verify --apk ...` 通过。Studio 分发定向测试 2/2 通过，`node --check` 用于本页 JS 的语法检查通过。

**本页状态为 PARTIAL。**冻结空态与筛选状态已在同设备实屏比对，空态边界已收敛到约 1px；Memo 行样式和完成状态业务触控、分页/详情、编辑浮层与草稿恢复尚未完成原生逐项配对。下一项按原生页面清单进入 AI 页面；产品 Cutover 仍为 HOLD。

## 2026-11-05 WebView 系统栏视口与 AI 首页配对（PARTIAL）

本切片继续以冻结原生 0.9.5-rc.1 为基准。参考 APK SHA-256 为 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`，冻结源码 SHA 为 `b1f7629f0c52812e1887443872f43aa6c49908c4`。AI 的 Native 截图与最终 Web 截图来自隔离 AVD `PowerCal_Native095_AI_20261105` / `emulator-5556`，API 35、1080×2400、420 dpi；当前系统 WebView 为 `com.google.android.webview 124.0.6367.219`。升级安装使用 `adb install -r`，未清除应用数据。

| 对照项 | 实际结果 |
|---|---|
| Host 内容视口 | 旧 `statusBarsPadding()` 在该 API 35 edge-to-edge AVD 上将 WebView 顶边放在 y=128；系统默认状态栏可见内容边界为 y=63。Host 改为优先读 `status_bar_height_default`，UIAutomator 最终测得 WebView bounds `[0,63][1080,2337]`，底边避开导航栏。该修正统一了 Timeline 与 AI 页面原点；Native/Compose 与 Web 的页面顶边现已对齐。 |
| AI 标题 | 原生墨迹 bbox `(32,101)–(211,155)`；Web `(32,101)–(213,154)`。字号和字宽已调整到接近原生。 |
| AI 副标题 | 原生 `(33,175)–(398,201)`；Web `(33,175)–(397,202)`。 |
| AI 空态说明 | 原生 `(50,263)–(1021,336)`；Web `(49,264)–(1021,337)`。两行内容与换行相同，约 1px 的边界差异保留为字体栅格误差，不据此宣称整页 PASS。 |
| 同设备截图 | Native [ai-native-home.png](../../.local/android-test/worktrees/skin-single-default-closure/.local/android-test/reports/native095-parity-20261105/ai-native-home.png)，SHA-256 `14201DF4234A4C268110640061FAE9AFEDEF2E59DE6E5F297E2AD965BF75454D`；最终 Web [ai-web-final-paired-20261105.png](../../.local/android-test/worktrees/skin-single-default-closure/.local/android-test/reports/native095-parity-20261105/ai-web-final-paired-20261105.png)，SHA-256 `931A44AB20E4D6CFD8458C4439AC1B70D41CCA6B97BADC70DFA71BEEA1D9A871`。UIAutomator 结构记录 [ai-web-final-window-20261105.xml](../../.local/android-test/worktrees/skin-single-default-closure/.local/android-test/reports/native095-parity-20261105/ai-web-final-window-20261105.xml)。 |
| Memo 空态回归 | 系统栏修正后增加 Memo 内容区上边距，冻结 Native 空态 bbox `(352,1254)–(731,1293)`；当前 Web `(351,1253)–(731,1291)`。参考图 `memo-native-empty.png` 与 Web 图 `memo-web-final-inset-20261105.png` 的哈希及先前触控证据保留在上一节记录。行完成、分页/详情和编辑器/草稿浮层仍未签核。 |
| 构建与包 | 根视口 Kotlin 修正提交 `96fa026594e8f085c6221c6bbda83a311829e779` 后，完整串行 Gradle 门禁 `BUILD SUCCESSFUL`，JVM `974/974`，0 failure/error/skip；源码边界、assemble、lint、AndroidTest Kotlin compile 均通过。随后仅修改默认皮肤 CSS，定向 `test_single_default_distribution.py` 为 `2/2 PASS`，Node 语法检查、Debug assemble、正式包校验及 AVD 实屏复核通过。最终 APK SHA-256 `996247646604CA935029920B5C2A2CA6CC1624ECF3C62B44A1CF6D1BA68546C0`；唯一包 `powercal-clear-default.pcskin` 为 10 文件、408148 字节，源码/APK 资源 SHA-256 同为 `D9CFAD9FFD368D60BE6D75316952463FFF6D42BAD97DB3F116B526312275B6DB`。 |
| 交互和数据边界 | 实际操作只冷启动应用并点开 AI 底栏；没有点击配置、发送消息、上传文件或调用服务商。没有执行 Store 快照测试，因此只记录“未触发业务写入入口”，不声称已证明 Store 哈希不变。Native 恢复、Core 行为、包 ID 和选择偏好未改。 |

## 2026-11-05 AI 溢出菜单与设置路由复核（PARTIAL）

| 对照项 | 实际结果 |
|---|---|
| Native 菜单基准 | `native-skin/.../AiChatScreen.kt` 中真实 `DropdownMenu` 顺序为“处理事件 → AI 批量发布/丢弃（先选择范围）→ 分隔线 → 清理 AI 临时缓存（保留聊天与事件）→ 清空聊天（不改变事件）”。批量操作在 pending count 为 0 时禁用。 |
| Web 修正 | `default-clear/source/web/index.html` 已按该顺序及文案重排；未改 action 名称或批次业务逻辑。Web 同设备实际展开截图 [ai-web-overflow-final-20261105.png](../../.local/android-test/worktrees/skin-single-default-closure/.local/android-test/reports/native095-parity-20261105/ai-web-overflow-final-20261105.png)，SHA-256 `73F1EDB6A075CE5E3C088A27458B2A56AE0C21D8C90681F99D0A182CAE91013E`；菜单外点按关闭截图 [ai-web-overflow-dismissed-final-20261105.png](../../.local/android-test/worktrees/skin-single-default-closure/.local/android-test/reports/native095-parity-20261105/ai-web-overflow-dismissed-final-20261105.png)，SHA-256 `FE3822B42FE1EBC0BD62D6BF19EF3D02610CAD531EF62C67C5DAAA04C7C5142B`。 |
| Native 实屏限制 | 曾在 `PowerCal_Native095_AI_20261105` 上以 `adb install -r` 安装冻结 APK，安装成功但显式启动后 Native `MainActivity` 未成为 resumed activity；随即恢复当前 Debug APK，未清除数据。另一个名为 `PowerCal_Native095_Original_20261105` 的 AVD 实测层级仍含 WebView，不能作为 Native 菜单参照。因此 Native 原始 APK 菜单截图本轮 `NOT TESTED`；上面的 Native 菜单顺序仅依据冻结源码，不冒充实机截图验收。 |
| 触控异常记录 | 第一次尝试点菜单外时坐标落在 Web“清理 AI 临时缓存”项；App 明确回报清理 `0` 个应用私有缓存文件，并提示聊天与正式事件保留。没有文件被删除；未触发批量发布/丢弃或清空聊天。之后用菜单左侧位置真实点按，菜单成功关闭。该误触不计入菜单关闭结果。 |
| AI 配置路由 | AI 顶部“配置”按钮实际打开 Web Skin 的“工具与设置”页（截图 `ai-web-config-route-20261105.png`）。该页“打开 AI 配置与隐私设置”按钮的源码 action 为 `nativeSettings`；`WebSkinHost.kt` 接受该 action，`PowerCalApp.kt` 将整页路由到 `Page.TOOLS`。这是仍从日常 Skin 进入完整 Native Tools 的实际调用链，必须后续收敛为 Web Settings 加受保护的窄 Host 配置表面。该按钮本轮未点击，凭据未查看或修改。 |
| 构建与包 | 当前 `aceae50b` 的默认包为 10 文件、408186 字节，SHA-256 `C8F8D3AE09B29A365A2B87BE2A9FEA4BB356E71F6E2DE93C0F9AA2667DA50338`；Debug APK SHA-256 `37E66E35ED14751E0FD3E71554CF42F5200762ED5F87B81A2BB886BDAD642B13`。官方构建器确认 APK 内唯一包与源码字节一致；分发测试 2/2、Node 语法检查、assemble 和真实 WebView 打开/关闭操作通过。Kotlin 完整门禁仍为前置 Host 修正提交 `96fa0265` 上的 974/974；此后只有 Skin HTML/CSS/包资源改变。 |

**AI 页面仍为 PARTIAL。**空首页、视口、菜单顺序和 Web 菜单关闭已验证；原始 APK 菜单截图、AI 配置表面、聊天记录/附件/消息、提案审核和失败状态仍未签核。Settings 通过 `nativeSettings` 进入完整 Native Tools 的链路是明确的迁移缺口，不得把它计作“Native 仅恢复”。Memo 行完成、分页/详情和编辑器/草稿浮层仍未签核。默认 Clear 仍是唯一 `.pcskin`，Product Cutover 保持 HOLD。

## 2026-11-05 AI 设置路由收敛（路由切片 PASS；AI 页面仍 PARTIAL）

针对上节记录的调用链 `#openAiSettings → nativeSettings → Page.TOOLS`，将 AI 配置入口收敛为独立 Host 保护表面。`#openAiSettings` 现在只发送闭合动作 `openAiSettings`；该动作已加入正式 v1 Host binding catalog，Host 仅在活动全画布 Web Skin 的 `SETTINGS` 页接受。`PowerCalApp` 不切换页面，而显示 `HostProtectedAiSettingsDialog`，复用 Host-owned 的 `HostProtectedAiSettingsSection` 与 `NativeProtectedAiSettingsSection`。API Key 留在 Compose Host 私有状态并继续密码遮罩；没有进入 Web state、DOM、Studio Mock 或 action payload。关闭存在未保存修改的表单前要求确认。

`#nativeRecoverySettings` 仍发送 `nativeSettings`，用于独立访问 Native 恢复/工具面；该路径没有被改成日常 AI 设置路由。AI 页面整体仍是 PARTIAL，不能将此路由切片计为 AI/chat/提案等功能等价。

| 验证项 | 结果 |
|---|---|
| 版本化动作与 Studio | Host catalog 与实际 closed decoder 一致；参数严格为空对象且拒绝额外字段。Studio Mock 验证动作只在 Settings 页模拟打开，不启动 Android Host、不读密钥或保存偏好。Catalog Python 测试 7/7，默认包/单默认分发 7/7，Mock Chromium/CDP E2E PASS；变更 JS 语法检查 PASS。第一次 E2E 的等待文本未与新 Mock 日志一致，更新断言文案后复跑通过。 |
| Android 真实交互 | AVD `PowerCal_Native095_AI_20261105` / `emulator-5556`，API 35、1080×2400、420 dpi。`clearSettingsRoutesAiCredentialsAndContextControlsToProtectedHostUi` 与 `clearAiSettingsDialogCloseReturnsToSkinWithoutNativeTools` 各 1/1 PASS。验证打开表单、展开模型字段、Settings 页面保持、关闭后仍在 Skin 并可回到时间轴；关闭/导航测试的 Core Store 快照一致。没有清除 App 数据；未输入、检查或保存凭据值。 |
| 构建/包 | 最终串行 Gradle 门禁 974/974 JVM（141 suites，0 failures/errors/skips）；Skin/Native 源码边界、Debug assemble、Lint（0 errors / 63 warnings）、AndroidTest Kotlin compile PASS。Debug APK SHA-256 `a27588ea0a5e2251e14bd4bdaa269703a336a38c9fbe2bbccb2e3af2b91a7bf6`。官方构建器验证唯一包 `powercal-clear-default.pcskin` 为 10 文件、408192 bytes，SHA-256 `eaa55919a071107938db08e788b59a89625adf4bd7a3c6547d6ac235bf1ceeb6`，与 APK asset 字节一致。 |
| 证据 | `.local/android-test/reports/host-ai-settings-20261105/`；实现提交 `07c62fbf16c0aa72181f92783e84a5826905fc56`；Issue `docs/issues/PC-ISSUE-SEPARATION-AI-SETTINGS-HOST-SURFACE-20261105.md`。 |

**剩余差异：**脏表单的“放弃更改”确认分支尚未单独执行；冻结原始 APK 的 AI 配置弹层截图尚未配对；Native/Web 聊天历史、附件授权、提案审核、错误与重试仍未验收。Memo、编辑器/审核以及其他 Settings 操作仍按原生 0.9.5 基准逐页迁移。Native 紧急恢复、默认包 ID/数量、Core 业务规则未改；Product Cutover 仍 HOLD。

## 2026-11-05 AI 消息操作菜单与 Host 内容解析（局部 PASS；AI 页面仍 PARTIAL）

冻结实现位于 `b1f7629f0c52812e1887443872f43aa6c49908c4:app/src/main/java/dev/powerfulcalendar/ui/v2/AiChatScreen.kt`。消息角色标识为“我”/“PowerCal AI”；长按菜单顺序为“从此消息创建事件草稿”、用户消息专属“编辑并重新发送”、按角色显示撤回/回退、复制文字。助手消息的结构化提案另有独立入口。

当前 Web v3 将角色标签和菜单顺序对齐，触屏长按约 520ms 打开菜单，并提供键盘可达的菜单按钮。桥接只发送 `messageId`、`historyRevision` 和封闭来源枚举；`PowerCalApp` 按当前 Host 历史解析文本，助手提案可用状态只投影布尔值。复制由 Host 从规范消息读取后写入 Android 剪贴板。Skin 不能提供自造文本、提案正文或剪贴板内容。草稿仍进入现有 AI/Host 提案链，不更改 Core 业务规则。

| 验证项 | 实际结果 |
|---|---|
| 冻结行为与新实现 | 菜单项目、角色条件、消息 ID/历史版本解析已按冻结 `AiChatScreen` 对照；助手提案入口依赖 Host 投影的 `proposalAvailable`，原始 `proposalSource` 不进入 Web state。 |
| Android WebView | 隔离 API 35 AVD `PowerCal_Native095_AI_20261105` / `emulator-5556`，1080×2400、420 dpi、WebView 124.0.6367.219。`clearChatMessagesMatchNativeLabelsAndUseHostOwnedMenuActions` 实际执行触屏长按、菜单项顺序和 Host 剪贴板复制，1/1 PASS；操作前后 Store 事件、草稿、待处理项、提交数、身份数与序列预览一致。仪器日志：`.local/android-test/reports/native095-ai-message-menu-20261105/instrument-final-ui.log`。 |
| 自动测试与构建 | 串行 Gradle 门禁 `154/154` tasks BUILD SUCCESSFUL；JVM `975/975`、141 suites、无失败/错误/跳过；两项源码边界、Debug APK 构建、Lint（0 errors）及 AndroidTest 编译通过。Studio Host catalog Python 定向测试 `8/8`、Node 语法检查和 Chromium E2E 通过。全套 Studio Python 为 `86/89`，3 项失败分别是 `test_architecture.PresentationBoundaryTests.test_full_route_registry_has_one_restore`、`test_architecture.PresentationBoundaryTests.test_privacy_editors_remain_host_owned_but_placeable`、`test_native_recovery_contract.NativeRecoveryContractTest.test_native_settings_and_hidden_activity_rescue_are_both_available`；这些断言针对源码字面量/路由结构，本切片未修改或重标它们，不能记为通过。 |
| APK 与默认包 | Debug APK SHA-256 `40BF867049F303EA32DA6B3C06D3321C30F0A56BA5AA20B2F07A38D1D07B770D`。APK 中仅有 `assets/powercal-clear-default.pcskin`；源码与 APK 包均为 `81A99440F5F9E67608460611FECE5825C61229852A8372B2455216086EE88456`，逐字节一致，ID 保持 `powercal-clear-default`。 |
| 未完成证据 | 原始冻结 APK 的长按菜单实屏无法恢复到前台，故 Native/Web 原 APK 菜单截图对照 `NOT TESTED`；本轮依据冻结源码核对菜单语义，并实测新 WebView。提案动作触控、历史并发冲突、附件授权、提案审核和错误/重试仍未作为完整 AI 页签核。 |

**结论：**消息标签、消息菜单和 Host 规范内容解析切片有 Android WebView 与 Store 不变证据；AI 页面和 Native 0.9.5 全量视觉/交互迁移继续 `PARTIAL/IN_PROGRESS`。Product Cutover 保持 `HOLD`。

## 2026-11-05 Month 选中日期圆角迁移（窄切片 PASS；Month 整页 PARTIAL）

冻结源码 `MonthScreen.kt` 以 `GlassShapes.chip` 呈现日期格；冻结 `PowerTheme.kt` 将该 shape 定义为 `RoundedCornerShape(14.dp)`，日格最小高度为 52dp。首轮检查只看到基础 `style.css` 的 24px，浏览器计算样式和打包结果进一步证明实际生效的 `native-095.css` 后置规则仍覆盖为 18px；Android/Browser 几何断言捕获了这个来源。现在 base 与 Native-095 两处 `.month-cell.selected` 以及 `.month-cell.today` 均为 14px，避免今天边框再次把选中形状覆盖。

| 验证项 | 实际结果 |
|---|---|
| Studio/浏览器 | `e2e_v3_month_selection.cjs` 加入运行时 computed-style 断言，Canonical Skin 仍有 10 个文件，选中格 `borderTopLeftRadius=14px`，月份/日期/Today/Camera 流程通过。`test_single_default_distribution.py` 2/2 PASS；JS 语法检查 PASS。 |
| 构建与 Android | 正式 `build_default_clear.py` 生成 409320 bytes 包，SHA-256 `DDA39D12548368A33CB0F90041B61EAFE08AA7D53C710822491B29819F5345D3`。APK SHA-256 `A064F2F66AEEB0BC726CC15F79C6ACDA3D4179160991030C2ACD083D103DF40D`，只含一个默认包且和源码逐字节一致。串行 `verifySkinSourceBoundary`、`verifyNativeSkinBoundary`、`assembleDebug`、AndroidTest Kotlin 编译/组装通过；本轮未重跑完整 JVM/Lint。 |
| 同设备 Native/Web 对照 | AVD `PowerCal_Native095_Parity_20261105` / `emulator-5580`，API 35、1080×2400、420 dpi。Native 原始 APK 图 [month-native-original-20261105.png](../../.local/android-test/reports/native095-month-radius-20261105/month-native-original-20261105.png)，SHA-256 `9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A`；新 WebView 图 [month-web-selected-20261105.png](../../.local/android-test/reports/native095-month-radius-20261105/month-web-selected-20261105.png)，SHA-256 `A3A7E1CEDDF54B3C89490DD5283D870F0873C25A654B3761A2EEB34C03E3653E`。两图为同设备、2026-11-05 选中日、`MonthFixture` ACTIVITY；Web 图由真实 Android WebView 仪器测试截取，状态栏/导航栏 ROI 已从图像误差统计中排除。 |
| 触控与几何 | `monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged` 1/1 PASS。真实日期触控 `event.isTrusted=true`；Web computed radius `14px`、选中格边界为 `{x:178.476,y:215.286,width:55.238,height:52}` CSS px；议程显示 `MonthFixture`。选日动作前后事件、草稿、待处理、提交数、身份数及序列预览相同。第一轮即时读取议程失败，已用显式 DOM 等待屏障修正测试时序，没有加入 sleep；最终复跑通过。 |
| 剩余实屏差异 | Native 选中日期的 `1 项` 文本继承 primary-container 深色，Web 仍使用次级灰；Native agenda 表面与 Web 白色高亮卡片、边距/垂直间距仍不同。当前截图的内容区域 MAE `7.264/255`、P95 `13`、完全相同像素比例 `41.20%`；日历 ROI MAE `7.611`、agenda ROI MAE `9.863`。这些指标仅描述截图，不设为像素 PASS 阈值；状态栏外观不一致已裁除。 |

**结论：**选中形状、格高和选中内容色已有冻结源码、浏览器 computed-style 与 Android WebView 证据；月格表面和 agenda 视觉仍待迁移。Month 整页保持 `PARTIAL`，Product Cutover `HOLD`。

## 2026-11-05 Month 选中日容器文字颜色迁移（窄切片 PASS；Month 整页 PARTIAL）

冻结 SHA `b1f7629f0c52812e1887443872f43aa6c49908c4` 的 `PowerTheme.kt:83-84` 定义浅色 `primaryContainer=#DCE5FF`、`onPrimaryContainer=#0B2A66`；深色值在 `PowerTheme.kt:111-112` 为 `#1B3A7A` / `#DCE5FF`。冻结 `MonthScreen.kt` 将日期格数字与计数文本放在使用 `primaryContainer` 的 `Surface` 内，二者继承其内容色。Web 原规则却让选中日数字继承 `--label`，计数被 `.month-count` 固定为 `--label-2`。

默认 Skin 现以 `--on-accent-container` 映射冻结的浅/深色容器前景，选中格计数继承父项颜色。没有修改状态投影、Host、Core、月份/重复规则或日历数据。Studio E2E 以实际浏览器 computed style 检查选中格、数字和计数；Android instrumentation 在真实 WebView 中检查同一三处、执行可信触控并验证 Store 快照不变。

| 验证项 | 实际结果 |
|---|---|
| Studio/浏览器 | `e2e_v3_month_selection.cjs` PASS；Canonical Skin 为 10 个文件。实测选中格、日期数字和 `1 项` 均为 `rgb(11, 42, 102)`，主题 token 为 `#0B2A66`；脚本同时核对深色 token 对应 `#DCE5FF`。日期、年月选择、Today、Camera 和新建预览流程通过。发行包分布测试 2/2 PASS，Node 语法检查 PASS。 |
| 构建门禁 | 源码边界 PASS；独立 `assembleDebug`、`lintDebug`、AndroidTest Kotlin 编译/组装 PASS。完整 Gradle 命令在 JVM 阶段失败：975 项中 1 项 `SystemCalendarImportTest.rejectedItemsArePersistedAsWarningsWithoutCalendarWrites` 于 `commitCount()` 报 SQLite `SQLITE_BUSY`；未改 Core 或该测试，详见本切片报告和本地日志。该失败不计作通过。 |
| 默认包 | `build_default_clear.py` 生成 409344 bytes 的 `powercal-clear-default` 包，SHA-256 `85E2DD18688CF622823BFBF23233DD6957617CDF772CC551E1323CAF54EBE120`。Debug APK SHA-256 `6CEA8B862C55C23F76262FE7D1B59F30A1B795EBFEE5AB78F7CCB7A321E7C64C`；APK 仅有一个 `.pcskin`，其字节与源码资源完全一致。 |
| 同设备截图 | 冻结原始 APK 与 WebView 均在隔离 AVD `PowerCal_Native095_Parity_20261105` / `emulator-5580`、API 35、1080×2400、420 dpi。Native 截图 SHA-256 `9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A`；新 WebView 截图 SHA-256 `64ECBB6639B0AB45A794DF9FE311BDAEB54E9234ABF37BD4C69FFDE34D079BF5`。`#DCE5FF` 填充像素框 Native `(471,627)-(609,757)`、Web `(471,630)-(611,762)`；可见数值颜色与截图一致，仍有数像素几何偏移，不能据此宣称整格像素相同。 |
| Android 行为 | `monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged` 1/1 PASS。真实日期触控后选中格/日期数字/计数均匹配冻结前景，MonthFixture 可见；选日操作前后事件、草稿、待处理、提交数、身份数及序列预览相同。 |
| 证据 | `.local/android-test/reports/native095-month-count-20261105/`；原生截图沿用 `.local/android-test/reports/native095-month-radius-20261105/month-native-original-20261105.png`。独立 Issue：`docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-SELECTION-COLOR-20261105.md`。 |

**结论：**选中格文字前景色窄切片有冻结色值、浏览器样式、真实 WebView、截图和 Store 不变证据，标记 `PASS`。内层日格玻璃填充颜色切片现有 Compose token、computed style 与同设备像素证据，但其范围仍有几何差异；外层面板和 agenda 卡片仍待迁移。Month 整页继续 `PARTIAL`，Product Cutover `HOLD`。

## 2026-11-05 Month 内层玻璃网格填充迁移（颜色与范围 PASS；Month 整页 PARTIAL）

冻结 `PowerTheme.kt` 的浅色 `AxisPalette.glassFill` 为 `0xCCFFFFFF`（80% 白），深色为 `0xB31A2130`；Month 网格与月份栏处于同一个 Native `GlassPanel`。当前默认 Skin 使用同一玻璃色 token，并把填充层锚定到 `.month-calendar-panel::before`，而不是网格内容层；父面板设置 `isolation:isolate`，标题栏和网格内容置于填充层之上，避免伪元素盖住月份标题、箭头或日期交互。

| 验证项 | 实际结果 |
|---|---|
| Studio/浏览器 | `e2e_v3_month_selection.cjs` PASS；实测背景 `rgba(255,255,255,.8)`、top `27px`、bottom `29px`、width `331px`。月份/日期/Today/Camera 和预览新建流程通过；默认包分发 2/2、相关 JS 语法检查 PASS。 |
| Android WebView | API 35 隔离 AVD `PowerCal_Native095_Parity_20261105` / `emulator-5580`。`monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged` 定向 instrumentation 1/1 PASS；真实可信日期触控后 11 月 5 日和 `MonthFixture` 可见，Core Store 计数快照不变。截图在 `WebView.postVisualStateCallback` 完成后捕获，旧的异步合成帧已被替代。 |
| 同设备像素 | Native 1080×2400 截图 SHA-256 `9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A`；同步 Web 截图 SHA-256 `163A5BE271320D25CEE3E2161866B507DF3DF9E6365B9817B81D8C798ED29A65`。在 ROI `(80,300)-(1000,1300)` 扫描精确 `#FDFDFE` 像素，Native 边界 `(105,338)-(973,1235)`，Web `(107,338)-(974,1234)`；各边差约 0–2 px。边界最大值为扫描包含端点。 |
| 构建与包 | 串行源码边界、Debug assemble、Lint、AndroidTest Kotlin 编译/组装 PASS。新增 screenshot barrier 首次编译错误已改为 `WebView.VisualStateCallback` 后通过；定向 connected test 1/1。完整 JVM 本切片未重跑；先前 975 项中一项 SQLite `SQLITE_BUSY` 仍未解决，本记录不把完整 JVM 标记 PASS。Debug APK SHA-256 `896D5BAB6A01A145FDA97D53706DCB7DF30E31CE77B3E0998C89730C7B2278D6`；默认包 409395 bytes，SHA-256 `0B41CAC7FA5BF65C5A5D64D97AA740D6656B93560F1269EC59359B1B9C6B5331`。APK 只含一份 `.pcskin` 且与源资源逐字节一致。 |
| 外层与其他差异 | 冻结 Native 外层采样主色约 `#F2F3F3`；Web 外层仍为 `#F3F5FA`，描边和阴影也不同。agenda 卡片表面与间距仍待逐项比对。本切片没有调整这些层。 |
| 证据 | `.local/android-test/reports/native095-month-grid-surface-20261105/`，包括同步截图、Gradle 和 instrumentation 日志；Native 原图沿用 `.local/android-test/reports/native095-month-radius-20261105/month-native-original-20261105.png`。Issue：`docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-GRID-GLASS-SURFACE-20261105.md`。代码提交：`4a0a3f5303e13f682abf9ccbea2875d988f15a68`。 |

**结论：**月格内层玻璃面颜色与像素范围窄切片 `PASS`。外层 Month 卡片、agenda 及其他页面仍未签核；Month 整页保持 `PARTIAL`，Product Cutover `HOLD`。

## 2026-11-05 Month 外层 GlassPanel 材质与阴影迁移（面板窄切片 PASS；Month 整页 PARTIAL）

冻结参考为原生 0.9.5-rc.1 APK（SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`）与源码 SHA `b1f7629f0c52812e1887443872f43aa6c49908c4`。Native `GlassPanel` 契约为 28dp 圆角、`outlineVariant` 55% alpha 的 1dp 边线及效果开启时 2dp elevation，见 `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeGlassPanel.kt:31-32`。新版 Web v3 在唯一默认包中实现浅色 `#F2F3F3` 面、同色系 outlineVariant 和压缩校准的阴影；CSS 见 `tools/skin-studio/default-clear/source/web/native-095.css:17-18,111`。深色 token 沿用冻结调色板，本轮未对深色截图签核。

| 验证项 | 实际结果 |
|---|---|
| Studio 浏览器 | `e2e_v3_month_selection.cjs` PASS：工厂包导入成功，Panel fill `rgb(242,243,243)`、边线色 `rgba(195,202,216,.55)`、28px 圆角、preview 缩放后 1px 描边及阴影样式均符合断言；月份/日期/Today/Camera 与预览新建操作继续通过。分发测试 2/2，Node 语法检查 PASS。 |
| API 35 Android WebView | 隔离 AVD `PowerCal_Native095_Parity_20261105` / `emulator-5580`，Android API 35，1080×2400、420 dpi，WebView 124.0.6367.219。`monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged` 1/1 PASS；真实日期触控后 2026-11-05 与 `MonthFixture` 可见；事件、草稿、待处理、提交、身份、序列快照不变。截图捕获先等待两帧 `requestAnimationFrame`，再等待 WebView visual-state callback，DOM 与截图日期一致。 |
| 同设备像素 | Native 原始截图 SHA-256 `9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A`；新版 Web 截图 SHA-256 `D0AD6368B24434BBA64A84C9593ECABC22E114FCEC54FF6B496CF9CAC9932D86`。同坐标左侧边缘 ROI `(x=22..35,y=600..1200)` MAE `0.786/255`、P95 通道差 `2`。Native/Web 描边都覆盖 3 个设备像素；WebView 对 1.5 CSS px 声明报告 computed width `1.14286px`，乘 DPR `2.625` 对应 3 个设备像素。 |
| 构建与工厂包 | 串行门禁 `:app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest` PASS。未运行 JVM suite，因为本切片只改 CSS、工厂包和 AndroidTest；历史 full gate 的 `SystemCalendarImportTest` SQLite `SQLITE_BUSY` 仍保持未解决记录。Debug APK SHA-256 `B7659FC55A3188D2915940FF879702E0FC6658E3750F0ADA0D5BBCE7C4DA7F37`。`build_default_clear.py --verify --apk` PASS；APK 恰有一份 `powercal-clear-default.pcskin`，包 SHA-256 `0AE888C5BC9CB7335AC2499A6A1D85DE11EB989BF37EEE9855A92411CF2B203C`、409426 bytes，和源工程包逐字节一致。 |
| 证据 | `.local/android-test/reports/native095-month-outer-panel-20261105/`：浏览器/Gradle/Android instrumentation 日志、像素对照 JSON 与 Web 截图；Native 原始截图沿用 `.local/android-test/reports/native095-month-radius-20261105/month-native-original-20261105.png`。Issue：`docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-OUTER-GLASSPANEL-20261105.md`。代码提交：`c268902482d14b7d4462c440253e0bf9f2493454`。 |

**结论：**外层 GlassPanel 的浅色材质、边框像素宽度、圆角和侧边阴影窄切片标记 `PASS`。这不代表 Month 整页通过：agenda 事件卡片表面/间距、日期行完整视觉状态及深色面板尚未签核；后续继续同一冻结原生基准。唯一默认 Skin ID 与包数量不变，没有修改 Core/Host 业务行为、Native 恢复或用户数据。Product Cutover 仍为 `HOLD`。

## 2026-11-06 Month agenda 事件卡片与日期标题（窄切片 PASS；Month 整页 PARTIAL）

Owner 冻结基准仍为 Native 0.9.5-rc.1 源码 SHA b1f7629f0c52812e1887443872f43aa6c49908c4 与原始 APK SHA-256 DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76。冻结 MonthScreen.kt 的 agenda 行使用 Surface(shape = skinBarShape(), color = surfaceContainerLow)、最小高度 64dp 和 16dp 内容内边距；标题、摘要、序号分别采用 Material titleMedium、bodyMedium、labelSmall，事件类型颜色来自冻结 PowerTheme.kt 的 kindColor。当前迁移位置为 tools/skin-studio/default-clear/source/web/native-095.css；浏览器与 WebView 断言位于 tools/skin-studio/tests/e2e_v3_month_selection.cjs 和 app/src/androidTest/java/dev/powerfulcalendar/ui/v2/NativeClearMonthOccurrenceParityInstrumentedTest.kt。

CSS computed-style 与 Android WebView 断言确认浅色卡面 #F6F8FC、20px 圆角、无投影、16px padding、0px 卡内间距、12px agenda 顶部和相邻卡间距、事件类型标题色，以及 Native 对应的标题/摘要/序号字体尺寸和行高。所选日期标题按 16px/500/24px 与 0.15px 字距呈现。该结论仅限浅色默认主题和已建立的 MonthFixture 状态；没有据此推断深色主题或其他页面等价。

| 验证 | 真实结果 |
|---|---|
| 冻结参考与运行环境 | 同一隔离 API 35 AVD PowerCal_Native095_Parity_20261105 / emulator-5580，1080×2400、420 dpi，WebView 124.0.6367.219。Native 原图 .local/android-test/reports/native095-month-radius-20261105/month-native-original-20261105.png，SHA-256 9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A；Web 图 .local/android-test/reports/native095-month-agenda-20261106/month-agenda-web-final-20261106.png，SHA-256 6021DC49EED9315B105C3A84D8701B0250F9E32E220206B52FFD12DE7443E16F。 |
| 几何与像素 | Native agenda 卡 bbox (32,1436)-(1047,1677)，Web (32,1438)-(1050,1679)；Web 下移 2px、右边界多 3px。所选日期标题 ink bbox Native (37,1357)-(379,1398)、Web (35,1358)-(376,1398)。ROI (0,1420)-(1080,1690) MAE 4.583、P95 通道差 2、通道差大于 2 的像素占 5.3443%、卡面 mask IoU 0.948156。因此样式参数已对齐，栅格化和边界仍存在小差异。 |
| 操作与数据 | monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged 真实 WebView 日期触控/样式断言 1/1 PASS，Store 快照不变；sameSelectedRecurringOccurrenceOpensEquivalentNativeAndClearMonthEditor 重复实例目标编辑器路径 1/1 PASS。后者只验证目标打开，不据此签核全套重复规则。 |
| 构建与自动测试 | 串行 Gradle 全门禁 :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 --no-parallel --console=plain 本轮 PASS；XML 报告 975/975 JVM、0 failures/errors/skips，141 个测试报告文件。先前记录的 SQLite SQLITE_BUSY 在本轮没有复现。Chromium E2E、Node 检查和默认包分发测试 2/2 PASS。 |
| 包身份与哈希 | build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk PASS。Debug APK SHA-256 1A6C36F310295A0594C72C43B73F478A4F0A7C5A5C84A688BAC44FA670AA7ACD。APK 仅含一份 powercal-clear-default.pcskin；10 文件、409458 bytes，SHA-256 F7ECF2A168A8D65968362A8A464053DA10248148E4837DC7ACF0DCB73F0B1826，与源工程包逐字节一致。 |
| 改动与数据边界 | 代码提交 caa1d18e7160df5ee51ed33783b4bf4fe36df1ee 修改 native-095.css、Canonical .pcskin 生成产物、Month instrumentation 样式/截图断言和 Studio month E2E。没有改 Core/Host、Native 源码、Skin ID、默认包数量或 Calendar 业务规则。Android 日期选择测试的 Store 计数与序列快照保持不变。 |
| 证据 | 本地原始日志、截图、比较 JSON 位于 .local/android-test/reports/native095-month-agenda-20261106/；独立 Issue：docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-AGENDA-CARD-20261106.md。本附录只关闭 agenda 卡片/标题窄视觉切片；Month 整页继续 PARTIAL，Native 恢复保持不变，Product Cutover 继续 HOLD。 |

**未签核差异（下述动作按钮切片之前）：**Month 底部两个动作按钮、年份和月份控件、深色主题、其它日期/空态/密集事件状态仍待逐项测量。不得据此提前签核整个 Month 或其他页面。底部动作按钮后续状态见下方追加记录。

## 2026-11-06 Month 底部动作按钮（尺寸、间距、点击窄切片 PASS；Month 整页 PARTIAL）

冻结基准为 Native 0.9.5-rc.1 源码 `b1f7629f0c52812e1887443872f43aa6c49908c4` 与 APK SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。`MonthScreen.kt` 中创建按钮是 `OutlinedButton`、最小高度 48dp，图标和文字同一 Row（无显式间隔）；时间轴跳转是最小高度 48dp 的 `TextButton`；两项由 `LazyColumn` 的 `Arrangement.spacedBy(12.dp)` 分隔。Web v3 原动作区 gap 为 2px、边框 1 CSS px，且 agenda 底 padding 4px 与动作区 top padding 10px 叠加为 14px。

更新后，创建动作使用 Material Add 路径 `M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6z`、24dp 图标、零图文间隙、14sp/500/20sp 标签和 24dp 横向内容 padding；操作区使用 12px gap 和 48dp 触控高度。1.5 CSS px 边框在当前 WebView 的 computed width 为 1.14286 CSS px，DPR 2.625 下形成 3 个设备像素，和原生 1dp 描边的 3 像素一致。agenda 底部 4px + 动作区顶部 8px = 12 CSS px，并由 WebView 几何断言验证。

| 验证项 | 本轮结果 |
|---|---|
| 冻结 Native 实际图 | `month-native-original-20261105.png`，1080×2400；SHA-256 `9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A`。截图含 Nov 5 `MonthFixture` 及两个底部动作。 |
| 新版 Android WebView 实际图 | 隔离 API 35 AVD `PowerCal_Native095_Parity_20261105` / `emulator-5580`、1080×2400、420dpi、WebView 124.0.6367.219；图 `month-actions-web-20261106.png` SHA-256 `51CCEC518B5D31A4C249784048A641448901A9D34B337D3C5B6CC87E2742B254`。截图等待 visual-state callback，和 Native 参考为相同月份/日期/fixture。 |
| 像素几何 | 以 `#C3CAD8` ±8 RGB 掩模定位按钮描边：Native y 行 `1710..1835`，Web `1711..1836`，按钮高度同为 126 个采样像素、Web 仅低 1px。ROI `x20..1059,y1690..1859` 的未平移描边掩模 IoU `0.4574`；将 Web 图按实测 1px 向上对齐后 IoU `0.8634`。 |
| 实际交互与 Store | `monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged` 1/1 PASS；`monthYearMonthDateAndTodayTransitionsMatchWithoutMovingTimelineOrWritingStore` 1/1 PASS。Native 与 Clear 日期选择、Create/Timeline 路由以及 Store 快照检查均通过；本次操作没有事件、草稿、待处理项、提交、身份或 event sequence 变化。 |
| 自动化和构建 | 串行完整命令包括 `:app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest`，PASS。JUnit XML 实计 975 tests、0 failures/errors/skips、141 reports。Chromium `e2e_v3_month_selection.cjs` PASS；JS syntax PASS；`test_single_default_distribution.py` 2/2 PASS；`build_default_clear.py --verify --apk` PASS。 |
| 包身份 | APK SHA-256 `19D52E6ED16AD42855E8DD5798A3B8D1B425C60A3B9951FD637ABA3BD4A9C818`。唯一 `assets/powercal-clear-default.pcskin` 为 409520 bytes、10 files、SHA-256 `04AC4E3358448C18C2D0FBCF03853C9F2B16BCE3DDBDB0CD01C6DB9F01022D77`，与正式构建器输出及源资源字节一致。 |
| 失败和重试 | 首次设备样式用例因 JSON 探针漏回传按钮 `gap` 而失败；已补齐探针字段后设备用例通过。曾在重建 APK 之前用旧 APK 做包校验，资源长度不符；完整串行构建后正式验证通过。无产品缺陷由这些失败引起。 |
| 证据与变更 | 本地日志/图像在 `.local/android-test/reports/native095-month-actions-20261106/`；Issue `docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-ACTIONS-20261106.md`；代码提交 `b8376da7bb46c2ae41d8a86618998a282b16d816`。仅修改默认 Skin Month 结构/CSS、重建工厂包和对应浏览器/Android 样式测试；无 Core、Host、Native、Store 或 ID 变更。 |

**结论与下一步：**创建及跳转按钮这一视觉/交互窄切片 `PASS`，捕获到的残余垂直差为 1 个物理像素。Month 整页仍 `PARTIAL`；下一项仅审计并对照年份/月选择控件及选择对话框的外观、状态与触控。深色主题、空态/密集态及其它 Native 页面仍未签核。Native 独立安全恢复不变，Product Cutover 仍 `HOLD`。
