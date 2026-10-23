# PowerCal 0.9.5-rc.1 · 冻结验收与 Issue 报告
日期：2026-10-23（北京时间）
判定：**FREEZE HOLD / 不可正式冻结**
仓库：`E:\Project\Powerful-Calendar`
主代码基线：`abde8e2900b507cf1a50730b22de8ec55e4bac38`（背景灰带补正与固定竖屏）
测试 APK：`app/build/outputs/apk/debug/app-debug.apk`
Issue 004 修复代码基线：`16e00d55435c5029e07784ec5d945dfd7fb37ac5`
最新测试 APK SHA-256：`80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`
最新核心回归代码/测试基线：`92dd9016d7907b1e57cd4a27920332fa72e572fc`；Debug APK SHA-256：`80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`
设备：Issue 004 使用隔离的 Android 15 / API 35 `emulator-5556`；原 `emulator-5554` 保持运行且未操作。无物理真机。
此前冻结工作记录了时间轴验收；本轮继续核心数据语义与提醒验收，修复重复事件延迟 snooze 广播处理并增加核心回归及文档，没有修改 UI、业务数据或 `design/`。按用户最新确认，变浅的横向边缘可接受，本轮不开展 UI 工作。

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
| FQ-19 | Envelope 哈希、水位拒绝与格式降级防护（REC-P0-091/097/106） | **PASS（定点 JVM 回归 3/3）** | `BackupEnvelopeTest` 三个精确方法通过；代码基线 `574092de1046763c4aad40d7db4a2dd9123848fa`，APK SHA-256 `53C200F398D13D636125572DBD0C4876FEA1A18585323C935F9C13F8E061E103`；日志 SHA-256 `F14D6EB7B5D96678EF84B4B6177B69446C27DF46AC5820712C5E424921D4CAD9`，详见 `docs/issues/PC-QA-GATE-001-209-case-ledger.md` |
| FQ-20 | 删除后显式恢复墓碑、保留同一 `eventSeq`（REC-P0-055/056） | **PASS（核心 JVM）；产品验收 PARTIAL / HOLD** | SQLite 与 DeckController 定点测试 2/2；恢复确认前保留墓碑，拒绝非 user actor，确认后 revision +1、序号水位不变并重建提醒。提交 `40e5dd35bd238da476f67507994b6c100b646b89`；全量 366 项 PASS / lint 0 errors、55 warnings；API 35 设备未测，当前无 UI 动作调用 `beginRestore`。独立报告 `docs/issues/PC-ISSUE-005-retired-event-restoration.md` |
| FQ-21 | 原生备份引用、水位、墓碑、草稿/提交历史与凭据边界（REC-P0-094/095/096/098/099/100/101/109） | **PASS（核心 JVM）；整体冻结 HOLD** | 定向 7/7，覆盖未知格式/版本、两类悬空引用、`nextOrder`、墓碑和历史往返、API 密钥及调度句柄排除。测试提交 `5df9e00b04ad50dc5c4e12154cf117cc4a159a23`；全量 372 项 PASS / lint 0 errors、55 warnings；无模拟器/真机测试。独立报告 `docs/issues/PC-ISSUE-006-backup-roundtrip-contract.md` |
| FQ-22 | 备份预检后出现新写入时事务内复查（REC-P0-103） | **PASS（核心 JVM）；整体冻结 HOLD** | 第二 SQLite store 在预检完成后写入草稿；事务内复查拒绝恢复并保留后来写入的数据。测试提交 `2171c3f96fb69a1e6d14e1fb170d4c53a9439184`；全量 373 项 PASS / lint 0 errors、55 warnings；没有设备测试。独立报告 `docs/issues/PC-ISSUE-007-post-preflight-concurrent-restore.md` |
| FQ-23 | 数据库迁移失败回滚与同库重试（REC-P0-083/085–088） | **PASS（核心 JVM）；整体冻结 HOLD** | SQLite trigger 在首条重复草稿归并及审计已成功后，于第二条更新处注入失败；整体回滚后 schema 仍为 v3 且三条 PENDING 草稿完整，移除 trigger 后同库升级成功；只增加测试。测试提交 `ef901e0d5878bb4b25c61ed10dd1021cf312ca97`；定向 4/4、全量 374/374，Lint 0 errors / 55 warnings；无设备验收。独立报告 `docs/issues/PC-ISSUE-008-database-migration-atomicity.md` |
| FQ-24 | 重复实例原始键、例外隔离与日期边界（REC-P0-060/061/062/063/066/067/068/070/071） | **PASS（核心 JVM）；整体冻结 HOLD** | 7 个纯时间模型测试验证包含式日期边界、有限窗口、跨日期移动仍保留原始 slotKey、跳过单实例、移动后自然槽位保留、例外按 eventSeq 隔离。测试提交 `67648e5b6c4c744034884e408f8835b208306b29`；定向 7/7、完整 Gradle 375/375，Lint 0 errors / 55 warnings；无设备验收。DST gap/fold 规格仍未签核。独立报告 `docs/issues/PC-ISSUE-009-recurrence-instance-identity.md` |
| FQ-25 | 重复事件单次稍后提醒投递（REC-P0-069） | **PASS（Robolectric 接收器回归）；真实设备投递未验** | 修复前回归复现 `expected FIRED, got IGNORED_STALE`；修复后定向 1/1、完整 Gradle 408/408（55 suites）通过，Lint 0 errors / 55 warnings。验证 snooze 不改重复规则、revision、完成键或投影序列。代码/测试提交 `f45710a6759fc5532f3982721345c06a912afa3e`；Android AlarmManager、通知权限/Doze/OEM 和实体设备仍未验。独立报告 `docs/issues/PC-ISSUE-050-snooze-recurring-occurrence.md` |
| FQ-26 | 首次草稿分配永久 eventSeq，丢弃后不复用（REC-P0-025/027） | **PASS（Robolectric PowerStore/SQLite）** | `firstDraftAllocatesASequenceEvenIfItIsDiscarded` 定向 1/1；首次草稿得到 1，丢弃后下一张得到 2，水位为 3、身份数为 2。该测试也包含在 409 项全量回归的 `PowerStoreTest` XML 中。随后 Issue 052 签核了失败插入后的序号退休。无源代码/UI改动于本 Issue 051。独立报告 `docs/issues/PC-ISSUE-051-first-draft-sequence-allocation.md` |
| FQ-27 | 首次草稿插入失败后永久退休已分配 eventSeq（REC-P0-026） | **PASS（Robolectric PowerStore/SQLite）** | 修复前 SQLite trigger 回归复现事务回滚：期望 identityCount=1，实际为 0。修复将序号预留独立提交并在草稿提交前标为退役；定向 2/2、完整 Gradle 409/409，Lint 0 errors / 55 warnings。物理设备不适用此存储契约。源码/测试提交 `c2f2ad5efb5b84b887fb38b4ee03916c0c23906e`；独立报告 `docs/issues/PC-ISSUE-052-failed-first-draft-sequence-retired.md` |
| FQ-28 | 原生备份未知字段与未来版本拒绝（REC-P0-089/090） | **PASS（Robolectric SQLite 定向回归 3/3）** | legacy v2 顶层未知字段、backupFormatVersion=99、新于当前版本的 databaseSchemaVersion 均被拒绝；未知字段拒绝后同一目标仍可导入有效备份并保留 nextOrder。当前代码/测试基线 `c2f2ad5efb5b84b887fb38b4ee03916c0c23906e`，未改源码或测试。日志 SHA-256 `029F8416FCDB443748A36E737559632B8F8C90BB598D6732C5C6B0275AFDA423`；JUnit XML SHA-256 `B117D99E56EFE2C8FE4612DE6994D3417E1C5635E955F6B343E45C1CA2BA4946`；台账结构校验 JSON SHA-256 `F89EB3716E146E5FFB58AE0820070A2B72D92B07A73480708BCAEC81D3130E70`。候选 100 PASS / 93 NOT TESTED；原始 209 项映射仍缺失。独立报告 `docs/issues/PC-ISSUE-053-backup-unknown-structure-rejection.md` |
| FQ-29 | 类型转换保留缺失关键字段并拒绝臆造时间（REC-P0-021/022） | **PASS（纯模型定向回归 2/2）** | Alarm→Activity 保留开始点并报告缺少结束时间；日期级 Deadline→Alarm 不生成开始时刻并报告需补 START。代码/测试基线 `c2f2ad5efb5b84b887fb38b4ee03916c0c23906e`，无源码或测试改动；编辑器如何展示待补字段未测试。日志 SHA-256 `9F27CEC16D6F006D1876B24D6E685E50DD2A8670EA51B8AF39957449D8D4F62B`；JUnit XML SHA-256 `4AB98B1E2E452AB8B6914524D076877206F2C3C6B059ED2A02B62484E86295DD`；独立报告 `docs/issues/PC-ISSUE-054-temporal-conversion-preserves-missing-fields.md` |
| FQ-30 | SQLite 保存单一当前正式快照并保留提交历史（REC-P0-081） | **PASS（PowerStore/SQLite 定向回归 1/1）** | 三次提交后关闭并重开 SQLite，当前正式快照仍唯一且为 revision 3，三条历史记录均保留。代码/测试基线 `c2f2ad5efb5b84b887fb38b4ee03916c0c23906e`，无源码或测试变更；UI/AI/ICS/Android 闹钟投影写入路径未验。日志 SHA-256 `0204D79B2E0FA988A6C2BD97E810C52335D59256CBD910B38582B9208422B2B1`；JUnit XML SHA-256 `DA3564895E0445B68F38D9B393C95BA8867F2DD939BB3F133F0A8DD507AFDAA9`；独立报告 `docs/issues/PC-ISSUE-055-runtime-event-sqlite-source.md` |
| FQ-31 | 调度意图 BLOCKED 与正式事件完成/删除状态隔离（REC-P0-018） | **PASS（PowerStore/SQLite 定向回归 1/1）** | 设置 `DesiredSchedule.BLOCKED` 后事件仍存在、完成键为空，关闭并重开 SQLite 后调度状态与正式快照均保留。测试提交 `92dd9016d7907b1e57cd4a27920332fa72e572fc`；完整 Gradle 410/410、55 suites，Lint 0 errors / 55 warnings。未覆盖 Android 精确闹钟权限拒绝/恢复及通知路径。日志 SHA-256 `CCE3127D5D083C4B3BA564D24BFF4E9E42A34EA85C7EBCDA33A7FC5539F6160F`；JUnit XML SHA-256 `BA4E34678FC7B0E4B63F03A6567BC9AD994EB1ED785F79FE63C43E582BF9E1F5`；完整回归日志 SHA-256 `92E006A3038FDD32D22A806CC2D990CE6255875450256D3DAB51A23B8EE66191`；独立报告 `docs/issues/PC-ISSUE-056-blocked-schedule-does-not-mutate-event.md` |
| FQ-32 | 提醒执行/响铃状态与正式事件生命周期隔离（REC-P0-019） | **PASS（Robolectric Receiver/SQLite 定向回归 1/1）** | 合成 `ACTION_FIRE` 经 `ReminderReceiver` 处理后执行记录为 `FIRED`、一次性调度意图移除；正式事件仍存在、revision 不变且完成键为空。定向日志 SHA-256 `70E056E358A19C4A0553BE8C5A8F9EB1931FC579D893AE37D4D68AC2F9F1575D`；JUnit XML SHA-256 `48B0A577EDF868BE040C3B132B9AAE2AFBA679DEA61DE0B4293A1932C690B429`。同一代码/测试基线 `92dd9016d7907b1e57cd4a27920332fa72e572fc` 的完整 Gradle 为 410/410，Lint 0 errors / 55 warnings；未测试 Android AlarmManager 实际投递、声音及实体设备。独立报告 `docs/issues/PC-ISSUE-057-ringing-state-does-not-complete-event.md` |

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

### PC-ISSUE-005 · P0 / 核心路径 PASS；产品验收 PARTIAL / HOLD · 墓碑恢复缺少可达的核心入口
- `PowerStore.proposeRestoreDraft` 现在可为墓碑建立同一 `eventSeq` 的待确认提案，`DeckController.beginRestore` 打开该提案；显式确认才恢复，非 user actor 不可复活事件。
- 定点 SQLite / Draft Deck 控制器测试 2/2 PASS；全量 Gradle 366 项 PASS、Lint 0 errors / 55 warnings。证据与哈希见 [`PC-ISSUE-005`](issues/PC-ISSUE-005-retired-event-restoration.md)。
- 由于本轮不改 UI，应用中尚无动作调用 `beginRestore`；API 35 设备验收也未做。本项核心模型验证通过，但产品入口与设备验收保留 HOLD。

### PC-ISSUE-006 · P0 / 原生备份核心契约定向 PASS；整体冻结 HOLD
- 新增并逐项签核 8 个重建候选项：未知格式/版本、双向悬空引用、`nextOrder` 下界、墓碑保留、草稿身份/顺序、提交修订历史、API 凭据及调度句柄排除。没有发现需要修改生产逻辑的缺陷。
- 定向 7/7，完整 Gradle 372/372，Lint 0 errors / 55 warnings；均为 JVM/SQLite 与构建验证，无 Android 设备验收。证据见 `docs/issues/PC-ISSUE-006-backup-roundtrip-contract.md`。

### PC-ISSUE-007 · P0 / 预检后并发写入保护 PASS；整体冻结 HOLD
- 定向测试在预检与 payload 校验完成后，经第二 SQLite store 写入新草稿；后续恢复事务通过已有非空复查拒绝导入，原身份、草稿和 allocator 保留。只增加 `internal` 确定性测试入口，公开恢复行为不变。
- 定向 1/1、完整 Gradle 373/373，Lint 0 errors / 55 warnings；不含 Android 设备验收。Robolectric 真锁交错尝试遇到 SQLite busy/线程归属问题，未计入 PASS；一份中间日志被后续最终日志覆盖，缺口已在独立报告中披露。详见 `docs/issues/PC-ISSUE-007-post-preflight-concurrent-restore.md`。

### PC-ISSUE-008 · P0 / 迁移原子性与重试核心契约 PASS；整体冻结 HOLD
- 既有 v1/v2 升级测试覆盖成功迁移，但没有注入 v4 中途失败；新测试确认 trigger 精确触发后，迁移 DDL、`user_version` 和旧草稿均按事务回滚。移除故障后，同一数据库成功升级。没有生产代码或 UI 变更。
- `REC-P0-083/085/086/087/088` 单独签核；合成历史 schema fixture 不代表用户数据库样本。故障注入在第一条重复草稿已经更新并写入审计后，于第二条更新处失败，事务回滚恢复三条 PENDING 草稿。定向 4/4、完整 Gradle 374/374，Lint 0 errors / 55 warnings，无设备验收。细节与哈希见 `docs/issues/PC-ISSUE-008-database-migration-atomicity.md`。

### PC-ISSUE-009 · P0 / 重复实例身份与例外隔离核心模型 PASS；整体冻结 HOLD
- `REC-P0-060/061/062/063/066/067/068/070/071` 单独签核；增加了跨两条 eventSeq 的例外隔离回归，并让长距离移动断言同时验证自然槽位与原始移动槽位都只出现一次。没有生产逻辑或 UI 变更。
- 定向 7/7、完整 Gradle 375/375，Lint 0 errors / 55 warnings；仅 JVM/Java 时间 fixture，无设备验收。DST gap/fold 的产品政策仍保持 SPEC UNRESOLVED。详细证据见 `docs/issues/PC-ISSUE-009-recurrence-instance-identity.md`。

### PC-OBS-005 · NEEDS REPRO · 部分提交操作即时反馈可能存在时序竞争
- 旧 Round 1/2 观察：某些 Publish 之后立刻采样 UI 未见结果，稍后实际提交成功。
- 当前证据不足以证明业务写入失败；按确定性等待稳定后复现再立缺陷。

### PC-TECH-006 · P3 / DEBT · Lint 有 55 个 Warning
- 最新完整回归：0 Errors、55 Warnings；类别主要为 `UseKtx`、`ComposableNaming`、`UnusedResources`、`IconLauncherShape` 等。证据见 `.local/android-test/reports/freeze-20261023/issue001-dst-full-gradle-20261023.log`。
- Kotlin 编译还存在已弃用 API 与交叉类型推断告警（未来 Kotlin 版本可能升级为编译错误）。
- 非单独冻结阻塞，后续按真实兼容性风险排序处理。

## 3. 冻结门槛与证据缺口（BLOCKED/NOT VERIFIED，不能记为代码 FAIL）

### PC-QA-GATE-001 · P0 / PARTIAL / HOLD · 没有可追溯的 209 项逐案签核台账
- 历史规划明确 209 核心验收项（P0 126，P1 82，另 1 项优先级待核）。
- 当前跟踪的仓库文件中只有对“209 项”的描述，**没有找到 209 个完整编号用例及 PASS/FAIL/证据 ID 一一映射**。
- 因此 **209 项未逐案签核**；既不能说全部未实际测试，也不能根据 325+37 单元测试声称全部通过。
- 本轮只读审计仍未找到原始逐案清单；已建立 208 条 RECONSTRUCTED 候选项及 1 条未定义历史槽位，见 `docs/CORE_FREEZE_ACCEPTANCE_LEDGER.csv`。候选按风险给出 P0=126/P1=82 建议分配，但 0 个原始 ID 有映射，优先级仍待审核。
- 当前候选逐案状态：P0 PASS 86 / BLOCKED 1 / NOT TESTED 34 / SPEC UNRESOLVED 5；P1 PASS 19 / BLOCKED 8 / NOT TESTED 54 / SPEC UNRESOLVED 1；历史未分类槽位 SPEC UNRESOLVED 1。共 105 PASS、88 NOT TESTED、7 SPEC UNRESOLVED；80 条重建候选有独立定点行为证据。`REC-P0-054` 因产品基线允许多张同事件人工/AI 草稿卡、而 SQLite v4 限制每事件仅一张可操作草稿而标记规格未决，详见 `docs/issues/PC-ISSUE-047-human-ai-draft-cardinality-spec-conflict.md`。`REC-P0-058` 的 semantic no-op Save、`REC-P0-059` 的 recurrence start-bound/anchor projection、`REC-P0-069` 的重复事件延迟 snooze 接收器回归、`REC-P0-025/027` 的首次草稿序号分配与丢弃不复用、`REC-P0-026` 的失败插入后序号退休、`REC-P0-089/090` 的未知备份字段和未来格式/schema 版本拒绝、`REC-P0-021/022` 的纯模型类型转换字段保持和缺失字段提示、`REC-P0-081` 的 PowerStore/SQLite 单一当前快照与历史持久化、`REC-P0-018` 的 BLOCKED 调度状态与正式事件状态隔离、`REC-P0-019` 的提醒执行状态与正式事件生命周期隔离均由当前基线签 PASS，分别见对应 Issue 048–057 报告。结构验证 artifact 更新到 `pc-qa-gate-001-ledger-validation-after-pc-issue-057.json`；SHA-256 `79161A5A18411CE0100D59F7936F6B2A11C3DD93AEA71C53046AC992C2E16B79`，CSV SHA-256 `888A7CC0E8C8CB6B40A1FB67B1D0553E16ACC92B1692B50982BA9CCFE1C3D946`；该验证仅检查结构与计数，不测试产品行为。
- 最新完整 Gradle 工作树结果 410/410 通过（409 个已跟踪测试 + 1 个受保护未跟踪测试）；Lint 0 errors / 55 warnings。日志 `.local/android-test/reports/freeze-20261023/pc-issue-056-full-gradle-20261023.log`，SHA-256 `92E006A3038FDD32D22A806CC2D990CE6255875450256D3DAB51A23B8EE66191`；APK SHA-256 `80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`。测试源提交 `92dd9016d7907b1e57cd4a27920332fa72e572fc` 已推送。结果没有映射到缺失的原始 209 个 ID。
- `PC-ISSUE-019` 的 `REC-P0-004` 日期型 Deadline 候选、`PC-ISSUE-020` 提醒测试夹具稳定化、`PC-ISSUE-021` 的 `REC-P0-005` 不完整 Activity 草稿恢复分别保留独立报告和边界，不替代设备及历史 ID 验收。
- `PC-ISSUE-022` 的 `REC-P0-007` 一日 Memo 本地日期语义通过 PowerStore/Robolectric 跨 SQLite 重启回归；独立报告 `docs/issues/PC-ISSUE-022-one-date-memo-local-date.md` 记录精确断言与边界。
- `PC-ISSUE-023` 的 `REC-P0-008` 日期范围 Memo 两端本地日期及时间字段隔离通过 PowerStore/Robolectric 跨 SQLite 重启回归；独立报告 `docs/issues/PC-ISSUE-023-date-span-memo-boundaries.md` 记录精确断言与边界。
- `PC-ISSUE-024` 的 `REC-P0-002` Activity 起止区间通过 PowerStore/Robolectric 跨 SQLite 重启回归；独立报告 `docs/issues/PC-ISSUE-024-activity-interval-persistence.md` 记录精确断言与边界。
- `PC-ISSUE-025` 的 `REC-P0-020` 类型转换保留事件身份通过 PowerStore/Robolectric 跨 SQLite 重启回归；独立报告 `docs/issues/PC-ISSUE-025-kind-conversion-keeps-event-identity.md` 记录精确断言与边界。
- `PC-ISSUE-026` 的 `REC-P0-016` 导入来源元数据与事件身份分离通过 PowerStore/Robolectric 跨 SQLite 重启回归；独立报告 `docs/issues/PC-ISSUE-026-import-source-metadata-identity.md` 记录精确断言与边界。
- `PC-ISSUE-027` 的 `REC-P0-028` 拒绝结构化导入不消耗 eventSeq，通过同一 source/externalId 修复重试及 SQLite 重开回归；仅新增测试，无生产/UI 修改。定向 1/1、完整 Gradle 390/390 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-027-rejected-import-does-not-consume-sequence.md`。
- `PC-ISSUE-028` 的 `REC-P0-006` 无日期 Memo 持久化语义通过 pure-model 与 PowerStore/Robolectric SQLite 重开回归，所有日期、时间与锚点字段均为空；仅新增测试，无生产/UI 修改。提交后定向 2/2、完整 Gradle 391/391 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-028-undated-memo-no-synthetic-time.md`。
- `PC-ISSUE-029` 的 `REC-P0-029/030` 删除 tombstone 与 eventSeq 退休、不复用语义通过 PowerStore/Robolectric SQLite 重开回归；仅新增测试，无生产/UI 修改。提交后定向 2/2、完整 Gradle 392/392 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-029-retired-identity-not-reused-after-restart.md`。
- `PC-ISSUE-030` 的 `REC-P0-031` 两个独立 create 草稿 draftId 唯一性及 eventSeq 映射跨 SQLite 重开回归通过；仅新增测试，无生产/UI 修改。提交后定向 2/2、完整 Gradle 393/393 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-030-independent-draft-identities.md`。
- `PC-ISSUE-031` 的 `REC-P0-032` 暂存草稿编辑后保留原 draftId 和目标 eventSeq、内容与版本更新并跨 SQLite 重开回归通过；仅新增测试，无生产/UI 修改。提交后定向 2/2、完整 Gradle 394/394 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-031-staged-draft-keeps-event-identity.md`。
- `PC-ISSUE-032` 的 `REC-P0-033` 按 V4 修正为“同一事件至多一个可操作草稿”，连续提案复用 draftId 与 queueOrder，且 SQLite 重开后约束保持；仅新增测试，无生产/UI 修改。提交后定向 2/2、完整 Gradle 395/395 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-032-one-actionable-draft-per-event.md`。
- `PC-ISSUE-033` 的 `REC-P0-034` 多次 whole-snapshot 提交后正式记录查询仅有一条最新快照，SQLite 重开后仍然如此，历史记录单独保留；仅新增测试，无生产/UI 修改。提交后定向 2/2、完整 Gradle 396/396 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-033-single-current-formal-snapshot.md`。
- `PC-ISSUE-034` 的 `REC-P0-035` 队列序号与 eventSeq 顺序反转时仍唯一，数据库重开后位置和事件映射保持；仅新增测试，无生产/UI 修改。提交后定向 2/2、完整 Gradle 397/397 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-034-independent-queue-order.md`。
- `PC-ISSUE-035` 的 `REC-P0-036` 提交/丢弃草稿的 queueOrder 高水位在 SQLite 重开后不回退，新草稿序号严格更大；仅新增测试，无生产/UI 修改。提交后定向 2/2、完整 Gradle 398/398 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-035-monotonic-queue-watermark.md`。
- `PC-ISSUE-036` 的 `REC-P0-038` 人工提案 queueOrder 追加到已有队列尾部，草稿 ID/顺序/标题跨 SQLite 重开保持；仅新增测试，无生产/UI 修改。提交后定向 2/2、完整 Gradle 399/399 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-036-human-proposal-appends-queue.md`。
- `PC-ISSUE-037` 的 `REC-P0-042` 编辑较早草稿会更新 updatedAtMillis，但 queueOrder 和队列次序跨 SQLite 重开不变；仅新增测试，无生产/UI 修改。提交后定向 2/2、完整 Gradle 400/400 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-037-timestamp-does-not-reorder-queue.md`。
- `PC-ISSUE-038` 的 `REC-P0-044` 创建和暂存有效修改提案不改变正式 revision；SQLite 重开后仍为 1，提交后升至 2；仅新增测试，无生产/UI 修改。提交后定向 2/2、完整 Gradle 401/401 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-038-revision-advances-only-on-commit.md`。
- `PC-ISSUE-039` 的 `REC-P0-045` 两次独立提交获得不同非空 commitId，SQLite 重开后历史 ID 仍唯一且保持；仅新增测试，无生产/UI 修改。提交后定向 2/2、完整 Gradle 402/402 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-039-unique-commit-id.md`。
- `PC-ISSUE-040` 的 `REC-P0-046` 显式 idempotencyKey 跨 SQLite 重开重试返回原结果；历史、revision、发布计数和单条提醒意图均未重复；仅新增测试，无生产/UI 修改，未覆盖系统级闹钟送达。提交后定向 2/2、完整 Gradle 403/403 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-040-idempotent-commit-retry.md`。
- `PC-ISSUE-041` 的 `REC-P0-047` stale baseRevision 在 SQLite 重开后返回 Conflict，正式标题、完成状态、revision 与历史均不被旧提案覆盖；仅新增测试，无生产/UI 修改，界面冲突提示未签核。提交后定向 2/2、完整 Gradle 404/404 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-041-stale-revision-conflict.md`。
- `PC-ISSUE-042` 的 `REC-P0-048` AI 批次遇到过期提案后保留 Conflict 草稿；SQLite 二次重开后草稿 ID、完整提案、baseRevision 与 queueOrder 均可取回，正式标题和完成状态不变，锁已释放。仅新增测试，无生产或 UI 修改。提交后定向 1/1、完整 Gradle 405/405 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-042-conflict-draft-retention.md`。
- `PC-ISSUE-043` 的 `REC-P0-050` 在 SQLite 重开后，未授权 stale commit 返回 Conflict 并保留新版本；只有显式 `approveStale=true` 才把整份旧提案作为 revision 3 提交，完成状态随获批快照更新。仅新增测试，无生产或 UI 修改。提交后定向 1/1、完整 Gradle 406/406 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-043-explicit-stale-reapproval.md`。
- `PC-ISSUE-044` 的 `REC-P0-051` 整对象替换在 SQLite 重开后持久化：提案清空的地点/备注不从旧快照合并回来，事件身份和创建时间保持、revision 升至 2。仅新增测试，无生产或 UI 修改。提交后定向 1/1、完整 Gradle 407/407 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-044-whole-snapshot-replacement.md`。
- `PC-ISSUE-045` 的 `REC-P0-052` 使用 `PC-ISSUE-043` 的定向回归证据：未批准的 stale commit 保留新版标题和完成状态，显式批准后才整对象替换。没有新增测试代码；同一测试只映射到其实际断言覆盖的两个候选项。最新完整 Gradle 407/407 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-045-no-silent-stale-field-merge.md`。
- `PC-ISSUE-046` 的 `REC-P0-053` 验证迟到 AI 替换在正式事件已被人类更新后返回 conflict、保留草稿、不覆盖完成状态，且重启后仍成立。现有回归当前基线定向 1/1；没有新增生产或测试代码。最新完整 Gradle 407/407 PASS，Lint 0 errors/55 warnings。独立报告 `docs/issues/PC-ISSUE-046-late-ai-response.md`。
- 解除：执行未测候选项、处理可解阻塞并审核拟定优先级；不可声称与缺失原始表完成历史逐项签核。

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

1. PC-ISSUE-001 的导出修复、DST SAF 与独立解析已通过；继续补齐外部日历导入和实体设备验收。PC-ISSUE-003 的非空恢复拒绝说明已修复，继续补设备权限验收。
2. 排查 PC-ISSUE-002：记录按模型区分的真实调用耗时，优先免费模型；任何付费仅限已授权的 `deepseek/deepseek-v4.1-flash`。
3. 以已存证据重建 209 项用例矩阵，不把分组测试/单测混算为人工签核；按测试条件集中执行仍未覆盖的 Android UI 组合。
4. 补齐真实通知在权限、后台/Doze/时区/重启下的送达证据，并在真机或多 API 环境完成门槛测试。
5. 逐项确认全部 P0/P1 结果，闭合高危 Issue 后方可发出 **GO**；之后才进入独立 36 项的皮肤分离验收。

**最终结论：HOLD。** 候选矩阵有 209 行，但原始 209 项仍未逐案映射或签核；禁止把候选行或当前测试成绩描述为“209/209 全部通过”。逐案门槛裁决见 `docs/CORE_FREEZE_VERDICT.md`。

## 5. 证据清单与数据安全
- 新增证据：`.local/android-test/reports/freeze-20261023/`（Git 忽略）。
- 历史证据：`docs/ANDROID_EMULATOR_SMOKE_QA_2026-10-23.md` 与 `docs/ANDROID_EMULATOR_REGRESSION_QA_2026-10-23.md`。
- AI 凭据不在本文、日志或 Git 中；只记录 `key_present=True`。测试后保持 `stealth/glyph-cluster:free`、上下文共享关闭。
- 只读 SQLite 快照是测试数据，留在被忽略的 `.local`，不可误提交。
