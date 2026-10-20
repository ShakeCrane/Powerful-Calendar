# PowerCal 0.9 · 2026-10-20 vivo Android 16 RC 真机缺陷清单

**状态**：真机反馈 + 部分只读代码确认，未经全项复现；不得把此文当作全部已修复。
**设备**：vivo V2502DA, Android 16 / API36，PowerCal debug 0.9.0 / 900。
**代码基线**：`release/0.9-data-foundation` (`2c3a0a6`)；本次复审分支 `fix/rc-device-feedback-01`。
**数据安全**：用户手机上有测试草稿／可能已有正式事件。未经备份恢复验证前，不清数据、不卸载、不重签覆盖、不用 ADB 强制修改其数据库。不要把用户截图中敏感日程／课表个人信息提交公开仓库。

## A. 用户反馈与根因/验收

| ID | 优先级 | 现象 | 已核实代码事实／判断 | 下版验收 |
| --- | --- | --- | --- | --- |
| RC-01 | P1 | 空时间轴“大悬浮条”无法消失，出现黑色残影 | `TimelineScreen.kt` 在 `records.isEmpty()` 时永久渲染整块 GlassPanel；黑影原因**未实测确定**，可能与 Dialog scrim / 渲染层有关，不能仅凭截图定罪 | 空态提示可关闭/不遮内容，打开关闭 Deck 100 次无残影，真机抓帧 |
| RC-02 | P1 | AI 入口不明确 | `Page` 当前只有 TIMELINE/MONTH/TOOLS，AI 配置和批量按钮藏 ToolsPage | 主导航固定「时间轴／月历／AI 助手／设置」；右上角「待处理／新建」；批量授权和审阅在 AI 中心，设置不重复列入口 |
| RC-03 | P0 UX | 点卡组外部未能保存退出 | `DraftDeck.kt` 声称外部点击走 `leave() -> controller.flush()`，但真机用户报告无响应；涉及 pointerInput 与 Dialog scrim | 任意非卡片空白单击，最近一个已接受字符必须保存，退出且无错误；IO 错误时保留窗口并提示 |
| RC-04 | P1 | 今日跳当前瞬间、缩放不复位、×1.5 级距不可逆 | `onToday` 只重设 `anchorMillis=now`；`hours` 不复位；当前 ± 乘除1.5，再经 clamp 导致不稳定离散值 | 今日回到本地当天 00–24 h 的可视预设（中心当地正午）、合理缩放；± 采用稳定刻度 4/6/8/12/24/48，不产生随机数 |
| RC-05 | P0 | AI 自然语言创建抛 Android Regex Syntax error near index 27 | `AiModels.kt` `extractJson` 旧 Regex 在 Android ICU 下抛错；不代表服务端必定故障 | 用设备原生 JVM/Android instrumentation 与真实 API 做 fenced/nested JSON 回归，失败不造事件；本分支用无 Regex 的平衡花括号扫描替代 |
| RC-06 | P2 | 圆角容器内又有直角矩形 | `MonthScreen.kt` 固定宽度 336dp 的星期格子嵌在圆角 GlassPanel；截图证实白色矩形边界 | 月历格子与外层统一背景、裁剪与自适应宽度 |
| RC-07 | P1 | 双指上下左右均只改变时间轴，不能调左右距离 | `timelineGestures` 只取Y质心与标量 `calculateZoom`，没有横向尺度状态 | 纵向双指缩时间；横向双指缩左右轴间距/列宽；混合捏合双维响应，拖拽不误识别 |
| RC-08 | P1 | 新建编辑器信息密度乱 | `DeckCardBody` 紧凑/完整层级过多，手机长备注与按钮拥挤 | 新建分层（类型→必要字段→高级选项）；长备注滚动、键盘和底部操作不互相遮挡 |
| RC-09 | P1 | Deck 左右切换无翻页/叠加扇形过渡 | `FanStack` 只绘制静态背景扇形；拖动 `translationX` 只 24px，切卡替换内容；不是真正层叠切换 | 切卡有可感知离场/进场、旋转与叠层重排；reduce-motion 改静态；页序/持久化不受动画影响 |
| RC-10 | P1 | OCR 出来乱创建草稿，未经过 AI | `ocrPicker` → `createDraftFromText` → 本地 `TextParser.parse`，确实未走 `AiClient` | OCR 识别文本先给用户核对，再选择 AI 解析/离线解析，生成的仅为草稿，失败不污染 |
| RC-11 | P0 功能 | 正式事件点进卡组仅能修改，无法删除 | `PowerCalApp.onOpenEvent` 永远 `proposeReplace`；Deck 已能确认 `DELETE` 提案，但用户手工路径缺失 | 正式事件详情有“编辑／删除”明确入口；删除走 `proposeDelete` 待处理 + 二次确认 + 永久序号退役，不把丢弃草稿说成删事件 |
| RC-12 | P1 | 月历编辑完后的退出说明提示“返回时间轴”与来源冲突 | 月历调用 Deck 保留 `page=MONTH`；Deck 返回文案未随来源变化 | 从月历进入 Deck，关闭后返回月历；文案统一「离开待处理」，不强迫去时间轴 |
| RC-13 | P1 | 外部 .ics 无法直接用 PowerCal 打开 | Manifest 仅有 LAUNCHER，没有 VIEW/SEND 和 MIME 过滤；Activity 也没有 inbound SAF Uri 路由 | 文件管理器分享/打开 ICS 能选择 PowerCal，预览、核验、确认后导入，不自动发布 |
| RC-14 | P0 功能 | 课表导入失败／0 导入 11 跳过 | `IcsCodec.parse` 明确不展开 RRULE/RECURRENCE-ID。无法仅凭 toast 判定11个都是重复还是无效；附加文件是 DOCX 表格，并非 ICS。现有原始 ICS 尚未提供 | 支持有限、可验证 RRULE (周次/例外/UNTIL/COUNT) + 可靠 UID；失败逐项解释。DOCX 课表走单独转换器，须有学期第一周日期和校历特殊周次才可生成具体时间 |
| RC-15 | P1 | 还有其他缺陷 | 用户待继续反馈 | 维护真机验收日志、录屏、问题→复现→原因→测试→验证证据，避免口头承诺 |

## B. 实施顺序和责任

1. **P0 功能阻断**：Android AI JSON 正则崩溃、正式事件删除入口、Deck 点击外部的可靠保存退出、课表/ICS 完整导入。任何行为都只能生成待确认草稿或显式的正式提交。
2. **P1 IA/UX（Compose UI 团队/Claude 实际 UI 代码，不是仅替换图片）**：底栏四 Tab + 顶部待处理新建、今日和预设缩放、卡组真实切卡动画、来源中立的退出、OCR/AI 统一中心、双轴几何、编辑密度。需保留现有领域对象协议。
3. **P2 皮肤**：月历装饰矩形、玻璃／阴影／残影；注意残影需先排除 Android 绘制泄漏，再谈皮肤。

DSH 仍负责持久化、导入语义/UID、AI 原子授权、提醒调度、时间精度和删除提交规则；Compose 负责导航、交互、输入验证和动画。不要引入第二个状态机、第二份数据库、或跳过 Draft 的快速直写通道。

## C. 课表证据与转换注意

用户提供 `我的课表.docx`，经表格结构检查有 14 行/9 列，其中每周约 11 个授课时段，部分同一课程有多个时段，课程记录标称 **第 1–17 周**。这是 DOCX，不是可直接导入的 ICS。**必须确认第1周的周一实际日期**及停课/调整周次，才能将这些周次正确转成具体日期；不可凭 2026/10/20 打印日期推算开学周。

如果先生成最兼容当前导入器的 ICS：每个真实授课日期一个 VEVENT，唯一 UID、TZID=Asia/Shanghai，精确起止，杜绝仅用一条 RRULE 导致导入一周后消失。但正常产品仍应开发有限 RRULE 展开或原生课表批量导入，而非永远依赖导出绕过。

## D. 发布与真机安全

- 当前安装于 Android 16 的是 Debug 0.9.0/900，正式签名尚未配置；不能混称稳定 Release。
- 新 APK 安装前应在手机通过界面导出并验证 Envelope 备份，且保留旧 APK 及签名锚点。不可清除／卸载数据来省测试时间。
- 应用的真实通知权限现已获得，闹钟触发与重启恢复仍未验收。
- Android 设备上可能有个人日程，抓取 logcat、文件、备份时按最小范围收集并避免打印私密文本/API key。
