# PC-ISSUE-021 — 不完整 Activity 不得发布且草稿可恢复

日期：2026-10-23

状态：**REC-P0-005 的 SQLite/Robolectric 候选回归通过；核心冻结仍 HOLD**

## 验收范围

缺少结束时刻的 Activity 不能成为正式事件。拒绝提交后，内容应留在可恢复的草稿队列中；进程/数据库重新打开后仍可补全，并在修复字段后正常提交。

## 最小测试变更

在 `PowerStoreTest` 新增 `incompleteActivityCommitStaysUnpublishedAndCanBeRepairedAfterRestart`。测试先创建缺少 `endText` 的 Activity 草稿并尝试提交，断言结果为 `Invalid`、正式事件不存在、草稿状态为 `INVALID` 且仍在队列中。随后关闭并重开同一隔离 SQLite 数据库，确认相同草稿和 eventSeq 保留；补上结束时刻后草稿回到 `PENDING` 并成功发布为 `ACTIVITY`。

只增加测试，没有修改生产逻辑、UI、设计资产或应用用户数据。该场景直接覆盖候选项 `REC-P0-005` 的存储、拒绝、重启恢复与修复后提交边界。

## 回归证据

- 提交后定向运行：`.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.PowerStoreTest.incompleteActivityCommitStaysUnpublishedAndCanBeRepairedAfterRestart' --offline --rerun-tasks --console=plain`，**1/1 PASS**。日志 `.local/android-test/reports/freeze-20261023/pc-issue-021-incomplete-activity-draft-targeted-postcommit-20261023.log`，SHA-256 `2010C351D82318FC0E3D1154BE91AD64FECD4CC8B1025117870DC8C5D2D2A673`；JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-021-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`，SHA-256 `56D2E5EA5DEE655C4F23A3054AD3FA38AFD48CABF8674CFCDE61F3A3351459DA`。运行基线：`02e32d21c74babb284fc41f29f2ec02fca08037e`。
- 提交前完整 Gradle 回归：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`，**385/385 PASS**、55 suites、0 failures/errors/skips；skin source boundary、Debug assemble 通过；Lint 0 errors、55 warnings。完整日志 `.local/android-test/reports/freeze-20261023/pc-issue-021-incomplete-activity-draft-full-gradle-20261023.log`，SHA-256 `830C3E74943993C467B5133208382E4FCED0A90FF9D699FF6D6D682BE550B5E3`。全量 JUnit XML 归档在 `.local/android-test/reports/freeze-20261023/pc-issue-021-full-junit/`；`PowerStoreTest` 为 38/38、包含本测试，XML SHA-256 `5046E297A5B28A13C59507A30BC012799F5AA1062A177A3C54791FE35F3629B0`。
- 完整回归运行时工作树中的测试内容随后作为单文件提交 `02e32d21c74babb284fc41f29f2ec02fca08037e` 推送；提交后定向复跑再次 PASS。APK SHA-256：`EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`。

## 签核与边界

`REC-P0-005` 签候选 PASS。它是重建候选，不映射到缺失的原始 209 项 ID；测试证明的是 PowerStore/SQLite 的草稿保留与修复路径，不单独构成 Android 设备或其他产品界面的签核。核心冻结继续 HOLD。

- 测试提交：`02e32d21c74babb284fc41f29f2ec02fca08037e`（`test(冻结): 验证不完整活动草稿可恢复发布`），已推送至 `origin/main`。
- 本 Issue 报告与候选台账更新将单独使用中文 Conventional Commit 提交并推送。
