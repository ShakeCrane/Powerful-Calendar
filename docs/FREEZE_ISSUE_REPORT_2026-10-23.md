# PowerCal 0.9.5-rc.1 · 冻结验收与 Issue 报告
日期：2026-10-24（北京时间；延续 2026-10-23 冻结验收记录）
判定：**FREEZE HOLD / 不可正式冻结**
仓库：`E:\Project\Powerful-Calendar`
主代码基线：`abde8e2900b507cf1a50730b22de8ec55e4bac38`（背景灰带补正与固定竖屏）
测试 APK：`app/build/outputs/apk/debug/app-debug.apk`
Issue 004 修复代码基线：`16e00d55435c5029e07784ec5d945dfd7fb37ac5`
最新测试 APK SHA-256：`D941A9BF701896C7D9E232540856D5854EA9EC8CFD6AEB2AB0AA9CDD8DE72ABB`
最新核心回归代码/测试基线：`65f6cabd653878f77127e167a583bea31043e5e3`；Debug APK SHA-256：`D941A9BF701896C7D9E232540856D5854EA9EC8CFD6AEB2AB0AA9CDD8DE72ABB`
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
| FQ-33 | 正式事件、提交历史与目标调度原子提交（REC-P0-110） | **PASS（PowerStore/SQLite 故障注入与重试）** | 在 `schedule_intents` 插入处注入 abort，确认正式快照、提交历史、草稿终态和调度意图全部回滚；移除 trigger 后 retry 成功，revision 1 与对应 PENDING 意图/commit 跨 SQLite 重开一致。定向 1/1；完整 Gradle 411/411、55 suites，Lint 0 errors / 55 warnings。测试提交 `4906601d221bba0298068a3568a1bac2ad47c382` 已推送。未测试 Android 调度送达/真机。定向日志 SHA-256 `B2D3FA1EC7E25E718EE17AD2F655D264611B59CD5C1CD1BC2B251F361BD473E2`；JUnit XML SHA-256 `F8518DBBA895E460463763A0F3287D419F0464446D74EBE630A5D1914699A72D`；完整 Gradle 日志 SHA-256 `57A54B40941585805737E5A48FAFAC16E052CCCEAD3EF3C1538BC67DACABB0E5`；`PowerStoreTest` XML SHA-256 `7BDCA77CE8F0BC11083B81D7C6188F0F278C8088C2F2711DFD2395C05C10FBC4`。独立报告 `docs/issues/PC-ISSUE-058-atomic-event-schedule-commit.md` |
| FQ-34 | 备份输入严格字节上限（REC-P0-108） | **PASS（核心有界流回归）** | `BackupEnvelope.readUtf8Bounded` 接受刚好达到上限的输入，并在第一个超限字节处抛错；超限测试证明只消费 `maxBytes + 1` 字节，SAF 读取路径接入该核心读取器。定向 1/1；完整 Gradle 412/412、55 suites，Lint 0 errors / 55 warnings。提交 `a03ada74e2413303b29d58325d24d5eb7081af3f` 已推送。未测试 Android SAF provider/真机。定向日志 SHA-256 `D5DCCE52E1E0B6FA32314F2EAF1FFBFC65F834F76860F27EB05A79C299E6C860`；JUnit XML SHA-256 `A7D77CDDC5302324161B2E61E7A493E86986EF483DAAE6929F9891C9E3B26248`；完整 Gradle 日志 SHA-256 `719C3C4D568F800BB8F48363592049F627216D1D416EF589E9656217D0802A1F`；完整 `BackupEnvelopeTest` XML SHA-256 `4E67C31DD333B9B110BB8D347782D3D14C12FA41068179A79FDD5D4A2AF79530`。独立报告 `docs/issues/PC-ISSUE-059-backup-size-cap.md` |
| FQ-35 | 备份应用版本与数据库 schema 独立（REC-P0-084） | **PASS（Robolectric/SQLite 恢复回归）** | 将 Envelope 的 `appVersionCode` 提高到当前版本号以上，同时保持 schema 为当前支持版本；verify 接受，恢复后正式活动与重建提醒意图均保留。定向 1/1；完整 Gradle 413/413、55 suites，Lint 0 errors / 55 warnings。测试提交 `ae0335479df24414c9d9b92696edafbc1efd6d24` 已推送；未测试 Android SAF provider/真机。定向日志 SHA-256 `99DBB5932D3D8244A5AE73074A6F6409A00484E7C47B0EFF9C8F0167275F8C4A`；JUnit XML SHA-256 `92CEF9655ADEF7FFAA8111B30506F2C10C02A7D75ABE3D8D57DC8F5E2F1FA6CA`；完整 Gradle 日志 SHA-256 `01DBFA3453683AE661611AD0F60C912C47670E71387C52FC94E95DD49C0B545D`；完整 `BackupEnvelopeTest` XML SHA-256 `6940193A90D0B7C441A96F048B0B0D0F900E5663C0E8AFF10A1F344A65F36154`。独立报告 `docs/issues/PC-ISSUE-060-backup-app-schema-version-independence.md` |
| FQ-36 | 无界重复 MEMO 仅按窗口投影（REC-P0-064） | **PASS（PowerStore/SQLite + JVM recurrence）** | 2024 年起的每日 MEMO 系列在 SQLite 中保持一条正式事件；2026-10-20..23 查询仅投影三个原始 occurrence key；投影前后序号水位不变，关闭并重开数据库后仍只有一个规则/事件。定向 1/1；完整 Gradle 414/414、55 suites，Lint 0 errors / 55 warnings。测试提交 `58ac0c8489d8a1363388c8a66f8a397c7d7610c6` 已推送；未测试 UI 展示/设备。定向日志 SHA-256 `846866B86EB8B369291003E9FA2FF7139A0AFB37618669E5F9C6D24675A1D81C`；JUnit XML SHA-256 `8C789D16C7CC3B2252314460DF71942BE4DBAEAEB790D3568AFC932A731CC360`；完整 Gradle 日志 SHA-256 `C4BC8F0E5EEAA0F66E707435A1AB5187275EDCA2928372EAA4F7EE2470FF13D6`；完整 `PowerStoreTest` XML SHA-256 `A4A22E45E176C36A5CDEEFEB7739DB744754D06FF40F239BFA0D3F6288845BC6`。独立报告 `docs/issues/PC-ISSUE-061-recurring-todo-bounded-projection.md` |
| FQ-37 | 保留过期未完成的重复待办 occurrence（REC-P0-065） | **PASS（PowerStore/SQLite 历史窗口回归）** | 每日 MEMO 规则中 2025-03-14 的 occurrence 在 `completedKeys` 为空时可由历史窗口取回；SQLite 重开后该 key 仍存在，正式规则未删除。定向 1/1；完整 Gradle 415/415、55 suites，Lint 0 errors / 55 warnings。测试提交 `74f3782a972fdddbf8b4d030e00f164152c1b75d` 已推送；未测试应用待办呈现/设备。定向日志 SHA-256 `2EAD5EFBAF553AA6B5CC8DA815466A2E4D4F58CC19212BA32D8D119DA91A3E46`；JUnit XML SHA-256 `E5510FE9F9C8ECC3C2F81E29B39CF4897530220972EC4E064A932C241BD400C7`；完整 Gradle 日志 SHA-256 `AFFA844FBDCD8AB615E491CFB4D9ADE044C5B8CE9FE87C025A74C1E926FC4E90`；完整 `PowerStoreTest` XML SHA-256 `DF0A260D32C7BF064D274C68E72422AE8925A6416D06526790904E73C008363B`。独立报告 `docs/issues/PC-ISSUE-062-past-incomplete-recurring-todo.md` |
| FQ-38 | 每日 Alarm 随系统时区变化保持本地墙钟时间（REC-P0-076） | **PASS（核心调度与 SQLite）；设备路径 HOLD** | 修复前在东京时区读取纽约创建的每日 07:30 Alarm 得到 20:30；当前 `nextOccurrence` 和 occurrence-key 校验按当前本地时区计算，Boot/时区恢复重建每日 Alarm intent，eventSeq、revision 和原始 occurrence key 不变。修复前定向 1/1 失败；最终定向 2/2、完整 Gradle 417/417、55 suites，Lint 0 errors / 55 warnings。真实 Android `TIMEZONE_CHANGED`、AlarmManager 投递与实体设备未测。代码/测试提交 `f708dfcf076f122cb77f23bd6e5a1ca7f02001ee` 已推送；最终完整日志 SHA-256 `1EC0BF04269D7FDF08E8927A507424FECD9BD78E125C12EA37C4047DC5007810`；APK SHA-256 `BB09CBA105BECEE9EFC9950940AA407B3C1AEEEAC39EC00AFC093B9177B69F4D`。独立报告 `docs/issues/PC-ISSUE-063-local-daily-alarm-timezone.md` |
| FQ-39 | 显式时区 Activity 重复在设备时区变化与 DST 后保持墙钟时间（REC-P0-077） | **PASS（JVM 重复模型回归）** | JVM 默认设备时区设为 Asia/Tokyo，纽约每日 Activity 保持 09:00、slot key 稳定；跨纽约春季 DST 的实际间隔为 23 小时。`occurrences`、`nextOccurrence` 与 `occurrenceForKey` 结果一致。定向 1/1；完整 Gradle 418/418、55 suites，Lint 0 errors / 55 warnings。此为 JVM 模型证据，未执行 Android 时区广播或设备投递。测试提交 `f497330abdd5851752f65f0e53112709a382b2b9` 已推送；完整日志 SHA-256 `B8C47A004349B2991582682DFB7A994604119DD511E57765EAFFB6A80B4D164B`；APK SHA-256 `DB944538964299348D31E8981D6A2F701F420FE74E60B508B8C53B253EC0517D`。独立报告 `docs/issues/PC-ISSUE-064-activity-declared-timezone.md` |
| FQ-40 | 附加 Alarm 提醒保持 Activity 单一正式身份（REC-P0-024） | **PASS（Robolectric API 34 / PowerStore SQLite 存储契约）** | 定向 `ReminderPersistenceTest.activityCanChooseExactAlarmOrMessageIndependentlyOfKind` 1/1；schedule intent 与 Activity 共用 `eventSeq`，store 仅一条身份。完整 Gradle 418/418（55 suites）；timeline rendering/UI 和设备路径未测试。详见 `docs/issues/PC-ISSUE-065-attached-alarm-identity.md` |
| FQ-41 | 直接编辑暂存保持原事件身份与序号水位（REC-P0-057） | **PASS（Robolectric API 34 / EditSession + PowerStore SQLite）** | `directEditorCheckpointsOnceAndNoChangeSaveClearsItWithoutRevision` 1/1；打开与内存输入不分配新身份，单个 checkpoint 复用 draftId/eventSeq，丢弃和 no-op Save 不动身份计数/序号水位。完整 Gradle 418/418；编辑器 UI、IME 和实体设备未测。测试提交 `217b2999ec5ec57e34731bcaabf05edc3c3ff26d`；见 `docs/issues/PC-ISSUE-066-direct-editor-identity.md` |
| FQ-42 | 已解析 AI 提案入队队尾且不夺当前焦点（REC-P0-039） | **PASS（Robolectric API 34 / AiDraftService + PowerStore SQLite + DeckController）** | 已解析的 CREATE 提案经 AiDraftService 持久化到两张现有草稿之后；refresh 后当前第二张卡不变，新卡位于队尾。定向 1/1；完整 Gradle 419/419、55 suites，Lint 0 errors / 55 warnings。真实模型网络、UI 展示和设备行为未测。测试提交 `feb3f2546d2130bce5a94f16a5734ec6dd576379`；见 `docs/issues/PC-ISSUE-067-ai-proposal-queue-focus.md` |
| FQ-43 | AI CREATE/REPLACE 先成为类型化草稿，不直接写正式事件（REC-P0-125） | **PASS（Robolectric API 34 / parser + AiDraftService + PowerStore SQLite）** | CREATE 与 REPLACE 被解析为类型化提案并保留为待处理草稿；CREATE 没有正式记录，既有正式事件标题、revision、开始时间未变。定向 1/1；完整 Gradle 420/420、55 suites，Lint 0 errors / 55 warnings。真实模型网络和设备路径未测。测试提交 `95f1be3ebb99fea96b063e48191e634c0fda1dbc`；见 `docs/issues/PC-ISSUE-068-ai-proposal-no-direct-write.md` |
| FQ-44 | 附件提示注入保持为不可信 user 数据（REC-P0-124） | **PASS（AiClient 请求边界；实时模型未测试）** | loopback stub 验证恶意附件仍在受界定的 user 消息，固定 system prompt 将其标记为不可信，且不提供 tools/tool_choice。定向 1/1；完整 Gradle 421/421、55 suites，Lint 0 errors / 55 warnings。不能据此声称模型实际遵守提示。测试提交 `926f9b2098d9fa529ba8887625b37f135ed1602c`；见 `docs/issues/PC-ISSUE-069-ai-attachment-prompt-injection-boundary.md` |
| FQ-45 | AI 上下文分享关闭时不发送日历事件（REC-P0-123） | **PASS（AiClient 外发边界；调用方单次授权 UI 未测）** | loopback stub 故意向 `AiClient.interpret` 传入私人事件；`shareContext=false` 时请求只包含用户输入，不含 title/notes/location；原列表仍供本地解析。定向 1/1；完整 Gradle 422/422、55 suites，Lint 0 errors / 55 warnings。测试提交 `ad2286864d4b89ce51e7c9dd0fae3508bd52b817`；见 `docs/issues/PC-ISSUE-070-ai-context-optout-boundary.md` |
| FQ-46 | AI 批量授权严格限定范围且完成后不可重放（REC-P0-126） | **PASS（PowerStore SQLite 授权边界；用户确认界面和实时模型未测）** | 定向测试确认完成批次的授权不能提交、旧 epoch 在新批次期间不能重放且没有正式写入；新批次授权可提交已审核草稿。定向 1/1；完整 Gradle 423/423、55 suites，Lint 0 errors / 55 warnings。测试提交 `e8eb48c96f4132c0fe9af63aac8933e0d8435257`；见 `docs/issues/PC-ISSUE-071-ai-batch-authorization.md` |
| FQ-47 | 旧提醒广播必须校验 trigger identity 后才能通知（REC-P0-115） | **PASS（Robolectric receiver/SQLite；Android PendingIntent 送达未测）** | 修复前 synthetic ACTION_FIRE 使用 `retired-trigger` 被错误记为 FIRED；修复后未知/缺失 trigger ID 记为 IGNORED_STALE 且不发布提醒。定向两个测试类 15/15；完整 Gradle 425/425、55 suites，Lint 0 errors / 55 warnings。代码提交 `2e74007c70fc0adccd14fdb42b6d491c58335ee6`；见 `docs/issues/PC-ISSUE-072-stale-alarm-trigger-identity.md` |
| FQ-48 | 稍后提醒不改变重复规则（REC-P0-119） | **PASS（Robolectric receiver/SQLite；Android AlarmManager 送达未测）** | 重复 Alarm 的 ACTION_SNOOZE 与延迟 ACTION_FIRE 后，recurrence、revision、completion keys 与投影实例序列不变。Issue 073 签核时的完整 Gradle 为 425/425 并包含该用例。未改源代码或测试；见 `docs/issues/PC-ISSUE-073-recurring-snooze-series-isolation.md` |
| FQ-49 | 单次跳过持久化且不改动重复序列（REC-P0-120） | **PASS（Robolectric API 34 / PowerStore SQLite；API 35 模拟器及真机未测）** | EditSession 保存一个 occurrence exception 后，同一 eventSeq、时间锚点、其余重复规则字段及非跳过实例均保留；关闭并重开 SQLite 后记录和投影一致。定向 1/1；强制完整 Gradle 426/426、55 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。测试提交 `25f69e1dc6ffa94078e7797e7034f881150be1c3`；独立报告 `docs/issues/PC-ISSUE-074-skip-occurrence-series-isolation.md` |
| FQ-50 | 旧设备 Alarm 导入先保持停用且不建立调度（REC-P0-121） | **PASS（Robolectric API 34 / PowerStore SQLite；API 35、OEM 与实际交接未测）** | 启用状态的精确提醒 Alarm 走默认 handoff-required 导入后正式记录为 disabled、无 durable schedule intent；重复导入保持 idempotent；关闭并重开 SQLite 后状态仍 disabled 且无 intent。定向 1/1；强制完整 Gradle 426/426、55 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。测试提交 `25f69e1dc6ffa94078e7797e7034f881150be1c3`；独立报告 `docs/issues/PC-ISSUE-075-imported-alarm-handoff-gate.md` |
| FQ-51 | 不自动声称读取 OEM 闹钟库存（REC-P0-122） | **PASS（核心适配器 / Robolectric API 34；设备层未测）** | `readUpcoming()` 读取 Calendar `Instances`；闹钟查看/设置使用 `ACTION_SHOW_ALARMS` / `ACTION_SET_ALARM`，设置时不请求跳过系统交互。定向 2/2；完整 Gradle 428/428、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。API 35 intent resolver、真机、OEM 闹钟库存与端到端交接未测。测试提交 `539174c8036924119ed4b8efecba85644e4aa2c3`；独立报告 `docs/issues/PC-ISSUE-076-oem-alarm-inventory-boundary.md` |
| FQ-52 | 事件修订后旧提醒广播不触发提醒副作用（REC-P0-116） | **PASS（Robolectric API 34 / isolated SQLite receiver path）** | 提交 Alarm 新 revision 后回放原 revision/key/trigger/instant；receiver 记录 `IGNORED_STALE`，没有该事件的活动通知，正式记录继续保留新 revision 与标题。定向 1/1；完整 Gradle 429/429、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。Android `AlarmManager` 实际投递与音频效果未测。测试提交 `afc99cb71acc13c7e75df6fff94996db52b387ac`；独立报告 `docs/issues/PC-ISSUE-077-stale-revision-broadcast.md` |
| FQ-53 | 精确闹钟权限不可用时不近似降级（REC-P0-114） | **PASS（Robolectric API 34 / scheduler + isolated SQLite）** | 显式 `ReminderMethod.ALARM` 生成 EXACT 意图；ShadowAlarmManager 拒绝精确调度后，reconcile 持久化 BLOCKED 与权限提示，且没有任何调度任务。定向 1/1；完整 Gradle 430/430、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。API 35 权限变化、真机与 OEM 调度未测。测试提交 `c2310688f92ef432721a93652753dc13fca9012d`；独立报告 `docs/issues/PC-ISSUE-078-exact-alarm-no-approximate-fallback.md` |
| FQ-54 | 撤销精确闹钟权限后重协调为 BLOCKED（REC-P0-113） | **PASS（Robolectric API 34 / scheduler + isolated SQLite）** | EXACT 意图先被标记 SCHEDULED 并提交给 ShadowAlarmManager；撤销能力后再次 reconcile，持久状态转 BLOCKED 且保存精确权限提示。定向 1/1；完整 Gradle 431/431、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。真实系统权限广播、OS 取消行为、API 35、真机/OEM 未测。测试提交 `a45188fd107b05cec1467851acf647422c8230f6`；独立报告 `docs/issues/PC-ISSUE-079-exact-alarm-permission-revoked.md` |
| FQ-55 | 待调度精确闹钟提交及重复重协调（REC-P0-111） | **PARTIAL；REC-P0-111 仍 NOT TESTED** | Robolectric API 34 直接调用 `ReminderScheduler.reconcile()` 后，PENDING→SCHEDULED 且只提交一个任务；第二次调用保持幂等。该用例 1/1；完整 Gradle 432/432、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。`BootReceiver.goAsync()` Android 广播路径未被可靠验证，不将候选行签为 PASS。测试提交 `d4e989cb9233d9be11ad3380234e3374613c1226`；独立报告 `docs/issues/PC-ISSUE-080-pending-intent-reconciliation.md` |
| FQ-56 | 数据库重开后 Draft Deck 仍先打开最早 queueOrder（REC-P0-037） | **PASS（Robolectric API 34 / PowerStore + DeckController core）** | 两张草稿持久化并重开同一 SQLite 后，DeckController 卡片顺序仍为 queueOrder ASC，currentId 聚焦最早草稿。定向 1/1；完整 Gradle 433/433、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。Compose 展示和 API 35 设备重启未测。测试提交 `2ff7bb50eb1c35e978317c6428b04e6363398d27`；独立报告 `docs/issues/PC-ISSUE-081-oldest-draft-first.md` |
| FQ-57 | 修改事件日期不改变草稿队列顺序（REC-P0-041） | **PASS（Robolectric API 34 / PowerStore + SQLite）** | 将队首草稿的事件时间改到后一张之后，暂存并重开 SQLite；事件时间更新，草稿 ID 与 queueOrder 次序保持。定向 1/1；完整 Gradle 434/434、56 suites，0 failures/errors/skips；APK 构建成功；Lint 0 errors / 58 warnings。测试提交 `141750b42472ddcfe365242f7702ff4f3a717080`；独立报告 `docs/issues/PC-ISSUE-082-edit-date-keeps-queue-order.md` |
| FQ-58 | SQLite 重开后重建持久化的 SCHEDULED 闹钟（REC-P0-117 核心子路径） | **PARTIAL（核心重建通过；候选仍 NOT TESTED）** | Robolectric API 34：持久化 SCHEDULED Intent，模拟 AlarmManager 注册丢失，重开同一 SQLite 后直接 reconcile 恢复一个任务。定向 1/1；完整 Gradle 435/435、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。未测 BootReceiver 异步广播、真实系统重启、升级/权限/时区广播或 API 35/真机。提交 `1f82667830fd038d7b2ae37e277516e639edc296`；`docs/issues/PC-ISSUE-083-scheduled-intent-recovery-after-reopen.md` |
| FQ-59 | 单个重复实例改期隔离并跨 SQLite 重开保持（REC-P0-078 核心子路径） | **PARTIAL（单实例核心持久化通过；候选仍 NOT TESTED）** | Robolectric API 34：显式指定原始 occurrence key，只移动 2026-10-21 实例；原系列锚点、其他四个实例时刻与原始键不变，同库重开后投影相同。定向 1/1；完整 Gradle 436/436、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。默认范围选择及“此后/整组”语义未测。测试提交 `6ff8afb3fa4696d07f06981c83d00ad58abb8cbb`；`docs/issues/PC-ISSUE-084-single-occurrence-override-persistence.md` |
| FQ-60 | 从指定重复实例拆分此后序列并保持原子持久化（REC-P0-079 核心子路径） | **PARTIAL（核心事务通过；候选仍 NOT TESTED）** | Robolectric API 34：旧序列截断、新 eventSeq、兼容状态迁移、第二段插入失败回滚；前序槽位全跳过时保留原身份，并在锚点移至所选实例时仅保留剩余次数。两项修复前回归分别复现错误身份/多生成两项；最终定向 4/4。完整 Gradle 440/440、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。编辑器范围选择/调用链、API 35、真机/OEM 与 whole-series 范围未测。提交 `cf1be08500f02adc0edba12c9c97bc4c7d53fea1`、`86043231d14594cce89942f8847c5fe66e7bd178`；`docs/issues/PC-ISSUE-085-this-and-future-series-split.md` |
| FQ-61 | 整组重复规则修改保持序列身份与锚点（REC-P0-080 核心持久化子路径） | **PARTIAL（核心持久化通过；候选仍 NOT TESTED）** | Robolectric API 34：通过 EditSession 改标题、有限次数和例外；同一 eventSeq 与原 startMillis 保持，实例投影在 SQLite 重开后相同。定向 1/1；完整 Gradle 441/441、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。显式整组范围选择、API 35、真机/OEM 未测。测试提交 `49f334e8cea1005de59ef9b98a16e32f544b937e`；`docs/issues/PC-ISSUE-086-whole-series-core-edit.md` |
| FQ-62 | 改变完成跟踪不意外取消提醒意图（REC-P0-017 核心状态隔离子路径） | **PARTIAL（PowerStore/SQLite 状态隔离通过；候选仍 NOT TESTED）** | Robolectric API 34：跟踪开关两个方向均保持事件启用、提醒配置与等价 PENDING 意图；SQLite 重开后记录和调度意图一致。定向 1/1；完整 Gradle 442/442、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。Android 系统调度/送达、API 35、真机/OEM 未测。测试提交 `5b610b7bc48951e1205cf18cc72fe83782d93fac`；`docs/issues/PC-ISSUE-087-tracking-and-reminder-state-independence.md` |
| FQ-63 | 完成日期型 Deadline 后保留 DATE 精度（REC-P0-023 核心持久化子路径） | **PARTIAL（核心持久化通过；候选仍 NOT TESTED）** | Robolectric API 34：完成后保留 deadlineDate，deadlineMillis/startMillis/endMillis 均为空，完成键与日期精度跨 SQLite 重开保持。定向 1/1；完整 Gradle 443/443、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。API 35、真机/OEM 未测。测试提交 `d94d98b1de5d3573420c8fd9e29f58c8f73ae9f8`；`docs/issues/PC-ISSUE-088-date-only-deadline-completion.md` |
| FQ-64 | ICS 投影只读且不生成第二份正式事件真相（REC-P0-082 核心子路径） | **PARTIAL（核心 ICS 投影通过；候选仍 NOT TESTED）** | Robolectric API 34：SQLite 正式 Activity 导出并解析为一条匹配事件；正式行、历史、提醒意图、身份计数和序列号在投影前后及 SQLite 重开后不变。定向 1/1；完整 Gradle 444/444、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。UI/AI 投影、Android SAF、外部日历及设备路径未测。测试提交 `13e720bce54d0b31a179e68e9fa1a86eec518c61`；`docs/issues/PC-ISSUE-089-ics-read-projection-does-not-mutate-store.md` |
| FQ-65 | ICS TZID 输出包含匹配 VTIMEZONE 定义（REC-P1-078） | **PASS（核心 codec 序列化）** | Robolectric API 34：Asia/Shanghai 固定偏移与 America/New_York DST 导出均包含匹配时区定义；DST observance/offset/yearly rule 与 TZID 往返保持。定向 6/6；完整 Gradle 444/444、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。第三方日历兼容未验证。证据基线 `13e720bce54d0b31a179e68e9fa1a86eec518c61`；`docs/issues/PC-ISSUE-090-ics-timezone-definition.md` |
| FQ-66 | AI 批次取消释放锁并隔离旧 epoch（REC-P1-061） | **PASS（核心批次状态恢复）** | Robolectric API 34：第一项完成后注入 `CancellationException`，runner 释放锁并持久化 `CANCELLED`；未完成草稿保留，SQLite 重开后旧授权拒绝。既有孤儿恢复用例模拟旧 owner token 并验证 stale epoch 拒写。定向 1/1；完整 Gradle 445/445、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。未执行在线 provider timeout 或 OS 级强杀。测试提交 `65fb3ca8bda51cf7c6bd91794789e2b152e9603a`；`docs/issues/PC-ISSUE-091-ai-batch-cancellation-recovery.md` |
| FQ-67 | 主动取消 AI 批次后停止后续提交并保留部分结果（REC-P1-060） | **PASS（核心取消与报告持久化）** | Robolectric API 34：首项完成后请求取消，第二项保留待审；runner 返回并持久化 `published=1`、`kept=0`、`cancelled=true`，重开 SQLite 后报告不变且旧授权被拒。定向 1/1；完整 Gradle 446/446、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。未调用模型网络。测试提交 `d79b0f6d11870c5a18c6b7401ebe18e72fe95c05`；`docs/issues/PC-ISSUE-092-ai-batch-user-cancel-partial-result.md` |
| FQ-68 | BootReceiver 开机异步重建期望提醒（REC-P0-111） | **PASS（Robolectric receiver 核心路径）** | Robolectric API 34：`ACTION_BOOT_COMPLETED` 进入 `goAsync()` 后，将持久化 Alarm 从 PENDING 重建为 SCHEDULED，ShadowAlarmManager 收到一项任务，PendingResult 正常结束，SQLite 重开状态保留。定向 1/1；完整 Gradle 447/447、57 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。真实系统开机广播、真机/OEM 未测。测试提交 `86e9d97106592edc41d988db317d47687be89976`；`docs/issues/PC-ISSUE-093-boot-receiver-async-reconciliation.md` |
| FQ-69 | 应用升级、时区/时钟、精确闹钟权限与内部重协调广播（REC-P0-117） | **PARTIAL（Robolectric API 34 合成 receiver 派发；候选仍 NOT TESTED）** | 对五条非开机 action 逐条清除 ShadowAlarmManager 注册后再派发；每条都进入 `goAsync()`、完成 PendingResult，并从持久 SCHEDULED 意图恢复一个注册。与 Issue 093 的开机分支合计覆盖所有声明 action。定向 2/2；完整 Gradle 448/448、57 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。Android 实际广播、重启、升级、权限/时区系统变化、API 35 与真机/OEM 未测。测试提交 `3983bf7dcc1ba5b5b1ec9e84d621fe4aad77d99c`；`docs/issues/PC-ISSUE-094-reminder-recovery-broadcast-matrix.md` |
| FQ-70 | AI 批次独立草稿中间项失败仍继续并持久化部分报告（REC-P1-058） | **PASS（PowerStore SQLite / runner 核心路径）** | SQLite trigger 让三项批次的中间项在 schedule_intents 写入时失败；修复前回归复现仅发布 1 项（期望 2）。修复后首尾项提交，中间项回滚并保留，报告为 published=2/kept=1/cancelled=false，FAILED 状态和报告跨 SQLite 重开保持，锁释放、旧 session 被拒，并可通过新授权重试。定向 1/1；完整 Gradle 449/449、57 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。未调用模型网络。提交 `361f074538a27ad75c2a91c7da4bdee6beb46795`；`docs/issues/PC-ISSUE-095-ai-batch-independent-item-failure.md` |
| FQ-71 | 拒绝 AI 修改提案只丢弃提案、不改正式事件（REC-P1-055） | **PASS（PowerStore/SQLite 核心批次路径）** | Robolectric API 34：对已存在 Activity 创建 AI REPLACE 草稿，通过批次 DISCARD 后核验正式事件快照、提交历史不变；草稿 DISCARDED、报告 discarded=1、锁释放，SQLite 重开后均保持。定向 1/1；完整 Gradle 450/450、57 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。未调用模型或网络。测试提交 `65f6cabd653878f77127e167a583bea31043e5e3`；`docs/issues/PC-ISSUE-096-ai-proposal-rejection-does-not-mutate-event.md` |


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

### PC-ISSUE-080 · P0 / 调度器内层 PASS；异步 Android 入口 NOT TESTED
- `ReminderValidationTest.pendingExactIntentIsSubmittedAndMarkedScheduledIdempotently` 验证 PENDING→SCHEDULED、Android AlarmManager 适配器提交一次、第二轮重协调仍只有一个任务。
- Robolectric API 34 定向 1/1；完整 Gradle 432/432、Lint 0 errors / 58 warnings。`BootReceiver.goAsync()` 入口未能由当前 Robolectric 测试可靠驱动，故 `REC-P0-111` 保持 NOT TESTED。详细证据与失败尝试记录见 `docs/issues/PC-ISSUE-080-pending-intent-reconciliation.md` 和 `.local/android-test/reports/freeze-20261024/pc-issue-080-async-receiver-test-triage.md`。

### PC-ISSUE-081 · P0 / CORE CONTROLLER PASS · 草稿队列按持久顺序打开
- `REC-P0-037` 由 `PowerStoreTest.openingDeckAfterStoreReopenFocusesOldestQueueOrder` 签核：重开 SQLite 后 controller 卡片按 `queueOrder` 升序，焦点落在最早草稿。
- 定向 1/1；完整 Gradle 433/433、Lint 0 errors / 58 warnings。此签核仅覆盖 PowerStore/DeckController 核心层；API 35 展示层未测。详见 `docs/issues/PC-ISSUE-081-oldest-draft-first.md`。

### PC-ISSUE-082 · P0 / CORE QUEUE SEMANTICS PASS · 改事件日期不改变队列顺序
- `REC-P0-041` 由 `PowerStoreTest.changingDraftEventDateDoesNotReorderQueueAfterRestart` 签核：改变队首草稿的事件时间使其晚于后一草稿，暂存并重开 SQLite 后，事件时间已更新而 draft ID / queueOrder 次序保持。
- 定向 1/1；完整 Gradle 434/434、Lint 0 errors / 58 warnings。该证据覆盖 PowerStore/SQLite 核心队列语义。详见 `docs/issues/PC-ISSUE-082-edit-date-keeps-queue-order.md`。

### PC-ISSUE-083 · P0 / CORE REARM PASS · REC-P0-117 remains NOT TESTED
- `ReminderValidationTest.scheduledIntentIsRearmedAfterStoreReopen` verifies that a durable SCHEDULED intent survives SQLite reopen and that direct `ReminderScheduler.reconcile()` recreates exactly one AlarmManager task after the simulated registration loss.
- Focused 1/1; full Gradle 435/435 across 56 suites, 0 failures/errors/skips; Lint 0 errors / 58 warnings. The system-triggered asynchronous receiver and actual device reboot/upgrade/permission/timezone recovery remain untested; therefore `REC-P0-117` stays NOT TESTED. See `docs/issues/PC-ISSUE-083-scheduled-intent-recovery-after-reopen.md`.

### PC-ISSUE-084 · P0 / 单实例改期核心持久化子路径通过 · REC-P0-078 remains NOT TESTED
- `PowerStoreTest.editingOneRecurringOccurrencePersistsOverrideWithoutChangingOtherSlotsAcrossRestart` 显式指定一个原始实例键并改期，验证系列锚点、其他实例、身份及历史记录，并跨 SQLite 重开复核。核心模型/存储子路径通过；默认范围选择、“此后”和“整组”编辑没有签核。
- 定向 1/1；完整 Gradle 436/436、56 suites、0 failures/errors/skips；Lint 0 errors / 58 warnings。测试提交 `6ff8afb3fa4696d07f06981c83d00ad58abb8cbb`，独立报告 `docs/issues/PC-ISSUE-084-single-occurrence-override-persistence.md`。因此候选 `REC-P0-078` 仍为 NOT TESTED，整体冻结保持 HOLD。

### PC-ISSUE-086 · P0 / 整组重复规则核心持久化子路径通过 · REC-P0-080 remains NOT TESTED
- `PowerStoreTest.wholeRecurringSeriesEditKeepsIdentityAndAnchorAcrossRestart` 通过 EditSession 修改规则并验证原 `eventSeq`、`startMillis`、实例投影和 SQLite 重开后状态保持；没有测试显式范围选择入口。
- 定向 1/1；完整 Gradle 441/441、56 suites、0 failures/errors/skips，Lint 0 errors / 58 warnings。测试提交 `49f334e8cea1005de59ef9b98a16e32f544b937e`，独立报告 `docs/issues/PC-ISSUE-086-whole-series-core-edit.md`。候选保持 NOT TESTED；整体冻结保持 HOLD。

### PC-ISSUE-087 · P0 / 跟踪与提醒持久状态隔离子路径通过 · REC-P0-017 remains NOT TESTED
- `PowerStoreTest.changingCompletionTrackingPreservesReminderIntentAcrossRestart` 对两种跟踪开关方向验证事件 enabled、提醒设置及待调度 occurrence/触发时间不被隐式修改，且跨 SQLite 重开保持。
- 定向 1/1；完整 Gradle 442/442、56 suites、0 failures/errors/skips，Lint 0 errors / 58 warnings。测试提交 `5b610b7bc48951e1205cf18cc72fe83782d93fac`，独立报告 `docs/issues/PC-ISSUE-087-tracking-and-reminder-state-independence.md`。Android 系统调度和实际送达未测；候选保持 NOT TESTED，整体冻结保持 HOLD。

### PC-ISSUE-088 · P0 / 日期型 Deadline 核心精度持久化子路径通过 · REC-P0-023 remains NOT TESTED
- `PowerStoreTest.completingDateOnlyDeadlinePreservesDatePrecisionAcrossRestart` 完成 tracked date-only Deadline 后，`deadlineDate` 保持、`deadlineMillis/startMillis/endMillis` 仍为空；完成键及 DATE 精度跨 SQLite 重开保持。
- 定向 1/1；完整 Gradle 443/443、56 suites、0 failures/errors/skips，Lint 0 errors / 58 warnings。测试提交 `d94d98b1de5d3573420c8fd9e29f58c8f73ae9f8`，独立报告 `docs/issues/PC-ISSUE-088-date-only-deadline-completion.md`。没有 UI 渲染或设备验收，候选保持 NOT TESTED，整体冻结保持 HOLD。

### PC-ISSUE-089 · P0 / ICS 核心只读投影子路径通过 · REC-P0-082 remains NOT TESTED
- SQLite → ICS 导出/解析只产生与正式 Activity 对应的一条事件；投影前后及 SQLite 重开后，正式行、历史、提醒意图、身份与序列号保持不变。该证据不覆盖 UI/AI 投影、Android SAF/外部日历或设备路径，候选保持 NOT TESTED。报告 `docs/issues/PC-ISSUE-089-ics-read-projection-does-not-mutate-store.md`。

### PC-ISSUE-090 · P1 / ICS TZID 与 VTIMEZONE codec 契约通过 · REC-P1-078 PASS
- 现有 `IcsExportRecurrenceTest` 定向 6/6 通过：固定偏移与纽约 DST 时区均有匹配 `VTIMEZONE`，规则和时区经解析往返保持。完整 Gradle 444/444。只签核 codec 输出契约，不代表每种第三方日历消费者兼容。报告 `docs/issues/PC-ISSUE-090-ics-timezone-definition.md`。

### PC-ISSUE-091 · P1 / AI 批次取消清理与旧授权隔离 · REC-P1-061 PASS
- 新增核心回归覆盖第一项提交后取消：已完成项保留、未完成草稿保留、批次锁释放并持久化为 `CANCELLED`；SQLite 重开后旧 `AiSession` 不能写入。既有孤儿恢复回归另覆盖旧 owner token 恢复与旧 epoch 拒写。
- 定向 1/1；完整 Gradle 445/445、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。只验证 runner/PowerStore 状态恢复；在线模型超时和 OS 级进程强杀未执行。独立报告 `docs/issues/PC-ISSUE-091-ai-batch-cancellation-recovery.md`。

### PC-ISSUE-092 · P1 / 主动取消返回准确的部分批次结果 · REC-P1-060 PASS
- 在 runner 进度回调中发出存储层取消请求，验证后续草稿未提交、首项提交保留，报告 `published=1`、`kept=0`、`cancelled=true` 并跨 SQLite 重开保持；旧 session 再提交被拒绝。
- 定向 1/1；完整 Gradle 446/446、56 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。未调用模型服务。独立报告 `docs/issues/PC-ISSUE-092-ai-batch-user-cancel-partial-result.md`。

### PC-ISSUE-093 · P0 / BootReceiver 异步恢复期望提醒 · REC-P0-111 PASS（API 34 核心路径）
- `ACTION_BOOT_COMPLETED` 经 `BootReceiver.goAsync()` 重建 SQLite 中 PENDING Alarm；ShadowAlarmManager 注册一项任务，PendingResult 在异步结束时完成，重开 SQLite 后仍为 SCHEDULED。
- 定向 1/1；完整 Gradle 447/447、57 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。只签核 Robolectric receiver 核心路径，真实系统启动、API 35 与物理/OEM 设备未测。独立报告 `docs/issues/PC-ISSUE-093-boot-receiver-async-reconciliation.md`。

### PC-ISSUE-094 · P0 / 恢复广播异步矩阵 · REC-P0-117 保持 NOT TESTED
- 新增五种非开机 receiver action 合成派发；每次先清除已有闹钟注册，再验证 goAsync() 完成并从 durable SCHEDULED 意图重建唯一注册。已声明的 boot action 由 Issue 093 覆盖。
- 定向 2/2；完整 Gradle 448/448、57 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。环境无 adb，实际 Android 广播、重启/升级/权限/时区变化及真机/OEM 未测。候选 REC-P0-117 继续 NOT TESTED。独立报告 docs/issues/PC-ISSUE-094-reminder-recovery-broadcast-matrix.md。

### PC-ISSUE-095 · P1 / 独立 AI 批次单项失败与部分结果恢复 · REC-P1-058 PASS
- 修复前注入中间项 schedule_intents 写入失败，批次只发布一项（期望两项），第三项被跳过。根因为 runner 在整个循环外捕获异常。
- 修复后单项失败被记录并继续；首尾项成功，中间项回滚并保留；部分报告与 FAILED 终态跨 SQLite 重开保持，锁释放、旧授权拒绝，新批次可重试失败草稿。
- 修复前复现日志 SHA-256 62EF0C91214E7C38C0DABAC65B2A60571C9B17E38264239510B60D8184C950FC；最终定向 1/1；完整 Gradle 449/449、57 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。未调用模型网络。独立报告 docs/issues/PC-ISSUE-095-ai-batch-independent-item-failure.md。

### PC-ISSUE-096 · P1 / 拒绝 AI 修改提案不改动正式事件 · REC-P1-055 PASS
- 新增 PowerStore/Robolectric 回归：对已提交 Activity 创建 AI REPLACE 草稿，再通过 `AiBatchRunner.Mode.DISCARD` 拒绝；只丢弃草稿，正式快照和历史在操作后及 SQLite 重开后均不变，批次正常结束并释放锁。未发现生产缺陷，本次只补逐案测试证据。
- 定向 API 34 1/1 PASS；完整 Gradle 450/450、57 suites，0 failures/errors/skips；Lint 0 errors / 58 warnings。测试提交 `65f6cabd653878f77127e167a583bea31043e5e3`。独立报告 `docs/issues/PC-ISSUE-096-ai-proposal-rejection-does-not-mutate-event.md`。
### PC-OBS-005 · NEEDS REPRO · 部分提交操作即时反馈可能存在时序竞争
- 旧 Round 1/2 观察：某些 Publish 之后立刻采样 UI 未见结果，稍后实际提交成功。
- 当前证据不足以证明业务写入失败；按确定性等待稳定后复现再立缺陷。

### PC-TECH-006 · P3 / DEBT · Lint 有 55 个 Warning
- 最新完整回归：0 Errors、55 Warnings；类别主要为 `UseKtx`、`ComposableNaming`、`UnusedResources`、`IconLauncherShape` 等。证据见 `.local/android-test/reports/freeze-20261023/issue001-dst-full-gradle-20261023.log`。
- Kotlin 编译还存在已弃用 API 与交叉类型推断告警（未来 Kotlin 版本可能升级为编译错误）。
- 非单独冻结阻塞，后续按真实兼容性风险排序处理。

## 3. 冻结门槛与证据缺口（BLOCKED/NOT VERIFIED，不能记为代码 FAIL）

### PC-QA-GATE-001 · P1 / PARTIAL / HOLD · 核心合同风险验收矩阵（2026-10-24 当前签核）

- 原始 209 项表无法提供；按用户明确指示，不再为其暂停或反复追索。以 `CORE_CONTRACT_FREEZE_2026-10-21.md`、已有验收文档、测试、历史 Issue 和实际产品行为重建当前矩阵。所有重建项均标 `RECONSTRUCTED` / `UNMAPPED`；不强凑数量、不伪称历史映射，优先级由当前风险决定。
- 当前活动候选 209 项（该数量为移除伪历史占位、退役已接受淡灰边缘项并新增 PC-ISSUE-135/136/137/138/139/140/141/142/143/144/145/146 后的偶然结果，不是目标），P0=144、P1=64、P2=1；状态 182 PASS / 0 FAIL / 8 BLOCKED / 13 NOT TESTED / 6 SPEC UNRESOLVED，另有 1 条 `RETIRED / OUT_OF_SCOPE`。
- `REC-P1-013` 淡灰横向边缘已由用户确认接受，留存旧证据但不再作为 GO 条件。贪心标题折叠仍按独立 P1 `REC-P1-021`–`030` 签核；蓝色当前时间线覆盖中轴符合用户确认的设计。
- 原始 209 项 ID 映射属于文档溯源缺口，需如实披露，但不阻塞开发或取代核心合同冻结标准。结构校验产物 `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r12-pc-issue-146.json` SHA-256 `FAA809C1589F8C49F5104BD2A1F30ACEEDD8BE4A6944F8BAA615783A44D6277F`，台账 SHA-256 `224B1F1DD2D7F85ACA8E0395AA3C19EE87967CFD583C03E58589835F7BB1481E`；仅检查结构，`product_behavior_tested=false`。PC-ISSUE-146 将 `REC-P1-052/053` 窄化签核为提案操作选择及批次授权清单/快照的 Compose 行为，定向 4/4、完整 Gradle 482/482；未运行独立 AVD 或实体设备。PC-ISSUE-145 的左右翼独立滚动、PC-ISSUE-144 的 DST 刻度与 PC-ISSUE-143 的 Today 修复见各自独立报告。正式冻结继续 **HOLD**，直到测试与外部/设备门槛闭环。

### PC-QA-GATE-001 · 历史快照（此前按原始 209 项映射追踪；已被上方当前政策取代）
- 历史规划明确 209 核心验收项（P0 126，P1 82，另 1 项优先级待核）。
- 当前跟踪的仓库文件中只有对“209 项”的描述，**没有找到 209 个完整编号用例及 PASS/FAIL/证据 ID 一一映射**。
- 因此 **209 项未逐案签核**；既不能说全部未实际测试，也不能根据 325+37 单元测试声称全部通过。
- 本轮只读审计仍未找到原始逐案清单；已建立 208 条 RECONSTRUCTED 候选项及 1 条未定义历史槽位，见 `docs/CORE_FREEZE_ACCEPTANCE_LEDGER.csv`。候选按风险给出 P0=126/P1=82 建议分配，但 0 个原始 ID 有映射，优先级仍待审核。
- 当前候选逐案状态：P0 PASS 111 / FAIL 0 / BLOCKED 1 / NOT TESTED 9 / SPEC UNRESOLVED 5；P1 PASS 24 / FAIL 0 / BLOCKED 8 / NOT TESTED 49 / SPEC UNRESOLVED 1；历史未分类槽位 SPEC UNRESOLVED 1。共 135 PASS、0 FAIL、9 BLOCKED、58 NOT TESTED、7 SPEC UNRESOLVED；117 条重建候选有独立定点行为证据。`REC-P0-054` 因产品基线与 SQLite v4 的同事件草稿数量规格冲突保持未决，见 `docs/issues/PC-ISSUE-047-human-ai-draft-cardinality-spec-conflict.md`。`REC-P0-058`、`059`、`069`、`025/027`、`026`、`089/090`、`021/022`、`081`、`018`、`019`、`057`、`110`、`108`、`084`、`064`、`065`、`066`、`067`、`076`、`077`、`123`、`115`、`119`、`120`、`121`、`122`、`116`、`114`、`113`、`124`、`125`、`126`、`037`、`041` 和 `111` 均有独立 Issue 报告记录对应核心回归证据。`REC-P0-111` 的证据限定为 Robolectric API 34 的 `BootReceiver.goAsync()`、持久化提醒重建及 `PendingResult` 完成；真实系统开机广播与物理/OEM 行为未测。`REC-P0-117` remains NOT TESTED. Issue 093 proves the synthetic boot receiver path; Issue 094 tests five non-boot receiver actions by synthetic dispatch and re-arms a cleared registration for each. Actual Android system delivery and device recovery remain untested. `REC-P0-078` remains NOT TESTED for default-scope selection; an explicitly selected instance override has core SQLite evidence. `REC-P0-079` remains NOT TESTED with partial core evidence for atomic this-and-future splitting; editor scope-selection/call-path and device behavior are untested. `REC-P0-080` remains NOT TESTED; Issue 086 covers same-series EditSession persistence and SQLite reopen, not explicit whole-series scope selection. `REC-P0-017` remains NOT TESTED with partial state-isolation evidence; see Issue 087. `REC-P0-023` remains NOT TESTED; Issue 088 has partial core evidence for preserving date-only Deadline precision after completion. `REC-P0-082` remains NOT TESTED; Issue 089 has partial core evidence for the read-only SQLite-to-ICS projection, while other projections and Android SAF/device execution are not covered. `REC-P1-078` PASS: Issue 090 verifies matching ICS VTIMEZONE definitions for fixed-offset and DST TZID output; external consumer compatibility is untested. `REC-P1-061` PASS: Issue 091 verifies core AI batch cancellation cleanup, persisted unlock, and stale-session fencing; live provider timeout and OS-level process kill are not covered. `REC-P1-060` PASS: Issue 092 verifies that runner cancellation stops later writes and persists the partial outcome across SQLite reopen. `REC-P1-055` PASS: Issue 096 verifies that discarding an AI REPLACE proposal leaves the formal event snapshot and history unchanged across SQLite reopen. `REC-P1-058` PASS: Issue 095 verifies independent-item failure isolation, partial-report persistence, and a successful retry under fresh authorization. 最新结构验证 artifact 为 `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-096.json`，SHA-256 `75C34EC671D238E5F1D1D7F71B1D584AF6124188921EED358283A09436D23C15`；CSV SHA-256 `8A9FCAF99E62FFB0F80E3E3959AC7B86C36D6D9567321D7C0DE8A4FFF9BD5FFE`。验证只检查结构与计数，不测试产品行为。
- 最新完整 Gradle 工作树结果 450/450 通过（449 个已跟踪测试 + 1 个受保护未跟踪测试），57 suites、0 failures/errors/skips；Lint 0 errors / 58 warnings。日志 `.local/android-test/reports/freeze-20261024/pc-issue-096-full-gradle-final-20261024-r1.log`，SHA-256 `43A8E132DECBCD3057072447A5F3EE646B2B8FF5F3ED67DBAD5719CE7A6D894E`；BootReceiverRecoveryTest XML SHA-256 `5ED78F245E9464AD0F946895B336B3C8FA765F0E9827F2B74D2EBC701A8EB908`; PowerStoreTest XML SHA-256 `984329BE08AC7EE7A3BEC95B780ADD5BD0BECBF8DDCC506F7B146D60AF5B46FE`；APK SHA-256 `D941A9BF701896C7D9E232540856D5854EA9EC8CFD6AEB2AB0AA9CDD8DE72ABB`。测试提交 `65f6cabd653878f77127e167a583bea31043e5e3` 与此前修复/测试提交均已推送。
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

## PC-ISSUE-147 — 明确日程请求与随口建议分流（2026-10-24）

根因是任何 `type=draft` marker 都会自动进入结构化提案解析。修复后 `intent=explicit` 才按当前用户原文启动自动预览；`suggested` 只留轻量按钮；缺失/未知值回退为建议，模型生成的 `source` 不进入 parser。定向本地测试 13/13；完整离线 Gradle 483/483、62 suites，0 failures/errors/skips，lint 0 errors/58 warnings。

代码/测试提交 `8e642ad0d8e1ba5786f2e2ee0bdbb16851a5b9b2` 已推送。定向日志 SHA-256 `87B8BFEE7D29155A458C6506DEE81DF578BB64E3BFDD1F56934BB43A14C8E01F`；完整日志 SHA-256 `47848074DB10EC273E31AAACCA2F1578DC454549862070593CEA6A2EBF47C9FF`；Debug APK SHA-256 `751E4DA86160A2032E9D74052AB3651E8B76FF83649FF71906404C86A93CA068`。独立报告：`docs/issues/PC-ISSUE-147-ai-explicit-vs-suggested-chat-routing.md`。

没有调用 provider、运行独立 Android 虚拟设备或实体设备。真实模型分类和 Compose 按钮行为未覆盖；`REC-P1-048` 保持 NOT TESTED，`REC-P1-047` 保持 BLOCKED，PC-ISSUE-002 与整体冻结仍 **HOLD**。重建矩阵统计不变：182 PASS / 0 FAIL / 8 BLOCKED / 13 NOT TESTED / 6 SPEC UNRESOLVED；结构校验 `product_behavior_tested=false`。
