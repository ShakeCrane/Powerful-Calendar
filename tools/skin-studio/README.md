## Runtime UI packages (.pcskin v2)

**v2 is implemented.** An upgraded PowerCal can import a .pcskin file and interpret its
bounded UI component tree, action bindings, enter animations and page transitions without
rebuilding the APK. Ready sample: examples/Nebula-Flux-v2.pcskin.

Build a new pack by editing examples/nebula-flux-manifest.json and
examples/nebula-flux-ui.json, then run from repository root:

    python tools/skin-studio/skin_studio.py build-v2 --manifest tools/skin-studio/examples/nebula-flux-manifest.json --ui tools/skin-studio/examples/nebula-flux-ui.json --out my-skin.pcskin
    python tools/skin-studio/skin_studio.py validate my-skin.pcskin

Full v2 scene/actions/motion contract: docs/SKIN_PACK_V2.md.
The web-based visual editor remains a v1 theme + design-planning editor; it does
not yet provide visual drag-and-drop editing of v2 node trees.

## Self-contained programmable packages (.pcskin v3)

The Studio now uses one v3 project in simple and advanced mode. Simple mode edits declared material
slots; advanced mode edits layers, position metadata and complete HTML/CSS/JS with a mocked typed
host bridge. Imported v3 packages reopen as runnable source and can be exported without rebuilding
the APK. See `docs/SKIN_PACK_V3.md`.

    python tools/skin-studio/skin_studio.py build-v3 --manifest tools/skin-studio/examples/v3/manifest.json --source-dir tools/skin-studio/examples/v3/source --out PowerCal-Web-v3.pcskin
    python tools/skin-studio/skin_studio.py validate PowerCal-Web-v3.pcskin

# PowerCal Skin Studio — 使用与开发

2026-10-21。分为两个工具：本地网页**设计编辑器**与**完整 Android Compose 皮肤代码生成器**。

## A. 可视化设计与静态主题 ZIP

从仓库根目录：

    python tools/skin-studio/skin_studio.py serve --port 8765

浏览器打开 http://127.0.0.1:8765 。无需安装 pip/npm、无需联网，默认只监听 127.0.0.1，不访问正式日历数据库。

你可以编辑明暗色板、字号比例、圆角、十张 PNG/WebP 固定插图，导出 Android 直接支持的 .pcskin v1；也可以在六页场景中编排槽位顺序、区域、显示与 fade/slide/scale 动效，保存包含完整设计元数据的 .pcstudio.json。内置六个模拟页面，可预览按钮 intent，但它们不会修改正式日历，且不是 Android 像素级渲染。

检查已有数据包：

    python tools/skin-studio/skin_studio.py validate "C:\skins\my-skin.pcskin"

**注意：** .pcskin 仅支持纯数据的配色、图片和尺寸，没有任意 Compose 页面布局和动态代码。网页 .pcstudio.json 仅是设计文件，不是可执行皮肤安装包。

## B. 任意布局、任意动画的编译式皮肤

要让任何人/Agent 能通过修改文件重构真实 Android UI：

    python tools/skin-studio/new_compiled_skin.py aurora-glass --activate

生成文件：app/src/main/java/dev/powerfulcalendar/ui/v2/skins/AuroraGlassSkin.kt，并在同目录的 ActiveVisualSkin.kt 激活它。模板涵盖十个可替换入口：时间轴、月历、Memo、AI、设置、草稿卡组、导航、全局弹窗、日期时间选择器、APP 根容器及提示。

每个入口在空白模板里都调用原生 fallback context.Native()，因此可逐个改写而不丢失功能。

将设计项目转成给编程 Agent 的实现任务书：

    python tools/skin-studio/new_compiled_skin.py aurora-glass --activate --design "C:\skins\design.pcstudio.json"

额外输出 AuroraGlassDESIGN_BRIEF.md，其中包含每页槽位、顺序、区域、动效提示。生成器**不会宣称自动把 JSON 变成 Compose**；必须自行修改 Render(context) 真正实现。

皮肤源码默认只是 APK 的可信一部分；普通用户无法通过导入 ZIP 在手机上执行 Kotlin。新皮肤需编译和审查。不应修改 PowerCalApp/Core/Ai/Reminders 代码，宿主操作通过 context.on... 回调触发。

完整入门 + API：docs/SKIN_AUTHORING_HANDBOOK.md
已实现正式契约：docs/UI_SKIN_EXTENSION_CONTRACT_V4.md
静态 ZIP ABI：docs/SKIN_PACK_V1.md

## 测试

    python -m unittest discover -s tools/skin-studio/tests -v
    node --check tools/skin-studio/app.js
    .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain

还必须在 Android 真机验证读屏、暗色、减动效、输入法、重复发生、1 分钟活动的真实时间轴高度、草稿提交冲突/恢复和 AI 锁。设计 Mock 和编译成功不代表视觉验收完成。
