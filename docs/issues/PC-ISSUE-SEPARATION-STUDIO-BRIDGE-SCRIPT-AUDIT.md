# PC-ISSUE-SEPARATION-STUDIO-BRIDGE-SCRIPT-AUDIT — 扩展 Bridge JavaScript 静态审计

日期：2026-10-28

状态：**Skin Studio JavaScript 名称索引与未知名称拦截 PASS；SEP-15 / S5 整体及 Android/设备验收仍 NOT TESTED**

## 问题与根因

`audit-bridge-v3` 原先只枚举 HTML 中静态 `data-action`，没有读取包内 JavaScript 的调用点。Clear 皮肤的 Host Bridge 主要通过 JS `send()`、`perform()` 和底层 `postMessage` / `sendCommand` 使用；因此旧审计无法发现 JS 缺少的 `host.action` 声明，也会让未登记的 JS action/command 名称通过。

复现时新增的两项回归在旧实现上均失败：包含 Bridge 调用时 `requiredCapabilities` 为空；加入未知的 `send("inventedAction")` / `perform("editor.notReal")` 后审计仍返回 `ok: true`。修复前日志 SHA-256：`7786D8C75DF39CD20BEFDC2B65C188DCE3CD2AFA46A086E627E1DAE0340CFBAD`。

## 修复内容

- 在 `project_ops_v3.py` 中加入有界 JavaScript 词法扫描，覆盖包内 `web/*.js`、`web/*.mjs`、`web/*.cjs`、可执行 inline script 和 HTML 事件处理属性；不执行脚本。
- 只索引裸调用 `send("action")` 与 `perform("group.command")`，并识别 `port.postMessage` / `PowerCalSkin.sendCommand` Bridge 传输形态，避免把普通对象 `.send()` 或 `window.postMessage()` 当作 Host Bridge。调用点包含路径、行列、接口类型和字面量名称。
- 名称分别对照 Bridge action registry 与 JSON Schema command 定义；未知字面量会使审计失败。动态名称、含潜在 Bridge 调用的模板插值、裸 `eval` / `Function` 与识别出的低层传输以 `REVIEW_REQUIRED_*` 输出；外部、缺失、不可扫描的链接源会进入源码复核清单。
- 使用 HTML parser 收集 inline script，忽略注释中的伪 script，并依据解析后的 `type` 属性识别 JavaScript MIME 类型（含实体编码及参数）；遵循 HTML 对 `<script/>` 的 raw-text 处理。控制语句条件/代码块后的正则字面量、对象方法声明和模板插值之外的普通文字不会被误报。模板插值检测只对各插值片段作线性扫描。
- 保持既有 JSON receipt `schemaVersion: 1` 和 `scope` 字段含义不变；新增 `scriptScope` 描述 JS 扫描范围。
- 检出任一 JS Bridge 调用后要求 `host.action`。结果列出脚本 SHA、schema 引用及明确限制；即使名称命中，也不验证 JavaScript payload 字段和值。
- 新增独立回归与 Clear AI/CLI 工作流集成断言；更新 CLI 帮助、Skin Studio README 和 AI 自动化指南。

## 变更文件

- `tools/skin-studio/project_ops_v3.py`
- `tools/skin-studio/skin_studio.py`
- `tools/skin-studio/tests/test_bridge_script_audit_v3.py`
- `tools/skin-studio/tests/test_project_ops_v3.py`
- `tools/skin-studio/tests/test_ai_skin_workflow_v3.py`
- `tools/skin-studio/README.md`
- `docs/SKIN_STUDIO_AI_AUTOMATION_V1.md`
- 本报告、`docs/agent-handoff/PROGRESS.md` 与 `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`

## 测试与实际结果

- 修复前回归：新增 2 项测试均按预期失败，分别复现能力漏报和未知名称漏检；日志 SHA-256：`7786D8C75DF39CD20BEFDC2B65C188DCE3CD2AFA46A086E627E1DAE0340CFBAD`。
- 修复后定向命令：`python -m unittest discover -s tools/skin-studio/tests -p test_bridge_script_audit_v3.py -v` **6/6 PASS**，日志 SHA-256 `F1C731D57F384FA9E41C831AF2206990EA3981ADB37028FD035A30E618DEB4CB`；`-p test_project_ops_v3.py -v` **27/27 PASS**，日志 SHA-256 `8243281D706927234FBE760CB3038D2FD1E9A0BFE5C1CA350EEDCDE6A1D0E1AC`；`-p test_ai_skin_workflow_v3.py -v` **2/2 PASS**，日志 SHA-256 `BCEC5D10AADCF44316CCABF1C522DD7CEFDBFBA7E8A033FB02B6117933A79BA7`。
- 最终执行 `python -m unittest discover -s tools/skin-studio/tests -v`：完整 Skin Studio Python 套件 **79/79 PASS**，12.270 秒。日志 `.local/android-test/reports/separation-bridge-script-audit-20261028/studio-python-final-20261028-131319.log` SHA-256：`A40036828CB940094F732D1A6DBFF5D7AE9B69B0FA0122742CDFC55FC47D55B7`。套件包括字面量索引、动态复核、未知名称/capability 的 CLI JSON/退出码、普通对象与浏览器 `window.postMessage` 排除、正则和对象方法语法、HTML 注释/实体编码/自闭合 script、inline event handler、长模板插值与表达式外文本，以及真实 Clear 包编辑工作流。
- `python -m py_compile`：`project_ops_v3.py`、CLI 和三份相关测试源码 **PASS**。
- `python tools/skin-studio/build_default_clear.py --verify`：**PASS**；Clear 包 8 文件 / 382786 bytes，SHA-256 `5ea7da58dde8722320ab6f32b16e854293260788eb930f8e0cce1321252d8080`。最终日志 `.local/android-test/reports/separation-bridge-script-audit-20261028/clear-verify-final-20261028-131319.log` SHA-256：`558A7055BB67B11D5F49B1506F473D5A2E5F6DC0EECEB273037C67B5C07BC3E8`。
- 对 canonical Clear 包实际运行 `audit-bridge-v3`：`ok=true`，18 个静态 HTML 绑定、36 个 JS 调用点（22 个已知字面量、14 个动态名称待人工复核）、0 个未知名称、3 个识别出的低层传输待人工复核、0 个 HTML event handler、0 个模板调用复核项；要求能力为 `host.action`，没有缺失 capability。JSON 证据 `.local/android-test/reports/separation-bridge-script-audit-20261028/clear-audit-final-20261028-131319.json` SHA-256：`587A2029226A11F21475F384279B796AADDDB6E181374E65C680FF83768446C2`。
- `git diff --check`：**PASS**。

完整单测日志和机器可读审计回执位于 `.local/android-test/reports/separation-bridge-script-audit-20261028/`。`git diff --check` 返回 0；Git 对追加进度文档提示工作副本 LF 将按仓库配置转为 CRLF，没有 whitespace error。本次没有运行 Gradle、Android WebView、AVD 或实体设备测试；此变更只涉及 Skin Studio Python CLI、测试与说明文档。

## 验收边界与剩余风险

该扫描器是有界词法索引器，不是 JavaScript parser 或 runtime。别名、对象方法别名、计算属性、模块间接调用、混淆、动态代码和复杂语法可能无法解析，必须人工检查；payload 的字段、类型、值与运行行为未由此验证。模板调用只产生复核提示，不作为已确认名称索引。`ok: true` 只说明本次扫描没有发现未知字面量名称或缺少已识别的 capability，不代表源码已完成复核或 Bridge/Native-Skin 兼容。

本 Issue 只关闭 Skin Studio 审计工具漏扫 JS 调用点这一窄问题。SEP-15 / S5 的完整 Studio 作者能力、Android WebView/设备验证及 Native/Skin 全量功能等价仍 **NOT TESTED**；不据此更新为 PASS。

实现与报告提交：待提交后补录。
