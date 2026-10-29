# PC-ISSUE-SEPARATION-MEMO-PAGING — Clear Memo 列表分页续载

日期：2026-10-29

范围：S0-ME-01「Memo 列表过滤、完成切换与可达性」的分页缺口。

状态：Host/Bridge 实现、自动化回归和 Skin Studio Mock **PASS**。Android WebView 与 Native/Clear 完整等价回放 **NOT TESTED**。本 Issue 关闭的是 250 项截断无法继续访问的实现缺口，不代表 S0-ME-01 的全 parity 签核，也不代表分离阶段完成。

## 结论

Native `TodoProjection` 已生成排序后的 Memo 行，但 Host 只向 Clear 投影 `todoEntries.take(250)`。超过 250 项时，Host 将 `memoTruncated` 设为真；Clear 页面却只显示不可交互的提示文字，没有请求后续数据的动作。第 251 项以后的 Memo 没有从 Store 删除，但 Clear 用户无法打开、切换完成状态或查看它们。

现已改为 Host 控制的连续前缀分页：Memo 页初始显示 250 行；Clear 发送无参数的 `loadMoreMemo`；Host 按同一排序结果扩大可见前缀，继续加载至全部当前投影行可见。Host 只在 Memo 页接受该动作，且分页不写入日历 Store。Clear 提供有剩余数量的可访问按钮；Studio 增加 251 行 Mock 场景。规范 Clear `.pcskin` 已重新生成并校验。

本修复只移除了 Host 到 Skin 的第二层固定 250 行截断，保留 `TodoProjection` 既有日期范围和重复实例生成规则，没有扩大其业务查询范围。

## 根因与修复

| 根因 | 修改 | 验证 |
| --- | --- | --- |
| `PowerCalApp` 只投影 `take(250)`，没有可推进的 Host 状态。 | 新增纯函数 `MemoProjectionPaging`，以 250 行为页长，按总数计算稳定前缀；用 `Long` 计算下一上限，避免整数溢出。 | `WebSkinHostProjectionTest.memoPagingUsesStableBoundedPrefixesAndNeverShrinksTheLimit` 覆盖 249/250/251、后续页、超过总数、负值和 `Int.MAX_VALUE`。 |
| `memoTruncated` 只用于显示静态提示，Skin 无法继续读取。 | Bridge 增加严格无参数动作 `loadMoreMemo`。未知字段（例如 Skin 自报 `count`）会被拒绝；Host 只在 `Page.MEMO` 接受续载。 | `WebSkinBridgeTest` 覆盖合法动作和额外字段拒绝；Compose/Robolectric 集成测试验证非 Memo 页动作无效。 |
| 首次打开 Memo 时，Host 可能还持有上一页的 records snapshot。 | 从 Skin 导航到 Memo 时刷新 Host Store snapshot，再生成该页投影。续载仍从当前记录重建排序前缀，不依赖 offset 游标，因此记录变化不会让游标重复或跳项。 | `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` 验证导航、顺序、250→251 及 Store 计数不变。 |
| Clear 在有截断时只能告知“还有数据”，没有操作入口。 | Clear 增加无障碍 `<button>`，显示 Host 提供的剩余行数；`memoTruncated=false` 时隐藏。 | Chrome/CDP 场景验证按钮语义、剩余数、点击后显示第 251 项及按钮隐藏。 |
| Studio 预览没有超过 250 行的场景，无法回归续载交互。 | 增加 `memo-overflow` 251 行 Mock，并只在 Memo 场景接收无参数续载。 | `e2e_v3_source.cjs` 驱动 Studio 场景完成 250→251 的 Mock 交互。 |
| 新集成测试最初将 Store 打开在基础 Robolectric context，未使用测试现成的命名空间隔离。 | 测试 Store 改用 `IsolatedContext` 打开，并通过相同 context 删除数据库。 | 隔离后的 3 个定向测试类 23/23 PASS；全量 605 项再次通过。 |

## 对外状态与写入边界

- `memoEntries` 始终是当前完整 `TodoProjection` 排序列表的前缀；初始最多 250 项，每次请求最多增加 250 项。
- `memoTruncated` 与 `memoRemainingCount` 只在 Memo 页反映是否还有未投影行；离开该页时列表为空、截断为 false、剩余数为 0。`memoCount` 保持原有“未完成数量”语义。
- Skin 不提供 offset、游标、页长或总数；客户端不能任意选择读取位置。
- Host 每次续载从当前记录生成相同排序的前缀，达到总数后重复动作不再改变状态。
- 分页只更新 Host 的 UI 状态，不创建/修改正式事件、草稿、身份或提交记录。
- 清单验收明确限定为当前 `TodoProjection` 范围内可访问全部投影行；其原有未来/历史窗口和重复实例上限未改。

## 修改文件

实现提交 `e4b6e620d56e72da7280a8c91ada0555f6755b8f`，提交说明：`fix(分离): 支持 Memo 列表分页续载`。

- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`：Host 可见行数、Memo 页续载动作、Memo 导航时刷新记录、分页状态字段。
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinHost.kt`：新增 `LoadMoreMemo` 和闭合 schema 解码。
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinMemoProjection.kt`：分页纯函数。
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebAiProposalReviewIntegrationTest.kt`：251 行真实 Store 投影、顺序、页面边界、无写入、隔离数据库回归。
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinBridgeTest.kt`、`WebSkinHostProjectionTest.kt`：动作 schema 与分页边界回归。
- `tools/skin-studio/default-clear/source/web/app.js`、`index.html`、`style.css`：Clear 加载更多按钮、剩余数和样式。
- `tools/skin-studio/v3-studio.js`、`index.html`、`tests/e2e_v3_source.cjs`：Studio 动作和溢出场景。
- `app/src/main/assets/powercal-clear-default.pcskin`：由规范 Clear source 重新生成的包。
- `docs/V3_HOST_BRIDGE_EXTENSION.md`：`loadMoreMemo` Host Bridge 契约。

验收矩阵与进度记录由后续独立文档提交更新。

## 验证环境与证据

环境：Windows PowerShell；JDK 20.0.1；Gradle Wrapper 8.14.3；Android SDK `E:\android-sdk`，平台 `android-36`；Python 3.11.4；Node.js 24.19.0。所有 JVM 结果都是 Robolectric/Compose 测试结果，不是 Android WebView 或真机结果。以下日志保存在 Git 忽略目录 `.local/android-test/reports/separation-memo-paging/`。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 定向 Gradle：`:app:testDebugUnitTest` 筛选 `WebSkinBridgeTest`、`WebSkinHostProjectionTest`、`WebAiProposalReviewIntegrationTest` | **23/23 PASS**，3 suites，0 failures/errors/skips；Memo 集成类 8/8，21.426 秒。 | `focused-host-isolated.log` SHA-256 `89E5F54F95A41E8B3D174E5573E5AFE8488B40754474BDDB7A4F12A517FADF26` |
| 完整离线 Gradle：`.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain` | **BUILD SUCCESSFUL**；605/605 tests、83 suites、0 failures/errors/skips；88/88 actionable tasks。source boundary、Debug APK、AndroidTest APK 编译、lint 均成功。Lint：0 errors / 52 warnings。 | `gradle-full-isolated-final.log` SHA-256 `1DCD208351BF100EB47FE58A01E0BF2972A53DB2FF8C41068F96E1C4143E4EE7` |
| Skin Studio Python：`python -m unittest discover -s tools/skin-studio/tests -v` | **51/51 PASS**。 | `studio-python.log` SHA-256 `9810D9AACA66E477552B45BFAC6629755C65A59B775EBC92F0F142FDF3674E2D` |
| Chrome/CDP：`$env:CHROME_PATH='C:\Program Files\Google\Chrome\Application\chrome.exe'; node tools/skin-studio/tests/e2e_v3_source.cjs` | **Mock PASS**：`visibleBefore=250`、`remainingBefore=1`、`visibleAfter=251`、`truncatedAfter=false`、`buttonHiddenAfter=true`。该浏览器是 Studio Mock Host，不是 Android WebView。 | `chrome-e2e.log` SHA-256 `E1A453DB24EDEBB97B142770AB293174F90A80F7C02CEB8D4FDB9650E55F3A8A` |
| Clear package：`python tools/skin-studio/build_default_clear.py --verify` | **PASS**；8 files / 378,172 bytes；package SHA-256 `0dc5a9bc6606e3b7a60aa2af5014ccedeb3303f421c7956dad0c0ba036d353a7`。 | `clear-package-verify.log` SHA-256 `75C26E16C5523825A08B0F8DC77E2B2B49317F4305E15074C9F6477AC056CDAF` |
| JavaScript 语法：Clear `app.js`、Studio `v3-studio.js`、`e2e_v3_source.cjs` 的 `node --check` | **3/3 PASS**，退出码均为 0。 | 本地命令输出；无单独日志。 |
| `git diff --check`、暂存区检查 | **PASS**。实现提交只包含上列 14 个实现/测试/契约文件；没有暂存 `.local` 证据目录。 | 实现提交 `e4b6e620d56e72da7280a8c91ada0555f6755b8f`。 |
| Debug APK | 构建成功；66,238,329 bytes。 | SHA-256 `0A3DF0259F877E66354E80C777EAA3496C1535727C69C92B3A24D723CD9C57F6`。 |
| AndroidTest APK | **仅构建成功，未安装或执行 instrumentation**；1,287,639 bytes。 | SHA-256 `60F08FFC420D8C67860FE9D85938C5237CF3C48CD49D5EFC260DC1187C5C9B90`。 |
| 设备可用性探测：`adb devices -l` | **BLOCKED**：ADB daemon 握手失败；`E:\android-sdk\emulator\emulator.exe` 不存在。没有 Android WebView、AVD 或实体设备测试。 | `adb-probe.log` SHA-256 `A550B83C0EFDEAB79FF6773486BE0EE851A332EDA5C829B7271DD456495F5A4C`。实体设备验证由用户执行。 |

### 失败重试记录

最初的全量运行中，`memoPagingContinuesTheSortedProjectionWithoutStoreWrites` 读取到的 Host 快照仍是 `page=TIMELINE`、Memo 数量为 0；原因是测试在异步恢复和 Store reload 完成前观察了初始状态。修复后从 Memo 导航触发 reload，测试明确等待对应页的 Host state。另一次全量运行使用 20 秒等待上限，触发 `ComposeTimeoutException`；最终将该检查限时设为 60 秒。隔离后的集成测试实际耗时 21.426 秒，三类定向测试和完整门禁随后均通过。

两份失败日志保留在同一忽略目录，未被覆盖：`gradle-full.log` SHA-256 `7AC20F5AB406B743AFCE6115E7C2FD067D83E63F3C81BED32C8E47239E285EF2`；`gradle-full-final.log` SHA-256 `F0C39483538A20FDB491EAFE2B0F8D94E6ADA5FED5BBB1A90E474FC7B4F905A7`。定向 Gradle 通过日志还含 Robolectric Windows 临时目录清理线程写 `.obsolete` 失败的异步输出，但 XML 测试结果为 23/23，进程退出码为 0；最终全量 Gradle 日志没有该清理异常。

## 未验证项和剩余风险

- ADB daemon 当前无法启动，本机也没有 Emulator launcher；没有实际打包 Clear 在 Android WebView 的渲染、Bridge 通信、滚动或生命周期证据。
- 没有执行冻结 Native 与 Clear 的配对回放；`S0-ME-01` 的 Clear 运行时 parity、完整 Memo 操作和父级 `SEP-03` 仍 **NOT TESTED**。
- 现有测试证明 251 行时可以续载至第 251 行，并有边界函数覆盖多页上限；未在 Android WebView 中验证超过 500 行、数据在分页期间新增/删除或重排的运行效果。
- 本修复没有改变 `TodoProjection` 的日期窗口、重复事项生成规则、完成筛选、提醒语义或 Store 数据。因而不替代对这些投影边界的独立 parity 验收。
- lint 有 52 个 warning，0 个 error；本 Issue 未清理其他模块警告。
- S0 与皮肤分离工作继续 **IN PROGRESS**。未切换默认皮肤、未移除 Native recovery，也未声称 Release QA/cutover GO。

## Follow-up — Compose 分页回归的空闲同步（2026-10-29）

复跑完整 Gradle 门禁时，`memoPagingContinuesTheSortedProjectionWithoutStoreWrites` 在第二页断言处超时。独立重跑可复现。诊断显示 Memo 页已投影 250/251 行，Host 收到 `LoadMoreMemo` 后 `stateRevision` 从 3 增至 4，但测试在 Compose 完成重组前开始读取 Host snapshot，仍看到 250 行。

只在该集成测试的 `LoadMoreMemo` action 后加入 `ui.waitForIdle()`，没有修改生产分页逻辑。隔离用例随后 PASS；包含 251 项 Host 数据、Studio Mock occurrence toggle 新回归的完整离线门禁 **624/624 tests、84 suites、0 failures/errors/skips**，Debug 构建和 lint 通过。

证据位于 `.local/android-test/reports/separation-s0-timeline-drag/memo-occurrence-toggle-cdp/`：`memo-paging-isolated-20261029.log` 记录首次失败；`memo-paging-revision-diagnostic-20261029.log` 记录 action 已执行但 Compose snapshot 未稳定；`memo-paging-idle-fix-focused-20261029.log` SHA-256 `C6C41B84FCBC7694C2DE838F665B62DD8B2F8DA7CFA5F1E99815AFC777DCD430` 记录修正后的隔离 PASS；`gradle-full-final-20261029.log` SHA-256 `352DBE0C2BBFEB5E270FE12B5B72D47845E7A9349A745414D3B1F9C87DB15142` 记录全量 PASS。

测试同步修正独立提交 `1965f93030444cead1643c42cb8500ad89d9bb46`（`test(分离): 等待 Memo 分页状态刷新`），已推送。此结果只校正 Robolectric/Compose 测试观测时点，不扩大 Android WebView 或 Native/Clear parity 签核。
