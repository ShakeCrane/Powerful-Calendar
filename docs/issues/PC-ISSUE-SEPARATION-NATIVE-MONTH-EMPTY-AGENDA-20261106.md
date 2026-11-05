# PC-ISSUE-SEPARATION-NATIVE-MONTH-EMPTY-AGENDA-20261106

**状态：PASS（Month 无事件空状态排版与底部动作间距窄切片）**
**代码提交：** `24fbb376eb8b0ccf1825d70e0b4f7f68fceaa552`
**日期：** 2026-11-06

## 冻结参照与差异

Owner 冻结视觉基准为 Native PowerCal 0.9.5-rc.1。源码 SHA 为 `b1f7629f0c52812e1887443872f43aa6c49908c4`；原始 APK SHA-256 为 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。

`MonthScreen.kt` 对无事件日显示“这一天还很从容。”，采用 Material 3 `bodyLarge`（16sp/24sp，默认 0.5sp 字距）和 16dp 垂直 padding。LazyColumn 项间距为 12dp。旧 Web 页面继承了通用 `.quiet` 的 12px/三级标签色/14px padding，导致文字更小、更浅并且向右偏；同时空状态段落的 11px 底边距与 `#monthAgenda` 底 padding 4px、动作区 top padding 8px 叠加，使创建按钮比 Native 低约 30 个物理像素。

## 实现

在 `native-095.css` 中仅覆盖 Month agenda 的直接子级 `.quiet`：使用 onSurface 色、16px/24px、0.5px 字距、16px 垂直 padding；清除段落 margin，避免重复累加 Native 的列表间距；1px CSS 相对位移用于匹配实际栅格基线。正式包由 `build_default_clear.py` 从源码重建。扩展 Android instrumentation 断言文案、颜色、字号、行高、字距、padding、margin 与偏移，并截图 Native 和 Web 两条真实 Compose/WebView 路径。

## 实测

- 原始冻结 APK 与最新 Debug APK 在隔离 API 35 AVD `PowerCal_Native095_Parity_20261105` / `emulator-5580` 对照，分辨率 1080×2400、420 dpi，WebView 124.0.6367.219。选中日期均为 2026-11-05、日程为空。
- 冻结 Native 文案墨迹边界 `(34,1492)-(349,1531)`、中位 RGB `(18,22,29)`；最终 Web 为 `(33,1492)-(348,1531)`、`(17,21,28)`。墨迹边界仅横向左/右各差 1px，纵向一致；中位颜色每通道差 1。
- 创建按钮描边行 Native `1615..1617`、Web `1617..1619`，Web 仍低 2 个物理像素。空状态像素差异之外的容器布局不在本 Issue 签核范围；不能据此宣称 Month 页面全部等价。
- `monthYearMonthDateAndTodayTransitionsMatchWithoutMovingTimelineOrWritingStore` Android 仪器测试 `OK (1 test)`；它同时检查页面选择和 Calendar Store / Camera 不变量。
- 最终串行 Gradle 门禁 PASS；141 份 JVM XML、976 tests、0 failures/errors/skips；两项源码边界、APK 构建、Lint、AndroidTest 编译与组装 PASS。Month Chromium E2E PASS；JavaScript syntax PASS；默认分发 Python 测试 2/2 PASS；正式包验证 PASS。
- Debug APK SHA-256 `24F92FC77FE64BDA5CE15747234A1C3E221172713D6C67BEC7D21D0FE4FB6934`；设备上安装的 `base.apk` 哈希相同。APK 仅含一个 `powercal-clear-default.pcskin`；10 个文件、410057 bytes、SHA-256 `0C60CB77F99A7074698025630B49FA8CA00BB7ED583EC16B153FA3A09B1FC53A`，与正式构建器输出和源资源字节一致。

## 证据与边界

本地日志、原始/最终截图、XML 和可复算像素比较脚本：`.local/android-test/reports/native095-month-empty-20261106/`。主截图 SHA：冻结 Native `99F96C44646D2CFC3FAEE8427E376B0A4C4E0FC065D479DB89C027ED6E725249`；最终 Web MainActivity `88C5A28FFE5912AA5CF649249E11C7B5BC22B9C8E39E89D1810CB2ED0662B498`。仪器测试同日期 Native/Web 截图和其 SHA 另列在该目录 `manifest.md`。

本切片没有修改 Core、Host、Native 页面、业务数据、皮肤 ID 或恢复逻辑。Dense agenda、深色主题、Month 完整页面矩阵及其它 Native 页面仍未签核；Native 0.9.5 迁移保持 `IN_PROGRESS`，Product Cutover 保持 `HOLD`。
