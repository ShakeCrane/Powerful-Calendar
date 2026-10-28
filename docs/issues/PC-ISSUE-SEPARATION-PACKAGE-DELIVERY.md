# PC-ISSUE-SEPARATION-PACKAGE-DELIVERY — Studio 包安装与冷启动恢复

日期：2026-10-29

状态：**S0-PKG-05 PASS（隔离 API 35 AVD）；不代表 S0 完成或 cutover GO**

## 验收目标

验证一份由 Skin Studio v3 builder 生成的 `.pcskin`，能够在 Android 上安装但不自动激活、显式激活、经真实目标进程终止后由 `MainActivity` 恢复选择，并在 Android WebView 中提供与导出包一致的源码与静态资源。

本 Issue 只关闭 `S0-PKG-05`。Chrome Studio Preview 与 WebView 的字体、CSP、能力结果和错误表现比较仍属于 `S0-PKG-06`，保持 **NOT TESTED**。

## 调查与实现

- 新增 `tools/skin-studio/tests/fixtures/android-delivery/`，包含 v3 manifest、`web/index.html`、相对 CSS/JS、`assets/probe.png` 和 `project.json`。Python 测试通过正式 `runtime_package_v3.build` 生成结果，并要求与 Android instrumentation asset 字节完全一致。
- 打包结果：1,611 bytes，SHA-256 `e7f200d4ddb6cb64cea3eda71f770b9dd920657f615d524319a3858897e5bddf`。
- 新增 `WebSkinPackageDeliveryInstrumentedTest`。准备阶段使用真实 app `filesDir` 和 `powercal-skin-choice`，但只有在 API 设备为 Android Emulator、当前皮肤仍为 Native 默认值、安装皮肤目录为空时才继续；否则通过 JUnit Assume 退出，不覆盖已有皮肤选择。
- 安装后断言 `currentId` 不变；选择后校验存储包、`WebSkinBundle` 的 `web/`、`assets/`、`project.json` 与 Studio 导出逐字节一致。
- 第二次独立 instrumentation 在目标进程被 force-stop 后启动真实 `MainActivity`。`MainActivity.onCreate` 的生产 `SkinPackageManager.restoreSelection()` 恢复所选包；测试从 MainActivity 的 WebView 验证 HTML 文本、JS 标记、CSS 计算色 `rgb(21, 67, 123)`、1×1 PNG 载入状态以及三个精确相对资源 URL。两个阶段保存并比较随机进程 token 与 PID，防止同进程重建被误报为冷启动。
- 完成时同步清理测试包、临时导出、进程标记和皮肤选择偏好；偏好先用 `commit()` 排空异步写，再删除。清理后对包文件、临时文件、marker 目录和 preference 文件均有断言。

本 Issue 未修改生产 Core、Host 或 runtime 源码；修复内容为可复现的交付 fixture、Android instrumentation 与验收记录。

## 失败与修正记录

1. 第一次真实 WebView 运行中，HTML、JS、CSS、图片均已加载，但 URL 断言失败。测试错误地期待 `skin.powerfulcalendar.local`；生产 `SecureWebSkin` 的既定 origin 是 `https://skin.powercal.local/`。修正为分别断言样式、脚本、图片的精确 URL。无需改动生产路由。失败日志 SHA-256：`2E719865C7C230AA546E7E6F586DAE910E9869E48A6474D66525EEBF0DD62637`。
2. 冷启动 marker 写入发现测试删除 marker 根目录后未重建。测试现在显式创建目录，并在清理中断言其删除。失败日志 SHA-256：`08804C5FE7119CB436FAD6D4071929531D11E8123F3E2F2D155CCDAF29750CBC`。
3. 初期临时 namespaced preference 清理与 `SharedPreferences.apply()` 并发，设备检查发现残留 XML。最终测试只对经过空包/默认皮肤保护的专用 AVD app 状态运行；先清理偏好，再创建安装用 manager，结束时同步清空、删除并断言文件不存在。最终 AVD 复验后 `files/skin-packs/` 为空，`shared_prefs` 只剩 Android WebView 自身文件，cache 中无该测试目录。

上述失败日志保留在 `.local/android-test/reports/separation-s0-package-delivery-20261029/`，没有将失败尝试计入 PASS。

## 验证证据

### Skin Studio

```powershell
python -m unittest discover -s tools/skin-studio/tests -v
```

结果：**55/55 PASS**，包括 `test_android_test_package_is_exact_studio_v3_builder_output`。日志 SHA-256：`8510CD570CF30361B8B52EC280CFBB7A932878635837C9110F902773B8171A8A`。

### API 35 Android Emulator

- 环境：新建独立 AVD `PowerCal_S0PackageDelivery_20261029`，Android 15 / API 35 / x86_64，ADB serial `emulator-5572`。没有启动或清理其他 AVD；未执行 `pm clear`、卸载或 wipe。
- prepare instrumentation：**1/1 PASS**，目标 app PID `7078`。断言包安装、显式激活、导出与 runtime 文件字节相同及 WebView 资源加载。日志 SHA-256：`92E8D8DC13DD72192D20A517B3F1E68940E035C1CFAE51E416379BDB6D233A2B`。
- Host 随后启动真实 MainActivity，观察 PID `7238`，执行 `am force-stop dev.powerfulcalendar`，确认 `pidof` 返回空。
- restore instrumentation：**1/1 PASS**。新进程 token/PID 与 prepare 阶段不同；真实 MainActivity 恢复所选 fixture，实际 WebView 重新载入导出资源。日志 SHA-256：`00B9C5A7D01018AB53BBD1BC662FB8F4130AEB1B240AA9B7B31B88A2943DEBC1`。
- 无阶段参数的整类运行用于检查默认路径清理，runner 返回 `OK (2 tests)`；restore 方法要求显式 `skinDeliveryPhase=restore`，不会在同一进程中伪装冷启动。日志 SHA-256：`5A4C8F995EC254E2B4D259CA96AD85165D90FFC63C6B18B22A6215AA8D0D00D1`。
- 最终设备清理检查确认没有安装的 fixture、skin-choice 测试偏好或本次 cache marker；新建 AVD 保持存在且未 wipe。

### 完整离线 Gradle 门禁

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果：**BUILD SUCCESSFUL；606/606 JVM tests、83 suites、0 failures/errors/skips、88/88 tasks**。Source boundary、Debug APK、AndroidTest APK 通过；Lint **0 errors / 52 warnings**。

- 日志 SHA-256：`F7BBEB623C8D870677AAD578BFB6D74469807D962FEBD3AA7D6484F11F19FD80`
- Debug APK SHA-256：`3532A2D731525A03D228FFA054FBF2F8658A94A03C2EB3D1AB3E849CC2C1242A`
- AndroidTest APK SHA-256：`ABE355B196F524AF95A179FF8E2295C84D2FB37CBE311501CA07ABA5DDA1998F`
- Lint XML SHA-256：`3079741BBDE3357FC27E26E14DD49D2946C45E8D1078E1C40FB70CD9C65D80CC`

详细命令日志位于 `.local/android-test/reports/separation-s0-package-delivery-20261029/`；Gradle JVM JUnit XML 位于该 worktree 的 `app/build/test-results/testDebugUnitTest/`；Android APK 留在 `app/build/outputs/apk/`。

## 状态边界

- `S0-PKG-05`：**PASS（隔离 Android 15 / API 35 AVD）**。
- 物理设备及 OEM WebView 行为：**NOT TESTED**，按当前分工由用户验证。
- `S0-PKG-06` Chrome Preview↔Android WebView 全维度比较、其他第三方包矩阵及完整 Native/Skin 等价：仍 **NOT TESTED**。
- Skin-First 分离仍 **IN PROGRESS**；本 Issue 不是总体 S0 完成、release GO 或 skin cutover 授权。

## Git 交付

- 实现、fixture、测试及本 Issue/矩阵更新：`32beb48b4e589aa5540cd8419ea8410e67070ae6`。
- 已推送：`origin/codex/separation-s0-inventory`。未合并到 `main`。
