# PowerCal 0.9.5-rc.1 · 冻结验收与 Issue 报告
日期：2026-10-23（北京时间）
判定：**FREEZE HOLD / 不可正式冻结**
仓库：`E:\Project\Powerful-Calendar`
主代码基线：`abde8e2900b507cf1a50730b22de8ec55e4bac38`（背景灰带补正与固定竖屏）
测试 APK：`app/build/outputs/apk/debug/app-debug.apk`
Issue 004 修复代码基线：`16e00d55435c5029e07784ec5d945dfd7fb37ac5`
最新测试 APK SHA-256：`B0AB218FF70D696E25B570F0151BEC0F58B07FE545A045EE976EBD4F1173CAE1`
设备：Issue 004 使用隔离的 Android 15 / API 35 `emulator-5556`；原 `emulator-5554` 保持运行且未操作。无物理真机。
本轮改动时间轴 UI 与回归测试；未清除模拟器/业务数据，未修改或删除 `design/`。

## 1. 本轮实际测试及结论

| 证据 ID | 测试内容 | 结果 | 证据/说明 |
|---|---|---|---|
| FQ-01 | Android JVM 单元测试完整强制重跑 | **PASS 325/325** | `.local/android-test/reports/freeze-20261023/gradle-rerun-tests.log`，32/32 Gradle 任务执行，0 failures/errors/skipped |
| FQ-02 | Android skin source boundary + Debug APK + Lint | **PASS（构建门槛）** | `gradle-full.log`；Lint 0 errors、56 warnings |
| FQ-03 | Skin Studio Python 端到端/契约与安全测试 | **PASS 37/37** | `skin-python.log` |
| FQ-04 | 四个 JS 文件语法、Clear bundle 源文件一致性 | **PASS** | `node --check`；bundle SHA-256 `1a5ed8f4af23a99dfbb09582463ab007fcc3b2b5598351d76cfc508e18b51b41` |
| FQ-05 | 新 APK 覆盖安装保留原有应用数据和 AI 密钥 | **PASS** | `adb install -r` 成功；密钥只验证存在，不读取明文；免费模型仍启用 |
| FQ-06 | 中央垂直时间轴、时间刻度、蓝色当前时间指示线 | **PASS（实机模拟器截图）** | `timeline-after-ruler-fix.png`；修复提交 `04cb056` |
| FQ-07 | 缩放 24h → 16h，双击当前时间刻度尺复位 24h | **PASS** | `axis-doubletap.json`，真实 ADB 手势与 UI 树 |
| FQ-08 | AI 简短免费模型调用 | **PASS（前轮证据）** | `docs/AI_COMMANDCODE_CONNECTIVITY_QA_2026-10-23.md`；显示 `PC_CONNECT_OK` |
| FQ-09 | AI 复杂日程提案通过免费模型 | **FAIL（一次真实超时）** | `ai-proposal-result.json` 与 UI；最终显示“AI 请求超时；没有修改日历” |
| FQ-10 | 对照临时使用用户许可的 `deepseek/deepseek-v4.1-flash` | **INCONCLUSIVE** | `flash-control.json`；操作期间误进系统日历界面，不计为模型功能通过/失败；恢复原配置，事件对象 9→9 |
| FQ-11 | 聊天记录进程重启后的持久化 | **PASS（定点抽查）** | `PC_CONNECT_OK` 和历史超时消息重新启动后仍存在 |
| FQ-12 | SQLite quick_check + integrity_check（只读快照） | **PASS** | 均返回 `ok`；读取 `powercal-v2.db`，没有修改数据库 |
| FQ-13 | 先前定时通知执行结果复核 | **PARTIAL PASS** | `executions` 事件 #7 / revision 1 / primary 为 `FIRED`，时刻 2026-10-23 09:59:00 +08:00；并无该时刻通知栏展示/声音的独立录像 |
| FQ-14 | 720×1600/320dpi 小屏、横屏与显示设置恢复 | **PARTIAL PASS** | `display-compat.json`、`small-phone.png`、`landscape.png`；进程存活且 UI 节点可见；横屏拥挤，未完成完整触控/无障碍验收 |
| FQ-15 | 连续 12 次强制停止/启动 | **PASS 12/12** | `restart-12x.json`；Key/模型配置在重启后仍存在 |
| FQ-16 | 旧版 Round 1/Round 2 数据操作 | **REUSED EVIDENCE** | 之前 11 组：Alarm/Activity/Deadline/Memo、草稿、撤销、删除、重复、Envelope 备份与恢复；不冒称本轮逐项重做 |
| FQ-17 | 标题实际矩形贪心折叠、事件几何/点击/语义保持 | **PASS（API 35 隔离模拟器）** | 8 个策略单测、5 个 Compose 测试；A/B/C/D 碰撞轨迹、修复前后截图及坐标见 [`PC-ISSUE-GREEDY-LABEL-FOLDING`](issues/PC-ISSUE-GREEDY-LABEL-FOLDING.md)；真机/TalkBack 仍未验证 |
| FQ-18 | 移除时间轴背景淡横带、固定竖屏及系统旋转请求回归 | **PASS（API 35 隔离模拟器）** | 画布背景取样 24 点 RGB 完全一致；90°旋转请求下应用仍为 1080×2400/ROTATION_0；54 套件 364 项完整回归通过。证据见 `docs/issues/PC-ISSUE-004-timeline-layout.md` 的补正记录 |

注意：`FQ-01` 的 325 为单元测试用例数，`FQ-03` 的 37 为工具测试用例数，`FQ-16` 的 11 是业务检查组。它们**不能**直接映射为冻结清单中的 209 个独立项目。

## 2. 可复现的产品 Issue

### PC-ISSUE-001 · P1 / 已修复；互操作验收 PARTIAL / HOLD · ICS 导出丢失重复规则
- **复现**：导出存在每天重复的 Activity `PC_EMU_REPEAT_06`。
- **实际**：导出的 `PowerfulCalendar-activities.ics` 中有 VEVENT，但没有 `RRULE`；无限重复系列变为孤立单次事件。
- **预期**：导出保留受支持的重复语义；若格式不支持，明示不可逆语义损失并阻止误认为无损备份。
- **证据**：`docs/ANDROID_EMULATOR_REGRESSION_QA_2026-10-23.md`（R2-ISSUE-01）；`.local/android-test/reports/regression-20261023/PowerfulCalendar-activities.ics`。
- **可能位置**：`PowerCalApp.kt::exportIcs` 与 `interop/IcsCodec.kt::export`。
- **修复记录**：`IcsCodec` 已保留支持范围内的重复规则并输出对应 VTIMEZONE，代码与单测已在提交 `50a83a0e4787503d2bf2c225d4d1091659c8d21f` 完成；本次在隔离 API 35 模拟器通过 DST 系列真实 SAF 导出和独立解析。证据、哈希及当前回归状态见 `docs/issues/PC-ISSUE-001-ICS-recurrence.md`。
- **剩余阻塞**：尚无可用的外部日历导入器及实体 Android 设备；需验证跨客户端重复/时区语义。**核心冻结仍 HOLD**。

### PC-ISSUE-002 · P1 / RUNTIME INCIDENT · 复杂日程请求触发免费模型超时
- **复现**：在已通过短消息连通性验证的 `stealth/glyph-cluster:free` 上，发送创建未来 Activity 的仅提案请求。
- **实际**：聊天超过首个观察窗口仍为“正在处理”，最终显示“AI 请求超时；没有修改日历”；未生成可审查提案。
- **预期**：明确超时边界、适当告知模型能力/重试选择；如果正式产品声明支持 AI 创建，则正常请求必须得到可审查提案或具体失败原因。
- **证据**：`ai-proposal-result.json`、`ai-proposal.png` 和重启后的 UI 树。
- **归因状态**：**未区分**免费服务端响应变慢、网络/模型能力与 App 两阶段调用时延，不声称必然是 App 代码错误。
- **退出条件**：至少 3 次受控请求记录端到端耗时、HTTP 状态、是否生成提案；经对照模型验证；不得放松授权安全检查。
- **本轮追加复测**：2026-10-23 API 35 免费模型聊天阶段 HTTP 200 / 17.811 秒 / 2048 tokens，`proposalIntent=true`；结构化阶段 HTTP 429 / 1.767 秒 / 2048 tokens。没有审查卡或日历授权动作。脱敏证据 JSON SHA-256 `402457CFAC21A1C4BF56C99BF7D44FFDBF7B05059C4140FD54961A476B167298`；独立 Issue 报告已补充。服务端阶段性限流未解除，Issue 仍开放。

### PC-ISSUE-003 · P2 / 已修复；设备验收 PARTIAL / HOLD · 非空库恢复拒绝提示仅显示异常类名
- **复现**：在非空数据库中选择已有合法备份恢复。
- **实际**：正确拒绝覆盖，但用户仅看到 `恢复失败：IllegalArgumentException`。
- **预期**：明确说明目标数据库非空，并提供安全导出/恢复路径。
- **修复与验证**：类型化的非空目标错误及可操作解释已在提交 `92f083b44582c4485bc8ab8a0d4a0505691ce14a` 完成；API 35 SAF 已验证非空目标拒绝、空库导入、篡改/无效 JSON 拒绝与数据库语义保留。最新完整 Gradle 回归 364 项通过。详情与逐项证据见 `docs/issues/PC-ISSUE-003-restore-feedback.md`。
- **剩余 HOLD**：尚未在实体 Android 设备验收；没有成功诱发系统实际撤销 SAF URI 权限的场景，当前权限错误解释由自动化异常测试覆盖。保持拒绝语义不变，补齐这两项后再签核。

### PC-ISSUE-004 · P2 / PARTIAL / HOLD · 时间轴灰带、方向与标签拥挤
- **复现**：横屏 2400×1080 时间轴中，渐变中心出现额外灰带；现时刻徽标可能压住刻度文字/事件卡。
- **根因与修复**：最初移除 `gridLine` 中心色标后，42%–58% `surfaceContainerLow` 平带仍留下淡横带；用户复查后再次去除整个位置渐变并实测底色一致。另将 Activity 固定为不依传感器旋转的竖屏；现时刻徽标避让、密集刻度处理和中央尺无障碍动作仍保留。
- **模拟器证据**：348/348 JVM 测试通过；`verifySkinSourceBoundary`、Debug APK、Lint 通过（0 errors/56 warnings）。最新横屏图 `.local/android-test/reports/freeze-20261023/issue004-accessibility-device-regression/landscape-fixed.png` SHA-256 `8644124407D99343364C8D80A18840F594F5B5222820178C576F6D32A802A7F3`；UI 树中尺的 `clickable=true`。独立报告：[`docs/issues/PC-ISSUE-004-timeline-layout.md`](issues/PC-ISSUE-004-timeline-layout.md)。
- **剩余 HOLD**：TalkBack 服务虽绑定，但系统通知权限提示遮挡应用树；未授予权限，实际 TalkBack 双击仍未验证。物理真机触摸、IME 遮挡和长时稳定性未验；字体 2x 工具栏换行待真机复核。
- **结论**：代码修复及 API 35 模拟器视觉回归 PASS；Issue 不视为完全关闭，核心冻结继续 HOLD。
- **后续补正**：实现提交 `abde8e2900b507cf1a50730b22de8ec55e4bac38`。强制设置系统旋转到 90°后 API 35 设备仍显示竖屏，旋转设置已恢复；背景像素跨视口一致，NOW 蓝线与午夜虚线仍可见。完整回归 364/364 PASS。外部平台覆盖方向、真机、IME 与 TalkBack 仍未验。

### PC-ISSUE-GREEDY-LABEL-FOLDING · P1 / API 35 PASS · 标题间视觉冲突
- **复现/根因**：旧逻辑以事件 anchor/end/side/lane 与固定阈值近似判断，未使用实际排版后的标题矩形；隐藏项也没有明确的 lead 语义。
- **修复**：先实测、裁切标题矩形，再按屏幕 Y 和稳定 ID 排序；每个区域只与当前可见 lead 比较。隐藏标题不改变 lead；标题层保留事件框点击和完整标题无障碍语义。
- **设备证据**：A/B/C/D 的实际标题矩形和碰撞轨迹见独立报告。A 显示、B 隐藏、C 不与 A 冲突而显示并接任 lead、D 与 C 冲突而隐藏；四个事件框的前后边界不变。未出现 `×N`。
- **自动化**：52 套件、362 项全量 JVM 测试通过；Skin boundary、Debug APK、Lint 通过。实现提交 `0099a8a201dbc605ba74e1999110eec7517ec6f6`。
- **剩余范围**：真机/OEM 与 TalkBack 交互未验；本项作为代码及 API 35 子项单独 PASS，核心冻结仍 HOLD。独立报告：[`docs/issues/PC-ISSUE-GREEDY-LABEL-FOLDING.md`](issues/PC-ISSUE-GREEDY-LABEL-FOLDING.md)。

### PC-OBS-005 · NEEDS REPRO · 部分提交操作即时反馈可能存在时序竞争
- 旧 Round 1/2 观察：某些 Publish 之后立刻采样 UI 未见结果，稍后实际提交成功。
- 当前证据不足以证明业务写入失败；按确定性等待稳定后复现再立缺陷。

### PC-TECH-006 · P3 / DEBT · Lint 有 56 个 Warning
- 0 Errors、56 Warnings；类别主要为 `UseKtx`、`ComposableNaming`、`UnusedResources`、`IconLauncherShape` 等。
- Kotlin 编译还存在已弃用 API 与交叉类型推断告警（未来 Kotlin 版本可能升级为编译错误）。
- 非单独冻结阻塞，后续按真实兼容性风险排序处理。

## 3. 冻结门槛与证据缺口（BLOCKED/NOT VERIFIED，不能记为代码 FAIL）

### PC-QA-GATE-001 · P0 / BLOCKED · 没有可追溯的 209 项逐案签核台账
- 历史规划明确 209 核心验收项（P0 126，P1 82，另 1 项优先级待核）。
- 当前跟踪的仓库文件中只有对“209 项”的描述，**没有找到 209 个完整编号用例及 PASS/FAIL/证据 ID 一一映射**。
- 因此 **209 项未逐案签核**；既不能说全部未实际测试，也不能根据 325+37 单元测试声称全部通过。
- 解除：重建用例原始定义、逐案结果表、明确其中哪些是模拟器可测、哪些需要真机；必须满足规定的 P0/P1 全部通过要求。

### PC-QA-GATE-002 · P0 / BLOCKED · OEM 真机、其他 Android API 等级与系统权限组合
- 当前只有 API 35 x86_64 模拟器；未有 Android 26–34、API 36、真实 OEM 通知策略、物理触摸手感和系统性能的签核证据。
- `FIRED` 执行记录证明触发处理链的一段，但不能证明通知弹窗/声音、Doze、权限拒绝与精确闹钟恢复的所有组合。
- 解除：按真实设备/API 维度逐条重现并保存截图、日志和状态。

### PC-QA-GATE-003 · P0 / BLOCKED · AI 授权链未完成真实端到端
- 尚未证明：可解析的创建/修改卡、四项批量处理授权、跨会话恢复、冲突、补偿撤销、图片/OCR/ICS 附件、结构化指令恶意内容防护的 Android 端到端路径。
- 单纯成功获得 `PC_CONNECT_OK` 只证明传输，不能证明上述能力。
- Flash 对照误入系统日历界面，不产生有效结论；付费模型已恢复为免费模型，未自动发布任何正式事件。

### PC-QA-GATE-004 · P1 / NOT DEVICE VERIFIED · 复杂重复、DST 与异常备份实际 UI 路径
- 本轮 JVM 单元层已覆盖篡改哈希、未知格式版本、事务中途失败回滚、v2 恢复同意、迁移等（参见 `BackupEnvelopeTest` 等）。
- 但“损坏备份 → Android SAF 选择 → UI 错误提示 → 原数据未改”、周/月系列隔离编辑/完成、跨午夜、DST、更新回滚的完整设备操作未逐项签核。
- 旧版 Envelope 导出/恢复和每日重复实操有证据，不作全量覆盖声明。

### PC-QA-GATE-005 · 后续独立阶段 · 36 项 .pcskin 分离验收
- Skin Studio 的 37 项测试通过不代表默认 Clear WebView 完全替代原生产品 UI。
- 所有 Web 编辑/提案/系统设置/输入法/恢复功能的设备级等价性尚未证明；禁止删除原生回退或开启不可逆 factory cutover。
- 这 36 项独立于核心冻结 209 项，不能相互冲抵。

## 4. 推荐处理顺序与冻结裁决

1. PC-ISSUE-001 的导出修复、DST SAF 与独立解析已通过；继续补齐外部日历导入和实体设备验收。同时完成 PC-ISSUE-003 的可读错误说明。
2. 排查 PC-ISSUE-002：记录按模型区分的真实调用耗时，优先免费模型；任何付费仅限已授权的 `deepseek/deepseek-v4.1-flash`。
3. 以已存证据重建 209 项用例矩阵，不把分组测试/单测混算为人工签核；按测试条件集中执行仍未覆盖的 Android UI 组合。
4. 补齐真实通知在权限、后台/Doze/时区/重启下的送达证据，并在真机或多 API 环境完成门槛测试。
5. 逐项确认全部 P0/P1 结果，闭合高危 Issue 后方可发出 **GO**；之后才进入独立 36 项的皮肤分离验收。

**最终结论：HOLD。** 这是有证据支持的部分验收与 Issue 盘点，不是“209/209 全部通过”；禁止把当前测试成绩描述为正式冻结完成。

## 5. 证据清单与数据安全
- 新增证据：`.local/android-test/reports/freeze-20261023/`（Git 忽略）。
- 历史证据：`docs/ANDROID_EMULATOR_SMOKE_QA_2026-10-23.md` 与 `docs/ANDROID_EMULATOR_REGRESSION_QA_2026-10-23.md`。
- AI 凭据不在本文、日志或 Git 中；只记录 `key_present=True`。测试后保持 `stealth/glyph-cluster:free`、上下文共享关闭。
- 只读 SQLite 快照是测试数据，留在被忽略的 `.local`，不可误提交。
