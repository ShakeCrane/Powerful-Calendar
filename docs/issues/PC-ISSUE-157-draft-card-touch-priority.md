# PC-ISSUE-157 — 草稿卡输入与展开内容手势优先级

日期：2026-10-24
优先级：P1
状态：**REC-P1-041/042 的 API 35 原生草稿卡行为 PASS**

## 需求与范围

依据 `PRODUCT_BASELINE_G1-G11.md` §5 及重建验收项：编辑字段应优先接收文本选择/输入手势，不能被卡片横向翻页抢走；展开卡片内的纵向手势应滚动表单内容，不能切换草稿页。历史 ID 均保持 `UNMAPPED`。

新增两条 AndroidJUnitRunner 回归，在 API 35 `emulator-5556`、竖屏 1080×2400 上使用 `DraftDeckDialog` 原生路径。测试数据写入带随机 UUID 的独立临时 PowerStore 数据库；测试只编辑内存中的表单或滚动内容，未发布、丢弃或修改用户草稿，也没有访问 PowerCal 默认数据库、调用 AI 或改动生产代码。fixture 在 `finally` 中关闭数据库并删除自身唯一文件名。

## REC-P1-041：文本交互优先于翻页 — PASS

创建 4 张合成草稿并打开第一张的完整编辑器。将标题替换为 `TYPED_TITLE_SURVIVES_SWIPE`，随后在标题输入框内横向滑动。滑动后 `currentId` 仍为第一张草稿，文本仍为新标题，队列长度仍为 4。轨迹：

```text
DRAFT_TEXT_TOUCH_PRIORITY currentId=1 title=TYPED_TITLE_SURVIVES_SWIPE queueSize=4
```

## REC-P1-042：展开内容滚动不切换页面 — PASS

创建一张带 32 行合成备注的展开草稿，从可见的“开始”时间标签处向上滑动。表单标题矩形从 `[100,622,980,790]` 移出可见视口（Compose 返回裁切后的空矩形 `[0,0,0,0]`）；当前草稿 ID 保持不变。轨迹：

```text
DRAFT_EXPANDED_SCROLL cardId=1 titleBefore=Rect.fromLTRB(100.0, 622.0, 980.0, 790.0) titleAfter=Rect.fromLTRB(0.0, 0.0, 0.0, 0.0) gestureStart=Offset(351.0, 425.0) gestureEnd=Offset(351.0, 165.0)
```

两项定向 instrumentation **2/2 PASS**。日志 SHA-256：`C206EF28A6B6FCF63019D9305A6CE248DC831B3D4E3E650E8D3A8115A4E1DCFD`；交互/矩形轨迹 SHA-256：`6ECF318FEDA5F992B2A293717A9FD11CBD77EBED6B20E23C6A9927CFEE29A9F7`。

## 完整 Gradle 回归

命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

结果 **BUILD SUCCESSFUL**：491/491 JVM 测试、66 个 suites、0 failures/errors/skips；source boundary 与 Debug/AndroidTest APK 组装通过；lint 0 errors / 58 warnings。491 项包括 490 个已跟踪测试及受保护、预先存在的未跟踪 `DraftDeckFeedbackTest.kt` 测试。完整 Gradle 日志 SHA-256：`46BF96596290D2131786519DA397CB34A6A74D1366887202A1397AECF9C404ED`。

- 定向 AndroidTest 构建日志 SHA-256：`68BDCFEC8071E35F1AE82DFFB05B3F8325E23D91C505845709FC71D55EB389B9`。
- Debug APK SHA-256：`EE7E4850A147AC3B3F21326DCB209B0967F14DE6990D56EE05A2976FEB252BCD`。
- AndroidTest APK SHA-256：`2D3FD0526FE6F15A99D295CCBC4CC1084CC8D9474D8F369AF8CE71A9F8C2DFBC`。
- lint XML SHA-256：`44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F`。

## 台账与 Git

结构校验 r23 记录 210 项活动重建验收（P0/P1/P2 为 144/65/1）：190 PASS、0 FAIL、8 BLOCKED、6 NOT TESTED、6 SPEC UNRESOLVED；历史 ID 映射为 0，`product_behavior_tested=false`。本 Issue 只签核 API 35 原生草稿卡的这两项手势行为，不声称完整应用路由或实体设备/OEM 手势已验证。

- 台账 SHA-256：`076EB56B2398A166C969815595DE6F37FE441874E59ED2A7FCD903C74AAB0437`。
- 结构校验 JSON：`.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r23-pc-issue-157-deck-touch.json`；SHA-256：`972D3F75365E4A7FD67BCC8DE17C9FA72212B259F80E260F9068AAAFB9B74F2E`。
- 测试提交：`d495732054188ef3999a611c443c5e4f371aedcd`（`test(core-freeze): 覆盖草稿卡手势优先级`），已推送至 `origin/main`。

## 结论

API 35 AVD 上，文本输入控件接收横向手势，展开草稿的纵向手势滚动表单且保留当前草稿身份。未测试实体设备；整体冻结仍为 **HOLD**。
