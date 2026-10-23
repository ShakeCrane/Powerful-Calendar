# PC-ISSUE-111 — 重复规则“此项及以后”核心事务签核

日期：2026-10-24

候选：`REC-P0-079`（重建候选；历史 209 项映射仍为 `UNMAPPED`）

状态：**PowerStore 核心拆分事务 PASS；整体冻结仍 HOLD**

## 核心验收

本次只签核 `PowerStore.saveThisAndFutureRecurringEdit` 的事务语义。四条 Robolectric API 34 回归覆盖：从所选原始 occurrence 开始拆分两组序列、只迁移新规则仍生成的未来例外/覆盖/完成键、插入新身份失败时回滚、首个有效 occurrence 保留原身份并校正剩余次数。

结果验证了旧序列在所选槽位前结束，新序列从所选原始 key 开始并取得新身份；兼容状态和历史正确迁移，投影跨 SQLite 重开一致。注入新对象插入失败后，旧记录、历史与身份分配器保持原状。选择首个 active slot（包括先前 occurrence 都被 exception 跳过的情况）时沿用原身份，有限 count 仅保留剩余 occurrence 数。因此 `REC-P0-079` 对这项核心事务契约签为 PASS。

## 验证证据

- 定向 Robolectric API 34：**4/4 PASS**，0 failures/errors/skips。日志 SHA-256：`77AB328142E24F2EA8129557FA6329DF41D078A1DB272883A302F91F1559C162`；JUnit XML SHA-256：`EDE4EE7FB1756E4C3776FDCBAF8435B17A130205359714BB920E6E0B43AA7898`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。**BUILD SUCCESSFUL**；460/460 测试、57 个套件，0 failures/errors/skips；lint 0 errors / 58 warnings。完整日志 SHA-256：`3F35F3A143BC2498F95CD7E97C1121AEF5E397619E5A37C1A266419A84F27FFB`。
- 全量 `PowerStoreTest` XML SHA-256：`0A14D9F5181FE7975DECBD2678C34F500F2499C46A8D3C4995F04CD2BE36FF17`；lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 定向与全量日志/JUnit XML 保存在 `.local/android-test/reports/freeze-20261024/pc-issue-111-*`。完整工作区回归包含一项受保护的未跟踪测试；本 Issue 未改动或暂存它。

## 台账与边界

- `REC-P0-079` 已收窄为直接调用核心存储操作的事务验收；没有签核编辑范围选择流程或设备行为。
- 结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-111.json`，SHA-256 `E7C3F871A1DD2743FC045B8251D116AAE0EF0AD47626CF24ED69B889A371B3AC`；CSV SHA-256 `8F75A6EB9BA2046EF604B0B9558BECF34ECFBF8AA9AD6EC995A8FFAA6A107E2A`。校验 209 行、唯一 ID、必填字段、证据完整性与 126/82/1 分区；`product_behavior_tested=false`。
- 候选计数：**140 PASS / 0 FAIL / 9 BLOCKED / 53 NOT TESTED / 7 SPEC UNRESOLVED**；P0 **116/0/1/4/5**，P1 **24/0/8/49/1**。历史用例 ID 仍无映射，整体保持 **HOLD**。
- 没有修改应用或测试源码。
