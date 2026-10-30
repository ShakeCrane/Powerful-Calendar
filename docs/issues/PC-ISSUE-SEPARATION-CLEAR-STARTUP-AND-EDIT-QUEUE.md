# PC-ISSUE-SEPARATION-CLEAR-STARTUP-AND-EDIT-QUEUE

日期：2026-10-31（北京时间）

优先级：P1
状态：**本报告列出的 Clear API 35 路径 PASS；整体 Skin 功能等价仍未完成**

## 复现与根因

Memo 首次打开时，Web Host 偶尔已显示 `page=MEMO`，但 Memo 数量和条目仍为空。Store 已有正式 Memo；旧实现先更新 `records`，再依赖 Compose `remember(records, todayKey)` 计算 Memo 投影。Bridge 状态发布可能先于重组完成，导致首帧混合了已加载页面状态和旧的空投影。

Clear 也会在 WebMessagePort 或 Host 数据尚未就绪时直接丢弃点击/命令。编辑表单正在发送字段 patch 时，Save 处理器因 `busy` 直接返回，用户紧接着点击保存会丢失操作。

## 修复

- `PowerCalApp` 在同一次 Store 读取中生成正式记录与 Memo 投影；应用两者后才发布 `hostDataReady=true` 和 Bridge revision。日期变化后重算投影。首屏 Store 投影先于提醒核对，提醒核对完成后再刷新诊断；失败时保留已加载日历并提供可重试提示。恢复中的草稿卡组在 Ready 发布前加载。
- Clear Web 皮肤增加有界 FIFO outbox，在端口和 Host 投影就绪前保留动作/命令；替换端口时清除旧传输队列，避免向新 Host 重放过期写入。
- 编辑器按序执行 Host 命令，Save 排在未完成字段 patch 后，并抑制重复 Save。
- 更新 Bridge readiness 契约、Clear 包内资源及因 Native 文件迁移而过时的 Studio 边界测试路径。

业务 Store、正式事件版本和授权规则未改变；Native 回退与默认皮肤选择未改变。

## 验证证据

环境：API 35 Android Emulator，AVD `codex-clear-api35`，ADB serial `emulator-5568`。测试数据使用独立数据库，未清除或重建 AVD。

- API 35 Clear/WebView：`ClearMemoProjectionInstrumentedTest` **2/2**（首次 Memo 投影；字段修改后立即发布，仍要求 Host 审批）。附加路径：重复事件 **4/4**、时间轴拖动/缩放/24 小时复位 **1/1**、正式删除 **2/2**、AI 提案审核 **2/2**。其中重复事件和正式删除测试文件当时包含其他 Agent 的未提交改动；这些文件未进入本提交，仍保留在工作区。
- 完整 Gradle 门禁：
  `:app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=1 --console=plain`
  **BUILD SUCCESSFUL**；JVM **784/784、125 suites、0 failures/errors/skips**；AndroidTest Kotlin 编译、两个源码边界、Debug APK 与 lint 通过（152 tasks）。
- Skin Studio Python：`python -m unittest discover -s tools/skin-studio/tests -v` **62/62 PASS**；Clear JS 两个文件 `node --check` 通过；`git diff --check` 通过。
- 皮肤包核对：9 个文件，394,439 bytes；源码、APK asset SHA-256 均为 `465cfd1b980f897a7540cfd49a2fc046f70662d7e9266195ba1ac65359359c82`。
- Debug APK SHA-256：`4EAAD84A88A78FE1A785DD20AB2A078956D2A3BC3DBC5E37CE14C4370AE716BF`。
- 代码提交：`733d2542f72473e37317e5dc2dd4cdaf0b5ee75e`，已正常推送到 `origin/feat/skin-first-separation`。

主要原始日志位于 `.local/android-test/reports/clear-runtime-api35/`：Gradle 门禁 SHA-256 `8FEC1BF46AD66EDEFC1D704B286486E6BBE2855E3CCF4BA42D721EAEF78DB062`；Studio Python SHA-256 `5B13ABD8170286BC74F8C707613AAFBCCCEC0D90EE61FEA7BAAEB17D8EB4CBFC`；Clear 首屏/编辑回归日志 `ClearMemoProjectionInstrumentedTest-final-source.log`。

诊断中曾观察到一次 AVD 进程退出，Gradle 收到 0 项测试。使用同一数据目录、未 wipe 的 AVD 重启后，目标类及上述 API 35 路径均通过。早期全量 JVM 曾出现 Memo Ready 超时；单项通过，最终门禁在同步 Store/Memo 投影并修正故障注入测试的 readiness 等待后通过。失败日志保存在同一忽略目录，没有从证据中删除。

## 尚未签核

未运行实体/OEM 设备、TalkBack 或完整 Native/Skin 双版本业务矩阵。本 Issue 关闭 Clear 首屏投影与立即保存这两类已复现缺陷，不代表 Clear 完整可替换或 SEP-01～SEP-16 已完成；继续保留 Native 回退。
