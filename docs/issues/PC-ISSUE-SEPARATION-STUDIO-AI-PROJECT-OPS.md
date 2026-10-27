# PC-ISSUE-SEPARATION-STUDIO-AI-PROJECT-OPS

日期：2026-10-27
状态：**AI 可操作的 v3 包检查/读写切片 PASS；SEP-15 / S5 未关闭。**

## 问题与根因

Skin Studio 原有 CLI 支持 v3 包打包和整体校验，但 Agent 无稳定的机器接口查询包内完整项目、逐个读取任意源码/资源，或执行带冲突保护的项目结构与文件编辑。浏览器编辑器也不能直接操作完整 `project.json`、新增/删除文件或替换任意二进制资源。这阻断了“AI 依据规范编辑、构建并交付皮肤”的可调用工作流。

本 Issue 补充机器操作层，不重写 `.pcskin v3` 格式、运行时或 Host 权限边界。

## 实现

- 新增 `inspect-v3`：先经生产 v3 parser 完整校验，再输出 UTF-8 JSON，包括 manifest、规范化 manifest fingerprint、完整 `project.json`、逐文件大小/SHA/文本或二进制类型、包体计数。
- 新增 `read-v3`：按安全包内路径读取一个文本或二进制文件；结果带原始字节数和 SHA-256。已知源码扩展返回 UTF-8；二进制或未知扩展返回 Base64，避免把碰巧为合法 UTF-8 的二进制误判成源码。
- 新增 `edit-v3`：操作文档支持源码/二进制文件新增、替换、移动、删除，`project.json` JSON Patch，以及受约束的 manifest Patch。所有现存文件编辑需要 SHA 前置条件；新文件需声明目标不存在。Host capability 声明变更需要显式 `allowCapabilityChange: true`，但这不授予实际 Host 权限或用户批准。
- 所有操作先在内存中完成；最终包必须通过现有 v3 生产校验器和确定性 builder 后才写入新的输出路径。输入/输出不能相同，已有输出绝不覆盖；任何操作/验证错误返回 JSON 错误和退出码 2，不创建输出包。
- 增加可运行协议说明、README 入口、AGENTS 当前工具说明和 parity matrix 后续记录。

## 回归与证据

命令：

```powershell
python -m unittest discover -s tools/skin-studio/tests -p test_project_ops_v3.py -v
python -m unittest discover -s tools/skin-studio/tests -v
python tools/skin-studio/build_default_clear.py --verify
```

结果：

- 定向回归：**7/7 PASS**。覆盖检查/读文本与二进制、UTF-8 外观的二进制识别、带 SHA 的文件 CRUD、JSON Patch 的组件树新增/移动/复制/替换/test、manifest 编辑、权限改动显式门禁、过期 SHA/越权路径/无效最终项目拒绝、CLI 结构化 UTF-8 输出、失败不落地、导出重导入和 `validate`。
- Skin Studio 全套：**49/49 PASS**，包括原有架构、编译皮肤生成器、Clear 同源、HTTP、Native recovery、v1/v2/v3 runtime、预览/导出及源码编辑回归。
- Clear 校验：**PASS，8 files / 376661 bytes**，bundle SHA-256 `0e17aabfe216e8ecb0775dafb8ea66857e25687b09e1167b7c68e1d9ffd81623`。
- 首次受限沙箱运行完整套件时，loopback 请求及既有临时目录替换触发系统权限错误；随后按项目本地测试流程在获准执行环境重跑，49/49 通过。失败属于测试运行权限，最终门禁没有失败项。
- 本 Issue 是 Python CLI 与包构建改动；没有修改 Android 应用代码，因此本次未运行 Gradle、Android WebView、模拟器或实体设备测试。结果不构成设备、视觉、无障碍或 Native/Skin 功能等价证据。

可追溯日志位于 `.local/android-test/reports/separation-20261027/studio-ai-operations/`：

- 最终全量测试 `python-full-final.log` SHA-256：`49EE681E21435E5094CCEA9F3C4FD7E17BE9D4A5D5CD2E897394D24D5B9BF9E7`
- 最终定向测试 `python-focused-final.log` SHA-256：`3EF000EB00BE5DD9D6CFE338F11AA2FABEE710C4B9102CB2738A12BD47F238DD`
- Clear 校验 `clear-verify.log` SHA-256：`9D3DB0782CE3C89198D6E27DA51650D8E7AB8BDD143783500D712FDA4444D82F`
- CLI 帮助快照 `cli-help.txt` SHA-256：`A6270197BC3AE8C0CADF4B67FF749918E1EFA560D99BDCB4527B011D05FA15C4`

## 边界与未完成项

- `project.json` 的任意 JSON 结构可由 JSON Pointer Patch 修改，但当前 v3 schema 没有正式页面/组件树模型；本 Issue 没有发明新的渲染 schema。
- 浏览器 Studio 的完整结构编辑器、资产浏览/替换 UI、源码无损视觉往返、AI 自动执行完整设计任务仍未实现。
- Studio/CLI/生产 WebView 的视觉一致性、Android 导入/激活/失败恢复、实体设备验收、完整 Host Bridge parity 与 Native/Skin 回放仍未验证。
- 所以 SEP-15/S5、S0–S8 总体分离目标继续开放；不切换默认皮肤、不删除 Native fallback。

## Git

- 实现与测试提交：`871f54d` — `feat(studio): 提供可调用的 v3 皮肤项目编辑器`，已推送 `origin/feat/skin-first-separation`。
- 协议、矩阵与本 Issue 文档另作一个提交，避免把文档遗漏在实现提交之外。
