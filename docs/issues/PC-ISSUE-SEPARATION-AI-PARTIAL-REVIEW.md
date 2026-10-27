# PC-ISSUE-SEPARATION-AI-PARTIAL-REVIEW

日期：2026-10-27

状态：**AI 提案部分处理的 Host 集成切片通过；SEP-10 与 S3 整体仍未签核。**

## 范围与需求来源

核心合同要求 AI 提案按独立事项展示四种决定，并允许独立事项部分成功；旧卡片不能授权已经变化的内容（`docs/CORE_CONTRACT_FREEZE_2026-10-21.md` 第 9 条及 Q8/Q18/Q33）。S3 开工范围要求批量同意、写入草稿、拒绝、补充细节以及批次快照由 Host 驱动；皮肤提交的选择只是请求。

本 Issue 检查用户从两项候选中只暂存一项后，另一项能否继续审核，以及旧审核 ID 的重放是否会误操作剩余候选。现有 Host/Bridge 功能已实现；缺口是没有穿过实际 `PowerCalApp`、Bridge 和 Store 的多提案部分处理回归。**未发现生产代码缺陷，本次只增加测试。**

## 回归行为

新增 `WebAiProposalReviewIntegrationTest.partialStageKeepsUnselectedProposalAndRejectsStaleReviewActions`。测试通过本机回环 HTTP stub 返回两项独立 MEMO CREATE，驱动实际 Host 并断言：

- 审核卡片展示期间，Store 草稿、正式事件、身份及提交计数不变。
- 用户选择仅暂存第一项后，恰有一张待处理草稿；没有正式事件，身份数为 1，提交数为 0。
- 第二项仍留在审核投影中，顺序和内容正确；部分处理后审核 ID 已更新。
- 使用旧审核 ID 和旧索引重放拒绝操作，剩余候选保持不变，Store 计数不变。
- 使用当前审核 ID 拒绝最后一项，仅移除该提案；已暂存草稿仍保持待处理且内容不变。

不调用真实 AI Provider、外部网络或付费模型。Robolectric 使用显式 Main Looper 推进来执行 `Dispatchers.Main` 完成回调；这是本测试 harness 的调度处理。测试也会等待聊天忙碌锁释放后才提交决定，符合 Host 的忙碌期间拒绝动作规则。

## 测试证据

定向 Host/Compose 测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests 'dev.powerfulcalendar.ui.v2.WebAiProposalReviewIntegrationTest.partialStageKeepsUnselectedProposalAndRejectsStaleReviewActions'
```

结果：新增测试 **1/1 PASS**。日志 SHA-256：`23D7066D0DCDA99399DBAC6A410DFDC32376E534E53A46715912D3D1C26952A8`。

相关审核、投影测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests 'dev.powerfulcalendar.ui.v2.WebAiProposalReviewIntegrationTest' --tests 'dev.powerfulcalendar.ui.v2.WebAiProposalReviewTest' --tests 'dev.powerfulcalendar.ui.v2.WebSkinHostProjectionTest'
```

结果：**20/20 PASS**，0 失败、错误或跳过。日志 SHA-256：`AE9962446CADE311F16DE117DCB82DDDC7BFEE90AF3185966DF5FCFDFE114A72`。

完整离线 Android 门禁：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果：**590/590 JVM 测试、83 个测试套件、0 失败/错误/跳过，88/88 Gradle 任务完成，BUILD SUCCESSFUL**。Source boundary、Debug APK、AndroidTest APK 和 lint 均通过；lint 为 0 errors / 52 warnings。完整日志 SHA-256：`0652792360766D1F032695FE399D862FD36E02EF490315096F52C310B9A24A66`。

Skin Studio / Skin 侧检查：

- `python -m unittest discover -s tools/skin-studio/tests -v`：**42/42 PASS**。日志 SHA-256：`283C19241A5483B39953CE075798E4E0CFCD2015648918D48B52AAE11666318E`。
- `node tools/skin-studio/tests/e2e_v3_source.cjs`：源码往返、附件 Host-action Mock、AI review selectable groups 与 supplement/stage/reject Mock 通过；确认 Mock 未写真实日历。日志 SHA-256：`770F64A46E489987B810EC0314F18A0F35FC6451C39E62C48774D7A732369510`。
- `python tools/skin-studio/build_default_clear.py --verify`：8 个文件、376,661 字节通过；Clear bundle SHA-256 `0e17aabfe216e8ecb0775dafb8ea66857e25687b09e1167b7c68e1d9ffd81623`。

完整证据目录：`.local/android-test/reports/separation-20261027/ai-partial-review/`。Debug / AndroidTest APK SHA-256：`B3115D80E25CBA24B1A6177D1EED5506DB613D467123E1BCE99268B31296780D` / `219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6`。

## 未覆盖范围

- Studio Chrome E2E 是 Host Mock；它不证明设备上 Android WebView 的 Host 执行、跨进程恢复或真实授权交互。
- 本次没有测试 AI REPLACE/DELETE/转换组合、依赖操作原子组、目标版本冲突时的部分结果、进程死亡后的审核恢复或真实 Provider 行为。
- 没有执行 Android 模拟器或实体设备验证。SEP-10 完整 Native/Skin parity 仍为 **NOT TESTED**，S3 仍在进行。
- 本轮没有改生产代码，也没有更改冻结 Core 语义。

## Git

测试提交：`d03778d18156331b5d4152c80bc0cee27591409f`（`test(skin): 验证 AI 提案部分审核与过期操作拒绝`），已推送至 `origin/feat/skin-first-separation`。本报告、矩阵和进度记录随后作为单独文档提交。
