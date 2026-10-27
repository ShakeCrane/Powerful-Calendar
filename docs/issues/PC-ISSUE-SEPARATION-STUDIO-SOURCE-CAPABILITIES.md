# Studio 附加源码编辑与能力声明保留

日期：2026-10-27。状态：**本专项源码编辑、Chrome 预览和 v3 导出重导入 PASS；完整 IDE / Android 功能等价未完成。**

## 根因与修改

Studio 原有高级编辑区只暴露 `web/index.html`、`web/style.css`、`web/app.js`。实际 Clear 编辑器位于 `web/edit.js`，设计样式位于 `web/clear-design.css`；它们能被导入和保留，却无法在 Studio 内修改。现增加附加 UTF-8 文本文件选择和编辑，编辑直接写入预览与导出共用的 `sourceFiles`。无法按 UTF-8 解码的文件不会出现在文本编辑器，未修改的二进制保持原始字节。

`payload()` 此前无条件把任何导入皮肤的 capabilities 重写成 `host.read`、`host.action`：只读皮肤会被额外授予 action，声明 `host.edit` 的皮肤则失去该声明。另一个差异是 Python v3 打包器对缺失 capabilities 默认授予读写动作，而 Android 解析器默认为空。现在保留导入的声明，并将打包器的缺省值改为空；实际运行时授权仍由 Host 验证，本修复不改变 Android 权限逻辑。

## 回归与证据

- 旧 `af229d9` 的 Studio 脚本运行新增测试：附加 `web/edit.js` 缺失，空权限、只读及含 `host.edit` 三组声明均被改写。旧版失败日志 SHA-256 `8D4C76E077649937E2DD3273A06271529E4D40E1B745E92A0C720800A82D80F5`。
- 打包器缺失 capabilities 的红测以 `['host.read','host.action']` 与预期空集合不等而失败；失败日志 SHA-256 `1832BD614A8D0892B2F87E7B733C6D6F8CF3FC09C33794F2B6DDAD80813661EA`。修复后 `test_runtime_v3.py` **3/3 PASS**，覆盖直接构建与导入缺失声明的现有 ZIP。
- 定向 `test_v3_source_editing.py` **2/2 PASS**：使用真实 APK Clear 资产，调用实际 Studio JS 事件处理，修改 `web/edit.js`；检查无效 UTF-8 文件不进入文本编辑器、字体等未编辑字节保留，并调用生产 v3 builder/unpack 验证导出重导入和三组 capabilities。
- 无 npm 依赖的真实 Chrome 回归 `node tools/skin-studio/tests/e2e_v3_source.cjs` **PASS**：在实际 Studio 页面编辑 `web/edit.js`，通过本地 HTTP 预览读取修改后的文本，导出并重导入 8 个文件，验证能力声明及每个文件字节。日志 SHA-256 `3BCEAF9F9AC80F673D2AAD58A942641EF3EA8A5A2C68A12A486FD8CC26BCA731`。首次运行遇到导航切换导致的 DevTools 上下文失效；已修正测试等待页面就绪，保留首次日志。
- Studio 全量 **41/41 PASS**；完整日志 SHA-256 `599426D66D8565644526F3E7E7694A17799EDF02CD3FD8F78A2BBAD1C2040945`。`node --check` 对生产脚本和浏览器回归脚本通过；`build_default_clear.py --verify` PASS。Chrome 在缺省权限修复后复跑仍 PASS，日志 SHA-256 与上项相同。
- 详细证据位于分离工作树 `.local/android-test/reports/separation-20261027/source-editor/`。浏览器 Mock、Node DOM harness 和 Python builder 均不是 Android WebView、真机或 Native/Skin 业务等价测试。

## 剩余工作

当前仍不支持在 Studio 中直接编辑 `project.json`、任意新增/删除文本文件、替换 `web/` 二进制素材或完整结构化组件编辑；不宣称完整 IDE。Studio Mock 对 capabilities 的行为仍需与 Android Host 对照。Host 当前以 `host.action` 守卫 action 和 editing command；`host.edit` 虽被 v3 格式接受，但没有单独执行门禁，需独立核对规范与安全兼容性，不能凭名称假设它会限制编辑。全量 Native/Skin 功能等价、Android WebView 与实体设备均未签核。
