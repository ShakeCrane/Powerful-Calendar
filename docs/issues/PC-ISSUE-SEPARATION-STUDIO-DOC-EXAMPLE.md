# PC-ISSUE-SEPARATION-STUDIO-DOC-EXAMPLE — 修正 AI 指南中的包内路径

日期：2026-10-28

状态：**文档示例与真实 `read-v3` CLI 回归 PASS；SEP-15 / S5 整体仍 NOT TESTED**

## 根因

`docs/SKIN_STUDIO_AI_AUTOMATION_V1.md` 的 `read-v3` 示例把 `.pcskin` 包内路径写成 Windows 分隔符 `web\app.js`。CLI 对 ZIP 内部路径执行安全校验，只接受 `web/`、`assets/` 下的 POSIX 分隔符路径，因此复制指南命令会以退出码 2 报错：`file path must be a safe web/ or assets/ path, or project.json`。宿主操作系统路径与包内路径属于不同语义；Windows shell 下的 `--file path\to\skin.pcskin` 示例仍然有效，错误仅在 `--path` 参数。

## 修复

- 将指南中的包内路径改为 `web/app.js`。
- 新增 `test_ai_automation_guide_read_example_uses_runnable_package_path`。测试从指南读取实际首条 `read-v3` 命令，断言其包内路径为 `web/app.js`，再对仓库 canonical Clear `.pcskin` 调用真实 CLI，并验证 JSON 回执、路径及非空文件字节数。
- 未改动 Runtime、CLI、Skin Studio、皮肤包或 Android 生产源码。

## 验证证据

- 修复前红灯：`test_project_ops_v3.py` 27 项中 26 项通过；新增回归因读取到 `web\app.js` 而失败。日志 SHA-256：`3CAFEC51A5D217D5352E00793D5ECED78345067550F816E459A7FCBA5CB3934A`。
- 定向回归：新增测试 **1/1 PASS**。日志 SHA-256：`D9D4401C0576CDE23C78282EBC8942C82CCB30699BDDC4694D49CDE9FEE46325`。
- 首次无提升权限运行完整套件时，11 项因沙箱拒绝 loopback socket / 临时文件 rename 报错；这不是产品断言失败。随后在允许本地测试所需权限的隔离执行中重跑完整套件，**73/73 PASS**。最终日志 SHA-256：`60C7F98E92519EE4F455A375D7DA716122EB230F6A5E313692187BAD37FEE20E`。
- `python tools/skin-studio/build_default_clear.py --verify`：PASS，canonical Clear 包为 8 个文件、382786 bytes，SHA-256 `5ea7da58dde8722320ab6f32b16e854293260788eb930f8e0cce1321252d8080`。验证日志 SHA-256：`558A7055BB67B11D5F49B1506F473D5A2E5F6DC0EECEB273037C67B5C07BC3E8`。
- `git diff --check`：PASS。

日志保存在 `.local/android-test/reports/separation-studio-doc-path-20261028/`。修复及回归测试提交 `1f0d8e553c5bec133527d02ebb298d78e315e078` 已推送到 `origin/codex/separation-legacy-import`。

## 验收边界

本 Issue 只验证 AI 指南中的一个 CLI 示例和真实包内文件读取，不增加 Studio 编辑能力，也不证明完整皮肤开发工作流、Android WebView 或设备行为。未运行浏览器、Android WebView、AVD 或实体设备测试。SEP-15 / S5 仍 **NOT TESTED**；完整布局与逻辑编辑、运行时动态节点、跨设备预览/导出等缺口仍开放。
