# Studio 无关操作改写第三方 project.json

日期：2026-10-27。状态：**Studio 指定源码与可视化往返 PASS；完整项目编辑仍未完成。**

## 根因和修复

Studio 加载项目时为缺失的 `layers` 建立内存默认值；原 `prepareFiles()` 每次预览或导出都重新序列化 `project.json`。因此第三方皮肤只修改 `web/edit.js`，其未修改的项目文件也被改写格式并加入 `layers`。切换源码/可视化面板还会改变项目里的 `mode`，即使用户没有修改皮肤内容。

加载时现在保存规范化的内存项目基线。项目模型没有变化时，预览和导出沿用导入的 `project.json` 字节；用户通过 Studio 真正修改材质或图层后，才序列化修改后的项目模型。编辑模式切换只影响当前编辑器视图，不再改变皮肤包的项目元数据。未知项目字段随实际模型修改保留。

## 回归证据

- 在真实 Clear fixture 注入带自定义字段、没有 `layers` 的紧凑 `project.json`，切换源码模式并修改 `web/edit.js`。修复前新增测试失败，指出未修改的项目元数据被重写；日志 SHA-256 `5D35D5A56CF8BC085692A73D4FFBDAF3557B13D850AEE751659378B419A9693B`。
- 修复后定向测试 **3/3 PASS**：源码修改保留原项目字节；新增材质会更新项目及 CSS，同时保留第三方字段；导出后通过 v3 unpack 恢复相同文件集和能力声明。
- 真实 Chrome 在实际 Studio 页面切换模式、修改脚本、预览、导出并重导入；随后新增材质，再次导出重导入并检查自定义字段，**PASS**。日志 SHA-256 `3BCEAF9F9AC80F673D2AAD58A942641EF3EA8A5A2C68A12A486FD8CC26BCA731`。
- Studio 完整回归 **42/42 PASS**；日志 SHA-256 `C79420CD66E7E53F3ACB806DEE6D15F80FD4A86D5CACFCC5534792C9F93E6F07`。`node --check` 与 `build_default_clear.py --verify` PASS。详细日志位于分离工作树 `.local/android-test/reports/separation-20261027/source-editor/`。

未运行 Android WebView 或实体设备；浏览器预览使用 Mock Host。Studio 仍不能直接编辑 `project.json`，任意新增/删除文件或替换所有二进制资源，不能因此宣称完整 IDE 或 Native/Skin 等价。
