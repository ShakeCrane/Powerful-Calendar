# PowerCal 0.9 · 版本号与 Release 签名机制

**目的**：让首次稳定安装成为之后所有升级的锚点。包名、签名证书与单调递增的 `versionCode`
共同决定 Android 是否允许原地覆盖安装；一旦用 Debug 签名开始存正式数据，将来切换到 Release
签名将无法覆盖升级。

## 1. 三个版本号必须分开

| 版本 | 当前值 | 位置 | 何时变化 |
| --- | --- | --- | --- |
| `applicationId` | `dev.powerfulcalendar` | `app/build.gradle.kts` | **永不变化**（变化等于另一个应用） |
| APK `versionCode` / `versionName` | 见 `gradle/version.properties` | 唯一来源 | 每次对外发行 |
| SQLite schema version | `PowerStore.VERSION = 3` | `core/PowerStore.kt` | 仅当数据库迁移 |
| Backup format version | `BackupEnvelope.FORMAT_VERSION = 1` | `core/BackupEnvelope.kt` | 仅当备份 Envelope 结构变化 |

禁止把 schema 或 backup 版本直接当作 APK 版本号，反之亦然。

## 2. versionCode 规则

- 唯一来源：`gradle/version.properties`。
- 每个已发布安装的版本追加到 `gradle/published-versions.txt`，格式 `code=name`，只追加不修改。
- 构建时校验：`versionCode` 必须 **严格大于** 该文件中最大已发布值，否则构建失败。
- 0.9 首发候选：`versionCode=900`、`versionName=0.9.0`（高于历史 Debug 的 2）。
- 若将来需要修补而不改产品版本号，提高 `versionCode`（例如 901），不要复用 900。

## 3. Release 签名材料

**本仓库不包含、也不应包含任何 keystore、别名或密码。** `.gitignore` 已排除
`*.jks`、`*.keystore`、`*.p12`、`*.pfx`、`keystore.properties`、`signing.properties`。

构建从**仓库外**读取以下环境变量（或同名 Gradle 属性）：

```text
POWERCAL_KEYSTORE            # 绝对路径，例如 D:\secure\powercal-release.jks
POWERCAL_KEYSTORE_PASSWORD
POWERCAL_KEY_ALIAS
POWERCAL_KEY_PASSWORD
```

- 四项齐全且 keystore 文件存在时，`release` 构建自动使用该证书。
- 任何一项缺失时，`assembleRelease` / `bundleRelease` 会在 `preReleaseBuild` 阶段**明确失败**，
  错误信息指向本节；不会静默生成 Debug 签名或未签名 APK 冒充发行版。
- 证书须由用户本人在受控设备生成，至少一份离线备份；密码不得进入仓库、聊天记录或日志。
- 同一份证书必须用于此后所有稳定升级；更换证书会导致无法覆盖安装。

### 用户需自行执行的操作（本 Agent 不代做）

1. 在安全环境生成长期 keystore（RSA 2048+，有效期 ≥ 25 年），离线保存。
2. 记录证书 SHA-256 指纹；不要把它与密码放在一起。
3. 首次构建 Release 时设置上述四个变量，并保留同一把证书用于后续版本。
4. 若设备上已安装 Debug 版并含真实数据：**先导出并验证 Envelope 备份**，再卸载 Debug、
   安装 Release、恢复备份；未验证恢复前不要卸载旧安装。

## 4. 验证命令

```powershell
# Debug / 单元测试 / lint 不受签名材料影响
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain

# 查看当前生效的签名配置（缺省时 release Config 为空）
.\gradlew.bat :app:signingReport --no-daemon --console=plain

# 提供用户签名材料后构建 Release
$env:POWERCAL_KEYSTORE="D:\secure\powercal-release.jks"
$env:POWERCAL_KEYSTORE_PASSWORD="..."
$env:POWERCAL_KEY_ALIAS="powercal"
$env:POWERCAL_KEY_PASSWORD="..."
.\gradlew.bat :app:assembleRelease --no-daemon --console=plain
```

## 5. 首次稳定安装前的必测项（设备）

- 从 Release APK 新装：创建/编辑/丢弃/恢复同一事件的多卡。
- 保存数据后安装更高 `versionCode`、同证书的覆盖包：正式对象、草稿、版本、序号不变。
- 备份 → 卸载/清除 → 新装 → 恢复 → 校验对象数、内容、高水位与下一次调度。
- `apksigner verify --print-certs` 校验同一证书指纹。
- 这些项目在无真机时只能标记 **Release Candidate**，不能宣称稳定版已验收。
