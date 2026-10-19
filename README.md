# Powerful Calendar — PowerCal 0.2

**Kotlin + Jetpack Compose** 的本地优先个人时间系统：中央纵向时间轴、三种时间对象、
统一「待处理」草稿卡组（Draft Deck）、单 AI 授权批处理与可验证的提醒调度。

> 产品依据：[G1–G11 产品基线](docs/PRODUCT_BASELINE_G1-G11.md) ·
> [Temporal Protocol v0.3](docs/PROTOCOL_V0_RESEARCH.md) ·
> [P0 现状审计](docs/AUDIT_P0.md) ·
> [v0.2 实施与验证记录](docs/IMPLEMENTATION_V0_2.md)

## 已实现（v0.2）

- **统一时间协议**：`AlarmEvent`（仅开始，左轴）/ `ActivityEvent`（区间，右轴）/
  `DeadlineEvent`（截止点或日期，右轴）。日期级截止保持日期精度，不会变成 23:59 或 00:00。
- **永久事件序号**：`eventSeq` 在首张草稿创建时分配，删除/丢弃后永不复用；
  `draftId` 与 `queueOrder` 独立，同一事件可有多张卡片。
- **整份快照提交**：最后一次合法提交覆盖整个对象；过期卡片必须查看完整差异并显式确认，
  绝不按字段自动合并。提交具备幂等键，重复点击不会重复发布。
- **待处理卡组**：中央玻璃弹窗、扇形背卡、卡内单步/卡外多步滑动、纵向滚动、
  防抖暂存与切页前 flush、勾号发布、叉号丢弃（可撤销）、完成动画与真实统计。
- **中央时间轴**：连续滚动（无日期边界）、真实 NOW 线、4–48 小时手势锚定缩放、
  左右稳定分轨与溢出折叠、长按拖动按缩放吸附并进入同一草稿编辑器、今日跳到当前时刻。
- **单 AI**：可配置 OpenAI 兼容端点/模型/密钥；自然语言 → 严格校验的结构化草稿；
  用户显式授权后由同一 AI 独占卡组批量发布或丢弃，带 sessionId、lockEpoch、范围与幂等提交，
  取消/崩溃/重启后安全解锁，迟到响应被 fencing 拒绝。
- **数据接入**：ICS 与系统日历一次性迁移（幂等、可重启）、旧版 v0.1 只读迁移、
  原生完整备份（不含密钥、AI 授权与 Android 调度句柄；仅恢复到空库）。
- **提醒可靠性**：提交后写入持久化期望调度；广播触发前校验事件序号、修订、
  发生实例与触发时刻；精确闹钟不可用显示 BLOCKED 而非静默降级；重启/时区/权限变化重建调度。

## 编译与测试

1. 安装 Android SDK 36（platforms;android-36、build-tools;36.0.0、platform-tools）与 JDK 17+。
2. 在未入库的 `local.properties` 写入 `sdk.dir=...`。
3. 构建与单元测试：

```bat
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon --console=plain
```

当前结果：`BUILD SUCCESSFUL`，**97 个单元测试，0 失败**（Robolectric 真实 SQLite）。
APK：`app/build/outputs/apk/debug/app-debug.apk`。

## 权限与限制

- **通知**（Android 13+）由用户在工具页授权；未授权时触发会记录为 `BLOCKED`，不会假装已提醒。
- **精确闹钟**（Android 12+）需要用户授权。缺失时精确闹钟显示为受阻并提供设置入口，
  PowerCal 不会把近似闹钟冒充精确闹钟。厂商后台限制无法被本应用绕过，不承诺所有设备 100% 准点。
- 系统日历与 ICS 均为**手动一次性迁移**，不是双向实时同步；删除本地记录不会删除外部记录。
- ICS 导出仅包含活动事件（该格式无法表达"只有开始"或日期级截止）；不展开 RRULE。
- Android 没有读取所有厂商时钟 App 现有闹钟的通用接口，迁移的旧闹钟默认停用等待人工交接。
- AI 密钥仅保存在应用私有存储，不进入备份与日志；上下文共享默认关闭，开启后最多 60 条。

## 结构

```text
app/src/main/java/dev/powerfulcalendar/
├── MainActivity.kt            # 单 Activity 宿主、系统深浅色、动效偏好
├── core/                      # 协议：时间模型、SQLite 存储、卡组交互内核
├── ai/                        # 单 AI 客户端、严格解析、独占批处理
├── data/                      # 旧版只读迁移、v0.1 历史类型
├── interop/                   # ICS、系统日历、离线中文解析
├── reminders/                 # 期望调度、触发校验、通知、重建
└── ui/v2/                     # PowerCal Glass、时间轴、待处理、工具页
```

`ui/CalendarScreens.kt`、`ui/EventEditor.kt` 与 `data/EventStore.kt` 属于 v0.1 历史原型，
保留用于对照，不再进入导航。

## 未验证（需真机）

实际响铃与厂商限制、手势手感（缩放锚定/长按拖动/文本选择优先级）、深浅色运行时切换、
TalkBack 与触控目标实测、进程被杀后的恢复、横屏布局、`uiMode` 变更不重建 Activity 的实际表现。
详见 [实施记录 §5](docs/IMPLEMENTATION_V0_2.md)。
