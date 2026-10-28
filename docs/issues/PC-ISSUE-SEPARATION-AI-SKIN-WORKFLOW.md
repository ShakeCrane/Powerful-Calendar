# PC-ISSUE-SEPARATION-AI-SKIN-WORKFLOW

- **状态：** canonical Clear → machine-readable v3 CLI authoring → validated `.pcskin` 工作流 `PASS`；模型设计质量、Android runtime 和 SEP-15 / S6 完整验收 `NOT TESTED`。
- **日期：** 2026-10-28
- **分支：** `codex/separation-skin-settings`
- **修复基线：** `8b1cea6c945798d05ab0e40fd3a370be800925e8`。
- **实现提交：** 待提交后回填。

## 目标与缺口

Skin Studio 已有机器可读的 `build-v3`、`inspect-v3`、`analyze-v3`、`read-v3`、`edit-v3`、`validate --json`，但各 CLI 门禁和编辑操作主要由独立单元测试覆盖，没有一条端到端用例证明 Agent 式调用能从 canonical Clear 源开始、动态解析检查回执、修改皮肤、构建出新的 `.pcskin` 并完成最终校验。

本 Issue 固化这条工作流作为可反复运行的回归。它不调用模型 Provider，不替代设计质量评估或 Android 运行验收。

## 实现

新增 `tools/skin-studio/tests/test_ai_skin_workflow_v3.py`。用例在仓库忽略的 `.local/android-test/` 临时目录执行真实 CLI：

1. 从 tracked canonical Clear manifest/source 调用 `build-v3 --json`，生成临时输入包，并验证其 SHA-256 与 APK 中 Clear 资源一致。
2. 调用 `inspect-v3` 读取 manifest、能力声明和文件哈希；调用 `analyze-v3` 动态定位 `#app` 和 HTML 页 SHA；调用 `read-v3` 获取 CSS 与 SHA。
3. 根据机器回执构造带 SHA 前置条件的 `component-insert` 与 CSS `write` 操作，调用 `edit-v3` 输出新的 `.pcskin`。
4. 用 `validate --json` 验证输出；重新 inspect/analyze/read，确认插入节点与 CSS 实际存在。
5. 解包比较输入与输出，断言仅 `web/index.html`、`web/style.css` 改变，Host `capabilities` 保持不变，原 APK 资源哈希未变化。

AI 自动化规范及 Skin Studio README 增加了可执行入口；SEP-15 矩阵记录此证据，但该矩阵整行仍保持 `NOT TESTED`。

## 测试与结果

| 检查 | 结果 | 范围 |
|---|---|---|
| `test_ai_skin_workflow_v3.py` | `PASS`，1/1 | 实际子进程调用完整 CLI 序列；操作值由前序 JSON 回执动态生成 |
| Skin Studio Python 全量套件 | `PASS`，60/60 | `python -m unittest discover -s tools/skin-studio/tests -q` |
| canonical Clear APK 包校验 | `PASS` | 8 files / 379252 bytes；SHA-256 `7b3fbb7c402e5c776894497d96ba1ceee7189e8acb48eee70ece2e9c19ccf81a` |
| 该端到端测试生成的编辑包 | `PASS` | SHA-256 `15bda77dcdabc3005457a2128fbb9dc50a468d1ebf27490bd325b02b45eb9b5c`；测试结束后临时目录自动清理 |
| Android WebView / AVD / 真机及真实 AI Provider | `NOT TESTED` | 此回归覆盖本地工具链，不调用 Provider、不声称设备验证 |

## 可追溯证据

忽略目录：`.local/android-test/reports/separation-20261028/ai-skin-workflow/`

| 文件 | SHA-256 |
|---|---|
| `ai-skin-workflow-focused.log` | `506E55C972B1B318A41181B9EBA0F6D2367BEC81172DAA9505C77FEB08130D8C` |
| `studio-python-full.log` | `D8F9DF5506862FA02DD4BF13BA7792B5DA6004C14C514EB105C308AC506D29FC` |
| `clear-bundle-verify.log` | `940F270E55B13CB57103F8FAE7AEAAC6CD815FB41684AAA4BA70FECA652D34AE` |

完整套件日志还记录：输入构建包 SHA 与原 APK 资源一致、能力声明未变、变更路径恰为 HTML/CSS 两项、编辑后包 SHA 如上。

## 剩余范围

- 此用例证明工具能执行一个具体、受限的 agent-style 修改任务；它不证明任意设计需求都能由模型正确表达，也不验证 AI 输出的视觉品质。
- Bridge 命令兼容、完整页面与组件体系、动态运行时 DOM、故障恢复、Android WebView parity、真机/OEM 行为和完整 Native/Skin 双版本回放仍需单独验收。
- 不切换默认皮肤，不删除 Native fallback，不改动 Core 或 APK 资源。

## Git 交付

- 实现提交和文档 SHA 回填提交待完成；仅暂存本 Issue 测试与对应文档。
