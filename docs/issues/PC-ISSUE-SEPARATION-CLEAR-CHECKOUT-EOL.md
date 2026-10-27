# Clear 皮肤源码检出换行导致 APK 同源校验失败

日期：2026-10-27。状态：**隔离工作树修复及 Studio 自动化 PASS；设备行为未测试。**

## 根因与修复

`core.autocrlf=true` 的新 Windows 工作树把 Clear 规范源码的 LF 文件检出为 CRLF。`.pcskin` 打包使用原始文件字节，所以 `build_default_clear.py --verify` 报告 APK 皮肤过期。对照 Git 对象与 APK 的 8 个文件后确认：其中 7 个文件以 Git 对象的字节直接匹配 APK，`web/clear-design.css` 则需要 CRLF。

增加仓库 `.gitattributes`，对 Clear 源码采用 `text=auto eol=lf`，对既有 `clear-design.css` 明确使用 `eol=crlf`。二进制字体由 Git 的自动二进制识别保留。只在独立工作树将六个被转换的文件恢复成现有 APK 所含字节，Git diff 确认这些源码内容没有变化；不重新生成 APK 资产。

## 证据与边界

- 修复前 `--verify` 返回 `Bundled Clear skin is stale`；独立复现日志 `.local/android-test/reports/separation-20261027/source-editor/pre-fix-clear-eol.log` SHA-256 `AA1CB39A71AD1C59C357EA77506BE70079A88C0C2C8266B2A65BDF6B980ACA8D`。该次测试的本地 HTTP 子项因沙箱阻止回环连接另报错；与源码字节不一致是不同问题。
- 修复后 `python tools/skin-studio/build_default_clear.py --verify` PASS；APK Clear 资产保持 372986 bytes，SHA-256 `1A5ED8F4AF23A99DFBB09582463AB007FCC3B2B5598351D76CFC508E18B51B41`。
- `test_default_clear.py` 新增检出属性断言；完整 Studio 回归 `python -m unittest discover -s tools/skin-studio/tests -v` 为 **40/40 PASS**。该运行还包含工作树中待单独提交的 Studio 附加源码编辑测试；日志 SHA-256 `25B0EEB7E188B5540B5B4E023CBEE404C39E6A71FF3FC766557E7CDC871492A3`。
- 本问题没有更改 Android 核心、默认皮肤内容或用户数据，也没有执行 Android 设备测试；该结果不表示完整皮肤分离已完成。

修复提交 `1a7de91` 后，以 `core.autocrlf=true` 创建全新 detached worktree；该工作树 `git status` 干净，`git ls-files --eol` 显示普通源码为 LF、`clear-design.css` 为 CRLF、字体为二进制。新工作树再次运行 `--verify` PASS，资产 SHA-256 与上述相同。这是实际检出验证，不是仅检查属性文本。
