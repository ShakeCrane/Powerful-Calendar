# PC-ISSUE-019 — 日期型 Deadline 精度不得补造时刻

日期：2026-10-23

状态：**REC-P0-004 的 PowerStore/Robolectric 候选回归通过；核心冻结仍 HOLD**

## 验收范围

日期型 Deadline 只保存日期语义。提交 `2026-10-24` 后必须保留 `deadlineDate` 与 `DATE` 精度，`deadlineMillis` 为空；不能把日期扩成午夜或 23:59 等人为时刻。

## 实现与回归

没有发现或修改生产代码缺陷。本 Issue 将现有 SQLite/Robolectric 合同测试映射到重建候选项 `REC-P0-004`，并在当前提交后复跑确认。测试使用独立临时数据库，不访问应用用户数据。

测试 `PowerStoreTest.dateOnlyDeadlineCommitsWithoutInventingATime` 构造 `TemporalKind.DEADLINE` 和 `endText="2026-10-24"`，确认提交成功，再读取持久化记录并断言：

- `deadlineDate == "2026-10-24"`
- `deadlineMillis == null`
- `precision == DATE`
- `kind == DEADLINE`

因此 `REC-P0-004` 签为候选 PASS。此签核仅覆盖 Robolectric API 34 下的 PowerStore/SQLite 持久化语义，不代表原始历史验收 ID，也不扩展到其他日期展示或平台行为。

## 测试证据

- 提交后定向命令：`.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.PowerStoreTest.dateOnlyDeadlineCommitsWithoutInventingATime' --offline --rerun-tasks --console=plain`，结果 **1/1 PASS**，BUILD SUCCESSFUL。运行于文档提交 `74cc48fb0e05ad08da2aaf457e31d93e390da1cd`；产品与测试代码基线仍为 `d60809b4133a45c0551019f1edabec1195d34cf3`。日志 `.local/android-test/reports/freeze-20261023/pc-issue-019-date-only-deadline-targeted-postcommit-20261023.log`，SHA-256 `8CF359ED5B02675EF95C78DC74B5A7A12A453F7BB676F4E7BC60AAAF362972E2`；JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-019-date-only-deadline-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`，SHA-256 `29C2B5C35ED08F5D72D8889D1BC1D84732DABC03FF3F2CE5B6B1056293973AD1`。
- 修复 Issue020 后完整回归包含此测试：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`，**384/384 PASS**，55 suites，0 failures/errors/skips；Lint 0 errors、55 warnings。日志 `.local/android-test/reports/freeze-20261023/pc-issue-020-reminder-dispatch-full-gradle-20261023.log`，SHA-256 `A4A67F15FCBA59BBBBE543943337501DAFD2ECA7A37ECBF3DE3A41664F155783`。归档的 `PowerStoreTest` XML 有 37/37 通过，包含本方法；SHA-256 `D3EFE1BDCEA6B0825D49A7ED0B9E4536FE8A3D21BCAEE00B38E133EF9DB58D71`。
- 完整回归构建 APK SHA-256：`EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`。
- 此前一次完整回归在同一 `384` 项中因提醒测试的并发 SQLite 轮询失败，未计为通过；该失败日志 SHA-256 `551DFD59864DD836E4E91D612DEA2FBB4ED6FD452223A437DB305478229F9779`，失败类 XML SHA-256 `754016642CA8490866BFBC9147C779D4866F6FC4134AFAA9AFEBB5C65B6947BD`。根因与修复详见 [`PC-ISSUE-020`](PC-ISSUE-020-reminder-receiver-test-race.md)。日期 Deadline 候选只在修复后的全量回归通过后签核。

## 变更与冻结边界

- 代码/测试提交：`d60809b4133a45c0551019f1edabec1195d34cf3`。本 Issue 没有生产代码、测试代码、界面、设计资产或业务数据变更。
- 本报告及冻结台账更新单独使用中文 Conventional Commit 提交并推送。
- 真实 Android 设备验证不在此候选的当前模型/持久化断言中；整体核心冻结仍受历史 209 ID 缺失、其余未测试候选及平台/AI 门槛阻塞，维持 HOLD。
