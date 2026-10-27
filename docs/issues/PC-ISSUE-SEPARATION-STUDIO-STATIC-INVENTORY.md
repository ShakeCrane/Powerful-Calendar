# PC-ISSUE-SEPARATION-STUDIO-STATIC-INVENTORY

- **状态：** `analyze-v3` 静态页面/HTML 节点清单切片 PASS；SEP-15 完整 Studio 与 AI authoring parity 仍为 **NOT TESTED**。
- **日期：** 2026-10-28
- **分支：** `codex/separation-skin-settings`
- **实现提交：** `9ec85b9e3f963aa86603555ab90c17ca194373c8`（CLI、静态索引、回归测试、接口文档及矩阵证据）

## 缺口与根因

v3 CLI 原有 `inspect-v3` 返回 manifest、`project.json` 和路径/哈希清单；`read-v3` 读取单个源文件；`edit-v3` 可基于 SHA 前置条件编辑源文件和项目 JSON。它们没有页面/HTML 节点清单，因此 Agent 需要自行解析整份 HTML 才能定位可见结构、静态控件和相邻源码。

这项缺口属于 Goal C 的机器可调用项目分析接口。它不意味着现有 `.pcskin` 缺少任意源码编辑能力，也不证明完整 Studio IDE 或运行时功能等价已经完成。

## 实现

新增只读命令：

```powershell
python tools/skin-studio/skin_studio.py analyze-v3 path\to\skin.pcskin
```

命令先用生产 v3 解包/校验器验证整个包，再以 Python 标准库 HTML parser 对 `web/` 下 `.html` / `.htm` 页面建立显式源码嵌套索引。UTF-8 JSON 回执提供 package/page SHA-256、标题、CSS/JS 引用、节点数、静态交互数、重复 ID、解析警告，以及每个节点的 tag、属性、source line/column、`id`、`data-component`、父子 node ID 和有界直接文本。节点与总输出均受 20000 个节点上限约束。

错误写入 stderr 并返回 2；成功返回 UTF-8 JSON。该命令只读，不重写 HTML 或 `.pcskin`。接口说明同步到 Skin Studio README、AI 自动化接口文档与 SEP-15 矩阵。

## 明确的分析边界

此结果是 `explicit-source-nesting` 静态索引，不是浏览器计算后的 DOM。命令不执行 JavaScript、不解析 CSS 级联，也不模拟完整 HTML5 tree-repair 规则；JS 模板创建的事件卡、运行时导航等不会作为运行时节点出现。每页都会带此限制警告，节点 ID 只对相同页面源码与顺序有效。Agent 仍需用 `read-v3` 检查脚本和样式，并以包哈希及源文件哈希构造 CAS 编辑操作。

因此本次补上的是页面和静态 HTML 结构的可机器读取能力，**没有**提供任意组件的语义化创建/移动/删除 UI，也没有让动态生成节点成为可视化树编辑器的一部分。

## 测试证据

新增回归覆盖：

- 多页面、标题实体解码、CSS/JS 引用、保序属性、直接文本及父子关系；
- 自定义标签和布尔/交互属性识别，重复 HTML ID 与无法匹配闭合标签的警告；
- JavaScript 不执行时的明确警告；
- 超过 20000 个节点时安全拒绝；
- CLI 成功 JSON、损坏包的 `ok:false` JSON stderr / exit code 2；
- 分析后 `.pcskin` 解包内容逐字节不变。

开发中的第一轮定向运行发现 `directText` 首段文本有多余前导空格；修正归一化逻辑后重跑通过：

| 检查 | 结果 |
| --- | --- |
| `test_project_ops_v3.py` | PASS；10/10 |
| 完整 Skin Studio Python | PASS；53/53，包含本机 loopback HTTP 预览/导出测试 |
| 规范 Clear `.pcskin` 实际分析 | PASS；1 page、88 static HTML nodes、23 static interactive nodes；主容器 ID 可定位 |
| Clear APK 资产同源校验 | PASS；8 files / 379252 bytes，与分析所用 package hash 一致 |
| Chrome E2E / Android WebView / AVD / 实体设备 | NOT RUN；此次只改 Python CLI、测试和文档，未作运行时通过声明 |

规范包分析实际输出：

- `pageCount=1`，`componentCount=88`，`interactiveCount=23`。
- 可定位 `app`、`timelinePage`、`monthPage`、`memoPage`、`aiPage`、`skinSheet`、`chatInput`。
- 输出明确警告：JavaScript 未执行，运行时生成组件不在清单内。
- package SHA-256：`7B3FBB7C402E5C776894497D96BA1CEEE7189E8ACB48EEE70ECE2E9C19CCF81A`。

命令：

```powershell
python -m unittest discover -s tools/skin-studio/tests -p test_project_ops_v3.py -v
python -m unittest discover -s tools/skin-studio/tests -v
python tools/skin-studio/skin_studio.py analyze-v3 app/src/main/assets/powercal-clear-default.pcskin
python tools/skin-studio/build_default_clear.py --verify
```

完整套件在允许本机 loopback 的执行环境运行；设备清单查询发现没有 `emulator.exe`，ADB daemon 也未能启动。本 issue 不依赖设备测试，但这不能代替后续 Android WebView / AVD / 实机验收。

证据保存在忽略目录 `.local/android-test/reports/studio-analyze-v3-20261028/`：

| 文件 | SHA-256 |
| --- | --- |
| `project-ops-focused.log` | `F257C312EF6BF7202CD40CB091C25F6AA3C0EF489CD390094E647B2867CE52FA` |
| `studio-python-final.log` | `83C1A21E0B7B54E53CF277346C43ECF4D0F9DBEE3AECBE355685724777C25C58` |
| `clear-analysis.json` | `063E8F7A2DE7910D6A5A4F21ABFE6CD3997B1A26EB6A11E3F72FC00D8C680518` |

## 剩余工作

- SEP-15 继续 **NOT TESTED**：浏览器 Studio 仍没有任意组件树可视化重组；静态分析不包含 JavaScript 生成的运行树；完整 AI “分析→修改→预览→构建→设备验收”工作流未闭环。
- 本次不改 Clear 源码、运行时或冻结 Core，不改变 manifest capability，也不触碰并发 `feat/skin-first-separation` 工作树中未提交的 PROGRESS/Clear 资产。
- 下一步可在现有 v3 模型上继续增强运行时 DOM 观察/语义编辑，同时保留原始源码的无损编辑路径；设备验证待 SDK/AVD 可用后再做。

## Git 交付

- 实现提交 `9ec85b9e3f963aa86603555ab90c17ca194373c8` 已生成；本报告和 PROGRESS 将作为独立文档提交，随后推送到 `origin/codex/separation-skin-settings`。
