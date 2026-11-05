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

冻结版实际日常界面不止时间轴。页面由冻结树中的 app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt 与 NativePowerNavigation.kt 路由；页面实现位于 app/src/main/java/dev/powerfulcalendar/ui/v2/。分离后对应源码位于 native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/。

| 页面 / 表面 | Native 组件、状态与交互 | 当前 Web v3 对应 | 差异与迁移边界 |
|---|---|---|---|
| 应用壳 / 主导航 | NativePowerNavigation 四项：时间轴、日历、AI 助手、设置；底栏固定。时间轴顶部显示品牌、日期、今日、Memo 计数、待处理计数、新建。颜色、选中态、面板和系统栏使用 Native 主题。 | index.html 的 clear-head、clear-tabbar；顶部文字工具条，底部五项含 Memo。 | 视觉/导航差异。Memo 应由时间轴快捷入口打开，底栏改回四项。保留当前唯一 Web v3 正常 Runtime。 |
| 时间轴 | NativeTimelineScreen：中心垂直时间尺、左右独立泳道（ALARM 左；ACTIVITY/DEADLINE 右）、真实时间区间、当前时间蓝线、框外标题、按实际文字矩形贪心折叠、拖动与惯性、左右横向滚动、纵向相机平移、缩放、双击时间尺复位 24 小时、“今日”定位本地中午、事件点击、提醒受阻提示、新建四类入口。 | app.js::renderTimeline、title-folding.js、.timelineWing/.timelineLanes；正式投影/Host 动作已存在。最新 WebView ACTIVITY 固定夹具显示蓝色现在线、双翼、框外贪心标题，并通过了折叠事件触控与 Camera 断言。 | 冻结原始 APK 与 WebView 的当前 ACTIVITY 夹具在同屏行、填充色及主要文字位置接近；Web 绘制仍有 1px 水平边界/文字墨迹差。局部测试不覆盖 ALARM、DEADLINE、重复实例、长标题/多泳道、大字体等，不能视作整个时间轴或全产品签核。继续按冻结参考移植双翼、真实时间几何、框外标题与已冻结手势；未验证的类型和状态逐项补齐。 |
| 月历 | NativeMonthScreen：返回时间轴（保留相机）、今天、上/下月、年份输入选择、月份选择、横向月切换、七列日期网格、日期选中、当天事件摘要/重复实例、为选定日期新建、跳转到时间轴。底栏仍为四项。 | index.html::monthPage、app.js::renderMonth，由月份和日期的正式 Host 投影驱动；现已实现分离年份/月份选择、原生式网格计数、按可见日期筛选 agenda，并通过正式 Host action 跳转时间轴。 | 本轮 Month 空态截图已与冻结 APK 实屏配对；标题、卡片、日期格和 agenda 已接近，但像素/文字灰度仍有差异，且没有用原始 APK 与 Web 对同一事件 fixture 做成对截图。当前 Native Compose 与 Web 的重复实例点击、日期选择、Today、月份手势、跳到本地中午和跨进程恢复有 Android 触控/测试证据。Month 子集仍为 PARTIAL，不代表完整页面或整产品签核。 |
| Memo / 待办 | NativeMemoCenterScreen：顶部返回与新增无日期 Memo、Open/Done、完成可撤销提示、行内完成/撤销、标题打开编辑、分页加载。重复待办显示范围由 Host 提供。 | memoPage、renderMemo、toggleMemo、分页按钮；当前为底部第五个页面，Filter 为大分段控件。 | 功能路径有先前 F-04 PREVIOUS PASS；视觉/导航有差异。迁移 Native 标题行、轻量 Open/Done 操作、行密度与返回/新增入口；分页和完成状态继续走现有正式 API。 |
| AI 聊天 | NativeAiChatScreen：标题/副标题、配置、待处理计数、溢出菜单（批次处理、清理缓存、清空聊天）、消息列表、长按消息菜单（提案、编辑重发、回退、复制）、附件“+”菜单、输入和发送、忙碌/锁定状态。提案补充与审核有独立卡面。 | aiPage、renderChat、renderAiBatchControls、renderAiProposalReview、消息/附件桥接。当前把清空聊天和批量发布/丢弃常驻显示，图片/文件按钮同时展开。 | 功能路径有先前 F-07 PREVIOUS PASS（真服务商仍未测）；视觉/信息架构有差异。迁移原生标题、溢出菜单、输入附件入口、消息/提案卡片层级；审核和审批保持 Host/Core 授权。 |
| 设置 / 工具 | NativeToolsPage 的个人资料、AI 受保护配置、通知/精确闹钟权限、提醒重建与状态、诊断、系统日历一次性导入、旧版只读迁移、原生备份恢复、ICS 导出。滚动卡片和每行说明构成设置结构。 | settingsPage、renderSettings、Host options、profile editor；当前默认 Web 底栏“设置”触控实际进入 PowerCalApp 的 Native `Page.TOOLS`，不是 Web `settingsPage`。 | 功能路径有先前 F-08 PREVIOUS PASS；视觉差异明显且存在普通用户日常 Native 路由。除必要的可信系统/恢复界面外，默认 Skin 应承接 Settings 表现与导航；SAF、权限提示、密钥持有和可信授权仍由 Host/Android 承担，不能暴露给 JS。需单独修复底栏路由并逐行实测，不能在 Month 子任务中顺手更改。 |
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
