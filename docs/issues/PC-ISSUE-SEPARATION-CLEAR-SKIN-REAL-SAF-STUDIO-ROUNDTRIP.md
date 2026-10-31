# PC-ISSUE-SEPARATION-CLEAR-SKIN-REAL-SAF-STUDIO-ROUNDTRIP

日期：2026-10-31  
范围：SEP-11 皮肤包导出与 Studio v3 重编辑窄路径  
状态：**该 v3 路径 CODE COMPLETE / TESTED；SEP-11 整体仍 PARTIAL / HOLD / INCOMPLETE**  
实现提交：`0621e647a6ad65fe46fb1499e8f5f9389693fe72`

## 问题与根因

原生 Settings 与 Clear Settings 共用 Host 的 SAF 导出。Host 给 `CreateDocument` 传入 `application/zip`，默认标题虽为 `{id}.pcskin`，API 35 DocumentsUI 会把实际文件名改成 `{id}.pcskin.zip`。首次真实 UI 回归按 `.pcskin` 路径读取，因而失败。拉取系统实际保存的 `.pcskin.zip` 后，Studio `validate --json` 成功；该文件 1726 bytes，SHA-256 `5C7E36A188A81F4100E5EFF9FF914D1F66FCBF297FC9F320AC16DCF15A106B60`。问题是文件扩展名与产品交付名不一致，不是 ZIP 内容被改写。

## 修复

- `PowerCalApp.kt` 为 Clear 与已安装皮肤导出统一使用 `application/vnd.powercal.skin+zip`，默认 `.pcskin` 文件名不再被测试用 DocumentsUI 追加 `.zip`。
- 两个皮肤导入入口均将此 MIME 加入选择过滤，同时保留 ZIP、octet-stream 与通配类型。
- `SkinPackageExportFlowTest` 锁定 Host 导出 MIME；API 35 仪器测试经过真实 Clear Settings、Host 回调、Android DocumentsUI、Downloads 保存，并比较安装包与保存包的完整字节及 Store/身份计数。
- 不改变皮肤包内容、验证规则、权限边界、Calendar Store、当前皮肤选择、Native 回退或默认皮肤。

## 验收证据

环境：Android 15 / API 35 x86_64、WebView 124.0.6367.219、仓库 AVD `emulator-5570`；使用直接 `adb shell am instrument`，未运行 UTP 清理流程。

| 检查 | 结果 | 证据 |
|---|---|---|
| 真实 SAF 皮肤导出 | **PASS 1/1** | `clearInstalledSkinExportUsesDocumentsUiAndPreservesArchive`；DocumentsUI 选择 Downloads 后精确保留 UUID `.pcskin` 文件名；安装输入与导出文件逐字节相同。日志 SHA-256 `AF4ACAC19A7C996A60CE58212380D8C1F1CBD53EAE63D39E5B89BECA67F1C8EF`。 |
| Clear Settings 整组设备回归 | **PASS 8/8** | `ClearSettingsOptionsInstrumentedTest`，覆盖现有设置/导入/删除/备份/ICS 和本次皮肤导出。日志 SHA-256 `E51301DE7227C0942B1E372F7BA201D9E20C7384FDBE471007A9C8CB88456BBA`。 |
| Android 导出包 | **PASS** | 1726 bytes；SHA-256 `BC5EAD32858BB20F1653138C53604D7F5708A30C9E3B77A5A958D3ABAE850815`。仪器测试断言它与已安装 archive 完全相同；Studio `inspect-v3` 识别为 schema v3、5 个文件。 |
| Studio CLI 重开、编辑、校验、读回 | **PASS** | `read-v3` 读取原 `web/app.js` SHA `1c16ea9522447f158918871e095ce27cad578f15e8a6217a2ad2a192c4df78fd`；`edit-v3` 用该 SHA 作并发保护，只追加一条注释，更新后 SHA 为 `4427f4f309c6542976563282b890fa56eb95f4b4c0b8961bc00b49830e5346db`。结果包 1694 bytes、5 个文件，SHA-256 `E46EF30DC0F10F10DCB78A8900F86E0CA974A54CB340182207EE32820A642CF7`；`validate --json` 成功，`read-v3` 读回修改内容成功。操作及 CLI 收据位于证据目录。 |
| Skin Studio 自动化回归 | **PASS** | Python **63/63**，日志 SHA-256 `2C21DFA6B8E4DA8708B466CFB64494D6E47EC967BFA533AF7B162784988B8474`。Chrome/CDP `e2e_v3_source.cjs` 的源码、项目结构、二进制资源往返与编辑场景 PASS，日志 SHA-256 `73C72F1BD66A774083520E59CC36A6DBFA2B82DF3FC01C805C4D9D081601A561`。浏览器 E2E 使用 Studio 项目模型测试包；本次精确从 AVD 拉出的 archive 另由 Studio CLI 直接导入并编辑。 |
| 完整离线 Gradle 门禁 | **PASS** | 两个 Skin/Native source boundary、784/784 JVM（125 suites，0 failures/errors/skips）、AndroidTest Kotlin 编译、Debug 与 AndroidTest APK、Lint 均通过；171/171 tasks。Lint **0 errors / 55 warnings**。日志 SHA-256 `B795F37E9FB162A1F2F6107736E05CBEF78FBD3A34CB94536B5A29DD972BE714`。 |

统一构建产物：Debug APK `app-debug.apk` SHA-256 `ED9D1A0B1F0AE48E60F3013E89235550DA54ADB794A683BA11FADBCF4E5CDB2`；AndroidTest APK `app-debug-androidTest.apk` SHA-256 `66528ABE255EF7051A90DA66A3C454B0FC4122E6F1851B884F7E642AE4352326`。完整日志、导出包、Studio 编辑产物和 JSON 收据保存在 `.local/android-test/reports/sep11-clear-skin-saf-20261031/`（Git ignored）。

测试结束后确认 AVD Downloads 为空，测试用皮肤和隔离数据库没有残留。测试创建的 UUID 文件只在该 AVD 的 Downloads 中读写和清理，没有访问真实用户日历或数据库。

## 状态边界

**TESTED：** API 35 AOSP DocumentsUI 中已安装 v3 皮肤 → `.pcskin` 文件 → Skin Studio v3 CLI 源码修改/校验/读回。该结果关闭 SEP-11 的 installed-skin export picker 验证缺口。

**仍 INCOMPLETE：** 其他 Android/OEM Documents provider 的 MIME/扩展名表现、实体设备、取消/写入失败路径、v1/v2 的 Studio 重编辑/升级、同 ID 原位替换，以及整个 SEP-11 的 Native/Skin 配对等价都未由本次证据覆盖。SEP-11 继续保持 **PARTIAL / HOLD**；没有据此宣称完整分离或皮肤切换验收完成。

