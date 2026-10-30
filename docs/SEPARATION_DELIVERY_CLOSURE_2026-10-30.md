# PowerCal 皮肤优先分离阶段交付收口（2026-10-30）

## 交付结论

已生成可独立验收的阶段候选包。代码基线为 `c54cb47a2b597a39dc4d856c74532d83c9be16f9`，即分离主线 `codex/separation-s0-timeline-drag`。该基线是 `feat/skin-first-separation` 的提交 `cc31026f81d4df6704dd89704a4ee93f030a5c00` 的 119 个提交后代。`codex/separation-s0-inventory` 的 22 个提交已包含在该主线中。

本交付不代表 100% Native/Skin 功能等价，也不是 Release QA 或 cutover GO。Native 回退仍可用，默认皮肤没有切换；实体/OEM 验收由所有者执行。

交付文件位于 `.local/android-test/deliveries/powercal-separation-stage-20261030/`：

- `PowerCal-stage-debug.apk`：可安装的 Debug APK，包名 `dev.powerfulcalendar`，版本 `0.9.5-rc.1`。APK 签名校验通过；它不是商店发布签名包。
- `PowerCal-Clear.pcskin`：Clear v3 皮肤包，与 APK 内置的 9 文件、394086 字节资源一致。
- `PowerCal-source-c54cb47.zip`：基于同一代码 SHA 的 Git 源码归档，含可运行的 Skin Studio。
- `README-DELIVERY.txt`、`manifest.json`：安装、运行 Studio、哈希和验收摘要。

Skin Studio 已从归档源码根目录启动验证：`python tools/skin-studio/skin_studio.py serve --port 18765`。`GET /` 返回 HTTP 200，页面标题为 `PowerCal Skin Studio v3`；`GET /api/template-v3` 返回 HTTP 200 和 654094 字节模板。验证后已停止服务。

## 统一基线构建与回归

所有下列构建和回归均在干净 detached worktree 的提交 `c54cb47a2b597a39dc4d856c74532d83c9be16f9` 上执行。没有把其他 worktree 的未提交文件带入构建。

| 验收 | 命令/方式 | 结果 |
| --- | --- | --- |
| 源码边界、JVM、Debug/AndroidTest APK、Lint | `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain` | `BUILD SUCCESSFUL`，88/88 Gradle tasks；727/727 JVM 测试、100 suites、0 failure/error/skip；Lint 0 errors、53 warnings |
| Skin Studio | `python -m unittest discover -s tools/skin-studio/tests -v` | 61/61 PASS |
| Clear 资源与 APK 同源 | `python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk` | PASS；9 files、394086 bytes；APK 内外 SHA-256 相同 |
| JavaScript 语法 | `node --check`：`v3-studio.js`、Clear `app.js`、`edit.js`、`title-folding.js` | 4/4 PASS |
| APK 签名 | Android SDK `apksigner verify --verbose` | PASS；APK v2 signature verified |
| Studio 本地启动 | HTTP smoke：`/` 和 `/api/template-v3` | 2/2 HTTP 200 |

详细证据位于 `.local/android-test/reports/separation-delivery-closure-20261030/`：

- `full-gradle-c54.log` SHA-256：`52898C4B1EA7C91010CC8AE85DFB75C8CBA5F33D88A4EE70FA326D0D8B5472B5`
- `skin-studio-python.log` SHA-256：`5B8ADC9FAC83C1F8B5F5C5EAE54C7CB04FC354590CB113F59C8D2D5A601F9E43`
- `clear-apk-verify.log` SHA-256：`FF97FE166A92B6674BDA6591DFE18FC976239EE964DFB8E91842B6495C742408`
- `studio-server-smoke.txt` 记录 Studio HTTP 验收。

| 文件 | SHA-256 |
| --- | --- |
| `PowerCal-stage-debug.apk` | `27B32DB71211BECFEAED06D1C8CBA61DA72B098CF6515AE34608665622633E27` |
| `PowerCal-Clear.pcskin` | `8F7F5680BFD27976B2792AAB06D051BAF0F03F4A914636EA73E82E48431F8035` |
| `PowerCal-source-c54cb47.zip` | `900A764B669F49791BBB8363F362E5A39280CE4882CAC0A25F531C77040A6E9F` |
| 构建出的 AndroidTest APK（仅供复核） | `72101800F5F580291B2019DE85F1B8019DB56CE799C196B6605A90E341D87A84` |

本次完整 Gradle 门禁没有连接模拟器，也没有运行实体设备测试。AndroidTest 源码已编译并打包；这不能替代 connected instrumentation。

## SEP-01～SEP-16 当前状态

下表中的 `TESTED` 只表示列出的具体路径有证据，不表示该 SEP 已完成完整 Native/Skin 等价回放。详细逐案历史仍以对应 Issue 报告和原始矩阵为准；原矩阵目前把完整等价状态保留为 NOT TESTED。

| SEP | 实现状态 | Studio 支持状态 | 真实验收证据与缺口 | 当前结论 |
| --- | --- | --- | --- | --- |
| SEP-01 时间轴 | 几何、左右泳道、缩放/复位、贪心标题折叠已实现；拖动改期代码存在，真实触屏拖动尚未通过 | 可编辑 HTML/CSS/JS；预览手势只是 Mock，不能证明 Android 拖动 | API 35 `TimelineGreedyFoldingWebViewInstrumentedTest` 2/2 PASS；有真实矩形与滚动/缩放轨迹。新建的 S0-TL-04 仪器测试两次得到 `lastResult=closed` 而非预期 Host 结果，观察到的 Bridge post 只有 editor.open/editor.close；第三次 AVD 在 JUnit 结果前断连。见 `PC-ISSUE-SEPARATION-CLEAR-TIMELINE-GEOMETRY.md`、`...GREEDY-LABEL-FOLDING.md`、`...TIMELINE-DRAG.md` | **BLOCKED**：标题与几何子项已测；触屏拖动待查，完整 SEP 未签核 |
| SEP-02 月历 | 日期导航、选日创建、重复实例打开已有实现 | 源码可编辑；无通用组件树编辑器 | 月历选日与 occurrence-open 的定向回归见 `PC-ISSUE-SEPARATION-CLEAR-MONTH-SELECTION.md`、`PC-ISSUE-SEPARATION-CLEAR-MONTH-OCCURRENCE-OPEN.md`；完整双轨回放/实体设备未做 | **TESTED**：仅列出的月历路径 |
| SEP-03 Memo/Todo | Memo 投影、分页、逐实例完成和打开已有实现 | 可编辑页面源码；没有完整结构化 Memo IDE | 分页、fallback、实例 toggle 的定向报告：`PC-ISSUE-SEPARATION-MEMO-PAGING.md`、`...MEMO-OPEN-FALLBACK.md`、`...MEMO-OCCURRENCE-TOGGLE.md`；没有完整操作/恢复等价回放 | **TESTED**：子路径已测，完整 SEP 不完整 |
| SEP-04 新建事件 | 四种类型、统一默认值、临时编辑器和 meaningful-diff 保存路径已实现 | 四类型选择器和字段源码可编辑；Studio Host 是不写 Store 的 Mock | Compose/Host 与 API 35 Clear WebView 的新建和零写入关闭回归见 `PC-ISSUE-SEPARATION-EDITOR-CREATE-SESSION.md`；四种类型有固定时钟测试；完整 Native/Skin 回放与实体设备未做 | **TESTED**：主要新建路径已覆盖，完整等价未签核 |
| SEP-05 正式编辑 | EditSession、版本检查、类型转换、锁定和 Host 审查路径已实现 | 编辑源码可改；Studio 的审批只是可取消 Mock | JVM/Compose 类型转换与 revision 回归见 `PC-ISSUE-SEPARATION-RECURRING-WEB-EDITOR.md`、`PC-ISSUE-SEPARATION-FORMAL-EDITOR-REVISION-RECOVERY.md`；当前 API 35 WebView 类型转换仍未测 | **CODE COMPLETE**：真实设备缺口保留 |
| SEP-06 重复实例编辑 | occurrence key 与 single/future/series 范围及 Host 复核已实现 | 范围控件在 `edit.js`；预览只模拟审批 | 固定重复数据的 SQLite/JVM 三范围回归及 API 35 单实例 WebView 路径记录于 `PC-ISSUE-SEPARATION-RECURRING-WEB-EDITOR.md`；其他事件种类与恢复组合仍缺 | **TESTED**：部分范围已测，整体不完整 |
| SEP-07 正式删除/恢复 | 正式删除走 Host 审查，草稿冲突/恢复路径已有实现 | 可编辑删除控件；Studio 只显示不写入的确认 Mock | Host/JVM 与 Studio/CDP 通过；API 35 删除仪器运行在 ADB 断连前没有 JUnit 结果。见 `PC-ISSUE-SEPARATION-WEB-FORMAL-DELETE.md` | **BLOCKED**：Android WebView 删除签核缺失 |
| SEP-08 草稿卡组 | Deck 的选择、patch、提交、丢弃、撤销、冲突和搜索导航已实现 | `edit.js` 可编辑；复杂卡片重组仍有限 | Deck 搜索/导航、生命周期和冲突恢复的定向报告：`PC-ISSUE-SEPARATION-DRAFT-DECK-SEARCH-NAVIGATION.md`、`...DRAFT-DECK-LIFECYCLE.md`、`...DRAFT-CONFLICT-RESTORE.md`；全操作重启回放未完成 | **TESTED**：多子路径通过，完整恢复矩阵未签核 |
| SEP-09 AI 聊天 | Host 保存历史、编辑/回退、附件选择与重发路径已实现 | 控件源码可编辑；Studio Mock 不调用 Provider、不读用户文件 | JVM/本地 stub 与 Chrome Studio Mock 有证据，见 `PC-ISSUE-SEPARATION-AI-CHAT-HISTORY.md`、`PC-ISSUE-SEPARATION-AI-CHAT-ERROR-RECOVERY.md`；实际 Android picker/OCR/Provider/WebView 长流程未齐 | **TESTED**：Host/Mock 子路径通过，生产 AI 全流程不完整 |
| SEP-10 AI 提案审查 | CREATE/REPLACE/DELETE/STAGE/REJECT/SUPPLEMENT 的 Host 审查与版本复核路径已实现 | Studio 可预览决定流程，但确认是非写入 Mock | API 35 Clear WebView 对部分决定有真实 Host 测试，且 JVM stub 测试覆盖取消/重试；Issue：`PC-ISSUE-SEPARATION-AI-WEBVIEW-DECISIONS.md`、`PC-ISSUE-SEPARATION-AI-REPLACE-DELETE.md`、`PC-ISSUE-SEPARATION-AI-MULTITURN-SUPPLEMENT.md`。Live provider、进程死亡和完整组合未测 | **TESTED**：关键子路径已有设备证据，完整 SEP 未签核 |
| SEP-11 设置/权限/数据工具 | 选定权限、导入导出、备份和提醒诊断命令由 Native Host 提供；完整可替换设置页未实现 | Studio 不提供完整设置/凭据表面 | API 35 的权限/提醒/包管理有定向报告，如 `PC-ISSUE-SEPARATION-NOTIFICATION-PERMISSION.md`、`...EXACT-ALARM-SETTINGS.md`、`...MANUAL-REMINDER-RECONCILE.md`；外部 Provider 和实体权限状态未齐 | **INCOMPLETE**：当前保留原生工具入口 |
| SEP-12 导航/对话框/Picker | 页面路由已接入；敏感确认、系统选择器和紧急恢复仍在 Compose/Android | 可改 HTML/CSS；不能替代所有原生 overlay/Picker | 现有 Compose/Bridge 子测试；无完整导航、可访问性和异常恢复双轨回放 | **INCOMPLETE**：Native fallback 是当前设计的一部分 |
| SEP-13 工厂包/第三方包 | 工厂 Clear 与已安装 v3 共用校验、registry 与 WebView Host 路径 | v3 导入/编辑/导出/校验及源码往返可用 | Package/Studio 往返与 APK 资源来源校验见 `PC-ISSUE-SEPARATION-PACKAGE-DELIVERY.md`、`PC-ISSUE-SEPARATION-S0-PKG-01-CLEAR-PROVENANCE.md`、`...S0-PKG-02-ROUNDTRIP.md`、`...S0-PKG-03-EDIT-ROUNDTRIP.md`；本次 APK 与 Clear 资产 SHA 一致 | **TESTED**：包路径通过；恶意包/OEM 与全面 parity 未签核 |
| SEP-14 皮肤切换/故障恢复 | Native 回退与若干 checkpoint/error recovery 路径已实现 | Studio Preview 使用 Mock；完整 WebView 失败注入支持不足 | 见 `PC-ISSUE-SEPARATION-SKIN-CHANGE-CHECKPOINT-RETRY.md`、`...SKIN-FAILURE-EDITOR-RECOVERY.md`、`...CORRUPT-SKIN-BOOT-RECOVERY.md`；进程死亡、攻击性包和设备恢复矩阵未闭合 | **INCOMPLETE**：fallback 保留，恢复签核未完成 |
| SEP-15 Studio 项目/资产编辑 | v3 CLI 提供 inspect/read/edit/build/validate；浏览器支持原始 project JSON、源码和素材往返 | Studio Python 全套 61/61、HTTP 启动通过；完整语义组件树与页面结构 IDE 尚不存在 | Python、Node 语法、Studio HTTP 与 Clear bundle 校验本轮 PASS；项目/源码/素材往返见 `PC-ISSUE-SEPARATION-STUDIO-STRUCTURAL-COMPONENT-OPS.md`、`PC-ISSUE-SEPARATION-STUDIO-SOURCE-CAPABILITIES.md` | **INCOMPLETE**：编辑切片已测，完整 IDE 能力未实现 |
| SEP-16 所有正式写入均需可信确认 | 正式编辑、Deck 发布、重复事件和选定 AI 发布路径由 Host 私有快照与 Compose 确认保护 | Studio 的确认只是 Mock，不授予真实权限 | JVM 与部分 API 35 WebView 写路径有证据；AI 决策全组合、所有类型/操作、生命周期恢复及完整 Native/Skin replay 未覆盖。见 `PC-ISSUE-SEPARATION-TRUSTED-FORMAL-WRITES.md` 与 AI 提案 Issue | **INCOMPLETE**：可信 Host 路径存在，但全写入面尚未签核 |

## Git、分支与 worktree 收口审计

审计时 `origin/feat/skin-first-separation` 位于 `cc31026`；推送候选是其严格后代，采用普通 fast-forward，不需要强推或冲突覆盖。关键分离分支：

| 分支 | 审计 SHA / 远端状态 | 处理 |
| --- | --- | --- |
| `feat/skin-first-separation` | 审计时 `cc31026`；目标 worktree 有未提交 progress 内容 | 保留该文件；候选主线在验收后正常快进推送 |
| `codex/separation-s0-timeline-drag` | `c54cb47`，与 origin 一致 | 本阶段候选主线；只新增本报告提交 |
| `codex/separation-s0-inventory` | `057b39e`，与 origin 一致 | 已是候选主线的祖先，22 个提交已包含 |
| `codex/separation-legacy-import` | `d43bac3`，与 origin 一致，worktree clean | 从旧共同基线独立分叉；与候选主线有大量重叠源码，未整支合并 |
| `codex/separation-skin-settings` | `c816906`，与 origin 一致 | 从旧共同基线独立分叉；该 worktree 有未提交 `WebSkinManagementInstrumentedTest.kt`，保留不动 |
| `codex/pc-d05-production-qa` | `c02c766`，与 origin 一致，worktree clean | 保留独立 QA 分支，不并入分离候选 |

`legacy-import` 与 `skin-settings` 分别有 85 和 77 个相对旧共同基线的独立提交；它们与当前候选分支并非祖先关系，差异触及 `PowerCalApp.kt`、`PowerStore.kt`、`WebSkinEditBridge.kt`、Clear 包和验收文档。按“不强行合并冲突”的要求，本轮不 cherry-pick 未审定的整组差异，原分支和远端提交均保留供下一阶段单独审查。

检查到的其他 worktree 与未提交内容均未清理、未暂存、未提交：

- 主工作树 `main`：`SkinPackageManagerTest.kt`、冻结验收 CSV、D05 instrumentation、`DraftDeckFeedbackTest.kt`、`design/`、`PC-OBS-005`、`.local/` 与 Python 缓存有未提交/未跟踪内容，全部保留。
- 干净 worktree：`issue-greedy-label-folding/pre-fix-worktree`、`pc-d05-prod-572cf10`、`phase1-72372ad`、`phase1-review-d600aaf`、`phase1-verify-6ddf9b9`、`skin-eol-fresh-1a7de91`、`separation-s0-inventory`、`legacy-import-fix`。
- 有未提交内容的 worktree：`ai-error-studio-verify-20261027`、`p02-today-512cfd5`、`pc-issue-166-controller-current`、`pc-issue-172-final-7ceb7fa`、`pc-issue-173-glass-fallback`、`pc-phase1-dst-earlier-offset`、`phase1-task-7ceb7fa`、`sep11-tracka-host-projection`、`sep11-trackb-reminder-diagnostics`、`skin-first-separation`、`skin-settings-shell`、`skin-web-kind-selector-15b5480`。它们的 AI、Phase 1、提醒诊断、Studio、设置和测试源码修改均保持原样。
- 当前 `separation-s0-timeline-drag` worktree 多了未提交 `TimelineDragWebViewInstrumentedTest.kt`，只用于本轮 S0-TL-04 诊断；它未进入统一 SHA、APK、源码归档或提交。两次 API 35 运行没有通过断言，之后 AVD 断连，因此明确为未验收，不纳入阶段结论。
- 目标 `skin-first-separation` worktree 的 `docs/agent-handoff/PROGRESS.md` 有真实未提交差异（约 871 行新增/892 行删除，文件时间为 2026-10-27）；它与候选提交中的 progress 文件冲突风险高，保持原样。另有 4 个工具资源路径显示 Git 状态变化，但其工作树 blob 与 HEAD 相同、无实际文本 diff。

## 剩余工作与阶段边界

1. S0-TL-04 真实 WebView 触屏拖动：诊断并通过 Android 仪器回归；本次不将失败项降格为 PASS。
2. 审查 `legacy-import`、`skin-settings` 分叉中相对候选主线的独立差异，再选择性整合。
3. 补齐 SEP-01～SEP-16 的完整 Native/Skin 同夹具回放、异常/进程恢复和可访问性验收；实体/OEM 测试由所有者执行。
4. SEP-15 完整组件树/结构化页面编辑、SEP-11/12 原生设置/Picker/对话框等价仍是后续能力，不阻塞这个保留 Native 回退的阶段候选。

**阶段结论：可交付阶段候选；整体 Native/Skin parity、实体设备签核和 cutover 继续 HOLD。** 本轮完成后停止自动扩展任务。
