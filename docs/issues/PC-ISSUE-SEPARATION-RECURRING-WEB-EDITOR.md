# 皮肤重复事件编辑与宿主可信审查

日期：2026-10-27。状态：**JVM、Studio、Chrome Mock 和 API 35 隔离 WebView 定向验证通过；本改动待提交；全功能等价、其他事件类型及实体设备尚未签核。**

## 问题与根因

时间轴和月份投影为每个实例提供的 `occurrenceKey` 原来带有事件序号前缀（例如 `8:20261022T090000`），而现有正式编辑会话要求按原始重复槽位键（例如 `2026-10-22T09:00`）选择范围。旧 Web 编辑命令没有槽位字段，也没有选择“仅本次 / 本次及以后 / 整个系列”的命令，因此 Clear 只能对整个重复规则编辑，无法与 Native 的实例范围语义一致。

另一个必须同时处理的安全问题是：皮肤脚本不可信。若新增范围编辑后仍让 JS 的 `editor.save` 直接保存重复事件，脚本即可跳过人类对拆分范围和完整变更的确认。因此重复相关正式保存必须暂停在 Host 内，展示可信 Android 审查，再由 Host 调用既有 `EditSession` 保存路径。

## 修改

- 时间轴和月份只读投影新增独立 `originalOccurrenceKey`，保留旧 `occurrenceKey` 字段供现有客户端兼容。Clear 以该槽位键打开编辑器。
- `editor.open` 的 closed schema 接受可选原始槽位键；Host 校验日期格式、当前版本、重复规则及该实例存在后，复用 `EditSession.open`。旧客户端不带槽位键时仍可打开整个系列。
- 新增 `editor.scope` 和 `editor.discardChanges`。必须先选范围才可修改或保存；脏表单不能切换范围；单次范围只开放标题和时间字段；JS 无法自行指定 scope 以外的字段。
- 对重复正式记录的有效修改，以及把单次记录改成重复规则的有效修改，`editor.save` 只返回 `confirmation_required`。私有随机 token、完整当前记录、原槽位显示值、待保存表单和范围均留在 Host 内存，不投影给 JS，也没有 JS 确认命令。
- Compose Host 显示完整 before/after 审查及事件版本。确认时重新读取记录并比较记录、表单和范围；变化则返回冲突。通过现有 `EditSession` 保存或拆分，并保留乐观版本检查和命令重放保护。取消保留编辑内容；皮肤切换会 checkpoint 并取消未处理审查。
- Clear 编辑页新增范围选择、单次字段限制、放弃修改和等待 Host 结果的状态。Studio 预览只提供明确标识的模拟确认/取消，不执行真实业务写入。
- 默认 Clear `.pcskin` 由规范源码重新构建。未改动的第三方皮肤仍使用同一 Host 实现。

## 红测和安全检查

- 原始实例键被旧桥接拒绝：日志 `.local/android-test/reports/separation-20261027/source-editor/recurrence-before.log`，SHA-256 `F7921676DA2DC7022CDDE1C668EB8A31E6BD2D17C1C55242F9388FB918F65E9F`。
- 初版实现曾允许不可信 JS 直接保存重复事件；新增安全回归按预期失败，要求 `confirmation_required` 却得到 `saved`。失败日志 SHA-256 `4080BF73E16E4F57D34E09BC529495CDD1EB2EDDBDE433EA588E5AD89125A9C7`。之后改为仅 Host Compose 确认可以授权保存。
- 定向 JVM 回归 **27/27 PASS**：`WebSkinRecurrenceBridgeTest` 9 项、`WebSkinEditBridgeTest` 10 项、`WebSkinHostProjectionTest` 8 项。覆盖 closed schema、全部三种范围与 Native `EditSession` 对照、单次字段限制、无效/伪造槽位、放弃修改、checkpoint、命令重放、未确认保存拦截、确认取消、外部版本变化冲突、皮肤切换及精确实例审查字段。最终定向 Gradle 日志 SHA-256 `27B473F99DD0270015AD6AEA6CD61D1C548CF1ED2ECEBA4032F6BEEEA9C87CAD`。
- Studio Python 全量 **42/42 PASS**；完整日志 SHA-256 `335F6CC9D8BC199F8E5191C22C556BCB09A0AF3AACD3A221A00094C0C6A01B43`。
- 无 npm 依赖的真实 Chrome CDP 页面回归通过源码编辑、预览、导出重导入及重复编辑交互。这里的确认由 Studio Mock 提供，只验证前端等待/控件行为，不是可信 Host 证据。日志 SHA-256 `97FA9DDCA79C8295D8B92C879FC7530CC126C816EAD5B9548BB0F2584D7FF698`。
- 最终完整离线命令：

  ```powershell
  .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
  ```

  结果 **569/569 JVM、79 suites、0 failures/errors/skips、88/88 Gradle tasks**；Source boundary、Debug 与 AndroidTest APK 构建通过；Lint **0 errors / 52 warnings**。完整日志 SHA-256 `1531F28093E1BA8A1A380F19ED703F46AF888CA15D9F572D4B57006D66A822FC`。
- 隔离 API 35 AVD `emulator-5564` 上的实际 Compose Host + Clear WebView instrumentation **1/1 PASS**：打开重复实例、强制先选范围、请求单次修改后检查正式记录未变、取消 Host 审查并确认仍未变，再次请求并在 Native Host dialog 确认，最终只有选中实例新增覆盖，正式事件数保持一个且 revision 只增加一次。测试日志 SHA-256 `F219B0FE1604D90C25AD7B9762C8AA8EC18479097A340F148C546E1DA85164C7`；安装测试时 QA APK SHA-256 `0BA65A226ACE688B508EABD428F64CF01C7066B05B0BE2B004879CD08907F6DF`，AndroidTest APK SHA-256 `D4B890CC8D19C3FDF77BBB26DFE18B6A38EC76A2F0A23DBF094F504B00CF3FD2`。设备上的哈希与本地构建匹配。
- `.pcskin` 校验通过；资产 SHA-256 `2283AE998C43927C627E1B67DBFA5924B189E0E20EC8C24DD032AE89688D0939`。`git diff --check` 通过。

## 范围与剩余工作

- API 35 instrumentation 覆盖真实 Compose Host 与 WebView，但只覆盖单次 ALARM 实例；ACTIVITY、DEADLINE、MEMO 的范围交互、进程死亡、完整 lifecycle 恢复和实体设备没有由此签核。最终全量构建包含一个随后加入的 `appScope` 生命周期调整；模拟器测试没有针对该最后源码重跑，故设备 PASS 只适用于报告中所列 QA APK。
- 尚未完成重复实例对所有 Native/Skin 操作的双轨业务结果比较，也未覆盖独立恢复、WebView 崩溃、无障碍和所有 UI 尺寸。
- 安全审计发现独立待办：非重复 `editor.save` 和 `deck.commit` 仍可从受 `host.action` 保护的 Web command 执行，但没有等价的可信 Host 完整内容确认。该缺口记录在 SEP-16，不能由本 Issue 关闭。
- `host.edit` 与 `host.action` 的能力门禁语义仍需独立兼容/安全审计。Studio 不能直接编辑全部 `project.json`、任意文件和所有二进制资源。
- 此 Issue 已具备本地验证证据，提交与推送 SHA 及后续复验将在本文件的交付记录中补录。整体功能等价和默认皮肤 cutover 仍未通过。

## 证据位置

详细日志与 APK 保存在忽略目录 `.local/android-test/reports/separation-20261027/source-editor/`。本报告没有复制密钥、用户数据或 AVD 数据。实体设备由用户测试。
