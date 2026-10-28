# PC-ISSUE-SEPARATION-INSTALLED-SKIN-EXPORT-REEDIT — 已安装第三方皮肤导出与 Studio 重编辑

日期：2026-10-29
状态：**S0-PKG-07 PARTIAL；宽范围验收 NOT TESTED；Skin-First 仍 IN PROGRESS**
工作分支：`codex/separation-s0-inventory`
本次基线：`836123ebb6ebed42c614fff4f57cf69f9ee329bc`

## 摘要

本次补上 Host 从 Android 私有存储导出任意已安装第三方皮肤原始 `.pcskin` 的入口，并在当前 v3 Studio 中验证同一 v3 包可导入、修改、重新导出和再导入。实际包字节、包选择、安装文件和日历数据库在导出过程中保持不变。还修复 Android 接受但 Studio 原先拒绝/改写的两个可选 v3 字段语义。

此结果不能签核整个 S0-PKG-07：当前浏览器 Studio 面向 v3，已安装 v1/v2 虽可由 Host 导出，却没有对应 Studio 重编辑路径；Android 对同 ID 已安装包的覆盖仍会拒绝。也没有在真机系统 DocumentsUI 上完成“Android 导出 → 文件交接 → Chrome Studio 重编辑 → Android 重新导入”的一条连续端到端流程。因此矩阵保留 **PARTIAL / broad row NOT TESTED**。

## 验收目标与边界

Parity Matrix 的目标是能重新打开已安装第三方包，在 Studio 完整编辑并重新导出。实现本轮可安全验证的 v3 路径：

1. Android Host 在完成包 ID、文件名、大小、包结构与哈希验证后，才打开用户选择的输出 URI。
2. 导出保留私有存储中的原始 ZIP 字节，不激活、改写或删除已安装包，也不写日历数据。
3. 用户可将 v3 `.pcskin` 导入 Studio，修改源码后重新打包及校验。
4. Studio 对未修改的可选字段保持 Android 的既有解释和输入字节，不擅自补字段或改变默认值。

未承诺 v1/v2 的 Studio 编辑转换、同 ID 原位替换、物理/OEM 兼容或真实 DocumentsUI provider 验收。

## 根因与修复对应

| 根因 | 修复 | 回归证据 |
|---|---|---|
| Android 只提供 Clear 候选导出；已安装第三方包没有 Host 导出入口，用户丢失导入文件后无法从应用取回包。 | 设置中每个已安装第三方皮肤增加“导出”；系统 `CreateDocument("application/zip")` 使用 `{id}.pcskin` 默认文件名；`SkinPackageManager.exportInstalled` 从私有原包读取并写入 Host 持有的 URI。 | Compose/Robolectric 驱动实际 Settings 行、验证 Intent action/MIME/标题、写出精确同字节 archive；API 35 instrumentation 验证安装包到输出包字节一致。 |
| 旧路径若先打开输出目标再校验，损坏或不匹配包可能留下空/部分导出。 | 先通过受限大小读取、完整 decode/校验和文件名-ID 一致性检查，再打开输出流；只在验证成功后写完整原包。 | Robolectric 覆盖成功 UI 流程；manager 定向测试覆盖 reserved/missing/tampered 输入均不写出任何字节。 |
| v3 Studio 将缺失 `fullCanvas` 当成 true，且拒绝 `false`；Android 缺省值为 false。 | Studio 仅在显式勾选变更后写入 `fullCanvas`；未修改且输入缺省时仍省略该字段，默认按 Android false 呈现；保留显式 false。 | Chrome E2E 覆盖 false 与缺省；Android decoder 测试以真实 Clear 来源构包、移除该字段后确认结果为 false。 |
| Studio 的 `project.json` 编辑器要求 `slots` 必须存在；Android 可接受缺省 `slots`。直接把 UI 默认值写回会改变原包。 | Studio 在内存中把缺省 slots 规范化为空数组供编辑器使用，原始 `project.json` 字节不变；显式 JSON 编辑也接受缺省 slots。 | Chrome/Python 往返测试移除 slots、编辑其他字段并确认导出仍缺省；Android v3 decoder 回归通过。 |

## 修改文件

- Android Host：`app/src/main/java/dev/powerfulcalendar/ui/v2/SkinPackageManager.kt`、`PowerCalApp.kt`、`PresentationSkinRuntime.kt`。
- JVM/Compose 测试：`app/src/test/java/dev/powerfulcalendar/ui/v2/SkinPackageManagerTest.kt`、新增 `SkinPackageExportFlowTest.kt`。
- Android instrumentation：新增 `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinInstalledPackageExportInstrumentedTest.kt`。
- Studio：`tools/skin-studio/v3-studio.js`、`tools/skin-studio/tests/e2e_v3_source.cjs`、`tools/skin-studio/tests/test_android_delivery_fixture.py`。
- 文档/验收：`tools/skin-studio/README.md`、`docs/SKIN_STUDIO_AI_AUTOMATION_V1.md`、`docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`、本 Issue 和 `docs/agent-handoff/PROGRESS.md`。

没有修改 Core、日历数据模型、正式事件存储、皮肤权限或当前皮肤选择策略。

## 测试方法与最终结果

| 状态 | 方法 | 结果与证据 |
|---|---|---|
| PASS | 定向 JVM/Compose：`:app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests dev.powerfulcalendar.ui.v2.SkinPackageManagerTest --tests dev.powerfulcalendar.ui.v2.SkinPackageExportFlowTest` | 命令成功。最终完整 JUnit XML 中 `SkinPackageManagerTest` 为 13/13、`SkinPackageExportFlowTest` 为 1/1；均 0 failures/errors/skips。Compose 用 Robolectric 验证实际设置页、`ACTION_CREATE_DOCUMENT`、`application/zip`、文件名、输出字节、选择状态及隔离数据库长度。 |
| PASS | Android instrumentation：`:app:connectedDebugAndroidTest --offline --no-daemon --console=plain '-Pandroid.injected.device.serial=emulator-5582' '-Pandroid.testInstrumentationRunnerArguments.class=dev.powerfulcalendar.ui.v2.WebSkinInstalledPackageExportInstrumentedTest'` | Android 15 / API 35 x86_64 AVD，单用例 **1/1 PASS**。已安装 v3 fixture 原始包 SHA-256 `e7f200d4ddb6cb64cea3eda71f770b9dd920657f615d524319a3858897e5bddf`；验证安装不自动激活、Host 导出逐字节相同、已安装源包及日历 DB 长度不变。结果 XML：`app/build/outputs/androidTest-results/connected/debug/TEST-PowerCal_S0PKG06_API35_20261029(AVD) - 15-_app-.xml`，SHA-256 `98193595D93E25F584BDA4B303B5B8B680D17CBC9835F489491C9B8858F58213`。**限制：** instrumentation 用 `Uri.fromFile` 经过 Android `ContentResolver`，不是系统 DocumentsUI/Saf provider；系统选择器 Intent 只由 Robolectric 驱动。 |
| PASS | Chrome Studio E2E：`node tools/skin-studio/tests/e2e_v3_source.cjs` | 对应浏览器测试日志 `.local/android-test/reports/separation-s0-pkg07/studio-chrome-e2e.log`，SHA-256 `B57896EC7C09EE82501E0B582270B849E86863C8E434CE466E78C6626FC106C9`。输出 `installedPackageReopenEditExportReimport=true`，编辑 `web/edit.js` 后导出/重导入；检查 capabilities、未编辑素材及 metadata，并覆盖缺省/显式 false `fullCanvas` 和缺省 slots。 |
| PASS | Skin Studio 全套 Python：`python -m unittest discover -s tools/skin-studio/tests -v` | **59/59 PASS**。日志 `.local/android-test/reports/separation-s0-pkg07/studio-full.log`，SHA-256 `29BAEE2F97BA46F8962BC548813412882FB822881CEBA47DA18E4B00FB5316BD`。 |
| PASS | 规范 Clear 包检查：`python -B tools/skin-studio/build_default_clear.py --verify` | 8 files / 378,172 bytes；SHA-256 `0dc5a9bc6606e3b7a60aa2af5014ccedeb3303f421c7956dad0c0ba036d353a7`。日志 `.local/android-test/reports/separation-s0-pkg07/clear-verify.log`。 |
| PASS | `node --check tools/skin-studio/v3-studio.js` 与 `node --check tools/skin-studio/tests/e2e_v3_source.cjs` | 两个文件语法检查通过；记录 `.local/android-test/reports/separation-s0-pkg07/node-check.log`。 |
| PASS | 完整离线 Gradle 门禁：`.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain` | **BUILD SUCCESSFUL；612/612 JVM 用例、84 suites、0 failures/errors/skips；88/88 tasks 执行成功。** source boundary、Debug APK、AndroidTest APK、lint 均完成；Lint 0 errors / 52 warnings。日志 `.local/android-test/reports/separation-s0-pkg07/full-offline-gate.log`，SHA-256 `58D79FD0A970F2368C06F0810D19F2EFB57982E3DF4528987065E49C93DC8965`。Debug APK SHA-256 `89F634DFF4482081B796712B61A657C5FDBE7EB79561AE31DD7376D1E3885007`；AndroidTest APK SHA-256 `82B1EFB2ACF5B4ED2677CEEFE50AB9E4F787E6A44439420E0C65372BE64EC755`；Lint XML SHA-256 `5BBE9972605BB9E6D10C30DC72E1BF7444967A1E8FF143341771FADB1ED617CD`。 |
| PASS | `git diff --check` | 无空白错误。 |

平台边界：Robolectric、Chrome Studio、API 35 Android instrumentation 分别报告，不互相替代。实体设备/OEM 按用户分工未测。

## 中间失败与修正记录

以下为测试开发期的断言/环境问题，均保留最终重跑，不计作 PASS：

1. Python fixture 测试最初将整个 manifest 当成不变值比较；导出器按预期重算文件哈希后使该断言错误。调整为比较非哈希 metadata 与未改文件字节后通过。
2. Chrome E2E 最初在额外文件下拉框寻找必需的 `web/app.js`，之后又错误要求修改源码后 manifest 哈希映射不变，并要求缺省 `slots` 被补回。分别改为检查主 JS 编辑区、允许 builder 更新哈希、验证可选字段原样缺省；Chrome E2E 最终通过。
3. 首次 Gradle 环境未设置 `ANDROID_HOME`/`ANDROID_SDK_ROOT`；配置仓库已安装 SDK `E:\android-sdk` 后成功。
4. 首次完整 Python suite 在受限环境遇到 loopback 网络权限和临时文件 `os.replace` 环境错误；在获准的本地执行环境重跑，59/59 通过。

这些情况没有通过跳过测试掩盖生产失败；详细最终日志均在上述 `.local/android-test/reports/separation-s0-pkg07/`。

## 设备副作用记录

API 35 instrumentation 返回后，`connectedDebugAndroidTest` 的后续任务使 `dev.powerfulcalendar` 与 `dev.powerfulcalendar.test` 从 `emulator-5582` 的已安装包列表消失；同一时间段的 logcat 出现两个包的 `PACKAGE_FULLY_REMOVED`。这不是本测试代码中的数据库清理步骤，且并非预期结果。测试运行期间断言数据库文件长度未变化，但未在 Gradle cleanup 前保存 app database 的独立文件清单，因此**不能证明 cleanup 后 app 私有数据仍存在**。这台 AVD 是隔离 API 35 QA AVD；运行前记录了第三方 skin 目录为空、Native 默认选择，但没有记录 DB 的存在/哈希。

未对 AVD 执行重装、wipe、`pm clear` 或其他恢复写操作，也没有继续启动 connected task。该 AVD 目前的应用安装/私有数据状态应视作已被 Gradle cleanup 改变；设备上原有应用数据是否存在属于未确定风险。本次实现和测试没有读取日历具体内容，也没有将敏感数据写入报告。

## 未解决项与状态判断

- **PARTIAL / broad row NOT TESTED:** Android 可导出已安装包，Studio v3 可重开 v3 包；v1/v2 Studio 重编辑/升级路径没有实现或验收。
- 同 ID 再安装仍受 Android 包管理器现有限制；需要编辑者另存新 ID 才能并存。本轮未改变防止意外覆盖的规则。
- 真实 DocumentsUI / SAF 文件 provider 到 Chrome Studio 的连续手动流程未验证；Robolectric Intent + ContentResolver 文件输出是分段证据。
- 物理设备/OEM、不同 Android 版本及用户真实文件提供方未测。
- 当前 APK/构建通过不能推出 P07 完整通过、S0 完成、皮肤切换 GO 或 cutover 授权。

## Git 交付

- 本 Issue、进度记录、矩阵和本次实现/测试将单独暂存并以中文 Conventional Commit 提交到 `codex/separation-s0-inventory`，不包含其他工作树资产。
- 推送与提交 SHA 将在提交后补录在本节及 `docs/agent-handoff/PROGRESS.md`。
