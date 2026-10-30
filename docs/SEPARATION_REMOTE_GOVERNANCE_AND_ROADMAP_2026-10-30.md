# PowerCal 分离阶段：远端治理与后续交付路线图

**核对日期：2026-10-30**
**实施状态：远端 Git 分支整理已完成；功能分离仍未达到 cutover GO。**
**权威代码与交付记录：** `main` 保持核心冻结；分离开发以 `origin/feat/skin-first-separation` 为唯一集成主线。
本文件是下一轮 Codex/Agent 接手入口，不能取代带测试证据的 `SEPARATION_DELIVERY_CLOSURE_2026-10-30.md`、`SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md` 和各 Issue 报告。

## 1. 当前版本基准及实际证据

| 用途 | 指向 | 解释 |
| --- | --- | --- |
| 原生业务核心 | `b1f7629f0c52812e1887443872f43aa6c49908c4` | 2026-10-27 核心冻结的应用业务源码 |
| Core 冻结标签 | `core-freeze-2026-10-27` | 指向核心冻结签核提交 `af229d9` |
| 交付构建源 | `6cd755621cc1c599dd72871d4e1eb77442998d4a` | 727 JVM / 61 Studio Python 的阶段候选构建、APK、皮肤和源码 ZIP 对应此 SHA |
| 阶段标记 | `skin-separation-stage-2026-10-30` | 指向 `3d099d37d44fe82e72435517f0325d23cc7239e7`，后两笔只有阶段交付报告修改；非 Release |
| 分离开发主线 | `origin/feat/skin-first-separation` | 集成和后续新功能唯一目标分支；本文件提交前为 `3d099d3` |

阶段构建在 `6cd7556` 报告 **88/88 Gradle Tasks、727/727 JVM（100 suites）、Lint 0 Error、Skin Studio Python 61/61、Clear APK 包同源、JS 4/4 语法检查、APK v2 签名验证**；这些是历史证据，本次远端整理**没有重新运行测试或制作新 APK**，不得写成新 HEAD 再次完整跑过。

`main` **不是**分离集成分支，仍保留完整 Native 回退。默认皮肤切换和完全移除原生 UI 均未经授权。分离目标依然是**完整功能等价 + Skin Studio 可以无损编辑/重构皮肤任一部分 + AI 根据规范自动化制作皮肤**；当前正式 cutover = HOLD。

## 2. 远端分支整理结果

整理前远端共 9 条分支。对每个拟删除分支先核验远端 SHA，再以 Git `merge-base --is-ancestor` 确认已包含在分离主线中；没有 force push、重置、删除本地分支或 worktree。

**保留的 4 条远端分支：**

| 远端分支 | 整理时 SHA | 治理结论 |
| --- | --- | --- |
| `main` | `af229d9` | 冻结核心；不接分离试验提交 |
| `feat/skin-first-separation` | `3d099d3` | 唯一分离集成主线；后续应逐里程碑普通快进推送 |
| `codex/separation-legacy-import` | `d43bac3` | **未合并，保留评审**；不能直接删/强并 |
| `codex/separation-skin-settings` | `c816906` | **未合并，保留评审**；其 worktree 还有未提交 AndroidTest |

**已删除的 5 条远端分支（均为已合并或完全重复）：**
`codex/separation-s0-timeline-drag`（与交付标签同 SHA）、
`codex/separation-s0-inventory`、
`codex/pc-d05-production-qa`、
`feat/r1-import-ai-compat`、
`integration/rc2-review-20261020`。

删除的仅是远端**分支引用**。历史提交仍由当前分离主线祖先链保存，本地分支与各 worktree、未提交试验文件没有删除。**不要在旧 `codex/separation-s0-timeline-drag` 工作树重新推送该已退休的远端分支**，需要进一步开发请另建清晰命名分支或从当前集成主线开新隔离工作树。

两条未合并分叉各约有 85 / 77 个相对当前交付主线独有的历史提交，且跨越旧共同基线；**这个计数不等于 85 / 77 个独立功能**。它们与当前 Host、PowerCalApp、Skin、Studio 和文档存在重叠，不能整支合并来“解决分支数量”。

**待审分叉流程（先于删除这两条分叉）：**
1. 逐分支列出独有功能、文档、测试及它们对应的 SEP 项，识别当前主线已有的等价实现。
2. 将候选变更分类：已有且更完整 / 独立有效 / 与冻结核心冲突 / 过时或已替代。
3. 对独立有效项单独开 Issue，在临时集成分支上做最小 cherry-pick 或重新实现，并同时补 Runtime、Studio、文档及回归。
4. 在同一 SHA 上通过受影响定向门禁与构建，正常合入 `feat/skin-first-separation`；明确证据后才允许退休分叉，**不强合**。

**本地治理限制：** 历史记录共有 23 个 worktree，多个含未提交资料或诊断测试。此次只整理远端，不清空本地目录；任何未来本地减量必须按每个 worktree 的未跟踪文件、修改、日志、实验数据、活动进程逐项核对，不得用 `git clean` / `reset --hard` / `worktree prune` 盲清。

## 3. SEP-01～SEP-16 的下一步真实工作

以下来自阶段交付报告，`TESTED` 仅代表**列出的局部路径**，不是整个 SEP 的 Native/Skin 等价 PASS：

| SEP | 本阶段状态 | 下一轮验收目标 |
| --- | --- | --- |
| SEP-01 时间轴 | **PARTIAL / HOLD**（提交 `10da1cc` 的 API 35 生产 WebView 触摸→Host→Store 拖动闭环 1/1；缩放后复位触摸 2/2，立即复位重复 3/3。完整 Native/Clear 配对、TalkBack 与实体/OEM 未签核） | 完成相同事件/相机/时区的 Native/Skin 配对回放；保持时间、刻度、双翼和贪心标题；补设备矩阵 |
| SEP-02 月历 | 部分路径 TESTED | 选日、新建、打开重复实例、返回与状态恢复双版本回放 |
| SEP-03 Memo/Todo | 部分路径 TESTED | 翻页、完成状态、恢复、重复和极端内容对照 |
| SEP-04 新建 | 主要路径 TESTED | 四类型、不同入口、默认时间、无 Diff、重建和 DST 全流程双版本回放 |
| SEP-05 正式编辑 | **CODE COMPLETE**; Native formal editor shell in `:native-skin`; Host Compose/Robolectric/full JVM tested; device not signed off | Android 真实设备编辑、类型转换、冲突与恢复 |
| SEP-06 重复实例 | 部分范围 TESTED | 单次/此后/整个系列，occurrence key、身份、提醒和重启后对照 |
| SEP-07 正式删除 | **PARTIAL / HOLD**（API 35 生产 WebView、Host Compose 与 Store 的重复系列取消/批准闭环 1/1；该测试用 DOM 点击与 Compose 语义触发，不是触摸验收） | 补真实触摸/TalkBack、并发版本冲突、恢复工作流与 Native/Skin 配对；不能据此签核整项 |
| SEP-08 草稿卡组 | Native Compose renderer **CODE COMPLETE** in `:native-skin`; focused Host/Compose cases TESTED; overall SEP remains PARTIAL | 批次导航、patch、flush、提交/拒绝、撤销、IME 与异常恢复的完整双路径回放；AndroidTest 设备执行 |
| SEP-09 AI 聊天 | 部分路径 TESTED；Native 图片授权、OCR 核对与提取文件/ICS 预检视图已 CODE COMPLETE 于 `:native-skin`，focused Compose/Host 附件及全量 JVM TESTED；设备 NOT TESTED | 真正 Picker/OCR 权限与设备闭环、记录持久化、错误/重试及可用免费 Provider |
| SEP-10 AI 提案 | 部分路径 TESTED；Native 提案审核卡、批量授权、卡住批次强制解除审核及锁定期间只读事件详情均 CODE COMPLETE 于 `:native-skin`；本路径 focused Compose 2/2，当前全量 JVM 774/774 TESTED；Android Emulator NOT TESTED（无 AVD，SDK emulator package metadata 缺失） | 四按钮、完整 CREATE/REPLACE/DELETE、多轮、不完整提案及跨进程恢复；正式发布只经 Host 授权 |
| SEP-11 设置/权限 | **INCOMPLETE**；Native 个人资料、AI 配置卡片（API key 输入仍为 Host slot）、首次系统日历导入提示、旧数据库迁移/旧备份确认展示已 CODE COMPLETE 于 `:native-skin`；AI settings/profile/protected-slot 定向 7/7，当前全量 JVM 778/778 TESTED；AI 存储与密钥仍由 Host 保管；Android Emulator NOT TESTED | 设置页面完整皮肤化，敏感能力经受信 Host，覆盖权限、ICS、备份、提醒诊断与设备验收 |
| SEP-12 导航/Picker | **INCOMPLETE**; Native root/notice/modal/system Picker renderers CODE COMPLETE in `:native-skin`; Picker Robolectric + full JVM tested | 页面等价、多窗口/IME/返回导航和 Android 设备 Picker 验收 |
| SEP-13 包格式 | 有限路径 TESTED；Native 设置页皮肤启用/删除确认已 CODE COMPLETE 于 `:native-skin`，focused Compose、Native 设置与导出路径 TESTED；设备 NOT TESTED | 官方与第三方包同权限，同源构建、导入/导出/升级及危险包检查 |
| SEP-14 切换/故障恢复 | INCOMPLETE；API 35 Clear WebView renderer 终止后切回 Native 已 1/1 TESTED（89e6cd3）；Clear WebView 的干净编辑器 Activity 重建零写入、脏标题重建后恢复唯一待处理草稿 1/1 TESTED（测试提交 5d6b421；设备证据见 .local/android-test/reports/sep14-activity-recreation-20261031/）；旧数据库迁移/旧格式备份确认路径有 JVM 证据 | 冷进程死亡/重启、renderer 退出时脏编辑恢复、损坏/脚本失败、升级失败、设备重启数据保持及实体/OEM 验收 |
| SEP-15 Skin Studio | **INCOMPLETE**；浏览器现可显式编辑三项 Host 能力请求，源/资源回归 62/62 与 Chrome 导出重导入通过 | 完成全定制内容的无损编辑/重构、可视结构能力、绑定及 AI 工作流；跨皮肤与 Android 行为仍需验证 |
| SEP-16 可信写入 | **INCOMPLETE**；API 35 Clear WebView AI proposal 当前 3/3 TESTED（local stub）：CREATE Host 批准/取消、REPLACE/DELETE Host 批准、STAGE/REJECT/SUPPLEMENT；普通编辑/新建/重复事件路径有既有 AVD 证据 | 四类事件与所有增删改/草稿操作的完整 Host 拒绝/授权对照、版本冲突、AI 多轮/部分失败恢复、Native/Skin 全量回放及设备生命周期 |

当前 **没有任何一项可以仅凭阶段报告认定为完整 100% Native/Skin 等价签核**。

## 4. 下一阶段四个有明确退出条件的里程碑

### M1 — 先关真正阻碍日常使用的交互和授权缺口

优先项：**SEP-01 触屏拖动、SEP-07 WebView 正式删除、SEP-16 可信写入的空洞**；同步保护 SEP-04/05/06/08。

- 修复前构造可以重现的真实 Android WebView 输入轨迹，测试 Host 收到对应动作及保存/取消结果，而不只是判断屏幕能点击。
- 测量冻结 Native 与 Skin 的操作结果差异，保存具体事件身份/版本/提醒调度证据。
- 每个新增交互都同步支持 Skin Studio 定位、源码修改、预览、导出导入、AI CLI 查找与可执行说明。
- **退出条件：** SEP-01 和 SEP-07 关键失败场景有目标设备/AVD 可追溯 PASS；SEP-16 对这些正式写入操作的拒绝与授权两条路径 PASS；其余失败留明确证据。

### M2 — 关系统页面与灾难恢复的阻塞

优先项：**SEP-11、SEP-12、SEP-14**；同步补 SEP-09/10 外部入口。

- Settings、权限、文件、备份、ICS、AI 设置和皮肤管理全部提供真正可达的皮肤 UI；危险确认仍由 Host 提供受信界面。
- 恢复清单必须覆盖损坏皮肤、错误脚本、WebView 崩溃、升级失败、正在编辑/存在草稿、进程死亡与设备重启。
- **退出条件：** 用户日常操作不依赖大块旧 Native 页面；独立恢复入口在故障注入后可触达、用户业务数据不变；公开的能力声明与 Runtime/Studio 相符。

### M3 — Studio 从源码面板走向完整皮肤 IDE，文档与 Agent 自动化同步

优先项：**SEP-15**，并回归 SEP-13。

- 完整源代码/资源编辑必须覆盖任意可定制内容；可视化与结构编辑不必强制理解所有用户代码，但必须无损保留未知节点与扩展。
- 支持页面/组件树的创建、移动、删除、重组与事件绑定；任意源文件、图片、脚本、CSS、Manifest/权限均有受控修改和无损导出回入。
- 人与 Agent 操作同一份 canonical project；CLI 提供 inspect/read/edit/build/validate、稳定 JSON 回执、示例和错误码。
- 引入文档与 API/Schema/CLI/示例的自动交叉校验，确保新 Agent 阅读后可独立改造皮肤、运行测试和打包。
- **退出条件：** 官方 Clear 与一个结构完全不同的自定义皮肤都能完整导入、任意重构、预览、导出、重新导入及设备运行；业务权限未扩大，模拟 Host 不算真实设备。

### M4 — 全部 SEP 的冻结版双路径验证和独立 Cutover 决策

- 在完全隔离的测试数据库里，使用**相同输入、相同时钟/时区、相同 Provider 响应**分别操作冻结 Native 和新的 Skin。
- 比较事件、版本、occurrence、草稿、提醒、AI 审核、导入/导出、失败和重启结果；保留用户可触达功能入口证据。
- 同步测试 Android WebView、不同屏幕/字号、IME、TalkBack、减少动画、离线和 OEM；无设备就保留 NOT VERIFIED，绝不冒充 PASS。
- **退出条件：** 必需功能差异全关闭，Skin Runtime 与 Studio 均在同一 SHA 上构建验证，故障恢复和回滚证明成立，Owner 独立批准切换。否则继续 HOLD，不删除完整 Native fallback。

## 5. Codex 以后如何工作

- 进入 `feat/skin-first-separation` 的**最新远端 HEAD**，而不是停在旧的本地 `cc31026`，更不能在 dirty 的原工作树上直接 reset/pull 覆盖。
- 每次只领取一个有明确验收出口的 M1/M2/M3/M4 子项，使用独立 worktree 与任务分支；测试、Studio 支持、CLI/文档更新**双轨随提交交付**。
- 单项完成后正常合并/快进到唯一主线；提交前检查远端是否有其他 Agent 推进，冲突先对账。
- 用 **CODE COMPLETE / TESTED (部分) / BLOCKED / INCOMPLETE / FULL PARITY PASS** 明确区分状态。不要因为 727/727 JVM、61/61 Studio 或大量提交就宣称完整分离。
- 任务结束必须报告最新 SHA、已合并范围、失败场景、测试命令/日志与下一项；不可无限扩展新问题，不以每次发现新优化为由无限延迟交付。
- 继续保护 `main` 的验收 CSV、`design/`、未提交 D05/Studio/Timeline 拖动测试和用户数据库/AVD；不清理历史本地 worktree，除非逐项确认归属和可恢复性。

**官方当前结论：远端治理完成；阶段候选可验收；完整 Skin-First、Studio IDE 与全部 Native/Skin parity 仍未 GO。**
