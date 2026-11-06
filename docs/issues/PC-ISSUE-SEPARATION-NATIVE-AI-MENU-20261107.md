# Native 0.9.5 AI message-action menu parity

Status: **NARROW PASS** for message action menu geometry, styling, labels, and Host-owned copy interaction. AI page parity remains `PARTIAL`; Product Cutover remains `HOLD`.

## Frozen reference

- Source SHA: `b1f7629f0c52812e1887443872f43aa6c49908c4`.
- APK: `.local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-b1f7629-debug-core-HOLD.apk`.
- APK SHA-256: `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`.
- Frozen menu implementation: `app/src/main/java/dev/powerfulcalendar/ui/v2/AiChatScreen.kt`, `combinedClickable` long press and Compose `DropdownMenu`.
- Frozen screenshot was captured from that APK on a newly isolated API 35 AVD. The current WebView comparison ran on the explicit API 35 QA AVD with WebView 124.0.6367.219. Both show the same 42-message synthetic history at 1080×2400 / 420 dpi.

## Finding and correction

The old Web menu was a wide white floating panel that opened above/over the message. Frozen Native opens its pale `#EFF2F8` menu immediately below the long-pressed bubble, with a 4 dp corner, four 48 dp actions, and 14 sp labels.

The canonical Web Skin now appends the menu under the bubble and uses the frozen menu fill, width, corner, spacing, and text scale. This retains the existing closed Host actions and does not add business behavior.

## Evidence

- Frozen Native bounds: `(134,444,428,546)` physical pixels.
- Web changed region: `(133..563,436..981)`, 431×546 physical pixels. Web's bubble is 8 pixels higher in this screenshot pair; relative menu placement is flush to the bubble in both. Width differs by 3 physical pixels; menu height matches.
- Web DOM: 164×208 CSS px for the four-action menu; flush to bubble; `rgb(239,242,248)`, 4 px radius, 48 px action rows, 14 px text. All four labels/order match Native.
- Real WebView touch long-press opened the menu. Copy went through Host and produced the expected clipboard text. Calendar Store snapshot (events, drafts, pending, commits, identities, sequence) was unchanged.
- Detailed AVD identities, screenshot hashes, commands and build evidence: `.local/android-test/reports/native095-ai-menu-20261107/manifest.md`.

## Verification and artifacts

- Target Android instrumentation method: `clearChatMessagesMatchNativeLabelsAndUseHostOwnedMenuActions`, 1/1 PASS on `PowerCal_Wave2_QA35_20261104` / `emulator-5556`.
- Serial Gradle gate PASS; 987 JVM tests passed on the full execution, 0 failures/errors/skips. Source boundaries, APK build, Lint, AndroidTest Kotlin compile and APK assembly passed.
- Node syntax PASS; default distribution Python tests 2/2 PASS; official package verification PASS.
- Debug APK SHA-256: `F3EB40CA90C732137F820C4F8843217C990AC9672F6843EC68F3D1EDDE5105D7`.
- The only packaged skin remains `powercal-clear-default.pcskin`, 10 files / 413166 bytes; source and APK asset SHA-256 `1B05DA65418D5C0C24273F369C54B95EE59D1DCC0CFBA32A3FFD6E80D5478F11`.

## Limits and next work

This does not establish AI page parity. The screenshot comparison covers the user menu; the assistant menu has DOM assertions but no separate raw-APK screenshot. Menu behavior near the scroll viewport edge/auto-flip, proposal, attachment, error/retry, assistant-specific states, composer interaction, dark-mode screenshot, focus traversal and full-screen Native/Web comparison remain unverified or partial. No Core Store, Host business API, default ID, Native recovery, or production-cutover behavior changed. Product Cutover remains `HOLD`.
