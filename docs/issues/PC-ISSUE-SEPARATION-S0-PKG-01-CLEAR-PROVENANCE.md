# PC-ISSUE-SEPARATION-S0-PKG-01-CLEAR-PROVENANCE — Clear 包来源追溯

- 日期：2026-10-29
- 状态：**PASS（仅重建验收项 S0-PKG-01 的源码、APK 资源和 Studio 模板来源链）**
- 工作树：`codex/separation-s0-timeline-drag`
- 被验证应用代码基线：`1d78353e0866c7bc19c6a555fb65cbc454aea1f2`

## 需求与审计发现

重建项 `S0-PKG-01` 要求 `default-clear/manifest.json` 与唯一规范源码、仓库 APK 资源、最终 APK 内的资源以及 Skin Studio starter template 指向同一版本和字节。此前矩阵标记为 **NOT TESTED**，因为没有在一个可复核断言里贯通所有节点。源码和 Studio 服务实际已共享该资源，但既有 HTTP 回归只比较 Manifest ID，没有比较完整 Manifest；构建工具也没有检查最终 APK ZIP 内的资源。

本任务发现的是验收证据缺口，没有发现生产默认皮肤内容分叉。

## 修改

- `tools/skin-studio/build_default_clear.py` 新增只读 `verify_apk_asset()` 和 `--verify --apk <path>`。它先保留原有规范源码→仓库资源比较，再断言 APK 中恰好有一个 `assets/powercal-clear-default.pcskin`，并逐字节比较规范生成包；`--apk` 不与 `--verify` 同用时拒绝运行，检查不会写 APK。
- `test_default_clear.py` 将 Studio `/api/template-v3` 的 Manifest 断言从仅比较 ID 加强为完整 Manifest 对等，并保留每个源文件的逐字节对比；新增测试覆盖正确 APK 资源、旧/错误资源及缺失目标资源。
- 更新 Skin Studio README、AI 自动化验证命令、S0 验收矩阵和进度记录。矩阵只签核 `S0-PKG-01` 来源链，不扩展到导入/运行时 parity。

## 来源链

```text
default-clear/manifest.json + default-clear/source/**
  → runtime_package_v3.build()
  → app/src/main/assets/powercal-clear-default.pcskin
  → app/build/outputs/apk/debug/app-debug.apk 内同名资源
  → /api/template-v3 完整 Manifest 与解包文件
```

每段比较均使用完整字节；Studio endpoint 来自实际 `Handler` HTTP 服务，而非 Mock。Debug APK 是已通过完整离线 Gradle 门禁生成的产物；本 Issue 的工具/测试文档改动不变更 Android 应用源码。

## 验证证据

| 验收 | 结果 | 证据 |
| --- | --- | --- |
| 默认 Clear 定向测试 | **4/4 PASS** | `python -m unittest discover -s tools/skin-studio/tests -p test_default_clear.py -v`；日志 SHA-256 `393F241A69B5555C1722314621210D0E309BE9F00E9C0591701B409B5486C1CC` |
| Skin Studio 完整 Python 回归 | **60/60 PASS** | `python -m unittest discover -s tools/skin-studio/tests -v`；日志 SHA-256 `851B03FBE7A7981CDDEEA5186D026685F7D69AB64FD5B9C07C9F0A434D8CA205` |
| 规范源码/清单到仓库资源和最终 APK ZIP 资源 | **PASS，逐字节相等** | `python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk`；校验日志 SHA-256 `0E5DE07F76B7D93E729B02EFDF03033F96CAA6DCA2F9ECF80805534A499EF197` |
| `/api/template-v3` | **PASS，完整 Manifest 和全部文件对等** | 定向 HTTP test 使用 Studio 生产 `Handler`，发出本地 `GET /api/template-v3` 并比较 `unpack()` 后所有字段/文件；属于 Studio 服务端来源证据，不代表 Android WebView 行为 |
| Clear 包与 APK | **PASS** | 9 files / 384,912 bytes；规范包及 APK 内资源 SHA-256 均为 `D0D136EEF118DC52CE767E8416EC1BD3B3134F75302BC64C3D3ED4FCA5192BF9`。完整 Debug APK SHA-256 `8E5940F2B73703E76CB0778E849487EB8F9D8D1CD96787BE207D27BF8EEC9986` |
| Diff 格式 | **PASS** | `git diff --check` |

首次在受限沙箱中运行 HTTP 测试时，绑定 `127.0.0.1` 因 `WinError 10013` 被策略拒绝；该次是环境拒绝，不计为产品测试结果。随后在审批的本地回环执行上下文中，定向测试和完整 Studio Python suite 均通过。HTTP 服务仅绑定 loopback。

## 验收边界

`S0-PKG-01` 的来源/字节追溯可签 **PASS**。本 Issue 不签核 S0-PKG-02/03 的全部包形状与综合 CRUD 往返、S0-PKG-05 Android 安装激活/进程恢复、S0-PKG-06 Studio Preview 与 Android WebView parity、v1/v2 Studio 重编辑、物理/OEM 行为或 Native/Skin 完整 parity。整体 Skin-First separation 继续 **IN PROGRESS**，没有 skin cutover GO。
