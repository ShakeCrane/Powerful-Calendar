# PowerCal 核心范围冻结签核｜2026-10-27

## 所有者裁决

2026-10-27 所有者明确指示：「可以冻结核心，准备进入分离阶段」。

**CORE SCOPE FREEZE = APPROVED；UI / SKIN SEPARATION ENTRY = GO。**
**RELEASE / EXTERNAL QA = HOLD。**

这表示结束常规核心功能迭代、授权进入 UI/皮肤分离开发；**不**表示全部验收项 PASS、实际设备/OEM/真实 AI Provider/第三方 ICS 已验收，亦不代表正式 Release GO。

## 被冻结的应用源码及可复现证据

- 应用源码 Git SHA：b1f7629f0c52812e1887443872f43aa6c49908c4。后续文档提交 1c4026990339e0678c8356e763c49366e994bd6b 不涉及业务代码。
- 应用版本 0.9.5-rc.1，versionCode=905，包名 dev.powerfulcalendar。**Debug 候选包，不是生产 Release**。
- APK：.local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-b1f7629-debug-core-HOLD.apk
- 大小 66,167,593 bytes；SHA-256 DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76；Android Debug v2 签名和 ZIP CRC 均已验证。
- 纯净源码独立构建：88/88 Gradle tasks、560/560 JVM tests、78 suites，0 failed/errors/skipped；Lint 0 errors、52 warnings。Debug/AndroidTest APK 构建通过。**没有执行 AndroidTest 仪器测试**。
- 独立构建出的 Debug APK SHA-256 与交付包完全一致。
- 日志：.local/android-test/reports/freeze-20261027/core-b1f7629-committed-isolated-gate.log；详见 docs/issues/PC-ISSUE-176-left-title-ai-review-deadline-default.md。

## 本次锁定的业务与视觉行为

- 独立的 ALARM、ACTIVITY、DEADLINE、MEMO；稳定 eventSeq/revision/occurrence，时间轴真实几何，DST、重复规则、授权发布及提醒状态语义不变。
- 左右标题统一字号、框外绘制、按真实文字矩形贪心折叠。右侧由所有者明确真机验收为可冻结。左侧上移缺陷修复后已通过本地自动化，未收到再次实体机截图，但本次所有者批准整体核心范围冻结。
- 「今日」定位本地中午，重置 24 小时视野。
- AI 明确日程意图可进入审核提案；信息不全时能展示原四按钮待补全卡，未经补全和正式授权不得发布；普通聊天不会直接变成正式写入。
- 精确到时分的 Deadline 默认提前 60 分钟提醒；date-only 不臆造时分，保留日期精度。
- 编辑器打开无有效业务 Diff 不落地持久化草稿；不破坏聊天持久化、卡组版本或权限边界。

## 携带进入分离阶段的未签核风险

受其他 Agent 占用的 docs/CORE_FREEZE_ACCEPTANCE_LEDGER.csv 当前有**并行未提交修改**；本轮只读统计 218 行（205 PASS、10 BLOCKED、1 NOT TESTED、1 SPEC UNRESOLVED、1 RETIRED）。该统计不等于 205 项现场重新执行，更不能将其余行假写 PASS。

遗留：真实 AI Provider 结构化意图/多轮提案；外部 ICS 含 DST 递归系列的导入再导出；OEM 提醒省电、TalkBack、低端机与大字体等；date-only Deadline 尚未完全签核的规格；旧版 REC-P1-002 「今日」验收行仍与新的已确认规则冲突；历史 209 项原始 ID 映射缺失。保持原状态、独立记录与后续验收，不用本次 Owner GO 伪装为技术 PASS。

**所有者本次批准的是进入分离开发，不是豁免公开发行的测试、安全、数据恢复门禁。**

## 冻结纪律

1. 分离阶段不得顺手改动 core、AI 解释/审批、提醒调度、存储 schema、复发规则等核心语义。确有严重回归或安全问题，必须先独立 Issue + 复现 + 兼容风险 + 最小修改方案，再按授权执行与完整回归。
2. 允许补充受限展示投影和 Host Bridge 能力。皮肤端仅接收最小权限、版本化只读投影及受控命令；不得拥有第二个业务写入/AI/提醒/生命周期实现。
3. 原生 Native UI 暂时仍是完整可用的恢复路径；分离阶段不等于立即切默认皮肤、删除原生视图、修改旧用户皮肤选择。
4. 切换皮肤不得更改、清空、迁移失败覆盖正式数据，也不能偷看凭据。AI 卡组的用户授权不得变成单纯 JS 发出的 confirm=true。
5. 不触碰正在由其他 Agent 编辑的 ledger CSV、D05 测试、DraftDeckFeedbackTest、design/、PC-OBS-005 和无关缓存。
6. 独立的默认皮肤 Cutover Gate 必须通过功能、无障碍、实体设备、IME、灾难恢复等验证并单独取得 Owner 同意。

## 新阶段入口

见 docs/SEPARATION_PHASE_KICKOFF_2026-10-27.md；冻结标签和分离分支是 Git 版本证据，不是应用 Release 标签。
