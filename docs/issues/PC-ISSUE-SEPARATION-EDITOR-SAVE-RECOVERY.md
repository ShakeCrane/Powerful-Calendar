# PC-ISSUE-SEPARATION-EDITOR-SAVE-RECOVERY

- **日期：** 2026-10-30
- **状态：** `PARTIAL / HOLD`
- **验收项：** 重建矩阵 `S0-ED-02`（RECONSTRUCTED；不是历史 209 项原始 ID）
- **源码与测试提交：** `df25568`（`fix(分离): 统一新建编辑保存的锁定恢复`，已推送到 `origin/codex/separation-s0-timeline-drag`）

## 结论

修复了 WebSkin 普通新建会话在 AI 批次锁期间的保存反馈与恢复路径，并补齐 ALARM、ACTIVITY、DEADLINE、MEMO 从不完整草稿暂存到 Host 确认发布的生产 Bridge 回归。Robolectric/Store 验收通过；由于没有可用 Android 设备或本地 emulator，生产 Android WebView 路由、辅助功能与设备重启行为未测试，故 `S0-ED-02` 仍为 `PARTIAL / HOLD`。

## 根因与修复

`WebSkinEditService.requestCommitReview()` 对已有 Deck 草稿使用 `DeckController` 状态检查 `aiLocked`，但内存态 `NewEventEditSession` 没有该投影。新建会话保存分支因此漏查权威 `PowerStore.isLocked()`：完整表单会先创建宿主确认请求，确认后 Store 才拒绝写入；不完整表单则由 `newDraft()` 抛出锁异常，经 `SaveOutcome.Failed` 映射成普通 `failed`。表单一直留在内存会话，但锁定状态和可重试性没有一致呈现。

修改 `WebSkinEditBridge.kt`：先保留无 Diff 的 `no_changes` 短路，再在新建会话保存前查询 Store 锁；被锁定时返回 `locked` 并说明表单仍保留，不创建宿主确认、不暂存、不分配身份。若锁在预检后才出现，不完整表单的暂存失败会重新检查 Store，并映射为同一 `locked` 状态；Store 事务仍是最终写保护。

新增 Bridge 回归：

- 四类事件分别以固定 `Clock` / `Asia/Tokyo` 创建；不完整表单各暂存一张草稿，补齐标题后必须经过 Host review 才发布；验证原身份与队列槽位复用、纯日期 DEADLINE、无日期 MEMO、无重复提交。
- 不完整 MEMO 遇 AI 锁时返回 `locked`，草稿/身份/序号/队列/提交计数不变且输入仍在编辑器；解锁后用新请求 ID 重试，恰好暂存一张并可继续发布同一身份。
- 完整 ALARM 遇 AI 锁时不弹 Host review、不写入；解锁后重新请求确认并发布一次，验证没有临时草稿或额外队列序号。

## 验收证据

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 定向生产 Bridge 测试 | `PASS` | `WebSkinEditBridgeTest` **35/35**，0 failures/errors/skips；覆盖新增的四类暂存续发、锁定保存恢复和完整发布锁定重试。日志 SHA-256：`863241014B9698FD805BAC7BE84B23370F4659E1B2A2D6D9663E9876DB64EEE1`。[日志](../../.local/android-test/reports/separation-s0-ed02-20261030/focused-bridge-rerun.log) |
| 完整离线 Gradle 门禁 | `PASS` | `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain --max-workers=1`；**718/718 JVM，98 suites，0 failures/errors/skips**；88 actionable tasks（13 executed / 75 up-to-date）；Lint **0 errors / 53 warnings**。[日志](../../.local/android-test/reports/separation-s0-ed02-20261030/full-gate.log)，SHA-256：`A586F0E7719C05B66EF6C26353C9E10A91F537629486A938BE8846727059CB8D`。 |
| 全量强制重跑 | `PASS` | `:app:testDebugUnitTest --offline --rerun-tasks --console=plain --max-workers=1`；**718/718，98 suites，0 failures/errors/skips**，32/32 tasks executed。[日志](../../.local/android-test/reports/separation-s0-ed02-20261030/full-rerun.log)，SHA-256：`0FA6AD5F5A5E87E7876649621907E3BA7D6F0F04662B5758F13F1E77C7916436`。 |
| Skin Studio Python | `PASS` | `python -m unittest discover -s tools/skin-studio/tests -v`：**61/61**。日志 SHA-256：`8814BB16C1B7031AC3139DF88BCCE614C499905C4EC70252AD93AB545A45A9E6`。[日志](../../.local/android-test/reports/separation-s0-ed02-20261030/skin-studio-python.log) |
| Clear 同源包校验 | `PASS` | `python tools/skin-studio/build_default_clear.py --verify`：9 files / 394,087 bytes，SHA-256 `544eb2b9ec1bd1ea3c3cf9d5949d944cba48018bc08478f60c08eea5a4e1cf3f`。[日志](../../.local/android-test/reports/separation-s0-ed02-20261030/clear-package-verify.log) |
| Chrome/CDP Studio Preview | `PASS` | `e2e_v3_source.cjs` 完成 Clear/Studio 源码 round-trip、编辑器转换和非写入 Mock Host 流程；它不运行 Android WebView，也不替代以上生产 Bridge 测试。日志 SHA-256：`E25509D4BC4E46BB3E319F443C35DD60FCB784F71DB7B2F0DFB5FD92E0ECC97E`。[日志](../../.local/android-test/reports/separation-s0-ed02-20261030/clear-source-e2e.log) |
| Debug / AndroidTest APK | `PASS`（仅组装） | Debug APK：67,513,305 bytes，SHA-256 `16BD871B2614F501964B0B7AABEBC171D633F4348FD3D3CA29A4267D753D44D2`。AndroidTest APK：1,557,196 bytes，SHA-256 `371A9C1AF76B5F0530FE4A69FA2FAF9BF1A053256DC8B24F7264129930653E`。Instrumentation 未运行。 |
| Android 设备 / emulator | `NOT TESTED` | `E:\android-sdk\platform-tools\adb.exe devices -l` 仅显示空设备列表；`E:\android-sdk\emulator\emulator.exe` 不存在。 |

首轮定向执行有一项新增测试断言失败：测试误将累计草稿数当作当前 pending 数。没有生产用例失败；将断言改为检查当前唯一 pending 草稿和累计草稿数后，定向测试及全量重跑均通过。失败日志仍保留在 `.local/android-test/reports/separation-s0-ed02-20261030/focused-bridge.log`。

## 未解决风险与下一步

- 生产 Android WebView→Host MessagePort、TalkBack/实际辅助功能服务、Activity/进程恢复及实体/OEM 设备行为仍 `NOT TESTED`。
- AI 锁恰好发生在 Bridge 预检与 Store 写事务之间的时序未被确定性注入；Store 事务仍会拒绝写入，暂存失败分支会重查锁状态并保留会话，但该窄竞态需后续压力/设备证据。
- Chrome/CDP 的 Preview Host 是 Mock；不得把该结果写成 Android WebView 或 Store 事务验收。
- `S0-ED-02` 保持 `PARTIAL / HOLD`。按分离阶段计划继续审查 `S0-ED-03` 编辑关闭、生命周期和恢复路径；Android 设备项保留给设备验收。

## 证据文件

详细日志与临时结果位于 Git 忽略路径 `.local/android-test/reports/separation-s0-ed02-20261030/`，Debug/AndroidTest APK 位于 Gradle 标准输出目录。未将数据库、凭据或敏感测试数据写入报告或 Git。
