# PowerCal Skin Studio — 使用说明

**状态：本地可视化设计工具 + .pcskin v1 打包器 + 编译式时间轴 Renderer Adapter。**

## 启动

在仓库根目录运行：

    python tools/skin-studio/skin_studio.py serve --port 8765

浏览器打开 http://127.0.0.1:8765 。仅绑定 127.0.0.1，无需 pip/npm/账户或网络服务。Ctrl+C 退出。Studio 不访问生产日历数据库。

现有 .pcskin 校验：

    python tools/skin-studio/skin_studio.py validate "C:\skins\my-skin.pcskin"

## 使用流程

1. Android PowerCal 设置 → 皮肤 → 导出原生皮肤，然后在 Studio 选择「导入 .pcskin」。
2. 选择时间轴/月历/Memo/AI/设置/草稿卡组页面，设计组件位置、区域（background/header/content/overlay/footer）和顺序。必要的语义/功能槽不可隐藏。
3. 切换明/暗模式配置颜色、卡片圆角、线宽、字号；按槽位上传 PNG/WebP 图。所有文件随项目保存在本地。
4. 给各组件指定无/淡入/平移/缩放动效（0–600ms）。勾选减少动效即可预览静态退化。
5. 点击结构预览中的按钮观察 Intent 标识。此时不会提交事件、调用 AI 或修改资料。
6. 「保存设计项目」导出 .pcstudio.json，保存 v1 颜色/素材以及编译式皮肤的布局/动效方案。此文件不可直接装进 Android。
7. 「检查皮肤包」对 ZIP ABI、安全限制、图片尺寸、素材声明进行检查。
8. 「导出 .pcskin」生成**当前 Android 支持**的 v1 数据包；仅包含配色、布局数值、字号与图片，不含完整页面排版和动效。把该文件导入 Android 测试。

网页模拟仅用于创作与检查结构，**不是 Compose 真实像素预览**。不要根据网页视觉直接声称完成 Android 适配。

## Compose 任意布局入口

文件：app/src/main/java/dev/powerfulcalendar/ui/v2/CompiledSkinTimeline.kt

目前时间轴已连接 SkinnableTimelineScreen，它默认直接使用原始 TimelineScreen，故默认皮肤行为不变。开发者可在随 APK 编译、经人工审核的 Kotlin 源码中实现 CompiledTimelineSkin.Render(context)，用任意 Compose Row/Column/Canvas/Animation 绘制新页面，也可先调用 context.Native() 作为可靠的回退。

示例 Kotlin：

    class MyTimeline : CompiledTimelineSkin {
        override val rendererId = "my-timeline-v1"

        @Composable
        override fun Render(context: TimelineSkinContext) {
            context.Native() // 替换为自己的 Compose 页面
        }
    }

    // 可信应用启动代码中注册，而非从外部 ZIP 加载：
    CompiledSkinRuntime.registerTimeline(MyTimeline())

在 Android Studio 预览 SkinStudioNativeLightPreview 和 SkinStudioNativeDarkPreview，使用真实 Compose 和 ALARM/ACTIVITY/DEADLINE 示例数据；网页结构预览不取代这两个原生预览。

重要：CompiledTimelineSkin 使用只读数据和宿主提供的回调。不能私自重写日历数据库、AI 草稿发布、闹钟调度流程。context.yOf(instantMillis,heightPx) 使用唯一时间轴坐标映射；活动区间的上/下边应严格等于开始/结束时刻映射，触摸目标与短矩形的真实几何独立。

skinMotion() 尊重系统减少动效；renderer 自定义动画也必须遵从 reduced-motion 设置，并不得用动画改变事件时间位置的真实性。

## 当前状态和边界

| 类型 | 自定义范围 | Android 直接导入 | 状态 |
| --- | --- | --- | --- |
| .pcskin v1 | 颜色、图片、字号、圆角、点线宽 | 是 | 已支持 |
| .pcstudio.json | 六页组件顺序、区域、动效设计 | 否 | 可编辑、保存与打开 |
| 编译式 Timeline Renderer | 任意 Compose 时间轴页面及动效 | 否，需编译 APK | 接口已接入 |
| 其他页面编译式 Renderer | 月历、Memo、AI、设置、草稿卡组 | 否 | 设计契约，运行时尚未迁移 |

.pcskin 无法逆向还原 .pcstudio.json 的布局和动效，请保存工程文件。编译式 Renderer 不是从不受信任 ZIP 运行代码的机制。

所有 Android 新渲染器需测试：空/密集事件、重复实例、跨日、DST、闹钟点、活动真实时长、截止点、1 分钟活动、缩放和手势、暗色、0.85/1.2 字号、TalkBack、减少动效、草稿确认/取消和进程重启。

运行测试：

    python -m unittest discover -s tools/skin-studio/tests -v
    node --check tools/skin-studio/app.js
    .\gradlew.bat :app:testDebugUnitTest

素材必须拥有合法授权。SHA-256 只校验完整性，不校验作者身份。该工具不加载第三方脚本/字体、不提供远程下载或动态 Dex 执行接口。
