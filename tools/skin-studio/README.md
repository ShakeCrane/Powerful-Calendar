# PowerCal Skin Studio — v3 编辑器

## 当前主入口：可视化 Web 皮肤编辑

从唯一的 PowerCal 主仓库执行：

```powershell
python tools/skin-studio/skin_studio.py serve --port 8765
```

然后访问 http://127.0.0.1:8765 。

**默认启动：** Studio 从 APK 内置的 powercal-clear-default.pcskin 逐字节读取完整的 Clear 候选皮肤包，再自动复制为受保护 ID 之外的自定义皮肤，普通用户不用手写 HTML/CSS/JS 即可修改背景、图标及装饰，并保留包内底层源码。当前候选仍处于 Skin-First 架构迁移阶段，尚不是出厂已激活的完整 Web 产品界面；功能覆盖与迁移门槛见 docs/SKIN_FIRST_MIGRATION_STATUS_2026-10-22.md。

现在首页是 v3 编辑器，旧 v1 的概念场景和 `app.js` 保留作兼容与测试，但不再参与 v3 页面编辑。

**单一真源：** Studio 内正在编辑的 `web/index.html`、`web/style.css`、
`web/app.js` 和每一项 `assets/` 都是导出包使用的原始字节。浏览器预览先执行与导出
一致的 v3 校验，再通过一个本地、临时的只读预览地址原样加载这些文件；
不再把源码替换成 `srcdoc` 的另一份版本。

完成 Debug APK 构建后，可运行下面的检查确认规范源码和单独 Manifest 所生成的包，
仓库中的 APK 皮肤资源，以及 APK ZIP 内实际打包的资源逐字节一致：

```powershell
python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk
```

`--verify` 单独使用时会检查规范源码到仓库 APK 资源；`--apk` 必须与 `--verify`
同用，并且只读检查已生成的 APK，不会重写它。

已实现：

- 简单模式修改已声明并已绑定的 CSS 材质变量；高级模式编辑完整 HTML/CSS/JS，
  两者直接修改同一份源码，不需要额外点击「应用」。
- 高级源码区可编辑 `project.json` 和受支持扩展名的其他 UTF-8 文件，包括 Clear 的 `web/edit.js` 与 `web/clear-design.css`；可在 `web/` 或 `assets/` 下新增、删除文本文件。资源区可新增、替换和删除任意安全文件名的二进制素材；发现文本源码仍引用该资源时会阻止删除。无法按 UTF-8 解码的既有文件不会在文本框中打开，未编辑字节保留。
- 浏览器 Studio 明示 `host.read`、`host.action` 和 `host.edit` 三项 Host 能力请求。导入、预览、源码编辑和导出不会自动修改声明；只有用户明确操作复选框才会更新 Manifest。缺失声明按 Android 的空能力处理。Bridge v1 中 host.read 控制状态投影，host.action 控制所有动作与编辑命令；host.edit 是保留兼容的无效元数据，单独声明不授予访问权。实际权限由 Android Host 验证，预览中的 Mock 并不是权限验收。
- 添加文字、图片和安全宿主按钮；修改图层位置、尺寸、不透明度及排序数值，
  拖动预览选框后实际修改包内 CSS，并将图层元数据保存进 `project.json`。
- 上传 PNG、WebP 和字体素材并保留字节；给已有 `data-action` 按钮设置图标素材。
- 模拟常规、空白、密集、重复、AI 锁定、AI 请求失败后手动重发和提案审核场景；用真实 v1 消息结构模拟宿主的
  `state/action`，包括时间轴状态、重复实例身份及 `optionGroups`。
- Clear 设置页会显示 Host 投影的已安装皮肤目录，并通过 `selectSkin` 请求 Host 确认；Preview Mock 允许模拟确认或取消，但不会启用 Android 皮肤或写入设备数据。
- Clear 的 AI/隐私入口通过既有 `nativeSettings` 路由打开 Host 保护的端点、模型、凭证和上下文表单；Preview Mock 只记录该路由，不会读取密钥、启动 Android 设置或保存真实偏好。
- 重复事件保存预览会显示“模拟确认/模拟取消”；这只是 Studio 的 Mock，Android 实际写入必须经过 Host 的原生确认。重复范围编辑源文件为 `web/edit.js`，与导出包及 Android Clear 资产同源。
- 通过“软件新增平行功能”场景检查通用选项列表是否自动继承新的同级选项。
- 可导入已有的自包含 v3 ZIP，保留 `web/`、`assets/` 中的额外文件；
  导出后仍是 Android 可安装的 `.pcskin`，可再次导入继续编辑。
- Android 设置中的第三方皮肤“导出”会通过系统文件选择器保存已安装的原始
  `.pcskin` 包；在 Studio 导入后可继续编辑和导出。导出的 Manifest ID 保持原值，
  Android 为防止误覆盖会拒绝安装同 ID 的现存皮肤；要在同一设备保留旧版时，
  请先在 Studio 派生新 ID。v1/v2 皮肤仍可由 Android 安装和导出，当前浏览器编辑器
  面向 v3，尚无 v1/v2 的 Studio 再编辑流程。
- `保存编辑工程` 输出含全部源码/图层/素材的 `.pcstudio-v3.json`。
- v1、v2 CLI 打包与运行时仍保留，未更改 Android 皮肤 ZIP 格式。

**画布限制：** Studio 保留导入包声明的 `fullCanvas` 值，也允许编辑该值；
Android 在 `fullCanvas=false` 时会用原生 Compose 内容覆盖 Web 层。浏览器 Studio
预览不包含这层 Android Compose 内容，因此非全画布包的浏览器截图不能代表设备上的完整画面。
没有暴露的业务能力（例如完整 Memo/AI 页、正式提交）不能被预览伪装为可用功能。
宿主敏感操作、设置和紧急恢复仍是原生界面。

当前源码界面已提供 `project.json` 原始 JSON 编辑、文本文件增删及任意资源文件新增/替换/删除；不认识的项目字段随有效 JSON 修改保留，非法 JSON 会阻止预览与导出并保留编辑草稿。删除保护按文本源码中的路径/文件名引用扫描，不能替代语义分析。完整组件树模型、页面可视化重组、所有结构属性的语义校验及完整 AI 设计工作流仍未完成，不能据此宣称完整 IDE。

**AI/脚本项目操作：** v3 `.pcskin` 可通过机器可读的 `inspect-v3`、`read-v3`、
`edit-v3` 命令检查项目、读取任意文本/二进制资源、编辑 `project.json` 结构及
新增/修改/移动/删除文件，再经同一生产校验器构建到新包路径。SHA 前置条件防止
覆盖检查之后已变化的文件；能力声明变更必须显式确认。该 CLI 补足 AI 操作层，
`build-v3 --json` / `validate --json` 则提供带哈希和计数的结构化回执；构建会原子
创建新的输出路径，拒绝覆盖已存在文件。无 `--json` 时成功结果仍为人读文本。
这不代表浏览器 UI 已具备完整 IDE 编辑能力。具体 JSON 协议与边界见
[`docs/SKIN_STUDIO_AI_AUTOMATION_V1.md`](../../docs/SKIN_STUDIO_AI_AUTOMATION_V1.md)。

```powershell
python tools/skin-studio/skin_studio.py inspect-v3 my-skin.pcskin
python tools/skin-studio/skin_studio.py read-v3 --file my-skin.pcskin --path web/app.js
python tools/skin-studio/skin_studio.py edit-v3 --file my-skin.pcskin --operations operations.json --out my-skin-edited.pcskin
python tools/skin-studio/skin_studio.py validate my-skin-edited.pcskin --json
```

仅切换源码/可视化面板或编辑其他源码文件时，原始 `project.json` 保持字节不变；面板模式仅是当前 Studio 视图。确实修改材质或图层后，项目元数据按修改后的模型输出，并保留未知字段。

**同源文件 ≠ Android 像素级完全相同：** 浏览器与 Android WebView 的字体、
屏幕像素比、状态栏安全区域、原生弹窗及触摸行为仍可能不同。实际发布需在 Android
真机/模拟器进行对照验收。

## v3 命令行与测试

```powershell
python tools/skin-studio/skin_studio.py build-v3 --manifest tools/skin-studio/examples/v3/manifest.json --source-dir tools/skin-studio/examples/v3/source --out PowerCal-Web-v3.pcskin --json
python tools/skin-studio/skin_studio.py validate PowerCal-Web-v3.pcskin --json
python -m unittest discover -s tools/skin-studio/tests -v
node --check tools/skin-studio/v3-studio.js
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

可选浏览器端到端脚本：`tools/skin-studio/tests/e2e_v3_browser.cjs`，
需要已安装的 Puppeteer/Chromium；运行前设置 `PUPPETEER_PATH` 指向 Puppeteer
模块所在目录，再设置 `STUDIO_URL` 指向本地运行的 Studio。此测试验证 Bridge
预览、材质、自由图层拖拽、PNG 真正加载以及导出/重导入文件字节一致性。

草稿卡组 Bridge 的独立预览回归为
`tools/skin-studio/tests/e2e_v3_deck_search.cjs`；它使用 Studio seeded Mock 验证
草稿/正式事件搜索、替换提案、卡片选择、导航及 transient 新建编辑器的 UI。
运行前同样启动本地 Studio，并设置 `PUPPETEER_PATH` 和 `STUDIO_URL`。该 Preview
Mock 不写 Android 数据，也不能代替 Android WebView 或实际 Store 验收。

## 旧版 CLI

v2 声明式皮肤：

```powershell
python tools/skin-studio/skin_studio.py build-v2 --manifest tools/skin-studio/examples/nebula-flux-manifest.json --ui tools/skin-studio/examples/nebula-flux-ui.json --out my-skin.pcskin
```

v1 主题与编译式 Compose 皮肤仍受支持，参见
`docs/SKIN_PACK_V1.md`、`docs/SKIN_PACK_V2.md`、`docs/SKIN_PACK_V3.md` 与
`docs/SKIN_AUTHORING_HANDBOOK.md`。源编译式皮肤必须重新构建 APK，
不可冒充直接可安装的动态 Web v3 包。
