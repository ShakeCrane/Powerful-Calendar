# PC-ISSUE-SEPARATION-LEGACY-MIGRATION — 旧版数据库迁移入口与批次恢复

日期：2026-10-28

状态：**Host / JVM / Skin Studio 范围 PASS；Android WebView、设备及 SEP-11 总体验收 NOT TESTED**

## 范围与根因

本 Issue 为现有只读旧版 PowerCal 数据导入补上皮肤设置入口，并修复缺失 `events` 表时遗留未完成迁移批次的问题。它不迁移 AI 凭据，不改变旧库 schema，也不删除旧数据库。

- Native 的 `NativeToolsPage` 已有受信任的迁移确认和 `LegacyImporter` 调用，但 `SemanticOptions`、Bridge action allowlist 和正式 JSON Schema 没有对应操作，Skin 设置页无法请求该能力。
- `LegacyImporter.migrate` 在 `beginMigration` 后发现旧库没有 `events` 表时直接返回，绕过 `finishMigration`。每次重试都会留下 `finished_ms IS NULL` 的批次记录。
- Clear 已能通用展示 Host 投影的 `optionGroups`；不需要在 Skin 中复制导入实现。Studio 预览必须明确这是非写入 Mock。

## 修复

- 添加闭合 Host action `data.legacy.migrate`。Skin 只能发出无数据参数的语义请求；`PowerCalApp` 打开现有 Native 确认，确认后复用 `LegacyImporter`。源数据库继续只读并保留。
- 缺表分支现在将警告写入迁移记录并结束批次，然后返回；重试不会积累未完成记录，也不会创建导入事件。
- 同步更新 Bridge schema 示例、Studio 设置 Mock 与 Chrome E2E。Studio Mock 明示不会读取、写入或删除应用数据库。
- 修正 `WebSkinHostProjectionTest` 的固定项目数和数组下标断言，改为按语义 ID 定位新增选项，避免后续 Host 选项扩展造成脆弱失败。

改动文件：

- `app/src/main/java/dev/powerfulcalendar/data/LegacyImporter.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt`
- `app/src/main/java/dev/powerfulcalendar/ui/v2/SemanticOptions.kt`
- `app/src/test/java/dev/powerfulcalendar/data/LegacyMigrationTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinBackupActionIntegrationTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinBridgeTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinHostProjectionTest.kt`
- `docs/contracts/web-skin-bridge-v1.schema.json`
- `tools/skin-studio/v3-studio.js`
- `tools/skin-studio/tests/e2e_v3_source.cjs`

## 测试与结果

### Android JVM / Robolectric

定向类：`LegacyMigrationTest` 4/4、`WebSkinBackupActionIntegrationTest` 2/2、`WebSkinBridgeTest` 5/5、`WebSkinHostProjectionTest` 15/15；共 **26/26 PASS**。其中缺表测试执行两次迁移，验证批次计数为 `(1, 0)` 再到 `(2, 0)`、警告可读、没有生成事件，旧数据库文件大小不变。Bridge 集成测试验证皮肤动作先显示可信确认、取消时 Store 计数不变、确认后导入一条记录，旧库仍保留原事件。

完整命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

最终结果：**BUILD SUCCESSFUL，649/649 JVM 测试、90 suites、0 failures/errors/skips、88/88 tasks**；Skin source boundary、Debug APK、AndroidTest APK 构建通过；Lint **0 errors / 52 warnings**。

首次完整回归捕获一项测试预期问题：`WebSkinHostProjectionTest` 固定期望 3 项，新增 Host 选项后实际为 4 项。该断言改为按 ID 查询后，投影类 15/15 通过，完整回归也通过。初次失败日志保留在证据目录。

- 初次失败完整门禁日志 SHA-256：`00B803F2B38A51B84C2118D5DC6A497B3E4C330612E9856F7523DF879BD3851C`（`gradle/full-gate.log`）。

### Skin Studio

- Python：**72/72 PASS**。
- Chrome Headless E2E：**PASS**；浏览器确认迁移选项可见并调用 Host Mock，Mock 回执明确没有读取或改写日历数据。
- 这些结果只验证 Studio/浏览器 Mock，不代表 Android WebView 或系统文件提供器已通过。

### 设备边界

本 Issue 没有运行 Android WebView、AVD instrumentation 或实体设备测试。检查 ADB 时本机服务无法启动（`cannot connect to daemon`），所以设备路径记录为 **NOT TESTED**；APK 构建成功不作为设备证据。

## 可追溯证据

证据目录：`.local/android-test/reports/separation-legacy-import-20261028/`（当前隔离 worktree 下的忽略目录）。

- 完整 Gradle 日志 SHA-256：`FEDC7B6BFA6A451819F005CF43FCB4719A4A1A7C79E4C21A605EB3072CADBFE9`
- 三个迁移/Bridge 定向类日志 SHA-256：`816369BF7E969BE6FD70687EF91233996AECFDA11C64E6BC28A3C58873990AE3`
- 投影回归日志 SHA-256：`7B0975173222C9C8C69EC421F661AF5A51087CF27764FD10A62CB85A7AC611A4`
- Skin Studio Python 日志 SHA-256：`84E5E0EA6E5E0B6174D20FAB048F4050650602D52830C7AD2E3070306D217993`
- Chrome Headless E2E 日志 SHA-256：`0823A8D5C73526DDF38336872456FB63826EABF06D795C8F6E32FECC8563F72E`
- Debug APK SHA-256：`29AB0792A35ABC35856B842526A208CBDA8AB3417D69D1CEAD916B3B91997448`
- AndroidTest APK SHA-256：`06F91487E2465995F89CF2C9D6A9DE53843BBD5430E191423A249BEA109BA97E`
- Lint XML SHA-256：`A016BF0CED62484371F5EA3DC4D91A951F67A6AA212635A67751E0CB7471ABFA`

## 剩余验收与 Git

- `SEP-11` 继续 **NOT TESTED**：设置、凭据、ICS、备份恢复、权限和皮肤管理等全范围尚无 Native/Skin 双版本回放；真实 SAF/Provider、真实 Android WebView 与系统权限恢复也未验证。
- 本次只覆盖只读旧库迁移入口和缺表重试；AI 凭据迁移与其余设置能力仍沿用现有 Native 边界。
- 实现提交：`d09a2492aff770d3776ff5a529e11253a1763815`（`fix(分离): 支持经宿主确认的旧版数据迁移`），已推送至 `origin/codex/separation-legacy-import`。
- 本 Issue 报告、SEP-11 矩阵和进度文档另作文档提交，提交后记录其 SHA 与推送状态。
