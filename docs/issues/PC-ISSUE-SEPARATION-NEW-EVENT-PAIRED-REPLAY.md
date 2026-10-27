# PC-ISSUE-SEPARATION-NEW-EVENT-PAIRED-REPLAY

- **状态：** 本次新建事件 Native/Skin 配对回放范围 PASS；SEP-04 完整 parity 仍为 NOT TESTED。
- **日期：** 2026-10-28
- **分支：** codex/separation-skin-settings
- **实现提交：** `ee18be116aeca17914a160db6fbab33ffc60f12e` (`test(分离): 补齐四类新建事件双轨回放`)
- **目标：** 用相同固定时钟、时区和选择输入，比较 Native 与 Skin 新建四类事件的默认表单、零差异退出、正式发布和有效草稿暂存结果。

## 根因与修复

Skin 新建服务直接调用 `NewEventEditSession.open`，但没有可注入的 Clock。测试无法将 Native 与 Skin 的默认时间固定在同一瞬间，只能比较不稳定的系统时间；这会掩盖入口默认值差异，也使可复现回放困难。

`WebSkinEditService` 现在接受可注入的 `Clock`，默认仍为 `Clock.systemUTC()`，并将其用于 Skin 的新建会话。生产默认行为保持原样；测试可以与 Native 使用同一个固定 Clock。新增 JVM/Robolectric 配对回放，并在 Studio 的真实 Chrome 预览 E2E 中逐一走过四类新建选择器。

## 回放方法及结果

新增测试 ID：`WebSkinEditBridgeTest.nativeAndSkinNewEventReplayMatchesDefaultsAndDurableBusinessResults`。

- 使用固定时刻 `2026-10-28T15:07:42Z`、`America/Los_Angeles` 时区；Native 与 Skin 同时验证 ALARM、ACTIVITY、DEADLINE、MEMO 的默认表单完全相同。
- 对每种类型重复打开/关闭 20 次（共 80 次每条路径）。比较草稿数、待处理数、正式事件数、identity 数、下一永久序号、下一队列顺序和 commit 数；均无变化。重复 Skin 关闭也保持幂等。
- 回放 8 个正式事件：四种类型分别使用普通默认值和明确选择值。Alarm、Activity、Deadline 使用同一显式 Instant，Memo 使用同一显式日期。Native 走 `NewEventEditSession.save`；Skin 走 `deck.create/patch/commit` 与 Host 正式写入审查批准。持久化业务记录和计数一致。
- 对不完整但有意义的 Deadline 备注分别执行 Native 退出暂存和 Skin 关闭暂存；都只产生一张相同业务内容的待处理草稿，正式记录不变。
- 比较正式记录和草稿时，仅将自动生成的 `createdAtMillis/updatedAtMillis` 审计时间归一化；ID、类型、时间字段、标题、备注、版本及所有其它字段仍逐项相等。
- Studio E2E 在 Chrome 中点击真实 Clear 新建选择器的四个选项，检查每个编辑器种类，并确认 Studio Mock 收到 `deck.create` 回执 `7001`。该项只证明 Studio 预览命令接线，不代替 Android WebView/设备测试。

## 测试证据

| 检查 | 结果 |
| --- | --- |
| 定向 Native/Skin 配对回放 | PASS；Gradle 测试日志见下方证据路径 |
| 完整 Android 离线门禁 | PASS；source boundary、Debug APK、AndroidTest APK、Debug lint、608/608 JVM 测试，85 个测试套件，0 failures/errors/skips，88/88 tasks |
| lint | PASS；0 errors，52 warnings |
| Skin Studio Python | PASS；51/51 |
| Studio Chrome E2E | PASS；源码往返、设置 Mock、四类新建 Mock 及既有 recurrence/AI/review/Host-confirm 检查通过 |
| Clear canonical bundle | PASS；8 个文件、379111 bytes，SHA-256 `DBE04D79DA759ADAC4A30054427956163BE963EF317DE0BF75D289C53AEF340A` |
| JavaScript 语法 | PASS；`node --check tools/skin-studio/tests/e2e_v3_source.cjs` |
| 设备验证 | NOT TESTED；本次没有 Android 模拟器或实体设备运行 |

完整门禁命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

Studio 命令：

```powershell
node tools/skin-studio/tests/e2e_v3_source.cjs
python -m unittest discover -s tools/skin-studio/tests -v
python tools/skin-studio/build_default_clear.py --verify
node --check tools/skin-studio/tests/e2e_v3_source.cjs
```

环境：Windows PowerShell、JDK 20（`C:\Program Files\Java\jdk-20`）、仓库 Android SDK `E:\android-sdk`，Gradle 离线执行。JVM 测试结果来自 Robolectric/JUnit；Chrome 测试使用 Studio Mock。没有将其表述为模拟器、Android WebView 或实体设备验收。

## 可追溯证据

本机忽略目录（不提交原始运行日志）：

- 定向测试：`E:\Project\Powerful-Calendar\.local\android-test\reports\separation-native-skin-replay-20261028\focused-native-skin-replay-final.log`
  - SHA-256：`A56F25831D0D8450DD07906E4044D4B4A495E4487F87094F32328EBB61AE5AE4`
- Studio Chrome E2E：`...\studio-chooser-e2e-final4.log`
  - SHA-256：`360370B89A561A1FECD4823E164FEF0A5707AF68272BEC1CFF894DEBF201CB641`
- 完整 Gradle 门禁：`...\full-gate-captured.log`
  - SHA-256：`79C68A8122880AA25A35E892E93848586BA66C27FB2611C29EE040846975928B`
- Studio Python：`...\studio-python-tests.log`
  - SHA-256：`9BEFE164D821043B6576BE05B806B6967A33C6A07BC63B0D6E77FA5635F84474`
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
  - SHA-256：`869AD65D0E00C1C37CE39042E2E946115C02D498B993B7821777DF3B8839845C`
- AndroidTest APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
  - SHA-256：`FD52D9A141D801274FA4E58E1F7B20F113525CE3A8117A92D76DA41A88619BF9`

第一次配对测试比较发现两条路径自动生成的草稿创建/更新时间相差 7ms，业务字段和写入计数相同。测试随后只对这两个审计时间字段归一化，并通过；没有忽略任何业务字段。Studio E2E 的早期尝试出现预览 DOM 时序、读取错误 frame 和已有 capability 断言波动，最终完整 E2E 重跑通过。上述早期失败没有记作 PASS。

## 影响与限制

本次只增加 Skin 服务的 Clock 注入点和回归证据，不改变数据库格式、Host 授权、正式事件规则、提醒规则或界面。实际手机交互、Android WebView 设备行为、OEM 差异，以及 SEP-04 的完整时间轴/月视图/Native-Skin 功能 parity 仍需后续验证。SEP-04 总行继续为 NOT TESTED；本报告的 PASS 仅覆盖上述配对回放与 Studio 选择器 Mock。
