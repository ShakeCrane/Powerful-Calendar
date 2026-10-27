# PC-ISSUE-SEPARATION-TEST-PROVIDER-ISOLATION

日期：2026-10-27

状态：**AndroidTest SAF fixture 已隔离并通过 API 35 验证；不改变产品行为或 SEP-10 状态。**

## 根因

AndroidTest 的 `RevocableSafTestProvider` 在 manifest 中使用固定 authority
`dev.powerfulcalendar.androidtest.revocable-saf`，测试 URI 和签名权限也写死为同一全局名称。
当两个互不相关的 QA applicationId 同时安装到同一 AVD 时，第二个 AndroidTest 包安装失败：
`INSTALL_FAILED_CONFLICTING_PROVIDER`。这使隔离 QA 包无法并行验证，不能通过卸载其他 Agent 的测试包规避。

## 修复

- AndroidTest manifest 将 provider authority 和签名权限改为 `${applicationId}` 派生值。
- `RevocableSafTestProvider.syntheticUri(testContext)` 从 AndroidTest 包名生成 URI；授权、撤销及 SAF 回归共同使用该 URI。
- `SafBackupReaderPermissionTest` 从 instrumentation context 取得测试包 URI。生产应用源码、数据库和授权逻辑未修改。

合并后的示例 authority：
`dev.powerfulcalendar.d05qaaiops2026102701.test.androidtest.revocable-saf`，与 AVD 上已有的
`dev.powerfulcalendar.androidtest.revocable-saf` 不冲突。

## 验证

- API 35 `emulator-5564`：隔离 QA 应用包与 AndroidTest 包均成功安装；其间已有的
  `dev.powerfulcalendar.d05qaskinrec20261027.test` 保持安装且未覆盖。
- `SafBackupReaderPermissionTest`：**1/1 PASS**。日志：
  `.local/android-test/reports/separation-20261027/ai-modify-delete/isolated-saf-provider-test-final.log`，
  SHA-256 `C4F6CD48EAAC398D75183C5C5BD188E1001AAE764C3A04FAA191D5482485EA61`。
- 同一代码快照完整门禁：**593/593 JVM tests、83 suites、0 failures/errors/skips、88/88 Gradle tasks**；
  `verifySkinSourceBoundary`、Debug/AndroidTest APK 和 lint 均通过，lint 0 errors / 52 warnings。
  日志 SHA-256 `067DCE0619BB8F6B05D016D226EF9E42230006662A8900EC58FE6FB917B8232F`。
- 安装并测试的 AndroidTest APK SHA-256：
  `F200E847BDB2872542139D55435BB95C9E45DB461CFE1C220E36E3187D512821`。

测试只使用本地合成内容和唯一 QA 包；运行后按随机命名空间核对数据库、缓存和 shared preferences，无残留。

## Git

提交并已推送：`fd6a14369c149739d3ef8e31dedce10e521adb45`
（`test(隔离): 为 SAF provider 使用唯一测试包 authority`）。

## 范围边界

这是 AndroidTest fixture 隔离，不是用户可见产品功能。它不证明 AI provider、完整 Native/Skin 行为等价或 SEP-10；
SEP-10 保持 **NOT TESTED**，S3 仍在进行。
