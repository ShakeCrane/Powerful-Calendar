# PC-ISSUE-002 — 免费模型超时与结构化提案未闭环

日期：2026-10-23（北京时间）｜优先级：P1
状态：**PARTIAL / BLOCKED；Issue 尚未关闭，核心冻结保持 HOLD**

## 复现与实际结果

环境为 PowerCal `0.9.5-rc.1`、Android 15 / API 35、仓库内模拟器 `emulator-5554`。设备系统时区为 GMT；测试主机时区为 Asia/Shanghai。AI 配置只检查到 `key_present=True`、`enabled=True`、模型 `stealth/glyph-cluster:free`，没有读取或记录密钥。

| 路径 | 实际观测 | 结果 |
|---|---|---|
| 修复前的复杂请求 | 旧探针记录总等待 64.5 秒后超时、没有日历变更；传输日志还记录了 45.015 秒 / HTTP 0 与 61.567 秒 / HTTP 200 的调用。历史证据对单次探针与调用的对应关系不充分，不把两行拼成一个请求。 | FAIL |
| 提高免费模型首轮预算后的调用 | 已有日志记录 HTTP 200、14.759 秒与 HTTP 200、5.737 秒，两次输出预算均为 2048；UI 未证明提案卡可用。历史合成请求 `PC_QA_AI_03` 的界面显示空的“无法生成结构化提案：”原因。 | PARTIAL |
| 免费模型纯文本控制请求 | HTTP 200、4.969 秒、输出预算 2048；模拟器聊天界面显示预期的合成控制回复。回复在原视口下方，手动滚动后才看见，另记为聊天跟随行为观察。 | PASS（仅传输与聊天呈现） |
| 免费模型结构化提案请求 | 合成 Activity 请求在 HTTP 503 后失败；耗时 35.807 秒、输出预算 2048；没有出现可审查卡，也没有触发任何草稿/发布操作。 | FAIL（端到端） |

关键证据均在 `.local/android-test/reports/freeze-20261023/`（Git 忽略）：

- `issue002-proposal-ui-probe.json`：API 35 UI 复测结果，SHA-256 `E6A3F08627CCABC404B1FA613D1F6A3486500210C5E04FFF92D1A8FF4351390B`。
- `issue002-proposal-ui-probe.png`：本轮 503 的模拟器画面，SHA-256 `4875ED5B4790D780564F7C3D8FC5111C456F4921A93A30DA0765CD1A19CEFCDC`。
- `issue002-control-after-scroll.png`：HTTP 200 控制回复及此前结构化失败 UI，SHA-256 `1F90049788E368446F00DFBADB273EB7BD8F8DDBD06A34883D7C013096BA6ECA`。
- `issue002-transport-perf-after.log`：预算调整后的阶段耗时日志；没有提示正文、响应正文、headers 或凭据。
- `.local/android-test/scripts/ai_issue002_ui_probe.py`：有界模拟器 UI 探针；测试文本为合成内容，探针不会按提案卡上的任何写入按钮。

早期 UI 探针曾把历史聊天里的 HTTP 401 误认成本轮错误，且页面识别条件不严；该次结果已排除，不用于判定。本报告只采用按本轮合成标记和新传输计时核验的记录。

## 根因与代码变更

1. 免费模型的首次聊天预算只有 540 tokens，结构化调用预算为 1800；旧空响应处理会再发一次请求，可能把无可见内容扩展为更长等待。新增 `AiCompletionPolicy`：`:free` 首轮至少 2048、上限 4096；空 HTTP 200 不盲目重发。自动重试仅允许精确模型 ID `deepseek/deepseek-v4.1-flash` 在确认 `finish_reason=length` 且预算不足时发生一次；其他付费模型不会自动重试，也不会自动切换模型。
2. 聊天模型生成的 `source` 可能来自示例或模型编造内容。聊天提案 Skill 现在只在模型给出意图标记时读取**本轮原始用户输入**，不把模型的 `source` 字段交给解析器；该标记本身不授权任何写入。
3. 结构化解析曾静默略过非对象事件项，导致“无提案、无警告”，界面拼出空白失败原因。解析器现在保证无可用提案时至少产生非空警告，UI 另有通用安全兜底提示。
4. Debug 遥测现在只记录固定阶段名、耗时、HTTP 状态、输出预算、提案意图布尔值及提案/警告数量，不记录请求正文、回复、URL、headers、密钥或日历字段。

改动文件：`AiClient.kt`、`AiChatActionParser.kt`、`AiModels.kt`、`PowerCalApp.kt`、新增 `AiCompletionPolicy.kt`、`AiCompletionPolicyTest.kt`、`AiCalendarHintSafetyTest.kt`。没有数据库格式或业务数据迁移。

## 回归验证

- 针对性 AI 单测：`AiCompletionPolicyTest` 与 `AiCalendarHintSafetyTest`，**9/9 PASS**，含未授权付费模型不得自动重试。
- 完整冻结 Gradle 命令：
  `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`：**BUILD SUCCESSFUL**；JVM **345/345**，失败 0、错误 0、跳过 0；Lint **0 errors / 56 warnings**。
- Skin Studio：`python -m unittest discover -s tools/skin-studio/tests -v`：**37/37 PASS**。
- 全量 Gradle 日志：`issue002-full-final.log`，SHA-256 `070A2E703872D9E0A6C7C10EF479F421F62783436B301CEBCB866811257F4B0C`。针对性 AI 日志：`issue002-targeted-paid-retry.log`，SHA-256 `87961D9BE777E486C31FB61EA4185D58869580A1A544EAA36DB1A77C910A32E2`。Python 日志：`issue002-skin-python.log`，SHA-256 `EE73B3E5381D39B7299D852ECB51440FC82637807F2C325403B5D7B4D44EE8A9`。
- 最终 Debug APK：versionCode `905`、versionName `0.9.5-rc.1`，SHA-256 `F47C456943A9685A85DD77DDED17C5B4DDC9A490635BF3C4CE78E4F7F97BE846`。已用 `adb install -r` 覆盖安装成功；`key_present=True`、免费模型与启用状态保留。

## 剩余缺口

- 免费服务的短文本控制请求成功，但复杂提案本轮收到 HTTP 503；先前结构化回复也没有形成可审查卡。因此不能签核“自然语言 → 提案卡”。服务端稳定性与 503 的提供方根因目前不可由本地代码确定。
- 尚未验证同意/拒绝/写入草稿后的数据库、UI、重启恢复、幂等、冲突、撤销或批量授权链。此次没有点击任何写入按钮，也没有修改正式事件。
- 付费 Flash 仅有旧的 inconclusive 记录，本轮未重复付费请求；物理真机和其他 OEM/API 仍未测试。
- 测试设备 GMT 与验收时区 Asia/Shanghai 不一致；本次用明确的未来固定日期，不据此签核时区/DST 行为。
- 另观察到在历史聊天视口发送后，新消息与回复可能留在视口之外，需作为独立 UI 问题复现/定级；本报告不把该行为并入 Issue 002 的通过结论。

## Git 与下一项

当前代码与本报告准备按普通提交推送；提交 SHA、push 结果和提交后遗留工作树状态将在提交后补录。Issue 002 保持打开且状态为 BLOCKED。下一优先项：复验已修复的 **PC-ISSUE-001** Android SAF ICS 实际导出与重复规则回环；不清除 `emulator-5554` 既有数据，使用隔离实例执行破坏性场景。
