# PC-ISSUE-158 — 草稿队列搜索默认范围

日期：2026-10-24
优先级：P1
状态：**REC-P1-038 的 API 35 原生待办队列行为 PASS**

## 需求与范围

依据 `PRODUCT_BASELINE_G1-G11.md` §5 和重建验收项 `REC-P1-038`：打开待办搜索时，默认先呈现待处理卡片；正式事件搜索应由用户输入查询触发。历史 ID 保持 `UNMAPPED`。

新增一条 AndroidJUnitRunner 回归，在 API 35 `emulator-5556`、竖屏 1080×2400 上使用生产 `DraftDeckDialog` 原生路径。测试创建两张合成待处理卡和一个只存在于测试回调中的合成正式事件结果；使用随机 UUID 命名的隔离 PowerStore 数据库，并在 `finally` 中关闭及删除自己的测试库。没有打开默认业务数据库、发布/丢弃事件、调用 AI、改动生产代码或修改 AVD 系统设置。

## 验收结果

打开待办队列且搜索框为空时：

- 搜索输入框可见。
- 最早的待处理卡 `PENDING_SEARCH_FIRST` 成为当前卡并显示，位置为 `1 / 2 · 已暂存`。
- 两张待处理卡的队列顺序保持 `[1, 2]`。
- 正式事件搜索回调调用次数为 0；合成正式事件结果没有显示。

定向 API 35 instrumentation **1/1 PASS**。运行轨迹：

```text
DRAFT_SEARCH_DEFAULT currentId=1 queue=[1, 2] formalSearchQueries=0
```

AndroidJUnitRunner 日志 SHA-256：`F39F47009A92228A59686AF189D80A5D1ACB3DBD2CFFA03C3A78EFE5FEEE3791`；轨迹文件 SHA-256：`AA6731D5F9B0CFF36B2C8AC987A72EDCA263786975D83E7E591CE724473EFDDB`。

## 测试夹具修正

首次 AndroidTest 编译因 `assertDoesNotExist` 的显式 Compose 导入路径错误而失败，尚未安装或运行设备测试。改用仓库现有的 `androidx.compose.ui.test.*` 导入后构建和定向测试通过。首次失败日志已保留，SHA-256：`F66E72E90EC86F72C0BB19E55DD411FF35EC5470C52F15AB2E373740950C88D4`；修正后的 AndroidTest 构建日志 SHA-256：`2890BDD8263017E9D8540EE76528C95628D61DA5ED1F514EF024785FA7C6B225`。

## 完整 Gradle 回归

命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

结果 **BUILD SUCCESSFUL**：491/491 JVM 测试、66 个 suites、0 failures/errors/skips；source boundary 与 Debug/AndroidTest APK 组装通过；lint 0 errors / 58 warnings。491 项包括 490 个已跟踪测试及受保护、预先存在的未跟踪 `DraftDeckFeedbackTest.kt` 测试。

- 完整 Gradle 日志 SHA-256：`304584E4D58B2F3161B294354248D4063B415F06F70D9DA4A0ED32BFCEDB9BE3`。
- Debug APK SHA-256：`EE7E4850A147AC3B3F21326DCB209B0967F14DE6990D56EE05A2976FEB252BCD`。
- AndroidTest APK SHA-256：`53C7C3ABD282429BE6CB8D8DC27FCB4B7D655430EFCDD133BD8295D0AA38BD50`。
- lint XML SHA-256：`44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F`。

## 台账与 Git

结构校验 r24 记录 210 项活动重建验收（P0/P1/P2 为 144/65/1）：191 PASS、0 FAIL、8 BLOCKED、5 NOT TESTED、6 SPEC UNRESOLVED；历史 ID 映射为 0，`product_behavior_tested=false`。本次只签核 API 35 原生待办页面的默认范围行为，不代表用户输入查询后的正式事件匹配或实体设备/OEM 行为已通过。

- 台账 SHA-256：`2FA424BDE4E75FDB0EE89008C72FB304AB786A5AF5194E852E42B1940EAB0EA8`。
- 结构校验 JSON：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r24-pc-issue-158-default-search.json`；SHA-256：`D7886DC039A62FC07DE55648EA04C674F424C4EC7986E8C8A9B2EDA33A746438`。
- 测试提交：`c692ad20c1b02260fed5deb3484500a881405c35`（`test(core-freeze): 验证待办搜索默认入口`），已推送至 `origin/main`。

## 结论

API 35 AVD 上，打开待办搜索首先显示待处理队列，且不会自动请求正式事件结果。实体设备测试由用户负责；整体冻结仍为 **HOLD**。
