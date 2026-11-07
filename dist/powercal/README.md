# PowerCal Functional Preview

## Latest package — 2026-11-07

- APK: PowerCal-Functional-Preview-2026-11-07-742ffbbd.apk
- SHA-256: 1DA53F916AE924C1583A2472CB10820754A7E4BD21E6EEBE433642505940D54A
- Build checkout: 742ffbbdcd98e3763fd59c1e917088bae4f718e6; app code: 5a267814ca83a201f4d2c3a1c3df357c1d35c1a0
- Package: dev.powerfulcalendar / 0.9.5-rc.1 / 905 / Debug
- Default package: one powercal-clear-default.pcskin; SHA-256 13a6efc6af0e68a8d91c7786ac3bd0264dea630fe4880feefaa39251be869ffa
- Package details and limitations: [2026-11-07 preview report](PowerCal-Functional-Preview-2026-11-07-742ffbbd.md).

This is an internal preview. The current default Skin is provisional and is not signed off as the final Native 0.9.5 visual migration. Full main-flow smoke was not repeated for this refreshed APK; Product Cutover remains HOLD.

## Previous functional preview — 2026-11-07

- APK: PowerCal-Functional-Preview-2026-11-07-05514a12.apk
- SHA-256: CF310827CA4C3A7989DB79A5D35F6F2755F2AD01E7601D28B40CA271C74492CB
- Its original smoke-test record remains in [PowerCal Functional Preview 2026-11-07](PowerCal-Functional-Preview-2026-11-07.md).

---

## Historical build — 2026-11-05

内部功能预览 APK，构建于 **2026-11-05**。用于隔离测试和试用，不代表跨 OEM 或正式发行签核。

- APK：`PowerCal-Functional-Preview-2026-11-05.apk`
- SHA-256：`2ff316e1e350fdd4573ceac7ab154b616fb2e425ef209bde0d2b3e79d89b33a6`
- 包名：`dev.powerfulcalendar`
- 版本：`0.9.5-rc.1`（versionCode `905`）
- 构建类型：Debug
- 源码构建基线：`96fc4bde3836724d1de88c36aa7de5e3699a8dd1`
- 默认 Skin：`powercal-clear-default`（APK 中唯一 `.pcskin`）
- 默认 Skin SHA-256：`b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3`

## 安装

在 Android SDK Platform Tools 可用的电脑上，连接隔离测试设备后运行：

```powershell
adb -s <设备序列号> install -r .\PowerCal-Functional-Preview-2026-11-05.apk
adb -s <设备序列号> shell monkey -p dev.powerfulcalendar 1
```

从本目录执行以上命令，或将 APK 路径替换为实际位置。该 Debug APK 使用工程调试签名；若设备上安装了不同签名的正式包，Android 不允许覆盖更新。不要为了安装预览包而清除正式应用数据。

## 本次验证

- 串行 Gradle 门禁：源码边界、Native 边界、JVM、APK、Lint、AndroidTest Kotlin 编译通过。JVM XML 汇总为 970 项，0 失败、0 错误、0 跳过。
- API 35 隔离 AVD `PowerCal_Wave2_QA35_20261104` / `emulator-5554`：本次构建 APK 完成 11 项定向 instrumentation 断言，覆盖四类事件创建与编辑、月历和重复实例、Memo 草稿、AI 对话与显式提案批准、Settings、强制进程冷启动恢复；0 失败。
- App 冷启动后实际显示默认 Clear 的 WebView 时间轴。
- 默认包官方校验：源码包与 APK 资源 SHA-256 相同，APK 内恰好一份 `.pcskin`。
- 详细日志、截图及层级文件：`.local/android-test/reports/functional-preview-20261105/`。

## 当前限制

- TalkBack 焦点遍历顺序仍为 **NOT TESTED**；此前 ADB 手势无法作为有效焦点遍历验证器。
- 真机/OEM 提醒投递与不同 ROM 后台行为未测试。
- 真实 AI 服务商连通性未测试；本次 AI 流程使用确定性测试 Stub。
- 本 APK 是 Debug 内部预览，不是公开发行版。Product Cutover 仍为 HOLD。

## PowerCal Release Candidate

此 RC 是 R-07 单一 Web Runtime 启动与 Native 独立恢复验证后的内部 Debug 包。它不代表正式发行或跨 OEM 签核。

- APK：`PowerCal-Release-Candidate-2026-11-05.apk`
- SHA-256：`EC27EBE7A85FD117CB83F846F26CD4D6A374D4908F09001FC051231A8592C340`
- 大小：`67,060,154` bytes
- 包名：`dev.powerfulcalendar`
- 版本：`0.9.5-rc.1`（versionCode `905`）
- 构建类型：Debug
- 构建源码提交：`b71cf0eedbd6dba9dafe0d9215ab33457258d045`
- 默认 Skin：`powercal-clear-default`；APK 中仅一份 `.pcskin`
- 默认 Skin SHA-256：`b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3`（与源工程构建结果一致）

安装到隔离测试设备：

```powershell
adb -s <设备序列号> install -r .\PowerCal-Release-Candidate-2026-11-05.apk
adb -s <设备序列号> shell monkey -p dev.powerfulcalendar 1
```

该 Debug 包使用工程调试签名。若设备装有不同签名的正式版本，Android 不允许覆盖安装；不要为安装 RC 清除正式应用数据。已验证设备、R-07 恢复场景和构建证据见 `.local/android-test/reports/R-07-20261105/manifest.md`。

RC 的 API 35 隔离 AVD 验证覆盖默认启动、损坏/缺失包回退、进程恢复、Web renderer/脚本错误和紧急回退前保存编辑草稿。强制 checkpoint I/O 失败、TalkBack 焦点顺序、真机/OEM 提醒投递、实时 AI 服务商连接和历史 SEP 全量签核仍未完成。Product Cutover 仍为 HOLD。

## Checkpoint B 更新：Q-05 单一默认迁移 QA Release Candidate

该包基于受保护分支提交 `7197a907cacc875e0e8ecf4c662d2d550342a2f4`，已在隔离 API 35 AVD 验证新安装、旧 Native/第三方选择迁移、冷启动及损坏工厂包的 Native 恢复。默认包损坏时保留旧包文件并停留在 Native；原 `dev.powerfulcalendar` 安装未被清除、卸载或覆盖。

- APK：`PowerCal-Release-Candidate-Q05-QA-2026-11-05.apk`
- SHA-256：`08E096B994C9078666DB43732F78D11B52B3603E836763B51A789E39348BA455`
- 大小：`67,011,026` bytes
- 包名：`dev.powerfulcalendar.d05qaq05final`（隔离 QA suffix，可与原版并装）
- 版本：`0.9.5-rc.1`（versionCode `905`），Debug
- APK 内恰好一份 `.pcskin`；源码与 APK 默认包 SHA-256 均为 `b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3`

在 Android SDK Platform Tools 可用的电脑上安装并启动：

```powershell
adb -s <设备序列号> install -r .\PowerCal-Release-Candidate-Q05-QA-2026-11-05.apk
adb -s <设备序列号> shell monkey -p dev.powerfulcalendar.d05qaq05final 1
```

这是一份内部 QA Debug 包，不用于覆盖现有正式安装或公开发布。TalkBack 焦点遍历、真机/OEM 提醒投递、实时 AI 服务商与完整 SEP/SSA 签核仍未完成，Product Cutover 保持 HOLD。命令日志和验收证据位于 `.local/android-test/reports/Q-05-20261105/`。

## Checkpoint C：Visual Release Candidate（Clear 回退版）

没有找到独立、已批准的 Claude 图稿/素材。因此本检查点使用已验证的 Clear 视觉，不擅自生成新图形；**功能交付已有 APK，定制视觉整合仍未完成**。

- APK：`PowerCal-Visual-Release-Candidate-2026-11-05.apk`
- APK SHA-256：`66fa0941beda7ff5fce3dbbfa176d3152a002205faafaba5666e8ac0068617a3`
- 构建提交：`447f3b6f7c8e2586cb787faa97ba9d27b9730cb5`
- 默认包：`powercal-clear-default-Visual-Release-Candidate-2026-11-05.pcskin`
- 默认包 SHA-256：`b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3`
- Studio 工程：`tools/skin-studio/default-clear/`
- 预览截图：[`Visual-Release-Candidate-2026-11-05/`](Visual-Release-Candidate-2026-11-05/)，来自 API 35 隔离 AVD 的 Timeline、Month、Memo、AI、Settings 实际 WebView 页面

### 启动 Studio 并编辑

从仓库根目录启动：

```powershell
python tools/skin-studio/skin_studio.py serve --port 8765
```

打开 `http://127.0.0.1:8765`，选择“导入 .pcskin”，载入本目录中的 Clear 包；编辑后使用“导出 .pcskin v3”。直接维护默认 Clear 时，以 `tools/skin-studio/default-clear/` 为 Studio 工程。要把经审核的编辑装入 App，先同步到该规范源码，再执行：

```powershell
python tools/skin-studio/build_default_clear.py
python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk
```

随后重新构建、安装和验证 APK。App 只装载唯一 bundled factory skin；导出的 `.pcskin` 不会通过运行时皮肤目录自动启用。不要把导出包加入 APK 作为第二套出厂皮肤。

本 Visual RC 在 Android 15 / API 35 AVD `PowerCal_Wave2_QA35_20261104`、序列号 `emulator-5554` 安装运行。全任务重跑式 `:app:assembleDebug` 构建成功，正式皮肤构建器确认 APK 只有一份 `.pcskin` 且与源包逐字节相同；五个页面已实际打开并留图。此回退版本没有改默认外观。Host Compose Draft Review、TalkBack 焦点顺序、真机/OEM 提醒、实时 AI 服务商及完整 SEP 签核仍是独立缺口；Product Cutover 仍为 HOLD。详细构建和图片哈希见 `.local/android-test/reports/V-02-20261105/manifest.md`。
