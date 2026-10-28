# PC-ISSUE-SEPARATION-MONTH-NAVIGATION

日期：2026-10-28

状态：**月份导航实现与自动化回归 PASS；SEP-02 完整 Native/Skin 与 Android 设备等价验收 NOT TESTED**

优先级：**P1（分离阶段功能对等缺口）**

## 问题与边界

Clear 皮肤原先只能前后翻月，不能选择指定年份、月份或某一天；月历导航复用了时间轴锚点，因此浏览月份会改变时间轴相机。返回今日、从月历为所选日期新建 Activity、跳到所选日期的时间轴，也没有与 Native `MonthScreen` 一致的状态路径。重复事项打开还必须保留原始实例槽，不能退化为打开整个系列。

本 Issue 补齐 SEP-02 的月份状态和交互，并保持 Native 月视图及正式事件存储语义不变。不覆盖完整 Native/Skin 视觉与行为等价验收；Edge Studio 使用 Mock Host，不能代表 Android WebView 或实体设备。

## 需求来源与期望/实际

- 来源：`docs/CORE_CONTRACT_FREEZE_2026-10-21.md` 的真实时间/事件独立性合同、当前 `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md` 的 SEP-02 月历条目，以及 Native `MonthScreen` 与现有 Host/皮肤 Bridge 的实现。
- 期望：月份浏览、选择日期、Today 和时间轴跳转使用独立月历日期状态；不能更改时间轴相机；由月历创建的 Activity 使用选中本地日期；重复实例编辑保留其原始槽位。
- 修复前实际：Studio 浏览器回归真实点击 2030-02-14 后仍停留在 2030-02-01，日志记录 Mock Host 拒绝合法日期。源码审查也发现 Host 月历投影和创建路径绑定 `anchorMillis`，导致月份浏览与时间轴视口耦合。
- 修复后实际：独立月历状态更新并通过下面列出的 JVM、Studio Python、Edge CDP、Debug 与 AndroidTest APK 编译门禁。Android WebView/AVD/实体设备行为未执行，保持 NOT TESTED。

## 根因与修复

1. `PowerCalApp` 使用 `anchorMillis` 作为月历选择日期；`BrowseMonth` 会改写相机时间，月份日期无法独立保留。
2. Bridge 没有指定年月、指定日及“月历回到今天”的闭合动作。
3. Studio Mock 初始月历以固定 2026-10 和固定日期为状态，不能模拟日期选择、月历 Today 或从月历跳转时间轴。加入指定月份后，Mock 的日期校验对默认日期和已选日期的 `slice(0, 7)` 放置不一致，合法的 `2030-02-14` 会被拒绝；Edge 回归重现后已修正。
4. Clear 重复事项通过 `PowerCalEdit.routeAction` 发出 `editor.open` 命令，而不是页面动作 `openEvent`。先前浏览器断言读取父页面 Bridge 日志，既不适用于隔离 iframe，也没有证明编辑器中的实例槽。断言现改为选择“仅本次”后核对表单时刻 `2026-10-22 09:00`。

修复后，Host 保存独立 `webMonthSelectedDateText`：进入月历时从当前时间轴日期初始化；选择年月时选中该月 1 日；日选择仅接受当前月内、年份 1900–2100 的日期；月份浏览不更改 `anchorMillis` 或时间跨度；Today 使用 Host 时区当前本地日期；月历创建 Activity 取被选日期；显式 Timeline 跳转使用被选日期并在回到月历后保留该日期。当前 Clear 与 Studio Mock 使用同一组 Bridge 动作。Host 仍校验日期范围及正式事件/重复槽身份。

## 修改文件

- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinHost.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinMonthProjection.kt`
- `app/src/main/assets/powercal-clear-default.pcskin`（由 canonical Clear source 重新构建）
- `app/src/main/java/dev/powerfulcalendar/ui/v2/` 下的 Compose、Bridge 与 Projection 回归测试：
  `PowerCalCasualSuggestionComposeTest.kt`、`WebSkinBridgeTest.kt`、`WebSkinHostProjectionTest.kt`
- `tools/skin-studio/default-clear/source/web/app.js`
- `tools/skin-studio/default-clear/source/web/index.html`
- `tools/skin-studio/default-clear/source/web/style.css`
- `tools/skin-studio/v3-studio.js`
- `tools/skin-studio/tests/e2e_v3_browser.cjs`
- `docs/V3_HOST_BRIDGE_EXTENSION.md`
- `docs/SKIN_STUDIO_AI_AUTOMATION_V1.md`
- `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`

## 回归与证据

### Android JVM 全量门禁

命令（Gradle 8.14.3，Android SDK API 36，离线依赖）：

```text
:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果：**BUILD SUCCESSFUL；617/617 JVM tests，87 suites，0 failures、0 errors、0 skipped；Debug APK 构建成功；皮肤源边界检查成功；Lint 51 warnings / 0 errors。**

相关测试：

- `PowerCalCasualSuggestionComposeTest.webMonthNavigationKeepsSelectionSeparateFromTimelineAndCreatesForSelectedDate`
- `WebSkinBridgeTest.eventOpenAcceptsOnlyARealProjectedRecurrenceSlot`
- `WebSkinHostProjectionTest.monthProjectionUsesRealHostOccurrencesAndExactDateWindow`

### AndroidTest APK 编译

命令：

```text
:app:assembleDebugAndroidTest --offline --rerun-tasks --no-daemon --console=plain
```

结果：**BUILD SUCCESSFUL；AndroidTest Kotlin 编译及 APK 打包完成。** 未运行 instrumented tests。

### Edge 浏览器回归

使用本机 Microsoft Edge **156.0.4314.42** headless + Chrome DevTools Protocol，连接本机 Skin Studio 与其 Clear source preview；脚本及日志保存在 `.local/android-test/skin-separation-month-navigation/` 和 `.local/android-test/reports/skin-separation-month-navigation/`。

结果：**PASS**。测试轨迹：

1. 固定 Studio 时间为 `2026-10-21T16:30Z`、Host zone 为 `Asia/Shanghai`，验证月历选择日与今日都是 `2026-10-22`。
2. 从 2026-10 选择 2030 年 2 月，时间轴标题/相机不变；点选 `2030-02-14` 后选中状态与议程日期更新。
3. 执行“在时间轴查看这一天”，时间轴显示 `2月14日`；返回月历仍选中 `2030-02-14`。
4. 从该月历日期打开 Activity 编辑器；关闭后月历日期仍保留；翻到下月，再执行 Today，选择日期为 `2026-10-22`，Today 的 Timeline 跳转也落在 `10月22日`。
5. 切换重复事项预览，打开原始槽 `2026-10-22T09:00`，选择“仅本次”，编辑器时间字段为 `2026-10-22 09:00`。

CDP JSON 结果日志 SHA-256：`86683F343EC68450D275ADC7ACE39531BB86AAC787E14EAF3D7A1FC8285EA012`。标准 Puppeteer E2E 命令未运行，因本工作区没有安装 `puppeteer`；上述流程由无外部 Node 依赖的 Edge CDP runner 实际执行。Studio 是 Mock Host，不属于 Android WebView 测试。

### Skin Studio 与打包

- `python -m unittest discover -s tools/skin-studio/tests -v`：**60/60 PASS**；日志 SHA-256 `0CBFD3AE98FE9802DD1888681E45EA6FEA3BE2E6083CCAAAE88B66AD7514B935`。
- `python tools/skin-studio/build_default_clear.py --verify`：**PASS**；Clear 包 8 个文件、380,940 bytes；源包 SHA-256 `9cc6bdd86a53c693b8cec6e84950ed89ec49bcde9fb78d773034e4147e45a454`。
- `node --check`：Clear `app.js`、`v3-studio.js`、`e2e_v3_browser.cjs` 均通过。
- `git diff --check`：通过。

### 构建产物与原始日志

- Debug APK SHA-256：`82F72FAFB1FB53FE4BDFFF38DD065821E5A4FBD3B2F53C5771A82828BE5FA7E2`
- Gradle 全门禁日志 SHA-256：`73ECAF355A6A799439DF8DCFDBD00D0B1EF3B29F0C1B6401DAACF1BAE9D82A4F`
- Skin Studio Python 日志：`.local/android-test/reports/skin-separation-month-navigation/python-unittest.log`
- Edge CDP 日志：`.local/android-test/reports/skin-separation-month-navigation/month-navigation-cdp.log`
- AndroidTest APK 编译日志：`.local/android-test/reports/skin-separation-month-navigation/gradle-androidtest-build.log`
- AndroidTest APK SHA-256：`6FBA400C709EDBE947F89626FBC50AB1F3550381D9FC18297C20661760F1A8A4`
- AndroidTest APK 编译日志 SHA-256：`9BDA8CEE6FCAAE8C60EDD2D8D4F13F20C179B50735C9E0C58F3335E600F8F689`

## 未验证与剩余范围

- `E:\android-sdk\emulator\emulator.exe` 不存在；本轮没有启动或清除 AVD。ADB daemon 在当前受限环境中无法启动。
- 未执行 Android WebView 仪器测试、API 35/36 AVD 交互、实体/OEM 设备测试。实体设备验证由用户执行。
- 未完成 SEP-02 全部 Native/Skin 对照回放及可访问性/生命周期/异常恢复矩阵；Studio CDP 仅验证清单所列交互。
- 所以本 Issue 的自动化实现结果为 PASS，但 parity matrix 的 SEP-02 整体仍为 `NOT TESTED`。不得将此 Issue 作为分离完成或发布 GO 的证据。

## 提交

实现与测试提交：`e347c85382024f9f19fc0f7735d754c810b114ff`。提交说明：`feat(分离): 补齐 Clear 月历导航与日期选择`。已推送到 `origin/codex/separation-skin-settings`。Issue 报告与矩阵作为后续独立文档提交。
