# PC-ISSUE-SEPARATION-NATIVE-DRAFT-DECK-EMPTY-AI-LOCKED-20261107

## 状态

**已解决（窄切片 PASS）。**适用范围只有冻结 Native Draft Deck 的空态表面与 AI 批次锁定时的只读卡。完整 Draft Deck 和整套 Native→Web parity 仍为 `PARTIAL`，Product Cutover 仍为 `HOLD`。

## 验收基准

- 冻结源码：`b1f7629f0c52812e1887443872f43aa6c49908c4`。
- 冻结原始 APK SHA-256：`DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。
- Web 实现 commit：`5a267814ca83a201f4d2c3a1c3df357c1d35c1a0`。
- Web 默认 Skin ID：`powercal-clear-default`；仅一份官方 `.pcskin`。

## 问题与修复

旧 Web 卡组缺少与原生相近的无草稿卡片状态；AI 批次锁定时的卡片也没有完整复现原生只读详情、状态提示和停止入口。

本次在现有 Web v3 卡组中补齐空态与锁定态表现，锁定字段与操作按 Native 状态禁用；停止操作仍调用既有 Host action。Host 侧使用既有授权与批次管理，投影仅包含有界剩余数，不包含草稿 ID 或 session ID。没有改 Core 事务、冻结业务语义、Host API 版本、默认皮肤 ID 或恢复路径。

主要源码：`PowerCalApp.kt`、`WebAiBatchProjection.kt`、`WebAiBatchProjectionTest.kt`、`edit.js`、`native-095.css`、`DraftDeckSearchWebViewInstrumentedTest.kt`。

## 验证

- 串行 Gradle 全门禁：142 XML suites / 989 JVM tests / 0 failures, errors, skips；source boundaries、Debug assemble、Lint、AndroidTest 编译通过。
- Studio Python：90/90 PASS；相关 Node syntax PASS；默认包 verifier PASS。
- Web QA API 35 仪器对照：`currentWebDraftDeckEmptyAndAiLockedStatesMatchFrozenNativeContract` 1/1 PASS。
- 冻结 APK 源码快照下仪器对照：`frozenApkEmptyAndAiLockedDeckRemainsReadOnly` 1/1 PASS。设备原始 base APK 测前/测后哈希均为冻结 SHA；为避免旧 APK 与新测试 APK ABI 不兼容，使用冻结源码归档单独构建测试 APK。
- 空态关键文本/按钮矩形：Native 相对 Web 横向最大差约 3px、纵向最大差约 8px。AI 锁定表单主要矩形垂直差约 2–3px，底部按钮纵向差约 3px，按钮横向总宽度差约 8px。这是组件几何近似对照，不是整页像素等价声明。
- 空卡打开/返回未改变 Store 快照；锁定态 discard 被拒绝；实际可信 Stop 触控触发既有 `CANCEL_REQUESTED`，锁与 pending review 保留，正式记录不变。
- 浏览器 E2E：本切片 NOT TESTED。

## 证据

- `.local/android-test/reports/NATIVE_095_DRAFT_DECK_EMPTY_AI_LOCKED-20261107/manifest.md`
- 对照审计：`docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md`
- 截图与 SHA-256 详见审计文档及本地 evidence manifest。

## 已知限制

本 Issue 不签核 Deck 恢复、冲突、删除确认、完成撤销、IME、TalkBack/焦点、进程恢复或整个 Draft Deck。后续必须用隔离 fixture 逐项与冻结原生对照；不得把本窄切片扩展为全卡组 PASS。Native 仍保留独立安全恢复。
