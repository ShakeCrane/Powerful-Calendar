# Native 0.9.5 AI message bubble and role-label chrome

Status: **NARROW PASS** for message surface colors, typography, corner shape, alignment and first-page geometry. The AI page and the overall Native-to-Web migration remain `PARTIAL`; Product Cutover remains `HOLD`.

## Authority and scope

- Frozen source: `b1f7629f0c52812e1887443872f43aa6c49908c4`.
- Frozen APK: `.local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-b1f7629-debug-core-HOLD.apk`, SHA-256 `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`.
- Native reference implementation: `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/AiChatScreen.kt` and `NativeSkinThemeDefaults.kt`.
- The comparison used the same deterministic 42-message synthetic history, API 35, 1080x2400 at 420 dpi, and System WebView 124.0.6367.219. It changes only the canonical default Skin presentation and its Android test; no Core or Host behavior changed.

## Result

The Web v3 bubbles now use the frozen Native user/assistant fills and text colors, 14sp/20sp body type, 11sp/16sp role labels, 16dp/4dp asymmetric corners, no shadow, 10% user-message inset and 8dp inter-message gap. The actual light-mode WebView screenshot was measured against the frozen APK screenshot. For the first five user bubbles, Native exact-color bounds are `(134..588, 293..435)`, `(134..588, 621..763)`, `(134..588, 949..1091)`, `(134..588, 1277..1419)`, `(134..588, 1605..1747)`; Web bounds are `(133..588, 293..435)`, `(133..588, 621..763)`, `(133..588, 949..1090)`, `(133..588, 1276..1418)`, `(133..588, 1604..1745)`. Thus the visible fill edges differ by at most 2 physical pixels and the left edge by 1 pixel; this is close measured alignment, not a claim of pixel identity.

The instrumentation also checks dark-theme computed colors, but no dark-theme screenshot was taken. Existing menu/copy behavior remains Host-owned and the test verifies it does not mutate Calendar Store state. Attachment, proposal, error/retry, composer, menu visual layout, and whole-page AI parity remain open.

## Verification evidence

- Target test: `PowerCalAiProposalReviewWebViewInstrumentedTest#clearChatMessagesMatchNativeLabelsAndUseHostOwnedMenuActions`, PASS 1/1 on `PowerCal_Wave2_QA35_20261104` / `emulator-5556`. The assertions cover light/dark colors, role/body typography, corner radii, no shadow, 10% inset, nominal top/height, Host-owned message menu/copy, and unchanged Store counters.
- Full serial Gradle gate: PASS, 987 JVM tests, zero failures/errors/skips; both source-boundary checks, `assembleDebug`, `lintDebug`, and AndroidTest Kotlin compilation PASS.
- `python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk`: PASS. The APK has exactly `assets/powercal-clear-default.pcskin`; the 413170-byte source package and APK asset both hash to `ee2bc91820f6222f4ed820f35befada7e97b48a43dbc144496f610cc673ce0c8`. Debug APK SHA-256: `42edab078121d1ca69c80cf828b7406af868d04214baacb610276c1edfc7b849`.
- Frozen screenshot SHA-256: `B82A5AC4C43E8AE74F3C356E73F808F20381131E8B36685189833F2F7765F157`. Current WebView screenshot SHA-256: `2e540564706f69e0fcd188e1a09323da43d97b3faeb04f3c54fa0dd95cb264f3`.
- Detailed commands, measurements, hashes, and retry history: `.local/android-test/reports/native095-ai-bubbles-20261107/manifest.md`.

## Device-run scope note

The first correctly quoted `connectedDebugAndroidTest` invocation enumerated all connected devices and ran this one test on both the requested Wave2 AVD and `PowerCal_Native095_RawFixture_20261106` / `emulator-5590`. Both reported 1/1 PASS. The second device was not intended for this run; no further commands or cleanup were directed at it, and this report does not claim it was unchanged. The manual fixture, touch navigation, screenshot, and test-artifact cleanup described above were confined to `emulator-5556`. Future connected instrumentation runs must restrict the device serial or invoke instrumentation through an explicitly selected device.

## Change and cleanup

- Presentation source: `tools/skin-studio/default-clear/source/web/native-095.css`.
- Regression assertion: `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/PowerCalAiProposalReviewWebViewInstrumentedTest.kt`.
- Generated package: `app/src/main/assets/powercal-clear-default.pcskin`, rebuilt only with the official Studio builder.
- The 42-message synthetic history, onboarding preference created by this run, temporary device screenshot/XML, staging fixture, and this run's `tcp:19226` ADB forward were removed from the requested AVD. The app and its empty fresh database remain installed. Native recovery, package IDs, Core/Host APIs, and production cutover policy were not changed.

Implementation commit: see `docs/agent-handoff/CURRENT.md` (`LATEST_CODE_HEAD`).
