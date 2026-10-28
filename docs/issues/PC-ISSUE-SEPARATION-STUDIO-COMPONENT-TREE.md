# PC-ISSUE-SEPARATION-STUDIO-COMPONENT-TREE

- **状态：** 浏览器 Studio 静态 HTML 结构编辑切片 `PASS`；SEP-15 / S5 完整 Studio、动态运行时结构编辑和 Native/Skin parity `NOT TESTED`。
- **日期：** 2026-10-28
- **分支：** `codex/separation-skin-settings`
- **修复基线：** `bb478cae97cb33bb98d0140accc9140569109d83`
- **实现提交：** 待本报告随实现提交后回填。

## 缺口与根因

上一个 Issue 给 `edit-v3` 增加了基于 `analyze-v3` 节点 ID 的静态 HTML 增删改移操作，但这些操作只可由 CLI 或脚本调用。浏览器 Studio 没有结构树和相应 HTTP 路由；用户即使在页面中看到真实预览，也仍需切换到源码编辑器手改 HTML 才能定位或重组节点。

这留下了 Goal B 中浏览器结构编辑能力的明确缺口。本 Issue 把现有操作接入浏览器 Studio，不扩展 HTML 解析器的语义范围。

## 实现

- 在右侧编辑栏新增 **HTML 结构树**，列出项目内所有静态 `.html` / `.htm` 页面及显式嵌套节点。每个节点显示标签、ID、`data-component`、简短文本和源代码行号。
- 结构树支持片段前插、后插、作为首/末子节点插入、替换、删除及相对目标移动。危险或无效操作继续由既有 `project_ops_v3` 规则拒绝；删除需确认。
- 新增本地 `POST /api/analyze-v3` 与 `POST /api/edit-components-v3`。两者使用生产 v3 builder 校验项目；编辑 API 复用现有 `edit_package`、节点 ID、页面 SHA-256 前置条件及最终包重建，返回变更文件字节、前后哈希和新分析结果。
- 浏览器只把成功回执中的变更文件写回 `sourceFiles`。源码编辑、实时预览、编辑工程保存、导出及重新导入继续使用同一份源文件字节。
- 直接源码编辑 HTML 时，结构修改会暂时禁用，直到重新分析完成。分析请求有序号校验，防止过期结果覆盖新状态。切换皮肤项目会清除旧节点和移动目标。
- 结构树采用普通嵌套列表与按钮，选择后保留键盘焦点；静态树不执行或枚举 JavaScript 动态 DOM。

## 修改文件

- `tools/skin-studio/skin_studio.py`：本地分析与编辑 HTTP 路由。
- `tools/skin-studio/index.html`、`style-v3.css`、`v3-studio.js`：结构树、操作控件、同源数据更新及竞态/重载处理。
- `tools/skin-studio/tests/test_v3_preview.py`：HTTP 分析、组件编辑回执及过期 SHA 拒绝测试。
- `tools/skin-studio/tests/e2e_v3_source.cjs`：真实 Chrome 的插入、替换、移动、删除、项目切换、预览字节及导入导出往返验证，并生成截图。
- `tools/skin-studio/README.md`、`docs/SKIN_STUDIO_AI_AUTOMATION_V1.md`：同步界面、API 和范围说明。
- `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`：回填 SEP-15 浏览器结构编辑证据，整行仍保持 `NOT TESTED`。

未修改 Android Host、业务 Core、Clear 生产源码、APK 资源、皮肤权限或 Bridge 契约。

## 测试结果

| 检查 | 结果 | 范围与限制 |
|---|---|---|
| `test_v3_preview.py` 定向 HTTP 测试 | `PASS`，4/4 | 新 API 分析/编辑、SHA 过期拒绝，及现有预览路径/包字节回归 |
| Skin Studio Python 全量套件 | `PASS`，59/59 | `python -m unittest discover -s tools/skin-studio/tests -q` |
| Chrome Studio E2E | `PASS`，退出码 0 | 结构树四种操作；项目重载状态；精确预览 HTML 字节；`.pcskin` 导出、重新导入后源码一致。已有 Host 交互仍为非写入 Mock，不是 Android Host 证据 |
| Clear 皮肤包校验 | `PASS` | 8 files / 379252 bytes；SHA-256 `7b3fbb7c402e5c776894497d96ba1ceee7189e8acb48eee70ece2e9c19ccf81a` |
| JS `node --check`、Python `py_compile`、`git diff --check` | `PASS` | 最终源码版本 |
| Android WebView / AVD / 实体设备 | `NOT TESTED` | 本 Issue 是本地 Web Studio 工具改动，不据 Chrome 结果推断 Android 或 OEM 行为 |

完整 Python 套件执行时，沙箱首次禁止本机 loopback socket（Windows `WinError 10013`）。按已授权的本地 Studio 测试流程在允许 loopback 的执行环境重跑后 59/59 通过。第一次失败没有进入任何测试断言。

## 可追溯证据

忽略目录：`.local/android-test/reports/separation-20261028/studio-component-tree/`

| 文件 | SHA-256 |
|---|---|
| `studio-python-full.log` | `C14C88E6984952B444150E235690A0589E74F5FED406261F242BFBB83DC12608` |
| `studio-chrome-e2e.log` | `9BE5DB3D0A85A61570F812B72A6F99BC3835EC0A5090FF4E6A2CDF0CE904706D` |
| `studio-component-tree.png` | `C7C15F8CD5345F3ADE9612E7C19829D12629409A176D7A9423067E58FA9C71AD` |
| `clear-bundle-verify.log` | `940F270E55B13CB57103F8FAE7AEAAC6CD815FB41684AAA4BA70FECA652D34AE` |

Chrome 截图记录了 Studio 编辑布局和 HTML 结构树。E2E 对操作结果另作源码断言，并逐字节比较预览响应和导出/导入的 `web/index.html`。

## 未解决项

- 树展示显式静态 HTML 源结构，不展示 JS 运行时生成节点或浏览器修复后的完整 DOM。
- 本次没有制作 CSS 计算样式、布局/响应式、动画、交互逻辑或 Bridge 绑定的可视化编辑器；源码区仍是这些部分的编辑入口。
- 测试不证明 Clear Skin 与冻结 Native 功能等价，不证明 Android WebView 兼容，也不关闭 SEP-15、S5 或 Goal A/B/C。
- 工程仍保留 Native 回退，未切换默认皮肤。
- 并行 `feat/skin-first-separation` 工作树的 `docs/agent-handoff/PROGRESS.md` 具有他方未提交修改，本 Issue 未触碰该文件；功能矩阵和本独立报告记录本次状态。

## Git 交付

- 实现、浏览器测试与契约/矩阵更新由本 Issue 的独立实现提交交付。
- 实现提交 SHA 在后续独立文档提交回填；均推送后才算 Git 交付完成。
