# Powerful Calendar — Android 原生日程管理

一款 **Kotlin + Jetpack Compose** 的本地优先日历、事件、提醒和系统闹钟管理 App。

> **研究阶段的新产品方向（2026-10-19）**：参见 [G1–G5 产品架构决策基线](docs/PRODUCT_BASELINE_G1-G5.md) 与 [Temporal Protocol v0 研究草案](docs/PROTOCOL_V0_RESEARCH.md)。本 README 下面的 v0.1 功能与原后续计划是历史 MVP 记录；特别是原“账户级双向同步”规划已被**一次性异步迁入、PowerCal 本地唯一主数据源**取代。新方向尚未开发。

## 当前功能 (MVP v0.1)

- **今日**：查看全天安排、重点数量、事件时间/优先级/备注/地点，编辑和删除。
- **月历**：逐日展示事件标记、切换月份、选择日期查看明细。
- **离线事件管理**：SQLite 存储，不要求登录或云服务器；事件分紧急、重要、普通、低优先级。
- **提醒**：采用 Android AlarmManager 配合通知频道；支持事件前 0/5/10/30/60/1440 分钟提醒或关闭，重启后恢复未来提醒。
- **系统日历**：经 READ_CALENDAR 授权读取 Android 日历未来一年和过去 30 天事件到本地；也可通过系统编辑器添加选定事件。
- **ICS 文件**：通过文件选择器导入/导出 `.ics`；基础 VEVENT 适配 Outlook、Google Calendar、Apple Calendar 等常见导出格式。
- **系统闹钟**：通过 AlarmClock Intent 创建闹钟并进入系统闹钟列表。
- **智能创建**：截图经本地 ML Kit 中文文字识别后，再由简单中文规则解析为事件草稿，由用户确认。也支持直接输入文本。

## Android Studio 编译

1. 使用支持 Android Gradle Plugin 8.13 的 Android Studio，安装 Android SDK 36，JDK 17 或 21。
2. 打开项目根目录 `D:\Project\Powerful-Calendar`。
3. 等待 Gradle 同步，运行 `gradlew.bat :app:assembleDebug`（或通过 Android Studio 运行）。
4. 调试版 APK 路径：`app/build/outputs/apk/debug/app-debug.apk`。
5. Android 8.0 (API 26) 及以上设备可安装。测试通知需给予通知权限。

项目使用 Gradle 8.14.3 / Kotlin 2.2.20 / Compose BOM 2025.10.00，SDK 路径通过不入库的 local.properties 配置。

## 权限与限制

- 通知权限（Android 13+）由用户在创建提醒时授权；未授权会影响提醒的显示。
- Android 12+ 若应用无“精确闹钟”权限，提醒会使用 **近似触发**，可能延迟；与系统闹钟不同。
- Android 没有对所有厂商时钟 App 的现存闹钟进行通用读写的标准接口。本 App 使用系统 Intent 委托创建/打开，不伪称可完整读取现有闹钟。
- **日历读取是手动快照，不是双向实时同步**。从本地删除导入的系统事件不会删除系统事件。点击事件上的外部图标可手动写入系统日历（由系统界面确认）。
- ICS 支持基本事件字段与时区，暂不展开 RRULE、RECURRENCE-ID 或复杂会议邀请；遇到重复日程会提示。ICS 导入以 UID 去重，重复导入会更新对应记录。
- OCR 全程在 Android 端运行，使用 ML Kit 中文识别依赖；首个版本尚未加入在线 OCR/API Key 配置。
- 删除本地事件只删除 App 内部记录，请按需导出 .ics 备份。
- 以上功能尚需真机测试 OEM 后台保活/省电行为。

## 结构

```text
app/src/main/java/dev/powerfulcalendar/
├── MainActivity.kt           # Compose 主界面、权限、文件选择、OCR
├── data/                     # Event / SQLite
├── interop/                  # ICS、Android 日历、中文文本解析
├── reminders/                # 提醒调度、通知、开机重建
└── ui/                       # 月历、事件卡片、编辑表单
```

## 后续计划

1. 单向系统日历刷新升级为真正的账户级双向同步，处理冲突与删除。
2. ICS RFC5545 完整解析、RRULE/EXDATE 展开、会议邀请与日历颜色。
3. 更强自然语言时间解析，识别截图内多个事件，新增可选云端 OCR/LLM API 提供商。
4. 独立的应用内闹钟实体（重复响铃、响铃界面、贪睡），以及提醒历史与开关。
5. Room + ViewModel + 协程数据库 I/O 分层、通知操作、桌面小组件、多日视图、搜索过滤。
