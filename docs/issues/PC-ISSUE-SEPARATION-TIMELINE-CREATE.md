# PC-ISSUE-SEPARATION-TIMELINE-CREATE — Clear 时间轴新建事件上下文

日期：2026-10-28

状态：**本 Issue 实现与 API 35 虚拟设备行为验证 PASS；时间轴 ACTIVITY 新建已有 Native/Clear 配对切片，SEP-04 全功能等价仍 PARTIAL / HOLD。**

实现提交：`2ba08a0`（`fix(分离): 补全时间轴四类事件新建上下文`）。

## 根因

Clear 顶部“＋”固定携带 data-kind=ACTIVITY，时间轴上无法像冻结版 Native 一样选择 ALARM、ACTIVITY、DEADLINE 或 MEMO。Skin 的 edit.js 将该入口送入现有 deck.create；Host 虽然用内存中的 NewEventEditSession 创建表单，但原实现只给 ZoneId.systemDefault()，没有传递时间轴当前相机的吸附时刻。Web 时间轴新建因此既缺少事件类型选择，也与 Native 的显式时间选择不一致。

同一 Host 创建路径用于 Month。其顶部“＋”原先也没有保留月历当前选中日期；Native Month 新建则将所选日期传给编辑会话。

## 修复

- Clear 时间轴“＋”现在打开可访问的四类型选择弹层。选择后仍由 edit.js 的现有事务路由发送 deck.create；没有新增皮肤自带的持久化、事件身份或时间输入能力。
- Host 对命令处理时读取可信 Compose 页面、相机和时区。TIMELINE 通过与 Native 相同的 TimelineCoordinate.snap(camera.anchorMillis, camera.hours, zone) 得到 AtInstant；MONTH 从相机和时区得到 OnDate。时间戳不由不受信任的 Skin 发送。
- WebSkinEditService 使用调用方提供的 zone/selection 初始化现有 transient NewEventEditSession。其他入口保留各自默认行为。
- Studio 的动作名称改为“新建事件”；预览 Mock 日志包含选中的 kind。功能入口与样式都在 Clear 的 web/app.js 源码中，可通过 Studio 源码编辑。
- 新增 Host service 与 PowerCalApp Compose 回归：覆盖四类表单、时区、时间轴锚点、Month 日期及关闭后数据库计数不变。

## 已取得的证据

- API 35 Android Emulator（serial `emulator-5554`，`sdk_gphone64_x86_64`）真实 WebView instrumentation：**1/1 PASS**。`timelineChooserUsesTrustedCameraForAllKindsAndClosingDoesNotWrite` 通过实际 Clear WebView 打开“＋”、选择四类并关闭；对每一类核对 Host 表单时区、时间轴吸附时间/结束建议值，且确认草稿、待处理、身份、提交、永久序号及队列号和正式事件数均不变。使用命名空间隔离的临时数据库、SharedPreferences、文件；没有清除模拟器数据。结果 XML：`app/build/outputs/androidTest-results/connected/debug/TEST-PowerCal_API35(AVD) - 15-_app-.xml`，SHA-256 `31D950C2CB8219AAAE219A7CD421A2AA0F49A625B6314C8E96899A709861108C`。本测试不代表实体设备验证。
- 最终完整离线 Gradle gate：`:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain`：**BUILD SUCCESSFUL，88/88 tasks；601/601 JVM tests、83 suites、0 failures/errors/skips；Lint 0 errors、52 warnings**。完整日志位于 `.local/android-test/reports/skin-separation-timeline-create/full-final-gradle-gate.log`，SHA-256 `779F317ECF5276396305D7B76552DB327F6C9F3A39B4BAE302A30596A1D27CB4`。
- Studio 回归：`python -m unittest discover -s tools/skin-studio/tests -v` **51/51 PASS**；Clear app.js、Studio v3-studio.js、浏览器回归脚本的 `node --check` 全部 PASS；`python tools/skin-studio/build_default_clear.py --verify` PASS，8 files / 377651 bytes。Python 日志 SHA-256 `210A946943EA29300543C3726346542B3C1B315F15668DB549975088A5920644`；Clear package SHA-256 `11F11CF20845BF7A74AD83A8C891903BD633BA9EAEFEF4F17C4BE675A435DB2C`。
- Microsoft Edge headless + Studio local server 的 CDP 回归：**PASS**。iframe 打开时间轴 chooser 并逐一选择四类；非写入 Mock 收到四条对应 `deck.create`，且呈现预期字段。JSON SHA-256 `8774FAF2DADC21AFB5BB7B4E8FCABF611F4BB7871656DC8896767F8E6B7C04A3`；脚本 SHA-256 `BA2F3191B95C8B75F3C3696DCED63754441FA68932FD152F8FDE0419DA9FB413`。这是 Studio Mock 证据，真实 Host 行为另由上述 API 35 WebView 测试验证。
- APK hashes：Debug `app/build/outputs/apk/debug/app-debug.apk` SHA-256 `1237A41F647E852DDAA406475488F15BC6153209C0843077A38D5C1ED224A43A`；AndroidTest `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` SHA-256 `EAF715BBEDA3B0B5AEC9143F2881CFF74E3D466F11B7D5606CEEE9F2D256957B`。
- 早先在未隔离目录运行时出现的包 stale 与 loopback/temp 权限问题，已通过逐个核对包内其余文件与 HEAD 完全一致后，采用隔离快照产物解决；随后当前工作目录包校验与 Studio 51 项均通过。没有覆盖或提交其它 Agent 所有的文件。

## 文件范围

- 生产 Host：PowerCalApp.kt、WebSkinEditBridge.kt、WebSkinHost.kt。
- 测试：WebSkinEditBridgeTest.kt、PowerCalCasualSuggestionComposeTest.kt、TimelineCreateContextWebViewInstrumentedTest.kt。
- Skin/Studio：Clear web/app.js、v3-studio.js、e2e_v3_browser.cjs。
- 内置包/契约/追踪：powercal-clear-default.pcskin、V3_HOST_BRIDGE_EXTENSION.md、SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md。

工作目录里另有其他 Agent 所有的 `docs/agent-handoff/PROGRESS.md` 与 Clear project.json/index.html/style.css/字体资产；它们未纳入本 Issue 的暂存与提交。全仓库 `git diff --check` 会报告 PROGRESS.md 的并行改动含 CRLF/trailing whitespace；本 Issue 文件需单独检查，不能通过改写该文件消除提示。

## 尚未签核

- 新增的 Native/Clear 配对只覆盖 24 小时初始本地正午相机上的 ACTIVITY CREATE。非网格相机与不同缩放下的吸附、四种类型分别从时间轴相机进入并配对、关闭/恢复组合、Studio 修改包运行、长时稳定性及实体/OEM 仍 **NOT TESTED**。
- Month 选日 ACTIVITY CREATE 已在独立测试中通过 API 35 配对；SEP-02 与 SEP-04 其余功能仍需逐项等价验证，不能以本 Issue 的窄切片签核母项。

## 2026-10-31 Native/Clear 时间轴相机 ACTIVITY 配对

- 在两个隔离 Store 中经 Native 时间轴新增菜单和 APK Clear 时间轴创建入口创建同标题 ACTIVITY。两个初始相机均为系统时区当地 2026-10-31 12:00、24 小时/屏；两路 `TimelineCoordinate.snap` 结果为 12:00，起始字段一致，结束建议均为 13:00。Native 通过 Compose 实际点击；Clear 的创建入口、类型选择、保存由 Android `SOURCE_TOUCHSCREEN` 注入且 `event.isTrusted=true`，Host 确认由 Compose 实际点击。
- 编辑与 Host 确认前两边 Store 均无写入；批准后 `DraftForm`、eventSeq=1、revision=1、commit=1、identity=1、next sequence=2 一致，draft/pending=0。API 35 `emulator-5570` focused **1/1 PASS**，整个 `TimelineCreateContextWebViewInstrumentedTest` **4/4 PASS**。
- 全量离线门禁 **785/785 JVM / 125 suites / 0 failures/errors/skips / 171 tasks PASS**，含 Native/Skin boundaries、Debug/AndroidTest APK 和 lint。class log SHA-256 `3B0B161F5FC5EF29E2507444D9190EC472FABF7D107D5D1B3AEEE69EA8F5276AF64E54C786`；full gate `76A87374C490B1F6BE2A708C2A33F336B398F80D966468B8A4B3999C65C9C0DF`；focused logcat `ED7D7515B6F55320B0C36060162BEEC755E25E1775C3FC2E30A1598A3E0D625B`；class XML `6DC643C6C6CE73D7AB8654F0363DBA3C2DFC2B18AAFB3D942CD0FD36CB419675`。Debug / AndroidTest APK SHA-256 `DE876D09D11689F98131C9DAD0A977AA1271E1657612B6A95DB89ABBBB80C31C` / `356993EBA0CF56CE2A201A7FB26485A54CEE59BC59FC5B5B36137B0C39BA79ED`。
- 日志与设备追踪在 `.local/android-test/reports/sep04-timeline-create-parity-20261031/`。测试提交 `76feaa4` 已推送。Core/Host/Clear 生产代码均未修改；因为相机固定在正午，非网格吸附行为不由本配对证明。
