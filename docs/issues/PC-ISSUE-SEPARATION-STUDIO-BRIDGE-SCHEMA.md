# PC-ISSUE-SEPARATION-STUDIO-BRIDGE-SCHEMA — Bridge v1 payload schema

日期：2026-10-28
状态：**Bridge v1 机器可读 payload 契约及所测 Host decoder 对齐 PASS；动态 JavaScript、Android WebView 和完整 SEP-15 parity NOT TESTED**

## 问题与根因

此前的 `audit-bridge-v3` 能检查 `.pcskin` 静态 HTML 中的 literal `data-action` 名称和 `host.action` 能力声明，但没有正式的 payload 字段契约。编辑器 `command` 消息也缺少供 Studio、AI 和外部工具共同使用的机器可读规范。仅有 action 名称清单无法让工具构造、检查或解释完整 Bridge 消息。

## 实施

- 新增 `docs/contracts/web-skin-bridge-v1.schema.json`（JSON Schema Draft 2020-12）：定义闭合的 action/command envelope、25 个 action payload 和 18 个编辑命令 payload，包括必需字段、类型、枚举、边界、示例、patch 字段限制及本地 `$ref`。
- Studio action registry 指向该 Schema；`audit-bridge-v3` 验证 Schema 的 action 清单和 action/command union 与 registry 一致，并在每个静态 binding 结果中输出对应 `payloadSchemaRef` 和 Schema SHA-256。
- Kotlin 回归把 Schema 中每个 action/command 示例交给实际 `decodeBridgeAction` / `WebEditWire.decode`；对必需字段删除和额外字段注入验证拒绝行为。patch 示例交给 Host 的 `WebEditWire.patch`，检查字段集合、长度、枚举、NUL、提醒分钟和重复间隔边界。
- Python 回归检查 Schema 与 Kotlin decoder 名称集合、必需字段、示例属性、本地 `$ref` 和审计 receipt 的一致性。
- 更新 Studio AI 自动化说明、Studio README、文档入口、SEP-15 矩阵和 `AGENTS.md`。
- 未更改生产 Kotlin decoder 或 Host ABI。Host decoder 仍是运行时行为的权威实现。

修改文件包括：

- `AGENTS.md`
- `docs/contracts/web-skin-bridge-v1.schema.json`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinBridgeContractFixture.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinBridgeTest.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridgeTest.kt`
- `tools/skin-studio/bridge_v1_action_registry.json`
- `tools/skin-studio/project_ops_v3.py`
- `tools/skin-studio/tests/test_project_ops_v3.py`
- `tools/skin-studio/tests/test_ai_skin_workflow_v3.py`
- `docs/README.md`
- `docs/SKIN_STUDIO_AI_AUTOMATION_V1.md`
- `tools/skin-studio/README.md`
- `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`

## 验证证据

环境：Windows PowerShell；Android Gradle 离线构建；Python Skin Studio 测试；API 35 AndroidTest APK 仅编译，未连接设备。证据目录：`.local/android-test/reports/separation-20261028/bridge-schema/`。

| 验证 | 结果 | 证据 |
|---|---|---|
| Bridge 定向 Gradle 测试 | **27/27 PASS**：`WebSkinBridgeTest` 5/5、`WebSkinEditBridgeTest` 22/22 | `focused-bridge-tests.log` SHA-256 `9F272A7FBA9C44FE8AEBACE4626C85A44E460059EBD575A1884DA032C128CDEA` |
| Skin Studio 完整 Python suite | **66/66 PASS** | `studio-full-python.log` SHA-256 `BAAB44A79ADCA6D880D36D34D5B40C7B15C65CBD1F62D78027EA70AD7F9D0BEB` |
| 完整离线 Gradle gate | **620/620 JVM 测试、87 suites、0 failures/errors/skips、88/88 tasks 成功**；source boundary、Debug/AndroidTest APK 和 lint 均完成；lint **0 errors / 52 warnings** | `full-gradle-gate.log` SHA-256 `4813812DB855E6A9B29B6F2FC9D41B6A779FE99AE23D95AE0E413A7EDE41ABDF` |
| Schema 定向用例 JUnit XML（完整门禁输出） | `WebSkinBridgeTest` 5/5；`WebSkinEditBridgeTest` 22/22 | SHA-256：`143E7FD1FA2DD403C2A3780AFB457AE8EE36BD3643C5C0D2FEEB30EF16F68898` / `F8CCB4402F36AE5965002616E0EFE4C341FFCE955DC770AB1F7DD11A1719B6E2` |
| Debug / AndroidTest APK | 成功构建 | SHA-256：`82F72FAFB1FB53FE4BDFFF38DD065821E5A4FBD3B2F53C5771A82828BE5FA7E2` / `6FBA400C709EDBE947F89626FBC50AB1F3550381D9FC18297C20661760F1A8A4` |
| lint XML | 0 errors / 52 warnings | SHA-256 `B3D4AF5AC60BC562F9727C97C5A65BC4DACC0DC7AD59B44907D6B626CF863BC0` |
| canonical Clear build | PASS；8 files，380,940 bytes | `.pcskin` SHA-256 `9cc6bdd86a53c693b8cec6e84950ed89ec49bcde9fb78d773034e4147e45a454` |
| canonical Clear `audit-bridge-v3` | PASS；25 registered actions，18 个静态 binding、0 未知 binding、0 capability 缺失；13 个唯一 action schema refs；因含脚本而设置 `requiresSourceReview=true` | `canonical-clear-bridge-audit.json` SHA-256 `12834E4C7FA91C66CEA230B1D51E28AE9026D98D33304A40D05B9B65BDAA9209`；receipt 中 Schema SHA-256 `ac5452e6abe780fd0057612a9b406e91efeac53a349ddb1aebd84e25070f90fe` |
| JavaScript 语法 | `node --check tools/skin-studio/default-clear/source/web/app.js` PASS | 命令结果见本轮验证记录 |
| diff 空白检查 | `git diff --check` PASS | 提交前检查 |

### 可复现命令

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests dev.powerfulcalendar.ui.v2.WebSkinBridgeTest --tests dev.powerfulcalendar.ui.v2.WebSkinEditBridgeTest
python -m unittest discover -s tools/skin-studio/tests -v
python tools/skin-studio/build_default_clear.py --verify
node --check tools/skin-studio/default-clear/source/web/app.js
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

静态审计针对构建输出的 Clear 包执行：

```powershell
python tools/skin-studio/skin_studio.py audit-bridge-v3 .local/android-test/reports/separation-20261028/bridge-schema/clear-9a6ea6f49eae495db272f032a155e1cd.pcskin
```

Schema 文件 SHA-256：`AC5452E6ABE780FD0057612A9B406E91EFEAC53A349DDB1AEBD84E25070F90FE`（45,888 bytes）。完整 Gradle 和 Studio 日志没有输出凭据或用户日历数据。

## 验收边界与未解决项

- 环境中没有通用 Draft 2020-12 JSON Schema validator。验证覆盖 JSON 解析、结构/本地引用、名称同步、文档示例与 Kotlin decoder 的实际接收/拒绝行为；因此不声称已由独立标准 validator 完成全部 JSON Schema 语义验证。
- 该 CLI 审计不执行皮肤 JavaScript，也不检查动态生成的 Bridge payload。Schema 示例与 decoder 回归不证明所有皮肤脚本构造的消息都正确。
- 本次未运行 Android WebView、AVD connected test 或实体/OEM 设备测试。AndroidTest APK 编译成功不能替代运行验收。
- SEP-15/S5 的完整结构、视觉、样式、交互、资源和源码编辑能力，Studio/设备 parity 及完整 Core/Host/Skin 等价仍未完成。SEP-15 总项继续 **NOT TESTED**，Skin-First 总目标保持 active。
- 另一 worktree 的 `docs/agent-handoff/PROGRESS.md` 和 canonical Clear 源仍有并发修改，本 Issue 未覆盖或提交这些资产；本分支以 parity matrix 和本报告记录本项。

## 提交

- 实现及规范提交：`e65714f` — `feat(分离): 定义 Bridge v1 机器可读契约`，已推送至 `origin/codex/separation-skin-settings`。
- 本独立 Issue 报告随后单独提交；完整 SHA 以该报告提交的 Git 记录为准。

下一步继续 SEP-15 双轨迁移：扩展 Skin Studio 对运行时组件/完整布局与交互的无损编辑，并对相应 Host/skin 功能进行配对验收；在真实 WebView/设备证据取得前，不提升其状态。
