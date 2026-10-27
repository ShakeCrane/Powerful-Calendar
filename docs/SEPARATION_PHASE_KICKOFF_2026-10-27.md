# PowerCal 分离阶段开工基线｜2026-10-27

状态：**分离开发入口已获 Owner 批准；尚未执行默认皮肤切换或删减原生 UI。**

核心范围冻结裁决：docs/CORE_FREEZE_OWNER_SIGNOFF_2026-10-27.md。应用代码冻结点：b1f7629f0c52812e1887443872f43aa6c49908c4。

## 目标与唯一工程边界

目标：把完整 PowerCal 的**展示层**从核心业务与 Android 持久化、系统集成逻辑中分离，最终由一个可直接编辑、导入导出的 .pcskin v3 自包含资源包构成普通产品 UI；Skin Studio 编辑的源码与 Android 实际加载的皮肤资源必须同源。PowerCal 功能仍由独立、受控的 Host 拥有。

明确禁止“重新写一个日历”：所有保存、删除、批次授权、冲突处理、AI 解释、重复规则、提醒排程、备份导入导出、身份与数据库事务**只能复用既有核心权威服务**。WebView 脚本是缺乏特权的数据展示面，永远不能仅凭 ID、字符串或自身声明绕过 Host 验证。

迁移期间必须保持**原生回退 UI**；未经独立完整验收，不切工厂默认皮肤，不删除原生页面，不触碰现有用户皮肤选择/本地数据。最终只保留最小独立恢复入口的设想属于未来 S9/S10 的独立裁决，非本阶段启动动作。

## 当前已有基础（2026-10-27 查验）

- 皮肤技术并存：v1 静态包、v2 受限声明式包、v3 自包含 Web UI 包、可信编译式 Compose Renderer。**最终 Skin-First 的目标是 .pcskin v3 + 现有 Host Bridge**，不是另起平行格式，不把 v2 运行时能力误当 v3 全功能已具备。
- 官方 Clear 单一可追踪源码：tools/skin-studio/default-clear/source/；APK 内资源：app/src/main/assets/powercal-clear-default.pcskin。
- 默认 Clear 校验：python tools/skin-studio/build_default_clear.py --verify 成功；此时被校验的 APK 内资源 SHA-256 为 1a5ed8f4af23a99dfbb09582463ab007fcc3b2b5598351d76cfc508e18b51b41（372986 bytes），校验时不改资源。
- Skin Studio 单测：37/37 PASS（python -m unittest discover -s tools/skin-studio/tests -q）；Web app.js：node --check PASS。日志见 .local/android-test/reports/freeze-20261027/skin-separation-preflight.log。
- 已有 WebMessagePort v1、SkinAppShell、SecureWebSkin、WebSkinEditBridge/WebSkinStateFeed/WebSkinHost、受版本保护的 editor.* 和 deck.* 操作、最小能力与 WebView 防护。所有官方/第三方 v3 必须共享同一解析器/桥接协议及授权规则，无工厂 ID 特权分叉。
- Native 完整 UI 仍是现阶段唯一安全回退；默认 Clear 尚未完成全功能等价和真机视觉验收。
- design/ 当前为并行 Agent 的未跟踪资产，**不得修改、搬走或擅自加入版本库**。设计审查成果必须先确认版本和所有者后才可用于最终视觉。

## 分离任务顺序（每项独立 Issue + 验证）

**S0 锁定不可变基线与责任边界**：完成 Owner 签核、独立 Git 标签、独立分离开发分支；清点现有十个可替换 Surface、Native 仍承担的职责和 Web Host Bridge 的缺口；建立源码/运行时动作/真实设备三层证据矩阵。不要因尚有旧台账 BLOCKED 就重新无目标打磨核心。

**S1 核对 Skin-First 双端同源**：确保 APK 预装 Clear、Studio 编辑、Studio 导出、官方/第三方导入和重新加载读取同一语义与源文件；v1/v2/v3 向后兼容、校验 SHA 和升级失败恢复。不得把浏览器 mock 误认为设备 WebView。

**S2 完成编辑器/草稿卡组的 Host Bridge 功能闭环**：基于已有 S1–S4 文档的 editor.open/patch/save/close 和 deck.open/create/patch/commit/undo；补正式删除确认、版本冲突完整 before/after、冲突恢复和旧草稿持久化；先进性不如重启后正确性重要。草稿无 Diff 不落地。

**S3 完整 AI 聊天／审核卡组 Surface**：把“批量同意／写入草稿／批量拒绝／补充细节”、不完整提案、附件授权、错误/取消/重试、多轮任务、批次快照、忙碌锁表现为皮肤上的 Host 驱动 UI。只能由既有 AiClient/AiDraftService/审批控制器执行；JS 发送的选择只是申请，Host 重新校验并要求独立受信授权。支持未配置 Provider 的安全降级；不得静默制造正式事件。

**S4 系统设置与文件/权限工具**：Settings 全屏、AI 配置凭据保护、ICS 导入导出、备份恢复、通知权限、皮肤导入导出及错误状态。密码和私有数据库不得进入 JavaScript state；提供 Host-owned protected slots 或受控系统 UI。

**S5 Clear 视觉实施与 Skin Studio 普通用户编辑**：依据已经审过的视觉成果，一次梳理十个 Surface、抽屉/弹窗/导航、浅/深色、输入法、TalkBack、大字/低动效；保证只改背景或图标也不破坏业务。源文件与 APK 打包无漂移、样式与布局变化没有业务写入能力。

**S6 最小独立恢复壳与故障注入**：先保留完整 Native；新增/证明独立恢复路径在皮肤损坏、WebView 崩溃、主线程重新启动、进程死亡、更新和无法渲染时可操作。直到故障测试证明无需完整 Native 才有资格提出删减清单。

**S7 独立 Cutover Gate（另需 Owner 确认）**：十个入口和四类事件全行为等价；真机验证多 DPI、小屏/字号、IME、TalkBack、减少动画、深浅色、重复/DST、通知、锁屏、WebView crash/重启、皮肤切换及升级迁移；导入坏包不能丢数据；正式 Release 签名与外部 Provider、ICS 的剩余风险单独审计。通过后才允许计划默认皮肤迁移、之后再考虑删除冗余 Native。

注：以上是**接续现有 S1–S10 工程工作的分离阶段执行顺序**，不代表此前未完成项已自动闭合。

## 定义明确的 Host/Skin 禁区

| Core / Host 唯一拥有 | Skin 仅展示与请求 |
| --- | --- |
| PowerStore、持久事件、身份/version、DST/重复解析 | 事件有限只读投影、操作按钮 |
| 编辑器/Deck Controller 生命周期与数据库事务 | 输入字段、请求 ID、UI 状态与受控编辑命令 |
| AI Provider、审查来源/模型证据、批量正式授权 | 聊天、提案列表、待补全项、触发受保护确认 |
| AlarmManager/Receiver、通知权限 | 提醒信息的渲染与权限申请入口 |
| ICS/备份、凭据、系统权限、恢复 | 文件选择受保护入口与结果状态 |
| 可信安全确认、回滚、皮肤包验证与加载 | 动画、排版、图片、主题和页面导航 |

## 工程纪律和验收门禁

- 每一项改动先读路径再改，先列 Native 相应功能与 Host 命令/状态投影；不凭假定实现不存在的 API。
- 一律**加法式桥接**，避免并行 Store、重复后台管理器及第二份生命周期。
- 皮肤业务操作统一 versioned closed-schema command + host-side re-read + 版本冲突/完整差异审核；用户确认来自受信 Host，不采信伪造的前端确认标记。
- 功能先于删代码。每项新桥接都需浏览器 mock + Android JVM/Compose + 设备 WebView 按层验证。Skin Studio 37 项现有测试与默认 Clear 同源校验作为初始门槛，不取代后续测试。
- 分支从冻结 tag 基础起步；更改 app/src/main/java/dev/powerfulcalendar/core、ai、reminders、数据库迁移与现有授权语义，原则上拒绝；需要就提交独立 Issue 和 Owner 决策。
- 保存 Git commit、APK/skin SHA、自动化结果、设备复现步骤与风险矩阵；并发未提交文件不参与 staging，禁止 git clean/reset/force push。
- S7 完成前不得启用“Skin-First 出厂默认”或删除完整 Native；该动作不是本次 Owner 进入分离阶段的自动授权。

## 初始验收表（仅代表已经运行过的准备检查）

| Gate | 实测状态 |
| --- | --- |
| 冻结应用源码纯净构建 | PASS：560/560 JVM，88/88 Gradle tasks；Lint 0 Error |
| 默认 Clear APK 资源同源校验 | PASS |
| 现有 Skin Studio Python 单测 | PASS：37/37 |
| 现有 Clear Web JS 语法 | PASS |
| 完整 Web UI 功能替代 Native | NOT VERIFIED；已有明确缺口 |
| 真实设备 UI 皮肤切换/崩溃恢复 | NOT VERIFIED |
| 真机正式切默认 / 移除 Native | NOT AUTHORIZED |

下一次实施起点：**只读列出 Native→Host Bridge→Clear Surface 映射和 S2/S3 功能缺口；优先补 AI 审核卡组与草稿卡组的用户完整交互，再进行默认皮肤切换讨论。**
