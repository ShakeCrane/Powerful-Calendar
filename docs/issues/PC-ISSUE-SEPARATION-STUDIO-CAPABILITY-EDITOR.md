# PC-ISSUE-SEPARATION-STUDIO-CAPABILITY-EDITOR — Studio Host 能力请求编辑

- 日期：2026-10-30
- 状态：**本 Issue 的 Studio 编辑能力与浏览器往返验证通过；SEP-15 整体仍 INCOMPLETE。**
- 代码提交：`35b72f026027f69185f9484a99f0d5b79fc45b0c`，已推送至 `origin/feat/skin-first-separation`

## 问题与根因

v3 Manifest 的 `capabilities` 是皮肤包向 Android Host 声明的请求。CLI 已有显式且受限制的 `manifest-patch` 能力，但浏览器 Skin Studio 没有对应编辑控件。作者必须离开 Studio 手工改 JSON，容易造成浏览器编辑、预览和导出结果之间的不一致。

能力声明不等同于 Host 授权，也不应因打开、导入、预览或导出而隐式改变。修复需让用户能明确编辑请求，同时保留 Host 的校验和用户审查边界。

## 修改及根因对应

- 在 Studio Manifest 区增加 `host.read`、`host.action`、`host.edit` 三个显式复选框；选中状态只反映当前 Manifest 声明。
- 载入和同步控件只读取声明，不回写 Manifest。只有用户明确切换复选框才更新 `manifest.capabilities`；源文件、资源、预览及导入/导出操作不会自动增删能力。
- 页面说明 Host 仍会验证请求并控制受信操作；`host.edit` 保持兼容元数据，不因该控件获得额外写权限。
- 补充 JavaScript 源编辑回归、Python 结构测试和 Chrome/CDP 浏览器端往返测试。
- 修正两处过时的架构静态测试：Native Compose renderer 已迁入 `:native-skin`，原断言仍查找旧 `app` 路径和已迁移的文案。

## 验证证据

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| Studio 源编辑定向测试 | **5/5 PASS** | `python -m unittest discover -s tools/skin-studio/tests -p test_v3_source_editing.py -v` |
| Skin Studio Python 全量 | **62/62 PASS** | `.local/android-test/reports/studio-capability-ui-20261030/full-studio-python.log`，SHA-256 `F9F0C43E8BAD264AB64048612FEB36975D319E89DA83C504F63826F55D2F73D6` |
| JavaScript 语法 | **PASS** | `node --check tools/skin-studio/v3-studio.js`；`node --check tools/skin-studio/tests/e2e_v3_source.cjs` |
| Chrome 156/CDP 浏览器回归 | **PASS** | 新建隔离 `pc-delivery-fixture`，明确编辑三项请求，导出并重新导入后声明及源/资源字节保持一致；完整源编辑 E2E 也通过。日志 `.local/android-test/reports/studio-capability-ui-20261030/chrome-source-e2e.log`，SHA-256 `A20A5523DB6121803E13E85CC3C64D24E690BC358FBA2F5EE9721DE5560C3EF3` |
| 补丁空白检查 | **PASS** | `git diff --check` |

全量 Studio 首次运行有三项失败，均为静态测试仍按模块迁移前的旧路径/文案检查 Native 架构。更新测试以检查现行 `:native-skin` 边界后，同一全量命令 **62/62 PASS**。这属于测试基线过期，未修改 Native 或 Clear 实现。

## 范围与限制

- 本 Issue 只交付 Host 能力声明编辑及项目结构测试。没有修改 Clear 源码、Core、Android Runtime 授权逻辑或皮肤默认选择。
- Studio 的浏览器 Mock 不是 Android Host 权限验收；此处证明的是控件状态、明确编辑以及包往返保真。
- 任意皮肤语义组件树、页面结构化重构与绑定编辑仍未完成。SEP-15 继续标记 **INCOMPLETE**；Native/Skin 完整等价、Android/WebView 与实体设备验收均不由本 Issue 签核。

## 交付

实现提交 `35b72f026027f69185f9484a99f0d5b79fc45b0c` 已推送。对应的验收矩阵、AI 自动化说明、Studio 使用文档、独立报告和进度记录作为后续单独文档提交，便于分别回档。
