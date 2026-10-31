# PC-ISSUE-SEPARATION-CLEAR-ICS-EXPORT

**状态：** Clear Settings 的 ICS 导出 happy path `PASS`；未复现运行时缺陷；SEP-11 整体仍为 `PARTIAL / HOLD`
**日期：** 2026-10-31
**测试提交：** `7a7af24`
**范围：** Clear Settings → Host `calendar.export.ics` → Android DocumentsUI → ICS 文件内容。

## 调查结论

Native Settings 和 Clear Settings 已共用 Host 的 `exportIcs` `CreateDocument("text/calendar")` launcher。Clear 的 `invokeOption(calendar.export.ics)` 与 Native `onExportIcs` 最终进入同一回调：读取 Host Store 投影、筛选 `ACTIVITY`，调用 `IcsCodec.exportActivities`，再通过 `ContentResolver` 写入 SAF URI。现有实现符合冻结的 ICS 精度边界；此前缺少 Clear 经真实 Android Save picker 写出文件并核对 payload 的设备证据，因此本 Issue 关闭的是验证缺口，没有改动生产导出逻辑。

## 回归覆盖

新增 `ClearSettingsOptionsInstrumentedTest#clearIcsExportUsesDocumentsUiAndContainsOnlyActivitySnapshots`。测试以 UUID 数据库写入 5 个正式事件：2 个 ACTIVITY（其中一个停用且配置 15 分钟提醒）、1 个 ALARM、1 个日期型 DEADLINE、1 个无日期 MEMO。随后从 Clear Settings 点击 ICS 操作，在实际 Android DocumentsUI 选择 Downloads 并保存 UUID 文件。

实际文件通过生产 `IcsCodec.parse` 解析且无警告；只包含两条 ACTIVITY，包含停用 ACTIVITY，不含其他三种事件或 `VALARM`。同时验证 VCALENDAR 首尾、VEVENT 数量，以及导出前后 Store 快照、待办数、提交数、身份数和序号水位完全一致。测试只删除自己创建的 UUID Downloads 文件与 UUID 数据库，并恢复皮肤选择和 onboarding 偏好。

## 测试证据

- API 35 Android 15 / WebView 124.0.6367.219，AVD `PowerCal_API35`，serial `emulator-5570`。Focused ICS 用例 **1/1 PASS**；`ClearSettingsOptionsInstrumentedTest` 全类 **7/7 PASS**。命令：

  ```powershell
  E:\android-sdk\platform-tools\adb.exe -s emulator-5570 shell am instrument -w -e class 'dev.powerfulcalendar.ui.v2.ClearSettingsOptionsInstrumentedTest#clearIcsExportUsesDocumentsUiAndContainsOnlyActivitySnapshots' dev.powerfulcalendar.test/androidx.test.runner.AndroidJUnitRunner
  E:\android-sdk\platform-tools\adb.exe -s emulator-5570 shell am instrument -w -e class dev.powerfulcalendar.ui.v2.ClearSettingsOptionsInstrumentedTest dev.powerfulcalendar.test/androidx.test.runner.AndroidJUnitRunner
  ```

- Skin Studio Python：`python -m unittest discover -s tools/skin-studio/tests -v`，**63/63 PASS**。Browser E2E：`node tools/skin-studio/tests/e2e_v3_source.cjs` 完成 source/project/resource round-trip，并输出 `BROWSER ICS EXPORT OPTION MOCK PASS`；Mock 明确不创建文件、不读取真实事件。
- 完整离线 Gradle 门禁：

  ```powershell
  .\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --max-workers=1 --console=plain
  ```

  **784/784 JVM、125 suites、0 failures/errors/skips、171/171 tasks 成功**；Lint **0 errors / 55 warnings**。此 worktree 未提交 `local.properties`，首次编译尝试因 Android SDK 环境变量缺失在项目配置前退出；随后在命令环境显式设置 `ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 为 `E:\android-sdk`，以上完整门禁通过。
- 最终 Debug APK SHA-256：`D650686340B54FE683A7262BDF4DBEFDAC0DC0BE3CCD320AAB46D700D5A5AB26`；AndroidTest APK SHA-256：`C230A0B0BEEA01DE4AB0FF932E2AB81839ED427E1650B1BB3E0A700355DDECDB`。
- Clear canonical package/APK asset：9 files、395,556 bytes，SHA-256 `7c2cbbea0774df21bb8a23fba622663a886367583d6da13e5fa12ef0f297b37`，`build_default_clear.py --verify --apk` 确认相同。
- 详细证据目录：`.local/android-test/reports/sep11-clear-ics-export-20261031/`。Focused log SHA-256 `713F62CD92DD5C1E35922208874F5653180D1FC63AB6D047B65441287A1B643D`；Settings class log `F7000999235AA3A151C62C6F140CFD099667274BE87E53D52C909E9F15A57752`；Gradle log `BAF6E191AD117DE7E2F09DB05D4D1AF7F236E68B49B29361B219F38C5648623E`；Studio Python log `0548CEAEE86B9A18FAD3A8989589460925844382EACA1FFBBF22562B6EF14230`；Browser E2E log `73C72F1BD66A774083520E59CC36A6DBFA2B82DF3FC01C805C4D9D081601A561`。
- 后置扫描：UUID 数据库残留 0、Downloads 测试文件残留 0、最近 1,200 行 logcat 中 fatal/未捕获 JS 错误标记 0；记录在 `post-run-checks.txt`。

## 未覆盖范围

本测试只证明 API 35 仿真器中的 ICS 导出成功路径和 Skin Studio Mock 可达性。Native 单独触控回放、ICS picker 取消/provider 写入错误、导入、皮肤包导出、系统日历权限授权/拒绝、完整 Native/Skin 对照、TalkBack、实体/OEM 仍未验证。SEP-11 保持 `PARTIAL / HOLD / INCOMPLETE`，本 Issue 不构成整体 Skin-First 或 cutover 签核。
