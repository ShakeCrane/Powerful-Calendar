# PC-ISSUE-SEPARATION-AI-REPLACE-DELETE

日期：2026-10-27

状态：**Clear Host 确认与一条 Native/Clear 正式 REPLACE+DELETE 配对均已通过自动化；SEP-10 和 S3 整体仍为 PARTIAL / HOLD。**

## 需求与调查结论

S3 要求 AI 提案审核卡由 Host 驱动；Skin 的操作只是请求，正式写入必须经过可信确认、目标版本复核和现有核心事务。
此前 CREATE、部分审核和多轮补充已有测试，但没有覆盖正式事件 REPLACE/DELETE 穿过实际 Skin → Web Host Bridge → `PowerCalApp` → `PowerStore` 的闭环。

没有复现生产业务缺陷；本 Issue 补齐行为证据，不改业务实现。

## 新增回归

`WebAiProposalReviewIntegrationTest` 增加两个真实 `PowerCalApp` / Host Bridge + loopback HTTP stub 场景：

1. 返回一项 REPLACE 和一项 DELETE。卡片展示及 Host 确认弹窗打开时，两个正式事件、草稿、identity 和 commit 计数保持不变。可信 Compose Host 确认后，REPLACE 保留 eventSeq 且 revision 增 1，DELETE 仅删除指定事件；总 commit 增 2，identity 数不变。
2. AI 提案审核后，由用户先提交目标事件的新版本，再确认旧 AI 提案。Host 拒绝过期目标的 AI 写入，用户版本保持权威；没有额外 AI commit，旧提案仍可供用户查看。

新增 `PowerCalAiProposalReviewWebViewInstrumentedTest`，在 API 35 `emulator-5564` 启动应用实际 WebView 并加载打包的 Clear skin。测试通过本地 stub 返回 REPLACE/DELETE，验证 Clear 卡片中的目标 eventSeq/revision、Host 弹出的完整批量确认，以及确认前零写入；确认后检查相同 REPLACE/DELETE 结果和身份/提交计数。

AndroidTest fixture 使用随机命名空间隔离 SQLite、AI 设置、Onboarding、皮肤选择、聊天文件与缓存；finally 后数据库、缓存、shared preferences 均无该命名空间残留。provider authority 并发冲突由独立 Issue `PC-ISSUE-SEPARATION-TEST-PROVIDER-ISOLATION.md` 修复。

所有 AI 响应均来自设备本机 loopback stub，没有请求真实 Provider、外网或付费模型。

## 测试证据

相关 JVM suites：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests dev.powerfulcalendar.ui.v2.WebAiProposalReviewIntegrationTest --tests dev.powerfulcalendar.ui.v2.WebAiProposalReviewTest --tests dev.powerfulcalendar.ui.v2.WebSkinHostProjectionTest --tests dev.powerfulcalendar.ai.AiTaskPipelineTest
```

结果 **24/24 PASS，4 suites，0 failures/errors/skips**。日志 SHA-256：`CC94484EAC4A89417A28AAE843D0940EA6725CB20875500C3BA65667B59B557E`。其中 integration 7/7、review model 5/5、projection 11/11、task pipeline 1/1。

完整 Android 门禁：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果 **593/593 JVM tests、83 suites、0 failures/errors/skips、88/88 tasks**；source boundary、Debug/AndroidTest APK 与 lint 通过，lint 0 errors / 52 warnings。日志 SHA-256：`067DCE0619BB8F6B05D016D226EF9E42230006662A8900EC58FE6FB917B8232F`。

API 35 `emulator-5564`：

- Clear WebView REPLACE/DELETE Host 确认仪器测试连续 **3/3 PASS**。日志 SHA-256：`1FFD9BDA3613B36181708EC7BE3DA87FFF550862EE72CAE554E9F6493EDC2D1C`。
- 动态 SAF provider 兼容回归 **1/1 PASS**。日志 SHA-256：`C4F6CD48EAAC398D75183C5C5BD188E1001AAE764C3A04FAA191D5482485EA61`。
- 本次实际安装 QA app APK SHA-256：`7D945F0145628C99F6C7EA9895ADCDD0D80D88061AE5E840F5C3594D93731261`；AndroidTest APK：`F200E847BDB2872542139D55435BB95C9E45DB461CFE1C220E36E3187D512821`。APK 副本与日志存放于 `.local/android-test/reports/separation-20261027/ai-modify-delete/`。

首次重跑时，测试在 Host 的 WebMessagePort 初始状态尚未到达前点击了 AI tab，超时停留在时间轴页。测试现在等待 `#dateSubtitle` 出现 Host 投影值后才交互；随后连续三次通过。失败日志保留于 `webview-androidtest-final2.log`，SHA-256 `1CBC053336D61CC1EF8A584E6418DA1F26B7FE090C635B3701C5B047EB365D21`。这是测试同步竞态，未发现产品路由缺陷。

## Skin Studio 同轨证据

- `python -m unittest discover -s tools/skin-studio/tests -v`：**42/42 PASS**。日志 SHA-256：`F9880DAA96E9CB90899F401CBA3128A5CD6B9D4D372D28EBE9E895D77F239450`。
- `node --check tools/skin-studio/default-clear/source/web/app.js`：PASS。
- `node tools/skin-studio/tests/e2e_v3_source.cjs`：source round-trip、附件 Host-action Mock、AI review/confirmation non-writing Mock PASS；Mock 没有写入日历。日志 SHA-256：`770F64A46E489987B810EC0314F18A0F35FC6451C39E62C48774D7A732369510`。
- `python tools/skin-studio/build_default_clear.py --verify`：Clear 8 files / 376661 bytes PASS，bundle SHA-256 `0e17aabfe216e8ecb0775dafb8ea66857e25687b09e1167b7c68e1d9ffd81623`。

Studio Mock 只证明编辑器源码路径和 mock 行为；设备 WebView 证据来自独立 AndroidTest，不能将 Mock 当作 Host 等价验证。

## 未覆盖范围与状态

- 未测试真实 AI Provider、实体/OEM 设备、进程死亡后的审核恢复或完整 Native/Skin 双版本回放。
- 这条 WebView 回归覆盖的是选择候选、Host 批量确认、REPLACE 和 DELETE；它不完成 SEP-10 所列所有四种决策、附件错误/取消/重试、依赖操作分组及所有部分结果组合。
- 在本 Issue 的原始 2026-10-27 证据基线，Native/Clear 正式 REPLACE+DELETE 配对仍为 NOT TESTED。2026-11-01 的追加回放覆盖了该具体切片；其余 SEP-10 决策/恢复组合和完整功能等价仍未证明，S3 继续保持 PARTIAL / HOLD。没有据此宣称 skin-first separation 完成。

## Git

测试提交已推送：`93d067b2733021ff07c8139cc19f03606dbb153e`
（`test(skin): 验证 AI REPLACE DELETE Host 确认与冲突`）。

## Follow-up — Native/Clear formal REPLACE+DELETE paired device replay (2026-11-01)

新增 `nativeAndClearFormalReplaceDeleteProposalsMatchAfterScopedApproval`，使用 API 35 Android 15 `emulator-5570`（WebView `124.0.6367.219`）和设备本机 loopback provider stub。Native 和 Clear 各自打开同一初始快照：两个正式 MEMO、匹配 eventSeq、revision、identity、commit 和 sequence 水位。AI 的 REPLACE 与 DELETE 候选显示后，用户确认前两侧 Store 与正式记录都保持不变。Native 由审核 UI 批量同意；Clear 由 WebView 提交后再经过 Host-owned `确认并提交 2 项`。审批后两端均保留被替换事件的 eventSeq 并将 revision 加一，只删除目标 DELETE 事件，最终正式记录投影相同；两侧 commit 各增 2，已结案提案记录各增 2，pending 为 0，identity 和下一序号水位不变。Clear AI tab/send 由 Android touchscreen input 触发；未调用外部/付费模型。

API 35 配对方法 **1/1 PASS**，完整 `PowerCalAiProposalReviewWebViewInstrumentedTest` 类 **9/9 PASS**。测试 XML SHA-256：`5B2EEBA0F79711830CE1D35C9254D9C4021244A66B67435846494B01DBFD0525`；配对 logcat SHA-256：`6210961F6F6127E3814B5FC4AD249EF6448CE87845E6B783D4BC2B30A2827C7F`。logcat 中包含 `NATIVE_CLEAR_AI_REPLACE_DELETE_PARITY` 的 Store/授权/触屏结果。

强制 JVM 回归为 **786/786，125 suites，0 failures/errors/skips**；边界、Debug 构建与 lint 门禁 PASS（171 actionable tasks，11 executed，160 up-to-date；lint 0 errors / 56 warnings）。Studio Python 回归 **65/65 PASS**，但它是现有 Studio 总体回归，并不证明 AI 审核 UI 的可视化编辑能力。APK SHA-256：Debug `EF85EA15AB31353F9A26C3EEEC3B5DBFC9CEB38BC73069752F7AB66F85965650`；AndroidTest `8A2A7DC15A6967CE9CA73CC48E5B5E0119DA391A72245474D73E3D43A444433B`。Gradle 全门禁、强制 JVM、Studio 日志 SHA-256 分别为 `4C1F7FA1454A7CABB5E0418CCFCAC8A3F99196279D440BFADCAA83C59502946F`、`8B1FDF486430C98FEFA8FB1B0C8B545BC75ADDFFE3C91F2E12E469E539685FEB`、`CCFF81A58F062B5B0629A261271828284D826FFEB1E7A04F2C9F3EF81F54AD77`。所有细节和原始日志位于忽略目录 `.local/android-test/reports/sep10-native-clear-replace-delete-parity-api35-20261101/`。

本 follow-up 只提供一条已选 REPLACE+DELETE 正式审批/写入组合的双端设备证据；其他四类决策、不同部分失败/重试组合、进程死亡恢复、Studio 任意皮肤编辑/结构重构、实时 Provider、TalkBack、实体/OEM 仍未验证。SEP-10 整体状态保持 **PARTIAL / HOLD**。
