# PC-ISSUE-SEPARATION-CLEAR-GREEDY-LABEL-FOLDING — Clear 时间轴标题贪心折叠

- 日期：2026-10-29
- 状态：**PARTIAL**。Clear 源码、确定性算法、Skin Studio 往返与 Edge Studio Mock 的窄范围视觉/交互回归通过；Android Gradle/API 36 门禁 **BLOCKED**，Android WebView 与 Native/Clear 完整 parity **NOT TESTED**。
- 工作树：`codex/separation-s0-inventory`
- 基线：`2a02ae71fbeb8b50e2cb099533665e66243a2e53`
- 范围：重建矩阵 `S0-TL-05` 的 Clear 运行时标题折叠；不更改 Native 时间轴、Host 时间几何或事件存储。

## 需求和根因

`S0-TL-05` 要求按标题实际渲染后的矩形折叠视觉冲突，只隐藏标题；事件框、真实开始/结束时间、点击目标和完整无障碍名称必须保留。基线 Clear 只在时间轴事件控件中渲染标签，没有从 DOM 测量候选标题并执行可见 lead 贪心决策的机制。因此，它既无法识别不同时间区间产生的文字矩形冲突，也无法保证被隐藏的标题不继续影响后续 lead。

## 修改内容

- 新增 `web/title-folding.js` 纯函数。候选按统一可见区域、屏幕 Y、稳定事件序号、稳定 ID 排序；仅对正面积严格相交的矩形判为冲突，所以边缘相切不折叠。每个候选只与当前可见 lead 比较；隐藏项不替换 lead。候选输入被验证，决策复制矩形，不依赖输入集合遍历顺序，也不修改输入。
- Clear `app.js` 将候选创建、实际 DOM 文字测量、可见区域裁切、纯函数决策和可见性应用分开。用 `Range.getClientRects()` 测量渲染结果，按既有泳道宽度单行截断并显示省略号。候选矩形统一使用浏览器视口坐标，并裁切到时间轴视口、对应左右翼和浏览器视口；不读取事件时间重叠关系作为碰撞代理。
- Activity 标签在事件视觉框能容纳时放在框内；不足时以真实可见开始位置为上缘锚点。事件时间几何和 44px 交互按钮独立保留。ALARM 与精确 Deadline 标签同样参与实测标题折叠；纯日期 Deadline 仍显示在日期条。
- 隐藏标题以 `visibility:hidden` 呈现，标题层 `pointer-events:none` 且 `aria-hidden=true`；正式事件按钮保留完整 accessible name、原始标题和点击/键盘目标。没有增加 `×N`，事件不合并、不隐藏。
- 滚动、视口尺寸变化、时间轴重绘和字体就绪后重新计算。Skin Studio 增加 A/B/C/D 和长标题场景；Clear bundle 包含纯折叠模块。

## 定向证据

### 纯函数回归

`node --test tools/skin-studio/tests/title-folding.unit.test.cjs`：**10/10 PASS**。

覆盖 A/B 冲突、被隐藏 B 不阻止 C 成为新 lead、C/D 下一冲突段、连续冲突、水平/垂直边缘相切、独立视觉区域、只用屏幕矩形（无事件时间字段）、屏幕 Y/序号/稳定 ID 排序、平移/缩放/复位确定性、输入不可变及非法输入拒绝。

### Edge Studio Mock 实测

- 环境：Microsoft Edge Headless `Edg/156.0.4314.50`，视口 `1460×960`，Skin Studio v3 Mock Host；证据目录 `.local/android-test/reports/separation-s0-greedy-title-folding/run-20261029-16/`。
- 决策轨迹：`101/A visible → 301/B folded against A → 302/C visible → 303/D folded against C`。完整候选中 C 前有其他泳道的可见标题，因此浏览器轨迹里 C 与序号 108 的可见标题比较；纯函数隔离 A/B/C/D 测试则直接确认 C 对比 A。两种轨迹都确认隐藏 B 未成为 lead。
- A/B/C/D 是相邻且不重叠的事件时间段：10:00–10:01、10:01–10:02、11:00–11:01、11:01–11:02；实际文字矩形却显示 A/B 与 C/D 视觉相交。A 不与 C 相交。

| 标题 | 视口文字矩形（left, top — right, bottom；CSS px） | 决策 |
| --- | --- | --- |
| A | `821, 502.890625 — 875.21875, 517.890625` | visible |
| B | `821, 503.34375 — 875.203125, 518.34375` | folded against A |
| C | `821, 529.6875 — 875.234375, 544.6875` | visible |
| D | `821, 530.140625 — 876.015625, 545.140625` | folded against C |

- 左翼标题序号 201/202/203：visible → 左翼横滚裁切后 clipped → 恢复后 visible。右翼 A/B/C/D：横滚到裁切区后均 clipped，恢复后 lead 决策完全相同。
- 纵向滚动、16 小时缩放、24 小时复位、日期离开再返回后，A/B/C/D 决策保持稳定。真实鼠标点击命中折叠事件 301 的 BUTTON；标题不在 hit target，Mock Host 打开 `#301 · ACTIVITY`。Mock 编辑器字段固定为“测试活动”，因此此 Mock 本身不能证明其表单展示 `Greedy_B`；已确认宿主事件按钮仍保留完整 `Greedy_B` accessible name。实体/Android WebView 的完整标题查看仍待测。
- 28 个事件均保留（27 个时间事件、1 个日期 Deadline）；按钮与视觉事件矩形相对基线逐事件相同；中轴、当前时间线和日期 Deadline 仍存在；没有 `×N`。
- 长标题显示为单行 `非常长的时…`，实际宽 `70.375px`，在 `71px` 泳道标题宽度内；按钮 accessible name 保留完整标题。

浏览器证据及摘要 SHA-256：

| Artifact | SHA-256 |
| --- | --- |
| `title-folding-trace.json` | `FB345216332C7B426F16854167085DD116617ED39DA94F428B077C58371CEE08` |
| `clear-before-greedy-folding.png` | `6BA5206B5B93612F9BD0FB303D7FD4D99FAD9E1978F3704AF257F727A173625B` |
| `clear-after-greedy-folding.png` | `D2C351E51200751ACFCED1F02A7DBCDCB48CC0F7A443972DBDCFF573F6EA6227` |
| Edge CDP 驱动脚本 | `0E242E4457C8C4E3A4D6AC2F99C56EAEBB7A61E87A03AF930BA257C0B3A256D1` |

截图为基线包与修改后包在同一 Studio timeline-geometry 场景下的前后视图。基线包 SHA-256 `0790173E60E4F991EAA2072B43DF04039AFC7AB80416770A285B96CD0B3118CD`；修改后 Clear 包 SHA-256 `75C1663B4FD7FCE40C8EADB31BA7E2C6C8AC092330FC4CB734EE6D21ACC2CE4C`。原始图片和 JSON 留在忽略的 `.local` 证据目录，不进入 Git。

### 其他验证

| 检查 | 结果 |
| --- | --- |
| `node --test tools/skin-studio/tests/title-folding.unit.test.cjs` | **10/10 PASS** |
| Clear、folding 模块、浏览器 E2E、单元测试、Studio JS 的 `node --check` | **PASS** |
| `python -m unittest discover -s tools/skin-studio/tests -v` | **59/59 PASS**；含规范包、Studio 源码编辑、预览/导出/重新导入检查 |
| `python tools/skin-studio/build_default_clear.py --verify` | **PASS**；9 files / 383,312 bytes |
| `skin_studio.py validate ...powercal-clear-default.pcskin --json` | **PASS**；v3 schema，9 files |
| `git diff --check` | **PASS** |
| 完整 Gradle：source boundary、JVM、Debug APK、lint | **BLOCKED**；本地 SDK `E:\Project\Powerful-Calendar\.local\android-test\sdk` 缺少项目要求的 `platforms;android-36`，失败文本为 `Failed to find target with hash string 'android-36'` |
| Android WebView / Android instrumentation / 模拟器 / 实体设备 | **NOT TESTED**；本轮没有安装、启动或清理任何设备/AVD |
| Native 与 Clear 同场景完整 parity | **NOT TESTED** |

可复核日志 SHA-256：Node 定向测试/语法 `887CD9B5D141480538DD67DF3D07BF5A7B4C63CE9977E296C3CEB969B94CEC75`；Skin Studio Python `F39A73B768F19407AB3AC50DC8B360E016E19E2347B59F523CC09A3E93D515D7`；包构建/校验 `ED8AC858453A61FE4C13C1764F2E20F3D63B51EECAC3B984EE2F9F0CF3944D0A`；Gradle 阻塞日志 `C213C4642AE46ACFDFA2E3BD6F8D073B71B9ADFFCEDF300074CC642662747B70`。路径均位于上述 `.local` 目录。

## 剩余风险和签核

- 本结论只证明 Clear 的独立 JavaScript 策略及 Edge Studio Mock 行为；Host/WebView 注入、Android WebView 字体和触摸、Native/Clear 等价及 OEM 表现没有设备证据。`S0-TL-05` 全 parity 不能签 PASS，分离/发布 cutover 不构成 GO。
- 项目最低 WebView 若不提供 `Intl.Segmenter`，当前 fallback 使用 Unicode code point 迭代，对 ZWJ/组合字符簇的截断边界仍需验证或加强。
- Android Gradle 门禁需要安装/配置 API 36 平台后重跑；此处不安装 SDK、不进行联网下载。
- 一次扩展 Mock 检查曾把固定占位编辑字段误当作完整事件标题断言，随后改为核实真实指针打开 `#301` 与完整 accessible name，并在 Edge 重跑通过；因此报告只认定打开事件和无障碍标题保留，不宣称 Mock 表单展示了原始标题。
