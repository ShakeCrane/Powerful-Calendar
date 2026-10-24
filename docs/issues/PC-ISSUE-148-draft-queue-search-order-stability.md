# PC-ISSUE-148 — 草稿搜索保持待处理队列顺序

日期：2026-10-24
状态：`REC-P0-040` 按纯过滤与存储顺序边界窄化签核 **PASS**；API/Compose 搜索交互未签核。

## 发现与处理

产品基线规定搜索/过滤不能重排 `queueOrder`。搜索原来在 `DraftDeck` 的 composable 内直接调用 `filter`，按 Kotlin 列表顺序运行是稳定的，但缺少独立回归，无法单独锁定该不变量。

将这段纯匹配规则抽为 `DraftQueueSearch.filter` 并继续由现有搜索结果使用。它按标题（不区分大小写）、事件序号或格式化序号筛选输入列表，不排序、不修改卡片。没有发现现存排序缺陷，也没有改动布局或样式。

代码/测试提交 `3a0fb7f01bccbf33c19c747226ef65316a5d1f70` 已推送。

## 测试证据

定向 **5/5 PASS**：新增两项纯过滤测试验证标题筛选和序号筛选保持输入次序及 `queueOrder`；另重跑三个 SQLite 队列测试，覆盖独立队列序号、日期改动后重开顺序、重开后选中最旧队列项。日志 `.local/android-test/reports/freeze-20261024/pc-issue-148-queue-search-targeted.log`，SHA-256 `7CF13719EE900F581521D50756018CE3B744BC2D6072229725F77C191FCE5D8E`；新增测试 XML SHA-256 `5241703919C3C157C2F4F49EE9E871BD9EB200E586C4FC187F424657A61D35D3`。

完整离线回归命令：

```text
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

结果 **485/485 JVM tests、63 suites，0 failures/errors/skips**；源码边界检查、Debug APK、AndroidTest APK 组装通过；lint **0 errors / 58 warnings**。日志 SHA-256 `4B83C3FF408732478CEF2244DEBE7B995676C3DB4CF7752BB12FFCBA9AD164FB`；`PowerStoreTest` XML SHA-256 `5332B7C8BE6402183CD0E2ECEEDABF4D37B4F67D0288E64A6C4653950178B0F6`；Debug APK SHA-256 `5F82337249AEB8355F766A3CC00F93142C93AF7FD01EC84F0A7206FC22A17B31`；AndroidTest APK SHA-256 `D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A`。

## 签核边界

`REC-P0-040` 本次只签核队列搜索过滤保持输入顺序/`queueOrder` 的核心规则，并依赖现有 `PowerStore.pendingDrafts()` oldest-first 查询。没有运行 AVD、实体设备或 Compose 交互；正式事件搜索结果、搜索入口/IME 行为和完整编辑器交互不在本次 PASS 范围。核心冻结其余未测项与 HOLD 状态不变。

更新后的结构校验仅验证台账：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r14-pc-issue-148.json`，SHA-256 `F0FDC211A16A4FD7072FB650ADB8FCC2B392ABC20E9E9E71A219ADA07C7D83E6`；ledger SHA-256 `BCAA0F71C6941F4FB3F45BEB2AF954041C1F15F70C4F95A7D295EAB14FA4211D`。当前 183 PASS / 0 FAIL / 8 BLOCKED / 12 NOT TESTED / 6 SPEC UNRESOLVED；`product_behavior_tested=false`。
