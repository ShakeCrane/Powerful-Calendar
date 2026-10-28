# PC-ISSUE-SEPARATION-STUDIO-ATTRIBUTE-EDITOR — static HTML attribute inspector

- **状态：** 静态节点属性编辑切片 **PASS**；SEP-15 / S5 完整 Studio 能力、Android WebView parity、Native/Skin 完整等价仍 **NOT TESTED**。
- **日期：** 2026-10-28
- **分支：** `codex/separation-skin-settings`
- **基线：** `2538062741144914e85729eb0a8f2bc9df226846`
- **实现提交：** `4e7326439635a61d3413aaa874c49b3a1653dc68`（已推送）。

## 问题与根因

CLI 的静态 HTML 组件操作和浏览器 Studio 结构树已经支持节点插入、替换、删除和移动，但没有专用的属性查询/编辑操作。作者要修改 `class`、`aria-*`、布尔属性或 `data-*` 时，仍须在整页源码中定位并手工改写开始标签，难以在结构面板完成可追踪、带版本保护的局部编辑。

## 实现内容

- 在现有 v3 项目操作层新增 `component-attributes`，输入包括页面路径及 SHA-256、分析器产生的 `targetNodeId`、`setAttributes` 和 `removeAttributes`。每批最多处理 256 个属性；名称按 HTML 不区分大小写处理，拒绝非法名称、重复归一化名称、冲突的 set/remove、NUL、非法 Unicode 和过长值。
- `null` 表示布尔属性，字符串值按双引号属性语法转义。重复的既有同名属性在触碰该属性时归一为一个值。无变化操作不产生变更并保留开始标签原字节；有变化时只重写所选节点的开始标签，保留页面其他字节，并重新校验完整页面及 v3 包。
- 浏览器 HTML 结构树新增节点属性检查器，可添加、修改、删除任意静态节点属性，支持字符串与布尔属性。操作复用 SHA 和节点 ID 防陈旧保护的 API，并将成功回执写回 `sourceFiles`；源码编辑、预览、工程保存和 `.pcskin` 导出继续共用该源码映射。
- Chrome E2E 在实际浏览器中编辑 `id`、普通字符串、`aria-label` 与 `hidden`，验证预览字节和包导出/重导入一致；重载导出包后重新选择节点，并逐项验证检查器表单恢复四个属性。生成属性检查器截图。
- 更新 `AGENTS.md`、Studio CLI/API 规范、Studio README 和功能等价矩阵。SEP-15 总行继续为 `NOT TESTED`。

## 修改文件

- `tools/skin-studio/project_ops_v3.py`
- `tools/skin-studio/v3-studio.js`
- `tools/skin-studio/index.html`
- `tools/skin-studio/style-v3.css`
- `tools/skin-studio/tests/test_project_ops_v3.py`
- `tools/skin-studio/tests/e2e_v3_source.cjs`
- `tools/skin-studio/tests/v3_source_edit_harness.cjs`
- `tools/skin-studio/README.md`
- `docs/SKIN_STUDIO_AI_AUTOMATION_V1.md`
- `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`
- `AGENTS.md`

未修改 Android Host、Core、Bridge 契约、Clear 生产源码或 APK 资产；没有触碰并行分支工作树的文件。

## 验证结果

环境：Windows PowerShell；Python Skin Studio 测试；本机 Google Chrome Studio E2E。完整 Studio Python 套件及 Chrome 本地服务需 loopback，已在允许本机 loopback 的执行环境运行。

| 检查 | 结果 | 证据与边界 |
|---|---|---|
| `test_project_ops_v3.py` 定向 | **24/24 PASS** | 覆盖字符串/布尔值、属性转义、属性删除、同名重复归一、无变化原字节保留、危险输入/陈旧 SHA 拒绝、CLI 回执及导出重导入；包含真实 Clear 源属性往返并确认 APK 资产未被改写。 |
| Skin Studio Python 全量 | **69/69 PASS** | `python -m unittest discover -s tools/skin-studio/tests -v`；另含实际 CLI AI authoring workflow。 |
| Chrome Studio E2E | **PASS，退出码 0** | 报告包含 `componentAttributes:true`、`componentPreviewExportRoundtrip:true` 和 `componentReloadState:true`。额外输出确认重导入后选中 `<button#studio-component-attributes>`，`id`、字符串、ARIA、布尔属性值均恢复。既有 AI/Host 路径在该浏览器测试中使用非写入 Mock，不是 Android Host 验收。 |
| Clear 包校验 | **PASS** | 8 files / 380,940 bytes；`.pcskin` SHA-256 `9cc6bdd86a53c693b8cec6e84950ed89ec49bcde9fb78d773034e4147e45a454`。 |
| JavaScript 语法 | **PASS** | `node --check tools/skin-studio/v3-studio.js`、`tests/e2e_v3_source.cjs`、`tests/v3_source_edit_harness.cjs`。 |
| `git diff --check` | **PASS** | 最终提交前检查无 whitespace error。 |
| Android Gradle、Android WebView、AVD、实体设备 | **NOT TESTED** | 本切片仅修改本地 Studio / Python / Web 工具，没有 Android 生产源码变更；浏览器结果不外推为 WebView/OEM 结果。 |

### 复现命令

```powershell
python -m unittest discover -s tools/skin-studio/tests -p test_project_ops_v3.py -v
python -m unittest discover -s tools/skin-studio/tests -v
node --check tools/skin-studio/v3-studio.js
node --check tools/skin-studio/tests/e2e_v3_source.cjs
node --check tools/skin-studio/tests/v3_source_edit_harness.cjs
node tools/skin-studio/tests/e2e_v3_source.cjs
python tools/skin-studio/build_default_clear.py --verify
git diff --check
```

## 可追溯证据

日志与截图保存在 `.local/android-test/reports/separation-20261028/studio-component-attributes/`（本地忽略证据，不入 Git）。

| 文件 | 大小 | SHA-256 |
|---|---:|---|
| `focused-project-ops.log` | 4,340 bytes | `d8d85a7b8320ca9a013d88418abab388949626df4d73451a4c110fb6748fd0fb` |
| `studio-python-full-final.log` | 13,422 bytes | `dfd72c8cbe6a99ef15efc210ef9c9ed5558c17d8cd565e1392a879f085aa388b` |
| `studio-chrome-e2e-final.log` | 2,498 bytes | `2075aca5865571615786153f159342776c296f557177c709ac9c3959952093e9` |
| `studio-component-attributes.png` | 363,053 bytes | `66f10d98d5d51cbc8ea50a25f18887e6b6ed06b216035f2d9518c1aaa4196ace` |
| `clear-bundle-verify.log` | 132 bytes | `6aa8a00544163ff8d5cc486624a88c3705e51bcb16d4ba63ba1efd644a9eb5db` |

E2E 重载断言开发期间有两次测试设置失败：一次在随后加载的基线工程中寻找已插入节点；另一次漏计节点原有 `id` 属性。两处均修正为在正确导出包上重载并断言四个属性；最终 E2E 通过。失败日志分别保留为 `studio-chrome-e2e-attribute-reload-setup-failure.log`（SHA-256 `de2b6b5e967aa292b0cbddc71f9e2c9d498507a81b0af8afffac52f96e70f0f8`）和 `studio-chrome-e2e-attribute-row-count-failure.log`（SHA-256 `b8360c717c5b570dafea611e61f856b52ef792660b7bcef8c555543088e2bd53`）。

## 验收边界与后续

- 属性面板作用于 `analyze-v3` 识别出的静态 HTML 源节点；JS 动态生成节点可继续用源码编辑，但不进入静态结构树。
- 本功能编辑 HTML 属性，不提供 CSS 计算样式、响应式布局、动画、交互逻辑或无障碍语义的专用可视化编辑器；高级源码编辑器仍是这些源码的入口。
- Chrome 验证不证明 Android WebView 像素、焦点、输入法或设备行为一致，也不证明 Native/Skin 全功能等价。SEP-15/S5、分离目标 A/B/C 和完整 Skin-First 目标保持开放。
- 矩阵只为本次静态属性编辑切片记录浏览器证据，SEP-15 总状态仍为 `NOT TESTED`。

## Git 交付

- 实现和矩阵/规范更新：`4e7326439635a61d3413aaa874c49b3a1653dc68` — `feat(分离): 增加静态节点属性检查器`，已推送至 `origin/codex/separation-skin-settings`。
- 本 Issue 报告随后以独立文档提交推送；报告提交 SHA 见本分支后续 Git 记录。
