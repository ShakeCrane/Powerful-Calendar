# Native 0.9.5 事件编辑器外框几何与表面

**状态：**外框几何与表面窄切片 PASS；事件编辑器整体仍 PARTIAL。
**冻结视觉基线：**`b1f7629f0c52812e1887443872f43aa6c49908c4`。
**原始 APK：**`.local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-b1f7629-debug-core-HOLD.apk`，SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`。

## 问题与处理

原生 `DirectEventEditor` 在屏幕上的 surface bounds 为 `[152,193][928,2272]` 物理像素，约合 `x=57.90dp, y=73.52dp, w=295.62dp, h=792dp`。Web 初版依据视口高度单位限制编辑器时，目标 Android WebView 把 `100dvh` 等高度值计算成 0，导致面板坍缩。实现改为相对固定全屏 `#skinSheet` 的百分比高度，并用 `data-mode="event"` 将样式限定到事件编辑模式。

WebView 视口为 412×867 CSS px、DPR 2.625 时，事件面板 DOM bounds 为 `x=58.095,y=49.524,w=296,h=792`；这里 y 坐标相对 WebView 内容区，屏幕顶部另有 24dp inset。宽度差小于 1dp，高度与顶边匹配。面板与原生取样底色均为 `#F6F8FC`，圆角 22，遮罩 alpha 0.60。底部操作区随后统一到同一 surface，消除 Web 自带白色分隔条造成的色块差异。

## Android 交互与 Store

在隔离 API 35 AVD 的 Web 包上，真实点按月历 DEADLINE 卡片打开对应事件编辑器。按 Back 无修改关闭；只读仪器检查结果 `1/1 PASS`，合成记录仍为 eventSeq 1–4、identity count 4、next sequence 5、pending draft count 0。未修改 Core、事件语义、Host API 或 Native 恢复路径。

冻结版编辑器截图：`.local/android-test/reports/native095-month-dense-20261106/emulator-5558-card-open.png`，SHA-256 `FC5D49AA6A84A354AAF5ACC3B0C734FCCA828159F837CF4535CBEB2D1513E1D1`。最终 Web 编辑器截图：`emulator-5582-editor-final2.png`，SHA-256 `8F38803727AD0E7DE89F3B9C52B3B9E23C5E33ED285442A36FB3BA6388D527FF`。Web DOM 计算值保存在 `web-editor-final2.json`；运行时高度诊断保存在 `test-vh-units.cjs` 及同目录日志。

## 代码和构建

实现涉及 `tools/skin-studio/default-clear/source/web/edit.js` 的事件模式标记、`native-095.css` 的作用域样式以及重建后的 `app/src/main/assets/powercal-clear-default.pcskin`。提交：`668bf33f21eaef757bc362f88282a8ed65404685` 和 `f05a7e7a1eab8ebdf2cfd30db3a0390b574d8907`。

最终 Debug APK SHA-256：`B9D7105231CE8ECEBAE2A83E6701E4F46D0425180518B0470BAFD465719CA0EF`。默认 `.pcskin` SHA-256：`1A5C89E0A0CFA8AD30A7CEDFF28C2B382FF3962A78DF47AA00BA746E9D552918`。正式构建器验证包 ID、Manifest 与 APK/source 字节一致性，APK 中仅一份 `.pcskin`。串行源码边界、assemble、Lint、Node syntax、Clear Studio bridge E2E 均通过；全量 Studio Python 89 项中 3 项失败，未作为通过结果。本轮没有运行完整 JVM 套件。

## 剩余差异

本窄切片不覆盖标题/事件类型的原生同排布局、各类字段控件、提醒区、重复规则控件、保存/取消/删除操作层级，也不证明四类事件编辑器的状态与交互等价。整体编辑器仍为 PARTIAL；后续逐组件移植必须继续使用冻结 APK 实屏对照并保留 Host/Core 写入语义。
