## Update: runtime-importable .pcskin v2

The source-compiled ten-renderer SDK described below remains available, but it is
no longer the only way to change real layouts. The upgraded Android host also supports
a **v2 declarative runtime interpreter**. Using ui.json inside a .pcskin ZIP, an Agent
can rearrange built-in Compose components, host-bound buttons, display widgets,
entry animations and page transitions with no Kotlin rebuild between skin switches.

First read [SKIN_PACK_V2.md](SKIN_PACK_V2.md) for ZIP build instructions, supported
node types, widget/action scope and security invariants. New unsupported custom
widgets/gestures still require a reviewed compiled renderer source change.

# PowerCal 编译式皮肤开发手册（给任何人 / Agent）

更新：2026-10-21。目标是让第一次接手仓库的开发者**只读本文件、使用生成器、编辑 skins 目录的文件**，即可重构应用的 Compose 视觉层，而不用碰日历核心、AI、提醒、数据库或宿主业务回调。

技术架构与接口契约：docs/UI_SKIN_EXTENSION_CONTRACT_V4.md。Skin Studio 网页工具：tools/skin-studio/README.md。

## 1. 三种皮肤文件，不要混淆

- .pcskin 是直接导入手机的安全数据 ZIP，支持色板、四个尺寸令牌、十张 PNG/WebP 图，不支持任意 Compose 布局、代码或动画。
- .pcstudio.json 是 Skin Studio 浏览器设计文件，保存六个页面布局槽、顺序、区域、动效和资源，**不能直接装进 Android**。
- 编译式 Kotlin Skin 是完全可替换页面布局、交互视觉与动画的源码文件，经过代码审查后随 APK 编译；属于你现在开发全新默认皮肤应使用的工作流。

## 2. 三步创建皮肤

从项目根目录运行：

    python tools/skin-studio/new_compiled_skin.py aurora-glass --activate

这会创建 app/src/main/java/dev/powerfulcalendar/ui/v2/skins/AuroraGlassSkin.kt，且只修改同目录的 ActiveVisualSkin.kt 激活它。默认全部 Render(context) 通过 context.Native() 显示原生界面，因此刚生成时没有任何原生功能缺失。

然后编辑 AuroraGlassSkin.kt 的十个 Render(context)；界面、布局、动效、绘图、背景和视觉输入反馈可以全部重写。不要修改 PowerCalApp.kt、MainActivity.kt、core/、ai/ 或 reminders/。可以把同一皮肤拆到 skins 下更多 Kotlin 文件中，只需让注册类引用它们。

最后运行验收命令，并启动 APK 测试。需要恢复原生时，把 ActiveVisualSkin.kt 的 PROFILE 设回 native 再编译。当前安装时默认就是 native。

### 可选导入设计工程

运行：

    python tools/skin-studio/skin_studio.py serve --port 8765

浏览器进入 http://127.0.0.1:8765；编辑布局槽、颜色、图片、动画，保存 .pcstudio.json。

再用：

    python tools/skin-studio/new_compiled_skin.py aurora-glass --activate --design "C:\skin\design.pcstudio.json"

生成的 AuroraGlassDESIGN_BRIEF.md 列出每个页面、槽位、区域和动效预期；它是实现任务书，不是已实现的 Compose 排版。真正改写 Kotlin 后才算实现。

## 3. 全部十个视觉入口

以下均在 package dev.powerfulcalendar.ui.v2，生成器会给出类型正确的 Kotlin 模板。

| 类别 | Renderer | 使用的 context | 主要职责 |
| --- | --- | --- | --- |
| 时间轴 | CompiledTimelineSkin | TimelineSkinContext | 时间轴绘制、事件点击/移动、视图 camera、Memo/待办/新建导航 |
| 月历 | MonthVisualRenderer | MonthVisualContext | 日期切换、日程选择、打开或创建草稿 |
| Memo | MemoVisualRenderer | MemoVisualContext | 任务完成状态、重复 occurrence 及安全勾选 |
| AI | AiVisualRenderer | AiVisualContext | 聊天、附件、候选提案、批量操作入口 |
| 设置 | SettingsVisualRenderer | SettingsVisualContext | 皮肤、ICS、备份、权限、诊断、设置导航 |
| 草稿卡组 | DeckVisualRenderer | DeckVisualContext | 草稿文本、转型、提交、丢弃、冲突、恢复、AI 锁 |
| 导航 | NavigationVisualRenderer | NavigationVisualContext | 底部全局页面标签及路由回调 |
| 弹窗 | OverlayVisualRenderer | OverlayVisualContext | 所有 Compose AlertDialog + Deck 窗口装饰 |
| 日期时间选择器 | PickerVisualRenderer | PickerVisualContext | Android 日期/时间选择 UI 和返回值 |
| 根容器及提示 | ChromeVisualRenderer | ChromeVisualContext | 整体背景、状态栏/导航栏 inset、短暂消息显示 |

所有 context 提供 Native() 原生回退。只有 Picker 的 Render(context) 是普通 Kotlin 方法，其余 Render(context) 都是 @Composable。时间轴的 ID 属性叫 rendererId，其他叫 id。

### Renderer 如何调用功能接口

不要在皮肤里实现“存入日历”或“呼叫 AI”的新函数。始终使用对应 context.on... 回调。例如时间轴：

    class AuroraTimeline : CompiledTimelineSkin {
        override val rendererId = "aurora/timeline"

        @Composable override fun Render(context: TimelineSkinContext) {
            Column {
                Row {
                    Button(onClick = context.onToday) { Text("今日") }
                    Button(onClick = context.onOpenMemo) { Text("Memo") }
                    Button(onClick = context.onOpenDeck) { Text("待办") }
                }
                Box(Modifier.weight(1f)) { context.Native() }
            }
        }
    }

这里先保留原生精确事件画布。之后可以完全替换为 Canvas 与自定义手势，但必须独立验证时间投影与安全操作。

### 自定义弹窗

OverlayVisualContext.type 为 ALERT 或 WINDOW。ALERT 可取得 title、text、icon、confirmButton、dismissButton、onDismissRequest、properties；用新的 Compose Dialog / Surface 排版这些宿主 slot。WINDOW 可取得 content 和 DialogProperties 来替换全屏对话框。Host 不向皮肤传递一个可以随意提交事件的对象。示例位于 skins/StudioExampleOverlay.kt。系统权限窗、文件选择器属于 Android OS，本系统不能重绘它们。

### 自定义日期选择器

PickerVisualContext 提供 kind、initialDate、initialHour、initialMinute、zone、androidContext、onDate、onTime；Render 是非 Compose 函数，可使用原生 Dialog，也可由开发者创建 ComposeView。默认调用 Native() 就是之前 Android 的时间/日期选择器。onDate/onTime 只有确认后调用，不能在绘制时自动提交。

### 自定义根容器

ChromeType.ROOT 可调用 context.Body(modifier)，渲染宿主内容并定义总背景、窗口 insets、局部 Compose MaterialTheme；ChromeType.NOTICE 提供 message 和 modifier，允许重绘轻提示，同时要保留 TalkBack liveRegion。示例位于 skins/StudioExampleChrome.kt。

### 设置的敏感信息

SettingsVisualContext 支持 context.ProtectedProfile() 与 context.ProtectedAiCredentials()，你可以把它们放在自定义页面的任意位置。由宿主原生控件读写用户配置与密钥，context 不提供 apiKey 字符串。它还提供 context.Native() 作为完整设置页回退。不要在 Skin Kotlin 使用 SharedPreferences、AiSettingsStore、文件、网络或反射读取凭证。

### 草稿生命周期

DeckVisualContext.state 是只读 DeckController.DeckState 快照，context.busy 是是否可编辑；onEdit 为一次新的 DraftForm 视图状态，onBrowse、onCommit、onDiscard、onUndoDiscard、onConfirmConflict、onConfirmRestore 等通过唯一 DeckController 的异步事务串行。宿主在输入后异步暂存并在关闭前 flush，失败不得离开。不能绕过宿主直接发版。示例含可全替换的 Draft Atelier 和完整原生高级编辑器回退。

## 4. 任意重构时都不可破坏的功能语义

- 活动真实几何：区间必须用 context.yOf(startMillis,heightPx) / context.yOf(endMillis,heightPx) 投影；触摸热区和视觉元素不应伪造最短活动时间。ALARM、DEADLINE 是瞬时点，日期-only 截止保持日期精度。
- 时间归属：跨时区、DST、重复实例和事件 ID 必须保持宿主算出的状态；Memo 的 onToggle 直接交原始 TodoEntry 让宿主比较 revision/occurrenceKey，不能自己写数据库。
- AI 权限：聊天不等于发布。AI 方案在 onApplyProposals 后仍要由宿主按快照核验；批量发布只会对用户明确授权范围执行。
- 草稿可靠性：操作序列化、保存失败可重试、强制关闭不报假成功；冲突对话框必须呈现并保留被批准的确切版本。
- UX 基线：明暗模式、横竖屏、IME、TalkBack、大字号、系统返回、减少动效、低内存都必须可用；任何装饰/动画不能妨碍核心操作。
- 安全防线：Gradle verifySkinSourceBoundary 禁止 skins 目录直接调用 PowerStore、DeckController、AiClient、AiSettingsStore、ReminderScheduler、文件和网络等服务。这是静态门禁而非恶意代码沙箱，生产发布仍须审查源码。

## 5. 验证与完整交付

先跑：

    python -m unittest discover -s tools/skin-studio/tests -v
    node --check tools/skin-studio/app.js
    .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain

再用 Android Studio 真 Compose Preview 或手机 APK 验证空/密集日程、1 分钟活动、日期-only 截止、DST/重复实例、手势与 camera、Memo 勾选、AI 未发布/锁定、草稿冲突/撤销/持久化、备份/恢复、权限拒绝、横屏键盘、TalkBack 与减少动效。

浏览器 Mock 不等于 Android 真实像素预览，代码通过编译也不等于新皮肤已通过真机视觉/无障碍回归。交付应包含 Git commit、完整皮肤源代码、浅暗色 Android 截图、交互测试记录、构建日志、原生回退方法。

## 6. 给 Agent 的任务边界

可以修改：app/src/main/java/dev/powerfulcalendar/ui/v2/skins 目录下生成的皮肤文件和该皮肤专用 UI 资源。

不要修改：PowerCalApp.kt、MainActivity.kt、core、ai、reminders 或持久化。若目前的 context 没有某个合法宿主功能入口，单独提出 VisualContext 升级并先完成安全接口/测试，不能把业务实现复制进皮肤。

完成条件：十个入口均有定义、全部关键操作可用、减动效和读屏有退化、核心几何与授权不变、编译与测试通过、设备端验证有证据。结束前切回 native 时必须能不损伤用户数据地恢复。
