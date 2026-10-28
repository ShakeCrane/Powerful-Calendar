# PC-ISSUE-SEPARATION-STUDIO-COMPONENT-STYLES — Studio 静态节点样式覆盖编辑

日期：2026-10-28
范围：Skin Studio / SEP-15 的一个局部能力
状态：**局部桌面与 CLI 验证 PASS；SEP-15 / S5 整体仍 NOT TESTED**

## 问题与根因

Studio 已能检查静态 HTML 节点、修改结构和属性，但节点外观只能通过完整 CSS 源码编辑。组件树没有可检查、可增改和可清除的节点样式覆盖入口，AI/CLI 工作流也没有节点级样式操作；用户不得不直接修改 CSS 选择器和规则。

## 修复行为

- Studio 在组件检查器增加样式表选择、CSS 属性和值列表、增行、移除和应用操作。
- 新增有 SHA-256 前置条件的 `component-style` 项目操作。它仅接受已分析的静态节点、本地 `web/*.css` 文件和有界的属性值；拒绝过期 HTML/CSS、非法属性名、换行、声明分隔符、花括号和注释边界。
- 第一次应用时在目标节点插入确定性 `data-pcskin-studio-style` 标记，并在 CSS 中追加 Studio 管理的独立规则。后续仅替换该规则；清空覆盖时删除生成的规则和标记。
- HTML 通过原始 opening-tag 范围修改，CSS 通过被标记的规则范围修改。测试确认被管理区域以外的原始 CSS/HTML 字节保留；无变化操作不写入差异。
- Studio 预览、导出与重导入沿用同一份 `sourceFiles`；重新打开后根据节点标记找回对应 CSS 规则。复杂 CSS 仍可用完整源码编辑器处理。
- AI/CLI 工作流新增一次节点级样式编辑、复查与验证，并核对包权限未改变、APK 中的规范 Clear 资产未改变。

## 修改文件

- `tools/skin-studio/project_ops_v3.py`
- `tools/skin-studio/v3-studio.js`
- `tools/skin-studio/index.html`
- `tools/skin-studio/style-v3.css`
- `tools/skin-studio/tests/test_project_ops_v3.py`
- `tools/skin-studio/tests/test_ai_skin_workflow_v3.py`
- `tools/skin-studio/tests/e2e_v3_source.cjs`
- `tools/skin-studio/README.md`
- `docs/SKIN_STUDIO_AI_AUTOMATION_V1.md`

## 验收与结果

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 项目操作定向测试 | **26/26 PASS** | `project-ops-focused-final.log`，SHA-256 `5749177281A53FCE47E7A6751432BACCA6FBBC98B9C812F096EAF83A720EE98E` |
| AI/CLI 工作流定向测试 | **2/2 PASS**；实际执行 build → inspect/analyze/read → component edit → component-style edit → validate → bridge audit → re-inspect | `ai-workflow-focused-final.log`，SHA-256 `2A765C3DB1337800A8CA4CFC4074A65BEC65B195D38261F7EA10F881599F830F` |
| Skin Studio Python 全量套件 | **72/72 PASS**，0 failures/errors | `studio-python-full-final-elevated.log`，SHA-256 `285135000782743ACF4609BAE699159295E05734C4B5E78DF28B6BF0CC59FF24` |
| Chrome Headless Studio E2E | **PASS**：样式增改/删除、重载恢复、预览计算样式、源码往返与包导出/导入 | `studio-chrome-e2e-final.log`，SHA-256 `1D57FDDDABCD7647444B2364C969A1B2727BA90658366CCDAFBDBF6BCD1605DA`；截图见 `chrome-e2e/studio-component-styles.png`，SHA-256 `1FA8978110BBB38A0532F37938E29093D286586837D4423AB4EE4A1A9204040D` |
| 可视样式断言 | **PASS**：预览文字计算为 `rgb(255, 0, 255)`，`fontWeight=900` | 同上 E2E 日志及截图 |
| JavaScript / Python 语法检查 | **PASS**：两个 `node --check`、三个 Python 文件 `py_compile` | 命令记录于本次最终检查；E2E 亦实际运行 Studio JS |
| Clear 规范资产一致性 | **PASS**：8 文件、381125 bytes；SHA-256 `04c5f92d1c30d00b84e4cbd90fa572daf8103daa05ca8f5c2552c6a9d94600ac` | `clear-asset-verify.log`，SHA-256 `700E8A004AABADAFDB71274D460262F09FF859C486E414196FB9D01323EF513B` |
| 整体 Skin Studio Python 套件 | 普通沙箱首跑 **11 个环境错误**（loopback `WinError 10013` / 临时目录访问拒绝）；授权本地回环权限重跑 **72/72 PASS** | 首跑失败日志 `studio-python-full-final.log` SHA-256 `733ABF64748908DBA009CAA8AD58177E948E56C1062E5E76BBC3DCEF1D157609`；通过日志见上 |

AI/CLI workflow 生成包 SHA-256 `3f51c079602e99c1dc7d8be2c6ca0cfe3a593a3eb5a3ec06bea7aad387a8c40b`；报告确认只有 `web/index.html`、`web/style.css` 两个路径改变，包 capabilities 保持不变，APK Clear 资产保持原 SHA。

证据目录（Git 忽略、本地保留）：`.local/android-test/reports/separation-20261028/studio-component-styles-final-20261028/`。

## Git 交付

- 实现提交：`7c3dc3832be65bd58b1ec7d017e56988af61dbeb` — `feat(分离): 增加 Studio 组件样式覆盖编辑`
- 已推送至 `origin/codex/separation-skin-settings`；推送后远端分支 SHA 与上述提交一致。
- 本独立报告和分离矩阵/进度记录随后的文档提交推送。
- 并行 Agent 对 `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinManagementInstrumentedTest.kt` 的未提交修改未编辑、未暂存、未提交。

## 限制与后续

本次只证明 Studio 的 Chromium 桌面预览、项目操作和 CLI 工作流。没有运行 Android WebView、AVD 或实体设备验证；不会把 Chrome 结果当作 Android 设备结果。当前编辑器只管理静态节点上的简单 CSS 声明，不提供完整布局/规则 IDE，不枚举 JavaScript 运行时生成 DOM，也不证明完整 Native/Skin 功能等价。因此 SEP-15 与 S5 整体仍为 **NOT TESTED**，不可据此宣布 Studio 或分离阶段完成。下一步可继续补齐 Studio 更完整的样式/布局编辑与运行时节点可视性，再按独立用例进行 Android WebView 验证。
