# PC-ISSUE-SEPARATION-AI-WEBVIEW-DECISIONS

日期：2026-10-27

状态：**STAGE / REJECT / SUPPLEMENT 的 API 35 Clear WebView 定向用例 PASS；PUBLISH 本轮复跑因模拟器断连无结果；SEP-10 与 S3 仍 NOT TESTED / 未关闭。**

## 问题与根因

Clear 已显示 AI 提案的四种决策按钮，Host/Robolectric 测试也分别覆盖了部分 STAGE、REJECT、SUPPLEMENT 路径。但此前 Android WebView instrumentation 只验证了 PUBLISH 的 REPLACE/DELETE 及可信 Host 确认，未实际点击 Clear WebView 的另外三个决策按钮。

这是端到端证据缺口，本轮没有复现产品代码缺陷，也没有改生产代码。

## 新增回归

在 `PowerCalAiProposalReviewWebViewInstrumentedTest.actualClearWebViewStagesRejectsAndSupplementsThroughHostWithoutUnapprovedWrites` 中，通过真实 PowerCalApp、打包的 Clear WebView、WebMessagePort 和 Host 决策路径执行：

1. 本地 HTTP stub 返回两项独立 MEMO 候选；未作决定前，草稿、正式事件、身份和 commit 计数不变。
2. 取消第二项选择并点击“写入草稿”：只生成一张待处理草稿，剩余候选仍显示；正式事件数与 commit 数不变。
3. 点击“批量拒绝”处理剩余候选：已暂存草稿不变，也不产生正式写入。
4. 为缺少结束时刻的 ACTIVITY 选择“补充细节”，再提交用户补充的结束时刻：Host 返回更新后的完整候选，但不创建草稿、正式事件、身份或 commit。
5. 拒绝更新后的候选：最终只留下用户明确选择暂存的那一张草稿。

整个用例使用随机命名的 SQLite、preferences 与缓存命名空间；stub 只绑定设备本机回环地址，不调用真实 AI provider。测试使用全新 QA applicationId `dev.powerfulcalendar.d05qaisolateddecisions20261027`，没有替换默认 `dev.powerfulcalendar`、已有 QA 包或其他 Agent 的包。

## 验证结果

### Android 自动化与构建

- 定向 Host/Bridge 回归：**23/23 PASS**，包括 `WebAiProposalReviewIntegrationTest` 7 项、`WebAiProposalReviewTest` 5 项、`WebSkinHostProjectionTest` 11 项。
- 最终完整离线门禁：**593/593 JVM tests、83 suites、0 failures/errors/skips、88/88 tasks**；`verifySkinSourceBoundary`、Debug APK、AndroidTest APK 构建通过；lint **0 errors / 52 warnings**。
- 新增 instrumentation 用例在默认和唯一 QA 包配置下均成功编译。唯一 QA APK 的 `applicationId` 为 `dev.powerfulcalendar.d05qaisolateddecisions20261027`，AndroidTest 包名为其 `.test` 后缀。
- API 35 `emulator-5564`：新 STAGE / REJECT / SUPPLEMENT 用例 **1/1 PASS**，Instrumentation 返回 `OK (1 test)`。
- PUBLISH：此前 bundled Clear WebView 的 REPLACE/DELETE Host 确认有 **PREVIOUS PASS**，见 `PC-ISSUE-SEPARATION-AI-REPLACE-DELETE.md`。本轮再次运行该方法时 ADB 以 255 退出，只有测试类名，没有 JUnit 结果；随后对 `emulator-5564` 的 `get-state` 返回 `device not found`。本轮 PUBLISH 复跑因此为 **NOT TESTED**，不把历史结果算成本轮通过。

### Skin Studio / Track B

- Skin Studio Python：**42/42 PASS**。
- Chrome source/AI Mock E2E：通过 Clear 源码 round-trip、recurrence Mock、附件 Mock，以及 AI review Mock 的 **supplement / stage / reject / Host confirmation** 操作；Mock 明确未读取真实文件或写入日历。
- Clear bundle 验证：**8 files / 376661 bytes PASS**，SHA-256 `0e17aabfe216e8ecb0775dafb8ea66857e25687b09e1167b7c68e1d9ffd81623`。
- `node --check tools/skin-studio/default-clear/source/web/app.js`：PASS。

这些 Studio Mock 结果只证明编辑器/浏览器原型路径，不能替代 Android WebView、真实 Provider 或 Native/Skin 全功能回放。

## 设备数据与失败记录

- 所有面向设备的安装、shell 和 instrumentation 命令均显式指定 `-s emulator-5564`。曾执行一次只读 `adb devices -l` 枚举（显示 5554 与 5564 在线），没有向 `emulator-5554` 发送任何命令或读取其包/数据。
- 先确认新的 applicationId 未安装，再安装独立 app/test APK。没有执行 `pm clear`、卸载或 AVD wipe；已有 `dev.powerfulcalendar.d05qaaiops2026102701`、其他 Agent 的 `dev.powerfulcalendar.d05qaskinrec20261027.test` 和默认包均未被替换。
- 新的 STAGE / REJECT / SUPPLEMENT 测试成功返回；用例 `finally` 清理随机数据库、preferences 与缓存。之后 PUBLISH 复跑期间 AVD 断连，无法完成额外的设备端残留目录复核；隔离包内可能留下该中断用例的合成临时数据，未尝试恢复、删除或重启设备。
- 第一次未经引用的 Gradle QA suffix 参数被 PowerShell/Gradle 拆分，构建在任务解析阶段失败；加引号并使用仓库规定的 `powercal.d05QaSuffix` 后，唯一 QA APK 构建成功。源码未受失败尝试影响。
- Python 初次运行受沙箱 loopback 限制，10 项报 `WinError 10013`；使用获准的本机回环测试权限重跑后 **42/42 PASS**。这不是产品断言失败。

## 可追溯证据

详细日志、JUnit XML、APK 和设备快照位于：`.local/android-test/reports/separation-20261027/ai-decisions-webview/`。

- 最终完整 Gradle 日志 SHA-256：`DF12723BEED45B7B98A7D01BC9B225229A21A635E49E27EDEA0962155A5D35C2`。
- 三个 Host/Bridge JUnit XML SHA-256：集成 `CE2FABB12B2157674A46080FFD8A9EDB76D79C5E97BA7F149E080647DC3DD2A8`；模型 `D12F8DAA4BC09E5F00411734B8228E78C4ADEDECB9D4F85703F25E03D2F02BA8`；投影 `F0F7DD87D692F24ADE5C8C58F6F2402CE0FBB21D42423310DB9A140BEABFB0E6`。
- 新 WebView STAGE / REJECT / SUPPLEMENT 测试及安装日志 SHA-256：`D7672806E91B5D3072C3C640EB1002DA2D233CB78CF70FFD78149916754A95F2`。
- PUBLISH 中断日志 SHA-256：`A37D45268DA45158F918E94957D537DA4496C409CA0B1FBBB6D3D0B794646525`。
- 完整门禁 Debug / AndroidTest APK SHA-256：`B3115D80E25CBA24B1A6177D1EED5506DB613D467123E1BCE99268B31296780D` / `EAE898E420416954792224E4EA0407EFC836AD9BF81C5D2151BEC0FA4283EBE6`。
- 唯一 QA 配置构建的 Debug / AndroidTest APK SHA-256：`2063F3FEFD3EA36659429EF60D8D3BC681CECF06C417525F4580F061C628E506` / `173CE223A59BF188E6534AC359BA26492D0185780DFD59117F42F1F319CC113E`。
- Studio Python / Chrome E2E 日志 SHA-256：`16A4D747A0965C7E8C275C696A7BFFA6A843DF772E465EF81E8ADF5D5AFC8DA1` / `770F64A46E489987B810EC0314F18A0F35FC6451C39E62C48774D7A732369510`。
- Clear 静态校验日志 SHA-256：`C02B48483629BF91511C6E212F5DD3B80E200B6C1BEA24908C5ACBE1190E4F22`。

## Git 与剩余工作

- 测试代码提交并推送：`5bc70f8aee4f1d1f157fd006118b29ef80858d64`（`test(skin): 补充 AI 审核决策 WebView 回归`）。
- 本报告和矩阵补充另行提交，避免与测试代码混在一个提交中。
- SEP-10、SEP-16 和 S3 仍保持 **NOT TESTED / OPEN**：PUBLISH 本轮未复验、实际 provider、全操作类型、冲突/取消/重试的 WebView 覆盖、进程死亡恢复和 Native/Skin 全量结果对照仍未完成。之后如 API 35 AVD 重新可用，应先检查 5564 与隔离 QA 包状态，再单独重跑 PUBLISH 和本用例；不清除或 wipe AVD。
