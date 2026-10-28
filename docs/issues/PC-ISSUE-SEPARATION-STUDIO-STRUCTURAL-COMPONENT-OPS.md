# PC-ISSUE-SEPARATION-STUDIO-STRUCTURAL-COMPONENT-OPS

- **状态：** `edit-v3` 静态 HTML 组件操作切片 `PASS`；SEP-15 / S5 完整 Studio、AI authoring 和 Native/Skin parity 继续 `NOT TESTED`。
- **日期：** 2026-10-28
- **分支：** `codex/separation-skin-settings`
- **修复基线：** `57f89d120b1346cfbf8a7314711806acf8c9f667`
- **实现提交：** `ce567cd497b0b157216cbf4da104d1e11fdbcfee`。

## 缺口与根因

已有 `inspect-v3` / `analyze-v3` / `read-v3` 能让 Agent 检查包、读源码并列出静态 HTML 节点；`edit-v3` 支持文件 CRUD 和 `project.json` JSON Patch。但 `_apply_one` 不接受任何按组件节点编辑的操作。Agent 只能重写整份 HTML 文件，难以对分析到的节点进行有前置条件、可回执的局部结构操作。当前浏览器 Studio 也没有语义组件树面板。

这阻碍了 Goal B/C 中“结构编辑”和 AI 按既有项目模型操作组件的能力。本 Issue 只补 AI/脚本可调用的 v3 CLI 操作，不声称浏览器 Studio 组件树编辑、完整页面重构、Host Bridge 兼容分析或运行时 DOM 分析已经完成。皮肤的全部原始 HTML/CSS/JS 仍可通过既有源码操作编辑。

## 实现

扩展既有 `edit-v3` 操作文档，新增四种静态 HTML 节点操作：

- `component-insert`：相对分析结果中的节点 `before` / `after`，或在配对容器内 `prepend` / `append`。
- `component-remove`：删除指定节点的精确源码范围。
- `component-replace`：以一个或多个合法 HTML 元素替换目标节点。
- `component-move`：在同一 `.html` / `.htm` 页面内移动源码子树。

安全与一致性约束：

1. 操作接收 `analyze-v3` 返回的 `nodeId` 和该 HTML 文件当前 SHA-256；过期哈希或节点 ID 被拒绝。
2. 解析器只接受完整、配对、显式的静态 HTML 源结构进行结构编辑；保留未编辑范围的原始字节，支持 CRLF 和 Unicode 偏移。
3. 拒绝不平衡 HTML、编辑 `html` / `head` / `body` 根壳、新增重复 HTML ID、把节点移动进其自身后代、向 `script` / `style` 等 raw-text 元素中插入 HTML，以及向 `img` 等 void 元素添加子节点。
4. 所有操作先在内存中执行；最终整包仍通过生产 v3 builder/validator 后才生成新包。失败时不创建输出文件；成功回执包含变更文件前后哈希。
5. JS 运行时创建的节点不在静态索引中，仍由普通源码 `write` 操作修改；操作不执行 JS，也不解析计算后的 DOM/CSS。

没有修改 Android 运行时、Host Bridge 权限、业务 Core、Clear 生产源码或 APK 资源。

## 修改文件

- `tools/skin-studio/project_ops_v3.py`：HTML 节点源码跨度、显式结构校验、四种事务操作。
- `tools/skin-studio/tests/test_project_ops_v3.py`：增删改移、各插入位置、CRLF/Unicode、SHA、重复 ID、损坏输入、raw-text/void/root 壳约束、CLI 失败不落盘及 Clear 真实源码往返测试。
- `docs/SKIN_STUDIO_AI_AUTOMATION_V1.md`：操作 Schema、限制及 Agent 工作流示例。
- `tools/skin-studio/README.md`：更新 CLI 能力和浏览器编辑器现状。
- `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`：更新 SEP-15 的 AI 结构操作证据，保留总项 `NOT TESTED`。

## 测试结果

| 检查 | 结果 | 证据范围 |
| --- | --- | --- |
| 修复前红测 | FAIL，3 个新增组件操作测试均未通过 | 旧 `_apply_one` 返回 `unsupported project operation: component-insert`（2 FAIL、1 ERROR）；复现了缺少结构操作的根因 |
| `test_project_ops_v3.py` | PASS，15/15 | 四种操作、全部插入位置、顺序变化、SHA、HTML 错误拒绝、CLI 回执和 Clear 往返 |
| Skin Studio 完整 Python 套件 | PASS，58/58 | 包含本地 HTTP 预览/导出与原有 v1/v2/v3 回归 |
| Chrome `e2e_v3_source.cjs` | PASS，退出码 0 | 真实浏览器验证既有源码编辑/预览/导出及 Host Mocks；Mock 不是 Android Host 或日历写入证据 |
| Clear APK 皮肤资源校验 | PASS，8 files / 379252 bytes | SHA-256：`7b3fbb7c402e5c776894497d96ba1ceee7189e8acb48eee70ece2e9c19ccf81a` |
| `py_compile`、Studio JS `node --check`、`git diff --check` | PASS | 最终完整门禁命令中的补充检查 |
| Android WebView / AVD / 实体设备 | NOT TESTED | 本 Issue 只改 Python 工具、测试和文档；没有宣称设备或 Core/Skin parity 通过 |

Clear 实包回归从 APK 内 canonical `.pcskin` 读取 `web/index.html`，按 `#app` 节点插入一个临时节点，再按新分析结果删除；最终重新解包后 manifest 和所有文件逐字节等于原包，且原 APK asset 字节未变。测试没有写入 Clear 源码或 APK 资源。

第一次增强测试夹具尝试嵌入 inline `<script>`，被生产 v3 校验器按合同拒绝（index 页面只允许加载包内 `app.js`）；夹具改用 `<script src="app.js">` 后继续验证 raw-text 节点保护。该次失败是测试输入违反既有包规范，不是生产编辑器回归。

证据位于忽略目录 `.local/android-test/reports/separation-20261028/studio-component-ops/`：

| 文件 | SHA-256 |
| --- | --- |
| `studio-python-full.log` | `6241F9497587D23B603A877E7B5F21609B691B9D621C73ABD02D5DDDF3155957` |
| `studio-chrome-source-e2e.log` | `862D93C91C687C86FCAAB62486EEE74E4C060236F7CCD53C7B2DE5C393DF8C82` |
| `clear-bundle-verify.log` | `940F270E55B13CB57103F8FAE7AEAAC6CD815FB41684AAA4BA70FECA652D34AE` |

完整 Python 命令：

```powershell
python -m unittest discover -s tools/skin-studio/tests -v
```

最终结果为 `Ran 58 tests ... OK`。Chrome E2E 命令：

```powershell
node tools/skin-studio/tests/e2e_v3_source.cjs
```

## 未解决项与后续

- 浏览器 Studio 仍没有语义组件树 GUI，不能在该 UI 中任意拖放/重组节点；本次机器接口让 AI/脚本可以按静态源结构定位并修改。
- 静态解析器不是完整 HTML5 浏览器树修复实现，不分析 JavaScript 动态 DOM、CSS 计算样式、脚本行为或 `data-action` 与 Bridge handler 的语义兼容。复杂页面应通过源码编辑和浏览器/Android 预览验证。
- 结构组件操作只操作 v3 包源文件，不证明 Clear 的 Native/Skin 功能等价、Android WebView 行为、可访问性或完整 AI skin authoring 工作流。
- SEP-15 / S5、Goal B/C 和整个 Skin-First separation 继续开放。默认皮肤未切换，Native fallback 未删除。
- 并行 `feat/skin-first-separation` 工作树中的 `docs/agent-handoff/PROGRESS.md` 有他方未提交编辑。本次未修改该共享进度文件以避免合并冲突；本 Issue 和 SEP-15 矩阵记录本轮状态。

## Git 交付

- 实现及回归提交：`ce567cd497b0b157216cbf4da104d1e11fdbcfee`。
- 本报告包含在实现提交中；报告内实现 SHA 回填由后续文档提交记录。两个提交均推送后才视为交付完成。
