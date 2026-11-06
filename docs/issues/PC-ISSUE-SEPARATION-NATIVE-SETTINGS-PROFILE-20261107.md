# Native 0.9.5 Settings personal profile card parity

**Status: narrow PASS. Settings page and overall Native/Web parity remain PARTIAL.**

## Baseline

- Frozen source: `b1f7629f0c52812e1887443872f43aa6c49908c4`.
- Frozen APK: `.local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-b1f7629-debug-core-HOLD.apk`.
- Frozen APK SHA-256: `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`.
- Reference AVD: `PowerCal_Native095_MenuReference_20261107`, serial `emulator-5560`, API 35, 1080×2400, 420 dpi, version `0.9.5-rc.1`. The installed `base.apk` was pulled and its SHA matched the frozen APK.
- Web AVD: `PowerCal_Wave2_QA35_20261104`, serial `emulator-5556`, API 35, 1080×2400, 420 dpi, System WebView `124.0.6367.219`.

## Finding and implementation

The Clear profile section used a separate “edit profile” button and modal, unlike the frozen Native Settings card, which embeds the labeled field and Save control in the card. This slice moves the profile title, local-only subtitle, privacy explanation, input and Save action into the card and aligns their light-mode bounds to the original screen.

The field stays blank and read-only until touched. That action requests the existing Host-owned profile projection; Save and close continue through the existing Host actions. Back clears the unsaved value and returns focus to the field. The Host continues to own profile persistence. Core APIs, calendar data, skin identity, package selection and Native recovery were not changed.

## Screenshot and geometry evidence

The original APK screen and current WebView screenshot are both 1080×2400. The original APK's Settings hierarchy reports the profile card `[42,299]-[1038,963]`, heading text `[84,341]-[312,423]`, input container `[84,606]-[996,774]`, and Save action container `[84,796]-[429,922]`.

The WebView is `[0,128]-[1080,2337]`, with `devicePixelRatio=2.625`. The input DOM rectangle is CSS `(x=32.2619, y=191.00595, w=347.6667, h=56)`, mapping to screen approximately `[84.69,629.39]-[997.31,776.39]`. Pixel scans of the screenshots locate the visible input stroke within about 1 px horizontally, 1 px at the top and 3 px at the bottom. Bounds sampled after focus/scroll are excluded from this comparison.

Screenshots:

- Native: `.local/android-test/reports/native095-settings-profile-20261107/native-settings-final.png`, SHA-256 `45841C5B564EF90071A8DC76DD0E1115C9B34FC2DD5279EA43C4A5B6EB054091`.
- Web: `.local/android-test/reports/native095-settings-profile-20261107/web-profile-final.png`, SHA-256 `B824351978BB112FED838A67ED9BDEB0CC62ACE1E19867C7097FDFF0A57AF16B`.

## Verification

- Serial Gradle gate: `:app:verifySkinSourceBoundary`, `:app:verifyNativeSkinBoundary`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`, `:app:compileDebugAndroidTestKotlin`, and `:app:assembleDebugAndroidTest` all succeeded. JVM results: 142 suites, 987 tests, 0 failures, 0 errors, 0 skipped.
- API 35 WebView instrumentation passed:
  - `nativeAndClearAuthoredProfileEditorSaveMatchWithScopedBridgeProjection` — 1/1. Verified Host projection only after explicit action, native/Web saved value equality, Back discarding an unsaved value, restored focus, and unchanged Store snapshot.
  - `clearSettingsRetireSkinCatalogueAndKeepProtectedOperations` — 1/1.
- Studio checks: default distribution Python tests 2/2; Default Clear Python tests 5/5; changed JavaScript and E2E syntax checks PASS; `e2e_v3_notification_permission_studio_mock.cjs` PASS. The browser E2E is a Studio preview mock and is not counted as Android verification.
- `build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk` passed. Exactly one `.pcskin` is shipped; ID `powercal-clear-default`; source and APK asset SHA-256 `4A057F27EA31343362B1EC7BD8F2E7B928E8C97A8520DE653B2A32196B3CD400`. Debug APK SHA-256 `B0AEEF6E19F8AA14AB5FBB2B4ED80C2DE0DED405C1519809A4827053EA09F0DF`.
- One initial instrumentation attempt stopped at an assertion that compared an empty JavaScript value without accounting for `evaluateJavascript` JSON encoding. The assertion was corrected; the final exact test above passed. This was test-harness output handling, not a product defect.

## Evidence and remaining scope

Full command logs, AVD identity, pulled original APK, screenshots, hierarchy dumps, package verification and the browser result are indexed in `.local/android-test/reports/native095-settings-profile-20261107/manifest.md`.

No AVD or app data was cleared. The isolated Web QA app's notification prompt flags were reset to rerun an existing Settings test. Calendar Store snapshots stayed unchanged. The Native catalogue is omitted per Owner retirement; this does not delete or export any installed skin files.

The profile card is a narrow PASS only. AI configuration, reminder/permission and calendar-data sections, Settings focus/scroll states and the complete Settings page remain unverified or PARTIAL. Product Cutover remains HOLD.

Implementation commit: `b6a9b91679c0f77d4e3415b6bdcee77d5663312e` (`fix(skin): 对齐原生个人资料设置卡片`).
