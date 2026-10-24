# PC-ISSUE-PHASE2 — 重复事件实例范围编辑接通

日期：2026-10-25

状态：**自动化、API 35 AVD 子项 PASS；实体设备由用户验收；核心冻结仍 HOLD**

实现提交：`9036a2a8db2e1a669f64cc6174acdc569c4c08b3`

## 根因

重复事件投影本来包含 occurrence key，但时间轴点击只把正式事件 owner 交给 `EditSession`；月份与 Memo 投影也没有把目标槽位交给编辑器。`EditSession` 仅能保存整个正式记录，未要求用户选择“仅本次 / 本次及以后 / 整个系列”。因此已有的 `PowerStore.saveThisAndFutureRecurringEdit` 事务没有通过产品编辑器调用，单次实例也没有稳定的 override 保存路径。

复核还发现两个边界缺陷：选中实例若已有标题 override，未来拆分时旧 override 会遮住新系列标题；正式事件保存后，如果 Compose 表单值与保存后的值相同，纯 Kotlin session 的基线更新不会自动触发重组，编辑器会继续把“放弃本次修改”显示为可用。

## 修复

- `DisplayOccurrence` 保留原始 occurrence key；时间轴、月份及 Memo/Todo 入口把正式 owner 和目标 key 一起传给 `EditSession.open`。没有具体目标 key 的普通事件入口仍可编辑整个系列。
- 为重复事件增加显式 `RecurrenceEditScope`。未选范围时不展示可编辑字段且保存按钮禁用；恢复 checkpoint 时同时恢复 scope 与原始 key。范围元数据不作为新事件、普通提交、AI batch 或通用草稿发布输入。
- “仅本次”把变更写入指定原始槽位的 occurrence override，只开放标题与时间；地点、备注、提醒、重复规则等系列字段不允许被静默改写。
- “本次及以后”调用现有原子拆分事务；“整个系列”沿用正式事件的版本栅栏与保存路径。选中槽位的有效字段成为新系列锚点，因此拆分时不再迁移该槽位的旧 override；其他可兼容未来 override、例外与完成状态仍由原迁移逻辑处理。
- 正式保存成功后提升编辑器 UI revision，使按钮和表单基线立即刷新，避免重复操作旧状态。

## 验证

定向核心/Compose 回归：9/9 通过，覆盖既有 skip、单次 override、whole-series 编辑，以及 checkpoint 恢复、重复保存冲突、旧标题覆盖、秋季 DST fold 首个 01:30 和保存后表单复位。日志 SHA-256：`4A63F4950C317DD71C245CCAA6AD79AA61BD29381B0AABBD7F3220D00714C82D`。

额外边界组：5/5 通过，日志 SHA-256：`060017A30AB39B5A9A464EF066EB3E319DE62777F963711D67D441D19F911351`。

API 35 `emulator-5554` AndroidJUnitRunner 实测 Compose 编辑器：**1/1 PASS**。未选择范围时“保存修改”不可用；选中“本次及以后”、编辑标题并保存后，旧系列只保留选中实例之前的 occurrence，新系列从所选原始槽位开始，count、标题和身份符合预期。测试使用随机隔离 SQLite 数据库并在 `finally` 中删除。

执行结束后，原 app/test APK 均重新安装并与运行前字节摘要匹配；应用数据库文件名清单摘要前后相同：`1339CD66F7353794C16E08D6BCC0E55E958C1FFD36D740CD19CC934792B113EA`。运行前/恢复后的 app APK SHA-256 均为 `7A5476F972473030B7DF2AE0E2CA6F305A71BD2A6092CFDA02F9B3378F40A03B`；test APK 均为 `79C9A6D83010087116B50D79012732BA9A6329D7D9B394661AD1C351D38D2F75`。未清除数据、卸载应用、wipe AVD 或操作 `emulator-5556`。

最终完整 Gradle 门禁：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

结果：**BUILD SUCCESSFUL**，522/522 JVM tests、70 suites、0 failures/errors/skips，88/88 tasks 执行；Debug 与 AndroidTest APK 构建成功；lint 0 errors / 56 warnings。完整日志 SHA-256：`42C63E29348DD23EAAFDC44EE0C2DD578D0F5F57DA091C6B80C8056444A01858`。

最终 APK：Debug `90BB816EC85AFD46C195C48697F1234D98D2C4571FB5209F0F1FD87B088DBDE8`；AndroidTest `5692FBC5C964C63CAF61EEED71F4BA51390282C3125729F1F24210944CBAA3F3`。Lint XML SHA-256：`BC35B853A90869F00471C96B11B6CDFA2B457DFC03C5D654B74CAB3ED83DF7DF`。

详细日志与 APK 保护副本位于 `.local/android-test/reports/freeze-20261025/pc-issue-phase2-recurring-scope/`。核心验收矩阵新增 `REC-P0-127`，作为重建验收项记录 scope 选择和编辑器写入路径；历史 209 项 ID 仍未映射。

台账结构校验结果：212 行、211 个活动重建项，199 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED；P0/P1/P2 = 145/65/1。台账 SHA-256：`FFBD395A80D864BB8ED9578A561B6188DD3ED35B88048E0EC31F2AC7951909A1`；结构报告 SHA-256：`09879FB7A8671E7C5DE02C69BEBF30A8B10751EE27EB576B91C3239FC1DAFC0F`。结构校验器标注 `product_behavior_tested=false`，行为签核来自上述独立测试。

## 残余风险

- AVD 用例覆盖 API 35 直接编辑器范围选择和 SQLite 拆分；时间轴→编辑器的 key 路由由 Robolectric Compose 回归覆盖，没有在本轮 AVD 上端到端启动 PowerCal 主界面复测。实体设备、OEM 与实际用户数据场景由用户验收。
- “仅本次”目前只支持标题和时间；其余系列字段需要选择“本次及以后”或“整个系列”。
- checkpoint 恢复及重复回调已由隔离 SQLite/Robolectric 测试覆盖；实体设备进程被系统终止后的恢复仍待用户验收。
- 本 Issue 只关闭 P08 重复编辑范围缺陷；后续 Phase 3 键盘发布按钮、Phase 4 后台提醒与其余冻结缺口仍未解决。核心冻结结论保持 **HOLD**。
