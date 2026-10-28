# PC-ISSUE-SEPARATION — Studio 预览与 Android WebView 资源 parity

日期：2026-10-29（最终验证）

状态：**PASS（S0-PKG-06 本报告列出的窄范围）；整体 Native/Skin parity、S0 与 cutover 仍 NOT TESTED / IN PROGRESS**

## 结论与签核边界

S0-PKG-06 本轮目标是核对 Skin Studio 浏览器预览和 Android WebView 对 v3 皮肤资源、字体、CSP、相对路径、Host capability 及失败请求的行为。本报告覆盖的 Python 预览路由、Edge/Chrome 浏览器行为、Android Host 响应策略，以及隔离 API 35 Android WebView instrumentation 均通过。两项真实 WebView instrumentation 在 `emulator-5582` 分别 **1/1 PASS**；最终完整 Gradle 门禁 **608/608 JVM 测试 PASS**。

据此将重建验收项 `S0-PKG-06` 在上述明确列出的覆盖范围内签为 **PASS（narrow scope）**。这不表示全部第三方 `.pcskin`、全部 Android System WebView/OEM 版本或 Native/Skin 功能 parity 已完整验证，也不构成 S0 完成、Release QA GO、默认皮肤切换或 cutover 授权。物理/OEM 设备仍由用户测试，整体 SEP/S0 与 `S0-PKG-07` 保持开放。

## 验收范围

- token-host 预览对根相对资源及 opaque-origin 字体的解析和响应。
- URL 解码、根路径 entrypoint、大小写 MIME、query / 缺失资源 / traversal 失败行为。
- CSP、`nosniff`、缓存和响应长度等 Android 资源响应头。
- Studio Mock 与 Android Host 对 `host.read`、`host.action` 和 inert `host.edit` 的权限门禁。
- 真实 Android WebView 加载大小写 PNG 与 WOFF2、根相对路径、CSP inline/connect-src 行为、失败请求与权限动作结果。

## 根因与修复对应

此前实现审查与回归发现以下 Studio/Host 差异，已在前置实现提交中修复：

1. Studio sandbox iframe 使用 opaque origin，字体请求携带 `Origin: null`，token-host 字体响应缺少对应 CORS 头；无 `Referer` 的根相对资源也未按 Android 包根路由解析。Studio 预览现为 opaque-origin 字体请求回送 `Access-Control-Allow-Origin: null` 与 `Vary: Origin`，并支持包根资源路由。
2. Python 预览与 Android `Uri.path` 对百分号编码的处理不一致；MIME 扩展名大小写处理不一致。预览解析器现在严格解码一次、拒绝 traversal/非法路径/缺失资源/query 资源请求，按 Android 路径语义映射 entrypoint，MIME 扩展名大小写不敏感。
3. Android 成功与错误响应曾有不一致的安全响应头。Host 现统一 MIME 选择及成功/403/404 的 CSP、`nosniff`、`no-store`、`Content-Length`。
4. Studio Mock 曾未按 `host.read` 和 `host.action` 完整限制状态投影与动作。Mock 已按 capability gate 执行；编辑声明 `host.edit` 不会被静默提升为读取或动作授权。
5. 最终 instrumentation 首轮失败的根因是**测试夹具双重 JSON 序列化**，不是生产 Host 拒绝了有效请求：测试将 JSON 字符串交给 `window.__sendHostFrame(frame)`，该 JS helper 会再序列化一次；Host 收到的是 JSON 字符串而非 JSON object，`decodeBridgeAction` 返回 null。探针显示 Host bridge、port、bundle、`host.action` 均有效，但 `decodedAction=null`。测试现在先 `JSON.parse(...)` 再发送 object，并将 action/write 分开断言恰好调用一次。
6. Resource instrumentation 的 `evaluateText` helper 也曾对 WebView 返回值做了多余的二次 JSON 解析，现改为直接解析 evaluate 回调返回的 JSON 字符串。

## 代码变更

本轮最终验证修正的未提交代码文件（相对于 `09088ab`）：

- `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinCapabilityGateInstrumentedTest.kt`：将序列化 frame 转为 JS object 后发送；action 与 write 分开等待和断言各一次；无权限分支仍验证没有副作用。
- `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinResourceParityInstrumentedTest.kt`：移除 evaluate 结果的重复 JSON 序列化解析。

Studio、Android Host 和核心 app 源码修复已在前置提交 `12ae4402956336ce0e0a09b9796f3726e2e5dd23`；Android resource instrumentation 的前置提交为 `083bea433cf141d85a41dfd7c40064334626e100`。本报告随本轮测试夹具修正与证据索引单独提交。

## 测试证据

### Skin Studio、浏览器与资源包

- `python -B -m unittest discover -s tools/skin-studio/tests -v`：**57/57 PASS**。最终日志 SHA-256：`4D5DBEC1C4CBDF5CE7D4BD1219713E658DE2AA320CBB2C036488046D21C7F6F7`。
- `python -B tools/skin-studio/build_default_clear.py --verify`：**PASS**，8 files / 378,172 bytes；bundle SHA-256：`0dc5a9bc6606e3b7a60aa2af5014ccedeb3303f421c7956dad0c0ba036d353a7`。
- Edge/CDP Studio browser E2E：**PASS**。实际验证 opaque-origin WOFF2、大小写 `.WOFF2`、根相对 PNG、inline script 与 `connect-src` CSP 阻断、query/missing 图片失败、`host.read` / `host.action` 与 inert `host.edit` 权限矩阵。日志 SHA-256：`318EEDEC0FC1E09B7E85A2BCEE8742A878C21E8712FA531AE0DA39803462EED8`。
- Chrome/CDP source E2E：**PASS**。覆盖源码和二进制资源 roundtrip、重复事件 scope Mock、Memo 编辑/完成动作、250→251 续载、AI 附件不读真实文件、错误重试及非写入审核确认。日志 SHA-256：`E1A453DB24EDEBB97B142770AB293174F90A80F7C02CEB8D4FDB9650E55F3A8A`。
- `node --check tools/skin-studio/v3-studio.js`、`node --check tools/skin-studio/tests/e2e_v3_browser.cjs`、`node --check tools/skin-studio/tests/e2e_v3_source.cjs`：均 **PASS**。

### API 35 Android WebView 实测

- 使用新建的隔离 AVD `emulator-5582`，目标串号显式指定；安装使用 `adb install -r`，未清除应用数据、未卸载应用、未 wipe AVD。最终 app 与 AndroidTest APK 安装均返回 `Success`。
- `WebSkinCapabilityGateInstrumentedTest#realWebViewMessagesAreGatedByManifestCapabilities`：**1/1 PASS**，5.76 秒。覆盖 capability 允许/拒绝、Host state gate、合法 action 和 deck.open write action 的调用次数/结果。
- `WebSkinResourceParityInstrumentedTest#v3ResourcesFailuresMimeAndCspAreObservedByRealWebView`：**1/1 PASS**，2.844 秒。覆盖真实 WebView 对 v3 资源、字体、失败请求、MIME 与 CSP 的观察结果。
- 最终 capability 日志 SHA-256：`24F5B04C973488085E10A26DB5151DF8861A85EE073B6E16DDC85257BABA3C10`；resource 日志 SHA-256：`E33BB4CB87CD66F7BB0D47808712197EB0F6CF82F12CBEF69BBC376969340BAC`。
- 修复前探针 SHA-256：`A0C965F00E1217BE374AF897CFDE7FDE11D5684F0826053488C16D5735F21CF2`。轨迹先显示有效 capability 下 `decodedAction=null`，修复后同一探针显示 `decodedAction=Routed`；拒绝权限用例中 capability 为 false 且无 action 副作用。该探针只记录非敏感 bridge 状态与解码结果。
- 实际 Android WebView 结果来自 API 35 AVD，不等同于实体/OEM 兼容性验证。

### 完整 Gradle 门禁与产物

在隔离分支工作树运行：

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
$env:ANDROID_SDK_ROOT = 'E:\android-sdk'
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果：**BUILD SUCCESSFUL**；88/88 tasks；608/608 JVM tests、83 suites、0 failures / errors / skipped；source boundary、Debug APK、AndroidTest APK 与 lint 全部完成。lint 为 **0 errors / 52 warnings**。Gradle 日志 SHA-256：`D4A1B1B3F5DFED0521B78D7528C74E6208007CE03A9C3244D19CC4C11BEA7C90`；lint XML SHA-256：`0D6B609E9B0EB9C00EA25985548897B744C3F9E6BF6BFE696459C08FEFA20D45`。

- Debug APK SHA-256：`6B39CA84B4D7414A791E658F87DF99617C4937518A8EE5E62EA19A0160C6EABE`。
- AndroidTest APK SHA-256：`8C60C8A1B73E766028D82FAF384A29E74253E7444B2472152D03617A7816C681`。

### 可追溯证据位置

详细日志保存在忽略目录 `E:\Project\Powerful-Calendar\.local\android-test\reports\separation-s0-package-parity-20261029\`：完整 Gradle、Python、Edge/Chrome E2E、两项最终 instrumentation、安装日志和修复前 probe。该目录不入 Git；本报告保留命令、结果与 SHA-256 索引。APK 和 lint XML 位于隔离工作树 `app/build/outputs/apk/` 与 `app/build/reports/`。

## 遗留限制

- 没有在实体 Android 设备或 OEM 定制 WebView 上运行；由用户进行该类设备测试。
- 本轮不覆盖全部第三方 `.pcskin` 形态、全部系统版本、全量应用页面/交互、长时运行或异常进程恢复，因此不能推导整体 Native/Skin 功能 parity。
- `S0-PKG-07`（已安装第三方包重新打开 Studio、完整编辑再导出）仍是独立缺口；尚无该完整往返链路证据。
- 其余 S0/SEP、发布 QA、默认皮肤切换和 cutover 仍按各自验收标准保持未签核。本项窄范围 PASS 不改变这些状态。

## 提交

本轮报告与两项 Android instrumentation fixture 修正在验证后作为独立提交推送至 `origin/codex/separation-s0-inventory`；最终提交 SHA 记录于 `docs/agent-handoff/PROGRESS.md` 和交付回复。
