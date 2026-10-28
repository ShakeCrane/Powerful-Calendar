# PC-ISSUE-SEPARATION — Studio 预览与 Android WebView 资源 parity

日期：2026-10-29
状态：**BLOCKED（Android AVD runtime）；Studio/Chromium 子项 PASS；full parity NOT TESTED；`S0-PKG-06` 未签核**

## 范围与结论

本报告处理 `S0-PKG-06`：比较 Skin Studio 预览与 Android WebView 对 v3 皮肤资源、字体、CSP、相对路径、权限及失败请求的处理。

当前分别验证了 Python 预览路由、Studio Chromium Mock，以及 Android Host 响应规则的 JVM 单测；这些证据覆盖了对应的路径/MIME/安全头/权限规则，但没有进行真实 Android WebView 与 Chromium 的运行时对照。Android instrumentation 已编译，但本轮没有在 WebView/AVD 上运行，因此完整平台 parity **NOT TESTED**，不能签核 `S0-PKG-06`，也不构成分离或 cutover GO。

## 根因

1. Studio sandbox iframe 使用 opaque origin。浏览器字体请求携带 `Origin: null`，而 token 化 `*.localhost` 预览 URL 的字体响应没有对应 CORS 头，字体在 Chrome/Edge 中被拒绝。无 `Referer` 的根相对资源请求也没有完整复现 Android 以包根目录查找资源的路由。
2. Python 预览解析器与 Android `Uri.path` 对 URL 百分号编码的处理不同；大小写扩展名的 MIME 结果亦不一致。query、缺失资源和 traversal 的失败规则需要明确并回归。
3. Android 成功响应含 CSP/nosniff，但 MIME 识别区分扩展名大小写；失败响应缺少同一组安全头及显式长度/缓存策略，难以与预览端作一致比较。
4. Studio Mock 的状态投影没有依赖 `host.read`，命令/动作也没有统一检查 `host.action`。旧 source E2E 在声明只读权限的包上继续尝试创建编辑草稿，造成测试夹具违反权限合同；这不是放宽生产权限的理由。

## 修复

- `skin_studio.py` 为 token-host 增加包根资源解析；仅对 opaque-origin 字体响应回送 `Access-Control-Allow-Origin: null`，并保留 `Vary: Origin`。其他 MIME 不因此获得 `null` CORS 授权。
- `studio_v3_preview.py` 对 URL path 严格解码一次，按 Android 的 `Uri.path` 语义查找；将 `/` 映射到包 entrypoint；拒绝解码后的 traversal、非法路径、缺失资源及带 query 的资源请求。MIME 扩展名不区分大小写，JavaScript 使用 `application/javascript`。
- `WebSkinHost.kt` 将 MIME 选择集中为不区分大小写的函数；成功、403 与 404 响应共用 CSP、nosniff、no-store 和 Content-Length 响应头。
- `v3-studio.js` 仅在 `host.read` 存在时投影模拟状态；所有 Mock Host 命令/动作均要求 `host.action`。source 往返仍验证只读权限不被 Studio 自动增加；随后用单独的明确授权预览夹具运行编辑交互。
- 新增 Python、Robolectric 与 Android instrumentation 覆盖：token-host 根资源、字体 opaque-origin CORS、大小写 MIME、根路径 entrypoint、一次 URL 解码与 traversal、query/404、安全响应头及 `host.read`/`host.action` 隔离。

## 验证证据

### PASS：Skin Studio 与 Chromium 子项

- `python -B -m unittest discover -s tools/skin-studio/tests -v`：**57/57 PASS**。首次未提权运行有 13 个环境错误：loopback socket 被拒绝（`WinError 10013`），测试临时目录的原子替换也被拒绝（`PermissionError`）；在允许本地回环和隔离临时文件操作的执行环境中完整重跑通过。失败运行未用于 PASS 判定。最终日志 SHA-256：`D5A21A4B841F5331A392C3F61E12CA8E1A83334FB0E112510B674B1C7443AA9C`。
- `python -B tools/skin-studio/build_default_clear.py --verify`：**PASS**，Clear package 8 files / 378,172 bytes，SHA-256 `0dc5a9bc6606e3b7a60aa2af5014ccedeb3303f421c7956dad0c0ba036d353a7`。
- Edge/CDP Studio browser E2E：**PASS**。真实 WOFF2 字体通过 opaque-origin CORS 加载；大小写 `.WOFF2` 与根相对 PNG 资源加载；sandbox inline script 和 `connect-src` 请求被阻止；query/missing 图片不显示；`host.read`、`host.action`、inert `host.edit` Mock 权限矩阵通过。日志 SHA-256：`318EEDEC0FC1E09B7E85A2BCEE8742A878C21E8712FA531AE0DA39803462EED8`。
- Chrome/CDP source E2E：**PASS**。覆盖源码与任意二进制资源 roundtrip、重复事件 scope Mock、Memo 编辑/完成动作分离、250→251 续载、AI 附件不读真实文件、AI 错误手动重试及非写入审核确认。日志 SHA-256：`E1A453DB24EDEBB97B142770AB293174F90A80F7C02CEB8D4FDB9650E55F3A8A`。
- JavaScript 语法检查：`node --check tools/skin-studio/v3-studio.js`、`node --check tools/skin-studio/tests/e2e_v3_browser.cjs`、`node --check tools/skin-studio/tests/e2e_v3_source.cjs` 均 **PASS**。
- 浏览器执行命令（PowerShell）：`$env:STUDIO_URL = 'http://127.0.0.1:18876'; node tools/skin-studio/tests/e2e_v3_browser.cjs`；source E2E 使用 `node tools/skin-studio/tests/e2e_v3_source.cjs`。两项均由本机 loopback Studio server 提供预览。

### PASS：自动化构建与 JVM 范围

- 离线完整门禁：

  ```powershell
  $env:ANDROID_HOME = 'E:\android-sdk'
  $env:ANDROID_SDK_ROOT = 'E:\android-sdk'
  .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
  ```

- **BUILD SUCCESSFUL**；608/608 JVM tests、83 suites、0 failures/errors/skips；88/88 Gradle tasks。source boundary、Debug APK、AndroidTest APK 与 lint 均完成。lint **0 errors / 52 warnings**。
- Debug APK SHA-256：`6B39CA84B4D7414A791E658F87DF99617C4937518A8EE5E62EA19A0160C6EABE`。
- AndroidTest APK SHA-256：`27C405BFB941CB0C14B8CEA7E4FD9BB6240CD75023B7FAC8AC5A3532DDB79D7B`。
- lint XML SHA-256：`0D6B609E9B0EB9C00EA25985548897B744C3F9E6BF6BFE696459C08FEFA20D45`。
- Gradle 日志 SHA-256：`53231E175C32A6A91D17D3682D60D2298C37656A6487427DFB59B6B2DEFBA2A5`。
- 完整 Python 测试日志、浏览器日志与 Gradle/lint 报告位于 `.local/android-test/reports/separation-s0-package-parity-20261029/`；APK 位于本隔离工作树 `app/build/outputs/apk/`。本次使用仓库既有 `local.properties` 指向的 `E:\android-sdk`，没有写入隔离工作树的 `local.properties`。

### NOT TESTED：Android WebView runtime

- Android API 35 `emulator-5572`（`PowerCal_S0PackageDelivery_20261029`）可由专用 ADB server 读取，但当时正运行现有 PowerCal 进程 PID `7194`，属于既有冷启动验收环境。本轮未替换 APK、安装 instrumentation、停止进程或改变设备数据，以免干扰该任务。
- 新增 `WebSkinCapabilityGateInstrumentedTest` 与此前独立提交的 `WebSkinResourceParityInstrumentedTest` 均随本轮完整构建成功编译；二者的 connected/AVD 执行均 **NOT TESTED**。
- `WebSkinCapabilityGateInstrumentedTest` 的拒绝路径目前因 Bridge 没有 denial acknowledgement 而使用 750ms settle delay。需在独立 API 35 AVD 上运行，并评估该负向断言的稳定性。
- 实体/OEM 设备测试不属于本轮自动化，按任务分工由用户执行。

## 变更与提交

- 实现与测试提交：`12ae4402956336ce0e0a09b9796f3726e2e5dd23`，`fix(分离): 对齐预览资源加载与权限行为`，已推送至 `origin/codex/separation-s0-inventory`。
- Android resource/CSP instrumentation 前置提交：`083bea433cf141d85a41dfd7c40064334626e100`，已推送至同一分支。

## 剩余验收

在一个可安全独占的 API 35 AVD 上，运行两项 instrumentation；保存真实 Android WebView 与 Chromium 预览的资源响应、字体状态、CSP 拦截、权限结果和失败轨迹，并复核测试后设备/包状态。通过之前，`S0-PKG-06` 保持 **NOT TESTED（full parity）**，S0 与 Skin-First separation 继续 **IN PROGRESS**。
