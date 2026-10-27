# PC-ISSUE-SEPARATION-STUDIO-DARK-MATERIAL-TOKENS

日期：2026-10-27

状态：**Studio 材质变量暗色覆盖已修复；Chrome 回归 PASS；Android WebView / 设备验收 NOT TESTED。**

## 问题与根因

Skin Studio 的材质控件把 Clear 的可编辑基础色写入 `web/style.css` 的 `:root` 规则。暗色主题也从 CSS 变量读取文字色，因此编辑浅色文字色会覆盖 `.dark` 主题自己的文字色，造成暗背景上的暗文字。修复前 Chrome 预览中实测到暗主题文字 `rgb(22, 23, 27)` 位于 `rgb(12, 12, 14)` 背景上，计算对比度约 **1.09:1**。工作树基线 `a786e5e` 的生成器确实输出 `:root`；该修复将材质槽定义限制在非暗色根节点。

## 修改

- `tools/skin-studio/v3-studio.js`：材质生成块使用 `:root:not(.dark)`，使材质面板编辑基础/浅色变量时保留 Clear 自己的 `.dark` palette。
- `tools/skin-studio/tests/e2e_v3_dark_material.cjs`：新增 Chrome CDP 回归，从实际 Clear 模板修改“文字颜色”材质，进入 AI 提案卡场景，验证暗色变量、卡片文字/背景对比度、切回浅色后的材质应用。

## 验证

- `node --check tools/skin-studio/v3-studio.js` 与新 E2E 脚本：PASS。
- `node tools/skin-studio/tests/e2e_v3_dark_material.cjs`：Chrome Headless PASS。暗色下 `--label=#F3F3F6`，AI 卡片文字 `rgb(243, 243, 246)` / 背景 `rgb(27, 27, 31)`，对比度 **15.50:1**；浅色下 `--label=#16171b`，正文 `rgb(22, 23, 27)` / 白色背景。
- `node tools/skin-studio/tests/e2e_v3_source.cjs`：源码 round-trip、重复事件 Mock、AI 审核 Mock 及 Host 确认 Mock 全部 PASS；Mock 不写入真实日历数据。
- `python -m unittest discover -s tools/skin-studio/tests -v`：**42/42 PASS**。第一次沙箱内执行因 Windows 本机 socket 权限失败；在获准的回环测试环境重跑后通过，最终日志位于 `.local/android-test/reports/separation-20261027/ai-proposal-review/studio-tests.log`。
- `python tools/skin-studio/build_default_clear.py --verify`：PASS；APK 皮肤资产与 canonical Clear 源一致，8 个文件、376167 bytes，SHA-256 `fd061a35252f3f821d80ccc4b97f50a46123c23e4db9fc12e0f4435305f993ab`。
- 与同一工作树 AI 审核更新共同执行的完整 Gradle 离线门禁，最终 **582/582 JVM、82 suites、0 failures/errors/skips、88/88 tasks**；source boundary、Debug / AndroidTest APK、lint 通过，0 lint errors / 52 warnings。完整日志 SHA-256 `F2FD85882879D9E2000A7E7C633B091E8D709E3E25BD8165A54A5F96EB206F2F`。
- Chrome 深色/浅色结果 JSON：`.local/android-test/reports/separation-20261027/studio-dark-material/chrome-dark-material-result.json`，SHA-256 `F1CCC42029F1C0C6A4FD0276842FDADD364E32C01CC35D70268943856408629C`。

## 验收边界

该 Issue 修复 Studio 生成基础材质规则对暗色 Clear palette 的覆盖。Chrome 实际渲染通过；没有运行 Android WebView、模拟器或实体设备测试，因此 SEP-13、SEP-15 及完整 Native/Skin parity 仍维持原状态。该浏览器结果不代表 Android WebView 或真机验收。

实现提交 SHA：`c496695b0fe3db73e181e67da731969fe881cb0c`（已提交到 `feat/skin-first-separation`，待推送）。
