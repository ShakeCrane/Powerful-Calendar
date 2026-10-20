# PowerCal — Claude 皮肤素材替换接口 v1

**状态：已接线的表现层扩展点；不是运行时下载皮肤市场。**
**分支：** `feat/skin-assets-api`（从 DSH `dsh/p0-interfaces` / `e823a9a` 派生）
**用途：** Claude 后续制作皮肤时只需提交自己的本地图片、主题色/字体配置；不应修改事件协议、AI 调度、SQL 或闹钟。

## 1. 公共 Kotlin 接口

文件：`app/src/main/java/dev/powerfulcalendar/ui/v2/SkinAssets.kt`

| 接口 | 作用 |
| --- | --- |
| `SkinSlot` | 五个固定、可枚举的素材槽 |
| `SkinArt(@DrawableRes drawableId, alpha)` | 引用 Android 编译后 drawable 资源，支持透明度 (0–1) |
| `SkinArtwork` | 一组素材槽到 drawable 的映射 |
| `PowerCalSkinPack` | 一份皮肤：明/暗素材、可选的 Material3 ColorScheme、AxisPalette 和 Typography |
| `PowerCalSkinRegistry.activate(pack)` | App 当前进程切换皮肤；Compose 自动重组 |
| `PowerCalSkinRegistry.reset()` | 恢复 PowerCal Glass 原设计 |
| `PowerCalTheme(skin = ...)` | 主题应用入口，暗色默认仍跟随系统 |
| `SkinImage`, `skinBackdrop`, `skinArt`, `skinPainter` | UI 的无业务依赖素材绘制原语 |

### 五个素材槽

| Slot | 已接入位置 | 建议内容 | 缺省行为 |
| --- | --- | --- | --- |
| `TIMELINE_BACKGROUND` | TimelineScreen 最外层渐变后 | 柔和材质纹理、微光 | 原始轴背景渐变 |
| `DECK_BACKDROP` | DraftDeck Dialog 的黑色半透明遮罩后 | 卡组背景的低对比装饰 | 原始半透明遮罩 |
| `MONTH_BACKGROUND` | MonthScreen 列表背景 | 轻微纹理/装饰 | 保持原月历 |
| `COMPLETION_EMBLEM` | 草稿全部完成的反馈标识 | 透明单体插画 | 原始 Check 图标 |
| `BRAND_MARK` | Timeline 顶部 PowerCal 标题旁 | 正方形透明标志 | 无图，保留 PowerCal 文字 |

**所有槽均可为 null**。没有素材时不改变现有 UI，不需要占位 PNG。素材不含任何点击、导航、任务执行或 AI 授权语义。

## 2. Claude 的接入示例

将生成的 PNG/WebP 或合法 XML vector 置于 Android 资源目录（例如 `app/src/main/res/drawable-nodpi/`），文件名采用 `skin_claude_*` 小写蛇形命名，**禁止导入未经检查的脚本或远程 URL**。

然后创建/修改一个单独的皮肤注册文件（仅示例，下面资源文件要先由 Claude 实际提供）：

```kotlin
package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.R

val ClaudeSkin = PowerCalSkinPack(
    key = "claude-v1",
    lightArtwork = SkinArtwork(
        timelineBackground = SkinArt(R.drawable.skin_claude_timeline_light, alpha = 0.20f),
        deckBackdrop = SkinArt(R.drawable.skin_claude_deck_light, alpha = 0.15f),
        completionEmblem = SkinArt(R.drawable.skin_claude_complete_light),
        brandMark = SkinArt(R.drawable.skin_claude_brand_light)
    ),
    darkArtwork = SkinArtwork(
        timelineBackground = SkinArt(R.drawable.skin_claude_timeline_dark, alpha = 0.18f),
        deckBackdrop = SkinArt(R.drawable.skin_claude_deck_dark, alpha = 0.14f),
        completionEmblem = SkinArt(R.drawable.skin_claude_complete_dark),
        brandMark = SkinArt(R.drawable.skin_claude_brand_dark)
    )
)
```

激活方式：应用入口在设置选定时调用 `PowerCalSkinRegistry.activate(ClaudeSkin)`，或在当前阶段的 `MainActivity.onCreate` 的 `setContent` 之前选择默认皮肤。后续要永久保存用户选择，可单独添加设置持久化；**v1 尚未提供皮肤选择器或跨进程持久化**。

如果 Claude 暂时只交一个主题，也可以只提供一部分槽；未指定的槽保留原 UI。明/暗主题需要分别配置，避免把亮色纹理直接复制到暗色背景导致刺眼。

## 3. 素材制作约束

- 背景为装饰层，文字、NOW、时间刻度、闹钟蓝、活动绿、截止琥珀等语义信息必须覆盖在上层，始终可读。
- 对时间轴背景优先使用低对比、无文字、无伪时间刻度的纹理。不要把网格/轴线画在 PNG 里；真实时间几何仍由 Canvas 绘制。
- 背景推荐初始 alpha 0.10–0.25；最终以真机和可读性测试调整。对图标/插画可使用 1.0。
- 背景避免超大无压缩位图，宜提供尺寸适配 Android 的 WebP/矢量。绘制时用 `ContentScale.Crop`；在不同宽高比下裁剪不得影响意义。
- 品牌标志及完成插画使用透明背景；可针对浅色/深色各交一份。
- 不在 drawable 中编码任何状态文字、事件序号、可编辑内容；保持多语言、字体缩放与 TalkBack 的语义可访问。
- 不移除基础图标、键盘、返回手势、无障碍语义，也不通过装饰层消费任何点击。
- 动效/玻璃效果仍遵循系统减少动画、低 RAM 降级。v1 图像接口仅负责静态素材；不保证真实背景采样模糊、shader、Lottie 或运行时下载的皮肤。
- 素材版权需可用于本项目，禁止提交未授权的图片或字体。**字体文件不得从聊天中直接分发**。

## 4. 已有主题接口

`PowerTheme.kt` 保留原有 `LightColors/DarkColors`, `AxisPalette`, `PowerTypography`, `GlassShapes` 和 `GlassEffects`。

`PowerCalSkinPack` 可选提供整份浅/深 Material3 `ColorScheme`、浅/深 `AxisPalette` 以及 `Typography`。未提供时自动回退旧主题。

注意：`GlassShapes` 与 `PowerMotion` 目前仍是静态设计常量，不会通过此皮肤包在运行时切换。Claude 可以在自己负责的 UI 皮肤阶段提出独立设计 tokens，但**不能声称当前接口已支持运行时替换所有形状和动画**。

## 5. 验收契约

1. 默认 `PowerCalSkinRegistry.default` 时，原六张 UI 软件渲染样例的主要布局/外观应保持不变。
2. 激活一个只提供单张图片的皮肤后，其余槽自动保持原样，不应留下空白占位。
3. 深/浅素材跟随 `isSystemInDarkTheme()` 自动选择；输入框内容、当前卡片 ID、时间轴相机不重置。
4. 运行时切回 `default`，已有文字与交互不受影响。
5. 在皮肤非默认时，背景层不得遮挡可点击按钮或抢占触控事件。
6. 触控目标、可读性、暗黑对比度、60/120Hz 的渲染表现需真机复测。
7. DSH 的 P0 接口测试、协议与提醒测试继续全部通过；皮肤不得进入 `core/` 或数据库。
8. 不得宣称支持在线第三方皮肤包。当前素材必须随 APK 编译打包。

**本任务只开放和接线素材接口；没有生成或安装任何 Claude 皮肤。**
