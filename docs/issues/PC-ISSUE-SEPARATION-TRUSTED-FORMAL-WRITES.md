# 皮肤正式写入必须经过宿主可信审查

日期：2026-10-27

状态：**本 Issue 的宿主审查实现与所列 JVM/API 35 定向验证通过；代码及报告已推送。SEP-16 全量 Native/Skin 等价仍为 NOT TESTED。**

## 问题与根因

皮肤 JavaScript 通过 WebMessagePort 发出的命令属于不可信输入。之前的 Host 只对重复事件相关的部分编辑保存要求 Android Compose 确认；普通正式事件的 editor.save 和有效草稿的 deck.commit 可以从 Web 命令直接进入持久化写入路径。即使命令受到 host.action 能力检查，这也只证明该皮肤获准请求相应动作，不能证明用户看过并批准了具体写入内容。

问题边界还包括：有效草稿提交可能创建、替换、转换或删除正式事件。若只拦截普通编辑而不拦截这些 deck 写入，脚本仍可绕过宿主审查。

真实 WebView 回归另发现一项数据传输根因：临时 clientDraftId 使用任意随机 63 位整数，经 JSON 暴露给 JavaScript Number 后可能超过 Number.MAX_SAFE_INTEGER；页面将 ID 原样发回时会发生精度丢失，Host 因 ID 不匹配拒绝 patch，表现为草稿没有应用标题等修改。

## 修改及根因对应

实现提交 725e1c79c300272ec9e2f6b1cae3a38dda364733（fix(skin): 皮肤正式写入须经宿主审查）包含 8 个源码/测试文件：

- WebSkinEditBridge 将原有重复事件待审状态扩展为 Host 私有的 pendingWriteReview。非重复正式编辑的有效 editor.save、新建临时会话的有效正式发布，以及持久化草稿 deck 的有效正式写入，先返回 confirmation_required，暂不提交写入。
- 待审记录绑定 Host 内的目标事件/版本、表单或草稿快照与操作类型。Compose 审查通过后，Host 再核对快照和版本，并调用原有 EditSession、NewEventEditSession 或 DeckController 路径。JS 不会取得可自行确认的 token；待审期间的并发写入受到阻止。取消、无变化和不完整草稿继续遵循原有语义，重复事件的既有范围审查继续使用同一 Host 机制。
- NewEventEditSession 的临时 clientDraftId 被限制在 JavaScript 可精确往返的整数范围内。它仍只标识内存会话，不分配持久化事件 ID 或序号。
- 补充 JVM 回归和真实 Compose + WebView instrumentation，检查 Host 审查前后持久化结果、审批、拒绝/取消、快照变化，以及 JS 数值 ID 往返。

修复前的回归确实失败：要求 confirmation_required，原实现返回 saved。红测日志：.local/android-test/reports/separation-20261027/source-editor/recurrence-trusted-review-before.log，SHA-256 4080BF73E16E4F57D34E09BC529495CDD1EB2EDDBDE433EA588E5AD89125A9C7。

## 测试与证据

### 定向 JVM 回归

四个定向 suite 共 **30/30 PASS**：

- NewEventEditSessionTest：7 项，包含 JavaScript 安全整数精确往返及会话副作用回归。
- WebSkinTrustedWriteReviewTest：4 项，包含普通正式编辑、新建正式发布、已有编辑草稿快照复核和正式删除审查。
- WebSkinEditBridgeTest：10 项。
- WebSkinRecurrenceBridgeTest：9 项，包含原有重复实例范围流程和重复事件 Host 审查。

最终定向日志 .local/android-test/reports/separation-20261027/source-editor/trusted-writes-focused-final.log，SHA-256 48305411FF49C09B1291E4AA945449F51172B20EB2EC3420E1F2CF51FB72D2B5。

### 完整 Android Gradle 门禁

执行：

    .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain

结果：**574/574 JVM 测试、80 suites、0 failures/errors/skips、88/88 Gradle tasks**；source boundary、Debug APK、AndroidTest APK 和 lint 均通过。Lint 为 **0 errors / 52 warnings**。完整日志 .local/android-test/reports/separation-20261027/source-editor/trusted-writes-full-gradle-final.log，SHA-256 D87418E9914E471A87FE54AA811B67DBC1668C4AA91BCF8E83CFB8F9456525EC。

完整门禁后只调整了 Android instrumentation 测试中的断言基线和 WebView 脚本隔离，没有再改生产代码；随后 assembleDebugAndroidTest 53/53 tasks 成功，日志 .local/android-test/reports/separation-20261027/source-editor/trusted-writes-androidtest-final-build.log，SHA-256 728DCBA6FB7BB3CF948FA21BDD36D771450B26B3606D0C7BB188723AB580102C。早期一次 instrumentation 尝试的测试夹具数量断言基于错误快照，已改为比较审查前后状态；最终结果以如下重跑为准。

### API 35 模拟器真实 WebView

在隔离 API 35 AVD emulator-5564、隔离 QA application ID dev.powerfulcalendar.d05qaskinrec20261027 上，运行真实 Compose Host 与 Clear WebView instrumentation，**2/2 PASS**：

- webFormalEditAndNewPublishWaitForNativeHostApproval：普通正式编辑和新建事项发布，在 Host 批准前不写入；由宿主确认后才完成写入。
- actualWebViewEditsOnlyTheSelectedRecurrence：原有重复实例编辑仍只修改选定实例。

最终 instrumentation 日志 .local/android-test/reports/separation-20261027/source-editor/trusted-writes-emulator-5564.log，SHA-256 2503C1A4E5E06BCE96D14CB296C0C74DDADC9A63363EDADA4E44B0E9C3863F8F；日志末尾为 OK (2 tests)。该次安装 APK：

- QA Debug APK SHA-256：0BFA6BABF47BB64F24A2F87D7B59146D9F9DE0DAC6DD791B923EC2D7DADFF9F4
- QA AndroidTest APK SHA-256：A2B856A60D8B6AFB642F6E8EB2224F2E3BDFF6AC4F8BB38BF9264C2CE21356E0

对应 APK 与构建日志存放于忽略目录 .local/android-test/reports/separation-20261027/source-editor/。本次没有使用真实用户数据库、生产包或实体设备。

## 签核范围与剩余风险

- **本 Issue 已验证**：Host 能阻止未确认的普通编辑与新建发布；明确批准后沿原有核心写入路径完成；相关快照/版本和临时 ID 回归通过；重复实例路径继续通过 API 35 WebView 测试。
- SEP-16 在矩阵中继续标记 **NOT TESTED**：这代表该行要求的完整 Native/Skin 业务结果等价还没有完成，不否认本报告中的窄范围安全行为证据。API 35 实测只覆盖一个普通 ALARM 编辑、一个新建 ACTIVITY 发布及重复实例场景；deck 的 CREATE/REPLACE/CONVERT/DELETE 组合、四类事件完整对照、异常恢复、进程死亡与完整生命周期尚未全部经过 WebView 端到端验证。
- 没有实体/OEM 设备测试；按用户约定由用户负责实体设备。不能把 AVD 结果当作实体设备签核。
- host.edit 与 host.action 的能力门禁差异、所有 Host 命令分派的系统安全审计，以及恢复/冲突路径的皮肤等价仍需独立完成。
- Studio / Chrome Mock 全量没有在本 Issue 重跑；修改集中于 Android Host/桥接，集成行为由 JVM 和上述真实 Android WebView 验证覆盖。其他皮肤外观、无障碍和完整功能 parity 仍未签核。
- 52 条 lint warning 未阻断构建；没有在本 Issue 中清理无关告警。
- 分离工作树里已有的 Studio project.json、字体许可证、HTML、CSS 变更未纳入代码或文档提交。git diff --ignore-space-at-eol --stat 未显示这些文件存在实质内容差异，本次保留其原状。

## 提交与推送

实现和测试提交：725e1c79c300272ec9e2f6b1cae3a38dda364733，已推送到 origin/feat/skin-first-separation。本报告、进度记录和 SEP-16 矩阵更新随后作为独立文档提交推送；两次提交分离，便于按代码或结案文档单独回档。

整体皮肤分离、功能等价、Release QA 和默认皮肤切换仍未完成；本 Issue 不构成总体 GO 或 cutover 授权。