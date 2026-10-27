# Bridge v1 皮肤能力声明语义与兼容审计

日期：2026-10-27

状态：**Bridge v1 的能力语义已完成源码审计、文档澄清和 JVM 回归；没有扩大 Host 权限。模拟器及设备证据 NOT TESTED，皮肤全量功能等价仍未签核。**

## 发现与根因

v3 包解析器接受 `host.read`、`host.action`、`host.edit` 三种声明，Studio 会在导入、预览和导出时保留声明。实际 Android Bridge v1 的有效权限只有两种：

- `WebSkinCapabilityPolicy.mayRead` 仅在存在 `host.read` 时开放状态投影；否则只返回 bridge 版本。
- `WebSkinCapabilityPolicy.mayAct` 仅在存在 `host.action` 时允许 Host 处理入站消息。该 gate 位于动作消息和 `WebEditWire` 编辑/草稿命令解码之前，因此编辑命令也要求 `host.action`。
- `host.edit` 虽被解析和保存，但没有独立的运行时检查点；单独声明不授予读状态或发送 Host 命令的能力。

问题来自 manifest schema 暴露了 `host.edit`，但 Bridge v1 没有实现独立的编辑能力语义。直接把 `host.edit` 加入 Host 授权会扩大现有第三方包权限并改变 Bridge ABI；而 capability 只允许请求进入 Host 验证，不等于用户批准正式写入。因此本次不改变权限门禁，按兼容元数据明确记录该字段的现状。若未来需要独立编辑权限，应通过版本化策略和旧包兼容测试迁移。

## 修改及根因对应

- `WebSkinHost.kt` 注释说明 Bridge v1 的 `host.read` / `host.action` 实际边界和 `host.edit` 的兼容性、无授权语义。
- `SkinPackageManager.kt` 注释说明解析器保留 `host.edit` 是为了 v3 包往返，不代表 runtime grant。
- `SkinPackageManagerTest` 在既有 capability policy 测试中加入 edit-only 与 read+edit 场景：前者不能读取或执行动作；后者只可读取，不能执行动作。既有无权限、只读和只动作断言继续保留。
- v3 示例 manifest 移除未生效的 `host.edit`，避免示例向皮肤作者暗示它是独立有效权限；解析器仍兼容并保留已有第三方包中的该字段。
- 更新 `SKIN_PACK_V3.md`、`V3_HOST_BRIDGE_EXTENSION.md`、`SKIN_RUNTIME_PARITY_V1.md`、Studio README 与 parity matrix，统一记录当前契约和后续审计边界。
- 没有更改 Host 的授权实现、事件写入路径、核心数据或用户包；没有把 Studio Mock 作为 runtime 权限证据。

## 测试与可追溯证据

### 定向 Android JVM

执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --tests dev.powerfulcalendar.ui.v2.SkinPackageManagerTest --no-daemon --console=plain
```

结果：**9/9 PASS**。日志位于 `.local/android-test/reports/separation-20261027/capability-audit/capability-focused-jvm.log`，SHA-256 `AE0BF5B63F26DC8DC0A8E1EB9C6276790B9F2EDBAC9A3CDBBCB179EEC2BF9179`。定向 XML SHA-256 `EAEC12586A7AC78D75B7F8847A0BF65DD804C561A1C580B601ACA230F899D01F`。

### 完整 Gradle 门禁

执行：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

结果：**574/574 JVM 测试、80 suites、0 failures / 0 errors / 0 skipped、88/88 tasks**。Source boundary、Debug APK、AndroidTest APK 和 `lintDebug` 均通过。完整日志 `.local/android-test/reports/separation-20261027/capability-audit/capability-full-gradle.log` SHA-256 `9C6B4C7AF3B6026D40CD5E4513BE8E5885A6CA86598EB17B9A8C2824458BC61C`。

本次生成的 Debug APK SHA-256：

- `app/build/outputs/apk/debug/app-debug.apk`：`5387C51C17A7CB35C7448DB6F1AEE8DE8638064B9B5760229761800670A79AE8`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`：`219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6`

Gradle 输出了现存 Kotlin/Java deprecation 与类型推导警告；它们未阻断本次构建，且不属于本 Issue 修复范围。

### Studio、包工具与 Clear 资产

- `python -m unittest discover -s tools/skin-studio/tests -v`：**42/42 PASS**。日志 `.local/android-test/reports/separation-20261027/capability-audit/capability-studio-full-final.log` SHA-256 `AC151E0E67DA7F5EDA7D4DB817CC977FD56C94745168B88268D3063ECE8B2EA4`。
- `test_v3_source_editing.py`：**3/3 PASS**，其中覆盖 capabilities 导入/导出不被静默增删；日志 SHA-256 `03ED5C4F4F02B87840ADF34B2895EEBDBA46DD0BB68475478EFE7E792C14D11B`。
- `test_runtime_v3.py`：**3/3 PASS**，其中覆盖缺失声明不给 Host 权限；日志 SHA-256 `AB1F8927DF0F907868D1202F4CF2CD1CE90E43E8C7BB5E916B8380A4EBD9A380`。
- `python tools/skin-studio/build_default_clear.py --verify`：PASS；规范 Clear 包 8 个文件、373,663 bytes，SHA-256 `2283ae998c43927c627e1b67dbfa5924b189e0e20ec8c24dd032ae89688d0939`。日志 SHA-256 `B161E31382DAA95001A8763B3B1A63E5D0AED3A154235E0632AB85A5E88CC322`。
- `node --check tools/skin-studio/v3-studio.js`：PASS。v3 demo `.pcskin` 已构建并通过包校验，能力仅为 `host.read,host.action`，产物保存在忽略目录 `.local/android-test/reports/separation-20261027/capability-audit/host-capability-demo.pcskin`。

上述 Studio / CLI 证据证明包元数据和工具行为，不证明 Android WebView 的实际运行时或 Native/Skin 业务等价。

## 未测试项与风险边界

- 本轮没有 Android WebView、模拟器或实体设备测试。现场检查时 `adb devices -l` 无法启动本机 ADB daemon，且未发现 `emulator.exe`；因此设备状态标为 **NOT TESTED**，不能用 JVM 或 Chrome Mock 替代。
- SEP-13 的工厂/第三方皮肤 Android runtime 及 SEP-16 完整 Native/Skin parity 仍为 **NOT TESTED**。单条 capability policy 测试不覆盖恶意包全路径、WebView 生命周期、状态刷新、写入审批、恢复和四类事项完整对照。
- 当前 Bridge v1 仍要求 `host.action` 才能发送编辑命令；`host.edit` 暂时是保留字段。未来如为其赋予独立权限，必须先定义版本化 ABI、旧包迁移和负向授权测试。
- 本轮没有运行真实 AI provider，不涉及凭据或付费调用。

## Git 交付

源码、测试和示例变更已单独提交：`394f7e13d23f98fb018428f8b44d47c6a9a0e0e7`（`fix(skin): 明确 v3 能力声明边界`）。本报告、PROGRESS 与 parity matrix 随后作为独立中文文档提交，两个提交一起推送至 `origin/feat/skin-first-separation`。工作树中既有的 Clear Studio 资产改动未纳入提交并继续保留。

本 Issue 不代表 UI/皮肤分离完成，不改变核心冻结范围，也不授予默认皮肤切换或 Native fallback 移除许可。
