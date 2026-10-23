# PC-ISSUE-116 — 重复提交核心幂等签核

日期：2026-10-24

候选：`REC-P1-057`（重建 P1；历史 209 项映射仍为 `UNMAPPED`）

状态：**窄化核心提交幂等契约 PASS；API 35/provider 批准入口未签核；整体冻结 HOLD**

## 签核范围

签核同一草稿重复调用 `PowerStore.commit` 的核心存储语义：第二次调用标记为 duplicate，返回首次提交的 revision 与 commitId，并且不新增提交历史。范围仅包含 Robolectric API 34 下的 PowerStore/SQLite；API 35、provider、批准入口/card 行为和设备行为未签核。没有改应用或测试源码。

## 验证结果

现有 `PowerStoreTest.repeatedCommitReturnsTheOriginalOutcome` 验证两次普通提交的结果。第二次结果 `duplicate=true`，revision 和 commitId 与首次相同，提交历史仍只有一条。实现先在事务中查找该草稿的既有 commit 记录，命中后直接返回原结果。

- 定向 Robolectric API 34：**1/1 PASS**。日志 SHA-256：`5369AB020545DA46FABE7686C05CBD869C3F0BDB69E0C9DCC48D3A7120B69300`；JUnit XML SHA-256：`E12E29E6EA5D693D250350E4C08CA46A97814407F5F80DB2FFACCD8BE603A5ED`。
- 完整命令：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`。**BUILD SUCCESSFUL**；460/460 测试、57 个套件，0 failures/errors/skips；lint 0 errors / 58 warnings。
- 完整日志 SHA-256：`14658BF074D124916E357927A5F6367444927CA24D4CF29A533DF81E606F2334`；完整 `PowerStoreTest` XML SHA-256：`AE82D79BB7BCF2408F9DB9242D5C165AAA9558825D5F34BD013618882CFAB378`；lint XML SHA-256：`E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`；Debug APK SHA-256：`138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 日志、JUnit XML、lint XML 和 APK 保存在 `.local/android-test/reports/freeze-20261024/pc-issue-116-*`。测试源码基线：`466cf1eeded40a4a0e150ad4bde5c06da79a18bb`。

## 台账与结论

- `REC-P1-057` 对“同一草稿重复调用普通 PowerStore.commit 时返回原提交结果且不重复写历史”的窄化核心契约签为 **PASS**。原候选中的 API 35/provider 批准入口及设备行为没有纳入本次 PASS。
- 候选统计：**145 PASS / 0 FAIL / 9 BLOCKED / 48 NOT TESTED / 7 SPEC UNRESOLVED**；P0 **119/0/1/1/5**，P1 **26/0/8/47/1**。历史 209 项仍无映射，整体冻结保持 **HOLD**。
- 最新结构校验 artifact：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-116.json`，SHA-256 `8293459B130547FDD612B8249859D0E09B5355FDE9CA47D3E11CB83912F47DB9`；CSV SHA-256 `7D76245F0CC198183152E0B404FA7B2B947258209AACA4C4130C0CC782CA865A`。结构校验仅验证账本结构、状态计数和证据引用，不签核产品行为或历史映射（`product_behavior_tested=false`）。
