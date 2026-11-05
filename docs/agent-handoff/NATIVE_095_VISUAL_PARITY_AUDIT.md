# PowerCal 0.9.5-rc.1 原生视觉基准差异审计

## 裁决与基线

Owner 于 2026-11-05 明确将 **2026-10-27 冻结的 PowerCal 0.9.5-rc.1 原生界面**指定为唯一正式默认 .pcskin 的视觉、交互和页面结构基准。冻结应用源码为 b1f7629f0c52812e1887443872f43aa6c49908c4，Owner 签核见 docs/CORE_FREEZE_OWNER_SIGNOFF_2026-10-27.md。

| 被比较对象 | 基线 |
|---|---|
| 原生参考 APK | .local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-b1f7629-debug-core-HOLD.apk |
| 原生 APK SHA-256 | DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76 |
| 冻结源码 | b1f7629f0c52812e1887443872f43aa6c49908c4 |
| 当前 Web 侧代码 | e41246d7ebe5b329adbdc657bd3e53735695efa1 |
| 当前 Web 侧 APK SHA-256 | 49DA3DA24E928B8099B0792E1D5C1BC29E8660048D990CEB2C564DC05D79E052 |
| 隔离设备 | 新建 AVD PowerCal_Native095_Parity_20261105，Android API 35，Pixel 6 设备配置 |
| 实际显示参数 | 1080×2400，420 dpi；模拟器运行属性 sdk_gphone64_x86_64 |
| 视觉证据目录 | .local/android-test/reports/skin-baseline-parity-20261105/ |

参考 APK 与当前 APK 在上述新建 AVD 上依次安装。只在该 AVD 的 dev.powerfulcalendar 包内重置了测试应用数据；没有触碰既有 Wave2 AVD、真机或其他用户数据。测试中拒绝了首次“导入系统日历”提示，没有导入任何日历内容。打开并无修改地退出活动编辑器后，待处理卡组显示 0 张。

截图包括 native-timeline-empty.png、native-month.png、native-memo.png、native-ai.png、native-tools-top.png、native-tools-lower.png、native-draft-deck.png、native-activity-editor.png，以及 current-web-timeline.png、current-web-month.png、current-web-memo.png、current-web-ai.png、current-web-settings.png、current-web-draft-deck.png、current-web-activity-editor.png。这是两个版本的真实 Android 界面，不是 Studio Mock。截图跨越模拟器本地午夜；日期和“现在”线的屏幕位置不能当作同一时刻像素差异。本轮先完成页面/布局/点击路径审计，后续逐项验收必须使用固定时钟、相同数据集和相同设备几何。

## Native 全部日常表现面清点

冻结版实际日常界面不止时间轴。页面由冻结树中的 app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt 与 NativePowerNavigation.kt 路由；页面实现位于 app/src/main/java/dev/powerfulcalendar/ui/v2/。分离后对应源码位于 native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/。

| 页面 / 表面 | Native 组件、状态与交互 | 当前 Web v3 对应 | 差异与迁移边界 |
|---|---|---|---|
| 应用壳 / 主导航 | NativePowerNavigation 四项：时间轴、日历、AI 助手、设置；底栏固定。时间轴顶部显示品牌、日期、今日、Memo 计数、待处理计数、新建。颜色、选中态、面板和系统栏使用 Native 主题。 | index.html 的 clear-head、clear-tabbar；顶部文字工具条，底部五项含 Memo。 | 视觉/导航差异。Memo 应由时间轴快捷入口打开，底栏改回四项。保留当前唯一 Web v3 正常 Runtime。 |
| 时间轴 | NativeTimelineScreen：中心垂直时间尺、左右独立泳道（ALARM 左；ACTIVITY/DEADLINE 右）、真实时间区间、当前时间蓝线、框外标题、按实际文字矩形贪心折叠、拖动与惯性、左右横向滚动、纵向相机平移、缩放、双击时间尺复位 24 小时、“今日”定位本地中午、事件点击、提醒受阻提示、新建四类入口。 | app.js::renderTimeline、title-folding.js、.timelineWing/.timelineLanes；正式投影/Host 动作已存在。Web 实屏目前显示红色现在标记、每小时刻度、独立“较早/稍后”按钮以及额外 24h 按钮。 | 几何和标题算法已有实现及此前 F-02 证据；本轮现屏未用确定性事件集验证标题矩形。视觉/交互有差异。保留两翼、真实几何、框外标题、贪心折叠与冻结手势；迁移蓝线、刻度/工具栏层次，移除以按钮代替手势的额外控制。 |
| 月历 | NativeMonthScreen：返回时间轴（保留相机）、今天、上/下月、年份输入选择、月份选择、横向月切换、七列日期网格、日期选中、当天事件摘要/重复实例、为选定日期新建、跳转到时间轴。底栏仍为四项。 | index.html::monthPage、app.js::renderMonth，由月份和日期的正式 Host 投影驱动。 | 先前 F-03 的实例投影/点击回归为 PREVIOUS PASS；当前实屏不同。当前月历仍露出时间轴顶部工具条，显示 2026-11 · GMT，网格/选中态和 Native 不同。需迁移原生页面头、卡片网格、月份/日期状态表现；不复制重复规则。 |
| Memo / 待办 | NativeMemoCenterScreen：顶部返回与新增无日期 Memo、Open/Done、完成可撤销提示、行内完成/撤销、标题打开编辑、分页加载。重复待办显示范围由 Host 提供。 | memoPage、renderMemo、toggleMemo、分页按钮；当前为底部第五个页面，Filter 为大分段控件。 | 功能路径有先前 F-04 PREVIOUS PASS；视觉/导航有差异。迁移 Native 标题行、轻量 Open/Done 操作、行密度与返回/新增入口；分页和完成状态继续走现有正式 API。 |
| AI 聊天 | NativeAiChatScreen：标题/副标题、配置、待处理计数、溢出菜单（批次处理、清理缓存、清空聊天）、消息列表、长按消息菜单（提案、编辑重发、回退、复制）、附件“+”菜单、输入和发送、忙碌/锁定状态。提案补充与审核有独立卡面。 | aiPage、renderChat、renderAiBatchControls、renderAiProposalReview、消息/附件桥接。当前把清空聊天和批量发布/丢弃常驻显示，图片/文件按钮同时展开。 | 功能路径有先前 F-07 PREVIOUS PASS（真服务商仍未测）；视觉/信息架构有差异。迁移原生标题、溢出菜单、输入附件入口、消息/提案卡片层级；审核和审批保持 Host/Core 授权。 |
| 设置 / 工具 | NativeToolsPage 的个人资料、AI 受保护配置、通知/精确闹钟权限、提醒重建与状态、诊断、系统日历一次性导入、旧版只读迁移、原生备份恢复、ICS 导出。滚动卡片和每行说明构成设置结构。 | settingsPage、renderSettings、Host options、profile editor。多数系统操作已接入 Web/Host；部分个人资料/AI 设置仍经 Host 原生受保护界面。 | 功能路径有先前 F-08 PREVIOUS PASS；视觉差异明显。将 Skin 可负责的状态与动作迁到 Native 卡片/行式层次。SAF、权限提示、密钥持有和可信授权仍由 Host/Android 承担，不能暴露给 JS。 |
| 待处理草稿卡组 | NativeDraftDeckScreen：叠卡、内外滑动、逐卡/快速浏览、搜索草稿与正式事件、恢复/删除条目、空态、AI 批次进度、冲突处理、审批/撤销/关闭。 | 当前 #pendingDeck overlay，有卡组/发布、搜索、恢复、删除和完成退出。 | 业务与状态功能有前序 F-05/F-07 PREVIOUS PASS；Native 是深色 scrim 上的白色浮层/叠卡，Web 当前为高面积极白底板，结构不等价。视觉/动作层次需迁移；写入仍 Host/Core。 |
| 四类事件编辑与详情 | NativeDirectEventEditor、NativeDeckEventForm、NativeEventInspection：类型选择、标题、时间/日期、地点/备注、提醒和启用状态、重复规则与作用域、详情、修改前后状态、只在业务修改后暂存、未修改关闭不落草稿。 | edit.js 生成编辑 Sheet 和字段；当前新建先弹四类选择对话框，再进入滚动表单。截图显示编辑中计数“1 张·已暂存”，关闭后卡组实际为 0；与 Native “只在修改后暂存 / 未修改”表达不一致。 | F-05/F-06 冻结业务路径有 PREVIOUS PASS；无修改关闭在本轮 Web AVD 操作后显示 0 张。保持真实零写入语义，修正临时状态文案；迁移原生全屏编辑/底部操作及四类表单的布局层级。 |
| 审核/安全子表面 | NativeFormalWriteReview、NativeAiProposalReview、NativeAiBatchAuthorizationReview、NativeAiInputReview（图片、OCR、文件）、NativeAiStuckBatchReleaseReview、NativeLegacyDataReview、NativeCalendarImportPrompt、NativeTextDraftDialog、原生 Date/Time Picker。 | Clear 有 Web 提案卡、正式写入确认入口、Web 状态卡；系统选择器、SAF 和受保护操作由 Host 调度。 | 属于完整审计面，不能只迁主页面。Skin 可表达的审核信息/布局由 Skin 接手；操作系统 Picker、SAF、权限页以及密钥/授权确认的可信面必须继续由 Android Host 提供。单独对照确认文案、字段、拒绝/取消和可访问性。 |

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
