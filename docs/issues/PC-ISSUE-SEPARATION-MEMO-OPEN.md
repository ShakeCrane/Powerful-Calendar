# PC-ISSUE-SEPARATION-MEMO-OPEN — Clear Memo 行打开正式编辑器

日期：2026-10-29（测试执行：2026-10-28）

阶段：S0 具体交互修复；源代码、Host bridge 和 Chrome Mock 回归通过。Android WebView 完整链路与 Native/Skin 配对等价仍 **NOT TESTED**。

## 结论

Clear 皮肤的 Memo 列表原先只能通过独立控件切换完成状态，点击标题不会打开已有正式事件编辑器。现已为标题加入可访问的按钮和 openEvent Host action，并从 Memo 投影传递重复实例的原始 occurrence key。完成状态切换仍走独立 toggleMemo action。Host 继续通过正式事件读取、revision 校验和 WebSkinEditService / EditSession 打开编辑流程；Skin 不获得额外写权限。

本 Issue 的代码缺口已修复，并由 Host/JVM 与浏览器 Mock 分段回归。由于 ADB daemon 无法启动且本机无 emulator 可执行文件，尚未证明打包 Clear 在 Android WebView 内从真实 Memo 行贯通到真实 Host 的端到端行为。因此此 Issue 对“源码及 Host 路由缺失”结案；S0-ME-03 与父项 SEP-03 的完整 parity 签核继续 **NOT TESTED**，分离阶段不因此关闭。

## 根因与修复映射

| 根因 | 修复 | 回归证据 |
| --- | --- | --- |
| PowerCalApp 的 Memo projection 没有给正式编辑器提供原始 occurrence slot。 | 新增 WebSkinMemoProjection，保留 completion 的既有 occurrenceKey；仅重复事项增加独立 originalOccurrenceKey，单次事项为 null。 | WebSkinHostProjectionTest：重复 Memo 精确槽位及单次 Memo 的两个 key 相互独立。 |
| Clear Memo 标题是普通文本，没有触发正式事件编辑命令。 | app.js 将标题渲染为带 aria-label、eventSeq、revision 与可选 occurrence key 的 openEvent 按钮。 | Chrome/CDP 回归验证实际 action payload、MEMO editor 和选择日期。 |
| 如果复用 completion key 作为编辑槽位，单次 Memo 的 "single" 哨兵会被误当真实 recurrence slot，或标题点击会与完成操作耦合。 | 标题编辑和 toggleMemo 使用各自独立按钮/action/key；Host 仍校验版本并从 Core 解析 occurrence。 | 单次 projection 回归、重复槽位 Bridge 回归、浏览器断言 completionRemainsSeparate=true。 |
| 标题从普通文本改成按钮后可能造成 CSS/点击命中回归。 | checkbox 规则限定到 [data-action=toggleMemo]；标题有独立 focus 样式。 | Clear Chrome 页面回归与节点语法校验。 |

Host Bridge 不向皮肤公开内部 occurrenceKey 状态字段。正式编辑会话所用 slot 通过 Host checkpoint 恢复验证；没有扩大皮肤可见状态或修改冻结 Core。单次 occurrence 的现有编辑器规则继续隐藏类型转换选项。

## 修改文件

- app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt：改为委托 Memo JSON projection。
- app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinMemoProjection.kt：新增纯 projection，保留/区分完成 key 与编辑槽位 key。
- app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinHostProjectionTest.kt：新增重复与单次 Memo projection 回归。
- app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinRecurrenceBridgeTest.kt：验证精确 occurrence 槽位打开、选择单次范围、修改后 checkpoint 恢复，且未正式写入或增加提交记录。
- tools/skin-studio/default-clear/source/web/app.js：接通可访问标题按钮及 openEvent payload。
- tools/skin-studio/default-clear/source/web/style.css：分离完成 checkbox 与标题按钮样式。
- tools/skin-studio/v3-studio.js、tools/skin-studio/tests/e2e_v3_source.cjs：增加重复 Memo Mock 与浏览器交互断言。
- app/src/main/assets/powercal-clear-default.pcskin：从规范 Clear source 重新生成的 APK 资源包。

## 测试与证据

以下哈希均为本机留存原始日志/产物的 SHA-256；详细文件位于仓库忽略目录 .local/android-test/reports/separation-memo-open/。测试没有使用真实日历数据或真实 AI provider。

验证环境：Windows PowerShell；Gradle 8.14.3（离线）；JDK 20；Android SDK 位于 E:\android-sdk；Python 3.11.4；Node.js 24.19.0。ADB daemon 无法连接，未发现 emulator.exe。

| 检查 | 结果 | 可追溯证据 |
| --- | --- | --- |
| 定向 Host/Bridge JVM：WebSkinRecurrenceBridgeTest + WebSkinHostProjectionTest | **23/23 PASS**，0 failure/error。覆盖 repeating Memo 槽位、单次 key 分离、revision/编辑服务路由和恢复 checkpoint。 | gradle-focused-memo-host-final.log SHA-256 6E53E58FA3C13F44F548A5B05CF0EB85DC71D4FBC24F644DB339DC5FE6DC173D |
| Skin Studio Python 全套：python -m unittest discover -s tools/skin-studio/tests -v | **51/51 PASS**。 | studio-unittest.log SHA-256 68DBA6884722ECE4BC4391A2F4A8BD11E8041B32ABD8552D91DECEC65BF30028 |
| Chrome/CDP Studio/skin Mock E2E（e2e_v3_source.cjs） | **Memo 打开及完成切换分离 PASS**。浏览器结果：eventSeq=31、revision=2、originalOccurrenceKey=2026-10-22T00:00、editorKind=MEMO、selectedDate=2026-10-22、completionRemainsSeparate=true。同日志还包含源码 round-trip、recurrence、AI Mock 回归 PASS；provider 和正式日历写入均未执行。 | chrome-e2e-final2.log SHA-256 9EA4EA8C13465832E19CE843B210116E9AFA4FDAF8652E3329CDE227BA29703C |
| Clear package 从规范源码构建及一致性验证：python tools/skin-studio/build_default_clear.py --verify | **PASS**，8 files / 378,086 bytes；package SHA-256 711e1ad695b47e5d05eea3db1761e933e444f608f9acf91619c6915423c8b45a。 | 命令输出；生成资产 app/src/main/assets/powercal-clear-default.pcskin |
| JavaScript 语法：node --check on Clear app.js、v3-studio.js、e2e_v3_source.cjs | **3/3 PASS**。 | 终端退出码 0；语法检查无独立持久日志。 |
| 完整 Gradle：:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain | **BUILD SUCCESSFUL**；603/603 JVM tests、83 suites、0 failures/errors/skipped，59/59 tasks；source boundary、Debug APK、lint 通过。Lint 为 0 errors / 52 warnings。 | gradle-full-final.log SHA-256 B396DD2AD012B0839D7A69CEDA0C2A3E0BB2A85B9640F0FD1AC6057CAB530A87；Debug APK SHA-256 BBFCEC10BD012910D9035EB624C22ECC029087E9424DCAE8CB90B75337E7C697 |
| AndroidTest APK 编译：:app:assembleDebugAndroidTest --offline --no-daemon --console=plain | **BUILD SUCCESSFUL**；只证明 instrumentation APK 编译，不代表设备执行。 | gradle-androidtest-assemble.log SHA-256 85435A546A9233D99BAD3FDAD836890698BD22B3BC322C9730C869B4333255D1；APK SHA-256 60F08FFC420D8C67860FE9D85938C5237CF3C48CD49D5EF2C60DC1187C5C9B90 |
| Android WebView / 模拟器 / 实体设备 | **NOT TESTED**。adb devices -l 无法启动/连接 ADB daemon（could not read ok from ADB Server），并且仓库机器上未发现 emulator.exe。 | 环境阻塞记录；Chrome Mock 和 Android APK 编译不替代此项。实体设备由用户执行。 |

## 验证中纠正的测试假设

- 一版 Bridge 回归错误地要求皮肤 state snapshot 暴露内部 occurrenceKey。该状态不应越过 Host 信任边界；测试改为通过实际编辑和持久化 recovery checkpoint 核实槽位保持。最终 Bridge/Projection 定向组 23/23 PASS。
- 一版浏览器回归试图读取 SINGLE_OCCURRENCE 下已按现有语义隐藏的类型切换字段。测试改为验证 MEMO subtitle、实例日期和类型字段隐藏，最终 Memo action 断言通过。
- 最初受限权限的 Studio 测试尝试遇到 loopback/temp-file 权限错误，且当时 Clear 资产尚未重建；在正确本地测试权限下重建包并重跑后，完整 Python 套件 51/51 PASS。初始失败不计为产品 PASS，相关过程日志仍留存在 .local/android-test/reports/separation-memo-open/。

## 未解决边界与阶段状态

- 尚无 Android WebView 内 Clear Memo row → WebMessagePort → Android Host → 正式编辑器的端到端运行证据；没有实体设备/OEM 验收。
- 没有完成冻结 Native 与 Clear 的双版本完整回放；因此 S0-ME-03 的 Android runtime parity 和父级 SEP-03 保持 **NOT TESTED**。
- S0 全面调查仍未结束：Memo 分页/250 条截断、stale revision/retry/restart 全链路、其他 Memo 操作、其他 61 个重建项、完整双轨等价回放尚未闭环。不能以本 Issue 关闭 S0 或宣布 Skin separation GO。
- 本次没有修改 Core 业务语义、事件数据、权限或 Native fallback；没有做 factory skin cutover。

后续提交由 Git 记录；此报告随修复提交。APK 与完整测试结果只作为当前源码快照证据，提交 SHA 将在提交后由进度记录及最终交付报告注明。
