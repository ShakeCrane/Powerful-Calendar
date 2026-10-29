# PC-ISSUE-SEPARATION-S0-PKG-02-ROUNDTRIP — v3 无修改导入/预览/导出

- 日期：2026-10-29
- 状态：**PARTIAL。扩展 Clear v3 fixture 的 Studio HTTP no-edit 往返及 Android-delivery fixture 的 Chrome 浏览器 no-edit 往返均 PASS；任意第三方包语料和 Android runtime parity 仍未测试。**
- 工作树：`codex/separation-s0-timeline-drag`
- 测试和独立报告提交：`c402b1ac02bb07a8cb265c8cf6b4845398d3d1a6`

## 需求和根因

重建项 `S0-PKG-02` 要求从 Studio 导入 v3 包后，无编辑预览并导出时保留包内源码、二进制资源、Manifest 字段/权限声明和 `project.json` 内容。已有回归覆盖了导入后编辑 CSS 的元数据保留，以及预览资源与打包源的部分字节对比；缺少一条无编辑流程贯穿 import → preview → export → reimport，并同时检查所有文件、扩展字段和能力声明。

本轮没有发现生产 HTTP 路由丢失包内容；增加的是完整性回归证据。

## 修改与测试场景

- `test_v3_preview.py` 新增 `test_unedited_v3_import_preview_export_reimport_preserves_every_file`。
- 测试由规范 Clear 包构造 11-file v3 fixture，额外包含 `web/extra.js`、带 `NUL/0xFF` 的 `assets/provenance.bin`、自定义 Manifest `x-vendor-metadata` 和 `project.json` 扩展字段；保留原有 `host.read` / `host.action` 能力。
- 使用 Studio 的真实 HTTP `Handler` 依次执行 `/api/import-v3`、`/api/preview-v3`、预览下每个 `web/` 与 `assets/` 文件读取、`/api/export-v3` 和再次导入。断言导入/导出 Manifest 字段一致，`project.json` 和全部源码/二进制资源字节相同，预览提供的每个资源也与输入相同。
- ZIP archive 的原始序列化字节不是本验收合同；Studio 可重新序列化 Manifest JSON 与 ZIP 条目。起初加入的 archive-byte 等值断言失败后，核对了完整 Manifest 与所有文件字节均一致，随后移除该过严断言；没有把容器重序列化误报为内容丢失。

## 验证证据

| 验收 | 结果 | 证据 |
| --- | --- | --- |
| `PreviewParityTest` 定向 | **7/7 PASS** | `python -m unittest discover -s tools/skin-studio/tests -p test_v3_preview.py -v`；日志 `.local/android-test/reports/separation-s0-timeline-drag/s0-pkg-02-focused-final.log` SHA-256 `8D1FEC3E002CF6E8BF021D9C2B101557FFA396E9C1CC17D6DA21C84CD673BB04` |
| 完整 Skin Studio Python suite | **61/61 PASS** | `python -m unittest discover -s tools/skin-studio/tests -v`；日志 SHA-256 `724AC1880FDE2C518D456210D0B1A715B1151F96535F7328D9C63341C88F759B` |
| HTTP 实际请求轨迹 | **PASS** | focused log 记录 `/api/import-v3`、`/api/preview-v3`、10 个非项目资源 GET、`/api/export-v3`、第二次 `/api/import-v3` 均返回成功；测试报告留在忽略的 `.local/android-test/reports/separation-s0-timeline-drag/` |
| 格式检查 | **PASS** | `git diff --check` |

## 签核范围和剩余风险

已验证的 HTTP 窄路径：扩展 Clear 内容的无修改 v3 import→preview-resource serving→export→reimport，包含自定义 Manifest/project 数据和一个 opaque binary resource。原报告时，浏览器 UI no-edit 流尚未实测。

## Follow-up — Chrome Studio 浏览器无修改往返（2026-10-29）

在 `e2e_v3_source.cjs` 中增加 Chrome 页面内的 no-edit 路径，先把实际 Android delivery fixture 经 `/api/import-v3` 导入，再以未变更的浏览器 payload 执行 preview、逐项 GET 所有 `web/` 与 `assets/` runtime 文件、export 和第二次 import。断言完整语义 Manifest/capabilities、5 个路径、原始文件字节和 Manifest `sha256`，且导出回导后仍逐项匹配。之后同一脚本继续已有的编辑回归。

- Chrome Headless/CDP：**PASS**；预览读取 4/4 runtime resources，no-edit 5/5 file hashes 与 Manifest entries 一致；浏览器日志 SHA-256 `6369D58CF5C17B5CC8D685624604E99ECE257DE6BB5054EF12198A4EDB0868ED`，位置 `.local/android-test/reports/separation-s0-timeline-drag/s0-pkg-02/run-20261029-01/browser-e2e.log`。
- 定向 `PreviewParityTest`：**7/7 PASS**；`python -m unittest discover -s tools/skin-studio/tests -p test_v3_preview.py -v`；日志 SHA-256 `90E7BA8A9C06B1682C1EC06339F228E5EB34AB165EF8FEAFD25655AEB9CAC4E8`，位置 `.local/android-test/reports/separation-s0-timeline-drag/s0-pkg-02/run-20261029-01/preview-parity-focused.log`。
- 本次浏览器 no-edit fixture 的文件 SHA-256：`assets/probe.png` `d3c936ebdd73f46e6422d5946044c99d524554082272019ab738800318b04892`; `project.json` `a5f9436761c7a20653767b69d5257553b6570882a73f2ea28b7955b2d6c12e03`; `web/app.js` `1c16ea9522447f158918871e095ce27cad578f15e8a6217a2ad2a192c4df78fd`; `web/index.html` `f53b88c73efd02f22e282d6fc6a917656fb579a357032e6a9774c91bda7e3229`; `web/style.css` `a244308e5b061e7843ba208e7368a0c7a8dc71c6a56ba2cd006111b99d561090`。
- `node --check tools/skin-studio/tests/e2e_v3_source.cjs` 与 `git diff --check` PASS。Chrome/browser 与 HTTP 服务只使用本机 loopback；未启动 AVD 或写入日历数据。

更新后的签核范围：HTTP 11-file extended fixture 与浏览器 5-file Android-delivery fixture 的 no-edit 导入、预览、导出、回导均有窄路径证据。`S0-PKG-02` 仍 **PARTIAL**：没有覆盖任意第三方包语料、完整异常/大小边界，亦没有签核 Android WebView、设备运行或 Native/Skin 等价；这些缺口不由当前两组 fixture 外推。编辑后的完整 CRUD 往返单列为 `S0-PKG-03`。本项不构成 S0 或 Skin-First GO。
