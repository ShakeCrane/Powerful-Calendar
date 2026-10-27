# PC-ISSUE-SEPARATION-SKIN-SETTINGS-LANDING

日期：2026-10-28

状态：**皮肤内设置落地页切片已实现并通过自动化；SEP-11 总体仍 NOT TESTED**

## 问题与范围

Clear 的设置底部标签原先直接发送 Bridge v1 `settings`，使普通设置导航离开皮肤并进入 `NativeToolsPage`。这让设置页无法由皮肤重构，也让旧版皮肤的受保护宿主工具路由与可定制的皮肤设置入口共用一个含义。

本 Issue 仅实现可定制的皮肤内设置落地页，并保留现有 Host 工具路由。凭据、Android 权限、备份/恢复、皮肤管理及敏感确认继续由可信 Android Host 处理。本切片不声称完成这些功能的皮肤等价迁移。

## 根因与实现

- Bridge action 使用 `settings` 直接导航到 Host `TOOLS`，没有独立的皮肤内设置路由。
- 增加闭合 schema action `skinSettings` 和纯函数 `reduceWebSkinNavigation`。原 `settings` action 保持向后兼容，仍进入 `TOOLS`；从 `TOOLS` 收到 `skinSettings` 不会绕过 Host 页面。
- `PowerCalApp` 将皮肤设置页作为可恢复的导航状态投影为 `SETTINGS`。页面打开时不向 Skin 提供事件、时间轴、月份、Memo 条目、AI 聊天或 AI 审核卡数据；保留必要的编辑快照及 Host 投影选项。
- Clear 设置标签发送 `skinSettings`；AI 页“配置”及皮肤设置页“打开宿主工具与设置”仍发送 `settings`。
- 设置页复用现有 `extraOptions` 和 Host 投影的 `optionGroups`，不建立第二套设置数据或业务实现。敏感能力由 Host 显示和执行。
- Studio Bridge action 列表和 Preview Mock 同步支持两条导航。Mock 明确不展示/操作权限、凭据或真实日历数据。
- Bridge 合同、SEP-11 矩阵和独立 Issue 报告同步更新。SEP-11 仍标记 `NOT TESTED`，因为凭据、权限、备份/恢复、皮肤管理的完整等价与设备行为尚未完成。

## Track B：Skin Studio

落地页结构、文案、样式和导航位于 canonical Clear `web/app.js`，由 Studio 的源码项目模型编辑；不是 Android 隐藏原生页面。Studio 支持对该文件进行源代码编辑、预览和构建。本切片的 Edge E2E 实际通过 Studio Preview 触发设置标签、检查两项 Host 投影导入选项、触发 Host-tools Mock 并返回时间轴；该 Mock 不写入日历数据。

Studio 仍不是完整任意皮肤 IDE；此结果只证明该页面的源码与 Mock 预览链路，不证明所有页面/组件均可视化重构或 Native/Skin 功能等价。

## 回归与证据

以下验证在隔离 worktree `codex/separation-skin-settings` 上完成，基线为 `172288a7c8bf3132aa5c0f10a3ac768f86626d9f`。

- `WebSkinBridgeTest` 定向测试：通过。覆盖闭合 schema 对新旧 action 的接受/拒绝、旧 `settings` 仍进入 `TOOLS`、新 `skinSettings` 保持皮肤内、未知 action 不改变状态。
- 完整离线 Gradle 门禁：

  ```powershell
  .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
  ```

  `BUILD SUCCESSFUL`；602/602 JVM tests，83 suites，0 failures/errors/skips，88/88 tasks；Debug 与 AndroidTest APK 均构建；lint 52 warnings、0 errors。日志：`.local/android-test/reports/separation-skin-settings-20261028/gradle-full-gate.log`，SHA-256 `36C441C5D41B2FBD4134D6EFF8769510E14FF47945AE97A03F8B7839CF1CBCBF`。
- Skin Studio Python suite：51/51 PASS。日志：`.local/android-test/reports/separation-skin-settings-20261028/studio-python-final.log`，SHA-256 `101AC8441D85BBD9E593D06F7378247E8BF443D279D3C6B60FD8046B5A382DD4`。
- Microsoft Edge Studio E2E：PASS。设置落地页、两项 Host 投影导入选项、非写入 Host Mock、回到时间轴均通过；同一用例还保留源码/资源往返、重复事件 Mock、附件 Mock、HTTP 503 后用户显式重发、AI 审核 Mock 等覆盖。日志：`.local/android-test/reports/separation-skin-settings-20261028/studio-edge-e2e-after-fix.log`，SHA-256 `C3CA1E1D5B621ED89A859F720BC4513F0271F71ED08378CA781AB546BA8BB80B`。
- `node --check` 对 `web/app.js`、`v3-studio.js`、`e2e_v3_source.cjs` 通过。
- Clear v3 包验证通过：8 files / 378,340 bytes；包与当前 canonical source 的 SHA-256 均为 `587473D3026F0CF69342B10569C10406563C947A6B04E29ADD6FE78CDD51F122`。
- Debug APK SHA-256：`00B005EE700EC136C44E390EE0292A7C708AAE4F4F31892E7BD35D81D8037524`；AndroidTest APK SHA-256：`60F08FFC420D8C67860FE9D85938C5237CF3C48CD49D5EFC260DC1187C5C9B90`。

未运行 Instrumentation/Android WebView AVD 测试，也未运行实体/OEM 测试。模拟器、Preview Mock 与 APK 构建均不作为 Android WebView 或实体设备 PASS。

## 修改文件

- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinHost.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinBridgeTest.kt`
- `tools/skin-studio/default-clear/source/web/app.js`
- `tools/skin-studio/v3-studio.js`
- `tools/skin-studio/tests/e2e_v3_source.cjs`
- `app/src/main/assets/powercal-clear-default.pcskin`（由 canonical Clear source 构建）
- `docs/V3_HOST_BRIDGE_EXTENSION.md`
- `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`

## 尚未完成与下一步

- SEP-11 全项保持 `NOT TESTED`：凭据与权限设置、备份/恢复、皮肤管理尚未迁移/验证；没有 Android WebView 与设备证据。
- 还需验证 WebView 崩溃/恢复、无障碍与小屏导航、未保存编辑期间的设置导航，以及底层业务状态未被设置页投影泄露。
- Studio 仍缺少任意组件树/页面结构的完整重构工具；当前源码编辑能力与本页 Mock 不能证明全项目 Skin Studio 目标达成。
- 原 `feat/skin-first-separation` worktree 中另一个 Agent 有未提交的 `PROGRESS.md` 和 canonical Clear 项目文件修改，本提交未触碰这些并行资产。该 worktree 与本 topic branch 基线相同；集成二进制包前应在并行源修改归属明确并提交后重新从最终 canonical source 构建。

## 提交记录

实现及本报告提交 SHA：待提交后补录。提交只包含本 Issue 的实现、测试、包、合同矩阵和本报告，不包含其他 Agent 的进度或源码改动。
