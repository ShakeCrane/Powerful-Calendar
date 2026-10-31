# Studio 无关操作改写第三方 project.json

历史状态（2026-10-27）：**Studio 指定源码与可视化往返 PASS；完整项目编辑仍未完成。**

当前状态（2026-10-31）：**可编辑项目/源码/资源往返已通过浏览器与 Studio 回归；SEP-15 整体仍 PARTIAL / HOLD。**

## 根因和修复

Studio 加载项目时为缺失的 `layers` 建立内存默认值；原 `prepareFiles()` 每次预览或导出都重新序列化 `project.json`。因此第三方皮肤只修改 `web/edit.js`，其未修改的项目文件也被改写格式并加入 `layers`。切换源码/可视化面板还会改变项目里的 `mode`，即使用户没有修改皮肤内容。

加载时现在保存规范化的内存项目基线。项目模型没有变化时，预览和导出沿用导入的 `project.json` 字节；用户通过 Studio 真正修改材质或图层后，才序列化修改后的项目模型。编辑模式切换只影响当前编辑器视图，不再改变皮肤包的项目元数据。未知项目字段随实际模型修改保留。

## 回归证据

- 在真实 Clear fixture 注入带自定义字段、没有 `layers` 的紧凑 `project.json`，切换源码模式并修改 `web/edit.js`。修复前新增测试失败，指出未修改的项目元数据被重写；日志 SHA-256 `5D35D5A56CF8BC085692A73D4FFBDAF3557B13D850AEE751659378B419A9693B`。
- 修复后定向测试 **3/3 PASS**：源码修改保留原项目字节；新增材质会更新项目及 CSS，同时保留第三方字段；导出后通过 v3 unpack 恢复相同文件集和能力声明。
- 真实 Chrome 在实际 Studio 页面切换模式、修改脚本、预览、导出并重导入；随后新增材质，再次导出重导入并检查自定义字段，**PASS**。日志 SHA-256 `3BCEAF9F9AC80F673D2AAD58A942641EF3EA8A5A2C68A12A486FD8CC26BCA731`。
- Studio 完整回归 **42/42 PASS**；日志 SHA-256 `C79420CD66E7E53F3ACB806DEE6D15F80FD4A86D5CACFCC5534792C9F93E6F07`。`node --check` 与 `build_default_clear.py --verify` PASS。详细日志位于分离工作树 `.local/android-test/reports/separation-20261027/source-editor/`。

当时的限制（2026-10-27）：未运行 Android WebView 或实体设备；浏览器预览使用 Mock Host；当时 Studio 尚不能直接编辑 `project.json`、任意新增/删除文件或替换所有二进制资源。

## 2026-10-31 SEP-15 round-trip verification

- 已有统一 v3 项目模型现在由浏览器 Studio 与 AI CLI 共用：Studio 可编辑原始 `project.json`、UTF-8 源码、Host capability 声明及任意受支持的二进制资源；可添加、替换、删除文件并执行引用保护。Studio Python 回归覆盖未知字段、项目层级、任意文本/二进制文件和 capability 显式修改。
- 实际 Chrome/CDP E2E 使用 Android delivery fixture：无编辑导入/预览/导出/重导入时核对清单及文件字节哈希不变；再编辑 `project.json`、`web/edit.js`、能力声明和源/资源，新增、替换、删除文件，通过引用守卫，预览资源并导出/重导入核对最终文件和哈希。结果 `BROWSER SOURCE ROUNDTRIP PASS`。日志 `.local/android-test/reports/studio-sep15-roundtrip-20261031/studio-source-roundtrip-e2e.log`，SHA-256 `059E7DF5A6C28A957104AE6E94C23175E5CE70919F44DE6446A389936214E686`。
- Skin Studio 全量 `python -m unittest discover -s tools/skin-studio/tests -v`：**63/63 PASS**；日志 `.local/android-test/reports/studio-sep15-roundtrip-20261031/studio-python-full.log`，SHA-256 `730CF6B363D80E93597E3A1F6D16864654C3588C920146A2FBBC2A0367228D06`。
- Runtime 接受 Studio 导出包的既有 API 35 Android 15 WebView/cold-process delivery 测试为 **2/2 PASS**，作为 delivery path 的 `PREVIOUS PASS`：日志 `.local/android-test/reports/clear-runtime-diagnostic-20261031/WebSkinPackageDeliveryInstrumentedTest-direct.log`，SHA-256 `99B819207A904A72A447FE6DA7081EE0500500EFE41E38FC6D674996EC458FEC`，测试 APK 来源 app SHA `2f69d27c578e18688fcf60f6d46276479e11bce8`。该 fixture 未被本次 E2E 编辑，因此不能声称新增任意结构皮肤已在 Android WebView 中验证。
- 上述浏览器证据之后的 Runtime follow-up 见下节；SEP-15 整体仍 **PARTIAL / HOLD**。

## 2026-10-31 Studio CLI edit → Android WebView runtime

- Built the baseline delivery fixture with the official `build-v3` CLI, read and inspected it with `inspect-v3` / `read-v3`, applied four SHA-guarded `edit-v3` operations across `web/index.html`, `web/style.css`, `web/app.js`, and `project.json`, then ran `validate` and re-inspected/read the edited package. The operation file is `tools/skin-studio/tests/fixtures/android-delivery/ai-runtime-edit-operations.json`; AndroidTest fixture `app/src/androidTest/assets/skin-ai-edited-fixture.pcskin` is byte-for-byte identical to CLI output (SHA-256 `C7A8F20336F9C6E6DDAAADA2C963403B28A6E6A6BD020B397B2438B9C6A87023`, 1,802 bytes). The source image resource was preserved byte-for-byte. This verifies the documented AI-automation CLI workflow using guarded operations; it is not a claim that a live model was invoked.
- Android API 35 / Android 15 emulator `emulator-5570`, WebView, actual package install and activation: **edited fixture 1/1 PASS**. The test compared all delivered runtime-file bytes to the `.pcskin`, then observed the edited accessible HTML marker, JavaScript dataset marker, and computed CSS color (`rgb(126, 42, 201)`) inside the Android WebView. Log `.local/android-test/reports/skin-studio-ai-runtime-20261031/android-webview-ai-edited-test.log`, SHA-256 `047FC136AE061E37A7DC243400EB69A8B085458B33266C7E21E452049C61ECBF`.
- The unedited Studio-built fixture also passed Android WebView install/activation/render **1/1** and a separate forced process-stop/restore cycle **1/1 prepare + 1/1 restore**. These cold-process checks used the baseline fixture, not the edited one. Logs: `android-webview-baseline-test.log` SHA-256 `49E5B52448C460DE427FD5934C1B68252901347C09A5A29D5BDFE94B89194A4B`, `android-webview-cold-prepare.log` SHA-256 `9EED797CE1409EC753A48C59D2108B1C4331239CB8E8B6ADAECC63CEBCBAA644`, and `android-webview-cold-restore.log` SHA-256 `B45831F21AF7581C0D8B1E29250415417CC0EA824EEF88EE9C1133CA4572DC19`. Test cleanup left `files/skin-packs` empty and removed the skin-choice preference.
- Studio full Python suite: **64/64 PASS**; log `skin-studio-full-suite.log`, SHA-256 `5314C6651DEF9B6463459985FF9259CA5334EA29E058743C2E3A62BD6B4C7C4B`. Focused project ops were **9/9 PASS**; CLI delivery fixture checks **4/4 PASS**. `node --check` of the edited packaged JavaScript passed.
- Offline Android gate `:app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: **785/785 JVM tests, 125 suites, 0 failures/errors/skips; both boundaries, AndroidTest compilation, APKs and lint PASS; 171 tasks**. Log `android-full-offline-gate.log`, SHA-256 `C92EB01F8E7ECD87E83642E5AC0BEE9AC19C0815BF8DD74A8A16F2079676891B`. Debug APK SHA-256 `960E21512503A1A36AE26B8EEF4FB52A67621A41BBC4DC2497FF23460CA8B329`; AndroidTest APK SHA-256 `69094B096022EEB386A8448AA66752D5B9A0295BD32BC698EFF74BBA69F20C8E`.
- Evidence is under `.local/android-test/reports/skin-studio-ai-runtime-20261031/`. This closes the narrow edited-package Runtime proof. SEP-15 remains **PARTIAL / HOLD**: one edited fixture does not establish arbitrary custom-skin Native parity, editable coverage for every reachable runtime behavior, edited-package cold-process recovery, full AI model autonomy, accessibility parity, or physical/OEM behavior. Clear production skin/source was not changed.
