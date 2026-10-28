# PC-ISSUE-SEPARATION-STUDIO-BRIDGE-AUDIT — Skin Studio 静态 Bridge v1 审计

日期：2026-10-28  
状态：**机器可调用的静态绑定/能力审计 PASS；SEP-15、完整 Bridge 兼容与 Core/Host/Skin 等价仍 NOT TESTED**

## 问题与根因

现有 AI Skin Studio CLI 可检查、编辑、构建和验证 v3 `.pcskin`，但缺少机器可调用的检查步骤，用于比较皮肤静态 HTML 中声明的 `data-action` 名称与 Android Host Bridge v1 实际接受的 action，以及静态绑定所需的 `host.action` 能力声明。已有 `host.read` / `host.action` / `host.edit` 能力语义报告没有验证 action 名称是否与 Host decoder 同步。

如果只验证 ZIP 和 manifest，未知 action 或静态 action 缺少能力声明仍可能进入导出包；AI 工作流也无法收到可解析的具体绑定和失败报告。

## 实施

- 新增 `tools/skin-studio/bridge_v1_action_registry.json`，登记 Bridge v1 的 25 个闭合 action 名称，并注明来源 `WebSkinHost.kt#decodeBridgeAction`。
- 新增 `audit_bridge_package(source)` 与 CLI `audit-bridge-v3 <skin.pcskin>`。审计通过生产 v3 解包/校验器读取包，按稳定路径顺序分析 `web/*.html` / `web/*.htm` 中 literal `data-action`，输出 package SHA、页面与静态 node ID、已知/未知 action、声明/需要/缺失的 capability 和来源审查提示。
- 静态 HTML 有 action 时要求声明 `host.action`；未知名称或缺失能力时 `ok=false`，命令在 stderr 输出 JSON 并返回退出码 2；成功时 stdout 输出 JSON 并返回 0。
- 增加 registry 与 `WebSkinHost.decodeBridgeAction` 的同步回归。Host ABI 未更改，Skin 声明仍只是请求能力，不构成用户授权。
- 将该步骤纳入真实 AI authoring CLI 测试、权威 AI 自动化文档及 Studio README。

修改文件：

- `tools/skin-studio/project_ops_v3.py`
- `tools/skin-studio/skin_studio.py`
- `tools/skin-studio/bridge_v1_action_registry.json`
- `tools/skin-studio/tests/test_project_ops_v3.py`
- `tools/skin-studio/tests/test_ai_skin_workflow_v3.py`
- `docs/SKIN_STUDIO_AI_AUTOMATION_V1.md`
- `tools/skin-studio/README.md`
- `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`

## 验证证据

环境：Windows PowerShell，Python 3.11.4。详细命令日志存于忽略目录 `.local/android-test/reports/separation-20261028/bridge-audit/`。

| 验证 | 结果 | 证据 |
|---|---|---|
| `python -m unittest discover -s tools/skin-studio/tests -p test_project_ops_v3.py -v` | **19/19 PASS**。覆盖静态绑定索引、未知 action、缺少 capability、CLI 错误 JSON/退出码、registry 与 Host decoder 同步。 | `project-ops-focused.log` SHA-256 `39FEE3EE0D19509EE4B10E7F249C8CE1AB16BA665FB2E85B40FD8BF0BB669122` |
| `python -m unittest discover -s tools/skin-studio/tests -p test_ai_skin_workflow_v3.py -v` | **1/1 PASS**。从 canonical Clear 构建皮肤、结构化编辑、validate、执行 Bridge 审计、重新读取；只更改预期 HTML/CSS，capabilities 不变，APK 资产未变化。 | `ai-workflow-focused.log` SHA-256 `1F6AA06F8856DE660E57AB736F48E505AABF1F80C22C94F4EE6C53068A904ECF` |
| `python -m unittest discover -s tools/skin-studio/tests -v` | **64/64 PASS**，完整 Skin Studio Python 套件。 | `studio-full-elevated.log` SHA-256 `16BA1EEA5D25CDA47CF4E19A4E154E084860DCAAF6F02D7B647978E1EC931188` |
| `python tools/skin-studio/build_default_clear.py --verify` | PASS；Clear 包 8 个文件、380,940 bytes，SHA-256 `9cc6bdd86a53c693b8cec6e84950ed89ec49bcde9fb78d773034e4147e45a454`。 | `clear-verify.log` SHA-256 `6AA8A00544163FF8D5CC486624A88C3705E51BCB16D4BA63BA1EFD644A9EB5DB` |
| 对生成的 canonical Clear 包直接运行 `audit-bridge-v3` | **PASS**；18 个静态 action 绑定、0 未知、需要 `host.action`、0 缺失；有脚本并返回 `requiresSourceReview=true`。 | `clear-bridge-audit.json` SHA-256 `C9C4C408E989A8D2F6BB9D5147B850F00EF762C43416216C39C6D330F56128A8`；包 SHA 与内置 APK 资产一致 |
| `git diff --check` | PASS。 | 提交前工作树检查 |

首次在受限沙箱运行完整套件时，64 项中有 11 个环境错误：本地 HTTP 测试遭 `WinError 10013` 回环 socket 限制，另有临时目录原子替换遭 `WinError 5`。没有据此跳过这些测试；在允许该测试所需 loopback/temp 操作的环境完整重跑后 64/64 PASS。失败现场保留为 `studio-full.log`，SHA-256 `4E915EE3683ECA3DD995C79FE61616A87A9BE947DF3AD0AE617BA25EB2664AB2`。

## 范围与未验证项

- `audit-bridge-v3` 仅检查静态 HTML 的 literal `data-action` 名称和对应 manifest capability。它不执行 JavaScript、不发现运行时创建的控件、不校验 payload 字段/schema，也不证明 Android Host runtime 行为。
- 存在 inline/linked script 时通过 `requiresSourceReview` 明确要求源码人工复核。即使静态审计 PASS，仍须结合 `V3_HOST_BRIDGE_EXTENSION.md` 检查脚本中的动态调用。
- 本次未改 Android/Kotlin 源码，未运行 Gradle、Android WebView、AVD 或实体设备测试；该 CLI 证据不能替代这些验收。
- SEP-15/S5 的完整页面、样式、交互和资产重构能力、动态 Bridge 兼容、Android parity 及完整 Skin/Core/Host parity 仍未完成。因此矩阵中的 SEP-15 总项继续为 **NOT TESTED**，Skin-First 总目标继续 active。
- `docs/agent-handoff/PROGRESS.md` 在另一个 worktree 有并发修改，本次未触碰；本分支用独立 Issue 报告和 parity matrix 记录本项。

## 提交

- 实现提交：`8c0b2eb8572fef42ea8d6c40d6d7f58eeb177b8b` — `feat(分离): 增加静态 Bridge 兼容审计`，已推送至 `origin/codex/separation-skin-settings`。
- 本报告及 SEP-15 记录作为后续独立文档提交交付。

下一步继续扩展可机器验证的 Bridge contract 覆盖，重点审查 payload/schema 与静态清单无法发现的运行时调用，同时保持设备和完整功能等价验收状态为 NOT TESTED，直至有对应证据。
