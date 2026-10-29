# PC-ISSUE-SEPARATION-S0-PKG-02-ROUNDTRIP — v3 无修改导入/预览/导出

- 日期：2026-10-29
- 状态：**PARTIAL。扩展 Clear v3 fixture 的 Studio HTTP no-edit 往返 PASS；任意包型、完整浏览器 UI 及 Android runtime 验收 NOT TESTED。**
- 工作树：`codex/separation-s0-timeline-drag`

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

已验证的窄路径：扩展 Clear 内容的无修改 v3 import→preview-resource serving→export→reimport，包含自定义 Manifest/project 数据和一个 opaque binary resource。总体 `S0-PKG-02` 仍 **PARTIAL**：任意第三方包语料、异常/极限路径、完整浏览器 UI 交互、大批量资源、Android 加载和 Native/Skin 等价没有本测试覆盖。综合 CRUD 编辑后再部署与回导是独立 `S0-PKG-03`；S0 与 Skin-First 分离不构成 GO。
