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

已实现：

- 简单模式修改已声明并已绑定的 CSS 材质变量；高级模式编辑完整 HTML/CSS/JS，
  两者直接修改同一份源码，不需要额外点击「应用」。
- 高级源码区可编辑 `project.json` 和受支持扩展名的其他 UTF-8 文件，包括 Clear 的 `web/edit.js` 与 `web/clear-design.css`；可在 `web/` 或 `assets/` 下新增、删除文本文件。资源区可新增、替换和删除任意安全文件名的二进制素材；发现文本源码仍引用该资源时会阻止删除。无法按 UTF-8 解码的既有文件不会在文本框中打开，未编辑字节保留。
- 导入的 v3 `capabilities` 声明在预览和导出时原样保留，不因打开 Studio 自动增加或删除能力；缺失声明按 Android 的空能力处理。Bridge v1 中 host.read 控制状态投影，host.action 控制所有动作与编辑命令；host.edit 是保留兼容的无效元数据，单独声明不授予访问权。实际权限由 Android Host 验证，预览中的 Mock 并不是权限验收。
- 添加文字、图片和安全宿主按钮；修改图层位置、尺寸、不透明度及排序数值，
  拖动预览选框后实际修改包内 CSS，并将图层元数据保存进 `project.json`。
- 上传 PNG、WebP 和字体素材并保留字节；给已有 `data-action` 按钮设置图标素材。
- 模拟常规、空白、密集、重复、AI 锁定、AI 请求失败后手动重发和提案审核场景；用真实 v1 消息结构模拟宿主的
  `state/action`，包括时间轴状态、重复实例身份及 `optionGroups`。
- 重复事件保存预览会显示“模拟确认/模拟取消”；这只是 Studio 的 Mock，Android 实际写入必须经过 Host 的原生确认。重复范围编辑源文件为 `web/edit.js`，与导出包及 Android Clear 资产同源。
- 通过“软件新增平行功能”场景检查通用选项列表是否自动继承新的同级选项。
- 可导入已有的自包含 v3 ZIP，保留 `web/`、`assets/` 中的额外文件；
  导出后仍是 Android 可安装的 `.pcskin`，可再次导入继续编辑。
- `保存编辑工程` 输出含全部源码/图层/素材的 `.pcstudio-v3.json`。
- v1、v2 CLI 打包与运行时仍保留，未更改 Android 皮肤 ZIP 格式。

**画布限制：** Android 在 `fullCanvas=false` 时会用原生 Compose 内容覆盖 Web 层；
为避免误导，Studio 不允许以非全画布模式输出一份声称“预览即使用”的 v3 包。
没有暴露的业务能力（例如完整 Memo/AI 页、正式提交）不能被预览伪装为可用功能。
宿主敏感操作、设置和紧急恢复仍是原生界面。

当前源码界面已提供 `project.json` 原始 JSON 编辑、文本文件增删及任意资源文件新增/替换/删除；不认识的项目字段随有效 JSON 修改保留，非法 JSON 会阻止预览与导出并保留编辑草稿。删除保护按文本源码中的路径/文件名引用扫描，不能替代语义分析。浏览器 Studio 的「HTML 结构树」复用 `analyze-v3` 静态节点清单，可插入、替换、删除、移动闭合 HTML 节点，并通过属性检查器增删改任意静态节点属性；这些操作经本地 v3 分析/编辑 API 与最终包校验，并直接更新预览和导出共用的源文件。JS 生成的运行时节点需通过源码编辑。该结构树不编辑计算后 DOM、CSS 布局或交互语义；完整页面设计、所有属性语义校验及完整 AI 设计工作流仍未完成，不能据此宣称完整 IDE。

**AI/脚本项目操作：** v3 `.pcskin` 可通过机器可读的 `inspect-v3`、`analyze-v3`、
`audit-bridge-v3`、`read-v3`、`edit-v3` 命令检查项目、枚举 HTML 页面及静态节点、
核对静态 `data-action` 名称与 Bridge v1 注册表及 `host.action` 声明、读取任意文本/二进制资源、
按节点结构编辑静态 HTML，并编辑 `project.json` 结构及新增/修改/移动/删除文件，再经同一生产校验器构建到新包路径。`component-attributes` 可由 AI/CLI 以页面 SHA 前置条件修改静态节点属性。SHA 前置条件防止
覆盖检查之后已变化的文件；能力声明变更必须显式确认。该 CLI 补足 AI 操作层，
`build-v3 --json` / `validate --json` 则提供带哈希和计数的结构化回执；构建会原子
创建新的输出路径，拒绝覆盖已存在文件。无 `--json` 时成功结果仍为人读文本。
这不代表浏览器 UI 已具备完整 IDE 编辑能力。具体 JSON 协议与边界见
[`docs/SKIN_STUDIO_AI_AUTOMATION_V1.md`](../../docs/SKIN_STUDIO_AI_AUTOMATION_V1.md)。

```powershell
python tools/skin-studio/skin_studio.py inspect-v3 my-skin.pcskin
python tools/skin-studio/skin_studio.py analyze-v3 my-skin.pcskin
python tools/skin-studio/skin_studio.py audit-bridge-v3 my-skin.pcskin
python tools/skin-studio/skin_studio.py read-v3 --file my-skin.pcskin --path web/app.js
python tools/skin-studio/skin_studio.py edit-v3 --file my-skin.pcskin --operations operations.json --out my-skin-edited.pcskin
python tools/skin-studio/skin_studio.py validate my-skin-edited.pcskin --json
```

`audit-bridge-v3` 只检查静态 HTML 中的 literal `data-action` 名称和所需
`host.action` 声明；不会执行 JS，也不会校验动态生成的 Bridge payload。带有 inline
或 linked script 的皮肤会标记 `requiresSourceReview`。统一的 action/command 字段、类型与
取值约束见 [`docs/contracts/web-skin-bridge-v1.schema.json`](../../docs/contracts/web-skin-bridge-v1.schema.json)，
CLI 会为每个静态 action 返回 schema 引用；JVM 测试把其样例交给实际 Host decoder 验证。
成功 JSON 写 stdout、退出码为 0；
审计失败写结构化 JSON 到 stderr、退出码为 2。它不替代 Android WebView 或设备验收。

仅切换源码/可视化面板或编辑其他源码文件时，原始 `project.json` 保持字节不变；面板模式仅是当前 Studio 视图。确实修改材质或图层后，项目元数据按修改后的模型输出，并保留未知字段。

**同源文件 ≠ Android 像素级完全相同：** 浏览器与 Android WebView 的字体、
屏幕像素比、状态栏安全区域、原生弹窗及触摸行为仍可能不同。实际发布需在 Android
真机/模拟器进行对照验收。

## v3 命令行与测试

```powershell
python tools/skin-studio/skin_studio.py build-v3 --manifest tools/skin-studio/examples/v3/manifest.json --source-dir tools/skin-studio/examples/v3/source --out PowerCal-Web-v3.pcskin --json
python tools/skin-studio/skin_studio.py validate PowerCal-Web-v3.pcskin --json
python -m unittest discover -s tools/skin-studio/tests -p test_ai_skin_workflow_v3.py -v
python -m unittest discover -s tools/skin-studio/tests -v
node --check tools/skin-studio/v3-studio.js
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

可选浏览器端到端脚本：`tools/skin-studio/tests/e2e_v3_browser.cjs`，
需要已安装的 Puppeteer/Chromium；运行前设置 `PUPPETEER_PATH` 指向 Puppeteer
模块所在目录，再设置 `STUDIO_URL` 指向本地运行的 Studio。此测试验证 Bridge
预览、材质、自由图层拖拽、PNG 真正加载以及导出/重导入文件字节一致性。

## 旧版 CLI

v2 声明式皮肤：

```powershell
python tools/skin-studio/skin_studio.py build-v2 --manifest tools/skin-studio/examples/nebula-flux-manifest.json --ui tools/skin-studio/examples/nebula-flux-ui.json --out my-skin.pcskin
```

v1 主题与编译式 Compose 皮肤仍受支持，参见
`docs/SKIN_PACK_V1.md`、`docs/SKIN_PACK_V2.md`、`docs/SKIN_PACK_V3.md` 与
`docs/SKIN_AUTHORING_HANDBOOK.md`。源编译式皮肤必须重新构建 APK，
不可冒充直接可安装的动态 Web v3 包。
