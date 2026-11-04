# PowerCal Functional Preview

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
