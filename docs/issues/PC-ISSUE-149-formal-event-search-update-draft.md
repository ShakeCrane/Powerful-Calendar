# PC-ISSUE-149 — 正式事件搜索生成匹配的 Update 草稿

日期：2026-10-24
状态：`REC-P1-039` 核心搜索/存储路径窄化签核 **PASS**；Compose 结果点击未签核。

## 验收来源与处理

`PRODUCT_BASELINE_G1-G11.md` 要求搜索既有正式事件时可以打开匹配的 Update 草稿，并保持 `queueOrder` 稳定。原正式事件匹配逻辑内联在 `PowerCalApp`，缺少直接回归；选中结果后的持久化路径也没有一项专门测试同时断言目标版本和既有队列次序。

将正式事件纯搜索抽为 `FormalEventSearch.filter`，保留原有标题/序号匹配、输入顺序及最多 8 条的行为。新增 SQLite 回归：搜索命中目标正式记录后，通过 `PowerStore.proposeReplace` 创建其 REPLACE/PENDING 草稿，验证草稿携带当前完整快照及 `baseRevision`、排在既有卡片之后，并在数据库重开后保持目标身份及队列次序。现有逻辑没有发现功能缺陷；本项补足了核心路径证据。

代码/测试提交 `0db7b534c7683059fea1f7f38c587e6d30179a73` 已推送。

## 测试证据

定向组 **5/5 PASS**：`FormalEventSearchTest` 2 项、正式事件结果到 REPLACE 草稿的 SQLite 测试 1 项、`DraftQueueSearchTest` 2 项。修正后日志 `.local/android-test/reports/freeze-20261024/pc-issue-149-formal-search-targeted-corrected.log`，SHA-256 `C6AFA52BB816008BDD3A0DBF7AEDC7B2705A4D0EC33494A2B401FFD6B570FE7B`。JUnit XML：`FormalEventSearchTest` SHA-256 `6DD4E0780C1293CE9E84A4B4D775C9B10A09CDA6849695FC9F71D36B8901D625`；`PowerStoreTest` SHA-256 `6438AC45E52313D89E1E0367F5548EA500C2442C891E31FBAC7517F7FA19286B`。

首轮定向测试有一条**夹具预期错误**：用序号查询字符串 `3` 预期命中事件 `12`。实际匹配规则正确地只命中事件序号 `3`；将测试改为查询 `1` 后验证序号 `12/19/31` 的稳定匹配顺序。首轮失败日志保留于 `.local/android-test/reports/freeze-20261024/pc-issue-149-formal-search-targeted.log`，SHA-256 `D3391DCEC771A7645103CEED6AE039107CEFD2ADBF18434AD5FD991475980D68`。该失败是测试夹具断言错误，不是产品行为失败。

完整离线回归命令：

```text
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

结果 **488/488 JVM tests、64 suites，0 failures/errors/skips**；源码边界检查、Debug APK、AndroidTest APK 组装通过；lint **0 errors / 58 warnings**。日志 SHA-256 `7E130576D974E04BC04729F870B254C044E2E46900E6E81E33561FE91D7D76F9`；Debug APK SHA-256 `6D8C68816D0085949D54A893D50022ACA040B8CBF54087FB957863BCF90AA71B`；AndroidTest APK SHA-256 `D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A`；lint XML SHA-256 `44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F`。

## 签核边界

`REC-P1-039` 本次只签正式事件纯匹配、基于所选记录创建/取得 Update 草稿、版本快照及追加顺序的核心行为。没有执行 Compose 搜索结果点击、键盘、真实 AVD 或实体设备交互；因此不代表完整搜索 UX 已通过。全局冻结仍为 **HOLD**。

更新后的结构校验只验证台账：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r15-pc-issue-149.json`，SHA-256 `45494C0672830234BB1A38739FFBB6406CBABA8465E5B5DE49941E458AA5EECF`；ledger SHA-256 `AD117D484ACE7AFBA1565397C1D01F73D3798F49A1DB0868CBF96CD0D9C82319`。当前 184 PASS / 0 FAIL / 8 BLOCKED / 11 NOT TESTED / 6 SPEC UNRESOLVED；`product_behavior_tested=false`。
