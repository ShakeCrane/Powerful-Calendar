# PC-ISSUE-SEPARATION-AI-PUBLISH-FAILURE-RETRY

日期：2026-10-28

状态：**AI 提案草稿写入失败后的服务层重试和 Skin Studio Mock 场景 PASS；Host Compose 故障注入路径与 Android WebView/设备行为未验证。SEP-10 / S3 仍 NOT TESTED / OPEN。**

## 范围与结果

补充 SEP-10 的一个窄场景：用户已明确确认 AI 正式发布，但 Host 创建候选草稿时存储写入失败；失败项应保持可再次审核，重试不能复用已经分配的永久事件身份。同步在 Skin Studio 中增加仅预览的失败后显式重试场景。

没有修改 Android 生产代码、Core 事务、Host 授权策略或 Clear 皮肤。测试直接调用 `AiDraftService` 并使用隔离 SQLite；Studio 场景不调用 Provider、不接触真实日历数据。

## 修复/回归内容

- `AiChatDraftPipelineTest.failedReviewedPublishCanRetryWithoutReusingReservedEventIdentity` 使用 SQLite trigger 拒绝第一次 `drafts` 插入。断言该候选没有形成草稿、正式事件或 commit；Store 保留已分配的 identity 序号，保持“不复用永久事件身份”的现有合同。移除故障后重试成功，正式事件使用下一个序号，最终只有一项正式发布。
- Studio 增加“AI 发布失败后显式重试（Mock）”场景。首次模拟 Host 确认返回失败后保留提案卡；再次请求并确认后，仅在 Mock 中处理所选卡。E2E 断言四张卡在失败后仍存在，且 Mock 明确没有真实日历写入。
- 原有 CREATE / REPLACE / DELETE、STAGE、REJECT、SUPPLEMENT、可信确认和取消回归均保留。

## 验证

相关 Gradle 测试类：

- `AiChatDraftPipelineTest`：**2/2 PASS**，包括新增加的失败后重试用例。
- `WebAiProposalReviewIntegrationTest`：**7/7 PASS**。
- 两类合计：**9/9 PASS**，0 failures/errors/skips。日志 SHA-256：`FACC2D56AE4AFF553628B585550E8CBEAB6CD4E8B35C44B57D8BAB1C762D8B85`。
- 新服务级用例单独运行：**1/1 PASS**。日志 SHA-256：`765392273933A63720013E6E9BB8297E6FD195C428D790B2572579575D00E05A`。

完整离线门禁：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon --console=plain
```

结果：**627/627 JVM tests、87 suites、0 failures/errors/skips、88/88 Gradle tasks**；source boundary、Debug APK、AndroidTest APK 和 lint 均成功；lint **0 errors / 52 warnings**。完整日志 SHA-256：`936B6DE34A981FF731178F2502F2CB1179F4BD8BB76690FCC47312AB707C002C`。

Skin Studio / Clear 验证：

- Python：**70/70 PASS**；日志 SHA-256 `D75192BAFF85BB6C5F82473885411E91CD75AADADEEED28007C2E1D7025CC8B5`。
- 真实 Chrome E2E：源码往返及既有场景通过；新 AI 发布失败/显式重试 Mock 通过，声明 `realCalendarWrites=false`。日志 SHA-256 `C873D92A6C679156091772FA6BD8649D63B30AE378E8DFD53DCC26810278AEC8`。
- `node --check` 对 `v3-studio.js` 和 `e2e_v3_source.cjs` 均通过。
- Clear 包验证：8 files / 381125 bytes，SHA-256 `04C5F92D1C30D00B84E4CDB90FA572DAF8103DAA05CA8F5C2552C6A9D94600AC`；验证日志 SHA-256 `700E8A004AABADAFDB71274D460262F09FF859C486E414196FB9D01323EF513B`。

构建产物：Debug APK SHA-256 `357F2D698A78D7D9643AF5F7E1291F185445D3D9226CE5F4B5E7E80288AF8FAF`；AndroidTest APK `6B89A35252F0106B6A1B9DB45918C22303CA422391644EB0A77A329F413BAEB9`；lint XML `1CD7790BFDF2A843A56BC734EDD45F0C83906ACDDF006DDE29D48352CCC0575D`。

## 未验证和失败证据

- 尝试从 `PowerCalApp` 的 Compose Host 确认 UI 注入 draft 插入失败。该尝试未在测试时限内完成；最终 Bridge 快照仍显示 `chatBusy=true`，审核卡仍在，隔离 Store 计数仍为 drafts 0 / events 0 / identities 0 / commits 0，DefaultDispatcher workers 空闲。原因尚未定位，不能据此判定 Host 界面重试 PASS，也不能归因成已证实的产品缺陷。失败/诊断日志保留在 `.local/android-test/reports/separation-20261028/ai-publish-failure-retry-3f0301d/`，其中 `diagnostic-host-retry-persisted-state.log` SHA-256 `767C41A6B520B33F2198872A1B093A6032CDEAA4F3507FA9DDC77CA9BDADFDB6`。
- `adb devices -l` 提升权限只读查询返回空设备列表；本轮没有 Android WebView、模拟器或真机测试。没有调用真实 AI Provider。
- Chrome E2E 首次误在皮肤 iframe 查询 Studio 父页面的 Host Mock 对话框，已修正测试上下文并重跑通过；失败日志保留为 `studio-chrome-e2e.log`，SHA-256 `AE1BDE012A7CC6B5AC64AD71638D73C73DAB1D53D5CC88989CC291217C349E21`。

因此本 Issue 只签核服务层失败后重试和 Studio Mock 的窄范围。SEP-10、SEP-16 与 S3 继续 **NOT TESTED / OPEN**；还缺 Host Compose 故障恢复、实际 Clear WebView、真实 Provider、进程死亡恢复及 Native/Skin 全量行为回放。

## 可复现证据位置

所有日志、Gradle XML 与构建产物留在 `.local/android-test/reports/separation-20261028/ai-publish-failure-retry-3f0301d/`。本报告不把模拟器或 Mock 结果扩大解释为 Android WebView、真实 Provider 或完整功能等价证据。

## 交付记录

- 代码、服务级回归及 Studio Mock/E2E 提交：`fe13f437694b3f3270f4ca988cf882924be5c458`（`test(分离): 覆盖 AI 发布失败后的显式重试`），已推送到 `origin/codex/separation-skin-settings`。
- 本报告、Parity Matrix 与进度记录已纳入后续独立文档提交并推送；该提交 SHA 在最终交付记录中列出。
- 并发 Agent 对 `WebSkinManagementInstrumentedTest.kt` 的未提交改动未暂存、未提交。
- 当前代码与文档均仅覆盖失败后重试这一窄切片；SEP-10 / SEP-16 的完整 parity 与 S3 不得据此签核。
