# PC-ISSUE-122 — DeepSeek V4.1 Flash 模拟器结构化提案验证

日期：2026-10-24（北京时间）｜优先级：P1（PC-ISSUE-002 补充）

状态：**模拟器结构化提案子路径 PASS；父 Issue 002 仍 PARTIAL / BLOCKED；核心冻结仍 HOLD**

## 需求与前置状态

本次按用户授权，在虚拟设备上使用唯一允许的付费模型 `deepseek/deepseek-v4.1-flash` 做一次最小合成请求。此前 PC-ISSUE-002 的免费提供方结构化提案阶段记录过 HTTP 503 和 HTTP 429，DeepSeek Flash 只有旧的 inconclusive 记录。

设备已有 AI 配置为启用状态，密钥存在；测试只断言密钥非空，并通过应用设置加载到进程内交给客户端使用，不打印或留存密钥文本、完整端点、提示正文或模型回复。模型 ID 仅在内存中覆盖，设置保持原免费模型且 `share_context=false`。合成输入不含真实日历数据。

## 环境与步骤

- Windows 主机；Android 15 / API 35 虚拟设备 `emulator-5554`；PowerCal `0.9.5-rc.1`，versionCode 905。
- 编译 `:app:assembleDebugAndroidTest`，使用 `adb install -r` 安装调试 APK 和测试 APK；未清除应用数据。
- 运行 `DeepSeekFlashLiveProbeTest.authorizedFlashReturnsUsableSyntheticActivityProposal`。测试从设备私有设置内存读取端点/凭据，将 model 临时覆盖为精确 Flash ID，显式关闭上下文共享，并调用 `AiClient.interpret`，传入空日历上下文。
- 测试文本要求生成虚构 ACTIVITY：“合成模型验证会议”，2026-11-02 14:00 至 14:30，Asia/Shanghai。测试只读检查返回值，不调用 UI、PowerStore、草稿提交或正式事件写入。

## 预期与实际

| 项目 | 预期 | 实际 |
| --- | --- | --- |
| 结构化阶段 | HTTPS 请求成功并解析为可用提案 | HTTP 200；传输 3.530 秒；输出预算 1800 |
| 提案内容 | ACTIVITY 标题、开始和结束时刻完整匹配合成输入 | 1 条提案；类型、标题、可发布字段及两个时间戳断言全部通过 |
| 错误与重试 | 无警告；成功响应不重试 | 0 warnings；脱敏遥测只有 1 条结构化请求记录 |
| 副作用 | 不改用户设置或正式日历 | 模型覆盖仅在内存；未进入任何 UI 或写入 API；持久设置仍为免费模型、上下文共享关闭 |

定向仪器测试 **1/1 PASS**，instrumentation 输出总耗时 3.759 秒。测试断言 `form.isPublishable()`、标题、ACTIVITY 类型和 Asia/Shanghai 下准确的 14:00/14:30 起止时间。

## 回归与证据

- 最终完整 Gradle 命令：
  `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`
- 结果：**BUILD SUCCESSFUL**；JVM **462/462** across 57 suites，失败 0、错误 0、跳过 0；Lint **0 errors / 58 warnings**。
- 完整 Gradle 日志：`.local/android-test/reports/freeze-20261024/issue002-deepseek-final-gradle.log`，SHA-256 `8C82E247D1FA802B4A876C68D0727A0C3E46BFEB8E6343092A1D1F29E3F10A2D`。
- 仪器测试输出：`issue002-deepseek-device-test.log`，SHA-256 `44540242960C718612AB56EB01485A5551DB09B55AFDED2D9457C520DBC234D3`。
- 脱敏客户端遥测：`issue002-deepseek-perf.log`，SHA-256 `120D20442E45369AE68055EE7C45821308C4D38A30B1E0E77C9C7FF80A1E6EBB`。唯一相关行记录 `stage=structured-proposal elapsedMs=3530 http=200 outputBudget=1800`。
- 最终 Debug APK：versionCode 905 / versionName `0.9.5-rc.1`，SHA-256 `138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`。
- 实际执行模型探针的 instrumentation APK SHA-256：`2295132934C0E009B8CC2FF17FCC8DB53E29A4B168FA3296BD7F777302D276DC`。探针通过后只升级测试 runner/JUnit：`cf57399` 添加探针，`cb910e7` 升至 runner 1.7.0，`b3ac840` 升至 JUnit 扩展 1.3.0；升级后的 AndroidTest 编译和 lint 均通过。为了遵守单次付费请求边界，没有再次运行模型探针。

日志不包含密钥、端点 URL、请求/回复正文、headers 或日历字段。真实 HTTP 状态、耗时和提案数量可由本机文件的 SHA-256 独立追溯。

## 结论与剩余缺口

本 Issue 证明了精确授权的 Flash 模型可在当前模拟器配置下完成一次结构化提案传输与解析。它不证明用户聊天到提案卡完整链路、免费模型 429/503 已恢复、草稿数据库/重启恢复、确认/拒绝/撤销/冲突/幂等流程，也不代表实体设备或 OEM 验收通过。上述交互与实体设备由后续虚拟机验收或用户真机测试补齐。

父 Issue PC-ISSUE-002 继续 **PARTIAL / BLOCKED**；本次不签核额外候选行，不改变重建台账统计（148 PASS / 0 FAIL / 9 BLOCKED / 45 NOT TESTED / 7 SPEC UNRESOLVED），核心版本保持 **HOLD**。

## Git

探针与测试依赖提交已推送：`cf57399`、`cb910e7`、`b3ac840`。本报告及关联冻结记录另行原子提交并推送。工作区里原有的未跟踪 `DraftDeckFeedbackTest.kt`、`design/` 和 `PC-OBS-005-commit-feedback.md` 均未改动、未暂存。
