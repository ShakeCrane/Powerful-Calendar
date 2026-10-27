# PC-ISSUE-SEPARATION-STUDIO-CLI-JSON-RECEIPTS

## 状态

**PASS：Studio CLI 的构建与校验回执切片。** 这只关闭 SEP-15 的机器操作链路缺口之一；完整皮肤 IDE、AI 自主设计工作流、Native/Skin 等价和设备验收仍未完成。SEP-15 与整体 parity 继续 `NOT TESTED`。

## 根因

Studio 已有 `inspect-v3`、`read-v3`、`edit-v3` 结构化接口，但 `build-v3` 和 `validate` 仅输出人读文本，Agent 无稳定 JSON 回执可核对导出包身份、哈希和文件数量。`build-v3` 还使用 `Path.write_bytes` 写目标，可能截断覆盖已有 `.pcskin`，且写入不是原子操作。

## 修改内容

- `build-v3 --json` 输出 UTF-8 JSON 成功回执：命令/schema、输出路径、包 SHA-256、字节数、文件数和经过生产校验器验证的 manifest。
- `validate <file> --json` 输出 JSON 回执：包 SHA-256、字节数、运行时版本、皮肤 ID/名称、manifest 及版本对应的文件/场景/资源计数。
- 两个 JSON 模式的执行错误均写至 stderr，返回退出码 2，包含 `schemaVersion`、`command`、`ok:false` 和错误文本。人读成功输出保持兼容。
- `build-v3` 现在先生成、完整校验并重新解包构建结果，再通过既有 `write_atomic` 发布到新路径。已有目标会被拒绝；测试确认错误回执明确且原文件字节不变。
- 更新 `AGENTS.md`、Skin Studio README、AI 自动化接口规范及 SEP-15 矩阵说明。SEP-15 仍为 `NOT TESTED`，避免把 CLI 回执误当作整个 Studio 或运行时 parity 的完成证明。

## 测试证据

### 修复前红测

- 新增 CLI 集成测试后，运行 `python -m unittest discover -s tools/skin-studio/tests -p test_project_ops_v3.py -v`。
- 8 项中 1 项失败：`build-v3` 不识别 `--json`，复现了机器构建回执缺失。
- 日志：`.local/android-test/reports/separation-20261027/studio-cli-json/focused-red.log`
- SHA-256：`D2AF357E6EA54252A43A65E395FEA9BCEA71CE6751F62A31E32BCA89EA79E2BD`

### 定向回归

- 同一命令最终 **8/8 PASS**。
- 覆盖 JSON 构建和校验成功回执、runtime v3 包重新导入后的 manifest/文件一致性、相同源码重复构建的确定性字节、无 `--json` 时的人读命令兼容、损坏 ZIP 的结构化错误，以及构建到已存在路径时拒绝覆盖并保持原字节。
- 日志：`.local/android-test/reports/separation-20261027/studio-cli-json/focused-green.log`
- SHA-256：`2A0AE691695CA5FE3B79A2C8ECD679391E9FA15977B5541A65BAC03F5EA743A4`

### 完整 Skin Studio 回归

- 命令：`python -m unittest discover -s tools/skin-studio/tests -v`
- **51/51 PASS**。完整套件包含本机 loopback HTTP 测试；在默认沙箱权限下首次运行有 10 项因 WinError 10013 不能绑定本机端口，授权本机测试端口后的最终运行全数通过。首次失败日志 `studio-full.log` SHA-256：`5B6B77FDD09EE5FC55BD90ADA8EDB0EFDC51F6DE15BAC495C2376FE6C8E38533`；最终证据以 `studio-full-final.log` 为准。
- 最终日志：`.local/android-test/reports/separation-20261027/studio-cli-json/studio-full-final.log`
- SHA-256：`3E77D74A726F038D4843D7155164E2A2A7F54129334B28B8B4986A78888E9D75`
- `python -m py_compile tools/skin-studio/skin_studio.py tools/skin-studio/project_ops_v3.py tools/skin-studio/tests/test_project_ops_v3.py` 通过；本次拥有文件的 `git diff --check` 通过。
- `python tools/skin-studio/build_default_clear.py --verify` 通过：当前工作树 Clear 为 8 文件、376661 bytes，SHA-256 `0e17aabfe216e8ecb0775dafb8ea66857e25687b09e1167b7c68e1d9ffd81623`。该哈希对应当时工作树里的并行未提交 Clear 源码，不属于本 Issue 的提交。
- 同源校验日志 SHA-256：`9D3DB0782CE3C89198D6E27DA51650D8E7AB8BDD143783500D712FDA4444D82F`

## 验收边界与风险

- 本次未运行 Chrome 浏览器 E2E、Android WebView、模拟器或实体设备；本切片改动仅涉及 Python CLI、测试和文档。CLI 重新解包证明的是 `.pcskin` 包结构与内容可读，不证明视觉、宿主桥权限或设备功能等价。
- 未生成面向用户的新皮肤成品或 APK；目标是给 AI/脚本一个可验证的构建及校验接口。
- 首次完整测试的沙箱端口限制不是产品 PASS；最终完整套件是在明确授权的本机 loopback 环境中通过。
- `docs/agent-handoff/PROGRESS.md` 和 `tools/skin-studio/default-clear/source/` 存在并行工作区修改，本次没有暂存或覆盖。Clear 同源校验哈希包含那些并行源码变更。

## Git 交付

- 分支：`feat/skin-first-separation`
- 实现、测试、规范及矩阵提交：`6e9d6ab3156dc36beb2d43503b843b507a6ef841`，提交说明 `feat(Studio): 提供结构化 v3 CLI 构建回执`，已推送至 `origin/feat/skin-first-separation`。
- 本 Issue 报告将作为紧随实现提交的独立文档提交并推送至同一分支。

## 后续

继续完成 SEP-15 的真实语义组件/页面重构编辑、AI 从规范到预览及运行时验证的完整工作流，并同时推进 Host/Skin 功能等价矩阵中尚未闭环的业务项。当前 CLI 回执只让构建结果可机器核对，不能作为 Skin-First 最终交付完成的证据。
