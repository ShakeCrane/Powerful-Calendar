# PC-ISSUE-SEPARATION-DRAFT-DECK-DISCARD-UNDO

- **状态：** 本 Issue 覆盖的草稿丢弃/撤销预览及持久化回放范围 PASS；SEP-08 完整 parity 仍为 NOT TESTED。
- **日期：** 2026-10-28
- **分支：** codex/separation-skin-settings
- **实现提交：** ccf68c807a36e66393751c74c2b57a36fd9154ed（Studio Mock 修复与配对回归）
- **测试加固提交：** 43f787bf8203b57dcd58523ca54261d3ae9cdfd1（两边关闭并重开 SQLite 文件）
- **报告/矩阵提交：** 220cc6b462e4bfa90b266dec005689484bfd1b67

## 分支并发情况

Goal 文件指定的 feat/skin-first-separation 当前由另一个工作树 .../worktrees/skin-first-separation 检出，且存在未提交的 docs/agent-handoff/PROGRESS.md 与 Clear project.json、字体许可、HTML、CSS 修改。为避免将该工作树的并发内容带入或使其基线漂移，本 Issue 提交推送到现有隔离分支 codex/separation-skin-settings；未更新 feature 分支。两者目前从 172288a 分叉，待并发工作所有者完成协调后再安全整合。

## 根因与修复

Clear 编辑器在丢弃草稿后保留当前卡组，并允许用户撤销最后一次丢弃。Studio 的 Host Mock 没有模拟这个状态机：deck.discard 直接把编辑器关闭，deck.undo 永远返回 missing；deck.select 也只回报成功、不切换表单。Mock 的 deck.flush 即使表单干净也递增版本，deck.patch 则没有核对版本。这使 Studio 预览与 Host/DeckController 的实际操作语义不一致。

在 tools/skin-studio/v3-studio.js 中，Mock 现在按草稿 ID 和版本验证 patch/discard；仅 dirty 表单 flush 时递增版本；丢弃前先模拟暂存，保存一次撤销快照，保留卡组并更新队列/current card；撤销恢复此前的表单、卡片和版本；选择操作会切换到对应卡片表单。它仍是明确的非写入 Mock，不访问真实日历数据。

首次新增的 Chrome 回归有一次测试时序失败：字段 change 事件发出后立即点击丢弃，编辑命令仍处于 UI busy 状态，点击被界面按既有忙碌保护忽略。测试现在等待 Mock 投影进入 dirty 状态后再操作。随后在修复前稳定复现真实 Mock 缺口，失败为 discard did not retain the undo action in Studio preview。修复后回归点击 Clear 预览中的真实双击丢弃控件，确认撤销入口出现，点击撤销后确认草稿编辑器仍打开且表单内容恢复。

## Native/Skin 持久化回放

新增测试 ID：WebSkinEditBridgeTest.persistedDraftDeckDiscardUndoAndPublishMatchNativeAcrossReopen。

测试在两个隔离 SQLite 数据库中创建相同的两张人工 MEMO 草稿，并逐步对比 Native DeckController 与 Skin WebSkinEditService：

1. 打开第一张卡，分别在 Native 与 Skin 编辑标题和备注。
2. 切换到第二张卡，验证两条路径都会先暂存第一张卡的最新输入，并选中相同队列项。
3. 编辑并显式暂存第二张卡，核对草稿字段和版本。
4. 关闭两边的 PowerStore，用相同数据库文件重新打开，再新建 Controller/Host service；恢复出的卡片、表单、版本和选中项一致。此处验证的是 SQLite 关闭/重开，不是 Android 进程死亡。
5. 使用旧版本执行 Skin patch，得到 stale，且状态无变化。
6. 丢弃第二张卡，验证队列切到第一张卡、lastDiscardId 相同、数据库队列一致；随后撤销，第二张卡回到原队列位置并重新选中，内容完整。
7. Native 通过 DeckController.commit() 发布；Skin deck.commit 必须先返回 confirmation_required，Host 尚未批准前正式事件不存在。批准后两边生成相同正式记录；重复发送同一 Skin 命令返回原回执，没有第二次提交。

逐阶段比较 Deck 可见状态、队列草稿、正式记录和计数，包括 draft/pending/formal event/identity/sequence/queue/commit。仅将系统自动生成的 createdAtMillis 和 updatedAtMillis 审计时间归一化，其余业务字段、ID、版本和顺序均保留比较。

## 测试证据

| 检查 | 结果 |
| --- | --- |
| 定向 SQLite Native/Skin 回放 | PASS；测试方法见上文 |
| 完整 Gradle 离线门禁 | PASS；609/609 JVM 测试、85 suites、0 failures/errors/skips、88/88 tasks；source boundary、Debug APK、AndroidTest APK、lint 均完成 |
| lint | PASS；0 errors、52 warnings |
| Skin Studio Python | PASS；51/51 |
| Studio Chrome E2E | PASS；含真实预览双击丢弃/撤销、恢复内容、四类新建选择器及既有设置、重复事件、AI Mock 检查 |
| JavaScript 语法 | PASS；node --check tools/skin-studio/v3-studio.js 与 node --check tools/skin-studio/tests/e2e_v3_source.cjs |
| Clear canonical bundle | PASS；8 files、379111 bytes；SHA-256 DBE04D79DA759ADAC4A30054427956163BE963EF317DE0BF75D289C53AEF340A |
| Android WebView / AVD / 实体设备 | NOT TESTED；本轮未启动 Android 设备，也未运行设备级 WebView 操作 |

完整门禁命令：

    .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain

Studio 命令：

    node --check tools/skin-studio/v3-studio.js
    node --check tools/skin-studio/tests/e2e_v3_source.cjs
    node tools/skin-studio/tests/e2e_v3_source.cjs
    python -m unittest discover -s tools/skin-studio/tests -v
    python tools/skin-studio/build_default_clear.py --verify

环境为 Windows PowerShell、JDK 20、仓库 Android SDK E:\android-sdk。Gradle 离线运行；JVM 回放使用 Robolectric 与隔离 SQLite。Studio Chrome E2E 访问本机预览页面及非写入 Mock。普通沙箱禁止回环连接，E2E 在允许本机 loopback 的授权运行中执行。

## 证据文件与哈希

详细运行日志在忽略目录，不提交原始日志：

- 完整 Gradle 门禁：E:\Project\Powerful-Calendar\.local\android-test\reports\separation-draft-deck-undo-20261028\full-gradle-gate-final.log
  - SHA-256：529A8EA83221D83399BF23F9046548A8EA71F61F94A5C6A46250F0B13E6BA67D
- Studio Chrome E2E：...\studio-e2e-final.log
  - SHA-256：100668BF73AD68215C801D74985BF5F1E9D440CFC4E49BF4B5CEC3376A111EDB
- Skin Studio Python：...\studio-python.log
  - SHA-256：61FE40D63E9DDBC20EFA158E7226812F5B450E58A5233A6FD98CCE9C8D5415AC
- Debug APK：app/build/outputs/apk/debug/app-debug.apk
  - SHA-256：869AD65D0E00C1C37CE39042E2E946115C02D498B993B7821777DF3B8839845C
- AndroidTest APK：app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  - SHA-256：FD52D9A141D801274FA4E58E1F7B20F113525CE3A8117A92D76DA41A88619BF9

完整 E2E 最终重跑通过。最终重跑前曾有一次旧源码往返检查观察到 capability 集合 host.read,host.action，与该断言预期的 host.read 不一致；失败发生在本次新增场景之前，随后有界重跑通过。这个时序问题未计为 PASS，也没有为本 Issue 顺带改动 capability 编辑功能。

## 影响与剩余风险

本次修改只涉及 Skin Studio 的预览 Mock 和回归测试，没有更改 Core、Host Bridge、数据库格式、正式授权规则或 Clear 皮肤业务源码。当前证据证明单卡 discard/undo Mock、两卡队列的 Native/Skin SQLite 回放及 Studio 预览行为一致。

SEP-08 整行继续 NOT TESTED：本轮不覆盖 AI 卡批次锁/冲突与恢复、删除 tombstone 恢复、多个 discard 的撤销策略、Android WebView 实际交互、低内存或 OS 进程死亡、实体/OEM 行为，也没有完成草稿卡组所有页面结构的 Studio 任意重构能力。不要据此宣布 S2、SEP-08 或分离目标通过。
