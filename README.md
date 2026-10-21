# Powerful Calendar (PowerCal)

PowerCal 是一个 **Android-first、本地优先、AI 驱动的个人时间信息系统**。它把活动、闹钟时间点和截止日期管理为同一套可验证的时间对象，通过对话、文件、截图或手动操作完成创建、修改、批处理和提醒。用户数据以设备上的 PowerCal 数据库为权威来源。

> Android 应用名称：**PowerCal**。应用内完整名称：**Powerful Calendar**。

## 产品结构

**时间轴**以中央纵向时间尺为中心，左侧展示点状闹钟，右侧展示活动区间和截止标记。上下滚动跨越日期；纵向双指缩放调整时间跨度，横向分量调整右侧事件区域。事件重叠使用稳定分轨和折叠计数。**日历**支持按年、按月跳转与横向切换月份。

**AI 助手**既是普通多轮聊天，也负责理解来自自然语言、ICS、图片 OCR、DOCX、XLSX 等来源的事件信息。结构化操作先生成待处理草稿，重要字段缺失时必须澄清。支持按具体草稿与版本执行经授权的批量发布/丢弃。AI 与日历内核职责分离：模型负责理解和提案，确定性执行层负责事件身份、时区、重复规则、验证、持久化与授权。

**待处理**是唯一的事件草稿编辑与审核中心。创建、替换、删除、类型转换均采用可验证的草稿协议；正式提交使用永久 `eventSeq`、递增修订和幂等提交标识。一个无限或有限的重复系列只有一个永久事件序号，视窗中派生发生实例并用 `occurrenceKey` 区分单次例外。

**提醒**与事件类型独立：可选择普通消息或需要系统精确闹钟能力的闹钟方式。活动默认在开始时发消息；只有日期的截止默认在当日 00:00 发消息，同时继续保留“截止只有日期”的原始精度。可自定义提前分钟数或按发生日期当地零点提醒。实际触发仍受 Android 权限、节电策略和设备环境影响，App 会记录调度与执行状态，不伪称静默失败为成功。

**皮肤**采用可导入的纯数据 `.pcskin` ZIP：调色板、几何尺度、文字比例和 PNG/WebP 素材可替换；原生玻璃皮肤始终可恢复。皮肤不获得脚本、网络、存储或日历操作权限。

## 构建

需要 **JDK 17+、Android SDK 及 Android SDK Build Tools**。SDK 路径写入不纳入版本控制的 `local.properties`。Windows：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --console=plain
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

发布版本号由 `gradle/version.properties` 管理，`applicationId` 固定为 `dev.powerfulcalendar`。正式 Release 需要独立签名证书及验收；**Debug APK 不等于稳定发布版**。

## 开放接口与开发规范

**[文档总索引、规范优先级与历史归档](docs/README.md)**（推荐从这里阅读）。


- [产品语义与时间模型](docs/PRODUCT_BASELINE_G1-G11.md) — 描述产品来源；若与后续实现契约冲突，以明确记录的新决策和实际版本化接口为准。
- [AI Agent、Skills、结构化事件与授权契约 v2](docs/AI_AGENT_PROTOCOL_V2.md)
- [皮肤与 UI 扩展契约 v3：功能/表现分层](docs/UI_SKIN_EXTENSION_CONTRACT_V3.md)
- [Skin Studio 皮肤开发工具与说明](tools/skin-studio/README.md)
- [皮肤与 UI 扩展契约 v2](docs/UI_SKIN_EXTENSION_CONTRACT_V2.md)
- [可直接导入的 .pcskin 格式 v1](docs/SKIN_PACK_V1.md)
- [本地存储与稳定数据契约](docs/STABLE_DATA_CONTRACT_V1.md)
- [APK 版本与签名](docs/RELEASE_VERSIONING.md)

## 项目结构

```text
app/src/main/java/dev/powerfulcalendar/
├── core/          事件、重复实例、草稿、SQLite、备份和事务
├── ai/            LLM 适配器、响应验证、批次范围与执行
├── interop/       ICS、系统日历、文档与图片的输入适配
├── reminders/     期望调度、通知与广播交付验证
├── ui/v2/         Compose 时间轴、日历、AI 助手、待处理与皮肤
└── MainActivity.kt
```

## 已知边界

- PowerCal 不会通过非公开 Android API 读取任意厂商的闹钟记录；系统日历导入是一次性迁移，不是双向实时同步。
- ICS 支持一组经测试的重复规则；复杂的 `RECURRENCE-ID`、`RDATE`、特定 `BYSETPOS` 等仍需进一步适配，不允许静默丢弃约束。
- AI 模型的普通聊天 API **不代表已经有联网搜索能力**；带来源的实时搜索需要额外启用和验证 `web.search` 工具适配器。当前实现尚未开放这一工具。
- 现阶段普通聊天中的结构化意图按钮是候选提案入口，并非完整的自动 Function Calling / 自主执行闭环。
- 皮肤包 v1 支持声明式参数和静态图片。任意 Compose 组件替换要经编译时 UI 扩展接口，不能通过从 ZIP 运行不可信脚本实现。
- 程序与设备都可能失败：高风险批量变更、网络超时、断电/重启、签名升级和厂商后台限制需要专门验证。

### Full visual skin authoring

The complete source-compiled skin SDK now exposes ten swappable Compose/Android presentation
surfaces: timeline, month, Memo, AI, settings, draft deck, navigation, modal windows,
date/time pickers, and application chrome/notices. Business logic remains in the host.

- [Start here: full skin authoring handbook](docs/SKIN_AUTHORING_HANDBOOK.md)
- [Actual renderer contract v4](docs/UI_SKIN_EXTENSION_CONTRACT_V4.md)
- [Local Skin Studio and compiled Kotlin generator](tools/skin-studio/README.md)

Example: python tools/skin-studio/new_compiled_skin.py aurora-glass --activate

The source-compiled skin requires an APK rebuild; portable .pcskin v1 remains a data-only
theme ZIP. Studio design JSON is a blueprint, not a runtime Android layout.
