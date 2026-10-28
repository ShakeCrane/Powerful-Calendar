# PC-ISSUE-SEPARATION-STUDIO-SCAFFOLD-V3 — 从零创建 v3 皮肤项目

日期：2026-10-28

状态：**`scaffold-v3` CLI 切片 PASS；SEP-15/S5 与完整 Skin-First 目标仍进行中**

## 根因

Skin Studio 已能检查、编辑、验证和构建 v3 `.pcskin`，但机器调用工作流必须先取得已有皮肤包或复制官方 Clear 源码。CLI 没有从空白项目生成有效 v3 包的入口，不满足 Agent 从零建立新皮肤项目的工作流。

## 实现

- 在 `tools/skin-studio/skin_studio.py` 新增 `scaffold-v3 --id --name --out [--capability ...] [--json]`。
- 生成可编辑的空白全画布 shell，包括 `project.json`、`web/index.html`、`web/style.css`、`web/app.js`。HTML 引用本地 CSS/JS；不包含远程资源或 Bridge action。
- 默认 `capabilities` 为空。只有调用者显式传入 `--capability host.read|host.action|host.edit` 才写入请求声明；重复或不支持的值被拒绝。声明不是授权，运行时权限仍由 Android Host 决定。
- 复用 `runtime_package_v3.build_files` 和 `unpack` 完整验证/重读包；用现有 `write_atomic` 独占原子创建输出，绝不覆盖已有目标。固定 ZIP 时间戳及稳定源码顺序使相同参数输出相同字节。
- JSON 成功回执包含命令、输出路径、字节/文件数、校验后的 manifest 和 package SHA-256；失败回执写 stderr 并返回 2。
- 更新 Skin Studio README 和 AI Automation Interface 文档，记录命令、能力边界、脚手架不等于完整功能皮肤、脚本需要源码审阅等限制。

## 回归证据

修复前运行新增回归时，Python CLI 返回 2，argparse 明确报告 `scaffold-v3` 不在可用命令中；同一测试在实现后通过。

- 定向 AI v3 workflow：**2/2 PASS**。覆盖 Clear 编辑工作流，以及空白项目构建、inspect/analyze/audit/validate、无 capability 默认、显式 capability、重复构建字节确定性、拒绝覆盖及非法 capability 不产生半成品包。
- 全部 Skin Studio Python 回归：**70/70 PASS**，0 failures/errors。
- Chrome Headless Studio E2E：**PASS**。通过实际 Studio 页面 `#importZip` handler 导入刚生成的空白包，在浏览器结构树插入组件，确认预览提供同一份编辑后 HTML 字节，再导出/重导入并把重导入包重新载入 Studio，确认组件仍可见且空 capability 清单不变。回执标记 `blankScaffoldImport/edit/previewExportImport/reimportedIntoStudio=true`；脚手架包 SHA-256 `4e5d60cda376b6b9295cb8aa4f4a6efbc6b881f999424310dd48e64f2de0156c`。
- `build_default_clear.py --verify`：**PASS**；8 个文件、380,940 bytes，包 SHA-256 `9cc6bdd86a53c693b8cec6e84950ed89ec49bcde9fb78d773034e4147e45a454`。APK 内 Clear 资源未修改。
- `node --check tools/skin-studio/tests/e2e_v3_source.cjs` 与 `git diff --check`：PASS。

本轮第一次在受限沙箱直接执行完整 suite 时，11 个既有测试因 loopback socket 和系统临时目录替换权限被阻止；将测试临时目录定向至忽略的仓库 `.local` 路径并在本机授权测试环境重跑后，70 项全部通过。该失败属于执行环境限制，保留在会话记录中，不计为产品行为失败。

可追溯日志目录：`.local/android-test/reports/separation-20261028/studio-v3-scaffold-20261028-112525-637/`

- 完整 suite：`skin-studio-full.log`，SHA-256 `EED2A9D8C55109B02FC3991C6280EB1EC0E5B72391AC982D9580B08AD79E72F8`
- 定向 workflow：`ai-workflow-focused.log`，SHA-256 `B6BDB81BF4A876584D59F726BE2F0AD800DAB92672D692B57C6BFC20E7CE9009`
- Clear 校验：`clear-verify.log`，SHA-256 `6AA8A00544163FF8D5CC486624A88C3705E51BCB16D4BA63BA1EFD644A9EB5DB`

浏览器证据目录：`.local/android-test/reports/separation-20261028/studio-v3-scaffold-browser-20261028-113628-271/`

- 最终完整往返 Chrome Headless E2E 日志：`studio-browser-e2e.log`，SHA-256 `9B9171319B8049B4556B9F241A7A4D708AB6C76780CFEB1918EA774981EB9064`。
- 同一 E2E 首次运行因测试没有恢复后续 Clear fixture 而失败；测试隔离修复后复跑 PASS。首次失败日志保留于 `studio-v3-scaffold-browser-20261028-113323-771/studio-browser-e2e.log`，SHA-256 `21D6FBB8D3E5FEF4D7DA61A89A34D000C85E216ECBADB2E8B800BAD0D20B72BC`。
- E2E 截图：`studio-component-attributes.png` SHA-256 `D8E9A1D96816099A178777903718B256FF1EB413D459F6E4F4AE199847D48521`；`ai-review-studio-mock.png` SHA-256 `97029E748EA641446960789679DF706563A96490B24F43B5B29D73D6EF82E198`。

## 验收边界

本 Issue 现验证 CLI 创建和桌面 Chrome Studio 导入/编辑/预览/导出重导入路径。没有验证 Android WebView、模拟器或实体设备；没有证明空白 shell 具备 PowerCal Native 的业务功能，也没有关闭完整布局/交互设计、全量 Studio 可视化编辑、Bridge 动态脚本分析或 Native/Skin 功能等价。对应 SEP-15/S5 和总体分离目标保持未完成。

实现、定向测试和 Studio 使用文档提交：`a9dd0a20463a13bd8bd25b79168af6fc4cfd5883`（`feat(Studio): 新增从零创建 v3 皮肤脚手架`）。本 Issue、进度和矩阵作为独立文档提交补录。

浏览器 Studio 导入/编辑/预览/往返回归提交：`86e2f3f550fc6738ddd944e68a8044e475ca487a`（`test(Studio): 验证空白皮肤浏览器往返`）。本次 Issue follow-up 与进度/矩阵证据另行提交。
