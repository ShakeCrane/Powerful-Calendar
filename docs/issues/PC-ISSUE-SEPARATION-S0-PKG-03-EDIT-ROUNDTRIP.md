# PC-ISSUE-SEPARATION-S0-PKG-03 — 编辑后的 v3 皮肤包完整往返与资源引用

- 日期：2026-10-29
- 工作树：`codex/separation-s0-timeline-drag`
- 验收范围：重建项 `S0-PKG-03` 的 Studio package edit → preview → export → re-import；不包含 Android runtime 激活（由 `S0-PKG-05` 验收）。
- 状态：**PASS（Chrome Studio fixture 组合编辑、完整文件哈希和资源引用往返）；任意第三方包语料及设备行为未测试。**

## 根因与调查

`S0-PKG-03` 曾只有独立 source edit、binary CRUD、CLI package operation 与 no-edit HTTP round-trip 证据。源码审查发现已有宽覆盖的 `e2e_v3_source.cjs`，但组合 CRUD 场景从 `/api/template-v3` 对象开始；该场景没有贯穿实际 `.pcskin` 导入，未逐文件输出 SHA-256，也只验证了资源引用阻止删除，没有从预览路由实际读取被引用资源。因此旧矩阵“无组合 hash/reference round-trip 证据”的结论对于当时签核范围成立。

本次没有发现打包、导入或编辑生产代码缺陷。修复的是测试入口与证据缺口，未改变 Android、Core 或 Skin Studio 生产行为。

## 修改

仅修改 `tools/skin-studio/tests/e2e_v3_source.cjs`：

1. `POWERCAL_E2E_REPORT_DIR` 可指定隔离的报告/profile 路径，默认既有路径保持兼容。
2. 组合编辑以仓库 AndroidTest 中真实 `.pcskin` delivery fixture 开始，先走 Studio `/api/import-v3`，编辑源并导出回导，再在该回导包上继续完整 CRUD。
3. 组合 CRUD 覆盖 `project.json` 结构、HTML/CSS/JS 源、未引用文件删除、引用文件删除保护、二进制新增与替换。
4. 预览会实际 GET CSS 中引用的 `assets/replace.bin`，断言响应字节 SHA-256 等于包内资源。
5. 导出回导后比较规范化后的完整 Manifest（排除由构建器重算的 `sha256` 字段）、capabilities、排序后的完整文件路径集、所有文件字节及逐文件 SHA-256，并检查 Manifest 中各 hash 与实际字节匹配。再确认被删除文件不复现、资源 URL 引用保留且包仍可继续编辑。

## 验证证据

环境：Windows；Node `v24.19.0`；Python `3.11.4`；本机 Chrome Headless，通过本地 Skin Studio HTTP server/CDP 驱动；所有 HTTP 测试限制在 `127.0.0.1`。没有访问外部 Provider、Android 设备或 PowerCal 日历数据库。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| Chrome Studio E2E：导入→编辑→预览资源→导出→回导→继续编辑 | **PASS**；完整 Manifest/capabilities、9 个文件路径、文件字节及 SHA 全部一致 | 命令：`node tools/skin-studio/tests/e2e_v3_source.cjs`（`POWERCAL_E2E_REPORT_DIR` 指向隔离 evidence dir）；日志 `.local/android-test/reports/separation-s0-timeline-drag/s0-pkg-03/run-20261029-02/browser-e2e.log`，SHA-256 `37234AB54AF1A24E4400F409A19E1875F9BC9E824DD7C11BC154F97DADFCDD6E` |
| 完整 Skin Studio Python 回归 | **61/61 PASS** | `python -m unittest discover -s tools/skin-studio/tests -v`；日志 `.local/android-test/reports/separation-s0-timeline-drag/s0-pkg-03/run-20261029-01/skin-studio-python.log`，SHA-256 `1A7EE953C4069E1433D8E3683654F39CEC71AECF3632442383D10D3DC6D33A9F` |
| 规范 Clear 包验证 | **PASS**，9 files / 384,912 bytes；bundle SHA-256 `D0D136EEF118DC52CE767E8416EC1BD3B3134F75302BC64C3D3ED4FCA5192BF9` | `python tools/skin-studio/build_default_clear.py --verify`；日志 SHA-256 `61EC37F0FA3F862E62CDFCC60D0DBBA1880377A41EACF3878E82BC928BFA6283` |
| JavaScript 语法与 whitespace | **PASS** | `node --check tools/skin-studio/tests/e2e_v3_source.cjs`；`git diff --check` |

### 回导后逐文件 SHA-256

| 包内路径 | SHA-256 |
| --- | --- |
| `assets/probe.png` | `d3c936ebdd73f46e6422d5946044c99d524554082272019ab738800318b04892` |
| `assets/replace.bin` | `ef192b7af54e943f206ab27075ec1805384c972c9959fc5820f1fa7d5268fcef` |
| `assets/agent-new.bin` | `49fd534fea14753447a1931542d9d58744a75810b88718eadc4ccded926317b5` |
| `project.json` | `9e9b76db28a68a43c2a4675096efa3fad63aaab7e96dc413b0b5fceb798ee247` |
| `web/app.js` | `df70146a298ea87ea720a47684325bfd9de697db8d48a341174a4dbb62fb523e` |
| `web/index.html` | `1ccf12f310337bd5562d72367d2914cee06469db06e63f10b252b842b390aab8` |
| `web/style.css` | `6b6b806120b49d4f9be75a33448f67033467bec60afef915aa1404caaf5788ef` |
| `web/edit.js` | `9a8a7342ed99e6c083296025e689f2865d3b0854d8c5245d521c1e43758f98e3` |
| `web/components/agent-card.js` | `fda12cc79cedcd4c5c74ff8bedf76cafe5931bafe7f305689eb7c0c2c02b92a4` |

## 验收边界

- Chrome E2E 使用真实 Studio HTTP handler 与真实 `.pcskin` fixture，但不是 Android WebView，也不是安装/激活皮肤的 Android runtime 测试。
- CLI 既有单测另覆盖 guarded file move、project JSON Patch、binary write、文件 create/delete 与 build/re-import。本 Issue 没有声称覆盖任意第三方包的全部格式组合或极限大小语料。
- Android package activation/restart 属于矩阵 `S0-PKG-05`；Android WebView preview parity 属于 `S0-PKG-06`；已安装包重编辑属于 `S0-PKG-07`。这些独立项状态不因本报告而改变。
- 本项 PASS 不代表 Skin Studio 全部能力完成，不代表 Native/Clear 全功能等价或 skin cutover GO。整体分离继续 **IN PROGRESS**。

## 提交

实现/测试/矩阵/进度提交：`2e47ae8f1b0ee50b4e9457f6465dcf87d8758cb3`（`test(分离): 验证皮肤包编辑哈希与引用往返`），已推送至 `origin/codex/separation-s0-timeline-drag`。本 SHA 补录为独立文档提交。
