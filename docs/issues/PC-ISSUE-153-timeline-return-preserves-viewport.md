# PC-ISSUE-153 — 返回时间轴保留视口与缩放

日期：2026-10-24

优先级：P1

状态：**API 35 原生时间轴 → Memo → 时间轴路径 PASS（窄化）；整体冻结 HOLD**

## 需求与范围

`REC-P1-003` 根据核心合同 Invariants 5–6、PC-ISSUE-004 和标题折叠验收要求重建，历史 209 项 ID 仍为 `UNMAPPED`。本项验证离开时间轴到 Memo 页面再返回时，最近一次相机锚点和垂直缩放保持不变。测试走真实 `PowerCalApp` Compose 路由及时间轴缩放/平移交互；不做视觉改版，不修改生产代码。

本次只签核原生时间轴与 Memo 页之间的往返。其他页面路径、Activity/进程重建以及实体设备行为不由此测试推断。

## API 35 AVD 定向回归

设备 `emulator-5556`，API 35，竖屏 1080×2400px；没有覆盖屏幕分辨率或方向。AndroidJUnitRunner 最终结果 **1/1 PASS**：

1. 从初始 `TIMELINE` 相机开始，锚点 `1792843200000`、24 小时/屏。
2. 点“放大，显示更短时间”并在时间轴竖向滑动后，锚点变为 `1792965924653`、16 小时/屏。
3. 打开 Memo 页面，确认 `page=MEMO`，再点返回。
4. 返回后 `page=TIMELINE`，锚点仍为 `1792965924653`，缩放仍为 16 小时/屏；两个相机字段与离开前逐值相等。

测试通过 `WebSkinBridgeRuntime` 读取应用当前 timeline 投影状态，比较实际 Compose 路由切换前后的锚点与小时跨度。日志输出和相机轨迹位于 `.local/android-test/reports/freeze-20261024/`：

| 证据 | SHA-256 |
| --- | --- |
| 最终定向输出 `pc-issue-153-return-viewport-targeted-r5.log` | `D26BCBF3177F8765A455D4243E1AFC208F53BF7973AAF0D8166B71462AA53D7B` |
| 初始/离开前/返回后相机轨迹 `pc-issue-153-return-viewport-trace.txt` | `680731D0EE21F9CE78193B4DDCADD755F7F5A919291C791A2F956FC85C3346FF` |

### 测试夹具失败记录

没有隐藏早期失败。首次测试代码编译曾引用当前 Compose 依赖不存在的 `SemanticsProperties.CustomActions`；改为与现有回归一致的竖向触摸手势后编译通过。随后三次 instrumentation 启动失败均发生在相机断言之前，原因是测试夹具存储：

| 尝试 | 结果 | 日志 SHA-256 |
| --- | --- | --- |
| r1 | 测试包 `Context.applicationContext` 为 null，`SQLiteOpenHelper` 无法解析测试数据库路径 | `5734321381407A9716E9FE8E03350767AB458FFD2BA14EBD1AB64D8596DDC6F5` |
| r2 | 测试包的 `databases/` 目录不存在，SQLite 无法打开隔离测试库 | `F66939C22E6A1A7BB6120A7576AD837B0FCF7CA3C2B6494DBE02EE079EB0C4AF` |
| r3 | 测试包缓存目录不可写，隔离上下文在 Compose 启动前终止 | `CE62A525ACEA28764F6EC76FCC6351F4EDA201872CDBAC32E261A2515F3FEC8E` |

最终夹具改用应用包中的随机命名数据库、偏好名称空间及缓存目录；所有路径在测试结束时清理。它没有打开正式 PowerCal 数据库，也没有读取或写入现有偏好。测试结束后 AVD 分辨率仍为 1080×2400，焦点回到 Launcher，未留下 `pc-issue-153-` 数据库、缓存或偏好项。

## 完整 Gradle 回归

命令：

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain
```

结果 **BUILD SUCCESSFUL**：490/490 JVM 测试、65 个 suite、0 failures/errors/skips；源码边界、Debug/AndroidTest APK 组装通过；Lint 0 errors / 58 warnings。490 项包含 489 个跟踪测试和一个受保护、预先存在且未暂存的 `DraftDeckFeedbackTest.kt` 测试；该文件未修改。

| 产物 | SHA-256 |
| --- | --- |
| 完整 Gradle 日志 `pc-issue-153-full-gradle.log` | `30E211EB07F1EFAF18BC5735B76B7FF46663A904AB09FA26C2EC08A8B03F7257` |
| Debug APK | `EE7E4850A147AC3B3F21326DCB209B0967F14DE6990D56EE05A2976FEB252BCD` |
| AndroidTest APK | `3228DF57FE8EAF3D38FBD25E54876FD533EE2228F05191507EBEF6B4793567E1` |
| Lint XML | `44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F` |

## 验收矩阵与交付

`REC-P1-003` 在台账中标为 `PASS`，实际范围明确限定为 API 35 原生竖屏 Timeline→Memo→Timeline 往返。结构校验 r19 在修正环境描述后重跑，记录 210 条活动重建项，P0/P1/P2 为 144/65/1；186 PASS / 0 FAIL / 8 BLOCKED / 10 NOT TESTED / 6 SPEC UNRESOLVED。历史 ID 映射为 0，`product_behavior_tested=false`。artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r19-pc-issue-153-metadata-corrected.json` SHA-256 `DF3E3697C15CA7EBB66DE4B1E7821A09114AFA8039DE34227230DEA90170D9BD`；CSV SHA-256 `AA80C181A42C015CFAD01B02E05C468F7A303CB73101D9D957DF12FA97677BD8`。结构检查只验证台账，不代表产品行为通过。

测试提交 `b3086bffffd97a412ecd0abefe0be419be1cc88f`（`test(timeline): 验证返回时保留视口与缩放`）已提交并推送。文档与台账单独提交。冻结仍为 **HOLD**，剩余设备、provider、未测及规格缺口继续保留。
