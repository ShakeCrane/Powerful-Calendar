# PC-ISSUE-SEPARATION-DRAFT-CONFLICT-RESTORE — Clear 冲突审查与已删除事件恢复

日期：2026-10-29
所属验收项：`S0-DK-03`
状态：**实现与桌面回归 PASS；Android WebView / 设备 parity NOT TESTED；验收项 PARTIAL / unsigned。**

## 问题与验收边界

Native 草稿卡组已有冲突快照、明确整份替换和 tombstone 恢复能力；Clear 只能收到 `hasConflict` / `hasRestore` 布尔状态，没有足够快照和操作入口。Store 也没有有界只读墓碑列表接口。

本 Issue 补齐 Clear 的冲突审阅与删除后恢复路径，保持事件身份、草稿和事务语义由 Core/Store 管理。Studio Preview 仅使用不写真实 Store 的 Mock。本文报告的实现与桌面回归证据不代表 Android WebView 或设备 parity 已通过。

## 根因

1. `WebSkinEditBridge` 只投影冲突/恢复是否存在；旧的皮肤确认命令被封闭 schema 拒绝，而新命令尚未建立。
2. 没有只读墓碑分页接口。用户不能从 Clear 选择已删除事件并开始 Core 恢复提案。
3. 冲突替换存在 TOCTOU：Host 在弹出确认前、后读取了正式 revision，但随后 `DeckController` 以 `approveStale=true` 提交；若另一个写入者恰在最后一次读取后修改正式事件，事务会接受用户未审阅的新 revision。
4. Clear 恢复渲染用未定义的 `args` 生成按钮参数；恢复面板还被长墓碑列表挤到滚动区域之外。
5. Clear 的重绘焦点保护把所有 `#sheetFields` 内焦点都视为正在输入。点击分页按钮后 Host 状态已更新，但旧按钮 DOM 没刷新，继续点击会重复发送旧页码。
6. 审核对话框的取消与明确“保留草稿”操作没有区分。取消应保留仍待审阅的提示；显式 Keep 才关闭提示。

## 修复

- 在 `PowerStore` 增加有界、只读的 `retiredPage(offset, limit)`：页大小限制为 1–100，Bridge 使用每页 50 项；顺序为 `updated_ms DESC, event_seq DESC`。没有数据库迁移或写入。
- Bridge 增加封闭 schema 命令：`deck.retired`、`deck.beginRestore`、`deck.keepConflict`、`deck.requestConflictReview`、`deck.keepRestore`、`deck.requestRestoreReview`。投影完整当前/提案表单、字段差异、事件序号、revision 和恢复草稿身份；皮肤不能传入 `approved`。
- 恢复列表选择只创建/恢复可编辑草稿。正式事件仍为删除状态；恢复需 Android Compose Host 再次确认，继续使用 Store 原有同一事件序号恢复事务。
- `DeckController` 将 Host 展示过的 revision 传递至 `PowerStore.commit`。Store 在同一 SQLite 写事务内比较当前 revision 与被审阅 revision；不相同则返回 Conflict、保留草稿和最新冲突提示。这样关闭了 Host 预检与提交之间的并发覆盖窗口。
- Host 取消冲突覆盖或恢复确认后，草稿和提示均保留；明确 Keep 只关闭相应提示并保留草稿。Compose 文案分别说明冲突取消与恢复取消的实际结果。
- Clear 显示字段差异、可展开的完整当前/提案快照、墓碑分页及恢复操作。冲突/恢复快照出现时收起长墓碑列表；按钮参数从当前状态生成；只有输入控件聚焦时暂停内容重绘。
- Skin Studio 增加 151 条稳定倒序的种子 tombstone 和非写入 Preview Mock。新增 Chrome/Edge Preview E2E，真实点击 Clear 按钮，验证完整分页、冲突替换/保留、恢复草稿保留、Host 审批取消/批准、皮肤伪造批准字段被拒绝，以及页面无 JavaScript 异常。为隔离工作树执行测试时，Git safe.directory 仅通过进程环境设置，没有更改用户全局 Git 配置。
- 通过 `build_default_clear.py` 从规范 Clear 源重建官方 `.pcskin`。

## 验收结果

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| Store / Controller / Bridge 焦点回归 | **PASS**。覆盖事务内 revision CAS、CAS 失败时保留最新冲突提示、Host 取消保留冲突/恢复提示、同序号恢复及恢复并发拒绝。4 项分别为 `PowerStoreTest.reviewedReplacementRejectsARevisionThatChangesAfterHostReview`、`DeckControllerTest.transactionRevisionMismatchKeepsConflictPromptAfterReview`、`WebSkinEditBridgeTest.staleHostConflictReviewCannotOverwriteAndKeepLeavesDraftUntouched`、`WebSkinEditBridgeTest.retiredPickerCreatesRecoverableDraftAndRestoreNeedsSeparateHostApproval`。 | `.local/android-test/reports/separation-s0-dk-03-20261029/gradle-cas-focused.log`，SHA-256 `DE8F88ECB775FA9DEE33796F5320FCFDBF62E8F92E239A26EF50BDF493FA20AB`。冲突控制器夹具补入正式 revision 后 `DeckControllerTest.aConflictKeepsTheCardAndAsksForExplicitReplacement` 单项复测 1/1 PASS：`gradle-conflict-controller.log`，SHA-256 `8FBBDBB37AAF7CEDD3F6EF535EEDE7C7FFAAFB633A72B5FADFD2E71DE4AA70AF`。 |
| 完整离线 Gradle 门禁 | **PASS**。637/637 JVM tests，84 suites，0 failures / errors / skipped；`verifySkinSourceBoundary`、`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 均通过，88/88 actionable tasks。AndroidTest APK 仅构建，未在设备运行。 | 命令：`$env:ANDROID_HOME='E:\android-sdk'; $env:ANDROID_SDK_ROOT='E:\android-sdk'; .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain`。日志 `.local/android-test/reports/separation-s0-dk-03-20261029/gradle-full-final.log`，SHA-256 `F9F0AA83D150931708917E6CC82720EBCB067A095B058EA33A23A10781649473`。 |
| Skin Studio Python | **PASS，61/61**。 | `.local/android-test/reports/separation-s0-dk-03-20261029/skin-studio-unittest.log`，SHA-256 `4DC13C4C8E2C837BCB12B0A6F1BE227FF3C575D115A74FCC5BC46565C767BF4B`。 |
| Preview Mock Browser E2E | **PASS**，Microsoft Edge 156.0.4314.50。验证 Clear 按钮到 Mock Bridge 的真实点击路径、151 条墓碑四页倒序顺序、恢复前/后可展开快照、Keep、Host 取消后重审、批准请求和拒绝 `approved:true`。此证据仅属于桌面 Preview Mock。 | 日志 `.local/android-test/reports/separation-s0-dk-03-20261029/browser-preview-e2e.log`，SHA-256 `4D878FB894119E2BF8BF0684FF8508D1EAFCC97D0214CB2CB4DC411E2AB2BA4F`。UI 截图 [restore-proposal.png](../../.local/android-test/reports/separation-s0-dk-03-20261029/restore-proposal.png)，SHA-256 `E8316C67DDD2F4477F37F6B2789B14814D7B01128B5091E78D14D96319C06A6E`。 |
| JavaScript / diff 检查 | **PASS**。三个修改的 JS 文件均通过 `node --check`；最终 `git diff --check` 通过。 | `tools/skin-studio/default-clear/source/web/edit.js`、`tools/skin-studio/v3-studio.js`、`tools/skin-studio/tests/e2e_v3_conflict_restore.cjs`。 |
| Clear 包 / Debug APK 同源 | **PASS**。9 files / 389,356 bytes；源码规范包与 Debug APK 内嵌 Clear 资源逐字节相同。 | 两者 SHA-256 均为 `74E82A11444E9BB6A79D3E222EE9D6F5DF3437A94EBB537C01814B6AEB0BCEF2`。 |
| APK 基线 | Debug APK SHA-256 `D271AB748990CCB303FD7B7350CC9100B29F8466C9F256AEE25BD569320C158B`。AndroidTest APK SHA-256 `33FC27215E84DE4D2BFCC919C1D46854879AB4A49FE1CAA0A1C45778A0B6DC31`，仅编译。 | `app/build/outputs/apk/debug/app-debug.apk`；`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`。 |
| Android WebView / AVD / 实体设备 | **NOT TESTED**。本机 SDK 仅有 API 36；没有 Emulator 可执行文件；`adb devices -l` 为空。 | Studio Mock、Robolectric 与 APK 编译不冒充 Android WebView 或实体设备验收。 |

本轮有一次较早的完整 Gradle 运行因 Controller 测试 Fake 未提供有效 formal revision 而出现 1 项失败；Fake 已补入 revision，单项复测通过，随后最终完整门禁 637/637 通过。另一次 Browser E2E 暴露焦点保护误阻止分页重绘；产品代码和 E2E 交互均已修正，最终 E2E 通过。

## 修改文件

- Core：`app/src/main/java/dev/powerfulcalendar/core/PowerStore.kt`、`DeckController.kt`、`StoreDeckPersistence.kt`
- Host：`app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridge.kt`、`PowerCalApp.kt`
- 回归测试：`app/src/test/java/dev/powerfulcalendar/core/PowerStoreTest.kt`、`DeckControllerTest.kt`、`DeckControllerConcurrencyTest.kt`、`app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridgeTest.kt`
- Clear / Studio：`tools/skin-studio/default-clear/source/web/edit.js`、`style.css`、`tools/skin-studio/v3-studio.js`、`tools/skin-studio/tests/e2e_v3_conflict_restore.cjs`、生成资产 `app/src/main/assets/powercal-clear-default.pcskin`
- 合同与进度：`docs/V3_HOST_BRIDGE_EXTENSION.md`、`docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`、`docs/agent-handoff/PROGRESS.md`

## 交付与结论

代码提交：`e04b2723e10ebb57e22a9c2c4da7580631bcd7c2`（`fix(分离): 补齐冲突审查与删除恢复`）。
报告、验收矩阵、Bridge 合同与进度通过独立文档提交；提交 SHA 可从本轮交付记录及 Git 历史追溯。
推送目标：`origin/codex/separation-s0-timeline-drag`。

Clear Bridge/Core 桌面实现和上述可运行回归已通过。完整的 `S0-DK-03` 仍为 **PARTIAL / unsigned**；待 Android WebView、实体设备及与 Native 的设备侧 parity 证据后再签核。该设备缺口不阻止继续下一项可自主完成的分离工作。
